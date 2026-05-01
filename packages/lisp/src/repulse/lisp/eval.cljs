(ns repulse.lisp.eval
  (:require [repulse.core :as core]
            [repulse.theory :as theory]
            [repulse.params :as params]
            [repulse.lisp.reader :as reader]
            [repulse.lisp.mini :as mini]))

;;; Source-tracking helpers

(defn- sourced? [x] (instance? reader/SourcedVal x))
(defn unwrap [x] (if (sourced? x) (:v x) x))
(defn- source-of [x]
  (if (sourced? x)
    (:source x)
    (:source (meta x))))

;;; Numeric coercion — handles rational pair [n d] from reader

(defn- ->num
  "Coerce a value to a number. Rational pairs [n d] → n/d (float)."
  [x]
  (let [v (unwrap x)]
    (if (and (vector? v) (= 2 (count v)) (number? (first v)) (number? (second v)))
      (/ (first v) (second v))
      v)))

;;; Levenshtein / typo hints

(defn levenshtein [a b]
  (let [m (count a) n (count b)
        row (atom (vec (range (inc n))))]
    (dotimes [i m]
      (let [prev @row
            cur  (atom [(inc i)])]
        (dotimes [j n]
          (swap! cur conj
                 (if (= (nth a i) (nth b j))
                   (nth prev j)
                   (inc (min (nth prev (inc j))
                             (last @cur)
                             (nth prev j))))))
        (reset! row @cur)))
    (last @row)))

(defn typo-hint [name known]
  (when (seq known)
    (let [best (apply min-key #(levenshtein name (str %)) known)]
      (when (<= (levenshtein name (str best)) 3)
        best))))

(declare eval-form)

(defn make-closure [params body env]
  (fn [& args]
    (let [bound (zipmap (map str params) args)
          local (merge env bound)]
      (last (map #(eval-form % local) body)))))

;;; loop/recur sentinel — a plain map, caught by `loop` iteration

(def ^:private recur-sentinel-type ::recur-sentinel)
(def ^:private max-loop-iterations 10000)

(defn- recur-sentinel [args]
  {::type recur-sentinel-type ::args args})

(defn- recur-sentinel? [x]
  (and (map? x) (= (::type x) recur-sentinel-type)))

;;; Quasiquote expansion

(defn expand-quasiquote
  "Walk a quasiquoted form. (unquote x) → evaluate x.
   (splice-unquote x) → splice evaluated x into surrounding list/vector."
  [form env]
  (cond
    ;; (unquote expr) → evaluate expr
    (and (seq? form) (= (first form) (symbol "unquote")))
    (eval-form (second form) env)

    ;; List — walk items, handle splice-unquote
    (seq? form)
    (apply list
      (reduce
        (fn [acc item]
          (if (and (seq? item) (= (first item) (symbol "splice-unquote")))
            (into acc (eval-form (second item) env))
            (conj acc (expand-quasiquote item env))))
        [] form))

    ;; Vector — walk items, handle splice-unquote
    (vector? form)
    (reduce
      (fn [acc item]
        (if (and (seq? item) (= (first item) (symbol "splice-unquote")))
          (into acc (eval-form (second item) env))
          (conj acc (expand-quasiquote item env))))
      [] form)

    ;; Symbols, keywords, numbers, strings — return as-is (quoted)
    :else form))

(defn eval-form [form env]
  (cond
    ;; SourcedVal from reader — treat as literal, pass through as-is
    (sourced? form)
    form

    (or (number? form) (string? form) (keyword? form)
        (true? form) (false? form) (nil? form))
    form

    (vector? form)
    (mapv #(eval-form % env) form)

    (map? form)
    (into {} (map (fn [[k v]]
                    [(unwrap (eval-form k env))
                     (unwrap (eval-form v env))])
                  form))

    (symbol? form)
    (let [n    (str form)
          defs (some-> (:*defs* env) deref)
          src  (source-of form)]
      (cond
        (contains? env n)   (get env n)
        (contains? defs n)  (get defs n)
        :else
        (let [known (concat (filter string? (keys env))
                            (filter string? (keys defs)))]
          (throw (ex-info (str "Undefined symbol: " n
                               (when-let [h (typo-hint n known)]
                                 (str " — did you mean " h "?")))
                          {:type :eval-error :from (:from src) :to (:to src)})))))

    (seq? form)
    (let [[head & tail] form]
      (case (when (symbol? head) (str head))
        "def"
        (let [[sym val-form] tail
              v (eval-form val-form env)]
          (when-let [defs (:*defs* env)]
            (swap! defs assoc (str sym) v))
          v)

        "defn"
        ;; (defn name [params] body...) — sugar for (def name (fn [params] body...))
        (let [[name-sym params & body] tail
              f (make-closure params body env)]
          (when-let [defs (:*defs* env)]
            (swap! defs assoc (str name-sym) f))
          f)

        "let"
        (let [[bindings & body] tail]
          (loop [pairs (partition 2 bindings) local env]
            (if (empty? pairs)
              (last (map #(eval-form % local) body))
              (let [[s vf] (first pairs)]
                (recur (rest pairs) (assoc local (str s) (eval-form vf local)))))))

        ("fn" "lambda")
        (let [[params & body] tail]
          (make-closure params body env))

        "if"
        (let [[c t e] tail]
          ;; unwrap needed: a SourcedVal wrapping false/nil would be truthy as a record
          (if (unwrap (eval-form c env))
            (eval-form t env)
            (when e (eval-form e env))))

        "and"
        (loop [forms tail]
          (if (empty? forms)
            true
            (let [value (eval-form (first forms) env)]
              (if (empty? (rest forms))
                value
                (if (unwrap value)
                  (recur (rest forms))
                  value)))))

        "or"
        (loop [forms tail]
          (if (empty? forms)
            nil
            (let [value (eval-form (first forms) env)]
              (if (or (unwrap value) (empty? (rest forms)))
                value
                (recur (rest forms))))))

        "quote"
        ;; (quote form) — return form unevaluated (no SourcedVal stripping)
        (first tail)

        "do"
        (last (map #(eval-form % env) tail))

        "->>"
        ;; Thread-last: pass accumulator as last arg to each subsequent call form.
        (reduce
          (fn [acc form]
            (if (seq? form)
              (let [[fhead & fargs] form
                    f    (eval-form fhead env)
                    args (mapv #(eval-form % env) fargs)]
                (if (fn? f)
                  (apply f (concat args [acc]))
                  (throw (ex-info (str (pr-str fhead) " is not a function")
                                  {:type :eval-error}))))
              ;; Bare symbol — call as unary function
              (let [f (eval-form form env)]
                (if (fn? f)
                  (f acc)
                  (throw (ex-info (str (pr-str form) " is not a function")
                                  {:type :eval-error}))))))
          (eval-form (first tail) env)
          (rest tail))

        "->"
        ;; Thread-first: pass accumulator as first arg to each subsequent call form.
        (reduce
          (fn [acc form]
            (if (seq? form)
              (let [[fhead & fargs] form
                    f    (eval-form fhead env)
                    args (mapv #(eval-form % env) fargs)]
                (if (fn? f)
                  (apply f acc args)
                  (throw (ex-info (str (pr-str fhead) " is not a function")
                                  {:type :eval-error}))))
              (let [f (eval-form form env)]
                (if (fn? f)
                  (f acc)
                  (throw (ex-info (str (pr-str form) " is not a function")
                                  {:type :eval-error}))))))
          (eval-form (first tail) env)
          (rest tail))

        "quasiquote"
        ;; Produced by the ` reader macro
        (expand-quasiquote (first tail) env)

        "defmacro"
        ;; (defmacro name [params] body)
        ;; Stores a macro function that receives unevaluated argument forms.
        (let [[name-sym params & body] tail
              macro-fn (fn [& args]
                         (let [bound (zipmap (map str params) args)
                               local (merge env bound)]
                           (last (map #(eval-form % local) body))))]
          (when-let [macros (:*macros* env)]
            (swap! macros assoc (str name-sym) macro-fn))
          nil)

        "defsynth"
        ;; (defsynth name [params] body...)
        ;; Registers a synth definition. The body (unevaluated AST) and the
        ;; current env are stored so the audio layer can build the Web Audio graph.
        (let [[name-sym params & body] tail
              synth-name  (keyword (str name-sym))
              param-names (mapv str params)]
          (when-let [synths (:*synths* env)]
            (swap! synths assoc synth-name {:params param-names :body body :env env}))
          ;; Notify the audio layer (wired in by app.cljs via :*register-synth-fn*)
          (when-let [reg-fn (:*register-synth-fn* env)]
            (reg-fn synth-name param-names body env))
          ;; Expose the name in defs so it's visible to the Lisp user
          (when-let [defs (:*defs* env)]
            (swap! defs assoc (str name-sym) synth-name))
          synth-name)

        "loop"
        ;; (loop [bindings...] body...)
        ;; Establishes a recursion point. `recur` jumps back with new binding values.
        (let [[bindings & body] tail
              pairs        (partition 2 bindings)
              binding-names (mapv (fn [[s _]] (str s)) pairs)]
          (loop [current-vals (mapv (fn [[_ vf]] (eval-form vf env)) pairs)
                 iterations   0]
            (when (> iterations max-loop-iterations)
              (throw (ex-info (str "loop exceeded " max-loop-iterations
                                   " iterations")
                              {:type :eval-error})))
            (let [local (reduce (fn [e [n v]] (assoc e n v))
                                env
                                (map vector binding-names current-vals))
                  result (reduce (fn [_ form]
                                   (let [r (eval-form form local)]
                                     (if (recur-sentinel? r)
                                       (reduced r)
                                       r)))
                                 nil body)]
              (if (recur-sentinel? result)
                (recur (::args result) (inc iterations))
                result))))

        "recur"
        ;; (recur expr...) — jump to enclosing loop with new binding values
        (recur-sentinel (mapv #(eval-form % env) tail))

        ;; Function call — check macros first, then eval as function
        (let [head-name (when (symbol? head) (str head))
              macros    (some-> (:*macros* env) deref)]
          (if-let [macro-fn (and head-name macros (get macros head-name))]
            ;; Macro expansion: call with unevaluated forms, then eval the result
            (let [expanded (apply macro-fn tail)]
              (eval-form expanded env))
            ;; Normal function call
            (let [f   (eval-form head env)
                  src (source-of head)]
              (if (fn? f)
                (try
                  (apply f (map #(eval-form % env) tail))
                  (catch :default e
                    ;; Re-throw with the source position of the call head,
                    ;; unless the error already carries a more specific range.
                    (let [data (ex-data e)
                          loc  (if (contains? data :from)
                                 (select-keys data [:from :to])
                                 {:from (:from src) :to (:to src)})]
                      (throw (ex-info (.-message e)
                                      (merge {:type :eval-error} loc))))))
                (throw (ex-info (str (pr-str head) " is not a function")
                                {:type :eval-error :from (:from src) :to (:to src)}))))))))

    :else
    (throw (ex-info (str "Cannot evaluate: " (pr-str form)) {:type :eval-error}))))

(defn make-env [stop-fn bpm-fn]
  (let [defs   (atom {})
        macros (atom {})
        synths (atom {})]
    {"seq"    (fn [& vs]
                (let [srcs (mapv source-of vs)
                      vals (mapv unwrap vs)]
                  (core/seq* vals srcs)))
     "stack"  (fn [& ps] (core/stack* (mapv unwrap ps)))
     "pure"   (fn [v] (core/pure (unwrap v) (source-of v)))
     "fast"   (fn
                ([f]   (fn [p] (core/fast (->num f) (unwrap p))))
                ([f p] (core/fast (->num f) (unwrap p))))
     "slow"   (fn
                ([f]   (fn [p] (core/slow (->num f) (unwrap p))))
                ([f p] (core/slow (->num f) (unwrap p))))
     "rev"    (fn [p] (core/rev (unwrap p)))
     "every"  (fn [n t p] (core/every (unwrap n) t (unwrap p)))
     "fmap"      (fn [f p] (core/fmap (fn [v] (unwrap (f v))) (unwrap p)))
     "scale"     (fn [kw root pat]
                   (theory/scale (unwrap kw) (unwrap root) (unwrap pat)))
     "chord"     (fn [kw root]
                   (theory/chord (unwrap kw) (unwrap root) (source-of kw)))
     "transpose" (fn
                   ([n]     (fn [p] (theory/transpose (unwrap n) (unwrap p))))
                   ([n pat] (theory/transpose (unwrap n) (unwrap pat))))
     ;; Per-event parameters — curried: one arg returns a (pat → pat) transformer
     "amp"     (fn
                 ([v]   (params/amp (unwrap v)))
                 ([v p] (params/amp (unwrap v) (unwrap p))))
     "attack"  (fn
                 ([v]   (params/attack (unwrap v)))
                 ([v p] (params/attack (unwrap v) (unwrap p))))
     "decay"   (fn
                 ([v]   (params/decay (unwrap v)))
                 ([v p] (params/decay (unwrap v) (unwrap p))))
     "release" (fn
                 ([v]   (params/release (unwrap v)))
                 ([v p] (params/release (unwrap v) (unwrap p))))
     "pan"     (fn
                 ([v]   (params/pan (unwrap v)))
                 ([v p] (params/pan (unwrap v) (unwrap p))))
     ;; Pattern combinators — Phase I
     "euclidean"    (fn
                      ([k n v]   (core/euclidean (unwrap k) (unwrap n) (unwrap v)))
                      ([k n v r] (core/euclidean (unwrap k) (unwrap n) (unwrap v) (unwrap r))))
     "cat"          (fn [& ps]      (core/cat* (mapv unwrap ps)))
     "late"         (fn
                      ([a]   (fn [p] (core/late  (unwrap a) (unwrap p))))
                      ([a p] (core/late  (unwrap a) (unwrap p))))
     "early"        (fn
                      ([a]   (fn [p] (core/early (unwrap a) (unwrap p))))
                      ([a p] (core/early (unwrap a) (unwrap p))))
     "sometimes"    (fn [f p]       (core/sometimes (unwrap f) (unwrap p)))
     "often"        (fn [f p]       (core/often (unwrap f) (unwrap p)))
     "rarely"       (fn [f p]       (core/rarely (unwrap f) (unwrap p)))
     "sometimes-by" (fn [prob f p]  (core/sometimes-by (unwrap prob) (unwrap f) (unwrap p)))
     "degrade"      (fn [p]         (core/degrade (unwrap p)))
     "degrade-by"   (fn
                      ([prob]   (fn [p] (core/degrade-by (unwrap prob) (unwrap p))))
                      ([prob p] (core/degrade-by (unwrap prob) (unwrap p))))
     "choose"       (fn [xs]
                      (let [xs' (unwrap xs)]
                        (core/choose (mapv unwrap xs') (mapv source-of xs'))))
     "wchoose"      (fn [pairs]
                      (let [pairs' (unwrap pairs)
                            srcs   (mapv #(source-of (second %)) pairs')
                            vecs   (mapv (fn [[w v]] [(unwrap w) (unwrap v)]) pairs')]
                        (core/wchoose vecs srcs)))
     "jux"          (fn [f p]       (params/jux (unwrap f) (unwrap p)))
     "jux-by"       (fn [w f p]     (params/jux-by (unwrap w) (unwrap f) (unwrap p)))
     "off"          (fn [a f p]     (core/off (unwrap a) (unwrap f) (unwrap p)))
     "comp"    (fn [& fs] (apply comp fs))
     ;; Sample playback control — Phase L
     "rate"        (fn
                     ([v]   (params/rate (unwrap v)))
                     ([v p] (params/rate (unwrap v) (unwrap p))))
     "begin"       (fn
                     ([v]   (params/begin (unwrap v)))
                     ([v p] (params/begin (unwrap v) (unwrap p))))
     "end"         (fn
                     ([v]   (params/end* (unwrap v)))
                     ([v p] (params/end* (unwrap v) (unwrap p))))
     "loop-sample" (fn
                     ([v]   (params/loop-sample (unwrap v)))
                     ([v p] (params/loop-sample (unwrap v) (unwrap p))))
     ;; Built-in synth voices — Phase L
     "saw"    (fn [note]
                {:note (unwrap note) :synth :saw})
     "square" (fn [note & opts]
                (let [n    (unwrap note)
                      opts' (apply hash-map (mapv unwrap opts))
                      pw   (get opts' :pw 0.5)]
                  {:note n :synth :square :pw pw}))
     "noise"  (fn [] {:synth :noise})
     "fm"     (fn [note & opts]
                (let [n     (unwrap note)
                      opts' (apply hash-map (mapv unwrap opts))
                      idx   (get opts' :index 1.0)
                      ratio (get opts' :ratio 2.0)]
                  {:note n :synth :fm :index idx :ratio ratio}))
     ;; synth transformer — apply a voice to an entire note pattern.
     ;; Also computes :freq for user-defined synths that need it.
     "synth"  (fn [voice-arg & rest-args]
                (let [voice    (unwrap voice-arg)
                      args'    (mapv unwrap rest-args)
                      last-a   (last args')
                      has-pat? (and (seq args')
                                    (map? last-a)
                                    (fn? (:query last-a)))]
                  ;; Detect common mistake: transformer (amp, pan, …) passed directly
                  ;; as a synth argument instead of chained via ->>.
                  (when (and (seq args') (fn? last-a) (not has-pat?))
                    (throw (js/Error. "amp, pan and other transformers must be chained with ->>, not passed as synth arguments.\nUse: (->> (synth :saw pattern) (amp 0.7))")))
                  (let [opts-map (apply hash-map (if has-pat? (butlast args') args'))
                        apply-xf (fn [pat]
                                    (core/fmap
                                     (fn [v]
                                       (let [base (if (map? v) v {:note v})
                                             freq (or (:freq base)
                                                      (when (number? (:note base)) (:note base))
                                                      (when (keyword? (:note base))
                                                        (theory/note->hz (:note base))))]
                                         (merge base {:synth voice :freq freq} opts-map)))
                                     pat))]
                    (if has-pat?
                      (apply-xf last-a)
                      apply-xf))))
     ;; Mini-notation
     "~"       (fn [s]
                 (let [src         (source-of s)
                       base-offset (when src (inc (:from src)))]
                   (mini/parse (unwrap s) base-offset)))
     "alt"     (fn [& pats] (mini/alt* (mapv unwrap pats)))
     ;; Curve generators for (fx :waveshape) — Phase DST5
     "chebyshev"
     (fn [n-arg]
       (let [n (unwrap n-arg)
             N 512
             arr (js/Float32Array. N)]
         (when (or (< n 1) (> n 8))
           (throw (js/Error. (str "chebyshev: order must be 1–8, got " n))))
         (dotimes [i N]
           (let [x (- (* 2.0 (/ i (dec N))) 1.0)
                 t (loop [t0 1.0 t1 x k 1]
                     (if (>= k n) t1
                       (recur t1 (- (* 2.0 x t1) t0) (inc k))))]
             (aset arr i (js/Math.max -1.0 (js/Math.min 1.0 t)))))
         arr))
     "fold"
     (fn []
       (let [N 512
             arr (js/Float32Array. N)]
         (dotimes [i N]
           (let [x (- (* 2.0 (/ i (dec N))) 1.0)
                 folded (let [a (js/Math.abs x)
                              t (mod a 1.0)
                              v (if (< t 0.5) (* t 2.0) (- 2.0 (* t 2.0)))]
                          (if (neg? x) (- v) v))]
             (aset arr i (js/Math.max -1.0 (js/Math.min 1.0 folded)))))
         arr))
     "bitcrush"
     (fn [bits-arg]
       (let [bits (unwrap bits-arg)
             N 512
             arr (js/Float32Array. N)
             steps (js/Math.pow 2 bits)]
         (when (or (< bits 1) (> bits 16))
           (throw (js/Error. (str "bitcrush: bits must be 1–16, got " bits))))
         (dotimes [i N]
           (let [x (- (* 2.0 (/ i (dec N))) 1.0)
                 quantized (/ (js/Math.round (* x (/ steps 2.0))) (/ steps 2.0))]
             (aset arr i (js/Math.max -1.0 (js/Math.min 1.0 quantized)))))
         arr))
     ;; Sound helpers
     "sound"  (fn [bank n] {:bank (unwrap bank) :n (or (unwrap n) 0)})
     "bpm"    (fn [b] (bpm-fn (->num b)) nil)
     ;; Arithmetic and comparison — unwrap SourcedVals and coerce rationals
     "+"      (fn [& args] (apply + (map ->num args)))
     "-"      (fn [& args] (apply - (map ->num args)))
     "*"      (fn [& args] (apply * (map ->num args)))
     "/"      (fn [& args] (apply / (map ->num args)))
     "="      (fn [& args] (apply = (map unwrap args)))
     "not="   (fn [& args] (apply not= (map unwrap args)))
     "<"      (fn [& args] (apply < (map ->num args)))
     ">"      (fn [& args] (apply > (map ->num args)))
     "<="     (fn [& args] (apply <= (map ->num args)))
     ">="     (fn [& args] (apply >= (map ->num args)))
     "not"    (fn [x] (not (unwrap x)))
     ;; Map operations
     "get"    (fn [m k & rest]
                (let [m' (unwrap m)
                      k' (unwrap k)]
                  (if (seq rest)
                    (get m' k' (unwrap (first rest)))
                    (get m' k'))))
     "assoc"  (fn [m k v] (assoc (unwrap m) (unwrap k) (unwrap v)))
     "merge"  (fn [& ms]  (apply merge (map unwrap ms)))
     "keys"   (fn [m]     (keys (unwrap m)))
     "vals"   (fn [m]     (vals (unwrap m)))
     ;; Song arrangement
     "arrange"     (fn [plan]
                     (core/arrange*
                       (mapv (fn [[pat dur]] [(unwrap pat) (unwrap dur)]) plan)))
     "play-scenes" (fn [sections]
                     (core/arrange*
                       (mapv (fn [pat] [(unwrap pat) 1]) sections)))
     ;; Collection helpers — useful for loop/recur and macro writing
     "conj"    (fn [coll v]
                 (conj (unwrap coll) (unwrap v)))
     "apply"   (fn [f & args]
                 (let [last-arg  (mapv unwrap (unwrap (last args)))
                       init-args (map unwrap (butlast args))]
                   (apply f (concat init-args last-arg))))
     "list"    (fn [& vs] (apply list (map unwrap vs)))
     "count"   (fn [coll] (count (unwrap coll)))
     "nth"     (fn [coll i] (nth (unwrap coll) (unwrap i)))
     "first"   (fn [coll] (first (unwrap coll)))
     "rest"    (fn [coll] (rest (unwrap coll)))
     "empty?"  (fn [coll] (empty? (unwrap coll)))
     "cons"    (fn [x coll] (cons (unwrap x) (unwrap coll)))
     "concat"  (fn [& colls] (apply concat (map unwrap colls)))
     "vec"     (fn [coll] (vec (unwrap coll)))
     "map"     (fn [f coll] (map f (unwrap coll)))
     "filter"  (fn [f coll] (filter f (unwrap coll)))
     "reduce"  (fn
                 ([f coll]    (reduce f (unwrap coll)))
                 ([f init coll] (reduce f (unwrap init) (unwrap coll))))
     "range"   (fn
                 ([n]        (range (unwrap n)))
                 ([a b]      (range (unwrap a) (unwrap b)))
                 ([a b step] (range (unwrap a) (unwrap b) (unwrap step))))
     "mod"     (fn [a b] (mod (->num a) (->num b)))
     "quot"    (fn [a b] (quot (->num a) (->num b)))
     "abs"     (fn [x]   (Math/abs (->num x)))
     "max"     (fn [& args] (apply max (map ->num args)))
     "min"     (fn [& args] (apply min (map ->num args)))
     "str"     (fn [& args] (apply str (map unwrap args)))
     "symbol"  (fn [s] (symbol (unwrap s)))
     "keyword" (fn [s] (keyword (unwrap s)))
     "name"    (fn [k] (cljs.core/name (unwrap k)))
     "number?" (fn [x] (number? (unwrap x)))
     "string?" (fn [x] (string? (unwrap x)))
     "keyword?" (fn [x] (keyword? (unwrap x)))
     "map?"    (fn [x] (map? (unwrap x)))
     "seq?"    (fn [x] (seq? (unwrap x)))
     "vector?" (fn [x] (vector? (unwrap x)))
     "nil?"    (fn [x] (nil? (unwrap x)))
     "identity" (fn [x] x)
     "tween"  (fn [curve-arg start-arg end-arg dur-arg]
                (let [curve (unwrap curve-arg)
                      start (->num start-arg)
                      end   (->num end-arg)
                      dur   (->num dur-arg)]
                  (when-not (#{:linear :exp :sine} curve)
                    (throw (js/Error.
                             (str "Unknown curve type " curve
                                  ". Available: :linear, :exp, :sine"))))
                  (when-not (pos? dur)
                    (throw (js/Error. "Transition duration must be > 0")))
                  {:type :tween :curve curve :start start :end end :duration-bars dur}))
     ;; General envelope constructor — returns pure data, usable at top level and
     ;; inside defsynth bodies.  Passed to (env-gen data source) inside a synth.
     "env"    (fn [& args]
                (let [args'  (mapv unwrap args)
                      ;; Unwrap SourcedVal records inside each vector element
                      levels (mapv unwrap (nth args' 0 []))
                      times  (mapv unwrap (nth args' 1 []))
                      curves (mapv unwrap (nth args' 2 []))]
                  (when (not= (count times) (dec (count levels)))
                    (throw (js/Error.
                             (str "env: times must have exactly (count levels - 1) elements. "
                                  "Got " (count levels) " levels and " (count times) " times."))))
                  {:type   :envelope
                   :levels levels
                   :times  times
                   :curves (into curves
                                 (repeat (max 0 (- (count times) (count curves))) :lin))}))
     "stop"   stop-fn
     :*defs*  defs
     :*macros* macros
     :*synths* synths}))
