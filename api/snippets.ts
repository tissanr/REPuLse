import type { VercelRequest, VercelResponse } from "@vercel/node";
import { createClient } from "@supabase/supabase-js";

// ── CORS ──────────────────────────────────────────────────────────────────────

const ALLOWED_ORIGINS = (process.env.ALLOWED_ORIGINS ?? "")
  .split(",")
  .map((s) => s.trim())
  .filter(Boolean);

function setCors(req: VercelRequest, res: VercelResponse) {
  const origin = req.headers.origin ?? "";
  const allowed =
    ALLOWED_ORIGINS.includes(origin) ||
    /^https?:\/\/localhost(:\d+)?$/.test(origin) ||
    /^https:\/\/[a-z0-9-]+-tissanr\.vercel\.app$/.test(origin);

  res.setHeader("Access-Control-Allow-Origin", allowed ? origin : "null");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
  res.setHeader("Vary", "Origin");
}

// ── Input limits ──────────────────────────────────────────────────────────────

const MAX_TITLE       = 120;
const MAX_DESCRIPTION = 500;
const MAX_CODE        = 32_000;
const MAX_TAG_LEN     = 40;
const MAX_TAGS        = 20;
const MAX_SEARCH_LEN  = 200;

// ── Supabase clients ──────────────────────────────────────────────────────────

function serviceClient() {
  return createClient(
    process.env.SUPABASE_URL!,
    process.env.SUPABASE_SERVICE_ROLE_KEY!
  );
}

function userClient(jwt: string) {
  return createClient(
    process.env.SUPABASE_URL!,
    process.env.SUPABASE_ANON_KEY!,
    { global: { headers: { Authorization: `Bearer ${jwt}` } } }
  );
}

function extractBearer(req: VercelRequest): string | null {
  const auth = req.headers["authorization"];
  if (!auth || !auth.startsWith("Bearer ")) return null;
  return auth.slice(7);
}

// ── Handlers ──────────────────────────────────────────────────────────────────

async function handleGet(req: VercelRequest, res: VercelResponse) {
  const sb = serviceClient();
  const rawTag = typeof req.query.tag === "string" ? req.query.tag : undefined;
  const rawQ   = typeof req.query.q   === "string" ? req.query.q   : undefined;

  const tag = rawTag && rawTag.length <= MAX_TAG_LEN ? rawTag : undefined;
  const q   = rawQ   && rawQ.length   <= MAX_SEARCH_LEN ? rawQ   : undefined;

  if (rawTag && !tag) {
    res.status(400).json({ error: "tag parameter too long" });
    return;
  }
  if (rawQ && !q) {
    res.status(400).json({ error: "q parameter too long" });
    return;
  }

  const limit = Math.min(Number(req.query.limit) || 100, 200);

  let query = sb
    .from("snippets")
    .select(
      "id, author_id, title, description, code, tags, bpm, star_count, usage_count, created_at, profiles(display_name, avatar_url)"
    )
    .order("star_count", { ascending: false })
    .limit(limit);

  if (tag) query = query.contains("tags", [tag]);
  if (q)   query = query.or(`title.ilike.%${q}%,description.ilike.%${q}%`);

  const { data, error } = await query;
  if (error) {
    res.status(500).json({ error: error.message });
    return;
  }
  res.status(200).json(data);
}

async function handlePost(req: VercelRequest, res: VercelResponse) {
  const jwt = extractBearer(req);
  if (!jwt) {
    res.status(401).json({ error: "Authentication required" });
    return;
  }

  const sb = userClient(jwt);
  const { data: { user }, error: authErr } = await sb.auth.getUser();
  if (authErr || !user) {
    res.status(401).json({ error: "Invalid token" });
    return;
  }

  const { title, description, code, tags, bpm } = req.body ?? {};

  if (!title || typeof title !== "string" || title.trim().length === 0) {
    res.status(400).json({ error: "title is required" });
    return;
  }
  if (!code || typeof code !== "string" || code.trim().length === 0) {
    res.status(400).json({ error: "code is required" });
    return;
  }
  if (title.length > MAX_TITLE) {
    res.status(400).json({ error: `title must be ≤ ${MAX_TITLE} characters` });
    return;
  }
  if (description && (typeof description !== "string" || description.length > MAX_DESCRIPTION)) {
    res.status(400).json({ error: `description must be ≤ ${MAX_DESCRIPTION} characters` });
    return;
  }
  if (code.length > MAX_CODE) {
    res.status(400).json({ error: `code must be ≤ ${MAX_CODE} characters` });
    return;
  }

  const safeTags: string[] = Array.isArray(tags)
    ? tags
        .filter((t): t is string => typeof t === "string")
        .map((t) => t.trim())
        .filter((t) => t.length > 0 && t.length <= MAX_TAG_LEN)
        .slice(0, MAX_TAGS)
    : [];

  const bpmNum = bpm != null ? Number(bpm) : null;
  if (bpmNum !== null && (!isFinite(bpmNum) || bpmNum < 1 || bpmNum > 999)) {
    res.status(400).json({ error: "bpm must be between 1 and 999" });
    return;
  }

  const { data, error } = await sb
    .from("snippets")
    .insert({
      author_id:   user.id,
      title:       title.trim(),
      description: description?.trim() ?? null,
      code,
      tags:        safeTags,
      bpm:         bpmNum,
    })
    .select()
    .single();

  if (error) {
    res.status(500).json({ error: error.message });
    return;
  }
  res.status(201).json(data);
}

// ── Entry point ───────────────────────────────────────────────────────────────

export default async function handler(req: VercelRequest, res: VercelResponse) {
  setCors(req, res);
  if (req.method === "OPTIONS") { res.status(204).end(); return; }

  if (req.method === "GET")  return handleGet(req, res);
  if (req.method === "POST") return handlePost(req, res);
  res.status(405).json({ error: "Method not allowed" });
}
