# REPuLse — AudioWorklet Phase (Phase 3) (delivered)

## Context

REPuLse is a browser-based live coding instrument. Phase 2 moved synthesis into a
Rust/WASM module running on the main thread. This phase moves that WASM module into
an **AudioWorklet** — the browser's dedicated audio processing thread.

Current state after Phase 2:
- `packages/audio/` — Rust crate compiled to WASM via `wasm-pack`
- `AudioEngine.trigger(value, time, duration)` — WASM API for scheduling sounds
- ClojureScript lookahead clock calls `trigger!` from the main thread
- Synthesis quality is good but WASM runs on the main thread, subject to jank

---

## Goal for this session

By the end of this session:

1. The WASM synthesis module runs inside an `AudioWorkletProcessor`
2. The main thread communicates with the Worklet via `MessagePort`
3. Audio is glitch-free even under heavy main-thread load (e.g. large pattern evaluations)
4. The ClojureScript scheduler interface (`trigger!`, `stop!`) is unchanged
5. All existing patterns continue to play correctly

---

## Architecture

```
Main Thread                          AudioWorklet Thread
─────────────────────────────────    ──────────────────────────────────
ClojureScript scheduler              AudioWorkletProcessor
  pattern.query(cycle)                 WASM AudioEngine
  → trigger!(value, time, dur)         processes MessagePort messages
      → port.postMessage(event)  ───►  engine.trigger(value, time, dur)
                                        Web Audio API nodes
                                        → AudioContext destination
```

The Worklet thread has access to the `AudioContext` and a higher-priority event loop —
it's not blocked by garbage collection or JS execution on the main thread.

---

## Repository structure changes

```
packages/
└── audio/
    ├── Cargo.toml          # unchanged
    ├── src/
    │   └── lib.rs          # add MessagePort handling, Worklet-compatible API
    └── pkg/                # wasm-pack output

app/
├── public/
│   └── worklet.js          # NEW — AudioWorkletProcessor that loads WASM
└── src/repulse/
    └── audio.cljs          # updated to use AudioWorklet
```

---

## Worklet processor (`app/public/worklet.js`)

```javascript
// AudioWorkletProcessor that hosts the WASM AudioEngine
class RepulseProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.engine = null;
    this.port.onmessage = (e) => this.handleMessage(e.data);
  }

  async handleMessage(msg) {
    if (msg.type === 'init') {
      // Load WASM inside the Worklet context
      const { default: init, AudioEngine } = await import(msg.wasmUrl);
      await init();
      this.engine = new AudioEngine(/* context available via globalThis */);
      this.port.postMessage({ type: 'ready' });
    } else if (msg.type === 'trigger' && this.engine) {
      this.engine.trigger(msg.value, msg.time, msg.duration);
    } else if (msg.type === 'stop' && this.engine) {
      this.engine.stop_all();
    }
  }

  process() {
    // No-op — synthesis is handled via scheduled Web Audio API nodes
    return true;
  }
}

registerProcessor('repulse-processor', RepulseProcessor);
```

---

## ClojureScript changes (`audio.cljs`)

```clojure
(defonce worklet-node (atom nil))

(defn init-worklet! [ac]
  (-> (.addModule (.-audioWorklet ac) "/worklet.js")
      (.then (fn []
               (let [node (js/AudioWorkletNode. ac "repulse-processor")]
                 (.connect node (.-destination ac))
                 (reset! worklet-node node)
                 ;; Send WASM URL to worklet
                 (.. node -port
                     (postMessage #js {:type "init"
                                       :wasmUrl "/js/repulse_audio_bg.wasm"}))
                 ;; Wait for ready
                 (set! (.. node -port -onmessage)
                       (fn [e]
                         (when (= "ready" (.-type (.-data e)))
                           (js/console.log "[REPuLse] AudioWorklet ready")))))))
      (.catch #(js/console.error "[REPuLse] Worklet init failed, using fallback" %))))

(defn trigger! [value time duration]
  (if-let [node @worklet-node]
    (.. node -port (postMessage #js {:type "trigger"
                                     :value (name value)
                                     :time time
                                     :duration duration}))
    ;; Fallback: direct WASM call on main thread
    (wasm/trigger! value time duration)))
```

---

## Rust changes (`lib.rs`)

The `AudioEngine` struct must be `Send`-compatible and not hold JS references that
can't cross the thread boundary. Key changes:

- `AudioEngine` stores only the `AudioContext` (available in worklet as `globalThis.currentTime`)
- All synthesis node creation remains the same — Web Audio API is available in Worklet context
- Add a `from_worklet()` constructor that retrieves the context from `globalThis`

```rust
#[wasm_bindgen]
impl AudioEngine {
    #[wasm_bindgen(constructor)]
    pub fn new(ctx: AudioContext) -> AudioEngine { ... }

    /// For use inside AudioWorkletProcessor where ctx comes from globalThis
    pub fn from_worklet() -> AudioEngine {
        let ctx = js_sys::Reflect::get(
            &js_sys::global(),
            &"sampleRate".into()
        );
        // construct with worklet context
    }
}
```

---

## WASM target considerations

The Worklet context can import ES modules. `wasm-pack --target web` produces ES module
output, which works. The WASM binary and JS glue need to be served from `app/public/js/`:

```json
"build:wasm": "wasm-pack build packages/audio --target web --out-dir app/public/js"
```

---

## Fallback strategy

If `AudioWorklet` is unavailable or the Worklet fails to load:
1. Fall back to direct WASM calls on the main thread (Phase 2 behaviour)
2. If WASM also unavailable, fall back to ClojureScript synthesis (Phase 1 behaviour)

Log the active backend on startup:
```
[REPuLse] audio backend: audioworklet+wasm
[REPuLse] audio backend: wasm (main thread)
[REPuLse] audio backend: clojurescript synthesis
```

---

## Definition of Done

- [ ] `AudioWorkletProcessor` loads and initialises WASM correctly
- [ ] Browser console shows `[REPuLse] AudioWorklet ready` on startup
- [ ] `(seq :bd :sd :bd :sd)` plays with no glitching under CPU load
- [ ] Evaluating a large/complex pattern does not cause audio dropouts
- [ ] `(stop)` stops all sound immediately via the Worklet port
- [ ] Dev tools Performance tab shows synthesis work on the Audio thread, not Main
- [ ] Fallback to main-thread WASM verified (temporarily break worklet import)

---

## What NOT to do in this phase

- No new synthesis voices — quality improvements are Phase 2's job
- No sample loading in the Worklet — that is a separate concern
- No shared memory (`SharedArrayBuffer`) yet — that is optional in Phase 4
- No changes to the Lisp language or pattern engine
