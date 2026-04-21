import type { VercelRequest, VercelResponse } from "@vercel/node";

// Exposes the public Supabase credentials to the browser SPA.
// The anon key is designed to be public — RLS enforces access control.
export default function handler(_req: VercelRequest, res: VercelResponse) {
  const url = process.env.SUPABASE_URL;
  const key = process.env.SUPABASE_ANON_KEY;

  if (!url || !key) {
    res.status(503).json({ error: "Supabase not configured" });
    return;
  }

  res.setHeader("Cache-Control", "public, max-age=3600");
  res.status(200).json({ url, key });
}
