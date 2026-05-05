(ns repulse.test-api
  (:require [repulse.audio :as audio]
            [repulse.core :as core]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]))

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
     :wholeStart (core/rat->float (:start whole))
     :wholeEnd   (core/rat->float (:end whole))}))

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
      (doseq [ev (core/query pattern span)]
        (let [part-start (core/rat->float (:start (:part ev)))
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
                                           :tween-state  {}})
  true)

(defn init! []
  (set! (.-__REPULSE_TEST__ js/window)
        #js {:evalToEvents eval-to-events
             :renderOffline render-offline
             :reset reset-test-state!
             :backend (fn [] "offline-js")})
  (set! (.-__REPULSE_TEST_READY__ js/window) true))
