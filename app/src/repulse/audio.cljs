(ns repulse.audio
  (:require [clojure.string :as str]
            [repulse.core :as core]
            [repulse.theory :as theory]
            [repulse.samples :as samples]
            [repulse.synth :as synth]
            [repulse.bus :as bus]
            [repulse.midi :as midi]
            [repulse.preview-track :as preview-track]
            [repulse.song :as song]
            [repulse.audio-clock :as clock]))

;; Web Audio API scheduler
;; Based on Chris Wilson's "A Tale of Two Clocks"

;; Set js/globalThis.__REPULSE_DEBUG__ = true (before page load) to enable verbose logging.
(def ^:private debug? (boolean (.-__REPULSE_DEBUG__ js/globalThis)))

(def ctx (atom nil))

;; AudioWorklet node — nil until loaded
(defonce worklet-node (atom nil))
(defonce worklet-ready? (atom false))

;; Master signal chain:
;;   sources → master-gain → [global FX…] → master-tap → [recorder] → analyser → destination
;; master-tap is a permanent pass-through GainNode marking the end of the master
;; FX chain; the recorder (SONG1 real-time export) sits between it and the analyser.
(defonce master-gain   (atom nil))
(defonce master-tap    (atom nil))
(defonce analyser-node (atom nil))

;; Recorder AudioWorkletNode ("repulse-recorder") — nil until loaded or when
;; AudioWorklet is unsupported. recorder-ready resolves to the node or nil.
(defonce recorder-node (atom nil))
(defonce ^:private recorder-ready-promise (atom nil))

;; Per-track audio routing: keyword → {:gain-node GainNode :fx-chain [...]}
(defonce track-nodes (atom {}))

(def ^:private public-base-url
  (let [document (.-document js/globalThis)
        script-src (some-> document .-currentScript .-src)]
    (.-href
     (cond
       ;; app build (/js/main.js) and TEST1 harness build (/test-js/main.js)
       ;; both live one level below app/public
       (and script-src (or (str/includes? script-src "/js/")
                           (str/includes? script-src "/test-js/")))
       (js/URL. "../" script-src)

       script-src
       (js/URL. "./" script-src)

       document
       (js/URL. "./" (.-baseURI document))

       :else
       (js/URL. "./" "http://localhost/")))))

(defn- public-url [path]
  (.-href (js/URL. path public-base-url)))

(defn- build-master-chain! [ac]
  (let [gain (doto (.createGain ac)
               (-> .-gain (.setValueAtTime 1.0 (.-currentTime ac))))
        tap  (doto (.createGain ac)
               (-> .-gain (.setValueAtTime 1.0 (.-currentTime ac))))
        anl  (doto (.createAnalyser ac)
               (aset "fftSize" 2048)
               (aset "smoothingTimeConstant" 0.8))]
    (.connect gain tap)
    (.connect tap anl)
    (.connect anl (.-destination ac))
    (reset! master-gain gain)
    (reset! master-tap tap)
    (reset! analyser-node anl)))

(defn- init-worklet!
  "Register the AudioWorkletProcessor. The main thread fetches the WASM bytes
   and transfers them as an ArrayBuffer; the worklet uses initSync to compile
   and instantiate synchronously — works on Chrome, Firefox, and Safari.
  Falls back to JS synthesis if AudioWorklet is unavailable."
  [ac]
  (if-let [worklet (.-audioWorklet ac)]
    (-> (.addModule worklet (public-url "worklet.js"))
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
                   (-> (js/fetch (public-url "repulse_audio_bg.wasm"))
                       (.then (fn [resp] (.arrayBuffer resp)))
                       (.then (fn [buf]
                                (when debug? (js/console.log "[REPuLse] WASM fetched, initializing worklet..."))
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

(declare on-recorder-message!)

(defn- init-recorder!
  "Load the pass-through recorder worklet and insert it between master-tap and
   the analyser. On failure master-tap stays connected straight to the analyser
   and recorder-node stays nil."
  [ac]
  (reset! recorder-ready-promise
          (if-let [worklet (.-audioWorklet ac)]
            (-> (.addModule worklet (public-url "worklets/recorder-processor.js"))
                (.then (fn []
                         (let [node (js/AudioWorkletNode. ac "repulse-recorder"
                                                          #js {:numberOfInputs     1
                                                               :numberOfOutputs    1
                                                               :outputChannelCount #js [2]
                                                               :channelCount       2
                                                               :channelCountMode   "explicit"})
                               tap  @master-tap]
                           (.disconnect tap)
                           (.connect tap node)
                           (.connect node @analyser-node)
                           (set! (.. node -port -onmessage) on-recorder-message!)
                           (reset! recorder-node node)
                           node)))
                (.catch (fn [e]
                          (js/console.warn "[REPuLse] recorder worklet unavailable — (export :song) disabled" e)
                          nil)))
            (do (js/console.warn "[REPuLse] AudioWorklet not supported — (export :song) disabled")
                (js/Promise.resolve nil)))))

(defn get-ctx []
  (or @ctx
      (let [c (make-audio-context)]
        (reset! ctx c)
        (build-master-chain! c)
        (init-worklet! c)
        (init-recorder! c)
        c)))

(defn recorder-ready
  "Promise resolving to the recorder node once loaded, or nil when unavailable."
  []
  (get-ctx)
  @recorder-ready-promise)

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
    (when debug? (js/console.log "[REPuLse] triggering WASM:" value "at t=" t "amp=" amp))
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

           (#{:sax :trumpet :trumpet-muted :trombone :synth-brass
              :harmon-out :harmon-in :epiano :bell :marimba :flute} synth)
           (let [hz     (if (theory/note-keyword? note) (theory/note->hz note) (double note))
                 preset (clojure.core/name synth)]
             (or (when-not offline?
                   (worklet-trigger-v2! (str "fm:" preset ":" hz)
                                        t amp-v attack-v decay-v pan-v dest))
                 (make-sine ac t hz decay-v amp-v attack-v pan-v dest)))

           (#{:western :nylon :guitar :harp :koto :pizz :lute :mandolin} synth)
           (let [hz     (if (theory/note-keyword? note) (theory/note->hz note) (double note))
                 preset (clojure.core/name synth)]
             (or (when-not offline?
                   (worklet-trigger-v2! (str "ks:" preset ":" hz)
                                        t amp-v attack-v decay-v pan-v dest))
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

(defn- interp-tween
  "Compute the interpolated value for a tween at audio time t.
   ts = {:tween pv :start-time t0} stored by arm-transitions!
   cycle-dur = seconds per bar (cycle)."
  [{:keys [tween start-time]} t cycle-dur]
  (let [{:keys [start end duration-bars curve]} tween
        u (-> (/ (- t start-time) (* duration-bars cycle-dur))
              (max 0.0)
              (min 1.0))
        k (case curve
            :exp  (* u u)
            :sine (* 0.5 (- 1.0 (Math/cos (* Math/PI u))))
            u)]
    (+ start (* (- end start) k))))

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
         :tween-state  {}     ; track-name → {param-key {:tween pv :start-time t}}
         ;; SONG1 song mode: {:song <song value> :origin int-or-nil :start-bar int}
         ;; song bar = scheduler cycle − origin; origin nil = fresh start, resolved
         ;; on the next tick so bar :start-bar begins at the next whole bar.
         :song         nil
         ;; Active (export :song) capture: {:status :waiting|:recording :tail s
         ;; :start-time t0 :end-time t1} — read by the context panel.
         :capture      nil
         ;; Called after a :stop song has finished (and its tail rung out).
         :on-song-end  nil}))

;; Seconds a finished :end :stop song keeps the scheduler alive so tails ring out.
(def song-release-secs 2.0)

;; A fresh song start never begins on a bar that starts less than this far ahead
;; of currentTime, so the first bar (and a capture's first frame) is never cut.
(def ^:private song-start-margin-secs 0.05)

(defn coerce-bpm
  "Clamp BPM to the safe scheduler range [20, 640]. Invalid, NaN, or
   non-positive input falls back to 120."
  [x]
  (let [n (if (number? x) x js/NaN)]
    (cond
      (or (js/isNaN n) (not (pos? n))) 120
      (< n 20) 20
      (> n 640) 640
      :else n)))

(defn set-bpm!
  "Set the tempo in BPM. One cycle = one bar (4 beats)."
  [bpm]
  (swap! scheduler-state assoc :cycle-dur (/ 240.0 (coerce-bpm bpm))))

(defn get-bpm []
  (/ 240.0 (:cycle-dur @scheduler-state)))

;;; Visual sync — fire highlight / beat callbacks when the sound is heard

(defn- clock-snapshot
  "Sample the AudioContext and performance.now() clocks together for
   repulse.audio-clock/audible-perf-ms."
  [^js ac]
  (let [ots (when (.-getOutputTimestamp ac)
              (try (.getOutputTimestamp ac) (catch :default _ nil)))]
    {:current-time   (.-currentTime ac)
     :perf-now       (.now js/performance)
     :output-ts      (when ots
                       {:context-time     (.-contextTime ots)
                        :performance-time (.-performanceTime ots)})
     :output-latency (.-outputLatency ac)
     :base-latency   (.-baseLatency ac)}))

;; Pending visual callbacks [{:at perf-ms :f fn}], drained by one rAF loop.
(defonce ^:private visual-queue (atom []))
(defonce ^:private visual-raf-id (atom nil))

(def ^:private frame-slack-ms 8)    ; half a 60 Hz frame
(def ^:private visual-stale-ms 250) ; drop callbacks this late (tab was hidden)

(defn- drain-visuals! [now]
  (let [{:keys [due pending]} (clock/split-due @visual-queue now
                                               frame-slack-ms visual-stale-ms)]
    (reset! visual-queue pending)
    (doseq [{:keys [f]} due]
      (try (f) (catch :default e (js/console.warn "[REPuLse] visual callback:" e))))
    (reset! visual-raf-id
            (when (seq pending)
              (js/requestAnimationFrame drain-visuals!)))))

(defn- schedule-visual!
  "Run f on the animation frame closest to performance.now() time at-ms."
  [at-ms f]
  (when-not (.-hidden js/document)
    (swap! visual-queue clock/enqueue {:at at-ms :f f})
    (when-not @visual-raf-id
      (reset! visual-raf-id (js/requestAnimationFrame drain-visuals!)))))

(defn- clear-visuals! []
  (when-let [id @visual-raf-id]
    (js/cancelAnimationFrame id))
  (reset! visual-raf-id nil)
  (reset! visual-queue []))

(defn- schedule-event!
  "Schedule one onset event of track-name at audio time t (duration dur, in
   seconds): tween interpolation, synthesis, MIDI out, FX event hooks and the
   visual callbacks. Events already in the past are skipped."
  [ac state snap track-name ev t dur]
  (let [{:keys [cycle-dur on-beat on-event on-fx-event]} state]
    (when (> t (.-currentTime ac))
      (let [tween-st (get-in state [:tween-state track-name] {})
            raw      (:value ev)
            value    (if (and (map? raw) (seq tween-st))
                       (reduce-kv (fn [m k ts]
                                    (if (tween? (get m k))
                                      (assoc m k (interp-tween ts t cycle-dur))
                                      m))
                                  raw tween-st)
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
                ts-on    (clock/audible-perf-ms snap t)
                ts-off   (+ ts-on (* dur 1000))]
            (when note-num
              (midi/send-note-on!  midi-ch note-num velocity ts-on)
              (midi/send-note-off! midi-ch note-num ts-off))))
        (when on-fx-event
          (on-fx-event (:value ev) t))
        (when (and on-event (:source ev) (not (preview-track/preview-track? track-name)))
          (let [src (:source ev)]
            (schedule-visual! (clock/audible-perf-ms snap t) #(on-event src))))
        (when on-beat
          (schedule-visual! (clock/audible-perf-ms snap t) on-beat))))))

(defn schedule-cycle! [ac state cycle]
  (let [{:keys [tracks muted cycle-dur]} state
        snap (clock-snapshot ac)]
    (if-let [{s :song origin :origin} (:song state)]
      ;; ── Song mode: the song decides which tracks sound in this bar ─────
      ;; song-onsets queries each track in song time and places the events at
      ;; song bar (cycle − origin); it returns [] in pre-roll and after the
      ;; end of a :stop song. Mute always wins (decision 8).
      (when origin
        (doseq [{:keys [track event]} (song/song-onsets s tracks muted (- cycle origin))]
          (let [whole-start (core/rat->float (:start (:whole event)))
                whole-end   (core/rat->float (:end   (:whole event)))
                t           (* (+ origin whole-start) cycle-dur)
                dur         (* (- whole-end whole-start) cycle-dur)]
            (schedule-event! ac state snap track event t dur))))
      ;; ── Free-running tracks (no song) ──────────────────────────────────
      (let [sp {:start [cycle 1] :end [(inc cycle) 1]}]
        (doseq [[track-name pattern] tracks]
          (when (and pattern (not (contains? muted track-name)))
            ;; Only onsets trigger: a whole that crosses a cycle boundary (or is
            ;; cut by a structure-aware combinator) also yields non-onset
            ;; fragments, which must not re-trigger the sound. Continuous (analog)
            ;; events have no :whole and are skipped here on purpose; the user is
            ;; warned about structureless signals at eval time.
            (doseq [ev (core/query-onsets pattern sp)]
              (let [whole-start       (core/rat->float (:start (:whole ev)))
                    whole-end         (core/rat->float (:end   (:whole ev)))
                    cycle-audio-start (* cycle cycle-dur)
                    event-offset      (* (- whole-start cycle) cycle-dur)
                    t                 (+ cycle-audio-start event-offset)
                    dur               (* (- whole-end whole-start) cycle-dur)]
                (schedule-event! ac state snap track-name ev t dur)))))))))

;;; Song mode and real-time capture (SONG1)

;; Main-thread side of an active capture: promise fns + preallocated PCM.
(defonce ^:private capture-ctl (atom nil))

(defn- arm-capture!
  "Start the recorder once the song origin is known: record from the start of
   bar :start-bar to the end of the song plus the tail."
  [ac state]
  (let [{s :song origin :origin start-bar :start-bar} (:song state)
        cap  (:capture state)
        ctl  @capture-ctl
        node @recorder-node]
    (when (and (= :waiting (:status cap)) ctl node origin)
      (let [cd          (:cycle-dur state)
            sr          (.-sampleRate ac)
            tail        (:tail cap)
            t0          (* (+ origin start-bar) cd)
            dur         (song/song-duration-secs s (/ 240.0 cd))
            t1          (+ t0 dur tail)
            start-frame (Math/round (* t0 sr))
            frames      (Math/round (* (+ dur tail) sr))]
        (swap! capture-ctl assoc
               :left   (js/Float32Array. frames)
               :right  (js/Float32Array. frames)
               :frames frames
               :sample-rate sr)
        (.. node -port (postMessage #js {:type        "start"
                                         :startFrame  start-frame
                                         :endFrame    (+ start-frame frames)
                                         :chunkFrames sr}))
        (swap! scheduler-state assoc :capture
               {:status :recording :tail tail :start-time t0 :end-time t1})))))

(defn- resolve-song-origin!
  "Fresh song start: place bar :start-bar on the next whole bar boundary."
  [ac now]
  (let [state @scheduler-state
        sg    (:song state)]
    (when (and sg (nil? (:origin sg)))
      (let [{:keys [cycle cycle-dur]} state
            first-bar (if (< (* cycle cycle-dur) (+ now song-start-margin-secs))
                        (inc cycle)
                        cycle)]
        (swap! scheduler-state assoc-in [:song :origin] (- first-bar (:start-bar sg)))
        (arm-capture! ac @scheduler-state)))))

(declare stop!)

(defn- song-finished?
  "True once a :stop song has played its last bar and the release has passed.
   While a capture is active the capture's \"done\" handler stops instead."
  [state now]
  (let [{s :song origin :origin} (:song state)]
    (and s origin (= :stop (:end s)) (nil? (:capture state))
         (>= now (+ (* (+ origin (:total s)) (:cycle-dur state)) song-release-secs)))))

(defn tick! []
  (let [ac (get-ctx)]
    (when (:playing? @scheduler-state)
      (let [now (.-currentTime ac)]
        (resolve-song-origin! ac now)
        (let [state @scheduler-state]
          (if (song-finished? state now)
            (let [on-end (:on-song-end state)]
              (stop!)
              (when on-end (on-end)))
            (let [{:keys [cycle cycle-dur lookahead]} state
                  cycle-start (* cycle cycle-dur)]
              (when (< (- cycle-start now) lookahead)
                (schedule-cycle! ac state cycle)
                (swap! scheduler-state update :cycle inc)))))))))

(defn set-song!
  "Activate a song value. Not playing → fresh start at :start-bar (default 0).
   Playing a song → keep the bar position (decision 6), or jump to :start-bar.
   Playing without a song → the song starts at the next unscheduled bar."
  [s {:keys [start-bar]}]
  (swap! scheduler-state
         (fn [st]
           (let [cur (:song st)]
             (cond
               (not (:playing? st))
               (assoc st :song {:song s :origin nil :start-bar (or start-bar 0)})

               (nil? cur)
               (assoc st :song {:song s :origin (- (:cycle st) (or start-bar 0))
                                :start-bar (or start-bar 0)})

               (nil? (:origin cur))
               (assoc st :song (assoc cur :song s :start-bar (or start-bar (:start-bar cur))))

               :else
               (let [old  (:song cur)
                     bar  (- (:cycle st) (:origin cur))
                     bar  (if (and (= :loop (:end old)) (>= bar 0)) (mod bar (:total old)) bar)
                     pos  (or start-bar (song/position-after-reeval s bar))]
                 (assoc st :song {:song s :origin (- (:cycle st) pos) :start-bar pos})))))))

(defn clear-song! []
  (swap! scheduler-state assoc :song nil))

(defn relocate-song!
  "Playing with a song: make `bar` the next scheduled song bar."
  [bar]
  (swap! scheduler-state
         (fn [st]
           (if-let [sg (:song st)]
             (if (:origin sg)
               (assoc st :song (assoc sg :origin (- (:cycle st) bar) :start-bar bar))
               (assoc st :song (assoc sg :start-bar bar)))
             st))))

(defn- finish-capture! [late?]
  (when-let [{:keys [resolve left right frames sample-rate]} @capture-ctl]
    (reset! capture-ctl nil)
    (swap! scheduler-state assoc :capture nil)
    (let [on-end (:on-song-end @scheduler-state)]
      (stop!)
      (when on-end (on-end)))
    (resolve {:left left :right right :sample-rate sample-rate
              :frames frames :late (boolean late?)})))

(defn- on-recorder-message! [^js e]
  (let [^js d (.-data e)]
    (case (.-type d)
      "chunk" (when-let [{:keys [left right frames]} @capture-ctl]
                (let [off (.-offset d)
                      n   (min (.-frames d) (- frames off))]
                  (when (pos? n)
                    (.set left  (.subarray (.-left d)  0 n) off)
                    (.set right (.subarray (.-right d) 0 n) off))))
      "done"  (finish-capture! (.-late d))
      nil)))

(defn capture!
  "Record one pass of the active song from the master output in real time.
   Requires a freshly activated song (origin not yet resolved) and a loaded
   recorder. Returns a Promise of {:left :right :sample-rate :frames :late}
   (Float32Arrays); rejects with ex-info {:cancelled true} when (stop) cancels."
  [{:keys [tail]}]
  (js/Promise.
   (fn [resolve reject]
     (let [sg (:song @scheduler-state)]
       (cond
         (nil? @recorder-node)
         (reject (ex-info "recorder unavailable" {}))

         (or (nil? sg) (some? (:origin sg)))
         (reject (ex-info "capture! needs a freshly started song" {}))

         @capture-ctl
         (reject (ex-info "an export is already running" {}))

         :else
         (do (reset! capture-ctl {:resolve resolve :reject reject})
             (swap! scheduler-state assoc :capture {:status :waiting :tail (or tail 0)})))))))

(defn- cancel-capture! []
  (when-let [{:keys [reject]} @capture-ctl]
    (reset! capture-ctl nil)
    (when-let [node @recorder-node]
      (.. node -port (postMessage #js {:type "cancel"})))
    (reject (ex-info "export cancelled" {:cancelled true}))))

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
  (cancel-capture!)
  (when-let [node @worklet-node]
    (.. node -port (postMessage #js {:type "stop"})))
  (when-let [id (:interval-id @scheduler-state)]
    (js/clearInterval id))
  (clear-visuals!)
  ;; Disconnect and clean up all per-track nodes
  (doseq [[_ {:keys [gain-node fx-chain]}] @track-nodes]
    (try (.disconnect gain-node) (catch :default _))
    (doseq [{:keys [plugin]} fx-chain]
      (try (.destroy ^js plugin) (catch :default _))))
  (reset! track-nodes {})
  ;; Clean up all named buses and their persistent synth writers
  (bus/cleanup-all!)
  (swap! scheduler-state assoc
         :playing?     false
         :interval-id  nil
         :tracks       {}
         :muted        #{}
         :tween-state  {}
         :song         nil
         :capture      nil))


(defn- arm-transitions!
  "Detect tween descriptors in the first cycle of pattern and record their start time.
   schedule-cycle! uses this to interpolate values per event at schedule time,
   which works uniformly for WASM synths, JS synths, and sample-based sounds."
  [track-name pattern ac]
  (let [sp   {:start [0 1] :end [1 1]}
        evs  (try (core/query pattern sp) (catch :default _ []))
        now  (.-currentTime ac)
        ts   (into {}
               (for [ev   evs
                     :let [v (:value ev)]
                     :when (map? v)
                     [k pv] v
                     :when (tween? pv)]
                 [k {:tween pv :start-time now}]))]
    (swap! scheduler-state update :tween-state assoc track-name ts)))

(defn play-track!
  "Add or replace a named track. Creates a per-track GainNode if needed.
   Starts the scheduler if not already running."
  [track-name pattern on-beat-fn on-event-fn]
  (let [ac (get-ctx)]
    ;; ensure-track-node!, arm-transitions!, and the state swap do not require a
    ;; running context — they just create/connect nodes and update atoms.  Only
    ;; ensure-running! must wait for .resume() so the scheduler clock is live.
    (ensure-track-node! ac track-name)
    (arm-transitions! track-name pattern ac)
    (swap! scheduler-state update :tracks assoc track-name pattern)
    (-> (.resume ac)
        (.then (fn [] (ensure-running! ac on-beat-fn on-event-fn))))))

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
    (arm-transitions! :_ pattern ac)
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
