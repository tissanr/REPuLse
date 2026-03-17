(ns repulse.audio
  (:require [repulse.core :as core]
            [repulse.theory :as theory]
            [repulse.samples :as samples]))

;; Web Audio API scheduler
;; Based on Chris Wilson's "A Tale of Two Clocks"

(def ctx (atom nil))

;; AudioWorklet node — nil until loaded
(defonce worklet-node (atom nil))
(defonce worklet-ready? (atom false))

;; Master signal chain: sources → master-gain → analyser → destination
(defonce master-gain   (atom nil))
(defonce analyser-node (atom nil))

(defn- build-master-chain! [ac]
  (let [gain (doto (.createGain ac)
               (-> .-gain (.setValueAtTime 1.0 (.-currentTime ac))))
        anl  (doto (.createAnalyser ac)
               (aset "fftSize" 2048)
               (aset "smoothingTimeConstant" 0.8))]
    (.connect gain anl)
    (.connect anl (.-destination ac))
    (reset! master-gain gain)
    (reset! analyser-node anl)))

(defn- init-worklet!
  "Register the AudioWorkletProcessor and load WASM inside it.
   Falls back to JS synthesis if AudioWorklet is unavailable."
  [ac]
  (if-let [worklet (.-audioWorklet ac)]
    (-> (.addModule worklet "/worklet.js")
        (.then (fn []
                 (let [node (js/AudioWorkletNode. ac "repulse-processor"
                                                  #js {:outputChannelCount #js [2]})]
                   (.connect node @master-gain)
                   (reset! worklet-node node)
                   (set! (.. node -port -onmessage)
                         (fn [e]
                           (let [d (.-data e)]
                             (condp = (.-type d)
                               "ready" (do (reset! worklet-ready? true)
                                           (js/console.log "[REPuLse] audio backend: audioworklet+wasm"))
                               "error" (js/console.warn "[REPuLse] Worklet WASM error:" (.-message d))
                               nil))))
                   ;; Compile WASM on the main thread — dynamic import() is banned in
                   ;; AudioWorkletGlobalScope. Send the compiled WebAssembly.Module
                   ;; (serialisable via structured clone) to the worklet instead.
                   (-> (.compileStreaming js/WebAssembly (js/fetch "/repulse_audio_bg.wasm"))
                       (.then (fn [wasm-module]
                                (.. node -port
                                    (postMessage #js {:type       "init"
                                                      :wasmModule wasm-module}))))
                       (.catch (fn [e]
                                 (js/console.warn "[REPuLse] WASM compile failed:" e)))))))
        (.catch (fn [e]
                  (js/console.warn "[REPuLse] audio backend: clojurescript synthesis (Worklet load failed)" e))))
    (js/console.warn "[REPuLse] audio backend: clojurescript synthesis (AudioWorklet not supported)")))

(defn- make-audio-context []
  ;; Safari < 14.1 requires the webkit prefix
  (let [ctor (or (.-AudioContext js/window)
                 (.-webkitAudioContext js/window))]
    (new ctor)))

(defn get-ctx []
  (or @ctx
      (let [c (make-audio-context)]
        (reset! ctx c)
        (build-master-chain! c)
        (init-worklet! c)
        c)))

;;; Synthesized voices (JS fallback when AudioWorklet is unavailable)

(defn- output-node [ac]
  (or @master-gain (.-destination ac)))

(defn- make-kick [ac t amp pan]
  (let [osc    (.createOscillator ac)
        gain   (.createGain ac)
        panner (.createStereoPanner ac)
        pk     (float amp)]
    (set! (.-type osc) "sine")
    (.setValueAtTime (.-frequency osc) 150 t)
    (.exponentialRampToValueAtTime (.-frequency osc) 40 (+ t 0.06))
    (.setValueAtTime (.-gain gain) pk t)
    (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t 0.4))
    (.setValueAtTime (.-pan panner) (float pan) t)
    (.connect osc gain)
    (.connect gain panner)
    (.connect panner (output-node ac))
    (.start osc t)
    (.stop osc (+ t 0.4))))

(defn- make-snare [ac t amp pan]
  (let [buf-size 4096
        buf  (.createBuffer ac 1 buf-size (.-sampleRate ac))
        data (.getChannelData buf 0)
        _    (dotimes [i buf-size]
               (aset data i (- (* 2 (Math/random)) 1)))
        src  (.createBufferSource ac)
        bpf  (.createBiquadFilter ac)
        gain (.createGain ac)
        panner (.createStereoPanner ac)
        pk   (* 0.9 (float amp))]
    (set! (.-buffer src) buf)
    (set! (.-type bpf) "bandpass")
    (.setValueAtTime (.-frequency bpf) 200 t)
    (.setValueAtTime (.-gain gain) pk t)
    (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t 0.2))
    (.setValueAtTime (.-pan panner) (float pan) t)
    (.connect src bpf)
    (.connect bpf gain)
    (.connect gain panner)
    (.connect panner (output-node ac))
    (.start src t)
    (.stop src (+ t 0.2))))

(defn- make-hihat [ac t amp pan]
  (let [buf-size 2048
        buf  (.createBuffer ac 1 buf-size (.-sampleRate ac))
        data (.getChannelData buf 0)
        _    (dotimes [i buf-size]
               (aset data i (- (* 2 (Math/random)) 1)))
        src  (.createBufferSource ac)
        hpf  (.createBiquadFilter ac)
        gain (.createGain ac)
        panner (.createStereoPanner ac)
        pk   (* 0.5 (float amp))]
    (set! (.-buffer src) buf)
    (set! (.-type hpf) "highpass")
    (.setValueAtTime (.-frequency hpf) 8000 t)
    (.setValueAtTime (.-gain gain) pk t)
    (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t 0.045))
    (.setValueAtTime (.-pan panner) (float pan) t)
    (.connect src hpf)
    (.connect hpf gain)
    (.connect gain panner)
    (.connect panner (output-node ac))
    (.start src t)
    (.stop src (+ t 0.045))))

(defn- make-sine
  "JS-synthesis fallback for when the AudioWorklet/WASM is unavailable.
   dur     — decay duration in seconds (default 1.5)
   amp     — peak amplitude 0.0–1.0 (default 1.0; scaled by 0.5 internally)
   attack  — linear ramp-up time in seconds (default 0.001 = instant)
   pan     — stereo position -1.0 (left) to 1.0 (right), default 0.0"
  ([ac t freq] (make-sine ac t freq 1.5 1.0 0.001 0.0))
  ([ac t freq dur] (make-sine ac t freq dur 1.0 0.001 0.0))
  ([ac t freq dur amp] (make-sine ac t freq dur amp 0.001 0.0))
  ([ac t freq dur amp attack] (make-sine ac t freq dur amp attack 0.0))
  ([ac t freq dur amp attack pan]
   (let [osc    (.createOscillator ac)
         gain   (.createGain ac)
         panner (.createStereoPanner ac)
         peak   (* 0.5 (float amp))    ; headroom for polyphony
         atk    (max 0.001 (float attack))
         stop-t (+ t atk (float dur))]
     (set! (.-type osc) "sine")
     (.setValueAtTime (.-frequency osc) freq t)
     (.setValueAtTime (.-gain gain) 0.0001 t)
     (.linearRampToValueAtTime (.-gain gain) peak (+ t atk))
     (.exponentialRampToValueAtTime (.-gain gain) 0.0001 stop-t)
     (.setValueAtTime (.-pan panner) (float pan) t)
     (.connect osc gain)
     (.connect gain panner)
     (.connect panner (output-node ac))
     (.start osc t)
     (.stop osc stop-t))))

;;; Synthesis dispatch

(defn- worklet-trigger!
  "Send a trigger message to the AudioWorklet. Returns true if worklet is ready."
  [value t]
  (when (and @worklet-ready? @worklet-node)
    (.. @worklet-node -port
        (postMessage #js {:type "trigger" :value value :time t}))
    true))

(defn- worklet-trigger-v2!
  "Send a trigger_v2 message with explicit synthesis parameters. Returns true if ready."
  [value t amp attack decay pan]
  (when (and @worklet-ready? @worklet-node)
    (.. @worklet-node -port
        (postMessage #js {:type "trigger_v2" :value value :time t
                          :amp amp :attack attack :decay decay :pan pan}))
    true))

(defn- js-synth
  "JS synthesis fallback — used when AudioWorklet is unavailable."
  ([ac t value] (js-synth ac t value 1.0 0.0))
  ([ac t value amp] (js-synth ac t value amp 0.0))
  ([ac t value amp pan]
   (case value
     :bd (make-kick ac t amp pan)
     :sd (make-snare ac t amp pan)
     :hh (make-hihat ac t amp pan)
     (make-sine ac t 440 1.5 amp 0.001 pan))))

(defn play-event [ac t value]
  (cond
    ;; Silence / rest
    (= value :_) nil

    ;; Parameter map {:note … :amp … :attack … :decay … :pan …}
    ;; from amp/attack/decay/release/pan transformers
    (and (map? value) (:note value))
    (let [note     (:note value)
          amp-v    (float (:amp value 1.0))
          attack-v (float (:attack value 0.001))
          decay-v  (float (:decay value 1.5))
          pan-v    (float (:pan value 0.0))]
      (cond
        (= note :_) nil
        (keyword? note)
        (if (theory/note-keyword? note)
          (let [hz (theory/note->hz note)]
            (or (worklet-trigger-v2! (str hz) t amp-v attack-v decay-v pan-v)
                (make-sine ac t hz decay-v amp-v attack-v pan-v)))
          (let [resolved (samples/resolve-keyword note)]
            (cond
              (samples/has-bank? resolved) (samples/play! ac t resolved 0 amp-v pan-v)
              :else (or (worklet-trigger-v2! (name note) t amp-v attack-v decay-v pan-v)
                        (js-synth ac t note amp-v pan-v)))))
        (number? note)
        (or (worklet-trigger-v2! (str note) t amp-v attack-v decay-v pan-v)
            (make-sine ac t note decay-v amp-v attack-v pan-v))))

    ;; Map {:bank :bd :n 2} from (sound :bd 2) — always use samples
    (and (map? value) (:bank value))
    (let [{:keys [bank n]} value]
      (if (samples/has-bank? bank)
        (samples/play! ac t bank n)
        (or (worklet-trigger! (name bank) t)
            (make-sine ac t 440))))

    ;; Keyword — note name (c4, eb3, …) → Hz tone, or bank prefix → sample → synth fallback
    (keyword? value)
    (if (theory/note-keyword? value)
      (let [hz (theory/note->hz value)]
        (or (worklet-trigger! (str hz) t)
            (make-sine ac t hz)))
      (let [resolved (samples/resolve-keyword value)]
        (cond
          (samples/has-bank? resolved) (samples/play! ac t resolved 0)
          :else (or (worklet-trigger! (name value) t)
                    (js-synth ac t value)))))

    ;; Number — frequency in Hz
    (number? value)
    (or (worklet-trigger! (str value) t)
        (make-sine ac t value))

    :else (make-sine ac t 440)))

;;; Scheduler state

(def scheduler-state
  (atom {:playing?    false
         :pattern     nil
         :cycle       0
         :cycle-dur   2.0   ; seconds per cycle — 120 BPM, 1 cycle = 1 bar (4 beats)
         :lookahead   0.2   ; look-ahead in seconds
         :interval-id nil
         :on-beat     nil
         :on-event    nil}))  ; (fn [{:keys [from to]}]) called per event at event time

(defn set-bpm!
  "Set the tempo in BPM. One cycle = one bar (4 beats)."
  [bpm]
  (swap! scheduler-state assoc :cycle-dur (/ 240.0 bpm)))

(defn schedule-cycle! [ac state cycle]
  (let [{:keys [pattern cycle-dur on-beat on-event]} state
        sp {:start [cycle 1] :end [(inc cycle) 1]}]
    (when pattern
      (let [evs (core/query pattern sp)]
        (doseq [ev evs]
          (let [part-start (core/rat->float (:start (:part ev)))
                cycle-audio-start (* cycle cycle-dur)
                event-offset (* (- part-start cycle) cycle-dur)
                t (+ cycle-audio-start event-offset)]
            (when (> t (.-currentTime ac))
              (play-event ac t (:value ev))
              (when (and on-event (:source ev))
                (let [delay-ms (max 0 (* 1000 (- t (.-currentTime ac))))]
                  (js/setTimeout #(on-event (:source ev)) delay-ms)))
              (when on-beat
                (let [delay-ms (* 1000 (- t (.-currentTime ac)))]
                  (js/setTimeout on-beat delay-ms))))))))))

(defn tick! []
  (let [ac    (get-ctx)
        state @scheduler-state]
    (when (:playing? state)
      (let [{:keys [cycle cycle-dur lookahead]} state
            now         (.-currentTime ac)
            cycle-start (* cycle cycle-dur)]
        (when (< (- cycle-start now) lookahead)
          (schedule-cycle! ac state cycle)
          (swap! scheduler-state update :cycle inc))))))

(defn start! [pattern on-beat-fn on-event-fn]
  (let [ac (get-ctx)]
    ;; Always call resume — Safari suspends the context even when state = "running"
    (.resume ac)
    (let [now       (.-currentTime ac)
          cycle-dur (:cycle-dur @scheduler-state)
          ;; Use floor so we start at the current cycle, not next one.
          ;; tick! will skip events already in the past via the (> t now) guard.
          start-cycle (int (Math/floor (/ now cycle-dur)))]
      (swap! scheduler-state assoc
             :playing?    true
             :pattern     pattern
             :cycle       start-cycle
             :on-beat     on-beat-fn
             :on-event    on-event-fn))
    (let [id (js/setInterval tick! 25)]
      (swap! scheduler-state assoc :interval-id id))
    (tick!)))

(defn stop! []
  (when-let [node @worklet-node]
    (.. node -port (postMessage #js {:type "stop"})))
  (when-let [id (:interval-id @scheduler-state)]
    (js/clearInterval id))
  (swap! scheduler-state assoc
         :playing?     false
         :interval-id  nil
         :pattern      nil))

(defn playing? []
  (:playing? @scheduler-state))
