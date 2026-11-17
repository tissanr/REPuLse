(ns repulse.lisp-patcher
  "Paren-aware source patching for live slider updates.
   Replaces the previous regex approach with a minimal tokenizer that skips
   comments and string literals, making patches correct under nesting, comments,
   and duplicate parameter names across tracks.

   All returned positions are character offsets into the original doc string.
   Phase WS3: the numeric finders also see through (slider NUMBER min max …)
   and return the inner value literal plus :slider {:from :to :min :max};
   find-slider-forms, slider-form-at, visible-sliders, visible-of-forms and
   slider-toggle-edit serve the inline slider widgets and ⌘K S."
  (:require [repulse.ui.slider-model :as slm]))

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

(defn- tokenize*
  "Token vector from doc[start..end], dropping the token types in `skip`."
  [doc start end skip]
  (loop [pos start acc (transient [])]
    (if (>= pos end)
      (persistent! acc)
      (if-let [{:keys [t to] :as tok} (next-tok doc pos end)]
        (if (skip t)
          (recur to acc)
          (recur to (conj! acc tok)))
        (persistent! acc)))))

(defn- tokenize
  "Returns vector of significant tokens (skips :ws :comment :str) from doc[start..end]."
  [doc start end]
  (tokenize* doc start end #{:ws :comment :str}))

;; ─── Helpers ──────────────────────────────────────────────────────────────────

(defn- atom-text [doc {:keys [t from to]}]
  (when (= t :atom) (.substring doc from to)))

(defn- num-atom? [doc tok]
  (when-let [txt (atom-text doc tok)]
    (.test #"^-?[0-9]" txt)))

(defn- keyword-atom? [doc tok]
  (when-let [txt (atom-text doc tok)]
    (.startsWith txt ":")))

(defn- ^number match-close
  "Index of the token closing the list opened at toks[j] (an :lp), or nil."
  [toks j]
  (let [n (count toks)]
    (loop [k (inc j) depth 1]
      (when (< k n)
        (case (:t (nth toks k))
          :lp (recur (inc k) (inc depth))
          :rp (if (= depth 1) k (recur (inc k) (dec depth)))
          (recur (inc k) depth))))))

(defn- slider-num-at
  "When toks[j] opens `(slider NUMBER …`, the NUMBER's {:from :to} plus
   :slider {:from :to :min :max} (:min/:max nil unless numeric literals;
   :to after the matching `)`, or the doc end when unclosed). Else nil."
  [doc toks j]
  (let [n  (count toks)
        t0 (when (< j n) (nth toks j))
        t1 (when (< (inc j) n) (nth toks (inc j)))
        t2 (when (< (+ j 2) n) (nth toks (+ j 2)))]
    (when (and t0 t1 t2
               (= :lp (:t t0))
               (= "(" (.charAt doc (:from t0)))
               (= "slider" (atom-text doc t1))
               (num-atom? doc t2))
      (let [lit   (fn [k] (when (< k n) (slm/parse-num-literal (atom-text doc (nth toks k)))))
            close (match-close toks j)]
        (assoc (select-keys t2 [:from :to])
               :slider {:from (:from t0)
                        :to   (if close (:to (nth toks close)) (count doc))
                        :min  (lit (+ j 3))
                        :max  (lit (+ j 4))})))))

(defn- num-or-slider-at
  "{:from :to} of a plain number at toks[j], or slider-num-at, or nil."
  [doc toks j]
  (when (< j (count toks))
    (let [t (nth toks j)]
      (if (num-atom? doc t)
        (select-keys t [:from :to])
        (slider-num-at doc toks j)))))

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
               (num-or-slider-at doc toks (inc j)))
          (num-or-slider-at doc toks (inc j))

          :else
          (recur (inc j) depth))))))

(defn- scan-for-named-keyword-param
  "Within toks[start-j..] at depth=1 relative to an enclosing form, find
   :param-name :keyword. Returns {:from N :to N} or nil when the form ends."
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
               (keyword-atom? doc tj+1))
          (select-keys tj+1 [:from :to])

          :else
          (recur (inc j) depth))))))

;; ─── Public API ───────────────────────────────────────────────────────────────

(defn find-param-num
  "Within doc[scope-start..scope-end], find the first (param-name NUMBER ...)
   form, paren-aware, skipping comments and strings.
   Returns {:from N :to N} of the number token, or nil. A (param (slider
   NUMBER min max)) value returns the inner NUMBER plus :slider (WS3)."
  [doc scope-start scope-end param-name]
  (let [toks (tokenize doc scope-start scope-end)
        n    (count toks)]
    (loop [i 0]
      (when (< i (- n 2))
        (let [t0 (nth toks i)
              t1 (nth toks (inc i))
              r  (when (and (= :lp (:t t0))
                            (= param-name (atom-text doc t1)))
                   (num-or-slider-at doc toks (+ i 2)))]
          (or r (recur (inc i))))))))

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

(defn find-fx-named-param-keyword
  "Within doc[scope-start..scope-end], find :param-name :keyword inside the
   first (fx :effect-name ...) form.
   Returns {:from N :to N} of the keyword token, or nil."
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
            (let [result (scan-for-named-keyword-param doc toks (+ i 3) n param-name)]
              (if result
                result
                (recur (inc i))))
            (recur (inc i))))))))

(defn find-fx-pos-param-num
  "Within doc[scope-start..scope-end], find the positional NUMBER in
   (fx :effect-name NUMBER ...).
   Returns {:from N :to N} of the number token, or nil. A (slider NUMBER …)
   in that position returns the inner NUMBER plus :slider (WS3)."
  [doc scope-start scope-end effect-name]
  (let [toks (tokenize doc scope-start scope-end)
        n    (count toks)]
    (loop [i 0]
      (when (< i (- n 3))
        (let [t0 (nth toks i)
              t1 (nth toks (inc i))
              t2 (nth toks (+ i 2))
              r  (when (and (= :lp (:t t0))
                            (= "fx" (atom-text doc t1))
                            (= (str ":" effect-name) (atom-text doc t2)))
                   (num-or-slider-at doc toks (+ i 3)))]
          (or r (recur (inc i))))))))

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

;; ─── Scoped fx edits (Phase WS2) ──────────────────────────────────────────────

(defn fx-form-range
  "When doc at offset `at` starts the form `(fx :<effect-name> …)` and it closes,
   returns {:from at :to <index after its )>}. Otherwise nil (also for a nil,
   negative or out-of-range `at`)."
  [doc at effect-name]
  (when (and (number? at) (>= at 0) (< at (count doc)))
    (let [toks (tokenize doc at (count doc))
          [t0 t1 t2] toks]
      (when (and t0 t1 t2
                 (= at (:from t0))
                 (= :lp (:t t0))
                 (= "fx" (atom-text doc t1))
                 (= (str ":" effect-name) (atom-text doc t2)))
        (when-let [close (find-fx-form-close doc at (count doc) effect-name)]
          {:from at :to (inc close)})))))

(defn- fx-scope
  "→ [start end] to search, or nil when `at` is given but does not match."
  [doc effect-name at]
  (if (nil? at)
    [0 (count doc)]
    (when-let [{:keys [from to]} (fx-form-range doc at effect-name)]
      [from to])))

(defn fx-param-edit
  "Edit to set a numeric fx parameter: {:from :to :insert} or nil.
   Order: named `:param NUMBER` → replace; else when `primary?` the positional
   number → replace; else insert \" :param v\" before the form's `)`.
   With `at` the search is limited to the `(fx …)` form starting there (nil when
   stale); without it, the whole doc (first matching form)."
  [doc effect-name param-name fmtd primary? at]
  (when-let [[s e] (fx-scope doc effect-name at)]
    (if-let [{:keys [from to] :as r} (find-fx-named-param-num doc s e effect-name param-name)]
      (merge {:from from :to to :insert fmtd} (select-keys r [:slider]))
      (if-let [{:keys [from to] :as r} (when primary? (find-fx-pos-param-num doc s e effect-name))]
        (merge {:from from :to to :insert fmtd} (select-keys r [:slider]))
        (when-let [close (find-fx-form-close doc s e effect-name)]
          {:from close :to close :insert (str " :" param-name " " fmtd)})))))

(defn fx-keyword-param-edit
  "Like fx-param-edit for `:param :keyword` values (no positional case).
   kw-text includes the leading colon."
  [doc effect-name param-name kw-text at]
  (when-let [[s e] (fx-scope doc effect-name at)]
    (if-let [{:keys [from to]} (find-fx-named-param-keyword doc s e effect-name param-name)]
      {:from from :to to :insert kw-text}
      (when-let [close (find-fx-form-close doc s e effect-name)]
        {:from close :to close :insert (str " :" param-name " " kw-text)}))))

;; ─── Inline sliders (Phase WS3) ───────────────────────────────────────────────

(defn- str-tokens
  "Like tokenize over the whole doc, but keeps string literals (as :str), so a
   string argument counts as one argument."
  [doc]
  (tokenize* doc 0 (count doc) #{:ws :comment}))

(defn- list-args
  "Argument spans of the list opened at toks[j] (head at j+1):
   [{:from :to :atom? bool :k idx}] and the closing token index (nil when
   unclosed)."
  [toks j]
  (let [n (count toks)]
    (loop [k (+ j 2) acc []]
      (if (>= k n)
        [acc nil]
        (let [t (nth toks k)]
          (case (:t t)
            :rp [acc k]
            :lp (if-let [c (match-close toks k)]
                  (recur (inc c) (conj acc {:from (:from t) :to (:to (nth toks c)) :atom? false :k k}))
                  [(conj acc {:from (:from t) :to (:to (peek toks)) :atom? false :k k}) nil])
            (recur (inc k) (conj acc {:from (:from t) :to (:to t) :atom? (= :atom (:t t)) :k k}))))))))

(defn- slider-head? [doc toks j]
  (let [t0 (nth toks j)
        t1 (when (< (inc j) (count toks)) (nth toks (inc j)))]
    (and (= :lp (:t t0))
         (= "(" (.charAt doc (:from t0)))
         t1
         (= "slider" (atom-text doc t1)))))

(defn- span-text [doc {:keys [from to]}]
  {:from from :to to :text (.substring doc from to)})

(defn find-slider-forms
  "Every well-formed `(slider A B C [D])` outside comments/strings: A, B, C
   numeric literal atoms, D (optional) one atom, exactly 3 or 4 args.
   → [{:from :to :value {:from :to :text} :min {…} :max {…} :mode nil|{…}}]
   in source order (:to = index after the `)`)."
  [doc]
  (let [toks (str-tokens doc)
        n    (count toks)]
    (into []
          (keep (fn [j]
                  (when (slider-head? doc toks j)
                    (let [[args close] (list-args toks j)
                          lit? (fn [a] (and (:atom? a)
                                            (slm/parse-num-literal (.substring doc (:from a) (:to a)))))]
                      (when (and close
                                 (<= 3 (count args) 4)
                                 (every? lit? (take 3 args))
                                 (or (= 3 (count args)) (:atom? (nth args 3))))
                        (let [[a b c d] args]
                          {:from  (:from (nth toks j))
                           :to    (:to (nth toks close))
                           :value (span-text doc a)
                           :min   (span-text doc b)
                           :max   (span-text doc c)
                           :mode  (when d (span-text doc d))}))))))
          (range n))))

(defn slider-form-at
  "The find-slider-forms entry whose `(` is at `at`, else nil."
  [doc at]
  (some #(when (= at (:from %)) %) (find-slider-forms doc)))

(defn- touches? [{:keys [from to]} [a b]]
  (and (<= (min a b) to) (>= (max a b) from)))

(defn visible-of-forms
  "visible-sliders over already scanned `forms` (see visible-sliders)."
  [doc forms sels]
  (into []
        (keep (fn [f]
                (when-let [c (slm/inline-cfg f)]
                  (when (and (neg? (.indexOf (.substring doc (:from f) (:to f)) "\n"))
                             (not-any? #(touches? f %) sels))
                    {:form f :cfg c}))))
        forms))

(defn visible-sliders
  "Slider forms drawn as widgets: valid config (slm/inline-cfg), single line,
   and no selection [[from to] …] touching them (inclusive at both ends).
   → [{:form f :cfg c}]."
  [doc sels]
  (visible-of-forms doc (find-slider-forms doc) sels))

(defn- ^number enclosing-open
  "Index of the :lp token of the list enclosing toks[k], or nil (top level)."
  [toks k]
  (loop [i (dec k) depth 0]
    (when (>= i 0)
      (case (:t (nth toks i))
        :rp (recur (dec i) (inc depth))
        :lp (if (zero? depth) i (recur (dec i) (dec depth)))
        (recur (dec i) depth)))))

(defn- param-name-for
  "Decision 13: the param a number at toks[k] belongs to, or nil."
  [doc toks k]
  (let [open (enclosing-open toks k)]
    (when open
      (let [[args _] (list-args toks open)
            head     (when (< (inc open) (count toks)) (atom-text doc (nth toks (inc open))))
            idx      (first (keep-indexed #(when (= k (:k %2)) %1) args))
            prev     (when (and idx (pos? idx)) (nth args (dec idx)))
            prev-txt (when (and prev (:atom? prev)) (.substring doc (:from prev) (:to prev)))
            kw?      (and prev-txt (.startsWith prev-txt ":") (> (count prev-txt) 1))]
        (or (when (and (= "fx" head) (= 1 idx) kw?)
              (get slm/FX-PRIMARY-PARAM (subs prev-txt 1)))
            (when kw? (subs prev-txt 1))
            head)))))

(defn slider-toggle-edit
  "⌘K S at `pos`: unwrap the innermost (slider …) touching pos (replace it by
   its first argument's text), else wrap the numeric literal touching pos in
   (slider <literal> <min> <max>) with the range from the param name.
   → {:from :to :insert :cursor} or nil."
  [doc pos]
  (let [toks  (str-tokens doc)
        n     (count toks)
        touch (fn [from to] (and (<= from pos) (<= pos to)))
        inner (->> (range n)
                   (keep (fn [j]
                           (when (slider-head? doc toks j)
                             (let [[args close] (list-args toks j)
                                   from (:from (nth toks j))
                                   to   (if close (:to (nth toks close)) (count doc))]
                               (when (touch from to)
                                 {:from from :to to :arg (first args)})))))
                   last)]
    (if inner
      (when-let [a (:arg inner)]
        (let [txt (.substring doc (:from a) (:to a))]
          {:from (:from inner) :to (:to inner) :insert txt
           :cursor (+ (:from inner) (count txt))}))
      (when-let [k (first (keep-indexed
                           (fn [k t]
                             (when (and (= :atom (:t t))
                                        (touch (:from t) (:to t))
                                        (slm/parse-num-literal (atom-text doc t)))
                               k))
                           toks))]
        (let [t       (nth toks k)
              txt     (atom-text doc t)
              [lo hi] (slm/wrap-range (param-name-for doc toks k) (slm/parse-num-literal txt))]
          {:from   (:from t)
           :to     (:to t)
           :insert (str "(slider " txt " " lo " " hi ")")
           :cursor (+ (:from t) (count "(slider ") (count txt))})))))
