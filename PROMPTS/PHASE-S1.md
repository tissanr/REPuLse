# Phase S1 — Local Snippet Library

## Goal

A browsable, searchable, auditionable snippet library built into the REPuLse
editor. Ships with a curated set of 20–30 snippets as **static JSON** — no
backend, no accounts, runs on current Netlify deployment.

This phase validates the snippet UX before building the community backend in
S2–S4. Everything learned here (browser layout, preview semantics, metadata
schema, insertion flow) will inform the later backend phases.

**Before:** user stares at a blank editor or a demo template. To discover
patterns they read docs, hover over builtins, or follow the tutorial.

**After:**

```lisp
;; User opens snippet browser → filters by "rhythm" → sees "Four on the Floor"
;; Clicks ▶ to preview → hears it solo
;; Clicks "Mix" → hears it layered with their current session
;; Clicks "Insert" →
(play :kick (seq :bd :_ :bd :_))
;; ...appears in the editor as a new track
```

---

## Background

### Related existing features

| Feature | Phase | What it does | Why it's not enough |
|---|---|---|---|
| Demo templates | J | Full session loaded via `(demo :name)` | Loads a whole session, not a track-level fragment |
| Tutorial | J | Step-by-step guided session | Linear learning, not discovery |
| `load-gist` | K | Fetch code from a GitHub Gist | No metadata, no browsing, no preview |
| Session URLs | 4 | Share full session via URL hash | Shares sessions, not reusable fragments |
| Hover docs | J | Docstrings on hover | Docs, not examples you can click |

### What "snippet" means here

A snippet is a **small, self-contained pattern fragment** — typically 1–10 lines
of code — that can be inserted as a named track alongside existing code:

```clojure
;; A snippet is what would go inside a single (play :name ...) call
(play :kick (seq :bd :_ :bd :_))
(play :hats (fast 2 (seq :hh :oh :hh :_)))
```

A snippet carries metadata: title, author, tags, BPM hint, description.

### Reusable infrastructure

- `(play :name pat)` already exists (Phase 4) — snippets can insert as named
  tracks without disturbing existing tracks
- `AnalyserNode` tap on master bus (Phase 6a) — can be reused for preview audio
- Scheduler is already multi-track — "mix preview" is free once we insert a
  temporary track
- CodeMirror editor is already set up — insertion is a dispatch transaction

---

## Files to change

| File | Change |
|------|--------|
| `app/public/snippets/library.json` | **New** — curated snippet collection |
| `app/src/repulse/snippets.cljs` | **New** — snippet registry, search, filter, insert |
| `app/src/repulse/ui/snippet_panel.cljs` | **New** — browser panel DOM + interactions (assumes R1 extraction pattern) |
| `app/src/repulse/app.cljs` | Wire snippet panel into init + register `snippet` built-in |
| `packages/lisp/src/repulse/lisp/eval.cljs` | Register `snippet` in `make-env` (no — actually register in `app.cljs` since it's app-layer) |
| `app/public/css/main.css` | Styles for snippet panel, cards, tags, buttons |
| `app/src/repulse/lisp-lang/completions.js` | Add `snippet` completion entry |
| `app/src/repulse/lisp-lang/hover.js` | Add `snippet` hover doc |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `snippet` to `BuiltinName` — requires `npm run gen:grammar` |

---

## Snippet JSON schema

```json
{
  "version": 1,
  "snippets": [
    {
      "id": "four-on-the-floor",
      "title": "Four on the Floor",
      "author": "repulse",
      "tags": ["rhythm", "house", "kick"],
      "bpm": 120,
      "description": "Classic 4/4 house kick pattern",
      "code": "(play :kick (seq :bd :_ :bd :_))"
    }
  ]
}
```

**Controlled tag vocabulary** (picked up again in S3):

- **Role**: `rhythm`, `bassline`, `melody`, `chord-progression`, `lead`, `pad`, `fx-demo`, `percussive`
- **Genre**: `house`, `techno`, `dnb`, `ambient`, `jazz`, `minimal`, `breakbeat`, `trap`
- **Technique**: `euclidean`, `polyrhythm`, `sidechain`, `mini-notation`, `macro`

Snippets may have multiple tags. S1 loads this vocabulary from the JSON file.

---

## UX details

### Panel layout

- Collapsible panel below the editor (like the existing plugin panel)
- Top bar: search input, tag filter dropdown, close/collapse button
- Grid/list of snippet cards, each showing:
  - Title and author
  - Tags as small pill chips
  - Description (1 line)
  - Three buttons: **Preview**, **Mix**, **Insert**
- Keyboard: Escape closes the panel

### Preview modes

**Solo preview:** evaluates the snippet code in isolation. The current session
is not affected. A temporary scheduler track named `:__preview__` plays the
snippet. On panel close, snippet change, or re-click, the preview stops.

**Mix preview:** the snippet plays as an additional track (`:__preview__`)
alongside whatever is currently running. Useful to test fit before insertion.

Both modes share the same `:__preview__` track name so only one preview plays
at a time.

### Insertion

- **Insert** button appends the snippet code to the editor at the end of the
  buffer, separated by a blank line, and triggers `(upd)` to merge it into the
  running session.
- If the snippet's named track (from its `(play :name ...)` call) conflicts
  with an existing track, the user is warned before insertion.

### `(snippet :name)` Lisp built-in

Programmatic access for users who already know the snippet ID:

```clojure
(snippet :four-on-the-floor)
;; → inserts the snippet code into the editor
```

---

## Curated starter set (20–30 snippets)

Aim for genre + technique coverage. Initial list:

**Rhythms** (5–7):
- Four on the Floor (house kick)
- Boom-Bap (classic hip-hop)
- Amen Break (dnb)
- Clave 3-2 (latin)
- Euclidean 5/8 kick
- Polyrhythm 3:4
- Minimal techno kick + hat

**Basslines** (4–5):
- Acid 303 line
- Sub bass walk
- Reese bass (dnb)
- Walking bass (jazz)
- Offbeat bass (reggae)

**Melodies** (4–5):
- Minor arpeggio
- Chromatic descent
- Blues scale riff
- Pentatonic loop
- Euclidean lead

**Chords** (3–4):
- Maj7 progression I-IV
- Lydian pad
- ii-V-I jazz turnaround

**FX demos** (3–4):
- Reverb tail
- Delay feedback
- Sidechain pumping
- Dattorro shimmer

---

## Definition of done

- [ ] `library.json` contains ≥20 snippets following the schema
- [ ] Snippet browser panel renders below the editor, collapsible
- [ ] Tag filter narrows the displayed snippets
- [ ] Free-text search matches title, description, and code content
- [ ] **Solo preview** plays a snippet in isolation via `:__preview__` track
- [ ] **Mix preview** plays a snippet alongside the running session
- [ ] **Insert** appends the snippet code to the editor and triggers `(upd)`
- [ ] Conflict warning if snippet track name already exists
- [ ] `(snippet :name)` Lisp built-in inserts a snippet by ID
- [ ] Preview audio stops cleanly on panel close, snippet change, or `(stop)`
- [ ] Panel close button and Escape key hide the panel
- [ ] `snippet` appears in autocomplete and hover docs
- [ ] Grammar regenerated (`npm run gen:grammar`) and `parser.js` committed
- [ ] No new npm dependencies
- [ ] Works on current Netlify static deployment (no backend calls)

---

## Out of scope (handled in S2–S4)

- User accounts and authentication (S2)
- Community snippet submission (S3)
- Star/rank/usage tracking (S3)
- Cross-user browsing (S3)
- Sophisticated preview sandboxing with separate AudioContext (S4)

---

## Open questions (blocking)

1. **Snippet format: code-only or mini session?** Code-only is simpler; mini
   session allows authors to bundle BPM and effects. **Decision for S1:** code +
   optional `bpm` hint in metadata, but no bundled FX. If the user wants the
   snippet's BPM, they set it explicitly after insertion.

2. **Preview track name collision with `:__preview__`** if a user coincidentally
   names their own track that. **Decision:** document the reserved name;
   conflict warning on insert.

3. **Where does the panel live visually?** Collapsible below editor (like
   plugin panel) vs. slide-out drawer vs. modal overlay. **Decision for S1:**
   collapsible below editor, reusing existing panel styling conventions.
