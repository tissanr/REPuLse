(ns repulse.synth
  (:require [repulse.lisp.eval :as leval]
            [repulse.lisp.util :as u]
            [repulse.bus :as bus]
            [repulse.envelope :as env]))

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
  (let [build-fn (make-build-fn synth-name param-names body closed-env)]
    (swap! synth-defs assoc synth-name {:params param-names :build-fn build-fn})))

(defn lookup-synth [synth-name]
  (get @synth-defs synth-name))

;;; ── Signal parameters ───────────────────────────────────────────────
;;
;; UGen parameters (frequency, cutoff, level, delay time) take either a number
;; or a signal — an AudioNode such as (in :lfo) or (* 800 (in :lfo)). A signal
;; is connected to the AudioParam and the param's own value is zeroed, so the
;; signal alone sets it. Add a constant to centre it: (+ 1200 (* 800 (in :lfo))).

(defn signal?
  "True for an AudioNode usable as a UGen source or parameter."
  [x]
  (and (some? x) (fn? (.-connect x))))

(defn- set-param!
  [param v pname]
  (cond
    (signal? v)
    (do (.setValueAtTime param 0 0)
        (.connect v param))

    (and (number? v) (js/isFinite v))
    (.setValueAtTime param v 0)

    :else
    (throw (ex-info (str pname " must be a number or a signal such as (in :lfo), got "
                         (pr-str v))
                    {:type :eval-error}))))

(defn signal-mul
  "(* …) inside defsynth: plain numbers multiply as usual; with signals, returns
   a GainNode scaling the first signal by the numeric product. Further signals
   ring-modulate it (each drives the gain of another GainNode)."
  [ac args]
  (let [sigs (filter signal? args)
        k    (apply * (map u/->num (remove signal? args)))]
    (if (empty? sigs)
      k
      (let [g (.createGain ac)]
        (.setValueAtTime (.-gain g) k 0)
        (.connect (first sigs) g)
        (reduce (fn [prev s]
                  (let [rm (.createGain ac)]
                    (.setValueAtTime (.-gain rm) 0 0)
                    (.connect prev rm)
                    (.connect s (.-gain rm))
                    rm))
                g
                (rest sigs))))))

(defn signal-add
  "(+ …) inside defsynth: plain numbers add as usual; with signals, returns a
   GainNode summing them plus a ConstantSourceNode for the numeric offset.
   Returns [node started-sources] — the offset source must be stopped later."
  [ac t args]
  (let [sigs (filter signal? args)
        c    (apply + (map u/->num (remove signal? args)))]
    (if (empty? sigs)
      [c nil]
      (let [g (.createGain ac)]
        (.setValueAtTime (.-gain g) 1 0)
        (doseq [s sigs] (.connect s g))
        (if (zero? c)
          [g nil]
          (let [cs (.createConstantSource ac)]
            (.setValueAtTime (.-offset cs) c 0)
            (.connect cs g)
            (.start cs t)
            [g [cs]]))))))

;;; ── UGen constructors ────────────────────────────────────────────────
;;
;; Each oscillator/source UGen takes (ac t [freq]) and returns a started AudioNode.
;; Processor UGens take (ac cutoff-or-level source) and return the processor node.
;; Envelope UGens take (ac t attack ... source) and return {:node g :duration d}.

(defn sin-osc [ac t freq]
  (let [osc (.createOscillator ac)]
    (set! (.-type osc) "sine")
    (set-param! (.-frequency osc) freq "frequency")
    (.start osc t)
    osc))

(defn saw-osc [ac t freq]
  (let [osc (.createOscillator ac)]
    (set! (.-type osc) "sawtooth")
    (set-param! (.-frequency osc) freq "frequency")
    (.start osc t)
    osc))

(defn square-osc [ac t freq]
  (let [osc (.createOscillator ac)]
    (set! (.-type osc) "square")
    (set-param! (.-frequency osc) freq "frequency")
    (.start osc t)
    osc))

(defn tri-osc [ac t freq]
  (let [osc (.createOscillator ac)]
    (set! (.-type osc) "triangle")
    (set-param! (.-frequency osc) freq "frequency")
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
    (set-param! (.-frequency flt) cutoff "cutoff")
    (.connect source flt)
    flt))

(defn hpf-node
  "Highpass filter: (hpf cutoff source)."
  [ac cutoff source]
  (let [flt (.createBiquadFilter ac)]
    (set! (.-type flt) "highpass")
    (set-param! (.-frequency flt) cutoff "cutoff")
    (.connect source flt)
    flt))

(defn bpf-node
  "Bandpass filter: (bpf freq source)."
  [ac freq source]
  (let [flt (.createBiquadFilter ac)]
    (set! (.-type flt) "bandpass")
    (set-param! (.-frequency flt) freq "frequency")
    (.connect source flt)
    flt))

(defn gain-node
  "Static gain: (gain level source)."
  [ac level source]
  (let [g (.createGain ac)]
    (set-param! (.-gain g) level "level")
    (.connect source g)
    g))

(defn delay-line
  "Delay: (delay-node time source)."
  [ac time source]
  (let [d (.createDelay ac 5.0)]
    (set-param! (.-delayTime d) time "delay time")
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

;;; ── General envelope UGen ───────────────────────────────────────────

(defn- apply-env-automation!
  "Automate `param` according to envelope data, starting at audio time `t0`.
   Uses the pure envelope/segment-samples fn to build Float32Arrays for
   curve-based segments; direct Web Audio ramp methods for :lin/:exp/:step."
  [param t0 levels times curves]
  (let [n-segs (count times)]
    (.setValueAtTime param (float (max 0.0001 (first levels))) t0)
    (loop [i      0
           cur-t  t0]
      (when (< i n-segs)
        (let [from-val (nth levels i)
              to-val   (nth levels (inc i))
              seg-time (nth times i)
              curve    (nth curves i :lin)
              end-t    (+ cur-t seg-time)]
          (case curve
            :lin  (.linearRampToValueAtTime param (float to-val) end-t)
            :exp  (.exponentialRampToValueAtTime param (float (max 0.0001 to-val)) end-t)
            :step (.setValueAtTime param (float to-val) end-t)
            ;; Curve-based: compute sample array and use setValueCurveAtTime
            (let [samples (env/segment-samples from-val to-val curve 32)
                  arr     (js/Float32Array. (count samples))]
              (dotimes [j (count samples)]
                (aset arr j (float (nth samples j))))
              (.setValueCurveAtTime param arr cur-t seg-time)))
          (recur (inc i) end-t))))))

(defn env-gen-node
  "General envelope: (env-gen env-data source).
   env-data is a map produced by (env levels times curves?).
   Applies the envelope to source via a GainNode; returns {:node g :duration d}."
  [ac t env-data source]
  (let [{:keys [levels times curves]} env-data
        g      (.createGain ac)
        curves (or curves (vec (repeat (count times) :lin)))]
    (apply-env-automation! (.-gain g) t levels times curves)
    (.connect source g)
    {:node g :duration (env/total-duration env-data)}))

;;; ── Bus UGens ───────────────────────────────────────────────────────

(defn out-node
  "Write a signal to a named bus.
   synth-name tracks the sending synth so re-triggers replace (not accumulate)
   the previous oscillator on that bus.
   Returns {:node nil :bus-writer true} — no audio output to the track."
  [synth-name bus-name signal]
  (let [kw (if (keyword? bus-name) bus-name (keyword bus-name))]
    (if (bus/get-bus-node kw)
      (bus/replace-synth-writer! synth-name kw signal)
      (js/console.warn "[REPuLse] bus" (str kw) "not found — declare it with (bus :name) first")))
  {:node nil :bus-writer true})

(defn in-node
  "Read from a named bus, returning its AudioNode as a UGen source.
   If the bus does not exist, logs a warning and returns a silent GainNode."
  [ac bus-name]
  (let [kw (if (keyword? bus-name) bus-name (keyword bus-name))]
    (or (bus/get-bus-node kw)
        (do
          (js/console.warn "[REPuLse] bus" (str kw) "not found — returning silence")
          (let [g (.createGain ac)]
            (.setValueAtTime (.-gain g) 0.0 0)
            g)))))

(defn kr-node
  "Control-rate pass-through.
   Web Audio does not expose control-rate processing to JS, so this is a
   transparent identity wrapper — the signal is returned unchanged."
  [_rate signal]
  signal)

;;; ── Build-fn factory ─────────────────────────────────────────────────

(defn- unwrap [x] (leval/unwrap x))

(defn- make-build-fn
  "Creates the Web Audio graph builder function for a defsynth.
   When called with [ac t param-map] it evaluates the synth body
   with UGen functions and param bindings in scope, and returns
   [result release!] — release! detaches the voice from shared bus nodes
   and stops sources started for signal arithmetic.
   synth-name is used by out-node for replace-on-retrigger tracking."
  [synth-name param-names body closed-env]
  (fn [ac t param-map]
    (let [releases (atom [])
          on-release! (fn [f] (swap! releases conj f))
          ;; Bind param values from the event's parameter map
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
                    ;; Signal-aware arithmetic: (* 800 (in :lfo)), (+ 1200 sig)
                    "*"          (fn [& args]        (signal-mul ac (map unwrap args)))
                    "+"          (fn [& args]
                                   (let [[node started] (signal-add ac t (map unwrap args))]
                                     (doseq [s started]
                                       (on-release! #(try (.stop s) (catch :default _))))
                                     node))
                    ;; Envelope UGens: source is last arg — use with ->>
                    "env-perc"   (fn [atk dec src]   (env-perc-node ac t (unwrap atk) (unwrap dec) (unwrap src)))
                    "env-asr"    (fn [atk sus rel src] (env-asr-node ac t (unwrap atk) (unwrap sus) (unwrap rel) (unwrap src)))
                    ;; General envelope UGen
                    "env-gen"    (fn [env-data src]  (env-gen-node ac t (unwrap env-data) (unwrap src)))
                    ;; Bus UGens
                    "out"        (fn [bus-name-v signal-v]
                                   (out-node synth-name (unwrap bus-name-v) (unwrap signal-v)))
                    ;; Each voice reads through its own tap so release! can cut it
                    ;; off the long-lived bus node without touching other voices.
                    "in"         (fn [bus-name-v]
                                   (let [src (in-node ac (unwrap bus-name-v))
                                         tap (.createGain ac)]
                                     (.connect src tap)
                                     (on-release! #(try (.disconnect src tap) (catch :default _)))
                                     tap))
                    "kr"         (fn [rate-v signal-v]
                                   (kr-node (unwrap rate-v) (unwrap signal-v)))}
          local  (merge closed-env param-bindings ugen-env)
          result (last (map #(leval/eval-form % local) body))]
      [result (fn [] (doseq [f @releases] (f)))])))

;;; ── Synth playback ───────────────────────────────────────────────────

(defn play-synth!
  "Instantiate a user-defined synth at scheduled time t.
   dest is the AudioNode to connect to (e.g. master-gain or track gain node).
   Schedules disconnection after the envelope completes."
  [ac t synth-def param-map dest]
  (let [{:keys [build-fn]} synth-def
        [result release!] (build-fn ac t param-map)
        ;; result is either:
        ;;   AudioNode               — no envelope, use 1.5 s default duration
        ;;   {:node AudioNode :duration N} — from env-perc / env-asr / env-gen
        ;;   {:node nil :bus-writer true}  — synth writes to bus, no audio output
        finish! (fn [out-n dur]
                  (.connect out-n dest)
                  (js/setTimeout
                    (fn []
                      (try (.disconnect out-n) (catch :default _))
                      (release!))
                    (* (+ dur 0.1) 1000)))]
    (cond
      ;; Bus writer — no audio connection, oscillator keeps running in bus
      (and result (map? result) (:bus-writer result))
      nil

      ;; Envelope result
      (and result (map? result) (:node result))
      (finish! (:node result) (:duration result))

      ;; Raw AudioNode — no envelope
      result
      (finish! result 1.5))))
