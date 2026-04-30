# Phase HRD2 — Security Hardening

**Status:** ✓ delivered  
**PR:** #60

---

## Motivation

A security audit identified five classes of vulnerability in the REPuLse backend
and frontend before public launch of the community snippet library:

1. **XSS** — user-controlled strings rendered into `innerHTML` without escaping
2. **Missing RLS** — `profiles` table had no Row Level Security
3. **Permissive CORS** — all API endpoints allowed `*` as the origin
4. **No input validation** — snippet POST had no field length or type guards
5. **No CSP** — no Content Security Policy or security response headers

---

## Changes

### 1. XSS in `context_panel.cljs`

The context panel built HTML strings via `str` and wrote them to `innerHTML`.
Track names, FX names, bus names, binding names, source IDs, bank prefix, MIDI
target names, and parameter values were all interpolated without escaping.  A user
typing `(def <script>alert(1)</script> (seq :bd))` would have executed arbitrary JS
in any other user's browser.

**Fix:** added a private `escape-html` helper (escapes `&`, `<`, `>`, `"`) and
applied it to every user-controlled string site in all eight section renderers and
three slider renderers.  Added `[clojure.string :as str]` to the ns require.

### 2. Profiles RLS (`supabase/schema.sql`)

The `snippets` and `stars` tables had RLS enabled but `profiles` did not.  Without
RLS any authenticated user could read all profiles (minor) or update other users'
display names and avatar URLs (significant).

**Fix:** added `ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY` plus two
policies — select for anyone, update restricted to `auth.uid() = id`.

### 3. CORS restriction (`api/snippets.ts`, `api/snippets/[id]/star.ts`)

Both endpoints set `Access-Control-Allow-Origin: *`.  Replaced with a `setCors`
helper that reflects the origin only when it matches:

- origins listed in the `ALLOWED_ORIGINS` env var (comma-separated)
- `localhost:*` (any port, for local dev)
- `*.vercel.app` preview deployments owned by `tissanr`

Unknown origins receive `null` (browser will block). Added `Vary: Origin` header.

**Deployment note:** set `ALLOWED_ORIGINS=https://your-production-domain.com` in
the Vercel project environment variables.

### 4. Input validation (`api/snippets.ts`)

The snippet POST endpoint accepted arbitrary field sizes with no guards.

**Fix:** added enforcement for:

| Field | Constraint |
|-------|-----------|
| `title` | required, non-empty string, ≤ 120 chars |
| `description` | optional string, ≤ 500 chars |
| `code` | required, non-empty string, ≤ 32 000 chars |
| `tags` | array of strings, each ≤ 40 chars, max 20 items |
| `bpm` | integer 1–999 (rejects `Infinity`, `NaN`) |
| `tag` query param | ≤ 40 chars |
| `q` query param | ≤ 200 chars |

Tags are sanitised (trimmed, non-string entries dropped) rather than rejected
outright, to be lenient with client-side inconsistencies.

### 5. CSP and security headers (`vercel.json`)

Added a `headers` block to `vercel.json` covering all routes:

| Header | Value |
|--------|-------|
| `Content-Security-Policy` | `default-src 'self'; script-src 'self' 'unsafe-eval' 'wasm-unsafe-eval' https:; connect-src 'self' https: wss:; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; media-src 'self' blob:; worker-src 'self' blob:; font-src 'self' data:; frame-src https://vercel.live https://vercel.com;` |
| `X-Frame-Options` | `DENY` |
| `X-Content-Type-Options` | `nosniff` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |

`'unsafe-eval'` is required because shadow-cljs compiled output uses `new Function()`
in its dispatch paths — there is no workaround without post-processing the bundle.
`'wasm-unsafe-eval'` covers WASM execution separately.  `script-src https:` allows
the plugin system to `import()` from arbitrary HTTPS URLs (a deliberate feature).

---

## Files changed

| File | Change |
|------|--------|
| `app/src/repulse/ui/context_panel.cljs` | Added `escape-html`, applied to all user-controlled HTML sites |
| `supabase/schema.sql` | Enabled RLS on `profiles`, added select + update-own policies |
| `api/snippets.ts` | CORS `setCors` helper, input validation constants and guards |
| `api/snippets/[id]/star.ts` | CORS `setCors` helper |
| `vercel.json` | Security headers block |
