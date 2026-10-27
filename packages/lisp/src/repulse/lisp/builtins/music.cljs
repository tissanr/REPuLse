(ns repulse.lisp.builtins.music
  "Music-theory builtins: scale, chord, transpose, voicing, root-notes,
   scale-transpose."
  (:require [clojure.string :as str]
            [repulse.core :as core]
            [repulse.theory :as theory]
            [repulse.lisp.util :as u]))

(defn- eval-error [msg]
  (ex-info msg {:type :eval-error}))

(defn- numeric-token? [tok]
  (boolean (re-matches #"-?\d+(\.\d+)?(/\d+)?" tok)))

(defn- check-chord-literal!
  "For a string argument, throw an eval error for the first token that is not
   a valid chord symbol. Non-strings (patterns, keywords) are left to query time."
  [x]
  (when (string? x)
    (doseq [tok (remove str/blank? (str/split x #"[\s\[\]<>*?:@,!%(){}|~_.]+"))
            :when (not (numeric-token? tok))]
      (when-not (theory/parse-chord-symbol tok)
        (throw (eval-error (theory/chord-symbol-error tok)))))))

(defn- chord-pat-arg
  "Pattern argument holding chord symbols: validate string literals, then parse
   as mini-notation."
  [x]
  (let [v (u/unwrap x)]
    (check-chord-literal! v)
    (u/pat-arg x)))

(defn- rewrite-intervals
  "Replace interval tokens (\"3M\", \"-2M\", \"5P\") by their semitone count,
   padded to the same width so editor source offsets stay exact."
  [s]
  (str/replace s #"-?\d+[PMmAd]"
               (fn [tok]
                 (let [n (theory/interval->semitones-str tok)]
                   (when (nil? n)
                     (throw (eval-error (str "transpose: invalid interval \"" tok "\""))))
                   (let [r (str n)]
                     (str r (apply str (repeat (max 0 (- (count tok) (count r))) " "))))))))

(defn- transpose-amount [n]
  (let [v (u/unwrap n)]
    (if (string? v)
      (u/pat-arg (if (u/sourced? n) (assoc n :v (rewrite-intervals v)) (rewrite-intervals v)))
      (u/pat-arg n))))

(defn- scale-name-arg [x]
  (let [v (u/unwrap x)]
    (cond
      (keyword? v) (do (when-not (some #{v} (theory/scale-names))
                         (throw (eval-error
                                 (str "scale: unknown scale " v " — known: "
                                      (str/join " " (theory/scale-names))))))
                       v)
      (vector? v)  (u/deep-unwrap v)
      :else        (u/pat-arg x))))

(defn- scale-root-arg [x]
  (let [v (u/unwrap x)]
    (if (keyword? v) v (u/pat-arg x))))

(defn- pattern-value? [v] (core/pattern? v))

(def ^:private voicing-keys #{:anchor :mode :offset})

(defn- voicing-opts [m]
  (let [m (u/deep-unwrap m)]
    (doseq [k (keys m)]
      (when-not (voicing-keys k)
        (throw (eval-error (str "voicing: unknown option " k " — known: :anchor :mode :offset")))))
    (when (and (contains? m :mode) (not (theory/voicing-modes (:mode m))))
      (throw (eval-error (str "voicing: unknown mode " (:mode m) " — known: :below :above :duck :root"))))
    m))

(defn make-builtins []
  {"scale"     (fn
                 ([name root]
                  (let [n (scale-name-arg name) r (scale-root-arg root)]
                    (fn [pat] (theory/scale n r (u/unwrap pat)))))
                 ([name root pat]
                  (theory/scale (scale-name-arg name) (scale-root-arg root) (u/unwrap pat))))
   "chord"     (fn
                 ([sym] (theory/chord-symbols (chord-pat-arg sym)))
                 ([kw root]
                  (theory/chord (u/unwrap kw) (u/unwrap root) (u/source-of kw))))
   "transpose" (fn
                 ([n]     (let [n (transpose-amount n)]
                            (fn [p] (theory/transpose n (u/unwrap p)))))
                 ([n pat] (theory/transpose (transpose-amount n) (u/unwrap pat))))
   "voicing"   (fn
                 ([x] (let [v (u/unwrap x)]
                        (if (and (map? v) (not (pattern-value? v)))
                          (let [opts (voicing-opts v)]
                            (fn [pat] (theory/voicing opts (chord-pat-arg pat))))
                          (theory/voicing (chord-pat-arg x)))))
                 ([opts pat] (theory/voicing (voicing-opts opts) (chord-pat-arg pat))))
   "root-notes" (fn
                  ([x] (let [v (u/unwrap x)]
                         (if (or (number? v)
                                 (and (vector? v) (= 2 (count v)) (every? number? v)))
                           (fn [pat] (theory/root-notes v (chord-pat-arg pat)))
                           (theory/root-notes (chord-pat-arg x)))))
                  ([oct pat] (theory/root-notes (u/pat-arg oct) (chord-pat-arg pat))))
   "scale-transpose" (fn
                       ([n sc] (let [n (u/pat-arg n) sc (u/deep-unwrap sc)]
                                 (fn [pat] (theory/scale-transpose n sc (u/unwrap pat)))))
                       ([n sc pat] (theory/scale-transpose (u/pat-arg n) (u/deep-unwrap sc) (u/unwrap pat))))})
