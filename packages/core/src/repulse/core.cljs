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
  "plan: [[pattern cycles] ...]
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
