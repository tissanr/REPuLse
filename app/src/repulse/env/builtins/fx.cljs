(ns repulse.env.builtins.fx
  "FX builtin — context-aware: per-track transformer when called from ->>, global otherwise."
  (:require [repulse.core :as core]
            [repulse.fx :as fx]
            [repulse.lisp.eval :as leval]))

(defn- effect-name
  "Return the canonical plugin name for an effect keyword/symbol/string."
  [x]
  (case (cljs.core/name x)
    "dattorro" "dattorro-reverb"
    (cljs.core/name x)))

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
