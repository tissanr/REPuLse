# Phase QA3 — App Robustness & Keyboard Accessibility

> **Spec status:** full
> **Automation:** 🤖 auto — every fix is observable without ears: scheduler times and
> counters through `window.__REPULSE_APP__`, DOM attributes and focus through Playwright,
> pure helpers through `cljs.test`. No sound changes, so no listen check is needed.

**Milestone:** none — quality track
**Depends on:** none (QA1 recommended first: it touches schedule-cycle!)

## Goal

A code review found bugs in the app layer that hurt a live set: a tempo change can
silence playback for a minute, every bar writes the session to `localStorage`, a typo
wipes the master effects, snippet previews can delete snapshots, and much of the UI only
works with a mouse. This phase fixes them in five parts:

- **A** Scheduler and audio app layer — tempo anchor, audible-bar timeline, throttled
  autosave, no FX reset on failed evals, voice-chain cleanup, sample negative cache.
- **B** Snippet preview sandbox — an allow-list instead of a deny-list.
- **C** Dialogs and global keys — focus trap, correct Enter/Escape, shortcuts stay out of
  inputs and dialogs.
- **D** Keyboard accessibility — every clickable thing is focusable and works with
  Enter/Space; keyboard insert menu; ARIA for output and icon buttons.
- **E** Snippet panel — keeps focus, ignores stale responses, filters for anonymous users,
  shows load errors.

## Background

All items were checked against `dev` at `ae8a377`. Line numbers are approximate.

**A1 Tempo.** `audio/set-bpm!` (`audio.cljs` ~1010) only does
`(swap! scheduler-state assoc :cycle-dur …)`. `tick!` (~1196) computes
`cycle-start (* cycle cycle-dur)`, `schedule-cycle!` (~1102) places events at
`(* cycle cycle-dur)` (free tracks) or `(* (+ origin whole-start) cycle-dur)` (song),
`arm-capture!` uses `(* (+ origin start-bar) cd)`, `song-finished?` uses
`(* (+ origin (:total s)) cycle-dur)`, `first-song-bar` uses `(/ (+ now margin) cycle-dur)`
and `ensure-running!` uses `(Math/floor (/ now cycle-dur))`. Time 0 is the implicit
anchor, so 120→60 BPM at cycle 30 puts the next bar at 62 s instead of ~31 s, and
60→120 makes `tick!` schedule past cycles in a burst (their events are dropped as late).
`snippets/sandbox.cljs` `restore!` writes `:cycle-dur` directly.

**A2 Timeline.** `ui/timeline.cljs` `active-track-data` queries `(:cycle state)`, the
next cycle to schedule, so it shows bar N+1 while N plays. `song-resting-tracks` works
around this with `(- cycle 1 origin)`. `start-playhead-raf!` calls `draw-viz!` every
frame, and `draw-viz!` queries every pattern (`track-events` → `core/query`) every frame,
also when stopped.

**A3 Autosave.** `app.cljs` (~395) adds `:session-save` watches on `scheduler-state`
(changes every bar via `:cycle`) and `fx/chain` (the snapshot stores `:fx []`, so chain
changes never change it). `session/schedule-save!` debounces 300 ms. `save-session!`
wraps `setItem` in `(catch :default _ nil)`, so a full storage fails silently.

**A4 FX reset.** `eval_orchestrator.cljs` `evaluate*` (~306) calls
`(fx/reset-global-effects!)` before every evaluation — a failed eval or a command-bar
`(tap!)` returns all master effects to plugin defaults and marks them inactive. The
global branch of `"fx"` in `env/builtins/fx.cljs` calls `fx/set-param!`, `fx/bypass!`,
`fx/remove-effect!` directly while evaluating.

**A5 Voice chains.** `audio/schedule-disconnect!` (~571) arms one `js/setTimeout` per
event and does nothing when `dur` is nil, non-finite or a Promise resolving to nil. In
`play-event`, a sample with an orbit send but no insert calls `(js-dest nil)`, so its
send GainNodes are never disconnected.

**A6 Samples.** `samples/get-buffer!` drops a failed URL from `in-flight`, so the next
hit fetches it again. `play!` logs failures with `console.debug`. `registry`,
`buffer-cache`, `in-flight` are `def`, and `buffer-cache` never shrinks.

**A7 Misc.** `audio.cljs` `ctx` and `scheduler-state` are `def` (hot reload makes a
second AudioContext and loses the scheduler). `init-worklet!` calls `.arrayBuffer` on
any response (a 404 page is sent to the worklet as WASM). `audio/tap!` uses
`js/Date.now`. `fx/notify-plugins!` swallows `onEvent` exceptions with `(catch :default _)`.

**B Sandbox.** `snippets/sandbox.cljs` `preview-env` overrides ~20 names and lets
everything else through: `restore!`, `delete-snapshot!`, `rename-snapshot!`,
`snapshot!`, `freesound-key!`, `ai`, `export`, `midi-export`, `midi-map`, `midi-out`,
`midi-clock-out!`, `midi-sync!`, `reset!`, `upd`, `tap!` all run for real. Every built-in's
`:side-effects` is in `content/builtin_meta.edn` and, via `npm run gen:ai-docs`, in
`docs/ai/builtins.json`, which `repulse.ai.knowledge/builtins` inlines into the app
(`{"name" {"side-effects" [...] …}}`).

**C Dialogs.** `ui/dialog.cljs` sets `role` and `aria-modal` but no `aria-labelledby`;
its overlay keydown sends Enter to `ok!` even with Cancel focused; Tab leaves the dialog;
focus is not restored. `ui/snippet_submit_modal.cljs` has no role/ARIA, its `×` has no
label, and both it and `ui/snippet_panel.cljs` close on Escape from `document`
listeners, so one Escape closes both. The global keydown in `app.cljs` (~401) runs in
the capture phase before any dialog handler: Alt+Enter evaluates with a dialog open,
Cmd/Ctrl+A in the snippet search box selects the whole editor. The submit modal's BPM
input has `max="300"`, but `audio/coerce-bpm` accepts 20–640.

**D Keyboard.** `ui/buffers.cljs` nests `<span class="buf-close">` inside the tab
`<button>`. `ui/outline_panel.cljs` uses `<div>` rows (`.ol-row[data-id]`,
`data-act="fold"`), `.ol-session-head[data-act=session]` and `.ol-rail[data-act=expand]`.
`ui/history_panel.cljs` selects via `<div class="hist-entry">` / `<div class="hist-snap">`.
`ui/inspector.cljs` uses `<span class="ins-inl" data-act="inline">`,
`<span class="ins-jump" data-act="jump">`, `<kbd class="ins-esc" data-act="close">`.
`lisp-lang/insert-helper.js` opens its menu only on `mousedown` of `.insert-plus-btn`,
which appears only on hover; items react only to `mousedown`. In `app.cljs`
`build-dom!`, `#output` has no live region, `tap`/`lib`/`hist` have no `aria-label`,
`#playing-dot` has no text alternative.

**E Snippet panel.** `snippet_panel.cljs`: the `library-atom` watch calls
`render-toolbar!`, which replaces the toolbar's `innerHTML`, so typing in the author
filter loses focus when the reload lands. `snippets/reload!` has no sequencing.
Anonymous users: sort and author changes call `reload!` → `load-from-static!`, which
ignores both. `load-from-static!` ignores `resp.ok` and on failure leaves "loading…".

## Decisions

1. **Tempo anchor.** `scheduler-state` gains `:anchor-time` (audio seconds) and
   `:anchor-cycle` (integer), default `0.0`/`0`, so behaviour is unchanged until the first
   tempo change. Cycle position x starts at `anchor-time + (x − anchor-cycle) × cycle-dur`.
   Every time computation listed in A1 uses it. A tempo change re-anchors at the next
   unscheduled cycle (`:cycle` while playing), at the time that cycle would have started
   under the old tempo: already scheduled bars keep the old tempo, the next bar starts
   exactly where the last one ends. No gap, no burst.
2. Stopped: `set-bpm!` re-anchors at `ceil(cycle-at-time(now))`, also continuous.
   `ensure-running!` keeps its current start rule (floor of the cycle at `now`).
3. A tempo change during an active capture is applied the same way; the capture's
   precomputed `:end-time` is not recomputed (export runs at a fixed BPM; out of scope).
4. **Timeline** shows the audible cycle `floor(cycle-at-time(currentTime))`, queries
   patterns at most once per cycle per track (cache keyed by track, pattern identity,
   cycle, muted), and draws in the rAF loop only while playing.
5. **Autosave** fires only when project-relevant state changes: the scheduler watch only
   reacts to a `:cycle-dur` change; the `fx/chain` watch is removed. Debounce 1000 ms;
   an unchanged JSON string is not rewritten. A failed `setItem` reports once per page
   load in the output.
6. **FX staging.** During `evaluate*`, global `(fx …)` calls are recorded, not applied.
   On success: if the eval recorded any op, or was a whole-program eval (`reconcile?` or
   `whole-buffer-upd`), reset the global effects and apply the ops in order; otherwise do
   nothing. On error: discard. Outside `evaluate*` (fx_test, plugin UI) calls apply
   immediately as today.
7. **Chain cleanup.** One pending list instead of one timer per event. Known duration →
   entry `{:at t+dur+0.5}`, swept every 10th `tick!` (250 ms). Unknown duration (nil,
   non-finite, Promise resolving to nil/non-finite) → entry bound to the track, removed
   when the track is retired or cleared and by `stop!`. `stop!` disconnects everything.
8. **Samples.** Failed URL → negative cache for the page lifetime (no refetch), one
   output warning per URL. `buffer-cache` becomes LRU, capped at 200 MB of decoded audio
   (`length × channels × 4` bytes).
9. **Preview allow-list.** A name is allowed in previews iff its `"side-effects"` in
   `knowledge/builtins` is empty, or it is in `preview-extra` (below). Every other string
   key of the env — including names missing from the metadata — is replaced by a function
   that throws `ex-info "<name> is not available in snippet previews" {:type :eval-error}`.
10. **One modal stack.** New `repulse.ui.modal-stack`; dialogs and the submit modal push
    on open and pop on close. Global shortcuts and the snippet panel's Escape do nothing
    while it is non-empty. Only the topmost layer handles Escape (overlay keydown +
    `stopPropagation`).
11. **BPM range** of the submit modal follows `audio/coerce-bpm`: `min="20" max="640"`, and
    `do-submit!` sends `(audio/coerce-bpm v)`.
12. **Insert menu shortcut:** `Mod-k i` (chord, like `Mod-k s` for sliders).
13. **Anonymous library:** sort and author filter are applied client-side; no refetch.

## Implementation

### Part A — scheduler and audio app layer

**A1 `app/src/repulse/tempo.cljs` (new, pure, no requires):**
`(time-at-cycle state x)` → `anchor-time + (x − anchor-cycle) × cycle-dur`;
`(cycle-at-time state t)` → float inverse; `(retempo state now new-cd)` → state with
the new `:cycle-dur` and anchor per Decisions 1–2 (uses `:playing?` and `:cycle`).
In `audio.cljs`: add the two keys to `scheduler-state`; `set-bpm!` becomes
`(swap! scheduler-state tempo/retempo (if-let [ac @ctx] (.-currentTime ac) 0) (/ 240.0 (coerce-bpm bpm)))`;
replace every product in A1 with `tempo/time-at-cycle`; `first-song-bar` gets a
6-arity `[cycle cycle-dur now margin anchor-time anchor-cycle]` and the existing 4-arity
calls it with `0.0 0` (existing tests in `song_transport_test.cljs` stay valid);
`resolve-song-origin!` passes the anchor. `sandbox/restore!` calls `audio/set-bpm!`
with `(/ 240.0 cycle-dur)` instead of writing `:cycle-dur`. Add to `__REPULSE_APP__`
(`app.cljs` ~324): `nextCycleTime` → `(tempo/time-at-cycle state (:cycle state))`,
`audibleCycle` → `(audio/audible-cycle)`.

**A2 `ui/timeline.cljs`:** `audio/audible-cycle` (new) → nil when not playing, else
`(max 0 (int (Math/floor (tempo/cycle-at-time state (.-currentTime ac)))))`.
`active-track-data` uses it (stopped: `(:cycle state)` as today) and `song-resting-tracks`
takes the audible cycle (`(- audible origin)` instead of `(- cycle 1 origin)`). Add
`(defonce ^:private events-cache (atom {}))` `{track-name {:pattern p :cycle c :muted? m :data d}}`;
`track-events` is called only when `(not (identical? pattern p))` or cycle/muted
differ; prune names no longer in `:tracks`. Count calls in
`(defonce query-count (atom 0))`, exposed as `__REPULSE_APP__.timelineQueries`. Also expose
`timelineCycle` (the cycle of the last draw). In `start-playhead-raf!`, call `draw-viz!`
only when `(audio/playing?)`; `render-track-panel!` still draws once (stopped state).

**A3 `session.cljs`:** `schedule-save!` timeout 1000; `(defonce ^:private last-saved (atom nil))`,
skip `setItem` when the string is equal; `(defonce on-save-error (atom nil))` and
`(defonce ^:private save-error-reported? (atom false))`: in the catch, call
`(@on-save-error)` once. Pure `(defn project-relevant-change? [old new] (not= (:cycle-dur old) (:cycle-dur new)))`.
`app.cljs`: scheduler watch → `(when (session/project-relevant-change? o n) (session/schedule-save!))`;
delete the `fx/chain` `:session-save` watch; set `session/on-save-error` to
`#(set-output! "session not saved — browser storage is full. Export the project to keep your work." :warning)`.

**A4 `fx.cljs`:** `(defonce staging (atom nil))` (nil = apply immediately, else a vector
of ops). `set-param!`, `bypass!`, `remove-effect!` get a public entry used by the
builtin — `(fx/global-op! op)` with `op` one of `[:set name param v]`, `[:bypass name bool]`,
`[:remove name]` — which conjs onto `@staging` when non-nil, else applies. Add
`begin-staging!`, `(commit-staging! whole?)` (reset + apply when ops non-empty or
`whole?`; always sets staging nil) and `discard-staging!`. `env/builtins/fx.cljs` global
branch calls `fx/global-op!`. `eval_orchestrator.cljs` `evaluate*`: replace the reset
with `(fx/begin-staging!)`; error branch → `(fx/discard-staging!)`; success →
`(fx/commit-staging! (boolean (or reconcile? @b-tracks/whole-buffer-upd)))` right after
evaluation, before `activate-buffer!`. Wrap so a thrown exception also discards.

**A5 `audio.cljs`:** `(defonce ^:private pending-disconnects (atom []))` of
`{:at t-or-nil :track kw :nodes [...]}`. `schedule-disconnect!` gets `track-name`
(thread it through `event-output` from `play-event`'s `js-dest` and the defsynth branch)
and conjs an entry (a Promise dur conjs when it resolves). Pure
`(split-due entries now)` → `[due keep]` (entries with `:at` ≤ now). `tick!` sweeps every
10th call. `retire-track!` (in its finish timeout) and `clear-track!` disconnect the
track's entries; `stop!` disconnects all and resets the atom. Offline contexts still skip.
Expose `__REPULSE_APP__.pendingDisconnects` → `(count @audio/pending-disconnects)`
(make the atom public).

**A6 `samples.cljs`:** `registry`, `buffer-cache`, `in-flight` → `defonce`.
`(defonce failed-urls (atom {}))` url → reason (the error's message; `fetch-ok!`
produces `"HTTP <status> <statusText>"`). `get-buffer!` rejects at once for a failed URL; its catch records the
URL. `(defonce on-load-error (atom nil))`; `play!`'s catch calls `(@on-load-error bank reason)`
once per URL (`reported-urls` set). `app.cljs` sets it to
`(set-output! (str "sample :" (name bank) " failed to load (" reason ") — it stays silent until reload") :warning)`.
LRU: keep a JS `Map` url → bytes in insertion order (delete+set on hit); pure
`(lru-evict order-vec cap)` → urls to drop until the sum ≤ cap; cap `(* 200 1024 1024)`.

**A7:** `ctx` and `scheduler-state` → `defonce`. `init-worklet!`: `(if (.-ok resp) (.arrayBuffer resp) (throw (js/Error. (str "HTTP " (.-status resp)))))`
and the catch logs `"[REPuLse] audio backend: clojurescript synthesis (WASM fetch failed: <msg>)"`.
`tap!`: `(.now js/performance)`. `fx/notify-plugins!`: `(defonce ^:private onevent-warned (atom #{}))`;
first failure per plugin name logs
`(js/console.warn (str "[REPuLse] plugin " name " onEvent failed (further errors from it are not logged):") e)`.

### Part B — preview allow-list

New pure ns `app/src/repulse/snippets/preview_policy.cljs` requiring only
`repulse.ai.knowledge`:

```clojure
(def preview-extra
  "Allowed although their metadata lists side effects: overridden by the sandbox,
   restored by sandbox/restore!, or pattern-level only."
  #{"track" "fx" "song" "play-from" "bpm" "stop" "clear!" "mute!" "unmute!" "solo!"
    "samples!" "load-plugin" "unload-plugin" "bus" "share!" "snippet" "demo" "tutorial"
    "load-gist" "master" "defsynth" "out" "bank" "tween" "arrange" "play-scenes"
    "distort" "amp-sim" "waveshape" "cab" "map?" "seq?" "vector?"})
(defn allowed? [name] …)            ; Decision 9
(defn denial-message [name] (str name " is not available in snippet previews"))
```

`sandbox/preview-env`: after the overrides, add `"master" (fn [& _] "preview: master block ignored")`,
then replace every string key `k` with `(not (policy/allowed? k))` by a throwing fn.

### Part C — dialogs and global keys

- `ui/modal_stack.cljs` (new): `(defonce stack (atom []))`, `push! id`, `remove! id`,
  `any-open?`, `top`.
- `ui/a11y.cljs` (new, DOM helpers): `focusable-in el` (visible `button, input, select,
  textarea, [tabindex="0"]`), `trap-tab! e container` (Tab/Shift+Tab wrap),
  `activate-on-key! container` (keydown: Enter or Space on a `[role=button]` that is not a
  `<button>` → `preventDefault` + `.click`).
- `ui/dialog.cljs`: message gets `id` `rp-dialog-msg-<n>` (counter), box gets
  `aria-labelledby`; remember `document.activeElement` as opener and refocus it on close
  when still connected; overlay keydown: Enter on a `<button>` → `(.click target)`, Enter
  elsewhere → `ok!`, Escape → `cancel!`, Tab → `a11y/trap-tab!`; `stopPropagation` stays.
  Push/pop `:dialog` on the modal stack.
- `ui/snippet_submit_modal.cljs`: `#snippet-submit-modal` gets `role="dialog"
  aria-modal="true" aria-labelledby="ssm-title-label"` (add that id to the title span);
  `#ssm-close-btn` gets `aria-label="Close"`; all buttons `type="button"`; BPM input
  `min="20" max="640"`; `do-submit!` coerces. Replace the document Escape listener by an
  overlay keydown (Escape → `close!`, Tab → trap, `stopPropagation`); `open!` stores the
  opener, pushes `:snippet-submit`, focuses `#ssm-title`; `close!` pops and refocuses.
- `ui/snippet_panel.cljs` `wire-panel!`: Escape handler also requires
  `(not (modal-stack/any-open?))`.
- `app.cljs` global keydown: return early when `(modal-stack/any-open?)`, or when the
  target is an `input`, `textarea`, `select` or `[contenteditable=true]` that is not
  inside the editor or `#cmd-container`. Expose `__REPULSE_APP__.openSubmitModal` →
  `submit-modal/open!` (test handle; the share button needs a login).

### Part D — keyboard accessibility

- `ui/buffers.cljs` `tabs-html`: each tab is `<div class="buf-tab …" data-buffer="n">`
  holding `<button type="button" class="buf-name">n</button>` and, for non-main buffers,
  `<button type="button" class="buf-close" data-act="delete" aria-label="delete buffer n">`
  (text `×` / `delete?` as today). `on-click`/`on-dblclick` keep working through
  `.closest`. CSS: move the button look from `.buf-tab` to the wrapper, reset `.buf-name`
  and `.buf-close` button styles. `e2e/buffers.spec.ts` selectors keep working.
- Focus survives re-renders: `ui/a11y.cljs` gets `focus-key` (a selector built from the
  focused element's class plus `data-id`/`data-act`/`data-buffer`/`data-name`) and
  `restore-focus! container key`. `buffers/render-tabs!`, `outline-panel/render!` and
  `history-panel/render!` read the key before replacing `innerHTML` and restore it after,
  only when focus was inside the container (the outline re-renders every bar).
- `ui/outline_panel.cljs`: `row-html` adds `role="button" tabindex="0"` when the row has
  `:id` or `:row-act`; session head and rail get the same plus `aria-expanded`; call
  `a11y/activate-on-key!` on `#outline-panel` in `init!`.
- `ui/history_panel.cljs`: `.hist-entry` and `.hist-snap` get `role="button" tabindex="0"`;
  `activate-on-key!` on the panel.
- `ui/inspector.cljs`: `ins-inl`, `ins-jump`, `ins-esc` become `<button type="button">`
  with the same classes and data attributes; CSS resets `border`, `background`, `font`,
  `padding` so they look unchanged.
- `lisp-lang/insert-helper.js`: export a command `openInsertMenuAtCursor(view)` (target =
  `listEdgeTargetAt(state, head) || lineTargetAt(state, head)`; null → return false) bound
  to `Mod-k i` in `insertHelper` via `Prec.high(keymap.of([...]))`. When the dropdown opens
  from the keyboard, focus its first item. Dropdown keydown: ArrowDown/ArrowUp move focus
  (wrap), Home/End, Enter/Space apply the focused item, Escape closes and refocuses the
  editor. Items also handle `click` (keyboard activation), not only `mousedown`. The
  dropdown gets `role="menu"`, items `role="menuitem"`.
- `app.cljs` `build-dom!`: `#output` gets `aria-live="polite" aria-atomic="true"`;
  `tap-btn` `aria-label="tap tempo"`, `snippet-toggle-btn` `aria-label="snippet library"`,
  `history-toggle-btn` `aria-label="evaluation history"`; `#playing-dot` gets `role="img"`
  and `aria-label="stopped"`, which `set-playing!` switches to `"playing"`.

### Part E — snippet panel

- `snippets.cljs`: `(defonce ^:private request-seq (atom 0))`; `load!`/`reload!` take a
  new seq and every `.then`/`.catch` applies only when it is still the latest.
  `(defonce load-error (atom nil))`; `load-from-static!` checks `resp.ok`, sets
  `load-error` to the message on failure, clears it on success. `server-filtered?` =
  `(= 2 (:version @library-atom))`. `filter-snippets` applies, when not server-filtered,
  the author filter (case-insensitive substring of `:author`) and the sort: `newest` by
  `:created_at` desc, `most-used` and `trending` by `:usage_count` desc, `top-rated` as
  today; missing keys keep file order (stable sort). `reload!` without a session and
  with a loaded static library does not fetch; it only does `(swap! filter-rev inc)` on a
  new `(defonce filter-rev (atom 0))`, which the panel watches to call `render-cards!`.
- `ui/snippet_panel.cljs`: build the toolbar once in `init!` (including the share button,
  hidden via `hidden` attribute when logged out) and wire all listeners there.
  `render-toolbar!` becomes `sync-toolbar!`: rebuild only the `<option>`s of
  `#snippet-tag-filter` when the tag list changed, set select values, set the search and
  author inputs' `.value` only when they are not `document.activeElement`, toggle the
  share button. Never assign the toolbar's `innerHTML` after `init!`. When `load-error`
  is set, `#snippet-cards` shows
  `<div class="snippet-empty snippet-load-error" role="alert">could not load the snippet library <button type="button" class="snippet-retry-btn">retry</button></div>`;
  retry calls `snippets/reload!`.

### Tests

- `app/src/repulse/audio_scheduler_test.cljs` (new, add to `test_runner.cljs`): `tempo`
  round trip; `retempo` 120→60 at `:cycle 31` keeps `time-at-cycle 31` = 62.0 → becomes
  the anchor and cycle 32 starts at 66.0; 60→120 likewise; stopped re-anchor;
  `split-due`; `samples/lru-evict`; `session/project-relevant-change?`.
- `app/src/repulse/snippets/preview_policy_test.cljs` (new, add to runner): enumerates
  every name in `knowledge/builtins`; asserts `allowed?` ⇔ empty side-effects or in
  `preview-extra`; asserts denied: `restore!`, `delete-snapshot!`, `rename-snapshot!`,
  `snapshot!`, `freesound-key!`, `ai`, `export`, `midi-export`, `midi-map`,
  `midi-clock-out!`, `reset!`, `upd`, `tap!`; asserts every `preview-extra` name is in
  `knowledge/builtins` or one of `map?` `seq?` `vector?`.
- `fx_test.cljs`: staging — ops recorded, not applied; discard leaves the param log;
  commit applies in order; `(commit-staging! false)` with no ops changes nothing.
- `e2e/qa3-robustness.spec.ts` and `e2e/qa3-keyboard.spec.ts` (new) — the Playwright
  checks in the Definition of done. `e2e/snippets-lib.spec.ts` gets E checks (mocked
  routes as today). Delete any scratch specs.

### Docs

`docs/USAGE.md`: keyboard section (`Mod-k i` insert menu, arrows/Enter/Escape; Enter and
Space activate outline rows, history entries, buffer tab buttons); snippet previews list
which built-ins are unavailable and the message; tempo changes take effect at the next
bar. No new built-ins, so no grammar/completions/`builtin_meta.edn` changes.

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/tempo.cljs` | new: anchor math (pure) |
| `app/src/repulse/audio.cljs` | anchor keys, `set-bpm!`, all cycle→time sites, `audible-cycle`, pending disconnects, `defonce`, WASM `ok`, `tap!` |
| `app/src/repulse/ui/timeline.cljs` | audible cycle, events cache, query counter, rAF only while playing |
| `app/src/repulse/session.cljs` | 1 s debounce, dedupe, `on-save-error`, `project-relevant-change?` |
| `app/src/repulse/fx.cljs`, `env/builtins/fx.cljs` | staging, `global-op!`, onEvent warn-once |
| `app/src/repulse/eval_orchestrator.cljs` | begin/commit/discard staging |
| `app/src/repulse/samples.cljs` | `defonce`, negative cache, `on-load-error`, LRU |
| `app/src/repulse/snippets/preview_policy.cljs` | new: allow-list |
| `app/src/repulse/snippets/sandbox.cljs` | deny wrapper, `master` override, `restore!` via `set-bpm!` |
| `app/src/repulse/snippets.cljs` | request seq, `load-error`, client sort/filter, `filter-rev` |
| `app/src/repulse/ui/modal_stack.cljs`, `ui/a11y.cljs` | new |
| `app/src/repulse/ui/dialog.cljs`, `ui/snippet_submit_modal.cljs` | ARIA, focus, keys |
| `app/src/repulse/ui/snippet_panel.cljs` | in-place toolbar, error state, Escape guard |
| `app/src/repulse/ui/buffers.cljs`, `outline_panel.cljs`, `history_panel.cljs`, `inspector.cljs` | focusable controls |
| `app/src/repulse/lisp-lang/insert-helper.js` | `Mod-k i`, keyboard menu |
| `app/src/repulse/app.cljs` | watches, hooks, key guard, ARIA, test handles |
| `app/public/css/main.css` | buffer tab wrapper, button resets, `:focus-visible` outlines |
| tests listed above, `packages/core/src/repulse/test_runner.cljs` | register new test nss |
| `docs/USAGE.md` | keyboard + preview + tempo notes |

## Definition of done

- [ ] Unit tests above pass; `npm run test:all` passes; `npx shadow-cljs compile app`
      reports no warnings.
- [ ] E2E tempo: `(bpm 480)` + `(track :a (seq :bd :sd))` playing 3 s, then evaluate
      `(bpm 120)`: `nextCycleTime() − audioTime()` is in `[0, 0.5 + 0.2]` s; 1 s later
      `cycle()` advanced by at most 2. Reverse (`120` → `480` after 6 s): within 300 ms
      `cycle()` advances by at most 2 (no catch-up burst).
- [ ] E2E timeline: at `(bpm 240)` playing, sampled 5 times 300 ms apart,
      `timelineCycle()` is `audibleCycle()` or `audibleCycle() − 1` (frame lag) and always
      less than `cycle()`. Stopped for 1 s: `timelineQueries()` does not change.
      Playing 2 tracks for 3 s at bpm 240: it grows by at most 2 × 4 + 2.
- [ ] E2E autosave (spy on `Storage.prototype.setItem` via `addInitScript`): playing at
      `(bpm 480)` for 4 s without edits, starting 1.5 s after the eval, at most 1 write of
      `repulse-session`. With `setItem` throwing a `QuotaExceededError`, two edits show
      the quota message once; after another eval changes the output, a third edit does
      not show it again.
- [ ] E2E FX: Alt+Enter on `(fx :reverb :wet 0.42)` + a track; then `evaluate("(tap!)")`
      and `evaluate("(seq :bd")`: `fxParam("reverb","wet")` stays `0.42`.
- [ ] E2E cleanup: `(track :a (->> (seq :bd :sd :bd :sd) (room 0.3)))` playing 2 s:
      `pendingDisconnects() > 0`; after `(stop)` it is `0`.
- [ ] E2E samples: route `https://raw.githubusercontent.com/**` so `Dirt-Samples.json`
      returns `{"_base":"https://samples.invalid/","qa":["qa.wav"]}` (other manifests
      `{}`) and `https://samples.invalid/**` returns 404 (counted). Playing
      `(track :a (seq :qa :qa))` for 3 cycles makes exactly 1 request to `qa.wav`, and the
      output starts with `sample :qa failed to load (HTTP 404` and ends with
      `— it stays silent until reload`.
- [ ] E2E preview: a mocked library snippet `(do (restore! 1) (track :x (seq :bd)))`
      previews with class `snippet-card--error` and title
      `restore! is not available in snippet previews`; all 24 snippets of the real
      `app/public/snippets/library.json` preview without that error.
- [ ] E2E dialog (history "clear history" confirm): Tab focuses Cancel, Enter cancels
      (entries stay); Escape cancels; Tab ×3 keeps `activeElement` inside `.rp-dialog`;
      Alt+Enter with the dialog open leaves `playing()` false; after closing, focus is on
      `[data-act=clear]`. `.rp-dialog` has `aria-labelledby` pointing at the message.
- [ ] E2E submit modal (`openSubmitModal()` with the snippet panel open): it has
      `role="dialog"`, `aria-modal="true"`, `aria-labelledby="ssm-title-label"`, `#ssm-bpm`
      has `max="640"`, `#ssm-close-btn` has `aria-label`; Escape closes the modal and
      `#snippet-panel` stays visible.
- [ ] E2E keys: in `#snippet-search` with text, Cmd/Ctrl+A selects the input text and the
      editor selection stays empty.
- [ ] E2E keyboard only (initial `focus()` allowed): `.buf-close` reachable and Enter
      twice deletes the buffer; Enter on a focused `.ol-row[data-id]` selects it; Enter on
      `.ol-session-head` toggles `aria-expanded`; Enter on a `.hist-entry` selects it;
      `Mod-k i` on an empty line opens `.insert-dropdown` with the first item focused,
      ArrowDown + Enter inserts that item's label, Escape closes and the editor has focus.
- [ ] `#output` has `aria-live="polite"`; `tap`/`lib`/`hist` buttons have `aria-label`;
      `#playing-dot` `aria-label` is `"playing"` while playing, `"stopped"` otherwise.
- [ ] E2E snippet panel: typing in `#snippet-author-filter` while a delayed `/api/snippets`
      response lands keeps focus and value; with two overlapping reloads, the slower first
      response does not replace the second; anonymous: author `mallory` shows only
      `Evil Tags`; `library.json` → 500 shows `.snippet-load-error` with a retry button,
      and retry after un-routing loads the cards.
- [ ] `docs/USAGE.md` updated; `ROADMAP.md` marked delivered; PR ends with `## Test protocol`.

## What NOT to do

- No MIDI input/clock changes (`handle-clock-pulse!` keeps `Date.now`) — QA6, needs hardware.
- No CSP, `/api`, Supabase or backend changes — QA5.
- Don't split `audio.cljs` or refactor beyond what each fix needs — QA7.
- No visual redesign: buttons that replace spans must look the same; only add
  `:focus-visible` outlines.
- Don't change song semantics (origin, relocation, `:end`) or the export path beyond
  using the anchor.
- Don't make previews call the real versions of denied built-ins, and don't edit
  `builtin_meta.edn` to make something allowed — use `preview-extra` with a reason.
- Don't add roving-tabindex or arrow navigation to the outline; Tab + Enter/Space is the
  scope.
