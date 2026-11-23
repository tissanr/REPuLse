(ns repulse.edit-distance
  "Levenshtein distance and the did-you-mean helper shared by the evaluator,
   the theory layer and the app. Pure; no dependencies.")

(defn levenshtein
  "Edit distance between two strings (or any two sequences): the minimum number
   of single-element insertions, deletions and substitutions. O(m·n) time, O(n)
   memory — two reused rows."
  [a b]
  (let [a (vec a)
        b (vec b)
        m (count a)
        n (count b)]
    (if (zero? m)
      n
      (let [prev (js/Int32Array. (inc n))
            cur  (js/Int32Array. (inc n))]
        (dotimes [j (inc n)] (aset prev j j))
        (loop [i 0 prev prev cur cur]
          (if (= i m)
            (aget prev n)
            (do
              (aset cur 0 (inc i))
              (dotimes [j n]
                (aset cur (inc j)
                      (if (= (nth a i) (nth b j))
                        (aget prev j)
                        (inc (min (aget prev (inc j))
                                  (aget cur j)
                                  (aget prev j))))))
              (recur (inc i) cur prev))))))))

(defn max-typo-distance
  "Largest edit distance still reported as a typo for a name of length n:
   1 for n ≤ 4, 2 for n ≤ 8, 3 otherwise."
  [n]
  (cond (<= n 4) 1
        (<= n 8) 2
        :else    3))

(defn typo-hint
  "The element of known closest to name, when it is within max-typo-distance of
   name's length; otherwise nil. On equal distances the last candidate wins."
  [name known]
  (when (seq known)
    (let [best (apply min-key #(levenshtein name (str %)) known)]
      (when (<= (levenshtein name (str best)) (max-typo-distance (count name)))
        best))))
