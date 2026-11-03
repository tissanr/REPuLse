(ns repulse.env.builtins.song
  "Song-form builtins (Phase SONG1): song, play-from.
   `section` lives in the Lisp layer (repulse.lisp.builtins.arrangement)."
  (:require [repulse.song :as song]
            [repulse.song-transport :as st]
            [repulse.lisp.eval :as leval]))

(defn- src-data [x]
  (when-let [src (leval/source-of x)]
    {:from (:from src) :to (:to src)}))

(defn- eval-error [msg arg]
  (ex-info msg (merge {:type :eval-error} (src-data arg))))

(defn- describe [v]
  (cond
    (song/section? v) "(section …)"
    (song/song? v)    "(song …)"
    (and (map? v) (:query v)) "a pattern"
    (fn? v)           "a function"
    :else             (pr-str v)))

(defn- kw-str [k] (str ":" (name k)))

(defn- parse-song-args
  "Split (song section… :opt val …) into [sections opts]."
  [args]
  (let [vals     (mapv leval/unwrap args)
        sections (vec (take-while song/section? vals))
        rest-n   (count sections)]
    (loop [i rest-n opts {}]
      (if (>= i (count vals))
        [sections opts]
        (let [k (nth vals i)]
          (cond
            (not (keyword? k))
            (throw (eval-error (str "song: expected (section …) or an option, got " (describe k))
                               (nth args i)))

            (not= :end k)
            (throw (eval-error (str "song: unknown option " (kw-str k) " — expected :end")
                               (nth args i)))

            :else
            (let [v (when (< (inc i) (count vals)) (nth vals (inc i)))]
              (when-not (#{:stop :loop} v)
                (throw (eval-error (str "song: :end must be :stop or :loop, got " (describe v))
                                   (if (< (inc i) (count vals)) (nth args (inc i)) (nth args i)))))
              (recur (+ i 2) (assoc opts :end v)))))))))

(defn make-builtins
  "No ctx dependencies."
  [_ctx]
  {"song"
   (fn [& args]
     (let [[sections opts] (parse-song-args args)]
       (when (empty? sections)
         (throw (eval-error "song needs at least one (section …)" (first args))))
       (st/register-song! sections opts (:source (first sections)))))

   "play-from"
   (fn [target]
     (let [t (leval/unwrap target)
           s (st/current-song)]
       (when-not s
         (throw (eval-error "play-from: no song — evaluate a buffer that ends with (song …) first" target)))
       (let [bar (cond
                   (keyword? t)
                   (or (song/section-start-bar s t)
                       (let [names (mapv (comp kw-str :name) (:sections s))
                             hint  (leval/typo-hint (kw-str t) names)]
                         (throw (eval-error (str "Unknown section " (kw-str t)
                                                 (if hint
                                                   (str " — did you mean " hint "?")
                                                   (str " — sections: " (apply str (interpose " " names)))))
                                            target))))

                   (and (number? t) (integer? t))
                   (if (and (<= 0 t) (< t (song/song-length s)))
                     t
                     (throw (eval-error (str "play-from: bar " t " is outside the song (0–"
                                             (dec (song/song-length s)) ")")
                                        target)))

                   :else
                   (throw (eval-error "play-from: expected a section name or a bar number" target)))]
         (reset! st/pending-play-from bar)
         (if (keyword? t)
           (str "=> playing from " (kw-str t) " (bar " bar ")")
           (str "=> playing from bar " bar)))))})
