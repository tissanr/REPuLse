# Phase F — Drum Machine Bank Prefix

## Goal

Add a `(bank :MachineName)` built-in that sets a global drum-machine prefix for the
current session. After `(bank :AkaiLinn)`, a bare keyword like `:bd` is automatically
resolved to the sample bank `AkaiLinn_bd` — no need to spell out the prefix every time.

```lisp
; Before this phase:
(seq (sound :AkaiLinn_bd 0) (sound :AkaiLinn_sd 0) :_ (sound :AkaiLinn_sd 0))

; After:
(do
  (bank :AkaiLinn)
  (seq :bd :sd :_ :sd))

; Clear / reset to defaults:
(bank nil)
```

---

## Background: sample bank naming in the CDN

The Strudel CDN's `tidal-drum-machines.json` manifest registers banks with names like
`AkaiLinn_bd`, `AkaiLinn_sd`, `AkaiLinn_hh`, `RolandTR808_bd`, `RolandTR808_sd`, etc.
— one bank per voice per machine, with the machine name and voice name joined by `_`.

When a user writes `(bank :AkaiLinn)`, the prefix is stored as `"AkaiLinn"`. During
playback, any keyword value `:kw` is resolved by first checking whether the bank
`"AkaiLinn_<kw>"` exists in the sample registry, and playing that if so. If the prefixed
bank does not exist (e.g. `:_` rest, or `:hh` for a machine that has no hi-hat entry),
the existing lookup cascade is used unchanged.

---

## Implementation

### 1. `samples.cljs` — bank prefix atom

Add a single atom to track the active prefix:

```clojure
;; Active drum machine prefix — nil means no prefix active.
;; When set, keyword :kw is looked up as "<prefix>_<kw>" first.
(defonce active-bank-prefix (atom nil))

(defn set-bank-prefix!
  "Set the global drum machine prefix. Pass nil to clear."
  [prefix]
  (reset! active-bank-prefix (when prefix (name prefix)))
  (js/console.log (str "[REPuLse] bank prefix: " (or (name prefix) "none"))))

(defn resolve-keyword
  "Resolve a keyword value against the active prefix.
   Returns the bank keyword to use for sample lookup:
   - If a prefix is active and \"<prefix>_<kw>\" exists → prefixed keyword
   - Otherwise → the original keyword unchanged"
  [kw]
  (if-let [pfx @active-bank-prefix]
    (let [candidate (keyword (str pfx "_" (name kw)))]
      (if (has-bank? candidate) candidate kw))
    kw))
```

No new dependencies. `has-bank?` already exists.

### 2. `audio.cljs` — use `resolve-keyword` in `play-event`

In the keyword branch of `play-event`, call `samples/resolve-keyword` before the
existing lookup cascade. **Nothing else in `play-event` changes.**

```clojure
;; Keyword — resolve prefix, then sample registry → Worklet → JS fallback
(keyword? value)
(let [resolved (samples/resolve-keyword value)]
  (cond
    (samples/has-bank? resolved) (samples/play! ac t resolved 0)
    :else (or (worklet-trigger! (name value) t)
              (js-synth ac t value))))
```

Note: the Worklet and JS-synth fallbacks intentionally use the **original** `value`,
not `resolved`, so that `:bd` still triggers the built-in kick if no prefixed bank
matched.

### 3. `app.cljs` — `(bank …)` Lisp built-in

Register `bank` in `ensure-env!` alongside the other audio built-ins:

```clojure
"bank"
(fn [prefix]
  (samples/set-bank-prefix! prefix)
  (str "bank: " (if prefix (name prefix) "cleared")))
```

The built-in accepts a keyword or `nil`. It returns a display string (shown in the
output line) rather than a Pattern so the scheduler ignores it.

---

## Context panel update

Add a **Bank** row to the context panel status bar (`#ctx-status`), next to BPM. It
shows the active prefix or is hidden when no prefix is set.

In `render-context-panel!`:

```clojure
(let [pfx @samples/active-bank-prefix]
  (set! (.-textContent bpm-el) (str bpm " BPM"))
  (if pfx
    (do (set! (.-textContent bank-el) (str "bank: " pfx))
        (set! (.-style bank-el) "display:inline"))
    (set! (.-style bank-el) "display:none")))
```

Add a `<span id="ctx-bank" class="ctx-bank"></span>` to the status bar HTML in
`build-dom!`. Add `add-watch` on `samples/active-bank-prefix` to trigger
`render-context-panel!`.

CSS — in `main.css`:

```css
.ctx-bank { color: #c678dd; font-weight: bold; }
```

---

## Behaviour specification

| Expression | Effect |
|---|---|
| `(bank :AkaiLinn)` | Sets prefix to `"AkaiLinn"`. `:bd` → `AkaiLinn_bd` if that bank exists |
| `(bank :RolandTR808)` | Switches prefix to `"RolandTR808"` |
| `(bank nil)` | Clears prefix — bare keywords use the default lookup cascade |
| `(bank :Nonexistent)` | Sets prefix even if no matching banks exist; bare keywords fall through to built-in synth |

Prefix lookup is **transparent**: if `AkaiLinn_bd` is not in the registry (e.g. the
machine has no hi-hat), the original keyword resolves normally. The user never gets a
hard error from a prefix miss.

Prefix is **global and immediate**: changing it mid-session affects the next scheduler
tick. No restart required.

---

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/samples.cljs` | Add `active-bank-prefix` atom, `set-bank-prefix!`, `resolve-keyword` |
| `app/src/repulse/audio.cljs` | Call `samples/resolve-keyword` in the keyword branch of `play-event` |
| `app/src/repulse/app.cljs` | Register `"bank"` built-in in `ensure-env!`; add bank display to context panel |
| `app/public/css/main.css` | Add `.ctx-bank` style |

No changes to `packages/core`, `packages/lisp`, or the Rust/WASM layer.

---

## Definition of done

- [ ] `(bank :AkaiLinn)` followed by `(seq :bd :sd :hh :sd)` plays AkaiLinn samples
- [ ] `(bank nil)` restores default behaviour — `:bd` plays the built-in kick
- [ ] Switching banks mid-session (evaluating a new `(bank …)`) takes effect immediately
- [ ] Prefix miss is silent — no error when `<prefix>_<kw>` is absent from the registry
- [ ] Context panel status bar shows the active bank name, or nothing when cleared
- [ ] `(bank :AkaiLinn)` inside a `do` block works: `(do (bank :AkaiLinn) (seq :bd :sd))`
- [ ] Core unit tests still pass (`npm run test:core`)
