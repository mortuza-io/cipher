/**
 * Contentless push wake-ups through Firebase Cloud Messaging.
 *
 * The push carries no message, no sender and no conversation — only the word
 * "wake". A phone that receives one opens its own encrypted socket, pulls the
 * sealed message and decrypts it locally, then writes its own notification.
 * Google therefore learns that a device was nudged and when, and nothing else.
 *
 * FCM's v1 API wants a short-lived OAuth token minted from a service account
 * key. The Admin SDK cannot run on Workers, so the JWT is signed here with
 * WebCrypto and exchanged for an access token that is cached until it expires.
 */

export type ServiceAccount = {
  clientEmail: string;
  privateKey: string;
  projectId: string;
  tokenUri: string;
};

/** How a device token fared, so dead ones can be dropped from the hub. */
export type PushOutcome = "sent" | "gone" | "failed";

const SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const ENCODER = new TextEncoder();

/**
 * Reads the service account from the Worker env.
 *
 * Returns null when the project has no Firebase credentials, which is what
 * leaves the hub in socket-only mode instead of failing a send.
 */
export function serviceAccount(env: Record<string, unknown>): ServiceAccount | null {
  const raw = typeof env.FCM_SERVICE_ACCOUNT === "string" ? env.FCM_SERVICE_ACCOUNT.trim() : "";
  if (raw.length === 0) return null;
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const clientEmail = typeof parsed.client_email === "string" ? parsed.client_email : "";
    const privateKey = typeof parsed.private_key === "string" ? parsed.private_key : "";
    const projectId = typeof parsed.project_id === "string" ? parsed.project_id : "";
    if (!clientEmail || !privateKey || !projectId) return null;
    return {
      clientEmail,
      privateKey,
      projectId,
      tokenUri: typeof parsed.token_uri === "string"
        ? parsed.token_uri
        : "https://oauth2.googleapis.com/token",
    };
  } catch (error: unknown) {
    console.warn("push: service account is not valid JSON", String(error));
    return null;
  }
}

function base64Url(input: string | Uint8Array): string {
  const bytes = typeof input === "string" ? ENCODER.encode(input) : input;
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Turns the PEM in the key file into the DER bytes WebCrypto imports. */
function pemToDer(pem: string): ArrayBuffer {
  const body = pem
    .replace(/-----BEGIN [^-]+-----/, "")
    .replace(/-----END [^-]+-----/, "")
    .replace(/\s+/g, "");
  const binary = atob(body);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}

/**
 * One cached access token per isolate.
 *
 * Minting costs a signature and a round trip to Google, so a burst of messages
 * to an offline phone must not pay for it more than once an hour.
 */
let cachedToken: { value: string; expiresAt: number } | null = null;

async function accessToken(account: ServiceAccount): Promise<string | null> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken !== null && cachedToken.expiresAt > now + 60) return cachedToken.value;

  try {
    const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
    const claims = base64Url(
      JSON.stringify({
        iss: account.clientEmail,
        scope: SCOPE,
        aud: account.tokenUri,
        iat: now,
        exp: now + 3600,
      }),
    );
    const signingInput = `${header}.${claims}`;

    const key = await crypto.subtle.importKey(
      "pkcs8",
      pemToDer(account.privateKey),
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["sign"],
    );
    const signature = await crypto.subtle.sign(
      "RSASSA-PKCS1-v1_5",
      key,
      ENCODER.encode(signingInput),
    );
    const assertion = `${signingInput}.${base64Url(new Uint8Array(signature))}`;

    const response = await fetch(account.tokenUri, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
        assertion,
      }),
    });
    if (!response.ok) {
      console.warn("push: token exchange refused", response.status);
      return null;
    }
    const payload = (await response.json()) as { access_token?: string; expires_in?: number };
    if (typeof payload.access_token !== "string") return null;
    cachedToken = {
      value: payload.access_token,
      expiresAt: now + (typeof payload.expires_in === "number" ? payload.expires_in : 3600),
    };
    return cachedToken.value;
  } catch (error: unknown) {
    console.warn("push: could not mint access token", String(error));
    return null;
  }
}

/**
 * Wakes one device.
 *
 * Data-only on purpose: a `notification` block would be drawn by Android
 * itself, which means its words would have to travel through Google. This
 * payload says nothing, so the phone has to decrypt to know what to show.
 *
 * @param reason a coarse label ("message") the client uses to decide what to
 *   do once awake — never a sender, a thread or any content.
 */
export async function wakeDevice(
  account: ServiceAccount,
  deviceToken: string,
  reason: string,
): Promise<PushOutcome> {
  const bearer = await accessToken(account);
  if (bearer === null) return "failed";

  try {
    const response = await fetch(
      `https://fcm.googleapis.com/v1/projects/${account.projectId}/messages:send`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${bearer}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token: deviceToken,
            data: { t: "wake", r: reason },
            android: {
              // High priority is what lets a data message through Doze; the
              // collapse key means a backlog wakes the phone once, not ten times.
              priority: "HIGH",
              collapse_key: "cipher.wake",
              ttl: "600s",
            },
          },
        }),
      },
    );

    if (response.ok) return "sent";
    // 404 UNREGISTERED and 400 INVALID_ARGUMENT both mean this token will never
    // work again — the app was uninstalled or the token was rotated.
    if (response.status === 404 || response.status === 400) return "gone";
    if (response.status === 401 || response.status === 403) {
      // A stale token in cache is the likeliest cause; the next send re-mints.
      cachedToken = null;
    }
    console.warn("push: send refused", response.status);
    return "failed";
  } catch (error: unknown) {
    console.warn("push: send failed", String(error));
    return "failed";
  }
}
