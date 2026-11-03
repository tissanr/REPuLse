(ns repulse.lisp.builtins.pattern
  "Pure pattern-algebra builtins: seq, stack, fast, slow, euclidean, etc."
  (:require [clojure.string :as str]
            [repulse.core :as core]
            [repulse.params :as params]
            [repulse.lisp.mini :as mini]
            [repulse.lisp.util :as u]))

;;; ── Phase TL5 helpers ───────────────────────────────────────────────

(defn- xf
  "Wrap a Lisp function argument so SourcedVals never reach core."
  [f]
  (let [f (u/unwrap f)]
    (fn [p] (u/unwrap (f p)))))

(defn- variadic-fs
  "Builtin (f fn … pat): the last argument is the pattern; if it is a function
   the call is curried and returns a pattern transformer."
  [f]
  (fn [& args]
    (let [l (u/unwrap (last args))]
      (if (fn? l)
        (let [fs (mapv xf args)]
          (fn [p] (f fs (u/pat-arg p))))
        (f (mapv xf (butlast args)) (u/pat-arg (last args)))))))

(defn- three+p
  "Builtin (f a b c pat) with curried (f a b c); a, b and c patternable."
  [f]
  (fn
    ([a b c]   (let [a (u/pat-arg a) b (u/pat-arg b) c (u/pat-arg c)]
                 (fn [p] (f a b c (u/unwrap p)))))
    ([a b c p] (f (u/pat-arg a) (u/pat-arg b) (u/pat-arg c) (u/unwrap p)))))

(defn- numeric-token? [tok]
  (boolean (re-matches #"-?\d+(\.\d+)?(/\d+)?" tok)))

(defn- mode-error [x]
  (ex-info (str "arp: unknown mode " (pr-str x) " — known: "
                (str/join " " (sort (map name (keys core/arp-modes)))))
           {:type :eval-error}))

(defn- arp-mode-arg
  "Decide between Tidal modes and Strudel index lists from the literal and
   validate literal modes. Returns [:index ipat] or [:mode mpat]."
  [x]
  (let [v (u/unwrap x)]
    (cond
      (keyword? v) (if (contains? core/arp-modes v) [:mode v] (throw (mode-error v)))
      (number? v)  [:index v]
      (and (vector? v) (= 2 (count v)) (every? number? v)) [:index v]
      (string? v)
      (let [toks (remove str/blank? (str/split v #"[\s\[\]<>*?:@,!%(){}|~_.]+"))]
        (cond
          (and (seq toks) (every? numeric-token? toks)) [:index (u/pat-arg x)]
          :else
          (do (doseq [t toks :when (not (numeric-token? t))]
                (when-not (contains? core/arp-modes (keyword t))
                  (throw (mode-error t))))
              [:mode (u/pat-arg x)])))
      :else [:mode v])))

(defn- arp-fn [x pat]
  (let [[kind m] (arp-mode-arg x)]
    (if (= kind :index) (core/arp-index m pat) (core/arp m pat))))

(defn make-builtins []
  {"seq"    (fn [& vs]
              (let [srcs (mapv u/source-of vs)
                    vals (mapv u/unwrap vs)]
                (core/seq* vals srcs)))
   "stack"  (fn [& ps] (core/stack* (mapv u/unwrap ps)))
   "pure"   (fn [v] (core/pure (u/unwrap v) (u/source-of v)))
   ;; Time arguments are passed through raw (never via u/->num) so a
   ;; rational literal 1/3 stays the exact [1 3]. Strings in patternable
   ;; positions are mini-notation: (fast "<1 2>" pat).
   "fast"   (fn
              ([f]   (let [f (u/pat-arg f)] (fn [p] (core/fast f (u/unwrap p)))))
              ([f p] (core/fast (u/pat-arg f) (u/unwrap p))))
   "slow"   (fn
              ([f]   (let [f (u/pat-arg f)] (fn [p] (core/slow f (u/unwrap p)))))
              ([f p] (core/slow (u/pat-arg f) (u/unwrap p))))
   "rev"    (fn [p] (core/rev (u/unwrap p)))
   "every"  (fn [n t p] (core/every (u/pat-arg n) t (u/unwrap p)))
   "fmap"   (fn [f p] (core/fmap (fn [v] (u/unwrap (f v))) (u/unwrap p)))
   "euclidean" (fn
                 ([k n v]   (core/euclidean (u/pat-arg k) (u/pat-arg n) (u/unwrap v)))
                 ([k n v r] (core/euclidean (u/pat-arg k) (u/pat-arg n) (u/unwrap v) (u/pat-arg r))))
   "cat"    (fn [& ps] (core/cat* (mapv u/unwrap ps)))
   "late"   (fn
              ([a]   (let [a (u/pat-arg a)] (fn [p] (core/late a (u/unwrap p)))))
              ([a p] (core/late (u/pat-arg a) (u/unwrap p))))
   "early"  (fn
              ([a]   (let [a (u/pat-arg a)] (fn [p] (core/early a (u/unwrap p)))))
              ([a p] (core/early (u/pat-arg a) (u/unwrap p))))
   "jux"    (fn [f p] (params/jux (u/unwrap f) (u/unwrap p)))
   "jux-by" (fn [w f p] (params/jux-by (u/pat-arg w) (u/unwrap f) (u/unwrap p)))
   "off"    (fn [a f p] (core/off (u/pat-arg a) (u/unwrap f) (u/unwrap p)))
   ;; ── Phase TL5: layering & arpeggios ──
   "superimpose" (variadic-fs core/superimpose)
   "layer"       (variadic-fs core/layer)
   "echo"        (three+p params/echo)
   "stut"        (three+p params/stut)
   "echo-with"   (fn
                   ([n t f]   (let [n (u/pat-arg n) t (u/pat-arg t) f (xf f)]
                                (fn [p] (core/echo-with n t f (u/unwrap p)))))
                   ([n t f p] (core/echo-with (u/pat-arg n) (u/pat-arg t) (xf f) (u/unwrap p))))
   "stutter"     (fn
                   ([n t]   (let [n (u/pat-arg n) t (u/pat-arg t)]
                              (fn [p] (core/stutter n t (u/unwrap p)))))
                   ([n t p] (core/stutter (u/pat-arg n) (u/pat-arg t) (u/unwrap p))))
   "jux-flip"    (fn
                   ([f]   (let [f (xf f)] (fn [p] (params/jux-flip f (u/unwrap p)))))
                   ([f p] (params/jux-flip (xf f) (u/unwrap p))))
   "jux-flip-by" (fn
                   ([w f]   (let [w (u/pat-arg w) f (xf f)]
                              (fn [p] (params/jux-flip-by w f (u/unwrap p)))))
                   ([w f p] (params/jux-flip-by (u/pat-arg w) (xf f) (u/unwrap p))))
   "xfade"       (fn [a pos b] (params/xfade (u/pat-arg a) (u/pat-arg pos) (u/pat-arg b)))
   "arp"         (fn
                   ([mode]     (let [m (arp-mode-arg mode)]
                                 (fn [p] (let [[kind mm] m
                                               p (u/pat-arg p)]
                                           (if (= kind :index) (core/arp-index mm p) (core/arp mm p))))))
                   ([mode pat] (arp-fn mode (u/pat-arg pat))))
   "arp-with"    (fn
                   ([f]   (let [f (u/unwrap f)]
                            (fn [p] (core/arp-with (fn [vs] (u/unwrap (f vs))) (u/pat-arg p)))))
                   ([f p] (let [f (u/unwrap f)]
                            (core/arp-with (fn [vs] (u/unwrap (f vs))) (u/pat-arg p)))))
   "rolled"      (fn [p] (core/rolled (u/pat-arg p)))
   "rolled-by"   (fn
                   ([t]   (let [t (u/pat-arg t)] (fn [p] (core/rolled-by t (u/pat-arg p)))))
                   ([t p] (core/rolled-by (u/pat-arg t) (u/pat-arg p))))
   "~"      (fn [s]
              (let [src         (u/source-of s)
                    base-offset (when src (inc (:from src)))]
                (mini/parse (u/unwrap s) base-offset)))
   "alt"    (fn [& pats] (mini/alt* (mapv u/unwrap pats)))})
