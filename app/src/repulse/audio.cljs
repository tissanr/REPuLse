(ns repulse.audio
  (:require [repulse.core :as core]
            [repulse.samples :as samples]))

;; Web Audio API scheduler
;; Based on Chris Wilson's "A Tale of Two Clocks"

(def ctx (atom nil))

(defn get-ctx []
  (or @ctx
      (let [c (js/AudioContext.)]
        (reset! ctx c)
        c)))

;;; Simple synth voices

(defn make-kick [ac t]
  (let [osc  (.createOscillator ac)
        gain (.createGain ac)]
    (set! (.-type osc) "sine")
    (.setValueAtTime (.-frequency osc) 150 t)
    (.exponentialRampToValueAtTime (.-frequency osc) 0.001 (+ t 0.5))
    (.setValueAtTime (.-gain gain) 1.0 t)
    (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t 0.5))
    (.connect osc gain)
    (.connect gain (.-destination ac))
    (.start osc t)
    (.stop osc (+ t 0.5))))

(defn make-snare [ac t]
  (let [buf-size 4096
        buf (.createBuffer ac 1 buf-size (.-sampleRate ac))
        data (.getChannelData buf 0)
        _    (dotimes [i buf-size]
               (aset data i (- (* 2 (Math/random)) 1)))
        src  (.createBufferSource ac)
        bpf  (.createBiquadFilter ac)
        gain (.createGain ac)]
    (set! (.-buffer src) buf)
    (set! (.-type bpf) "bandpass")
    (.setValueAtTime (.-frequency bpf) 3000 t)
    (.setValueAtTime (.-gain gain) 0.8 t)
    (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t 0.2))
    (.connect src bpf)
    (.connect bpf gain)
    (.connect gain (.-destination ac))
    (.start src t)
    (.stop src (+ t 0.2))))

(defn make-hihat [ac t]
  (let [buf-size 2048
        buf (.createBuffer ac 1 buf-size (.-sampleRate ac))
        data (.getChannelData buf 0)
        _    (dotimes [i buf-size]
               (aset data i (- (* 2 (Math/random)) 1)))
        src  (.createBufferSource ac)
        hpf  (.createBiquadFilter ac)
        gain (.createGain ac)]
    (set! (.-buffer src) buf)
    (set! (.-type hpf) "highpass")
    (.setValueAtTime (.-frequency hpf) 7000 t)
    (.setValueAtTime (.-gain gain) 0.4 t)
    (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t 0.05))
    (.connect src hpf)
    (.connect hpf gain)
    (.connect gain (.-destination ac))
    (.start src t)
    (.stop src (+ t 0.05))))

(defn make-sine [ac t freq]
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

(defn play-event [ac t value]
  (cond
    ;; Silence / rest
    (= value :_)    nil

    ;; Map with :bank key — e.g. {:bank :bd :n 2} from (sound :bd 2)
    (and (map? value) (:bank value))
    (let [{:keys [bank n]} value]
      (if (samples/has-bank? bank)
        (samples/play! ac t bank n)
        (make-sine ac t 440)))

    ;; Keyword — look up in sample registry first, fall back to synth
    (keyword? value)
    (cond
      (samples/has-bank? value) (samples/play! ac t value 0)
      (= value :bd)             (make-kick ac t)
      (= value :sd)             (make-snare ac t)
      (= value :hh)             (make-hihat ac t)
      :else                     (make-sine ac t 440))

    (number? value) (make-sine ac t value)
    :else           (make-sine ac t 440)))

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
                ;; Convert pattern time to audio context time
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
        ;; Schedule all cycles that fall within the lookahead window
        (when (< (- cycle-start now) lookahead)
          (schedule-cycle! ac state cycle)
          (swap! scheduler-state update :cycle inc))))))

(defn start! [pattern on-beat-fn]
  (let [ac (get-ctx)]
    ;; Resume if suspended (browser autoplay policy)
    (when (= "suspended" (.-state ac))
      (.resume ac))
    (let [now       (.-currentTime ac)
          cycle-dur (:cycle-dur @scheduler-state)
          ;; Start from the next cycle boundary
          start-cycle (int (Math/ceil (/ now cycle-dur)))]
      (swap! scheduler-state assoc
             :playing?    true
             :pattern     pattern
             :cycle       start-cycle
             :on-beat     on-beat-fn))
    (let [id (js/setInterval tick! 25)]
      (swap! scheduler-state assoc :interval-id id))
    ;; Kick off immediately
    (tick!)))

(defn stop! []
  (when-let [id (:interval-id @scheduler-state)]
    (js/clearInterval id))
  (swap! scheduler-state assoc
         :playing?     false
         :interval-id  nil
         :pattern      nil))

(defn playing? []
  (:playing? @scheduler-state))
