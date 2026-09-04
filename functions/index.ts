export { Hub } from "./hub";

type Env = { DO: Fetcher };

const HUB_CLASS = "Hub";
const HUB_ID = "global";

/**
 * Cipher entrypoint. Every route is forwarded to the single `Hub` Durable
 * Object instance, which owns the username registry, the WebSocket peers and
 * the encrypted message store.
 */
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type",
        },
      });
    }

    if (url.pathname === "/" ) {
      return Response.json({ ok: true, service: "cipher-hub" });
    }

    if (!url.pathname.startsWith("/v1/")) {
      return new Response("not found", { status: 404 });
    }

    const wrapped = new Request(request.url, request);
    wrapped.headers.set("X-Rork-DO-Class", HUB_CLASS);
    wrapped.headers.set("X-Rork-DO-Id", HUB_ID);
    return env.DO.fetch(wrapped);
  },
} satisfies ExportedHandler<Env>;
