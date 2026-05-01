# Phase P2 — Modular Effect Routing

Add advanced routing for the global and per-track effect chains: reorderable master
bus, parallel effect paths, and "Aux Sends" that route audio to named busses.

## Goal

Currently, the master FX chain is in a fixed order, and FX are mostly linear. Phase P2
breaks this by allowing users to define the chain order and split signals for parallel
processing.

```lisp
;; Global chain reordering
(fx-chain :distort :amp-sim :filter :reverb)

;; Parallel FX via stack
(track :pad
  (->> (slow 2 (seq :c3 :eb3))
       (synth :saw)
       (stack
         (fx :reverb 0.5)
         (->> (fx :distort 8) (fx :filter 400)))))

;; Aux Sends — route audio to a bus with its own FX
(bus :reverb-bus :audio)
(track :vocal (->> pat (fx :send :reverb-bus 0.5)))
```

---

## Background

### Current FX System

`app/src/repulse/fx.cljs` manages a global `chain` atom and per-track `fx-chain` in
`audio/track-nodes`.
- **Global**: `rewire!` has a hardcoded order (reverb → delay → filter → ...).
- **Per-track**: Order is determined by the sequence of `(fx ...)` calls in the pattern.

### Current Busses (Phase P)

`app/src/repulse/bus.cljs` provides named audio/control busses. They are used for
inter-synth communication but are not yet integrated into the FX system as destinations.

---

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/fx.cljs` | Add `set-chain-order!` to reorder the master chain; Update `rewire!` to use the defined order; Implement `fx-send` plugin logic. |
| `app/src/repulse/env/builtins.cljs` | Add `fx-chain` top-level command; Update `fx` builtin to handle `stack` of FX (parallel chains). |
| `app/src/repulse/audio.cljs` | Ensure bus nodes can receive audio from `fx-send` and route back to master. |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `fx-chain` and `fx-send` to `BuiltinName`. |
| `app/src/repulse/lisp-lang/completions.js` | Add completion entries for new commands. |
| `app/src/repulse/lisp-lang/hover.js` | Add documentation for `fx-chain` and `fx-send`. |
| `docs/USAGE.md` | Document reorderable chains and parallel routing. |

---

## Definition of done

### Master Chain Reordering
- [ ] `(fx-chain :distort :filter :reverb)` changes the processing order of the master bus
- [ ] Order persists and is reflected in the session dashboard
- [ ] `(fx-chain :default)` restores the standard musical order

### Parallel FX
- [ ] `(stack (fx :reverb 0.5) (fx :distort 8))` inside `->>` splits the signal and processes both in parallel, mixing the result
- [ ] Nesting works: `(stack (fx :a) (->> (fx :b) (fx :c)))`

### Aux Sends (Audio Busses)
- [ ] `(fx :send :bus-name amount)` routes a portion of the signal to the named audio bus
- [ ] The audio bus is correctly mixed into the master chain or can be processed independently
- [ ] `(stop)` clears all send connections

### Integration
- [ ] All new tokens highlighted in the editor (grammar regenerated)
- [ ] All new functions have autocomplete entries with detail strings
- [ ] No regressions in existing track FX behavior
- [ ] All existing tests pass (`npm run test`)
