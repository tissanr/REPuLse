(ns repulse.orbits
  "Phase TL7b — reverb and delay sends per orbit.

   Each orbit (0–3) owns one ConvolverNode reverb and one feedback DelayNode,
   built from browser nodes in every context (live and offline). Events send
   part of their signal there at a per-event level (`room`, `delay`); the
   orbit outputs return into the master gain, so the global `fx` chain still
   processes them. The WASM worklet does not implement the effects: it writes
   per-orbit send buses (lib.rs `process_block_buses`) that feed the same nodes.

     room-in  → conv ─────────────┐
     delay-in → delay ⇄ fb        ├→ out → master
                delay ────────────┘

   Orbit settings (`size`, `delay-time`, `delay-feedback`) come from events,
   last event wins (SuperDirt). Buses are created on first use, so an orbit
   nobody sends to costs nothing. The pure helpers at the top are unit-tested
   in orbits_test.cljs.")

;;; ── Pure helpers ──────────────────────────────────────────────────────

(def n-orbits
  "Number of orbits. Must match N_ORBITS in packages/audio/src/lib.rs."
  4)

(def defaults
  "Orbit settings after (stop) and before any event sets them."
  {:size 0.5 :delay-time 0.25 :delay-feedback 0.5})

(def max-delay-time
  "DelayNode maxDelayTime, and the upper clamp of delay-time (seconds)."
  4.0)

(def ir-decay
  "Exponent of the noise-burst impulse-response envelope (as reverb.js)."
  3.0)

(defn- ->num
  "A finite JS number from a number or an [n d] rational, else nil."
  [x]
  (let [n (cond
            (number? x) x
            (and (vector? x) (= 2 (count x)) (number? (first x)) (number? (second x))
                 (not (zero? (second x))))
            (/ (first x) (second x)))]
    (when (and n (js/isFinite n)) (double n))))

(defn- clamp [lo hi x] (min hi (max lo x)))

(defn orbit-index
  "Orbit index for an :orbit value: rounded, clamped to 0–3; absent or not a
   number → 0."
  [x]
  (if-some [n (->num x)]
    (int (clamp 0 (dec n-orbits) (Math/round n)))
    0))

(defn out-of-range?
  "True when the :orbit value x is a number that orbit-index had to clamp."
  [x]
  (if-some [n (->num x)]
    (not= (Math/round n) (orbit-index x))
    false))

(defn- fmt-num [x]
  (if (and (vector? x) (= 2 (count x)))
    (str (first x) "/" (second x))
    (str x)))

(defn out-of-range-message
  "Warning text for an out-of-range :orbit value x."
  [x]
  (str "orbit " (fmt-num x) " is out of range 0–" (dec n-orbits)
       "; using " (orbit-index x)))

(defn send-params
  "Send levels of an event value map, or nil when it sends nothing (the common
   case: no extra nodes, no extra WASM work).
   → {:room 0–1 :delay 0–1 :orbit 0–3}"
  [v]
  (when (map? v)
    (let [room  (clamp 0.0 1.0 (or (->num (:room v)) 0.0))
          delay (clamp 0.0 1.0 (or (->num (:delay v)) 0.0))]
      (when (or (pos? room) (pos? delay))
        {:room room :delay delay :orbit (orbit-index (:orbit v))}))))

(defn wasm-slots
  "trigger_v3 slots 12–14 (room delay orbit) for a send-params map."
  [snd]
  [(:room snd 0) (:delay snd 0) (:orbit snd 0)])

(defn orbit-settings
  "The orbit settings present in event value v, clamped, or nil.
   → {:size 0–1 :delay-time 0.01–4 :delay-feedback 0–0.95} (present keys only)"
  [v]
  (when (map? v)
    (let [m (cond-> {}
              (some? (->num (:size v)))
              (assoc :size (clamp 0.0 1.0 (->num (:size v))))
              (some? (->num (:delay-time v)))
              (assoc :delay-time (clamp 0.01 max-delay-time (->num (:delay-time v))))
              (some? (->num (:delay-feedback v)))
              (assoc :delay-feedback (clamp 0.0 0.95 (->num (:delay-feedback v)))))]
      (not-empty m))))

(defn size-step
  "Reverb size rounded to steps of 0.05 → integer 0–20 (the IR cache key)."
  [size]
  (int (Math/round (* 20 (clamp 0.0 1.0 size)))))

(defn ir-seconds
  "Impulse-response length in seconds for a size step: 0.3 + 5.7 · size."
  [step]
  (+ 0.3 (* 5.7 (/ step 20))))

(defn- prng
  "Deterministic PRNG (mulberry32) → fn returning floats in [0, 1)."
  [seed]
  (let [s (atom (bit-or seed 0))]
    (fn []
      (let [a (swap! s #(bit-or (+ % 0x6D2B79F5) 0))
            t (js/Math.imul (bit-xor a (unsigned-bit-shift-right a 15)) (bit-or a 1))
            t (bit-xor t (+ t (js/Math.imul (bit-xor t (unsigned-bit-shift-right t 7))
                                           (bit-or t 61))))]
        (/ (unsigned-bit-shift-right (bit-xor t (unsigned-bit-shift-right t 14)) 0)
           4294967296)))))

(defn fill-ir!
  "Fill Float32Array data with the noise-burst IR of reverb.js
   ((2r − 1) · (1 − i/len)^decay), using a PRNG seeded by step and channel so
   renders are reproducible."
  [data step channel]
  (let [len  (.-length data)
        rand (prng (+ 1 (* 7919 step) (* 104729 channel)))]
    (dotimes [i len]
      (aset data i (* (- (* 2 (rand)) 1) (Math/pow (- 1 (/ i len)) ir-decay))))
    data))

(defn orbit-warnings
  "Distinct warning messages for out-of-range :orbit values in the first
   `cycles` cycles of the given patterns. query-fn — (fn [pat cycle] events)."
  [pats query-fn cycles]
  (->> (for [pat pats
             c   (range cycles)
             ev  (try (query-fn pat c) (catch :default _ nil))
             :let [v (:value ev)]
             :when (and (map? v) (contains? v :orbit) (out-of-range? (:orbit v)))]
         (out-of-range-message (:orbit v)))
       distinct
       vec))

;;; ── Web Audio ─────────────────────────────────────────────────────────

(defn- offline? [ac]
  (and (exists? js/OfflineAudioContext) (instance? js/OfflineAudioContext ac)))

;; AudioContext → (atom {:buses {i bus} :settings {i settings} :irs {step AudioBuffer}})
(defonce ^:private states
  (when (exists? js/WeakMap) (js/WeakMap.)))

(defn- state [ac]
  (or (.get states ac)
      (let [s (atom {:buses {} :settings {} :irs {} :gen 0})]
        (.set states ac s)
        s)))

(defn bus-count
  "Number of orbit buses created in context ac (tests, node-count checks)."
  [ac]
  (if-let [s (and states (.get states ac))]
    (count (:buses @s))
    0))

(defn- impulse-response
  "Cached stereo IR AudioBuffer for a size step (≤ 21 per context)."
  [ac step]
  (let [s (state ac)]
    (or (get-in @s [:irs step])
        (let [sr  (.-sampleRate ac)
              len (max 1 (Math/ceil (* sr (ir-seconds step))))
              buf (.createBuffer ac 2 len sr)]
          (fill-ir! (.getChannelData buf 0) step 0)
          (fill-ir! (.getChannelData buf 1) step 1)
          (swap! s assoc-in [:irs step] buf)
          buf))))

(defn- settings-of [ac i]
  (merge defaults (get-in @(state ac) [:settings i])))

(defn- set-size!
  "Swap the reverb IR of bus when the size step changes. Swapping the buffer of
   a ringing ConvolverNode may click; that is accepted (spec)."
  [ac bus step]
  (when (not= step @(:step bus))
    (reset! (:step bus) step)
    (set! (.-buffer ^js (:conv bus)) (impulse-response ac step))))

(defn- apply-to-bus!
  "Apply settings map m to bus at audio time t."
  [ac bus m t]
  (when-let [dt (:delay-time m)]
    (.setValueAtTime (.-delayTime ^js (:delay bus)) dt t))
  (when-let [fb (:delay-feedback m)]
    (.setValueAtTime (.-gain ^js (:fb bus)) fb t))
  (when-let [size (:size m)]
    (let [step (size-step size)
          wait (- t (.-currentTime ac))]
      ;; A buffer swap cannot be scheduled: live contexts wait for t; offline
      ;; renders schedule every event before rendering, so the swap is
      ;; immediate and the last size event of an orbit wins for the render.
      (if (and (not (offline? ac)) (pos? wait))
        (let [gen (:gen @(state ac))]
          ;; (stop) bumps :gen, so a swap still pending from before is dropped.
          (js/setTimeout #(when (= gen (:gen @(state ac))) (set-size! ac bus step))
                         (* 1000 wait)))
        (set-size! ac bus step)))))

(defn orbit-bus
  "The bus of orbit i in context ac, created on first use with its output
   connected to out:
   {:room-in GainNode :delay-in GainNode :conv ConvolverNode :delay DelayNode
    :fb GainNode :out GainNode}"
  [ac i out]
  (let [s (state ac)]
    (or (get-in @s [:buses i])
        (let [now      (.-currentTime ac)
              room-in  (.createGain ac)
              delay-in (.createGain ac)
              conv     (.createConvolver ac)
              dl       (.createDelay ac max-delay-time)
              fb       (.createGain ac)
              o        (.createGain ac)
              m        (settings-of ac i)
              bus      {:room-in room-in :delay-in delay-in :conv conv
                        :delay dl :fb fb :out o :step (atom nil)}]
          (.connect room-in conv)
          (.connect conv o)
          (.connect delay-in dl)
          (.connect dl fb)
          (.connect fb dl)
          (.connect dl o)
          (.connect o out)
          (.setValueAtTime (.-delayTime dl) (:delay-time m) now)
          (.setValueAtTime (.-gain fb) (:delay-feedback m) now)
          (set-size! ac bus (size-step (:size m)))
          (swap! s assoc-in [:buses i] bus)
          bus))))

(defn connect-worklet!
  "Create all orbit buses of ac (output to out) and connect the send outputs
   of a 9-output WASM worklet node: output 1+i → room-in of orbit i,
   output 5+i → delay-in of orbit i."
  [ac ^js node out]
  (dotimes [i n-orbits]
    (let [{:keys [room-in delay-in]} (orbit-bus ac i out)]
      (.connect node room-in (+ 1 i))
      (.connect node delay-in (+ 1 n-orbits i)))))

(def worklet-outputs
  "AudioWorkletNode options for the WASM worklet with send outputs."
  (let [n (+ 1 (* 2 n-orbits))]
    {:numberOfOutputs n :outputChannelCount (vec (repeat n 2))}))

(defn apply-orbit-settings!
  "Record the orbit settings of event value v (size / delay-time /
   delay-feedback, when present) for orbit i at audio time t. An existing bus
   follows at t; a bus created later starts with them."
  [ac i t v]
  (when-let [m (orbit-settings v)]
    (swap! (state ac) update-in [:settings i] merge m)
    (when-let [bus (get-in @(state ac) [:buses i])]
      (apply-to-bus! ac bus m t))))

(defn send!
  "Connect node (the event's output, after inserts and pan) to the room/delay
   inputs of its orbit through two per-event GainNodes at the send levels.
   Creates the orbit bus (output to out) on first use. → the send nodes."
  [ac t ^js node {:keys [room delay orbit]} out]
  (let [bus (orbit-bus ac orbit out)
        tap (fn [level in]
              (when (pos? level)
                (let [g (.createGain ac)]
                  (.setValueAtTime (.-gain g) level t)
                  (.connect node g)
                  (.connect g in)
                  g)))]
    (filterv some? [(tap room (:room-in bus)) (tap delay (:delay-in bus))])))

(defn reset-orbits!
  "Back to the default settings for every orbit of ac (called by (stop)).
   Tails already in the reverb or delay ring out."
  [ac]
  (when-let [s (and ac states (.get states ac))]
    (swap! s #(-> % (assoc :settings {}) (update :gen (fnil inc 0))))
    (let [now (.-currentTime ac)]
      (doseq [[_ bus] (:buses @s)]
        (doseq [p [(.-delayTime ^js (:delay bus)) (.-gain ^js (:fb bus))]]
          (.cancelScheduledValues p 0))
        (.setValueAtTime (.-delayTime ^js (:delay bus)) (:delay-time defaults) now)
        (.setValueAtTime (.-gain ^js (:fb bus)) (:delay-feedback defaults) now)
        (set-size! ac bus (size-step (:size defaults)))))))

(defn settings
  "Current settings of orbit i in ac (defaults merged) — for tests."
  [ac i]
  (settings-of ac i))
