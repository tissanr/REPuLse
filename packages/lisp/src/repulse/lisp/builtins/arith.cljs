(ns repulse.lisp.builtins.arith
  "Pattern arithmetic (Phase TL1): add, sub, mul, div, set.

   Every operator takes an optional leading mode keyword that chooses where
   the rhythm (event structure) comes from — the Tidal/Strudel operator family:

     :in      structure from the pattern (last argument)   Tidal |+   (default)
     :out     structure from the amount                     Tidal +|
     :mix     structure from both (wholes intersect)        Tidal |+|
     :squeeze each cycle of the amount squeezed into every event of the pattern

   (add 7 pat) · (add \"<0 2 4>\" pat) · (add :out (seq 10 20) pat) · (add 12)"
  (:require [repulse.core :as core]
            [repulse.theory :as theory]
            [repulse.lisp.util :as u]))

(def ^:private modes #{:in :out :mix :squeeze})

(defn- num-val
  "Numeric value of an amount: numbers as-is, rationals [n d] as floats, else nil."
  [a]
  (cond
    (number? a) a
    (and (vector? a) (= 2 (count a)) (number? (first a)) (number? (second a))
         (not (zero? (second a))))
    (/ (first a) (second a))
    :else nil))

(defn- numeric-op
  "Lift a binary number fn to event values. Note keywords go through MIDI note
   numbers and back (:c3 + 12 → :c4); maps apply the op to :note; anything
   non-numeric (rests, drum names) passes through unchanged."
  [f]
  (fn [v amt]
    (if-let [a (num-val amt)]
      (let [app (fn [x]
                  (cond
                    (number? x)               (f x a)
                    (theory/note-keyword? x)  (theory/midi->note (f (theory/note->midi x) a))
                    :else                     x))]
        (if (map? v)
          (if (contains? v :note) (update v :note app) v)
          (app v)))
      v)))

(defn- set-op
  "Tidal `#`: control values win. Maps merge into the event value (a bare
   value becomes {:note v}); a non-map control replaces the value. Rests stay rests."
  [v ctrl]
  (cond
    (= v :_)     v
    (map? ctrl)  (merge (if (map? v) v {:note v}) ctrl)
    :else        ctrl))

(defn apply-op
  "Combine pat with amt (scalar or pattern) using (op value amount),
   taking structure according to mode."
  [mode op amt pat]
  (let [pat (if (core/pattern? pat) pat (core/pure pat))]
    (if (and (= mode :in) (not (core/pattern? amt)))
      ;; Fast path: a scalar amount adds no structure of its own.
      (core/fmap #(op % amt) pat)
      (let [amt-pat (if (core/pattern? amt) amt (core/pure amt))]
        (case mode
          :in      (core/app-left  op pat amt-pat)
          :out     (core/app-right op pat amt-pat)
          :mix     (core/app-both  op pat amt-pat)
          :squeeze (core/squeeze-join
                    (core/fmap (fn [v] (core/fmap #(op v %) amt-pat)) pat)))))))

(defn- make-op
  "Build the variadic builtin: ([mode] amt) → transformer, ([mode] amt pat) → pattern."
  [name op]
  (fn [& args]
    (let [m              (u/unwrap (first args))
          [mode args']   (if (contains? modes m) [m (rest args)] [:in args])]
      (case (count args')
        1 (let [amt (u/pat-arg (first args'))]
            (fn [p] (apply-op mode op amt (u/unwrap p))))
        2 (apply-op mode op (u/pat-arg (first args')) (u/unwrap (second args')))
        (throw (ex-info (str name " expects ([mode] amount pattern), got "
                             (count args) " argument(s)")
                        {:type :eval-error}))))))

(defn make-builtins []
  {"add" (make-op "add" (numeric-op +))
   "sub" (make-op "sub" (numeric-op -))
   "mul" (make-op "mul" (numeric-op *))
   "div" (make-op "div" (numeric-op (fn [x a] (if (zero? a) x (/ x a)))))
   "set" (make-op "set" set-op)})
