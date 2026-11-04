# Phase SK1 — Stem Export & Full-Song MIDI Export

> **Spec status:** full (expanded 2026-11-04 from the outline; design decisions 1–3 by the
> project owner, 2026-11-04; decisions K1–K16 taken during expansion)
> **Automation:** 🤖 auto — offline rendering, WAV/MIDI file structure, durations and per-stem content are all assertable in the TEST1 offline harness and unit tests.

**Milestone:** M4 — Sketchpad
**Depends on:** SONG1 (song length and section form), EXP1 (offline render with WASM voices and FX — stems can't be captured in real time one by one). TL7 recommended (per-event effects to render into stems).

## Goal

Move an idea from REPuLse into a DAW in under a minute: render every track of a song
to its own WAV file (stems) of identical length, and export the whole song — all
tracks, with velocity, drums on the General MIDI drum channel and section markers — as
one multi-track MIDI file. Along the way, fix the `midi-export` bugs that make today's
MIDI files unusable for more than one cycle or for chords, and let `(export :track n)`
take a cycle count.

```lisp
;; Before: one track or the mix, fixed cycle count; MIDI one track at a time, channel 1
(export 4)
(export :drums)            ; 4 cycles only — (export :drums 8) silently ignores the 8
(midi-export :bass 8)      ; every cycle lands on top of cycle 0, chords are corrupt

;; After
(export :stems)            ; repulse-stems.zip: one WAV per track, full song length, same start/end
(export :stems 8)          ; without a song: 8 cycles of every track
(export :drums 8)          ; 8 cycles of one track
(midi-export :song)        ; repulse-song-24bars.mid: one MIDI track per REPuLse track,
                           ; tempo, section markers, velocity from amp, drums on channel 10
(midi-export :bass 8)      ; 8 cycles in the right place, with velocity
```

## Scope

- Stems: render each track separately for the full song length (or `n` cycles without a
  song), through the EXP1 offline renderer, with its per-track FX chain; identical frame
  counts; one `repulse-stems.zip` download (decision 1).
- Full-song MIDI: format-1 file, one MIDI track per REPuLse track with notes, a conductor
  track with tempo, time signature and section markers; velocity from `amp`.
- Drum keywords → General MIDI drum notes on channel 10, overridable with `:drum-map`.
- Fix `midi-export`: absolute event times, overlapping notes (chords), exact tick
  rounding, tracks defined in the same buffer.
- `(export :track n)` accepts a cycle count.

## Design decisions

Decided by the project owner on 2026-11-04.

1. Multiple downloads vs. a zip (needs a zip writer — no external CLJS libs in core, but app may allow one?).
   **Decided (2026-11-04):** a single `<project>-stems.zip` download, produced by a small
   hand-written STORE-only (uncompressed) zip writer in `app/` (local file headers, central
   directory, CRC32). No new npm dependency.
2. Render FX into stems now, or after TL7 per-event effects land?
   **Decided (2026-11-04):** now. EXP1's offline path renders WASM voices and FX, so each stem
   includes its per-track FX chain. Global/master FX are applied to the mix export only, not
   to stems; document this as a limitation.
3. Velocity mapping: `amp` 0–1 → velocity 1–127 linear, or a curve?
   **Decided (2026-11-04):** linear, `velocity = clamp(round(amp × 127), 1, 127)`
   (amp 0.5 → 64, matching the Definition of done).

## Decisions taken during spec expansion

Questions the outline left open, decided in line with its intent ("into a DAW in under a
minute", stems that sound like the track, decisions 1–3).

- **K1 — `<project>` is `repulse`.** The project model (`app/src/repulse/project.cljs`)
  has no project title, and every existing export is named `repulse-…`
  (`repulse-all-4cycles.wav`, `repulse-song-24bars.wav`, `repulse-lead.mid`). The zip is
  `repulse-stems.zip`; its entries are `repulse-<track>.wav`, in song track order (buffer
  order) for a song and sorted by track name for `(export :stems n)`. Characters outside
  `[A-Za-z0-9._-]` in a track name become `_`; a name that collides after that gets
  `-2`, `-3`, … appended.
- **K2 — Which tracks become stems / MIDI tracks.** With a song: every track of
  `(:tracks song)` that is listed in at least one section (a track defined above the song
  but used in no section would be pure silence and is left out). Without a song
  (`(export :stems n)`): every active track, like `(export n)`. Mute is ignored (EXP1 E12).
- **K3 — `(export :stems)` grammar.** `(export :stems)` — the song (song length + tail,
  default tail 2 s, like `(export :song)`) when one exists, otherwise 4 cycles of all
  tracks (like `(export)`); `(export :stems n)` — `n` cycles of all tracks, with or without
  a song; `(export :stems :tail s)` — song only. `:realtime` is not accepted (decision 2 and
  the dependency note: stems are offline-only). A track named `:stems` can't be exported by
  name (like `:song` and `:realtime`).
- **K4 — Stems have no real-time fallback.** Capturing N tracks in real time one after the
  other would take N × song length. If the offline renderer can't run (any EXP1 fallback
  reason), stems fail with `Error: stems need the offline renderer — <reason>`.
- **K5 — A stem is "the track before the master".** Each stem is rendered by
  `offline-render/render!` with two new job keys: `:stem kw` (only this track's events are
  played and only its FX chain is built) and `:master? false` (no global FX chain, master
  gain fixed at 1.0 — the "master" node is a unity gain straight to the destination). So
  Σ stems equals the *pre-master* mix, not the `(export :song)` mix, which always passes
  through the global chain (the CLJS compressor is always on at threshold −24 dB, the
  default `reverb` entry keeps `dry 0.8`, …). This is decision 2's documented limitation.
- **K6 — Event-driven plugins see every track's events.** `sidechain.js` ducks on
  `onEvent` for events of *other* tracks (`(fx :sidechain :trigger :bd)` on a pad). A stem
  render therefore still calls `fx/notify-plugins!` for the events of **all** exported
  tracks; it only *plays* (`audio/play-event`) the stem's events. A ducked pad stem ducks.
- **K7 — Stems render sequentially**, one `OfflineAudioContext` at a time (each one loads
  the WASM worklet, as in EXP1), to bound memory. Progress goes to the output panel.
  Offline renders stay non-cancellable (EXP1 E14); `export-running?` guards the whole
  stems job.
- **K8 — Zip format.** STORE (method 0) only; version needed / made by = 20 (`0x0014`);
  general-purpose flag `0x0800` (UTF-8 names); fixed DOS time `0x0000` and date `0x0021`
  (1980-01-01 00:00) so archives are byte-reproducible; no extra fields, comments or ZIP64.
  If the archive would exceed `0xFFFFFFFF` bytes, the export fails *before* rendering with
  `Error: stems too large for one zip (over 4 GB) — export fewer bars or tracks`. The
  archive is offered as a `Blob` built from parts (headers + WAV buffers), never copied
  into one big array.
- **K9 — One MIDI encoder, format 1 everywhere.** `(midi-export :track n)` also writes
  format 1 (conductor track + one note track) instead of today's format 0. Every DAW reads
  both, and one encoder means one set of tests. 480 ppqn (unchanged), 4/4, 1 bar
  (cycle) = 4 quarter notes = 1920 ticks.
- **K10 — Ticks come from rational time, not seconds.** `tick = round(n × 1920 / d)` for
  an event start `[n d]` (cycles). Today's `(int (* secs (/ bpm 60.0) 480))` truncates float
  products: e.g. at 120 BPM an event at cycle 49/24 lands on tick 3919 instead of 3920.
  Note length = `max(1, tick(whole end) − tick(whole start))`.
- **K11 — Tempo = `round(cycle-dur × 250 000)` µs per quarter note**
  (`60 000 000 / bpm` with `bpm = 240 / cycle-dur`), rounded, not truncated. BPM is read
  when `midi-export` runs (synchronously during evaluation, as today), so `(bpm …)` must
  come before it in the buffer.
- **K12 — Event → note.** Pitch (melodic channel): note keyword (`theory/note->midi`,
  exact), number = Hz (`midi/hz->midi`), `{:note kw|Hz}`, `{:freq Hz}`; MIDI notes are
  clamped to 0–127 (as `hz->midi` already does). Drum (channel 10): a non-note keyword, a
  `{:note kw}` with a non-note keyword, or `{:bank kw}` (from `(sound :bd 2)`), looked up
  in the drum map; when the full name is not found and it contains `_`, the part after
  the last `_` is tried (`:RolandTR808_bd` → `bd`). Skipped silently: rests (`:_`,
  `{:note :_}`), `{:synth :noise}` without a note. Skipped *with a warning*: keywords with
  neither a pitch nor a drum-map entry (sample names like `:tabla`, bare `defsynth` names).
  `(bank …)` prefixes don't change the event value, so `:bd` stays `bd`.
- **K13 — Default GM drum map** (GM Level 1 percussion key map, Strudel / tidal-drum-machines
  abbreviations): `:bd 36 :rim 37 :sd 38 :cp 39 :hh 42 :lt 45 :oh 46 :mt 47 :cr 49 :ht 50
  :rd 51 :tb 54 :cb 56 :sh 70`. `:drum-map {kw note}` is merged over it (the "configurable"
  of the outline); the drum channel stays 10 (GM). `:drum-map` values must be integers
  0–127.
- **K14 — Channels.** Melodic events of the i-th exported track (0-based, export order)
  use channel `[1 2 3 4 5 6 7 8 9 11 12 13 14 15 16][i mod 15]` — so a single-track export
  keeps today's channel 1. Drum events always use channel 10. One MIDI track may contain
  both (a track mixing notes and drums).
- **K15 — Velocity** follows decision 3 after tweens are resolved
  (`audio/tween-state-for` at start time 0 + `audio/resolve-tweens` at the event's time,
  the same helpers the offline renderer uses, so `(amp (tween …))` exports as a velocity
  ramp). A missing or non-numeric `:amp` counts as 1.0 → 127. Live `midi-out`
  (`audio/schedule-event!`, `clamp(…, 0, 127)`) is **not** changed.
- **K16 — Event order and track ends.** Within a track, events are sorted by
  `[tick kind channel note]` with note-off (`kind 0`) before note-on (`kind 1`), so
  overlapping notes and chords produce non-negative deltas. Note-off = `8n note 00`,
  note-on = `9n note vel`, no running status. Every track ends with `FF 2F 00` at
  `max(end-tick, last event tick)`, where end-tick = `bars × 1920` (song) or `n × 1920`
  (`(midi-export :track n)`), so DAWs see the full length even when the last bars are
  silent. Section markers (`FF 06`, UTF-8 section name without the colon) go in the
  conductor track at `section start bar × 1920`.

## Background

### Current export path (EXP1 has landed — the outline's Background was stale)

- **`app/src/repulse/env/builtins/export.cljs`** no longer renders anything. `export-cycles`
  validates `(export)`, `(export n)`, `(export :track)` and sets
  `song-transport/pending-export {:kind :cycles :n n :track kw :set-output! f}`;
  `export-song` / `parse-song-opts` handle `(export :song [:tail s] [:realtime])` →
  `{:kind :song :tail s :realtime? b …}`. Messages: `realtime-error`, `tail-error`,
  `running-error` (private defs), `"Error: export needs at least 1 cycle"`,
  `"Error: no track :<name>"`, `"Error: no active tracks to export"`. Return value =
  `rendering all tracks — 4 cycles offline…` etc.
  - **Still true from the outline:** a track export is always 4 cycles. `export-cycles`
    takes `n` only when the *first* argument is a number, so `(export :drums 8)` renders 4
    cycles and `(export 8 :drums)` renders all tracks; extra arguments are silently
    ignored.
- **`app/src/repulse/song_transport.cljs`**: `activate-buffer!` → `start-export!` after
  evaluation (EXP1 E5). `export-job` builds `{:kind :cycles|:song :tracks {kw pat} :n :song
  :tail :cycle-dur}`, `start-export!` calls `offline-render/render!`, downloads via
  `wav/encode-wav-16` + `wav/download-wav!`, prints `exported … (offline)`; on a
  `{:fallback true}` rejection it prints `fallback-notice` and runs the SONG1 real-time
  capture (`capture-song!`, `synthetic-song-buffer`). Helpers: `current-song`
  (`pending-song` or last `song-buffer`), `song-summary [s tail]` →
  `"24 bars, 50.0 s"`, `cycles-label`, `cycles-filename`, `export-running?` atom,
  `pending-tracks` (`[[kw pat] …]` collected during evaluation, activated afterwards),
  `song-buffer` (`{:tracks [[kw pat] …] :song s}` of the last activation).
- **`app/src/repulse/offline_render.cljs`** (EXP1): `render! [job]` →
  `Promise<{:left :right :sample-rate :frames}>`, or rejects with
  `(ex-info reason {:fallback true})`. Pure helpers: `fx-plan`, `used-effects [entries
  tracks]` (active global entries + every exported track's `:track-fx` names),
  `fx-plan-reason`, `job-frames [job sr]` (`audio/export-frames` of `n × cycle-dur` or
  `song-duration-secs + tail`), `job-events [job]` (`:cycles` → `core/query-onsets` of
  cycles 0…n−1 per track; `:song` → `song/song-onsets s tracks #{} bar` for every bar).
  Private: `build-graph! [offline sr plan tracks]` (master gain = live master value →
  global chain → destination; per-track chains → master; returns `{:master :instances
  :track-dests}`), `build-instance!`, `replay-global-params!`, `wire-chain!`,
  `await-ready!`, `precondition-failure` (no AudioWorklet, active buses, plan failure).
  `render!` renders at the live `AudioContext` sample rate with `pre-roll-secs` = 0.25 s
  trimmed off, registers the graph (`audio/register-offline-graph!`), resolves tweens
  (`audio/tween-state-for` at `pre-roll-secs`, `audio/resolve-tweens`), calls
  `audio/play-event offline t' value track` and `fx/notify-plugins! plugins value t'` for
  every job event, waits for sample loads, `audio/sync-worklet!`, `startRendering`.
  **Offline render no longer bypasses FX** — the outline's claim about
  `output-for-track` returning the destination only holds for unregistered offline
  contexts (the TEST1 `"offline-js"` harness).
- **`app/src/repulse/audio.cljs`**: `export-frames [secs sr]` = `(Math/round (* secs sr))`;
  `tween-state-for [pattern start-time]`, `resolve-tweens [raw tween-st t cycle-dur]`;
  `get-bpm` = `(/ 240.0 cycle-dur)`; `set-bpm!` stores `:cycle-dur (/ 240.0 bpm)` (BPM
  clamped 20–640 by `coerce-bpm`); `play-event` value shapes: plain keyword (note → tone,
  else sample bank / WASM drum `bd sd hh oh` / `js-synth`), number (Hz), `{:note …
  :amp … :synth …}`, `{:bank kw :n i}` (`sound`), `{:synth :noise}`. WASM voices only
  trigger into the master gain (EXP1 E1), so on a track with `(fx …)` they use JS
  fallback voices — in playback, in exports and therefore in stems.
  Live MIDI out (`schedule-event!`, ~line 851) maps `amp` to `clamp(round(amp × 127), 0,
  127)` — unchanged by this phase.
- **`app/src/repulse/wav.cljs`**: `encode-wav-16 [l r sr]` → ArrayBuffer (44-byte header +
  `frames × 4` bytes), `download-wav! [buf filename]` (Blob `audio/wav`, temporary `<a>`).
- **`app/src/repulse/fx.cljs`**: `chain` (global entries), `notify-plugins! [plugins value
  t]` (`onEvent(name, t)`), `instantiate-plugin`, `validate-effect-nodes!`, `param-log`.
  `app/public/plugins/sidechain.js` ducks its gain in `onEvent` when the event name equals
  its `trigger` — the reason for K6.

### Current MIDI export

- **`app/src/repulse/env/builtins/midi.cljs` `"midi-export"`**: `(midi-export track n)`,
  `n` default 4, no validation. Looks the track up in `(:tracks @audio/scheduler-state)`
  only — **a track defined in the same buffer is not active yet during evaluation
  (SONG1), so `(track :bass …) (midi-export :bass)` returns `Error: no track :bass`.**
  Converts values with `theory/note->hz` → `midi/hz->midi`; **drum keywords are dropped**
  (`:hz nil`); fixed channel 1; downloads `repulse-<track>.mid` with inline `<a>` code;
  returns `exported <n> cycles of :<track> as MIDI`.
- **The time bug still exists:** `:time-sec (* (- ps c) cycle-dur)` — time *within* the
  cycle, so every cycle is stacked onto cycle 0. Verified in the current code.
- **`app/src/repulse/midi.cljs` `export-midi [events bpm]`** — Type 0, 1 track, 480 ppqn,
  tempo `(int (/ 60000000.0 bpm))` (truncated), fixed velocity 100. Further bugs found
  while expanding:
  - it emits each note's on *and* off before the next note and keeps `last-tick` = the
    previous note-*off* tick, so any overlap (chords, `stack`, legato) gives a **negative
    delta**, which `write-vlq` writes as a negative byte → corrupt file;
  - `sec->tick` truncates float products (K10).
  - Private helpers `write-vlq` (correct for n ≥ 0), `write-u16`, `write-u32` are reused.
  - `hz->midi` (rounded, clamped 0–127) is also used by live MIDI out and stays.
- **`app/src/repulse/specs.cljs` `midi-export-event?`** validates the old
  `{:time-sec :duration-sec :midi-note :channel}` shape; only `export-midi` uses it.
- **`packages/core/src/repulse/theory.cljs`**: `note-keyword?`, `note->midi` (`:c4` → 60),
  `note->hz`. `packages/core/src/repulse/core.cljs`: rationals are `[n d]` vectors,
  `rat->float`, `query-onsets`, `cycle-span`.

### Song (SONG1) — `packages/core/src/repulse/song.cljs`

`make-song` resolves sections to `{:name :bars :tracks :start :end}` (`:all` → every
known track); song = `{:type :song :end :stop|:loop :total bars :tracks [kw…]
:sections […]}`. `song-length` = `:total`; `song-duration-secs [song bpm]` =
`total × 240 / bpm` (1 bar = 1 cycle = 4 beats); `song-onsets [song tracks muted bar]`
returns `[{:track kw :event ev}]` for the tracks active in that bar, events shifted to the
bar (for one pass `bar` = song bar, so `:whole :start` is the absolute song cycle).

### Tests and harness

- Unit tests: `npm run test` = `shadow-cljs compile test` (`:node-script`,
  `repulse.test-runner/main` in `packages/core/src/repulse/test_runner.cljs`) — every
  test namespace is listed twice there (`:require` and `run-tests`). App namespaces are
  compiled through it (`song_transport_test.cljs` already requires
  `repulse.env.builtins.export`; `fx_test.cljs` requires `repulse.midi`), so the new pure
  MIDI/zip code can be unit-tested in node. `song_transport_test.cljs` has `make-env`
  and `run-export` helpers that evaluate Lisp with `track` + song builtins.
- `e2e/export-parity.spec.ts` (EXP1) drives the **production app** at `/`
  (`__REPULSE_APP__.evaluate`, `.audioBackend`, Alt+Enter, reads the `download` event),
  aborts `https://raw.githubusercontent.com/**` so `:bd :sd :hh` use the WASM drum voices,
  and uses `e2e/helpers/audio-analysis.ts` (`parseWav16` → floats `/ 32767`, `rms`,
  `rmsPerWindow`, `mixMono`, `goertzel`, `toDb`). Stems must be tested the same way: the
  TEST1 harness (`app/src/repulse/test_api.cljs`, `renderOffline`, backend
  `"offline-js"`) deliberately has no registered offline graph (EXP1 E2) and stays
  untouched.
- `app.cljs` sets `window.__REPULSE_APP__` (`cycleDur`, `muted`, `selection`,
  `trackParam`, `fxParam`, `evaluate`, `audioBackend`).

### Editor / AI docs touching `export` and `midi-export`

`app/src/repulse/lisp-lang/hover.js` (`"export"`, `"midi-export"` entries — the
`export` description still says `(export :song)` "records … in real time", stale since
EXP1), `app/src/repulse/lisp-lang/completions.js` (`export`, `midi-export` details),
`app/src/repulse/content/builtin_meta.edn` (`:export`, `:midi-export` examples),
`docs/ai/builtins.json` (generated by `npm run gen:ai-docs` from those). `docs/USAGE.md`
§ "`export` — render to WAV" (~line 1452) and § "MIDI file export" (~line 3456, still
says "Type 0"). `README.md` line ~56 ("WAV and MIDI export").

## Implementation

### 0. Verify tooling (CLAUDE.md Rule 4)

`npm run gen:grammar && npm run gen:ai-docs && npm run test` on the unmodified branch.
Also `npx playwright test e2e/export-parity.spec.ts -g "length"` once, to confirm the
offline worklet starts in the local headless Chromium.

### 1. Download helper — `app/src/repulse/download.cljs` (new)

```clojure
(defn download-parts!
  "Offer parts (ArrayBuffers / typed arrays) as one file download."
  [parts mime filename] …)   ; Blob of parts, <a download>, revokeObjectURL after 1 s
```

`wav/download-wav!` becomes `(download/download-parts! #js [buf] "audio/wav" filename)`.
The inline `<a>` code in `env/builtins/midi.cljs` goes away.

### 2. Zip writer — `app/src/repulse/zip.cljs` (new, pure)

```clojure
(defn crc32 [^js bytes] …)            ; table-driven, poly 0xEDB88320 → unsigned int
(defn zip-size [entries] …)           ; entries [{:name str :size n}] → total bytes (K8 guard)
(defn zip-parts
  "entries [{:name str :data Uint8Array}] → JS array of Uint8Array parts:
   local header + data per entry, central directory, end record (K8)."
  [entries] …)
(defn zip-bytes [entries] …)          ; zip-parts concatenated (tests)
```

Layout per entry (all little-endian): local header `50 4B 03 04`, version 20, flags
`0x0800`, method 0, time `0x0000`, date `0x0021`, CRC-32, compressed size = size, name
length, extra 0, name (UTF-8 via `TextEncoder`), data. Central directory entry
`50 4B 01 02`, made-by 20, needed 20, same flags/method/time/date/CRC/sizes, name length,
extra/comment/disk 0, internal/external attrs 0, local-header offset, name. End record
`50 4B 05 06`, disk 0/0, entry counts, central-directory size, its offset, comment 0.

### 3. MIDI encoder — `app/src/repulse/midi.cljs`

Replace `export-midi` with:

```clojure
(def ppqn 480)
(def ticks-per-bar (* 4 ppqn))        ; 1920 — 1 cycle = 4 quarter notes

(defn amp->velocity [amp] …)          ; decision 3; non-number / NaN → 127
(defn tempo-us [cycle-dur] (Math/round (* cycle-dur 250000)))   ; K11

(defn midi-file
  "Pure. → Uint8Array of a format-1 SMF (K9, K16).
   {:cycle-dur cd
    :end-tick  t
    :markers   [{:tick t :text str}]
    :tracks    [{:name str :notes [{:tick t :dur d :note n :velocity v :channel ch}]}]}"
  [spec] …)
```

Conductor track: `00 FF 58 04 04 02 18 08`, `00 FF 51 03 <tempo-us, 3 bytes>`, markers
(`<delta> FF 06 <vlq len> <utf-8>`), `<delta to end-tick> FF 2F 00`. Note tracks:
`00 FF 03 <vlq len> <name>`, note events per K16, end of track. Header
`MThd 00000006 0001 <1 + track count> 01E0`. Notes are filtered through the updated
`specs/midi-export-event?` (`{:tick int ≥ 0 :dur int ≥ 1 :note 0–127 :velocity 1–127
:channel 1–16}`). Keep `write-vlq`, `write-u16`, `write-u32`; keep `hz->midi`.

### 4. MIDI collection — `app/src/repulse/midi_export.cljs` (new, pure)

```clojure
(def gm-drum-notes {:bd 36 :rim 37 :sd 38 :cp 39 :hh 42 :lt 45 :oh 46 :mt 47
                    :cr 49 :ht 50 :rd 51 :tb 54 :cb 56 :sh 70})       ; K13

(defn drum-note [kw drum-map] …)       ; full name, then the part after the last "_"
(defn event-note
  "K12. → {:kind :pitch :note n} | {:kind :drum :note n} | {:kind :skip}
         | {:kind :unmapped :name kw}"
  [value drum-map] …)
(defn rat->tick [[n d]] (Math/round (/ (* n midi/ticks-per-bar) d)))  ; K10
(defn melodic-channel [i] (nth [1 2 3 4 5 6 7 8 9 11 12 13 14 15 16] (mod i 15)))

(defn cycles->midi
  "{:tracks [[kw pat] …] :n n :cycle-dur cd :drum-map m} → {:spec midi-file-spec
   :skipped {kw count}}. Cycles 0…n−1 via core/query-onsets."
  [job] …)

(defn song->midi
  "{:song s :tracks {kw pat} :cycle-dur cd :drum-map m} → same shape; one pass of the
   song via song/song-onsets (mute ignored), markers from (:sections s)."
  [job] …)

(defn skipped-suffix [skipped] …)      ; "" or " — skipped N events with no GM drum note (:a :b); map them with :drum-map"
```

Velocity: resolve each event's value with `audio/resolve-tweens` against
`(audio/tween-state-for pat 0)` at `t = (rat->float whole-start) × cycle-dur`, then
`midi/amp->velocity (:amp value)` (K15). Track order: song `:tracks` filtered by K2;
`(midi-export :track n)` is a one-element vector. Unmapped names are counted per keyword
for the warning (sorted by name in the message).

### 5. `midi-export` built-in — `app/src/repulse/env/builtins/midi.cljs`

Pure `parse-midi-args [args]` → `{:target :song|kw :n n :drum-map m}` or `{:error str}`.
Valid forms: `(midi-export :song)`, `(midi-export :song :drum-map m)`,
`(midi-export :track)`, `(midi-export :track n)`, `(midi-export :track :drum-map m)`,
`(midi-export :track n :drum-map m)`. Unwrap with `leval/deep-unwrap` (the map literal
may carry source wrappers). Then:

- `:song` → `song-transport/current-song-buffer` (new: `{:song @pending-song :tracks
  @pending-tracks}` when this evaluation registered a song, else `@song-buffer`); none →
  the existing `Error: no song — evaluate a buffer that ends with (song …) first`.
  Download `repulse-song-<bars>bars.mid`; return
  `exported song as MIDI — <T> tracks, <bars> bars` + `skipped-suffix`.
- track → look the pattern up in `(merge (:tracks @audio/scheduler-state) (into {}
  @song-transport/pending-tracks))` (same lookup as `export-cycles`); download
  `repulse-<track>.mid`; return `exported <n> cycles of :<track> as MIDI` (unchanged) +
  `skipped-suffix`.

### 6. Offline renderer — `app/src/repulse/offline_render.cljs`

Two optional job keys (K5, K6); absent = today's behaviour, so `(export n)` /
`(export :song)` are untouched:

- `:master? false` — `used-effects` only counts the exported track's `:track-fx` names
  (an active third-party *global* plugin without `getParams` must not fail a stem);
  `build-graph!` sets the master gain to 1.0 and wires `master → destination` with no
  global instances (track-FX plugins are still looked up by name in `(:build plan)`).
- `:stem kw` — `build-graph!` builds a track chain for `kw` only; in `render!`, every job
  event still goes through `fx/notify-plugins!`, but `audio/play-event` (and the sample
  promise collection) only for events whose `:track` is `kw`.

`job-frames` doesn't look at either key, so all stems of a job have identical length.

### 7. Stems orchestration — `app/src/repulse/song_transport.cljs`

- `pending-export` gains `{:kind :stems :n n-or-nil :tail secs :set-output! f}`.
- `stem-tracks [s]` (pure, K2): song `:tracks` that appear in some section's `:tracks`.
- `stem-filenames [track-kws]` (pure, K1).
- `stems-job [pending]` → `{:job base-job :tracks [kw…] :label str}`: with a song and no
  `:n` → `{:kind :song :tracks (into {} (:tracks buf)) :song s :tail tail :cycle-dur cd}`;
  otherwise `{:kind :cycles :tracks (export-tracks nil) :n (or n 4) :cycle-dur cd}`
  (tracks sorted by name).
- `start-stems!`: guard `export-running?`; compute frames with
  `offline-render/job-frames` at `(.-sampleRate (audio/get-ctx))` and check
  `zip/zip-size` (K8) before rendering; then for each track in order:
  `(set-output! "rendering stem 2/3 — :bass…" :idle)`,
  `(offline-render/render! (assoc base-job :stem kw :master? false))`,
  `wav/encode-wav-16` → keep the ArrayBuffer. Chain the renders sequentially
  (promise reduce). Finally `zip/zip-parts` → `download/download-parts! parts
  "application/zip" "repulse-stems.zip"` and
  `exported 3 stems — 24 bars, 50.0 s (offline)` / `exported 3 stems — 4 cycles
  (offline)` (`:success`). A `{:fallback true}` rejection → `Error: stems need the
  offline renderer — <reason>` (`:error`, K4); other errors → `report-failure!`.
- `start-export!` dispatches `:stems` to `start-stems!`.

### 8. `export` built-in — `app/src/repulse/env/builtins/export.cljs`

- `make-builtins` dispatches `:song` → `export-song`, `:stems` → new `export-stems`,
  else `export-cycles`.
- `export-cycles`: valid argument shapes `[]`, `[n]`, `[kw]`, `[kw n]`. Keep the existing
  order of checks (`:realtime` first), then any other shape →
  `Error: usage — (export [cycles]), (export :track [cycles]), (export :stems …) or (export :song …)`.
  `(export :drums 8)` → `{:kind :cycles :n 8 :track :drums}`,
  `rendering :drums — 8 cycles offline…`, file `repulse-drums-8cycles.wav`. `n` keeps the
  `(int n)` truncation and the `≥ 1` check.
- `export-stems` + pure `parse-stems-opts [opts]` (K3): `[]`, `[n]`, `[:tail s]`;
  `:realtime` anywhere → `realtime-error`; bad `:tail` → `tail-error`; `n < 1` →
  `Error: export needs at least 1 cycle`; `:tail` without a song → `Error: no song —
  evaluate a buffer that ends with (song …) first`; other shapes →
  `Error: (export :stems) takes a cycle count or :tail secs`; no tracks →
  `Error: no active tracks to export`; running → `running-error`. Return value:
  `rendering stems — 3 tracks, 24 bars, 50.0 s offline…` (song, via `song-summary`) or
  `rendering stems — 3 tracks, 4 cycles offline…`.

### 9. Test handle — `app/src/repulse/app.cljs`

Add `__REPULSE_APP__.renderDryMix (fn [tail] …)`: renders the last song buffer (all its
stem tracks, K2) in one pass with `{:kind :song … :tail tail :master? false}` and
resolves with `#js {:sampleRate :frames :left (Array) :right (Array)}`. Used only by the
Σ-stems test.

### 10. Tests

- **`app/src/repulse/zip_test.cljs`** (new): CRC and byte vectors below.
- **`app/src/repulse/midi_export_test.cljs`** (new): `midi/amp->velocity`,
  `midi/tempo-us`, VLQ via `midi-file` output, `event-note`, `drum-note`, `rat->tick`,
  `cycles->midi`, `song->midi`, full-file byte vectors below. Build patterns by
  evaluating Lisp (copy the `make-env` / `track` pattern from `song_transport_test.cljs`).
- **`app/src/repulse/song_transport_test.cljs`**: export grammar for `(export :track n)`,
  the usage error and `(export :stems …)`; `stem-tracks`, `stem-filenames`; midi-export
  argument grammar (`parse-midi-args`) and messages.
- **`app/src/repulse/offline_render_test.cljs`**: `used-effects` with `:master? false`.
- **`app/src/repulse/wav_test.cljs`**: unchanged (encoder untouched).
- Register `repulse.zip-test` and `repulse.midi-export-test` in
  `packages/core/src/repulse/test_runner.cljs` (both lists).
- **`e2e/stems-midi.spec.ts`** (new), modelled on `e2e/export-parity.spec.ts` (same
  `load`, `setText`, `readDownload`-style helpers, GitHub routes aborted), plus
  `e2e/helpers/zip.ts` (new): `parseZipStore(buf)` → `[{name, data: Buffer}]`, verifying
  signatures, method 0 and CRC-32 of each entry.

### 11. Docs and AI docs (CLAUDE.md Rule 2)

- `docs/USAGE.md`: export table gains `(export :track n)` and `(export :stems …)` rows plus
  a "Stems" paragraph (identical length, per-track FX included, global/master FX *not*
  included — K5 / decision 2 —, sidechain still ducks, no real-time fallback, zip layout);
  "MIDI file export" rewritten: format 1, `(midi-export :song)`, velocity from `amp`, GM
  drum table (K13), `:drum-map`, section markers, the skipped-events warning, and a **DAW
  import walkthrough** (drag `repulse-stems.zip` contents into Ableton/Logic/Reaper at bar
  1 with the project tempo set to the REPuLse BPM; open the `.mid` — tracks, tempo and
  markers come in automatically; drum track → assign a GM drum kit).
- `README.md` line ~56: "WAV, stem and MIDI export".
- **No new built-in names** — only new argument forms of `export` and `midi-export`
  (`:stems`, `:drum-map` and `:song` are keyword arguments). Rule 3 steps 1–2 (grammar,
  `gen:grammar`) do **not** apply. Do update:
  `app/src/repulse/lisp-lang/hover.js` (`export` and `midi-export` signatures and
  descriptions; also fix the stale "records … in real time" wording),
  `app/src/repulse/lisp-lang/completions.js` (`export`, `midi-export` details),
  `app/src/repulse/content/builtin_meta.edn` (`:export` examples `"(export :stems)"`,
  `"(export :kick 8)"`; `:midi-export` examples `"(midi-export :song)"`,
  `"(midi-export :drums 4 :drum-map {:perc 60})"`), then **`npm run gen:ai-docs`** and
  commit the regenerated `docs/ai/builtins.json` (`npm run check:ai-docs` must be clean).
- `docs/ARCHITECTURE.md` file list: add `download.cljs`, `zip.cljs`, `midi_export.cljs`.
- `ROADMAP.md`: mark SK1 delivered (Rule 2.3).

### 12. PR test protocol (CLAUDE.md Rule 5)

See "## Test protocol" below for the items the protocol must contain.

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/download.cljs` | **new** — `download-parts!` |
| `app/src/repulse/zip.cljs` | **new** — `crc32`, `zip-size`, `zip-parts`, `zip-bytes` (K8) |
| `app/src/repulse/midi_export.cljs` | **new** — `gm-drum-notes`, `drum-note`, `event-note`, `rat->tick`, `melodic-channel`, `cycles->midi`, `song->midi`, `skipped-suffix` |
| `app/src/repulse/midi.cljs` | `export-midi` → `midi-file` (format 1), `ppqn`, `ticks-per-bar`, `amp->velocity`, `tempo-us` |
| `app/src/repulse/specs.cljs` | `midi-export-event?` → tick-based shape |
| `app/src/repulse/env/builtins/midi.cljs` | `midi-export`: `parse-midi-args`, `:song`, `:drum-map`, pending-track lookup, messages; uses `download-parts!` |
| `app/src/repulse/offline_render.cljs` | `:master? false`, `:stem kw` job keys (K5, K6) |
| `app/src/repulse/song_transport.cljs` | `:stems` pending kind, `stem-tracks`, `stem-filenames`, `stems-job`, `start-stems!`, `current-song-buffer` |
| `app/src/repulse/env/builtins/export.cljs` | `(export :track n)`, usage error, `export-stems`, `parse-stems-opts` |
| `app/src/repulse/wav.cljs` | `download-wav!` delegates to `download-parts!` |
| `app/src/repulse/app.cljs` | `__REPULSE_APP__.renderDryMix` |
| `app/src/repulse/zip_test.cljs` | **new** |
| `app/src/repulse/midi_export_test.cljs` | **new** |
| `app/src/repulse/song_transport_test.cljs` | export / stems / midi-export grammar tests |
| `app/src/repulse/offline_render_test.cljs` | `used-effects` with `:master? false` |
| `packages/core/src/repulse/test_runner.cljs` | register the two new test namespaces |
| `e2e/stems-midi.spec.ts` | **new** — Playwright tests (Definition of done) |
| `e2e/helpers/zip.ts` | **new** — `parseZipStore` |
| `app/src/repulse/lisp-lang/hover.js` | `export`, `midi-export` entries |
| `app/src/repulse/lisp-lang/completions.js` | `export`, `midi-export` details |
| `app/src/repulse/content/builtin_meta.edn` | `:export`, `:midi-export` examples |
| `docs/ai/builtins.json` | regenerated by `npm run gen:ai-docs` |
| `docs/USAGE.md` | export section, MIDI file export section, DAW walkthrough |
| `docs/ARCHITECTURE.md` | new namespaces in the file list |
| `README.md` | feature line (~56) |
| `ROADMAP.md` | mark SK1 delivered (Rule 2) |

`app/src/repulse/test_api.cljs`, `app/public/test-harness.html`, `packages/audio/`, the
plugins and the real-time audio graph are **not** changed.

## Definition of done

Reference vectors. Hex bytes; "song S" is:

```lisp
(bpm 120)
(track :kick (seq :bd :sd :hh :oh))
(track :bass (amp 0.5 (seq :c2 :_ :g2 :_)))
(song (section :intro 2 [:kick]) (section :verse 4 :all) (section :outro 2 [:bass]))
```

(8 bars, kick in bars 0–5, bass in bars 2–7; `:c2` = 36, `:g2` = 43.) `sr` = the
device sample rate reported in the WAV header (44 100 or 48 000 in practice).

### MIDI (unit tests, `midi_export_test.cljs`)

- [ ] **Velocity (decision 3).** `amp->velocity`: 1.0 → **127**, 0.5 → **64**, 0.25 →
  **32**, 0.1 → **13**, 0.0 → **1**, −1 → **1**, 2.0 → **127**, `nil` / `:x` / `NaN` →
  **127**. `(midi-export :t 1)` of `(track :t (amp 0.5 (seq :c4)))` has note-on
  `90 3C 40`.
- [ ] **Tempo (K11).** `tempo-us`: 120 BPM (cycle-dur 2.0) → **500 000** → meta
  `FF 51 03 07 A1 20`; 140 BPM → **428 571** → `FF 51 03 06 8A 1B`; 90 BPM → **666 667**
  → `FF 51 03 0A 2C 2B` (old code: 666 666); 60 BPM → **1 000 000** →
  `FF 51 03 0F 42 40`.
- [ ] **VLQ.** Deltas 0 → `00`, 127 → `7F`, 128 → `81 00`, 480 → `83 60`, 1920 → `8F 00`,
  3840 → `9E 00`, 7680 → `BC 00`, 0x0FFFFFFF → `FF FF FF 7F`.
- [ ] **GM drums (K13), channel 10.** `event-note` with the default map: `:bd` **36**,
  `:rim` **37**, `:sd` **38**, `:cp` **39**, `:hh` **42**, `:lt` **45**, `:oh` **46**,
  `:mt` **47**, `:cr` **49**, `:ht` **50**, `:rd` **51**, `:tb` **54**, `:cb` **56**,
  `:sh` **70**, all `:kind :drum`; `{:note :sd :amp 0.5}` → 38; `{:bank :hh :n 2}` → 42;
  `:RolandTR808_oh` → 46; `:tabla` → `{:kind :unmapped :name :tabla}`;
  `:tabla` with `:drum-map {:tabla 60}` → 60; `{:bd 35}` overrides `:bd` → 35. Drum
  note-on status byte is **`99`**, note-off **`89`**. Pitch: `:c4` → 60, `440` → 69,
  `{:note :a4}` → 69, `{:freq 261.63}` → 60; `:_` and `{:note :_}` → `:skip`.
- [ ] **Song S header and conductor.** `(midi-export :song)` writes exactly **449
  bytes**: header `4D 54 68 64 00 00 00 06 00 01 00 03 01 E0` (format 1, 3 tracks,
  480 ppqn); conductor track (57 bytes)
  `4D 54 72 6B 00 00 00 31 00 FF 58 04 04 02 18 08 00 FF 51 03 07 A1 20 00 FF 06 05 69 6E 74 72 6F 9E 00 FF 06 05 76 65 72 73 65 BC 00 FF 06 05 6F 75 74 72 6F 9E 00 FF 2F 00`
  — markers `intro` / `verse` / `outro` at ticks **0 / 3840 / 11520**, end of track at
  **15360**.
- [ ] **Song S note tracks.** Track 1 (237 bytes, length field `00 00 00 E5`) starts
  `00 FF 03 04 6B 69 63 6B 00 99 24 7F 83 60 89 24 00 00 99 26 7F 83 60 89 26 00 00 99 2A 7F 83 60 89 2A 00 00 99 2E 7F 83 60 89 2E 00`
  (bar 0: bd/sd/hh/oh = 36/38/42/46, velocity 127, 480 ticks each), has 24 note-ons
  (bars 0–5) and ends `89 2E 00 9E 00 FF 2F 00` (last off at 11520, EOT at 15360).
  Track 2 (141 bytes) is exactly
  `4D 54 72 6B 00 00 00 85 00 FF 03 04 62 61 73 73 9E 00 91 24 40 83 60 81 24 00 83 60 91 2B 40 83 60 81 2B 00`
  followed by the same 18-byte bar pattern (`83 60 91 24 40 83 60 81 24 00 83 60 91 2B 40 83 60 81 2B 00`)
  five more times and `83 60 FF 2F 00` — channel 2 (K14), velocity 64, first note at
  tick 3840.
- [ ] **Time-bug regression.** `(track :lead (seq :c4 :e4)) (midi-export :lead 2)`
  (same buffer — also proves the pending-track lookup) writes header
  `4D 54 68 64 00 00 00 06 00 01 00 02 01 E0`, conductor
  `4D 54 72 6B 00 00 00 14 00 FF 58 04 04 02 18 08 00 FF 51 03 07 A1 20 9E 00 FF 2F 00`
  and note track
  `4D 54 72 6B 00 00 00 30 00 FF 03 04 6C 65 61 64 00 90 3C 7F 87 40 80 3C 00 00 90 40 7F 87 40 80 40 00 00 90 3C 7F 87 40 80 3C 00 00 90 40 7F 87 40 80 40 00 00 FF 2F 00`
  — note-ons at ticks **0, 960, 1920, 2880** (old code: 0, 960, 0, 960).
- [ ] **Chords / overlap.** `(track :ch (chord :major :c4)) (midi-export :ch 1)`: the note
  track's events are exactly
  `00 90 3C 7F 00 90 40 7F 00 90 43 7F 8F 00 80 3C 00 00 80 40 00 00 80 43 00` (no
  negative deltas).
- [ ] **Exact ticks (K10).** `(track :t (fast 24 (seq :c4)))` `(midi-export :t 3)` at
  120 BPM: the 72 note-ons are at ticks `80 × k`, k = 0…71 (old code puts k = 49 at 3919).
- [ ] **Unmapped warning.** `(track :p (seq :bd :tabla :tabla :perc)) (midi-export :p 2)`
  returns `exported 2 cycles of :p as MIDI — skipped 6 events with no GM drum note
  (:perc :tabla); map them with :drum-map`; with `:drum-map {:tabla 60 :perc 61}` it
  returns `exported 2 cycles of :p as MIDI` and the file has 8 note-ons.

### `export` grammar (unit tests, `song_transport_test.cljs`)

- [ ] `(track :a (seq :bd)) (export :a 8)` → `rendering :a — 8 cycles offline…`, pending
  `{:kind :cycles :n 8 :track :a}`; `(export :a 0)` → `Error: export needs at least 1
  cycle`; `(export 8 :a)` and `(export :a 8 2)` → `Error: usage — (export [cycles]),
  (export :track [cycles]), (export :stems …) or (export :song …)`; existing
  `export-grammar-test` cases unchanged.
- [ ] `(export :stems)` with song S's tracks and song → `rendering stems — 2 tracks, 8
  bars, 18.0 s offline…`, pending `{:kind :stems :n nil :tail 2}`; without a song and two
  tracks → `rendering stems — 2 tracks, 4 cycles offline…`; `(export :stems 8)` →
  `… 8 cycles offline…`; `(export :stems :tail 1)` without a song → `Error: no song —
  evaluate a buffer that ends with (song …) first`; `(export :stems :realtime)` →
  `Error: :realtime only applies to (export :song)`; `(export :stems :foo)` →
  `Error: (export :stems) takes a cycle count or :tail secs`; `(export :stems :tail 40)` →
  `Error: :tail must be a number of seconds between 0 and 30`.
- [ ] `stem-tracks`: for `(song (section :a 2 [:k]) (section :b 2 [:k :bass]))` with
  tracks `:k :bass :unused` → `[:k :bass]`. `stem-filenames [:kick :bass :drums/kick
  :a?b]` → `["repulse-kick.wav" "repulse-bass.wav" "repulse-kick-2.wav"
  "repulse-a_b.wav"]`.
- [ ] `midi-export` grammar: `(midi-export)` → `Error: usage — (midi-export :track
  [cycles]) or (midi-export :song), optionally followed by :drum-map {…}`;
  `(midi-export :a 0)` → `Error: midi-export needs at least 1 cycle`;
  `(midi-export :a :drum-map {:x 200})` → `Error: :drum-map must map keywords to MIDI
  notes 0–127, e.g. {:perc 60}`; `(midi-export :song)` without a song → `Error: no song —
  evaluate a buffer that ends with (song …) first`; `(midi-export :zz)` →
  `Error: no track :zz`.

### Zip (unit tests, `zip_test.cljs`)

- [ ] `crc32`: `"123456789"` → **0xCBF43926**, `""` → **0**, `"hi"` → **0xD8932AAC**.
- [ ] `zip-bytes [{:name "a.txt" :data "hi" as UTF-8}]` is exactly these **110 bytes**:
  `50 4B 03 04 14 00 00 08 00 00 00 00 21 00 AC 2A 93 D8 02 00 00 00 02 00 00 00 05 00 00 00 61 2E 74 78 74 68 69 50 4B 01 02 14 00 14 00 00 08 00 00 00 00 21 00 AC 2A 93 D8 02 00 00 00 02 00 00 00 05 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 61 2E 74 78 74 50 4B 05 06 00 00 00 00 01 00 01 00 33 00 00 00 25 00 00 00 00 00`
  (verified with Python `zipfile` and `unzip -t`). Two 10-byte entries named
  `repulse-kick.wav` / `repulse-bass.wav` → **258 bytes**. `zip-size` = Σ(30 + name + size)
  + Σ(46 + name) + 22 and the K8 guard trips above `0xFFFFFFFF`.

### Stems (Playwright, `e2e/stems-midi.spec.ts`, production app)

- [ ] **Identical length.** Song S plus `(export :stems)` downloads `repulse-stems.zip`
  with exactly two entries, `repulse-kick.wav` then `repulse-bass.wav`, each a valid
  16-bit stereo WAV at `sr` with exactly `round((8 × 2.0 + 2) × sr)` frames: **793 800**
  at 44.1 kHz, **864 000** at 48 kHz. The output ends with `exported 2 stems — 8 bars,
  18.0 s (offline)`. Unit test (`job-frames`): 140 BPM, 8 bars, tail 2 → **693 000** /
  **754 286**; `(export :stems 4)` at 120 BPM → **352 800** / **384 000**.
- [ ] **Isolation by section.** In song S's stems, the kick stem's 1 s-window RMS is
  ≥ 1e-3 in every window of bars 0–5 (0–12 s) and ≤ 1e-4 in [12.5 s, 16 s]; the bass stem
  is ≤ 1e-4 in [0, 3.9 s] and ≥ 1e-3 in [4, 16 s].
- [ ] **Σ stems = pre-master mix (K5).** Song D:
  ```lisp
  (bpm 120)
  (track :kick (->> (seq :bd :_ :bd :_) (amp 0.4)))
  (track :bass (->> (seq :c2 :_ :g2 :_) (synth :saw) (amp 0.2)))
  (track :keys (->> (seq :c4 :e4) (synth :epiano) (amp 0.2)))
  (track :lead (->> (seq :a3 :_ :e4 :_) (synth :saw) (amp 0.2) (fx :filter :freq 1200)))
  (song (section :a 2 [:kick :bass]) (section :b 2 :all))
  ```
  (deterministic voices only — no noise drums, no reverb). `(export :stems :tail 1)`
  gives 4 stems; their sample-wise sum differs from
  `__REPULSE_APP__.renderDryMix(1)` by at most **2.5e-4** (4 × 16-bit truncation + float
  error) at every sample of both channels, and the sum's RMS is ≥ 1e-2.
- [ ] **Per-track FX in, master FX out.** `(bpm 120) (track :hat (seq :hh :_ :_ :_))
  (master (fx :delay :wet 0.5 :time 0.25)) (song (section :a 1 [:hat])) (export :stems
  :tail 0)`: in the `:hat` stem the 10 ms-window RMS peak in [0.24, 0.30] s is ≤ **0.02 ×**
  the peak in [0, 0.05] s (no echo), while `(export :song :tail 0)` of the same buffer
  gives ≥ **0.25 ×** (EXP1 numbers). The track-FX case is covered by `:lead` above
  (filter) and by:
- [ ] **Sidechain still ducks (K6).** `(bpm 120) (track :k (seq :bd :_ :bd :_)) (track
  :pad (->> (seq :c3) (synth :saw) (decay 4) (amp 0.3) (fx :sidechain :trigger :bd
  :amount 0.8 :release 0.1))) (song (section :a 1 [:k :pad])) (export :stems :tail 0)`:
  in the `:pad` stem, RMS of [1.000, 1.020] s ≤ **0.5 ×** RMS of [0.92, 0.98] s.
- [ ] **No real-time fallback (K4).** With `**/worklet.js` and `**/repulse_audio_bg.wasm`
  aborted after the live backend is up (as in `export-parity.spec.ts`), song S plus
  `(export :stems)` prints `Error: stems need the offline renderer — WASM worklet failed
  to start offline: …` and no download happens within 5 s.
- [ ] **`(export :track n)`.** `(bpm 240) (track :drums (seq :bd :hh)) (export :drums 8)`
  downloads `repulse-drums-8cycles.wav` with `round(8 × 1.0 × sr)` frames (**352 800** /
  **384 000**) and prints `exported :drums — 8 cycles (offline)`.
- [ ] **MIDI download.** Song S plus `(midi-export :song)` in the production app downloads
  `repulse-song-8bars.mid`, 449 bytes, byte-identical to the unit-test vector; the
  output is `exported song as MIDI — 2 tracks, 8 bars`.

### Regressions, docs, verification

- [ ] `e2e/export-parity.spec.ts`, `e2e/song.spec.ts`, `e2e/audio-render.spec.ts` and the
  rest of the suite pass unmodified; `(export n)`, `(export :song)` and
  `(export :song :realtime)` behave as before (the new job keys default to today's graph).
- [ ] `docs/USAGE.md` documents `(export :track n)`, `(export :stems …)` (with the
  master-FX limitation), `(midi-export :song)`, `:drum-map`, the GM drum table, velocity
  from `amp`, section markers, and has a DAW import walkthrough; `npm run check:ai-docs`
  is clean.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass (CLAUDE.md pre-push
  verification).
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5)

## Test protocol

The implementation PR's `## Test protocol` (CLAUDE.md Rule 5) must have one checkbox for
each of the following, with real expected values (check snippets with the TEST1 harness
where they are pattern questions; take message texts from the code):

**New argument forms** (no new built-in names):
`(export :track n)`, `(export :stems)` (song and no-song), `(export :stems n)`,
`(export :stems :tail s)`, `(midi-export :song)`, `(midi-export :song :drum-map {…})`,
`(midi-export :track n :drum-map {…})`; plus the changed `(midi-export :track n)`
(absolute times, velocity, drums on channel 10, format 1) — open the `.mid` in a DAW or
an online MIDI viewer and state the notes/bars seen.

**New messages** (each one triggered once):
- `rendering stems — <T> tracks, <bars> bars, <secs> s offline…` /
  `rendering stems — <T> tracks, <n> cycles offline…`
- `rendering stem <i>/<T> — :<track>…`
- `exported <T> stems — <bars> bars, <secs> s (offline)` / `exported <T> stems — <n> cycles (offline)`
- `Error: stems need the offline renderer — <reason>`
- `Error: stems too large for one zip (over 4 GB) — export fewer bars or tracks`
  (may be listed under "Known issues" as not practically triggerable by hand; it is
  covered by the `zip-size` unit test)
- `Error: (export :stems) takes a cycle count or :tail secs`
- `Error: usage — (export [cycles]), (export :track [cycles]), (export :stems …) or (export :song …)`
- `exported song as MIDI — <T> tracks, <bars> bars`
- ` — skipped <N> events with no GM drum note (<kws>); map them with :drum-map`
- `Error: usage — (midi-export :track [cycles]) or (midi-export :song), optionally followed by :drum-map {…}`
- `Error: midi-export needs at least 1 cycle`
- `Error: :drum-map must map keywords to MIDI notes 0–127, e.g. {:perc 60}`

**Changed behaviour:** `(export 8 :drums)` is now a usage error (was: all tracks);
`(midi-export :track)` in the same buffer as its `(track …)` now works (was:
`Error: no track`); `.mid` files are format 1 with velocity from `amp` (were format 0,
velocity 100).

**Regressions (at least):** `(export 4)`, `(export :song)` with a `(master (fx :reverb
0.3))`, `(export :song :realtime)`, live `(midi-out 1 …)` velocity unchanged.

**Listen check:** import the stems zip of a demo song into a DAW (or play the WAVs side by
side) — every stem starts at bar 1 and lines up with the others.

## What NOT to do

- No live per-track audio outputs — that is LIVE2.
- No FLAC/MP3 encoding and no zip compression (STORE only, decision 1) — backlog.
- No loop-region export / timeline UI (backlog).
- No MIDI import — capture is SK3–SK5.
- No master/global FX in stems and no "stems with master" option (decision 2); don't route
  WASM voices through per-track FX (EXP1 E1 follow-up).
- Don't change the real-time audio graph, the TEST1 `"offline-js"` harness, live
  `midi-out` velocity, or plugin DSP; don't copy scheduler code into the MIDI collector —
  tweens go through `audio/tween-state-for` / `audio/resolve-tweens`.
