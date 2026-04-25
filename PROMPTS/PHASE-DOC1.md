# Phase DOC1 — User Documentation Overhaul

## Goal

Make REPuLse easy to learn, easy to reference, and easy to copy from.

The current documentation has useful material, but most user-facing content is
concentrated in one very large `docs/USAGE.md`. That makes it hard for a new
user to find a first path, and hard for an experienced user to quickly look up
syntax, effects, samples, MIDI, or examples.

DOC1 turns the documentation into a small, purpose-built user manual with
progressive examples. It is a documentation phase only: no language, audio, or
UI feature changes.

---

## Background

REPuLse has grown from a compact live-coding prototype into a broad instrument:

- Pattern basics: `seq`, `stack`, `fast`, `slow`, `every`, `cat`, `euclidean`
- Musical helpers: note keywords, `scale`, `chord`, `transpose`
- Per-event parameters: `amp`, `pan`, envelopes, sample offsets, `tween`
- Tracks and performance controls: `track`, `mute!`, `solo!`, `upd`, tap BPM
- Sound sources: Strudel samples, external sample repos, Freesound, MIDI out
- Effects and routing: global FX, track FX, busses, sidechain, distortion
- Lisp features: `def`, `let`, `fn`, macros, loop/recur, rationals
- UX features: demos, tutorial chapters, snippets, sharing, embeddable editor

The docs should acknowledge that breadth without forcing users to read it all
linearly. A musician should be able to start with grooves and examples. A
programmer should be able to jump into language semantics. A performer should
find tracks, command bar, MIDI, and session persistence quickly.

---

## Proposed documentation structure

Split user-facing documentation into **11 focused files** plus the existing
developer-facing docs:

| File | Audience | Purpose |
|------|----------|---------|
| `docs/README.md` | Everyone | Documentation index, reading paths, feature map |
| `docs/GETTING-STARTED.md` | New users | Install/open app, play first pattern, stop, edit, save |
| `docs/TUTORIAL.md` | New users | Step-by-step lessons from first beat to full session |
| `docs/COOKBOOK.md` | Musicians | Copyable recipes: drums, bass, chords, fills, transitions |
| `docs/LANGUAGE.md` | Lisp users | Syntax, values, `def`, `let`, `fn`, control flow, macros |
| `docs/PATTERNS.md` | Live coders | Pattern model, timing, combinators, arrangement |
| `docs/SOUND.md` | Sound design | Samples, synths, params, envelopes, music theory helpers |
| `docs/EFFECTS.md` | Sound design | Global FX, track FX, routing, distortion, sidechain |
| `docs/PERFORMANCE.md` | Performers | Tracks, command bar, BPM, MIDI, session URLs, persistence |
| `docs/REFERENCE.md` | All users | Complete built-in reference table with signatures/examples |
| `docs/TROUBLESHOOTING.md` | All users | Common mistakes, diagnostics, browser/audio/MIDI issues |

Keep the existing developer docs, but make their audience explicit:

- `docs/ARCHITECTURE.md` — developer architecture
- `docs/PLUGINS.md` — plugin author documentation
- `docs/DEPLOYMENT.md` — deployment and backend setup
- `docs/CONTRIBUTING.md` — contributor workflow and checks
- `docs/FUTURE-FEATURES.md` — product backlog

`docs/USAGE.md` should not remain the primary manual. Either replace it with a
short compatibility page pointing to the new docs, or keep it as generated/legacy
reference only if existing links require it.

---

## Content plan

### 1. Documentation index

`docs/README.md` should answer "where do I go now?" in the first screen:

- "I want to make sound in 2 minutes" → Getting Started
- "I want to learn by building a track" → Tutorial
- "I want examples to copy" → Cookbook
- "I want to look up a function" → Reference
- "I am performing live" → Performance
- "Something broke" → Troubleshooting

It should also include a compact feature map grouped by task, not implementation
phase.

### 2. In-app help and documentation access

The documentation should not only live in Markdown files. REPuLse should expose
help at the moment the user needs it, from the editor and surrounding UI.

High-value ideas:

- **Help drawer** — a right-side panel opened from a `?` button or command bar,
  with search across built-ins, examples, cookbook recipes, and troubleshooting.
- **Command bar help queries** — commands like `(help)`, `(help seq)`,
  `(help :effects)`, `(examples euclidean)`, and `(why-error)` should show focused
  help without leaving the app.
- **Contextual error help** — diagnostics should include a short fix and a link or
  button to the relevant troubleshooting section. Example: using `->` at pattern
  level should offer the `->>` correction and explain the two layers.
- **Inline examples from autocomplete** — completion details should include one
  copyable example, not only a short signature.
- **Hover docs with "more" links** — existing hover docs should link to the
  matching reference section and cookbook examples.
- **Example insertion buttons** — help entries should offer "insert example",
  "replace selection", and "open in tutorial" actions where practical.
- **First-run learning path** — the first visit should offer three clear choices:
  quick beat, guided tutorial, or browse examples. Avoid forcing a modal before
  sound can be made.
- **Recipe browser** — surface cookbook recipes inside the app, filtered by goal:
  drums, bass, melody, effects, performance, MIDI, samples.
- **Cheat sheet overlay** — a compact overlay for live use: shortcuts, common
  functions, track commands, and panic/stop/reset actions.
- **Explain current code** — a lightweight command that identifies top-level
  forms, tracks, effects, BPM, and likely mistakes in the current buffer.
- **Documentation deep links** — every built-in, hover doc, tutorial chapter, and
  error should have a stable docs anchor so links remain useful outside the app.
- **Empty-state help** — when no tracks, snippets, samples, or MIDI devices exist,
  panels should show one useful next action with a small example.
- **Progressive disclosure** — beginner help should show `seq`, `stack`, `fast`,
  and `track` first; advanced Lisp/macros/routing should be available but not
  presented as the default path.
- **Offline docs bundle** — because REPuLse is browser-based, core docs should ship
  with the app and work without network access.

Potential app files for a later implementation phase:

| File | Possible change |
|------|-----------------|
| `app/src/repulse/ui/help_panel.cljs` | **New** — searchable help drawer |
| `app/src/repulse/content/help.cljs` | **New** — normalized docs/examples metadata |
| `app/src/repulse/env/builtins.cljs` | Add `(help)`, `(examples)`, maybe `(why-error)` built-ins |
| `app/src/repulse/lisp-lang/hover.js` | Add docs anchors and richer examples |
| `app/src/repulse/lisp-lang/completions.js` | Add example snippets to completion metadata |
| `app/src/repulse/eval_orchestrator.cljs` | Attach troubleshooting ids to diagnostics |
| `app/src/repulse/ui/snippet_panel.cljs` | Cross-link snippets with cookbook/help topics |

DOC1 should design the content model and anchors so this can be built cleanly
later, even if the full in-app help UI is implemented in a separate phase.

### 3. Getting Started

Target: a user who has never used REPuLse.

Must include:

- Requirements and quickest local start
- First pattern: `(seq :bd :sd :bd :sd)`
- How to evaluate, stop, edit, and recover from errors
- How cycles/bars work in plain language
- A five-minute path from drums → hats → bass → effects → track names
- Links to tutorial and cookbook at natural stopping points

### 4. Tutorial

Target: structured learning, not exhaustive reference.

Suggested chapters:

1. First beat: `seq`, rests, play/stop
2. Layering: `stack`, hats, odd lengths
3. Time: `fast`, `slow`, `rev`, `every`
4. Naming: `def`, reusable parts
5. Tracks: `track`, `upd`, `mute!`, `solo!`
6. Melody: note keywords, `scale`, `chord`
7. Expression: `amp`, `pan`, envelopes, `->>`
8. Variation: `euclidean`, `sometimes`, `choose`, `degrade`
9. Arrangement: `cat`, `arrange`, `play-scenes`
10. Effects: reverb, delay, filter, distortion, track FX
11. Samples: `sound`, `bank`, `samples!`, Freesound
12. Performance: BPM, command bar, MIDI, sharing

Every chapter should contain:

- A working starting example
- One small change to make
- One "try this" variation
- A short explanation of what the user hears
- A link to deeper reference material

### 5. Cookbook

Target: copy/paste and modify.

Include short, named recipes:

- Four-on-the-floor
- Backbeat
- Offbeat hats
- Euclidean percussion
- Breakbeat-ish hats/snare
- Acid bassline
- Minor chord pad
- Call-and-response melody
- Every-four-bars fill
- Dub delay send
- Track-local filter sweep
- Sidechain ducking
- Build a full 4-track loop

Recipes should be self-contained and playable. Prefer many small examples over a
few large examples.

### 6. Language and pattern reference split

`docs/LANGUAGE.md` should explain Lisp syntax and evaluation without teaching
every music feature.

`docs/PATTERNS.md` should explain the pattern algebra and time semantics:

- What a cycle is
- What a pattern returns
- Difference between values, patterns, and transformers
- Why `->>` is used at pattern level
- How transformations compose
- How arrangement loops
- Rational-time note for developers, but in user-friendly words

### 7. Complete reference

`docs/REFERENCE.md` should be exhaustive and scannable:

| Name | Signature | Returns | Example | See also |
|------|-----------|---------|---------|----------|

Group entries by task:

- Sources
- Time transforms
- Random/variation
- Parameters
- Sound and samples
- Effects
- Tracks/session
- MIDI/I/O
- Lisp forms
- Collections/math
- Embedding/sharing

The reference should be checked against actual built-ins, completions, hover docs,
and grammar names so it does not drift.

### 8. Troubleshooting

Include the common AI/human mistakes already documented in `docs/USAGE.md`, but
turn them into a lookup guide:

- "I used `->` and got an error"
- "No sound plays"
- "WASM did not load"
- "MIDI permission does not appear"
- "A sample keyword is silent"
- "My effect changes everything, not one track"
- "My snippet changed my session"
- "Browser autoplay blocked audio"
- "I edited grammar but highlighting did not change"

---

## Files to change

| File | Change |
|------|--------|
| `docs/README.md` | **New** — documentation index and reading paths |
| `docs/GETTING-STARTED.md` | **New** — beginner start guide |
| `docs/TUTORIAL.md` | **New** — structured lessons |
| `docs/COOKBOOK.md` | **New** — copyable musical recipes |
| `docs/LANGUAGE.md` | **New** — REPuLse-Lisp syntax and semantics |
| `docs/PATTERNS.md` | **New** — pattern model and combinators |
| `docs/SOUND.md` | **New** — samples, synths, params, theory |
| `docs/EFFECTS.md` | **New** — effects, track FX, routing, distortion |
| `docs/PERFORMANCE.md` | **New** — live controls, MIDI, persistence, sharing |
| `docs/REFERENCE.md` | **New** — complete built-in reference |
| `docs/TROUBLESHOOTING.md` | **New** — common errors and fixes |
| `docs/USAGE.md` | Replace with short pointer page or keep as legacy reference |
| `README.md` | Link to the new documentation index and first-user path |
| `app/src/repulse/lisp-lang/completions.js` | Audit signatures/details against `docs/REFERENCE.md` |
| `app/src/repulse/lisp-lang/hover.js` | Audit hover docs against `docs/REFERENCE.md` |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Audit built-in names for reference completeness; regenerate only if changed |

---

## Documentation style guide

- Lead with working examples before terminology.
- Prefer one concept per page section.
- Keep examples playable; avoid pseudo-code unless explicitly labelled.
- Show both short syntax and practical musical use.
- Use consistent names: `kick`, `snare`, `hats`, `bass`, `pad`, `lead`.
- Explain what the user should hear after each tutorial step.
- Keep advanced caveats out of the beginner path; link to reference instead.
- Use "pattern", "transformer", "track", and "effect" consistently.
- Avoid implementation terms in user docs unless the user needs them.
- Keep developer architecture details in `ARCHITECTURE.md`.

---

## Verification plan

Documentation should be verified like code:

- Run `npm run test` to ensure examples did not expose stale syntax in tests.
- Manually paste every tutorial chapter and cookbook recipe into the app.
- Verify examples that rely on samples, MIDI, or auth degrade gracefully.
- Cross-check every `docs/REFERENCE.md` entry against:
  - `packages/lisp/src/repulse/lisp/eval.cljs`
  - `app/src/repulse/env/builtins.cljs`
  - `app/src/repulse/lisp-lang/completions.js`
  - `app/src/repulse/lisp-lang/hover.js`
  - `app/src/repulse/lisp-lang/repulse-lisp.grammar`
- Run `rg` for old links to `docs/USAGE.md` and update them.

---

## Definition of done

- [ ] New docs index exists at `docs/README.md`
- [ ] User-facing docs are split into the 11-file structure above
- [ ] `README.md` points new users to `docs/GETTING-STARTED.md`
- [ ] `docs/USAGE.md` is no longer the only full user manual
- [ ] Every built-in has a reference entry with signature and example
- [ ] Tutorial chapters cover the current core workflow end to end
- [ ] Cookbook includes at least 12 copyable, playable examples
- [ ] Common mistakes from the old usage doc moved into troubleshooting
- [ ] Docs anchors are designed so future in-app help can link to each topic
- [ ] In-app help/content model recommendations are documented for a later UI phase
- [ ] Examples are manually tested in the browser
- [ ] `npm run test` passes
- [ ] ROADMAP, CLAUDE.md, and AGENTS.md mark DOC1 delivered only after the docs land

---

## Out of scope

- Building a separate documentation website
- Adding new language features
- Changing the in-app tutorial UI
- Auto-generating docs from source metadata
- Translating docs into other languages

---

## Open questions

1. Should `docs/REFERENCE.md` be hand-maintained, generated from completion/hover
   metadata, or both? Start hand-maintained for DOC1; revisit generation after the
   structure proves useful.
2. Should the in-app tutorial chapters mirror `docs/TUTORIAL.md` exactly? Not in
   DOC1. The docs can be more comprehensive than the in-app flow.
3. Should examples use `track` by default? Beginner examples should start with raw
   patterns, then introduce `track` once the user understands layering.
