(ns repulse.snippets.preview
  "Production snippet preview engine: sandboxed eval, preview-track cleanup,
   card state, and lightweight waveform drawing."
  (:require [repulse.audio :as audio]
            [repulse.core :as core]
            [repulse.fx :as fx]
            [repulse.lisp.core :as lisp]
            [repulse.snippets.sandbox :as sandbox]))

(def preview-timeout-ms 500)
(def default-preview-track :__preview__)

(defonce state
  (atom {:active-id nil
         :mode nil
         :tracks #{}
         :snapshot nil
         :errors {}
         :raf-id nil}))

(defn active-id [] (:active-id @state))
(defn active-mode [] (:mode @state))
(defn previewing? [id] (= (str id) (:active-id @state)))
(defn previewing-mode? [id mode]
  (and (previewing? id) (= mode (:mode @state))))
(defn error-for [id] (get-in @state [:errors (str id)]))

(defn- remember-track! [track-name]
  (swap! state update :tracks (fnil conj #{}) track-name))

(defn- clear-preview-tracks! []
  (doseq [track-name (:tracks @state)]
    (audio/clear-track! track-name))
  (swap! state assoc :tracks #{}))

(defn- stop-waveform-loop! []
  (when-let [id (:raf-id @state)]
    (js/cancelAnimationFrame id))
  (swap! state assoc :raf-id nil))

(defn stop!
  "Stop the active preview and restore the session snapshot captured at start."
  []
  (stop-waveform-loop!)
  (let [snap (:snapshot @state)]
    (clear-preview-tracks!)
    (when snap
      (sandbox/restore! snap)
      (sandbox/restore-audio! snap)))
  (swap! state assoc
         :active-id nil
         :mode nil
         :snapshot nil
         :tracks #{}))

(defn- elapsed-ms [start]
  (- (.now js/performance) start))

(defn- play-result! [result]
  (let [val (:result result)]
    (cond
      (core/pattern? val)
      (do
        (remember-track! default-preview-track)
        (audio/play-track! default-preview-track val nil nil)
        (fx/apply-track-effects! default-preview-track (:track-fx val)))

      (and (map? val) (or (:note val) (:bank val) (:synth val)))
      (do
        (remember-track! default-preview-track)
        (audio/play-track! default-preview-track (core/pure val) nil nil))

      :else nil)))

(defn start!
  "Start a solo or mix preview. Returns {:ok? true} or {:ok? false :error msg}."
  [mode snippet]
  (stop!)
  (let [id       (str (:id snippet))
        code     (or (:code snippet) "")
        snap     (sandbox/snapshot)
        solo?    (= mode :solo)
        started  (.now js/performance)]
    (when solo?
      (audio/stop!))
    (swap! state assoc
           :active-id id
           :mode mode
           :snapshot snap
           :tracks #{})
    (let [result (sandbox/eval-string code {:on-track remember-track!})
          elapsed (elapsed-ms started)
          error-msg (cond
                      (lisp/eval-error? result) (:message result)
                      (> elapsed preview-timeout-ms)
                      (str "Preview evaluation exceeded " preview-timeout-ms "ms")
                      :else nil)]
      (if error-msg
        (do
          (swap! state assoc-in [:errors id] error-msg)
          (stop!)
          {:ok? false :error error-msg})
        (do
          (play-result! result)
          (swap! state update :errors dissoc id)
          {:ok? true})))))

(defn- draw-idle! [^js canvas]
  (let [ctx (.getContext canvas "2d")
        w   (.-width canvas)
        h   (.-height canvas)
        mid (/ h 2)]
    (.clearRect ctx 0 0 w h)
    (set! (.-strokeStyle ctx) "rgba(92, 99, 112, 0.75)")
    (set! (.-lineWidth ctx) 1)
    (.beginPath ctx)
    (.moveTo ctx 0 mid)
    (.lineTo ctx w mid)
    (.stroke ctx)))

(defn- draw-live! [^js canvas analyser data]
  (let [ctx (.getContext canvas "2d")
        w   (.-width canvas)
        h   (.-height canvas)
        n   (.-length data)
        step (/ w (max 1 (dec n)))]
    (.getByteTimeDomainData analyser data)
    (.clearRect ctx 0 0 w h)
    (set! (.-strokeStyle ctx) "#98c379")
    (set! (.-lineWidth ctx) 1.5)
    (.beginPath ctx)
    (dotimes [i n]
      (let [x (* i step)
            y (* (/ (aget data i) 255) h)]
        (if (zero? i)
          (.moveTo ctx x y)
          (.lineTo ctx x y))))
    (.stroke ctx)))

(defn render-waveforms! []
  (stop-waveform-loop!)
  (let [canvases (.querySelectorAll js/document ".snippet-waveform")
        active   (:active-id @state)]
    (doseq [i (range (.-length canvases))
            :let [canvas (.item canvases i)]]
      (let [ratio (or (.-devicePixelRatio js/window) 1)
            rect  (.getBoundingClientRect canvas)
            w     (max 1 (int (* ratio (.-width rect))))
            h     (max 1 (int (* ratio (.-height rect))))]
        (when (or (not= (.-width canvas) w)
                  (not= (.-height canvas) h))
          (set! (.-width canvas) w)
          (set! (.-height canvas) h))
        (draw-idle! canvas)))
    (when (and active @audio/analyser-node)
      (let [data (js/Uint8Array. (.-fftSize @audio/analyser-node))]
        (letfn [(frame []
                  (when-let [canvas (.querySelector js/document
                                                    (str ".snippet-waveform[data-id=\"" active "\"]"))]
                    (draw-live! canvas @audio/analyser-node data)
                    (swap! state assoc :raf-id (js/requestAnimationFrame frame))))]
          (frame))))))

(defn init! []
  (.addEventListener js/window "beforeunload" stop!))
