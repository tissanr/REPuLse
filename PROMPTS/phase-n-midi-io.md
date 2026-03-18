# Phase N — MIDI & External I/O

## Goal

Five additions that connect REPuLse to the outside world — hardware synths, DAWs,
controllers, and sample libraries:

1. **MIDI controller input** — map CC/Note messages from hardware controllers to pattern
   parameters
2. **MIDI note output** — route pattern events as MIDI Note On/Off to external instruments
3. **MIDI clock output** — send 24ppqn clock + Start/Stop/Continue at the current BPM
4. **MIDI file export** — export pattern as a downloadable `.mid` file for DAW import
5. **Freesound API** — search and load samples directly from freesound.org

```lisp
;; Before — REPuLse is a closed system, no external I/O:
(play :bass (scale :minor :c2 (seq 0 3 5 7)))

;; After — drive a hardware synth via MIDI:
(play :bass
  (midi-out :ch 1 (scale :minor :c2 (seq 0 3 5 7))))

;; After — map a MIDI controller knob to filter cutoff:
(midi-map :cc 1 :filter)

;; After — broadcast tempo to DAW/hardware:
(midi-clock-out! true)

;; After — export pattern as MIDI file:
(midi-export :bass 8)   ; export 8 cycles of :bass track as .mid

;; After — search Freesound and load a kick sample:
(freesound! "kick 808")
```

---

## Background

### Web MIDI API

The Web MIDI API (`navigator.requestMIDIAccess()`) is available in Chrome and Edge.
Firefox and Safari do not support it. REPuLse already uses it for `midi-sync!` (Phase 4)
which listens for incoming MIDI clock messages.

Current MIDI infrastructure in `app/src/repulse/app.cljs`:

```clojure
;; Phase 4 — midi-sync! registers a clock listener on the first MIDI input
"midi-sync!"
(fn [on?]
  (let [on (leval/unwrap on?)]
    (if on
      (-> (js/navigator.requestMIDIAccess)
          (.then (fn [access]
                   (let [inputs (.. access -inputs)]
                     ;; ... registers listener for timing clock messages
                     ))))
      ;; ... removes listener
      )))
```

This provides the foundation — `requestMIDIAccess` is already called, inputs are
enumerated. Phase N extends this to:
- Enumerate outputs (for note/clock out)
- Parse Note On/Off and CC messages (for controller input)
- Schedule MIDI messages aligned with audio time

### Scheduler timing

The scheduler in `audio.cljs` works in AudioContext time (seconds from context creation).
MIDI messages must be sent at specific AudioContext times. The Web MIDI API uses
`performance.now()` timestamps. Conversion:

```javascript
midiTimestamp = performance.now() + (audioTime - audioContext.currentTime) * 1000
```

### Existing sample infrastructure

`samples!` in `app.cljs` loads samples from GitHub repos via the Strudel CDN. Freesound
uses a different API (REST + OAuth) but the same pattern: fetch audio → decode →
store in the sample registry.

---

## Design

### 1. MIDI access module

Create `app/src/repulse/midi.cljs` — a dedicated namespace for all MIDI I/O. This
module manages:
- A single `MIDIAccess` object (shared across input/output)
- Input listeners (controller mapping, note input)
- Output sending (note out, clock out)
- Device enumeration

### 2. Controller mapping

`(midi-map :cc N :param)` registers a listener on all MIDI inputs that maps CC number N
to a named parameter. The mapping is stored in an atom:

```clojure
;; CC mappings: {cc-number {:target :param-name :min 0.0 :max 1.0}}
(defonce cc-mappings (atom {}))
```

When a CC message arrives, the mapped parameter is updated in real time. Initially
supported targets: `:filter` (master lowpass cutoff), `:amp` (master gain), `:bpm`
(tempo). Custom targets can be added later.

### 3. MIDI note output

`(midi-out :ch N pat)` is a pattern transformer (like `amp`, `pan`). It adds
`:midi-out {:channel N}` to each event's value map. The scheduler checks for this key
after calling `play-event` and additionally sends a MIDI Note On at event start and
Note Off at event end.

MIDI note output requires converting Hz frequencies to MIDI note numbers:

```clojure
(defn hz->midi [hz]
  (Math/round (+ 69 (* 12 (/ (Math/log (/ hz 440.0)) (Math/log 2))))))
```

### 4. MIDI clock output

24 pulses per quarter note (ppqn). At a given BPM:
- Pulse interval = `60 / BPM / 24` seconds
- Start message (0xFA) when playback begins
- Stop message (0xFC) when playback stops
- Continue (0xFB) when resuming

Clock output runs on a `setInterval` timer synced to the audio clock.

### 5. MIDI file export

MIDI files are a simple binary format (Standard MIDI File, Type 0). The implementation
needs:
- A header chunk (MThd: format, tracks, division)
- A single track chunk (MTrk: delta-time + Note On/Off events)
- Variable-length quantity encoding for delta times

This is ~100 lines of pure JavaScript/ClojureScript — no external library needed.
Resolution: 480 ticks per quarter note (standard).

### 6. Freesound API integration

Freesound.org offers a REST API for searching and downloading samples. Requires:
- An API key (users provide their own via `(freesound-key! "...")`)
- Search: `GET https://freesound.org/apiv2/search/text/?query=...&token=...`
- Preview download: each result has a `previews.preview-hq-mp3` URL (no auth needed)

The flow: search → display results → user picks one → download preview → decode →
register in sample bank → available as `:freesound-N` keywords.

---

## Implementation

### 1. New file: `app/src/repulse/midi.cljs`

```clojure
(ns repulse.midi)

;;; ── State ─────────────────────────────────────────────────────────

(defonce midi-access (atom nil))
(defonce cc-mappings (atom {}))         ; {cc-num {:target kw :min f :max f}}
(defonce clock-interval-id (atom nil))
(defonce midi-outputs (atom []))

;;; ── Access ────────────────────────────────────────────────────────

(defn ensure-access!
  "Request MIDI access if not already granted. Returns a Promise."
  []
  (if @midi-access
    (js/Promise.resolve @midi-access)
    (-> (js/navigator.requestMIDIAccess #js {:sysex false})
        (.then (fn [access]
                 (reset! midi-access access)
                 ;; Cache outputs
                 (let [outs (array)]
                   (.forEach (.-outputs access) #(.push outs %))
                   (reset! midi-outputs (vec outs)))
                 access)))))

;;; ── Input: CC mapping ─────────────────────────────────────────────

(defn- on-midi-message [event]
  (let [data  (.-data event)
        status (aget data 0)
        cmd    (bit-and status 0xF0)]
    ;; CC message: 0xB0
    (when (= cmd 0xB0)
      (let [cc-num (aget data 1)
            value  (aget data 2)]   ; 0–127
        (when-let [mapping (get @cc-mappings cc-num)]
          (let [{:keys [target min max on-change]} mapping
                normalized (/ value 127.0)
                scaled     (+ min (* normalized (- max min)))]
            (when on-change
              (on-change target scaled))))))))

(defn register-cc-listener!
  "Start listening for CC messages on all MIDI inputs."
  []
  (when-let [access @midi-access]
    (.forEach (.-inputs access)
              (fn [input]
                (set! (.-onmidimessage input) on-midi-message)))))

(defn map-cc!
  "Map a MIDI CC number to a parameter target."
  [cc-num target-kw on-change-fn]
  (swap! cc-mappings assoc cc-num
         {:target target-kw :min 0.0 :max 1.0 :on-change on-change-fn})
  (register-cc-listener!))

;;; ── Output: Note On/Off ──────────────────────────────────────────

(defn hz->midi
  "Convert Hz frequency to MIDI note number (0–127)."
  [hz]
  (let [m (+ 69 (* 12 (/ (Math/log (/ hz 440.0)) (Math/log 2))))]
    (Math/round (Math/max 0 (Math/min 127 m)))))

(defn send-note-on!
  "Send MIDI Note On to all outputs on the given channel (1-indexed)."
  [channel note velocity timestamp]
  (let [status (+ 0x90 (dec channel))
        msg    (js/Uint8Array. #js [status note velocity])]
    (doseq [^js output @midi-outputs]
      (.send output msg timestamp))))

(defn send-note-off!
  "Send MIDI Note Off to all outputs."
  [channel note timestamp]
  (let [status (+ 0x80 (dec channel))
        msg    (js/Uint8Array. #js [status note 0])]
    (doseq [^js output @midi-outputs]
      (.send output msg timestamp))))

;;; ── Output: Clock ─────────────────────────────────────────────────

(defn send-clock-byte! [byte-val]
  (let [msg (js/Uint8Array. #js [byte-val])]
    (doseq [^js output @midi-outputs]
      (.send output msg))))

(defn start-clock!
  "Start sending 24ppqn MIDI clock at the given BPM."
  [bpm]
  (stop-clock!)
  (send-clock-byte! 0xFA)  ; MIDI Start
  (let [interval-ms (* (/ 60.0 bpm 24.0) 1000)]
    (reset! clock-interval-id
            (js/setInterval #(send-clock-byte! 0xF8) interval-ms))))

(defn stop-clock!
  "Stop MIDI clock output."
  []
  (when-let [id @clock-interval-id]
    (js/clearInterval id)
    (reset! clock-interval-id nil)
    (send-clock-byte! 0xFC)))  ; MIDI Stop

(defn update-clock-bpm!
  "Update clock tempo without stopping. Restarts the interval."
  [bpm]
  (when @clock-interval-id
    (start-clock! bpm)))

;;; ── MIDI file export ──────────────────────────────────────────────

(defn- write-vlq
  "Variable-length quantity encoding for MIDI file delta times."
  [n]
  (if (< n 128)
    [n]
    (let [bytes (loop [v n acc []]
                  (if (zero? v)
                    acc
                    (recur (unsigned-bit-shift-right v 7)
                           (conj acc (bit-and v 0x7F)))))]
      ;; Set continuation bits on all but the last byte
      (let [reversed (vec (reverse bytes))]
        (into (mapv #(bit-or % 0x80) (butlast reversed))
              [(last reversed)])))))

(defn- write-u16 [n] [(bit-and (unsigned-bit-shift-right n 8) 0xFF)
                       (bit-and n 0xFF)])
(defn- write-u32 [n] [(bit-and (unsigned-bit-shift-right n 24) 0xFF)
                       (bit-and (unsigned-bit-shift-right n 16) 0xFF)
                       (bit-and (unsigned-bit-shift-right n 8) 0xFF)
                       (bit-and n 0xFF)])

(defn export-midi
  "Export a list of note events [{:time-sec f :duration-sec f :midi-note n :channel n}]
   as a standard MIDI file (Type 0). Returns a Uint8Array."
  [events bpm]
  (let [ppqn        480
        us-per-beat (int (/ 60000000 bpm))
        ;; Sort events by time
        sorted      (sort-by :time-sec events)
        ;; Build MTrk data
        track-bytes
        (reduce
          (fn [{:keys [bytes last-tick]} {:keys [time-sec duration-sec midi-note channel]}]
            (let [ch       (dec (or channel 1))
                  sec->tick (fn [s] (int (* s (/ bpm 60.0) ppqn)))
                  on-tick  (sec->tick time-sec)
                  off-tick (sec->tick (+ time-sec duration-sec))
                  delta-on (- on-tick last-tick)
                  ;; Note On
                  on-bytes (concat (write-vlq delta-on)
                                   [(+ 0x90 ch) midi-note 100])
                  ;; Note Off (delta from Note On)
                  delta-off (- off-tick on-tick)
                  off-bytes (concat (write-vlq delta-off)
                                    [(+ 0x80 ch) midi-note 0])]
              {:bytes (into bytes (concat on-bytes off-bytes))
               :last-tick off-tick}))
          {:bytes [] :last-tick 0}
          sorted)
        ;; Tempo meta event: FF 51 03 tt tt tt
        tempo-evt (concat [0x00 0xFF 0x51 0x03]
                          [(bit-and (unsigned-bit-shift-right us-per-beat 16) 0xFF)
                           (bit-and (unsigned-bit-shift-right us-per-beat 8) 0xFF)
                           (bit-and us-per-beat 0xFF)])
        ;; End of track: FF 2F 00
        eot [0x00 0xFF 0x2F 0x00]
        ;; Combine
        mtk-data (vec (concat tempo-evt (:bytes track-bytes) eot))
        ;; MThd header
        header (concat
                 [0x4D 0x54 0x68 0x64]    ; MThd
                 (write-u32 6)             ; header length
                 (write-u16 0)             ; format 0
                 (write-u16 1)             ; 1 track
                 (write-u16 ppqn))
        ;; MTrk header
        trk-header (concat
                     [0x4D 0x54 0x72 0x6B]  ; MTrk
                     (write-u32 (count mtk-data)))]
    (js/Uint8Array. (clj->js (concat header trk-header mtk-data)))))
```

---

### 2. `packages/core/src/repulse/params.cljs` — midi-out parameter

Add a `midi-out` param function, same pattern as `amp`, `pan`:

```clojure
(defn midi-out
  "Tag events for MIDI note output on a channel (1–16).
   (midi-out 1 pat)       — send all events to MIDI channel 1
   (midi-out 1)           — return transformer"
  ([ch]     (fn [pat] (midi-out ch pat)))
  ([ch pat] (apply-param :midi-ch ch pat)))
```

---

### 3. `app/src/repulse/app.cljs` — Lisp bindings

Add to `ensure-env!`:

```clojure
;; --- MIDI controller input ---
"midi-map"
(fn [& args]
  (let [args' (mapv leval/unwrap args)]
    (-> (midi/ensure-access!)
        (.then (fn [_]
                 (let [[_ cc-num target] args']
                   (midi/map-cc! (int cc-num) target
                     (fn [tgt val]
                       (case tgt
                         :filter (fx/set-param! "filter" "value" val)
                         :amp    (audio/set-master-gain! val)
                         :bpm    (audio/set-bpm! (+ 60 (* val 180)))
                         nil)))))))
    "mapping MIDI CC"))

;; --- MIDI note output ---
"midi-out"
(fn
  ([ch]   (params/midi-out (leval/unwrap ch)))
  ([ch p] (params/midi-out (leval/unwrap ch) (leval/unwrap p))))

;; --- MIDI clock output ---
"midi-clock-out!"
(fn [on?]
  (let [on (leval/unwrap on?)]
    (if on
      (-> (midi/ensure-access!)
          (.then (fn [_]
                   (let [bpm (:bpm @audio/scheduler-state)]
                     (midi/start-clock! (or bpm 120))))))
      (midi/stop-clock!))
    nil))

;; --- MIDI file export ---
"midi-export"
(fn [& args]
  (let [args'      (mapv leval/unwrap args)
        track-name (first args')
        n-cycles   (or (second args') 4)
        state      @audio/scheduler-state
        pattern    (get-in state [:tracks track-name])
        bpm        (or (:bpm state) 120)
        cycle-dur  (:cycle-dur state)]
    (if-not pattern
      (str "Error: no track " (name track-name))
      (let [events
            (for [c (range n-cycles)
                  :let [sp {:start [(int c) 1] :end [(int (inc c)) 1]}]
                  ev (core/query pattern sp)
                  :let [value (:value ev)
                        hz (cond
                             (number? value) value
                             (keyword? value) (theory/note->hz value)
                             (and (map? value) (:note value))
                             (let [n (:note value)]
                               (if (keyword? n) (theory/note->hz n) n))
                             :else nil)]
                  :when hz]
              (let [part-start (core/rat->float (:start (:part ev)))
                    part-end   (core/rat->float (:end (:part ev)))
                    t          (* (- part-start 0) cycle-dur)
                    dur        (* (- part-end part-start) cycle-dur)]
                {:time-sec     t
                 :duration-sec dur
                 :midi-note    (midi/hz->midi hz)
                 :channel      1}))
            midi-data (midi/export-midi (vec events) bpm)
            blob      (js/Blob. #js [midi-data] #js {:type "audio/midi"})
            url       (.createObjectURL js/URL blob)
            a         (.createElement js/document "a")]
        (set! (.-href a) url)
        (set! (.-download a) (str "repulse-" (name track-name) ".mid"))
        (.click a)
        (.revokeObjectURL js/URL url)
        (str "exported " n-cycles " cycles of :" (name track-name) " as MIDI")))))

;; --- Freesound ---
"freesound-key!"
(fn [key]
  (reset! freesound-api-key (leval/unwrap key))
  "Freesound API key set")

"freesound!"
(fn [query]
  (let [q   (leval/unwrap query)
        key @freesound-api-key]
    (if-not key
      "Error: set API key first with (freesound-key! \"your-key\")"
      (do
        (-> (js/fetch (str "https://freesound.org/apiv2/search/text/"
                           "?query=" (js/encodeURIComponent q)
                           "&token=" key
                           "&fields=id,name,previews"
                           "&page_size=5"))
            (.then #(.json %))
            (.then (fn [data]
                     (let [results (js->clj (.-results data) :keywordize-keys true)]
                       (doseq [{:keys [id name previews]} results]
                         (let [url (get previews "preview-hq-mp3")]
                           (when url
                             (samples/register-url!
                               (str "freesound-" id) 0 url))))
                       (set-output!
                         (str "loaded " (count results) " sounds: "
                              (clojure.string/join ", "
                                (map #(str ":freesound-" (:id %)) results)))
                         :success))))
            (.catch (fn [e]
                      (set-output! (str "Freesound error: " e) :error))))
        "searching freesound…"))))
```

---

### 4. `app/src/repulse/audio.cljs` — MIDI note dispatch in scheduler

In `schedule-cycle!`, after `play-event`, check for `:midi-ch` in the event value
and send MIDI Note On/Off:

```clojure
;; After (play-event ac t (:value ev) track-name):
(when-let [midi-ch (:midi-ch (:value ev))]
  (let [value (:value ev)
        hz    (or (:freq value)
                  (when (keyword? (:note value))
                    (theory/note->hz (:note value)))
                  (when (number? (:note value))
                    (:note value)))
        note  (when hz (midi/hz->midi hz))
        t-ms  (* (+ (* cycle cycle-dur)
                     (* (- part-start cycle) cycle-dur))
                 1000)
        dur   (* (- part-end part-start) cycle-dur 1000)]
    (when note
      (let [perf-now (js/performance.now)
            ac-now   (.-currentTime ac)
            offset   (* (- t (float ac-now)) 1000)
            ts       (+ perf-now offset)]
        (midi/send-note-on! midi-ch note 100 ts)
        (midi/send-note-off! midi-ch note (+ ts dur))))))
```

---

### 5. Grammar and completions

Add to `BuiltinName` in `repulse-lisp.grammar`:

```
"midi-map" | "midi-out" | "midi-clock-out!" | "midi-export" |
"freesound!" | "freesound-key!" |
```

Add to `completions.js`:

```javascript
// --- MIDI ---
{ label: "midi-map",        type: "function", detail: "(midi-map :cc N :target) — map MIDI CC to parameter" },
{ label: "midi-out",        type: "function", detail: "(midi-out ch pat) — route events as MIDI notes on channel 1–16" },
{ label: "midi-clock-out!", type: "function", detail: "(midi-clock-out! true/false) — broadcast 24ppqn MIDI clock" },
{ label: "midi-export",     type: "function", detail: "(midi-export :track N) — export N cycles as .mid file" },
// --- Freesound ---
{ label: "freesound-key!",  type: "function", detail: "(freesound-key! \"key\") — set your Freesound API key" },
{ label: "freesound!",      type: "function", detail: "(freesound! \"query\") — search and load Freesound samples" },
```

After editing the grammar, run `npm run gen:grammar`.

---

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/midi.cljs` | **New** — MIDI access, CC mapping, note/clock output, MIDI file export |
| `packages/core/src/repulse/params.cljs` | `midi-out` param function |
| `app/src/repulse/app.cljs` | `midi-map`, `midi-out`, `midi-clock-out!`, `midi-export`, `freesound!`, `freesound-key!` bindings; `freesound-api-key` atom |
| `app/src/repulse/audio.cljs` | MIDI note dispatch in `schedule-cycle!`; require `repulse.midi` |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add 6 tokens to BuiltinName |
| `app/src/repulse/lisp-lang/completions.js` | Add 6 completion entries |
| `app/src/repulse/lisp-lang/hover.js` | Add hover docs for 6 new built-ins |
| `docs/USAGE.md` | New sections: "MIDI controller mapping", "MIDI output", "MIDI clock", "MIDI file export", "Freesound" |
| `README.md` | Add MIDI/Freesound rows to language reference table |
| `CLAUDE.md` | Mark Phase N as delivered when done |

No changes to `packages/core/src/repulse/core.cljs`, `packages/audio/` (Rust/WASM),
or the pattern algebra.

---

## Platform requirements

| Feature | Browser support |
|---|---|
| MIDI controller input | Chrome, Edge (Web MIDI API) |
| MIDI note output | Chrome, Edge |
| MIDI clock output | Chrome, Edge |
| MIDI file export | All (pure binary generation, no API dependency) |
| Freesound API | All (fetch + audio decode) |

For browsers without Web MIDI API (Firefox, Safari), MIDI functions should return a
clear error message: `"MIDI not supported in this browser — use Chrome or Edge"`.

---

## Definition of done

### MIDI controller input

- [ ] `(midi-map :cc 1 :filter)` maps CC #1 to master filter cutoff
- [ ] Turning the mapped CC knob on a MIDI controller changes the filter in real time
- [ ] `(midi-map :cc 7 :amp)` maps CC #7 to master gain
- [ ] `(midi-map :cc 10 :bpm)` maps CC #10 to tempo (range 60–240)
- [ ] Multiple CC mappings work simultaneously
- [ ] Browser without Web MIDI returns a clear error message

### MIDI note output

- [ ] `(midi-out 1 (seq :c4 :e4 :g4))` sends Note On/Off on MIDI channel 1
- [ ] Note timing aligns with audio playback (± 10ms)
- [ ] Note Off is sent at event end, not at next event start
- [ ] Works with `scale`, `chord`, `transpose` — any pattern producing Hz values
- [ ] `(->> (scale :minor :c4 (seq 0 2 4 7)) (midi-out 1) (amp 0.7))` chains with params
- [ ] MIDI velocity maps to `amp` value (0.0 → vel 0, 1.0 → vel 127)

### MIDI clock output

- [ ] `(midi-clock-out! true)` starts sending 24ppqn clock
- [ ] Clock tempo matches REPuLse BPM
- [ ] Changing BPM via `(bpm N)` updates clock rate
- [ ] `(midi-clock-out! false)` sends Stop and stops clock
- [ ] MIDI Start (0xFA) is sent at the beginning
- [ ] External hardware/DAW locks to REPuLse tempo

### MIDI file export

- [ ] `(midi-export :bass 4)` downloads a `.mid` file
- [ ] The MIDI file opens in any DAW (Logic, Ableton, Reaper)
- [ ] Notes have correct pitch, timing, and duration
- [ ] Tempo is embedded in the file header
- [ ] Multi-note chords export correctly

### Freesound

- [ ] `(freesound-key! "abc123")` stores the API key
- [ ] `(freesound! "kick 808")` searches and loads up to 5 results
- [ ] Loaded sounds are accessible as `:freesound-N` keywords
- [ ] `(seq :freesound-12345 :freesound-67890)` plays the loaded samples
- [ ] Network errors show clear messages
- [ ] Without an API key, `(freesound! ...)` returns an explanatory error

### No regressions

- [ ] Existing `(midi-sync! true)` clock input still works
- [ ] All existing core tests pass (`npm run test:core`)
- [ ] Patterns without MIDI output are unaffected
