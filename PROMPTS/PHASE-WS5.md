# Phase WS5 — Code Is the Source of Truth: Track Reconciliation, `:mute` & `:solo` in Code

> **Spec status:** full
> **Automation:** 🤖 auto — reconciliation and flag parsing are pure functions with unit
> tests. The editor sequences, inspector buttons and session migration are Playwright-testable
> on the real app, and "which tracks are scheduled" is observable in `scheduler-state`.

**Milestone:** M2 — Full songs in code
**Depends on:** WS1 (inspector mute/solo buttons) and WS3 (code patching path), both delivered.

## Goal

After an Alt+Enter, what plays is exactly what the code says. Today evaluation only *adds*
tracks. A track whose code you deleted keeps playing, and mute/solo are hidden state that
lives in the dashboard and session storage, never in the code.

```lisp
;; 1. evaluate this
(->> (fast 8 (seq :hh)) (lpf (range 400 6000 (slow 4 sine))) (resonance 4))

;; 2. replace the buffer with this and press Alt+Enter
(track :d (->> (seq :bd :sd) (fx :reverb 0.4)))

;; before: the hi-hats keep playing (anonymous :_ track never removed) + :d starts
;; after:  the hi-hats stop, only :d plays
```

Mute and solo become track options in the code. The inspector buttons edit the code instead
of flipping a hidden flag:

```lisp
;; before — muted in the dashboard, the code says nothing about it
(track :hats (seq :hh :hh :oh :hh))

;; after — the mute button writes :mute, unmuting removes it
(track :hats (seq :hh :hh :oh :hh) :mute)
(track :kick (seq :bd :_ :bd :_) :solo)
```

Copying the buffer, sharing a `#v3:` link or restoring a snapshot now reproduces exactly what
you heard, including mutes.

## Background

### Evaluation and track activation

- `app/src/repulse/eval_orchestrator.cljs`
  - `evaluate*` [code opts] is the shared core. It resets per-eval state
    (`builtins/seen-tracks`, `song-transport/reset-pending!`), evaluates, then calls
    `song-transport/activate-buffer!`, then a `cond` on the final value:
    - **pattern** → `(audio/stop!)` + `(audio/play-track! :_ val …)`. This is why a bare
      pattern at the end of a buffer kills every named track (and why the hi-hats in the goal
      example would be replaced, while a buffer of only `track` forms never removes anything).
    - **event map** → `audio/stop!` + `:_` plays `(core/pure val)`.
    - nil / string / other → output only.
  - After the `cond`: the *legacy `:_` hot-swap* (if `:_` is playing, the last value is not a
    pattern and exactly one pattern is in `*defs*`, `:_` is updated to it), then the
    **`pending-mutes`** block (applies `@pending-mutes` once tracks exist, then clears it), then
    the warning reporters.
  - `evaluate-project!` {:record? …} — Alt+Enter, play button, slider re-evals. Evaluates the
    project from its main buffer (`project-eval/run-project`).
  - `evaluate!` [code] — single string. Used by the **command bar** (`app.cljs:273`), the tap
    button (`"(tap!)"`), `builtins/evaluate-ref` (demos, snippets `"(upd)"`, gist load) and the
    **embed** editor (`embed.cljs`).
  - Code patching: `slider-patch-and-eval!`, `per-track-fx-*-patch-and-eval!`,
    `inline-slider-patch-and-eval!` patch the doc via `lisp-patcher` and call
    `schedule-slider-eval!` (150 ms debounce → `evaluate-project! {:record? false}`).
    `begin-slider-drag!` / `end-slider-drag!` group patches into one undo step.
  - `pending-mutes` (atom, set of keywords) — filled by session restore (`app.cljs:383`) and
    snapshot/history restore (`ui/history_panel.cljs:42` via `:set-pending-mutes!`).
- `app/src/repulse/song_transport.cljs`
  - `collect-track!` [name pat src] — called by the `track` built-in. Rejects duplicates and
    tracks below a `(song …)`. Appends to `pending-tracks` `[[kw pat] …]`.
  - `activate-buffer!` — `play-tracks!` every collected track (`audio/play-track!` +
    `fx/apply-track-effects!`). **Never removes anything.**
- `app/src/repulse/env/builtins/tracks.cljs` — `track` [name pat] (exactly two args today),
  `mute!`, `unmute!`, `solo!`, `clear!` ([] → `audio/stop!`; [name] → `audio/clear-track!`),
  `tracks`, `upd`, `tap!`. `snippets/sandbox.cljs` stubs `track`, `mute!`, `unmute!`, `solo!`,
  `clear!` for the sandboxed preview evaluation.

### Audio state

- `app/src/repulse/audio.cljs`
  - `scheduler-state` holds `:tracks {kw pattern}` and `:muted #{kw}`. The scheduler
    (`schedule-cycle!`, and `song/song-onsets` for songs) skips muted tracks.
  - `play-track!` [name pat on-beat on-event] — add/replace, creates the per-track node
    (`ensure-track-node!`, a GainNode + FX chain in `track-nodes`), arms tweens.
  - `mute-track!`, `unmute-track!`, `solo-track!` (mutes every other track) — mutate `:muted`.
  - `clear-track!` [name] — disconnects the GainNode **immediately**, destroys FX plugins,
    removes the track; `stop!` when no tracks are left. Events already scheduled inside the
    ~100 ms lookahead still play into a disconnected node, so the cut is abrupt.
  - `stop!` — stops the worklet, clears all tracks, nodes, buses, `:muted`.
- `app/src/repulse/snippets/preview.cljs` plays previews on `:__preview__` tracks via
  `play-track!` with nil callbacks and cleans them up itself.

### Inspector (WS1)

- `app/src/repulse/ui/inspector_model.cljs` — pure: `track-flags` [names muted name] →
  `{:muted? :solo?}` (solo = more than one track and this is the only unmuted one),
  `mute-action` → `[:mute|:unmute name]`, `solo-action` → `[:solo name]` or `[:unmute-all]`.
- `app/src/repulse/ui/inspector.cljs` — `track-block` renders status `muted` / `solo` /
  `rests in :section` / `live`. `on-click` `"mute"` / `"solo"` → `run-action!`, which calls the
  `audio/*-track!` functions directly. Nothing touches the code.

### Session

- `app/src/repulse/session.cljs` — the v3 session map has `:muted ["kick" …]`
  (`build-session-snapshot` writes `(:muted @audio/scheduler-state)`). `session_test.cljs`
  covers `:muted` loading and sanitising. `history_test.cljs` fixes the canonical text of a v3
  map, which includes `:muted []`.

### Code patching

- `app/src/repulse/lisp_patcher.cljs` — a paren-aware tokenizer (skips comments and strings).
  The finders locate `(track :name …)` forms to scope param and fx patches. All positions are
  offsets into the doc string. Project mode: patches must target the buffer the form lives in
  (`buffers/*`, the outline's `:from` + buffer bases from `last-bases`).

## Decisions

1. **Reconcile only on whole-code evaluations.** `evaluate-project!` (Alt+Enter, play button,
   slider and patch re-evals) and the embed editor's evaluation reconcile. `evaluate!` from
   the command bar, tap button, `upd` and `evaluate-ref` callers do **not**: the command bar
   is for one-off commands and must not tear down the set. Implemented as an option:
   `(evaluate* code {:project p :reconcile? true})`; embed passes `{:reconcile? true}`.
2. **The declared set.** A successful reconciling evaluation declares:
   - every track collected by `track` (`pending-tracks`), across all buffers of the project;
   - `:_` when the final value is a pattern or an event map;
   - `:_` when the legacy hot-swap fires (its pattern comes from the code too).
   Every other scheduled track is **retired**, except names starting with `:__` (snippet
   previews). A buffer that declares nothing (only `def`s, `(bpm 120)`, comments) retires
   everything and leaves the transport stopped, with output `"nothing to play"`.
3. **Errors change nothing.** A failed evaluation (reader or eval error) retires nothing and
   changes no mute state, as today. A typo never silences the set.
4. **A bare pattern no longer stops everything.** The `(core/pattern? val)` branch drops
   `audio/stop!` and plays `:_` like any declared track: hot-swapped in place, keeping the
   cycle position, next to the named tracks. The event-map branch does the same. `(stop)`
   still stops everything.
5. **Retiring is click-free and cycle-safe.** New `audio/retire-track!` [name]: remove the
   track from `:tracks` and `:muted` now (no new events are scheduled), ramp its GainNode to 0
   over 30 ms starting at the end of the lookahead window (`currentTime` + lookahead), then
   disconnect and destroy its FX after the ramp (`setTimeout`). Events already in the
   lookahead finish into the ramp. `clear-track!` keeps its immediate behaviour for
   `(clear! :x)`. When the last track retires, `stop!` runs after the ramp.
6. **`:mute` and `:solo` are trailing flags on `track`.** `(track name pattern & flags)` where
   each flag is the keyword `:mute` or `:solo`, in any order, each at most once. An unknown
   keyword is an eval error with a fuzzy suggestion (`unknown track option :mut — did you mean
   :mute?`). A non-keyword third arg is an eval error. Flags are collected with the track:
   `collect-track!` stores `[name pat {:mute? :solo?}]`.
7. **Effective mute rule.** A declared track is audible iff it is not `:mute` **and** (no
   declared track has `:solo` **or** it has `:solo`). Several tracks may be soloed at once.
   `:mute` beats `:solo` on the same track. Pure function
   `tracks-model/muted-set` [declared] → `#{kw}`, written to `scheduler-state :muted` by
   reconciliation. `:_` can't carry flags and follows the rule like an unflagged track (it is
   silent while any track is soloed).
8. **Inspector buttons patch the code.** Mute toggles `:mute` on the track's form. Solo
   toggles `:solo` on that track's form (multi-solo; the old exclusive `solo-track!` and
   `:unmute-all` actions go away). The mute set is applied to `scheduler-state` immediately
   (so the click is heard without waiting), the form is patched, then the normal debounced
   re-eval runs and reconciles to the same state. One click = one undo step. `:_` has no
   form to patch, so its buttons are disabled with tooltip `"name the track to mute it:
   (track :name …)"`.
9. **`mute!` / `unmute!` / `solo!` become code edits.** From the command bar they patch the
   named track's form exactly like the buttons (`solo!` adds `:solo` to that track only), then
   re-evaluate. Evaluated inside the buffer (a reconciling evaluation) they are an eval error:
   `"mute! edits the code — write (track :hats … :mute) instead"`, since a buffer form that
   rewrites the buffer would loop. If no `(track :name …)` form exists:
   `"track :hats is not in the code"`. Names stay registered (no grammar change).
10. **Session `:muted` is migrated, then dropped.** Loading a session, `#v3:` link, snapshot or
    history entry whose `:muted` is non-empty patches `:mute` onto each named track form
    present in its buffers *before* the first evaluation (tracks without a form are ignored),
    with no undo entry. New sessions are written without `:muted`; the loader still accepts
    it. `pending-mutes` and `:set-pending-mutes!` are deleted. The history canonical text
    changes because `:muted` disappears: update `history_test.cljs` and accept that older
    history entries hash differently (they are still restorable, only dedup across the
    change is lost).
11. **Songs.** Section track lists keep working. A track that is `:mute`d is silent in every
    section, as today with dashboard mutes; `rests in :section` status is unchanged.

## Implementation

### 1. Pure model — `app/src/repulse/tracks_model.cljs` (new)

```clojure
(ns repulse.tracks-model
  "Pure track reconciliation and flag rules (Phase WS5).")

(def flags #{:mute :solo})

(defn parse-flags
  "Trailing track args → {:mute? bool :solo? bool} or {:error msg}."
  [args] …)

(defn muted-set
  "declared — [[kw {:mute? :solo?}] …] → set of kws that must be silent."
  [declared]
  (let [any-solo? (some (comp :solo? second) declared)]
    (set (for [[k {:keys [mute? solo?]}] declared
               :when (or mute? (and any-solo? (not solo?)))]
           k))))

(defn plan
  "current — set of scheduled track kws; declared — set of declared kws.
   → {:retire #{…}} (never retires :__* preview tracks)."
  [current declared] …)
```

Fuzzy suggestion for unknown flags: reuse the evaluator's fuzzy matcher
(`repulse.lisp.eval` closest-name helper, or a local Levenshtein ≤ 2 over `flags`).

Tests in `app/src/repulse/tracks_model_test.cljs`, registered in
`packages/core/src/repulse/test_runner.cljs`.

### 2. `track` built-in — `env/builtins/tracks.cljs`

`"track"` becomes variadic `(fn [track-name pat & flags] …)`. It parses flags with
`tracks-model/parse-flags` (`leval/unwrap` each). On error it throws `ex-info` carrying the
flag's `:source`, so the diagnostic underlines the bad keyword. It passes the flags to
`collect-track!`. Update `snippets/sandbox.cljs` so the stub accepts the extra args.

`mute!` / `unmute!` / `solo!`: see Decision 9. They need to know whether they run in a
reconciling evaluation; `evaluate*` binds a dynamic var or sets an atom
(`builtins/reconciling?`) for the duration of the evaluation.

### 3. Collection and activation — `song_transport.cljs`

`collect-track!` [name pat src flags] stores `[name pat flags]`. `activate-buffer!` returns
the declared tracks so `evaluate*` can reconcile. Keep `play-tracks!` destructuring working
(`[[nm pat] …]` with a third element is fine for `doseq` destructuring; check `song-buffer` /
`replay-buffer!` users).

### 4. Reconciliation — `eval_orchestrator.cljs`

In `evaluate*`, after a successful evaluation and after the `cond`:

```clojure
(when reconcile?
  (let [declared (cond-> (mapv (fn [[k _ f]] [k f]) activated)
                   underscore? (conj [:_ {}]))
        {:keys [retire]} (tracks-model/plan (set (keys (:tracks @audio/scheduler-state)))
                                            (set (map first declared)))]
    (doseq [k retire] (audio/retire-track! k))
    (swap! audio/scheduler-state assoc :muted (tracks-model/muted-set declared))
    (when (empty? declared)
      (set-playing! false)
      (set-output! "nothing to play" :success))))
```

`underscore?` is true when the `cond` played `:_` or the legacy hot-swap fired. Drop
`audio/stop!` from the pattern and event-map branches (Decision 4); keep
`editor/clear-highlights!` only for retired tracks' ranges, or keep the full clear if
highlight ranges are re-registered on the next cycle anyway (check `editor/highlight-range!`).
Delete the `pending-mutes` block.

### 5. `audio/retire-track!` — `audio.cljs`

Per Decision 5. Look up the lookahead constant the scheduler uses. Use
`setTargetAtTime`/`linearRampToValueAtTime` on the track GainNode. Make it safe against a
re-declaration inside the ramp window: if `play-track!` is called for the same name before the
timeout fires, cancel the pending disconnect (keep a `retiring` map of timeout ids) and ramp
back to 1.

### 6. Code patching — `lisp_patcher.cljs`

```clojure
(defn track-flag-edit
  "doc, track kw, flag (:mute|:solo), on? → {:from :to :insert} or nil when the
   form is missing or already in the requested state."
  [doc track-name flag on?] …)
```

Insert ` :mute` before the track form's closing paren (after the pattern arg and any existing
flags). Removal deletes the keyword and one adjacent space. It must work with multi-line
forms, comments inside the form, and both flags present. Project mode: the function works on
one buffer's text, and the caller finds the buffer holding `(track :name …)` (outline entries
carry the global `:from`; `last-bases` maps it back to a buffer).

New in `eval_orchestrator.cljs`: `track-flag-patch-and-eval!` [name flag on?]. It applies the
predicted muted set to `scheduler-state` immediately, patches through `dispatch-replace!` as
one undo step, then calls `schedule-slider-eval!`. It returns false when the form is missing.

### 7. Inspector — `inspector_model.cljs`, `inspector.cljs`

- `track-flags` takes the declared flags (`{kw {:mute? :solo?}}` kept in a new atom
  `eval-orchestrator/declared-flags`, set on each reconcile) so the status shows `muted` /
  `solo` from the code. `solo` means "has `:solo`", not "only unmuted track".
- `mute-action` → `[:flag name :mute (not mute?)]`. `solo-action` →
  `[:flag name :solo (not solo?)]`.
- `run-action!` `:flag` → `eo/track-flag-patch-and-eval!`. Delete `:unmute-all`.
- `:_` row: buttons disabled with the Decision 8 tooltip.

### 8. Session — `session.cljs`, `app.cljs`, `ui/history_panel.cljs`

- `build-session-snapshot` stops writing `:muted`. The sanitiser keeps accepting and
  returning it for loading.
- New `session/migrate-muted` [buffers muted] → buffers with `:mute` patched onto each
  matching track form (pure, uses `lisp-patcher/track-flag-edit`; unit tested). Call it in
  the restore path (`app.cljs:383`) and in the history/snapshot restore path before the
  buffers are written into the editor.
- Remove `pending-mutes`, `:set-pending-mutes!`, and their call sites.

### 9. Docs and editor

- `docs/USAGE.md`: the `track` reference gets the flags, and a new section *"What plays is
  what the code says"* explains reconciliation (whole-code evaluations only, errors change
  nothing, the command bar does not reconcile). `mute!` / `unmute!` / `solo!` are documented
  as command-bar code edits. Update "Error messages" with the new errors.
- `completions.js`, `hover.js`, `builtin_meta.edn`: `track` signature
  `(track :name pattern & flags)` with a `:mute` example. Update the `mute!` / `unmute!` /
  `solo!` details. Run `npm run gen:ai-docs` (`builtins.json` drift). No new built-in names,
  so no grammar change.
- `README.md`: one line under features, "mute and solo live in the code".

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/tracks_model.cljs` (new) | `parse-flags`, `muted-set`, `plan` |
| `app/src/repulse/tracks_model_test.cljs` (new) | Unit tests; register in `packages/core/src/repulse/test_runner.cljs` |
| `app/src/repulse/env/builtins/tracks.cljs` | Variadic `track` with flags; `mute!`/`unmute!`/`solo!` as code edits / buffer errors |
| `app/src/repulse/snippets/sandbox.cljs` | `track` stub accepts flags |
| `app/src/repulse/song_transport.cljs` | `collect-track!` stores flags; `activate-buffer!` returns declared tracks |
| `app/src/repulse/eval_orchestrator.cljs` | `:reconcile?` option, reconcile step, no `stop!` for bare patterns, `track-flag-patch-and-eval!`, `declared-flags`, remove `pending-mutes` |
| `app/src/repulse/audio.cljs` | `retire-track!` with ramp + cancellable disconnect; `play-track!` cancels a pending retire |
| `app/src/repulse/lisp_patcher.cljs` | `track-flag-edit` |
| `app/src/repulse/lisp_patcher_test.cljs` | `track-flag-edit` cases (multi-line, comments, both flags, missing form) |
| `app/src/repulse/ui/inspector_model.cljs` | Flags from code; `:flag` actions |
| `app/src/repulse/ui/inspector.cljs` | `run-action!` patches code; `:_` buttons disabled |
| `app/src/repulse/session.cljs` | Stop writing `:muted`; `migrate-muted` |
| `app/src/repulse/session_test.cljs`, `history_test.cljs` | `:muted` migration; canonical text without `:muted` |
| `app/src/repulse/app.cljs`, `ui/history_panel.cljs` | Migration on restore; remove `pending-mutes` wiring |
| `app/src/repulse/embed.cljs` | Embed evaluation reconciles |
| `app/src/repulse/lisp-lang/completions.js`, `hover.js` | `track` flags, `mute!`/`solo!` details |
| `app/src/repulse/content/builtin_meta.edn` → `npm run gen:ai-docs` | `track` example with `:mute`; regenerate `docs/ai/builtins.json` |
| `e2e/code-truth.spec.ts` (new) | Real-app Playwright tests (see Definition of done) |
| `docs/USAGE.md`, `README.md` | Reference + "What plays is what the code says" |
| `ROADMAP.md` | Delivered entry |

## Definition of done

- [ ] Evaluating `(->> (fast 8 (seq :hh)) (lpf (range 400 6000 (slow 4 sine))) (resonance 4))`,
      then replacing the buffer with `(track :d (->> (seq :bd :sd) (fx :reverb 0.4)))` and
      pressing Alt+Enter leaves `(keys (:tracks @scheduler-state))` = `(:d)` (Playwright, real app).
- [ ] With `(track :a (seq :bd :bd))` `(track :b (seq :hh :hh))` playing, deleting the `:b`
      line and re-evaluating leaves only `:a` scheduled. `:b`'s GainNode reaches 0 within
      150 ms of the evaluation and is disconnected afterwards (no abrupt cut: gain is
      monotonically non-increasing over the ramp).
- [ ] A buffer `(track :a (seq :bd :sd))` followed by a bare `(seq :hh :hh)` plays **both**
      `:a` and `:_`. Re-evaluating it keeps the scheduler's cycle counter running (no reset
      to the start).
- [ ] A buffer with only `(def x (seq :bd))` (no hot-swap case) stops everything, and the
      output reads `nothing to play`.
- [ ] A buffer with an eval error (e.g. `(track :a (seq :bd)` unbalanced, or `(fsat 2 …)`)
      retires nothing: the previously scheduled tracks and the muted set are unchanged.
- [ ] Command-bar evaluations (`(tracks)`, `(bpm 130)`, `(tap!)`) never retire a track.
      Snippet-preview tracks (`:__preview__`) are never retired by an editor evaluation.
- [ ] `(track :hats (seq :hh :oh) :mute)` schedules `:hats` and puts it in `:muted`.
      `(track :k (seq :bd) :solo) (track :h (seq :hh)) (track :s (seq :sd) :solo)` mutes only
      `:h`. `(track :x (seq :bd) :mute :solo)` is muted.
- [ ] `(track :x (seq :bd) :mut)` is an eval error containing `did you mean :mute?`, underlined
      on `:mut`. `(track :x (seq :bd) 3)` is an eval error.
- [ ] Clicking the inspector mute button on `:hats` turns the form into
      `(track :hats (seq :hh :oh) :mute)` (one undo step; Ctrl/⌘-Z restores the original
      text), and the track is silent immediately. Clicking again removes `:mute`. This works
      for a three-line track form with a `;` comment inside.
- [ ] Clicking solo on two tracks adds `:solo` to both and leaves only those audible. Clicking
      solo on a soloed track removes its `:solo`.
- [ ] `(mute! :hats)` in the command bar patches the form like the button. In the buffer it
      is an eval error containing `write (track :hats … :mute) instead`. For an unknown name
      the error contains `track :nope is not in the code`.
- [ ] Loading a saved session whose `:muted` is `["hats"]` and whose buffer contains
      `(track :hats …)` shows `:mute` on that form after load. Newly saved sessions and
      `#v3:` links contain no `:muted` key. `pending-mutes` no longer exists in the codebase.
- [ ] Project mode: a track declared in a second buffer is retired when its form is deleted
      there, and the mute button patches the second buffer, not the main one.
- [ ] Unit tests cover `parse-flags`, `muted-set`, `plan`, `track-flag-edit` and
      `migrate-muted`. `npm run test:all` passes. `npx shadow-cljs compile app` reports no
      warnings. No `gen:ai-docs` drift.
- [ ] `docs/USAGE.md` documents the flags, reconciliation and the new command-bar meaning of
      `mute!`/`unmute!`/`solo!`. The PR ends with a `## Test protocol` section, including a
      listen check of the goal example (the hi-hats stop without a click).

## What NOT to do

- Don't reconcile on command-bar, tap, `upd` or snippet evaluations. Only whole-code
  evaluations define the set.
- Don't add a `#_` discard reader macro or comment-based muting. Commented-out code is
  *removed*, and that's what retiring handles. Muting keeps the track and its FX chain alive.
- Don't add mute/solo for the anonymous `:_` track, per-section mutes, or mute automation
  (`(mute (every 4 …))`). Those belong in SONG2-style follow-ups.
- Don't change `(stop)` or `(clear!)` semantics, and don't add a cycle-quantised "retire at
  the next bar" option. Retirement happens after the lookahead window with a short ramp.
- Don't keep any second store of mute state (localStorage, session, inspector atom) that can
  disagree with the code. The only derived copy is `scheduler-state :muted`, rebuilt from
  the code on each reconcile.
