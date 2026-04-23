import type { VercelRequest, VercelResponse } from "@vercel/node";
import { createClient } from "@supabase/supabase-js";

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

type SortOrder = "newest" | "most-starred" | "most-used" | "trending";

function trendingScore(row: { star_count: number; usage_count: number; created_at: string }): number {
  const ageDays = (Date.now() - new Date(row.created_at).getTime()) / 86_400_000;
  return row.star_count * Math.exp(-ageDays / 7) + row.usage_count * Math.exp(-ageDays / 14);
}

async function handleGet(req: VercelRequest, res: VercelResponse) {
  const sb = serviceClient();
  const tag    = typeof req.query.tag    === "string" ? req.query.tag    : undefined;
  const q      = typeof req.query.q      === "string" ? req.query.q      : undefined;
  const author = typeof req.query.author === "string" ? req.query.author : undefined;
  const sort: SortOrder = (typeof req.query.sort === "string" ? req.query.sort : "most-starred") as SortOrder;
  const limit  = Math.min(Number(req.query.limit) || 100, 200);

  let query = sb
    .from("snippets")
    .select(
      "id, author_id, title, description, code, tags, bpm, star_count, usage_count, created_at, profiles(display_name, avatar_url)"
    )
    .limit(sort === "trending" ? Math.min(limit * 3, 200) : limit); // fetch more for in-process trending sort

  // Ordering (skip for trending — sort after fetch)
  if (sort === "newest")      query = query.order("created_at",  { ascending: false });
  else if (sort === "most-used") query = query.order("usage_count", { ascending: false });
  else if (sort !== "trending")  query = query.order("star_count",  { ascending: false });

  if (tag)    query = query.contains("tags", [tag]);
  if (q)      query = query.or(`title.ilike.%${q}%,description.ilike.%${q}%`);

  // Author filter: find profile IDs matching display_name
  if (author) {
    const { data: profiles } = await sb
      .from("profiles")
      .select("id")
      .ilike("display_name", `%${author}%`);
    const ids = (profiles ?? []).map((p: { id: string }) => p.id);
    if (ids.length === 0) {
      res.status(200).json([]);
      return;
    }
    query = query.in("author_id", ids);
  }

  const { data, error } = await query;
  if (error) {
    res.status(500).json({ error: error.message });
    return;
  }

  let result = data ?? [];
  if (sort === "trending") {
    result = [...result].sort((a, b) => trendingScore(b) - trendingScore(a)).slice(0, limit);
  }

  res.status(200).json(result);
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
  if (!title || !code) {
    res.status(400).json({ error: "title and code are required" });
    return;
  }

  const { data, error } = await sb
    .from("snippets")
    .insert({
      author_id:   user.id,
      title,
      description: description ?? null,
      code,
      tags:        Array.isArray(tags) ? tags : [],
      bpm:         bpm ? Number(bpm) : null,
    })
    .select()
    .single();

  if (error) {
    res.status(500).json({ error: error.message });
    return;
  }
  res.status(201).json(data);
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
  if (req.method === "OPTIONS") { res.status(204).end(); return; }

  if (req.method === "GET")  return handleGet(req, res);
  if (req.method === "POST") return handlePost(req, res);
  res.status(405).json({ error: "Method not allowed" });
}
