(ns repulse.tracks-model
  "Pure track reconciliation and flag rules (Phase WS5).
   After a whole-code evaluation the declared tracks are exactly what plays:
   everything else is retired, and the muted set is derived from the trailing
   :mute / :solo flags of the `track` forms.
   Exports: flags, parse-flags, muted-set, plan, reserved-track?."
  (:require [clojure.string :as str]
            [repulse.lisp.eval :as leval]))

(def flags
  "Trailing keyword options of `track`, in canonical order."
  [:mute :solo])

(def ^:private flag-key {:mute :mute? :solo :solo?})

(defn- unknown-flag-message [kw]
  (let [s    (str kw)
        ;; the options are short: only suggest within edit distance 2
        dist #(leval/levenshtein s %)
        hint (->> (map str flags)
                  (sort-by dist)
                  (filter #(<= (dist %) 2))
                  first)]
    (if hint
      (str "unknown track option " s " — did you mean " hint "?")
      (str "unknown track option " s " — expected :mute or :solo"))))

(defn parse-flags
  "Trailing track args (already unwrapped) → {:mute? bool :solo? bool}, or
   {:error msg :index i} where i is the position of the offending arg."
  [args]
  (loop [i 0 acc {:mute? false :solo? false} seen #{}]
    (if (>= i (count args))
      acc
      (let [a (nth args i)]
        (cond
          (not (keyword? a))
          {:error (str "track options must be :mute or :solo, got " (pr-str a))
           :index i}

          (not (contains? flag-key a))
          {:error (unknown-flag-message a) :index i}

          (contains? seen a)
          {:error (str "duplicate track option " a) :index i}

          :else
          (recur (inc i) (assoc acc (flag-key a) true) (conj seen a)))))))

(defn muted-set
  "declared — [[kw {:mute? :solo?}] …] → set of kws that must be silent.
   A track is audible iff it is not :mute and (no track has :solo or it has
   :solo). :mute beats :solo on the same track."
  [declared]
  (let [any-solo? (some (comp :solo? second) declared)]
    (set (for [[k {:keys [mute? solo?]}] declared
               :when (or mute? (and any-solo? (not solo?)))]
           k))))

(defn reserved-track?
  "Track names starting with __ (snippet previews) are never reconciled."
  [k]
  (and (keyword? k) (str/starts-with? (name k) "__")))

(defn plan
  "current — set of scheduled track kws; declared — set of declared kws.
   → {:retire #{…}}: every current track that is not declared, except the
   reserved :__* preview tracks."
  [current declared]
  {:retire (set (remove #(or (contains? declared %) (reserved-track? %)) current))})
