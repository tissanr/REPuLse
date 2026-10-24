import type { VercelRequest, VercelResponse } from "@vercel/node";
import { isUuid, serviceClient, setCors } from "../../_lib";

export default async function handler(req: VercelRequest, res: VercelResponse) {
  setCors(req, res, "POST, OPTIONS");
  if (req.method === "OPTIONS") { res.status(204).end(); return; }
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method not allowed" });
    return;
  }

  const snippetId = req.query.id;
  if (!isUuid(snippetId)) {
    res.status(400).json({ error: "Invalid snippet id (must be a UUID)" });
    return;
  }

  const sb = serviceClient();
  const { error } = await sb.rpc("increment_snippet_usage", { p_snippet_id: snippetId });
  if (error) {
    res.status(500).json({ error: error.message });
    return;
  }
  res.status(200).json({ ok: true });
}
