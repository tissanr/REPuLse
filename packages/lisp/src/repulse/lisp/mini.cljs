(ns repulse.lisp.mini
  (:require [repulse.core :as core]))

;;; ── Tokeniser ──────────────────────────────────────────────────────

;; Token types:
;;   :open-bracket   [
;;   :close-bracket  ]
;;   :open-angle     <
;;   :close-angle    >
;;   :star           *
;;   :question       ?   (with :str-from — the ?amount adjacency check)
;;   :pipe           |
;;   :colon          :
;;   :at             @
;;   :atom           (with :text, :str-from, :str-to — positions within the string)

(defn- whitespace? [ch]
  (contains? #{\space \tab \newline \return} ch))

(defn- special? [ch]
  (contains? #{\[ \] \< \> \* \? \: \@ \, \! \% \( \) \{ \} \|} ch))

(def ^:private single-char-tokens
  {\[ :open-bracket  \] :close-bracket \< :open-angle \> :close-angle
   \* :star          \? :question      \| :pipe       \: :colon
   \@ :at            \, :comma         \! :bang       \% :percent
   \( :open-paren    \) :close-paren   \{ :open-brace \} :close-brace})

(defn- tokenise
  "Scan a mini-notation string into a flat sequence of tokens. Every token
   carries :text, :str-from and :str-to — character offsets within s."
  [s]
  (loop [i 0, tokens []]
    (if (>= i (count s))
      tokens
      (let [ch (nth s i)]
        (cond
          (whitespace? ch)
          (recur (inc i) tokens)

          (contains? single-char-tokens ch)
          (recur (inc i) (conj tokens {:type (single-char-tokens ch) :text (str ch)
                                       :str-from i :str-to (inc i)}))

          :else
          ;; Accumulate word characters until whitespace or special
          (let [end (loop [j i]
                      (if (or (>= j (count s))
                              (whitespace? (nth s j))
                              (special? (nth s j)))
                        j
                        (recur (inc j))))
                word (subs s i end)]
            (recur end (conj tokens {:type (if (= word ".") :dot :atom) :text word
                                     :str-from i :str-to end}))))))))

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

(declare peek-token)

(def ^:private rand-scope-msg "| is only allowed at top level and inside [ ]")

(def ^:private ^:dynamic *src-len*
  "Length of the string being parsed — the position of an end-of-input error."
  0)

(defn- parse-error
  "Mini-notation parse error at a token (its range) or at a position (end of
   input: a zero-width range at the string's end). The message gets
   \" in mini-notation at position <n>\" appended; parse adds :from/:to."
  [msg tok-or-pos]
  (let [from (if (map? tok-or-pos) (:str-from tok-or-pos) tok-or-pos)
        to   (if (map? tok-or-pos) (:str-to tok-or-pos) tok-or-pos)]
    (ex-info (str msg " in mini-notation at position " from)
             {:type :mini-parse-error :str-from from :str-to to})))

(defn- error-at
  "The token at pos, or the end-of-input position."
  [tokens pos]
  (or (peek-token tokens pos) *src-len*))

(defn- unexpected [tok]
  (parse-error (str "Unexpected " (pr-str (:text tok))) tok))

(defn- peek-token [tokens pos]
  (when (< pos (count tokens))
    (nth tokens pos)))

(defn- tok-type [tokens pos] (:type (peek-token tokens pos)))

(defn- parse-number-text
  "Parse a whole token as a decimal number (-?\\d+(\\.\\d+)? or -?\\.\\d+).
   Returns nil for anything else: \"808bd\", \"1e3\" and \"1x\" are not numbers."
  [s]
  (when (or (re-matches #"-?\d+(\.\d+)?" s) (re-matches #"-?\.\d+" s))
    (js/parseFloat s)))

(defn- parse-rational-text
  "\"3/2\" → exact [3 2]; nil if s is not a rational literal."
  [s]
  (when (re-matches #"-?\d+/\d+" s)
    (let [[n d] (.split s "/")]
      (core/->rat [(js/parseInt n 10) (js/parseInt d 10)]))))

(defn- suffix-number
  "The number after a * or @ suffix: a decimal or an exact rational.
   Throws a parse error at tok when it is anything else."
  [tok sym]
  (or (parse-rational-text (:text tok))
      (parse-number-text (:text tok))
      (throw (parse-error (str "Expected a number after " sym) tok))))

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
    (parse-rational-text text)      (parse-rational-text text)
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
          (recur {:type :repeat :child node :times (suffix-number nxt "*")
                  :weight (:weight node 1)}
                 (+ p 2))
          (recur {:type :repeat :child node :times 2 :weight (:weight node 1)} (inc p)))

        :question
        (let [amt (when (and nxt (= :atom (:type nxt))
                             (= (:str-from nxt) (inc (:str-from tok)))
                             (re-matches #"\d*\.?\d+" (:text nxt)))
                    (parse-number-text (:text nxt)))]
          (recur {:type :degrade :child node :amount amt :weight (:weight node 1)}
                 (if amt (+ p 2) (inc p))))

        :colon
        (if (and (= (:type node) :atom) (= (:type nxt) :atom))
          (recur (assoc node :sample-index (int (or (parse-number-text (:text nxt)) 0)))
                 (+ p 2))
          [node p])

        :at
        (if (= (:type nxt) :atom)
          (recur (assoc node :weight (suffix-number nxt "@")) (+ p 2))
          [node p])

        :open-paren
        (let [[a p1] (parse-sequence tokens (inc p))
              _      (when-not (= :comma (tok-type tokens p1))
                       (throw (parse-error "Expected ," (error-at tokens p1))))
              [b p2] (parse-sequence tokens (inc p1))
              [c p3] (if (= :comma (tok-type tokens p2))
                       (parse-sequence tokens (inc p2))
                       [nil p2])]
          (when-not (= :close-paren (tok-type tokens p3))
            (throw (parse-error "Expected )" (error-at tokens p3))))
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
        (when-not (= :close-bracket (tok-type tokens p)) (throw (parse-error "Expected ]" (error-at tokens p))))
        [node (inc p)])

      :open-angle
      (let [[node p] (parse-layers tokens (inc pos))]
        (when-not (= :close-angle (tok-type tokens p)) (throw (parse-error "Expected >" (error-at tokens p))))
        (when (= :rand (:type node)) (throw (parse-error rand-scope-msg tok)))
        (let [alts (mapv (fn [l] {:type :alt :children (:children l)}) (layers->vec node))]
          [(if (= 1 (count alts)) (first alts) {:type :stack :layers alts}) (inc p)]))

      :open-brace
      (let [[node p] (parse-layers tokens (inc pos))]
        (when-not (= :close-brace (tok-type tokens p)) (throw (parse-error "Expected }" (error-at tokens p))))
        (when (= :rand (:type node)) (throw (parse-error rand-scope-msg tok)))
        (let [p (inc p)
              layers (layers->vec node)]
          (if (= :percent (tok-type tokens p))
            (let [nt (peek-token tokens (inc p))
                  n  (when (= :atom (:type nt)) (parse-number-text (:text nt)))]
              (when-not n (throw (parse-error "Expected a number after %" (error-at tokens (inc p)))))
              [{:type :polymeter :layers layers :steps n} (+ p 2)])
            [{:type :polymeter :layers layers :steps nil} p])))

      :atom
      (parse-atom tokens pos)

      (throw (if tok (unexpected tok) (parse-error "Unexpected end" *src-len*))))))

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
            (contains? #{:close-bracket :close-angle :close-brace :close-paren :comma :pipe} t))
        (let [groups (if (seq cur) (conj groups cur) groups)]
          [(if (<= (count groups) 1)
             {:type :seq :children (or (first groups) [])}
             {:type :seq :feet true
              :children (mapv (fn [g] {:type :seq :children g :weight 1}) groups)})
           p])

        (= t :dot)
        (recur (if (seq cur) (conj groups cur) groups) [] (inc p))

        (= t :bang)
        (do
          (when (empty? cur) (throw (parse-error "Nothing to repeat before !" tok)))
          (let [n (adjacent-int tokens p)
                _ (when (and n (> n core/max-steps))
                    (throw (parse-error (str "! count = " n " exceeds the limit of " core/max-steps)
                                        tok)))
                copies (if n (max 0 (dec n)) 1)]
            (recur groups (into cur (repeat copies (peek cur))) (if n (+ p 2) (inc p)))))

        :else
        (let [[el p1]   (parse-element tokens p)
              [node p2] (parse-suffixes tokens el p1)]
          (recur groups (conj cur node) p2))))))

(defn- parse-layers
  "sequence (\",\" sequence)* or sequence (\"|\" sequence)* — a single layer is
   returned as is. \"|\" gives a :rand node (one alternative per cycle)."
  [tokens pos]
  (loop [layers [] seps #{} p pos]
    (let [[node p1] (parse-sequence tokens p)
          layers    (conj layers node)
          sep       (tok-type tokens p1)]
      (if (contains? #{:comma :pipe} sep)
        (do
          (when (and (seq seps) (not (contains? seps sep)))
            (throw (parse-error "Cannot mix , and | in one group" (peek-token tokens p1))))
          (recur layers (conj seps sep) (inc p1)))
        (do
          [(cond
             (= 1 (count layers)) (first layers)
             (contains? seps :pipe) {:type :rand :choices layers}
             :else {:type :stack :layers layers})
           p1])))))

(defn- number-seeds
  "Post-order walk (children first, in order: :child, :args, :children, :layers,
   :choices, then the node itself). :degrade, :rand and :feet nodes take the next
   seed 0, 1, 2 … (Strudel krill's seed++)."
  [ast]
  (let [counter (atom -1)
        walk (fn walk [n]
               (let [n (cond-> n
                         (:child n)    (update :child walk)
                         (:args n)     (update :args #(mapv walk %))
                         (:children n) (update :children #(mapv walk %))
                         (:layers n)   (update :layers #(mapv walk %))
                         (:choices n)  (update :choices #(mapv walk %)))]
                 (if (or (contains? #{:degrade :rand} (:type n)) (:feet n))
                   (assoc n :seed (swap! counter inc))
                   n)))]
    (walk ast)))

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

(defn- seed-rand
  "rand shifted by seed * 3/10000 of a cycle (Strudel: rand.early(seed * 0.0003))."
  [seed]
  (core/early (core/rat (* 3 seed) 10000) core/rand))

(defn- step-count
  "Steps in a polymeter layer: the sum of its children's weights."
  [node]
  (if (= :seq (:type node))
    (reduce core/rat+ [0 1] (map #(core/->rat (:weight % 1)) (:children node)))
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
        (core/cat* (mapv #(compile-ast % base-offset) children))))

    :repeat
    (let [child-pat (compile-ast (:child node) base-offset)
          times     (:times node)]
      (core/fast times child-pat))

    :degrade
    (core/degrade-by-with (seed-rand (:seed node))
                          (or (:amount node) 0.5)
                          (compile-ast (:child node) base-offset))

    :rand
    (core/choose-in-with (core/segment 1 (seed-rand (:seed node)))
                         (mapv #(compile-ast % base-offset) (:choices node)))

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
   (let [tokens (tokenise s)
         len    (count s)
         ast    (try
                  (binding [*src-len* len]
                    (let [[ast p] (parse-layers tokens 0)]
                      ;; A stray ] > } ) after the top level is an error, not the end.
                      (when (< p (count tokens))
                        (throw (unexpected (nth tokens p))))
                      ast))
                  (catch :default e
                    (let [{:keys [type str-from str-to]} (ex-data e)]
                      (throw
                       (if (and base-offset (= type :mini-parse-error) str-from)
                         ;; Underline the token; at end of input, the last character.
                         (let [[f t] (if (= str-from str-to len)
                                       [(max 0 (dec len)) len]
                                       [str-from str-to])]
                           (ex-info (ex-message e)
                                    (assoc (ex-data e) :from (+ base-offset f)
                                           :to (+ base-offset t))))
                         e)))))]
     (compile-ast (number-seeds ast) base-offset))))
