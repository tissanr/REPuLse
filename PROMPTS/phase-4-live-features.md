# REPuLse — Live Performance Features (Phase 4)

## Context

REPuLse has a working REPL, pattern engine, sample library, and WASM synthesis in an
AudioWorklet. This phase adds the features that make it usable for real live performance:
multiple simultaneous patterns, a visual timeline, MIDI clock, and session persistence.

---

## Goal for this session

By the end of this session:

1. Multiple named patterns can run simultaneously on independent **tracks**
2. A track can be muted, soloed, or removed without stopping others
3. The BPM can be tapped or synced to MIDI clock
4. The session (all active tracks + BPM) can be saved to a URL and restored
5. A visual timeline shows active tracks and their cycle positions

---

## Terminology note

REPuLse calls independent pattern streams **tracks** — the term DAW users know from
Ableton, Logic, and Pro Tools. Each track holds one looping pattern; it can be muted,
soloed, or replaced without affecting other tracks.

> **For live coding context:** a REPuLse track is the same concept as an **orbit** in
> Strudel / TidalCycles — an independent pattern output stream. This equivalence should
> be mentioned in `docs/USAGE.md` so users coming from those tools feel at home.

---

## Feature 1: Multiple simultaneous patterns

### Language additions

```lisp
; Named tracks — evaluated expression runs on that track
(play :kick  (seq :bd :_ :bd :_))
(play :snare (seq :_ :sd :_ :sd))
(play :hats  (fast 2 (seq :hh :_)))

; Mute / unmute a track
(mute :kick)
(unmute :kick)

; Solo a track (mute all others)
(solo :hats)

; Remove a track
(clear :kick)

; Remove all tracks and stop playback
(clear)

; List active track names
(tracks)   ; => (:kick :snare :hats)
```

### Scheduler changes

Replace the single `pattern` in scheduler state with a map `{track-name -> pattern}`.
Each track is queried and scheduled independently on every cycle tick.

```clojure
;; scheduler-state
{:tracks   {:kick <Pattern> :snare <Pattern> :hats <Pattern>}
 :muted    #{:kick}
 :cycle    42
 :cycle-dur 2.0 ...}
```

### UI changes

The footer area expands to show the active track panel:

```
┌──────────────────────────────────────────┐
│  REPuLse               ▶ play  ●         │
├──────────────────────────────────────────┤
│  (play :kick (seq :bd :_ :bd :_))        │  ← editor
│                                          │
├──────────────────────────────────────────┤
│  :kick  ████░░██░░  ● ●                  │  ← timeline
│  :snare ░░██░░░░██  ●                    │
│  :hats  ████████    ● ● ● ●              │
├──────────────────────────────────────────┤
│  => playing 3 tracks                     │  ← output
└──────────────────────────────────────────┘
```

Each track row shows: name, a mini piano-roll of the current cycle, and beat indicators.
Click a track name to mute/unmute.

---

## Feature 2: BPM tap and MIDI clock

### Tap tempo

A **tap** button in the header. Click 4 times to set BPM from the average interval.

```clojure
;; In CLJS — track last N tap times, compute average interval
(defn tap! []
  (let [now (js/Date.now)]
    (swap! tap-times conj now)
    (when (>= (count @tap-times) 2)
      (let [intervals (map - (rest @tap-times) @tap-times)
            avg       (/ (reduce + intervals) (count intervals))
            bpm       (/ 60000.0 avg)]
        (audio/set-bpm! bpm)))))
```

### MIDI clock input

If a MIDI device is connected, sync to its clock signal (24 pulses per quarter note):

```clojure
(defn init-midi! []
  (when (.-requestMIDIAccess js/navigator)
    (-> (.requestMIDIAccess js/navigator)
        (.then (fn [access]
                 (doseq [input (.. access -inputs vals)]
                   (set! (.-onmidimessage input) handle-midi!)))))))

(defn handle-midi! [event]
  (let [data (.-data event)]
    (case (aget data 0)
      0xF8 (handle-clock-pulse!)   ; MIDI clock tick
      0xFA (handle-clock-start!)   ; Start
      0xFC (handle-clock-stop!)))) ; Stop
```

Add `(midi-sync true/false)` to the Lisp environment.

---

## Feature 3: Session persistence

### URL-based state

Encode the current editor buffer (and optionally all track definitions) as a Base64
URL fragment. A share button copies the URL.

```
https://repulse.example.com/#v1:eyJ0cmFja3MiOnsia2lj...
```

Format:
```json
{
  "v": 1,
  "bpm": 120,
  "tracks": {
    "kick":  "(seq :bd :_ :bd :_)",
    "snare": "(seq :_ :sd :_ :sd)"
  },
  "editor": "(play :kick (seq :bd :_ :bd :_))"
}
```

On load, if the URL has a fragment, decode and restore the session.

### Local storage

Auto-save the current session to `localStorage` every 5 seconds. Restore on reload
if no URL fragment is present.

---

## Feature 4: Visual timeline

A mini piano-roll / step-sequencer view below the editor showing all active tracks.

Each track shows:
- The events in the current cycle as coloured blocks
- The current playhead position
- Beat numbers (1–4)

The timeline is read-only — editing is done in the REPL. It updates every cycle.

```clojure
(defn render-track-timeline [track-name pattern cycle]
  ;; Query pattern for current cycle
  (let [sp  {:start [cycle 1] :end [(inc cycle) 1]}
        evs (core/query pattern sp)]
    ;; Draw SVG bars proportional to event position and duration
    ))

---

## Language additions summary

| Expression | Description |
|---|---|
| `(play :name pattern)` | Start or replace a named track |
| `(mute :name)` | Silence a track without removing it |
| `(unmute :name)` | Re-enable a muted track |
| `(solo :name)` | Play only this track (mute all others) |
| `(clear :name)` | Remove a track |
| `(clear)` | Remove all tracks and stop |
| `(tracks)` | Return list of active track names |
| `(tap)` | Register a BPM tap |
| `(midi-sync true)` | Enable MIDI clock sync |

---

## USAGE.md additions

Add a new section **"Tracks (multi-pattern playback)"** to `docs/USAGE.md` that:

1. Introduces the `play` / `mute` / `solo` / `clear` / `tracks` functions with examples
2. Includes this equivalence note:

   > **Strudel / TidalCycles users:** a REPuLse track is the same concept as an
   > **orbit** in Strudel or a **dirt connection** (`d1`, `d2`, …) in TidalCycles —
   > an independent output stream that loops its own pattern on every cycle.
   > The difference is naming: in REPuLse you give tracks meaningful keyword names
   > (`:kick`, `:bass`, `:lead`) rather than numbers.

3. Shows the timeline ASCII art so users know what to expect

---

## Architecture notes

- `scheduler-state` becomes the source of truth for all tracks — the timeline reads from it
- `play` and `mute` mutate `scheduler-state` atoms, just like `stop` does now
- Session encode/decode is pure (no side effects) — easy to unit test
- Timeline rendering uses `requestAnimationFrame` at ~30fps, not on every audio tick

---

## Definition of Done

- [ ] `(play :kick (seq :bd :_ :bd :_))` adds a track; all other tracks keep playing
- [ ] `(mute :kick)` silences the kick without stopping the session
- [ ] `(unmute :kick)` restores the kick at the next cycle boundary
- [ ] `(solo :hats)` mutes all tracks except `:hats`
- [ ] `(clear)` stops everything cleanly
- [ ] `(tracks)` returns the list of currently active track names
- [ ] BPM tap sets tempo within ±2 BPM accuracy over 4 taps
- [ ] Share button copies a URL that restores the full session when opened in a new tab
- [ ] Timeline shows current cycle events and scrolling playhead
- [ ] `localStorage` restore works after browser refresh

---

## What NOT to do in this phase

- No sample recording or custom sample upload
- No collaborative / multi-user session
- No mobile-specific layout (keep it desktop-first)
- No export to audio file
