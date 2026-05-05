(ns repulse.lisp.builtins.music
  "Music-theory builtins: scale, chord, transpose."
  (:require [repulse.theory :as theory]
            [repulse.lisp.util :as u]))

(defn make-builtins []
  {"scale"     (fn [kw root pat]
                 (theory/scale (u/unwrap kw) (u/unwrap root) (u/unwrap pat)))
   "chord"     (fn [kw root]
                 (theory/chord (u/unwrap kw) (u/unwrap root) (u/source-of kw)))
   "transpose" (fn
                 ([n]     (fn [p] (theory/transpose (u/unwrap n) (u/unwrap p))))
                 ([n pat] (theory/transpose (u/unwrap n) (u/unwrap pat))))})
