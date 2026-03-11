# REPuLse — Advanced Plugins (Phase 7)

## Context

REPuLse has a plugin system (Phase 6a), visual plugins, and global effect plugins on the
master bus (Phase 6b). This phase adds three advanced capabilities:

1. **Per-pattern effect routing** — each pattern can have its own effects chain, not just
   the global master bus
2. **MIDI output plugin** — route REPuLse pattern events to hardware synths and DAWs
   via the Web MIDI API
3. **Recorder plugin** — capture the master audio output to a downloadable WAV/OGG file

Current state after Phase 6b:
```
WorkletNode ──► masterGain ──► [reverb] ──► [delay] ──► [filter] ──► [compressor] ──► analyser ──► destination
```

---

## Goal for this session

By the end of this session:

1. `(play :kick (seq :bd :_))` runs patterns in **named slots**, each with its own output gain
2. `(with-fx :kick (reverb 0.3) (delay 0.25))` routes a named slot through its own effects chain
3. A **MIDI output plugin** enables `(midi-out :ch1 :bd)` to send Note On/Off messages
4. A **recorder plugin** adds a **⏺ record** button that captures audio to a downloadable file
5. All Phase 6 behaviour (global effects, visual plugins) continues unchanged

---

## Feature 1 — Per-pattern effect routing

### Architecture

Each named pattern slot gets a `GainNode` that feeds into either the master chain or its
own per-slot effects chain before merging back:

```
WorkletNode (shared synthesis)
      │
      ├──► slotGain[:kick] ──► [slot reverb] ──► merge
      ├──► slotGain[:snare] ──────────────────► merge
      └──► slotGain[:hats] ──► [slot delay]  ──► merge
                                                    │
                                              masterGain ──► [global effects] ──► analyser ──► destination
```

This requires Phase 4 (multiple simultaneous patterns / named slots) to be implemented first,
since per-pattern routing is meaningless without named slots. If Phase 4 is not yet done,
implement a simplified version where each `(play :name ...)` call creates a slot implicitly.

### Slot gain management

**`app/src/repulse/slots.cljs`** — maintains a map of slot-name → `GainNode`:

```clojure
(defonce slots (atom {}))  ; {keyword → {:gain GainNode :fx-chain [effect-nodes]}}

(defn ensure-slot! [name]
  (when-not (get @slots name)
    (let [ac   (audio/get-ctx)
          gain (.createGain ac)]
      ;; Connect slot gain into master chain (before masterGain)
      (.connect gain @audio/master-gain)
      (swap! slots assoc name {:gain gain :fx-chain []}))))

(defn get-slot-gain [name]
  (some-> (get @slots name) :gain))
```

The WorkletNode is extended: instead of connecting to `masterGain`, the scheduler connects
per-event to the appropriate slot gain node. This requires `play-event` to accept a
target `AudioNode` parameter.

### `(with-fx :slot-name effect ...)` Lisp built-in

```lisp
(with-fx :kick (reverb 0.4) (delay 0.125))
```

This inserts a local effects chain on the `:kick` slot output, before the master chain.
The `reverb` and `delay` inside `with-fx` are effect *constructors* returning configuration
maps — they do not modify the global master chain.

---

## Feature 2 — MIDI output plugin

### Plugin file: `app/public/plugins/midi-out.js`

```javascript
export default {
  type: "midi",
  name: "midi-out",
  version: "1.0.0",

  async init(host) {
    this._output  = null;
    this._outputs = [];
    this._channel = 0;  // MIDI channel 0–15
    this._noteMap = {
      "bd": 36, "sd": 38, "hh": 42, "oh": 46, "cp": 39,
      "cb": 56, "cr": 49, "ride": 51
    };
    host.registerLisp("midi-out",     (ch, val) => this._trigger(ch, val));
    host.registerLisp("midi-channel", (ch)      => { this._channel = ch; });
    host.registerLisp("midi-connect", ()        => this._requestAccess());
    try { await this._requestAccess(); }
    catch(e) { console.warn("[REPuLse] MIDI unavailable:", e); }
  },

  async _requestAccess() {
    const access   = await navigator.requestMIDIAccess();
    this._outputs  = [...access.outputs.values()];
    if (this._outputs.length > 0) {
      this._output = this._outputs[0];
      console.log("[REPuLse] MIDI output:", this._output.name);
    }
  },

  _trigger(channel, value) {
    if (!this._output) return;
    const note     = typeof value === "number" ? value
                   : (this._noteMap[String(value)] ?? 60);
    const ch       = typeof channel === "number" ? channel : this._channel;
    const noteOn   = [0x90 | ch, note, 100];
    const noteOff  = [0x80 | ch, note, 0];
    const t        = performance.now();
    this._output.send(noteOn, t);
    this._output.send(noteOff, t + 100);
  },

  mount()   { /* no visual component */ },
  unmount() { },
  destroy() { this._output = null; }
};
```

### Lisp integration

```lisp
; Connect to first available MIDI output
(midi-connect)

; Send :bd as MIDI note 36 on channel 0
(seq :bd :sd :hh)
; — or route specific values —
(fmap (fn [x] (midi-out 0 x)) (seq :bd :sd))

; Set default channel
(midi-channel 1)
```

### Web MIDI notes

- `navigator.requestMIDIAccess()` requires HTTPS or localhost
- Safari does not support Web MIDI API — show a warning if unavailable
- MIDI output timing: `performance.now()` is used (Web MIDI timestamps are in ms from page
  load); this is approximate, not sample-accurate

---

## Feature 3 — Recorder plugin

### Plugin file: `app/public/plugins/recorder.js`

Uses the Web Audio `MediaRecorder` API by tapping the master bus through a `MediaStreamDestinationNode`:

```javascript
export default {
  type: "recorder",
  name: "recorder",
  version: "1.0.0",

  init(host) {
    this._ctx        = host.audioCtx;
    this._masterGain = host.masterGain;
    this._streamDest = null;
    this._recorder   = null;
    this._chunks     = [];
    this._button     = null;
    host.registerLisp("record",      () => this._startRecording());
    host.registerLisp("record-stop", () => this._stopRecording());
  },

  mount(container) {
    this._button = document.createElement("button");
    this._button.textContent = "⏺ record";
    this._button.className   = "record-btn";
    this._button.onclick     = () => this._toggle();
    container.appendChild(this._button);
  },

  _toggle() {
    if (this._recorder && this._recorder.state === "recording")
      this._stopRecording();
    else
      this._startRecording();
  },

  _startRecording() {
    this._streamDest = this._ctx.createMediaStreamDestination();
    this._masterGain.connect(this._streamDest);
    const mimeType = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
                   ? "audio/webm;codecs=opus" : "audio/ogg;codecs=opus";
    this._recorder = new MediaRecorder(this._streamDest.stream, { mimeType });
    this._chunks   = [];
    this._recorder.ondataavailable = (e) => this._chunks.push(e.data);
    this._recorder.onstop          = ()  => this._exportFile(mimeType);
    this._recorder.start();
    if (this._button) this._button.textContent = "⏹ stop rec";
    console.log("[REPuLse] Recording started");
  },

  _stopRecording() {
    if (this._recorder) this._recorder.stop();
    if (this._streamDest) this._masterGain.disconnect(this._streamDest);
    if (this._button) this._button.textContent = "⏺ record";
  },

  _exportFile(mimeType) {
    const blob = new Blob(this._chunks, { type: mimeType });
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement("a");
    a.href     = url;
    a.download = `repulse-${Date.now()}.${mimeType.includes("webm") ? "webm" : "ogg"}`;
    a.click();
    URL.revokeObjectURL(url);
    console.log("[REPuLse] Recording saved");
  },

  unmount() { if (this._button) this._button.remove(); },
  destroy() { this._stopRecording(); }
};
```

### ⏺ record button placement

The recorder plugin mounts its button inside the plugin panel (Phase 6a). Alternatively,
it can be added to the header alongside the play/stop button — pass a different `container`
element to `mount()` for header-level plugins.

### Lisp integration

```lisp
(record)       ; start recording
(record-stop)  ; stop and download file
```

---

## Repository structure changes

```
app/
├── public/
│   └── plugins/
│       ├── oscilloscope.js    (Phase 6a)
│       ├── reverb.js          (Phase 6b)
│       ├── delay.js           (Phase 6b)
│       ├── filter.js          (Phase 6b)
│       ├── compressor.js      (Phase 6b)
│       ├── midi-out.js        NEW
│       └── recorder.js        NEW
└── src/repulse/
    ├── slots.cljs             NEW — per-slot gain management
    ├── fx.cljs                updated — per-slot fx chain support
    ├── audio.cljs             updated — route events to slot gains
    └── app.cljs               updated — auto-load new plugins
```

---

## Definition of Done

**Per-pattern routing:**
- [ ] `(play :kick (seq :bd :_))` and `(play :snare (seq :_ :sd))` run simultaneously
- [ ] Each slot has an independent volume (verified by muting one slot's gain)

**MIDI out:**
- [ ] On Chrome/Edge (HTTPS), `(midi-connect)` enumerates MIDI outputs in the console
- [ ] `:bd` in a sequence sends MIDI note 36 to the selected output
- [ ] On browsers without Web MIDI, a clear console warning is shown (no crash)

**Recorder:**
- [ ] ⏺ record button appears in the plugin panel
- [ ] Clicking it starts recording; clicking again downloads an audio file
- [ ] The downloaded file plays back correctly in a media player
- [ ] `(record)` / `(record-stop)` work from the REPL

**General:**
- [ ] All Phase 6 effects and visual plugins still work
- [ ] All core unit tests still pass

---

## What NOT to do in this phase

- No MIDI input or MIDI clock sync (see FUTURE-FEATURES.md)
- No Ableton Link sync
- No OfflineAudioContext rendering (real-time recording only)
- No multi-track recording (one stereo mix only)
- No changes to the pattern engine, Lisp language, or WASM synthesis
