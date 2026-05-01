(ns repulse.env.builtins.fx
  "FX builtin — context-aware: per-track transformer when called from ->>, global otherwise."
  (:require [repulse.core :as core]
            [repulse.fx :as fx]
            [repulse.lisp.eval :as leval]))

(defn make-builtins
  "No ctx dependencies — all state accessed via fx/audio modules directly."
  [_ctx]
  {"fx"
   (fn [& raw-args]
     (let [args'    (mapv leval/unwrap raw-args)
           last-arg (last args')
           per-track? (and (> (count args') 1)
                           (core/pattern? last-arg))]
       (if per-track?
         ;; ── Per-track mode: annotate pattern with FX metadata ──────────
         (let [fx-args     (butlast args')
               pat         last-arg
               effect-name (cljs.core/name (first fx-args))
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
               (fx/bypass! (cljs.core/name (second args')) true)

               (= first-arg :on)
               (fx/bypass! (cljs.core/name (second args')) false)

               (= first-arg :remove)
               (fx/remove-effect! (cljs.core/name (second args')))

               :else
               (let [effect-name (cljs.core/name first-arg)
                     rest-args   (rest args')]
                 (if (keyword? (first rest-args))
                   (doseq [[k v] (partition 2 rest-args)]
                     (fx/set-param! effect-name (cljs.core/name k) v))
                   (fx/set-param! effect-name "value" (first rest-args))))))
           nil))))})
