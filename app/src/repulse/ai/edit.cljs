(ns repulse.ai.edit
  "Pure helpers behind the agent's editing tools: locating a propose_edit
   target, building an append, and the per-turn undo stack update.
   No DOM or CodeMirror, so everything here is unit-testable in Node."
  (:require [clojure.string :as str]))

(defn count-occurrences
  "Number of non-overlapping occurrences of `match` in `doc`."
  [doc match]
  (loop [idx 0 n 0]
    (let [i (.indexOf doc match idx)]
      (if (neg? i) n (recur (+ i (count match)) (inc n))))))

(defn line-at
  "1-based line number of character index `idx` in doc."
  [doc idx]
  (inc (count (re-seq #"\n" (subs doc 0 idx)))))

(defn append
  "Change that appends `text` at the end of `doc`, separated by a blank line
   when the buffer is not empty."
  [doc text]
  (let [n (count doc)]
    {:from n :to n :insert (str (when (seq doc) "\n\n") text)}))

(defn locate
  "Resolve a propose_edit against `doc`. Edits are anchored by exact text
   match, never by character offsets — models cannot compute those reliably
   against a buffer they saw once as plain text.
   Returns {:from :to :insert} or {:error msg}. A blank match appends."
  [doc match replacement]
  (let [match (str match)]
    (if (str/blank? match)
      (append doc replacement)
      (let [n (count-occurrences doc match)]
        (cond
          (zero? n)
          {:error (str "match not found in buffer — call read_buffer and copy the text "
                       "to replace exactly, including whitespace and newlines "
                       "(without the <untrusted> wrapper)")}

          (> n 1)
          {:error (str "match is ambiguous — found " n " occurrences; "
                       "include more surrounding lines so it matches exactly once")}

          :else
          (let [from (.indexOf doc match)]
            {:from from :to (+ from (count match)) :insert replacement}))))))

(def max-undo-turns 20)

(defn push-snapshot
  "Undo stack update: add {:turn-id :text} unless this turn already has a
   snapshot (later edits in the same turn must not overwrite the pre-turn
   text). Keeps at most max-undo-turns turns."
  [s turn-id text]
  (if (= turn-id (:turn-id (peek s)))
    s
    (let [s (conj s {:turn-id turn-id :text text})]
      (if (> (count s) max-undo-turns) (subvec s 1) s))))
