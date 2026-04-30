# Phase S4 — Snippet Audio Preview

## Goal

Audition community snippets before inserting. Both **solo preview** (snippet
plays alone) and **mix preview** (snippet plays alongside the running session)
become polished, reliable features with proper sandboxing and visual feedback.

S1 shipped a minimal preview (shared `:__preview__` track). S4 upgrades it to
production quality: isolated execution, consistent latency, visual indicators,
per-snippet mini waveforms.

Depends on **S1** (snippet panel, basic preview), **S2** (backend, API),
**S3** (community library). R1 is also a prerequisite — this phase depends on
modularized `ui/` code.

---

## Background

The S1 preview used a shared `:__preview__` track name and the main scheduler.
This is fine for local snippets but has real issues at scale:

- **Eval safety** — community snippets may contain syntax errors, infinite
  loops, or malicious code. S1 evaluates them in the same env as the user's
  session.
- **State contamination** — if a snippet calls `(bpm 140)` or `(fx :reverb 0.8)`,
  it mutates the user's session. Preview should not modify user state.
- **Visual feedback** — S1 has no indication *which* snippet is currently
  previewing. If the user scrolls away, they can't tell.

S4 fixes all three and adds a mini-waveform visualisation per card.

---

## Files to change

| File | Change |
|------|--------|
| `app/src/repulse/snippets/preview.cljs` | **New** — isolated preview engine |
| `app/src/repulse/snippets/sandbox.cljs` | **New** — sandboxed eval env snapshot/restore |
| `app/src/repulse/ui/snippet_panel.cljs` | Update cards to show playing state + waveform |
| `app/public/css/main.css` | Styles for playing indicator + waveform canvas |
| `api/snippets/[id]/waveform.ts` | **New** — optional pre-computed waveform endpoint (server-side render) |

---

## Preview engine

### Sandboxed eval

A preview is evaluated in a **snapshot** of the current Lisp env:

1. Before preview: save `(env-snapshot)` — atoms for session state, fx chain, bpm
2. Eval snippet into a forked env — mutations stay local to the preview
3. On stop/change: restore the snapshot

This requires `env-snapshot` / `env-restore` functions in `eval.cljs` — cheap
if the env is already a persistent map; more work if it's a mutable atom.

### Execution time limit

Any snippet that takes >500ms to evaluate is killed. Infinite loops caught by
the existing `loop/recur` budget (Phase M).

### Audio isolation

S1 used a shared scheduler. S4 options:

**Option A — Temporary scheduler instance.** Spin up a second scheduler for
preview, bypassing the main one. More code, more isolation.

**Option B — Reserved track name + post-stop cleanup.** Keep using the main
scheduler but enforce the `:__preview__` track is always cleared on stop.
Simpler, good enough for MVP.

**Decision for S4 MVP:** Option B. Upgrade to Option A only if real users hit
bleed issues.

### Visual state

- Snippet card shows a "playing" indicator (pulsing dot or animated border)
  while its preview is active
- Mini waveform canvas per card — can be:
  - Client-rendered from live `AnalyserNode` data while preview plays
  - Pre-computed server-side (via `OfflineAudioContext` in a Vercel Edge
    function) and cached in Supabase storage

**Decision:** client-rendered in S4 MVP. Pre-computed is a nice-to-have.

---

## Definition of done

- [x] Solo preview plays a snippet without modifying user session state
- [x] Mix preview plays alongside session without disturbing existing tracks
- [x] Preview stops cleanly on: snippet change, panel close, `(stop)`, app unmount
- [x] Syntax errors in community snippets show as tooltip on the card, don't crash preview
- [x] Runaway `loop/recur` snippets are stopped by the evaluator iteration budget; evals over 500ms are surfaced as preview errors after returning
- [x] Playing card has visible "playing" indicator (animated)
- [x] Mini waveform canvas renders during preview playback
- [x] Session state (BPM, fx chain, tracks) is unchanged after preview ends
- [x] Works with auth + without — anonymous users get preview too
- [x] Preview cleanup cancels waveform RAF handles and clears reserved preview tracks on each stop

---

## Out of scope

- Pre-computed server-side waveforms (deferred)
- Full sandboxing via WebWorker (deferred — Option A above is the fallback)
- Snippet "remixing" (fork to editor) — separate feature
- Recording preview audio to WAV (use existing `(export n)`)

---

## Open questions

1. **How is `:__preview__` cleaned up if the user navigates away mid-preview?**
   `beforeunload` listener calls `(stop!)` on the preview track. **Resolved.**
2. **Can preview play while the main session is paused?** Yes — the scheduler
   ensures at least one track is running if preview is active. **Resolved.**
3. **Sandboxing community snippets — how paranoid?** MVP trusts snippets
   within the existing Lisp eval (which is already sandboxed vs. arbitrary JS).
   **Resolved for MVP**; reopen if abuse becomes real.
