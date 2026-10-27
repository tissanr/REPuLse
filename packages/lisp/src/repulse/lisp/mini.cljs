(ns repulse.lisp.mini
  (:require [repulse.core :as core]))

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
;;   :atom           (with :text, :str-from, :str-to — positions within the string)

(defn- whitespace? [ch]
  (contains? #{\space \tab \newline \return} ch))

(defn- special? [ch]
  (contains? #{\[ \] \< \> \* \? \: \@ \, \! \% \( \) \{ \}} ch))

(defn- tokenise
  "Scan a mini-notation string into a flat sequence of tokens.
   :atom tokens carry :str-from/:str-to — character offsets within s."
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
          (= ch \,) (recur (inc i) (conj tokens {:type :comma}))
          (= ch \!) (recur (inc i) (conj tokens {:type :bang :str-from i}))
          (= ch \%) (recur (inc i) (conj tokens {:type :percent}))
          (= ch \() (recur (inc i) (conj tokens {:type :open-paren}))
          (= ch \)) (recur (inc i) (conj tokens {:type :close-paren}))
          (= ch \{) (recur (inc i) (conj tokens {:type :open-brace}))
          (= ch \}) (recur (inc i) (conj tokens {:type :close-brace}))

          :else
          ;; Accumulate word characters until whitespace or special
          (let [end (loop [j i]
                      (if (or (>= j (count s))
                              (whitespace? (nth s j))
                              (special? (nth s j)))
                        j
                        (recur (inc j))))
                word (subs s i end)]
            (recur end (conj tokens (if (= word ".")
                                      {:type :dot}
                                      {:type :atom :text word
                                       :str-from i :str-to end})))))))))

;;; ── Parser ─────────────────────────────────────────────────────────

;; Recursive-descent parser over the token stream. Every parse fn returns
;; [ast-node next-pos].
;;
;; Grammar (informal):
;;   layers   = sequence ("," sequence)*       — stack of layers
;;   sequence = step+, split into groups by "." tokens
;;   step     = element suffix* | "!" (repeat the previous step)
;;   element  = "[" layers "]" | "<" layers ">" | "{" layers "}" ("%" n)? | atom
;;   suffix   = "*" n | "?" | ":" n (atoms) | "@" n | "(" seq "," seq ["," seq] ")"
;;
;; Precedence: "," (lowest) splits layers, "." splits groups, spaces split steps.

(declare parse-layers parse-sequence)

(defn- parse-error [msg]
  (ex-info msg {:type :mini-parse-error}))

(defn- peek-token [tokens pos]
  (when (< pos (count tokens))
    (nth tokens pos)))

(defn- tok-type [tokens pos] (:type (peek-token tokens pos)))

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
    ;; Rational literal "1/8" → exact [1 8] (used by patterned time args)
    (re-matches #"-?\d+/\d+" text)
    (let [[n d] (.split text "/")]
      (core/->rat [(js/parseInt n 10) (js/parseInt d 10)]))
    (parse-number-text text)        (parse-number-text text)
    (note-name? text)               (keyword text)
    :else                           (keyword text)))

(defn- parse-atom [tokens pos]
  (let [tok (nth tokens pos)]
    [{:type :atom :value (atom-value (:text tok)) :weight 1
      :str-from (:str-from tok) :str-to (:str-to tok)}
     (inc pos)]))

(defn- parse-suffixes
  "Consume suffixes (* ? : @ and euclid arguments) after an element."
  [tokens node pos]
  (loop [node node p pos]
    (let [tok (peek-token tokens p)
          nxt (peek-token tokens (inc p))]
      (case (:type tok)
        :star
        (if (= (:type nxt) :atom)
          (recur {:type :repeat :child node :times (int (or (parse-number-text (:text nxt)) 1))
                  :weight (:weight node 1)}
                 (+ p 2))
          (recur {:type :repeat :child node :times 2 :weight (:weight node 1)} (inc p)))

        :question
        (recur {:type :degrade :child node :weight (:weight node 1)} (inc p))

        :colon
        (if (and (= (:type node) :atom) (= (:type nxt) :atom))
          (recur (assoc node :sample-index (int (or (parse-number-text (:text nxt)) 0)))
                 (+ p 2))
          [node p])

        :at
        (if (= (:type nxt) :atom)
          (recur (assoc node :weight (int (or (parse-number-text (:text nxt)) 1))) (+ p 2))
          [node p])

        :open-paren
        (let [[a p1] (parse-sequence tokens (inc p))
              _      (when-not (= :comma (tok-type tokens p1)) (throw (parse-error "Expected ,")))
              [b p2] (parse-sequence tokens (inc p1))
              [c p3] (if (= :comma (tok-type tokens p2))
                       (parse-sequence tokens (inc p2))
                       [nil p2])]
          (when-not (= :close-paren (tok-type tokens p3))
            (throw (parse-error "Expected )")))
          (recur {:type :euclid :child node :args (if c [a b c] [a b])
                  :weight (:weight node 1)}
                 (inc p3)))

        [node p]))))

(defn- layers->vec [node]
  (if (= :stack (:type node)) (:layers node) [node]))

(defn- parse-element [tokens pos]
  (let [tok (peek-token tokens pos)]
    (case (:type tok)
      :open-bracket
      (let [[node p] (parse-layers tokens (inc pos))]
        (when-not (= :close-bracket (tok-type tokens p)) (throw (parse-error "Expected ]")))
        [node (inc p)])

      :open-angle
      (let [[node p] (parse-layers tokens (inc pos))]
        (when-not (= :close-angle (tok-type tokens p)) (throw (parse-error "Expected >")))
        (let [alts (mapv (fn [l] {:type :alt :children (:children l)}) (layers->vec node))]
          [(if (= 1 (count alts)) (first alts) {:type :stack :layers alts}) (inc p)]))

      :open-brace
      (let [[node p] (parse-layers tokens (inc pos))]
        (when-not (= :close-brace (tok-type tokens p)) (throw (parse-error "Expected }")))
        (let [p (inc p)
              layers (layers->vec node)]
          (if (= :percent (tok-type tokens p))
            (let [nt (peek-token tokens (inc p))
                  n  (when (= :atom (:type nt)) (parse-number-text (:text nt)))]
              (when-not n (throw (parse-error "Expected a number after %")))
              [{:type :polymeter :layers layers :steps n} (+ p 2)])
            [{:type :polymeter :layers layers :steps nil} p])))

      :atom
      (parse-atom tokens pos)

      (throw (parse-error (str "Unexpected token: " (pr-str tok)))))))

(defn- adjacent-int
  "If the token after the bang at pos is an integer atom directly adjacent to it, return it."
  [tokens pos]
  (let [bang (nth tokens pos)
        nxt  (peek-token tokens (inc pos))]
    (when (and nxt (= :atom (:type nxt))
               (= (:str-from nxt) (inc (:str-from bang)))
               (re-matches #"\d+" (:text nxt)))
      (js/parseInt (:text nxt) 10))))

(defn- parse-sequence
  "Parse steps until a closing token, comma or end. Standalone dots split the
   steps into groups. Returns [{:type :seq :children [...]} next-pos]."
  [tokens pos]
  (loop [groups [] cur [] p pos]
    (let [tok (peek-token tokens p)
          t   (:type tok)]
      (cond
        (or (nil? tok)
            (contains? #{:close-bracket :close-angle :close-brace :close-paren :comma} t))
        (let [groups (if (seq cur) (conj groups cur) groups)]
          [(if (<= (count groups) 1)
             {:type :seq :children (or (first groups) [])}
             {:type :seq
              :children (mapv (fn [g] {:type :seq :children g :weight 1}) groups)})
           p])

        (= t :dot)
        (recur (if (seq cur) (conj groups cur) groups) [] (inc p))

        (= t :bang)
        (do
          (when (empty? cur) (throw (parse-error "Nothing to repeat before !")))
          (let [n (adjacent-int tokens p)
                copies (if n (max 0 (dec n)) 1)]
            (recur groups (into cur (repeat copies (peek cur))) (if n (+ p 2) (inc p)))))

        :else
        (let [[el p1]   (parse-element tokens p)
              [node p2] (parse-suffixes tokens el p1)]
          (recur groups (conj cur node) p2))))))

(defn- parse-layers
  "sequence (\",\" sequence)* — a single layer is returned as is."
  [tokens pos]
  (loop [layers [] p pos]
    (let [[node p1] (parse-sequence tokens p)
          layers    (conj layers node)]
      (if (= :comma (tok-type tokens p1))
        (recur layers (inc p1))
        [(if (= 1 (count layers)) (first layers) {:type :stack :layers layers}) p1]))))

;;; ── Compiler: AST → Pattern ─────────────────────────────────────────

;; NOTE: Every compiled node returns a Pattern (a map with :query fn).
;; Sequences compile to core/timecat, which squeezes child patterns into
;; exact rational slots and plays child cycle c in result cycle c.
;;
;; base-offset: absolute editor position of the first character inside the
;; mini-notation string (position after the opening "). When non-nil, each
;; :atom node computes its source range as {:from base-offset+str-from
;;                                          :to   base-offset+str-to}
;; so the exact token highlights during playback (not the whole string).

(defn alt*
  "Alternation: on cycle N, play the (mod N count)-th pattern."
  [pats]
  (let [n (count pats)]
    (core/pattern
      (fn [sp]
        (mapcat (fn [piece]
                  (let [cycle (int (Math/floor (core/rat->float (:start piece))))
                        idx   (mod cycle n)]
                    (core/query (nth pats idx) piece)))
                (core/split-cycles sp))))))

(defn- degrade
  "Randomly gate events — each event has a ~50% chance of being dropped."
  [pat]
  (core/pattern
    (fn [sp]
      (filter (fn [_] (> (Math/random) 0.5)) (core/query pat sp)))))

(defn- step-count
  "Steps in a polymeter layer: the sum of its children's weights."
  [node]
  (if (= :seq (:type node))
    (reduce + (map #(:weight % 1) (:children node)))
    1))

(defn- compile-ast
  "Compile an AST node to a Pattern.
   base-offset: absolute editor position after the opening quote of the
   mini-notation string. When provided, :atom events carry per-token
   :source ranges so individual tokens flash during playback."
  [node base-offset]
  (case (:type node)
    :atom
    (let [v      (:value node)
          source (when (and base-offset (:str-from node))
                   {:from (+ base-offset (:str-from node))
                    :to   (+ base-offset (:str-to node))})]
      (if (:sample-index node)
        (core/pure {:bank v :n (:sample-index node)} source)
        (core/pure v source)))

    :rest
    (core/pure :_)

    :seq
    (let [children (:children node)]
      (cond
        (zero? (count children))
        (core/pattern (fn [_] []))

        (= 1 (count children))
        (compile-ast (first children) base-offset)

        :else
        (core/timecat (mapv (fn [c] [(:weight c 1) (compile-ast c base-offset)])
                            children))))

    :stack
    (core/stack* (mapv #(compile-ast % base-offset) (:layers node)))

    :polymeter
    (core/polymeter-pats
     (:steps node)
     (mapv (fn [l] [(compile-ast l base-offset) (step-count l)]) (:layers node)))

    :euclid
    (let [arg (fn [n]
                (let [kids (:children n)
                      only (when (and (= :seq (:type n)) (= 1 (count kids))) (first kids))]
                  (if (and only (= :atom (:type only)) (number? (:value only))
                           (not (:sample-index only)))
                    (:value only)
                    (compile-ast n base-offset))))
          [k n r] (:args node)]
      (core/euclidean (arg k) (arg n) (compile-ast (:child node) base-offset)
                      (if r (arg r) 0)))

    :alt
    (let [children (:children node)]
      (if (= 1 (count children))
        (compile-ast (first children) base-offset)
        (alt* (mapv #(compile-ast % base-offset) children))))

    :repeat
    (let [child-pat (compile-ast (:child node) base-offset)
          times     (:times node)]
      (core/fast times child-pat))

    :degrade
    (degrade (compile-ast (:child node) base-offset))

    ;; Fallback
    (core/pure :_)))

;;; ── Public API ──────────────────────────────────────────────────────

(defn parse
  "Parse a mini-notation string and return a Pattern.
   (parse \"bd sd [hh hh] bd\")  → pattern equivalent to (seq :bd :sd (seq :hh :hh) :bd)

   base-offset (optional): absolute editor position of the first character
   inside the string (i.e. one past the opening quote). When provided,
   each token in the pattern carries a :source range pointing to its exact
   position in the editor so it highlights individually during playback."
  ([s] (parse s nil))
  ([s base-offset]
   (let [tokens  (tokenise s)
         [ast _] (parse-layers tokens 0)]
     (compile-ast ast base-offset))))
