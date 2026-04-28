(ns repulse.lisp-patcher
  "Paren-aware source patching for live slider updates.
   Replaces the previous regex approach with a minimal tokenizer that skips
   comments and string literals, making patches correct under nesting, comments,
   and duplicate parameter names across tracks.

   All returned positions are character offsets into the original doc string.")

;; ─── Tokenizer ────────────────────────────────────────────────────────────────

(defn- atom-break? [s i]
  (.test #"[\s()\[\]{};\"`,']" (.charAt s i)))

(defn- next-tok [s pos end]
  (when (< pos end)
    (let [c (.charAt s pos)]
      (case c
        "(" {:t :lp   :from pos :to (inc pos)}
        ")" {:t :rp   :from pos :to (inc pos)}
        "[" {:t :lp   :from pos :to (inc pos)}
        "]" {:t :rp   :from pos :to (inc pos)}
        ";" (let [nl (.indexOf s "\n" pos)
                  e  (if (neg? nl) end (inc nl))]
              {:t :comment :from pos :to e})
        "\"" (loop [i (inc pos)]
               (cond
                 (>= i end)             {:t :str :from pos :to end}
                 (= (.charAt s i) "\\") (recur (+ i 2))
                 (= (.charAt s i) "\"") {:t :str :from pos :to (inc i)}
                 :else                  (recur (inc i))))
        (if (.test #"\s" c)
          (loop [i (inc pos)]
            (if (and (< i end) (.test #"\s" (.charAt s i)))
              (recur (inc i))
              {:t :ws :from pos :to i}))
          (loop [i (inc pos)]
            (if (and (< i end) (not (atom-break? s i)))
              (recur (inc i))
              {:t :atom :from pos :to i})))))))

(defn- tokenize
  "Returns vector of significant tokens (skips :ws :comment :str) from doc[start..end]."
  [doc start end]
  (loop [pos start acc (transient [])]
    (if (>= pos end)
      (persistent! acc)
      (if-let [{:keys [t to] :as tok} (next-tok doc pos end)]
        (if (#{:ws :comment :str} t)
          (recur to acc)
          (recur to (conj! acc tok)))
        (persistent! acc)))))

;; ─── Helpers ──────────────────────────────────────────────────────────────────

(defn- atom-text [doc {:keys [t from to]}]
  (when (= t :atom) (.substring doc from to)))

(defn- num-atom? [doc tok]
  (when-let [txt (atom-text doc tok)]
    (.test #"^-?[0-9]" txt)))

(defn- scan-for-named-param
  "Within toks[start-j..] at depth=1 relative to an enclosing form, find
   :param-name NUMBER. Returns {:from N :to N} or nil when the form ends."
  [doc toks start-j n param-name]
  (loop [j start-j depth 1]
    (when (and (< j n) (pos? depth))
      (let [tj   (nth toks j)
            tj+1 (when (< (inc j) n) (nth toks (inc j)))]
        (cond
          (= :lp (:t tj))
          (recur (inc j) (inc depth))

          (= :rp (:t tj))
          (recur (inc j) (dec depth))

          (and (= :atom (:t tj))
               (= (str ":" param-name) (atom-text doc tj))
               tj+1
               (num-atom? doc tj+1))
          (select-keys tj+1 [:from :to])

          :else
          (recur (inc j) depth))))))

;; ─── Public API ───────────────────────────────────────────────────────────────

(defn find-param-num
  "Within doc[scope-start..scope-end], find the first (param-name NUMBER ...)
   form, paren-aware, skipping comments and strings.
   Returns {:from N :to N} of the number token, or nil."
  [doc scope-start scope-end param-name]
  (let [toks (tokenize doc scope-start scope-end)
        n    (count toks)]
    (loop [i 0]
      (when (< i (- n 2))
        (let [t0 (nth toks i)
              t1 (nth toks (inc i))
              t2 (nth toks (+ i 2))]
          (if (and (= :lp (:t t0))
                   (= param-name (atom-text doc t1))
                   (num-atom? doc t2))
            (select-keys t2 [:from :to])
            (recur (inc i))))))))

(defn find-fx-named-param-num
  "Within doc[scope-start..scope-end], find :param-name NUMBER inside the
   first (fx :effect-name ...) form.
   Returns {:from N :to N} of the number token, or nil."
  [doc scope-start scope-end effect-name param-name]
  (let [toks (tokenize doc scope-start scope-end)
        n    (count toks)]
    (loop [i 0]
      (when (< i (- n 2))
        (let [t0 (nth toks i)
              t1 (nth toks (inc i))
              t2 (when (< (+ i 2) n) (nth toks (+ i 2)))]
          (if (and (= :lp (:t t0))
                   (= "fx" (atom-text doc t1))
                   t2
                   (= (str ":" effect-name) (atom-text doc t2)))
            (let [result (scan-for-named-param doc toks (+ i 3) n param-name)]
              (if result
                result
                (recur (inc i))))
            (recur (inc i))))))))

(defn find-fx-pos-param-num
  "Within doc[scope-start..scope-end], find the positional NUMBER in
   (fx :effect-name NUMBER ...).
   Returns {:from N :to N} of the number token, or nil."
  [doc scope-start scope-end effect-name]
  (let [toks (tokenize doc scope-start scope-end)
        n    (count toks)]
    (loop [i 0]
      (when (< i (- n 3))
        (let [t0 (nth toks i)
              t1 (nth toks (inc i))
              t2 (nth toks (+ i 2))
              t3 (when (< (+ i 3) n) (nth toks (+ i 3)))]
          (if (and (= :lp (:t t0))
                   (= "fx" (atom-text doc t1))
                   (= (str ":" effect-name) (atom-text doc t2))
                   t3
                   (num-atom? doc t3))
            (select-keys t3 [:from :to])
            (recur (inc i))))))))

(defn find-fx-form-close
  "Within doc[scope-start..scope-end], find the character offset of the closing )
   of the first (fx :effect-name ...) form, or nil."
  [doc scope-start scope-end effect-name]
  (let [toks (tokenize doc scope-start scope-end)
        n    (count toks)]
    (loop [i 0]
      (when (< i (- n 2))
        (let [t0 (nth toks i)
              t1 (nth toks (inc i))
              t2 (when (< (+ i 2) n) (nth toks (+ i 2)))]
          (if (and (= :lp (:t t0))
                   (= "fx" (atom-text doc t1))
                   t2
                   (= (str ":" effect-name) (atom-text doc t2)))
            (loop [j (+ i 3) depth 1]
              (when (and (< j n) (pos? depth))
                (let [tj (nth toks j)]
                  (cond
                    (= :lp (:t tj)) (recur (inc j) (inc depth))
                    (= :rp (:t tj)) (if (= depth 1)
                                      (:from tj)
                                      (recur (inc j) (dec depth)))
                    :else           (recur (inc j) depth)))))
            (recur (inc i))))))))
