import type { VercelRequest, VercelResponse } from "@vercel/node";
import { createClient } from "@supabase/supabase-js";

function userClient(jwt: string) {
  return createClient(
    new URL(process.env.SUPABASE_URL!).origin,
    process.env.SUPABASE_ANON_KEY!,
    { global: { headers: { Authorization: `Bearer ${jwt}` } } }
  );
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
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

  const sb = userClient(jwt);
  const { data: { user }, error: authErr } = await sb.auth.getUser();
  if (authErr || !user) {
    res.status(401).json({ error: "Invalid token" });
    return;
  }

  if (rating === 0) {
    const { error } = await sb
      .from("stars")
      .delete()
      .match({ user_id: user.id, snippet_id: snippetId });
    if (error) { res.status(500).json({ error: error.message }); return; }
    res.status(200).json({ rating: 0 });
  } else {
    // Upsert — insert or update the rating for this user+snippet
    const { error } = await sb
      .from("stars")
      .upsert(
        { user_id: user.id, snippet_id: snippetId, rating },
        { onConflict: "user_id,snippet_id" }
      );
    if (error) { res.status(500).json({ error: error.message }); return; }
    res.status(200).json({ rating });
  }
}
