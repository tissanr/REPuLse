(ns repulse.lisp.integration-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [repulse.core :as core]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]))

;;; Helpers

(defn- make-test-env []
  (leval/make-env (fn [] nil) (fn [_] nil)))

(defn- eval-pattern
  "Evaluate code, assert it produces a pattern, return events for one cycle."
  [code]
  (let [env (make-test-env)
        r   (lisp/eval-string code env)]
    (is (not (lisp/eval-error? r)) (str "eval error: " (:message r)))
    (let [pat (:result r)]
      (is (core/pattern? pat) (str "expected pattern, got: " (type pat)))
      (when (core/pattern? pat)
        (core/query pat (core/cycle-span 0))))))

(defn- eval-result
  "Evaluate code and return the raw result (not necessarily a pattern)."
  [code]
  (let [env (make-test-env)
        r   (lisp/eval-string code env)]
    (is (not (lisp/eval-error? r)) (str "eval error: " (:message r)))
    (:result r)))

(defn- event-values [evs]
  (mapv #(leval/unwrap (:value %)) evs))

;;; ── Basic pattern combinators ───────────────────────────────────────

(deftest seq-produces-correct-events
  (testing "(seq :bd :sd :hh :sd) produces 4 events per cycle"
    (let [evs (eval-pattern "(seq :bd :sd :hh :sd)")]
      (is (= 4 (count evs)))
      (is (= [:bd :sd :hh :sd] (event-values evs))))))

(deftest stack-overlaps-patterns
  (testing "(stack (pure :bd) (pure :sd)) produces 2 overlapping events"
    (let [evs (eval-pattern "(stack (pure :bd) (pure :sd))")]
      (is (= 2 (count evs)))
      (is (= #{:bd :sd} (set (event-values evs)))))))

(deftest fast-doubles-events
  (testing "(fast 2 (seq :bd :sd)) produces 4 events"
    (let [evs (eval-pattern "(fast 2 (seq :bd :sd))")]
      (is (= 4 (count evs)))
      (is (= [:bd :sd :bd :sd] (event-values evs))))))

(deftest slow-halves-events
  (testing "(slow 2 (seq :bd :sd)) produces 1 event in cycle 0"
    (let [evs (eval-pattern "(slow 2 (seq :bd :sd))")]
      (is (= 1 (count evs)))
      (is (= [:bd] (event-values evs))))))

(deftest rev-reverses-pattern
  (testing "(rev (seq :a :b :c)) reverses order within cycle"
    (let [evs (eval-pattern "(rev (seq :a :b :c))")]
      (is (= 3 (count evs)))
      ;; rev reverses timing positions; the last event occupies the first slot
      (is (= #{:a :b :c} (set (event-values evs)))))))

;;; ── Parameter threading ─────────────────────────────────────────────

(deftest amp-wraps-event-value
  (testing "(amp 0.7 (seq :bd :sd)) wraps values with :amp param"
    (let [evs (eval-pattern "(amp 0.7 (seq :bd :sd))")]
      (is (= 2 (count evs)))
      (doseq [ev evs]
        (is (map? (:value ev)))
        (is (= 0.7 (:amp (:value ev))))))))

(deftest thread-last-chains-params
  (testing "(->> (seq :c4 :e4) (amp 0.5) (pan -1.0)) chains correctly"
    (let [evs (eval-pattern "(->> (seq :c4 :e4) (amp 0.5) (pan -1.0))")]
      (is (= 2 (count evs)))
      (doseq [ev evs]
        (let [v (:value ev)]
          (is (map? v))
          (is (= 0.5 (:amp v)))
          (is (= -1.0 (:pan v))))))))

(deftest attack-decay-params
  (testing "attack and decay params thread through"
    (let [evs (eval-pattern "(->> (pure :c4) (attack 0.1) (decay 0.5))")]
      (is (= 1 (count evs)))
      (let [v (:value (first evs))]
        (is (= 0.1 (:attack v)))
        (is (= 0.5 (:decay v)))))))

;;; ── Euclidean and combinators ───────────────────────────────────────

(deftest euclidean-3-8
  (testing "(euclidean 3 8 :bd) produces 8 slots with 3 hits and 5 rests"
    (let [evs (eval-pattern "(euclidean 3 8 :bd)")
          hits (filter #(= :bd (leval/unwrap (:value %))) evs)
          rests (filter #(= :_ (leval/unwrap (:value %))) evs)]
      (is (= 8 (count evs)))
      (is (= 3 (count hits)))
      (is (= 5 (count rests))))))

(deftest cat-concatenates-patterns
  (testing "(cat (seq :bd :sd) (seq :hh :oh)) plays first in cycle 0"
    (let [evs (eval-pattern "(cat (seq :bd :sd) (seq :hh :oh))")]
      (is (= 2 (count evs)))
      (is (= [:bd :sd] (event-values evs))))))

(deftest off-produces-offset-layer
  (testing "(off 0.5 identity (pure :c4)) produces offset events"
    (let [evs (eval-pattern "(off 0.5 identity (pure :c4))")]
      (is (>= (count evs) 2)))))

;;; ── Music theory integration ────────────────────────────────────────

(deftest chord-produces-stacked-notes
  (testing "(chord :major :c4) produces a pattern with 3 notes"
    (let [evs (eval-pattern "(chord :major :c4)")]
      (is (= 3 (count evs))))))

(deftest transpose-shifts-notes
  (testing "(transpose 12 (seq :c4 :e4)) shifts values up an octave"
    (let [evs (eval-pattern "(transpose 12 (seq :c4 :e4))")]
      (is (= 2 (count evs)))
      ;; transpose produces Hz frequencies, not keywords
      (let [vals (event-values evs)]
        (is (number? (first vals)))
        (is (> (first vals) 500))))))

;;; ── def and multi-form programs ─────────────────────────────────────

(deftest def-and-reuse
  (testing "def + track reuse across forms"
    (let [evs (eval-pattern "(def kick (seq :bd :bd)) kick")]
      (is (= 2 (count evs)))
      (is (= [:bd :bd] (event-values evs))))))

(deftest multi-form-returns-last-pattern
  (testing "multi-form program returns the last evaluated pattern"
    (let [evs (eval-pattern "(def x (seq :a :b)) (fast 2 x)")]
      (is (= 4 (count evs)))
      (is (= [:a :b :a :b] (event-values evs))))))

;;; ── Mini-notation ───────────────────────────────────────────────────

(deftest mini-notation-basic
  (testing "(~ \"bd sd hh sd\") produces 4 events"
    (let [evs (eval-pattern "(~ \"bd sd hh sd\")")]
      (is (= 4 (count evs)))
      (is (= [:bd :sd :hh :sd] (event-values evs))))))

(deftest mini-notation-subdivision
  (testing "(~ \"bd [sd sd] hh\") subdivides second slot"
    (let [evs (eval-pattern "(~ \"bd [sd sd] hh\")")]
      (is (= 4 (count evs))))))

;;; ── Error handling ──────────────────────────────────────────────────

(deftest undefined-symbol-returns-error
  (testing "undefined symbol returns an EvalError"
    (let [env (make-test-env)
          r   (lisp/eval-string "(nonexistent-fn)" env)]
      (is (lisp/eval-error? r))
      (is (re-find #"Undefined symbol" (:message r))))))

(deftest type-error-returns-error
  (testing "calling a non-function returns an EvalError"
    (let [env (make-test-env)
          r   (lisp/eval-string "(42 :bd)" env)]
      (is (lisp/eval-error? r))
      (is (re-find #"not a function" (:message r))))))

;;; ── Event timing ────────────────────────────────────────────────────

(deftest seq-events-span-equal-slices
  (testing "seq events divide the cycle into equal parts"
    (let [evs (eval-pattern "(seq :a :b :c :d)")]
      (is (= 4 (count evs)))
      (doseq [[i ev] (map-indexed vector evs)]
        (let [start (core/rat->float (:start (:part ev)))
              end   (core/rat->float (:end   (:part ev)))]
          (is (< (Math/abs (- start (/ i 4))) 1e-9)
              (str "event " i " start should be " (/ i 4) " got " start))
          (is (< (Math/abs (- end (/ (inc i) 4))) 1e-9)
              (str "event " i " end should be " (/ (inc i) 4) " got " end)))))))

(deftest fast-compresses-timing
  (testing "(fast 2 (seq :a :b)) events fit 4 in one cycle"
    (let [evs (eval-pattern "(fast 2 (seq :a :b))")]
      (is (= 4 (count evs)))
      (let [starts (mapv #(core/rat->float (:start (:part %))) evs)]
        (is (< (Math/abs (- (nth starts 0) 0.0))  1e-9))
        (is (< (Math/abs (- (nth starts 1) 0.25)) 1e-9))
        (is (< (Math/abs (- (nth starts 2) 0.5))  1e-9))
        (is (< (Math/abs (- (nth starts 3) 0.75)) 1e-9))))))

;;; ── Synth voices ────────────────────────────────────────────────────

(deftest calling-a-signal-is-a-helpful-error
  (testing "(saw :c4) and (square :c4 :pw 0.3) point to (synth :saw ...)"
    (doseq [[code voice] [["(saw :c4)" "(synth :saw"]
                          ["(square :c4 :pw 0.3)" "(synth :square"]]]
      (let [r (lisp/eval-string code (make-test-env))]
        (is (lisp/eval-error? r) code)
        (is (re-find #"not a function" (:message r)) code)
        (is (.includes (:message r) voice) code))))
  (testing "(sine) says to drop the parentheses"
    (let [r (lisp/eval-string "(sine)" (make-test-env))]
      (is (lisp/eval-error? r))
      (is (re-find #"without parentheses" (:message r)))))
  (testing "the synth voice still works"
    (let [evs (eval-pattern "(synth :saw (seq :c4 :e4))")]
      (is (= 2 (count evs)))
      (is (every? #(= :saw (:synth (leval/unwrap (:value %)))) evs)))))

(deftest fm-produces-synth-map
  (testing "(fm :a4 :index 3.0 :ratio 1.5) produces correct params"
    (let [r (eval-result "(fm :a4 :index 3.0 :ratio 1.5)")]
      (is (map? r))
      (is (= :fm (:synth r)))
      (is (= 3.0 (:index r)))
      (is (= 1.5 (:ratio r))))))

;;; ── Arrange / song structure ────────────────────────────────────────

(deftest arrange-creates-pattern
  (testing "arrange creates a playable pattern from a plan"
    (let [evs (eval-pattern "(arrange [[(seq :bd :sd) 2] [(pure :hh) 1]])")]
      (is (pos? (count evs))))))

;;; ── Phase TL1: patterned arguments & pattern arithmetic ─────────────

(defn- eval-pat [code]
  (let [r (lisp/eval-string code (make-test-env))]
    (is (not (lisp/eval-error? r)) (str "eval error: " (:message r)))
    (:result r)))

(defn- cycle-evs [pat n] (core/query pat (core/cycle-span n)))

(defn- onsets [pat n] (core/query-onsets pat (core/cycle-span n)))

(defn- starts [evs] (mapv #(:start (:whole %)) evs))

(deftest tl1-fast-rational-literal-stays-exact
  (testing "(fast 1/3 (seq :a :b)) over cycles 0–3000: no float drift, no overflow"
    (let [pat (eval-pat "(fast 1/3 (seq :a :b))")
          evs (mapcat #(cycle-evs pat %) (range 0 3001))]
      ;; one (seq :a :b) cycle now lasts 3 cycles: :a starts at 0, 3, 6, … < 3001
      (is (= 1001 (count (filter #(and (core/onset? %) (= :a (:value %))) evs))))
      (is (= [3000 1] (:start (:whole (first (onsets pat 3000))))))
      (is (every? (fn [e] (every? (fn [[_ d]] (<= 1 d 2))
                                  [(:start (:whole e)) (:end (:whole e))
                                   (:start (:part e)) (:end (:part e))]))
                  evs)))))

(deftest tl1-fast-patterned-factor
  (testing "(fast (seq 1 2) (seq :bd :sd)): :bd at 0, then :bd at 1/2 and :sd at 3/4"
    (let [evs (cycle-evs (eval-pat "(fast (seq 1 2) (seq :bd :sd))") 0)]
      (is (= [:bd :bd :sd] (event-values evs)))
      (is (= [[0 1] [1 2] [3 4]] (starts evs))))))

(deftest tl1-fast-mini-notation-factor
  (testing "(fast \"<1 2 4>\" (seq :bd :sd)) yields 2, 4, 8 events in cycles 0, 1, 2"
    (let [pat (eval-pat "(fast \"<1 2 4>\" (seq :bd :sd))")]
      (is (= [2 4 8] (mapv #(count (onsets pat %)) [0 1 2]))))))

(deftest tl1-fast-mini-string-keeps-source
  (testing "events from a mini-notation factor still carry the inner pattern's source"
    (let [evs (cycle-evs (eval-pat "(fast \"<1 2>\" (seq :bd :sd))") 0)]
      (is (every? :source evs)))))

(deftest tl1-late-rational-mini
  (testing "(late \"<0 1/8>\" …) nudges every other cycle by exactly 1/8"
    (let [pat (eval-pat "(late \"<0 1/8>\" (seq :hh :hh :hh :hh))")]
      (is (= [[0 1] [1 4] [1 2] [3 4]] (starts (onsets pat 0))))
      (is (= [[9 8] [11 8] [13 8] [15 8]] (starts (onsets pat 1)))))))

(deftest tl1-euclidean-patterned
  (testing "(euclidean \"<3 5>\" 8 :bd) has 3 onsets in cycle 0 and 5 in cycle 1"
    (let [pat  (eval-pat "(euclidean \"<3 5>\" 8 :bd)")
          hits (fn [n] (count (filter #(= :bd (:value %)) (onsets pat n))))]
      (is (= 3 (hits 0)))
      (is (= 5 (hits 1))))))

(deftest tl1-other-patterned-args
  (testing "slow, early, every, degrade-by, sometimes-by, off, jux-by accept patterns/strings"
    (doseq [code ["(slow \"<1 2>\" (seq :a :b))"
                  "(early \"<0 1/4>\" (seq :a :b))"
                  "(every \"<2 3>\" rev (seq :a :b))"
                  "(degrade-by \"<0 0.5>\" (seq :a :b))"
                  "(sometimes-by (seq 0 1) rev (seq :a :b))"
                  "(off \"<1/8 1/4>\" (fast 2) (seq :a :b))"
                  "(jux-by \"<0.5 1>\" rev (seq :a :b))"
                  "(->> (seq :a :b) (fast \"<1 2>\") (late \"<0 1/8>\") (degrade-by \"0\"))"]]
      (is (core/pattern? (eval-pat code)) code)))
  (testing "curried forms return transformers"
    (let [evs (cycle-evs (eval-pat "((fast \"<2 4>\") (seq :a :b))") 0)]
      (is (= 4 (count evs))))))

(deftest tl1-scalar-calls-unchanged
  (testing "integer and float scalar args give the same events as before TL1"
    (is (= (mapv (juxt :value :whole :part) (cycle-evs (eval-pat "(fast 2 (seq :a :b))") 0))
           [[:a {:start [0 1] :end [1 4]} {:start [0 1] :end [1 4]}]
            [:b {:start [1 4] :end [1 2]} {:start [1 4] :end [1 2]}]
            [:a {:start [1 2] :end [3 4]} {:start [1 2] :end [3 4]}]
            [:b {:start [3 4] :end [1 1]} {:start [3 4] :end [1 1]}]]))
    (is (= [[1 4]] (starts (onsets (eval-pat "(late 0.25 (pure :a))") 0))))))

;;; Arithmetic — Tidal "1 2 3" |+ "10 20" and friends

(defn- vals-and-onsets [evs]
  (mapv (fn [e] [(:value e) (core/onset? e)]) evs))

(deftest tl1-add-in
  (let [evs (cycle-evs (eval-pat "(add :in (seq 10 20) (seq 1 2 3))") 0)]
    (testing "onsets 11 12 23 with the wholes of (seq 1 2 3), plus fragment 22"
      (is (= [[11 true] [12 true] [22 false] [23 true]] (vals-and-onsets evs)))
      (is (= [[0 1] [1 3] [1 3] [2 3]] (starts evs))))
    (testing ":in is the default"
      (is (= (map :value evs)
             (map :value (cycle-evs (eval-pat "(add (seq 10 20) (seq 1 2 3))") 0)))))))

(deftest tl1-add-out
  (let [evs (cycle-evs (eval-pat "(add :out (seq 10 20) (seq 1 2 3))") 0)]
    (is (= [11 22] (map :value (filter core/onset? evs))))
    (is (= [[0 1] [1 2]] (starts (filter core/onset? evs))))))

(deftest tl1-add-mix
  (let [evs (cycle-evs (eval-pat "(add :mix (seq 10 20) (seq 1 2 3))") 0)]
    (is (= [11 12 22 23] (map :value (filter core/onset? evs))))
    (is (every? core/onset? evs))))

(deftest tl1-add-squeeze
  (let [evs (cycle-evs (eval-pat "(add :squeeze (seq 0 10) (seq 1 2))") 0)]
    (is (= [1 11 2 12] (map :value evs)))
    (is (= [[0 1] [1 4] [1 2] [3 4]] (starts evs)))))

(deftest tl1-sub-mul-div
  (is (= [9 19] (map :value (cycle-evs (eval-pat "(sub 1 (seq 10 20))") 0))))
  (is (= [20 40] (map :value (cycle-evs (eval-pat "(mul 2 (seq 10 20))") 0))))
  (is (= [5 10] (map :value (cycle-evs (eval-pat "(div 2 (seq 10 20))") 0))))
  (is (= [1 1.5] (map :value (cycle-evs (eval-pat "(add 1/2 (seq 0.5 1))") 0)))))

(deftest tl1-add-notes-and-rests
  (testing "(add 12 (seq :c3 :e3)) moves notes up an octave"
    (is (= [:c4 :e4] (map :value (cycle-evs (eval-pat "(add 12 (seq :c3 :e3))") 0)))))
  (testing "rests and drum names pass through"
    (is (= [:_ 3] (map :value (cycle-evs (eval-pat "(add 1 (seq :_ 2))") 0))))
    (is (= [:bd] (map :value (cycle-evs (eval-pat "(add 1 (pure :bd))") 0)))))
  (testing "maps apply the op to :note"
    (is (= [{:note :d4 :amp 0.5}]
           (map :value (cycle-evs (eval-pat "(add 2 (amp 0.5 (pure :c4)))") 0))))))

(deftest tl1-set-merges-controls
  (is (= [{:note :c4 :amp 0.5} {:note :e4 :amp 0.25}]
         (map :value (cycle-evs (eval-pat "(set (seq {:amp 0.5} {:amp 0.25}) (seq :c4 :e4))") 0))))
  (is (= [{:note :c4 :amp 0.9}]
         (map :value (cycle-evs (eval-pat "(set {:amp 0.9} (amp 0.1 (pure :c4)))") 0))))
  (is (= [:_] (map :value (cycle-evs (eval-pat "(set {:amp 0.9} (pure :_))") 0)))))

(deftest tl1-arith-curried-in-thread
  (testing "(->> (seq 1 3 5) (add \"<0 2>\") (scale :minor :c3))"
    (let [pat  (eval-pat "(->> (seq 1 3 5) (add \"<0 2>\") (scale :minor :c3))")
          base (eval-pat "(scale :minor :c3 (seq 1 3 5))")
          up   (eval-pat "(scale :minor :c3 (seq 3 5 7))")]
      (is (= (map :value (cycle-evs base 0)) (map :value (cycle-evs pat 0))))
      (is (= (map :value (cycle-evs up 1)) (map :value (cycle-evs pat 1))))))
  (testing "mode + amount curried form"
    (is (= [11 22] (map :value (filter core/onset?
                                       (cycle-evs (eval-pat "((add :out (seq 10 20)) (seq 1 2 3))") 0))))))
  (testing "bad arity is an eval error"
    (is (lisp/eval-error? (lisp/eval-string "(add)" (make-test-env))))))

(deftest tl1-documented-examples-evaluate
  (testing "every TL1 example in builtin_meta.edn / docs evaluates to a pattern"
    (doseq [code ["(fast \"<1 2 4>\" (seq :bd :sd))"
                  "(slow (seq 1 2) (seq :bd :sd :hh :oh))"
                  "(every \"<2 4>\" rev (seq :bd :sd :hh :oh))"
                  "(late \"<0 1/8>\" (seq :hh :hh :hh :hh))"
                  "(early (seq 0 1/8) (seq :hh :oh :hh :oh))"
                  "(sometimes-by \"<0.2 0.8>\" rev (seq :bd :sd :hh :oh))"
                  "(degrade-by \"<0 0.5>\" (fast 8 (pure :hh)))"
                  "(jux-by \"<0.3 1>\" rev (seq :bd :sd))"
                  "(off \"<1/8 1/4>\" (fast 2) (seq :bd :sd))"
                  "(euclidean \"<3 5>\" 8 :bd)"
                  "(add 12 (seq :c3 :e3 :g3))"
                  "(->> (seq 1 3 5 8) (add \"<0 2 4>\") (scale :minor :c3))"
                  "(add :out (seq 10 20) (seq 1 2 3))"
                  "(sub 12 (seq :c4 :e4 :g4))"
                  "(->> (seq 8 7 5) (sub \"<0 1>\") (scale :major :c4))"
                  "(mul 2 (seq 220 330))"
                  "(mul :mix (seq 1 2) (seq 110 220 330))"
                  "(div 2 (seq 440 660))"
                  "(div \"<1 2>\" (seq 440 880))"
                  "(set {:amp 0.5 :pan -0.5} (seq :c4 :e4))"
                  "(set (seq {:amp 1.0} {:amp 0.4}) (seq :bd :hh :sd :hh))"
                  "(add :squeeze (seq 0 7) (seq :c3 :g3))"
                  "(mul 2 (seq 220 330))"
                  "(fast 1/3 (seq :bd :sd))"
                  "(~ \"0 1/8 1/4\")"]]
      (let [pat (eval-pat code)]
        (is (core/pattern? pat) code)
        (when (core/pattern? pat)
          (is (seq (mapcat #(cycle-evs pat %) (range 4))) code))))))

;;; ── Phase TL2: continuous signals ───────────────────────────────────

(defn- seg
  "Values of (segment n sig) at cycle k for Lisp code."
  ([code] (seg code 0))
  ([code k]
   (->> (onsets (eval-pat code) k)
        (sort-by #(core/rat->float (:start (:whole %))))
        (mapv :value))))

(defn- near-all? [expected actual tol]
  (and (= (count expected) (count actual))
       (every? true? (map #(< (Math/abs (- %1 %2)) tol) expected actual))))

(deftest tl2-bare-signals-are-patterns
  (doseq [nm ["sine" "cosine" "saw" "isaw" "tri" "itri" "square" "isquare" "sine2" "saw2"
              "rand" "rand2" "perlin" "time"]]
    (is (core/pattern? (eval-pat nm)) nm)
    (is (empty? (onsets (eval-pat nm) 0)) nm)))

(deftest tl2-segment-and-waveforms
  (is (= [0 0.25 0.5 0.75] (seg "(segment 4 saw)")))
  (is (= [0 0.25 0.5 0.75] (seg "(segment 4 saw)" 1)))
  (is (near-all? [0.5 1 0.5 0] (seg "(segment 4 sine)") 1e-12))
  (is (= [0 0 1 1] (seg "(segment 4 square)")))
  (is (= [0 0.6295689214020967 0.2591378428041935 0.20052618719637394] (seg "(segment 4 rand)")))
  (is (= [0 5 2 1] (seg "(segment 4 (irand 8))")))
  (is (= [0 1 2 3] (seg "(run 4)")))
  (is (= [3 3 3 3] (seg "(segment 4 (steady 3))")))
  (is (near-all? [0 0.5 1 1.5] (seg "(segment 4 (signal (fn [t] (* 2 t))))") 1e-12)))

(deftest tl2-parameters-sampled-at-onsets
  (let [evs (sort-by #(core/rat->float (:start (:whole %)))
                     (onsets (eval-pat "(pan sine (fast 8 (seq :hh)))") 0))]
    (is (= 8 (count evs)))
    (is (near-all? [0.5 0.8535533905932737 1 0.8535533905932737 0.5
                    0.14644660940672627 0 0.14644660940672616]
                   (mapv #(:pan (:value %)) evs) 1e-12)))
  (let [pat (eval-pat "(->> (fast 16 (seq :hh)) (amp (range 0.3 0.9 (slow 4 saw))))")
        amp-at (fn [cyc start]
                 (->> (onsets pat cyc)
                      (filter #(= start (:start (:whole %))))
                      first :value :amp))]
    (is (< (Math/abs (- 0.3 (amp-at 0 [0 1]))) 1e-12))
    (is (< (Math/abs (- 0.375 (amp-at 0 [1 2]))) 1e-12))
    (is (< (Math/abs (- 0.440625 (amp-at 0 [15 16]))) 1e-12))
    (is (< (Math/abs (- 0.890625 (amp-at 3 [63 16]))) 1e-12))))

(deftest tl2-range-dispatch
  (testing "pattern last argument is Tidal range"
    (let [vs (seg "(segment 16 (range 2 6 saw))")]
      (is (= 2 (first vs)))
      (is (every? #(and (>= % 2) (< % 6)) vs)))
    (is (< (Math/abs (- 6 (second (seg "(segment 4 (range 2 6 sine))")))) 1e-12))
    (is (near-all? [20 63.245553203367585 200] (take 3 (seg "(segment 4 (rangex 20 2000 saw))")) 1e-9))
    (is (near-all? (seg "(segment 4 sine2)") (seg "(segment 4 (range2 -1 1 sine2))") 1e-12)))
  (testing "rangex with non-positive bounds is an eval error"
    (is (lisp/eval-error? (lisp/eval-string "(rangex 0 10 saw)" (make-test-env)))))
  (testing "collection range unchanged"
    (is (= [0 1 2 3 4] (eval-result "(vec (range 5))")))
    (is (= [0 2 4 6] (vec (eval-result "(range 0 8 2)"))))))

(deftest tl2-lisp-examples
  (let [evs (onsets (eval-pat "(->> (segment 8 (range 1 8 rand)) (scale :minor :c4))") 0)]
    (is (= 8 (count evs)))
    (is (every? #(number? (:value %)) evs)))
  (let [evs (onsets (eval-pat "(pan (range -0.5 0.5 perlin) (fast 4 (seq :hh)))") 0)]
    (is (= 4 (count evs)))
    (is (every? #(<= -0.5 (:pan (:value %)) 0.5) evs)))
  (testing "curried forms"
    (is (= [0 0.25 0.5 0.75] (seg "(->> saw (segment 4))")))
    (is (= 4 (count (seg "(->> saw (range 2 6) (segment 4))"))))
    (is (= 2 (count (seg "(->> sine (rangex 100 1000) (segment 2))")))))
  (testing "nil-whole safety through the Lisp layer"
    (is (= [0 0.5 0 0.5] (seg "(segment 4 (fast 2 saw))")))
    (is (= [0.75 0 0.25 0.5] (seg "(segment 4 (late 1/4 saw))")))))

(deftest tl2-documented-examples-evaluate
  (testing "every TL2 example in builtin_meta.edn / docs evaluates to a pattern"
    (doseq [code ["(pan sine2 (fast 8 (seq :hh)))"
                  "(->> (fast 16 (seq :hh)) (amp (range 0.3 0.9 (slow 4 saw))))"
                  "(->> (segment 8 (range 1 8 rand)) (scale :minor :c4))"
                  "(pan (range -0.5 0.5 perlin) (fast 4 (seq :hh)))"
                  "(segment 4 saw)"
                  "(synth :saw (seq :c4 :e4))"
                  "(segment 4 (range 200 2000 saw))"
                  "(segment 8 (rangex 100 5000 sine))"
                  "(segment 4 (range2 0 1 sine2))"
                  "(segment 4 (to-bipolar saw))"
                  "(segment 4 (from-bipolar sine2))"
                  "(segment 8 (irand 8))"
                  "(segment 4 (early 7 rand))"
                  "(run 4)"
                  "(segment 4 (signal (fn [t] (* t 2))))"
                  "(segment 4 (steady 1))"
                  "(segment 4 time)"
                  "(segment 4 tri)" "(segment 4 itri)" "(segment 4 isaw)"
                  "(segment 4 cosine)" "(segment 4 isquare)" "(segment 4 square)"
                  "(segment 4 rand2)" "(segment 4 sine2)" "(segment 4 cosine2)"
                  "(segment 4 saw2)" "(segment 4 isaw2)" "(segment 4 tri2)"
                  "(segment 4 itri2)" "(segment 4 square2)" "(segment 4 isquare2)"
                  "(->> saw (segment 4))"
                  "(->> sine (rangex 100 1000) (segment 2))"
                  "(slow 4 saw)"]]
      (let [pat (eval-pat code)]
        (is (core/pattern? pat) code)
        (when (core/pattern? pat)
          (is (seq (mapcat #(core/query pat (core/cycle-span %)) (range 4))) code))))))

;;; ── Phase TL3: time & structure combinators ─────────────────────────

(defn- rs [[n d]] (if (= d 1) (str n) (str n "/" d)))

(defn- tl3-sorted [evs]
  (sort-by (juxt #(core/rat->float (:start (:whole %))) #(str (:value %))) evs))

(defn- tl3 "[value ws we] with rationals as strings, sorted, cycle n"
  [code n]
  (mapv (fn [e] [(:value e) (rs (:start (:whole e))) (rs (:end (:whole e)))])
        (tl3-sorted (cycle-evs (eval-pat code) n))))

(defn- tl3v "values in whole-start order at cycle n" [code n] (mapv first (tl3 code n)))

(defn- tl3o "onset starts as strings at cycle n" [code n]
  (mapv #(rs (:start (:whole %))) (tl3-sorted (onsets (eval-pat code) n))))

(defn- tl3-cycles [code from to] (mapv #(tl3v code %) (range from (inc to))))

(deftest tl3-struct
  (is (= [[:bd "0" "1/4"] [:bd "1/2" "3/4"] [:bd "3/4" "1"]]
         (tl3 "(struct \"t ~ t t\" (pure :bd))" 0)))
  (is (= [[:a "0" "1/3"] [:b "2/3" "1"]]
         (tl3 "(struct (seq true false true) (seq :a :b))" 0)))
  (let [evs (tl3-sorted (cycle-evs (eval-pat "(struct \"t\" (seq :a :b))") 0))]
    (is (= [[:a [0 1] [1 2]] [:b [1 2] [1 1]]]
           (mapv (fn [e] [(:value e) (:start (:part e)) (:end (:part e))]) evs)))
    (is (= [true false] (mapv core/onset? evs))))
  (is (= (tl3 "(struct \"t ~ t t\" (pure :bd))" 0)
         (tl3 "(struct (seq 1 0 1 1) (pure :bd))" 0))))

(deftest tl3-mask
  (is (= ["0" "1/8" "1/2" "5/8" "3/4" "7/8"]
         (tl3o "(mask \"1 0 1 1\" (fast 8 (pure :hh)))" 0)))
  (let [evs (tl3-sorted (cycle-evs (eval-pat "(mask \"t f\" (seq :a :b :c))") 0))]
    (is (= [:a :b] (mapv :value evs)))
    (is (= [[1 3] [1 2]] [(:start (:part (second evs))) (:end (:part (second evs)))]))))

(deftest tl3-invert
  (is (= [false true :_ false] (tl3v "(invert (seq true false :_ 1))" 0)))
  (is (= [[:x "1/4" "1/2"] [:x "3/4" "1"]]
         (tl3 "(struct (invert \"t f t f\") (pure :x))" 0))))

(deftest tl3-euclidean-struct
  (is (= [[:bd "0" "1/8"] [:bd "3/8" "1/2"] [:sd "3/4" "7/8"]]
         (tl3 "(euclidean 3 8 (seq :bd :sd))" 0)))
  (is (= [[:x "1/8" "1/4"] [:x "1/2" "5/8"] [:x "3/4" "7/8"]]
         (tl3 "(euclidean 3 8 (pure :x) 2)" 0)))
  (is (= ["1/8" "1/4" "1/2" "5/8" "7/8"] (tl3o "(euclidean -3 8 (pure :x))" 0)))
  (is (= ["1" "5/4" "11/8" "13/8" "7/4"] (tl3o "(euclidean \"<3 5>\" 8 (pure :x))" 1)))
  (is (= [:x :_ :_ :x :_ :_ :x :_] (tl3v "(euclidean 3 8 :x)" 0)))
  (is (= [:_ :x :_ :_ :x :_ :x :_] (tl3v "(euclidean 3 8 :x 2)" 0))))

(deftest tl3-euclid-legato
  (is (= [["0" "3/8"] ["3/8" "3/4"] ["3/4" "1"]]
         (mapv rest (tl3 "(euclid-legato 3 8 (pure :x))" 0))))
  (is (= [["0" "1/4"] ["1/4" "3/8"] ["3/8" "5/8"] ["5/8" "3/4"] ["3/4" "1"]]
         (mapv rest (tl3 "(euclid-legato 5 8 (pure :x))" 0))))
  (is (= [["1/8" "1/2"] ["1/2" "3/4"] ["3/4" "1"]]
         (mapv rest (tl3 "(euclid-legato-rot 3 8 2 (pure :x))" 0))))
  (is (= 5 (count (tl3 "(euclid-legato \"<3 5>\" 8 (pure :x))" 1))))
  (is (= [["9/8" "3/2"] ["3/2" "7/4"] ["7/4" "2"]]
         (mapv rest (tl3 "(euclid-legato-rot 3 8 \"<0 2>\" (pure :x))" 1)))))

(deftest tl3-ply
  (is (= [[:bd "0" "1/4"] [:bd "1/4" "1/2"] [:sd "1/2" "3/4"] [:sd "3/4" "1"]]
         (tl3 "(ply 2 (seq :bd :sd))" 0)))
  (is (= [[:a "1" "7/6"] [:a "7/6" "4/3"] [:a "4/3" "3/2"]
          [:b "3/2" "5/3"] [:b "5/3" "11/6"] [:b "11/6" "2"]]
         (tl3 "(ply \"<2 3>\" (seq :a :b))" 1)))
  (is (= [[:a "0" "1/2"] [:b "1/2" "3/4"] [:b "3/4" "1"]]
         (tl3 "(ply (seq 1 2) (seq :a :b))" 0)))
  (let [evs (cycle-evs (eval-pat "(ply 2 (~ \"bd sd\"))") 0)
        src (fn [e] (:source e))]
    (is (= 4 (count evs)))
    (is (= 2 (count (distinct (map src evs)))))
    (is (every? some? (map src evs)))))

(deftest tl3-ply-with
  (is (= [[0 "0" "1/6"] [12 "1/6" "1/3"] [24 "1/3" "1/2"]
          [7 "1/2" "2/3"] [19 "2/3" "5/6"] [31 "5/6" "1"]]
         (tl3 "(ply-with 3 (add 12) (seq 0 7))" 0)))
  (is (= 4 (count (tl3 "(ply-with \"<1 2>\" (add 12) (seq 0 7))" 1)))))

(deftest tl3-inside-outside
  (is (= [3 2 1 0 7 6 5 4] (tl3v "(inside 2 rev (seq 0 1 2 3 4 5 6 7))" 0)))
  (is (= [1 0 3 2] (tl3v "(inside \"<1 2>\" rev (seq 0 1 2 3))" 1)))
  (is (= [[:d "0" "1/2"] [:c "1/2" "1"]]
         (tl3 "(outside 2 rev (cat (seq :a :b) (seq :c :d)))" 0)))
  (is (= [[:b "1" "3/2"] [:a "3/2" "2"]]
         (tl3 "(outside 2 rev (cat (seq :a :b) (seq :c :d)))" 1)))
  (is (= [[:b "0" "1/2"] [:a "1/2" "1"]]
         (tl3 "(outside \"<1 2>\" rev (cat (seq :a :b) (seq :c :d)))" 0)))
  (is (= [[:b "1" "3/2"] [:a "3/2" "2"]]
         (tl3 "(outside \"<1 2>\" rev (cat (seq :a :b) (seq :c :d)))" 1))))

(deftest tl3-within
  (let [evs (tl3-sorted (onsets (eval-pat "(within 0 1/2 (fast 2) (seq :a :b :c :d))") 0))]
    (is (= [[:a "0"] [:b "1/8"] [:c "1/4"] [:d "3/8"] [:c "1/2"] [:d "3/4"]]
           (mapv (fn [e] [(:value e) (rs (:start (:whole e)))]) evs)))
    (is (= ["1/8" "1/8" "1/8" "1/8" "1/4" "1/4"]
           (mapv #(rs (core/rat- (:end (:whole %)) (:start (:whole %)))) evs))))
  (is (= [10 11] (tl3v "(within \"<0 1/2>\" 1 (add 10) (seq 0 1))" 0)))
  (is (= [0 11] (tl3v "(within \"<0 1/2>\" 1 (add 10) (seq 0 1))" 1))))

(deftest tl3-swing
  (let [expect ["0" "5/24" "1/4" "11/24" "1/2" "17/24" "3/4" "23/24"]]
    (is (= expect (tl3o "(swing-by 1/3 4 (fast 8 (pure :hh)))" 0)))
    (is (= expect (tl3o "(swing 4 (fast 8 (pure :hh)))" 0)))
    (is (every? #(= [1 8] (core/rat- (:end (:whole %)) (:start (:whole %))))
                (tl3-sorted (onsets (eval-pat "(swing-by 1/3 4 (fast 8 (pure :hh)))") 0)))))
  (is (= ["0" "1/8" "1/4" "3/8" "1/2" "5/8" "3/4" "7/8"]
         (tl3o "(swing-by \"<0 1/3>\" 4 (fast 8 (pure :hh)))" 0)))
  (let [c1 ["1" "29/24" "5/4" "35/24" "3/2" "41/24" "7/4" "47/24"]]
    (is (= c1 (tl3o "(swing-by \"<0 1/3>\" 4 (fast 8 (pure :hh)))" 1)))
    (is (= c1 (tl3o "(swing \"<2 4>\" (fast 8 (pure :hh)))" 1)))))

(deftest tl3-brak
  (is (= [[:a "0" "1/2"] [:b "1/2" "1"]] (tl3 "(brak (seq :a :b))" 0)))
  (is (= [[:a "5/4" "3/2"] [:b "3/2" "7/4"]] (tl3 "(brak (seq :a :b))" 1))))

(deftest tl3-iter
  (is (= [[:b "1" "5/4"] [:c "5/4" "3/2"] [:d "3/2" "7/4"] [:a "7/4" "2"]]
         (tl3 "(iter 4 (seq :a :b :c :d))" 1)))
  (is (= [[:c :d :a :b] [:d :a :b :c] [:a :b :c :d]]
         (mapv #(tl3v "(iter 4 (seq :a :b :c :d))" %) [2 3 4])))
  (is (= [:d :a :b :c] (tl3v "(iter-back 4 (seq :a :b :c :d))" 1)))
  (is (= [:b :c :d :a] (tl3v "(iter-back 4 (seq :a :b :c :d))" 3)))
  (is (= [[:a :b] [:b :c] [:c :d] [:d :a]]
         (tl3-cycles "(iter 2 (cat (seq :a :b) (seq :c :d)))" 0 3)))
  (is (= [:c :d :a :b] (tl3v "(iter \"<4 2>\" (seq :a :b :c :d))" 1)))
  (is (= [:c :d :a :b] (tl3v "(iter-back \"<4 2>\" (seq :a :b :c :d))" 1))))

(deftest tl3-palindrome
  (is (= [[:a :b :c] [:c :b :a]] (tl3-cycles "(palindrome (seq :a :b :c))" 0 1)))
  (is (= [[:a :b] [:b :a] [:c :d] [:d :c]]
         (tl3-cycles "(palindrome (cat (seq :a :b) (seq :c :d)))" 0 3))))

(deftest tl3-rot
  (is (= [[:b "0" "1/4"] [:c "1/4" "1/2"] [:d "1/2" "3/4"] [:a "3/4" "1"]]
         (tl3 "(rot 1 (seq :a :b :c :d))" 0)))
  (is (= [:d :a :b :c] (tl3v "(rot -1 (seq :a :b :c :d))" 0)))
  (is (= [:b :_ :c :a] (tl3v "(rot 1 (seq :a :_ :b :c))" 0)))
  (is (= [:c :a :b] (tl3v "(rot \"<0 1 2>\" (seq :a :b :c))" 2)))
  (let [evs (core/query (eval-pat "(rot 1 (seq :a :b :c :d))") (core/span [1 2] [1 1]))]
    (is (= [[:d "1/2" "3/4"] [:a "3/4" "1"]]
           (mapv (fn [e] [(:value e) (rs (:start (:whole e))) (rs (:end (:whole e)))])
                 (tl3-sorted evs))))))

(deftest tl3-chunk
  (is (= [[10 1 2 3] [0 11 2 3] [0 1 12 3] [0 1 2 13] [10 1 2 3]]
         (tl3-cycles "(chunk 4 (add 10) (seq 0 1 2 3))" 0 4)))
  (is (= [[10 1] [0 11] [14 5] [4 15]]
         (tl3-cycles "(chunk 2 (add 10) (cat (seq 0 1) (seq 4 5)))" 0 3)))
  (is (= [0 11 2 3] (tl3v "(chunk \"<2 4>\" (add 10) (seq 0 1 2 3))" 1))))

(deftest tl3-chunk-back-and-fast-chunk
  (is (= [[10 1 2 3] [0 1 2 13] [0 1 12 3] [0 11 2 3]]
         (tl3-cycles "(chunk-back 4 (add 10) (seq 0 1 2 3))" 0 3)))
  (is (= [0 1 12 13] (tl3v "(chunk-back \"<4 2>\" (add 10) (seq 0 1 2 3))" 1)))
  (is (= [[10 1] [4 15] [10 1] [4 15]]
         (tl3-cycles "(fast-chunk 2 (add 10) (cat (seq 0 1) (seq 4 5)))" 0 3)))
  (is (= [0 1 12 13] (tl3v "(fast-chunk \"<4 2>\" (add 10) (seq 0 1 2 3))" 1))))

(deftest tl3-polymeter
  (is (= [[:a "0" "1/3"] [:d "0" "1/3"] [:b "1/3" "2/3"] [:e "1/3" "2/3"]
          [:c "2/3" "1"] [:d "2/3" "1"]]
         (tl3 "(polymeter [:a :b :c] [:d :e])" 0)))
  (is (= [:e :d :e] (->> (tl3 "(polymeter [:a :b :c] [:d :e])" 1)
                         (filter #(#{:d :e} (first %)))
                         (mapv first))))
  (is (= [:a :b :c :a] (tl3v "(polymeter 4 [:a :b :c])" 0)))
  (is (= [:b :c :a :b] (tl3v "(polymeter 4 [:a :b :c])" 1)))
  (is (= [[:a "0" "1/2"] [:b "1/2" "1"]] (tl3 "(polymeter \"<2 4>\" [:a :b :c :d])" 0)))
  (is (= [:a :b :c :d] (tl3v "(polymeter \"<2 4>\" [:a :b :c :d])" 1))))

(deftest tl3-timecat
  (is (= [[:e3 "0" "3/4"] [:g3 "3/4" "1"]] (tl3 "(timecat [3 :e3] [1 :g3])" 0)))
  (is (= [[:bd "0" "1/6"] [:sd "1/6" "1/3"] [:hh "1/3" "1"]]
         (tl3 "(timecat [1 (seq :bd :sd)] [2 :hh])" 0)))
  (is (= [[:b "1" "3/2"] [:c "3/2" "2"]]
         (tl3 "(timecat [1 (cat (pure :a) (pure :b))] [1 :c])" 1))))

(deftest tl3-first-of-last-of
  (is (= [[10 11] [0 1] [0 1] [0 1] [10 11]]
         (tl3-cycles "(first-of 4 (add 10) (seq 0 1))" 0 4)))
  (is (= (tl3-cycles "(every 4 (add 10) (seq 0 1))" 0 7)
         (tl3-cycles "(first-of 4 (add 10) (seq 0 1))" 0 7)))
  (is (= (mapv #(if (#{3 7} %) [10 11] [0 1]) (range 8))
         (tl3-cycles "(last-of 4 (add 10) (seq 0 1))" 0 7)))
  (is (= [[0 1] [10 11] [0 1] [10 11]]
         (tl3-cycles "(last-of \"<4 2>\" (add 10) (seq 0 1))" 0 3)))
  (is (= [[10 11] [0 1] [10 11] [10 11]]
         (tl3-cycles "(first-of \"<2 3>\" (add 10) (seq 0 1))" 0 3))))

(deftest tl3-curried-forms
  (doseq [code ["(->> (seq :bd :sd) (ply 2) (iter 4) (swing 4))"
                "(->> (seq 0 1 2 3) (chunk 4 (add 10)))"
                "(->> (seq 0 1 2 3) (within 0 1/2 (fast 2)))"
                "(->> (seq :a :b) (struct \"t ~ t t\"))"
                "(->> (fast 8 (pure :hh)) (mask \"1 0 1 1\") (swing-by 1/3 4))"
                "(->> (seq :a :b) (euclid-legato 3 8))"
                "(->> (seq :a :b) (euclid-legato-rot 3 8 1))"
                "(->> (seq 0 1) (ply-with 2 (add 1)) (rot 1) (invert) (brak) (palindrome))"
                "(->> (seq 0 1 2 3) (inside 2 rev) (outside 2 rev) (chunk-back 4 (add 1)) (fast-chunk 4 (add 1)))"
                "(->> (seq 0 1) (first-of 2 (add 1)) (last-of 2 (add 1)) (iter-back 2))"]]
    (let [pat (eval-pat code)]
      (is (core/pattern? pat) code)
      (when (core/pattern? pat)
        (is (seq (mapcat #(cycle-evs pat %) (range 4))) code)))))

(deftest tl3-documented-examples-evaluate
  (doseq [code ["(struct \"t ~ t t\" (pure :bd))"
                "(mask \"1 0 1 1\" (fast 8 (pure :hh)))"
                "(invert (seq true false true))"
                "(euclidean 3 8 :bd)"
                "(euclidean 3 8 (seq :bd :sd))"
                "(euclidean \"<3 5>\" 8 (pure :x) 2)"
                "(euclid-legato 3 8 (pure :x))"
                "(euclid-legato \"<3 5>\" 8 (pure :x))"
                "(euclid-legato-rot 3 8 2 (pure :x))"
                "(ply 2 (seq :bd :sd))"
                "(ply \"<2 3>\" (seq :a :b))"
                "(ply-with 3 (add 12) (seq 0 7))"
                "(ply-with \"<1 2>\" (add 12) (seq 0 7))"
                "(inside 2 rev (seq 0 1 2 3 4 5 6 7))"
                "(inside \"<1 2>\" rev (seq 0 1 2 3))"
                "(outside 2 rev (cat (seq :a :b) (seq :c :d)))"
                "(within 0 1/2 (fast 2) (seq :a :b :c :d))"
                "(within \"<0 1/2>\" 1 (add 10) (seq 0 1))"
                "(swing-by 1/3 4 (fast 8 (pure :hh)))"
                "(swing 4 (fast 8 (pure :hh)))"
                "(swing \"<2 4>\" (fast 8 (pure :hh)))"
                "(brak (seq :a :b))"
                "(iter 4 (seq :a :b :c :d))"
                "(iter \"<4 2>\" (seq :a :b :c :d))"
                "(iter-back 4 (seq :a :b :c :d))"
                "(palindrome (seq :a :b :c))"
                "(rot 1 (seq :a :b :c :d))"
                "(rot \"<0 1 2>\" (seq :a :b :c))"
                "(chunk 4 (add 10) (seq 0 1 2 3))"
                "(chunk \"<2 4>\" (add 10) (seq 0 1 2 3))"
                "(chunk-back 4 (add 10) (seq 0 1 2 3))"
                "(fast-chunk 2 (add 10) (cat (seq 0 1) (seq 4 5)))"
                "(polymeter [:bd :sd :hh] [:c3 :e3])"
                "(polymeter 4 [:a :b :c])"
                "(polymeter \"<2 4>\" [:a :b :c :d])"
                "(timecat [3 :e3] [1 :g3])"
                "(first-of 4 (add 10) (seq 0 1))"
                "(last-of 4 (add 10) (seq 0 1))"
                "(~ \"bd!3 sd . hh hh hh, cp(3,8)\")"
                "(~ \"{a b c}%4\")"
                "(~ \"bd(<3 5>,8,2)\")"]]
    (let [pat (eval-pat code)]
      (is (core/pattern? pat) code)
      (when (core/pattern? pat)
        (is (seq (mapcat #(cycle-evs pat %) (range 8))) code)))))

;;; ── Phase TL3b (Lisp level) ──────────────────────────────────────────

(deftest tl3b-lisp-closures
  (is (= [[0 "0" "1/6"] [12 "1/6" "1/3"] [24 "1/3" "1/2"]
          [7 "1/2" "2/3"] [19 "2/3" "5/6"] [31 "5/6" "1"]]
         (tl3 "(ply-for-each 3 (fn [p i] (add (* i 12) p)) (seq 0 7))" 0)))
  (is (= [[0 "0" "1/2"] [7 "1/2" "1"]]
         (tl3 "(ply-for-each \"<1 2>\" (fn [p i] (add i p)) (seq 0 7))" 0)))
  (is (= [[0 "1" "5/4"] [1 "5/4" "3/2"] [7 "3/2" "7/4"] [8 "7/4" "2"]]
         (tl3 "(ply-for-each \"<1 2>\" (fn [p i] (add i p)) (seq 0 7))" 1)))
  (is (= [[3 "1/2" "3/4"] [4 "3/4" "1"]]
         (tl3 "(filter-events (fn [v] (> v 2)) (seq 1 2 3 4))" 0)))
  (is (= [[:bd "0" "1/3"] [:bd "2/3" "1"]]
         (tl3 "(filter-events (fn [v] (= v :bd)) (seq :bd :sd :bd))" 0)))
  (is (= [[:a "0" "1/4"] [:b "1/4" "1/2"]]
         (tl3 "(filter-when (fn [t] (< t 1/2)) (seq :a :b :c :d))" 0)))
  (is (= [] (tl3 "(filter-when (fn [t] (< t 1/2)) (seq :a :b :c :d))" 1)))
  (is (= [[:a "0" "1/2"]] (tl3 "(filter-when (fn [t] (= t 0)) (seq :a :b))" 0)))
  (is (= [[1 "0" "1/4"] [0 "1/4" "1/2"] [2 "1/2" "3/4"] [3 "3/4" "1"]]
         (tl3 "(chunk-into 2 rev (seq 0 1 2 3))" 0)))
  (is (= [0 1 3 2] (tl3v "(chunk-into 2 rev (seq 0 1 2 3))" 1)))
  (is (= [0 1 2 3] (tl3v "(chunk-into \"<4 2>\" rev (seq 0 1 2 3))" 0)))
  (is (= [0 1 3 2] (tl3v "(chunk-into \"<4 2>\" rev (seq 0 1 2 3))" 1)))
  (is (= [0 1 3 2] (tl3v "(chunk-back-into 2 rev (seq 0 1 2 3))" 0)))
  (is (= [1 0 2 3] (tl3v "(chunk-back-into \"<4 2>\" rev (seq 0 1 2 3))" 1))))

(deftest tl3b-lisp-vectors
  (is (= [[:bd "1/4" "1/2"] [:sd "1/2" "3/4"]] (tl3 "(compress 1/4 3/4 (seq :bd :sd))" 0)))
  (is (= [] (tl3 "(compress 3/4 1/4 (seq :bd :sd))" 0)))
  (is (= [[:a "0" "1/2"] [:b "1/2" "1"]] (tl3 "(compress \"<0 1/2>\" 1 (seq :a :b))" 0)))
  (is (= [[:a "3/2" "7/4"] [:b "7/4" "2"]] (tl3 "(compress \"<0 1/2>\" 1 (seq :a :b))" 1)))
  (is (= [[:b "0" "1/2"] [:c "1/2" "1"]] (tl3 "(zoom 1/4 3/4 (seq :a :b :c :d))" 0)))
  (is (= [[:a "1" "3/2"] [:b "3/2" "2"]] (tl3 "(zoom 0 \"<1 1/2>\" (seq :a :b :c :d))" 1)))
  (is (= [:c :d :a :b :c :d :a :b] (tl3v "(focus 1/4 3/4 (seq :a :b :c :d))" 0)))
  (is (= [[:bd "1" "5/4"] [:sd "5/4" "3/2"]] (tl3 "(fast-gap \"<1 2>\" (seq :bd :sd))" 1)))
  (is (= [6 7 6 7 6 7 6 7] (tl3v "(linger -1/4 (seq 0 1 2 3 4 5 6 7))" 0)))
  (is (= [0 1 2 3 0 1 2 3] (tl3v "(linger \"<1/4 1/2>\" (seq 0 1 2 3 4 5 6 7))" 1)))
  (is (= [:b :c :b :c]
         (mapv #(first (first (tl3 "(ribbon 1 2 (cat (pure :a) (pure :b) (pure :c) (pure :d)))" %)))
               (range 4))))
  (is (= [:b] (tl3v "(ribbon \"<0 1>\" 1 (cat (pure :a) (pure :b) (pure :c)))" 1)))
  (is (= [[:a "1" "5/4"] [:b "5/4" "3/2"]] (tl3 "(trunc \"<1 1/2>\" (seq :a :b :c :d))" 1)))
  (is (= [:c :b :a] (tl3v "(revv (seq :a :b :c))" 0)))
  (is (= [:a :a :b :b]
         (mapv #(first (first (tl3 "(repeat-cycles 2 (cat (pure :a) (pure :b)))" %))) (range 4))))
  (is (= [[:a "1/4" "1/2"] [:b "3/4" "1"]] (tl3 "(press (seq :a :b))" 0)))
  (is (= [[:a "1/8" "1/2"] [:b "5/8" "1"]] (tl3 "(press-by 1/4 (seq :a :b))" 0)))
  (is (= [[:a "5/4" "3/2"] [:b "7/4" "2"]] (tl3 "(press-by \"<0 1/2>\" (seq :a :b))" 1)))
  (is (= [:a :b :e :f] (tl3v "(bite 4 (seq 0 2) (seq :a :b :c :d :e :f :g :h))" 0)))
  (is (= [:a :b :c :d] (tl3v "(bite 4 (seq 0 5) (seq :a :b :c :d :e :f :g :h))" 0)))
  (is (= [:a :b :c :d :e :f :g :h]
         (tl3v "(bite \"<4 2>\" (seq 0 1) (seq :a :b :c :d :e :f :g :h))" 1)))
  (is (= [[:x "0" "1/8"] [:x "3/8" "1/2"] [:x "3/4" "7/8"]] (tl3 "(euclidish 3 8 0 (pure :x))" 0)))
  (is (= [[:x "0" "1/8"] [:x "1/3" "11/24"] [:x "2/3" "19/24"]] (tl3 "(euclidish 3 8 1 (pure :x))" 0)))
  (is (= ["1" "4/3" "5/3"] (tl3o "(euclidish 3 8 \"<0 1>\" (pure :x))" 1)))
  (is (= ["1" "5/4" "11/8" "13/8" "7/4"] (tl3o "(euclidish \"<3 5>\" 8 0 (pure :x))" 1)))
  (is (= [] (tl3 "(bypass 1 (seq :a :b))" 0)))
  (is (= [:a :b] (tl3v "(bypass \"<0 1>\" (seq :a :b))" 0)))
  (is (= [] (tl3v "(bypass \"<0 1>\" (seq :a :b))" 1)))
  (is (= [[:a "0" "1/4"] [:b "1/4" "1/2"]] (tl3 "(bypass \"0 1\" (seq :a :b :c :d))" 0)))
  (is (= [[:bd "0" "1/16"] [:bd "7/16" "1/2"] [:bd "5/8" "11/16"]]
         (tl3 "(beat \"0,7,10\" 16 (pure :bd))" 0)))
  (is (= [[:b "1/2" "3/4"]] (tl3 "(beat 2 4 (seq :a :b))" 0)))
  (is (= [[:bd "5/4" "3/2"]] (tl3 "(beat \"<0 1>\" 4 (pure :bd))" 1)))
  (is (= [[:a "0" "1/4"] [:a "1/4" "1/2"] [:b "1/2" "3/4"] [:b "3/4" "1"]]
         (tl3 "(sew \"t f\" (seq :a :a :a :a) (seq :b :b :b :b))" 0)))
  (is (= [[:a "0" "1"]] (tl3 "(sew \"<t f>\" (pure :a) (pure :b))" 0)))
  (is (= [[:b "1" "2"]] (tl3 "(sew \"<t f>\" (pure :a) (pure :b))" 1)))
  (is (= [[:bd "0" "1/4"] [:sn "1/4" "1/2"] [:bd "1/2" "3/4"] [:bd "3/4" "1"]]
         (tl3 "(stitch \"t f t t\" (pure :bd) (pure :sn))" 0)))
  (is (= [[:a "0" "1/3"] [:b "2/3" "1"]] (tl3 "(stitch \"t ~ f\" (pure :a) (pure :b))" 0)))
  (is (= [[true "0" "1/3"] [false "1/3" "2/3"] [true "2/3" "1"]] (tl3 "(binary 5)" 0)))
  (is (= [[:x "0" "1/3"] [:x "2/3" "1"]] (tl3 "(struct (binary 5) (pure :x))" 0)))
  (is (= [[true "1" "3/2"] [true "3/2" "2"]] (tl3 "(binary \"<5 3>\")" 1)))
  (is (= [false true false true] (tl3v "(binary-n 5 4)" 0)))
  (is (= 16 (count (tl3 "(binary-n 5)" 0))))
  (is (= [true true true true] (tl3v "(binary-n -1 4)" 0)))
  (is (= [] (tl3 "(binary-n 5 0)" 0)))
  (is (= [false false false true] (tl3v "(binary-n 1 \"<2 4>\")" 1))))

(deftest tl3b-lisp-hurry
  (let [evs (cycle-evs (eval-pat "(hurry 2 (seq :bd :sd))") 0)]
    (is (= [{:note :bd :rate 2} {:note :sd :rate 2} {:note :bd :rate 2} {:note :sd :rate 2}]
           (mapv :value (tl3-sorted evs)))))
  (is (= 2 (count (cycle-evs (eval-pat "(hurry \"<1 2>\" (seq :bd :sd))") 0))))
  (is (= 4 (count (cycle-evs (eval-pat "(hurry \"<1 2>\" (seq :bd :sd))") 1)))))

(deftest tl3b-source-preserved
  (let [evs (cycle-evs (eval-pat "(press (~ \"a b\"))") 0)]
    (is (= 2 (count evs)))
    (is (every? :source evs))))

(deftest tl3b-curried-forms
  (doseq [code ["(->> (seq :bd :sd) (fast-gap 2) (linger 1/2) (hurry 2) (zoom 0 1/2))"
                "(->> (seq :bd :sd) (compress 0 1/2) (focus 0 1) (trunc 3/4) (ribbon 0 1))"
                "(->> (seq :a :b) (repeat-cycles 2) (press-by 1/4) (bypass 0) (beat 0 4))"
                "(->> (seq 0 1 2 3) (bite 4 (seq 0 1)) (chunk-into 2 rev) (chunk-back-into 2 rev))"
                "(->> (seq 0 7) (ply-for-each 2 (fn [p i] (add i p))) (euclidish 3 8 1/2))"
                "(->> (seq 1 2 3 4) (filter-events (fn [v] (> v 1))) (filter-when (fn [t] (< t 1))) (revv) (press))"]]
    (let [pat (eval-pat code)]
      (is (core/pattern? pat) code)
      (when (core/pattern? pat)
        (is (seq (mapcat #(cycle-evs pat %) (range 4))) code)))))

(deftest tl3b-documented-examples-evaluate
  (doseq [code [
                "(compress 1/4 3/4 (seq :bd :sd))"
                "(compress \"<0 1/2>\" 1 (seq :a :b))"
                "(zoom 1/4 3/4 (seq :a :b :c :d))"
                "(zoom 0 \"<1 1/2>\" (seq :a :b :c :d))"
                "(focus 1/4 3/4 (seq :a :b :c :d))"
                "(focus 0 \"<1 1/2>\" (seq :a :b))"
                "(fast-gap 2 (seq :bd :sd))"
                "(fast-gap \"<1 2>\" (seq :a :b))"
                "(linger 1/4 (seq 0 1 2 3 4 5 6 7))"
                "(linger \"<1/4 1/2>\" (seq 0 1 2 3 4 5 6 7))"
                "(bite 4 (seq 0 2) (seq :a :b :c :d :e :f :g :h))"
                "(bite \"<4 2>\" (seq 0 1) (seq :a :b :c :d :e :f :g :h))"
                "(ribbon 1 2 (cat (pure :a) (pure :b) (pure :c) (pure :d)))"
                "(ribbon \"<0 1>\" 1 (cat (pure :a) (pure :b) (pure :c)))"
                "(hurry 2 (seq :bd :sd))"
                "(hurry \"<1 2>\" (seq :bd :sd))"
                "(revv (seq :a :b :c))"
                "(repeat-cycles 2 (cat (seq :a) (seq :b)))"
                "(repeat-cycles \"<1 2>\" (cat (pure :a) (pure :b) (pure :c)))"
                "(trunc 3/4 (seq :a :b :c :d))"
                "(trunc \"<1 1/2>\" (seq :a :b :c :d))"
                "(press (seq :a :b))"
                "(press-by 1/4 (seq :a :b))"
                "(press-by \"<0 1/2>\" (seq :a :b))"
                "(chunk-into 2 rev (seq 0 1 2 3))"
                "(chunk-into \"<4 2>\" rev (seq 0 1 2 3))"
                "(chunk-back-into 2 rev (seq 0 1 2 3))"
                "(chunk-back-into \"<4 2>\" rev (seq 0 1 2 3))"
                "(euclidish 3 8 1/2 (pure :x))"
                "(euclidish 3 8 \"<0 1>\" (pure :x))"
                "(ply-for-each 3 (fn [p i] (add (* i 12) p)) (seq 0 7))"
                "(ply-for-each \"<1 2>\" (fn [p i] (add i p)) (seq 0 7))"
                "(filter-events (fn [v] (> v 2)) (seq 1 2 3 4))"
                "(filter-when (fn [t] (< t 1/2)) (seq :a :b :c :d))"
                "(bypass 0 (seq :a :b))"
                "(bypass \"<0 1>\" (seq :a :b))"
                "(beat \"0,7,10\" 16 (pure :bd))"
                "(beat \"<0 1>\" 4 (pure :bd))"
                "(sew \"t f\" (seq :a :a :a :a) (seq :b :b :b :b))"
                "(sew \"<t f>\" (pure :a) (pure :b))"
                "(stitch \"t f t t\" (pure :bd) (pure :sn))"
                "(struct (binary 5) (pure :x))"
                "(struct (binary \"<5 3>\") (pure :x))"
                "(struct (binary-n 5 4) (pure :x))"
                "(binary-n 1 \"<2 4>\")"]]
    (let [pat (eval-pat code)]
      (is (core/pattern? pat) code)
      (when (core/pattern? pat)
        (is (seq (mapcat #(cycle-evs pat %) (range 8))) code)))))
