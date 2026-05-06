(ns repulse.snippets.sandbox
  "Sandbox helpers for snippet preview evaluation.
   Preview code runs in a forked Lisp env and any app-level mutable state it
   touches is restored when the preview ends."
  (:require [repulse.audio :as audio]
            [repulse.core :as core]
            [repulse.env.builtins :as builtins]
            [repulse.fx :as fx]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.preview-track :as preview-track]
            [repulse.samples :as samples]
            [repulse.synth :as synth]))

(defn- ->num [x]
  (let [v (leval/unwrap x)]
    (if (and (vector? v) (= 2 (count v)) (number? (first v)) (number? (second v)))
      (/ (first v) (second v))
      v)))

(defn snapshot []
  (builtins/ensure-env!)
  (let [env @builtins/env-atom]
    {:env-defs      (some-> (:*defs* env) deref)
     :env-macros    (some-> (:*macros* env) deref)
     :env-synths    (some-> (:*synths* env) deref)
     :synth-defs    @synth/synth-defs
     :scheduler     (select-keys @audio/scheduler-state
                                  [:playing? :tracks :muted :cycle-dur
                                   :on-beat :on-event :on-fx-event])
     :fx-active     (mapv #(select-keys % [:name :active? :bypassed?]) @fx/chain)
     :bank-prefix   @samples/active-bank-prefix}))

(defn restore! [snap]
  (builtins/ensure-env!)
  (let [env @builtins/env-atom]
    (when-let [defs (:*defs* env)]   (reset! defs (:env-defs snap)))
    (when-let [macros (:*macros* env)] (reset! macros (:env-macros snap)))
    (when-let [synths (:*synths* env)] (reset! synths (:env-synths snap))))
  (reset! synth/synth-defs (:synth-defs snap))
  (reset! samples/active-bank-prefix (:bank-prefix snap))
  (let [{:keys [cycle-dur muted]} (:scheduler snap)]
    (swap! audio/scheduler-state assoc
           :cycle-dur cycle-dur
           :muted (or muted #{})))
  (doseq [{:keys [name active? bypassed?]} (:fx-active snap)]
    (swap! fx/chain
           (fn [chain]
             (mapv (fn [entry]
                     (if (= name (:name entry))
                       (assoc entry :active? active? :bypassed? bypassed?)
                       entry))
                   chain)))))

(defn restore-audio! [snap expected-preview-tracks]
  (let [{:keys [playing? tracks muted on-beat on-event]} (:scheduler snap)]
    ;; If another stop path already cleared the preview tracks, do not resurrect
    ;; the session from the stale snapshot. Normal preview stops still have at
    ;; least one reserved preview track present at this point.
    (let [current-tracks         (set (keys (:tracks @audio/scheduler-state)))
          preview-still-present? (or (empty? expected-preview-tracks)
                                     (boolean (some current-tracks expected-preview-tracks)))]
      (doseq [track-name current-tracks]
        (when (preview-track/preview-track? track-name)
          (audio/clear-track! track-name)))
      (swap! audio/scheduler-state assoc :muted (or muted #{}))
      (when (and preview-still-present? playing? (seq tracks))
        (doseq [[track-name pattern] tracks]
          (audio/play-track! track-name pattern on-beat on-event)
          (fx/apply-track-effects! track-name (:track-fx pattern)))
        (swap! audio/scheduler-state assoc :muted (or muted #{}))))))

(defn- fork-env [env]
  (assoc env
         :*defs*   (atom (or (some-> (:*defs* env) deref) {}))
         :*macros* (atom (or (some-> (:*macros* env) deref) {}))
         :*synths* (atom (or (some-> (:*synths* env) deref) {}))))

(defn- sandbox-fx [& raw-args]
  (let [args'    (mapv leval/deep-unwrap raw-args)
        last-arg (last args')
        per-track? (and (> (count args') 1) (core/pattern? last-arg))]
    (when per-track?
      (let [fx-args     (butlast args')
            pat         last-arg
            effect-name (cljs.core/name (first fx-args))
            rest-fx     (rest fx-args)
            params      (if (keyword? (first rest-fx))
                          (into {} (map (fn [[k v]] [(cljs.core/name k) v])
                                        (partition 2 rest-fx)))
                          (let [named (rest rest-fx)]
                            (into (when (seq rest-fx) {"value" (first rest-fx)})
                                  (when (keyword? (first named))
                                    (map (fn [[k v]] [(cljs.core/name k) v])
                                         (partition 2 named))))))]
        (update pat :track-fx (fnil conj []) {:name effect-name :params (or params {})})))))

(defn preview-env
  [{:keys [on-track]}]
  (builtins/ensure-env!)
  (let [env (fork-env @builtins/env-atom)]
    (assoc env
           :*register-synth-fn* synth/register-synth!
           "track"
           (fn [track-name pat]
             (let [name'         (leval/unwrap track-name)
                   pat'          (leval/unwrap pat)
                   preview-name  (preview-track/preview-track-name name')]
               (if (core/pattern? pat')
                 (do
                   (on-track preview-name)
                   (audio/play-track! preview-name pat' nil nil)
                   (fx/apply-track-effects! preview-name (:track-fx pat'))
                   (str "=> preview :" (name name') " playing"))
                 "Error: second argument to track must be a pattern")))
           "fx" sandbox-fx
           "bpm" (fn [b] (audio/set-bpm! (->num b)) nil)
           "stop" (fn [] nil)
           "clear!" (fn [& _] nil)
           "mute!" (fn [& _] nil)
           "unmute!" (fn [& _] nil)
           "solo!" (fn [& _] nil)
           "samples!" (fn [url] (str "preview skipped sample load: " (leval/unwrap url)))
           "load-plugin" (fn [& _] "preview skipped plugin load")
           "unload-plugin" (fn [& _] "preview skipped plugin unload")
           "bus" (fn [& _] "preview skipped bus creation")
           "share!" (fn [] nil)
           "snippet" (fn [& _] "snippet insertion is disabled during preview")
           "demo" (fn [& _] "demo loading is disabled during preview")
           "tutorial" (fn [& _] "tutorial loading is disabled during preview")
           "load-gist" (fn [& _] "gist loading is disabled during preview"))))

(defn eval-string [code opts]
  (lisp/eval-string code (preview-env opts)))
