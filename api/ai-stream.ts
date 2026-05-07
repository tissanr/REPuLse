import type { VercelRequest, VercelResponse } from "@vercel/node";

/**
 * /api/ai-stream — thin server-side proxy for AI provider streaming requests.
 *
 * Why this exists: Anthropic, OpenAI, Groq, xAI, and Google all block
 * cross-origin (CORS) requests from third-party browser origins. The browser
 * SPA posts the prepared request envelope here; we forward it server-side
 * (no CORS concern) and pipe the SSE stream straight back to the client.
 *
 * Security:
 *  - Target URL host is validated against a strict allowlist — no SSRF.
 *  - API keys travel only over HTTPS (browser → Vercel → provider).
 *  - Keys are never logged or stored server-side.
 */

const ALLOWED_HOSTS = new Set([
  "api.anthropic.com",
  "api.openai.com",
  "api.groq.com",
  "api.x.ai",
  "generativelanguage.googleapis.com",
]);

// Hop-by-hop headers must not be forwarded to the upstream
const HOP_BY_HOP = new Set([
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailers",
  "transfer-encoding",
  "upgrade",
  "host",
]);

export const config = {
  api: {
    bodyParser: {
      sizeLimit: "256kb",
    },
  },
};

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "Method not allowed" });
  }

  const body = req.body as Record<string, unknown> | undefined;
  const { url, headers: fwdHeaders, body: requestBody } = body ?? {};

  if (!url || typeof url !== "string") {
    return res.status(400).json({ error: "Missing or invalid 'url' field" });
  }

  let parsedUrl: URL;
  try {
    parsedUrl = new URL(url);
  } catch {
    return res.status(400).json({ error: "Malformed URL" });
  }

  if (!ALLOWED_HOSTS.has(parsedUrl.hostname)) {
    return res.status(403).json({ error: `Host not allowed: ${parsedUrl.hostname}` });
  }

  if (parsedUrl.protocol !== "https:") {
    return res.status(403).json({ error: "Only HTTPS URLs are allowed" });
  }

  if (!fwdHeaders || typeof fwdHeaders !== "object" || Array.isArray(fwdHeaders)) {
    return res.status(400).json({ error: "Missing or invalid 'headers' field" });
  }

  if (!requestBody || typeof requestBody !== "string") {
    return res.status(400).json({ error: "Missing or invalid 'body' field" });
  }

  // Build safe forwarding headers (drop hop-by-hop, ensure content-type)
  const safeHeaders: Record<string, string> = {};
  for (const [k, v] of Object.entries(fwdHeaders as Record<string, string>)) {
    if (!HOP_BY_HOP.has(k.toLowerCase())) {
      safeHeaders[k] = v;
    }
  }
  if (!safeHeaders["content-type"]) {
    safeHeaders["content-type"] = "application/json";
  }

  let upstream: Response;
  try {
    upstream = await fetch(url, {
      method: "POST",
      headers: safeHeaders,
      body: requestBody,
    });
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    return res.status(502).json({ error: `Upstream fetch failed: ${msg}` });
  }

  const contentType =
    upstream.headers.get("content-type") ?? "text/event-stream";
  res.setHeader("Content-Type", contentType);
  res.setHeader("Cache-Control", "no-cache, no-store");
  res.setHeader("X-Accel-Buffering", "no");
  res.status(upstream.status);

  if (!upstream.ok) {
    const errText = await upstream.text();
    return res.end(errText);
  }

  if (!upstream.body) {
    return res.end();
  }

  // Stream SSE chunks straight through
  const reader = upstream.body.getReader();
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      res.write(Buffer.from(value));
    }
  } catch {
    // Client disconnected or upstream error — end cleanly
  } finally {
    res.end();
  }
}
