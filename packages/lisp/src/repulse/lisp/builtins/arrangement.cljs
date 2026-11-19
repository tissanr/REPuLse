(ns repulse.lisp.builtins.arrangement
  "Song-arrangement builtins: arrange, play-scenes, section, ramp, automate, fill."
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

(defn- song-call
  "Run (f) and turn a :song-error into an :eval-error located at src-fn's result."
  [f src-fn]
  (try
    (f)
    (catch :default e
      (let [data (ex-data e)]
        (if (= :song-error (:type data))
          (let [src (src-fn data)]
            (throw (ex-info (ex-message e)
                            (cond-> (assoc data :type :eval-error)
                              src (merge src)))))
          (throw e))))))

(defn- eval-error [msg src]
  (ex-info msg (cond-> {:type :eval-error} src (merge src))))

(defn- parse-automate-opts
  "Keyword pairs after the curve → {:bars … :op …}."
  [opts]
  (loop [xs (seq opts) acc {}]
    (if-not xs
      acc
      (let [k (u/unwrap (first xs)) ksrc (u/source-of (first xs))]
        (when-not (#{:bars :op} k)
          (throw (eval-error (str "automate: unknown option " (pr-str k)
                                  " — expected :bars or :op")
                             ksrc)))
        (when (nil? (next xs))
          (throw (eval-error (str "automate: option " (pr-str k) " needs a value") ksrc)))
        (recur (nnext xs) (assoc acc k (u/deep-unwrap (second xs))))))))

(defn make-builtins []
  {"arrange"     (fn [plan]
                   (core/arrange*
                     (mapv (fn [[pat dur]] [(u/unwrap pat) (u/unwrap dur)]) plan)))
   "play-scenes" (fn [sections]
                   (core/arrange*
                     (mapv (fn [pat] [(u/unwrap pat) 1]) sections)))
   ;; (section name bars tracks & items) — items are (automate …) / (fill …) (SONG2).
   "section"     (fn [name-arg bars-arg tracks-arg & more]
                   (let [nm    (u/unwrap name-arg)
                         items (mapv u/unwrap more)
                         sec   (song-call
                                (fn [] (song/make-section nm (u/unwrap bars-arg)
                                                          (u/deep-unwrap tracks-arg) items))
                                (fn [data]
                                  (if (= :item (:arg data))
                                    (or (:source (:item data))
                                        (some-> (:item-index data) (->> (nth more)) u/source-of)
                                        (u/source-of name-arg))
                                    (source-for (:arg data) name-arg bars-arg tracks-arg))))]
                     (assoc sec
                            :source (or (u/source-of tracks-arg) (u/source-of name-arg))
                            :track-sources (or (track-sources tracks-arg) {}))))

   "ramp"        (fn [curve-arg from-arg to-arg]
                   (song-call
                    (fn [] (song/make-ramp (u/unwrap curve-arg) (u/->num from-arg) (u/->num to-arg)))
                    (fn [data]
                      (case (:arg data)
                        :curve (u/source-of curve-arg)
                        :from  (or (u/source-of from-arg) (u/source-of curve-arg))
                        (u/source-of curve-arg)))))

   "automate"    (fn [track-arg param-arg curve-arg & opts]
                   (let [src  (u/source-of track-arg)
                         o    (parse-automate-opts opts)
                         curve (let [c (u/unwrap curve-arg)]
                                 (if (and (vector? c) (= 2 (count c)) (every? number? c))
                                   (u/->num c) c))
                         a    (song-call
                               (fn [] (song/make-automation (u/unwrap track-arg) (u/unwrap param-arg)
                                                            curve o))
                               (fn [data]
                                 (case (:arg data)
                                   :param (or (u/source-of param-arg) src)
                                   :curve (or (u/source-of curve-arg) src)
                                   src)))]
                     (cond-> a src (assoc :source src))))

   "fill"        (fn [track-arg bars-arg pat-arg*]
                   (let [src (u/source-of track-arg)
                         p   (u/pat-arg pat-arg*)
                         f   (song-call
                              (fn [] (song/make-fill (u/unwrap track-arg) (u/->num bars-arg) p))
                              (fn [data]
                                (case (:arg data)
                                  :bars (or (u/source-of bars-arg) src)
                                  src)))]
                     (cond-> f src (assoc :source src))))})
