# Phase SONG4 — Multi-Buffer Projects

> **Spec status:** full (expanded 2026-11-03 by the auto-phase pipeline from the outline;
> design questions 1–4 decided by the project owner)
>
> **Automation:** 🤖 auto — buffer storage, `use` resolution order and persistence are unit-testable; tab switching and cross-buffer navigation are Playwright-testable.

**Milestone:** M2 — Full songs in code
**Depends on:** SONG1 (song form), SONG3 (navigation, extended here across buffers).
**Unblocks:** SK2 (snapshots of whole projects), SK3 (insert a capture into a new buffer —
uses `repulse.ui.buffers/create-buffer!` from this phase).

## Goal

Let a song grow beyond one file. A **project** has one **main buffer** — the song file
with the arrangement at the bottom — and any number of named buffers (drums, bass,
chords, …). The main buffer loads others explicitly at the top with `use`, so
"evaluate top to bottom, define before use" stays true across the whole project.
A single-file song is simply a project that only has a main buffer.

```lisp
;; Before: one editor buffer, everything in one file.

;; After
;; buffer "drums"
(track :kick  (seq :bd :_ :bd :_))
(track :snare (seq :_ :sd :_ :sd))

;; buffer "bass"
(track :bass (->> (scale :minor :c2 (seq 0 :_ 4 :_)) (synth :saw)))

;; buffer "main"
(use :drums :bass)          ; evaluates buffers "drums" then "bass" first
(song
  (section :verse 8 [:kick :snare :bass])
  (section :chorus 8 :all))
```

Alt+Enter in any tab evaluates the project from `main`. Errors in `drums` switch to the
`drums` tab and underline the right spot. Outline and go-to-definition work in every
buffer, and Cmd/Ctrl-click on `:kick` in `main` jumps into `drums`. Projects persist in
`localStorage` (session v3), travel in share links (`#v3:`), and export/import as one
`.repulse` EDN file.

## Background

### Evaluation

- **Lisp entry point:** `packages/lisp/src/repulse/lisp/core.cljs` `eval-string [src env]`
  reads all forms with `repulse.lisp.reader/read-all`, evaluates them one by one with
  `repulse.lisp.eval/eval-form` in the **same** `env` (user defs go into the `:*defs*` atom
  of the env), and returns `{:result v}` or an `EvalError` record `{:message :source}`
  (`eval-error?`). Exceptions are caught there; `:source` = `{:from :to}` taken from the
  `ex-data`.
- **Source positions:** `reader.cljs` `read-form` attaches `{:source {:from :to}}` as
  metadata to lists, vectors, maps and symbols, and wraps primitives (keywords, numbers,
  strings, booleans, nil) in a `SourcedVal` record `{:v :source}`. Positions are offsets
  into the string passed to `read-all` (the reader indexes `src` from 0). Reader errors
  throw `ex-info` with `{:type :read-error :from :to}` (e.g. `read-string*`
  "Unterminated string — missing closing \"", `read-form*` "Unexpected character: …").
  Mini-notation strings carry editor offsets too: `repulse.lisp.util/pat-arg` calls
  `(mini/parse v (inc (:from src)))`. Events keep `:source` all the way to the scheduler
  (`app/src/repulse/audio.cljs` ~L722 calls `on-event` with `(:source ev)`).
- **Error positions through builtin calls:** `eval.cljs` `eval-form`, normal function call
  branch (~L335–346): an exception from a builtin is re-thrown with
  `(merge {:type :eval-error} loc)` where `loc` is the exception's own `{:from :to}` if it
  has one ("unless the error already carries a more specific range"), else the call head's
  source. Only `:from`/`:to` survive this re-throw. Undefined symbols throw
  `"Undefined symbol: <n>"` plus `" — did you mean <h>?"` from `typo-hint`
  (Levenshtein ≤ 3).
- **App orchestration:** `app/src/repulse/eval_orchestrator.cljs` `evaluate! [code]`:
  bumps `eval-generation`, `builtins/ensure-env!`, `fx/reset-global-effects!`,
  `(reset! builtins/seen-tracks #{})`, `song-transport/reset-pending!`, then
  `(lisp/eval-string code (assoc @builtins/env-atom "stop" (make-stop-fn)))`.
  On error: `editor/clear-highlights!`, `set-diagnostics!` on `@editor/editor-view` with the
  error's `:source`, output `"Error: " msg`. On success: clears diagnostics (when the value
  is non-nil), `song-transport/activate-buffer!` with
  `{:on-beat … :on-event editor/highlight-range! :set-playing! …}`, then a `cond` on the
  final value (song / pattern / event map / nil / string / other), the legacy `:_`
  hot-swap, pending mutes, `report-structureless-signals!` and `report-unknown-sounds!`
  (the latter places warning diagnostics via `sound-check/keyword-ranges code …`).
  `evaluate-in-context!` is the silent AI `eval_preview` path.
- **Slider re-evaluation:** `slider-patch-and-eval!`, `fx-slider-patch-and-eval!`,
  `fx-select-patch-and-eval!`, `per-track-fx-slider-patch-and-eval!` and
  `per-track-fx-select-patch-and-eval!` patch the text of `@editor/editor-view` and then
  call `(evaluate! (.. view -state -doc (toString)))` after 150 ms.
- **Tracks and duplicates (SONG1):** `app/src/repulse/env/builtins/tracks.cljs` `"track"`
  calls `song-transport/collect-track! name' pat' src` and adds the name to
  `builtins/seen-tracks`. `app/src/repulse/song_transport.cljs` keeps per-evaluation
  `pending-tracks` (`[[kw pat] …]`), `pending-song`, `pending-play-from`, `pending-export`
  (`reset-pending!`). `collect-track!` throws
  `"Duplicate track name :kick — each track must have a unique name in the buffer"` (with
  the second definition's source) and `"track :x is defined after (song …) — define tracks
  above the song"`. `register-song!` resolves `:all` to the tracks collected so far.
  Because pending state is only reset once per `evaluate!`, anything evaluated inside the
  same evaluation (e.g. a used buffer) is automatically part of the duplicate check.
- **Other callers of evaluation:** `app/src/repulse/app.cljs`: `make-editor … eo/evaluate!`
  (Alt+Enter keymap in the editor and `(upd)` via `Ctrl-.`/`F9`), the global capture-phase
  `keydown` handler (`Alt+Enter` → `(eo/evaluate! (.. view -state -doc (toString)))`,
  `Ctrl+.`/`F9` → `(eo/evaluate! "(upd)")`), `on-play-btn-click`, the tap button
  (`"(tap!)"`), the command bar (`editor/make-cmd-editor … eo/evaluate!`) and
  `builtins/evaluate-ref` (used by `demo`, `load-gist`, `snippet`). `"upd"` in
  `env/builtins/tracks.cljs` re-evaluates `@editor/editor-view`'s text without resetting
  anything.

### Editor

- `app/src/repulse/ui/editor.cljs`: `editor-view` and `cmd-view` atoms;
  `highlights-field` + `highlight-range! [{:keys [from to]}]` (clamps to the current doc
  length, removes the range after 120 ms) and `clear-highlights!`; `save-listener`
  (`session/schedule-save!` on `docChanged`) and `clear-diag-listener`;
  `make-editor [container initial-value on-eval]` builds one `EditorState` with
  `history`, `lineNumbers`, `oneDarkTheme`, `lispLanguage`, syntax highlighting,
  `bracketMatching`, `highlights-field`, `lintGutter`, the two listeners, line wrapping
  and the keymap (`Escape`, `Alt-Enter` → `(on-eval doc)`, `Ctrl-.`/`F9` →
  `(on-eval "(upd)")`, `defaultKeymap`, `historyKeymap`). `make-editor` is also used by
  `app/src/repulse/embed.cljs` (`<repulse-editor>`), which must not change.
- `app/src/repulse/app.cljs` `init`: creates the view with the restored text, then
  `(.dispatch view #js {:effects (.of StateEffect.appendConfig #js [outline-panel/extension goto/extension])})`,
  `(outline-panel/init! view)`, `(set! (.-__REPULSE_EDITOR__ js/window) view)`. Many
  modules read or write `@editor/editor-view` directly: `ui/snippet_panel.cljs`
  `insert-snippet!`, `ui/snippet_submit_modal.cljs` `current-code`, `snippets.cljs`
  `snippet-builtin`, `content/first_visit.cljs`, `content/demos.cljs`,
  `content/tutorial.cljs`, `env/builtins/content.cljs` (`load-gist`), `ai/tools.cljs`,
  `ai/undo.cljs`, `ui/assistant_panel.cljs`. Keeping **one** `EditorView` and swapping its
  `EditorState` per buffer (`view.setState`) keeps all of them working on the active
  buffer with no change.

### Navigation (SONG3)

- `app/src/repulse/outline.cljs` (pure): `extract [tree text]` → `{:entries :partial?}`,
  `entry-at`, `definition-for [entries kind name]` (`:symbol` → def/defn/defsynth,
  `:keyword` → track; last match wins), `song-bars`, `playing-section`.
  `app/src/repulse/outline_test.cljs` loads the Lezer parser in node with
  `["./lisp-lang/parser.js" :refer [parser]]` and `(.parse parser text)`.
- `app/src/repulse/ui/goto.cljs`: `jump-stack` (atom of **integer** positions, max 50),
  `name-at`, `entries-of` (private), `goto-definition! [view pos]` (searches only the
  current state), `go-back! [view]`, `reveal! [view pos from to]`, `flash-field`,
  `extension` (F12 / Shift-F12 keymap + Mod-click `mousedown`).
- `app/src/repulse/ui/outline_panel.cljs`: atoms `entries`, `partial?`, `cursor-pos`,
  `collapsed?`, `song-open?`, private `view-ref`; `re-extract!` (private), `render!`,
  `schedule-render!`, `toggle-collapsed!`, `extension` (an `updateListener`: re-extract
  150 ms after `docChanged`), `init! [view]`. A `view.setState` call does **not** run update
  listeners, so the outline must be refreshed explicitly after a buffer switch.
- `e2e/outline.spec.ts` drives the real app page through `window.__REPULSE_EDITOR__`;
  `playwright.config.ts` compiles `test-harness app` and serves `app/public`.

### Persistence and sharing

- `app/src/repulse/session.cljs`: `current-version 2`, `storage-key "repulse-session"`,
  `editor-text-fn` (atom, set in `app.cljs`), `build-session-snapshot` →
  `{:v 2 :editor :bpm :fx [] :bank :sources :muted :midi}` sanitized by
  `specs/sanitize-session-v2`, `save-session!`, `schedule-save!` (300 ms debounce),
  `load-session` (accepts only `:v 2`), `migrate-legacy!` (Phase D keys
  `repulse-editor` / `repulse-bpm` → v2), `wipe!`.
- `app/src/repulse/specs.cljs` `sanitize-session-v2`: rebuilds the map key by key and
  checks `(s/valid? ::session …)`; returns nil for non-maps or `:v ≠ 2`.
- `app/src/repulse/app.cljs`: private `b64-encode` / `b64-decode`
  (`btoa(unescape(encodeURIComponent s))`), `encode-session` (`"#v2:" + b64(JSON)`),
  `decode-session` (`#v2:` and legacy `#v1:`), `share!` (copies
  `origin + pathname + hash`, `"URL copied to clipboard"`). Restore precedence in `init`:
  URL hash > `load-session` > `migrate-legacy!` > first visit
  (`first-visit/first-visit-setup!`). `(reset!)` (`env/builtins/session.cljs`) wipes and
  reloads.
- `app/src/repulse/session_test.cljs` mocks `localStorage` and uses
  `session/editor-text-fn` and `(:editor loaded)`.
- Snippets: S1/S3 (`snippets.cljs`, `ui/snippet_panel.cljs`,
  `ui/snippet_submit_modal.cljs`) append to / read from `@editor/editor-view` — i.e. the
  active buffer once buffers exist. That is exactly decision 3 ("snippets stay
  single-buffer"); no code change is needed there.

### Built-in registration and tests

- App builtins are merged in `app/src/repulse/env/builtins.cljs` `ensure-env!` from
  `env/builtins/*.cljs` `make-builtins [ctx]` maps.
- Grammar: `app/src/repulse/lisp-lang/repulse-lisp.grammar` `BuiltinName` (line ~45 has
  `"song" | "section" | "play-from"`), regenerate with `npm run gen:grammar`.
  `completions.js` (`{ label, type, detail }`, `song` at ~L253), `hover.js` (`DOCS` map,
  `play-from` at ~L1024), `app/src/repulse/content/builtin_meta.edn`
  (`:def` category `"special"`), `npm run gen:ai-docs` → `docs/ai/builtins.json`
  (`scripts/gen_ai_docs.mjs` also reads `hover.js`).
- `npm test` runs `packages/core/src/repulse/test_runner.cljs` (`:test` node target); new
  test namespaces must be added to both the `:require` list and `run-tests`.
- The test harness (`app/src/repulse/test_api.cljs`, `window.__REPULSE_TEST__`:
  `evalToEvents`, `renderOffline`, `songEvents`, `captureSong`, …) builds its own env and
  does not need `use`. Multi-buffer behaviour is tested with unit tests (pure store) and
  Playwright against the real app page.

## Design decisions

The four open questions were decided by the project owner (kept verbatim):

1. Alt+Enter semantics: always evaluate from `main`, or evaluate the current buffer only
   (fast iteration) with a separate "evaluate project" key?
   **Decided:** Alt+Enter in any buffer always evaluates the whole project starting from
   `main`. This keeps "evaluate top to bottom, define before use" true across the project
   and matches SONG1's per-evaluation duplicate-track detection. No separate
   current-buffer-only key in this phase.
2. Does `use` share one environment (simple) or give each buffer a namespace (`drums/kick`)?
   **Decided:** one shared environment. Defs and tracks from used buffers are visible
   unqualified in `main` (as in the Goal example). No namespaces in this phase.
3. Share-link size limits with multiple buffers — URL hash vs. Supabase-backed project share.
   **Decided:** share links encode the full project in the URL hash (a v3 hash format;
   existing `#v2:` links keep loading as a single-`main` project). If the encoded link
   exceeds a size limit, `share!` shows a clear error instead of producing a broken link.
   Snippets (S1/S3) stay single-buffer: inserting or submitting a snippet uses only the
   current buffer's text. Supabase-backed project sharing is out of scope (later phase).
4. Import/export a project as a single file (e.g. `.repulse` EDN) for backup and version control?
   **Decided:** yes. Export the whole project as one `.repulse` EDN file
   (`{:version 3 :main "main" :buffers [{:name … :code …} …]}`) and import it back,
   validated by the same sanitizer as the v3 session. Import replaces the current project.

### Decisions taken during spec expansion

Conservative choices, consistent with 1–4:

5. **Global source offsets.** Each buffer gets an offset base: `main` → 0, every other
   buffer → `1e9 × (1 + its index among the non-main buffers in tab order)`.
   `eval-string` gets an optional `{:offset n}` that shifts every reader source position
   (and reader-error position) by `n`. Errors, active-event highlights and the duplicate
   check then carry one global position. `repulse.project/locate` maps it back to
   `{:buffer :from :to}`. `main` keeps base 0, so a single-buffer project produces exactly
   today's positions. No buffer-specific code goes into `packages/lisp`, only the generic
   offset.
6. **`use` is a function, not a special form.** It is valid anywhere at evaluation time
   (recommended at the top of a buffer). Used buffers may `use` other buffers (DFS, in
   argument order). Cycle check happens before the "already evaluated" check; a buffer is
   evaluated at most once per project evaluation. A used buffer's final value is ignored:
   only `main`'s last form goes through the existing result `cond` (so a bare pattern at
   the end of `drums` does not play as `:_`). `use` returns
   `"=> used :drums :bass"` (all named buffers, including skipped ones).
7. **`use` only works inside a project evaluation** (Alt+Enter, play button, slider
   re-eval). The command bar, `(upd)`, AI `eval_preview`, snippet previews, demos /
   `load-gist` (`evaluate-ref`) and the `<repulse-editor>` embed keep their single-string
   evaluation. `use` raises the out-of-project error there. `(upd)` is unchanged: it
   re-evaluates the active buffer's text only. A project-aware `upd` is out of scope.
8. **Duplicate tracks across buffers** keep SONG1's single-buffer message when both
   definitions are in the same buffer. Across buffers the message names the first buffer
   and the error points to the second definition (the one evaluated later).
9. **Error output:** `"Error: <msg>"` when the error is in `main` (unchanged), else
   `"Error in :<buffer>: <msg>"`. A Lisp error without a position inside a used buffer is
   reported at the `use` argument keyword in the calling buffer, prefixed
   `"in buffer :<name>: "`.
10. **Buffer names** match `^[a-z][a-z0-9-]{0,31}$`, are unique, and a project has at most
    32 buffers. There is no UI to change which buffer is `main` (an imported file can name
    any buffer as main). Tabs stay in creation order; there is no drag-reordering.
    `main` can be renamed (the `:main` pointer follows) but not deleted. Renaming a buffer
    does **not** rewrite `(use :old)` references (no refactoring, as in SONG3).
11. **One `EditorView`, one `EditorState` per buffer** (`view.setState` on switch): per-buffer
    undo history and diagnostics, and every module that uses `@editor/editor-view` keeps
    working on the active buffer. AI tools (`read_buffer`, `propose_edit`, …), snippets,
    demos, tutorial and `load-gist` operate on the active buffer only.
12. **Slider patching** (context-panel sliders/selects) still patches the **active**
    buffer's text, as today. Only the follow-up re-evaluation changes: it now evaluates
    the project. A param of a track defined in a non-active buffer is applied live but
    not written back into that buffer's text. Retargeting the patcher is out of scope.
13. **Outline** shows the active buffer's entries (unchanged rendering). Go-to-definition
    searches the active buffer first (SONG3 rule: last match wins). Otherwise it searches
    the other buffers in tab order, and the first buffer with a match wins (its last
    match). The jump stack stores `{:buffer :pos}` and Shift+F12 switches back.
14. **Share size limit:** the full share URL may be at most **64 000** characters (below
    Safari's ~80 k limit; the hash never reaches the server). Share links always use
    `#v3:` (a single-`main` project included). `#v2:` and `#v1:` links are still decoded.
15. **`.repulse` file:** human-readable layout (one buffer per line, trailing newline),
    file name `<main>.repulse`, max 5 000 000 characters, parsed with `cljs.reader/read-string`.
    `:active` is not stored in the file; after import `main` is active. Import asks
    `js/confirm` first, does not stop playback and does not evaluate. Loading a share link
    doesn't evaluate either (same as today).
16. **Persisted v3 session** keeps the v2 keys, replaces `:editor` with
    `:main :active :buffers`, and stores the active tab. A v2 blob is migrated in memory
    on load and written back as v3 by the next save.
17. **Create / rename / delete UI** uses `js/prompt` / `js/confirm` (the same pattern as
    `insert-snippet!`'s `js/confirm`). A new buffer starts empty and becomes active. The
    proposed name is the first free `buffer-N` (N from 2). Validation errors go to the
    output line as `"Error: <reason>"`.

## Implementation

### 0. Session start

Run `npm run gen:grammar && npm run gen:ai-docs && npm run test` on the unmodified branch
(CLAUDE.md Rule 4) before touching code.

### 1. Lisp: offset-aware reading — `packages/lisp`

`reader.cljs`: add

```clojure
(defn shift-sources
  "Return form with every :source {:from :to} (SourcedVal field or metadata on
   lists/vectors/maps/symbols) shifted by n, recursively."
  [form n] …)

(defn read-all
  ([src] …existing…)
  ([src offset]
   (let [forms (try (read-all src)
                    (catch :default e
                      (let [d (ex-data e)]
                        (throw (if (and d (:from d) (pos? offset))
                                 (ex-info (ex-message e)
                                          (assoc d :from (+ offset (:from d))
                                                   :to   (+ offset (:to d))))
                                 e)))))]
     (if (pos? offset) (mapv #(shift-sources % offset) forms) forms))))
```

`shift-sources` must keep list/vector/map types (lists stay `seq?`, so the evaluator's
`case` dispatch is unchanged), and it must keep the reader's quasiquote/unquote wrapper
lists. Maps: shift keys and values.

`core.cljs`: `eval-string` gets a 3-arity `[src env {:keys [offset]}]`. The 2-arity calls it
with `nil`. It uses `(reader/read-all src (or offset 0))`. Everything else stays the same.
With offset 0 or nil, behaviour and positions are byte-identical to today.

Tests in `packages/lisp/src/repulse/lisp/eval_test.cljs` (vectors L1–L3 below).

### 2. Pure project model — `app/src/repulse/project.cljs` (new, ns `repulse.project`)

No DOM, no CodeMirror, no audio. A project is
`{:main "main" :active "main" :buffers [{:name "main" :code "…"} …]}`.

```clojure
(def MAX-BUFFERS 32)
(def OFFSET-STRIDE 1000000000)
(def MAX-IMPORT-CHARS 5000000)

(defn name-problem [project name])   ; nil | reason string (format, uniqueness)
(defn next-buffer-name [project])    ; first free "buffer-N", N ≥ 2
(defn add-buffer [project name code]); {:ok p'} | {:error reason}; new buffer active
(defn rename-buffer [project old new]); {:ok p'} | {:error reason}; :main/:active follow
(defn remove-buffer [project name])  ; {:ok p'} | {:error reason}; active → main if removed
(defn set-active [project name])     ; {:ok p'} | {:error reason}
(defn code-of [project name])        ; string or nil
(defn offset-bases [project])        ; {"main" 0 "drums" 1e9 …} (decision 5)
(defn locate [bases from to])        ; {:buffer name :from local :to local} or nil
(defn project->edn [project])        ; exact layout, see vector P6
(defn edn->project [text])           ; {:ok project} | {:error "Import failed: …"}
```

Reason strings (exact, reused by the UI with an `"Error: "` prefix):

| Situation | Reason |
|---|---|
| bad name format | `invalid buffer name "Drums!" — use a-z, 0-9 and -, starting with a letter (max 32)` |
| name taken (add/rename) | `a buffer named "drums" already exists` |
| 33rd buffer | `too many buffers (max 32)` |
| delete main | `the main buffer can't be deleted` |
| unknown name | `unknown buffer "foo"` |

`edn->project`: check `(count text) > MAX-IMPORT-CHARS` first, then
`cljs.reader/read-string` in try/catch, then map?, then `:version` = 3, then
`specs/project-problem` (step 3). Return `{:ok (assoc sanitized :active main)}` or one of the
exact errors in vector P7. `project->edn` builds the string by hand (vector P6) with
`pr-str` for each string value, so the output is stable and diff-friendly.

### 3. Specs — `app/src/repulse/specs.cljs`

- `(project-problem p)` → nil or the **first** problem, checked in this order:
  1. not a map → `expected a map`
  2. `:buffers` not a non-empty vector → `:buffers must be a non-empty vector`
  3. more than 32 → `too many buffers (max 32)`
  4. an element without string `:name` and string `:code` →
     `each buffer needs a :name and a :code string`
  5. a name not matching `^[a-z][a-z0-9-]{0,31}$` →
     `invalid buffer name "Drums!" — use a-z, 0-9 and -, starting with a letter (max 32)`
  6. a repeated name → `duplicate buffer name "main"`
  7. `:main` not one of the names → `main buffer "song" not found` (`pr-str` of `:main`,
     so a missing main reads `main buffer nil not found`)
- `(sanitize-project p)` → `{:main … :buffers [{:name :code} …]}` (only those keys) or nil.
- `(sanitize-session-v3 data)` → nil unless map with `:v 3` and a valid project. It
  rebuilds `:bpm :fx [] :bank :sources :muted :midi` exactly like `sanitize-session-v2`
  (extract a shared private helper), sets `:active` to `(:active data)` if that names a
  buffer, else to `:main`, and validates with a new `::session-v3` spec
  (`::v3 #{3}`, `::main`/`::active` strings, `::buffers` vector).
- `sanitize-session-v2` stays (used for migration and old hashes).

### 4. Session v3 and share links — `app/src/repulse/session.cljs`

- `current-version 3`. Replace `editor-text-fn` with `project-fn` (atom; returns the
  project map). Fallback when nil: `{:main "main" :active "main" :buffers [{:name "main" :code ""}]}`.
- `build-session-snapshot` →
  `{:v 3 :main :active :buffers :bpm :fx [] :bank :sources :muted :midi}` through
  `specs/sanitize-session-v3`. Invalid → warn and return the empty v3 session (single empty
  `main`, bpm 120).
- `(migrate-v2->v3 s2)` (pure): `(-> s2 (dissoc :editor) (assoc :v 3 :main "main" :active "main" :buffers [{:name "main" :code (:editor s2)}]))`.
- `load-session`: `:v 3` → `sanitize-session-v3`. `:v 2` → `sanitize-session-v2`, then
  `migrate-v2->v3`. Anything else → nil. `migrate-legacy!` builds the v2 map as today,
  sanitizes, migrates to v3 and stores the **v3** JSON.
- Move `b64-encode`, `b64-decode` and the hash logic out of `app.cljs` (so they're
  node-testable):
  - `(encode-share-hash snapshot)` → `"#v3:" + b64(JSON.stringify(clj->js snapshot))`.
  - `(decode-share-hash hash)` → a v3 session or nil. `#v3:` → `sanitize-session-v3`
    (bpm via `audio/coerce-bpm`). `#v2:` → v2 sanitize + `migrate-v2->v3`. `#v1:` → the
    existing v1 mapping, then migrate. Wrapped in try/catch like today.
  - `(def max-share-url-length 64000)`.
  - `(share-link base snapshot)` → `{:url u}` or
    `{:error "Share failed: link would be N characters (limit 64000). Export the project as a .repulse file instead."}`,
    where `N = (count u)` and `u = (str base (encode-share-hash snapshot))`.
- `app.cljs` `share!`: `(session/share-link (str origin pathname) (session/build-session-snapshot))`.
  On `:error`, `(set-output! msg :error)` and copy nothing. Otherwise, same as today.
  `decode-session` becomes `session/decode-share-hash`.

### 5. Project evaluation — `app/src/repulse/project_eval.cljs` (new, ns `repulse.project-eval`)

Requires `repulse.lisp.core`, `repulse.lisp.eval` (for `typo-hint`, `unwrap`,
`source-of`), `repulse.project` and `repulse.song-transport`. It has no DOM.

```clojure
(defonce ^:private ctx (atom nil)) ; {:project :env :bases :stack [..] :done #{..} :order [..]}

(defn run-project
  "Evaluate (:main project) in env with `use` enabled.
   → {:value <{:result v} | EvalError> :order [names in start order] :bases {...}}"
  [project env] …)

(defn use-builtin [& args] …) ; registered as "use"
```

`run-project`:
1. `bases (project/offset-bases project)`; `reset! ctx` with
   `:stack [main] :done #{main} :order [main]`.
2. `(reset! song-transport/buffer-of #(some-> % :from (as-> f (:buffer (project/locate bases f f)))))`.
3. `(lisp/eval-string (project/code-of project main) env {:offset 0})`.
4. `finally`: `(reset! ctx nil)`, `(reset! song-transport/buffer-of nil)`.

`use-builtin` (args are `SourcedVal` keywords, as for `track`):
- no ctx → throw `use only works when the project is evaluated (Alt+Enter in the editor)`
- no args → throw `use needs at least one buffer name, e.g. (use :drums)`
- a non-keyword arg → throw `use expects buffer names as keywords, e.g. (use :drums) — got <pr-str v>`
- per keyword, in argument order (errors carry the keyword's own `:from/:to`, so they
  point into the calling buffer):
  - unknown → `Unknown buffer :drumz` plus `" — did you mean :drums?"` when
    `(leval/typo-hint "drumz" names)` finds one
  - on the stack → `Circular use: main → drums → bass → drums` (stack + name, joined with
    `" → "`)
  - in `:done` → skip
  - else push on the stack, append to `:order`, eval its code with
    `{:offset (bases name)}` in the ctx's env. On `EvalError`: if it has a `:source`,
    throw `ex-info` with the same message and `{:type :eval-error :from :to}` from that
    source. If not, throw `"in buffer :<name>: <msg>"` at the keyword. Pop the stack, add
    to `:done`.
- return `(str "=> used " (join " " (map #(str ":" %) names)))`.

`app/src/repulse/env/builtins/project.cljs` (new): `(defn make-builtins [_ctx] {"use" project-eval/use-builtin})`,
merged in `env/builtins.cljs` `ensure-env!`.

### 6. Cross-buffer duplicate tracks — `app/src/repulse/song_transport.cljs`

- New atoms: `pending-track-srcs` (`{kw src}`, cleared by `reset-pending!`) and
  `buffer-of` (nil or `fn src → buffer name`, set only by `run-project`).
- `collect-track!` records the source. On a duplicate: `a = (buffer-of first-src)`,
  `b = (buffer-of src)`. If both are non-nil and different →
  `"Duplicate track name :kick — already defined in buffer :drums; track names must be unique across the project"`.
  Otherwise the unchanged SONG1 message. The error still carries the second definition's
  source.

### 7. Orchestrator — `app/src/repulse/eval_orchestrator.cljs`

- Refactor the body of `evaluate!` into a private `evaluate* [code {:keys [project]}]`.
  `(evaluate! code)` = `(evaluate* code nil)`, unchanged for its callers.
- New `(evaluate-project!)`: reads `(buffers/current-project)` and runs the same pipeline,
  but step "eval" is `(project-eval/run-project project env)`. It keeps `:bases` as
  `last-bases` (an atom) **only on success**, for highlight mapping.
- Error branch (project mode): `loc (project/locate bases from to)`. If `(:buffer loc)` ≠
  active → `(buffers/switch-to! (:buffer loc))`. Then `buffers/clear-all-diagnostics!` and
  `set-diagnostics!` on the view with the local `from/to`. Output
  `"Error: " msg` if the buffer is `main`, else `"Error in :" buffer ": " msg`.
  Errors without a source: no diagnostic, output as before.
- Success branch (project mode): `buffers/clear-all-diagnostics!` (inactive states too),
  and `:on-event` / `play-track!` callbacks use
  `(fn [src] (when-let [{:keys [buffer from to]} (project/locate @last-bases (:from src) (:to src))] (when (= buffer (buffers/active-name)) (editor/highlight-range! {:from from :to to}))))`.
  Single-string `evaluate!` keeps `editor/highlight-range!`.
- `report-unknown-sounds!` gets the active buffer's text in project mode.
- The five `*-patch-and-eval!` functions call `(evaluate-project!)` instead of
  `(evaluate! (.. view -state -doc (toString)))` (decision 12).

### 8. Buffers UI — `app/src/repulse/ui/buffers.cljs` (new, ns `repulse.ui.buffers`)

State: `meta` atom (the project without codes: `:main :active :names`), `states` atom
`{name EditorState}`, `extensions` (JS array for new states), `switch-hooks` (vector of
fns called after every switch, used to break namespace cycles).

API:
- `(init! view project exts)`: one `EditorState` per buffer (`editor/make-state`), and the
  view shows the active one.
- `(active-name)`, `(current-project)` (codes from `states`, active from `(.-state view)`).
- `(switch-to! name)`: store the current state, `.setState view (states name)`,
  `editor/clear-highlights!`, run the hooks, `render-tabs!`, `session/schedule-save!`, focus.
- `(create-buffer! name code)` → `{:ok name}` | `{:error reason}` (SK3 uses this).
  `(rename-buffer! old new)`, `(delete-buffer! name)` and `(replace-project! project)`
  (import) apply the pure `repulse.project` ops, then update states and tabs and save.
- `(clear-all-diagnostics!)`: `setDiagnostics` on the view, and
  `(.-state (.update st (setDiagnostics st #js [])))` for inactive states.
- DOM: `build-dom!` in `app.cljs` adds `<div id="buffer-tabs" class="buffer-tabs"></div>`
  directly before `#editor-container`. `render-tabs!` writes an HTML string
  (`escape-html` on every name): one `button.buf-tab[data-buffer]` per buffer
  (`.active` on the active one, `.main` plus `title="main buffer — Alt+Enter evaluates from here"`
  on main), a `span.buf-close[data-act=delete]` "×" on every non-main tab,
  `button.buf-add[data-act=add]` "+", then `button[data-act=export]` "export" and
  `button[data-act=import]` "import" with a hidden `<input type="file" accept=".repulse">`.
  One delegated click listener: tab → switch. `+` → `js/prompt "New buffer name:"` with
  `(project/next-buffer-name p)` as default. `dblclick` on a tab →
  `js/prompt "Rename buffer:"`. `×` → `js/confirm "Delete buffer \"drums\"? This can't be undone."`.
  Export → `Blob` (`application/edn`), download `<main>.repulse`. Import → read
  `file.text()`, `project/edn->project`. On error `set-output!` with that message. On success
  `js/confirm "Replace the current project (N buffers) with <file name>?"`, then
  `replace-project!`, `goto/jump-stack` reset, output `"imported <file name> — N buffers"`.
  Cancelled prompts do nothing. Errors → `(set-output! (str "Error: " reason) :error)`.
- Expose `window.__REPULSE_BUFFERS__` = `#js {:project #(clj->js (current-project)) :switchTo switch-to! :create (fn [n c] (clj->js (create-buffer! n c)))}`
  next to `__REPULSE_EDITOR__` (test/debug handle).

### 9. Editor factory — `app/src/repulse/ui/editor.cljs`

- Extract the extension list into `(editor-extensions on-eval on-upd)` (`on-upd` is a
  0-arg fn bound to `Ctrl-.` and `F9`).
- Add `(make-state text exts)` and `(make-view container state)`.
- `make-editor [container initial-value on-eval]` stays (used by the embed):
  `(make-view container (make-state initial-value (editor-extensions on-eval #(on-eval "(upd)"))))`.

### 10. App wiring — `app/src/repulse/app.cljs`

- `exts = (.concat (editor/editor-extensions (fn [_] (eo/evaluate-project!)) #(eo/evaluate! "(upd)")) #js [outline-panel/extension goto/extension])`.
  This replaces the `StateEffect.appendConfig` dispatch. The embed still never gets the
  outline or goto.
- Restore: `active-sess` is now always v3 (`decode-share-hash` / `load-session` /
  `migrate-legacy!`). Project = `(select-keys active-sess [:main :active :buffers])`, or
  the single empty `main` for a first visit. Create the view with the active buffer's
  state, then `buffers/init!`, `outline-panel/init!`, and
  `(reset! session/project-fn buffers/current-project)`.
- `buffers/add-switch-hook!`: `outline-panel/refresh!` (new public fn: reset `view-ref`
  and `cursor-pos`, re-extract, render).
- Wire `goto/cross-buffer` (step 11).
- Global `keydown` Alt+Enter and `on-play-btn-click` → `(eo/evaluate-project!)`.
  Ctrl+./F9, tap, command bar and `builtins/evaluate-ref` keep `eo/evaluate!`.

### 11. Cross-buffer go-to-definition — `outline.cljs`, `ui/goto.cljs`

- `outline/project-definition [others kind name]` (pure). `others` = `[[buffer-name entries] …]`
  in tab order, without the active buffer. It returns `{:buffer b :entry e}` for the first
  buffer whose `definition-for` matches, or nil.
- `goto/cross-buffer` atom `{:find (fn [kind text] → {:buffer :entry}|nil) :switch! (fn [name])}`,
  set by `app.cljs`. `:find` parses each other buffer's code with
  `(.parse parser code)` (`"../lisp-lang/parser.js"`) and `outline/extract`.
- `goto-definition!`: if the active buffer has no match, call `:find`. On a hit, push
  `{:buffer active :pos head}`, call `switch!`, then `reveal!` on the view (now that
  buffer's state). Jump-stack entries become `{:buffer :pos}` everywhere (same-buffer jumps
  push the active name).
- `go-back!`: pop. If the entry's buffer no longer exists, drop it and do nothing. If it
  isn't active, `switch!` first. Then move the cursor as today.

### 12. CSS — `app/public/css/main.css`

Add a `/* Buffer tabs (SONG4) */` block: `.buffer-tabs` (height 26 px, `display:flex`,
background `#10172a`, bottom border `#1a2440`, mono font 11 px, horizontal scroll if
overflowing), `.buf-tab` (padding 0 10px, colour `#5a6a8a`), `.buf-tab.active`
(background `#1b2744`, colour `#c8d0e0`, 2 px top border `#e94560`), `.buf-tab.main`
(bold), `.buf-close`, `.buf-add`, and export/import buttons right-aligned. Don't touch
other blocks.

### 13. Built-in checklist for `use` (CLAUDE.md Rule 3)

1. Add `"use"` to `BuiltinName` in `app/src/repulse/lisp-lang/repulse-lisp.grammar`
   (next to `"song" | "section" | "play-from"`).
2. **Run `npm run gen:grammar`**: it regenerates `parser.js` (and `parser.terms.js` if
   it changes).
3. `completions.js`: `{ label: "use", type: "function", detail: "(use :drums :bass) — evaluate other project buffers first (once per evaluation, shared definitions)" }`.
4. `hover.js` `DOCS`: `"use"` with signature `(use :buffer …)`, a description (evaluates the named
   buffers in order before continuing; shared environment; each buffer at most once per
   evaluation; cycles are errors; only works when Alt+Enter evaluates the project) and
   example `(use :drums :bass)`.
5. `builtin_meta.edn`: `:use {:category "special" :returns "String" :side-effects []
   :examples ["(use :drums)" "(use :drums :bass)"] :see-also ["def" "track" "song"]}`.
6. **Run `npm run gen:ai-docs`**: it regenerates `docs/ai/builtins.json`.
7. Commit grammar, `parser.js`, `completions.js`, `hover.js`, `builtin_meta.edn` and
   `builtins.json` together.

### 14. Tests

- Unit (`npm test`): `packages/lisp/.../eval_test.cljs` (L1–L3); new
  `app/src/repulse/project_test.cljs` (P1–P7); new `app/src/repulse/project_eval_test.cljs`
  (E1–E8; the env is `leval/make-env` + `"use"` + a test `"track"` like `test_api`'s
  `song-env` + a `"mark!"` fn that appends to an atom); `session_test.cljs` (S1–S5;
  update existing tests from `editor-text-fn`/`:editor` to `project-fn`/`:buffers`);
  `song_transport_test.cljs` (the same-buffer duplicate message stays); `outline_test.cljs`
  (O1). Register `repulse.project-test` and `repulse.project-eval-test` in
  `test_runner.cljs` (`:require` and `run-tests`).
- Playwright: new `e2e/buffers.spec.ts` against `/` (same `load`/`setText` helpers as
  `e2e/outline.spec.ts`; use `page.on('dialog', d => d.accept(text))` for prompts). Clear
  `localStorage` before each test. `e2e/outline.spec.ts` must pass unchanged.

### 15. Docs

- `docs/USAGE.md`: new section **"Projects & buffers"** after "Writing a song" (tabs, main
  buffer, `use` with the Goal example, evaluation order and once-per-evaluation, shared
  environment, cycles, errors switching tabs, cross-buffer go-to-definition, what stays
  single-buffer: command bar, `(upd)`, snippets, AI tools, slider write-back). Add `use`
  to the reference tables. Update "Session persistence and sharing" (buffers persisted,
  `#v3:` links, 64 000-character limit, `.repulse` export/import with an example file).
  Add every new error from vectors E2–E7, P5, P7 and S5 to "Error messages".
- `README.md`: one line plus `(use :drums)` in the language reference / quick tour.
- `ROADMAP.md`: SONG4 → `✅ *delivered*` + **Delivered:**. In the Implementation-order
  table, Spec `outline` → `full`.
- `CLAUDE.md` phase table: if a SONG4 row exists, mark it delivered.

## Files to change

| File | Change |
|---|---|
| `packages/lisp/src/repulse/lisp/reader.cljs` | `shift-sources`, `read-all` 2-arity with offset |
| `packages/lisp/src/repulse/lisp/core.cljs` | `eval-string` 3-arity `{:offset n}` |
| `packages/lisp/src/repulse/lisp/eval_test.cljs` | L1–L3 |
| `app/src/repulse/project.cljs` | new: pure project model, bases/locate, EDN |
| `app/src/repulse/project_test.cljs` | new: P1–P7 |
| `app/src/repulse/project_eval.cljs` | new: `run-project`, `use-builtin` |
| `app/src/repulse/project_eval_test.cljs` | new: E1–E8 |
| `app/src/repulse/env/builtins/project.cljs` | new: registers `use` |
| `app/src/repulse/env/builtins.cljs` | merge the project builtins |
| `app/src/repulse/song_transport.cljs` | `pending-track-srcs`, `buffer-of`, cross-buffer duplicate message |
| `app/src/repulse/specs.cljs` | `project-problem`, `sanitize-project`, `sanitize-session-v3`, `::session-v3` |
| `app/src/repulse/session.cljs` | v3 snapshot/load/migration, `project-fn`, share hash encode/decode, `share-link` |
| `app/src/repulse/session_test.cljs` | S1–S5, existing tests moved to v3 |
| `app/src/repulse/eval_orchestrator.cljs` | `evaluate*`, `evaluate-project!`, error/highlight mapping, slider re-eval |
| `app/src/repulse/ui/editor.cljs` | `editor-extensions`, `make-state`, `make-view` |
| `app/src/repulse/ui/buffers.cljs` | new: buffer states, tabs, create/rename/delete/switch, export/import |
| `app/src/repulse/ui/goto.cljs` | `cross-buffer` atom, `{:buffer :pos}` jump stack |
| `app/src/repulse/ui/outline_panel.cljs` | public `refresh!` |
| `app/src/repulse/outline.cljs`, `outline_test.cljs` | `project-definition` + O1 |
| `app/src/repulse/app.cljs` | tab DOM, state-based editor creation, v3 restore, `share!` via `share-link`, Alt+Enter/play → `evaluate-project!`, hooks |
| `app/public/css/main.css` | buffer tab styles |
| `packages/core/src/repulse/test_runner.cljs` | register the two new test namespaces |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` + `parser.js` | `use` → `npm run gen:grammar` |
| `app/src/repulse/lisp-lang/completions.js`, `hover.js` | `use` entries |
| `app/src/repulse/content/builtin_meta.edn` + `docs/ai/builtins.json` | `:use` → `npm run gen:ai-docs` |
| `e2e/buffers.spec.ts` | new: W1–W8 |
| `docs/USAGE.md`, `README.md`, `ROADMAP.md`, `CLAUDE.md` | docs |

## Definition of done

### Reference test vectors

All buffer stores are written as `[[name code] …]` in tab order, `main` first unless
stated otherwise. `B` = 1 000 000 000 (`OFFSET-STRIDE`).

**Lisp offsets (`eval_test`)**
- **L1.** `(lisp/eval-string "(seq :bd :sd)" env {:offset 1000})` → events with
  `:source {:from 1005 :to 1008}` and `{:from 1009 :to 1012}`. The 2-arity result is
  unchanged (`{:from 5 :to 8}`, `{:from 9 :to 12}`).
- **L2.** `(lisp/eval-string "(qqqqqqqq 1)" env {:offset 1000})` → `EvalError`, message
  `"Undefined symbol: qqqqqqqq"`, `:source {:from 1001 :to 1009}`.
- **L3.** `(lisp/eval-string "(seq \"ab" env {:offset 1000})` → message
  `Unterminated string — missing closing "`, `:source {:from 1005 :to 1006}`.

**Pure project model (`project_test`)**
- **P1.** `offset-bases` of `[main drums bass kit scale]` (main = `"main"`) →
  `{"main" 0 "drums" B "bass" (* 2 B) "kit" (* 3 B) "scale" (* 4 B)}`. With tab order
  `[drums main]` → `{"main" 0 "drums" B}`.
- **P2.** `(locate {"main" 0 "drums" B} (+ B 5) (+ B 10))` → `{:buffer "drums" :from 5 :to 10}`.
  `(locate … 3 7)` → `{:buffer "main" :from 3 :to 7}`. `(locate … nil nil)` → nil.
- **P3.** From `p1 = {:main "main" :active "main" :buffers [{:name "main" :code ""}]}`:
  `(add-buffer p1 "drums" "")` → `{:ok {:main "main" :active "drums" :buffers [{:name "main" :code ""} {:name "drums" :code ""}]}}`.
  `(add-buffer p1 "Drums!" "")` →
  `{:error "invalid buffer name \"Drums!\" — use a-z, 0-9 and -, starting with a letter (max 32)"}`.
  `(add-buffer p1 "main" "")` → `{:error "a buffer named \"main\" already exists"}`.
  Adding a 33rd buffer → `{:error "too many buffers (max 32)"}`.
  `(next-buffer-name p1)` → `"buffer-2"`.
- **P4.** `(rename-buffer p1 "main" "song")` →
  `{:ok {:main "song" :active "song" :buffers [{:name "song" :code ""}]}}`. The code is not
  rewritten.
- **P5.** `(remove-buffer p1 "main")` → `{:error "the main buffer can't be deleted"}`.
  Removing the active `"drums"` from P3's result → active `"main"`.
  `(remove-buffer p1 "foo")` → `{:error "unknown buffer \"foo\""}`.
- **P6.** `project->edn` of
  `{:main "main" :active "drums" :buffers [{:name "main" :code "(use :drums)\n(song (section :a 4 :all))"} {:name "drums" :code "(track :kick (~ \"bd sd\"))"}]}`
  is exactly (with a trailing newline):
  ```
  {:version 3
   :main "main"
   :buffers [{:name "main" :code "(use :drums)\n(song (section :a 4 :all))"}
             {:name "drums" :code "(track :kick (~ \"bd sd\"))"}]}
  ```
  `edn->project` of that text → `{:ok {:main "main" :active "main" :buffers <same two buffers>}}`
  (round trip; `:active` resets to main).
- **P7.** `edn->project` errors (exact):
  - `"{:version 3 :main"` → `"Import failed: could not read EDN"`
  - `"[1 2]"` → `"Import failed: expected a map"`
  - `"{:version 2 :main \"main\" :buffers [{:name \"main\" :code \"\"}]}"` →
    `"Import failed: unsupported version 2 — expected :version 3"`
  - `"{:version 3 :main \"main\" :buffers []}"` → `"Import failed: :buffers must be a non-empty vector"`
  - `"{:version 3 :main \"main\" :buffers [{:name \"main\"}]}"` →
    `"Import failed: each buffer needs a :name and a :code string"`
  - `"{:version 3 :main \"main\" :buffers [{:name \"Drums!\" :code \"\"}]}"` →
    `"Import failed: invalid buffer name \"Drums!\" — use a-z, 0-9 and -, starting with a letter (max 32)"`
  - `"{:version 3 :main \"main\" :buffers [{:name \"main\" :code \"\"} {:name \"main\" :code \"\"}]}"` →
    `"Import failed: duplicate buffer name \"main\""`
  - `"{:version 3 :main \"song\" :buffers [{:name \"main\" :code \"\"}]}"` →
    `"Import failed: main buffer \"song\" not found"`
  - a 5 000 001-character string → `"Import failed: file too large (max 5 MB)"`

**Project evaluation (`project_eval_test`)**
- **E1. Order.** Store `[["main" "(use :drums :bass)\n(mark! :main)"] ["drums" "(use :kit)\n(mark! :drums)"] ["bass" "(use :kit :scale)\n(mark! :bass)"] ["kit" "(mark! :kit)"] ["scale" "(mark! :scale)"]]`
  → `:order ["main" "drums" "kit" "bass" "scale"]`. The marks atom is
  `[:kit :drums :scale :bass :main]` (`kit` evaluated exactly once). No error.
- **E2. Shared env.** `[["main" "(use :drums)\n(fast 2 kick)"] ["drums" "(def kick (seq :bd :sd))"]]`
  → `:value` is `{:result <pattern>}` with 4 events in cycle 0. `(use :drums)` alone in
  main → `{:result "=> used :drums"}`.
- **E3. Cycle.** `[["main" "(use :drums)"] ["drums" "(use :bass)"] ["bass" "(use :drums)"]]` →
  message `"Circular use: main → drums → bass → drums"`, `:source {:from (+ (* 2 B) 5) :to (+ (* 2 B) 11)}`,
  i.e. `locate` → `{:buffer "bass" :from 5 :to 11}`. `[["main" "(use :main)"]]` →
  `"Circular use: main → main"`.
- **E4. Unknown buffer.** `[["main" "(use :drumz)"] ["drums" ""]]` →
  `"Unknown buffer :drumz — did you mean :drums?"`, `:source {:from 5 :to 11}`.
  `(use :zzz)` → `"Unknown buffer :zzz"`.
- **E5. Argument errors.** `(use)` → `"use needs at least one buffer name, e.g. (use :drums)"`.
  `(use "drums")` → `"use expects buffer names as keywords, e.g. (use :drums) — got \"drums\""`.
  `(lisp/eval-string "(use :drums)" env)` outside `run-project` →
  `"use only works when the project is evaluated (Alt+Enter in the editor)"`.
- **E6. Error inside a used buffer.** `[["main" "(use :drums)"] ["drums" "(def k 1)\n(def x (qqqqqqqq 2 k))"]]`
  → `"Undefined symbol: qqqqqqqq"`, `:source {:from (+ B 18) :to (+ B 26)}` → locate
  `{:buffer "drums" :from 18 :to 26}`.
- **E7. Duplicate tracks** (with `song-transport/reset-pending!` before each run):
  - `[["main" "(use :drums)\n(track :kick (seq :sd))"] ["drums" "(track :kick (seq :bd))"]]` →
    `"Duplicate track name :kick — already defined in buffer :drums; track names must be unique across the project"`,
    `:source {:from 20 :to 25}` (main).
  - `[["main" "(track :kick (seq :sd))\n(use :drums)"] ["drums" "(track :kick (seq :bd))"]]` →
    `"… already defined in buffer :main; …"` (same template), `:source {:from (+ B 7) :to (+ B 12)}` (drums).
  - `[["main" "(track :kick (seq :bd)) (track :kick (seq :sd))"]]` → the unchanged
    `"Duplicate track name :kick — each track must have a unique name in the buffer"`.
- **E8. Cleanup.** After any `run-project` (success or error), `use` outside again gives
  the E5 out-of-project error, and `@song-transport/buffer-of` is nil.

**Session and share (`session_test`)**
- **S1. v2 → v3.** `(migrate-v2->v3 {:v 2 :editor "(seq :bd :sd)" :bpm 132 :fx [] :bank nil :sources [] :muted ["kick"] :midi {}})`
  → `{:v 3 :main "main" :active "main" :buffers [{:name "main" :code "(seq :bd :sd)"}] :bpm 132 :fx [] :bank nil :sources [] :muted ["kick"] :midi {}}`.
  A v2 blob in `localStorage` loads via `load-session` as exactly that map.
- **S2. v3 round trip.** With `project-fn` returning
  `{:main "main" :active "drums" :buffers [{:name "main" :code "(use :drums)"} {:name "drums" :code "(track :kick (seq :bd))"}]}`
  and bpm 132: `save-session!` then `load-session` → `:v 3`, the same `:main :active :buffers`, `:bpm 132`.
  A stored `:active "gone"` loads as `:active "main"`.
- **S3. Legacy keys.** `repulse-editor` = `"(seq :bd)"` and `repulse-bpm` = `"140"` →
  `migrate-legacy!` stores and returns
  `{:v 3 :main "main" :active "main" :buffers [{:name "main" :code "(seq :bd)"}] :bpm 140 :fx [] :bank nil :sources [] :muted [] :midi {}}`.
- **S4. Share round trip.** `(decode-share-hash (encode-share-hash snap))` = `snap` for S2's
  snapshot. The hash starts with `"#v3:"`. A `#v2:` hash built in the test from
  `{"v":2,"editor":"(seq :bd :sd)","bpm":120,"fx":[],"bank":null,"sources":[],"muted":[],"midi":{}}`
  decodes to `{:v 3 :main "main" :active "main" :buffers [{:name "main" :code "(seq :bd :sd)"}] :bpm 120 :fx [] :bank nil :sources [] :muted [] :midi {}}`.
  A `#v1:` hash still decodes, to a single `main`.
- **S5. Size limit.** `(share-link "https://repulse.app/" snap)` with a 60 000-character
  `main` → `{:error msg}`. `msg` matches
  `#"Share failed: link would be (\d+) characters \(limit 64000\)\. Export the project as a \.repulse file instead\."`,
  and the number equals the length of the URL that would have been produced. S2's
  snapshot → `{:url u}` with `(<= (count u) 64000)`.

**Outline (`outline_test`)**
- **O1.** `(project-definition [["drums" (entries-of "(def kick 1)\n(track :kick (seq :bd))")] ["bass" (entries-of "(def kick 2)")]] :symbol "kick")`
  → `{:buffer "drums" :entry <def kick at 0>}`. `:keyword ":kick"` → drums' track entry.
  `"nope"` → nil.

**Playwright (`e2e/buffers.spec.ts`)**
- **W1.** First load: one tab `main` (`.buf-tab.main.active`), and `__REPULSE_BUFFERS__.project()`
  has one buffer.
- **W2.** `+` with prompt `drums` creates and activates `drums`. Double-click → `kit`
  renames it. `+` with `Drums!` shows output `Error: invalid buffer name "Drums!" — …`
  and creates nothing. Switching tabs shows each buffer's text. After a reload, tabs
  `main, kit`, active tab and texts are restored. `×` on `kit` + confirm deletes it;
  `main` has no `×`.
- **W3.** Project `main` = `(use :drums)\n(song (section :a 4 :all))`, `drums` =
  `(track :kick (seq :bd :sd))`. Alt+Enter while `drums` is active → output starts with
  `song: 1 sections`, no error, and `drums` stays active.
- **W4.** `drums` = `(track :kick (seq :bd))\n(def x (qqqqqqqq 2 kick))`, Alt+Enter with
  `main` active → `drums` becomes active, `.cm-lintRange-error` covers `qqqqqqqq`
  (diagnostic `from 32 to 40`), and the output is `Error in :drums: Undefined symbol: qqqqqqqq`.
- **W5.** Cmd/Ctrl-click on `kick` in `main` = `(use :drums)\n(fast 2 kick)` (drums =
  `(def kick (seq :bd))`) → `drums` active, cursor on line 1. Shift+F12 → `main`
  active, cursor back where it was.
- **W6.** With only `main`, `e2e/outline.spec.ts` passes unchanged and an Alt+Enter of
  `(seq :bd :sd)` plays with output `playing pattern — Alt+Enter to re-evaluate, (stop) to stop`.
- **W7.** Export → a download named `main.repulse` whose text satisfies P6's layout. Import
  of that file (via `setInputFiles` + confirm) replaces a different project. Importing
  `[1 2]` shows `Import failed: expected a map` and leaves the project unchanged.
- **W8.** Embed untouched: `app/src/repulse/embed.cljs` does not require
  `repulse.ui.buffers`, `repulse.project-eval` or `repulse.ui.goto`, and still calls the
  3-arity `editor/make-editor` (a code check in review — the e2e server doesn't rebuild
  the committed `embed.js`).

### Checklist

- [ ] Session v3 schema with buffers; a v2 session (and legacy keys, and `#v1:`/`#v2:`
      hashes) migrate to a project whose `main` buffer holds the old editor text — S1–S4.
- [ ] `(use :drums)` makes `drums`' defs and tracks available to `main`. Evaluation order is
      deterministic and each buffer runs once per evaluation — E1, E2 (unit test with a fake
      buffer store).
- [ ] Circular `use` reports a clear eval error naming the cycle, located in the buffer that
      closes it — E3. Unknown buffers and bad arguments give the E4/E5 messages.
- [ ] A duplicate track name across two buffers is reported as an error pointing to the
      second definition's buffer, and the same-buffer message is unchanged — E7.
- [ ] Source offsets: L1–L3 pass, and `eval-string` without an offset is byte-identical to
      before (existing lisp tests pass unchanged).
- [ ] Pure project model, EDN layout and import validation — P1–P7; `.repulse` export →
      import round-trips a project, and malformed files are rejected with the exact P7
      errors.
- [ ] A share link round-trips a multi-buffer project; an old `#v2:` link loads as a
      single-`main` project; an oversized project shows the S5 share error and copies
      nothing.
- [ ] Creating, renaming, switching and deleting buffers works and persists across reload
      (Playwright W1–W2).
- [ ] Alt+Enter in any tab evaluates the project from `main` (W3). An error in a used buffer
      switches to that buffer and shows the diagnostic at the right position (W4).
- [ ] Go-to-definition jumps into another buffer and Shift+F12 returns (W5, O1).
- [ ] A project with only `main` behaves exactly like today's single-buffer editor
      (existing unit and e2e tests pass, W6). The embed is unchanged (W8).
- [ ] Rule 3 checklist done for `use` (grammar + `gen:grammar`, `completions.js`,
      `hover.js`, `builtin_meta.edn` + `gen:ai-docs`). `gen:grammar` / `gen:ai-docs` show
      no drift afterwards.
- [ ] `docs/USAGE.md` documents projects, buffers, `use`, `#v3:` sharing, `.repulse` files
      and every new error message. README mentions `use`. ROADMAP marked delivered (order
      table Spec → `full`).
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5). Under
      `### New features` it covers `use` (the Goal example), Alt+Enter from a non-main
      tab, tab create/rename/switch/delete + reload, cross-buffer F12/Shift+F12,
      export/import and a `#v3:` share link. Under `### Warnings and errors` it has one
      checkbox per new message: circular use, unknown buffer (with and without hint),
      `(use)`, `(use "drums")`, `use` in the command bar (out-of-project),
      cross-buffer duplicate track, `Error in :drums: …`, share too large, each P7 import
      error (at least: could not read EDN, expected a map, unsupported version, duplicate
      name, main not found, file too large), and the buffer-name errors (invalid, already
      exists, main can't be deleted, too many buffers). Under `### Regressions`:
      single-buffer Alt+Enter, `(upd)`/F9, snippet insert into the active buffer, slider
      patch + re-eval, an old `#v2:` link, and SONG3 outline/F12 within one buffer.

## What NOT to do

- No collaborative editing of buffers — that is O3 (deferred).
- No per-buffer audio routing or per-buffer BPM.
- No MIDI capture into buffers — SK3 uses this phase's API.
- Don't build a file-system/Git sync; local + existing share/snippet mechanisms only.
- No namespaces (`drums/kick`), no current-buffer-only eval key, no Supabase project
  sharing, no multi-buffer snippets (decisions 1–3).
- Don't rewrite `use` references on rename, don't retarget slider patching to other
  buffers, and don't make `(upd)`, the command bar, AI tools or the embed project-aware
  (decisions 7, 10–12).
