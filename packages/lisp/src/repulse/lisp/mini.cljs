(ns repulse.lisp.mini
  (:require [repulse.core :as core]))

;;; ── Helpers ─────────────────────────────────────────────────────────

(defn- float->rat
  "Convert a floating-point number to a rational [n d].
   Uses denominator 100000 — sufficient precision for music timing."
  [f]
  (let [n (int (Math/round (* f 100000)))]
    (core/rat n 100000)))

;;; ── Tokeniser ──────────────────────────────────────────────────────

;; Token types:
;;   :open-bracket   [
;;   :close-bracket  ]
;;   :open-angle     <
;;   :close-angle    >
;;   :star           *
;;   :question       ?
;;   :colon          :
;;   :at             @
;;   :atom           (with :text field — the raw word)

(defn- whitespace? [ch]
  (contains? #{\space \tab \newline \return} ch))

(defn- special? [ch]
  (contains? #{\[ \] \< \> \* \? \: \@} ch))

(defn- tokenise
  "Scan a mini-notation string into a flat sequence of tokens."
  [s]
  (loop [i 0, tokens []]
    (if (>= i (count s))
      tokens
      (let [ch (nth s i)]
        (cond
          (whitespace? ch)
          (recur (inc i) tokens)

          (= ch \[) (recur (inc i) (conj tokens {:type :open-bracket}))
          (= ch \]) (recur (inc i) (conj tokens {:type :close-bracket}))
          (= ch \<) (recur (inc i) (conj tokens {:type :open-angle}))
          (= ch \>) (recur (inc i) (conj tokens {:type :close-angle}))
          (= ch \*) (recur (inc i) (conj tokens {:type :star}))
          (= ch \?) (recur (inc i) (conj tokens {:type :question}))
          (= ch \:) (recur (inc i) (conj tokens {:type :colon}))
          (= ch \@) (recur (inc i) (conj tokens {:type :at}))

          :else
          ;; Accumulate word characters until whitespace or special
          (let [end (loop [j i]
                      (if (or (>= j (count s))
                              (whitespace? (nth s j))
                              (special? (nth s j)))
                        j
                        (recur (inc j))))
                word (subs s i end)]
            (recur end (conj tokens {:type :atom :text word}))))))))

;;; ── Parser ─────────────────────────────────────────────────────────

;; Recursive-descent parser over the token stream.
;; Returns an AST node and the remaining token index.
;;
;; Grammar (informal):
;;   sequence = element+
;;   element  = group | alt-group | atom-expr
;;   group    = "[" sequence "]"
;;   alt      = "<" sequence ">"  (children are alternatives, not subsequence)
;;   atom-expr = atom suffix*
;;   suffix   = "*" number | "?" | ":" number | "@" number
;;
;; Precedence: suffixes bind tighter than sequencing.
;; Sequence is implicit — space-separated elements form a seq.

(declare parse-sequence)

(defn- peek-token [tokens pos]
  (when (< pos (count tokens))
    (nth tokens pos)))

(defn- parse-number-text
  "Parse a string as a number. Returns nil if not a number."
  [s]
  (let [n (js/parseFloat s)]
    (when-not (js/isNaN n) n)))

(defn- note-name?
  "True if s looks like a note name: a-g, optional accidental (s/b), then digit(s)."
  [s]
  (boolean (re-matches #"[a-g][sb]?\d+" s)))

(defn- atom-value
  "Convert a raw text token to its REPuLse value: keyword, number, or note keyword."
  [text]
  (cond
    (or (= text "~") (= text "_")) :_
    (parse-number-text text)        (parse-number-text text)
    (note-name? text)               (keyword text)
    :else                           (keyword text)))

(defn- parse-atom
  "Parse a single atom token (not brackets/angles), then consume any suffixes.
   Returns [ast-node next-pos]."
  [tokens pos]
  (let [tok      (nth tokens pos)
        base-val (atom-value (:text tok))
        pos      (inc pos)]
    ;; Consume suffixes: * ? : @
    (loop [node {:type :atom :value base-val :weight 1}
           p    pos]
      (let [tok (peek-token tokens p)]
        (cond
          ;; *N — repetition
          (and tok (= (:type tok) :star))
          (let [next-tok (peek-token tokens (inc p))]
            (if (and next-tok (= (:type next-tok) :atom))
              (let [n (or (parse-number-text (:text next-tok)) 1)]
                (recur {:type :repeat :child node :times (int n) :weight (:weight node)}
                       (+ p 2)))
              ;; bare * with no number — treat as *2
              (recur {:type :repeat :child node :times 2 :weight (:weight node)}
                     (inc p))))

          ;; ? — probability (50% chance of rest)
          (and tok (= (:type tok) :question))
          (recur {:type :degrade :child node :weight (:weight node)}
                 (inc p))

          ;; :N — sample index
          (and tok (= (:type tok) :colon))
          (let [next-tok (peek-token tokens (inc p))]
            (if (and next-tok (= (:type next-tok) :atom))
              (let [n (or (parse-number-text (:text next-tok)) 0)]
                (recur (assoc node :sample-index (int n))
                       (+ p 2)))
              [node p]))

          ;; @N — weight / elongation
          (and tok (= (:type tok) :at))
          (let [next-tok (peek-token tokens (inc p))]
            (if (and next-tok (= (:type next-tok) :atom))
              (let [w (or (parse-number-text (:text next-tok)) 1)]
                (recur (assoc node :weight (int w))
                       (+ p 2)))
              [node p]))

          ;; No more suffixes
          :else
          [node p])))))

(defn- parse-element
  "Parse one element: a bracketed group, an angle-bracket alternation, or an atom."
  [tokens pos]
  (let [tok (peek-token tokens pos)]
    (cond
      ;; [ ... ] — subdivision group
      (= (:type tok) :open-bracket)
      (let [[seq-node next-pos] (parse-sequence tokens (inc pos))
            close               (peek-token tokens next-pos)]
        (when-not (and close (= (:type close) :close-bracket))
          (throw (ex-info "Expected ]" {:type :mini-parse-error})))
        ;; After the ], check for suffixes on the group
        (loop [node seq-node p (inc next-pos)]
          (let [tok (peek-token tokens p)]
            (cond
              (and tok (= (:type tok) :star))
              (let [next-tok (peek-token tokens (inc p))]
                (if (and next-tok (= (:type next-tok) :atom))
                  (let [n (or (parse-number-text (:text next-tok)) 2)]
                    (recur {:type :repeat :child node :times (int n) :weight 1} (+ p 2)))
                  (recur {:type :repeat :child node :times 2 :weight 1} (inc p))))

              (and tok (= (:type tok) :question))
              (recur {:type :degrade :child node :weight 1} (inc p))

              (and tok (= (:type tok) :at))
              (let [next-tok (peek-token tokens (inc p))]
                (if (and next-tok (= (:type next-tok) :atom))
                  (let [w (or (parse-number-text (:text next-tok)) 1)]
                    (recur (assoc node :weight (int w)) (+ p 2)))
                  [node p]))

              :else
              [node p]))))

      ;; < ... > — alternation
      (= (:type tok) :open-angle)
      (let [[seq-node next-pos] (parse-sequence tokens (inc pos))
            close               (peek-token tokens next-pos)]
        (when-not (and close (= (:type close) :close-angle))
          (throw (ex-info "Expected >" {:type :mini-parse-error})))
        [{:type :alt :children (:children seq-node)} (inc next-pos)])

      ;; atom
      (= (:type tok) :atom)
      (parse-atom tokens pos)

      :else
      (throw (ex-info (str "Unexpected token: " (pr-str tok))
                      {:type :mini-parse-error})))))

(defn- parse-sequence
  "Parse a sequence of elements until we hit ], >, or end of tokens.
   Returns [{:type :seq :children [...]} next-pos]."
  [tokens pos]
  (loop [children [] p pos]
    (let [tok (peek-token tokens p)]
      (if (or (nil? tok)
              (= (:type tok) :close-bracket)
              (= (:type tok) :close-angle))
        [{:type :seq :children children} p]
        (let [[node next-pos] (parse-element tokens p)]
          (recur (conj children node) next-pos))))))

;;; ── Compiler: AST → Pattern ─────────────────────────────────────────

;; NOTE: Every compiled node returns a Pattern (a map with :query fn).
;; We cannot use core/seq* for sequences of patterns because core/seq*
;; stores its arguments as event *values*, not as child patterns to be
;; queried. We need seq-of-pats which sequences patterns in time slots.

(declare compile-node)

(defn alt*
  "Alternation: on cycle N, play the (mod N count)-th pattern."
  [pats]
  (let [n (count pats)]
    (core/pattern
      (fn [sp]
        (let [cycle (int (Math/floor (core/rat->float (:start sp))))
              idx   (mod cycle n)]
          (core/query (nth pats idx) sp))))))

(defn- scale-events
  "Query pat within [0,1) and scale/shift event times into [slot-sf, slot-ef).
   Clips the :part span to [sp-sf, sp-ef)."
  [pat slot-sf slot-ef sp-sf sp-ef]
  (let [raw-evs (core/query pat {:start [0 1] :end [1 1]})
        dur     (- slot-ef slot-sf)]
    (keep
      (fn [ev]
        (let [ws-f  (core/rat->float (:start (:whole ev)))
              we-f  (core/rat->float (:end   (:whole ev)))
              ps-f  (core/rat->float (:start (:part  ev)))
              pe-f  (core/rat->float (:end   (:part  ev)))
              nws-f (+ slot-sf (* ws-f dur))
              nwe-f (+ slot-sf (* we-f dur))
              nps-f (+ slot-sf (* ps-f dur))
              npe-f (+ slot-sf (* pe-f dur))
              cls-f (max nps-f sp-sf)
              cle-f (min npe-f sp-ef)]
          (when (< cls-f cle-f)
            (assoc ev
              :whole {:start (float->rat nws-f) :end (float->rat nwe-f)}
              :part  {:start (float->rat cls-f) :end (float->rat cle-f)}))))
      raw-evs)))

(defn- seq-of-pats
  "Sequence n patterns: pattern i plays in slot [i/n, (i+1)/n) of each cycle.
   Queries each child as if it owns [0,1), then scales event times to its slot."
  [pats]
  (let [n (count pats)]
    (core/pattern
      (fn [sp]
        (let [sp-sf (core/rat->float (:start sp))
              sp-ef (core/rat->float (:end sp))
              c0    (int (Math/floor sp-sf))
              c1    (int (Math/ceil  sp-ef))]
          (mapcat
            (fn [c]
              (mapcat
                (fn [[i pat]]
                  (let [slot-sf (/ (+ (* c n) i)       n)
                        slot-ef (/ (+ (* c n) (inc i)) n)
                        ol-sf   (max slot-sf sp-sf)
                        ol-ef   (min slot-ef sp-ef)]
                    (when (< ol-sf ol-ef)
                      (scale-events pat slot-sf slot-ef sp-sf sp-ef))))
                (map-indexed vector pats)))
            (range c0 c1)))))))

(defn- weighted-seq*
  "Proportional sequence: child i plays for (weight_i / total) of the cycle."
  [pairs total-weight]
  (core/pattern
    (fn [sp]
      (let [sp-sf       (core/rat->float (:start sp))
            sp-ef       (core/rat->float (:end sp))
            cycle-start (Math/floor sp-sf)]
        (loop [remaining pairs
               offset-f  0.0
               result    []]
          (if (empty? remaining)
            result
            (let [{:keys [pat weight]} (first remaining)
                  frac    (/ weight total-weight)
                  slot-sf (+ cycle-start offset-f)
                  slot-ef (+ cycle-start offset-f frac)]
              (recur (rest remaining)
                     (+ offset-f frac)
                     (into result
                           (if (and (< slot-sf sp-ef) (> slot-ef sp-sf))
                             (scale-events pat slot-sf slot-ef sp-sf sp-ef)
                             []))))))))))

(defn- degrade
  "Randomly gate events — each event has a ~50% chance of being dropped."
  [pat]
  (core/pattern
    (fn [sp]
      (filter (fn [_] (> (Math/random) 0.5)) (core/query pat sp)))))

(defn- compile-node
  "Compile an AST node to a Pattern."
  [node]
  (case (:type node)
    :atom
    (let [v (:value node)]
      (if (:sample-index node)
        (core/pure {:bank v :n (:sample-index node)})
        (core/pure v)))

    :rest
    (core/pure :_)

    :seq
    (let [children (:children node)]
      (cond
        (zero? (count children))
        (core/pattern (fn [_] []))

        (= 1 (count children))
        (compile-node (first children))

        :else
        (let [total-weight (reduce + (map #(or (:weight %) 1) children))
              all-unit?    (every? #(= 1 (or (:weight %) 1)) children)]
          (if all-unit?
            (seq-of-pats (mapv compile-node children))
            (let [pairs (mapv (fn [child]
                                {:pat    (compile-node child)
                                 :weight (or (:weight child) 1)})
                              children)]
              (weighted-seq* pairs total-weight))))))

    :alt
    (let [children (:children node)]
      (if (= 1 (count children))
        (compile-node (first children))
        (alt* (mapv compile-node children))))

    :repeat
    (let [child-pat (compile-node (:child node))
          times     (:times node)]
      (core/fast times child-pat))

    :degrade
    (degrade (compile-node (:child node)))

    ;; Fallback
    (core/pure :_)))

;;; ── Public API ──────────────────────────────────────────────────────

(defn parse
  "Parse a mini-notation string and return a Pattern.
   (parse \"bd sd [hh hh] bd\")  → pattern equivalent to (seq :bd :sd (seq :hh :hh) :bd)"
  [s]
  (let [tokens  (tokenise s)
        [ast _] (parse-sequence tokens 0)]
    (compile-node ast)))
