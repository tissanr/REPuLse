# Phase QA5 — Security & Backend Hardening

> **Spec status:** outline. Expand it into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🧑 human-in-loop. The API logic can be unit-tested. But the CSP, CORS, Vercel function limits, the Firewall and the Supabase schema only take effect on a deployed preview and the live database, and a CSP mistake breaks the production app in ways no local test catches. A human deploys, checks the preview and applies the migration.

**Milestone:** none (quality track)
**Depends on:** none. QA3 Part B (allow-list snippet sandbox) is the client-side half of the snippet hardening.

## Goal

Close the security and abuse gaps the quality review found in `vercel.json`, `api/` and
`supabase/`. The app is BYOK: AI and Freesound keys sit in the browser's localStorage. So
script injection and an open proxy cost more here than in a typical app. After this phase:

- the CSP allows scripts only from known hosts,
- the AI proxies can't be used as a free relay,
- the community endpoints can't be gamed,
- the database functions and policies pass Supabase's linter,
- `api/` has its own tests.

## Background

- **CSP** in `vercel.json`: `script-src 'self' 'wasm-unsafe-eval' 'sha256-…' https:`.
  The trailing `https:` allows any HTTPS script and makes the hash pointless. `img-src`
  and `connect-src` also allow `localhost` in production.
- **CORS / origin gate**, `api/_lib.ts` (`originAllowed`, `setCors`):
  - The preview regex `^https://[a-z0-9-]+-(tissanr|tissanrs-projects)\.vercel\.app$`
    matches anyone's project named `x-tissanr`.
  - localhost is accepted in production.
- **AI proxies**, `api/ai-stream.ts` and `api/ai-models.ts`:
  - The only guard is an `Origin` check, which non-browser clients can spoof.
  - No rate limit, no upstream `AbortSignal` timeout, no `maxDuration`.
  - `ai-stream` buffers the whole upstream body with no size cap and doesn't stream.
  - `ai-models` checks its 2 MB cap only after `arrayBuffer()` has read everything.
  - Fetch errors and the requested hostname are echoed back.
  - `ai-stream` doesn't type-check header values.
- **Snippets**, `api/snippets.ts`, `api/snippets/[id].ts`, `api/snippets/[id]/*`:
  - `[id]/use.ts` has no auth and no dedupe, and calls the usage RPC with the service-role
    key. That undoes the `revoke` in `schema.sql`.
  - `sort=trending` scores an arbitrary unordered `limit*3` subset.
  - `top-rated` sorts by `avg_rating` before the Bayesian score.
  - `limit` is not bounded below. Unknown `sort` values are accepted.
  - The `author` param is unbounded, and `%`/`_` are not escaped.
  - 200 rows with full `code` can exceed Vercel's 4.5 MB response cap. The public GET has
    no Cache-Control.
  - `star.ts` does update-then-insert, which races into a PK violation. Upsert instead;
    whether authors may rate their own snippets is open.
  - `my-stars.ts` returns 500 for a forged token and has a wrong RLS comment.
  - Raw `error.message` from the DB is returned in 500s across all handlers.
- **Supabase**, `supabase/schema.sql` (a single file, no migration history, not
  re-runnable):
  - `handle_new_user` and `update_star_count` are `SECURITY DEFINER` without
    `set search_path`.
  - `stars` select is `using (true)`, so anyone can see who rated what.
  - `reports` has no unique `(user_id, snippet_id)`.
  - `profiles` has no length constraints, a user-updatable `created_at`, and an
    unrestricted `avatar_url`.
  - `snippets.author_id … on delete set null` leaves orphans nobody can edit.
  - No trigram index for the title, description and display-name search.
- **Build and dependencies:**
  - `scripts/vercel-build.sh` downloads `latest/21/ga` JDK with no checksum.
  - `api/` imports `@supabase/supabase-js`, which only `app/package.json` declares.
  - `@vercel/node@30` brings 9 dev-only advisories (32 available).
  - There are no tests for `api/`.

## Scope

1. **CSP:**
   - Replace `https:` in `script-src` with the explicit hosts actually loaded (audit
     `index.html`, plugins and Speed Insights).
   - Drop localhost from the production CSP.
   - Ship `Content-Security-Policy-Report-Only` on a preview first.
2. **Origin gate:**
   - Allow only the production domain, `ALLOWED_ORIGINS`, and same-host previews
     (`VERCEL_URL` / `VERCEL_BRANCH_URL`).
   - No foreign-project regex. localhost only when `VERCEL_ENV !== 'production'`.
3. **AI proxies:**
   - Upstream timeout and streaming size cap; really stream `ai-stream`.
   - `maxDuration` in `vercel.json`.
   - Per-IP rate limit: Vercel Firewall rule or a small store-backed limiter.
   - Generic error messages.
4. **Snippets API:**
   - Validate `limit`, `sort` and `author`.
   - Correct trending and top-rated in SQL (view or RPC).
   - List responses return a code excerpt; full code comes from `[id]`.
   - Cache-Control on public GETs.
   - Star via upsert.
   - `use` requires auth or is deduped per user/IP per day.
   - 401 for invalid tokens; generic 500 bodies, details only in logs.
5. **Supabase:**
   - Move to `supabase/migrations/` (baseline = current schema) so changes are reviewable
     and re-runnable.
   - `set search_path = ''` on the definer functions.
   - Restrict `stars` select to the owner and expose only aggregates.
   - Unique constraint on reports; profile constraints.
   - Decide orphan handling; trigram indexes.
   - Run `get_advisors` (security + performance) until clean.
6. **Tests:** add a Node test runner for `api/` (e.g. `node --test` with ts, or vitest).
   Cover `originAllowed`, `sanitizeFilterValue`, query validation, error mapping and the
   proxies' host allowlist with mocked `fetch`. Wire it into CI.
7. **Build and dependencies:**
   - Pin the JDK by version with a checksum.
   - Declare `@supabase/supabase-js` in the root `package.json`.
   - Bump `@vercel/node`.

## Open design questions

- **Q1. Rate limiting backend.** Vercel Firewall rate-limit rules (no code, per path), or
  an app-level limiter with a Marketplace store. *Recommendation:* Firewall rules first;
  they cover both proxies and the snippet writes without new infrastructure.
- **Q2. Snippet `use` counting.** Auth required, or anonymous deduped by IP hash per day.
  *Recommendation:* anonymous deduped. Previews by logged-out users should still count.
- **Q3. Self-rating.** Allow authors to star their own snippets? *Recommendation:* no
  (check constraint plus API check).
- **Q4. Orphaned snippets on account deletion.** Delete with the account, or keep them
  read-only under "deleted user". This is the user's decision.

## Definition of done

- [ ] The preview deploy loads with no CSP violations in the console. A script tag
      pointing at an unlisted HTTPS host is blocked.
- [ ] `curl -H 'Origin: https://evil-tissanr.vercel.app' …/api/ai-models` → 403.
      localhost is rejected on production.
- [ ] An upstream that never answers makes `ai-stream` return 504 within the configured
      timeout. A response over the cap is cut off with 502. Rate limit → 429.
- [ ] `limit=-5` → 400. `sort=bogus` → 400. Trending returns the true top N by score.
- [ ] Repeated `POST /api/snippets/<id>/use` from one client increments at most once per day.
- [ ] Supabase advisors report no security warnings. `stars` rows of other users can't be
      selected with the anon key.
- [ ] `supabase/migrations/` exists and applies cleanly to a fresh project.
- [ ] `api/` tests run in CI and cover the cases above.
- [ ] No 500 body contains a Postgres error text.

## What NOT to do

- Don't move AI keys server-side or change the BYOK model. That's a product decision.
- Don't change the snippet sandbox. That's QA3 Part B.
- Don't introduce a new auth provider or change the Supabase auth flow.
- Don't change CI workflows beyond adding the `api/` test job. Tooling is QA7.
