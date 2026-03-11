(ns repulse.lisp.eval
  (:require [repulse.core :as core]))

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

(defn eval-form [form env]
  (cond
    (or (number? form) (string? form) (keyword? form)
        (true? form) (false? form) (nil? form))
    form

    (vector? form)
    (mapv #(eval-form % env) form)

    (symbol? form)
    (let [n (str form)]
      (if-let [v (find env n)]
        (val v)
        (throw (ex-info (str "Undefined symbol: " n
                             (when-let [h (typo-hint n (filter string? (keys env)))]
                               (str " — did you mean " h "?")))
                        {:type :eval-error}))))

    (seq? form)
    (let [[head & tail] form]
      (case (when (symbol? head) (str head))
        "def"
        (let [[sym val-form] tail
              v (eval-form val-form env)]
          (when-let [defs (:*defs* env)]
            (swap! defs assoc (str sym) v))
          v)

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
          (if (eval-form c env)
            (eval-form t env)
            (when e (eval-form e env))))

        "do"
        (last (map #(eval-form % env) tail))

        ;; Function call
        (let [f (eval-form head env)]
          (if (fn? f)
            (apply f (map #(eval-form % env) tail))
            (throw (ex-info (str (pr-str head) " is not a function")
                            {:type :eval-error}))))))

    :else
    (throw (ex-info (str "Cannot evaluate: " (pr-str form)) {:type :eval-error}))))

(defn make-env [stop-fn]
  (let [defs (atom {})]
    {"seq"    (fn [& vs] (core/seq* (vec vs)))
     "stack"  (fn [& ps] (core/stack* (vec ps)))
     "pure"   core/pure
     "fast"   (fn [f p] (core/fast f p))
     "slow"   (fn [f p] (core/slow f p))
     "rev"    core/rev
     "every"  (fn [n t p] (core/every n t p))
     "fmap"   (fn [f p] (core/fmap f p))
     "+"      +
     "-"      -
     "*"      *
     "/"      /
     "stop"   stop-fn
     :*defs*  defs}))

(defn eval-top
  "Evaluate a form in the given env, returning the result or {:error ...}"
  [form env]
  (try
    (eval-form form env)
    (catch :default e
      {:error (or (.-message e) (str e))})))
