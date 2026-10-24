import type { VercelRequest, VercelResponse } from "@vercel/node";
import { extractBearer, setCors, userClient } from "./_lib";

/** Extract the `sub` (user UUID) from a JWT payload without verifying the
 *  signature.  Security: the JWT is forwarded to PostgREST in the Authorization
 *  header and Supabase validates the signature there before executing the query,
 *  so a forged token would be rejected at the DB layer.  We only need the sub
 *  here to build the .eq() filter; we never trust it for write operations. */
function subFromJwt(token: string): string | null {
  try {
    const b64 = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    const payload = JSON.parse(atob(b64));
    return typeof payload.sub === "string" ? payload.sub : null;
  } catch {
    return null;
  }
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  setCors(req, res, "GET, OPTIONS");
  if (req.method === "OPTIONS") { res.status(204).end(); return; }
  if (req.method !== "GET") {
    res.status(405).json({ error: "Method not allowed" });
    return;
  }

  const jwt = extractBearer(req);
  if (!jwt) {
    res.status(401).json({ error: "Authentication required" });
    return;
  }

  const userId = subFromJwt(jwt);
  if (!userId) {
    res.status(401).json({ error: "Invalid token" });
    return;
  }

  // Use the user client so PostgREST validates the JWT signature via RLS.
  // No extra HTTP round-trip to auth.getUser() — avoids Warp timeout.
  const sb = userClient(jwt);
  const { data, error } = await sb
    .from("stars")
    .select("snippet_id, rating")
    .eq("user_id", userId);

  if (error) {
    res.status(500).json({ error: error.message });
    return;
  }

  res.status(200).json(data ?? []);
}
