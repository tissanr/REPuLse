(ns repulse.env.builtins.fx
  "FX builtin — context-aware: per-track transformer when called from ->>, global otherwise."
  (:require [repulse.core :as core]
            [repulse.fx :as fx]
            [repulse.lisp.eval :as leval]
            [repulse.song-transport :as st]))

(defn- effect-name
  "Return the canonical plugin name for an effect keyword/symbol/string."
  [x]
  (case (cljs.core/name x)
    "dattorro" "dattorro-reverb"
    (cljs.core/name x)))

;; Per-evaluation master state (Phase WS2), reset by eval-orchestrator.
(defonce master-run (atom {:src nil :in? false :loose 0 :ret nil}))
(defonce hinted? (atom false))   ; public so tests can reset it

(def LOOSE-FX-HINT
  "[REPuLse] Hint: this code uses (master …) and top-level (fx …) — consider moving the top-level (fx …) forms into (master …)")

(defn reset-master-run! [] (reset! master-run {:src nil :in? false :loose 0 :ret nil}))

(defn run-master!
  "The :*master-fn* hook: reject a second master of this evaluation, then run
   the children with :in? set. → \"=> master: <n> fx\"."
  [src n run]
  (when-let [prev (:src @master-run)]
    (let [f (or @st/buffer-of (constantly nil))
          a (f prev)
          b (f src)]
      (throw (ex-info (if (and a b (not= a b))
                        (str "Only one (master …) per project — already defined in buffer :" a)
                        "Only one (master …) per buffer — move these (fx …) forms into the first (master …)")
                      (merge {:type :eval-error} (select-keys src [:from :to]))))))
  (swap! master-run assoc :src src :in? true)
  (try (run) (finally (swap! master-run assoc :in? false)))
  (let [ret (str "=> master: " n " fx")]
    (swap! master-run assoc :ret ret)
    ret))

(defn master-result? [v]
  (and (some? (:src @master-run)) (string? v) (= v (:ret @master-run))))

(defn loose-fx-hint!
  "Log LOOSE-FX-HINT once per page load when this evaluation had a master and a
   top-level global fx. → the message when logged, else nil."
  []
  (let [{:keys [src loose]} @master-run]
    (when (and src (pos? loose) (not @hinted?))
      (reset! hinted? true)
      (js/console.info LOOSE-FX-HINT)
      LOOSE-FX-HINT)))

(defn make-builtins
  "No ctx dependencies — all state accessed via fx/audio modules directly."
  [_ctx]
  {"fx"
   (fn [& raw-args]
     (let [args'    (mapv leval/deep-unwrap raw-args)
           last-arg (last args')
           per-track? (and (> (count args') 1)
                           (core/pattern? last-arg))]
       (if per-track?
         ;; ── Per-track mode: annotate pattern with FX metadata ──────────
         (let [fx-args     (butlast args')
               pat         last-arg
               effect-name (effect-name (first fx-args))
               rest-fx     (rest fx-args)
               params      (if (keyword? (first rest-fx))
                             ;; All named: (fx :reverb :wet 0.3)
                             (into {} (map (fn [[k v]] [(cljs.core/name k) v])
                                          (partition 2 rest-fx)))
                             ;; Positional first, then optional named: (fx :delay 0.25 :feedback 0.4)
                             (let [named (rest rest-fx)]
                               (into (when (seq rest-fx) {"value" (first rest-fx)})
                                     (when (keyword? (first named))
                                       (map (fn [[k v]] [(cljs.core/name k) v])
                                            (partition 2 named))))))]
           (update pat :track-fx (fnil conj []) {:name effect-name :params (or params {})}))
         ;; ── Global chain mode: apply to master chain ─────────────────
         (do
           (when-not (:in? @master-run) (swap! master-run update :loose inc))
           (let [first-arg (first args')]
             (cond
               (= first-arg :off)
               (fx/bypass! (effect-name (second args')) true)

               (= first-arg :on)
               (fx/bypass! (effect-name (second args')) false)

               (= first-arg :remove)
               (fx/remove-effect! (effect-name (second args')))

               :else
               (let [effect-name (effect-name first-arg)
                     rest-args   (rest args')]
                 (if (keyword? (first rest-args))
                   (doseq [[k v] (partition 2 rest-args)]
                     (fx/set-param! effect-name (cljs.core/name k) v))
                   (fx/set-param! effect-name "value" (first rest-args))))))
           nil))))})
