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

  // createClient requires the bare project URL (https://xxx.supabase.co).
  // Strip any accidental path suffix (e.g. /rest/v1) that may have been stored.
  const projectUrl = new URL(url).origin;

  // VERCEL_URL is set by Vercel for every deployment (preview + production).
  // We expose it so the frontend can use the correct origin for OAuth redirectTo,
  // ensuring GitHub → Supabase → back to *this* deployment (not the main site URL).
  const vercelUrl = process.env.VERCEL_URL
    ? `https://${process.env.VERCEL_URL}`
    : null;

  res.setHeader("Cache-Control", "no-store"); // deployment-specific, must not be shared
  res.status(200).json({ url: projectUrl, key, siteUrl: vercelUrl });
}
