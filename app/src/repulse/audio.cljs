(ns repulse.audio
  (:require [clojure.string :as str]
            [repulse.core :as core]
            [repulse.theory :as theory]
            [repulse.samples :as samples]
            [repulse.inserts :as inserts]
            [repulse.orbits :as orbits]
            [repulse.synth :as synth]
            [repulse.bus :as bus]
            [repulse.midi :as midi]
            [repulse.preview-track :as preview-track]
            [repulse.song :as song]
            [repulse.voices :as voices]
            [repulse.tempo :as tempo]
            [repulse.audio-clock :as clock]))

;; Web Audio API scheduler
;; Based on Chris Wilson's "A Tale of Two Clocks"

;; Set js/globalThis.__REPULSE_DEBUG__ = true (before page load) to enable verbose logging.
(def ^:private debug? (boolean (.-__REPULSE_DEBUG__ js/globalThis)))

;; QA3 A7: defonce — a hot reload must not create a second AudioContext.
(defonce ctx (atom nil))

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
                                                  (clj->js orbits/worklet-outputs))]
                   ;; Output 0 = dry → master; outputs 1–8 = orbit sends (TL7b).
                   (.connect node @master-gain 0)
                   (orbits/connect-worklet! ac node @master-gain)
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
                       ;; QA3 A7: a 404 page must not reach the worklet as WASM.
                       (.then (fn [resp]
                                (if (.-ok resp)
                                  (.arrayBuffer resp)
                                  (throw (js/Error. (str "HTTP " (.-status resp)))))))
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
                                 (js/console.warn
                                  (str "[REPuLse] audio backend: clojurescript synthesis (WASM fetch failed: "
                                       (or (ex-message e) (str e)) ")"))))))))
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

;;; Offline graphs (Phase EXP1)
;; OfflineAudioContext → {:master-gain node :worklet-node node :track-dests {kw node}}
;; registered by repulse.offline-render. An OfflineAudioContext WITHOUT a
;; registered graph keeps the JS-only path (TEST1 harness, backend "offline-js").
(defonce ^:private offline-graphs
  (when (exists? js/WeakMap) (js/WeakMap.)))

(defn register-offline-graph!
  "Register the offline graph of an OfflineAudioContext so play-event routes and
   dispatches exactly like the real-time graph (decision E2)."
  [ac graph]
  (.set offline-graphs ac graph)
  ac)

(defn- offline-context? [ac]
  (and (exists? js/OfflineAudioContext) (instance? js/OfflineAudioContext ac)))

(defn- offline-graph [ac]
  (when (offline-context? ac) (.get offline-graphs ac)))

(defn- output-for-track
  "Returns the AudioNode that a source should connect to for the given track.
   Falls back to masterGain (or destination for an unregistered offline
   context) when track-name is nil. A registered offline graph mirrors the
   real-time routing: first track-FX input, else the offline master gain."
  [ac track-name]
  (if-let [g (offline-graph ac)]
    (get-in g [:track-dests track-name] (:master-gain g))
    (if (and track-name (not (offline-context? ac)))
      (if-let [tn (get @track-nodes track-name)]
        (if-let [first-fx (first (:fx-chain tn))]
          (:input first-fx)
          @master-gain)
        ;; track-name given but no node yet — fallback
        (or @master-gain (.-destination ac)))
      ;; no track-name or unregistered offline render
      (if (offline-context? ac)
        (.-destination ac)
        (or @master-gain (.-destination ac))))))

;;; Synthesized voices (JS fallback when AudioWorklet is unavailable)

(defn- output-node [ac]
  ;; A registered offline graph has its own master gain; an unregistered
  ;; OfflineAudioContext has none — route directly to its destination.
  (if-let [g (offline-graph ac)]
    (:master-gain g)
    (if (offline-context? ac)
      (.-destination ac)
      (or @master-gain (.-destination ac)))))

(defn- make-kick
  "Swept sine. f-start → f-end Hz over sweep s, decay dur s (SYN6 kit fallback
   uses the long arity; the defaults are the legacy kick)."
  ([ac t amp pan dest] (make-kick ac t amp pan dest 150 40 0.06 0.4))
  ([ac t amp pan dest f-start f-end sweep dur]
   (let [osc    (.createOscillator ac)
         gain   (.createGain ac)
         panner (.createStereoPanner ac)
         pk     (float amp)]
     (set! (.-type osc) "sine")
     (.setValueAtTime (.-frequency osc) f-start t)
     (.exponentialRampToValueAtTime (.-frequency osc) f-end (+ t sweep))
     (.setValueAtTime (.-gain gain) pk t)
     (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t dur))
     (.setValueAtTime (.-pan panner) (float pan) t)
     (.connect osc gain)
     (.connect gain panner)
     (.connect panner dest)
     (.start osc t)
     (.stop osc (+ t dur)))))

(defn- make-snare
  ([ac t amp pan dest] (make-snare ac t amp pan dest 0.2))
  ([ac t amp pan dest dur]
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
    (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t dur))
    (.setValueAtTime (.-pan panner) (float pan) t)
    (.connect src bpf)
    (.connect bpf gain)
    (.connect gain panner)
    (.connect panner dest)
    (.start src t)
    (.stop src (+ t dur)))))

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
     (.stop osc stop-t)
     osc)))

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
    (.stop osc stop-t)
    osc))

(defn- make-osc
  "JS fallback of the oscillator voices (SYN6 A8): one native OscillatorNode
   per unison oscillator (detune and pan from voices/unison-layout), an
   optional sub-oscillator, one gain envelope, glide as an exponential
   frequency ramp. on-osc is applied to every oscillator (TL7c pitch mods).
   Not available here: sync, pw-rate/pw-depth (static pw). Returns the first
   oscillator."
  [ac t hz dur amp attack pan dest value on-osc]
  (let [synth  (:synth value)
        n      (max 1 (min 7 (Math/round (or (:unison value) 1))))
        det    (or (:detune value) (if (> n 1) 20 0))
        layout (voices/unison-layout n det (or (:spread value) 0))
        gain   (.createGain ac)
        panner (.createStereoPanner ac)
        peak   (* 0.5 (float amp))
        atk    (max 0.001 (float attack))
        stop-t (+ t atk (float dur))
        pw     (double (or (:pw value) 0.5))
        from   (:glide-from value)
        gl     (:glide value)
        freq!  (fn [^js o f]
                 (if (and from (number? gl) (pos? gl))
                   (do (.setValueAtTime (.-frequency o) from t)
                       (.exponentialRampToValueAtTime (.-frequency o) f (+ t (min 5 gl))))
                   (.setValueAtTime (.-frequency o) f t)))
        ;; Native oscillators all start at phase 0; the unison start phases of
        ;; the layout become a start offset of phase/hz (≤ one period).
        start! (fn [^js o node phase]
                 (.connect o node)
                 (on-osc o)
                 (.start o (+ t (/ phase hz)))
                 (.stop o stop-t)
                 o)
        oscs   (mapv (fn [{:keys [cents pan phase]}]
                       (let [o  (.createOscillator ac)
                             g  (.createGain ac)
                             sp (.createStereoPanner ac)]
                         (case synth
                           (:square :pulse) (if (== pw 0.5)
                                              (set! (.-type o) "square")
                                              (.setPeriodicWave o (make-pulse-wave ac pw)))
                           :tri  (set! (.-type o) "triangle")
                           :sine (set! (.-type o) "sine")
                           (set! (.-type o) "sawtooth"))
                         (freq! o hz)
                         (.setValueAtTime (.-detune o) cents t)
                         (.setValueAtTime (.-gain g) (/ 1 (Math/sqrt n)) t)
                         (.setValueAtTime (.-pan sp) pan t)
                         (.connect g sp)
                         (.connect sp gain)
                         (start! o g (if (> n 1) phase 0))))
                     layout)]
    (when-let [sub (:sub-osc value)]
      (when (pos? sub)
        (let [o (.createOscillator ac)
              g (.createGain ac)
              c (case synth (:square :pulse) 1.27324 :tri 0.81057 :sine 1.0 0.63662)]
          (set! (.-type o) "sine")
          (freq! o (/ hz 2))
          (when from
            (.setValueAtTime (.-frequency o) (/ from 2) t)
            (when (and (number? gl) (pos? gl))
              (.exponentialRampToValueAtTime (.-frequency o) (/ hz 2) (+ t (min 5 gl)))))
          (.setValueAtTime (.-gain g) (* (min 1 sub) c) t)
          (.connect g gain)
          (start! o g 0))))
    (.setValueAtTime (.-gain gain) 0.0001 t)
    (.linearRampToValueAtTime (.-gain gain) peak (+ t atk))
    (.exponentialRampToValueAtTime (.-gain gain) 0.0001 stop-t)
    (.setValueAtTime (.-pan panner) (float pan) t)
    (.connect gain panner)
    (.connect panner dest)
    (first oscs)))

(defn- fill-noise!
  "Fill data with noise of colour (SYN6 A7; Math/random as the source).
   → the gain factor of the colour (dust: 1/0.3, so its impulses are not
   scaled down like continuous noise)."
  [^js data color density sr freq]
  (let [n    (.-length data)
        w    #(- (* 2 (Math/random)) 1)]
    (case color
      :pink
      (let [b (js/Float64Array. 7)]
        (dotimes [i n]
          (let [x (w)]
            (aset b 0 (+ (* 0.99886 (aget b 0)) (* x 0.0555179)))
            (aset b 1 (+ (* 0.99332 (aget b 1)) (* x 0.0750759)))
            (aset b 2 (+ (* 0.96900 (aget b 2)) (* x 0.1538520)))
            (aset b 3 (+ (* 0.86650 (aget b 3)) (* x 0.3104856)))
            (aset b 4 (+ (* 0.55000 (aget b 4)) (* x 0.5329522)))
            (aset b 5 (- (* -0.7616 (aget b 5)) (* x 0.0168980)))
            (aset data i (* 0.11 (+ (aget b 0) (aget b 1) (aget b 2) (aget b 3)
                                    (aget b 4) (aget b 5) (aget b 6) (* x 0.5362))))
            (aset b 6 (* x 0.115926))))
        1)
      :brown
      (let [a (Math/exp (/ (* -2 Math/PI 20) sr))
            k (Math/sqrt (/ (+ 1 a) (- 1 a)))]
        (loop [i 0 y 0]
          (when (< i n)
            (let [y' (+ (* a y) (* (- 1 a) (w)))]
              (aset data i (* k y'))
              (recur (inc i) y'))))
        1)
      :dust
      (let [p (/ (max 1 (min 2000 (or density 20))) sr)]
        (dotimes [i n]
          (aset data i (if (< (Math/random) p) (w) 0)))
        (/ 1 0.3))
      :lfsr
      (let [clock (min (/ sr 2) (if freq (* 16 freq) 8000))
            inc'  (/ clock sr)]
        (loop [i 0 ph 1 r (bit-or 1 (Math/floor (* 32767 (Math/random)))) hold 0]
          (when (< i n)
            (let [[ph r hold] (loop [ph (+ ph inc') r r hold hold]
                                (if (>= ph 1)
                                  (let [bit (bit-and (bit-xor r (bit-shift-right r 1)) 1)
                                        r'  (bit-or (bit-shift-right r 1) (bit-shift-left bit 14))]
                                    (recur (dec ph) r' (if (= 1 (bit-and r' 1)) 1 -1)))
                                  [ph r hold]))]
              (aset data i hold)
              (recur (inc i) ph r hold))))
        1)
      (do (dotimes [i n] (aset data i (w)))
          1))))

(defn- make-noise
  ([ac t dur amp pan dest] (make-noise ac t dur amp pan dest nil))
  ([ac t dur amp pan dest opts]
  (let [buf-size (max 1 (* (.-sampleRate ac) (int (Math/ceil dur))))
        buf  (.createBuffer ac 1 buf-size (.-sampleRate ac))
        data (.getChannelData buf 0)
        k    (fill-noise! data (:color opts) (:density opts) (.-sampleRate ac) (:freq opts))
        src  (.createBufferSource ac)
        gain (.createGain ac)
        panner (.createStereoPanner ac)
        pk   (* 0.3 k (float amp))]
    (set! (.-buffer src) buf)
    (.setValueAtTime (.-gain gain) pk t)
    (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t dur))
    (.setValueAtTime (.-pan panner) (float pan) t)
    (.connect src gain)
    (.connect gain panner)
    (.connect panner dest)
    (.start src t)
    (.stop src (+ t dur)))))

;;; Synthesis dispatch

(defn- worklet-target
  "The WASM worklet that play-event dispatches to for context ac:
   {:node :ready? :master}. A registered offline graph has its own worklet and
   master gain; otherwise the real-time atoms."
  [ac]
  (if-let [g (offline-graph ac)]
    {:node (:worklet-node g) :ready? true :master (:master-gain g)}
    {:node @worklet-node :ready? @worklet-ready? :master @master-gain}))

(defn- worklet-trigger!
  "Send a trigger message to the AudioWorklet. Returns true if worklet is ready.
   dest must be the target's master gain — the worklet always outputs there and
   cannot be redirected (decision E1: same rule offline and in real time)."
  [{:keys [node ready? master]} value t dest]
  (when (and ready? node (= dest master))
    (.. ^js node -port
        (postMessage #js {:type "trigger" :value value :time t}))
    true))

(defn- worklet-trigger-v3!
  "Send a trigger to the WASM worklet with explicit synthesis parameters, the
   event's insert params (Phase TL7a), its orbit sends (Phase TL7b) and its
   envelopes / pitch modulation (Phase TL7c, pm from inserts/pitch-params;
   gate = event length in seconds or nil). ins, snd, pm nil sends the smaller
   trigger_v2 message. Returns true if ready. dest must be the target's master
   gain — the worklet always outputs there."
  [{:keys [node ready? master]} value t amp attack decay pan ins snd pm gate dest]
  (when (and ready? node (= dest master))
    (when debug? (js/console.log "[REPuLse] triggering WASM:" value "at t=" t "amp=" amp))
    (.. ^js node -port
        (postMessage
          (if (or ins snd pm)
            ;; Slot layout = lib.rs trigger_v3 (appended, never reordered):
            ;; 0–3 voice · 4–11 inserts · 12–14 room delay orbit · 15–40 TL7c.
            ;; 41–43 SYN6 cutoff LFO (after TL7c's slots).
            (let [lfo (:lp-lfo ins)
                  c?  (or (inserts/tl7c? ins pm) (some? lfo))]
              #js {:type "trigger_v3" :value value :time t
                   :params (js/Float32Array.from
                             (clj->js (cond-> (into [amp attack decay pan]
                                                    (inserts/wasm-slots (or ins inserts/off)))
                                        (or snd c?) (into (orbits/wasm-slots snd))
                                        c?          (into (inserts/wasm-slots-tl7c ins pm gate))
                                        lfo         (into [(:rate lfo) (:phase lfo) (:depth lfo)]))))})
            #js {:type "trigger_v2" :value value :time t
                 :amp amp :attack attack :decay decay :pan pan})))
    true))

;;; Per-event inserts on the JS path (Phase TL7a) — native nodes only, no DSP.

(defonce ^:private curve-cache (atom {}))

(defn- cached-curve
  "Shared WaveShaper curve for kind (:shape amount | :crush bits), keyed on the
   value rounded to 3 decimals. Small bounded cache: a signal-driven value would
   otherwise grow it without limit."
  [kind x]
  (let [x' (/ (Math/round (* x 1000)) 1000)
        k  [kind x']]
    (or (get @curve-cache k)
        (let [c (if (= kind :shape)
                  (inserts/shape-curve x' 4096)
                  (inserts/crush-curve x' 65536))]
          (when (>= (count @curve-cache) 64) (reset! curve-cache {}))
          (swap! curve-cache assoc k c)
          c))))

(defn- insert-biquad
  "BiquadFilterNode of type at freq/q. env (inserts/env-params map, TL7c)
   moves the cutoff from freq to freq·2^depth and back, following
   inserts/env-points: exponential ramps = straight lines in octaves."
  ([ac t type freq q] (insert-biquad ac t type freq q nil nil))
  ([ac t type freq q env gate]
   (let [f (.createBiquadFilter ac)
         p (.-frequency f)]
     (set! (.-type f) type)
     (if env
       (let [d (inserts/effective-depth freq (:depth env) (.-sampleRate ac))]
         (doseq [[pt e kind] (inserts/env-points env gate)
                 :let [v (* freq (Math/pow 2 (* d e)))]]
           (if (= kind :set)
             (.setValueAtTime p v (+ t pt))
             (.exponentialRampToValueAtTime p v (+ t pt)))))
       (.setValueAtTime p freq t))
     (.setValueAtTime (.-Q f) (inserts/q->webaudio type q) t)
     f)))

(defn- lp-lfo!
  "SYN6 C3 on the JS path: an OscillatorNode at the LFO rate, started at its
   phase (PeriodicWave), into the low-pass detune in cents. Returns f."
  [ac t ^js f lfo dur]
  (when lfo
    (let [o    (.createOscillator ac)
          g    (.createGain ac)
          ph   (* 2 Math/PI (:phase lfo))
          real (js/Float32Array. #js [0 (Math/sin ph)])
          imag (js/Float32Array. #js [0 (Math/cos ph)])]
      (.setPeriodicWave o (.createPeriodicWave ac real imag #js {:disableNormalization true}))
      (.setValueAtTime (.-frequency o) (:rate lfo) t)
      (.setValueAtTime (.-gain g) (* 1200 (:depth lfo)) t)
      (.connect o g)
      (.connect g (.-detune f))
      (.start o t)
      (.stop o (+ t (if (number? dur) dur 10)))))
  f)

(defn- vowel-stage
  "Formant bank for vowel kw (TL7c): in → 5 band-passes × formant gains → out
   (gain inserts/vowel-makeup). → {:in node :out node :nodes [all]}"
  [ac t kw]
  (let [[freqs dbs bws] (get inserts/vowel-table kw)
        in    (.createGain ac)
        out   (.createGain ac)
        bands (mapv (fn [f db bw]
                      (let [bq (insert-biquad ac t "bandpass" f (/ f bw))
                            g  (.createGain ac)]
                        (.setValueAtTime (.-gain g) (Math/pow 10 (/ db 20)) t)
                        (.connect in bq)
                        (.connect bq g)
                        (.connect g out)
                        [bq g]))
                    freqs dbs bws)]
    (.setValueAtTime (.-gain out) inserts/vowel-makeup t)
    {:in in :out out :nodes (into [in out] (mapcat identity bands))}))

(defn- pitch-mods!
  "Pitch envelope and vibrato (TL7c, pm from inserts/pitch-params) on the
   detune AudioParam of src (an OscillatorNode or AudioBufferSourceNode)
   starting at t. The vibrato LFO stops when src ends."
  [ac t ^js src pm gate]
  (let [param (.-detune src)]
    (when-let [env (:penv pm)]
      (let [cents (* 100 (:depth env))]
        (doseq [[pt e kind] (inserts/env-points env gate)]
          (if (= kind :set)
            (.setValueAtTime param (* cents e) (+ t pt))
            (.linearRampToValueAtTime param (* cents e) (+ t pt))))))
    (when (pos? (:vib pm 0))
      (let [lfo (.createOscillator ac)
            g   (.createGain ac)]
        (set! (.-type lfo) "sine")
        (.setValueAtTime (.-frequency lfo) (:vib pm) t)
        (.setValueAtTime (.-gain g) (* 100 (:vib-mod pm)) t)
        (.connect lfo g)
        (.connect g param)
        (.start lfo t)
        (.addEventListener src "ended"
                           (fn [] (try (.stop lfo) (.disconnect g) (catch :default _ nil))))))
    src))

(defonce ^:private coarse-warned? (atom false))

(defn- warn-coarse!
  "coarse has no native node (TL7c decision 2): warn once per session when an
   event with coarse takes the JS path."
  []
  (when-not @coarse-warned?
    (reset! coarse-warned? true)
    (js/console.warn "[REPuLse] coarse only works on synth voices (WASM); ignored for"
                     "samples, defsynth voices and tracks with an fx chain")))

(defn- insert-shaper [ac curve]
  (let [ws (.createWaveShaper ac)]
    (set! (.-curve ws) curve)
    (set! (.-oversample ws) "none")
    ws))

;; QA3 A5: per-event JS node chains waiting to be disconnected — one list
;; instead of one js/setTimeout per event. Entries {:at t-or-nil :track kw
;; :nodes [...]}: :at = end of the voice + 0.5 s (swept by tick!), nil = length
;; unknown (removed with the track, by clear-track! / retire-track! / stop!).
(defonce pending-disconnects (atom []))

(defn split-due
  "Pure. [due keep] of pending-disconnect entries: due = :at known and ≤ now."
  [entries now]
  (let [due? (fn [{:keys [at]}] (and (some? at) (<= at now)))]
    [(filterv due? entries) (filterv (complement due?) entries)]))

(defn- disconnect-entries! [entries]
  (doseq [{:keys [nodes]} entries
          n nodes]
    (try (.disconnect n) (catch :default _ nil))))

(defn- sweep-disconnects!
  "Disconnect every pending chain whose voice has ended by now."
  [now]
  (let [[due keep] (split-due @pending-disconnects now)]
    (when (seq due)
      (reset! pending-disconnects keep)
      (disconnect-entries! due))))

(defn- disconnect-track-chains!
  "Disconnect and drop every pending chain of track-name."
  [track-name]
  (let [[mine others] ((juxt filterv remove) #(= track-name (:track %)) @pending-disconnects)]
    (when (seq mine)
      (reset! pending-disconnects (vec others))
      (disconnect-entries! mine))))

(defn- schedule-disconnect!
  "Disconnect nodes dur seconds (+ 0.5 s) after t. dur is a number or a Promise
   of one (sample length, known once the buffer is loaded). nil, non-finite or a
   Promise resolving to nil/non-finite = unknown: the chain stays until its
   track is cleared or retired, or (stop). Offline contexts skip this: the
   context is discarded after rendering."
  [ac t nodes dur track-name]
  (when-not (offline-context? ac)
    (let [at    (fn [d] (when (and (number? d) (js/isFinite d)) (+ t d 0.5)))
          entry {:at (when (number? dur) (at dur)) :track track-name :nodes nodes}]
      (swap! pending-disconnects conj entry)
      (when (and dur (not (number? dur)) (fn? (.-then dur)))
        ;; Known once the buffer is loaded: set :at on this very entry (it may
        ;; already be gone when the track was cleared meanwhile).
        (.then dur (fn [d]
                     (when-let [t' (at d)]
                       (swap! pending-disconnects
                              (fn [es] (mapv #(if (identical? (:nodes %) nodes) (assoc % :at t') %) es))))))))))

(defn- event-output
  "Return the node a JS voice should connect to. If the event has insert params
   (ins, from inserts/insert-params), build lpf → hpf → bpf → vowel → crush →
   shape → postgain (only the stages present; filter envelopes from TL7c ride
   on their biquad, released at gate seconds) ending in dest, and schedule
   disconnection after the voice ends. gain ≠ 1 adds a leading GainNode
   (defsynth voices, whose amp the audio layer does not control). Orbit sends
   (snd, from orbits/send-params, Phase TL7b) add an output GainNode at the end
   of the chain that feeds dest (dry) and the room/delay inputs of the event's
   orbit. Without any of these, return dest — no extra nodes. coarse has no
   node here (warned once)."
  ([ac t ins snd dest dur] (event-output ac t ins snd dest dur 1.0 nil nil))
  ([ac t ins snd dest dur gain gate] (event-output ac t ins snd dest dur gain gate nil))
  ([ac t ins snd dest dur gain gate track-name]
   (if (and (nil? ins) (nil? snd) (== gain 1))
     dest
     (let [{:keys [lpf resonance hpf hpq bpf bpq shape crush
                   lp-env hp-env bp-env vowel coarse postgain]} ins
           node   (fn [n] {:in n :out n :nodes [n]})
           levels (when crush (inserts/crush-levels crush))
           _      (when coarse (warn-coarse!))
           stages (cond-> []
                    (not (== gain 1))
                    (conj (node (let [n (.createGain ac)]
                                  (.setValueAtTime (.-gain n) gain t)
                                  n)))
                    lpf    (conj (node (lp-lfo! ac t (insert-biquad ac t "lowpass" lpf resonance lp-env gate)
                                                 (:lp-lfo ins) dur)))
                    hpf    (conj (node (insert-biquad ac t "highpass" hpf hpq hp-env gate)))
                    bpf    (conj (node (insert-biquad ac t "bandpass" bpf bpq bp-env gate)))
                    vowel  (conj (vowel-stage ac t vowel))
                    levels (conj (node (insert-shaper ac (cached-curve :crush crush))))
                    (and shape (pos? shape))
                    (conj (node (insert-shaper ac (cached-curve :shape shape))))
                    (and postgain (not= postgain 1.0))
                    (conj (node (let [n (.createGain ac)]
                                  (.setValueAtTime (.-gain n) postgain t)
                                  n)))
                    snd    (conj (node (.createGain ac))))
           ;; coarse alone builds no stage: connect straight to dest.
           stages (if (seq stages) stages [(node (.createGain ac))])
           sends  (when snd (orbits/send! ac t (:out (peek stages)) snd (output-node ac)))]
       (doseq [[a b] (partition 2 1 stages)]
         (.connect (:out a) (:in b)))
       (.connect (:out (peek stages)) dest)
       (schedule-disconnect! ac t (into (vec (mapcat :nodes stages)) sends) dur track-name)
       (:in (first stages))))))

(defn- sample-length
  "Promise of how long sample n of bank sounds for the event params in extra
   (playback-plan :length), or nil when the bank has no such sample."
  [ac bank n extra]
  (when-let [url (samples/get-url bank n)]
    (-> (samples/get-buffer! url ac)
        (.then (fn [buf] (:length (samples/playback-plan extra (.-duration buf)))))
        (.catch (fn [_] nil)))))

(defn- js-kit
  "JS fallback of a synthesized kit voice (SYN6 B4): the nearest JS voice
   from voices/js-drum. No choke, tone ignored. Since the default-kit decision
   (2026-11-21) this is also what bare :bd :sd :hh :oh … play on the JS path;
   the old fixed JS drums (js-synth) are gone."
  [ac t drum value amp pan dest]
  (let [p (voices/js-drum drum value)]
    (case (:kind p)
      :kick   (make-kick ac t amp pan dest (:f-start p) (:f-end p) (:sweep p) (:dur p))
      :sine   (make-sine ac t (:freq p) (:dur p) amp 0.001 pan dest)
      :snare  (make-snare ac t amp pan dest (:dur p))
      :hihat  (make-hihat ac t amp pan dest (:dur p) (:gain p) (:hpf p))
      :square (make-square ac t (:freq p) (:dur p) amp 0.001 pan dest 0.5)
      (make-sine ac t 440 1.5 amp 0.001 pan dest))))

(def ^:private sample-param-keys
  [:rate :begin :end :loop :loop-begin :loop-end :unit :accelerate :legato :sustain :cut])

(defn- sample-extra
  "Sample playback params of an event value plus the event timing (TL6, S12).
   With pitch modulation (pm, TL7c) :on-source applies pitch! to the source."
  ([value timing] (sample-extra value timing nil nil))
  ([value timing pitch! pm]
   (cond-> (merge (select-keys value sample-param-keys)
                  (select-keys timing [:dur :cycle-dur]))
     pm (assoc :on-source (fn [src _t'] (pitch! src))))))

(defn play-event
  "Play one event value at audio time t. timing — optional {:dur :cycle-dur} in
   seconds (TL6): needed by legato, looped samples and unit :c.
   Per-event inserts (TL7a: lpf hpf bpf crush shape) run in WASM when the worklet
   takes the event, else as native Web Audio nodes in front of dest; gain and
   velocity multiply the voice amp. Orbit sends (TL7b: room delay orbit) go out
   on the worklet's send buses or through per-event send GainNodes; size,
   delay-time and delay-feedback set the event's orbit at t.
   TL7c: filter envelopes ride on the inserts (gate = (:dur timing)); penv/vib
   modulate tonal voices and samples (not drums, noise, ks or defsynth);
   nudge shifts t; delay-sync sets the orbit delay in cycles."
  ([ac t value] (play-event ac t value nil nil))
  ([ac t value track-name] (play-event ac t value track-name nil))
  ([ac t0 value0 track-name timing]
   ;; An OfflineAudioContext without a registered graph has no worklet — skip
   ;; to JS fallbacks (TEST1 harness). A registered offline graph (EXP1 export)
   ;; takes exactly the real-time branches.
   (let [;; SYN6 C1/C2: instrument presets and accent expand into ordinary
         ;; event parameters (a defsynth of the same name wins).
         value    (if (and (map? value0) (not (synth/lookup-synth (:synth value0))))
                    (voices/expand-preset value0)
                    value0)
         nud      (inserts/nudge value)
         t        (if (zero? nud)
                    t0
                    (cond-> (+ t0 nud) (offline-context? ac) (max 0)))
         offline? (and (offline-context? ac) (nil? (offline-graph ac)))
         wt       (when-not offline? (worklet-target ac))
         dest     (output-for-track ac track-name)
         ins      (let [ins (inserts/insert-params value)
                        ;; SYN6 C3: cutoff LFO rides on the low-pass.
                        lfo (when (map? value) (voices/lp-lfo value t (:cycle-dur timing)))]
                    (cond-> ins lfo (assoc :lp-lfo lfo)))
         snd      (orbits/send-params value)
         af       (inserts/amp-factor value)
         pm       (inserts/pitch-params value)
         gate     (:dur timing)
         ;; TL7c: pitch envelope / vibrato on a JS voice's source node.
         pitch!   (fn [src] (when (and pm src) (pitch-mods! ac t src pm gate)) src)
         ;; Destination for a JS voice sounding dur seconds. Only called on the
         ;; fallback branches, so a worklet event never builds a node chain; WASM
         ;; eligibility is still decided on the original dest.
         js-dest  (fn [dur] (event-output ac t ins snd dest dur 1.0 gate track-name))]
     ;; TL7b: size / delay-time / delay-feedback set the event's orbit, whichever
     ;; path plays the voice (and even when its own sends are 0). Rests carry
     ;; the keys too but are not events.
     (when (and (map? value) (not= :_ (:note value)))
       (orbits/apply-orbit-settings! ac (orbits/orbit-index (:orbit value)) t value
                                     (:cycle-dur timing)))
     (cond
       ;; Silence / rest
       (= value :_) nil

       ;; User-defined synth — check BEFORE built-in dispatch.
       ;; Produced by (synth :my-synth pat) when :my-synth is registered via defsynth.
       (and (map? value) (:synth value) (synth/lookup-synth (:synth value)))
       (let [synth-def (synth/lookup-synth (:synth value))]
         ;; The defsynth's own envelope sets its length (unknown here); 30 s
         ;; bounds the cleanup so long pads are never cut.
         (synth/play-synth! ac t synth-def value (event-output ac t ins snd dest 30.0 af gate track-name)))

       ;; Bare defsynth name — (seq :lfo-sine) triggers a parameterless synth,
       ;; e.g. a bus writer. Note names keep their meaning.
       (and (keyword? value) (not (theory/note-keyword? value)) (synth/lookup-synth value))
       (synth/play-synth! ac t (synth/lookup-synth value) {} dest)

       ;; Parameter map {:note … :amp … :attack … :decay … :pan … :synth …}
       ;; from amp/attack/decay/release/pan/saw/square/noise/fm transformers
       (and (map? value) (:note value))
       (let [note     (:note value)
             amp-v    (float (* (:amp value 1.0) af))
             attack-v (float (:attack value 0.001))
             decay-v  (float (:decay value 1.5))
             pan-v    (float (:pan value 0.0))
             synth    (:synth value)
             wasm!    (fn [v] (worklet-trigger-v3! wt v t amp-v attack-v decay-v pan-v ins snd pm gate dest))
             tone-dur (+ attack-v decay-v)]
         (cond
           (= note :_) nil

           ;; SYN6: noise for every non-rest event (owner decision 4).
           (= synth :noise)
           (let [hz  (voices/event-hz value)
                 dv  (float (:decay value 0.3))]
             (or (when-not offline?
                   (worklet-trigger-v3! wt (voices/noise-string value hz) t amp-v 0.001 dv
                                        pan-v ins snd pm gate dest))
                 (make-noise ac t dv amp-v pan-v (js-dest dv)
                             (assoc (select-keys value [:color :density]) :freq hz))))

           ;; SYN6: band-limited oscillators — saw square pulse tri sine supersaw
           (voices/osc-voice? synth)
           (let [hz (if (theory/note-keyword? note) (theory/note->hz note) (double note))
                 v  (voices/with-defaults value)]
             (or (when-not offline? (wasm! (voices/osc-string v hz)))
                 (make-osc ac t hz decay-v amp-v attack-v pan-v (js-dest tone-dur) v pitch!)))

           (= synth :fm)
           (let [hz    (if (theory/note-keyword? note) (theory/note->hz note) (double note))
                 index (or (:index value) 1.0)
                 ratio (or (:ratio value) 2.0)]
             (or (when-not offline? (wasm! (str "fm:" hz ":" index ":" ratio)))
                 ;; JS fallback: sine (FM needs two connected oscillators — WASM only)
                 (pitch! (make-sine ac t hz decay-v amp-v attack-v pan-v (js-dest tone-dur)))))

           (contains? voices/fm-presets synth)
           (let [hz     (if (theory/note-keyword? note) (theory/note->hz note) (double note))
                 preset (clojure.core/name synth)]
             (or (when-not offline? (wasm! (str "fm:" preset ":" hz)))
                 (pitch! (make-sine ac t hz decay-v amp-v attack-v pan-v (js-dest tone-dur)))))

           (contains? voices/string-presets synth)
           (let [hz     (if (theory/note-keyword? note) (theory/note->hz note) (double note))
                 preset (clojure.core/name synth)]
             (or (when-not offline? (wasm! (str "ks:" preset ":" hz)))
                 (make-sine ac t hz decay-v amp-v attack-v pan-v (js-dest tone-dur))))

           ;; Standard note/sample dispatch
           (keyword? note)
           (if (theory/note-keyword? note)
             (let [hz (theory/note->hz note)]
               (or (when-not offline? (wasm! (str hz)))
                   (pitch! (make-sine ac t hz decay-v amp-v attack-v pan-v (js-dest tone-dur)))))
             (let [route (voices/drum-route note @samples/active-bank-prefix samples/has-bank?)]
               (case (:kind route)
                 :sample
                 (let [bank  (:bank route)
                       extra (sample-extra value timing pitch! pm)]
                   (samples/play! ac t bank 0 amp-v pan-v extra
                                  (js-dest (when ins (sample-length ac bank 0 extra)))))
                 ;; SYN6 kit voice — the default drum kit (decision 2026-11-21,
                 ;; see voices/drum-route): tune / tone / decay travel in the string.
                 :synth-drum
                 (or (when-not offline? (wasm! (voices/drum-string (:name route) value)))
                     (js-kit ac t (:name route) value amp-v pan-v (js-dest 2.0)))
                 ;; :unknown — a quiet 440 Hz sine (the engine plays any
                 ;; unparsable string as 440 Hz); sound-check warns.
                 (or (when-not offline? (wasm! (name note)))
                     (make-sine ac t 440 1.5 amp-v 0.001 pan-v (js-dest 1.5))))))

           (number? note)
           (or (when-not offline? (wasm! (str note)))
               (pitch! (make-sine ac t note decay-v amp-v attack-v pan-v (js-dest tone-dur))))))

       ;; Map {:bank :bd :n 2} from (sound :bd 2) — always use samples
       (and (map? value) (:bank value))
       (let [{:keys [bank n]} value
             amp-v  (float (* (:amp value 1.0) af))
             pan-v  (float (:pan  value 0.0))
             extra  (sample-extra value timing pitch! pm)]
         (if (samples/has-bank? bank)
           (samples/play! ac t bank n amp-v pan-v extra
                          (js-dest (when ins (sample-length ac bank n extra))))
           (if offline?
             (make-sine ac t 440 1.5 1.0 0.001 0.0 dest)
             (or (worklet-trigger-v3! wt (name bank) t amp-v 0.001 1.5 pan-v ins snd pm gate dest)
                 (make-sine ac t 440 1.5 amp-v 0.001 pan-v (js-dest 1.5))))))

       ;; Keyword — note name → Hz tone, or sample bank
       (keyword? value)
       (if (theory/note-keyword? value)
         (let [hz (theory/note->hz value)]
           (if offline?
             (make-sine ac t hz 1.5 1.0 0.001 0.0 dest)
             (or (worklet-trigger! wt (str hz) t dest)
                 (make-sine ac t hz 1.5 1.0 0.001 0.0 dest))))
         (let [route (voices/drum-route value @samples/active-bank-prefix samples/has-bank?)]
           (case (:kind route)
             :sample     (samples/play! ac t (:bank route) 0 1.0 0.0 {} dest)
             :synth-drum (or (when-not offline?
                               (worklet-trigger! wt (voices/drum-string (:name route) nil) t dest))
                             (js-kit ac t (:name route) nil 1.0 0.0 dest))
             ;; :unknown — 440 Hz sine
             (if offline?
               (make-sine ac t 440 1.5 1.0 0.001 0.0 dest)
               (or (worklet-trigger! wt (name value) t dest)
                   (make-sine ac t 440 1.5 1.0 0.001 0.0 dest))))))

       ;; Map with :synth :noise — no :note key
       (and (map? value) (= (:synth value) :noise))
       (let [amp-v  (float (* (:amp value 1.0) af))
             decay-v (float (:decay value 0.3))
             pan-v  (float (:pan value 0.0))]
         (or (when-not offline?
               (worklet-trigger-v3! wt (voices/noise-string value nil) t amp-v 0.001 decay-v
                                    pan-v ins snd pm gate dest))
             (make-noise ac t decay-v amp-v pan-v (js-dest decay-v)
                         (select-keys value [:color :density]))))

       ;; Number — frequency in Hz
       (number? value)
       (if offline?
         (make-sine ac t value 1.5 1.0 0.001 0.0 dest)
         (or (worklet-trigger! wt (str value) t dest)
             (make-sine ac t value 1.5 1.0 0.001 0.0 dest)))

       :else (make-sine ac t 440 1.5 1.0 0.001 0.0 dest)))))

;;; Offline WASM worklet (Phase EXP1, decision E11)

(def ^:private offline-init-timeout-ms 5000)

(defn init-offline-worklet!
  "Load the WASM worklet into an OfflineAudioContext, connect it to dest (the
   offline master gain) and initialise the engine with freshly fetched WASM
   bytes. → Promise<node>; rejects on any addModule/fetch failure, on the
   worklet's \"error\" message and after 5 s without \"ready\"."
  [^js offline dest]
  (js/Promise.
   (fn [resolve reject]
     (let [done?  (atom false)
           finish (fn [f v] (when-not @done? (reset! done? true) (f v)))
           timer  (js/setTimeout
                   #(finish reject (js/Error. "WASM worklet did not report ready within 5 s"))
                   offline-init-timeout-ms)
           fail   (fn [e] (js/clearTimeout timer) (finish reject e))]
       (if-let [worklet (.-audioWorklet offline)]
         (-> (.addModule worklet (public-url "worklet.js"))
             (.then (fn []
                      (let [node (js/AudioWorkletNode. offline "repulse-processor"
                                                       (clj->js orbits/worklet-outputs))]
                        (.connect node dest 0)
                        (orbits/connect-worklet! offline node dest)
                        (set! (.. node -port -onmessage)
                              (fn [e]
                                (let [d (.-data e)]
                                  (case (.-type d)
                                    "ready" (do (js/clearTimeout timer) (finish resolve node))
                                    "error" (fail (js/Error. (str "WASM init failed: " (.-message d))))
                                    "synced" (when-let [cb (aget node "__repulseOnSynced")]
                                               (cb (.-id d)))
                                    nil))))
                        (-> (js/fetch (public-url "repulse_audio_bg.wasm"))
                            (.then (fn [^js resp]
                                     (if (.-ok resp)
                                       (.arrayBuffer resp)
                                       (throw (js/Error. (str "WASM fetch failed (HTTP " (.-status resp) ")"))))))
                            (.then (fn [buf]
                                     (.. node -port
                                         (postMessage #js {:type "init" :wasmBytes buf} #js [buf]))))))))
             (.catch fail))
         (fail (js/Error. "AudioWorklet unavailable offline")))))))

(defonce ^:private sync-counter (atom 0))

(defn sync-worklet!
  "Post {type: \"sync\", id} to an offline worklet node and resolve once it
   answers \"synced\" — every message posted before has then been handled.
   Rejects after 5 s."
  [^js node]
  (js/Promise.
   (fn [resolve reject]
     (let [id    (swap! sync-counter inc)
           timer (js/setTimeout #(reject (js/Error. "WASM worklet did not answer sync within 5 s"))
                                offline-init-timeout-ms)]
       (aset node "__repulseOnSynced"
             (fn [got] (when (= got id) (js/clearTimeout timer) (resolve true))))
       (.. node -port (postMessage #js {:type "sync" :id id}))))))

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

(defn export-frames
  "Frame count for secs at sample rate sr — shared by the offline render and
   the real-time capture (Phase EXP1, decision E3)."
  [secs sr]
  (Math/round (* secs sr)))

(defn tween-state-for
  "Pure core of arm-transitions!: detect tween descriptors in the first cycle of
   pattern → {param {:tween pv :start-time start-time}}."
  [pattern start-time]
  (let [sp  {:start [0 1] :end [1 1]}
        evs (try (vec (core/query pattern sp)) (catch :default _ []))]
    (into {}
          (for [ev   evs
                :let [v (:value ev)]
                :when (map? v)
                [k pv] v
                :when (tween? pv)]
            [k {:tween pv :start-time start-time}]))))

(defn rearm-tweens
  "Set every :start-time in a tween-state map (track → param → ts) to t0
   (decision 3: exports start tweens at the export's bar 0)."
  [tween-state t0]
  (update-vals tween-state (fn [m] (update-vals m #(assoc % :start-time t0)))))

(defn resolve-tweens
  "Interpolate the tweened params of event value raw at audio time t.
   tween-st — {param ts} of the event's track; cycle-dur — seconds per bar."
  [raw tween-st t cycle-dur]
  (if (and (map? raw) (seq tween-st))
    (reduce-kv (fn [m k ts]
                 (if (tween? (get m k))
                   (assoc m k (interp-tween ts t cycle-dur))
                   m))
               raw tween-st)
    raw))

;; QA3 A7: defonce — a hot reload must not lose the running scheduler.
(defonce scheduler-state
  (atom {:playing?     false
         :tracks       {}     ; keyword → Pattern
         :muted        #{}    ; set of muted track keywords
         :cycle        0
         :cycle-dur    2.0    ; seconds per cycle — 120 BPM, 1 cycle = 1 bar (4 beats)
         ;; QA3 A1 tempo anchor: cycle x starts at
         ;; anchor-time + (x − anchor-cycle) × cycle-dur (repulse.tempo).
         :anchor-time  0.0
         :anchor-cycle 0
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
         ;; :start-time t0 :end-time t1} — read by the status head (REC text).
         :capture      nil
         ;; Called after a :stop song has finished (and its tail rung out).
         :on-song-end  nil
         ;; QA1: (fn [message]) — shows a track's query error on the output line.
         :on-query-error nil}))

;;; QA1 — per-track query isolation

;; [track message] pairs already reported; each is shown once. A track's entries
;; are cleared when play-track! replaces it, all of them by stop!.
(defonce ^:private reported-query-errors (atom #{}))

;; Test handle: track → onsets scheduled since the last stop!.
(defonce scheduled-counts (atom {}))

(defn query-error-message
  "Pure. Output-line text for a query error of track (:_ = the pattern result)."
  [track msg]
  (if (= track :_)
    (str "Error in pattern: " msg)
    (str "Error in track " track ": " msg)))

(defn report-query-error!
  "A track's pattern threw while being queried (scheduler or export). Reported
   once per [track message]: a console warning and the :on-query-error callback."
  [track e]
  (let [msg (or (ex-message e) (str e))
        k   [track msg]]
    (when-not (contains? @reported-query-errors k)
      (swap! reported-query-errors conj k)
      (js/console.warn "[REPuLse] query error in track" (str track) e)
      (when-let [f (:on-query-error @scheduler-state)]
        (f (query-error-message track msg))))))

(defn- clear-query-errors!
  ([] (reset! reported-query-errors #{}))
  ([track] (swap! reported-query-errors
                  (fn [s] (into #{} (remove #(= track (first %))) s)))))

;; Seconds a finished :end :stop song keeps the scheduler alive so tails ring out.
(def song-release-secs 2.0)

;; A fresh song start never begins on a bar that starts less than this far ahead
;; of currentTime, so the first bar is never cut.
(def ^:private song-start-margin-secs 0.05)

;; The same margin for a fresh start that a real-time capture is waiting on.
;; The first bar is scheduled on a later tick than the one that resolves the
;; origin, and schedule-event! drops events whose time has passed, so a
;; main-thread stall longer than the margin used to lose the song's downbeat
;; from the export (flaky e2e/export-parity.spec.ts on busy CI runners).
;; An export can afford to wait: the capture starts at bar 0 whenever that is.
(def ^:private capture-start-margin-secs 0.5)

(defn first-song-bar
  "Pure. The first bar index ≥ cycle whose start time is at least margin
   seconds after now. Bar x starts at anchor-time + (x − anchor-cycle) ×
   cycle-dur (QA3 tempo anchor; the 4-arity uses the anchor 0.0 / 0)."
  ([cycle cycle-dur now margin]
   (first-song-bar cycle cycle-dur now margin 0.0 0))
  ([cycle cycle-dur now margin anchor-time anchor-cycle]
   (max cycle (int (Math/ceil (+ anchor-cycle
                                 (/ (- (+ now margin) anchor-time) cycle-dur)))))))

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
  "Set the tempo in BPM. One cycle = one bar (4 beats). QA3: the change takes
   effect at the next unscheduled bar, which starts exactly where the last
   scheduled bar ends (tempo/retempo)."
  [bpm]
  (swap! scheduler-state tempo/retempo
         (if-let [ac @ctx] (.-currentTime ac) 0)
         (/ 240.0 (coerce-bpm bpm))))

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
            value    (resolve-tweens (:value ev) tween-st t cycle-dur)]
        (play-event ac t value track-name {:dur dur :cycle-dur cycle-dur})
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

;; SYN6 C4: per track, the pitches of the latest onset group and the one before
;; ({track-name {:t :cur :prev}}). Cleared by stop! and clear-track!.
(defonce ^:private glide-state (atom {}))

(defn- with-glide
  "Run voices/assign-glide over one track's onsets [[ev t dur] …] with that
   track's glide state (stored back) → the onsets, sorted by time, values with
   :glide-from where a note glides."
  [track-name onsets]
  (let [[evs st] (voices/assign-glide
                   (get @glide-state track-name)
                   (map (fn [[ev t dur]]
                          {:t t :hz (voices/event-hz (:value ev)) :value (:value ev)
                           :ev ev :dur dur})
                        onsets))]
    (swap! glide-state assoc track-name st)
    (map (fn [{:keys [ev t dur value]}] [(assoc ev :value value) t dur]) evs)))

(defn schedule-cycle! [ac state cycle]
  (let [{:keys [tracks muted cycle-dur]} state
        snap (clock-snapshot ac)]
    (if-let [{s :song origin :origin} (:song state)]
      ;; ── Song mode: the song decides which tracks sound in this bar ─────
      ;; song-onsets queries each track in song time and places the events at
      ;; song bar (cycle − origin); it returns [] in pre-roll and after the
      ;; end of a :stop song. Mute always wins (decision 8).
      (when origin
        (doseq [[track onsets] (group-by first
                                         (for [{:keys [track event]} (song/song-onsets s tracks muted (- cycle origin)
                                                                                        report-query-error!)]
                                           (let [whole-start (core/rat->float (:start (:whole event)))
                                                 whole-end   (core/rat->float (:end   (:whole event)))]
                                             [track event
                                              (tempo/time-at-cycle state (+ origin whole-start))
                                              (* (- whole-end whole-start) cycle-dur)])))
                [event t dur] (with-glide track (map rest onsets))]
          (swap! scheduled-counts update track (fnil inc 0))
          (schedule-event! ac state snap track event t dur)))
      ;; ── Free-running tracks (no song) ──────────────────────────────────
      (let [sp {:start [cycle 1] :end [(inc cycle) 1]}]
        (doseq [[track-name pattern] tracks]
          (when (and pattern (not (contains? muted track-name)))
            ;; Only onsets trigger: a whole that crosses a cycle boundary (or is
            ;; cut by a structure-aware combinator) also yields non-onset
            ;; fragments, which must not re-trigger the sound. Continuous (analog)
            ;; events have no :whole and are skipped here on purpose; the user is
            ;; warned about structureless signals at eval time.
            ;; QA1: a track whose query throws is skipped for this cycle and
            ;; reported once; the other tracks and the cycle counter go on.
            (when-let [evs (try (vec (core/query-onsets pattern sp))
                                (catch :default e (report-query-error! track-name e) nil))]
              (doseq [[ev t dur]
                      (with-glide
                        track-name
                        (for [ev evs]
                          (let [whole-start (core/rat->float (:start (:whole ev)))
                                whole-end   (core/rat->float (:end   (:whole ev)))]
                            [ev
                             (tempo/time-at-cycle state whole-start)
                             (* (- whole-end whole-start) cycle-dur)])))]
                (swap! scheduled-counts update track-name (fnil inc 0))
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
            t0          (tempo/time-at-cycle state (+ origin start-bar))
            dur         (song/song-duration-secs s (/ 240.0 cd))
            t1          (+ t0 dur tail)
            start-frame (Math/round (* t0 sr))
            frames      (export-frames (+ dur tail) sr)]
        ;; Decision 3 (EXP1): every tween starts at the captured song's bar 0.
        ;; No event of the song has been scheduled yet (pre-roll bars are empty).
        (swap! scheduler-state update :tween-state rearm-tweens t0)
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
      (let [{:keys [cycle cycle-dur anchor-time anchor-cycle]} state
            margin    (if (= :waiting (:status (:capture state)))
                        capture-start-margin-secs
                        song-start-margin-secs)
            first-bar (first-song-bar cycle cycle-dur now margin
                                      (or anchor-time 0.0) (or anchor-cycle 0))]
        (swap! scheduler-state assoc-in [:song :origin] (- first-bar (:start-bar sg)))
        (arm-capture! ac @scheduler-state)))))

(declare stop!)

(defn- song-finished?
  "True once a :stop song has played its last bar and the release has passed.
   While a capture is active the capture's \"done\" handler stops instead."
  [state now]
  (let [{s :song origin :origin} (:song state)]
    (and s origin (= :stop (:end s)) (nil? (:capture state))
         (>= now (+ (tempo/time-at-cycle state (+ origin (:total s))) song-release-secs)))))

;; QA3 A5: tick! sweeps pending-disconnects every sweep-every-ticks calls
;; (10 × 25 ms = 250 ms).
(def ^:private sweep-every-ticks 10)
(defonce ^:private tick-count (atom 0))

(defn tick! []
  (let [ac (get-ctx)]
    (when (:playing? @scheduler-state)
      (let [now (.-currentTime ac)]
        (when (zero? (mod (swap! tick-count inc) sweep-every-ticks))
          (sweep-disconnects! now))
        (resolve-song-origin! ac now)
        (let [state @scheduler-state]
          (if (song-finished? state now)
            (let [on-end (:on-song-end state)]
              (stop!)
              (when on-end (on-end)))
            (let [{:keys [cycle lookahead]} state
                  cycle-start (tempo/time-at-cycle state cycle)]
              (when (< (- cycle-start now) lookahead)
                ;; QA1: the cycle always advances, even if scheduling throws —
                ;; otherwise the same cycle is retried every tick and all goes silent.
                (try
                  (schedule-cycle! ac state cycle)
                  (catch :default e
                    (js/console.error "[REPuLse] schedule-cycle! failed:" e))
                  (finally
                    (swap! scheduler-state update :cycle inc)))))))))))

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
    (let [now         (.-currentTime ac)
          start-cycle (int (Math/floor (tempo/cycle-at-time @scheduler-state now)))]
      (swap! scheduler-state assoc
             :playing?    true
             :cycle       start-cycle
             :on-beat     on-beat-fn
             :on-event    on-event-fn))
    (let [id (js/setInterval tick! 25)]
      (swap! scheduler-state assoc :interval-id id))
    (tick!)))

;; Phase WS5: {track-kw timeout-id} of tracks whose GainNode is ramping out
;; before it is disconnected (retire-track!).
(defonce ^:private retiring (atom {}))

(defn- cancel-retire-timers! []
  (doseq [[_ id] @retiring] (js/clearTimeout id))
  (reset! retiring {}))

(defn stop! []
  (reset! glide-state {})
  (clear-query-errors!)
  (reset! scheduled-counts {})
  (cancel-capture!)
  (cancel-retire-timers!)
  (when-let [node @worklet-node]
    (.. node -port (postMessage #js {:type "stop"})))
  ;; TL7b: every orbit back to its default size / delay-time / feedback
  (orbits/reset-orbits! @ctx)
  (when-let [id (:interval-id @scheduler-state)]
    (js/clearInterval id))
  (clear-visuals!)
  ;; Disconnect and clean up all per-track nodes
  (doseq [[_ {:keys [gain-node fx-chain]}] @track-nodes]
    (try (.disconnect gain-node) (catch :default _))
    (doseq [{:keys [plugin]} fx-chain]
      (try (.destroy ^js plugin) (catch :default _))))
  (reset! track-nodes {})
  ;; QA3 A5: every per-event JS chain goes with the tracks
  (disconnect-entries! @pending-disconnects)
  (reset! pending-disconnects [])
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
  (swap! scheduler-state update :tween-state assoc track-name
         (tween-state-for pattern (.-currentTime ac))))

(def retire-ramp-start-secs
  "Phase WS5: a retired track's gain ramp starts this far after the
   evaluation, so the events about to play start into the ramp."
  0.05)

(def retire-ramp-secs
  "Phase WS5: length of the click-free gain ramp to 0 of a retired track."
  0.03)

(defn- ramp-gain!
  "Ramp GainNode g from its current value to `to` over [t0, t1]."
  [^js g to t0 t1]
  (let [p (.-gain g)]
    (.cancelScheduledValues p 0)
    (.setValueAtTime p (.-value p) t0)
    (.linearRampToValueAtTime p to t1)))

(defn- revive-retiring!
  "A track re-declared while its retire ramp runs: cancel the pending
   disconnect and ramp its gain back to 1."
  [ac track-name]
  (when-let [id (get @retiring track-name)]
    (js/clearTimeout id)
    (swap! retiring dissoc track-name)
    (when-let [g (get-in @track-nodes [track-name :gain-node])]
      (let [now (.-currentTime ac)]
        (ramp-gain! g 1.0 now (+ now retire-ramp-secs))))))

(defn retire-track!
  "Phase WS5: remove a track the code no longer declares, click-free. It
   leaves :tracks and :muted now (no new events are scheduled), its GainNode
   ramps to 0 (retire-ramp-start-secs → + retire-ramp-secs), then the node is
   disconnected and its FX destroyed. stop! runs when no track is left by
   then. play-track! for the same name inside the ramp revives it."
  [track-name]
  (swap! scheduler-state (fn [s] (-> s
                                     (update :tracks dissoc track-name)
                                     (update :muted disj track-name)
                                     (update :tween-state dissoc track-name))))
  (let [ac @ctx
        tn (get @track-nodes track-name)
        finish! (fn []
                  (when (and (empty? (:tracks @scheduler-state))
                             (:playing? @scheduler-state))
                    (stop!)))]
    (if (and ac tn)
      (let [g       (:gain-node tn)
            now     (.-currentTime ac)
            t0      (+ now retire-ramp-start-secs)
            t1      (+ t0 retire-ramp-secs)
            wait-ms (+ (* 1000 (+ retire-ramp-start-secs retire-ramp-secs)) 50)]
        (ramp-gain! g 0.0 t0 t1)
        (when-let [old (get @retiring track-name)] (js/clearTimeout old))
        (swap! retiring assoc track-name
               (js/setTimeout
                (fn []
                  (swap! retiring dissoc track-name)
                  (disconnect-track-chains! track-name)
                  (try (.disconnect g) (catch :default _))
                  (doseq [{:keys [plugin]} (:fx-chain tn)]
                    (try (.destroy ^js plugin) (catch :default _)))
                  ;; Only drop the entry when it still is this node.
                  (when (identical? g (get-in @track-nodes [track-name :gain-node]))
                    (swap! track-nodes dissoc track-name))
                  (finish!))
                wait-ms)))
      (do (disconnect-track-chains! track-name)
          (finish!)))))

(defn play-track!
  "Add or replace a named track. Creates a per-track GainNode if needed.
   Starts the scheduler if not already running."
  [track-name pattern on-beat-fn on-event-fn]
  (let [ac (get-ctx)]
    ;; ensure-track-node!, arm-transitions!, and the state swap do not require a
    ;; running context — they just create/connect nodes and update atoms.  Only
    ;; ensure-running! must wait for .resume() so the scheduler clock is live.
    (revive-retiring! ac track-name)
    (clear-query-errors! track-name)
    (ensure-track-node! ac track-name)
    (arm-transitions! track-name pattern ac)
    (swap! scheduler-state update :tracks assoc track-name pattern)
    ;; The latest evaluation's callbacks win even while the scheduler already
    ;; runs: a project evaluation's on-event maps global source positions to
    ;; its buffer (SONG4) and must replace a stale one from an earlier
    ;; evaluation. nil (snippet previews) keeps the current callbacks.
    (swap! scheduler-state
           (fn [s] (cond-> s
                     on-beat-fn  (assoc :on-beat on-beat-fn)
                     on-event-fn (assoc :on-event on-event-fn))))
    (-> (.resume ac)
        (.then (fn [] (ensure-running! ac on-beat-fn on-event-fn))))))

;; Phase WS5: mute-track! / unmute-track! / solo-track! are gone — the muted
;; set is derived from the code's :mute / :solo flags on every reconciling
;; evaluation (repulse.eval-orchestrator) and never edited directly.

(defn clear-track! [track-name]
  (swap! glide-state dissoc track-name)
  (disconnect-track-chains! track-name)
  (when-let [id (get @retiring track-name)]
    (js/clearTimeout id)
    (swap! retiring dissoc track-name))
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
    (let [now         (.-currentTime ac)
          start-cycle (int (Math/floor (tempo/cycle-at-time @scheduler-state now)))]
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

(defn audible-cycle
  "QA3 A2: the cycle sounding now (floor of the cycle position at the audio
   clock), or nil when not playing. Unlike :cycle (the next cycle to schedule)
   this is the bar the listener hears."
  []
  (let [state @scheduler-state]
    (when-let [ac (and (:playing? state) @ctx)]
      (max 0 (int (Math/floor (tempo/cycle-at-time state (.-currentTime ac))))))))

;;; Tap tempo

(defonce ^:private tap-times (atom []))

(defn tap!
  "Register a tap for BPM detection. Returns computed BPM or nil if fewer than 2 taps."
  []
  ;; QA3 A7: monotonic clock — Date.now jumps with wall-clock adjustments.
  (let [now (.now js/performance)]
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
