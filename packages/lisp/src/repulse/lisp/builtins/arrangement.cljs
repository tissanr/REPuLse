(ns repulse.lisp.builtins.arrangement
  "Song-arrangement builtins: arrange, play-scenes, section."
  (:require [repulse.core :as core]
            [repulse.song :as song]
            [repulse.lisp.util :as u]))

(defn- source-for
  "Source range of the argument that make-section complained about."
  [arg name-arg bars-arg tracks-arg]
  (case arg
    :name   (u/source-of name-arg)
    :bars   (or (u/source-of bars-arg) (u/source-of name-arg))
    :tracks (or (u/source-of tracks-arg) (u/source-of name-arg))
    (u/source-of name-arg)))

(defn- track-sources
  "keyword → {:from :to} for each literal track keyword in the tracks vector."
  [tracks-arg]
  (let [v (u/unwrap tracks-arg)]
    (when (vector? v)
      (into {} (keep (fn [x]
                       (let [k (u/unwrap x) src (u/source-of x)]
                         (when (and (keyword? k) src) [k src]))))
            v))))

(defn make-builtins []
  {"arrange"     (fn [plan]
                   (core/arrange*
                     (mapv (fn [[pat dur]] [(u/unwrap pat) (u/unwrap dur)]) plan)))
   "play-scenes" (fn [sections]
                   (core/arrange*
                     (mapv (fn [pat] [(u/unwrap pat) 1]) sections)))
   ;; (section name bars tracks & more) — `more` is reserved for SONG2's (automate …).
   "section"     (fn [name-arg bars-arg tracks-arg & more]
                   (let [nm (u/unwrap name-arg)]
                     (when (seq more)
                       (let [extra (first more) src (u/source-of extra)]
                         (throw (ex-info (str "section " (pr-str nm) ": unexpected argument "
                                              (pr-str (u/deep-unwrap extra))
                                              " — sections take (section name bars tracks)")
                                         (cond-> {:type :eval-error} src (merge src))))))
                     (let [sec (try
                                 (song/make-section nm (u/unwrap bars-arg) (u/deep-unwrap tracks-arg))
                                 (catch :default e
                                   (let [data (ex-data e)
                                         src  (source-for (:arg data) name-arg bars-arg tracks-arg)]
                                     (throw (ex-info (ex-message e)
                                                     (cond-> (assoc data :type :eval-error)
                                                       src (merge src)))))))]
                       (assoc sec
                              :source (or (u/source-of tracks-arg) (u/source-of name-arg))
                              :track-sources (or (track-sources tracks-arg) {})))))})
