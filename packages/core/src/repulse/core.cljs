(ns repulse.core
  (:refer-clojure :exclude [rand time]))

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

(defn- euclidean*
  [k n val rotation]
  (let [k (int k) n (int n) rotation (int rotation)]
   (let [;; Björklund algorithm — iteratively distribute remainders
         result
         (loop [groups  (into (vec (repeat k [true]))
                              (repeat (- n k) [false]))]
           (let [cnt-a (count (filter #(= (first %) (first (first groups))) groups))
                 cnt-b (- (count groups) cnt-a)]
             (if (or (<= cnt-b 1) (= (count groups) n))
               (vec (mapcat identity groups))
               (let [take-n (min cnt-a cnt-b)
                     head   (subvec groups 0 take-n)
                     mid    (subvec groups take-n cnt-a)
                     tail   (subvec groups cnt-a)]
                 (recur (into (mapv (fn [a b] (into a b))
                                    head (subvec tail 0 take-n))
                              (into mid (subvec tail take-n))))))))
         ;; Apply rotation
         rotated (let [r (mod rotation n)
                       v (vec result)]
                   (into (subvec v r) (subvec v 0 r)))
         ;; Map booleans to values
         values (mapv #(if % val :_) rotated)]
     (seq* values))))

(defn euclidean
  "Björklund's algorithm: distribute k onsets across n steps as evenly as possible.
   Returns a seq pattern of val and :_ rests. k, n and rotation may each be a
   number or a pattern of numbers.
   (euclidean 5 8 :bd)       — 5 hits in 8 steps
   (euclidean 5 8 :bd 2)     — rotated 2 steps
   (euclidean (seq 3 5) 8 :bd) — patterned hit count"
  ([k n val] (euclidean k n val 0))
  ([k n val rotation]
   ((patternify3 (fn [k' n' r' v] (euclidean* k' n' v r'))) k n rotation val)))

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
