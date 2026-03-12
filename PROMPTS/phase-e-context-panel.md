# Phase E — Session Context Panel

## Goal

Add a live sidebar to the right of the editor that shows the current session state
at a glance: BPM, user-defined names with inferred types, and the active effect
chain with bypass status and key parameter values.

```
┌───────────────────────────────────────────────────┬──────────────────────┐
│  editor                                           │  120 BPM  ◉ playing  │
│                                                   ├──────────────────────┤
│  (def kick (seq :bd :_ :bd :_))                   │ Bindings             │
│  (def snare (seq :_ :sd :_ :sd))                  │  kick     pattern    │
│  (stack kick snare)                               │  snare    pattern    │
│                                                   │  make-v   fn         │
│                                                   ├──────────────────────┤
│                                                   │ Effects              │
│                                                   │  reverb   wet: 0.40  │
│                                                   │  delay  ⊘            │
│                                                   │  filter   freq: 800  │
└───────────────────────────────────────────────────┴──────────────────────┘
```

---

## Data sources

All data is already available as atoms — no new state is needed:

| Panel section | Source |
|---|---|
| BPM | `(Math/round (/ 240.0 (:cycle-dur @audio/scheduler-state)))` |
| Playing | `(audio/playing?)` |
| Bindings | `@env-atom`, filtered to user-defined names only |
| Effects | `@fx/chain` → each `{:name :plugin :bypassed?}` |

---

## Implementation

All changes are in `app/src/repulse/app.cljs` and `app/public/css/style.css`.
No other files need modification.

### 1. Layout — HTML structure

In `build-dom!`, split the main area into a flex row:

```clojure
"<div class=\"main-area\">"
"  <div id=\"editor-container\" class=\"editor-container\"></div>"
"  <div id=\"context-panel\" class=\"context-panel\">"
"    <div id=\"ctx-status\" class=\"ctx-status\"></div>"
"    <div id=\"ctx-bindings\" class=\"ctx-section\"></div>"
"    <div id=\"ctx-effects\" class=\"ctx-section\"></div>"
"  </div>"
"</div>"
```

### 2. CSS

```css
.main-area {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.editor-container {
  flex: 1;
  min-width: 0;
  overflow: auto;
}

.context-panel {
  width: 200px;
  flex-shrink: 0;
  border-left: 1px solid #3e4451;
  background: #21252b;
  overflow-y: auto;
  font-size: 12px;
  font-family: monospace;
  color: #abb2bf;
}

.ctx-status {
  padding: 6px 10px;
  border-bottom: 1px solid #3e4451;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
}

.ctx-bpm { color: #e5c07b; font-weight: bold; }
.ctx-playing { color: #98c379; }
.ctx-stopped { color: #5c6370; }

.ctx-section { padding: 6px 0; border-bottom: 1px solid #3e4451; }

.ctx-section-title {
  padding: 2px 10px 4px;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #5c6370;
}

.ctx-row {
  display: flex;
  justify-content: space-between;
  padding: 2px 10px;
  gap: 6px;
  white-space: nowrap;
  overflow: hidden;
}

.ctx-name   { color: #61afef; overflow: hidden; text-overflow: ellipsis; }
.ctx-type   { color: #5c6370; flex-shrink: 0; }
.ctx-param  { color: #56b6c2; flex-shrink: 0; }
.ctx-bypass { color: #e06c75; flex-shrink: 0; }
```

### 3. Value type inference

```clojure
(defn- infer-type [v]
  (cond
    (and (map? v) (fn? (:query v))) "pattern"
    (fn? v)                          "fn"
    (number? v)                      "number"
    (string? v)                      "string"
    (keyword? v)                     "keyword"
    (sequential? v)                  "list"
    :else                            "value"))
```

### 4. Tracking built-in names

After `ensure-env!` creates the initial environment, snapshot the built-in key set
so user `def`s can be separated:

```clojure
(defonce builtin-names (atom #{}))

;; In ensure-env!, after reset!:
(reset! builtin-names (set (keys @env-atom)))
```

User bindings are then `(remove @builtin-names (keys @env-atom))`.

### 5. Rendering function

```clojure
(defn- render-context-panel! []
  (when-let [status (el "ctx-status")]
    (let [bpm (Math/round (/ 240.0 (:cycle-dur @audio/scheduler-state)))
          playing? (audio/playing?)]
      (set! (.-innerHTML status)
            (str "<span class=\"ctx-bpm\">" bpm " BPM</span>"
                 "<span class=\"" (if playing? "ctx-playing" "ctx-stopped") "\">"
                 (if playing? "◉ playing" "◌ stopped") "</span>"))))

  (when-let [bindings-el (el "ctx-bindings")]
    (let [env       (or @env-atom {})
          builtins  @builtin-names
          user-defs (sort (remove builtins (keys env)))]
      (set! (.-innerHTML bindings-el)
            (if (empty? user-defs)
              "<div class=\"ctx-section-title\">Bindings</div><div class=\"ctx-row\" style=\"color:#5c6370\">—</div>"
              (str "<div class=\"ctx-section-title\">Bindings</div>"
                   (apply str
                          (map (fn [k]
                                 (str "<div class=\"ctx-row\">"
                                      "<span class=\"ctx-name\">" k "</span>"
                                      "<span class=\"ctx-type\">" (infer-type (get env k)) "</span>"
                                      "</div>"))
                               user-defs)))))))

  (when-let [effects-el (el "ctx-effects")]
    (let [chain @fx/chain]
      (set! (.-innerHTML effects-el)
            (if (empty? chain)
              "<div class=\"ctx-section-title\">Effects</div><div class=\"ctx-row\" style=\"color:#5c6370\">—</div>"
              (str "<div class=\"ctx-section-title\">Effects</div>"
                   (apply str
                          (map (fn [{:keys [name plugin bypassed?]}]
                                 (let [params (when (and plugin (not bypassed?))
                                               (try (js->clj (.getParams plugin))
                                                    (catch :default _ {})))
                                       key-param (first (seq params))
                                       param-str (when key-param
                                                   (str (cljs.core/name (first key-param))
                                                        ": "
                                                        (.toFixed (second key-param) 2)))]
                                   (str "<div class=\"ctx-row\">"
                                        "<span class=\"ctx-name\">" name "</span>"
                                        (if bypassed?
                                          "<span class=\"ctx-bypass\">⊘</span>"
                                          (when param-str
                                            (str "<span class=\"ctx-param\">" param-str "</span>")))
                                        "</div>")))
                               chain))))))))
```

### 6. Reactivity — `add-watch`

Call `render-context-panel!` whenever relevant state changes:

```clojure
(add-watch env-atom   ::ctx (fn [_ _ _ _] (render-context-panel!)))
(add-watch fx/chain   ::ctx (fn [_ _ _ _] (render-context-panel!)))
(add-watch audio/scheduler-state ::ctx (fn [_ _ _ _] (render-context-panel!)))
```

Add these watches at the end of `init`. Also call `render-context-panel!` once
immediately after adding the watches (initial render).

### 7. Playing status refresh

`audio/playing?` is not an atom — it reads `scheduler-state` — so the
`scheduler-state` watch already covers it. No extra polling needed.

---

## Files to change

```
app/src/repulse/app.cljs    — build-dom!, ensure-env!, init, render-context-panel!
app/public/css/style.css    — main-area flex layout + context-panel styles
```

---

## What NOT to do

- No click-to-edit, no inline controls — read-only display only
- No plugin panel or oscilloscope in the context panel — those stay below the editor
- No persistence of panel state — it reflects live atoms only
- No virtualisation or pagination — a session rarely has more than 20 bindings
- Do not show built-in names (seq, stack, bpm, etc.) in the Bindings section
- Do not show bypassed effect parameters — just show the ⊘ indicator

---

## Acceptance criteria

- [ ] Context panel renders to the right of the editor without affecting editor width
- [ ] BPM updates immediately when `(bpm N)` is evaluated
- [ ] Defined names appear in Bindings after evaluating `(def name ...)`
- [ ] Re-defining a name updates its type in the panel without duplication
- [ ] Effect names, bypass state, and a key param appear in Effects
- [ ] `(fx :off :reverb)` toggles the bypass indicator in real time
- [ ] Panel scrolls independently when content overflows
- [ ] Layout holds correctly at 1024 px wide (panel at 200 px, editor fills the rest)
