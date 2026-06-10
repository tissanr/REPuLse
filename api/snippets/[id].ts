import type { VercelRequest, VercelResponse } from "@vercel/node";
import { extractBearer, isUuid, setCors, userClient } from "../_lib";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  setCors(req, res, "DELETE, OPTIONS");
  if (req.method === "OPTIONS") { res.status(204).end(); return; }
  if (req.method !== "DELETE") {
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

  const sb = userClient(jwt);
  const { data: { user }, error: authErr } = await sb.auth.getUser();
  if (authErr || !user) {
    res.status(401).json({ error: "Invalid token" });
    return;
  }

  const { data, error } = await sb
    .from("snippets")
    .delete()
    .match({ id: snippetId, author_id: user.id })
    .select("id");

  if (error) {
    res.status(500).json({ error: error.message });
    return;
  }
  if (!data || data.length === 0) {
    res.status(403).json({ error: "Only the snippet author can delete this snippet" });
    return;
  }

  res.status(200).json({ ok: true });
}
