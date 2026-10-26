# Phase KIT1 — High-Quality Drum Kits

> **Spec status:** outline — expand into a full spec before implementing. The licence
> research below must be finished first.
>
> **Automation:** 🤖👂 auto + human check — loading, layer selection and round-robin are
> testable automatically; choosing kits and judging sound quality needs a human listen.

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** TL1 (patterned params). Coordinates with TL7 (`velocity` param) and SK3
(captured velocity writes this param).

## Goal

Ship drum kits with studio quality, comparable to modern electronic drum modules (for
example Roland V-Drums), alongside the existing Dirt-Samples and Tidal Drum Machines
banks. Quality comes from **velocity layers** (different recordings for soft and hard
hits) and **round-robin** (alternate recordings so repeated hits don't sound like a
machine gun), not just from better single samples.

```lisp
;; Before — one sample per hit, loudness only via amp
(seq :bd :sd :bd :sd)

;; After (proposed)
(kit :studio)                                               ; select a multisampled kit
(->> (fast 2 (seq :hh :hh :hh :hh))
     (velocity (seq 0.9 0.4 0.7 0.4)))                      ; picks soft/hard recordings
(->> (seq :sd :sd :sd :sd) (velocity "0.2 0.5 0.8 1"))       ; ghost note → accent
```

## Background

- Sample loading: `app/src/repulse/samples.cljs` (Strudel CDN manifests, GitHub repos,
  `samples!`, lazy decode + cache). Phase F `(bank :Name)` prefixes.
- Tidal Drum Machines already include Roland TR-808/909/707/606 *machine* samples.
- **Licensing is the main constraint.** Roland's own module samples (V-Drums, TD-series)
  are proprietary and must not be redistributed. Candidates to verify (licence, quality,
  layers, size): Versilian VCSL percussion (reported CC0), DrumGizmo kits (reported
  CC-BY), Hydrogen drumkits (per-kit licences), freesound packs (per-file licences).
  Treat all of these as *unverified* until checked.

## Scope

- Manifest format extension: per-instrument velocity layers (`[{:vel-max 0.3 :files [...]}, …]`)
  and round-robin groups; backwards compatible with existing manifests.
- `velocity` per-event parameter (0–1): selects the layer, then applies a gain curve.
  For synth voices it maps to amplitude.
- Round-robin selection: deterministic per event (time-seeded, TL2/TL4 RNG) so offline
  renders are reproducible.
- `(kit :name)`: selects a multisampled kit; works alongside `(bank …)`.
- Hosting: kits served from a CDN or GitHub repo (size budget, lazy loading per instrument).
- One or two curated kits (e.g. acoustic studio kit and electronic kit) with licence files.

## Open design questions

- Which kits clear both the licence and quality bar? (Human listen and decision.)
- Download size: load all layers lazily on first use, or a "lite" set first?
- Should `velocity` and `amp` multiply, or should `velocity` replace `amp` for kits?

## Definition of done

- [ ] Licence of every shipped sample documented in the kit folder; no proprietary samples.
- [ ] `velocity` 0.1 and 0.9 select different recordings (test with a fixture manifest).
- [ ] 8 consecutive identical hits cycle through round-robin recordings deterministically.
- [ ] Existing manifests and `(bank …)` keep working unchanged.
- [ ] Offline render of a kit pattern is non-silent and reproducible run to run.
- [ ] Human listen check recorded in the PR.
- [ ] Rule 3 registration for `kit` and `velocity`; USAGE.md "Drum kits" section.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.

## What NOT to do

- Don't ship or link proprietary (e.g. Roland) sample content.
- No sampler instrument editor or user kit builder UI (later phase).
- No pitched multisampled instruments (pianos etc.) in this phase. The manifest format
  should allow them later.
