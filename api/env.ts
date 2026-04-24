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

  // VERCEL_BRANCH_URL is stable per-branch (e.g. repulse-git-my-branch-team.vercel.app).
  // Prefer it over VERCEL_URL (which changes with every deploy) so that after OAuth,
  // Supabase redirects back to the recognisable branch alias URL, not a hash-based URL.
  const vercelUrl = process.env.VERCEL_BRANCH_URL
    ? `https://${process.env.VERCEL_BRANCH_URL}`
    : process.env.VERCEL_URL
      ? `https://${process.env.VERCEL_URL}`
      : null;

  res.setHeader("Cache-Control", "no-store"); // deployment-specific, must not be shared
  res.status(200).json({ url: projectUrl, key, siteUrl: vercelUrl });
}
