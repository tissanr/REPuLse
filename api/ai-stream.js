/**
 * /api/ai-stream — thin server-side proxy for AI provider streaming requests.
 *
 * Why this exists: Anthropic, OpenAI, Groq, xAI, and Google all block
 * cross-origin (CORS) requests from third-party browser origins with either
 * a 400 on the OPTIONS preflight or an explicit CORS rejection. The browser
 * SPA posts the prepared request envelope here; we forward it server-side
 * (no CORS concern) and pipe the SSE stream straight back to the client.
 *
 * Security:
 *  - Target URL host is validated against a strict allowlist — no SSRF.
 *  - API keys travel only over HTTPS (browser → Vercel → provider).
 *  - Keys are never logged or stored server-side.
 *  - Request body size is capped at 256 KB.
 */

const ALLOWED_HOSTS = new Set([
  "api.anthropic.com",
  "api.openai.com",
  "api.groq.com",
  "api.x.ai",
  "generativelanguage.googleapis.com",
]);

// Strip hop-by-hop headers that must not be forwarded to the upstream
const HOP_BY_HOP = new Set([
  "connection", "keep-alive", "proxy-authenticate",
  "proxy-authorization", "te", "trailers", "transfer-encoding", "upgrade",
  // Also strip host so the upstream sees its own hostname
  "host",
]);

export const config = {
  api: {
    bodyParser: {
      sizeLimit: "256kb",
    },
  },
};

export default async function handler(req, res) {
  // Only POST is supported
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "Method not allowed" });
  }

  const { url, headers: fwdHeaders, body } = req.body ?? {};

  // Validate inputs
  if (!url || typeof url !== "string") {
    return res.status(400).json({ error: "Missing or invalid 'url' field" });
  }

  let parsedUrl;
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

  if (!fwdHeaders || typeof fwdHeaders !== "object") {
    return res.status(400).json({ error: "Missing or invalid 'headers' field" });
  }

  if (!body || typeof body !== "string") {
    return res.status(400).json({ error: "Missing or invalid 'body' field" });
  }

  // Build safe forwarding headers (drop hop-by-hop, ensure content-type)
  const safeHeaders = {};
  for (const [k, v] of Object.entries(fwdHeaders)) {
    if (!HOP_BY_HOP.has(k.toLowerCase())) {
      safeHeaders[k] = v;
    }
  }
  if (!safeHeaders["content-type"]) {
    safeHeaders["content-type"] = "application/json";
  }

  let upstream;
  try {
    upstream = await fetch(url, {
      method: "POST",
      headers: safeHeaders,
      body,
    });
  } catch (err) {
    return res.status(502).json({ error: `Upstream fetch failed: ${err.message}` });
  }

  // Forward status and relevant headers
  res.status(upstream.status);

  const contentType = upstream.headers.get("content-type") || "text/event-stream";
  res.setHeader("Content-Type", contentType);
  res.setHeader("Cache-Control", "no-cache, no-store");
  res.setHeader("X-Accel-Buffering", "no");   // disable nginx/Vercel response buffering

  if (!upstream.ok) {
    // Non-2xx: read body and relay error text
    const errText = await upstream.text();
    return res.end(errText);
  }

  // Stream SSE chunks straight through
  const reader = upstream.body.getReader();
  const pump = async () => {
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        res.write(Buffer.from(value));
      }
    } catch (err) {
      // Client disconnected or upstream error — just end
    } finally {
      res.end();
    }
  };

  await pump();
}
