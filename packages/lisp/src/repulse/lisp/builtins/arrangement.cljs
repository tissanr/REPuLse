(ns repulse.lisp.builtins.arrangement
  "Song-arrangement builtins: arrange, play-scenes."
  (:require [repulse.core :as core]
            [repulse.lisp.util :as u]))

(defn make-builtins []
  {"arrange"     (fn [plan]
                   (core/arrange*
                     (mapv (fn [[pat dur]] [(u/unwrap pat) (u/unwrap dur)]) plan)))
   "play-scenes" (fn [sections]
                   (core/arrange*
                     (mapv (fn [pat] [(u/unwrap pat) 1]) sections)))})
