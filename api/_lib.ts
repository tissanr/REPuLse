import type { VercelRequest, VercelResponse } from "@vercel/node";
import { createClient } from "@supabase/supabase-js";

// ── CORS ──────────────────────────────────────────────────────────────────────

const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGINS ?? "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);

export function originAllowed(origin: string, req?: VercelRequest): boolean {
  // Same-origin requests are always allowed: the SPA calls /api/* on the
  // deployment that served it, whatever its URL (preview, production alias,
  // custom domain). Vercel routes by Host, so x-forwarded-host/host is the
  // hostname the browser actually navigated to — a third-party page's Origin
  // can never match it.
  if (req) {
    const fwd = req.headers["x-forwarded-host"];
    const host = (typeof fwd === "string" && fwd) || req.headers.host;
    if (host && origin === `https://${host}`) return true;
  }
  return (
    ALLOWED_ORIGINS.includes(origin) ||
    /^https?:\/\/localhost(:\d+)?$/.test(origin) ||
    /^https:\/\/[a-z0-9-]+-(tissanr|tissanrs-projects)\.vercel\.app$/.test(origin)
  );
}

export function setCors(req: VercelRequest, res: VercelResponse, methods: string) {
  const origin = req.headers.origin ?? "";
  res.setHeader("Access-Control-Allow-Origin", originAllowed(origin, req) ? origin : "null");
  res.setHeader("Access-Control-Allow-Methods", methods);
  res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
  res.setHeader("Vary", "Origin");
}

// ── Supabase clients ──────────────────────────────────────────────────────────

// Strip any accidental path suffix (e.g. /rest/v1) — createClient needs the bare origin.
export function supabaseOrigin() {
  return new URL(process.env.SUPABASE_URL!).origin;
}

export function serviceClient() {
  return createClient(supabaseOrigin(), process.env.SUPABASE_SERVICE_ROLE_KEY!);
}

export function anonClient() {
  return createClient(supabaseOrigin(), process.env.SUPABASE_ANON_KEY!);
}

export function userClient(jwt: string) {
  return createClient(
    supabaseOrigin(),
    process.env.SUPABASE_ANON_KEY!,
    { global: { headers: { Authorization: `Bearer ${jwt}` } } }
  );
}

// ── Request helpers ───────────────────────────────────────────────────────────

export function extractBearer(req: VercelRequest): string | null {
  const auth = req.headers["authorization"];
  if (!auth || !auth.startsWith("Bearer ")) return null;
  return auth.slice(7);
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isUuid(s: unknown): s is string {
  return typeof s === "string" && UUID_RE.test(s);
}
