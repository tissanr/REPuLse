(ns repulse.synth
  (:require [repulse.lisp.eval :as leval]))

(declare make-build-fn)

;;; ── Synth definition registry ───────────────────────────────────────

;; Stores registered synth definitions:
;;   keyword → {:params [param-name-strs] :build-fn (fn [ac t param-map] → AudioNode)}
(defonce synth-defs (atom {}))

(defn register-synth!
  "Called by defsynth (via app-level callback) to register a synth.
   param-names is a vector of strings. body is a vector of AST forms.
   closed-env is the eval env at defsynth time."
  [synth-name param-names body closed-env]
  (let [build-fn (make-build-fn param-names body closed-env)]
    (swap! synth-defs assoc synth-name {:params param-names :build-fn build-fn})))

(defn lookup-synth [synth-name]
  (get @synth-defs synth-name))

;;; ── UGen constructors ────────────────────────────────────────────────
;;
;; Each oscillator/source UGen takes (ac t [freq]) and returns a started AudioNode.
;; Processor UGens take (ac cutoff-or-level source) and return the processor node.
;; Envelope UGens take (ac t attack ... source) and return {:node g :duration d}.

(defn sin-osc [ac t freq]
  (let [osc (.createOscillator ac)]
    (set! (.-type osc) "sine")
    (.setValueAtTime (.-frequency osc) freq 0)
    (.start osc t)
    osc))

(defn saw-osc [ac t freq]
  (let [osc (.createOscillator ac)]
    (set! (.-type osc) "sawtooth")
    (.setValueAtTime (.-frequency osc) freq 0)
    (.start osc t)
    osc))

(defn square-osc [ac t freq]
  (let [osc (.createOscillator ac)]
    (set! (.-type osc) "square")
    (.setValueAtTime (.-frequency osc) freq 0)
    (.start osc t)
    osc))

(defn tri-osc [ac t freq]
  (let [osc (.createOscillator ac)]
    (set! (.-type osc) "triangle")
    (.setValueAtTime (.-frequency osc) freq 0)
    (.start osc t)
    osc))

(defn noise-src [ac t]
  (let [buf-size 8192
        buf  (.createBuffer ac 1 buf-size (.-sampleRate ac))
        data (.getChannelData buf 0)
        _    (dotimes [i buf-size]
               (aset data i (- (* 2 (Math/random)) 1)))
        src  (.createBufferSource ac)]
    (set! (.-buffer src) buf)
    (.start src t)
    src))

;; Processor UGens — take source as last arg, connect, return processor node.

(defn lpf-node
  "Lowpass filter: (lpf cutoff source) — source-last for ->> threading."
  [ac cutoff source]
  (let [flt (.createBiquadFilter ac)]
    (set! (.-type flt) "lowpass")
    (.setValueAtTime (.-frequency flt) cutoff 0)
    (.connect source flt)
    flt))

(defn hpf-node
  "Highpass filter: (hpf cutoff source)."
  [ac cutoff source]
  (let [flt (.createBiquadFilter ac)]
    (set! (.-type flt) "highpass")
    (.setValueAtTime (.-frequency flt) cutoff 0)
    (.connect source flt)
    flt))

(defn bpf-node
  "Bandpass filter: (bpf freq source)."
  [ac freq source]
  (let [flt (.createBiquadFilter ac)]
    (set! (.-type flt) "bandpass")
    (.setValueAtTime (.-frequency flt) freq 0)
    (.connect source flt)
    flt))

(defn gain-node
  "Static gain: (gain level source)."
  [ac level source]
  (let [g (.createGain ac)]
    (.setValueAtTime (.-gain g) level 0)
    (.connect source g)
    g))

(defn delay-line
  "Delay: (delay-node time source)."
  [ac time source]
  (let [d (.createDelay ac 5.0)]
    (.setValueAtTime (.-delayTime d) time 0)
    (.connect source d)
    d))

(defn mix-node
  "Mix two signals: (mix a b) — connects both into a single GainNode."
  [ac source-a source-b]
  (let [g (.createGain ac)]
    (.setValueAtTime (.-gain g) 1.0 0)
    (.connect source-a g)
    (.connect source-b g)
    g))

;; Envelope UGens — wrap source in a GainNode, return {:node g :duration d}.

(defn env-perc-node
  "Percussive envelope: (env-perc attack decay source)."
  [ac t attack decay source]
  (let [g   (.createGain ac)
        atk (max 0.001 attack)]
    (.setValueAtTime (.-gain g) 0.0001 t)
    (.linearRampToValueAtTime (.-gain g) 1.0 (+ t atk))
    (.exponentialRampToValueAtTime (.-gain g) 0.0001 (+ t atk decay))
    (.connect source g)
    {:node g :duration (+ atk decay)}))

(defn env-asr-node
  "ASR envelope: (env-asr attack sustain release source).
   Sustain hold = 1.0 s. Returns {:node g :duration (atk + 1.0 + rel)}."
  [ac t attack sustain release source]
  (let [g            (.createGain ac)
        atk          (max 0.001 attack)
        rel          (max 0.001 release)
        sustain-hold 1.0
        total        (+ atk sustain-hold rel)]
    (.setValueAtTime (.-gain g) 0.0001 t)
    (.linearRampToValueAtTime (.-gain g) sustain (+ t atk))
    (.setValueAtTime (.-gain g) sustain (+ t atk sustain-hold))
    (.exponentialRampToValueAtTime (.-gain g) 0.0001 (+ t total))
    (.connect source g)
    {:node g :duration total}))

;;; ── Build-fn factory ─────────────────────────────────────────────────

(defn- unwrap [x] (leval/unwrap x))

(defn- make-build-fn
  "Creates the Web Audio graph builder function for a defsynth.
   When called with [ac t param-map] it evaluates the synth body
   with UGen functions and param bindings in scope."
  [param-names body closed-env]
  (fn [ac t param-map]
    (let [;; Bind param values from the event's parameter map
          param-bindings (zipmap param-names
                                 (map #(get param-map (keyword %)) param-names))
          ;; UGen functions capture ac and t from the call site
          ugen-env {"sin"        (fn [freq]          (sin-osc    ac t (unwrap freq)))
                    "saw"        (fn [freq]          (saw-osc    ac t (unwrap freq)))
                    "square"     (fn [freq]          (square-osc ac t (unwrap freq)))
                    "tri"        (fn [freq]          (tri-osc    ac t (unwrap freq)))
                    "noise"      (fn []              (noise-src  ac t))
                    ;; Processor UGens: source is last arg — use with ->>
                    "lpf"        (fn [cutoff src]    (lpf-node   ac (unwrap cutoff) (unwrap src)))
                    "hpf"        (fn [cutoff src]    (hpf-node   ac (unwrap cutoff) (unwrap src)))
                    "bpf"        (fn [freq src]      (bpf-node   ac (unwrap freq)   (unwrap src)))
                    "gain"       (fn [level src]     (gain-node  ac (unwrap level)  (unwrap src)))
                    "delay-node" (fn [time src]      (delay-line ac (unwrap time)   (unwrap src)))
                    "mix"        (fn [a b]           (mix-node   ac (unwrap a)      (unwrap b)))
                    ;; Envelope UGens: source is last arg — use with ->>
                    "env-perc"   (fn [atk dec src]   (env-perc-node ac t (unwrap atk) (unwrap dec) (unwrap src)))
                    "env-asr"    (fn [atk sus rel src] (env-asr-node ac t (unwrap atk) (unwrap sus) (unwrap rel) (unwrap src)))}
          local (merge closed-env param-bindings ugen-env)]
      (last (map #(leval/eval-form % local) body)))))

;;; ── Synth playback ───────────────────────────────────────────────────

(defn play-synth!
  "Instantiate a user-defined synth at scheduled time t.
   dest is the AudioNode to connect to (e.g. master-gain or track gain node).
   Schedules disconnection after the envelope completes."
  [ac t synth-def param-map dest]
  (let [{:keys [build-fn]} synth-def
        result   (build-fn ac t param-map)
        ;; result is either:
        ;;   AudioNode               — no envelope, use 1.5 s default duration
        ;;   {:node AudioNode :duration N} — from env-perc / env-asr
        [out-node duration] (if (and result (map? result) (:node result))
                              [(:node result) (:duration result)]
                              [result 1.5])]
    (when out-node
      (.connect out-node dest)
      (js/setTimeout
        (fn [] (try (.disconnect out-node) (catch :default _)))
        (* (+ duration 0.1) 1000)))))
