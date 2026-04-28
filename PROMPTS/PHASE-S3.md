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
- 5-star rating row on each card (1–5; clicking the same star removes the rating)
- Average rating + count shown next to the stars; default sort is **top rated**
  (Bayesian weighted average that dampens snippets with few ratings)
- Sort options: top rated, newest, most used, trending
- Filter by author, by tag, by free-text search
- Anonymous users can browse but can't submit or rate/report

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

## Rating / usage / report

- **5-star rating** — per user, per snippet (1–5 stars); clicking the active
  star removes the rating (sets to 0). Disabled when not logged in or for local
  snippets (slug IDs). Optimistic UI update + API call; revert on error.
  Schema: `stars.rating integer 1–5`; `snippets.avg_rating` and
  `snippets.weighted_rating` (Bayesian) maintained by trigger.
- **Usage counter** — incremented silently when user clicks **Insert** on a
  snippet. Anonymous usage still counts.
- **Report** — small flag icon on card → prompt for reason → `POST /api/snippets/:id/report`.

---

## Sort and filter

| Sort | Implementation |
|---|---|
| Top rated *(default)* | `order by weighted_rating desc` — Bayesian avg: `(n·avg + 5·3) / (n+5)` |
| Newest | `order by created_at desc` |
| Most used | `order by usage_count desc` |
| Trending | in-memory after fetch: `weighted_rating · e^(-age/7d) + 0.1·uses · e^(-age/14d)` |

The Bayesian formula (`k=5, prior=3.0`) ensures snippets with few ratings stay near
3.0 rather than dominating the top of the list with a single 5-star rating.

Filters: by tag (array contains), by author (display_name ilike), by free text (title/description ILIKE).

---

## Definition of done

- [ ] Snippet panel fetches from `/api/snippets` when connected; falls back to static JSON on error
- [ ] Logged-in user sees **Share as snippet** button in the editor header
- [ ] Submit modal opens, validates, and submits successfully
- [ ] Submitted snippet appears in the browser after a refresh
- [ ] 5-star rating row on each card; clicking active star removes rating; avg + count shown
- [ ] Ratings sorted by Bayesian weighted average (`weighted_rating`) by default
- [ ] Usage counter increments when user clicks Insert
- [ ] Report button creates a report row in Supabase
- [ ] Sort dropdown: Top rated, Newest, Most used, Trending
- [ ] Filter: tag, author, free-text search
- [ ] Anonymous users can browse + preview + insert, but cannot submit/rate/report
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
