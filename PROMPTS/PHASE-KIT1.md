# Phase KIT1 — High-Quality Drum Kits

> **Spec status:** full (expanded from the outline on 2026-11-23).
> **Automation:** 🤖👂 auto + human check. Manifest parsing, layer selection,
> round-robin, hat choke and kit loading are tested automatically with a fixture kit
> whose recordings are sines of known frequency. Whether the shipped kits sound good,
> whether layer boundaries are smooth, and the final ship/hold decision per kit need a
> human listen ([Listen check](#listen-check)). Follow the CLAUDE.md "FM preset tests"
> guidance: concrete Goertzel/level assertions only, no claims about how a kit sounds.

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** TL1 (patterned params), TL6 (cut groups), TL7a (`velocity` param,
`inserts/amp-factor`), TL2/TL4 (`core/time->rand`)
**Coordinates with:** SYN6 (`(bank :synth)` uses the same prefix mechanism), SK3
(captured velocity writes `:velocity`)

## Goal

Ship drum kits with studio quality alongside the existing Dirt-Samples and Tidal Drum
Machines banks. Quality comes from **velocity layers** (different recordings for soft and
hard hits) and **round-robin** (alternate recordings so repeated hits don't sound like a
machine gun), not just from better single samples.

```lisp
(bank :JazzKit)                                         ; bare drum names use the kit
(seq :bd :sd :bd :sd)                                   ; top layer, rotating takes
(->> (fast 4 (seq :hh :hh :hh :hh))
     (velocity (seq 0.9 0.35 0.6 0.35)))                ; accents and soft hats
(->> (seq :sd :sd :sd :sd) (velocity "0.15 0.4 0.7 1")) ; ghost note → rimshot-hard
(seq :JazzKit_rd :StudioKit_bd)                         ; address a kit instrument directly
(sound :JazzKit_sd 4)                                   ; one fixed recording, no layering
```

## Decisions

Made while expanding the spec (2026-11-23). The implementer follows them; a reviewer
who disagrees changes the spec first.

1. **No `kit` built-in. A kit is a set of layered banks selected with `(bank …)`.** Kit
   instruments register as banks named `<Kit>_<inst>` (`JazzKit_bd`), exactly like Tidal
   Drum Machines (`RolandTR909_bd`), so `(bank :JazzKit)`, the status-head badge, session
   persistence, `sample-banks` and `(sound :JazzKit_sd 4)` all work with no new code
   path. Reason: a separate `(kit …)` would be a second prefix with its own precedence
   rules against `(bank …)` and SYN6's `(bank :synth)`; one prefix has none. The
   outline's `(kit :studio)` becomes `(bank :StudioKit)`. No Rule 3 registration is
   needed; `velocity`'s docs change (decision 4).
2. **Layering is a property of a bank, not of a kit.** The registry keeps
   `{bank → [url …]}` (all files, flattened low→high layer, take order within a layer) so
   every existing caller works. A new `layers` atom holds `{bank → [{:v :gain :urls}]}`
   for banks whose manifest entry has layers. Any manifest loaded with `samples!` can
   therefore ship a layered instrument; the two curated kits are just the first users.
3. **Manifest format: an object value with `"layers"` next to the existing array
   values.** Backwards compatible both ways: the current parser only accepts array
   values and ignores objects, so an old manifest parses exactly as today and a kit
   manifest loaded by Strudel or an older REPuLse is ignored rather than misread. JSON
   only in this phase; `.edn` Lisp manifests stay array-only.
4. **`velocity` on a layered bank picks the layer and replaces the linear multiply.**
   On every other sound `velocity` keeps multiplying amp exactly as since TL7a. On a
   layered bank the level is `amp × gain × layer-gain × in-layer-scale(velocity)`
   ([formulas](#formulas)): the recording carries most of the dynamics, so multiplying
   linearly as well would make soft layers far too quiet. Absent `velocity` means 1.0
   (top layer), as everywhere else. `velocity 0` is silent.
5. **Round-robin is "random without immediate repeat", seeded by the event onset.**
   Pick = `time->rand` of the onset (plus a per-bank salt) over the takes of the chosen
   layer, skipping the take that bank+layer played last. The "last take" state lives in
   the audio layer per `AudioContext` (a `WeakMap`, like TL6 cut groups), so an offline
   export never disturbs live playback and two renders of the same pattern from cycle 0
   pick the same takes. A strict cycle (1, 2, 3, 1, …) was rejected: it is audible as a
   pattern on 16th hats and depends on how many hits came before, which a seek changes.
6. **Explicit `n` bypasses layering.** `(sound :JazzKit_sd 4)` plays flat file 4 with
   today's linear `velocity` multiply: the escape hatch for "exactly this recording".
   Layered selection applies to bare keywords (`:bd` under `(bank :JazzKit)`,
   `:JazzKit_bd`) and `:note` maps from param transformers.
7. **Hat choke via a reserved cut group.** A manifest entry may carry `"choke": "hat"`.
   Its voices join the TL6 cut group `[:kit "<Kit>" "hat"]` (a vector, so it can never
   collide with a user's numeric `(cut n)`), unless the event sets `:cut` itself. Closed
   and pedal hats choke a ringing open hat, as on a real kit.
8. **Assets live in `app/public/kits/`, served same-origin.** Budget: ≤ 6 MB per kit,
   ≤ 300 KB per file, ≤ 12 MB for the phase. Reasons: no CORS, no second repository to
   create and keep in sync, Vercel serves it as static files, and the offline harness
   reaches it from the test server. A larger library later moves to its own repository
   (out of scope).
9. **Encoding: Ogg Vorbis (`-q:a 5`), with FLAC as the fallback.** Ogg keeps both kits
   inside the budget with cymbals in stereo. Part 0 checks `decodeAudioData` on an
   `.ogg` in Playwright's WebKit; if WebKit cannot decode it, use FLAC and trim cymbals
   to 2 s and everything except cymbals and overheads to mono. MP3/AAC are not used:
   their encoder delay shifts the attack of every hit.
10. **Lazy loading, plus a background preload when a kit becomes the prefix.**
    Files are fetched on first use, as for every bank today. `(bank :<Kit>)` with a kit
    prefix additionally starts fetching every file of that kit (≤ 6 MB) so the first
    bars aren't late. No "lite" set.
11. **Two acoustic kits: `JazzKit` and `StudioKit`.** Electronic kits are already
    covered by Tidal Drum Machines (808/909/707/606…) and SYN6's synthesized kit; what is
    missing is a convincing acoustic kit. Names are CamelCase like the drum machine banks;
    `sample-banks` lists them in a "kits" section (not grouped under "Jazz"/"Studio").

## Licence research (2026-11-23)

Status: *desk research*. Part 0 confirms each licence from the LICENSE/README file inside
the actual download before anything is converted, and stops the phase if it differs.

| Candidate | Licence (reported) | Use | Notes |
|---|---|---|---|
| **Virtuosity Drums** (Versilian Studios) | CC0 1.0 | **JazzKit** | Free jazz kit, SFZ, released as a companion to the CC0 VCSL. Multi-velocity, round-robin. No attribution required; credit given anyway. |
| **DRSKit** (DrumGizmo / DRSDrums, SFZ port on sfzinstruments.github.io) | CC-BY 4.0 | **StudioKit**, first choice | Full rock/pop kit, many velocity layers. Attribution required. |
| **Salamander Drumkit** (Alexander Holm) | CC-BY-SA 3.0 | StudioKit fallback | Stereo, normalized, no processing. Our trimmed/re-encoded files are a derivative: they ship under CC-BY-SA 3.0 in their own folder. Music made with them is unaffected. |
| VCSL percussion | CC0 1.0 | — | Orchestral/world percussion, no drum kit. Not used. |
| CrocellKit, other DrumGizmo kits | not confirmed | — | Licence not found in research. Not used. |
| SM MegaReaper and similar freeware | "Creative Commons", variant unstated | — | Rejected unless a CC0/CC-BY/CC-BY-SA licence text is found. |
| Roland V-Drums / TD-series content | proprietary | — | **Never.** |

Allowed licences: CC0, CC-BY, CC-BY-SA. Not allowed: NC, ND, "free for music
production" EULAs without a redistribution grant, GPL (sample redistribution terms
unclear for the app bundle).

## Part 0 results (2026-11-23)

- **Licences confirmed** from the LICENSE file in each source repository (texts are
  reproduced in `app/public/kits/<Kit>/LICENSE.md`): Virtuosity Drums
  (`sfzinstruments/virtuosity_drums`) is CC0 1.0 Universal; DRSKit
  (`sfzinstruments/DrumGizmo.DRSKit`, SFZ port v1.0) is CC BY 4.0. Both pass the gate;
  the Salamander fallback was not needed.
- **Encoding: FLAC, not Ogg.** The dev machine's Homebrew `ffmpeg` has no `libvorbis`
  (only the experimental native `vorbis` encoder), so decision 9's fallback applies. An
  Ogg Vorbis, an Ogg Opus and a FLAC test file all decoded with `decodeAudioData` in
  Playwright's Chromium, WebKit and Firefox, so the format is not the blocker; FLAC is
  lossless and fits: cymbals are trimmed to 3 s, everything else is mono. JazzKit is
  ≈ 5 MB. `build-kit.mjs` supports `"format": "ogg"` for machines that have libvorbis.
- **Sources are cherry-picked over the GitHub API** (`--fetch`), not cloned: the full
  repositories are 1.2 GB and 0.7 GB.
- JazzKit has no `mt` (the source kit has two toms, both mapped to `lt`/`ht`).

## Formulas

Shared by the pure namespace and its tests. Layers of an entry are sorted by `v`
ascending; `v` is the layer's upper velocity bound, the top layer has `v = 1`.

```
velocity  vel = clamp(event :velocity, 0, 1), default 1.0; vel = 0 → no voice
layer     i   = first layer with vel ≤ v_i           (v_0 = 0.3 takes vel 0.3 itself)
bounds    lo  = v_{i-1} (0 for the first layer), hi = v_i
scale     s   = vel / hi                              for the first layer
              = 0.6 + 0.4 · (vel − lo) / (hi − lo)    for every other layer
level         = amp × gain × layer.gain × s           (amp, gain as today; no ×vel)
take      r   = floor(time->rand(onset + salt) · (k − 1)) for k ≥ 2 takes,
              then r' = r if r < last else r + 1      (never equals last)
              k = 1 → 0; no previous take on this context → r over all k takes
salt          = (mod (hash bank-name) 997) whole cycles, so two instruments
                hit at the same onset don't pick the same take index
```

`layer.gain` is written by the build script so that each layer's measured RMS at its
upper bound follows `L(v) = 40·log10(v)` dB relative to the top layer (a v² power law,
close to how MIDI velocity is usually mapped). In-layer scaling then spans −4.4…0 dB,
so a slow velocity sweep has no level jumps at layer boundaries.

## Manifest format

`app/public/kits/kits.json`, appended to `DEFAULT-MANIFESTS` (resolved against the
app's own origin; check how `repulse.embed` builds asset URLs and resolve the same way so
embeds keep working):

```json
{
  "_base": "kits/",
  "_format": "repulse-kit/1",
  "JazzKit_bd": {
    "layers": [
      {"v": 0.3,  "gain": 1.42, "files": ["JazzKit/bd/v1-1.ogg", "JazzKit/bd/v1-2.ogg"]},
      {"v": 0.6,  "gain": 1.10, "files": ["JazzKit/bd/v2-1.ogg", "JazzKit/bd/v2-2.ogg"]},
      {"v": 0.85, "gain": 1.0,  "files": ["JazzKit/bd/v3-1.ogg", "JazzKit/bd/v3-2.ogg"]},
      {"v": 1,    "gain": 1.0,  "files": ["JazzKit/bd/v4-1.ogg", "JazzKit/bd/v4-2.ogg", "JazzKit/bd/v4-3.ogg"]}
    ]
  },
  "JazzKit_hh": {"choke": "hat", "layers": [ … ]}
}
```

Validation (spec in `repulse.specs`, malformed entries are skipped with one
`console.warn` naming the entry, the rest of the manifest still loads): `layers`
non-empty; every `v` in (0, 1], strictly ascending, last = 1; `gain` a number in
(0, 4], default 1; `files` a non-empty array of strings; `choke` absent or a string.
`_format` other than `repulse-kit/1` on a manifest that has object entries → skip the
object entries with a warning (forward compatibility).

**Instrument set per kit** (Tidal names so patterns switch between kits and drum
machines unchanged): `bd sd rim hh ph oh lt mt ht cr rd rb`
(`rim` = cross-stick, `ph` = pedal hat, `rb` = ride bell). Layers × takes:
`bd sd hh`: 4 × 3; `oh ph rim lt mt ht`: 3 × 2; `cr rd rb`: 3 × 2. A kit that lacks an
articulation omits it; under `(bank :JazzKit)` that name then falls through
`voices/drum-route` to the SYN6 synth kit voice, as for drum machines today (no error).
`hh ph oh` carry `"choke": "hat"`.

## Background (where things are)

- `app/src/repulse/samples.cljs` — `registry`, `parse-manifest` (array values only),
  `DEFAULT-MANIFESTS`, `get-url` (`n` wraps), `get-buffer!` (cache + in-flight),
  `play!` (amp, pan, `extra` params, TL6 cut groups via `join-cut-group!` keyed by any
  value), `set-bank-prefix!`/`resolve-keyword`, `format-banks` (groups by leading
  TitleCase word — needs a "kits" section).
- `app/src/repulse/voices.cljs` `drum-route` (SYN6) — decides synth kit voice vs sample
  for a bare keyword. Rule 2 (a drum-machine prefix whose `<prefix>_<name>` bank is
  registered → that sample) already covers `(bank :JazzKit)`; KIT1 adds no routing
  rule. The synth kit stays the default without a prefix (SYN6 decision 3).
- `app/src/repulse/audio.cljs` `play-event` — sample paths: bare keyword and `:note` map
  both go through `drum-route` (`{:kind :sample :bank …}`; the `:note` map's `amp-v`
  already includes `inserts/amp-factor`, i.e. `gain × velocity`), and the `{:bank :n}`
  map from `sound`.
- `samples.cljs` decoded-buffer cache is LRU-capped at 200 MB (QA3). A fully preloaded
  kit decodes to roughly 40 MB (≈ 110 files × ~1 s × stereo float), so the preload
  stays well under the cap; don't raise it.
  `schedule-event!` calls it with `{:dur :cycle-dur}`; the event's `:whole` start is
  known there but not passed.
- `app/src/repulse/offline_render.cljs` and `app/src/repulse/test_api.cljs` — the other
  two `play-event` callers (export and TEST1 harness), both have the event.
- `app/src/repulse/inserts.cljs` `amp-factor` — `gain × velocity`, clamped.
- `packages/core/src/repulse/core.cljs` `time->rand` (Tidal timeToRand, rational input).
- `packages/core/src/repulse/params.cljs` `velocity` — writes `:velocity`; unchanged.
- `app/src/repulse/env/builtins/samples.cljs` — `bank`, `samples!`, `sample-banks`.
- `ffmpeg` is available on the dev machine (`/opt/homebrew/bin/ffmpeg`); it is a
  build-time tool only, never a runtime or CI dependency.

## Implementation

### Part 0 — Gates (before any code)

1. Download the source kits into a gitignored `.kit-sources/` (add to `.gitignore`).
   Open each download's licence file and record its exact text, version and URL. If a
   licence is not CC0, CC-BY or CC-BY-SA, drop that kit and use the fallback row; if
   both StudioKit candidates fail, ship JazzKit alone and say so in the PR.
2. Encode one test file with `ffmpeg -c:a libvorbis -q:a 5` and decode it with
   `decodeAudioData` in Playwright's WebKit, Chromium and Firefox (scratch spec, deleted
   afterwards). Record the result in the PR. WebKit fails → FLAC (decision 9).

### Part A — Pure kit model (`app/src/repulse/kits.cljs`, new)

No DOM, no Web Audio, unit-tested in `kits_test.cljs` (add it to the `:test` build).

- `(parse-entry name js-obj)` → `{:layers [{:v :gain :urls}] :choke s|nil}` or nil
  (invalid, with the reason for the warning). Applies `_base`.
- `(pick-layer layers vel)` → `[index layer]` per [formulas](#formulas).
- `(layer-scale layers i vel)` → `s`.
- `(pick-take k last onset bank)` → take index, using `core/time->rand`.
- `(flatten-urls layers)` → the flat vector the registry stores.

### Part B — Loading (`samples.cljs`)

- `parse-manifest` also returns layered entries; `load-manifest!` merges flat URLs into
  `registry` and layers into a new `layers` atom (`{bank → {:layers … :choke …}}`).
  `samples!` with a JSON URL gets this for free.
- `(layered? bank)`, `(kit-names)` (prefixes whose banks are all layered and share a
  `_format` manifest), and `format-banks` with a "kits (N): JazzKit: bd sd …
  (4 vel × 3 takes)" section ahead of the manufacturer sections.
- `set-bank-prefix!`: when the prefix is a kit, start the background preload
  (`get-buffer!` on every URL of the kit; needs an `AudioContext`, so do it from the
  builtin where `audio/get-ctx` is available, and skip it when there is none yet — the
  first evaluation that starts audio triggers it then).
- `DEFAULT-MANIFESTS` gains the kit manifest URL.

### Part C — Playback (`audio.cljs`, `samples.cljs`)

- Thread the onset: `schedule-event!`, `offline_render` and `test_api` pass
  `:onset (:start (:whole ev))` (rational) in the `timing` map. A caller without an
  onset gets `time->rand` of 0 (the no-repeat rule still rotates takes).
- `samples/play-layered!` `[ac t bank vel amp pan extra dest]`: picks layer and take,
  updates the per-context last-take `WeakMap` (keyed `[bank layer-index]`), computes the
  level, sets `:cut` to `[:kit prefix choke]` when the entry has `:choke` and `extra` has
  no `:cut`, then delegates to `play!` with the chosen URL (refactor `play!` so its body
  takes a URL; `play!` keeps its signature).
- `play-event`: on the bare-keyword path and the `:note`-map sample path, when
  `drum-route` returns `{:kind :sample :bank b}` and `b` is `layered?`, call `play-layered!` with `amp × gain` (compute the
  factor without velocity: add `inserts/gain-factor` next to `amp-factor`) and the
  clamped velocity. `{:bank :n}` maps (`sound`) keep `play!` and `amp-factor`
  (decision 6). `sample-length` (used for per-event inserts) takes the chosen URL.
- `velocity 0` on a layered bank schedules nothing (no voice, no fetch).
- MIDI out / MIDI export keep deriving velocity from `amp` (unchanged; noted in docs).

### Part D — Assets & build script

- `scripts/kits/build-kit.mjs <config.json>` (Node, no npm dependencies, shells out to
  `ffmpeg`/`ffprobe`). The config names the source SFZ, maps source regions to
  instruments (`lovel`/`hivel`, `seq_position` or `lorand`/`hirand`), and sets the
  layer/take counts. The script:
  1. picks layers spread evenly over the source velocity range and the first N takes;
  2. sums multi-mic sources to the SFZ's own mix if the SFZ mixes them, else uses the
     stereo mix/overhead file named in the config;
  3. trims leading silence to the first sample above −50 dBFS minus 1 ms, cuts the tail
     at −60 dBFS (max 4 s cymbals, 1.5 s everything else), 5 ms fade-out;
  4. mono for everything except `cr rd rb oh` (keep stereo);
  5. measures RMS per layer, writes `gain` per [formulas](#formulas) (top layer 1.0,
     peak of the loudest file ≤ −1 dBFS after gain);
  6. encodes, writes the files under `app/public/kits/<Kit>/<inst>/v<L>-<T>.ogg`, and
     merges the entries into `app/public/kits/kits.json`;
  7. prints a size report and exits non-zero over the budget (decision 8).
- Configs `scripts/kits/JazzKit.json`, `scripts/kits/StudioKit.json` are committed;
  sources are not.
- `app/public/kits/<Kit>/LICENSE.md`: source name and URL, author, licence name and
  full licence text (or the official URL for CC texts plus the required notice),
  download date, and "Modifications: selected layers and takes, trimmed, faded,
  downmixed, level-matched, re-encoded as Ogg Vorbis by the REPuLse project."
  CC-BY-SA kits add "These derived samples are licensed under CC-BY-SA 3.0."
- Re-running the script with the same sources and config produces byte-identical
  `kits.json` (sorted keys, fixed number formatting).

### Docs

- `docs/USAGE.md`: new "Drum kits" section after `bank` (selecting a kit, velocity
  layers, round-robin, `sound` as the fixed-take escape hatch, hat choke, the
  instrument table, how to write your own layered manifest for `samples!`); add both
  kits to "Available sample banks"; update the `velocity` row/section with the layered
  rule (decision 4). A short "Credits" line per kit with licence.
- `velocity` text in `builtin_meta.edn`, `hover.js` and `completions.js` gains "on
  layered kit banks it picks the recording layer"; run `npm run gen:ai-docs`.
- `README.md`: one line in the sounds list mentioning the two kits.
- `docs/ARCHITECTURE.md` sample-loading paragraph: layered banks and the kit manifest.

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/kits.cljs` (new), `kits_test.cljs` (new) | Part A |
| `app/src/repulse/samples.cljs` | Part B, `play-layered!`, `play!` refactor |
| `app/src/repulse/specs.cljs` | kit entry spec |
| `app/src/repulse/audio.cljs` | Part C dispatch, onset threading |
| `app/src/repulse/inserts.cljs` (+ test) | `gain-factor` |
| `app/src/repulse/offline_render.cljs`, `test_api.cljs` | pass `:onset` |
| `app/src/repulse/env/builtins/samples.cljs` | kit preload on `bank` |
| `scripts/kits/build-kit.mjs`, `scripts/kits/*.json` (new) | Part D |
| `app/public/kits/**` (new) | kit files, `kits.json`, `LICENSE.md` per kit |
| `e2e/kit1-kits.spec.ts` (new) | e2e tests |
| `.gitignore` | `.kit-sources/` |
| docs listed above, `ROADMAP.md` | Docs, delivery |

## Tests

**Unit (`kits_test.cljs`, `inserts_test.cljs`, existing samples tests):**
- `parse-entry`: valid entry; each invalid case (empty layers, descending `v`, last
  `v` ≠ 1, `gain` 0, non-string file) returns nil with a reason; `_base` applied.
- `pick-layer` on bounds `[0.3 0.6 0.85 1]`: vel 0.1 → 0, 0.3 → 0, 0.31 → 1, 0.6 → 1,
  0.9 → 3, 1.0 → 3.
- `layer-scale`: first layer vel 0.15 → 0.5; layer 1 at vel 0.3+ε ≈ 0.6, at 0.6 → 1.0.
- `pick-take`: k = 1 → 0 always; k = 3 over 100 consecutive onsets (1/16 steps) never
  returns `last`, uses all three takes, and is identical on a second run.
- A manifest mixing array and layered entries: arrays parse exactly as before (assert
  against the current `parse-manifest` output for the same arrays).
- `amp-factor` unchanged; `gain-factor` = clamped gain.

**E2E (`e2e/kit1-kits.spec.ts`, TEST1 harness).** Route-mock a fixture manifest
`FixKit_*` whose files are generated in the test (0.2 s sines, WAV via a few lines of
Node): layer L take T of `FixKit_bd` = `200·L + 20·T` Hz (layers `v` 0.3/0.6/1).
Identify which file sounded per onset with a Goertzel scan over the rendered buffer.
- `(->> (seq :FixKit_bd) (velocity 0.1))` → 220/240 Hz family (layer 1); `0.9` → layer 3.
- `(bank :FixKit) (fast 8 (seq :bd))` over 1 cycle: ≥ 2 different takes, no take twice
  in a row, sequence identical over two renders.
- `(sound :FixKit_bd 4)` → always flat file 4 (layer 2, take 2 → 440 Hz).
- `(->> (seq :FixKit_bd) (velocity 0))` → silent buffer.
- Level: velocity 0.3 vs 0.31 (layer boundary) RMS within 1.5 dB of what the formulas
  give with the fixture gains.
- Choke: `FixKit_oh` (2 s sine) then `FixKit_hh` ¼ cycle later — energy at the oh
  frequency drops ≥ 30 dB within 10 ms after the hh onset; with `(cut 3)` on the hh it
  does not.
- Regression: `(bank :RolandTR909) (seq :bd :sd)`, `(bank :dirt) (seq :bd :sd)`,
  bare `(seq :bd :sd)` (synth kit) and `(sound :bd 3)` render the same onsets and
  non-silent output as before.
- Real kits (served from `app/public`): `(bank :JazzKit)` and `(bank :StudioKit)`
  each render `(seq :bd :sd :hh :oh :lt :cr)` non-silent at every onset, and two
  renders are sample-identical.

## Definition of done

- [ ] Part 0 results (licence texts, WebKit decode) recorded in the PR.
- [ ] Licence of every shipped sample in `app/public/kits/<Kit>/LICENSE.md`; no
      proprietary content; build script under budget.
- [ ] All unit and e2e tests above pass; existing manifests and `(bank …)` unchanged.
- [ ] USAGE "Drum kits" section, `velocity` docs updated, `gen:ai-docs` committed.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] Human listen check recorded in the PR; each kit marked *ship* or *hold*.
- [ ] ROADMAP delivered section, status cell, *Where we stand*.

## Listen check

Headphones, both kits, in the browser (not only offline):

1. **Velocity sweep.** `(bank :JazzKit) (->> (fast 8 (seq :sd)) (velocity (slow 4 saw)))`
   — over 4 cycles the snare goes from brushed-soft to full, the tone changes at layer
   changes but the level rises smoothly (no step louder than the neighbours). Repeat for
   `:bd`, `:hh`, `:rd` and for StudioKit.
2. **Machine gun.** `(fast 16 (seq :hh))` vs `(fast 16 (seq (sound :JazzKit_hh 0)))` —
   the first sounds like a player, the second like a loop.
3. **Choke.** `(seq :oh :_ :hh :_)` — the open hat is cut by the closed hat.
4. **Mix level.** `(stack (seq :bd :_ :bd :_) (seq (sound :bd 0) :_ :_ :_))` with and
   without `(bank :JazzKit)` — kit level within roughly ±6 dB of Dirt-Samples.
5. **Groove.** A demo-style beat with ghost notes and accents on each kit; decide
   ship/hold per kit and note it in the PR.
6. **Safari.** One kit pattern plays in Safari (Ogg/FLAC decode).

## What NOT to do

- Don't ship or link proprietary (e.g. Roland) sample content, or anything NC/ND.
- Don't commit source downloads (`.kit-sources/`).
- No `kit` built-in, no new prefix mechanism (decision 1).
- No sampler instrument editor or kit-builder UI.
- No pitched multisampled instruments (pianos etc.). The format allows them later
  (a `"key"` range per layer is a backwards-compatible addition), but don't add it now.
- No layered `.edn` Lisp manifests in this phase.
- Don't change MIDI velocity derivation or how `velocity` works on non-layered sounds.
