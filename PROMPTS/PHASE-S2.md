# Phase S2 — Backend & Authentication

## Goal

Migrate REPuLse from static Netlify deployment to Vercel + Supabase, and add the
backend infrastructure that S3/S4 will build on: user accounts, a snippets
database, and a REST API. This is **infrastructure only** — the user-visible
community feature ships in S3.

**Before (S1):**
- Netlify static deployment
- Snippets loaded from `app/public/snippets/library.json`
- No accounts, no backend

**After (S2):**
- Vercel deployment (Edge/Node functions available)
- Supabase project with `users`, `snippets`, `stars` tables
- OAuth login via GitHub (+ optional Google)
- REST API: `GET /api/snippets`, `POST /api/snippets`, `POST /api/snippets/:id/star`
- Login button in app header; auth state visible in session panel
- S1's curated snippets seeded into Supabase as the initial content

---

## Background

### Current deployment

- Pure client-side SPA on Netlify
- Build: `wasm-pack` → `shadow-cljs release app`
- Publish directory: `app/public`
- No server-side code anywhere

### Why Vercel + Supabase

- **Vercel** — serverless functions, Node runtime, good CLJS/shadow-cljs support,
  similar DX to Netlify
- **Supabase** — Postgres + auth + row-level security in one service, no
  custom backend to maintain, OAuth providers built-in
- **Alternative considered:** staying on Netlify with Netlify Functions + a
  separate Postgres. Rejected because Supabase bundles auth, which is the
  biggest source of complexity otherwise.

### Risks of migration

- Build pipeline changes (WASM must still build on Vercel)
- DNS and existing shared-session URLs must keep working (URL hash encoding is
  stateless, so this is safe, but verify)
- OAuth redirect URLs are environment-specific — dev vs. production URLs must
  both be configured

---

## Files to change

| File | Change |
|------|--------|
| `vercel.json` | **New** — replaces `netlify.toml`; defines build, functions, env |
| `netlify.toml` | Keep for now as fallback; remove after migration validation |
| `api/` | **New directory** — serverless function handlers |
| `api/snippets.ts` | **New** — `GET` list, `POST` create |
| `api/snippets/[id]/star.ts` | **New** — `POST` toggle star |
| `api/auth/callback.ts` | **New** — OAuth callback handler (if needed; Supabase usually handles this) |
| `supabase/schema.sql` | **New** — Postgres schema + RLS policies |
| `supabase/seed.sql` | **New** — seed S1 curated snippets |
| `app/src/repulse/api.cljs` | **New** — API client (fetch wrapper, auth token handling) |
| `app/src/repulse/auth.cljs` | **New** — login state atom, OAuth flow, logout |
| `app/src/repulse/ui/auth_button.cljs` | **New** — login/logout button in header |
| `app/src/repulse/app.cljs` | Wire auth button into bootstrap |
| `app/src/repulse/snippets.cljs` | Update to fetch from API when logged in; fall back to static JSON when not |
| `package.json` | Add `@supabase/supabase-js`; remove netlify-specific deps if any |
| `.env.example` | Document required env vars: `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `VITE_APP_URL` |
| `docs/DEPLOYMENT.md` | **New** — document Vercel + Supabase setup |

---

## Supabase schema (draft)

```sql
-- Users are managed by Supabase Auth; we mirror a public profile table

create table public.profiles (
  id uuid primary key references auth.users on delete cascade,
  display_name text,
  avatar_url text,
  created_at timestamptz default now()
);

create table public.snippets (
  id uuid primary key default gen_random_uuid(),
  author_id uuid references public.profiles(id) on delete set null,
  title text not null,
  description text,
  code text not null,
  tags text[] not null default '{}',
  bpm integer,
  star_count integer not null default 0,
  usage_count integer not null default 0,
  created_at timestamptz default now(),
  updated_at timestamptz default now()
);

create table public.stars (
  user_id uuid references public.profiles(id) on delete cascade,
  snippet_id uuid references public.snippets(id) on delete cascade,
  created_at timestamptz default now(),
  primary key (user_id, snippet_id)
);

-- Row-level security
alter table public.snippets enable row level security;
create policy "snippets readable by anyone" on public.snippets
  for select using (true);
create policy "snippets insertable by authed users" on public.snippets
  for insert with check (auth.uid() = author_id);
create policy "snippets updatable by author" on public.snippets
  for update using (auth.uid() = author_id);
create policy "snippets deletable by author" on public.snippets
  for delete using (auth.uid() = author_id);
```

Star count is maintained via a Postgres trigger on `stars` insert/delete.

---

## Auth flow

1. User clicks **Sign in with GitHub** button → Supabase OAuth redirect
2. GitHub authorizes → callback → Supabase creates/reuses user
3. Supabase returns JWT → stored in app `auth-atom`
4. Subsequent API calls include `Authorization: Bearer <jwt>`
5. Logout clears atom + calls `supabase.auth.signOut()`

On page load, `auth.cljs` checks for an existing session and restores it.
Anonymous users can still browse snippets (public read) — only submitting
and starring require login.

---

## Definition of done

- [ ] `vercel.json` builds the app successfully (WASM + shadow-cljs release)
- [ ] Supabase project created with schema applied
- [ ] S1 curated snippets seeded into `snippets` table
- [ ] `/api/snippets` returns snippet list as JSON
- [ ] `/api/snippets` accepts authenticated `POST` to create a snippet
- [ ] `/api/snippets/:id/star` toggles star for authed user
- [ ] GitHub OAuth login works end-to-end on production URL
- [ ] `auth.cljs` persists session across reloads
- [ ] Anonymous users can still browse and preview (S1 behaviour preserved)
- [ ] Login button appears in header; shows avatar + name when logged in
- [ ] `.env.example` documents all required env vars
- [ ] `docs/DEPLOYMENT.md` describes Vercel + Supabase setup steps
- [ ] Session URL sharing (`#v2:...`) still works — verified after migration
- [ ] No new client-side npm dependencies beyond `@supabase/supabase-js`

---

## Out of scope

- UI for browsing community snippets — that's S3
- Starring UI — that's S3
- Audio preview — that's S4
- Moderation — deferred
- Email/password auth — OAuth only

---

## Open questions

1. **GitHub only, or GitHub + Google?** GitHub is natural for a dev tool;
   Google broadens the audience. **Deferrable** — can add Google later.
2. **Custom domain migration?** If REPuLse has a custom domain on Netlify, it
   needs DNS update for Vercel. **Blocking** — needs user decision.
3. **Keep Netlify as fallback?** Dual-deploy for a transition period? **Deferrable.**
4. **Supabase free tier limits.** Verify project fits (500 MB DB, 50k MAU,
   2 GB egress). **Blocking** if the community grows fast.
5. **Seeding S1 snippets as "system" author** (no user profile) or as a synthetic
   `repulse` user. **Blocking for schema design.**
