# Phase WS1 — Inspector Column & Status Head (retire the context panel)

> **Spec status:** outline — expand into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🤖 auto — selection, inspector content and slider write-back are DOM +
> buffer behaviour, verifiable with unit tests and Playwright.

**Milestone:** M2 — Full songs in code
**Depends on:** SONG3 (outline column, entry model). Recommended before WS2 and WS3.

## Goal

Finish the workspace layout from Claude Design's *Outline Panel v2*
(`design/outline-panel-v2/`, `outline-v2.jsx`: `StatusHead`, `Inspector`,
`TrackBlock`, `SectionBlock`, `DefBlock`). The right **context panel goes away**, and its
parts move to where they belong:

- **Status head** at the top of the outline column: BPM (click to edit; ↑↓ ±1, ⇧↑↓ ±10,
  40–300), a `wasm`/`js` badge, a **song progress bar** split into one segment per section
  (click a segment = cue that section at the next bar), and audio latency.
- **Inspector column** (220 px, resizable 180–380 px, right of the outline). It's only
  open while something is selected and shows only that thing:
  - track: M/S buttons, param sliders, fx chain in signal order
  - section: bar strip, `▶ cue`, the tracks in it with M/S + amp
  - def/defn/synth: a syntax-coloured source preview + "used by"
- **Selection model:** clicking an outline row selects it and highlights its range in
  the editor (bg `#1f2d4e70`, gutter bar `#4a5a7e`), **without moving the cursor**.
  Clicking the same row again deselects. ⇧-click adds/removes tracks (multi-select).
  Esc clears. `↗ L20` in the inspector header or F12 moves the cursor. This replaces
  SONG3's "click moves the cursor".
- Inspector sliders write back into the code exactly like today's context-panel sliders
  (`eval-orchestrator/slider-patch-and-eval!` and its FX variants), one undo step per
  drag; ⇧-drag = 10× finer.

## Scope

- Move every context-panel section to its new home, or drop it deliberately:
  - status → status head
  - TRACKS → track inspector
  - FX → master entry (until WS2: a plain "fx" outline group built from top-level `(fx …)` forms)
  - MIDI, BUSES, SOURCES → recommended: a small "session" block at the bottom of the
    outline column, collapsed by default
  - BINDINGS → dropped (the outline lists defs)
- Remove `#context-panel`, `repulse.ui.context-panel` rendering and its CSS once
  nothing uses them. Keep the pure helpers that move (`song-bar`, `song-status-text`,
  slider constants) in a namespace that survives.
- Outline footer hints change to the v2 set (click inspect · ⇧ click add · F12 · esc ·
  ⌘⇧O hide).
- Mute/solo buttons call the existing `mute!`/`solo!` machinery (no code rewrite).

## Open design questions

1. Cue a section from the progress bar or inspector: reuse SONG1's `play-from` semantics
   (jump at the next bar) or jump immediately? **Recommendation:** next bar, via the same
   code path as `(play-from :section)`.
2. "cpu" figure: Web Audio exposes no CPU load. **Recommendation:** show only
   `latency` (`AudioContext.baseLatency + outputLatency`, in ms) and drop "cpu" unless the
   worklet can cheaply report render time per quantum, which is optional.
3. MIDI / BUSES / SOURCES: **Recommendation:** a collapsed "session" block at the bottom
   of the outline column, as in the scope above.
4. Inspector width persistence: **Recommendation:** localStorage, like the SONG3
   collapse state.

## Definition of done

- [ ] No `#context-panel` in the DOM. Every former section is reachable in its new
      place, or its removal is documented in the PR.
- [ ] Selecting a track shows its sliders. Dragging `amp` rewrites the number in the
      buffer and the sound changes without a full re-eval (Playwright: buffer text changes,
      one undo restores it).
- [ ] ⇧-click on two tracks shows both. Esc closes the inspector and keeps editor focus.
- [ ] Clicking a section segment in the progress bar while a song plays switches to that
      section at the next bar (harness/unit test on the transport call).
- [ ] BPM edit: click, type 90, Enter → `(:cycle-dur state)` matches 90 BPM. Esc cancels.
      Values outside 40–300 are rejected.
- [ ] At 1280×800 with the inspector closed, the editor is ≥ 1000 px wide.
- [ ] Docs: USAGE.md updated (outline status head, inspector, selection); README
      screenshot or text if it mentions the context panel.
- [ ] `npm run test:all` + `npx shadow-cljs compile app` pass. Test protocol in the PR.

## What NOT to do

- No `(master …)` form (WS2), no inline sliders in the code (WS3), no AI rail (WS4).
- Don't change how sliders patch code (`lisp_patcher.cljs` stays the single writer).
- Don't change the bottom console / track visualisation.
