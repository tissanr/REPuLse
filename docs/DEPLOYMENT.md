# Deployment Guide — Vercel + Supabase

## Overview

REPuLse is deployed on **Vercel** (static SPA + serverless API) with **Supabase** as
the Postgres + auth backend.

---

## Prerequisites

- Node.js 20+, Rust + `wasm-pack` (for local builds)
- [Vercel CLI](https://vercel.com/docs/cli): `npm i -g vercel`
- A [Supabase](https://supabase.com) account

---

## 1. Create a Supabase project

1. Go to [app.supabase.com](https://app.supabase.com) and create a new project.
2. Note your **Project URL** and **API keys** (Settings → API):
   - `SUPABASE_URL` — the project URL (e.g. `https://xyz.supabase.co`)
   - `SUPABASE_ANON_KEY` — the public anon key
   - `SUPABASE_SERVICE_ROLE_KEY` — the secret service-role key (server-side only)

### Apply the schema

Paste `supabase/schema.sql` into the Supabase SQL editor and run it. This creates:
- `profiles`, `snippets`, `stars` tables
- Row-level security policies
- Triggers for star counts and profile creation

### Seed the curated snippets

Paste `supabase/seed.sql` into the SQL editor and run it to insert the 24 built-in
S1 snippets as system content (no author).

### Configure GitHub OAuth

1. In Supabase: Authentication → Providers → GitHub → Enable
2. Create a GitHub OAuth App at github.com/settings/developers:
   - **Homepage URL**: your Vercel deployment URL
   - **Authorization callback URL**: `https://your-project.supabase.co/auth/v1/callback`
3. Paste the GitHub Client ID and Secret into Supabase.

---

## 2. Deploy to Vercel

### First deploy

```bash
vercel
```

Follow the prompts. Vercel detects `vercel.json` and uses the configured build command.

### Environment variables

Set these in the Vercel dashboard (Project → Settings → Environment Variables)
or via CLI:

```bash
vercel env add SUPABASE_URL
vercel env add SUPABASE_ANON_KEY
vercel env add SUPABASE_SERVICE_ROLE_KEY
```

The `SUPABASE_URL` and `SUPABASE_ANON_KEY` are also exposed to the browser via the
`/api/env` endpoint (the anon key is designed to be public — RLS enforces access).

### Production deploy

```bash
vercel --prod
```

---

## 3. Build configuration

`vercel.json` configures:

| Setting | Value |
|---------|-------|
| Build command | `wasm-pack build` + `shadow-cljs release app` |
| Output directory | `app/public` |
| API functions | TypeScript files in `api/` |
| SPA rewrites | All non-API routes → `index.html` |

The build installs Rust + `wasm-pack` at build time via `rustup`. Vercel's build
environment provides Node 20 and Java 21 (needed for shadow-cljs).

---

## 4. Local development

```bash
# Copy and fill in env vars
cp .env.example .env

# Install dependencies
npm install

# Run the Vercel dev server (serves API functions locally)
vercel dev

# Or for CLJS-only dev (no API):
npm run dev
```

With `vercel dev`, the `/api/*` endpoints work locally against your Supabase project.

---

## 5. Session URLs

Session URLs (`#v2:...`) are purely client-side hash fragments. They are not affected
by the Vercel migration — no server round-trip is involved and existing shared URLs
continue to work.

---

## 6. Migrating from Netlify

1. Remove Netlify DNS records (or point the domain to Vercel).
2. Add the custom domain in Vercel: Project → Domains.
3. Update the GitHub OAuth callback URL in both Supabase and GitHub to the new domain.
4. Optionally remove `netlify.toml` once the Vercel deploy is confirmed working.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| WASM build fails on Vercel | Ensure `JAVA_HOME` is available; check build logs for `wasm-pack` install step |
| `/api/env` returns 503 | `SUPABASE_URL` or `SUPABASE_ANON_KEY` env var not set in Vercel |
| OAuth redirect loop | Verify callback URL in GitHub app matches Supabase project URL exactly |
| Snippets not loading | Check browser console — anonymous users load static JSON, auth users load from API |
