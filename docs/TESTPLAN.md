# REPuLse Test Plan

This plan describes the automated coverage expected after Phase TEST1.

## Standard Commands

```bash
npm test              # CLJS unit/integration tests
npm run test:rust     # Rust AudioEngine tests
npm run test:e2e      # Browser offline audio tests
npm run test:all      # Full local verification suite
./scripts/test-all.sh # Same full suite
```

Run `npm run test:all` after every code phase before marking the phase delivered.
For docs-only or prompt-only changes, `npm test` is the minimum acceptable check.

## Coverage Map

| Area | Coverage | Command |
|---|---|---|
| Pattern algebra | `seq`, `stack`, `pure`, `fast`, `slow`, `rev`, `every`, `arrange`, timing spans | `npm test` |
| Music theory | note parsing, Hz conversion, scales, chords, transpose | `npm test` |
| Parameter transforms | `amp`, `attack`, `decay`, `release`, `pan`, threading, param maps | `npm test` |
| Envelopes | envelope curve math and interpolation | `npm test` |
| Lisp evaluator | special forms, errors, bindings, macros, typo handling | `npm test` |
| Mini-notation | tokenization, parsing, subdivisions, rests, repeats, alternation | `npm test` |
| Eval-to-events integration | Lisp source -> Pattern -> queried Events | `npm test` |
| Session persistence | restore, corrupt BPM sanitization, local state shape | `npm test` |
| FX contract | plugin chain data behavior and app-level FX helpers | `npm test` |
| Rust audio engine | voices, scheduling, amp, pan, decay, pending events, finite PCM | `npm run test:rust` |
| Browser offline audio | OfflineAudioContext render, onset count, RMS, pan, silence, finite PCM | `npm run test:e2e` |
| App smoke | no console error or CSP violation on load, first evaluation + `(stop)`, production CSP header (`e2e/smoke.spec.ts`) | `npm run test:e2e` |
| Editor | completion, hover, highlight classes, Ctrl/Cmd+K S, comment toggle, Alt+Enter (`e2e/editor.spec.ts`) | `npm run test:e2e` |
| Projects and share links | `#v3:`/`#v2:` links, size limits, buffer-name errors, `(use …)`, `(upd)`/F9, snippet insert target (`e2e/project-share.spec.ts`) | `npm run test:e2e` |
| Snippet library | mocked API; search/tag input as text, tag filter, insert (`e2e/snippets-lib.spec.ts`) | `npm run test:e2e` |
| Embed | `(history)` refusal, no workbench UI (`e2e/embed.spec.ts`) | `npm run test:e2e` |
| Messages | inspector notes, loose-fx hint, command-bar slider, export guard (`e2e/messages.spec.ts`) | `npm run test:e2e` |
| Measured sound | filter centroid, amp/slider RMS, `rate` pitch, `accelerate` glide, master-reverb tail (`e2e/audio-properties.spec.ts`) | `npm run test:e2e` |
| Release build | WASM build and shadow-cljs release build | CI `release-build` |

## Release-Protocol Coverage

Phase TEST3 went through Teil 1 of the v0.1.1 release protocol (PR #181, 100 items that
were tested by hand) and gave every item a status. `covered` — an existing e2e or unit
test already asserts it; `TEST3` — a test added by Phase TEST3; `manual — <reason>` — it
needs a person. A future release protocol keeps only the `manual` rows in Teil 1; the
others are pre-ticked in Teil 2 with their test name.

| Item | Check | Covered by | Status |
|---|---|---|---|
| 1 | `use` goal example (song, 16 bars) | `project_eval_test › e1-order`, `e2-shared-env`; `buffers.spec › W3`; `song.spec › song plays sections in order…` | covered |
| 2 | Alt+Enter from a non-main tab | `buffers.spec › W3`, `W4` | covered |
| 3 | `use` alone prints `used :drums` | `project_eval_test › e2-shared-env` | covered |
| 4 | Shared definitions across buffers | `project_eval_test › e2-shared-env` | covered |
| 5 | Create, rename, switch, delete + reload | `buffers.spec › W2` | covered |
| 8 | `#v3:` share link round trip | `project-share.spec › P1` | TEST3 |
| 14 | `(use …)` in the command bar | `project-share.spec › P5` | TEST3 |
| 17 | Share too large | `project-share.spec › P3`; `session_test › s5-share-size-limit` | TEST3 |
| 18 | Import errors | `project_test › edn-import-errors-p7`; `buffers.spec › W7` | covered |
| 19 | Buffer-name errors (`Drums!`, `main`, 33rd buffer) | `project-share.spec › P4`; `project_test › add-buffer-p3` | TEST3 |
| 21 | `(upd)` / F9 takes the active tab | `project-share.spec › P6` | TEST3 |
| 22 | Snippet insert goes to the active buffer | `project-share.spec › P7` | TEST3 |
| 24 | Old `#v2:` link | `project-share.spec › P2`; `session_test › s4-share-round-trip` | TEST3 |
| 35 | Plain Lisp: outline, slider, `Used by` | `outline.spec › outline lists 10 rows…`; `workspace.spec › section, def and fx inspectors` | covered |
| 38 | Track-FX slider writes the code | `workspace.spec › pointer drag rewrites only the amp number…`; `master.spec › inspector slider inside master…`; duller sound: `audio-properties.spec › M1` | covered |
| 45 | Inspector: not evaluated / not active yet | `messages.spec › R45` | TEST3 |
| 46 | Cue disabled after code change; unused def note | `messages.spec › R46` | TEST3 |
| 47 | Alt+Enter on buffer W, running section marked | `outline.spec › the playing section is marked while a song plays`; `song.spec` | covered |
| 50 | Embed: no status head, inspector or tint | `embed.spec › EM1` | TEST3 |
| 51 | `master` highlight, completion, hover | `editor.spec › E1`, `E2`, `E3` | TEST3 |
| 54 | `(fx …)` and `(master (fx …))` equal in the inspector | `master.spec › selecting master opens all global fx; a child opens one` | covered |
| 59 | Loose-fx console hint, once | `messages.spec › R59`; `fx_test` (hint logic) | TEST3 |
| 62 | Loose fx group in outline and inspector | `workspace.spec › section, def and fx inspectors` | covered |
| 63 | Track FX stays untouched by global reverb slider | `master.spec › inspector slider inside master rewrites the right literal…` | covered |
| 64 | `slider` completion, highlight, hover | `editor.spec › E1`, `E2`, `E3` | TEST3 |
| 65 | Target example: widget drawn, `track :bass playing` | `inline-slider.spec › widgets render with label, size, fill and tooltip` | covered |
| 66 | Drag changes code and level; undo in one step | `inline-slider.spec › drag rewrites the value…`; level: `audio-properties.spec › M2` | covered |
| 71 | Ctrl/Cmd+K S on fx parameters | `editor.spec › E4`; `inline-slider.spec › ⌘K S on (fx :filter :freq 800)…` | TEST3 |
| 84 | Command bar `(slider …)` prints a value, no widget | `messages.spec › R84` | TEST3 |
| 93 | AI sees editor code only when shared | needs a real AI provider key and model | manual — AI key |
| 95 | Inspector with the AI rail open | `ai-rail.spec › A13` | TEST3 |
| 98 | Master reverb tail in the export | `audio-properties.spec › M5` | TEST3 |
| 107 | Only one export at a time | `messages.spec › R107` | TEST3 |
| 108 | Live playback: e-piano with reverb sounds as before | export path: `export-parity.spec › FM e-piano exports…`, `audio-properties.spec › M5`; the live AudioWorklet path is TEST2 | covered |
| 110 | Track FX in playback: duller drums | `audio-properties.spec › M1` | TEST3 |
| 122 | Velocity and chords in MIDI export | `midi_export_test › amp->velocity-test`; `stems-midi.spec › (midi-export :song) downloads…` | covered |
| 131 | `(export :song)` with master FX | `export-parity.spec › offline (export :song) matches the real-time capture`; `audio-properties.spec › M5` | covered |
| 132 | `(export :song :realtime)` | `export-parity.spec › :realtime keeps the SONG1 capture and its messages` | covered |
| 133 | Live `midi-out` velocity | needs a MIDI device | manual — MIDI device |
| 134 | Stems in a DAW | needs a DAW | manual — DAW |
| 135 | `pick` with a map | `core_test › tl4b-pick-name`; `integration_test › tl4b-lisp-pick` | covered |
| 136 | `pick` with mini-notation | `integration_test › tl4b-lisp-pick` | covered |
| 137 | `pick-mod` | `core_test › tl4b-pick-mod` | covered |
| 138 | `pick-f` | `core_test › tl4b-pick-f`; `integration_test › tl4b-lisp-pick-f` | covered |
| 139 | `pick-mod-f` | `core_test › tl4b-pick-f` | covered |
| 140 | `pick-out` | `core_test › tl4b-pick-out` | covered |
| 141 | `pick-mod-out` | `core_test › tl4b-pick-out` | covered |
| 142 | `pick-restart` (+ unknown-sound warning) | `core_test › tl4b-pick-restart-reset`; `sound_check_test` | covered |
| 143 | `pick-mod-restart` | `integration_test › tl4b-lisp-pick` | covered |
| 144 | `pick-reset` | `core_test › tl4b-pick-restart-reset` | covered |
| 145 | `pick-mod-reset` | `integration_test › tl4b-lisp-pick` | covered |
| 147 | `inhabit-mod` | `core_test › tl4b-inhabit` | covered |
| 149 | `fix` with a list | `params_test › tl4b-fix-any-of`; `integration_test › tl4b-lisp-fix` | covered |
| 150 | `fix` with a fraction | `params_test › tl4b-fix-numeric` | covered |
| 151 | `fix` hits `:cb` | `params_test › tl4b-fix-and-derived-s` | covered |
| 152 | `fix {:s :c4}` hits the note | `params_test › tl4b-fix-and-derived-s` | covered |
| 158 | `when-pat` | `core_test › tl4-when-pat` | covered |
| 159 | `choose-in` | `core_test` (choose-in tests, line ~1086); `integration_test` (line ~1080) | covered |
| 160 | `amp` | `params_test`; `audio-properties.spec › M2` | covered |
| 176 | `(history)` in the embed | `embed.spec › EM1` | TEST3 |
| 177 | Snapshot link too large | `project-share.spec › P3` | TEST3 |
| 178 | No history entry from command bar, `(upd)`/F9, slider drag | `history.spec › H8` | TEST3 |
| 179 | `(share!)` copies a link; `.repulse` export/import | `history.spec › H10`; `buffers.spec › W7` | TEST3 |
| 180 | `(reset!)` clears the session, snapshots survive | `history.spec › H9` | TEST3 |
| 181 | `chop` | `params_test › chop-four-equal-pieces`; `sample-slicing.spec › chop 4 spreads the slices…` | covered |
| 182 | `chop` patterned | `params_test › chop-curried-and-patterned`; `integration_test › tl6-chop-sound` | covered |
| 183 | `striate` | `params_test › striate-interleaves` | covered |
| 186 | `loop-at` | `params_test › loop-at-slows-and-sets-speed`; `sample-slicing.spec › loop-at 2 fits…` | covered |
| 187 | `fit` | `params_test › fit-sets-rate-from-range-and-whole` | covered |
| 191 | `sustain` + `accelerate` | `audio-properties.spec › M4`; `sample-slicing.spec › legato 0.5…` | TEST3 |
| 192 | Loop range of a pad sample | needs the real `:pad` sample bank and a listen for the loop | manual — sample bank, ear |
| 193 | Highlight follows every `chop` slice | visual timing check on a sample bank that is not served offline | manual — visual, sample bank |
| 194 | Breakbeat listening test | clicks, natural groove, hat choke: ears | manual — ear |
| 196 | `begin`/`end`/`rate` | `audio-properties.spec › M3`; `params_test › rate-scalar` | covered |
| 197 | `hurry` | `params_test › tl3b-hurry`; `integration_test › tl3b-lisp-hurry` | covered |
| 198 | `(export 2)` sounds like playback | `export-parity.spec` (offline export parity tests) | covered |
| 199 | Comment toggle, German layout (Shift+Mod+7) | `editor.spec › E5` | TEST3 |
| 200 | Comment toggle, US layout (Mod+/) | `editor.spec › E5` | TEST3 |
| 201 | Alt+Enter still evaluates | `editor.spec › E6` | TEST3 |
| 202 | No CSP errors on load | `smoke.spec › S1` | TEST3 |
| 203 | IPv6 base URL rejected | `ai-rail.spec › A11` | TEST3 |
| 204 | No inline-handler error opening AI settings | `ai-rail.spec › A12` | TEST3 |
| 205 | Enter in the Freesound key field | `ai-rail.spec › A12` | TEST3 |
| 206 | `http://localhost` base URL accepted | `ai-rail.spec › A11` | TEST3 |
| 207 | LAN `http://` base URL is mixed content | `ai-rail.spec › A11` | TEST3 |
| 208 | `base-uri` enforced on the deployment | config: `smoke.spec › S3`; the header on the Vercel deployment and the console experiment | manual — deployment |
| 209 | Search field survives a redraw | `snippets-lib.spec › L1` | TEST3 |
| 210 | Own tags render as text (share-as-snippet) | needs a login to open the submit dialog; card tags: `snippets-lib.spec › L2` | manual — login |
| 211 | Publish, rate, delete a snippet | needs a login and the backend | manual — login |
| 212 | Community tags in the submit dialog | dev-console only (`repulse.*` namespaces), submit dialog; card tags: `snippets-lib.spec › L2` | manual — dev console |
| 213 | `supabase/schema.sql` Hardening runs twice | Supabase SQL editor | manual — SQL editor |
| 214 | `star_count` update is denied | Supabase SQL editor | manual — SQL editor |
| 215 | Tag length check constraint | Supabase SQL editor | manual — SQL editor |
| 216 | Tag filter `techno` | `snippets-lib.spec › L3` | TEST3 |
| 217 | `↓ insert` appends the snippet | `snippets-lib.spec › L4` | TEST3 |
| 218 | Page load: no errors, no CSP violations | `smoke.spec › S1` | TEST3 |
| 220 | `(stop)` | `smoke.spec › S2` | TEST3 |
| 221 | `every 4 (fast 2)` | `core_test › every-multi-cycle-span`, `slow-early-every-patterned` | covered |
| 222 | `(synth :saw)` arpeggio | `integration_test` (the `(synth :saw …)` assertions near line 215); `per-event-inserts.spec › lpf 400 lowers the 8th saw harmonic…` | covered |
| 223 | Login, insert a snippet | real GitHub OAuth login | manual — login |

Totals: 47 covered, 39 TEST3, 14 manual (of 100).

Test names are `<spec>.spec.ts › <test id or title>` for Playwright and
`<file>_test › <deftest>` for ClojureScript. `messages.spec` holds the checks that no
other spec asserted (R45, R46, R59, R84, R107).

### Not automated by TEST3

| Check | Why |
|---|---|
| `audio-properties` M6: a global reverb changes the filtered track's spectral centroid by less than 5 % | Not separable. Over 20 local runs the difference between identical renders varied from 0.4 % to 28 % (drum track) and 0.2 % to 4.6 % (saw bass), so no 5 % threshold with a 2x margin exists. Stays manual: filter a track, add a global reverb, listen that the track is still dull. |
| Deployment CSP header and the `base-uri` console experiment (item 208) | `smoke.spec › S3` checks the config the server sends; only the Vercel deployment can show what Vercel sends. |

### Notes on the measured-sound tests

- M1 and M5 read the WAV of `(export N)` / `(export :song … :realtime)` from the full app,
  because track and master FX exist only there. The harness `renderOffline` renders
  patterns and per-event parameters, not FX chains; M2–M4 use it.
- Thresholds and the measured values behind them are in comments in
  `e2e/audio-properties.spec.ts`. M1's limit (0.5x) is fixed by the phase spec; the
  measured ratio is 0.29-0.31.
- `npx playwright test audio-properties --repeat-each 20` passed 20 of 20 for M1-M5 and the
  helper tests.
- The e2e server serves a shadow-cljs dev build, which loads modules through `eval`, so
  `playwright.config.ts` starts it with `--dev-eval` (adds `'unsafe-eval'` to `script-src`
  only). Every other CSP directive is exactly the one in `vercel.json`.
- `e2e/embed.spec.ts` needs the `:embed` build; `playwright.config.ts` compiles it with the
  others. That overwrites the checked-in `app/public/embed.js` with a fresh dev build; do not
  commit that change.

## Browser Audio Boundary

TEST1 browser tests use `OfflineAudioContext` and report the `"offline-js"`
backend. They verify the offline render/export-style path, not live production
AudioWorklet + WASM capture.

Production browser audio capture is TEST2 scope:

```text
AudioContext -> AudioWorkletNode -> Rust/WASM AudioEngine -> captured PCM
```

## What Is Not Fully Covered Yet

- Live browser AudioWorklet + WASM PCM capture.
- Full effect-plugin audio output through the production graph.
- Randomized pattern determinism for `choose`, `sometimes`, `degrade`, and related
  functions.
- External network flows such as Freesound and remote sample repository loading.
- Editor interaction beyond what `editor`, `project-share` and `snippets-lib` specs cover (see the coverage table below).
- MIDI hardware input/output behavior.

## Adding Coverage For New Phases

When adding a feature, update the closest layer:

- Pure pattern or parameter behavior: add CLJS tests under `packages/core` or
  `packages/lisp`.
- Lisp syntax or evaluator behavior: add evaluator/integration tests.
- Rust synthesis behavior: add `AudioEngine` tests.
- Browser rendering behavior: add Playwright tests through the TEST1 harness if
  the offline path is sufficient.
- Production live audio behavior: use or extend the future TEST2 harness.

If a phase adds user-facing syntax, also update README/USAGE docs and keep the
syntax highlighting/completion/AI-docs workflows in sync.

Manual checks (what needs ears, a device, a login or the deployment) go in the PR, not in a file: every
PR's description ends with a `## Test protocol` task list that the reviewer ticks off on
GitHub. Format and rules: `CLAUDE.md`, Rule 5.
