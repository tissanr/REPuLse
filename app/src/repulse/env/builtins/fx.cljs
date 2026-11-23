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

(defn- report-warnings!
  "Record each {:message :arg} warning through warn! with the source range of
   its argument (fallback: the effect keyword)."
  [warn! warnings effect-arg]
  (when warn!
    (doseq [{:keys [message arg]} warnings]
      (let [src (or (some-> arg leval/source-of) (leval/source-of effect-arg))]
        (warn! (merge {:message message} (select-keys src [:from :to])))))))

(defn make-builtins
  "ctx — {:warn! f} (optional): records eval warnings for unknown fx parameters
   and values (Phase FX1, D4). Other state is accessed via the fx/audio modules."
  [ctx]
  (let [warn! (:warn! ctx)]
    {"fx"
     (fn [& raw-args]
       (let [args'      (mapv leval/deep-unwrap raw-args)
             last-arg   (last args')
             per-track? (and (> (count args') 1)
                             (core/pattern? last-arg))]
         (if per-track?
           ;; ── Per-track mode: annotate pattern with FX metadata ──────────
           (let [raw-fx      (butlast raw-args)
                 pat         last-arg
                 effect-name (effect-name (first args'))
                 {:keys [pairs warnings]} (fx/prepare-fx-args effect-name (rest raw-fx))]
             (report-warnings! warn! warnings (first raw-args))
             (update pat :track-fx (fnil conj [])
                     {:name effect-name :params (into {} pairs)}))
           ;; ── Global chain mode: apply to master chain ─────────────────
           (do
             (when-not (:in? @master-run) (swap! master-run update :loose inc))
             (let [first-arg (first args')]
               (cond
                 ;; QA3 A4: fx/global-op! records these while an evaluation
                 ;; runs; they apply only when it succeeds.
                 (= first-arg :off)
                 (fx/global-op! [:bypass (effect-name (second args')) true])

                 (= first-arg :on)
                 (fx/global-op! [:bypass (effect-name (second args')) false])

                 (= first-arg :remove)
                 (fx/global-op! [:remove (effect-name (second args'))])

                 :else
                 (let [effect-name (effect-name first-arg)
                       rest-raw    (rest raw-args)]
                   (if (empty? rest-raw)
                     (if-let [w (fx/needs-param-warning (fx/find-plugin effect-name) effect-name)]
                       (report-warnings! warn! [{:message w :arg nil}] (first raw-args))
                       ;; no descriptor: unchanged pre-FX1 behaviour
                       (fx/global-op! [:set effect-name "value" nil]))
                     (let [{:keys [pairs warnings]} (fx/prepare-fx-args effect-name rest-raw)]
                       (report-warnings! warn! warnings (first raw-args))
                       (doseq [[k v] pairs]
                         (fx/global-op! [:set effect-name k v])))))))
             nil))))}))
