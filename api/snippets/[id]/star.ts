import type { VercelRequest, VercelResponse } from "@vercel/node";
import { extractBearer, isUuid, setCors, userClient } from "../../_lib";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  setCors(req, res, "POST, OPTIONS");
  if (req.method === "OPTIONS") { res.status(204).end(); return; }
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed" });
    return;
  }

  const jwt = extractBearer(req);
  if (!jwt) {
    res.status(401).json({ error: "Authentication required" });
    return;
  }

  const snippetId = req.query.id;
  if (!isUuid(snippetId)) {
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
