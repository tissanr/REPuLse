(ns repulse.audio
  (:require [repulse.core :as core]
            [repulse.samples :as samples]
            ["repulse-audio" :as wasm]))

;; Web Audio API scheduler
;; Based on Chris Wilson's "A Tale of Two Clocks"

(def ctx (atom nil))

;; WASM AudioEngine instance — nil until loaded
(defonce wasm-engine (atom nil))

(defn- init-wasm!
  "Load the WASM synthesis module. Falls back to JS synthesis on failure."
  [ac]
  (-> ((.-default wasm))
      (.then (fn [_]
               (reset! wasm-engine (wasm/AudioEngine. ac))
               (js/console.log "[REPuLse] audio backend: wasm")))
      (.catch (fn [e]
                (js/console.warn "[REPuLse] audio backend: js synthesis (WASM unavailable)" e)))))

(defn- make-audio-context []
  ;; Safari < 14.1 requires the webkit prefix
  (let [ctor (or (.-AudioContext js/window)
                 (.-webkitAudioContext js/window))]
    (new ctor)))

(defn get-ctx []
  (or @ctx
      (let [c (make-audio-context)]
        (reset! ctx c)
        (init-wasm! c)
        c)))

;;; Synthesized voices (JS fallback when WASM not available)

(defn- make-kick [ac t]
  (let [osc  (.createOscillator ac)
        gain (.createGain ac)]
    (set! (.-type osc) "sine")
    (.setValueAtTime (.-frequency osc) 150 t)
    (.exponentialRampToValueAtTime (.-frequency osc) 40 (+ t 0.06))
    (.setValueAtTime (.-gain gain) 1.0 t)
    (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t 0.4))
    (.connect osc gain)
    (.connect gain (.-destination ac))
    (.start osc t)
    (.stop osc (+ t 0.4))))

(defn- make-snare [ac t]
  (let [buf-size 4096
        buf  (.createBuffer ac 1 buf-size (.-sampleRate ac))
        data (.getChannelData buf 0)
        _    (dotimes [i buf-size]
               (aset data i (- (* 2 (Math/random)) 1)))
        src  (.createBufferSource ac)
        bpf  (.createBiquadFilter ac)
        gain (.createGain ac)]
    (set! (.-buffer src) buf)
    (set! (.-type bpf) "bandpass")
    (.setValueAtTime (.-frequency bpf) 200 t)
    (.setValueAtTime (.-gain gain) 0.9 t)
    (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t 0.2))
    (.connect src bpf)
    (.connect bpf gain)
    (.connect gain (.-destination ac))
    (.start src t)
    (.stop src (+ t 0.2))))

(defn- make-hihat [ac t]
  (let [buf-size 2048
        buf  (.createBuffer ac 1 buf-size (.-sampleRate ac))
        data (.getChannelData buf 0)
        _    (dotimes [i buf-size]
               (aset data i (- (* 2 (Math/random)) 1)))
        src  (.createBufferSource ac)
        hpf  (.createBiquadFilter ac)
        gain (.createGain ac)]
    (set! (.-buffer src) buf)
    (set! (.-type hpf) "highpass")
    (.setValueAtTime (.-frequency hpf) 8000 t)
    (.setValueAtTime (.-gain gain) 0.5 t)
    (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t 0.045))
    (.connect src hpf)
    (.connect hpf gain)
    (.connect gain (.-destination ac))
    (.start src t)
    (.stop src (+ t 0.045))))

(defn- make-sine [ac t freq]
  (let [osc  (.createOscillator ac)
        gain (.createGain ac)]
    (set! (.-type osc) "sine")
    (.setValueAtTime (.-frequency osc) freq t)
    (.setValueAtTime (.-gain gain) 0.5 t)
    (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t 0.3))
    (.connect osc gain)
    (.connect gain (.-destination ac))
    (.start osc t)
    (.stop osc (+ t 0.3))))

;;; Synthesis dispatch

(defn- wasm-trigger!
  "Schedule a sound via WASM. Returns true if WASM handled it."
  [value t]
  (when-let [eng @wasm-engine]
    (.trigger ^js eng value t)
    true))

(defn- js-synth
  "JS synthesis fallback."
  [ac t value]
  (case value
    :bd (make-kick ac t)
    :sd (make-snare ac t)
    :hh (make-hihat ac t)
    (make-sine ac t 440)))

(defn play-event [ac t value]
  (cond
    ;; Silence / rest
    (= value :_) nil

    ;; Map {:bank :bd :n 2} from (sound :bd 2) — always use samples
    (and (map? value) (:bank value))
    (let [{:keys [bank n]} value]
      (if (samples/has-bank? bank)
        (samples/play! ac t bank n)
        (or (wasm-trigger! (name bank) t)
            (make-sine ac t 440))))

    ;; Keyword — sample registry first, WASM synth second, JS synth fallback
    (keyword? value)
    (cond
      (samples/has-bank? value) (samples/play! ac t value 0)
      :else (or (wasm-trigger! (name value) t)
                (js-synth ac t value)))

    ;; Number — frequency in Hz
    (number? value)
    (or (wasm-trigger! (str value) t)
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
         :on-beat     nil}))

(defn set-bpm!
  "Set the tempo in BPM. One cycle = one bar (4 beats)."
  [bpm]
  (swap! scheduler-state assoc :cycle-dur (/ 240.0 bpm)))

(defn schedule-cycle! [ac state cycle]
  (let [{:keys [pattern cycle-dur on-beat]} state
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

(defn start! [pattern on-beat-fn]
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
             :on-beat     on-beat-fn))
    (let [id (js/setInterval tick! 25)]
      (swap! scheduler-state assoc :interval-id id))
    (tick!)))

(defn stop! []
  (when-let [eng @wasm-engine]
    (.stop_all ^js eng))
  (when-let [id (:interval-id @scheduler-state)]
    (js/clearInterval id))
  (swap! scheduler-state assoc
         :playing?     false
         :interval-id  nil
         :pattern      nil))

(defn playing? []
  (:playing? @scheduler-state))
