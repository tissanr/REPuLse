import type { VercelRequest, VercelResponse } from "@vercel/node";

// Supabase handles the OAuth token exchange automatically via its redirect URL.
// This endpoint exists only as a fallback redirect target so the browser lands
// on the app after sign-in completes (Supabase appends #access_token to the URL).
export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.writeHead(302, { Location: "/" });
  res.end();
}
