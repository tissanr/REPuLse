# Phase S3 — Community Snippets

## Goal

Turn S1's local snippet browser into a community library. Authenticated users
can submit their own snippets, star others' work, and browse by popularity.
Usage is tracked implicitly when snippets are inserted.

Depends on **S2** (backend, auth, API) being complete.

**Before (S2):**
- Backend exists, users can log in, but there's no UI beyond the login button
- S1 still shows only the curated static library

**After (S3):**
- Snippet browser pulls from Supabase instead of (or in addition to) the static JSON
- "Share as snippet" button in the editor — opens a modal to submit the current
  code as a new snippet with metadata
- Star button on each card; shows current star count
- Sort options: newest, most starred, most used, trending
- Filter by author, by tag, by free-text search
- Anonymous users can browse but can't submit or star

---

## Background

S2 delivered the backend. S3 delivers the user-visible community features that
justify the backend's existence. This is where REPuLse starts having network
effects — the more users, the more valuable the library becomes.

### Moderation is deliberately minimal for MVP

- **Report button** on each snippet → creates a row in a `reports` table
- **Manual review** via a Supabase dashboard query — no built-in moderation UI
- **No auto-hide, no trust system, no bans** — if the community grows to the
  point where this matters, it gets its own phase

---

## Files to change

| File | Change |
|------|--------|
| `app/src/repulse/ui/snippet_panel.cljs` | Update to fetch from API; add star/submit/report buttons |
| `app/src/repulse/ui/snippet_submit_modal.cljs` | **New** — modal for submitting a new snippet |
| `app/src/repulse/snippets.cljs` | Extend with API fetch, cache, star/usage tracking |
| `app/src/repulse/api.cljs` | Add `submit-snippet`, `star-snippet`, `track-usage`, `report-snippet` |
| `api/snippets.ts` | Extend `GET` with sort/filter query params |
| `api/snippets/[id]/star.ts` | Already from S2 |
| `api/snippets/[id]/use.ts` | **New** — increments usage counter |
| `api/snippets/[id]/report.ts` | **New** — creates a report row |
| `supabase/schema.sql` | Add `reports` table |
| `app/public/css/main.css` | Styles for submit modal, star button, sort controls |

---

## Submit flow

1. User writes code in editor, clicks **Share as snippet** button (disabled if
   not logged in)
2. Modal opens with fields:
   - Title (required)
   - Description (optional)
   - Tags (multi-select from controlled vocabulary + free tags)
   - BPM (optional — pre-filled from current session BPM)
   - Code preview (read-only; shows what will be submitted)
3. User clicks **Submit** → `POST /api/snippets` → success toast → snippet appears
   in the panel (refresh list)

---

## Star / usage / report

- **Star toggle** — per user, per snippet; disabled when not logged in. Optimistic
  UI update + API call; revert on error.
- **Usage counter** — incremented silently when user clicks **Insert** on a
  snippet. Anonymous usage still counts (tracked by IP or session token, but
  deduped server-side).
- **Report** — small flag icon on card → prompt for reason → `POST /api/snippets/:id/report`.

---

## Sort and filter

| Sort | SQL |
|---|---|
| Newest | `order by created_at desc` |
| Most starred | `order by star_count desc` |
| Most used | `order by usage_count desc` |
| Trending | weighted: `star_count * exp(-age_days/7) + usage_count * exp(-age_days/14)` |

Filters: by tag (array contains), by author (foreign key), by free text (title/description ILIKE).

---

## Definition of done

- [ ] Snippet panel fetches from `/api/snippets` when connected; falls back to static JSON on error
- [ ] Logged-in user sees **Share as snippet** button in the editor header
- [ ] Submit modal opens, validates, and submits successfully
- [ ] Submitted snippet appears in the browser after a refresh
- [ ] Star button toggles on click; star count updates
- [ ] Usage counter increments when user clicks Insert
- [ ] Report button creates a report row in Supabase
- [ ] Sort dropdown: Newest, Most starred, Most used, Trending
- [ ] Filter: tag, author, free-text search
- [ ] Anonymous users can browse + preview + insert, but cannot submit/star/report
- [ ] Error states handled: network failure, validation error, duplicate star
- [ ] Submit modal closes on Escape, click-outside, or successful submit
- [ ] No regressions to S1/S2 functionality

---

## Out of scope

- Audio preview (S4)
- Author profiles with follower counts
- Snippet editing/deletion UI (possible in S3 if cheap; otherwise deferred)
- Collections / playlists of snippets
- Comments / discussion
- Notifications
- Internationalisation

---

## Open questions

1. **Should "Insert" increment usage anonymously?** Yes — it's the best signal
   of actual value. Server deduplicates by IP+day to prevent spam.
2. **Edit existing snippet?** Low-cost if schema supports it (it does), but
   the UI is extra work. **Deferrable** — author can delete + resubmit.
3. **Tag vocabulary growth.** Free tags or controlled? **Decision:** controlled
   vocabulary plus user-suggested tags that go through a manual approval queue.
4. **Profanity filter on titles/descriptions?** **Deferrable** — report button
   is the MVP solution.
