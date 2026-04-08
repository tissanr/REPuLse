(ns repulse.audio
  (:require [repulse.core :as core]
            [repulse.theory :as theory]
            [repulse.samples :as samples]
            [repulse.synth :as synth]
            [repulse.midi :as midi]))

;; Web Audio API scheduler
;; Based on Chris Wilson's "A Tale of Two Clocks"

(def ctx (atom nil))

;; AudioWorklet node — nil until loaded
(defonce worklet-node (atom nil))
(defonce worklet-ready? (atom false))

;; Master signal chain: sources → master-gain → analyser → destination
(defonce master-gain   (atom nil))
(defonce analyser-node (atom nil))

;; Per-track audio routing: keyword → {:gain-node GainNode :fx-chain [...]}
(defonce track-nodes (atom {}))

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
  "Register the AudioWorkletProcessor. The main thread fetches the WASM bytes
   and transfers them as an ArrayBuffer; the worklet uses initSync to compile
   and instantiate synchronously — works on Chrome, Firefox, and Safari.
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
                   ;; Fetch raw WASM bytes on the main thread and transfer the
                   ;; ArrayBuffer to the worklet.  The worklet uses initSync()
                   ;; (new WebAssembly.Module + new WebAssembly.Instance) which
                   ;; avoids every browser-specific limitation we hit before.
                   (-> (js/fetch "/repulse_audio_bg.wasm")
                       (.then (fn [resp] (.arrayBuffer resp)))
                       (.then (fn [buf]
                                (.. node -port
                                    (postMessage #js {:type "init" :wasmBytes buf}
                                                 #js [buf]))
                                (js/setTimeout
                                 (fn []
                                   (when-not @worklet-ready?
                                     (js/console.warn
                                      "[REPuLse] audio backend: clojurescript synthesis (WASM init timed out — worklet did not send 'ready' after 5 s)")))
                                 5000)))
                       (.catch (fn [e]
                                 (js/console.warn "[REPuLse] WASM fetch failed:" e)))))))
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

;;; Per-track routing helpers

(defn- ensure-track-node!
  "Create a GainNode for a track if it doesn't exist; connect it to masterGain."
  [ac track-name]
  (when (and track-name (not (get @track-nodes track-name)))
    (let [gain (doto (.createGain ac)
                 (-> .-gain (.setValueAtTime 1.0 (.-currentTime ac))))]
      (.connect gain @master-gain)
      (swap! track-nodes assoc track-name {:gain-node gain :fx-chain []}))))

(defn- output-for-track
  "Returns the AudioNode that a source should connect to for the given track.
   Falls back to masterGain (or destination for offline) when track-name is nil."
  [ac track-name]
  (if (and track-name (not (instance? js/OfflineAudioContext ac)))
    (if-let [tn (get @track-nodes track-name)]
      (if-let [first-fx (first (:fx-chain tn))]
        (:input first-fx)
        @master-gain)
      ;; track-name given but no node yet — fallback
      (or @master-gain (.-destination ac)))
    ;; no track-name or offline render
    (if (instance? js/OfflineAudioContext ac)
      (.-destination ac)
      (or @master-gain (.-destination ac)))))

;;; Synthesized voices (JS fallback when AudioWorklet is unavailable)

(defn- output-node [ac]
  ;; OfflineAudioContext has no master-gain — route directly to its destination.
  (if (instance? js/OfflineAudioContext ac)
    (.-destination ac)
    (or @master-gain (.-destination ac))))

(defn- make-kick [ac t amp pan dest]
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
    (.connect panner dest)
    (.start osc t)
    (.stop osc (+ t 0.4))))

(defn- make-snare [ac t amp pan dest]
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
    (.connect panner dest)
    (.start src t)
    (.stop src (+ t 0.2))))

(defn- make-hihat
  "Noise burst through a highpass filter. dur controls envelope length."
  ([ac t amp pan dest] (make-hihat ac t amp pan dest 0.045 0.5 8000))
  ([ac t amp pan dest dur gain-scale hpf-freq]
   (let [buf-size 2048
         buf  (.createBuffer ac 1 buf-size (.-sampleRate ac))
         data (.getChannelData buf 0)
         _    (dotimes [i buf-size]
                (aset data i (- (* 2 (Math/random)) 1)))
         src  (.createBufferSource ac)
         hpf  (.createBiquadFilter ac)
         gain (.createGain ac)
         panner (.createStereoPanner ac)
         pk   (* gain-scale (float amp))]
     (set! (.-buffer src) buf)
     (set! (.-loop src) true)          ; loop noise for sustained tails
     (set! (.-type hpf) "highpass")
     (.setValueAtTime (.-frequency hpf) hpf-freq t)
     (.setValueAtTime (.-gain gain) pk t)
     (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t dur))
     (.setValueAtTime (.-pan panner) (float pan) t)
     (.connect src hpf)
     (.connect hpf gain)
     (.connect gain panner)
     (.connect panner dest)
     (.start src t)
     (.stop src (+ t dur)))))

(defn- make-sine
  "JS-synthesis fallback for when the AudioWorklet/WASM is unavailable.
   dur     — decay duration in seconds (default 1.5)
   amp     — peak amplitude 0.0–1.0 (default 1.0; scaled by 0.5 internally)
   attack  — linear ramp-up time in seconds (default 0.001 = instant)
   pan     — stereo position -1.0 (left) to 1.0 (right), default 0.0
   dest    — AudioNode to connect to (default: output-node)"
  ([ac t freq] (make-sine ac t freq 1.5 1.0 0.001 0.0 (output-node ac)))
  ([ac t freq dur] (make-sine ac t freq dur 1.0 0.001 0.0 (output-node ac)))
  ([ac t freq dur amp] (make-sine ac t freq dur amp 0.001 0.0 (output-node ac)))
  ([ac t freq dur amp attack] (make-sine ac t freq dur amp attack 0.0 (output-node ac)))
  ([ac t freq dur amp attack pan] (make-sine ac t freq dur amp attack pan (output-node ac)))
  ([ac t freq dur amp attack pan dest]
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
     (.connect panner dest)
     (.start osc t)
     (.stop osc stop-t))))

(defn- make-saw [ac t freq dur amp attack pan dest]
  (let [osc    (.createOscillator ac)
        gain   (.createGain ac)
        panner (.createStereoPanner ac)
        peak   (* 0.5 (float amp))
        atk    (max 0.001 (float attack))
        stop-t (+ t atk (float dur))]
    (set! (.-type osc) "sawtooth")
    (.setValueAtTime (.-frequency osc) freq t)
    (.setValueAtTime (.-gain gain) 0.0001 t)
    (.linearRampToValueAtTime (.-gain gain) peak (+ t atk))
    (.exponentialRampToValueAtTime (.-gain gain) 0.0001 stop-t)
    (.setValueAtTime (.-pan panner) (float pan) t)
    (.connect osc gain)
    (.connect gain panner)
    (.connect panner dest)
    (.start osc t)
    (.stop osc stop-t)))

(defn- make-pulse-wave
  "Build a PeriodicWave for a pulse wave with duty cycle pw (0.0–1.0).
   Uses Fourier synthesis so any pulse width is supported — unlike the native
   OscillatorNode 'square' type which is always 50%.
   Coefficients: real[n] = 2·sin(2πnD)/(πn), imag[n] = 2·(1−cos(2πnD))/(πn)."
  [ac pw]
  (let [n    64
        real (js/Float32Array. n)
        imag (js/Float32Array. n)
        d    (double pw)]
    (aset real 0 0.0)   ; zero DC offset
    (aset imag 0 0.0)
    (dotimes [i (dec n)]
      (let [k  (inc i)
            pk (* js/Math.PI k)]
        (aset real k (/ (* 2.0 (js/Math.sin (* 2.0 js/Math.PI k d))) pk))
        (aset imag k (/ (* 2.0 (- 1.0 (js/Math.cos (* 2.0 js/Math.PI k d)))) pk))))
    (.createPeriodicWave ac real imag #js {:disableNormalization false})))

(defn- make-square [ac t freq dur amp attack pan dest pw]
  (let [osc    (.createOscillator ac)
        gain   (.createGain ac)
        panner (.createStereoPanner ac)
        peak   (* 0.5 (float amp))
        atk    (max 0.001 (float attack))
        stop-t (+ t atk (float dur))]
    ;; Use native "square" only for the 50% default — any other pw needs
    ;; PeriodicWave because OscillatorNode has no pulse-width parameter.
    (if (== (double pw) 0.5)
      (set! (.-type osc) "square")
      (.setPeriodicWave osc (make-pulse-wave ac pw)))
    (.setValueAtTime (.-frequency osc) freq t)
    (.setValueAtTime (.-gain gain) 0.0001 t)
    (.linearRampToValueAtTime (.-gain gain) peak (+ t atk))
    (.exponentialRampToValueAtTime (.-gain gain) 0.0001 stop-t)
    (.setValueAtTime (.-pan panner) (float pan) t)
    (.connect osc gain)
    (.connect gain panner)
    (.connect panner dest)
    (.start osc t)
    (.stop osc stop-t)))

(defn- make-noise [ac t dur amp pan dest]
  (let [buf-size (max 1 (* (.-sampleRate ac) (int (Math/ceil dur))))
        buf  (.createBuffer ac 1 buf-size (.-sampleRate ac))
        data (.getChannelData buf 0)
        _    (dotimes [i buf-size]
               (aset data i (- (* 2 (Math/random)) 1)))
        src  (.createBufferSource ac)
        gain (.createGain ac)
        panner (.createStereoPanner ac)
        pk   (* 0.3 (float amp))]
    (set! (.-buffer src) buf)
    (.setValueAtTime (.-gain gain) pk t)
    (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t dur))
    (.setValueAtTime (.-pan panner) (float pan) t)
    (.connect src gain)
    (.connect gain panner)
    (.connect panner dest)
    (.start src t)
    (.stop src (+ t dur))))

;;; Synthesis dispatch

(defn- worklet-trigger!
  "Send a trigger message to the AudioWorklet. Returns true if worklet is ready.
   dest must be master-gain — worklet always outputs there and cannot be redirected."
  [value t dest]
  (when (and @worklet-ready? @worklet-node (= dest @master-gain))
    (.. @worklet-node -port
        (postMessage #js {:type "trigger" :value value :time t}))
    true))

(defn- worklet-trigger-v2!
  "Send a trigger_v2 message with explicit synthesis parameters. Returns true if ready.
   dest must be master-gain — worklet always outputs there and cannot be redirected."
  [value t amp attack decay pan dest]
  (when (and @worklet-ready? @worklet-node (= dest @master-gain))
    (.. @worklet-node -port
        (postMessage #js {:type "trigger_v2" :value value :time t
                          :amp amp :attack attack :decay decay :pan pan}))
    true))

(defn- js-synth
  "JS synthesis fallback — used when AudioWorklet is unavailable."
  ([ac t value dest] (js-synth ac t value 1.0 0.0 dest))
  ([ac t value amp pan dest]
   (case value
     :bd (make-kick ac t amp pan dest)
     :sd (make-snare ac t amp pan dest)
     :hh (make-hihat ac t amp pan dest)
     :oh (make-hihat ac t amp pan dest 1.0 0.4 8000)
     (make-sine ac t 440 1.5 amp 0.001 pan dest))))

(defn play-event
  ([ac t value] (play-event ac t value nil))
  ([ac t value track-name]
   ;; OfflineAudioContext has no worklet — skip to JS fallbacks for rendering.
   (let [offline? (instance? js/OfflineAudioContext ac)
         dest     (output-for-track ac track-name)]
     (cond
       ;; Silence / rest
       (= value :_) nil

       ;; User-defined synth — check BEFORE built-in dispatch.
       ;; Produced by (synth :my-synth pat) when :my-synth is registered via defsynth.
       (and (map? value) (:synth value) (synth/lookup-synth (:synth value)))
       (let [synth-def (synth/lookup-synth (:synth value))]
         (synth/play-synth! ac t synth-def value dest))

       ;; Parameter map {:note … :amp … :attack … :decay … :pan … :synth …}
       ;; from amp/attack/decay/release/pan/saw/square/noise/fm transformers
       (and (map? value) (:note value))
       (let [note     (:note value)
             amp-v    (float (:amp value 1.0))
             attack-v (float (:attack value 0.001))
             decay-v  (float (:decay value 1.5))
             pan-v    (float (:pan value 0.0))
             synth    (:synth value)]
         (cond
           (= note :_) nil

           ;; New synth voices dispatched by :synth key
           (= synth :saw)
           (let [hz (if (theory/note-keyword? note) (theory/note->hz note) (double note))]
             (or (when-not offline?
                   (worklet-trigger-v2! (str "saw:" hz) t amp-v attack-v decay-v pan-v dest))
                 (make-saw ac t hz decay-v amp-v attack-v pan-v dest)))

           (= synth :square)
           (let [hz  (if (theory/note-keyword? note) (theory/note->hz note) (double note))
                 pw' (double (or (:pw value) 0.5))]
             (or (when-not offline?
                   (worklet-trigger-v2! (str "square:" hz ":" pw')
                                        t amp-v attack-v decay-v pan-v dest))
                 (make-square ac t hz decay-v amp-v attack-v pan-v dest pw')))

           (= synth :fm)
           (let [hz    (if (theory/note-keyword? note) (theory/note->hz note) (double note))
                 index (or (:index value) 1.0)
                 ratio (or (:ratio value) 2.0)]
             (or (when-not offline?
                   (worklet-trigger-v2! (str "fm:" hz ":" index ":" ratio)
                                        t amp-v attack-v decay-v pan-v dest))
                 ;; JS fallback: sine (FM needs two connected oscillators — WASM only)
                 (make-sine ac t hz decay-v amp-v attack-v pan-v dest)))

           ;; Standard note/sample dispatch
           (keyword? note)
           (if (theory/note-keyword? note)
             (let [hz (theory/note->hz note)]
               (if offline?
                 (make-sine ac t hz decay-v amp-v attack-v pan-v dest)
                 (or (worklet-trigger-v2! (str hz) t amp-v attack-v decay-v pan-v dest)
                     (make-sine ac t hz decay-v amp-v attack-v pan-v dest))))
             (let [resolved (samples/resolve-keyword note)
                   extra    {:rate  (:rate value)
                             :begin (:begin value)
                             :end   (:end value)
                             :loop  (:loop value)}]
               (cond
                 (samples/has-bank? resolved)
                 (samples/play! ac t resolved 0 amp-v pan-v extra dest)
                 :else
                 (if offline?
                   (js-synth ac t note amp-v pan-v dest)
                   (or (worklet-trigger-v2! (name note) t amp-v attack-v decay-v pan-v dest)
                       (js-synth ac t note amp-v pan-v dest))))))

           (number? note)
           (if offline?
             (make-sine ac t note decay-v amp-v attack-v pan-v dest)
             (or (worklet-trigger-v2! (str note) t amp-v attack-v decay-v pan-v dest)
                 (make-sine ac t note decay-v amp-v attack-v pan-v dest)))))

       ;; Map {:bank :bd :n 2} from (sound :bd 2) — always use samples
       (and (map? value) (:bank value))
       (let [{:keys [bank n]} value
             amp-v  (float (:amp  value 1.0))
             pan-v  (float (:pan  value 0.0))
             extra {:rate  (:rate value) :begin (:begin value)
                    :end   (:end value)  :loop  (:loop value)}]
         (if (samples/has-bank? bank)
           (samples/play! ac t bank n amp-v pan-v extra dest)
           (if offline?
             (make-sine ac t 440 1.5 1.0 0.001 0.0 dest)
             (or (worklet-trigger-v2! (name bank) t amp-v 0.001 1.5 pan-v dest)
                 (make-sine ac t 440 1.5 amp-v 0.001 pan-v dest)))))

       ;; Keyword — note name → Hz tone, or sample bank
       (keyword? value)
       (if (theory/note-keyword? value)
         (let [hz (theory/note->hz value)]
           (if offline?
             (make-sine ac t hz 1.5 1.0 0.001 0.0 dest)
             (or (worklet-trigger! (str hz) t dest)
                 (make-sine ac t hz 1.5 1.0 0.001 0.0 dest))))
         (let [resolved (samples/resolve-keyword value)]
           (cond
             (samples/has-bank? resolved) (samples/play! ac t resolved 0 1.0 0.0 {} dest)
             :else (if offline?
                     (js-synth ac t value dest)
                     (or (worklet-trigger! (name value) t dest)
                         (js-synth ac t value dest))))))

       ;; Map with :synth :noise — no :note key
       (and (map? value) (= (:synth value) :noise))
       (let [amp-v  (float (:amp value 1.0))
             decay-v (float (:decay value 0.3))
             pan-v  (float (:pan value 0.0))]
         (or (when-not offline?
               (worklet-trigger-v2! "noise" t amp-v 0.001 decay-v pan-v dest))
             (make-noise ac t decay-v amp-v pan-v dest)))

       ;; Number — frequency in Hz
       (number? value)
       (if offline?
         (make-sine ac t value 1.5 1.0 0.001 0.0 dest)
         (or (worklet-trigger! (str value) t dest)
             (make-sine ac t value 1.5 1.0 0.001 0.0 dest)))

       :else (make-sine ac t 440 1.5 1.0 0.001 0.0 dest)))))

;;; Scheduler state

(defn- tween? [v]
  (and (map? v) (= (:type v) :tween)))

(def scheduler-state
  (atom {:playing?     false
         :tracks       {}     ; keyword → Pattern
         :muted        #{}    ; set of muted track keywords
         :cycle        0
         :cycle-dur    2.0    ; seconds per cycle — 120 BPM, 1 cycle = 1 bar (4 beats)
         :lookahead    0.2    ; look-ahead in seconds
         :interval-id  nil
         :on-beat      nil
         :on-event     nil
         :on-fx-event  nil
         :tween-armed  {}}))  ; track-name → #{param-keys already sent}

(defn set-bpm!
  "Set the tempo in BPM. One cycle = one bar (4 beats)."
  [bpm]
  (swap! scheduler-state assoc :cycle-dur (/ 240.0 bpm)))

(defn get-bpm []
  (/ 240.0 (:cycle-dur @scheduler-state)))

(defn schedule-cycle! [ac state cycle]
  (let [{:keys [tracks muted cycle-dur on-beat on-event on-fx-event]} state
        sp {:start [cycle 1] :end [(inc cycle) 1]}]
    (doseq [[track-name pattern] tracks]
      (when (and pattern (not (contains? muted track-name)))
        (let [evs (core/query pattern sp)]
          (doseq [ev evs]
            (let [part-start        (core/rat->float (:start (:part ev)))
                  part-end          (core/rat->float (:end   (:part ev)))
                  cycle-audio-start (* cycle cycle-dur)
                  event-offset      (* (- part-start cycle) cycle-dur)
                  t                 (+ cycle-audio-start event-offset)
                  dur               (* (- part-end part-start) cycle-dur)]
              (when (> t (.-currentTime ac))
                (let [armed  (get-in state [:tween-armed track-name] #{})
                      raw    (:value ev)
                      value  (if (and (map? raw) (seq armed))
                               (reduce (fn [m k]
                                         (if (tween? (get m k))
                                           (assoc m k (if @worklet-ready?
                                                         (case k
                                                           :amp 1.0
                                                           :pan 0.0
                                                           (get-in m [k :start]))
                                                         (get-in m [k :start])))
                                           m))
                                       raw armed)
                               raw)]
                (play-event ac t value track-name)
                ;; MIDI note output — triggered by :midi-ch key added via (midi-out ch pat)
                (when-let [midi-ch (:midi-ch (:value ev))]
                  (let [value    (:value ev)
                        hz       (cond
                                   (:freq value) (:freq value)
                                   (and (:note value) (theory/note-keyword? (:note value)))
                                   (theory/note->hz (:note value))
                                   (number? (:note value)) (:note value)
                                   (theory/note-keyword? value) (theory/note->hz value)
                                   (number? value) value
                                   :else nil)
                        note-num (when hz (midi/hz->midi (double hz)))
                        amp-v    (float (:amp value 1.0))
                        velocity (min 127 (max 0 (Math/round (* amp-v 127))))
                        ac-now   (.-currentTime ac)
                        perf-now (.now js/performance)
                        ts-on    (+ perf-now (* (- t ac-now) 1000))
                        ts-off   (+ ts-on (* dur 1000))]
                    (when note-num
                      (midi/send-note-on!  midi-ch note-num velocity ts-on)
                      (midi/send-note-off! midi-ch note-num ts-off))))
                (when on-fx-event
                  (on-fx-event (:value ev) t))
                (when (and on-event (:source ev))
                  (let [delay-ms (max 0 (* 1000 (- t (.-currentTime ac))))]
                    (js/setTimeout #(on-event (:source ev)) delay-ms)))
                (when on-beat
                  (let [delay-ms (* 1000 (- t (.-currentTime ac)))]
                    (js/setTimeout on-beat delay-ms))))))))))))

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

(defn- ensure-running!
  "Start the scheduler tick loop if not already running."
  [ac on-beat-fn on-event-fn]
  (when-not (:interval-id @scheduler-state)
    (let [now       (.-currentTime ac)
          cycle-dur (:cycle-dur @scheduler-state)
          start-cycle (int (Math/floor (/ now cycle-dur)))]
      (swap! scheduler-state assoc
             :playing?    true
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
  ;; Disconnect and clean up all per-track nodes
  (doseq [[_ {:keys [gain-node fx-chain]}] @track-nodes]
    (try (.disconnect gain-node) (catch :default _))
    (doseq [{:keys [plugin]} fx-chain]
      (try (.destroy ^js plugin) (catch :default _))))
  (reset! track-nodes {})
  (swap! scheduler-state assoc
         :playing?     false
         :interval-id  nil
         :tracks       {}
         :muted        #{}))

(defn- arm-transitions!
  "Detect tween descriptors in the first cycle of pattern and send transition messages
   to the WASM worklet. Re-evaluation clears the armed set so the transition is restarted."
  [track-name pattern ac]
  (let [cycle-dur (:cycle-dur @scheduler-state)
        sr        (.-sampleRate ac)
        sp        {:start [0 1] :end [1 1]}
        evs       (try (core/query pattern sp) (catch :default _ []))
        tweens    (into {}
                    (for [ev   evs
                          :let [v (:value ev)]
                          :when (map? v)
                          [k pv] v
                          :when (tween? pv)]
                      [k pv]))]
    (swap! scheduler-state update :tween-armed assoc track-name #{})
    (doseq [[k pv] tweens]
      (let [dur-samples (Math/round (* (:duration-bars pv) cycle-dur sr))]
        (when (and @worklet-ready? @worklet-node)
          (.. @worklet-node -port
              (postMessage #js {:type             "transition"
                                :param            (name k)
                                :start            (float (:start pv))
                                :end              (float (:end pv))
                                :duration_samples dur-samples
                                :curve            (name (:curve pv))})))
        (swap! scheduler-state update-in [:tween-armed track-name] (fnil conj #{}) k)))))

(defn play-track!
  "Add or replace a named track. Creates a per-track GainNode if needed.
   Starts the scheduler if not already running."
  [track-name pattern on-beat-fn on-event-fn]
  (let [ac (get-ctx)]
    (.resume ac)
    (ensure-track-node! ac track-name)
    (arm-transitions! track-name pattern ac)
    (swap! scheduler-state update :tracks assoc track-name pattern)
    (ensure-running! ac on-beat-fn on-event-fn)))

(defn mute-track! [track-name]
  (swap! scheduler-state update :muted conj track-name))

(defn unmute-track! [track-name]
  (swap! scheduler-state update :muted disj track-name))

(defn solo-track! [track-name]
  (let [others (remove #{track-name} (keys (:tracks @scheduler-state)))]
    (swap! scheduler-state assoc :muted (set others))))

(defn clear-track! [track-name]
  ;; Disconnect and remove per-track node and its FX chain
  (when-let [tn (get @track-nodes track-name)]
    (try (.disconnect (:gain-node tn)) (catch :default _))
    (doseq [{:keys [plugin]} (:fx-chain tn)]
      (try (.destroy ^js plugin) (catch :default _)))
    (swap! track-nodes dissoc track-name))
  (swap! scheduler-state (fn [s]
    (-> s
        (update :tracks dissoc track-name)
        (update :muted disj track-name))))
  (when (empty? (:tracks @scheduler-state))
    (stop!)))

(defn start!
  "Legacy single-pattern start: stops all tracks, starts fresh with one anonymous pattern."
  [pattern on-beat-fn on-event-fn]
  (stop!)
  (let [ac (get-ctx)]
    (.resume ac)
    (let [now       (.-currentTime ac)
          cycle-dur (:cycle-dur @scheduler-state)
          start-cycle (int (Math/floor (/ now cycle-dur)))]
      (swap! scheduler-state assoc
             :playing?    true
             :tracks      {:_ pattern}
             :muted       #{}
             :cycle       start-cycle
             :on-beat     on-beat-fn
             :on-event    on-event-fn))
    (let [id (js/setInterval tick! 25)]
      (swap! scheduler-state assoc :interval-id id))
    (tick!)))

(defn playing? []
  (:playing? @scheduler-state))

;;; Tap tempo

(defonce ^:private tap-times (atom []))

(defn tap!
  "Register a tap for BPM detection. Returns computed BPM or nil if fewer than 2 taps."
  []
  (let [now (js/Date.now)]
    (swap! tap-times (fn [ts]
      (->> (conj ts now)
           (filterv #(> % (- now 4000)))
           (take-last 8)
           vec)))
    (let [ts @tap-times]
      (when (>= (count ts) 2)
        (let [diffs (map - (rest ts) ts)
              avg   (/ (reduce + diffs) (count diffs))
              bpm   (/ 60000.0 avg)]
          (set-bpm! bpm)
          bpm)))))

;;; MIDI clock sync

(defonce ^:private midi-sync-enabled? (atom false))
(defonce ^:private clock-pulses (atom []))

(defn- handle-clock-pulse! []
  (let [now (js/Date.now)]
    (swap! clock-pulses (fn [ps]
      (->> (conj ps now)
           (filterv #(> % (- now 2000)))
           (take-last 48)
           vec)))
    (let [ps @clock-pulses]
      ;; Need at least 25 samples for 24 inter-pulse intervals
      (when (>= (count ps) 25)
        (let [diffs (map - (rest ps) ps)
              avg   (/ (reduce + diffs) (count diffs))
              bpm   (/ 60000.0 (* avg 24))]
          (set-bpm! bpm))))))

(defn- handle-clock-start! [] (reset! clock-pulses []))
(defn- handle-clock-stop!  [] (reset! clock-pulses []))

(defn- handle-midi-msg! [event]
  (let [data (.-data event)]
    (case (aget data 0)
      0xF8 (handle-clock-pulse!)
      0xFA (handle-clock-start!)
      0xFC (handle-clock-stop!)
      nil)))

(defn set-midi-sync!
  "Enable or disable MIDI clock sync. Requests MIDI access on first enable."
  [enabled?]
  (reset! midi-sync-enabled? enabled?)
  (if enabled?
    (if (.-requestMIDIAccess js/navigator)
      (-> (.requestMIDIAccess js/navigator)
          (.then (fn [access]
            (doseq [input (array-seq (.values (.-inputs access)))]
              (set! (.-onmidimessage input) handle-midi-msg!))
            (js/console.log "[REPuLse] MIDI clock sync enabled")))
          (.catch (fn [e]
            (js/console.warn "[REPuLse] MIDI access failed:" e))))
      (js/console.warn "[REPuLse] Web MIDI not supported"))
    (reset! clock-pulses [])))
