# Phase WS2 — `(master …)` Form & Master FX Outline Group

> **Spec status:** outline — expand into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🤖 auto — the form is evaluator + fx-chain behaviour (unit tests), and the
> outline group and inspector block are Playwright-testable.

**Milestone:** M2 — Full songs in code
**Depends on:** WS1 (inspector), SONG3 (outline).

## Goal

Give the global effect chain a home in the code, as in *Outline Panel v2*
(`docs/design/song3-outline-v2/outline-v2.jsx`, `MasterBlock`, the `master` outline group):

```lisp
;; before — global effects are loose top-level forms
(fx :delay :time 0.375 :feedback 0.4)
(fx :reverb 0.3)

;; after — one block, listed in the outline as "master fx 2"
(master
  (fx :delay  :time 0.375 :feedback 0.4)
  (fx :reverb 0.3))
```

The outline shows a `master` group (kind colour `#6f9bd8`, 1 px divider above, children
indented 12 px). Selecting it opens all global fx stacked in the inspector, each with `↗`
to its line. Selecting a child shows that one effect.

## Scope

- New built-in `master`: evaluates its `(fx …)` children in order as the global chain
  (same result as the same `(fx …)` forms at top level). Anything else inside it is an
  eval error with a clear message.
- Rule 3 / Rule 5 checklist for the new name: grammar `BuiltinName`, `gen:grammar`,
  `completions.js`, `builtin_meta.edn`, `gen:ai-docs`, hover docs.
- `repulse.outline/extract` learns the `:master` kind with `:fx` children.
- Inspector master block (WS1 slider components); slider write-back uses the existing
  `fx-slider-patch-and-eval!` path, which must find params inside a `(master …)` form.
- Top-level `(fx …)` keeps working unchanged.

## Open design questions

1. Several `(master …)` forms, or `master` mixed with top-level `(fx …)`?
   **Recommendation:** at most one `master` per buffer (a second is an eval error).
   Top-level `(fx …)` forms are applied after it, in source order, with a one-time
   console hint suggesting moving them into `master`.
2. Should `master` also carry a master gain? **Recommendation:** not in WS2. Effects only.
3. Ordering in the outline: source order or always last? **Recommendation:** always last
   (after the song group), as in the design, with a divider.

## Definition of done

- [ ] `(master (fx :reverb 0.3))` and top-level `(fx :reverb 0.3)` produce the same
      fx-chain state (unit test on the chain data).
- [ ] `(master (seq :bd))` → eval error naming `master` and the offending form.
- [ ] A second `master` → eval error.
- [ ] Outline lists `master fx N` + children. Inspector sliders inside `master` rewrite the
      right literal (Playwright).
- [ ] Grammar, completions, builtin_meta, ai-docs regenerated with no drift. USAGE.md documents `master`.
- [ ] `npm run test:all` + app compile pass. Test protocol in the PR.

## What NOT to do

- No per-track routing changes, no sends/busses (P2).
- Don't remove or deprecate top-level `(fx …)`.
