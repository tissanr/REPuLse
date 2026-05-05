(ns repulse.lisp.builtins.math
  "Arithmetic and comparison builtins: +, -, *, /, =, <, mod, etc."
  (:require [repulse.lisp.util :as u]))

(defn make-builtins []
  {"+"    (fn [& args] (apply + (map u/->num args)))
   "-"    (fn [& args] (apply - (map u/->num args)))
   "*"    (fn [& args] (apply * (map u/->num args)))
   "/"    (fn [& args] (apply / (map u/->num args)))
   "="    (fn [& args] (apply = (map u/unwrap args)))
   "not=" (fn [& args] (apply not= (map u/unwrap args)))
   "<"    (fn [& args] (apply < (map u/->num args)))
   ">"    (fn [& args] (apply > (map u/->num args)))
   "<="   (fn [& args] (apply <= (map u/->num args)))
   ">="   (fn [& args] (apply >= (map u/->num args)))
   "not"  (fn [x] (not (u/unwrap x)))
   "mod"  (fn [a b] (mod (u/->num a) (u/->num b)))
   "quot" (fn [a b] (quot (u/->num a) (u/->num b)))
   "abs"  (fn [x] (Math/abs (u/->num x)))
   "max"  (fn [& args] (apply max (map u/->num args)))
   "min"  (fn [& args] (apply min (map u/->num args)))})
