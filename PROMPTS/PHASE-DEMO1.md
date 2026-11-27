# Phase DEMO1 — Song Demos: Real Music That Shows Everything

> **Automation:** 🤖👂 auto + human check — the demos can be written, and their harmony,
> form and levels verified by tests unattended, but only a human can say whether a demo is
> a good *song*. The phase is delivered after the owner's listen check (see *Listen check*).

**Milestone:** 2 — Full songs
**Depends on:** SONG1, SONG2 (song form, automation, fills), SONG4 (multi-buffer projects),
TL5 (chord symbols, `voicing`, `arp`), SYN6, FX1, KIT1 (sound palette) — all delivered.
Soft: MST1 (loudness). If MST1 lands later, re-run the level test (D-L1) and re-balance.

## Goal

The nine current demos are genre *sketches*: 30–50 lines, one loop, one feature group each.
They prove the engine works but they are not music anyone would listen to twice. DEMO1 adds
**ten song demos** — complete pieces of 1½–3 minutes with an intro, a form and an ending —
that are written with real harmony (functional progressions, voice leading, modulation,
modes, odd meters, counterpoint) and that together use **at least half of the musically
relevant built-ins** of REPuLse. Every song demo states in its header which harmonic idea it
is built on and what to listen for, so the demos also teach. The sketches stay as short
starters; `(demo)` lists both groups.

```lisp
;; Before — every demo is one loop of one genre
(demo :house)            ; 4 tracks, plays forever, Am7 – Dm7 back and forth

;; After — complete pieces, each built on one idea from harmony
(demo)                   ; songs:    anthem aurora blues bossa canon cinema lofi nocturne odyssey sunrise
                         ; sketches: ambient dnb dub experimental house minimal modular rock techno
(demo :nocturne)         ; AABA jazz ballad in Db: ii–V–I chains, tritone subs, voice-led maj9/13 chords
(demo :anthem)           ; pop song: intro, 2 verses, pre-chorus with ♭VI–♭VII, chorus, bridge,
                         ; last chorus a whole step up, outro — 2:40, then stops
(play-from :bridge)      ; every song demo is a real `song`, so navigation works
```

## How many demos? — the coverage argument

"Show what the thing can do" is made measurable instead of guessed:

- **Relevant built-ins.** Of the 431 built-ins in `docs/ai/builtins.json`, the categories
  `source` (42), `transform` (128), `signal` (32), `synth` (36), `param` (59), `ugen` (10),
  `effect` (17) and `sample` (4) are music; plus the special forms `def defn let fn defmacro
  defsynth loop` and `slider`. That is **336 names**. Session, io, ai, midi and transport
  commands (`snapshot!`, `share!`, `midi-out`, `export` …) are not demo material.
- **Today.** The nine sketches use 60 of them (18 %, counting `(synth :x)` and `(fx :x)` names).
- **Per song.** A full song with 6–9 tracks and a song form uses 30–45 distinct built-ins.
  The first songs add ~25 new names each; because the basics (`track`, `seq`, `~`, `amp`,
  `synth`, `scale`, `chord`, `song`, `section`) repeat, later songs add ~12–15.
- **Estimate.** 60 + 2×25 + 8×13 ≈ **215 names ≈ 64 %** after ten songs. Each further song
  would add fewer than 10 names; the long tail (`pick-mod-restart`, `chunk-back-into`,
  `euclid-legato-rot`, …) belongs in the tutorial and USAGE examples, not in songs that have to
  sound good first.

So: **ten song demos**, with a test-enforced floor of **≥ 55 % overall** (D-C1) and a floor
per category (D-C2), so the number stays honest when demos are edited. Ten is also the most a
human can listen through carefully in one session (~25 minutes), which the listen check needs.

## Background

### Demo storage and loading

- `app/src/repulse/content/demos.cljs` — `demo-templates` is a map `keyword → {:bpm n :code
  "…"}`. Every demo starts with `;; NAME — …`, a `;; Shows:` line (enforced by
  `demos-name-their-features`) and calls `(bpm n)` matching `:bpm` (`demo-bpm-matches-code`).
- `demo-builtin` returns the `demo` built-in: no argument → `"available demos: …"` (sorted
  keys); with a keyword → `audio/stop!`, `audio/set-bpm!`, replace the whole editor document
  via `.dispatch` on the current `EditorView`, then evaluate after 50 ms through
  `evaluate-ref`. It only knows a single buffer.
- `app/src/repulse/content/first_visit.cljs` — on first visit / after `(reset!)` picks a
  random key of `demo-templates` and writes its `:code` into the editor.
- `app/src/repulse/content/demos_test.cljs` — `every-demo-parses`, `demo-bpm-matches-code`,
  `demos-name-their-features`, `demos-only-call-known-builtins` (every head symbol is a
  built-in from `repulse.ai.knowledge/builtins` or defined in the demo). Its helpers
  `called-names` / `defined-names` are what the coverage test builds on.
- `docs/USAGE.md` → *Demos and tutorials* → table `| Demo | BPM | Style | Shows |`.

### Multi-buffer projects (SONG4)

- `app/src/repulse/project.cljs` — pure model (`empty-project`, `add-buffer`, `edn->project`).
- `app/src/repulse/ui/buffers.cljs` — `replace-project!` swaps the whole project (one
  `EditorState` per buffer, tab bar), `switch-to!`. `(use :drums :bass)` in `main` evaluates
  the other buffers. Alt+Enter evaluates the project from `main`.

### What a song can use

- **Form:** `(song …)` with `(section :name bars [tracks] items…)`, `:end :stop|:loop`,
  `(play-from :section)` (SONG1). Items: `(automate :track :param curve [:bars [a b]] [:op …])`,
  `(ramp :linear|:exp|:sine from to)`, `(fill :track bars pattern|:_)` (SONG2).
- **Harmony (TL5):** `(chord "Dbmaj9")` — 34 qualities, slash chords; `voicing` with
  `:anchor :mode :offset` (voice leading); `root-notes`; `arp` (13 modes) / `arp-with`;
  `rolled` / `rolled-by`; `scale` (0-based, ~30 scales incl. modes, `:blues`, pentatonics);
  `scale-transpose`; `transpose` with interval strings (`"3M"`).
- **Time & rhythm:** `swing` / `swing-by`, `timecat`, `polymeter`, `euclid*`, `iter`,
  `palindrome`, `rot`, `ply`, `chunk`, `whenmod`, `within`, `off`, `echo`, `nudge`.
- **Randomness & signals:** `with-seed`, `wchoose`, `randcat`, `degrade-by`, `perlin`,
  `brand`, `sine`/`saw`/`tri` signals, `range`, `segment`, `tween`.
- **Sound:** 36 synth presets (`epiano bell strings brass-pad sweep-pad nylon harp pizz lute
  sax flute trumpet supersaw acid reese sub-bass fm-bass sync-lead pad theremin …`),
  `defsynth` + UGens, KIT1 drum kits via `bank`, samples via `sound`, `chop`, `splice`,
  `loop-at`, `slice`.
- **Mix:** per-event params (`cutoff lp-env room delay orbit vib glide unison detune …`),
  `fx` chains (`dattorro-reverb eq limiter amp-sim cab width auto-pan fold chebyshev …`),
  `(master …)` (WS2), `:mute`/`:solo` in code (WS5), `(slider v min max)` (WS3).

### Test harness

`app/src/repulse/test_api.cljs` exposes `window.__REPULSE_TEST__` on `test-harness.html`:
`evalToEvents(code, cycles)`, `songEvents(code, fromBar, toBar, opts)` and
`renderOffline(code, cycles)` (offline-js backend). `e2e/helpers/audio-analysis.ts` has
`rms`, `rmsStereo`, `rmsPerWindow`. Voices only the WASM backend renders (Karplus-Strong,
bowed, some FM presets) may render silent in `renderOffline`; the level test uses the EXP1
offline export path for those (see Part D).

## Implementation

### Part A — Data model and loading

1. In `demos.cljs`, extend each template with metadata (sketches get `:kind :sketch`):

   ```clojure
   :nocturne
   {:kind  :song
    :bpm   72
    :title "Nocturne"
    :key   "D♭ major"
    :idea  "ii–V–I chains, tritone substitution, voice-led extended chords"
    :code  "…"}                       ; single-buffer song
   :sunrise
   {:kind    :song
    :bpm     138
    :buffers [[:main "…"] [:drums "…"] [:harmony "…"] [:lead "…"]]}   ; project demo
   ```

   Exactly one of `:code` / `:buffers`. `:buffers` is an ordered vector; the first entry is
   `:main` and contains the `(use …)` call and the `song`.
2. `demo-builtin`:
   - no argument → `"songs: anthem aurora … · sketches: ambient dnb …"` (both sorted).
   - `:buffers` demo → build the project with `repulse.project` (`empty-project` +
     `add-buffer`) and call `buffers/replace-project!`, then evaluate the project from `main`
     the same way Alt+Enter does (not `evaluate-ref` on a single string). Pass the needed fns
     in from `app.cljs` like `editor-view-atom` / `evaluate-ref` today, so
     `repulse.content.demos` does not require `repulse.ui.*` (layering).
   - `:code` demo → unchanged path. Loading any demo while a project with several buffers is
     open replaces the project with a single `main` buffer (otherwise old buffers keep
     playing via `use`).
3. `first_visit.cljs` — pick only from templates with `:first-visit true`. Mark three songs
   that start strong within the first 4 bars (proposal: `:anthem`, `:lofi`, `:bossa`) plus the
   current sketches `:house` and `:techno`.

### Part B — The ten song demos

Each song demo:

- starts with a header block:

  ```lisp
  ;; NOCTURNE — jazz ballad in D♭ major, AABA, 72 bpm, ~2:40
  ;; Theory: ii–V–I chains through three keys; the B part reaches E♭m7 – A♭7 via the
  ;;         tritone sub D7 → D♭; voicing keeps the top voice moving by step
  ;; Listen for: the bass falling a half step on the sub; the 13th on every dominant
  ;; Form: intro 4 · A 8 · A 8 · B 8 · A 8 · outro 4
  ;; Shows: chord symbols, voicing :anchor, rolled, swing-by, epiano, sub-bass, orbit sends
  ;; Try: (play-from :b), or change :anchor :c5 to :g4
  ```

  `;; Theory:`, `;; Listen for:`, `;; Form:`, `;; Shows:` and `(bpm n)` are mandatory
  (enforced by tests D-M1/D-M2). Comments explain the harmony in one or two plain sentences,
  for a reader who knows what a chord is but not what a tritone substitution is.
- is a `(song …)` with named sections and `:end :stop` (except `:aurora`, see below), lasts
  1:30–3:00 at its bpm, and ends on a resolved ending (final chord, fade, or button) — never
  by just stopping mid-loop.
- uses `def`/`defn` for its material (progressions, motifs, voices) so the code reads as a
  score: harmony on top, instruments in the middle, form at the bottom.
- stays readable: ≤ 160 lines per buffer, no line over 100 characters.
- has a mix where nothing clips, melody sits above accompaniment, and bass and kick do not
  mask each other (see Part D for what is measured).

| Demo | Style · key · bpm | Harmony idea (the "Theory" line) | Engine showcase (headline features) | Form |
|---|---|---|---|---|
| `:nocturne` | Jazz / neo-soul ballad · D♭ · 72 | ii–V–I chains, tritone substitution, extended chords (maj9, m11, 13, 7♭9), voice leading | chord-symbol strings, `voicing` `:anchor`/`:mode`, `rolled-by`, `swing-by`, `:epiano`, `:sub-bass`, room/delay sends via `orbit` | AABA 32 bars + intro/outro |
| `:anthem` | Pop · G → A · 100 | I–V–vi–IV, modal interchange (♭VI–♭VII–I) in the pre-chorus, final chorus modulates up a whole step | full `song`: intro, verse ×2, pre, chorus, bridge, chorus↑, outro; `automate` filter risers, `fill` drum fills, `play-from`; KIT1 kit via `bank`, `:supersaw`, `:pluck`, `:strings` | verse/chorus pop form |
| `:canon` | Baroque · D · 80 | ground bass (I–V–vi–iii–IV–I–IV–V), 3-voice canon entering every 2 bars, sequence by falling fifths, cadences | Lisp as score: `defn voice`, `late` entries, `map`/`reduce` to build voices, `scale-transpose`, `:harp`, `:lute`, `:pizz`, `:strings` | ground + entries, cadence |
| `:bossa` | Bossa nova · A minor · 130 | minor ii–V (Bm7♭5–E7♭9), line cliché Am–Am(maj7)–Am7–Am6, chromatic descent | `:nylon` comping via `struct` + bossa clave (`timecat`/`euclid`), `:flute` melody with `vib`/`glide`, `bpf`, `nudge` humanising | A–B–A |
| `:blues` | Shuffle blues · E · 96 | 12-bar form (I7–IV7–V7), quick change, turnaround, blues scale over dominant chords, call & response | `swing`, `:guitar` + `amp-sim` + `cab`, `:sax` lead, `whenmod` turnaround, `chunk`, improvised solo chorus from `:blues` scale with `wchoose` + `with-seed` | 4 choruses (head, solo, solo, head) |
| `:cinema` | Film score · D minor · 70 | epic minor i–♭VI–♭III–♭VII, chromatic mediant (D♭ major shift), pedal point, sus4→3 suspensions, Picardy third (ends on D major) | `:strings`, `:brass-pad`, `:sweep-pad`, synth timpani/taiko, `automate` crescendo with `(ramp :exp …)`, `dattorro-reverb`, `eq`, `width`, `(master … limiter)` | arch: quiet → climax → resolution |
| `:aurora` | Generative ambient · D Lydian · 60 | Lydian ♯4 colour, no functional progression: pedal + drifting cluster chords from pentatonic subsets; loops of incommensurate length (5, 7, 11, 13 cycles) never line up the same way twice | `polymeter`, `slow` with prime lengths, `perlin`, `brand`, `randcat`, `wchoose`, `segment` + `range` modulating `cutoff`, `auto-pan`, `:pad`, `:bell`, `:theremin` | free-running; `song` with `:end :loop` over one long section, so it never ends |
| `:lofi` | Lo-fi hip hop · F · 82 | jazz 9th/11th chords, borrowed minor iv (B♭m6 in F), chromatic passing chord | sample chops: `chop`, `splice`, `loop-at`, `slice`, `swing-by`, `crush`/`coarse`, `bitcrush`, `djf` filter sweep, `rolled`, ducking via an `amp` pattern | intro, loop A ×2, break, loop B, outro |
| `:odyssey` | Prog / math rock · E dorian · 140 | odd meters (7/8 grouped 2+2+3, 5/4 bridge), polyrhythm 3:4, quartal harmony (stacked fourths), dorian | `timecat`, `polymeter`, `iter`, `rot`, `palindrome`, `ply`, `jux`, `:sync-lead`, `:fm-bass`, `fold`/`chebyshev` distortion, a `defsynth` lead | A (7/8) – B (5/4) – A′ |
| `:sunrise` | Trance · A minor · 138 · **project demo** | i–♭VI–♭III–♭VII (Am F C G), suspended arpeggios, dominant pedal in the build, tension → release dramaturgy | 4 buffers via `(use :drums :harmony :lead)`, `arp` modes, `:supersaw` `unison`/`detune`, `lp-env`, 16-bar `automate` cutoff ramp, snare-roll `fill` with `ply`, inline `slider` for live cutoff, `:mute`/`:solo` in code, master chain | intro, build, breakdown, build, drop, outro |

The implementer may change keys, tempi, progressions and instrument choices when something
sounds better — the *idea* of each demo (one harmony concept + one engine area) and the
coverage floors are fixed; record deviations in the ROADMAP **Delivered** section.

Write each demo by iterating: draft → `songEvents` check of the chords (Part D) → render →
fix levels. Note names in comments must be the ones the code really produces.

### Part C — Docs

- `docs/USAGE.md` *Demos and tutorials*: split into **Song demos** (table: Demo, Style, Key,
  BPM, Length, Harmony idea, Shows) and **Sketches** (existing table). Explain `(demo)`
  listing, that song demos are `song`s (so `play-from` works) and that `:sunrise` opens as a
  project with four buffers.
- `README.md`: one line in the quick start: `(demo :nocturne)` hears a complete piece.
- New `docs/DEMO-COVERAGE.md`, generated by `npm run gen:demo-coverage`
  (`scripts/gen-demo-coverage.mjs`): per category the used / relevant counts and the list of
  relevant built-ins no demo uses. Generated, never hand-edited; CI does not check drift
  (it is informational), but the floors are enforced by D-C1/D-C2.

### Part D — Tests

In `demos_test.cljs` (CLJS, `npm run test`):

- **D-M1** every template has `:kind` ∈ `#{:song :sketch}` and exactly one of `:code` /
  `:buffers`; `:buffers` starts with `:main`.
- **D-M2** every song demo (all buffers concatenated) contains the lines `;; Theory:`,
  `;; Listen for:`, `;; Form:`, `;; Shows:`, a `(song` form and `(bpm n)` matching `:bpm`;
  ≤ 160 lines per buffer and ≤ 100 characters per line.
- Existing tests run over every buffer of project demos.
- **D-C1** union of `called-names` over all demos ∩ the relevant set (categories above +
  special forms) ≥ **55 %** of the relevant set. Print the actual ratio in the test name or
  message so it shows in the log.
- **D-C2** per-category floors: `source` ≥ 50 %, `transform` ≥ 45 %, `signal` ≥ 40 %,
  `synth` ≥ 60 %, `param` ≥ 45 %, `ugen` ≥ 60 %, `effect` ≥ 70 %, `sample` ≥ 75 %.
  Counting rule (same as the estimate above): a name counts when it is called in head
  position, or when it is the first argument of `(synth :x …)` / `(fx :x …)`. Keyword
  options such as `:unison 3` also count when a built-in of that name exists in the `param`
  category. Document the rule in the test.

In a new `e2e/demos.spec.ts` (TEST1 harness), music-theory assertions on real events:

- **D-H1 `:nocturne`** — bass roots of bars 5–12 (first A) follow the written ii–V–I chain;
  every dominant-quality chord event set contains its 13th.
- **D-H2 `:anthem`** — the pitch-class multiset of the last chorus equals the first chorus
  transposed by +2 semitones; pre-chorus contains E♭ and F major triads (♭VI, ♭VII in G).
- **D-H3 `:canon`** — voice 2 equals voice 1 delayed by exactly 2 cycles (same notes,
  onset + 2), voice 3 by 4 cycles, over the canon section.
- **D-H4 `:blues`** — chord roots over bars 1–12 are E A E E A A E E B A E B (quick change,
  turnaround); every solo-chorus melody note is in E blues.
- **D-H5 `:cinema`** — the final sounding chord contains F♯ (Picardy third), no event of
  the last section contains F♮.
- **D-H6 `:odyssey`** — in an A-section bar, drum onsets fall on the 7/8 grid (multiples of
  1/7 of the cycle, accents on 0, 2/7, 4/7); in the B section on the 5/4 grid.
- **D-H7 `:aurora`** — with the default seed, 64 consecutive cycles contain no two identical
  8-cycle windows (event value+onset sets), and every note is in D Lydian.
- **D-H8 `:sunrise`** — the project evaluates through `(use …)`; chord roots of the drop
  are A F C G; the `automate` cutoff rises monotonically across the build.
- **D-H9** every song demo: `songEvents` over all bars returns events in the last section,
  none after the song's length, and no `unknown sound` warning.

Levels (`e2e/demos.spec.ts`, render via the EXP1 offline export path so WASM voices
sound; `renderOffline` only where it is equivalent):

- **D-L1** each song demo, rendered from a representative 8-bar window (the chorus/drop/
  climax, named per demo in the spec file): sample peak ≤ −0.3 dBFS, no NaN, RMS within
  ±4 dB of the median RMS of all ten. Sketches are not re-balanced.
- **D-L2** project and UI: `(demo :sunrise)` in the app creates buffers `main drums harmony
  lead`, plays, and `(demo :blues)` afterwards leaves exactly one buffer `main`.

### Listen check (owner, before marking delivered)

The owner listens to every song demo start to finish (≈ 25 min) and answers per demo:

1. Would I play this to someone as an example of REPuLse? (yes / no)
2. Does the harmony idea named in `;; Theory:` audibly come through? (yes / no)
3. Mix: anything too loud, buried, harsh or muddy? (note)

A demo with a "no" is reworked and re-checked. The answers go into the ROADMAP
**Delivered** section.

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/content/demos.cljs` | `:kind :title :key :idea :first-visit`, `:buffers` support, ten song demos, `(demo)` listing in two groups, project loading |
| `app/src/repulse/app.cljs` | pass project-loading / project-evaluation fns into `demo-builtin` |
| `app/src/repulse/ui/buffers.cljs` | ? — only if `replace-project!` needs a variant that skips persisting or switching tabs |
| `app/src/repulse/content/first_visit.cljs` | pick from `:first-visit true` templates |
| `app/src/repulse/content/demos_test.cljs` | D-M1, D-M2, D-C1, D-C2; existing tests over all buffers |
| `e2e/demos.spec.ts` | new: D-H1–D-H9, D-L1, D-L2 |
| `scripts/gen-demo-coverage.mjs`, `package.json` | new script `gen:demo-coverage` |
| `docs/DEMO-COVERAGE.md` | new, generated |
| `docs/USAGE.md` | *Demos and tutorials*: song demo table, sketches table, project demo |
| `README.md` | quick-start line |
| `app/src/repulse/content/builtin_meta.edn` | update `demo` `:doc`/`:examples` (songs and sketches); then `npm run gen:ai-docs` and commit generated files |
| `ROADMAP.md` | delivered section incl. listen-check answers, status ✅, *Where we stand* |

## Definition of done

- [ ] `(demo)` prints `songs: anthem aurora blues bossa canon cinema lofi nocturne odyssey sunrise · sketches: ambient dnb dub experimental house minimal modular rock techno`
- [ ] `(demo :anthem)` loads a single-buffer `song` that plays intro → outro in 2:00–3:00 and stops by itself; `(play-from :bridge)` jumps to the bridge
- [ ] `(demo :sunrise)` opens a project with buffers `main drums harmony lead`; Alt+Enter re-evaluates it from `main`; `(demo :techno)` afterwards leaves only `main`
- [ ] `(demo :aurora)` plays without stopping and passes D-H7 (no repeated 8-cycle window in 64 cycles)
- [ ] Every song demo has `;; Theory:`, `;; Listen for:`, `;; Form:` and `;; Shows:` lines whose note names and chords match the events the code produces (D-H1–D-H8)
- [ ] D-C1: the demos together use ≥ 55 % of the relevant built-ins; D-C2 per-category floors pass; `docs/DEMO-COVERAGE.md` is generated and committed
- [ ] D-L1: no song demo clips and all ten are within ±4 dB RMS of their median in the reference window
- [ ] No song demo produces an `unknown sound` warning or an eval error on load
- [ ] The nine sketches still load and play unchanged (existing `demos_test` passes)
- [ ] First visit loads only `:first-visit true` templates
- [ ] `docs/USAGE.md` lists every song demo (style, key, bpm, length, harmony idea, shows); `README.md` quick start mentions `(demo :nocturne)`
- [ ] `builtin_meta.edn` entry for `demo` updated and `npm run gen:ai-docs` output committed
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass
- [ ] Owner listen check done, answers recorded in ROADMAP; every demo has two "yes"
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5)

## What NOT to do

- No new built-ins, synth voices or effects. If a demo "needs" one, change the demo; list
  the wish in the PR as a follow-up (SYN7/FX2 hold the deferred sound palette).
- Don't rewrite or delete the nine sketches; only add `:kind :sketch` (and `:first-visit`).
- No demo browser UI, thumbnails or a gallery panel — `(demo)` and USAGE are the interface.
  A visual demo picker is a separate phase.
- No samples beyond the banks the app already loads by default; no external downloads.
- Don't tune the master output stage or loudness to make demos louder — that is MST1.
- No covers or recreations of existing songs: the ground bass, progressions and forms are
  common musical vocabulary, the melodies are original.
