import type { VercelRequest, VercelResponse } from "@vercel/node";
import { createClient } from "@supabase/supabase-js";

// ── CORS ──────────────────────────────────────────────────────────────────────

const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGINS ?? "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);

function setCors(req: VercelRequest, res: VercelResponse) {
  const origin = req.headers.origin ?? "";
  const allowed =
    ALLOWED_ORIGINS.includes(origin) ||
    /^https?:\/\/localhost(:\d+)?$/.test(origin) ||
    /^https:\/\/[a-z0-9-]+-tissanr\.vercel\.app$/.test(origin);

  res.setHeader("Access-Control-Allow-Origin", allowed ? origin : "null");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
  res.setHeader("Vary", "Origin");
}

// ── Handler ───────────────────────────────────────────────────────────────────

function userClient(jwt: string) {
  return createClient(
    process.env.SUPABASE_URL!,
    process.env.SUPABASE_ANON_KEY!,
    { global: { headers: { Authorization: `Bearer ${jwt}` } } }
  );
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  setCors(req, res);
  if (req.method === "OPTIONS") { res.status(204).end(); return; }
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed" });
    return;
  }

  const auth = req.headers["authorization"];
  if (!auth?.startsWith("Bearer ")) {
    res.status(401).json({ error: "Authentication required" });
    return;
  }
  const jwt = auth.slice(7);

  const snippetId = req.query.id as string;
  if (!snippetId) {
    res.status(400).json({ error: "Missing snippet id" });
    return;
  }

  const sb = userClient(jwt);
  const { data: { user }, error: authErr } = await sb.auth.getUser();
  if (authErr || !user) {
    res.status(401).json({ error: "Invalid token" });
    return;
  }

  // Toggle: try insert; if it exists, delete instead
  const { error: insertErr } = await sb
    .from("stars")
    .insert({ user_id: user.id, snippet_id: snippetId });

  if (insertErr) {
    if (insertErr.code === "23505") {
      // Already starred — remove the star
      const { error: delErr } = await sb
        .from("stars")
        .delete()
        .match({ user_id: user.id, snippet_id: snippetId });
      if (delErr) { res.status(500).json({ error: delErr.message }); return; }
      res.status(200).json({ starred: false });
    } else {
      res.status(500).json({ error: insertErr.message });
    }
    return;
  }

  res.status(200).json({ starred: true });
}
