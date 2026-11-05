# Phase SK2 — Session Snapshots & Eval History

> **Spec status:** full (expanded 2026-11-05 by the auto-phase pipeline from the outline;
> design questions 1–4 decided by the project owner 2026-11-05; decisions K1–K20 taken
> during expansion)
>
> **Automation:** 🤖 auto — snapshot storage, history append/collapse/evict, restore and diffing are pure functions over a storage-adapter protocol (in-memory adapter in unit tests); the history panel and reload persistence are Playwright-testable.

**Milestone:** M4 — Sketchpad
**Depends on:** SONG4 (snapshots must capture whole projects, not one buffer).

## Goal

Never lose an idea. Every project evaluation (Alt+Enter, play button) is recorded in a
bounded **eval history** you can scroll back through, preview as a line diff against
the current code and restore with one click. Named **snapshots** ("take A", "take B")
save the whole project — every buffer, BPM, bank prefix, sample sources, mutes — and
can be listed, restored, renamed, deleted and copied as a share link. Restoring never
destroys anything: the current state is saved to the history first. Everything is
stored locally in IndexedDB and survives reloads.

```lisp
;; Before: one auto-saved session slot (D2); undo is per-buffer only
;; (CodeMirror) and per assistant turn (AI4). An idea from ten minutes ago is gone.

;; After
(snapshot! "take A")              ; => snapshot "take A" saved
(snapshots)                       ; => ["take A"]
(restore! "take A")               ; => restored snapshot "take A" — previous state is history #7
(restore! 7)                      ; back to where you were (history entry #7)
(rename-snapshot! "take A" "verse")
(delete-snapshot! "verse")
(history)                         ; opens the history panel: => history: 7 evaluations, 0 snapshots
```

## Scope

- Eval history: one entry per project evaluation (success or error), identical
  consecutive states collapsed, bounded by 500 entries **and** 20 000 000 characters of
  stored state, oldest evicted first.
- Named snapshots of the full project state: save (overwrite by name), list, restore,
  rename, delete; never evicted automatically; at most 100.
- Restore (snapshot or history entry) always records the current state as a history
  "restore point" first.
- History panel: snapshots + evaluations, each row with time, status and a `+a -b`
  summary; click → read-only line diff against the current project, Restore; per
  snapshot Copy link / Rename / Delete; Clear history.
- Storage: content-addressed blobs in IndexedDB via a hand-written wrapper; pure logic
  over a storage-adapter protocol.

## Design decisions

The four open questions were decided by the project owner (kept verbatim):

1. Storage size: full copies vs. deltas; localStorage quota vs. IndexedDB.
   **Decided (2026-11-05): full copies, content-addressed, in IndexedDB.** Each distinct
   project state (the sanitized v3 map from `session/build-session-snapshot`) is stored
   once under its content hash; history entries and named snapshots reference blobs by
   hash, so repeated identical states cost almost nothing. No deltas — a corrupt delta
   breaks every later entry. Storage is IndexedDB via a small hand-written wrapper (no
   external library), separate from the D2 `"repulse-session"` localStorage slot, which
   stays unchanged. History is bounded by count (500) **and** total size (~20 MB),
   evicting oldest entries first and garbage-collecting unreferenced blobs; named
   snapshots are never evicted automatically. All history/snapshot logic (append,
   collapse, evict, GC, restore) is pure over a storage-adapter protocol: unit tests use
   an in-memory adapter, the app uses the IndexedDB adapter.
2. Should previewing a history entry play it (audition, like AI5's planned audition) or only show code?
   **Decided (2026-11-05): code only.** Preview shows the entry's code read-only with a
   line diff against the current buffer, plus Restore. No audio audition — extending the
   S4 snippet sandbox to whole multi-buffer projects is out of scope; audition belongs to AI5.
3. Cloud sync now or later?
   **Decided (2026-11-05): later.** Local-only in SK2. Supabase sync needs a table, RLS,
   a migration and auth flow (human-in-loop) and is a separate follow-up phase.
4. Should snapshots be shareable as links (reuse `share!`)?
   **Decided (2026-11-05): yes, minimal.** A "Copy link" action per named snapshot calls
   the existing `session/share-link` with the snapshot's stored project state (it already
   takes the snapshot as an argument). If the link is too long or invalid, surface
   `share-link`'s existing `:error` result; no new backend or URL format.

### Decisions taken during spec expansion

Simplest options consistent with 1–4 and the existing code:

- **K1. What is recorded.** Only *project* evaluations are recorded:
  `eval-orchestrator/evaluate-project!` called from Alt+Enter (editor keymap and the
  global keydown handler) and the play button. Slider/inspector re-evaluations
  (`schedule-slider-eval!`, 150 ms debounce, fires during drags) are **not** recorded —
  a single drag would otherwise push dozens of entries; the next Alt+Enter records the
  result. Single-string evaluations (`evaluate!`: command bar, `(upd)`/F9, tap, demos,
  `load-gist`, the embed, AI `eval_preview`) are not recorded — they don't represent a
  project state.
- **K2. What a state is.** The map from `session/build-session-snapshot` (sanitized v3:
  `:v :main :active :buffers :bpm :fx :bank :sources :muted :midi`), captured right
  **after** the evaluation (so a `(bpm 140)` in the code is part of it), with `:active`
  normalised to `:main` before hashing — the focused tab is UI state, not project
  state, and switching tabs must not defeat collapsing. Restoring therefore always shows
  the main buffer.
- **K3. Canonical serialisation.** Blob text = `pr-str` of the state with every map
  turned (recursively) into a `sorted-map-by` comparing `(pr-str key)`; vectors and
  seqs become vectors. Deterministic regardless of map insertion order (the 10-key
  session map is a hash map). Blobs are read back with `cljs.reader/read-string` and
  re-validated with `specs/sanitize-session-v3`.
- **K4. Hash.** `content-hash` = 16 lowercase hex chars: FNV-1a 32-bit over the UTF-16
  code units of the text, followed by FNV-1a 32-bit over the text reversed (code-unit
  order). 64 bits make an accidental collision among ≤ 600 live blobs negligible
  (~10⁻¹⁴); no probing. Dependency-free (`Math/imul`).
- **K5. Collapse.** A new eval entry whose hash equals the **newest** entry's hash
  replaces that entry in place: same `:id`, new `:t`, `:ok?`, `:error`, `:kind :eval`.
  No new id is used. Non-adjacent repeats are separate entries (they share the blob).
- **K6. Limits.** `MAX-ENTRIES 500`, `MAX-CHARS 20000000` (sum of `count` of every
  distinct blob referenced by history **or** snapshots — characters, ≈ bytes for code),
  `MAX-SNAPSHOTS 100`, snapshot name 1–64 characters after trimming. After every history
  append the oldest entry is dropped while `count > MAX-ENTRIES` or total `> MAX-CHARS`,
  but the newest entry is never dropped (a single oversized state, or snapshots alone
  above the budget, leave one history entry). Blobs no longer referenced are deleted.
- **K7. Snapshot names** are arbitrary strings, trimmed, 1–64 characters, unique.
  `snapshot!` with an existing name **overwrites** it (like `def`) and moves it to the
  newest position. 101st distinct name → error. Named snapshots are only removed by
  `delete-snapshot!` / the panel's Delete.
- **K8. Restore points live in the history**, not as auto-named snapshots (which would
  pile up forever because snapshots are never evicted). `restore!` first appends the
  current state as an entry with `:kind :restore-point` (`:ok? nil`) — unless the
  newest entry already has the same hash, in which case that entry is the restore
  point and nothing is written. The returned message names the entry id, and
  `(restore! <id>)` takes it back.
- **K9. `restore!` targets** a snapshot name (string) or a history entry id (integer).
- **K10. What a restore applies** — exactly what app init applies for a restored
  session: buffers via `buffers/replace-project!` (main tab shown, jump stack cleared),
  `audio/set-bpm!`, `samples/set-bank-prefix!` (nil clears), `samples/load-external!`
  for each `github` source not already in `@samples/loaded-sources`, and the mutes into
  `eval-orchestrator/pending-mutes` (applied after the next evaluation). It does **not**
  evaluate, stop playback or restore MIDI mappings — same as loading a share link or
  importing a `.repulse` file.
- **K11. Restore from inside an evaluation is deferred.** The `restore!` built-in
  resolves the target and writes the restore point synchronously (so errors surface as
  normal eval errors), then applies the state with `(js/setTimeout … 0)`, after the
  running evaluation (and its own history record) has finished. The panel applies
  immediately.
- **K12. In-memory mirror.** The coordinator keeps the whole store (index + blob texts,
  ≤ ~20 MB plus snapshots) in an atom, loaded once at app start; every mutation is
  computed purely, the atom is reset, and the resulting ops are written to IndexedDB in
  one `readwrite` transaction (write-behind). Built-ins are therefore synchronous.
- **K13. Before the store has loaded**, evaluations are queued and replayed after load
  (in order, with their original timestamps); built-ins throw
  `snapshot store is still loading — try again in a moment`. In the embed
  (`<repulse-editor>`) the store is never initialised and the built-ins throw
  `snapshots and history are only available in the full app`.
- **K14. IndexedDB unavailable** (private mode, `open` error/blocked): fall back to the
  in-memory adapter, `console.warn` once
  `[REPuLse] IndexedDB unavailable — history and snapshots last only until reload`, and
  `(history)` appends ` — not saved across reloads (IndexedDB unavailable)`. A failed
  write transaction (e.g. quota) logs `[REPuLse] Could not save history: <error name>`
  and keeps the in-memory state.
- **K15. Corrupt data on load** is repaired, never fatal: unreadable/invalid meta → empty
  store; entries/snapshots whose blob is missing are dropped; orphan blobs are deleted.
  A blob that fails `sanitize-session-v3` at restore time gives
  `snapshot "x" is unreadable (corrupt data)` / `history #n is unreadable (corrupt data)`.
- **K16. `(reset!)` does not touch the history store.** Snapshots are deliberate saves;
  `(reset!)` keeps wiping only the localStorage session and AI keys (unchanged).
  Clearing is explicit: the panel's **Clear history** (removes all history entries,
  keeps snapshots) and `delete-snapshot!`. Documented in USAGE.
- **K17. Diff.** Line diff with common prefix/suffix stripped, then LCS on the middle
  (ties → deletion first); if the middle is larger than 1 000 000 cells
  (`n × m`) it falls back to "all deleted, then all added". Lines are split on `"\n"`;
  `""` has zero lines. Project diff is per buffer by name. Preview direction:
  **old = the entry, new = the current live project** ("−" = only in the entry,
  "+" = added since). Row summaries compare an entry with the next older entry (the
  oldest entry against an empty project).
- **K18. History panel placement** follows the snippet library: a `#history-panel` div in
  the workbench below `#snippet-panel`, toggled by a new `hist` button in the top nav
  (next to `lib`) and opened by `(history)`. It renders the newest 100 evaluations and a
  "Show older" button for the next 100. Rename/Delete use `js/prompt` / `js/confirm`
  (the SONG4 buffer-tab pattern); Restore has no confirm (it is always undoable via the
  restore point).
- **K19. Typo hints** for unknown snapshot names use `repulse.lisp.eval/levenshtein`
  with distance ≤ 2 (stricter than symbol hints — names are short); closest name wins,
  ties → the newer snapshot.
- **K20. Errors are short strings** stored on the entry (first 200 characters of the
  eval error message); no source positions are stored.

## Background

### Evaluation — `app/src/repulse/eval_orchestrator.cljs`

- `evaluate* [code {:keys [project]}]` (private) does all the work: bumps
  `eval-generation`, `builtins/ensure-env!`, resets FX/seen-tracks/eval-warnings/master
  run/`song-transport/reset-pending!`, then either
  `(project-eval/run-project project env)` (project mode) or `(lisp/eval-string code env)`.
  On `lisp/eval-error?` it calls the private `report-eval-error! [result project set-output!]`
  (diagnostic + `"Error: " msg` or `"Error in :<buf>: " msg`). Otherwise it activates
  the buffer (`song-transport/activate-buffer!`), runs the result `cond` and the warning
  reporters. Its return value is currently unused.
- `evaluate! [code]` → `(evaluate* code nil)` — command bar, `(upd)`, tap, demos
  (`builtins/evaluate-ref`), the embed.
- `evaluate-project! []` → `(evaluate* (project/code-of p (:main p)) {:project p})` with
  `p = (buffers/current-project)`. Callers: `app.cljs` editor extension
  (`editor/editor-extensions (fn [_] (eo/evaluate-project!)) …`), the global capture-phase
  `keydown` handler (Alt+Enter), `on-play-btn-click`, and the private
  `schedule-slider-eval!` (150 ms debounce after any slider/select patch).
- `pending-mutes` (public atom) — keywords muted after the next evaluation creates tracks.
- `evaluate-in-context!` is the silent AI preview path (untouched).

### Session — `app/src/repulse/session.cljs`

- `build-session-snapshot []` → sanitized v3 map
  `{:v 3 :main :active :buffers [{:name :code}…] :bpm :fx [] :bank :sources [{:type "github" :id}] :muted ["kick"] :midi {"1" "amp"}}`
  via `specs/sanitize-session-v3`; reads the project through the `project-fn` atom
  (`buffers/current-project`, set in `app.cljs` init). Falls back to the empty session.
- `schedule-save!` (300 ms debounce) / `save-session!` write the single
  `"repulse-session"` localStorage slot; `load-session`, `migrate-legacy!`,
  `migrate-v2->v3`, `wipe!` (removes the session, legacy and `repulse:ai:` keys).
- `share-link [base snapshot]` → `{:url u}` or `{:error "Share failed: link would be N characters (limit 64000). Export the project as a .repulse file instead."}`.
  `encode-share-hash` / `decode-share-hash` (`#v3:` / `#v2:` / `#v1:`).
- `app.cljs` `share!` builds `base = origin + pathname`, calls `share-link` with
  `(session/build-session-snapshot)`, copies with `navigator.clipboard.writeText`
  (`"URL copied to clipboard"`), falls back to `js/prompt`, and shows `:error` with
  `set-output!`.

### Specs — `app/src/repulse/specs.cljs`

`sanitize-session-v3 [data]` → the sanitized map or nil (requires `:v 3`, a valid project
via `project-problem`, `:active` defaults to `:main`; common keys via the private
`sanitize-session-common`). This is the validator for every blob read back.

### Project & buffers (SONG4)

- `app/src/repulse/project.cljs`: `empty-project`, `buffer-names`, `code-of [p name]`,
  `offset-bases`, `locate`, `project->edn`, `edn->project`.
- `app/src/repulse/ui/buffers.cljs`: `current-project` (live codes), `active-name`,
  `switch-to!`, `replace-project! [p]` (rebuilds every `EditorState`, shows `(:active p)`,
  clears `goto/jump-stack`, runs switch hooks → outline refresh, `render-tabs!`,
  `session/schedule-save!`), `init!` (sets `window.__REPULSE_BUFFERS__`
  `{project, switchTo, create}`). Its import path (`import-file!`) is the model for
  "replace the project without evaluating".

### AI4 undo (reuse ideas, don't merge)

`app/src/repulse/ai/edit.cljs` `push-snapshot [s turn-id text]` (pure, bounded stack of
`{:turn-id :text}`, `max-undo-turns 20`) and `app/src/repulse/ai/undo.cljs`
(`record-pre-edit!`, `revert-last-turn!` on `@editor/editor-view`). SK2 copies the shape
— a pure, bounded update function plus a thin effectful wrapper — but keeps a separate
store; the AI undo stack is unchanged.

### App restore path — `app/src/repulse/app.cljs` `init`

After `buffers/init!`, `(reset! session/project-fn buffers/current-project)`, then for a
restored session: `audio/set-bpm! (audio/coerce-bpm bpm)`, `samples/set-bank-prefix!`,
`samples/load-external! (str "github:" id)` per source, and
`(reset! eo/pending-mutes (set (map keyword muted)))` after 500 ms. K10 reuses exactly
these calls. `samples/set-bank-prefix!` accepts nil to clear; `samples/loaded-sources`
is `[{:type :github :id "owner/repo" …} …]`.

### UI patterns

- `build-dom!` in `app.cljs`: top nav buttons `#tap-btn`, `#snippet-toggle-btn` (`lib`),
  `#share-btn`; the workbench holds `#buffer-tabs`, `#editor-container`,
  `#snippet-panel.snippet-panel.hidden`, `#cmd-bar`, `#track-panel`, `#plugin-panel`.
- `app/src/repulse/ui/snippet_panel.cljs`: `visible?` atom, `show-panel!` /
  `hide-panel!` / `toggle-panel!` (toggle the `hidden` class), delegated click handler
  on the panel (`wire-panel!`), HTML built as strings with
  `repulse.ui.html/escape-html`. CSS in `app/public/css/main.css` (`.snippet-panel`,
  `.snippet-panel.hidden { display: none; }`).
- The WS1 inspector (`app/src/repulse/ui/inspector.cljs`) is a column bound to the
  outline selection — not a fit for a list of history entries; the history panel uses
  the snippet-panel pattern instead (K18).

### Built-in registration

- App built-ins are maps from `make-builtins [ctx]` in `app/src/repulse/env/builtins/*.cljs`,
  merged in `app/src/repulse/env/builtins.cljs` `ensure-env!` (last: `b-project`).
  Built-ins receive reader-wrapped literals: unwrap with `repulse.lisp.eval/unwrap`
  (see `env/builtins/content.cljs` `load-gist`). Errors are thrown as `ex-info`;
  `lisp/eval-string` turns them into `EvalError` with the call's source range, and the
  orchestrator prints `Error: <msg>`. A string result is printed as-is
  (`=> used :drums` convention from `project-eval/use-builtin`).
- `env/builtins/session.cljs`: `share!`, `reset!`, `help-export` (`reset!` calls
  `session/wipe!` and reloads — unchanged by K16).
- Rule 3 surface: `BuiltinName` in `app/src/repulse/lisp-lang/repulse-lisp.grammar`
  (line ~53 `"upd" | "demo" | "tutorial" | "snippet" | "share!" |`, line ~71
  `"reset!" | "help-export" | "ai"`), `npm run gen:grammar` → `parser.js`;
  `completions.js` (`share!` at ~L160, `reset!` ~L265); `hover.js` `DOCS` (`reset!` at
  ~L1158; read by `scripts/gen_ai_docs.mjs`); `app/src/repulse/content/builtin_meta.edn`
  (`;; ── Help / session` block, `:help-export {:category "session" …}`);
  `npm run gen:ai-docs` → `docs/ai/builtins.json`.
- No existing built-in or grammar name collides with `snapshot!`, `snapshots`,
  `restore!`, `history`, `delete-snapshot!`, `rename-snapshot!`.

### Tests

- `npm test` compiles the `:test` build in `shadow-cljs.edn` (`:node-script`, main
  `repulse.test-runner/main`, source paths `app/src`, `packages/core/src`,
  `packages/lisp/src`). Only namespaces reachable from
  `packages/core/src/repulse/test_runner.cljs` are compiled: a new test namespace must be
  added to **both** its `:require` list and its `run-tests` call. The pure history
  namespace must therefore not require anything browser-only (`repulse.audio`,
  CodeMirror, DOM); requiring `repulse.specs`, `cljs.reader`, `clojure.string` and
  `repulse.lisp.eval` is fine (`session_test`, `project_test` already do).
- Playwright: `playwright.config.ts` runs `npx shadow-cljs compile test-harness app` and
  serves `app/public` on port 3100; every test gets a fresh browser context (empty
  localStorage **and** IndexedDB). `e2e/buffers.spec.ts` has the helpers to copy
  (`fresh`, `setText`, `text`, `altEnter`, `output`, `page.on('dialog', …)`), using
  `window.__REPULSE_EDITOR__`, `window.__REPULSE_BUFFERS__` and
  `window.__REPULSE_APP__.evaluate(code)` (= `eo/evaluate!`, command-bar style).
  `window.__REPULSE_TEST__` (`app/src/repulse/test_api.cljs`, test-harness page) is the
  offline-audio harness and is not used here.

## Implementation

### 0. Session start

Run `npm run gen:grammar && npm run gen:ai-docs && npm run test` on the unmodified branch
(CLAUDE.md Rule 4) before touching code.

### 1. Pure store — `app/src/repulse/history.cljs` (new, ns `repulse.history`)

Requires only `clojure.string`, `cljs.reader`, `repulse.specs`, `repulse.lisp.eval`
(for `levenshtein`). No DOM, no IndexedDB, no `js/Date`.

```clojure
(def MAX-ENTRIES 500)
(def MAX-CHARS 20000000)
(def MAX-SNAPSHOTS 100)
(def MAX-NAME 64)
(def MAX-DIFF-CELLS 1000000)
(def MAX-ERROR 200)
(def default-limits {:max-entries MAX-ENTRIES :max-chars MAX-CHARS})

;; ── hashing / serialisation (K3, K4)
(defn fnv1a32 [s])        ; unsigned 32-bit int, FNV offset 0x811c9dc5, prime 0x01000193,
                          ; per UTF-16 code unit: h = imul(h ^ c, prime) >>> 0
(defn content-hash [s])   ; (str (hex8 (fnv1a32 s)) (hex8 (fnv1a32 (reverse-code-units s))))
(defn canonical-str [x])  ; pr-str of x with maps → (sorted-map-by #(compare (pr-str %1) (pr-str %2)))
(defn state->blob [state]); (canonical-str (assoc state :active (:main state)))
(defn blob->state [text]) ; (specs/sanitize-session-v3 (reader/read-string text)) in try/catch → map or nil

;; ── store
;; {:next-id 1
;;  :history   [{:id :t :hash :kind (:eval|:restore-point) :ok? (bool|nil) :error (str|nil)} …] ; oldest first
;;  :snapshots [{:name :t :hash} …]   ; oldest write first, newest last
;;  :blobs     {hash text}}
(def empty-store {:next-id 1 :history [] :snapshots [] :blobs {}})

(defn meta-of [store])          ; (-> store (dissoc :blobs) (assoc :version 1))
(defn total-chars [store])      ; sum of (count text) over all :blobs (= referenced blobs after gc)
(defn snapshot-names [store])   ; newest first
(defn entries-newest-first [store])
(defn entry-by-id [store id])
```

Every mutation returns `{:store s' :ops ops}` (plus result keys) or `{:error msg}`.
Ops are `[:put-blob hash text]`, `[:put-meta edn-string]` (`(pr-str (meta-of s'))`),
`[:delete-blob hash]`, always in that order: all `:put-blob`s, then one `:put-meta`,
then all `:delete-blob`s (a crash between steps leaves only orphan blobs, which the next
load collects). A mutation that changes nothing returns `:ops []`.

```clojure
(defn append-eval
  "Record one project evaluation (K5 collapse, K6 eviction + GC)."
  ([store text now ok? error] …)
  ([store text now ok? error limits] …))      ; → {:store :ops :id}

(defn add-restore-point [store text now])      ; → {:store :ops :id} (K8; reuse newest if same hash)
(defn save-snapshot [store name text now])     ; → {:store :ops :status (:saved|:updated) :name trimmed} | {:error}
(defn delete-snapshot [store name])            ; → {:store :ops :name} | {:error}
(defn rename-snapshot [store old new])         ; → {:store :ops :old :new} | {:error}
(defn resolve-target [store target])           ; string name | integer id → {:text :label} | {:error}
(defn prepare-restore [store target current-text now])
  ;; resolve-target first (errors leave the store untouched), then add-restore-point
  ;; → {:store :ops :text <target blob> :label "snapshot \"take A\"" | "history #3" :restore-point id}
(defn restore-message [label restore-point])  ; "=> restored <label> — previous state is history #<id>"
(defn clear-history [store])                   ; history → [], GC; :next-id unchanged
(defn gc [store])                              ; drop unreferenced blobs → {:store :ops}
(defn load->store [meta-text blobs])           ; K15 repair → {:store :ops}

;; ── diff (K17)
(defn split-lines* [s])                        ; "" → [], else (vec (.split s "\n"))
(defn line-diff [old new])                     ; → [[:same|:del|:add line] …]
(defn project-diff [old-state new-state])
  ;; nil state = no buffers. → [{:buffer name :status (:same|:changed|:added|:removed) :lines […]} …]
  ;; order: buffers of new-state in order, then buffers only in old-state in old order
(defn diff-stats [pdiff])                      ; {:added n :removed m} over all buffers
(defn summary-text [{:keys [added removed]}])  ; "no changes" when both 0, else "+<added> -<removed>"

;; ── storage adapter protocol
(defprotocol HistoryStorage
  (-load [this on-done])   ; (on-done {:meta <edn string or nil> :blobs {hash text}}), or (on-done nil) on failure
  (-write! [this ops]))    ; apply ops in order

(defn memory-storage
  "In-memory adapter over an atom {:meta nil :blobs {}} — unit tests and the K14 fallback."
  ([] (memory-storage (atom {:meta nil :blobs {}})))
  ([a] (reify HistoryStorage …)))
```

Rules the functions must implement exactly:

- **Names** (`save-snapshot`, `rename-snapshot` new name): `str/trim`; length 1–64 else
  `snapshot name must be 1–64 characters`. Lookups (`delete`, `rename` old, `resolve`)
  also trim.
- **Unknown name:** `unknown snapshot "<n>" — did you mean "<m>"?` when the closest name
  by `leval/levenshtein` is ≤ 2 (ties → newer), else
  `unknown snapshot "<n>" — (snapshots) lists the saved names`. Names are printed with
  `pr-str`.
- `save-snapshot`: new name with 100 snapshots →
  `too many snapshots (max 100) — delete one with (delete-snapshot! "name")`. An
  existing name is removed from its position and re-appended (newest) with the new
  `:hash`/`:t`; `:status :updated`.
- `rename-snapshot`: target name taken by *another* snapshot →
  `a snapshot named "<new>" already exists`; keeps the position and `:t`; renaming to
  the same name returns `:ops []`.
- `resolve-target`: integer not in history → `unknown history entry #<id>`.
- `append-eval`: `error` is truncated to 200 characters (`subs`), stored only when
  `ok?` is false (else nil).
- `add-restore-point` / collapse never evict anything except via the K6 loop that runs
  after every append (shared private `enforce-limits`).
- `load->store`: meta text read with `cljs.reader/read-string` in try/catch; anything but
  a map with `:version 1` → start from `empty-store`. Keep history entries that are maps
  with integer `:id`, number `:t`, `:kind` in `#{:eval :restore-point}` and a `:hash`
  present in `blobs`; keep snapshots with a valid name, number `:t` and a present
  `:hash` (duplicate names → last wins). `:next-id` = max(meta `:next-id`, max id + 1, 1).
  The store is the meta map without `:version`, plus `:blobs`. Then `gc`. Ops: `[[:put-meta …] [:delete-blob h] …]` if the repaired meta differs from
  the loaded text's map or blobs were deleted, else `[]`.

### 2. IndexedDB adapter — `app/src/repulse/history_idb.cljs` (new, ns `repulse.history-idb`)

Hand-written wrapper, browser only (never required by a test namespace).

- `DB-NAME "repulse-history"`, `DB-VERSION 1`; `onupgradeneeded` creates object stores
  `"blobs"` (out-of-line keys: hash → text string) and `"meta"` (key `"store"` → EDN
  string).
- `(open! on-done)`: `js/indexedDB.open`; on success calls `(on-done adapter)` with a
  `reify` of `history/HistoryStorage`; on `onerror` / `onblocked` / missing
  `js/indexedDB` / exception calls `(on-done nil)`.
- `-load`: one `readonly` transaction over both stores: `meta.get("store")`,
  `blobs.getAllKeys()` + `blobs.getAll()` (zip into a map). Calls `on-done` on
  `oncomplete`, `(on-done nil)` on error.
- `-write!`: one `readwrite` transaction over both stores applying ops in order
  (`put(text, hash)`, `put(edn, "store")`, `delete(hash)`); `onerror`/`onabort` →
  `(js/console.warn "[REPuLse] Could not save history:" (.. tx -error -name))`.
  Empty op vectors open no transaction.

### 3. Coordinator — `app/src/repulse/history_store.cljs` (new, ns `repulse.history-store`)

Requires `repulse.history` only (no DOM, so it stays node-compilable, though it is tested
through Playwright). Holds the K12 mirror.

```clojure
(defonce store (atom history/empty-store))
(defonce status (atom :uninitialized))   ; :uninitialized | :loading | :ready
(defonce persistent? (atom false))
(defonce ^:private adapter (atom nil))
(defonce ^:private queued (atom []))     ; [[text now ok? error] …] before :ready (K13)

(defn init! [adapter-impl persistent])   ; :loading → -load → load->store → write ops →
                                         ; replay queued appends → :ready
(defn- commit! [{s :store ops :ops :as r}] (reset! store s) (history/-write! @adapter ops) r)
(defn- check-ready! [])                  ; throws the K13 errors for :uninitialized / :loading
(defn record-eval! [state ok? error])    ; state->blob; queue when :loading; ignore when :uninitialized
(defn save-snapshot! [state name])       ; → message string (throws ex-info on {:error})
(defn delete-snapshot! [name])
(defn rename-snapshot! [old new])
(defn begin-restore! [target state])     ; → {:state <sanitized v3 map> :message …}; throws on error
                                         ; and on blob->state nil (K15 corrupt message)
(defn clear-history! [])
(defn summary [])                        ; "history: 3 evaluations, 1 snapshot" (+ K14 suffix)
```

All timestamps come from `(js/Date.now)` here; the pure layer receives them as `now`.
Messages: `=> snapshot "take A" saved` / `=> snapshot "take A" updated`,
`=> snapshot "take A" deleted`, `=> snapshot "take A" renamed to "verse"`,
`restore-message` for restores, `=> history: 1 evaluation, 2 snapshots` (singular/plural
per count). The corrupt-blob message uses the `:label`:
`snapshot "take A" is unreadable (corrupt data)` / `history #3 is unreadable (corrupt data)`.

### 4. Orchestrator hook — `app/src/repulse/eval_orchestrator.cljs`

- Require `repulse.history-store` and `repulse.session`.
- `evaluate*` returns `{:ok? false :error (:message result)}` from the error branch and
  `{:ok? true}` from the success branch (wrap the existing bodies; behaviour unchanged).
  `evaluate!` keeps returning nil: `(evaluate* code nil) nil`.
- `evaluate-project!` gets an optional opts arity:

```clojure
(defn evaluate-project!
  ([] (evaluate-project! {:record? true}))
  ([{:keys [record?]}]
   (let [p (buffers/current-project)
         r (evaluate* (project/code-of p (:main p)) {:project p})]
     (when record?
       (history-store/record-eval! (session/build-session-snapshot)
                                   (:ok? r) (:error r)))
     nil)))
```

- `schedule-slider-eval!` calls `(evaluate-project! {:record? false})` (K1).
  All other existing callers keep calling the 0-arity.
- `record-eval!` must never throw into the evaluation: wrap its body in try/catch with
  `console.warn`.

### 5. History panel — `app/src/repulse/ui/history_panel.cljs` (new, ns `repulse.ui.history-panel`)

Requires `repulse.history`, `repulse.history-store`, `repulse.session`, `repulse.audio`,
`repulse.samples`, `repulse.ui.buffers`, `repulse.ui.html`. It must **not** require
`repulse.eval-orchestrator` or `repulse.env.builtins` (cycle: builtins → this ns); the
mute setter is injected.

```clojure
(defonce visible? (atom false))
(defonce ^:private cbs (atom {}))        ; {:set-output! f :set-pending-mutes! f}
(defonce ^:private selected (atom nil))  ; nil | [:entry id] | [:snapshot name]
(defonce ^:private shown (atom 100))     ; number of evaluation rows rendered
(defonce ^:private summary-cache (atom {})) ; {[old-hash new-hash] "+a -b"}

(defn apply-state! [state])  ; K10, then session/schedule-save! (replace-project! already does)
(defn restore! [target])     ; begin-restore! + apply-state! + set-output! message; errors → "Error: …"
(defn open! [] …) (defn close! [] …) (defn toggle! [] …) (defn render! [] …)
(defn init! [{:keys [set-output! set-pending-mutes!]}]) ; wires clicks, add-watch on history-store/store → render! when visible
```

Markup (all text through `escape-html`; selectors are what the e2e test uses):

```html
<div class="hist-bar">
  <span class="hist-title">history</span>
  <button data-act="clear">clear history</button>
  <button data-act="close">×</button>
</div>
<div class="hist-section">snapshots</div>                      <!-- only when any -->
<div class="hist-snap" data-name="take A">
  <span class="hist-name">take A</span><span class="hist-time">14:03:12</span>
  <button data-act="restore">restore</button><button data-act="link">link</button>
  <button data-act="rename">rename</button><button data-act="delete">delete</button>
</div>
<div class="hist-section">evaluations</div>
<div class="hist-entry hist-ok|hist-err|hist-rp" data-id="7" title="<error>">
  <span class="hist-id">#7</span><span class="hist-time">14:03:12</span>
  <span class="hist-kind">before restore</span>        <!-- restore points only -->
  <span class="hist-sum">+2 -1</span>
</div>
<button data-act="older">show older</button>            <!-- when more rows exist -->
<div class="hist-preview">                              <!-- when something is selected -->
  <div class="hist-preview-bar">#7 → now <button data-act="restore-selected">restore</button></div>
  <div class="hist-buf"><div class="hist-buf-name">main</div>
    <div class="hist-line hist-same">  (use :drums)</div>
    <div class="hist-line hist-del">- (fast 2 kick)</div>
    <div class="hist-line hist-add">+ (fast 4 kick)</div></div>
  <div class="hist-buf hist-buf-unchanged">drums — unchanged</div>
</div>
```

- Click on a `.hist-entry` / `.hist-snap` (not on a button) selects it and renders the
  preview: `history/project-diff (blob->state <entry blob>) (session/build-session-snapshot)`.
  Buffers with `:status :same` render as one `.hist-buf-unchanged` line; `:added` /
  `:removed` buffers get `(added)` / `(removed)` after the name. Time = `(.toLocaleTimeString (js/Date. t))`.
- `restore` (snapshot row) / `restore-selected` → `restore!`; `link` →
  `session/share-link base state` then clipboard like `app.cljs` `share!`, success output
  `link to snapshot "take A" copied to clipboard`, `:error` shown as-is (`:error` status);
  `rename` → `js/prompt "Rename snapshot:" name`; `delete` →
  `js/confirm "Delete snapshot \"take A\"? This can't be undone."`; `clear` →
  `js/confirm "Clear the evaluation history? Snapshots are kept."` then
  `history-store/clear-history!`, output `history cleared — snapshots kept`;
  `older` → `(swap! shown + 100)`; `close` → `close!`.
- Errors from these actions go to the output line as `"Error: " msg`.
- Test handle (set in `init!`): `window.__REPULSE_HISTORY__ =
  {ready: () => status === "ready", persistent: () => bool,
   entries: () => [{id, kind, ok, hash, error}] newest first,
   snapshots: () => names newest first, open: open!}`.

### 6. Built-ins — `app/src/repulse/env/builtins/history.cljs` (new, ns `repulse.env.builtins.history`)

`make-builtins [_ctx]`; registered in `app/src/repulse/env/builtins.cljs` `ensure-env!`
as `(b-history/make-builtins ctx)` after `b-project`. All arguments go through
`leval/unwrap`. Arity/type errors (exact, thrown as `ex-info`):

| Built-in | Signature | Returns | Arg error |
|---|---|---|---|
| `snapshot!` | `(snapshot! name)` | `"=> snapshot \"take A\" saved"` / `"… updated"` | `snapshot! expects one name string, e.g. (snapshot! "take A")` |
| `snapshots` | `(snapshots)` | vector of names, newest first (`[]` when none) | — |
| `restore!` | `(restore! name)` / `(restore! id)` | `restore-message`; applies via `setTimeout 0` (K11) | `restore! expects a snapshot name or a history number, e.g. (restore! "take A") or (restore! 12)` |
| `rename-snapshot!` | `(rename-snapshot! old new)` | `"=> snapshot \"take A\" renamed to \"verse\""` | `rename-snapshot! expects two name strings, e.g. (rename-snapshot! "take A" "verse")` |
| `delete-snapshot!` | `(delete-snapshot! name)` | `"=> snapshot \"take A\" deleted"` | `delete-snapshot! expects one name string, e.g. (delete-snapshot! "take A")` |
| `history` | `(history)` | `"=> history: 7 evaluations, 1 snapshot"`; opens the panel | — |

`snapshot!` and `restore!` read the current state with `(session/build-session-snapshot)`.
Every built-in first calls the coordinator's readiness check (K13). The panel opens via
`history-panel/open!`; `restore!` schedules `history-panel/apply-state!`.

### 7. App wiring — `app/src/repulse/app.cljs`

- `build-dom!`: add `<button id="history-toggle-btn" class="history-toggle-btn">hist</button>`
  after `#snippet-toggle-btn`, and `<div id="history-panel" class="history-panel hidden"></div>`
  after `#snippet-panel`. Click → `history-panel/toggle!`.
- `init`, right after `(reset! session/project-fn buffers/current-project)`:

```clojure
(history-panel/init! {:set-output!        set-output!
                      :set-pending-mutes! #(reset! eo/pending-mutes %)})
(history-idb/open!
  (fn [idb]
    (when-not idb
      (js/console.warn "[REPuLse] IndexedDB unavailable — history and snapshots last only until reload"))
    (history-store/init! (or idb (history/memory-storage)) (some? idb))))
```

- `embed.cljs` is not changed (K13: the store stays `:uninitialized` there).

### 8. CSS — `app/public/css/main.css`

`.history-toggle-btn` like `.snippet-toggle-btn`; `.history-panel` like `.snippet-panel`
(including `.history-panel.hidden { display: none; }`, max-height with scroll);
`.hist-entry`/`.hist-snap` rows (pointer, hover background, `.selected`), `.hist-ok`
(green dot), `.hist-err` (red dot), `.hist-rp` (muted); monospace `.hist-line` with
`white-space: pre`, `.hist-add` green tint, `.hist-del` red tint, `.hist-same` dim.
Use existing CSS variables/colours of the snippet panel.

### 9. Built-in checklist (CLAUDE.md Rule 3)

1. `BuiltinName`: add `"snapshot!" | "snapshots" | "restore!" | "rename-snapshot!" | "delete-snapshot!" | "history"`
   on the `"upd" | "demo" | … | "share!"` line.
2. **`npm run gen:grammar`** (regenerates `parser.js`).
3. `completions.js`, after `share!`:
   - `snapshot!` — `(snapshot! "take A") — save the whole project as a named snapshot (overwrites)`
   - `snapshots` — `(snapshots) — names of saved snapshots, newest first`
   - `restore!` — `(restore! "take A") or (restore! 12) — restore a snapshot or history entry (current state kept in history)`
   - `rename-snapshot!` — `(rename-snapshot! "take A" "verse") — rename a snapshot`
   - `delete-snapshot!` — `(delete-snapshot! "take A") — delete a snapshot`
   - `history` — `(history) — open the eval history & snapshots panel`
4. `hover.js` `DOCS`: one entry each (signature, description, example) mirroring the table
   in step 6.
5. `builtin_meta.edn`, in the "Help / session" block, `:category "session"`:
   `:snapshot!` (`:returns "String"`, `:side-effects ["storage"]`,
   `:examples ["(snapshot! \"take A\")"]`, `:see-also ["snapshots" "restore!" "history"]`),
   `:snapshots` (`"Vector"`, `[]`, `["(snapshots)"]`), `:restore!` (`"String"`,
   `["storage" "dom" "audio"]`, `["(restore! \"take A\")" "(restore! 12)"]`),
   `:rename-snapshot!` / `:delete-snapshot!` (`"String"`, `["storage"]`), `:history`
   (`"String"`, `["dom"]`, `["(history)"]`). Each `:see-also` names two or three of the
   other five.
6. **`npm run gen:ai-docs`** (regenerates `docs/ai/builtins.json`).
7. Commit grammar, `parser.js`, `completions.js`, `hover.js`, `builtin_meta.edn` and
   `builtins.json` together.

### 10. Tests

- Unit: new `app/src/repulse/history_test.cljs` (ns `repulse.history-test`) with vectors
  H1–H12 below. Register it in `packages/core/src/repulse/test_runner.cljs` — add
  `[repulse.history-test]` to `:require` **and** `'repulse.history-test` to `run-tests`.
  Helper: `(defn st [code] {:v 3 :main "main" :active "main" :buffers [{:name "main" :code code}] :bpm 120 :fx [] :bank nil :sources [] :muted [] :midi {}})`,
  `tA = (state->blob (st "A"))`, `hA = (content-hash tA)` etc.
- Playwright: new `e2e/history.spec.ts` (W1–W7) against `/`, helpers copied from
  `e2e/buffers.spec.ts`; wait for `window.__REPULSE_HISTORY__.ready()` after each
  `goto`/`reload`. Grant `['clipboard-read', 'clipboard-write']` for W5.
  `e2e/buffers.spec.ts`, `outline.spec.ts`, `workspace.spec.ts`, `inline-slider.spec.ts`
  must pass unchanged.

### 11. Docs

- `docs/USAGE.md`: new section **"Snapshots & history"** after "Session persistence and
  sharing": what is recorded (K1), collapse and limits (K5–K6), the six built-ins with
  examples, restore semantics (K8, K10: not evaluated, main tab shown, mutes after next
  eval), the panel (diff direction K17, copy link, clear history), storage (IndexedDB,
  local only, `(reset!)` keeps snapshots — K16, private-mode fallback K14). Add the six
  built-ins to the reference tables. In "`(reset!)` — wipe session" add one sentence that
  snapshots and history are kept. Add every new error message (step 6 table + the
  name/limit/unknown/corrupt/loading/embed messages) to "Error messages".
- `README.md`: one line plus `(snapshot! "take A")` / `(restore! "take A")` in the
  language reference.
- `ROADMAP.md`: SK2 → `✅ *delivered*` + **Delivered:**.
- `CLAUDE.md` phase table: if an SK2 row exists, mark it delivered.

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/history.cljs` | new: pure store, hashing, canonical blobs, diff, `HistoryStorage` protocol, `memory-storage` |
| `app/src/repulse/history_test.cljs` | new: H1–H12 |
| `app/src/repulse/history_idb.cljs` | new: IndexedDB adapter (`open!`) |
| `app/src/repulse/history_store.cljs` | new: in-memory mirror + write-behind coordinator |
| `app/src/repulse/ui/history_panel.cljs` | new: panel, preview diff, restore/apply, copy link, test handle |
| `app/src/repulse/env/builtins/history.cljs` | new: six built-ins |
| `app/src/repulse/env/builtins.cljs` | merge `b-history/make-builtins` |
| `app/src/repulse/eval_orchestrator.cljs` | `evaluate*` result map, `evaluate-project!` opts + `record-eval!`, slider re-eval not recorded |
| `app/src/repulse/app.cljs` | nav button, panel div, panel/store/IDB init |
| `app/public/css/main.css` | history button and panel styles |
| `packages/core/src/repulse/test_runner.cljs` | register `repulse.history-test` |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` + `parser.js` | six names → `npm run gen:grammar` |
| `app/src/repulse/lisp-lang/completions.js`, `hover.js` | six entries each |
| `app/src/repulse/content/builtin_meta.edn` + `docs/ai/builtins.json` | six entries → `npm run gen:ai-docs` |
| `e2e/history.spec.ts` | new: W1–W7 |
| `docs/USAGE.md`, `README.md`, `ROADMAP.md`, `CLAUDE.md` | docs |

## Definition of done

### Reference test vectors

`st`, `tX`, `hX` as in step 10. All values below were computed with the exact
algorithms of K3/K4/K17.

**Hashing and blobs (`history_test`)**
- **H1.** `(fnv1a32 "")` = `0x811c9dc5`, `(fnv1a32 "a")` = `0xe40c292c`,
  `(fnv1a32 "foobar")` = `0xbf9cf968`. `(content-hash "")` = `"811c9dc5811c9dc5"`,
  `(content-hash "a")` = `"e40c292ce40c292c"`, `(content-hash "foobar")` = `"bf9cf9686f5d6112"`.
- **H2.** `(state->blob (st "(seq :bd :sd)"))` is exactly
  `{:active "main", :bank nil, :bpm 120, :buffers [{:code "(seq :bd :sd)", :name "main"}], :fx [], :main "main", :midi {}, :muted [], :sources [], :v 3}`
  (149 characters, hash `"d76c73930618fa63"`). `(state->blob (assoc (st "(seq :bd :sd)") :active "x"))`
  and the same map built with its keys in reverse insertion order yield the identical
  string. `(count tA)` = 137, `hA` = `"55af15e1d7cae5c1"`,
  `hB` = `"82552244434d49bc"`, `hC` = `"133ff32f9f2214cf"`, `hD` = `"ba436872783b1bb2"`.
  A two-buffer state with `:midi {"1" "amp"}` and `:sources [{:type "github" :id "a/b"}]`
  serialises as
  `{:active "main", :bank nil, :bpm 120, :buffers [{:code "x", :name "main"} {:code "y\n\"q\"", :name "drums"}], :fx [], :main "main", :midi {"1" "amp"}, :muted [], :sources [{:id "a/b", :type "github"}], :v 3}`
  (buffer order preserved, map keys sorted).
- **H3.** `(blob->state tA)` = `(st "A")`. `(blob->state "{:v 2}")` = nil,
  `(blob->state "{{")` = nil.

**History (`history_test`)** — `s0 = empty-store`, default limits unless stated.
- **H4. Collapse.** Appending, in order, `[tA 1000 true nil]`, `[tA 2000 true nil]`,
  `[tB 3000 false "Undefined symbol: qqq"]`, `[tB 4000 true nil]`, `[tA 5000 true nil]`
  gives `:history`
  `[{:id 1 :t 2000 :hash hA :kind :eval :ok? true :error nil} {:id 2 :t 4000 :hash hB :kind :eval :ok? true :error nil} {:id 3 :t 5000 :hash hA :kind :eval :ok? true :error nil}]`,
  `:next-id 4`, `(set (keys :blobs))` = `#{hA hB}`. Ops of the first append:
  `[[:put-blob hA tA] [:put-meta m]]`; of the second: `[[:put-meta m]]`; the third
  returns `:id 2` and stores `:error "Undefined symbol: qqq"`. A 300-character error is
  stored as its first 200 characters.
- **H5. Count eviction.** Limits `{:max-entries 3 :max-chars 20000000}`, append tA, tB,
  tC, tD (t = 1, 2, 3, 4, all ok) → ids `[2 3 4]`, blobs `#{hB hC hD}`, ops of the last
  append exactly `[[:put-blob hD tD] [:put-meta m] [:delete-blob hA]]`.
  With `(save-snapshot s0 "keep" tA 0)` first: ids `[2 3 4]`, blobs `#{hA hB hC hD}`,
  last ops `[[:put-blob hD tD] [:put-meta m]]`.
  With default limits, 501 appends of distinct codes `"0"` … `"500"` → 500 entries, ids
  2 … 501.
- **H6. Size eviction.** Limits `{:max-entries 500 :max-chars 300}`: append tA, tB, tC →
  ids `[2 3]`, blobs `#{hB hC}` (`total-chars` 274). With snapshot `"keep"` = tA saved
  first: ids `[3]`, blobs `#{hA hC}`. Limits `{:max-entries 500 :max-chars 100}`, append
  tA → ids `[1]` (newest is never evicted).
- **H7. Snapshots.** From s0: save `"a"` tA t10 → `:status :saved`; save `"b"` tB t20;
  `(snapshot-names s)` = `["b" "a"]`; save `"a"` tC t30 → `:status :updated`, names
  `["a" "b"]`, ops `[[:put-blob hC tC] [:put-meta m] [:delete-blob hA]]`.
  `(rename-snapshot s "b" "c")` → names `["a" "c"]`. `(rename-snapshot s "a" "c")` →
  `{:error "a snapshot named \"c\" already exists"}`. `(delete-snapshot s "a")` → names
  `["c"]`, ops `[[:put-meta m] [:delete-blob hC]]`. `(delete-snapshot s "zzz")` →
  `{:error "unknown snapshot \"zzz\" — (snapshots) lists the saved names"}`.
  Save `"  x  "` → `:name "x"`. Save `"   "` or a 65-character name →
  `{:error "snapshot name must be 1–64 characters"}`. With snapshots `"s0"` … `"s99"`:
  save `"s100"` → `{:error "too many snapshots (max 100) — delete one with (delete-snapshot! \"name\")"}`,
  save `"s5"` → `:status :updated`. With `"take A"` saved,
  `(resolve-target s "take B")` → `{:error "unknown snapshot \"take B\" — did you mean \"take A\"?"}`.
- **H8. Restore.** `s1`: append tA t1000 (#1), save `"take A"` tA t2000, append tB t3000 (#2).
  - `(prepare-restore s1 "take A" tC 4000)` → `:text tA`, `:label "snapshot \"take A\""`,
    `:restore-point 3`; history ids `[1 2 3]`, #3 =
    `{:id 3 :t 4000 :hash hC :kind :restore-point :ok? nil :error nil}`.
    `(restore-message "snapshot \"take A\"" 3)` =
    `"=> restored snapshot \"take A\" — previous state is history #3"`.
  - On that result, `(prepare-restore s2 3 tA 5000)` → `:text tC`, `:label "history #3"`,
    `:restore-point 4` (#4 hash hA, `:kind :restore-point`).
  - `(prepare-restore s1 "take A" tB 4000)` → `:restore-point 2`, history unchanged,
    `:ops []` (newest entry already holds the current state).
  - `(prepare-restore s1 99 tC 4000)` → `{:error "unknown history entry #99"}`, and
    `(prepare-restore s1 "nope" tC 4000)` leaves no restore point (store untouched).
- **H9. Clear.** H5's store with snapshot `"keep"` → `(clear-history s)` → `:history []`,
  blobs `#{hA}`, `:next-id 5`; the next append gets id 5.
- **H10. Load & repair.** For `s` = H7's store right after `(delete-snapshot s "a")`:
  `(load->store (pr-str (meta-of s)) (:blobs s))` = `{:store s :ops []}`. Meta
  `{:version 1 :next-id 3 :history [{:id 1 :t 1 :hash hA :kind :eval :ok? true :error nil} {:id 2 :t 2 :hash "deadbeefdeadbeef" :kind :eval :ok? true :error nil}] :snapshots []}`
  with blobs `{hA tA hB tB}` → history `[#1]`, `:next-id 3`, ops
  `[[:put-meta m] [:delete-blob hB]]`. Meta text `"{{"` or `"[1 2]"` with blobs `{hA tA}`
  → `empty-store`, ops `[[:put-meta (pr-str (meta-of empty-store))] [:delete-blob hA]]`.
  `memory-storage` round trip: run H8's sequence (three mutations + both
  `prepare-restore` calls), writing each result's `:ops` in order to one `memory-storage`;
  then `-load` + `load->store` → exactly the final H8 store and `:ops []`.
- **H11. Line diff.**
  - `(line-diff "a\nb\nc" "a\nc\nd")` = `[[:same "a"] [:del "b"] [:same "c"] [:add "d"]]`
  - `(line-diff "" "x\ny")` = `[[:add "x"] [:add "y"]]`; `(line-diff "x" "")` = `[[:del "x"]]`
  - `(line-diff "a\nb" "a\nb")` = `[[:same "a"] [:same "b"]]`; `(line-diff "a" "b")` = `[[:del "a"] [:add "b"]]`
  - `(line-diff "a\n" "a")` = `[[:same "a"] [:del ""]]`
  - `(line-diff "(def a 1)\n(def b 2)\n(def c 3)\n(seq :bd)" "(def a 1)\n(def c 3)\n(def b 2)\n(seq :sd)")` =
    `[[:same "(def a 1)"] [:del "(def b 2)"] [:same "(def c 3)"] [:del "(seq :bd)"] [:add "(def b 2)"] [:add "(seq :sd)"]]`
  - Fallback: old = `"c"` + lines `"o1"`…`"o1000"`, new = `"n1"`…`"n1000"` + `"c"`
    (1001 × 1001 cells > 1 000 000) → 2002 ops, no `:same`, op 0 = `[:del "c"]`,
    op 1001 = `[:add "n1"]`. With 998 instead of 1000 (999 × 999 ≤ 1 000 000) → 1997
    ops, exactly one `:same` (`"c"`), op 0 = `[:add "n1"]`.
- **H12. Project diff.** old buffers `[["main" "(use :drums)\n(fast 2 kick)"] ["drums" "(def kick (seq :bd :sd))"]]`,
  new `[["main" "(use :drums)\n(fast 4 kick)"] ["bass" "(def b 1)"]]` →
  `[{:buffer "main" :status :changed :lines [[:same "(use :drums)"] [:del "(fast 2 kick)"] [:add "(fast 4 kick)"]]} {:buffer "bass" :status :added :lines [[:add "(def b 1)"]]} {:buffer "drums" :status :removed :lines [[:del "(def kick (seq :bd :sd))"]]}]`;
  `diff-stats` → `{:added 2 :removed 2}`; `summary-text` → `"+2 -2"`.
  `(project-diff nil (st "A\nB"))` → `[{:buffer "main" :status :added :lines [[:add "A"] [:add "B"]]}]`.
  Identical states → every buffer `:same`, `summary-text` `"no changes"`.

**Playwright (`e2e/history.spec.ts`, real app page)** — W1–W6 are consecutive steps of
one test (one page, state carries over); W7 is a separate test.
- **W1. Recording.** Fresh page: `__REPULSE_APP__.evaluate("(bpm 130)")` → `entries()`
  is `[]` (command-bar evaluations are not recorded). `main` = `(seq :bd :sd)`: Alt+Enter twice →
  `__REPULSE_HISTORY__.entries()` has 1 entry (`ok: true`); `(seq :bd :hh)` + Alt+Enter → 2;
  `(qqqqqqqq)` + Alt+Enter → 3, newest `ok: false`, `error` = `"Undefined symbol: qqqqqqqq"`.
- **W2. Panel.** Click `#history-toggle-btn` → `#history-panel` loses `hidden`; three
  `.hist-entry` rows, first has `data-id="3"` and class `hist-err`, last row's
  `.hist-sum` = `+1 -0`, middle row's = `+1 -1`.
- **W3. Preview & restore by click.** Click the `data-id="1"` row → `.hist-preview`
  contains `.hist-del` with `- (seq :bd :sd)` and `.hist-add` with `+ (qqqqqqqq)`.
  Click `[data-act="restore-selected"]` → editor text `(seq :bd :sd)`, output
  `=> restored history #1 — previous state is history #3`, still 3 entries (the
  current state already was #3, K8).
- **W4. Snapshots via built-ins.** `__REPULSE_APP__.evaluate('(snapshot! "take A")')` →
  output `snapshot "take A" saved`; edit to `(seq :cp)`; `evaluate('(restore! "take A")')`
  → output `restored snapshot "take A" — previous state is history #4`, and after the
  deferred apply the editor shows `(seq :bd :sd)`, entry #4 has `kind: "restore-point"`.
  `evaluate('(snapshots)')` → output `["take A"]`. `evaluate('(restore! "take B")')` →
  `Error: unknown snapshot "take B" — did you mean "take A"?`.
  `evaluate('(snapshot! 1)')` → `Error: snapshot! expects one name string, e.g. (snapshot! "take A")`.
- **W5. Copy link.** Snapshot row `take A` → `[data-act="link"]` → output
  `link to snapshot "take A" copied to clipboard`; the clipboard text starts with
  `http://127.0.0.1:3100/#v3:`; opening it in a new page shows `(seq :bd :sd)` in `main`.
- **W6. Rename / delete via dialogs.** Rename (prompt → `verse`) → row `data-name="verse"`;
  Delete (confirm) → no `.hist-snap` rows, `__REPULSE_HISTORY__.snapshots()` = `[]`.
  Clear history (confirm) → no `.hist-entry` rows.
- **W7. Reload persistence.** Fresh page, W1's three evaluations, then
  `evaluate('(snapshot! "take A")')`; `page.reload()` → `ready()` true, `persistent()`
  true, the same entry ids/hashes and `snapshots()` = `["take A"]`.
  `evaluate("(history)")` → output `history: 3 evaluations, 1 snapshot` and
  `#history-panel` visible. Then set the editor to `(seq :cp)` and `evaluate('(restore! "take A")')` → editor shows
  `(qqqqqqqq)` (the snapshot was taken while `main` was `(qqqqqqqq)`; the original vector
  expected `(seq :bd :sd)`, which is inconsistent with K2/K7).

### Checklist

- [ ] Each Alt+Enter / play-button evaluation (success **and** error) appends exactly one
      history entry; identical consecutive states collapse (H4, W1). Slider re-evals and
      command-bar evaluations are not recorded (W1).
- [ ] History is bounded by 500 entries and 20 000 000 characters with oldest-first
      eviction and blob GC; named snapshots are never evicted (H5, H6).
- [ ] Content hashes and canonical blobs match H1–H3 exactly.
- [ ] `(snapshot! "take A")`, `(snapshots)`, `(restore! "take A")` round-trip the full
      project state (buffers, BPM, bank, sources, mutes) — H7, H8, W4.
- [ ] `restore!` records the current state as a restore point first and that point is
      restorable with `(restore! <id>)` (H8, W4).
- [ ] `rename-snapshot!` / `delete-snapshot!` and the panel's Rename/Delete/Clear work
      (H7, H9, W6).
- [ ] The history panel lists entries and snapshots and restores one by click (W2, W3).
- [ ] The diff view shows added/removed lines between an entry and the current project
      (H11, H12, W3).
- [ ] "Copy link" produces a working `#v3:` link via `session/share-link` (W5); an
      oversized snapshot shows `share-link`'s `Share failed: …` error.
- [ ] History and snapshots survive reload (H10, W7); IndexedDB failure falls back to
      memory with the K14 warning; `(reset!)` leaves snapshots intact.
- [ ] Every error message in step 6 and K13–K15 is produced exactly as specified.
- [ ] Rule 3 checklist done for all six names (grammar + `gen:grammar`,
      `completions.js`, `hover.js`, `builtin_meta.edn` + `gen:ai-docs`); no drift after
      regenerating.
- [ ] `docs/USAGE.md` documents history and snapshots and lists the new errors; README
      mentions `snapshot!`/`restore!`; ROADMAP marked delivered.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass; existing e2e specs pass
      unchanged.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5) — see below.

## Test protocol

The implementation PR's `## Test protocol` (CLAUDE.md Rule 5) must have one checkbox for
each of the following, with the exact expected output taken from the code:

**New features:** `(snapshot! "take A")` (saved) and again (updated); `(snapshots)`;
`(restore! "take A")` then `(restore! <id from the message>)` back; `(restore! 1)`;
`(rename-snapshot! "take A" "verse")`; `(delete-snapshot! "verse")`; `(history)` and the
`hist` button; panel: preview diff of an older entry, restore by click, snapshot
Copy link (open the link in a new tab), Rename, Delete, Clear history, "show older"
(after > 100 evaluations, e.g. via a quick loop of edits); reload keeps everything.

**Warnings and errors** (each triggered once): each arg error of the step-6 table;
`snapshot name must be 1–64 characters`; `too many snapshots (max 100) — …` (may be
listed as covered by H7 only); `unknown snapshot "…" — did you mean "…"?` and the
no-hint variant; `a snapshot named "…" already exists`; `unknown history entry #99`;
`… is unreadable (corrupt data)` (covered by H3/unit test, or by editing the IndexedDB
blob in DevTools); `snapshot store is still loading — …` (unit/hard to trigger — say so);
`snapshots and history are only available in the full app` (in an embed page);
the K14 console warning (private window) and `[REPuLse] Could not save history: …`
(may be listed as not practically triggerable); a Copy link on a > 64 000-character
project → `Share failed: …`.

**Regressions:** Alt+Enter/play/(upd)/command bar behave as before; slider drags still
re-evaluate live and add no history entry; `(share!)`; `.repulse` export/import; `(reset!)` (session wiped,
snapshots kept); AI assistant undo (`revert-last-turn!`) unaffected; buffer tabs.

## What NOT to do

- No A/B playback comparison or audio audition of history entries (decision 2; AI5 /
  backlog "session comparison").
- No cloud sync / Supabase table for snapshots (decision 3) and no new URL format
  (decision 4).
- No deltas, no compression, no external IndexedDB library (decision 1).
- Don't replace CodeMirror undo or the AI4 undo stack, and don't merge their stores.
- Don't record slider re-evaluations or single-string (`evaluate!`) evaluations (K1);
  don't make `(reset!)` delete the history store (K16).
- No collaborative history (O3) and no "export history as story" HTML (backlog).
