(ns repulse.lisp.core
  (:require [repulse.lisp.reader :as reader]
            [repulse.lisp.eval :as evaluator]))

(defn eval-string
  "Parse and evaluate a string in the given environment.
   Returns {:result v} or {:error msg}"
  [src env]
  (try
    (let [forms (reader/read-all src)]
      (if (empty? forms)
        {:result nil}
        (loop [remaining forms
               last-result nil]
          (if (empty? remaining)
            {:result last-result}
            (let [result (evaluator/eval-form (first remaining) env)]
              (if (and (map? result) (:error result))
                result
                (recur (rest remaining) result)))))))
    (catch :default e
      (let [data (ex-data e)]
        (cond-> {:error (or (.-message e) (str e))}
          (:from data) (assoc :from (:from data) :to (:to data)))))))
