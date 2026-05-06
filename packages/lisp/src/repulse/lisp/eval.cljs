(ns repulse.lisp.eval
  (:require [repulse.lisp.util :as util]
            [repulse.lisp.builtins.pattern :as b-pattern]
            [repulse.lisp.builtins.math :as b-math]
            [repulse.lisp.builtins.music :as b-music]
            [repulse.lisp.builtins.params :as b-params]
            [repulse.lisp.builtins.collection :as b-collection]
            [repulse.lisp.builtins.types :as b-types]
            [repulse.lisp.builtins.synth :as b-synth]
            [repulse.lisp.builtins.arrangement :as b-arrangement]))

;;; Re-export helpers used by app-layer code (e.g. env/builtins.cljs)

(def sourced? util/sourced?)
(def unwrap   util/unwrap)
(def deep-unwrap util/deep-unwrap)
(def source-of util/source-of)
(def ->num    util/->num)

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
          (if (and (sourced? head)
                   (every? #(number? (unwrap %)) form))
            ;; Parenthesized numeric lists are useful for data passed to host
            ;; APIs, e.g. waveshaper curves: (-1.0 -0.5 0 0.5 1.0).
            (map #(eval-form % env) form)
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
                                  {:type :eval-error :from (:from src) :to (:to src)})))))))))

    :else
    (throw (ex-info (str "Cannot evaluate: " (pr-str form)) {:type :eval-error}))))

(defn make-env [stop-fn bpm-fn]
  (let [defs   (atom {})
        macros (atom {})
        synths (atom {})]
    (merge
      (b-pattern/make-builtins)
      (b-math/make-builtins)
      (b-music/make-builtins)
      (b-params/make-builtins)
      (b-collection/make-builtins)
      (b-types/make-builtins)
      (b-synth/make-builtins)
      (b-arrangement/make-builtins)
      {"bpm"     (fn [b] (bpm-fn (->num b)) nil)
       "stop"    stop-fn
       :*defs*   defs
       :*macros* macros
       :*synths* synths})))
