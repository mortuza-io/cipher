/**
 * Cloudflare R2 access over its S3-compatible API.
 *
 * The Rork Worker runtime exposes only the `env.DO` service binding — there is
 * no R2 binding to declare — so R2 is reached the portable way: presigned
 * SigV4 URLs the client uses directly. That is the better shape anyway. File
 * bytes never pass through the Worker, so an upload is not bounded by Worker
 * request size or CPU limits, and the hub only ever mints a short-lived URL to
 * an object whose contents are already ciphertext it cannot read.
 */

export type R2Config = {
  accountId: string;
  accessKeyId: string;
  secretAccessKey: string;
  bucket: string;
};

/**
 * Reads R2 credentials from the Worker env.
 *
 * Returns null when the project has not configured R2, which is what puts the
 * hub into its built-in chunked-storage mode instead.
 */
export function r2Config(env: Record<string, unknown>): R2Config | null {
  const accountId = str(env.R2_ACCOUNT_ID);
  const accessKeyId = str(env.R2_ACCESS_KEY_ID);
  const secretAccessKey = str(env.R2_SECRET_ACCESS_KEY);
  const bucket = str(env.R2_BUCKET);
  if (!accountId || !accessKeyId || !secretAccessKey || !bucket) return null;
  return { accountId, accessKeyId, secretAccessKey, bucket };
}

function str(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

const ENCODER = new TextEncoder();

async function hmac(key: ArrayBuffer | Uint8Array, data: string): Promise<ArrayBuffer> {
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    key as BufferSource,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  return crypto.subtle.sign("HMAC", cryptoKey, ENCODER.encode(data));
}

async function sha256Hex(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", ENCODER.encode(value));
  return hex(digest);
}

function hex(buffer: ArrayBuffer): string {
  return [...new Uint8Array(buffer)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** RFC 3986 encoding — S3 signing is stricter than `encodeURIComponent`. */
function encodeRfc3986(value: string): string {
  return encodeURIComponent(value).replace(
    /[!'()*]/g,
    (c) => `%${c.charCodeAt(0).toString(16).toUpperCase()}`,
  );
}

/**
 * Mints a presigned URL for one object.
 *
 * @param method the verb the URL is valid for — a PUT URL cannot be replayed
 *   as a GET, so an upload link never becomes a download link.
 * @param expiresIn seconds the link stays valid; kept short because a link is
 *   handed out per request rather than stored anywhere.
 */
export async function presign(
  config: R2Config,
  method: "PUT" | "GET" | "DELETE",
  key: string,
  expiresIn = 900,
): Promise<string> {
  const host = `${config.accountId}.r2.cloudflarestorage.com`;
  const path = `/${config.bucket}/${key.split("/").map(encodeRfc3986).join("/")}`;

  const now = new Date();
  const stamp = now.toISOString().replace(/[:-]|\.\d{3}/g, "");
  const day = stamp.slice(0, 8);
  const scope = `${day}/auto/s3/aws4_request`;

  const query = new URLSearchParams({
    "X-Amz-Algorithm": "AWS4-HMAC-SHA256",
    "X-Amz-Credential": `${config.accessKeyId}/${scope}`,
    "X-Amz-Date": stamp,
    "X-Amz-Expires": String(expiresIn),
    "X-Amz-SignedHeaders": "host",
  });
  // S3 requires the canonical query string sorted by key.
  const canonicalQuery = [...query.entries()]
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    .map(([k, v]) => `${encodeRfc3986(k)}=${encodeRfc3986(v)}`)
    .join("&");

  const canonicalRequest = [
    method,
    path,
    canonicalQuery,
    `host:${host}\n`,
    "host",
    "UNSIGNED-PAYLOAD",
  ].join("\n");

  const stringToSign = [
    "AWS4-HMAC-SHA256",
    stamp,
    scope,
    await sha256Hex(canonicalRequest),
  ].join("\n");

  let signing: ArrayBuffer | Uint8Array = ENCODER.encode(`AWS4${config.secretAccessKey}`);
  for (const part of [day, "auto", "s3", "aws4_request"]) {
    signing = await hmac(signing, part);
  }
  const signature = hex(await hmac(signing, stringToSign));

  return `https://${host}${path}?${canonicalQuery}&X-Amz-Signature=${signature}`;
}

/** Deletes one object, best effort — a failed cleanup must never fail a request. */
export async function removeObject(config: R2Config, key: string): Promise<void> {
  try {
    const url = await presign(config, "DELETE", key, 120);
    await fetch(url, { method: "DELETE" });
  } catch (error: unknown) {
    console.warn("r2: delete failed", key, String(error));
  }
}
