# Phase WS6 — Runtime-Aware Outline: What You Hear Is What You See

> **Spec status:** full
> **Automation:** 🤖 auto — the row states come from a pure model with unit tests, and
> every state change is observable in the real app through `scheduler-state` and the
> outline DOM (Playwright). No listen check is needed: the phase changes no sound.

**Milestone:** M2 — Full songs in code
**Depends on:** SONG3 (outline), SONG4 (multi-buffer projects), WS5 (reconciliation,
`:mute`/`:solo` in code), all delivered.

## Goal

The outline lists the forms in the code, but it doesn't show which tracks actually run.
Outside a song, a track row looks the same whether it plays, was cleared with `(clear!)`,
was stopped with `(stop)` or has never been evaluated. A track started from the command bar
appears nowhere, although it is audible. Found while testing v0.2.0 (PR #203, tests 8 and
17).

After this phase the outline shows the **union of the code and the scheduler**. Every track
row in the code carries its run state, and every audible track that isn't in the active
buffer gets a row in a new *running* group:

```lisp
;; buffer
(track :kick (seq :bd :_ :bd :_))

;; command bar
(track :extra (seq :hh :hh))
```

```
before                         after
──────                         ─────
track :kick          L1        • track :kick                 L1
                               ── running ──
                               • track :extra   not in code
```

```lisp
;; command bar, after the above
(clear!)
```

```
before                         after
──────                         ─────
track :kick          L1          track :kick                 L1   ← dimmed, no dot
                               (the :extra row is gone: not in code, not running)
```

The code is never changed. A command-bar track is not written into the buffer. The outline
only reports it.

## Background

### Outline panel — `app/src/repulse/ui/outline_panel.cljs`

- `entries` (atom) holds the active buffer's entries from `repulse.outline/extract`
  (Lezer tree, kinds `:def :defn :defsynth :track :song :fx :master`). Re-extracted by
  `re-extract!` on doc changes (150 ms debounce, `schedule-extract!`) and by `refresh!` on a
  buffer switch.
- `render-html` builds the whole panel as an HTML string. Track rows are built in the
  `:track` branch of the `case`:
  ```clojure
  (let [m?   (contains? muted (keyword (subs (:name e) 1)))
        in?  (and sec-trk (or (= [":all"] sec-trk) (some #{(:name e)} sec-trk)))
        aud? (and playing (not m?) in?)]
    (row-html {… :muted? m? :off? (and playing (not in?))
               :glyph (cond m? "■" aud? "•") …}))
  ```
  `playing` is only non-nil when the buffer has a `(song …)` **and** `audio/playing?`. So
  without a song a track row never gets `•` and never gets `ol-off`. Only `■` (muted)
  works. This is the gap.
- `row-html` takes `:glyph :glyph-class :muted? :off? :meta :id :sel?`. CSS in
  `app/public/css/main.css` (~line 1558): `.ol-row.ol-muted` (opacity .55),
  `.ol-row.ol-off .ol-name` (grey), `.ol-glyph.ol-g-aud` (cyan dot).
- After the entries come the fx group (`ol-group-divider` + rows) and the master group,
  then the session block (MIDI/buses/sources). The new *running* group follows the same
  divider pattern.
- `on-click` handles row clicks through `data-id` (selection) and `data-act` (fold). Rows
  without `data-id` are not selectable.

### Repaint triggers — `app/src/repulse/app.cljs`

`(add-watch audio/scheduler-state ::workspace …)` already calls
`outline-panel/schedule-render!` on **every** scheduler change. `(clear!)`, `(clear! :a)`,
`(stop)`, `play-track!`, `retire-track!` and reconciliation all go through
`scheduler-state`, so no new watch is needed.

### Scheduler — `app/src/repulse/audio.cljs`

- `scheduler-state` keys used here: `:playing?`, `:tracks` (keyword → pattern), `:muted`
  (set of keywords, rebuilt from the code's `:mute`/`:solo` flags by WS5 `reconcile!`).
- `stop!` resets `:tracks {}` and `:muted #{}`. `clear-track!` removes one track and calls
  `stop!` when none are left. `retire-track!` removes the track from `:tracks` at once and
  ramps its gain to 0.
- Track names starting with `__` are snippet previews (`tracks-model`: never reconciled).
  `:_` is the anonymous track of a bare pattern at the end of a buffer or command-bar eval.

### Command-bar tracks and `(clear!)` — `app/src/repulse/env/builtins/tracks.cljs`

- `track` collects into `song-transport/pending-tracks`. A command-bar eval activates them
  without reconciling, so `(track :extra …)` from the command bar adds a scheduler track
  that no buffer declares.
- `(clear!)` = `audio/stop!` + reset `song-buffer` + clear highlights. `(clear! :a)` =
  `audio/clear-track!`. Neither touches the code.
- `(upd)` in a single-buffer project reconciles, so `:extra` is retired (PR #190).

### Other buffers — `app/src/repulse/app.cljs`, `ui/buffers.cljs`

`goto/cross-buffer` is set up in `app.cljs` with `:find`, which re-parses every
non-active buffer with `(.parse parser code)` + `outline/extract`, and `:switch!`
(`buffers/switch-to!`). `buffers/current-project` returns
`{:main :active :buffers [{:name :code} …]}` with live codes. `goto/goto-definition!` shows
the cross-buffer jump: `switch!` the buffer, then `goto/reveal!` at the entry.

## Decisions

1. **Code wins over runtime, and runtime is never written into code.** The outline shows
   the code's rows first, unchanged in order. Runtime information decorates them or adds
   rows in a separate group, and is never inserted between code rows.
2. **Track row states** (outside a song):

   | State | Condition | Row |
   |---|---|---|
   | `:playing` | name in `:tracks`, not in `:muted` | `•` cyan dot (`ol-g-aud`) |
   | `:muted` | name in `:tracks` and in `:muted` | `■` + `ol-muted` (as today) |
   | `:stopped` | name not in `:tracks` | no glyph + `ol-off`, `title="not running"` |

   Inside a playing song the existing section logic stays as it is. It already dims
   tracks outside the current section. The new states only replace the case where no song
   is playing.
3. **The running group** lists every scheduler track that is **not a `:track` entry of the
   active buffer**, except `__*` preview tracks:
   - declared in another project buffer → `meta` = the buffer's name (e.g. `drums`). A click
     switches to that buffer and reveals the form (same path as `goto-definition!`).
   - `:_` → name `_`, `meta` = `anonymous`. Not clickable.
   - in no buffer → `meta` = `not in code`. Not clickable.

   Each row carries `•` or `■` like a code track. The group is hidden when it is empty. It
   sits after the master group and before the session block, under an `ol-group-divider`
   with the label `running`.
4. **"In no buffer" is computed against all buffers, not just the active one.** Otherwise a
   multi-buffer project would show every other buffer's tracks as `not in code`.
5. **Running-group rows are not selectable** (no `data-id`). The inspector edits code, and
   these tracks have no form to patch. "Copy into the buffer" is out of scope.
6. Song rail and collapsed outline: unchanged.

## Implementation

### 1. Pure model — `app/src/repulse/ui/outline_model.cljs` (new)

No DOM, no atoms, unit-tested in the `:test` build (same place as `status_model.cljs`).

```clojure
(ns repulse.ui.outline-model
  "Pure run-state model for the outline (Phase WS6).")

(defn track-state
  "Run state of a code track outside a song: :playing | :muted | :stopped.
   kw is the track keyword, running = set of scheduled keywords, muted = set."
  [kw running muted]
  (cond (not (contains? running kw)) :stopped
        (contains? muted kw)         :muted
        :else                        :playing))

(defn running-rows
  "Rows for the running group, sorted by name.
   active-tracks = set of keywords declared in the active buffer,
   owner        = {kw buffer-name} for tracks declared in other buffers.
   → [{:kw :extra :state :playing :origin :none}
      {:kw :hats  :state :muted   :origin :buffer :buffer \"drums\"}
      {:kw :_     :state :playing :origin :anonymous} …]"
  [running muted active-tracks owner]
  …)
```

Rules: drop keywords whose name starts with `__`; drop those in `active-tracks`; `:_` →
`:anonymous`; in `owner` → `:buffer`; else `:none`. Sort by `(name kw)` with `:_` last.

### 2. Other buffers' track owners — `outline_panel.cljs` + `app.cljs`

Add an injectable `(defonce other-tracks-fn (atom (constantly {})))` to `outline_panel`,
set in `app.cljs` next to `goto/cross-buffer`. It returns `{kw buffer-name}` for every
`:track` entry of every non-active buffer (reuse the `.parse parser` + `outline/extract`
pattern of `:find`; the first buffer in tab order wins on duplicates).

Cache the result per buffer code string (a small `{name [code result]}` memo), so a
render triggered by a scheduler change doesn't re-parse unchanged buffers. Clear the
memo on a buffer switch (`buffers/add-switch-hook!` already calls `outline-panel/refresh!`).

### 3. Rendering — `outline_panel.cljs` `render-html`

- `running` = `(set (keys (:tracks state)))`.
- In the `:track` branch: when `playing` (song) is nil, use
  `(outline-model/track-state kw running muted)` and map `:playing` → glyph `•` +
  `ol-g-aud`, `:muted` → `■` + `:muted? true`, `:stopped` → `:off? true` and a new
  `:title "not running"` option in `row-html` (emit `title="…"` on the row div). Keep the
  current branch for a playing song.
- After the master group, when `(seq rows)`: divider, a group label row
  (`row-html {:name "running" :meta (str (count rows))}`, styled like the fx group header),
  then one row per entry: `:kind :track`, `:name (str ":" (name kw))` (`"_"` for `:_`),
  `:meta` per Decision 3, glyph as above, `:indent 12`. A `:buffer` row gets
  `data-act="goto-buffer"` and `data-buffer`/`data-track` attributes.
- `n` (header count) stays the count of code entries; the running group shows its own
  count.

### 4. Click — `outline_panel.cljs` `on-click`

For `data-act="goto-buffer"`: call the cross-buffer path, i.e.
`((:switch! @goto/cross-buffer) buffer)`, then look up the entry with
`outline/definition-for` in the new active entries and `goto/reveal!` it. Factor a small
`goto/goto-entry-in-buffer!` [view buffer kind text] out of `goto-definition!` so both
share it (push the jump so Shift+F12 returns).

### 5. CSS — `app/public/css/main.css`

- `.ol-row.ol-off` also dims the kind label and line number (today only `.ol-name`).
- `.ol-meta` inside running rows: italic for `not in code`.
- Group label reuses the fx group header styles; no new colours.

### 6. Docs

`docs/USAGE.md`, outline section: the three row states, the running group and its three
origins, and that `(clear!)`/`(stop)` dim rows instead of removing them. No new built-ins,
so no grammar, completions, `builtin_meta.edn` or `gen:ai-docs` changes.

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/ui/outline_model.cljs` | new: `track-state`, `running-rows` |
| `app/src/repulse/ui/outline_model_test.cljs` | new: unit tests for both functions |
| `app/src/repulse/ui/outline_panel.cljs` | run states in track rows, running group, `other-tracks-fn`, `goto-buffer` click, `:title` in `row-html` |
| `app/src/repulse/ui/goto.cljs` | extract `goto-entry-in-buffer!` from `goto-definition!` |
| `app/src/repulse/app.cljs` | set `outline-panel/other-tracks-fn` next to `goto/cross-buffer` |
| `app/public/css/main.css` | `ol-off` dims the whole row; running-group meta style |
| `packages/core/src/repulse/test_runner.cljs` | register `repulse.ui.outline-model-test` next to `status-model-test` |
| `e2e/outline.spec.ts` (or new `e2e/outline-runtime.spec.ts`) | Playwright cases below |
| `docs/USAGE.md` | outline section |
| `ROADMAP.md` | mark delivered |

## Definition of done

- [ ] Unit tests: `track-state` returns `:playing`, `:muted` and `:stopped` for the three
      conditions. `running-rows` drops `__preview__`, drops active-buffer tracks, labels
      `:_` as `:anonymous`, other-buffer tracks as `:buffer` with the name and the rest as
      `:none`, sorted with `:_` last.
- [ ] Buffer `(track :kick (seq :bd :_ :bd :_))`, Alt+Enter: the `:kick` row shows `•`.
      No running group is rendered.
- [ ] Then command bar `(track :extra (seq :hh :hh))`: a running group appears with one
      row `:extra` carrying `•` and the meta `not in code`. The buffer text is unchanged.
- [ ] Then command bar `(upd)`: the `:extra` row and the running group disappear within one
      render; `:kick` still shows `•`.
- [ ] Then command bar `(clear!)`: the `:kick` row stays, loses `•` and has class `ol-off`
      and `title="not running"`. Alt+Enter brings `•` back.
- [ ] `(track :a (seq :bd :bd)) (track :b (seq :hh :hh))`, Alt+Enter, then `(clear! :a)`:
      only `:a` is `ol-off`, `:b` keeps `•`.
- [ ] `(track :hats (seq :hh :oh) :mute)`, Alt+Enter: the row shows `■` and `ol-muted`.
      After `(stop)` it is `ol-off` without `■`.
- [ ] A buffer whose last form is a bare `(seq :hh :hh)` shows a running row `_` with the
      meta `anonymous` while it plays.
- [ ] Project with buffers `main` and `drums` (`(track :hats (seq :hh :hh))` in `drums`),
      evaluated: in `main` the running group lists `:hats` with the meta `drums`. Clicking it
      switches to `drums` and puts the cursor on `:hats`; Shift+F12 returns to `main`.
      Inside `drums` there is no running row for `:hats` (it is a code row with `•`).
- [ ] A snippet preview (`:__preview__`) never appears in the running group.
- [ ] While a `(song …)` plays, track rows keep today's section behaviour (dimmed outside
      the current section, `•` inside). The song e2e tests in `e2e/song.spec.ts` and
      `e2e/outline.spec.ts` pass unchanged.
- [ ] Running-group rows have no `data-id`: clicking one never changes the selection or the
      inspector.
- [ ] `npm run test:all` passes; `npx shadow-cljs compile app` reports no warnings.
- [ ] `docs/USAGE.md` documents the row states and the running group. The PR ends with a
      `## Test protocol` section covering the cases above.

## What NOT to do

- Don't write command-bar tracks into the buffer, and don't add a "copy into buffer" action.
  That is a possible follow-up, opt-in by click only.
- Don't change what `(clear!)`, `(stop)`, `(upd)` or reconciliation do. This phase only
  shows their effect.
- Don't make running-group rows selectable or give them inspector controls (mute/solo edit
  code, and these tracks have none).
- Don't merge all buffers into one outline. The outline stays per buffer; other buffers'
  running tracks appear only in the running group.
- Don't add new colours or a new visual language. Reuse `•`, `■`, `ol-off`, `ol-muted` and
  the fx group header.
