import { DurableObject } from "cloudflare:workers";

/**
 * Cipher hub: the single authority for username registration, presence,
 * message relay and encrypted message storage.
 *
 * The hub never sees plaintext. Clients ship AES-GCM ciphertext encrypted
 * under a conversation key derived from an ECDH exchange between the two
 * accounts, so `cipher` blobs stored here are opaque to the server.
 */

import { presign, r2Config, removeObject, type R2Config } from "./r2";
import { serviceAccount, wakeDevice, type ServiceAccount } from "./push";

type Env = {
  DO: Fetcher;
  /** Optional R2 credentials; absent means files use built-in chunked storage. */
  R2_ACCOUNT_ID?: string;
  R2_ACCESS_KEY_ID?: string;
  R2_SECRET_ACCESS_KEY?: string;
  R2_BUCKET?: string;
  /** Optional Firebase key; absent means messages wait for the app to open. */
  FCM_SERVICE_ACCOUNT?: string;
};

type UserRow = {
  username: string;
  auth_digest: string;
  public_key: string;
  sealed_private_key: string;
  created_at: number;
  last_seen: number;
  receipts: number;
  typing_on: number;
  presence: number;
  strangers: number;
};

type MessageRow = {
  seq: number;
  id: string;
  sender: string;
  recipient: string;
  cipher: string;
  created_at: number;
  state: string;
  reply_to: string;
  expires_at: number;
  reactions: string;
  blob_id: string;
  delivered_at: number;
  read_at: number;
};

type BlobRow = {
  id: string;
  owner: string;
  recipient: string;
  cipher: string;
  created_at: number;
};

type FileRow = {
  id: string;
  owner: string;
  recipient: string;
  /** "r2" when the bytes live in the bucket, "do" when they live in chunks here. */
  store: string;
  size: number;
  chunks: number;
  created_at: number;
};

/** Cadence of the hub's own keepalive tick to every connected peer. */
const BEAT_MS = 25_000;

const USERNAME_RULE = /^[a-z0-9._]{3,20}$/;
const BLOB_ID_RULE = /^[a-zA-Z0-9._-]{6,80}$/;
const MAX_CIPHER = 12_000;
/** Base64 ciphertext of one attachment. Roughly 1 MB of image bytes. */
const MAX_BLOB = 1_400_000;
/**
 * One chunk of a file held in Durable Object storage. DO rows cap at 2 MB, so
 * this leaves comfortable headroom for the row's other columns.
 */
const MAX_CHUNK = 700_000;
/** Ceiling for a file kept in DO storage, which is shared by every account. */
const MAX_FILE_DO = 8 * 1024 * 1024;
/** Ceiling for a file in R2, where storage is the project's own. */
const MAX_FILE_R2 = 128 * 1024 * 1024;
const MAX_TTL_SECONDS = 60 * 60 * 24 * 30;
const HISTORY_PAGE = 400;

function clean(value: unknown): string {
  return typeof value === "string" ? value.trim().toLowerCase().replace(/^@/, "") : "";
}

function bad(message: string, status = 400): Response {
  return Response.json({ ok: false, error: message }, { status });
}

export class Hub extends DurableObject<Env> {
  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    const sql = this.ctx.storage.sql;
    sql.exec(`
      CREATE TABLE IF NOT EXISTS users (
        username TEXT PRIMARY KEY,
        auth_digest TEXT NOT NULL,
        public_key TEXT NOT NULL,
        sealed_private_key TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        last_seen INTEGER NOT NULL,
        receipts INTEGER NOT NULL DEFAULT 1,
        typing_on INTEGER NOT NULL DEFAULT 1,
        presence INTEGER NOT NULL DEFAULT 1,
        strangers INTEGER NOT NULL DEFAULT 1
      )
    `);
    sql.exec("CREATE INDEX IF NOT EXISTS users_auth ON users (auth_digest)");
    sql.exec(`
      CREATE TABLE IF NOT EXISTS messages (
        seq INTEGER PRIMARY KEY AUTOINCREMENT,
        id TEXT NOT NULL UNIQUE,
        sender TEXT NOT NULL,
        recipient TEXT NOT NULL,
        cipher TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        state TEXT NOT NULL
      )
    `);
    sql.exec("CREATE INDEX IF NOT EXISTS messages_recipient ON messages (recipient, seq)");
    sql.exec("CREATE INDEX IF NOT EXISTS messages_sender ON messages (sender, seq)");

    // Additive migrations: replies, disappearing timers and reactions.
    const columns = sql
      .exec<{ name: string }>("PRAGMA table_info(messages)")
      .toArray()
      .map((column) => column.name);
    if (!columns.includes("reply_to")) {
      sql.exec("ALTER TABLE messages ADD COLUMN reply_to TEXT NOT NULL DEFAULT ''");
    }
    if (!columns.includes("expires_at")) {
      sql.exec("ALTER TABLE messages ADD COLUMN expires_at INTEGER NOT NULL DEFAULT 0");
    }
    if (!columns.includes("reactions")) {
      sql.exec("ALTER TABLE messages ADD COLUMN reactions TEXT NOT NULL DEFAULT '{}'");
    }
    if (!columns.includes("blob_id")) {
      sql.exec("ALTER TABLE messages ADD COLUMN blob_id TEXT NOT NULL DEFAULT ''");
    }
    // Receipt clocks: when the hub handed the message over, and when it was opened.
    if (!columns.includes("delivered_at")) {
      sql.exec("ALTER TABLE messages ADD COLUMN delivered_at INTEGER NOT NULL DEFAULT 0");
    }
    if (!columns.includes("read_at")) {
      sql.exec("ALTER TABLE messages ADD COLUMN read_at INTEGER NOT NULL DEFAULT 0");
    }
    sql.exec("CREATE INDEX IF NOT EXISTS messages_expiry ON messages (expires_at)");

    // Encrypted attachments. The hub stores the ciphertext only; the key that
    // opens it travels inside the message envelope, which is itself encrypted.
    sql.exec(`
      CREATE TABLE IF NOT EXISTS blobs (
        id TEXT PRIMARY KEY,
        owner TEXT NOT NULL,
        recipient TEXT NOT NULL,
        cipher TEXT NOT NULL,
        created_at INTEGER NOT NULL
      )
    `);
    sql.exec("CREATE INDEX IF NOT EXISTS blobs_owner ON blobs (owner)");

    // Shared files. The row is only bookkeeping — who may fetch it and where
    // the bytes are. The bytes themselves are ciphertext the hub cannot open,
    // in R2 when the project has configured it, in `file_chunks` otherwise.
    sql.exec(`
      CREATE TABLE IF NOT EXISTS files (
        id TEXT PRIMARY KEY,
        owner TEXT NOT NULL,
        recipient TEXT NOT NULL,
        store TEXT NOT NULL,
        size INTEGER NOT NULL,
        chunks INTEGER NOT NULL DEFAULT 0,
        created_at INTEGER NOT NULL
      )
    `);
    sql.exec("CREATE INDEX IF NOT EXISTS files_owner ON files (owner)");
    sql.exec(`
      CREATE TABLE IF NOT EXISTS file_chunks (
        file_id TEXT NOT NULL,
        seq INTEGER NOT NULL,
        cipher TEXT NOT NULL,
        PRIMARY KEY (file_id, seq)
      )
    `);

    // Devices that may be woken for an account. A row is a Google-side push
    // address and nothing else: no message, no thread, no history. It exists so
    // a closed app can be told "there is something for you" without Cipher
    // having to hold a socket open behind a permanent notification.
    sql.exec(`
      CREATE TABLE IF NOT EXISTS devices (
        token TEXT PRIMARY KEY,
        username TEXT NOT NULL,
        updated_at INTEGER NOT NULL
      )
    `);
    sql.exec("CREATE INDEX IF NOT EXISTS devices_user ON devices (username)");

    sql.exec(`
      CREATE TABLE IF NOT EXISTS blocks (
        blocker TEXT NOT NULL,
        blocked TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        PRIMARY KEY (blocker, blocked)
      )
    `);
  }

  // ---------------------------------------------------------------- storage

  private user(username: string): UserRow | null {
    return (
      this.ctx.storage.sql
        .exec<UserRow>("SELECT * FROM users WHERE username = ?", username)
        .toArray()[0] ?? null
    );
  }

  private userByDigest(digest: string): UserRow | null {
    return (
      this.ctx.storage.sql
        .exec<UserRow>("SELECT * FROM users WHERE auth_digest = ?", digest)
        .toArray()[0] ?? null
    );
  }

  private touch(username: string): void {
    this.ctx.storage.sql.exec(
      "UPDATE users SET last_seen = ? WHERE username = ?",
      Date.now(),
      username,
    );
  }

  /** Every account this user has exchanged messages with. */
  private peersOf(username: string): string[] {
    return this.ctx.storage.sql
      .exec<{ peer: string }>(
        `SELECT DISTINCT sender AS peer FROM messages WHERE recipient = ?
         UNION
         SELECT DISTINCT recipient AS peer FROM messages WHERE sender = ?`,
        username,
        username,
      )
      .toArray()
      .map((row) => row.peer)
      .filter((peer) => peer !== username);
  }

  // ----------------------------------------------------------------- blocks

  /** Usernames this account has blocked. */
  private blocksOf(username: string): string[] {
    return this.ctx.storage.sql
      .exec<{ blocked: string }>("SELECT blocked FROM blocks WHERE blocker = ?", username)
      .toArray()
      .map((row) => row.blocked);
  }

  private isBlocked(blocker: string, blocked: string): boolean {
    return (
      this.ctx.storage.sql
        .exec<{ n: number }>(
          "SELECT COUNT(*) AS n FROM blocks WHERE blocker = ? AND blocked = ?",
          blocker,
          blocked,
        )
        .toArray()[0]?.n ?? 0
    ) > 0;
  }

  private hasWrittenTo(sender: string, recipient: string): boolean {
    return (
      this.ctx.storage.sql
        .exec<{ n: number }>(
          "SELECT COUNT(*) AS n FROM messages WHERE sender = ? AND recipient = ?",
          sender,
          recipient,
        )
        .toArray()[0]?.n ?? 0
    ) > 0;
  }

  // ---------------------------------------------------------------- sockets

  /**
   * Live sockets per username.
   *
   * Deliberately in memory and deliberately NOT the hibernation API. A
   * hibernated object keeps its sockets looking alive at the edge — the
   * runtime even answers protocol pings on its behalf — but the first app
   * frame that arrives afterwards is dropped and the connection dies. A chat
   * hub is the one workload that must never sleep behind a live socket, so
   * every peer is accepted with the standard API, which keeps the object
   * resident for as long as anyone is connected.
   */
  private peers: Map<string, Set<WebSocket>> = new Map();

  /** Keeps sockets warm and proves liveness to clients. */
  private beatHandle: ReturnType<typeof setTimeout> | null = null;

  private socketsFor(username: string): WebSocket[] {
    const set = this.peers.get(username);
    return set === undefined ? [] : [...set];
  }

  private socketCount(): number {
    let total = 0;
    for (const set of this.peers.values()) total += set.size;
    return total;
  }

  private track(username: string, socket: WebSocket): void {
    const set = this.peers.get(username) ?? new Set<WebSocket>();
    set.add(socket);
    this.peers.set(username, set);
  }

  private untrack(username: string, socket: WebSocket): void {
    const set = this.peers.get(username);
    if (set === undefined) return;
    set.delete(socket);
    if (set.size === 0) this.peers.delete(username);
  }

  /**
   * One tick for every connected peer. Traffic every [BEAT_MS] keeps proxies
   * and phone radios from quietly dropping an idle connection, and gives the
   * client positive proof the hub is still there instead of a guess.
   */
  private ensureBeat(): void {
    if (this.beatHandle !== null || this.socketCount() === 0) return;
    this.beatHandle = setTimeout(() => {
      this.beatHandle = null;
      const body = JSON.stringify({ t: "beat", now: Date.now() });
      for (const [username, set] of [...this.peers.entries()]) {
        for (const socket of [...set]) {
          try {
            socket.send(body);
          } catch {
            this.untrack(username, socket);
          }
        }
      }
      this.ensureBeat();
    }, BEAT_MS);
  }

  private isOnline(username: string): boolean {
    const row = this.user(username);
    if (row === null || row.presence === 0) return false;
    return this.socketsFor(username).length > 0;
  }

  private sendTo(username: string, payload: unknown, except?: WebSocket): void {
    const body = JSON.stringify(payload);
    for (const socket of this.socketsFor(username)) {
      if (socket === except) continue;
      try {
        socket.send(body);
      } catch (error: unknown) {
        console.warn("hub: send failed", String(error));
      }
    }
  }

  private announcePresence(username: string, online: boolean): void {
    const row = this.user(username);
    if (row === null || row.presence === 0) return;
    for (const peer of this.peersOf(username)) {
      this.sendTo(peer, { t: "presence", user: username, online });
    }
  }

  // ------------------------------------------------------------------ fetch

  override async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.headers.get("Upgrade") === "websocket") {
      return this.upgrade(url);
    }

    try {
      if (request.method === "POST" && path === "/v1/claim") return await this.claim(request);
      if (request.method === "POST" && path === "/v1/login") return await this.login(request);
      if (request.method === "POST" && path === "/v1/delete") return await this.deleteAccount(request);
      if (request.method === "POST" && path === "/v1/push/register") {
        return await this.registerDevice(request);
      }
      if (request.method === "POST" && path === "/v1/push/forget") {
        return await this.forgetDevice(request);
      }
      if (request.method === "POST" && path === "/v1/blob") return await this.putBlob(request);
      if (request.method === "GET" && path === "/v1/blob") return this.getBlob(url);
      if (request.method === "POST" && path === "/v1/file/begin") return await this.beginFile(request);
      if (request.method === "POST" && path === "/v1/file/chunk") return await this.putChunk(request);
      if (request.method === "POST" && path === "/v1/file/commit") return await this.commitFile(request);
      if (request.method === "GET" && path === "/v1/file") return await this.openFile(url);
      if (request.method === "GET" && path === "/v1/file/chunk") return this.getChunk(url);
      if (request.method === "GET" && path === "/v1/user") return this.lookup(url);
      if (request.method === "GET" && path === "/v1/search") return this.search(url);
      if (request.method === "GET" && path === "/v1/health") {
        const total = this.ctx.storage.sql
          .exec<{ n: number }>("SELECT COUNT(*) AS n FROM users")
          .toArray()[0]?.n ?? 0;
        // `files` reports where attachments rest, never a credential, so a
        // misconfigured bucket is visible without exposing anything.
        return Response.json({
          ok: true,
          accounts: total,
          sockets: this.socketCount(),
          files: this.r2() === null ? "do" : "r2",
          limit: this.r2() === null ? MAX_FILE_DO : MAX_FILE_R2,
          push: this.fcm() !== null,
          devices: this.ctx.storage.sql
            .exec<{ n: number }>("SELECT COUNT(*) AS n FROM devices")
            .toArray()[0]?.n ?? 0,
        });
      }
    } catch (error: unknown) {
      console.error("hub: request failed", path, String(error));
      return bad("server error", 500);
    }

    return bad("not found", 404);
  }

  private async claim(request: Request): Promise<Response> {
    const body = (await request.json()) as Record<string, unknown>;
    const username = clean(body.username);
    const authDigest = typeof body.authDigest === "string" ? body.authDigest : "";
    const publicKey = typeof body.publicKey === "string" ? body.publicKey : "";
    const sealedPrivateKey = typeof body.sealedPrivateKey === "string" ? body.sealedPrivateKey : "";

    if (!USERNAME_RULE.test(username)) return bad("invalid username");
    if (authDigest.length < 32 || publicKey.length < 32 || sealedPrivateKey.length < 32) {
      return bad("invalid key material");
    }
    if (this.user(username) !== null) {
      return Response.json({ ok: false, error: "taken" }, { status: 409 });
    }

    const now = Date.now();
    this.ctx.storage.sql.exec(
      `INSERT INTO users
         (username, auth_digest, public_key, sealed_private_key, created_at, last_seen)
       VALUES (?, ?, ?, ?, ?, ?)`,
      username,
      authDigest,
      publicKey,
      sealedPrivateKey,
      now,
      now,
    );
    console.log("hub: claimed", username);
    return Response.json({ ok: true, username, createdAt: now });
  }

  private async login(request: Request): Promise<Response> {
    const body = (await request.json()) as Record<string, unknown>;
    const authDigest = typeof body.authDigest === "string" ? body.authDigest : "";
    if (authDigest.length < 32) return bad("invalid key");

    const row = this.userByDigest(authDigest);
    if (row === null) return Response.json({ ok: false, error: "unknown" }, { status: 404 });

    return Response.json({
      ok: true,
      username: row.username,
      publicKey: row.public_key,
      sealedPrivateKey: row.sealed_private_key,
      createdAt: row.created_at,
      settings: this.settingsOf(row),
    });
  }

  private async deleteAccount(request: Request): Promise<Response> {
    const body = (await request.json()) as Record<string, unknown>;
    const authDigest = typeof body.authDigest === "string" ? body.authDigest : "";
    const row = this.userByDigest(authDigest);
    if (row === null) return Response.json({ ok: false, error: "unknown" }, { status: 404 });

    const peers = this.peersOf(row.username);
    this.ctx.storage.sql.exec(
      "DELETE FROM blobs WHERE owner = ? OR recipient = ?",
      row.username,
      row.username,
    );
    this.ctx.storage.sql.exec(
      "DELETE FROM messages WHERE sender = ? OR recipient = ?",
      row.username,
      row.username,
    );
    this.ctx.storage.sql.exec("DELETE FROM users WHERE username = ?", row.username);
    for (const socket of this.socketsFor(row.username)) {
      try {
        socket.send(JSON.stringify({ t: "gone" }));
        socket.close(1000, "account deleted");
      } catch {
        // socket already gone
      }
    }
    for (const peer of peers) {
      this.sendTo(peer, { t: "presence", user: row.username, online: false });
    }
    this.ctx.storage.sql.exec("DELETE FROM devices WHERE username = ?", row.username);
    console.log("hub: deleted", row.username);
    return Response.json({ ok: true });
  }

  // ---------------------------------------------------------------- push

  /** Firebase credentials, or null when the project has none configured. */
  private fcm(): ServiceAccount | null {
    return serviceAccount(this.env as unknown as Record<string, unknown>);
  }

  /**
   * Remembers a device so it can be woken when its owner is not connected.
   *
   * The token is Google's address for one installation. It is stored against
   * the account and nothing else — no history is attached to it, and it is
   * dropped the moment Google says it is dead or the phone signs out.
   */
  private async registerDevice(request: Request): Promise<Response> {
    const body = (await request.json()) as Record<string, unknown>;
    const authDigest = typeof body.authDigest === "string" ? body.authDigest : "";
    const token = typeof body.token === "string" ? body.token.trim() : "";

    const owner = this.userByDigest(authDigest);
    if (owner === null) return bad("unauthorized", 401);
    if (token.length < 20 || token.length > 512) return bad("invalid token");

    // A phone that changes hands must not keep waking the previous account.
    this.ctx.storage.sql.exec(
      "INSERT OR REPLACE INTO devices (token, username, updated_at) VALUES (?, ?, ?)",
      token,
      owner.username,
      Date.now(),
    );
    return Response.json({ ok: true, push: this.fcm() !== null });
  }

  /** Forgets a device — signing out must end the wake-ups immediately. */
  private async forgetDevice(request: Request): Promise<Response> {
    const body = (await request.json()) as Record<string, unknown>;
    const token = typeof body.token === "string" ? body.token.trim() : "";
    if (token.length === 0) return bad("invalid token");
    this.ctx.storage.sql.exec("DELETE FROM devices WHERE token = ?", token);
    return Response.json({ ok: true });
  }

  /**
   * Nudges every device of an account that is not currently connected.
   *
   * Nothing about the message travels in the push. The phone wakes, opens its
   * own socket, and finds out what arrived by decrypting it itself.
   */
  private async nudge(username: string, reason: string): Promise<void> {
    const account = this.fcm();
    if (account === null) return;

    const tokens = this.ctx.storage.sql
      .exec<{ token: string }>("SELECT token FROM devices WHERE username = ?", username)
      .toArray()
      .map((row) => row.token);
    if (tokens.length === 0) return;

    const results = await Promise.all(
      tokens.map(async (token) => ({ token, outcome: await wakeDevice(account, token, reason) })),
    );
    for (const result of results) {
      if (result.outcome !== "gone") continue;
      this.ctx.storage.sql.exec("DELETE FROM devices WHERE token = ?", result.token);
    }
  }

  // -------------------------------------------------------------- attachments

  /**
   * Stores one encrypted attachment. The body is opaque base64: the hub cannot
   * tell a photo from noise, and never receives the key that decrypts it.
   */
  private async putBlob(request: Request): Promise<Response> {
    const body = (await request.json()) as Record<string, unknown>;
    const authDigest = typeof body.authDigest === "string" ? body.authDigest : "";
    const id = typeof body.id === "string" ? body.id : "";
    const to = clean(body.to);
    const cipher = typeof body.cipher === "string" ? body.cipher : "";

    const owner = this.userByDigest(authDigest);
    if (owner === null) return bad("unauthorized", 401);
    if (!BLOB_ID_RULE.test(id)) return bad("invalid id");
    if (cipher.length === 0) return bad("empty attachment");
    if (cipher.length > MAX_BLOB) return bad("attachment too large", 413);
    if (this.user(to) === null) return bad("unknown recipient", 404);
    if (this.isBlocked(to, owner.username) || this.isBlocked(owner.username, to)) {
      return bad("blocked", 403);
    }

    this.ctx.storage.sql.exec(
      `INSERT OR REPLACE INTO blobs (id, owner, recipient, cipher, created_at)
       VALUES (?, ?, ?, ?, ?)`,
      id,
      owner.username,
      to,
      cipher,
      Date.now(),
    );
    return Response.json({ ok: true, id });
  }

  private getBlob(url: URL): Response {
    const username = clean(url.searchParams.get("u"));
    const authDigest = url.searchParams.get("a") ?? "";
    const id = url.searchParams.get("id") ?? "";

    const row = this.user(username);
    if (row === null || row.auth_digest !== authDigest) return bad("unauthorized", 401);

    const blob = this.ctx.storage.sql
      .exec<BlobRow>("SELECT * FROM blobs WHERE id = ?", id)
      .toArray()[0];
    if (blob === undefined) return bad("gone", 404);
    if (blob.owner !== username && blob.recipient !== username) return bad("forbidden", 403);

    return Response.json({ ok: true, cipher: blob.cipher });
  }

  private dropBlobs(ids: string[]): void {
    for (const id of ids) {
      if (id.length === 0) continue;
      this.ctx.storage.sql.exec("DELETE FROM blobs WHERE id = ?", id);
      this.dropFile(id);
    }
  }

  // --------------------------------------------------------------- files

  /** R2 credentials, or null when the project stores files in the hub itself. */
  private r2(): R2Config | null {
    return r2Config(this.env as unknown as Record<string, unknown>);
  }

  /**
   * Opens an upload.
   *
   * The hub records who is allowed to fetch the file and how big it claims to
   * be, then hands back either a short-lived R2 link the client writes to
   * directly, or the chunk size to post here. Either way the bytes that arrive
   * are ciphertext sealed under a key the hub never receives.
   */
  private async beginFile(request: Request): Promise<Response> {
    const body = (await request.json()) as Record<string, unknown>;
    const authDigest = typeof body.authDigest === "string" ? body.authDigest : "";
    const id = typeof body.id === "string" ? body.id : "";
    const to = clean(body.to);
    const size = typeof body.size === "number" ? Math.floor(body.size) : 0;

    const owner = this.userByDigest(authDigest);
    if (owner === null) return bad("unauthorized", 401);
    if (!BLOB_ID_RULE.test(id)) return bad("invalid id");
    if (this.user(to) === null) return bad("unknown recipient", 404);
    if (this.isBlocked(to, owner.username) || this.isBlocked(owner.username, to)) {
      return bad("blocked", 403);
    }

    const config = this.r2();
    const limit = config === null ? MAX_FILE_DO : MAX_FILE_R2;
    if (size <= 0) return bad("empty file");
    if (size > limit) {
      return Response.json({ ok: false, error: "too large", limit }, { status: 413 });
    }

    this.ctx.storage.sql.exec(
      `INSERT OR REPLACE INTO files (id, owner, recipient, store, size, chunks, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      id,
      owner.username,
      to,
      config === null ? "do" : "r2",
      size,
      0,
      Date.now(),
    );

    if (config !== null) {
      const put = await presign(config, "PUT", `files/${id}`, 900);
      return Response.json({ ok: true, mode: "r2", put, limit });
    }
    return Response.json({ ok: true, mode: "do", chunk: MAX_CHUNK, limit });
  }

  /** Accepts one chunk of a file being stored in the hub's own storage. */
  private async putChunk(request: Request): Promise<Response> {
    const body = (await request.json()) as Record<string, unknown>;
    const authDigest = typeof body.authDigest === "string" ? body.authDigest : "";
    const id = typeof body.id === "string" ? body.id : "";
    const seq = typeof body.seq === "number" ? Math.floor(body.seq) : -1;
    const cipher = typeof body.cipher === "string" ? body.cipher : "";

    const owner = this.userByDigest(authDigest);
    if (owner === null) return bad("unauthorized", 401);
    if (seq < 0 || seq > 64) return bad("invalid chunk");
    if (cipher.length === 0) return bad("empty chunk");
    if (cipher.length > MAX_CHUNK * 2) return bad("chunk too large", 413);

    const row = this.fileRow(id);
    if (row === null) return bad("gone", 404);
    if (row.owner !== owner.username) return bad("forbidden", 403);

    this.ctx.storage.sql.exec(
      "INSERT OR REPLACE INTO file_chunks (file_id, seq, cipher) VALUES (?, ?, ?)",
      id,
      seq,
      cipher,
    );
    return Response.json({ ok: true });
  }

  /** Marks an upload complete, so a half-sent file is never served. */
  private async commitFile(request: Request): Promise<Response> {
    const body = (await request.json()) as Record<string, unknown>;
    const authDigest = typeof body.authDigest === "string" ? body.authDigest : "";
    const id = typeof body.id === "string" ? body.id : "";
    const chunks = typeof body.chunks === "number" ? Math.floor(body.chunks) : 0;

    const owner = this.userByDigest(authDigest);
    if (owner === null) return bad("unauthorized", 401);
    const row = this.fileRow(id);
    if (row === null) return bad("gone", 404);
    if (row.owner !== owner.username) return bad("forbidden", 403);

    this.ctx.storage.sql.exec(
      "UPDATE files SET chunks = ? WHERE id = ?",
      Math.max(chunks, row.store === "r2" ? 1 : chunks),
      id,
    );
    return Response.json({ ok: true });
  }

  /** Tells a recipient where to read a file from. */
  private async openFile(url: URL): Promise<Response> {
    const username = clean(url.searchParams.get("u"));
    const authDigest = url.searchParams.get("a") ?? "";
    const id = url.searchParams.get("id") ?? "";

    const user = this.user(username);
    if (user === null || user.auth_digest !== authDigest) return bad("unauthorized", 401);

    const row = this.fileRow(id);
    if (row === null) return bad("gone", 404);
    if (row.owner !== username && row.recipient !== username) return bad("forbidden", 403);
    if (row.chunks === 0) return bad("incomplete", 409);

    if (row.store === "r2") {
      const config = this.r2();
      if (config === null) return bad("gone", 404);
      const get = await presign(config, "GET", `files/${id}`, 900);
      return Response.json({ ok: true, mode: "r2", get, size: row.size });
    }
    return Response.json({ ok: true, mode: "do", chunks: row.chunks, size: row.size });
  }

  private getChunk(url: URL): Response {
    const username = clean(url.searchParams.get("u"));
    const authDigest = url.searchParams.get("a") ?? "";
    const id = url.searchParams.get("id") ?? "";
    const seq = Number(url.searchParams.get("seq") ?? "-1");

    const user = this.user(username);
    if (user === null || user.auth_digest !== authDigest) return bad("unauthorized", 401);

    const row = this.fileRow(id);
    if (row === null) return bad("gone", 404);
    if (row.owner !== username && row.recipient !== username) return bad("forbidden", 403);

    const chunk = this.ctx.storage.sql
      .exec<{ cipher: string }>(
        "SELECT cipher FROM file_chunks WHERE file_id = ? AND seq = ?",
        id,
        seq,
      )
      .toArray()[0];
    if (chunk === undefined) return bad("gone", 404);
    return Response.json({ ok: true, cipher: chunk.cipher });
  }

  private fileRow(id: string): FileRow | null {
    if (!BLOB_ID_RULE.test(id)) return null;
    return (
      this.ctx.storage.sql.exec<FileRow>("SELECT * FROM files WHERE id = ?", id).toArray()[0] ??
      null
    );
  }

  /** Removes a file's bookkeeping and its bytes, wherever they live. */
  private dropFile(id: string): void {
    const row = this.fileRow(id);
    if (row === null) return;
    this.ctx.storage.sql.exec("DELETE FROM file_chunks WHERE file_id = ?", id);
    this.ctx.storage.sql.exec("DELETE FROM files WHERE id = ?", id);
    if (row.store !== "r2") return;
    const config = this.r2();
    if (config === null) return;
    this.ctx.waitUntil(removeObject(config, `files/${id}`));
  }

  private lookup(url: URL): Response {
    const username = clean(url.searchParams.get("u"));
    const row = this.user(username);
    if (row === null) return Response.json({ ok: false, error: "unknown" }, { status: 404 });
    return Response.json({ ok: true, user: this.publicView(row) });
  }

  private search(url: URL): Response {
    const query = clean(url.searchParams.get("q"));
    if (query.length === 0) return Response.json({ ok: true, users: [] });
    const rows = this.ctx.storage.sql
      .exec<UserRow>(
        `SELECT * FROM users
         WHERE username = ? OR username LIKE ?
         ORDER BY CASE WHEN username = ? THEN 0 ELSE 1 END, username
         LIMIT 25`,
        query,
        `${query}%`,
        query,
      )
      .toArray();
    return Response.json({ ok: true, users: rows.map((row) => this.publicView(row)) });
  }

  private publicView(row: UserRow): Record<string, unknown> {
    return {
      username: row.username,
      publicKey: row.public_key,
      createdAt: row.created_at,
      online: row.presence === 1 && this.socketsFor(row.username).length > 0,
      acceptsStrangers: row.strangers === 1,
    };
  }

  private settingsOf(row: UserRow): Record<string, boolean> {
    return {
      receipts: row.receipts === 1,
      typing: row.typing_on === 1,
      presence: row.presence === 1,
      strangers: row.strangers === 1,
    };
  }

  // -------------------------------------------------------------- websocket

  private upgrade(url: URL): Response {
    const username = clean(url.searchParams.get("u"));
    const authDigest = url.searchParams.get("a") ?? "";
    const row = this.user(username);
    if (row === null || row.auth_digest !== authDigest) {
      return new Response("unauthorized", { status: 401 });
    }

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    server.accept();
    this.track(username, server);
    server.addEventListener("message", (event: MessageEvent) => {
      this.onFrame(server, username, event.data as string | ArrayBuffer);
    });
    server.addEventListener("close", () => this.onGone(username, server));
    server.addEventListener("error", () => this.onGone(username, server));
    this.ensureBeat();
    this.touch(username);

    server.send(
      JSON.stringify({
        t: "ready",
        username,
        settings: this.settingsOf(row),
        publicKey: row.public_key,
        blocked: this.blocksOf(username),
        now: Date.now(),
      }),
    );
    this.announcePresence(username, true);

    return new Response(null, { status: 101, webSocket: client });
  }

  private onFrame(ws: WebSocket, me: string, raw: string | ArrayBuffer): void {
    if (me.length === 0) return;
    this.ensureBeat();

    let payload: Record<string, unknown>;
    try {
      payload = JSON.parse(typeof raw === "string" ? raw : new TextDecoder().decode(raw));
    } catch {
      return;
    }

    const kind = typeof payload.t === "string" ? payload.t : "";
    try {
      switch (kind) {
        case "ping":
          ws.send(JSON.stringify({ t: "pong", now: Date.now() }));
          return;
        case "sync":
          this.handleSync(ws, me, Number(payload.since ?? 0));
          return;
        case "send":
          this.handleSend(ws, me, payload);
          return;
        case "read":
          this.handleRead(me, clean(payload.peer));
          return;
        case "typing":
          this.handleTyping(me, clean(payload.to), payload.on === true);
          return;
        case "settings":
          this.handleSettings(ws, me, payload);
          return;
        case "watch":
          this.handleWatch(ws, payload.peers);
          return;
        case "react":
          this.handleReact(me, payload);
          return;
        case "unsend":
          this.handleUnsend(me, typeof payload.id === "string" ? payload.id : "");
          return;
        case "block":
          this.handleBlock(ws, me, clean(payload.user), payload.on === true);
          return;
        default:
          return;
      }
    } catch (error: unknown) {
      console.error("hub: ws handler failed", kind, String(error));
    }
  }

  private onGone(me: string, ws: WebSocket): void {
    if (!this.socketsFor(me).includes(ws)) return;
    this.untrack(me, ws);
    this.touch(me);
    if (this.socketsFor(me).length === 0) this.announcePresence(me, false);
    if (this.socketCount() === 0 && this.beatHandle !== null) {
      clearTimeout(this.beatHandle);
      this.beatHandle = null;
    }
  }

  private handleSync(ws: WebSocket, me: string, since: number): void {
    this.purgeExpired();
    const cursor = Number.isFinite(since) && since > 0 ? Math.floor(since) : 0;
    const rows = this.ctx.storage.sql
      .exec<MessageRow>(
        `SELECT * FROM messages
         WHERE (sender = ? OR recipient = ?) AND seq > ?
         ORDER BY seq ASC LIMIT ?`,
        me,
        me,
        cursor,
        HISTORY_PAGE,
      )
      .toArray();

    ws.send(
      JSON.stringify({
        t: "history",
        messages: rows.map((row) => this.wireMessage(row)),
        more: rows.length === HISTORY_PAGE,
      }),
    );

    this.markDelivered(me);
    const peers = this.peersOf(me);
    const presence: Record<string, boolean> = {};
    for (const peer of peers) presence[peer] = this.isOnline(peer);
    ws.send(JSON.stringify({ t: "presenceSnapshot", online: presence }));
  }

  /** Flags every pending inbound message as delivered and tells the senders. */
  private markDelivered(me: string): void {
    const pending = this.ctx.storage.sql
      .exec<MessageRow>(
        "SELECT * FROM messages WHERE recipient = ? AND state = 'sent'",
        me,
      )
      .toArray();
    if (pending.length === 0) return;

    const now = Date.now();
    this.ctx.storage.sql.exec(
      "UPDATE messages SET state = 'delivered', delivered_at = ? WHERE recipient = ? AND state = 'sent'",
      now,
      me,
    );
    for (const row of pending) {
      this.sendTo(row.sender, {
        t: "ack",
        id: row.id,
        state: "delivered",
        seq: row.seq,
        deliveredAt: now,
      });
    }
  }

  private wireMessage(row: MessageRow): Record<string, unknown> {
    return {
      id: row.id,
      from: row.sender,
      to: row.recipient,
      cipher: row.cipher,
      at: row.created_at,
      seq: row.seq,
      state: row.state,
      reply: row.reply_to.length > 0 ? row.reply_to : null,
      expiresAt: row.expires_at,
      reactions: this.parseReactions(row.reactions),
      deliveredAt: row.delivered_at,
      readAt: row.read_at,
    };
  }

  private parseReactions(raw: string): Record<string, string> {
    try {
      const parsed = JSON.parse(raw.length > 0 ? raw : "{}") as Record<string, unknown>;
      const out: Record<string, string> = {};
      for (const [user, value] of Object.entries(parsed)) {
        if (typeof value === "string" && value.length > 0) out[user] = value;
      }
      return out;
    } catch {
      return {};
    }
  }

  private handleSend(ws: WebSocket, me: string, payload: Record<string, unknown>): void {
    const id = typeof payload.id === "string" ? payload.id.slice(0, 64) : "";
    const to = clean(payload.to);
    const cipher = typeof payload.cipher === "string" ? payload.cipher : "";
    const at = Number(payload.at ?? Date.now());
    const replyTo = typeof payload.reply === "string" ? payload.reply.slice(0, 64) : "";
    const blobId = typeof payload.blob === "string" ? payload.blob.slice(0, 80) : "";
    const ttlRaw = Number(payload.ttl ?? 0);
    const ttl = Number.isFinite(ttlRaw) && ttlRaw > 0
      ? Math.min(Math.floor(ttlRaw), MAX_TTL_SECONDS)
      : 0;

    if (id.length === 0 || to.length === 0 || cipher.length === 0) {
      ws.send(JSON.stringify({ t: "error", code: "bad_message", id }));
      return;
    }
    if (cipher.length > MAX_CIPHER) {
      ws.send(JSON.stringify({ t: "error", code: "too_long", id }));
      return;
    }
    const recipient = this.user(to);
    if (recipient === null) {
      ws.send(JSON.stringify({ t: "error", code: "unknown_user", id }));
      return;
    }
    if (this.isBlocked(to, me) || this.isBlocked(me, to)) {
      ws.send(JSON.stringify({ t: "error", code: "blocked", id }));
      return;
    }
    // Writing to yourself is a notebook, not a stranger knocking, so the
    // "only people I have messaged" rule has nothing to say about it.
    if (to !== me && recipient.strangers === 0 && !this.hasWrittenTo(to, me)) {
      ws.send(JSON.stringify({ t: "error", code: "not_accepting", id }));
      return;
    }

    const existing = this.ctx.storage.sql
      .exec<MessageRow>("SELECT * FROM messages WHERE id = ?", id)
      .toArray()[0];
    if (existing !== undefined) {
      ws.send(
        JSON.stringify({
          t: "ack",
          id,
          state: existing.state,
          seq: existing.seq,
          expiresAt: existing.expires_at,
          deliveredAt: existing.delivered_at,
          readAt: existing.read_at,
        }),
      );
      return;
    }

    const online = this.socketsFor(to).length > 0;
    const state = online ? "delivered" : "sent";
    const createdAt = Number.isFinite(at) ? at : Date.now();
    const expiresAt = ttl > 0 ? createdAt + ttl * 1000 : 0;

    const deliveredAt = online ? Date.now() : 0;
    this.ctx.storage.sql.exec(
      `INSERT INTO messages
         (id, sender, recipient, cipher, created_at, state, reply_to, expires_at, reactions, blob_id, delivered_at, read_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, '{}', ?, ?, 0)`,
      id,
      me,
      to,
      cipher,
      createdAt,
      state,
      replyTo,
      expiresAt,
      blobId,
      deliveredAt,
    );
    const seq = this.ctx.storage.sql
      .exec<{ seq: number }>("SELECT seq FROM messages WHERE id = ?", id)
      .toArray()[0]?.seq ?? 0;

    const envelope = {
      t: "msg",
      id,
      from: me,
      to,
      cipher,
      at: createdAt,
      seq,
      reply: replyTo.length > 0 ? replyTo : null,
      expiresAt,
    };
    this.sendTo(to, envelope);
    this.sendTo(me, { t: "ack", id, state, seq, expiresAt, deliveredAt });
    // Nobody is listening, so the phone has to be woken. The push says only
    // that something arrived; the message itself stays here, sealed.
    if (!online) this.ctx.waitUntil(this.nudge(to, "message"));
    if (expiresAt > 0) void this.scheduleBurn();
  }

  /** Encrypted emoji reaction; an empty cipher removes the reaction. */
  private handleReact(me: string, payload: Record<string, unknown>): void {
    const id = typeof payload.id === "string" ? payload.id : "";
    const cipher = typeof payload.cipher === "string" ? payload.cipher.slice(0, 512) : "";
    if (id.length === 0) return;

    const row = this.ctx.storage.sql
      .exec<MessageRow>("SELECT * FROM messages WHERE id = ?", id)
      .toArray()[0];
    if (row === undefined) return;
    if (row.sender !== me && row.recipient !== me) return;

    const reactions = this.parseReactions(row.reactions);
    if (cipher.length === 0) delete reactions[me];
    else reactions[me] = cipher;

    this.ctx.storage.sql.exec(
      "UPDATE messages SET reactions = ? WHERE id = ?",
      JSON.stringify(reactions),
      id,
    );

    const event = { t: "reaction", id, from: me, cipher };
    this.sendTo(row.sender, event);
    if (row.recipient !== row.sender) this.sendTo(row.recipient, event);
  }

  /** Retracts a message for everyone. Only the sender may do this. */
  private handleUnsend(me: string, id: string): void {
    if (id.length === 0) return;
    const row = this.ctx.storage.sql
      .exec<MessageRow>("SELECT * FROM messages WHERE id = ?", id)
      .toArray()[0];
    if (row === undefined || row.sender !== me) return;

    this.dropBlobs([row.blob_id]);
    this.ctx.storage.sql.exec("DELETE FROM messages WHERE id = ?", id);
    const event = { t: "unsend", id, from: me };
    this.sendTo(row.sender, event);
    if (row.recipient !== row.sender) this.sendTo(row.recipient, event);
  }

  private handleBlock(ws: WebSocket, me: string, user: string, on: boolean): void {
    if (user.length === 0 || user === me) return;
    if (on) {
      this.ctx.storage.sql.exec(
        "INSERT OR REPLACE INTO blocks (blocker, blocked, created_at) VALUES (?, ?, ?)",
        me,
        user,
        Date.now(),
      );
      this.sendTo(user, { t: "presence", user: me, online: false });
    } else {
      this.ctx.storage.sql.exec(
        "DELETE FROM blocks WHERE blocker = ? AND blocked = ?",
        me,
        user,
      );
      this.sendTo(user, { t: "presence", user: me, online: this.isOnline(me) });
    }
    ws.send(JSON.stringify({ t: "blocked", users: this.blocksOf(me) }));
  }

  // ------------------------------------------------------------------- burn

  /** Deletes every message whose timer has run out and tells both sides. */
  private purgeExpired(): void {
    const now = Date.now();
    const rows = this.ctx.storage.sql
      .exec<MessageRow>(
        "SELECT * FROM messages WHERE expires_at > 0 AND expires_at <= ?",
        now,
      )
      .toArray();
    if (rows.length === 0) return;

    this.dropBlobs(rows.map((row) => row.blob_id));
    this.ctx.storage.sql.exec(
      "DELETE FROM messages WHERE expires_at > 0 AND expires_at <= ?",
      now,
    );

    const byUser = new Map<string, string[]>();
    for (const row of rows) {
      for (const user of [row.sender, row.recipient]) {
        const list = byUser.get(user) ?? [];
        list.push(row.id);
        byUser.set(user, list);
      }
    }
    for (const [user, ids] of byUser) {
      this.sendTo(user, { t: "burn", ids });
    }
    console.log("hub: burned", rows.length);
  }

  private async scheduleBurn(): Promise<void> {
    const next = this.ctx.storage.sql
      .exec<{ next: number | null }>(
        "SELECT MIN(expires_at) AS next FROM messages WHERE expires_at > 0",
      )
      .toArray()[0]?.next ?? null;
    if (next === null || next <= 0) return;
    await this.ctx.storage.setAlarm(Math.max(next, Date.now() + 1_000));
  }

  override async alarm(): Promise<void> {
    this.purgeExpired();
    await this.scheduleBurn();
  }

  private handleRead(me: string, peer: string): void {
    if (peer.length === 0) return;
    const rows = this.ctx.storage.sql
      .exec<MessageRow>(
        "SELECT * FROM messages WHERE recipient = ? AND sender = ? AND state != 'read'",
        me,
        peer,
      )
      .toArray();
    if (rows.length === 0) return;

    const now = Date.now();
    this.ctx.storage.sql.exec(
      `UPDATE messages
          SET state = 'read',
              read_at = ?,
              delivered_at = CASE WHEN delivered_at = 0 THEN ? ELSE delivered_at END
        WHERE recipient = ? AND sender = ? AND state != 'read'`,
      now,
      now,
      me,
      peer,
    );

    const row = this.user(me);
    if (row === null || row.receipts === 0) return;
    for (const message of rows) {
      this.sendTo(peer, {
        t: "ack",
        id: message.id,
        state: "read",
        seq: message.seq,
        deliveredAt: message.delivered_at > 0 ? message.delivered_at : now,
        readAt: now,
      });
    }
  }

  private handleTyping(me: string, to: string, on: boolean): void {
    if (to.length === 0) return;
    const row = this.user(me);
    if (row === null || row.typing_on === 0) return;
    if (this.isBlocked(to, me) || this.isBlocked(me, to)) return;
    this.sendTo(to, { t: "typing", from: me, on });
  }

  private handleSettings(ws: WebSocket, me: string, payload: Record<string, unknown>): void {
    const row = this.user(me);
    if (row === null) return;
    const flag = (key: string, current: number): number =>
      typeof payload[key] === "boolean" ? (payload[key] === true ? 1 : 0) : current;

    const receipts = flag("receipts", row.receipts);
    const typingOn = flag("typing", row.typing_on);
    const presence = flag("presence", row.presence);
    const strangers = flag("strangers", row.strangers);

    this.ctx.storage.sql.exec(
      "UPDATE users SET receipts = ?, typing_on = ?, presence = ?, strangers = ? WHERE username = ?",
      receipts,
      typingOn,
      presence,
      strangers,
      me,
    );

    if (presence !== row.presence) {
      const online = presence === 1 && this.socketsFor(me).length > 0;
      for (const peer of this.peersOf(me)) {
        this.sendTo(peer, { t: "presence", user: me, online });
      }
    }
    ws.send(
      JSON.stringify({
        t: "settings",
        receipts: receipts === 1,
        typing: typingOn === 1,
        presence: presence === 1,
        strangers: strangers === 1,
      }),
    );
  }

  private handleWatch(ws: WebSocket, peers: unknown): void {
    if (!Array.isArray(peers)) return;
    const online: Record<string, boolean> = {};
    for (const raw of peers.slice(0, 200)) {
      const peer = clean(raw);
      if (peer.length === 0) continue;
      online[peer] = this.isOnline(peer);
    }
    ws.send(JSON.stringify({ t: "presenceSnapshot", online }));
  }
}
