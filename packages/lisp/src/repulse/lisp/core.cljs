(ns repulse.lisp.core
  (:require [repulse.lisp.reader :as reader]
            [repulse.lisp.eval :as evaluator]))

(defrecord EvalError [message source])

(defn eval-error
  ([message] (->EvalError message nil))
  ([message source] (->EvalError message source)))

(defn eval-error? [x]
  (instance? EvalError x))

(defn eval-string
  "Parse and evaluate a string in the given environment.
   Returns {:result v} or an EvalError record."
  [src env]
  (try
    (let [forms (reader/read-all src)]
      (if (empty? forms)
        {:result nil}
        (let [final-result (loop [remaining forms
                                  last-result nil]
                             (if (empty? remaining)
                               last-result
                               (let [result (evaluator/eval-form (first remaining) env)]
                                 (if (eval-error? result)
                                   result
                                   (recur (rest remaining) result)))))]
          (if (eval-error? final-result)
            final-result
            {:result final-result}))))
    (catch :default e
      (let [data (ex-data e)]
        (eval-error (or (.-message e) (str e))
                    (when (:from data)
                      {:from (:from data) :to (:to data)}))))))
