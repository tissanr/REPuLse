import type { VercelRequest, VercelResponse } from "@vercel/node";
import { originAllowed, AI_ALLOWED_HOSTS, HOP_BY_HOP } from "./_lib";

/**
 * /api/ai-models — GET-only proxy for AI provider model catalogues.
 *
 * Providers reject cross-origin browser requests, so the SPA posts
 * { url, headers } here and we perform a GET upstream. It is deliberately
 * narrower than /api/ai-stream so it cannot become a general fetcher:
 *  - POST in (keeps the API key out of query strings and server logs), GET out.
 *  - Same origin check and host allowlist as ai-stream (shared in _lib.ts).
 *  - Only HTTPS URLs whose pathname ends in /models are forwarded.
 *  - Upstream response size is capped.
 *  - Keys are never logged or stored.
 */

const MAX_BYTES = 2 * 1024 * 1024;

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return res.status(405).json({ error: "Method not allowed" });
  }

  const origin = req.headers.origin;
  if (!origin || !originAllowed(origin, req)) {
    return res.status(403).json({ error: "Origin not allowed" });
  }

  const body = req.body as Record<string, unknown> | undefined;
  const { url, headers: fwdHeaders } = body ?? {};

  if (!url || typeof url !== "string") {
    return res.status(400).json({ error: "Missing or invalid 'url' field" });
  }

  let parsedUrl: URL;
  try {
    parsedUrl = new URL(url);
  } catch {
    return res.status(400).json({ error: "Malformed URL" });
  }

  if (!AI_ALLOWED_HOSTS.has(parsedUrl.hostname)) {
    return res.status(403).json({ error: `Host not allowed: ${parsedUrl.hostname}` });
  }

  if (parsedUrl.protocol !== "https:") {
    return res.status(403).json({ error: "Only HTTPS URLs are allowed" });
  }

  if (!parsedUrl.pathname.endsWith("/models")) {
    return res.status(403).json({ error: "Only /models paths are allowed" });
  }

  if (!fwdHeaders || typeof fwdHeaders !== "object" || Array.isArray(fwdHeaders)) {
    return res.status(400).json({ error: "Missing or invalid 'headers' field" });
  }

  const safeHeaders: Record<string, string> = {};
  for (const [k, v] of Object.entries(fwdHeaders as Record<string, unknown>)) {
    if (typeof v === "string" && !HOP_BY_HOP.has(k.toLowerCase())) {
      safeHeaders[k] = v;
    }
  }

  let upstream: Response;
  try {
    upstream = await fetch(url, { method: "GET", headers: safeHeaders });
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    return res.status(502).json({ error: `Upstream fetch failed: ${msg}` });
  }

  let buf: Buffer;
  try {
    buf = Buffer.from(await upstream.arrayBuffer());
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    return res.status(502).json({ error: `Upstream read error: ${msg}` });
  }
  if (buf.length > MAX_BYTES) {
    return res.status(502).json({ error: "Upstream response too large" });
  }

  res.setHeader("Content-Type", upstream.headers.get("content-type") ?? "application/json");
  res.setHeader("Cache-Control", "no-store");
  res.status(upstream.status);
  return res.send(buf);
}
