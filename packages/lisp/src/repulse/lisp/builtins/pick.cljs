(ns repulse.lisp.builtins.pick
  "TL4b pick family, inhabit and fix — thin Lisp wrappers over repulse.core
   and repulse.params."
  (:require [repulse.core :as core]
            [repulse.params :as params]
            [repulse.lisp.util :as u]))

(defn- xf
  "Wrap a Lisp function argument so SourcedVals never reach core."
  [f]
  (let [f (u/unwrap f)]
    (fn [p] (u/unwrap (f p)))))

(defn- entry->pat [x]                       ; K5
  (let [v (u/unwrap x)]
    (cond (core/pattern? v) v
          (string? v)       (u/pat-arg x)
          :else             (core/pure v (u/source-of x)))))

(defn- lookup-of [name conv lk]             ; E1, K5
  (let [v (u/unwrap lk)]
    (cond (vector? v) (mapv conv v)
          (map? v)    (into {} (map (fn [[k e]] [(u/unwrap k) (conv e)])) v)
          :else (throw (js/Error.
                        (str name ": the lookup must be a vector [...] or a map {...}. "
                             "Usage: (" name " lookup index-pattern)"))))))

(defn- lookup-builtin [name core-fn]
  (fn
    ([lk]      (let [l (lookup-of name entry->pat lk)] (fn [i] (core-fn l (u/pat-arg i)))))
    ([lk ipat] (core-fn (lookup-of name entry->pat lk) (u/pat-arg ipat)))))

(defn- fn-entry [name]                      ; E2
  (fn [e]
    (if (fn? (u/unwrap e))
      (xf e)
      (throw (js/Error. (str name ": every lookup entry must be a function, e.g. (fast 2) or rev"))))))

(defn- fns-lookup [name fs]
  ;; E1 uses the function-variant text
  (let [v (u/unwrap fs)]
    (when-not (or (vector? v) (map? v))
      (throw (js/Error. (str name ": the lookup must be a vector [...] or a map {...} of functions. "
                             "Usage: (" name " index-pattern [f1 f2] pat)"))))
    (lookup-of name (fn-entry name) fs)))

(defn- pick-f-builtin [name core-fn]
  (fn
    ([ipat fs]   (let [i (u/pat-arg ipat) l (fns-lookup name fs)]
                   (fn [p] (core-fn i l (u/unwrap p)))))
    ([ipat fs p] (core-fn (u/pat-arg ipat) (fns-lookup name fs) (u/unwrap p)))))

(defn- rational-literal? [x]   ; K11: reader rationals are plain [n d] with unwrapped ints
  (and (vector? x) (= 2 (count x)) (not-any? u/sourced? x)
       (every? integer? x) (not (zero? (second x)))))

(defn- fix-cond [c]
  (let [m (u/unwrap c)]
    (when-not (map? m)
      (throw (js/Error. "fix: the condition must be a map like {:s :bd}. Usage: (fix f {:s :bd} pat)")))
    (into {} (map (fn [[k v]]
                    (let [v' (u/unwrap v)]
                      [(u/unwrap k) (if (rational-literal? v')
                                      (/ (first v') (second v'))
                                      (u/deep-unwrap v'))])))
          m)))

(defn make-builtins []
  {"pick"             (lookup-builtin "pick" core/pick)
   "pick-mod"         (lookup-builtin "pick-mod" core/pick-mod)
   "pick-out"         (lookup-builtin "pick-out" core/pick-out)
   "pick-mod-out"     (lookup-builtin "pick-mod-out" core/pick-mod-out)
   "pick-restart"     (lookup-builtin "pick-restart" core/pick-restart)
   "pick-mod-restart" (lookup-builtin "pick-mod-restart" core/pick-mod-restart)
   "pick-reset"       (lookup-builtin "pick-reset" core/pick-reset)
   "pick-mod-reset"   (lookup-builtin "pick-mod-reset" core/pick-mod-reset)
   "inhabit"          (lookup-builtin "inhabit" core/inhabit)
   "inhabit-mod"      (lookup-builtin "inhabit-mod" core/inhabit-mod)
   "pick-f"           (pick-f-builtin "pick-f" core/pick-f)
   "pick-mod-f"       (pick-f-builtin "pick-mod-f" core/pick-mod-f)
   "fix"
   (fn
     ([g c]   (let [g (xf g) c (fix-cond c)] (fn [p] (params/fix g c (u/unwrap p)))))
     ([g c p] (params/fix (xf g) (fix-cond c) (u/unwrap p))))})
