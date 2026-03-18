(ns repulse.core)

;;; Rational arithmetic — rationals as [numerator denominator]

(defn gcd [a b]
  (loop [a (Math/abs a) b (Math/abs b)]
    (if (zero? b) a (recur b (mod a b)))))

(defn rat
  ([n] [n 1])
  ([n d]
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
  {:query query-fn})

(defn query [pat sp]
  ((:query pat) sp))

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

(defn combine
  "Applicative liftA2: pair events from pat-a and pat-b that overlap in time.
   For each (ea, eb) pair whose :part spans intersect, produce an event with
   value (f va vb) at the intersection. Uses eb's :whole."
  [f pat-a pat-b]
  (pattern
   (fn [sp]
     (let [evs-a (query pat-a sp)
           evs-b (query pat-b sp)]
       (for [ea evs-a
             eb evs-b
             :let [isect (span-intersect (:part ea) (:part eb))]
             :when isect]
         (let [base (event (f (:value ea) (:value eb)) (:whole eb) isect)]
           (if-let [src (:source eb)]
             (assoc base :source src)
             base)))))))

(defn fast [factor pat]
  (let [fr (if (vector? factor) factor (int->rat factor))]
    (pattern
     (fn [sp]
       (let [fast-sp (span (rat* (:start sp) fr) (rat* (:end sp) fr))
             evs     (query pat fast-sp)]
         (map (fn [e]
                (-> e
                    (update :whole #(span (rat-div (:start %) fr)
                                          (rat-div (:end %) fr)))
                    (update :part  #(span (rat-div (:start %) fr)
                                          (rat-div (:end %) fr)))))
              evs))))))

(defn slow [factor pat]
  (let [fr (if (vector? factor) factor (int->rat factor))]
    (fast (rat-div (int->rat 1) fr) pat)))

(defn rev [pat]
  (pattern
   (fn [sp]
     (map (fn [e]
            (let [mirror (fn [{:keys [start end]}]
                           (let [c (int (Math/floor (rat->float start)))
                                 c1 (int->rat (inc c))]
                             (span (rat- c1 (rat- end (int->rat c)))
                                   (rat- c1 (rat- start (int->rat c))))))]
              (-> e
                  (update :whole mirror)
                  (update :part  mirror))))
          (query pat sp)))))

(defn every [n transform pat]
  (pattern
   (fn [sp]
     (let [cycle (int (Math/floor (rat->float (:start sp))))]
       (if (zero? (mod cycle n))
         (query (transform pat) sp)
         (query pat sp))))))

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
                    (-> e
                        (update :whole #(span (rat+ (:start %) [offset 1])
                                              (rat+ (:end   %) [offset 1])))
                        (update :part  #(span (rat+ (:start %) [offset 1])
                                              (rat+ (:end   %) [offset 1])))))
                  evs))))))))

;;; ── Phase I: Pattern Combinators ───────────────────────────────────

(defn euclidean
  "Björklund's algorithm: distribute k onsets across n steps as evenly as possible.
   Returns a seq pattern of val and :_ rests.
   (euclidean 5 8 :bd)     — 5 hits in 8 steps
   (euclidean 5 8 :bd 2)   — rotated 2 steps"
  ([k n val] (euclidean k n val 0))
  ([k n val rotation]
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

(defn cat*
  "Concatenate patterns: each plays for one full cycle, then the whole sequence loops.
   Unlike seq* (which subdivides one cycle), cat* gives each pattern its own cycle.
   (cat* [p1 p2 p3]) — 3-cycle loop: p1 for cycle 0, p2 for cycle 1, p3 for cycle 2."
  [pats]
  (let [n (count pats)]
    (if (zero? n)
      (pattern (fn [_] []))
      (pattern
       (fn [{:keys [start end] :as sp}]
         (let [cycle  (int (Math/floor (rat->float start)))
               idx    (mod cycle n)
               pat    (nth pats idx)]
           (query pat sp)))))))

(defn late
  "Shift all events forward in time by amount (fraction of a cycle).
   Queries the pattern at (start - offset, end - offset), then shifts events
   back by +offset. Preserves :source for editor highlighting.
   (late 0.25 pat) — delay by 1/4 cycle"
  [amount pat]
  (let [off (if (vector? amount) amount (rat (int (* amount 1000)) 1000))]
    (pattern
     (fn [{:keys [start end]}]
       (let [q-start (rat- start off)
             q-end   (rat- end off)
             evs     (query pat (span q-start q-end))]
         (map (fn [e]
                (-> e
                    (update :whole #(span (rat+ (:start %) off)
                                          (rat+ (:end %) off)))
                    (update :part  #(span (rat+ (:start %) off)
                                          (rat+ (:end %) off)))))
              evs))))))

(defn early
  "Shift all events backward in time by amount (fraction of a cycle).
   Equivalent to (late (- amount) pat).
   (early 0.25 pat) — advance by 1/4 cycle"
  [amount pat]
  (let [neg-off (if (vector? amount)
                  [(- (first amount)) (second amount)]
                  (rat (- (int (* amount 1000))) 1000))]
    (late neg-off pat)))

(defn- cycle-hash
  "Deterministic hash of a cycle number. Returns 0–99."
  [cycle]
  (mod (+ (* (Math/abs cycle) 48271) 12345) 100))

(defn sometimes-by
  "Apply transform f to pat on cycles where (cycle-hash cycle) < (prob * 100).
   prob is 0.0–1.0. Deterministic: same cycle number always makes the same choice.
   (sometimes-by 0.5 rev pat) — reverse ~50% of cycles"
  [prob f pat]
  (let [threshold (int (* prob 100))]
    (pattern
     (fn [sp]
       (let [cycle (int (Math/floor (rat->float (:start sp))))]
         (if (< (cycle-hash cycle) threshold)
           (query (f pat) sp)
           (query pat sp)))))))

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

(defn degrade-by
  "Randomly drop events from pat with probability prob (0.0–1.0).
   Uses deterministic hash of each event's time position.
   (degrade-by 0.3 pat) — drop ~30% of events"
  [prob pat]
  (let [threshold (int (* prob 100))]
    (pattern
     (fn [sp]
       (filter #(>= (event-hash %) threshold) (query pat sp))))))

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
      (fn [{:keys [start end] :as sp}]
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
      (fn [{:keys [start end] :as sp}]
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
   (off 0.125 (fast 2) pat) — original + 1/8-cycle-shifted double-speed copy"
  [amount f pat]
  (stack* [pat (late amount (f pat))]))
