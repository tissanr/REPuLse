# Phase AI3 — Tool-Using Agent

## Goal

Promote the AI assistant from a chat companion into an agent that can act on the
session: read the editor buffer, query session state, browse snippets, propose
unified-diff edits with an in-editor review UI, and silently preview code before
suggesting it. Every action that modifies the session requires explicit user
confirmation. No change is ever applied automatically (that opt-in arrives in AI4).

```
;; Before (AI2) — assistant can only describe; user must copy/paste:
;;   User: "Add a dub delay to the bass track"
;;   Assistant: "Try `(track :bass (>> bass-pat (delay 0.6 0.5)))`"
;;   User: copies, pastes, evaluates

;; After (AI3) — assistant proposes a diff the user clicks to apply:
;;   User: "Add a dub delay to the bass track"
;;   Assistant calls read_buffer → sees current editor text
;;   Assistant calls propose_edit → diff overlay appears in editor:
;;     - (track :bass bass-pat)
;;     + (track :bass (>> bass-pat (delay 0.6 0.5)))
;;   User clicks "Apply" → edit lands; or "Reject" → feedback returned to model
```

---

## Background

### Existing code the agent will call into

**Editor / buffer access**
- `app/src/repulse/app.cljs` — `editor-view` CodeMirror EditorView instance (global);
  `editor-state` atom updated after each eval. The agent reads `.state.doc.toString()`
  to get the full buffer text.
- `app/src/repulse/eval_orchestrator.cljs` — `evaluate!` is the top-level eval entry
  point the agent reuses for `eval_preview` (with a patched silent AudioContext).

**Session state**
- `app/src/repulse/audio.cljs` — `scheduler-state` atom holds `{:tracks {...} :bpm N
  :muted #{...}}`.
- `app/src/repulse/fx.cljs` — `chain` atom is the global FX chain; per-track `:fx-chain`
  lives inside each track in `scheduler-state`.

**Snippets**
- `app/src/repulse/snippets.cljs` — `search-snippets` and `insert-snippet!` already
  used by the snippet panel; AI3 calls them via the tool layer.

**AI panel (AI2 layer)**
- `app/src/repulse/ai/client.cljs` — `stream!` with `on-tool-call` callback added in
  this phase; tool results returned to the model via a second request in the agent loop.
- `app/src/repulse/ui/assistant_panel.cljs` — `add-message!` and `set-pending!` used by
  the agent loop to render intermediary state and tool-call status.

### CodeMirror patching for the diff overlay

CodeMirror 6 diff views use `StateEffect` + `Decoration` to highlight ranges. The diff
overlay for `propose_edit` uses:

```clojure
(defn apply-diff-decoration! [view from to]
  (let [effect (.-StateEffect (.-state view))]
    (.dispatch view
      #js {:effects [(.. effect (define) (of #js {:from from :to to}))]})))
```

A banner div below the editor renders "Apply / Reject" buttons; clicking "Apply" calls
`.dispatch` with a `ReplaceRange` transaction; clicking "Reject" removes decorations and
returns a synthetic tool result `{:rejected true :reason "user rejected"}` to the model.

---

## Implementation

### 1. `app/src/repulse/ai/tools.cljs` — tool registry

Define all tool descriptors and their executor functions:

```clojure
(ns repulse.ai.tools
  (:require [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.snippets :as snippets]))

(def tool-registry
  {:read_buffer
   {:description "Read the current editor buffer text."
    :params {}
    :side-effects #{:none}
    :execute (fn [_args]
               (let [view @repulse.app/editor-view]
                 {:ok true :text (.. view -state -doc toString)}))}

   :propose_edit
   {:description "Propose a unified-diff edit to the editor buffer. The user must approve before it is applied."
    :params {:from {:type "integer" :description "Start character offset (inclusive)"}
             :to   {:type "integer" :description "End character offset (exclusive)"}
             :replacement {:type "string" :description "Replacement text"}}
    :side-effects #{:edit}
    :execute propose-edit!}   ; see §2

   :eval_preview
   {:description "Evaluate REPuLse-Lisp code silently and return scheduled-event count and duration."
    :params {:code {:type "string" :description "REPuLse-Lisp expression to evaluate"}}
    :side-effects #{:audio}
    :execute eval-preview!}   ; see §3

   :query_session
   {:description "Return current session state: BPM, track names, muted tracks, active FX."
    :params {}
    :side-effects #{:none}
    :execute (fn [_] (query-session))}

   :query_track
   {:description "Return details for one track: pattern source, params, FX chain."
    :params {:name {:type "string"}}
    :side-effects #{:none}
    :execute (fn [{:keys [name]}] (query-track (keyword name)))}

   :find_snippet
   {:description "Search the snippet library by text query."
    :params {:q {:type "string"} :limit {:type "integer" :default 5}}
    :side-effects #{:network}
    :execute (fn [{:keys [q limit]}] (snippets/search-snippets q (or limit 5)))}

   :insert_snippet
   {:description "Insert a snippet by ID into the editor at the cursor."
    :params {:id {:type "string"}}
    :side-effects #{:edit}
    :execute (fn [{:keys [id]}] (snippets/insert-snippet! id))}

   :set_bpm_proposal
   {:description "Propose a BPM change; user must confirm."
    :params {:bpm {:type "number"}}
    :side-effects #{:edit}
    :execute set-bpm-proposal!}})
```

### 2. `propose-edit!` — diff overlay

```clojure
(defn propose-edit! [{:keys [from to replacement]}]
  (let [p (js/Promise.
            (fn [resolve _reject]
              (show-diff-overlay! from to replacement
                {:on-apply  #(do (apply-edit! from to replacement)
                                 (resolve {:ok true :applied true}))
                 :on-reject #(resolve {:ok true :applied false
                                       :reason "user rejected"})})))]
    p))
```

`show-diff-overlay!` renders a banner div beneath the editor with the before/after
text and "Apply" / "Reject" buttons. The overlay is removed once the user acts.

### 3. `eval-preview!` — silent audio context

```clojure
(defn eval-preview! [{:keys [code]}]
  (js/Promise.
    (fn [resolve reject]
      (let [silent-ctx (js/AudioContext.)
            silent-gain (.createGain silent-ctx)]
        (set! (.. silent-gain -gain -value) 0)
        (.connect silent-gain (.-destination silent-ctx))
        (try
          (let [result (repulse.eval-orchestrator/evaluate-in-context!
                         code silent-ctx silent-gain)]
            (.close silent-ctx)
            (resolve {:ok true
                      :event-count (:event-count result)
                      :duration-bars (:duration-bars result)}))
          (catch :default e
            (.close silent-ctx)
            (reject {:ok false :error (.-message e)})))))))
```

`evaluate-in-context!` is a new thin wrapper around `evaluate!` that accepts a
caller-supplied audio context and master gain node instead of the global ones.

### 4. `app/src/repulse/ai/agent_loop.cljs` — bounded loop

```clojure
(defn run-agent-turn! [user-message]
  (go-loop [messages (conj @history {:role "user" :content user-message})
            call-count 0]
    (if (>= call-count max-tool-calls)
      (add-message! :assistant "I've reached the tool-call limit for this turn.")
      (let [response (<! (client/complete! messages tool-descriptors))]
        (if-let [tool-calls (:tool_calls response)]
          (let [results (<! (execute-tools! tool-calls))]
            (recur (conj messages
                         {:role "assistant" :content nil :tool_calls tool-calls}
                         {:role "tool" :content results})
                   (+ call-count (count tool-calls))))
          (do
            (add-message! :assistant (:content response))
            (swap! history conj {:role "assistant" :content (:content response)})))))))
```

`max-tool-calls` defaults to 8 per user turn and is configurable via AI4's budget
settings. A "Cancel" button in the panel calls `(.abort abort-controller)`.

### 5. Provider tool-call adapters

`client.cljs` gains an `on-tool-call` callback path alongside `on-chunk`. Each
provider's SSE delta format differs:

| Provider | Tool call format |
|---|---|
| OpenAI | `choices[0].delta.tool_calls[]` with `index`, `id`, `function.name`, `function.arguments` (streamed JSON) |
| Anthropic | `content_block_start` with `type: "tool_use"`, then `content_block_delta` with `input_json_delta` |
| Google | `candidates[0].content.parts[].functionCall` |
| Groq | Same as OpenAI (OpenAI-compatible) |

`tool-descriptors` converts `tool-registry` to provider-specific JSON Schema format:

```clojure
(defn ->openai-tools [registry]
  (mapv (fn [[k {:keys [description params]}]]
          {:type "function"
           :function {:name (name k)
                      :description description
                      :parameters {:type "object"
                                   :properties params}}})
        registry))
```

### 6. Edit-proposal UI wiring in `assistant_panel.cljs`

When a `propose_edit` tool call is initiated, the panel shows a status line
"Waiting for your approval…" until the user acts. The panel's abort button
cancels the whole agent turn (including any pending overlay).

---

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/ai/tools.cljs` | **New** — tool registry, executors |
| `app/src/repulse/ai/agent_loop.cljs` | **New** — bounded go-loop, tool dispatch |
| `app/src/repulse/ai/client.cljs` | Add `on-tool-call` callback, `complete!` fn, provider tool adapters |
| `app/src/repulse/eval_orchestrator.cljs` | Add `evaluate-in-context!` accepting caller-supplied AudioContext + gain |
| `app/src/repulse/ui/assistant_panel.cljs` | Add diff overlay render, tool-call status lines, Cancel button |
| `app/src/repulse/app.cljs` | Export `editor-view` atom so `tools.cljs` can read it |
| `app/src/repulse/env/builtins.cljs` | Update `(ai "prompt")` to trigger agent loop if tools enabled |

---

## Definition of done

- [ ] `read_buffer` returns the full current editor text as a string
- [ ] `propose_edit` shows a diff overlay with Apply / Reject; applying the diff
      updates the editor; rejecting feeds `{:applied false}` back to the model
- [ ] `eval_preview` evaluates `(seq :bd :sd :bd :sd)` silently and returns
      `{:ok true :event-count 4 :duration-bars 1}` without audible output
- [ ] `query_session` returns current BPM, track names, muted tracks, and global FX
- [ ] `find_snippet` returns ≤5 results for query "euclidean drums"
- [ ] `insert_snippet` inserts a snippet by ID via the same path as manual insert
- [ ] `set_bpm_proposal` shows a confirmation prompt; confirming updates BPM
- [ ] Agent loop does not exceed `max-tool-calls` per turn; panel shows "limit
      reached" message when capped; Cancel button works mid-loop
- [ ] Tool-call adapters produce valid JSON Schema for OpenAI and Anthropic tool-use
      schemas (smoke-tested against both providers)
- [ ] `(ai "write a euclidean kick pattern")` triggers the agent loop and the
      assistant proposes a `propose_edit` diff that the user can apply
- [ ] Rejecting a `propose_edit` causes the assistant to receive feedback and
      produce a revised proposal (not silently stop)
- [ ] No tool can modify the session without the user clicking a confirmation UI
      element (buttons in diff overlay or BPM confirm prompt)
- [ ] `npm run test` passes

---

## What NOT to do

- Do not implement auto-apply (no change applies without user click — that is AI4).
- Do not add a token/cost budget system here — that is AI4.
- Do not build the Supabase server-relay — that is AI4.
- Do not add new Lisp built-ins beyond updating `(ai "prompt")` to use the agent loop.
- Do not support streaming tool-call results mid-stream in the diff overlay — resolve
  the full replacement text before showing the overlay.
- Do not read files outside the editor buffer or session state — no filesystem or
  network access beyond the defined tool list.
