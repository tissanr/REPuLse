(ns repulse.lisp.eval
  (:require [repulse.core :as core]
            [repulse.lisp.reader :as reader]))

;;; Source-tracking helpers

(defn- sourced? [x] (instance? reader/SourcedVal x))
(defn- unwrap  [x] (if (sourced? x) (:v x) x))
(defn- source-of [x]
  (if (sourced? x)
    (:source x)
    (:source (meta x))))

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
          defs (some-> (:*defs* env) deref)]
      (cond
        (contains? env n)   (get env n)
        (contains? defs n)  (get defs n)
        :else
        (let [known (concat (filter string? (keys env))
                            (filter string? (keys defs)))]
          (throw (ex-info (str "Undefined symbol: " n
                               (when-let [h (typo-hint n known)]
                                 (str " — did you mean " h "?")))
                          {:type :eval-error})))))

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
          ;; unwrap needed: a SourcedVal wrapping false/nil would be truthy as a record
          (if (unwrap (eval-form c env))
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

(defn make-env [stop-fn bpm-fn]
  (let [defs (atom {})]
    {"seq"    (fn [& vs]
                (let [srcs (mapv source-of vs)
                      vals (mapv unwrap vs)]
                  (core/seq* vals srcs)))
     "stack"  (fn [& ps] (core/stack* (mapv unwrap ps)))
     "pure"   (fn [v] (core/pure (unwrap v) (source-of v)))
     "fast"   (fn [f p] (core/fast (unwrap f) (unwrap p)))
     "slow"   (fn [f p] (core/slow (unwrap f) (unwrap p)))
     "rev"    (fn [p] (core/rev (unwrap p)))
     "every"  (fn [n t p] (core/every (unwrap n) t (unwrap p)))
     "fmap"   (fn [f p] (core/fmap (fn [v] (unwrap (f v))) (unwrap p)))
     ;; Sound helpers
     "sound"  (fn [bank n] {:bank (unwrap bank) :n (or (unwrap n) 0)})
     "bpm"    (fn [b] (bpm-fn (unwrap b)) nil)
     ;; Arithmetic and comparison — unwrap SourcedVals
     "+"      (fn [& args] (apply + (map unwrap args)))
     "-"      (fn [& args] (apply - (map unwrap args)))
     "*"      (fn [& args] (apply * (map unwrap args)))
     "/"      (fn [& args] (apply / (map unwrap args)))
     "="      (fn [& args] (apply = (map unwrap args)))
     "not="   (fn [& args] (apply not= (map unwrap args)))
     "<"      (fn [& args] (apply < (map unwrap args)))
     ">"      (fn [& args] (apply > (map unwrap args)))
     "<="     (fn [& args] (apply <= (map unwrap args)))
     ">="     (fn [& args] (apply >= (map unwrap args)))
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
     "stop"   stop-fn
     :*defs*  defs}))

(defn eval-top
  "Evaluate a form in the given env, returning the result or {:error ...}"
  [form env]
  (try
    (eval-form form env)
    (catch :default e
      {:error (or (.-message e) (str e))})))
