(ns repulse.test-api
  (:require [repulse.audio :as audio]
            [repulse.core :as core]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.song :as song]
            [repulse.song-transport :as song-transport]
            [repulse.env.builtins.song :as b-song]))

(def ^:private default-sample-rate 44100)
(def ^:private default-cycle-dur 2.0)

(defn- make-test-env []
  (leval/make-env (fn [] nil) (fn [_] nil)))

(defn- eval-code [code]
  (let [env (make-test-env)
        r   (lisp/eval-string code env)]
    (when (lisp/eval-error? r)
      (throw (js/Error. (:message r))))
    (:result r)))

(defn- pattern-from-code [code]
  (let [result (eval-code code)]
    (when-not (core/pattern? result)
      (throw (js/Error. (str "Expected pattern, got " (type result)))))
    result))

(defn- safe-value [v]
  (cond
    (keyword? v) (str ":" (name v))
    (map? v) (into {}
                   (map (fn [[k val]]
                          [(if (keyword? k) (name k) (str k))
                           (safe-value val)]))
                   v)
    (sequential? v) (mapv safe-value v)
    :else v))

(defn- span->event-summary [ev]
  (let [part  (:part ev)
        whole (:whole ev)]
    {:value      (safe-value (:value ev))
     :start      (core/rat->float (:start part))
     :end        (core/rat->float (:end part))
     :wholeStart (when whole (core/rat->float (:start whole)))
     :wholeEnd   (when whole (core/rat->float (:end whole)))}))

(defn- eval-to-events
  ([code] (eval-to-events code 1))
  ([code cycles]
   (let [pattern (pattern-from-code code)
         n       (max 1 (int (or cycles 1)))]
     (clj->js
      (mapv (fn [ev] (span->event-summary ev))
            (mapcat (fn [c]
                      (core/query pattern {:start [c 1] :end [(inc c) 1]}))
                    (range n)))))))

(defn- schedule-pattern! [offline pattern cycles cycle-dur]
  (doseq [c (range cycles)]
    (let [span {:start [c 1] :end [(inc c) 1]}]
      (doseq [ev (core/query-onsets pattern span)]
        (let [part-start (core/rat->float (:start (:whole ev)))
              abs-t      (* part-start cycle-dur)]
          (when (>= abs-t 0)
            (audio/play-event offline abs-t (:value ev))))))))

(defn- channel-array [buffer idx]
  (let [data (.getChannelData buffer idx)]
    (vec (array-seq (js/Array.from data)))))

(defn- render-offline
  ([code] (render-offline code 1 nil))
  ([code cycles] (render-offline code cycles nil))
  ([code cycles options]
   (let [pattern     (pattern-from-code code)
         opts        (js->clj (or options #js {}) :keywordize-keys true)
         n-cycles    (max 1 (int (or cycles 1)))
         sample-rate (int (or (:sampleRate opts) default-sample-rate))
         cycle-dur   (double (or (:cycleDur opts) default-cycle-dur))
         duration    (* n-cycles cycle-dur)
         n-frames    (int (* sample-rate duration))
         offline     (js/OfflineAudioContext. 2 n-frames sample-rate)]
     (schedule-pattern! offline pattern n-cycles cycle-dur)
     (-> (.startRendering offline)
         (.then (fn [buffer]
                  (clj->js {:sampleRate (.-sampleRate buffer)
                            :channels   (.-numberOfChannels buffer)
                            :length     (.-length buffer)
                            :duration   (.-duration buffer)
                            :backend    "offline-js"
                            :left       (channel-array buffer 0)
                            :right      (channel-array buffer 1)})))))))

(defn- reset-test-state! []
  (when (:interval-id @audio/scheduler-state)
    (audio/stop!))
  (song-transport/reset-pending!)
  (cljs.core/reset! song-transport/song-buffer nil)
  (cljs.core/reset! audio/scheduler-state {:playing?     false
                                           :tracks       {}
                                           :muted        #{}
                                           :cycle        0
                                           :cycle-dur    default-cycle-dur
                                           :lookahead    0.2
                                           :interval-id  nil
                                           :on-beat      nil
                                           :on-event     nil
                                           :on-fx-event  nil
                                           :tween-state  {}
                                           :song         nil
                                           :capture      nil
                                           :on-song-end  nil})
  true)

;;; ── SONG1: song harness ───────────────────────────────────────────────

(defn- song-env
  "Lisp env + harness `track` (collect only) + the app `song`/`play-from` builtins.
   bpm-fn — (bpm …) handler: a no-op for songEvents, audio/set-bpm! for captures."
  [bpm-fn]
  (merge (leval/make-env (fn [] nil) bpm-fn)
         {"track" (fn [n p]
                    (let [name' (leval/unwrap n) pat' (leval/unwrap p)]
                      (when-not (core/pattern? pat')
                        (throw (js/Error. "second argument to track must be a pattern")))
                      (song-transport/collect-track! name' pat' (leval/source-of n))
                      (str "=> track :" (name name') " playing")))}
         (b-song/make-builtins {})))

(defn- eval-song-code
  "Evaluate a song buffer; leaves tracks and song in song-transport's pending atoms."
  [code bpm-fn]
  (song-transport/reset-pending!)
  (let [r (lisp/eval-string code (song-env bpm-fn))]
    (when (lisp/eval-error? r)
      (song-transport/reset-pending!)
      (throw (js/Error. (:message r))))
    (when-not @song-transport/pending-song
      (song-transport/reset-pending!)
      (throw (js/Error. "Expected a buffer with (song …)")))
    r))

(defn- rest-value? [v]
  (or (= v :_) (and (map? v) (= :_ (:note v)))))

(defn- song-events
  "Onsets the scheduler would play for song bars [from-bar, to-bar), as
   [{track value wholeStart wholeEnd}] sorted by wholeStart then track; rests dropped.
   opts.muted — array of \":kick\"-style track names."
  ([code from-bar to-bar] (song-events code from-bar to-bar nil))
  ([code from-bar to-bar opts]
   (eval-song-code code (fn [_] nil))
   (let [s      @song-transport/pending-song
         tracks (into {} @song-transport/pending-tracks)
         o      (js->clj (or opts #js {}) :keywordize-keys true)
         muted  (set (map #(keyword (subs % 1)) (:muted o)))
         evs    (for [bar (range (int from-bar) (int to-bar))
                      {:keys [track event]} (song/song-onsets s tracks muted bar)
                      :when (not (rest-value? (:value event)))]
                  {:track      (str ":" (name track))
                   :value      (safe-value (:value event))
                   :wholeStart (core/rat->float (:start (:whole event)))
                   :wholeEnd   (core/rat->float (:end (:whole event)))})]
     (song-transport/reset-pending!)
     (clj->js (vec (sort-by (juxt :wholeStart :track) evs))))))

(defn- backend-name []
  (if (and @audio/worklet-ready? @audio/recorder-node) "audioworklet+wasm" "js"))

(defn- audio-ready
  "Create the real AudioContext and resolve \"audioworklet+wasm\" once the WASM
   worklet and the recorder are up, or \"js\" after 10 s."
  []
  (let [ac (audio/get-ctx)
        t0 (js/Date.now)]
    (-> (.resume ac)
        (.then (fn []
                 (js/Promise.
                  (fn [resolve _]
                    (letfn [(poll []
                              (cond
                                (= "audioworklet+wasm" (backend-name)) (resolve "audioworklet+wasm")
                                (> (- (js/Date.now) t0) 10000)        (resolve "js")
                                :else (js/setTimeout poll 50)))]
                      (poll)))))))))

(defn- capture-song
  "Evaluate code, activate it on the real AudioContext and capture one pass of
   the song from the master output in real time (song-transport/capture-song!)."
  ([code] (capture-song code nil))
  ([code opts]
   (let [o    (js->clj (or opts #js {}) :keywordize-keys true)
         tail (double (or (:tail o) 2))]
     (reset-test-state!)
     (eval-song-code code audio/set-bpm!)
     (let [s (:song (do (song-transport/activate-buffer! {}) @song-transport/song-buffer))]
       (-> (song-transport/capture-song! {:tail tail})
           (.then (fn [{:keys [left right sample-rate frames late]}]
                    (let [expected (Math/round (* (+ (song/song-duration-secs s (audio/get-bpm)) tail)
                                                  sample-rate))]
                      #js {:sampleRate     sample-rate
                           :length         frames
                           :left           (js/Array.from left)
                           :right          (js/Array.from right)
                           :backend        (backend-name)
                           :expectedFrames expected
                           :late           late}))))))))

(defn init! []
  (set! (.-__REPULSE_TEST__ js/window)
        #js {:evalToEvents eval-to-events
             :renderOffline render-offline
             :reset reset-test-state!
             :backend (fn [] "offline-js")
             :songEvents song-events
             :audioReady audio-ready
             :captureSong capture-song})
  (set! (.-__REPULSE_TEST_READY__ js/window) true))
