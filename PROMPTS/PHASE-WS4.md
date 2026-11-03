# Phase WS4 — AI Assistant Rail

> **Spec status:** outline — expand into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🤖 auto — layout, toggle and working-state are DOM behaviour, verifiable with
> Playwright using a mocked provider.

**Milestone:** M2 — Full songs in code
**Depends on:** AI2/AI3 (assistant panel, delivered). WS1 recommended (selection as context).

## Goal

Move the AI assistant out of the editor column into a right-hand rail, as in
*Outline Panel v2* (`design/outline-panel-v2/outline-v2.jsx`: `AIRail`, `AIPanel`;
frame 4 in `Outline Panel v2.html`):

- A **28 px rail** on the right edge (`‹` button, vertical "AI" label). Clicking it (or
  **⌘I**) opens a **300 px panel** to its left, and the rail then shows `›`.
- The `ai` button in the top nav goes away (the rail replaces it).
- **Working state** while a request runs: three 5 px cyan dots pulsing (1.1 s, staggered
  .18 s) with "thinking", a 1 px cyan sweep along the top of the input (1.4 s), and a
  pulsing dot on the rail when the panel is collapsed.
- With WS1: the current selection is sent as context and shown as a `ctx :chorus` chip
  in the panel header.

## Scope

- Re-host the existing `repulse.ui.assistant-panel` UI (settings, messages, revert, log,
  input) in the rail panel. No change to providers, tools or safety logic.
- Persist open/closed in localStorage.
- The "apply / discard" diff buttons keep their current behaviour.

## Open design questions

1. ⌘I conflicts: in some browsers ⌘I opens page info. **Recommendation:** bind `Mod-i` in
   the editor keymap with preventDefault. If Chromium consumes it, use `Mod-Shift-a` and
   document it.
2. Without WS1 there's no selection. **Recommendation:** send the current outline entry
   under the cursor (`repulse.outline/entry-at`) as context instead.

## Definition of done

- [ ] No `#ai-panel` in the editor column and no `ai` nav button. The rail toggles the
      panel, and the state survives a reload.
- [ ] With a mocked streaming provider, the working state appears during the request
      and disappears after (Playwright).
- [ ] All existing assistant features work from the rail (send, cancel, revert, settings).
      Existing AI tests stay green.
- [ ] At 1280×800 with the outline open and the AI panel closed, the editor is ≥ 1000 px wide.
- [ ] USAGE.md / AI docs updated. `npm run test:all` + app compile pass. Test protocol in the PR.

## What NOT to do

- No new providers (AI2b), no changes to tool calling or budgets.
