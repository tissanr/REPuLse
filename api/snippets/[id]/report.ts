import type { VercelRequest, VercelResponse } from "@vercel/node";
import { extractBearer, isUuid, setCors, userClient } from "../../_lib";

const MAX_REASON = 500;

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

  const sb = userClient(jwt);
  const { data: { user }, error: authErr } = await sb.auth.getUser();
  if (authErr || !user) {
    res.status(401).json({ error: "Invalid token" });
    return;
  }

  const { reason } = req.body ?? {};
  if (reason != null && (typeof reason !== "string" || reason.length > MAX_REASON)) {
    res.status(400).json({ error: `reason must be a string of ≤ ${MAX_REASON} characters` });
    return;
  }

  const { error } = await sb
    .from("reports")
    .insert({ user_id: user.id, snippet_id: snippetId, reason: reason ?? null });

  if (error) {
    res.status(500).json({ error: error.message });
    return;
  }
  res.status(201).json({ ok: true });
}
