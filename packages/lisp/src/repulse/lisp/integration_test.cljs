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
