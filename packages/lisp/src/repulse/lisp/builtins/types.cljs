(ns repulse.lisp.builtins.types
  "Type predicate builtins: number?, string?, keyword?, map?, seq?, vector?, nil?"
  (:require [repulse.lisp.util :as u]))

(defn make-builtins []
  {"number?"  (fn [x] (number? (u/unwrap x)))
   "string?"  (fn [x] (string? (u/unwrap x)))
   "keyword?" (fn [x] (keyword? (u/unwrap x)))
   "map?"     (fn [x] (map? (u/unwrap x)))
   "seq?"     (fn [x] (seq? (u/unwrap x)))
   "vector?"  (fn [x] (vector? (u/unwrap x)))
   "nil?"     (fn [x] (nil? (u/unwrap x)))})
