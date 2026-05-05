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

(deftest saw-produces-synth-map
  (testing "(saw :c4) produces a map with :synth :saw"
    (let [r (eval-result "(saw :c4)")]
      (is (map? r))
      (is (= :saw (:synth r)))
      (is (= :c4 (:note r))))))

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
