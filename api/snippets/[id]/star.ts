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
    new URL(process.env.SUPABASE_URL!).origin,
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
  const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  if (!snippetId || !UUID_RE.test(snippetId)) {
    res.status(400).json({ error: "Invalid snippet id (must be a UUID)" });
    return;
  }

  // rating: 1–5 sets/updates; 0 removes the rating
  const rating = Number(req.body?.rating ?? 0);
  if (!Number.isInteger(rating) || rating < 0 || rating > 5) {
    res.status(400).json({ error: "rating must be an integer 0–5 (0 = remove)" });
    return;
  }

  const authClient = userClient(jwt);
  const { data: { user }, error: authErr } = await authClient.auth.getUser();
  if (authErr || !user) {
    res.status(401).json({ error: "Invalid token" });
    return;
  }

  const sb = authClient;
  if (rating === 0) {
    const { error } = await sb
      .from("stars")
      .delete()
      .match({ user_id: user.id, snippet_id: snippetId });
    if (error) { res.status(500).json({ error: error.message }); return; }
    res.status(200).json({ rating: 0 });
  } else {
    const row = { user_id: user.id, snippet_id: snippetId, rating };
    const { data: updated, error: updateErr } = await sb
      .from("stars")
      .update({ rating })
      .match({ user_id: user.id, snippet_id: snippetId })
      .select("snippet_id");
    if (updateErr) { res.status(500).json({ error: updateErr.message }); return; }

    if (!updated || updated.length === 0) {
      const { error: insertErr } = await sb
        .from("stars")
        .insert(row);
      if (insertErr) { res.status(500).json({ error: insertErr.message }); return; }
    }

    res.status(200).json({ rating });
  }
}
