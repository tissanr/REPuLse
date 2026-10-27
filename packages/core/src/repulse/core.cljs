(ns repulse.core
  (:refer-clojure :exclude [rand time chunk iter]))

;;; Rational arithmetic — rationals as [numerator denominator]

(defn gcd [a b]
  (loop [a (Math/abs a) b (Math/abs b)]
    (if (zero? b) a (recur b (mod a b)))))

(def ^:private max-safe-int 9007199254740991)

(defn rat
  ([n] [n 1])
  ([n d]
   ;; Dev-only overflow guard: once |n| or |d| leaves the safe-integer range,
   ;; JS numbers silently lose precision and timing drifts. Release builds
   ;; (goog.DEBUG = false) compile this check away.
   (when ^boolean goog/DEBUG
     (when (or (> (Math/abs n) max-safe-int) (> (Math/abs d) max-safe-int))
       (throw (ex-info (str "Rational overflow: " n "/" d)
                       {:type :rational-overflow :n n :d d}))))
   (let [g (gcd (Math/abs n) (Math/abs d))
         sign (if (neg? d) -1 1)]
     [(* sign (/ n g)) (* sign (/ d g))])))

(defn rat+ [[n1 d1] [n2 d2]] (rat (+ (* n1 d2) (* n2 d1)) (* d1 d2)))
(defn rat- [[n1 d1] [n2 d2]] (rat (- (* n1 d2) (* n2 d1)) (* d1 d2)))
(defn rat* [[n1 d1] [n2 d2]] (rat (* n1 n2) (* d1 d2)))
(defn rat-div [[n1 d1] [n2 d2]] (rat (* n1 d2) (* d1 n2)))
(defn rat< [[n1 d1] [n2 d2]] (< (* n1 d2) (* n2 d1)))
(defn rat<= [a b] (or (= a b) (rat< a b)))
(defn rat> [a b] (rat< b a))
(defn rat>= [a b] (rat<= b a))
(defn rat-min [a b] (if (rat< a b) a b))
(defn rat-max [a b] (if (rat> a b) a b))
(defn rat->float [[n d]] (/ n d))
(defn int->rat [n] [n 1])
(defn rat-neg [[n d]] [(- n) d])

(def ^:private max-denominator 1000000)

(defn- float->rat
  "Best rational approximation of float x with denominator ≤ 1 000 000,
   via the continued-fraction convergents of x."
  [x]
  (when-not (js/isFinite x)
    (throw (ex-info (str "Expected a finite number, got " x) {:type :not-a-number})))
  (if (== x (Math/floor x))
    [x 1]
    (let [sign (if (neg? x) -1 1)
          ax   (Math/abs x)]
      ;; h/k are successive convergents; start from h₋₂/k₋₂ = 0/1, h₋₁/k₋₁ = 1/0.
      (loop [h0 0 h1 1 k0 1 k1 0 y ax i 0]
        (let [a  (Math/floor y)
              h2 (+ (* a h1) h0)
              k2 (+ (* a k1) k0)]
          (if (> k2 max-denominator)
            (rat (* sign h1) k1)
            (let [frac (- y a)]
              (if (or (zero? frac)
                      (< (Math/abs (- ax (/ h2 k2))) 1e-12)
                      (> i 64))
                (rat (* sign h2) k2)
                (recur h1 h2 k1 k2 (/ 1 frac) (inc i))))))))))

(defn ->rat
  "Coerce user input to a normalised rational [n d].
   Integers → [n 1]; [n d] vectors are reduced; floats become the best
   rational approximation with denominator ≤ 1 000 000
   (0.25 → [1 4], 1.5 → [3 2], 0.3333333 → [1 3])."
  [x]
  (cond
    (vector? x)
    (let [[n d] x]
      (when (or (not (number? n)) (not (number? d)) (zero? d))
        (throw (ex-info (str "Not a rational: " (pr-str x)) {:type :not-a-number})))
      (if (and (integer? n) (integer? d))
        (rat n d)
        (rat-div (->rat n) (->rat d))))

    (integer? x) [x 1]
    (number? x)  (float->rat x)
    :else
    (throw (ex-info (str "Expected a number, got " (pr-str x)) {:type :not-a-number}))))

;;; Time spans

(defn span [start end] {:start start :end end})

(defn span-intersect [{s1 :start e1 :end} {s2 :start e2 :end}]
  (let [s (rat-max s1 s2)
        e (rat-min e1 e2)]
    (when (rat< s e)
      (span s e))))

(defn cycle-span [n]
  (span (int->rat n) (int->rat (inc n))))

;;; Events

(defn event [value whole part]
  {:value value :whole whole :part part})

;;; Pattern — a map wrapping a query function

(defn pattern [query-fn]
  ;; ::pat true is a reliable type tag so plain event-value maps (which also
  ;; happen to be Clojure maps) are never mistaken for patterns.
  {:query query-fn, ::pat true})

(defn pattern?
  "True if x is a REPuLse pattern created via (pattern ...)."
  [x]
  (true? (::pat x)))

(defn query [pat sp]
  ((:query pat) sp))

(defn- map-spans
  "Apply f (span → span) to the event's :part, and to its :whole when present.
   Analog events (whole = nil) keep whole = nil."
  [f ev]
  (cond-> (update ev :part f)
    (:whole ev) (update :whole f)))

(defn split-cycles
  "Split a span at cycle boundaries: [0, 5/2) → ([0,1) [1,2) [2,5/2))."
  [{:keys [start end]}]
  (let [start-c (int (Math/floor (rat->float start)))
        end-c   (int (Math/ceil  (rat->float end)))]
    (keep #(span-intersect (cycle-span %) (span start end))
          (range start-c end-c))))

(defn- per-cycle
  "Lift a query fn that decides by the cycle its span starts in, so it also
   answers spans covering several cycles — as (fast 2 …) asks of its input."
  [f]
  (fn [sp] (mapcat f (split-cycles sp))))

;;; Core combinators

(defn pure
  ([value] (pure value nil))
  ([value source]
   (pattern
    (fn [{:keys [start end]}]
      (let [start-c (int (Math/floor (rat->float start)))
            end-c   (int (Math/ceil  (rat->float end)))]
        (for [c (range start-c end-c)
              :let [whole (cycle-span c)
                    part  (span-intersect whole (span start end))]
              :when part]
          (let [base (event value whole part)]
            (if source (assoc base :source source) base))))))))

(defn seq*
  ([values] (seq* values nil))
  ([values sources]
   (let [n (count values)]
     (if (zero? n)
       (pattern (fn [_] []))
       (pattern
        (fn [{:keys [start end] :as sp}]
          (let [start-c (int (Math/floor (rat->float start)))
                end-c   (int (Math/ceil  (rat->float end)))]
            (for [c (range start-c end-c)
                  i (range n)
                  :let [s (rat (+ (* c n) i) n)
                        e (rat (+ (* c n) (inc i)) n)
                        whole (span s e)
                        part  (span-intersect whole sp)]
                  :when part]
              (let [base (event (nth values i) whole part)
                    src  (when sources (nth sources i nil))]
                (if src (assoc base :source src) base))))))))))

(defn stack* [pats]
  (pattern
   (fn [sp]
     (mapcat #(query % sp) pats))))

(defn fmap [f pat]
  (pattern
   (fn [sp]
     (map (fn [e] (update e :value f)) (query pat sp)))))

;;; ── Onsets ─────────────────────────────────────────────────────────

(defn onset?
  "True when ev is the start of its event: it has a :whole and its :part begins
   where the whole begins. Fragments (a whole cut by a query boundary, or by a
   structure-aware combinator) are not onsets and must not trigger sound."
  [ev]
  (boolean
   (when-let [w (:whole ev)]
     (= (:start w) (:start (:part ev))))))

(defn analog?
  "True for a continuous (analog) event: it has no :whole."
  [ev]
  (nil? (:whole ev)))

(defn query-onsets
  "Like query, but returns only the onset events — those that should trigger
   a sound in sp. Used by the scheduler, offline render and MIDI export."
  [pat sp]
  (filter onset? (query pat sp)))

;;; ── Structure-aware application (Tidal appLeft / appRight / appBoth) ─

(def silence (pattern (fn [_] [])))

(defn- ->pat [x] (if (pattern? x) x (pure x)))

(defn- whole-or-part [ev] (or (:whole ev) (:part ev)))

(defn- with-source [ev src]
  (if src (assoc ev :source src) ev))

(defn app-left
  "Tidal appLeft (`|+`): structure from pat-a. For every event of pat-a,
   query pat-b over that event's whole and pair them; the result keeps
   pat-a's whole and the intersection of both parts. Value: (f va vb)."
  [f pat-a pat-b]
  (pattern
   (fn [sp]
     (for [ea (query pat-a sp)
           eb (query pat-b (whole-or-part ea))
           :let [p (span-intersect (:part ea) (:part eb))]
           :when p]
       (with-source (event (f (:value ea) (:value eb)) (:whole ea) p)
                    (:source ea))))))

(defn app-right
  "Tidal appRight (`+|`): structure from pat-b. For every event of pat-b,
   query pat-a over that event's whole and pair them; the result keeps
   pat-b's whole and the intersection of both parts. Value: (f va vb)."
  [f pat-a pat-b]
  (pattern
   (fn [sp]
     (for [eb (query pat-b sp)
           ea (query pat-a (whole-or-part eb))
           :let [p (span-intersect (:part ea) (:part eb))]
           :when p]
       (with-source (event (f (:value ea) (:value eb)) (:whole eb) p)
                    (:source eb))))))

(defn app-both
  "Tidal appBoth (`|+|`): structure from both sides. Pairs events whose wholes
   and parts intersect; whole and part are the respective intersections.
   Value: (f va vb). :source comes from pat-a, falling back to pat-b."
  [f pat-a pat-b]
  (pattern
   (fn [sp]
     (let [evs-b (query pat-b sp)]
       (for [ea (query pat-a sp)
             eb evs-b
             :let [w (if (and (:whole ea) (:whole eb))
                       (span-intersect (:whole ea) (:whole eb))
                       (or (:whole ea) (:whole eb)))
                   p (span-intersect (:part ea) (:part eb))]
             :when (and w p)]
         (with-source (event (f (:value ea) (:value eb)) w p)
                      (or (:source ea) (:source eb))))))))

(defn combine
  "Applicative lift with structure from pat-b — identical to app-right.
   Kept for callers that predate TL1."
  [f pat-a pat-b]
  (app-right f pat-a pat-b))

;;; ── Joins ────────────────────────────────────────────────────────────

(defn inner-join
  "Flatten a pattern of patterns, taking structure from the inner patterns:
   for each outer event, the inner pattern is queried over the outer part."
  [pp]
  (pattern
   (fn [sp]
     (for [oe (query pp sp)
           ie (query (->pat (:value oe)) (:part oe))
           :let [p (span-intersect (:part ie) sp)]
           :when p]
       (assoc ie :part p)))))

(defn outer-join
  "Flatten a pattern of patterns, taking structure from the outer pattern:
   each outer event takes the value(s) the inner pattern has at the start
   of the outer event's whole."
  [pp]
  (pattern
   (fn [sp]
     (for [oe (query pp sp)
           :let [w (whole-or-part oe)
                 t (:start w)]
           ie (query (->pat (:value oe)) w)
           :when (and (rat<= (:start (:part ie)) t)
                      (rat< t (:end (:part ie))))]
       (with-source (event (:value ie) (:whole oe) (:part oe))
                    (or (:source ie) (:source oe)))))))

(defn squeeze-join
  "Flatten a pattern of patterns by squeezing one cycle of each inner pattern
   into the whole of the outer event that carries it (Tidal squeezeJoin).
   The outer event in cycle c plays cycle c of its inner pattern."
  [pp]
  (pattern
   (fn [sp]
     (mapcat
      (fn [oe]
        (let [w        (whole-or-part oe)
              ws       (:start w)
              d        (rat- (:end w) ws)
              c        (int->rat (Math/floor (rat->float ws)))
              to-inner (fn [t] (rat+ c (rat-div (rat- t ws) d)))
              to-outer (fn [t] (rat+ ws (rat* (rat- t c) d)))
              map-span (fn [f s] (span (f (:start s)) (f (:end s))))]
          (for [ie (query (->pat (:value oe)) (map-span to-inner (:part oe)))
                :let [nw (span-intersect w (map-span to-outer (whole-or-part ie)))
                      np (span-intersect (:part oe) (map-span to-outer (:part ie)))]
                :when (and nw np)]
            (assoc ie :whole nw :part np))))
      (query pp sp)))))

;;; ── Patterned arguments ──────────────────────────────────────────────

(defn patternify
  "Lift (f arg pat) so arg may be a scalar or a pattern.
   Scalars take the fast path (identical to the pre-TL1 behaviour and
   performance); a patterned arg is inner-joined (Tidal tParam)."
  [f]
  (fn [arg pat]
    (if (pattern? arg)
      (inner-join (fmap #(f % pat) arg))
      (f arg pat))))

(defn patternify2
  "Like patternify for (f a b pat): a and b may each be a scalar or a pattern.
   Patterned args are combined with app-both, then inner-joined."
  [f]
  (fn [a b pat]
    (if (or (pattern? a) (pattern? b))
      (inner-join (app-both (fn [x y] (f x y pat)) (->pat a) (->pat b)))
      (f a b pat))))

(defn patternify3
  "Like patternify for (f a b c pat)."
  [f]
  (fn [a b c pat]
    (if (or (pattern? a) (pattern? b) (pattern? c))
      (inner-join (app-both (fn [[x y] z] (f x y z pat))
                            (app-both vector (->pat a) (->pat b))
                            (->pat c)))
      (f a b c pat))))

;;; ── Speed ────────────────────────────────────────────────────────────

(defn- fast* [factor pat]
  (let [fr (->rat factor)]
    (if (zero? (first fr))
      silence
      (pattern
       (fn [sp]
         (let [fast-sp (span (rat* (:start sp) fr) (rat* (:end sp) fr))
               evs     (query pat fast-sp)]
           (map (fn [e]
                  (map-spans #(span (rat-div (:start %) fr)
                                    (rat-div (:end %) fr))
                             e))
                evs)))))))

(defn- slow* [factor pat]
  (let [fr (->rat factor)]
    (if (zero? (first fr))
      silence
      (fast* (rat-div (int->rat 1) fr) pat))))

(defn fast
  "Speed pat up by factor. factor may be a number, a rational [n d] or a
   pattern of numbers: (fast (seq 1 2) pat)."
  [factor pat]
  ((patternify fast*) factor pat))

(defn slow
  "Slow pat down by factor. factor may be a number, a rational [n d] or a
   pattern of numbers."
  [factor pat]
  ((patternify slow*) factor pat))

(defn rev [pat]
  (pattern
   (per-cycle
    (fn [{:keys [start end]}]
      ;; Reflect time within the queried cycle c: t ↦ c + (c+1) − t.
      ;; Query the reflected span so partial spans return the right events.
      (let [c       (int (Math/floor (rat->float start)))
            axis    (int->rat (inc (* 2 c)))
            reflect (fn [t] (rat- axis t))
            mirror  (fn [{s :start e :end}] (span (reflect e) (reflect s)))]
        (map (fn [e] (map-spans mirror e))
             (query pat (span (reflect end) (reflect start)))))))))

(defn- every* [n transform pat]
  (pattern
   (per-cycle
    (fn [sp]
      (let [cycle (int (Math/floor (rat->float (:start sp))))]
        (if (zero? (mod cycle n))
          (query (transform pat) sp)
          (query pat sp)))))))

(defn every
  "Apply transform to pat on every nth cycle (cycles 0, n, 2n, …).
   n may be a number or a pattern of numbers."
  [n transform pat]
  ((patternify (fn [n' p] (every* n' transform p))) n pat))

(defn arrange*
  "plan: [[pattern cycles] …]
   Returns a Pattern that plays each section in order, looping after the total duration."
  [plan]
  (let [timeline (reduce
                   (fn [acc [pat dur]]
                     (let [prev (:to (last acc) 0)]
                       (conj acc {:pat pat :from prev :to (+ prev dur)})))
                   [] plan)
        total    (or (:to (last timeline)) 1)]
    (pattern
     (per-cycle
      (fn [{:keys [start end]}]
        (let [g-cycle  (int (Math/floor (rat->float start)))
              lc       (mod g-cycle total)
              loop-off (- g-cycle lc)
              entry    (some #(when (and (>= lc (:from %)) (< lc (:to %))) %) timeline)]
          (when entry
            (let [sec-off     (:from entry)
                  offset      (+ loop-off sec-off)
                  local-start (rat+ start [(- offset) 1])
                  local-end   (rat+ end   [(- offset) 1])
                  evs         (query (:pat entry) {:start local-start :end local-end})]
              (map (fn [e]
                     (map-spans #(span (rat+ (:start %) [offset 1])
                                       (rat+ (:end   %) [offset 1]))
                                e))
                   evs)))))))))

;;; ── Phase I: Pattern Combinators ───────────────────────────────────

(defn cat*
  "Concatenate patterns: each plays for one full cycle, then the whole sequence loops.
   Unlike seq* (which subdivides one cycle), cat* gives each pattern its own cycle.
   (cat* [p1 p2 p3]) — 3-cycle loop: p1 for cycle 0, p2 for cycle 1, p3 for cycle 2."
  [pats]
  (let [n (count pats)]
    (if (zero? n)
      (pattern (fn [_] []))
      (pattern
       (per-cycle
        (fn [{:keys [start] :as sp}]
          (let [cycle  (int (Math/floor (rat->float start)))
                idx    (mod cycle n)
                pat    (nth pats idx)]
            (query pat sp))))))))

(defn- late* [amount pat]
  (let [off (->rat amount)]
    (pattern
     (fn [{:keys [start end]}]
       (let [q-start (rat- start off)
             q-end   (rat- end off)
             evs     (query pat (span q-start q-end))]
         (map (fn [e]
                (map-spans #(span (rat+ (:start %) off)
                                  (rat+ (:end %) off))
                           e))
              evs))))))

(defn late
  "Shift all events forward in time by amount (fraction of a cycle).
   amount may be a number, a rational [n d] or a pattern of numbers.
   Preserves :source for editor highlighting.
   (late 1/4 pat) — delay by 1/4 cycle"
  [amount pat]
  ((patternify late*) amount pat))

(defn early
  "Shift all events backward in time by amount (fraction of a cycle).
   Equivalent to (late (- amount) pat); amount may be a pattern.
   (early 1/4 pat) — advance by 1/4 cycle"
  [amount pat]
  ((patternify (fn [a p] (late* (rat-neg (->rat a)) p))) amount pat))

(defn cycle-hash
  "Deterministic hash of a cycle number. Returns 0–99."
  [cycle]
  (mod (+ (* (Math/abs cycle) 48271) 12345) 100))

(defn- sometimes-by*
  [prob f pat]
  (let [threshold (int (* prob 100))]
    (pattern
     (per-cycle
      (fn [sp]
        (let [cycle (int (Math/floor (rat->float (:start sp))))]
          (if (< (cycle-hash cycle) threshold)
            (query (f pat) sp)
            (query pat sp))))))))

(defn sometimes-by
  "Apply transform f to pat on cycles where (cycle-hash cycle) < (prob * 100).
   prob is 0.0–1.0, or a pattern of probabilities. Deterministic: the same
   cycle number always makes the same choice.
   (sometimes-by 0.5 rev pat) — reverse ~50% of cycles"
  [prob f pat]
  ((patternify (fn [pr p] (sometimes-by* pr f p))) prob pat))

(defn sometimes
  "Apply transform on ~50% of cycles.
   (sometimes rev pat)"
  [f pat]
  (sometimes-by 0.5 f pat))

(defn often
  "Apply transform on ~75% of cycles.
   (often (fast 2) pat)"
  [f pat]
  (sometimes-by 0.75 f pat))

(defn rarely
  "Apply transform on ~25% of cycles.
   (rarely rev pat)"
  [f pat]
  (sometimes-by 0.25 f pat))

(defn- event-hash
  "Deterministic hash of an event's start position. Returns 0–99.
   Uses the :whole start [numerator denominator] to seed."
  [event]
  (let [[n d] (:start (:whole event))]
    (mod (+ (* (Math/abs n) 48271) (* (Math/abs d) 22543) 9137) 100)))

(defn- degrade-by*
  [prob pat]
  (let [threshold (int (* prob 100))]
    (pattern
     (fn [sp]
       (filter #(>= (event-hash %) threshold) (query pat sp))))))

(defn degrade-by
  "Randomly drop events from pat with probability prob (0.0–1.0, or a pattern
   of probabilities). Uses a deterministic hash of each event's time position.
   (degrade-by 0.3 pat) — drop ~30% of events"
  [prob pat]
  ((patternify degrade-by*) prob pat))

(defn degrade
  "Drop ~50% of events randomly. Shorthand for (degrade-by 0.5 pat).
   (degrade (fast 4 (seq :hh :oh :hh :oh)))"
  [pat]
  (degrade-by 0.5 pat))

(defn choose
  "Pick one value from xs per cycle (deterministic based on cycle number).
   Returns a pattern that produces one event per cycle.
   (choose [:bd :sd :hh :oh])
   Optional sources vector attaches :source to events for editor highlighting."
  ([xs] (choose xs nil))
  ([xs sources]
   (let [n (count xs)]
     (pattern
      (fn [{:keys [start end]}]
        (let [start-c (int (Math/floor (rat->float start)))
              end-c   (int (Math/ceil  (rat->float end)))]
          (for [c (range start-c end-c)
                :let [idx   (mod (cycle-hash c) n)
                      whole (cycle-span c)
                      part  (span-intersect whole (span start end))]
                :when part]
            (let [base (event (nth xs idx) whole part)
                  src  (when sources (nth sources idx nil))]
              (if src (assoc base :source src) base)))))))))

(defn wchoose
  "Weighted random choice per cycle. Takes a vector of [weight value] pairs.
   Weights are relative (don't need to sum to 1.0).
   (wchoose [[0.5 :bd] [0.3 :sd] [0.2 :hh]])
   Optional sources vector attaches :source to events for editor highlighting."
  ([pairs] (wchoose pairs nil))
  ([pairs sources]
   (let [total      (reduce + (map first pairs))
         cumulative (reductions + (map #(* 100 (/ (first %) total)) pairs))
         values     (mapv second pairs)]
     (pattern
      (fn [{:keys [start end]}]
        (let [start-c (int (Math/floor (rat->float start)))
              end-c   (int (Math/ceil  (rat->float end)))]
          (for [c (range start-c end-c)
                :let [h     (cycle-hash c)
                      idx   (or (first (keep-indexed
                                         (fn [i thresh]
                                           (when (< h thresh) i))
                                         cumulative))
                                (dec (count values)))
                      whole (cycle-span c)
                      part  (span-intersect whole (span start end))]
                :when part]
            (let [base (event (nth values idx) whole part)
                  src  (when sources (nth sources idx nil))]
              (if src (assoc base :source src) base)))))))))

(defn off
  "Layer the original pattern with a time-shifted, transformed copy.
   amount may be a number, a rational or a pattern of numbers.
   (off 1/8 (fast 2) pat) — original + 1/8-cycle-shifted double-speed copy"
  [amount f pat]
  (stack* [pat (late amount (f pat))]))

;;; ── Phase TL2: Deterministic RNG (Tidal timeToRand) ─────────────────

(def ^:private two-29 536870912)

(defn- i64 [x] (js/BigInt.asIntN 64 x))

(defn xorwise
  "Tidal's xorshift step on a 64-bit signed integer (BigInt in, BigInt out).
   Public: TL4's random combinators reuse it."
  [x]
  (let [a (i64 (js* "((~{} << 13n) ^ ~{})" x x))
        b (i64 (js* "((~{} >> 17n) ^ ~{})" a a))]
    (i64 (js* "((~{} << 5n) ^ ~{})" b b))))

(defn time->int-seed
  "Tidal timeToIntSeed' (before xorwise): truncate(frac(t / 300) * 2^29) for an
   exact rational time [n d] → BigInt, in (-2^29, 2^29)."
  [[n d]]
  (let [n   (js/BigInt n)
        den (js* "(~{} * 300n)" (js/BigInt d))
        r   (js* "(~{} % ~{})" n den)]               ; truncating remainder = properFraction
    (js* "((~{} * 536870912n) / ~{})" r den)))       ; BigInt division truncates toward zero

(defn time->rand
  "Tidal timeToRand: deterministic float in [0, 1) for rational time t."
  [t]
  (let [s (xorwise (time->int-seed (->rat t)))
        m (js* "(((~{} % 536870912n) + 536870912n) % 536870912n)" s)] ; floored mod
    (/ (js/Number m) two-29)))

;;; ── Phase TL2: Continuous signals ───────────────────────────────────

(defn signal
  "Continuous pattern from f : rational time → value. A query returns one analog
   event (whole nil) covering the query span, sampled at the span's start."
  [f]
  (pattern
   (fn [{:keys [start end] :as sp}]
     (if (rat< start end)
       [(event (f start) nil sp)]
       []))))

(defn steady [v] (signal (constantly v)))

(defn- cycle-pos
  "Exact position within the cycle, as a float in [0, 1): (n mod d) / d."
  [[n d]]
  (/ (mod n d) d))

(defn- sine-at [x] (/ (+ (Math/sin (* 2 Math/PI x)) 1) 2))

(def time (signal rat->float))
(def saw (signal cycle-pos))
(def isaw (signal #(- 1 (cycle-pos %))))
(def sine (signal #(sine-at (cycle-pos %))))
(def cosine (signal #(sine-at (cycle-pos (rat- % [1 4])))))
(defn- tri-at [x] (if (< x 0.5) (* 2 x) (- 2 (* 2 x))))
(def tri (signal #(tri-at (cycle-pos %))))
(def itri (signal #(- 1 (tri-at (cycle-pos %)))))
(def square (signal #(if (< (cycle-pos %) 0.5) 0 1)))
(def isquare (signal #(if (< (cycle-pos %) 0.5) 1 0)))
(def rand (signal time->rand))

(def perlin
  (signal
   (fn [[n d :as t]]
     (let [fl (Math/floor (/ n d))
           x  (- (rat->float t) fl)
           a  (time->rand [fl 1])
           b  (time->rand [(inc fl) 1])
           sm (+ (* 6 (Math/pow x 5)) (* -15 (Math/pow x 4)) (* 10 (Math/pow x 3)))]
       (+ a (* sm (- b a)))))))

(defn to-bipolar   [pat] (fmap #(if (number? %) (- (* 2 %) 1) %) pat))
(defn from-bipolar [pat] (fmap #(if (number? %) (/ (+ % 1) 2) %) pat))

(def sine2 (to-bipolar sine))
(def cosine2 (to-bipolar cosine))
(def saw2 (to-bipolar saw))
(def isaw2 (to-bipolar isaw))
(def tri2 (to-bipolar tri))
(def itri2 (to-bipolar itri))
(def square2 (to-bipolar square))
(def isquare2 (to-bipolar isquare))
(def rand2 (to-bipolar rand))

(defn- irand* [n]
  (fmap #(Math/floor (* % (rat->float (->rat n)))) rand))

(defn irand
  "Random integers 0 … n-1 as a continuous pattern. n may be a pattern."
  [n]
  (if (pattern? n) (inner-join (fmap irand* n)) (irand* n)))

(defn- run* [n]
  (let [k (int (rat->float (->rat n)))]
    (if (pos? k) (seq* (vec (range k))) silence)))

(defn run
  "Discrete pattern 0 … n-1 in one cycle. n may be a pattern."
  [n]
  (if (pattern? n) (inner-join (fmap run* n)) (run* n)))

;;; ── Phase TL2: Shaping ──────────────────────────────────────────────

(defn- segment* [n pat]
  ;; Tidal: _segment n p = _fast n (pure id) <* p
  (app-left (fn [_ v] v) (fast* n (pure true)) pat))

(defn segment
  "Sample pat n times per cycle: n discrete events with values taken from pat at
   each onset. n may be a number, rational or pattern."
  [n pat]
  ((patternify segment*) n pat))

(defn- num-val [x] (if (vector? x) (rat->float x) x))

(defn- scale-val [v lo hi]
  (if (number? v)
    (let [lo (num-val lo) hi (num-val hi)]
      (+ lo (* (- hi lo) v)))
    v))

(defn range-pat
  "Tidal range: scale numeric values v in [0,1] to lo + (hi - lo)·v.
   lo/hi: numbers (fast path, fmap) or patterns (app-left, structure from pat).
   Non-numeric values pass through unchanged."
  [lo hi pat]
  (if (or (pattern? lo) (pattern? hi))
    (app-left (fn [v [l h]] (scale-val v l h))
              pat
              (app-both vector (->pat lo) (->pat hi)))
    (fmap #(scale-val % lo hi) pat)))

(defn rangex-pat
  "Exponential range: exp(range(log lo, log hi, v)). lo and hi must be > 0,
   otherwise throw ex-info {:type :eval-error} \"rangex needs positive bounds\"."
  [lo hi pat]
  (let [check (fn [x] (when (and (number? (num-val x)) (not (pos? (num-val x))))
                        (throw (ex-info "rangex needs positive bounds"
                                        {:type :eval-error}))))]
    (when-not (pattern? lo) (check lo))
    (when-not (pattern? hi) (check hi))
    (let [lg (fn [x] (if (pattern? x) (fmap #(do (check %) (Math/log (num-val %))) x)
                         (Math/log (num-val x))))]
      (fmap #(if (number? %) (Math/exp %) %)
            (range-pat (lg lo) (lg hi) pat)))))

(defn range2-pat
  "Strudel range2: range over a bipolar input = (range-pat lo hi (from-bipolar pat))."
  [lo hi pat]
  (range-pat lo hi (from-bipolar pat)))

;;; ── Phase TL3: Time & structure combinators ─────────────────────────

(defn step-true?
  "Boolean reading of a step value: false for false nil 0 :_ :f :false."
  [v]
  (not (contains? #{false nil 0 :_ :f :false} v)))

(defn- keep-values [pred pat]
  (pattern (fn [sp] (filter #(pred (:value %)) (query pat sp)))))

(defn- cycle-of [sp] (int (Math/floor (rat->float (:start sp)))))

(defn- cycle-pos-rat
  "Exact position of rational t inside its cycle, in [0, 1)."
  [[n d]] (rat (mod n d) d))

(defn- shift-event [ev off]
  (map-spans #(span (rat+ (:start %) off) (rat+ (:end %) off)) ev))

(defn- ->int
  "Truncate a number or rational to an integer."
  [x] (int (rat->float (->rat x))))

(defn- fmap-event [f pat]
  (pattern (fn [sp] (map #(assoc % :value (f %)) (query pat sp)))))

(defn- compress*
  "Tidal compressArc: cycle c of pat squeezed into [c+s, c+e). 0 <= s < e <= 1."
  [s e pat]
  (let [s (->rat s) e (->rat e) d (rat- e s)]
    (if (or (rat< s [0 1]) (rat> e [1 1]) (rat<= e s))
      silence
      (pattern
       (per-cycle
        (fn [piece]
          (let [c   (int->rat (cycle-of piece))
                win (span-intersect piece (span (rat+ c s) (rat+ c e)))]
            (when win
              (let [to-inner (fn [t] (rat+ c (rat-div (rat- t (rat+ c s)) d)))
                    to-outer (fn [t] (rat+ (rat+ c s) (rat* (rat- t c) d)))
                    in-sp    (span (to-inner (:start win)) (to-inner (:end win)))]
                (keep (fn [ev]
                        (let [ev' (map-spans #(span (to-outer (:start %)) (to-outer (:end %))) ev)]
                          (when-let [p (span-intersect (:part ev') win)]
                            (assoc ev' :part p))))
                      (query pat in-sp)))))))))))

(defn- slowcat-held*
  "Tidal cat offsets: result cycle c plays (pat-of c) at source cycle floor(c/n)."
  [n pat-of]
  (pattern
   (per-cycle
    (fn [sp]
      (let [c     (cycle-of sp)
            delta (int->rat (- c (int (Math/floor (/ c n)))))
            p     (pat-of c)]
        (map #(shift-event % delta)
             (query p (span (rat- (:start sp) delta) (rat- (:end sp) delta)))))))))

(defn- within* [s e f pat]
  (let [s   (->rat s) e (->rat e)
        fp  (f pat)
        in? (fn [ev] (let [p (cycle-pos-rat (:start (whole-or-part ev)))]
                       (and (rat<= s p) (rat< p e))))]
    (stack* [(pattern (fn [sp] (filter in? (query fp sp))))
             (pattern (fn [sp] (remove in? (query pat sp))))])))

(defn timecat
  "pairs: [[weight x] …]; x a value or a pattern. Tidal timeCat."
  [pairs]
  (let [pairs (->> pairs
                   (map (fn [[w x]] [(->rat w) (->pat x)]))
                   (filter #(rat< [0 1] (first %)))
                   vec)
        total (reduce rat+ [0 1] (map first pairs))]
    (if (empty? pairs)
      silence
      (stack* (second (reduce (fn [[acc out] [w p]]
                                (let [nxt (rat+ acc w)]
                                  [nxt (conj out (compress* (rat-div acc total)
                                                            (rat-div nxt total) p))]))
                              [[0 1] []] pairs))))))

(defn- fastcat* [xs] (timecat (mapv (fn [x] [1 x]) xs)))

;;; Björklund + Euclid

(defn bjorklund
  "Tidal's bjorklund: vector of n booleans with k trues spread evenly.
   Negative k inverts; k > n clamps to n; n <= 0 → []."
  [k n]
  (let [k (int k) n (int n)]
    (cond
      (<= n 0) []
      (neg? k) (mapv not (bjorklund (- k) n))
      :else
      (let [k (min k n)]
        (loop [i k j (- n k)
               xs (vec (repeat k [true]))
               ys (vec (repeat (- n k) [false]))]
          (if (<= (min i j) 1)
            (vec (concat (apply concat xs) (apply concat ys)))
            (if (> i j)
              (recur j (- i j) (mapv into (subvec xs 0 j) ys) (subvec xs j))
              (recur i (- j i) (mapv into xs (subvec ys 0 i)) (subvec ys i)))))))))

(defn- euclid-bools [k n r]
  (let [k (->int k) n (->int n) r (->int r)]
    (if (<= n 0)
      []
      (let [v (bjorklund k n)
            r (mod r n)]
        (into (subvec v r) (subvec v 0 r))))))

;;; Boolean structure

(defn struct
  "Structure from bools: for every true event of bools, the value(s) of pat
   over that event's whole. False/rest steps produce nothing."
  [bools pat]
  (let [b (keep-values step-true? (->pat bools))]
    (pattern
     (fn [sp]
       (for [eb (query b sp)
             ev (query pat (whole-or-part eb))
             :let [p (span-intersect (:part eb) (:part ev))]
             :when p]
         (with-source (event (:value ev) (:whole eb) p)
                      (or (:source ev) (:source eb))))))))

(defn mask
  "Structure from pat; keep only the parts overlapping true events of bools."
  [bools pat]
  (app-left (fn [v _] v) pat (keep-values step-true? (->pat bools))))

(defn invert
  "Boolean not on every non-rest value."
  [pat]
  (fmap #(if (= :_ %) % (not (step-true? %))) pat))

(defn- euclidean* [k n val r]
  (let [bs (euclid-bools k n r)]
    (if (empty? bs)
      silence
      (if (pattern? val)
        (struct (seq* bs) val)
        (seq* (mapv #(if % val :_) bs))))))

(defn euclidean
  "Björklund's algorithm (Tidal bjorklund): distribute k onsets across n steps.
   k, n and rotation (left) may each be a number or a pattern of numbers.
   Third argument a value → seq of value and :_ rests;
   a pattern → struct form (Strudel euclid): the pattern gets the rhythm.
   (euclidean 3 8 :bd)              — bd . . bd . . bd .
   (euclidean 3 8 (seq :bd :sd) 2)  — restructure a pattern, rotated 2"
  ([k n val] (euclidean k n val 0))
  ([k n val rotation]
   ((patternify3 (fn [k' n' r' v] (euclidean* k' n' v r'))) k n rotation val)))

(defn- euclid-legato* [k n r pat]
  (let [bs   (euclid-bools k n r)
        idxs (vec (keep-indexed (fn [i b] (when b i)) bs))
        cnt  (count bs)]
    (if (empty? idxs)
      silence
      (let [lead (first idxs)
            hits (map-indexed (fn [j i] [(- (if (< (inc j) (count idxs)) (nth idxs (inc j)) cnt) i) true])
                              idxs)]
        (struct (timecat (into (if (pos? lead) [[lead false]] []) hits)) pat)))))

(defn euclid-legato
  "Like the struct form of euclidean, but each hit lasts until the next hit."
  [k n pat]
  ((patternify2 (fn [k' n' p] (euclid-legato* k' n' 0 p))) k n pat))

(defn euclid-legato-rot
  "euclid-legato on the rotated boolean list; steps before the first hit rest."
  [k n r pat]
  ((patternify3 euclid-legato*) k n r pat))

;;; Repetition

(defn- ply* [n pat]
  (squeeze-join (fmap-event #(fast* n (pure (:value %) (:source %))) pat)))

(defn ply
  "Repeat each event n times inside its own whole."
  [n pat] ((patternify ply*) n pat))

(defn- ply-with* [n f pat]
  (let [k (max 1 (int (Math/ceil (rat->float (->rat n)))))]
    (squeeze-join
     (fmap-event #(fastcat* (vec (take k (iterate f (pure (:value %) (:source %)))))) pat))))

(defn ply-with
  "Each event becomes ceil(n) sub-steps; sub-step i has f applied i times."
  [n f pat] ((patternify (fn [n' p] (ply-with* n' f p))) n pat))

;;; Windows & groove

(defn- inside* [n f pat] (fast* n (f (slow* n pat))))

(defn inside
  "Apply f as if each 1/n of a cycle were a cycle."
  [n f pat] ((patternify (fn [n' p] (inside* n' f p))) n pat))

(defn outside
  "Apply f across n cycles: (inside 1/n f pat)."
  [n f pat]
  ((patternify (fn [n' p]
                 (let [r (->rat n')]
                   (if (zero? (first r)) silence (inside* (rat-div [1 1] r) f p)))))
   n pat))

(defn within
  "Apply f only to events whose whole starts in cycle position [s, e)."
  [s e f pat] ((patternify2 (fn [s' e' p] (within* s' e' f p))) s e pat))

(defn- swing-by* [x n pat]
  (inside* n #(within* [1 2] [1 1] (fn [q] (late* x q)) %) pat))

(defn swing-by
  "Delay events in the second half of each 1/n slice by x of the slice."
  [x n pat] ((patternify2 swing-by*) x n pat))

(defn swing
  "(swing-by 1/3 n pat)"
  [n pat] ((patternify (fn [n' p] (swing-by* [1 3] n' p))) n pat))

(defn brak
  "Odd cycles squeezed into the first half and shifted right by 1/4."
  [pat]
  (let [odd-pat (late* [1 4] (compress* [0 1] [1 2] pat))]
    (pattern (per-cycle (fn [sp] (query (if (odd? (cycle-of sp)) odd-pat pat) sp))))))

;;; Rotation

(defn- iter* [n pat back?]
  (let [n (->int n)]
    (if (< n 1)
      pat
      (slowcat-held* n (fn [c] (let [amt (rat (mod c n) n)]
                                 (if back? (late* amt pat) (late* (rat-neg amt) pat))))))))

(defn iter
  "Shift the start of the pattern earlier by 1/n each cycle (cycle-holding)."
  [n pat] ((patternify #(iter* %1 %2 false)) n pat))

(defn iter-back
  "Like iter, shifting later."
  [n pat] ((patternify #(iter* %1 %2 true)) n pat))

(defn palindrome
  "Alternate pat and (rev pat) each cycle (cycle-holding)."
  [pat]
  (let [r (rev pat)] (slowcat-held* 2 #(if (even? %) pat r))))

(defn- cmp-rat [a b] (cond (rat< a b) -1 (rat< b a) 1 :else 0))

(defn- rot* [n pat]
  (let [n (->int n)]
    (pattern
     (per-cycle
      (fn [sp]
        (let [c      (cycle-of sp)
              evs    (query pat (cycle-span c))
              rot?   (fn [e] (and (:whole e) (not= :_ (:value e))))
              rs     (vec (sort (fn [a b]
                                  (let [r (cmp-rat (:start (:whole a)) (:start (:whole b)))]
                                    (if (zero? r)
                                      (cmp-rat (:start (:part a)) (:start (:part b)))
                                      r)))
                                (filter rot? evs)))
              fixed  (remove rot? evs)
              m      (count rs)
              vs     (mapv :value rs)
              rotated (if (zero? m)
                        rs
                        (vec (map-indexed (fn [j e] (assoc e :value (nth vs (mod (+ j n) m)))) rs)))]
          (keep (fn [e] (when-let [p (span-intersect (:part e) sp)] (assoc e :part p)))
                (concat rotated fixed))))))))

(defn rot
  "Shift values n places left within each cycle, keeping the structure."
  [n pat] ((patternify rot*) n pat))

;;; Chunks

(defn- chunk* [n f pat idx-fn hold?]
  (let [n (->int n)]
    (if (< n 1)
      pat
      (let [variant (fn [c] (let [i (idx-fn c n)] (within* (rat i n) (rat (inc i) n) f pat)))]
        (if hold?
          (slowcat-held* n variant)
          (pattern (per-cycle (fn [sp] (query (variant (cycle-of sp)) sp)))))))))

(defn chunk
  "Apply f to chunk (c mod n) of the cycle, cycle-holding."
  [n f pat] ((patternify (fn [n' p] (chunk* n' f p #(mod %1 %2) true))) n pat))

(defn chunk-back
  "As chunk, with chunk index (mod (- c) n)."
  [n f pat] ((patternify (fn [n' p] (chunk* n' f p #(mod (- %1) %2) true))) n pat))

(defn fast-chunk
  "As chunk, without cycle-holding."
  [n f pat] ((patternify (fn [n' p] (chunk* n' f p #(mod %1 %2) false))) n pat))

;;; Meters & conditionals

(defn- poly* [st pairs]
  (let [st (->rat st)]
    (if (<= (first st) 0)
      silence
      (stack* (map (fn [[p k]] (fast* (rat-div st (->rat k)) p)) pairs)))))

(defn polymeter-pats
  "pairs: [[pat step-count] …]. steps nil → first layer's count; number or pattern."
  [steps pairs]
  (let [pairs (filterv #(pos? (rat->float (->rat (second %)))) pairs)]
    (cond
      (empty? pairs)     silence
      (nil? steps)       (poly* (second (first pairs)) pairs)
      (pattern? steps)   (inner-join (fmap #(poly* % pairs) steps))
      :else              (poly* steps pairs))))

(defn polymeter
  "layers: [[x …] …] step vectors (values or patterns)."
  [steps layers]
  (polymeter-pats steps (keep #(when (seq %) [(fastcat* %) (count %)]) layers)))

(defn first-of
  "Exactly every: apply f when c mod n = 0."
  [n f pat] (every n f pat))

(defn- last-of* [n f pat]
  (let [n (->int n)]
    (if (< n 1)
      pat
      (pattern (per-cycle (fn [sp] (query (if (= (mod (cycle-of sp) n) (dec n)) (f pat) pat) sp)))))))

(defn last-of
  "Apply f when c mod n = n - 1."
  [n f pat] ((patternify (fn [n' p] (last-of* n' f p))) n pat))

;;; ── Phase TL3b: Zoom, compress & structure extras ───────────────────

(defn compress
  "Squeeze each cycle into [s, e) of the cycle (0 <= s < e <= 1), silence elsewhere."
  [s e pat] ((patternify2 compress*) s e pat))

(defn- zoom*
  "Tidal zoomArc: window [c+s, c+e) of cycle c stretched onto [c, c+1)."
  [s e pat]
  (let [s (->rat s) e (->rat e) d (rat- e s)]
    (if (rat<= e s)
      silence
      (pattern
       (per-cycle
        (fn [piece]
          (let [c      (int->rat (cycle-of piece))
                to-src (fn [t] (rat+ (rat+ c s) (rat* (rat- t c) d)))
                to-res (fn [t] (rat+ c (rat-div (rat- t (rat+ c s)) d)))]
            (map (fn [ev] (map-spans #(span (to-res (:start %)) (to-res (:end %))) ev))
                 (query pat (span (to-src (:start piece)) (to-src (:end piece))))))))))))

(defn zoom
  "Play the window [s, e) of each cycle, stretched to fill the cycle."
  [s e pat] ((patternify2 zoom*) s e pat))

(defn- focus* [s e pat]
  (let [s (->rat s) e (->rat e)]
    (if (rat<= e s)
      silence
      (late* s (fast* (rat-div [1 1] (rat- e s))
                      (late* (rat-neg (int->rat (cycle-of (span s s)))) pat))))))

(defn focus
  "Squeeze the cycle into [s, e) and keep playing (no gaps)."
  [s e pat] ((patternify2 focus*) s e pat))

(defn- fast-gap* [n pat]
  (let [n (->rat n)]
    (cond (zero? (first n)) silence
          (rat< n [1 1])    pat
          :else             (compress* [0 1] (rat-div [1 1] n) pat))))

(defn fast-gap
  "n times faster, then silence until the next cycle."
  [n pat] ((patternify fast-gap*) n pat))

(defn- linger* [n pat]
  (let [n (->rat n) len (if (neg? (first n)) (rat-neg n) n)]
    (cond
      (zero? (first n))    silence
      (rat>= len [1 1])    pat
      :else
      (let [off (if (neg? (first n)) (rat- [1 1] len) [0 1])
            k   (int (Math/ceil (rat->float (rat-div [1 1] len))))]
        (pattern
         (per-cycle
          (fn [piece]
            (let [c (int->rat (cycle-of piece))]
              (mapcat (fn [i]
                        (let [ws  (rat+ c (rat* (int->rat i) len))
                              win (span-intersect piece (span ws (rat-min (rat+ ws len) (rat+ c [1 1]))))]
                          (when win
                            (query (late* (rat- (rat* (int->rat i) len) off) pat) win))))
                      (range k))))))))))

(defn linger
  "Repeat the first n of every cycle (last |n| for negative n)."
  [n pat] ((patternify linger*) n pat))

(defn- split-at-multiples
  "Split span sp at multiples of the rational L: [[k piece] …]."
  [L sp]
  (let [k0 (int (Math/floor (rat->float (rat-div (:start sp) L))))
        k1 (int (Math/ceil  (rat->float (rat-div (:end sp) L))))]
    (keep (fn [k]
            (when-let [piece (span-intersect sp (span (rat* (int->rat k) L)
                                                      (rat* (int->rat (inc k)) L)))]
              [k piece]))
          (range k0 k1))))

(defn- ribbon* [offset cycles pat]
  (let [off (->rat offset) L (->rat cycles)]
    (if (rat<= L [0 1])
      silence
      (pattern
       (fn [sp]
         (mapcat (fn [[k piece]]
                   (query (late* (rat- (rat* (int->rat k) L) off) pat) piece))
                 (split-at-multiples L sp)))))))

(defn ribbon
  "Loop `cycles` cycles of pat starting at `offset`."
  [offset cycles pat] ((patternify2 ribbon*) offset cycles pat))

(defn- trunc* [t pat]
  (let [t (->rat t)]
    (cond
      (rat<= t [0 1]) silence
      (rat>= t [1 1]) pat
      :else
      (pattern
       (per-cycle
        (fn [piece]
          (let [c (int->rat (cycle-of piece))]
            (when-let [w (span-intersect piece (span c (rat+ c t)))]
              (query pat w)))))))))

(defn trunc
  "Only the first t of every cycle plays; wholes are kept, parts cut."
  [t pat] ((patternify trunc*) t pat))

(defn revv
  "Reverse time across cycles (t -> -t)."
  [pat]
  (let [neg (fn [{s :start e :end}] (span (rat-neg e) (rat-neg s)))]
    (pattern (fn [sp] (map #(map-spans neg %) (query pat (neg sp)))))))

(defn- repeat-cycles* [n pat]
  (let [n (->int n)]
    (if (< n 1) pat (slowcat-held* n (constantly pat)))))

(defn repeat-cycles
  "Each source cycle plays n times before the next."
  [n pat] ((patternify repeat-cycles*) n pat))

(defn- press-by* [r pat]
  (squeeze-join (fmap-event #(compress* r [1 1] (pure (:value %) (:source %))) pat)))

(defn press-by
  "Shift every event to start r of the way through its own whole."
  [r pat] ((patternify press-by*) r pat))

(defn press
  "(press-by 1/2 pat)"
  [pat] (press-by* [1 2] pat))

(defn- bite* [n ipat pat]
  (let [n (->int n)]
    (if (< n 1)
      silence
      (squeeze-join
       (pattern
        (fn [sp]
          (keep (fn [ev]
                  (let [v (:value ev)]
                    (when (or (number? v) (vector? v))
                      (let [i (mod (->int v) n)]
                        (assoc ev :value (zoom* (rat i n) (rat (inc i) n) pat))))))
                (query ipat sp))))))))

(defn bite
  "Cut each cycle into n slices; each event of ipat plays its slice."
  [n ipat pat] ((patternify (fn [n' p] (bite* n' (->pat ipat) p))) n pat))

(defn- chunk-into* [n f pat idx-fn]
  (let [n (->int n)]
    (if (< n 1)
      pat
      (pattern
       (per-cycle
        (fn [sp]
          (let [i (idx-fn (cycle-of sp) n) a (rat i n) b (rat (inc i) n)]
            (query (within* a b #(compress* a b (f (zoom* a b %))) pat) sp))))))))

(defn chunk-into
  "Apply f to the looped sub-cycle of chunk (c mod n)."
  [n f pat] ((patternify (fn [n' p] (chunk-into* n' f p #(mod %1 %2)))) n pat))

(defn chunk-back-into
  "As chunk-into, chunk index (mod (-(c+1)) n)."
  [n f pat] ((patternify (fn [n' p] (chunk-into* n' f p #(mod (- (inc %1)) %2)))) n pat))

(defn- ply-for-each* [n f pat]
  (let [k (max 1 (int (Math/ceil (rat->float (->rat n)))))]
    (squeeze-join
     (fmap-event (fn [ev]
                   (let [p0 (pure (:value ev) (:source ev))]
                     (fastcat* (into [p0] (map #(f p0 %) (range 1 k))))))
                 pat))))

(defn ply-for-each
  "Each event becomes ceil(n) sub-steps; sub-step i >= 1 is (f (pure v) i)."
  [n f pat] ((patternify (fn [n' p] (ply-for-each* n' f p))) n pat))

(defn- euclidish* [k n g pat]
  (let [bs   (euclid-bools k n 0)
        n'   (count bs)
        idxs (vec (keep-indexed (fn [i b] (when b i)) bs))
        k'   (count idxs)
        g    (rat-max [0 1] (rat-min [1 1] (->rat g)))]
    (if (or (<= (->int k) 0) (zero? n') (zero? k'))
      silence
      (struct (stack* (map-indexed
                       (fn [j i]
                         (let [p (rat i n') q (rat j k')
                               b (rat+ p (rat* (rat- q p) g))]
                           (compress* b (rat+ b (rat 1 n')) (pure true))))
                       idxs))
              pat))))

(defn euclidish
  "Euclidean rhythm morphed towards even spacing by g in [0,1]."
  [k n g pat] ((patternify3 euclidish*) k n g pat))

(defn filter-events
  "Keep events whose value satisfies pred."
  [pred pat] (keep-values (comp boolean pred) pat))

(defn- lisp-time [[n d]] (if (= d 1) n [n d]))

(defn filter-when
  "Keep events whose whole start (integer or [n d]) satisfies pred."
  [pred pat]
  (pattern (fn [sp] (filter #(pred (lisp-time (:start (whole-or-part %)))) (query pat sp)))))

(defn bypass
  "on true -> silence, otherwise pat."
  [on pat]
  ((patternify (fn [o p] (if (step-true? o) silence p))) on pat))

(defn- beat* [t div pat]
  (let [div (->int div)]
    (if (<= div 0)
      silence
      (let [t (mod (->int t) div)]
        (inner-join (fmap-event #(compress* (rat t div) (rat (inc t) div)
                                            (pure (:value %) (:source %)))
                                pat))))))

(defn beat
  "Place every value of pat on step t mod div of div."
  [t div pat] ((patternify2 beat*) t div pat))

(defn sew
  "Structure from a and b, cut to where bools is true / false."
  [bools a b]
  (let [bools (->pat bools)]
    (stack* [(mask bools a) (mask (invert bools) b)])))

(defn stitch
  "Structure from bools; true steps take a, false steps take b."
  [bools a b]
  (let [bools (->pat bools)]
    (stack* [(struct bools a) (struct (invert bools) b)])))

(defn- bits->pat [n bits]
  (seq* (mapv #(odd? (Math/floor (/ n (Math/pow 2 %)))) (range (dec bits) -1 -1))))

(defn- binary* [n]
  (let [n (->int n)]
    (if (<= n 0) (seq* [false]) (bits->pat n (count (.toString n 2))))))

(defn binary
  "Bits of n, MSB first, minimal bit count, as a true/false step sequence."
  [n] (if (pattern? n) (inner-join (fmap binary* n)) (binary* n)))

(defn- binary-n* [n bits]
  (let [bits (min 52 (->int bits))]
    (if (<= bits 0)
      silence
      (bits->pat (mod (->int n) (Math/pow 2 bits)) bits))))

(defn binary-n
  "The low `bits` (default 16) bits of n, MSB first."
  ([n] (binary-n n 16))
  ([n bits]
   (if (or (pattern? n) (pattern? bits))
     (inner-join (app-both binary-n* (->pat n) (->pat bits)))
     (binary-n* n bits))))
