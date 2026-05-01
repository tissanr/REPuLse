(ns repulse.lisp.eval-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [repulse.core :as core]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.lisp.reader :as reader]))

;;; Helpers

(defn- make-test-env []
  (leval/make-env (fn [] nil) (fn [_] nil)))

(def ^:private one-cycle
  (core/span (core/int->rat 0) (core/int->rat 1)))

;;; ── seq source ───────────────────────────────────────────────────────

(deftest lisp-seq-attaches-source
  (testing "seq in Lisp attaches :source to events from keyword positions"
    (let [code "(seq :bd :sd)"
          ;;    0123456789012
          ;;         ^5   ^9
          env  (make-test-env)
          pat  (:result (lisp/eval-string code env))
          evs  (core/query pat one-cycle)]
      (is (= 2 (count evs)))
      ;; :bd starts at position 5 (after "(seq "), ends at 8
      (is (= {:from 5 :to 8} (:source (first evs))))
      ;; :sd starts at 9, ends at 12
      (is (= {:from 9 :to 12} (:source (second evs)))))))

;;; ── ->> source propagation ───────────────────────────────────────────

(deftest lisp-thread-last-preserves-source
  (testing "->> passes :source through param chains"
    (let [code "(->> (seq :c4 :e4 :g4) (amp 0.7) (attack 0.02) (decay 0.5))"
          ;;    0         1         2         3         4         5
          ;;    0123456789012345678901234567890123456789012345678901234567890
          ;;              ^10  ^14  ^18
          env  (make-test-env)
          pat  (:result (lisp/eval-string code env))
          evs  (core/query pat one-cycle)]
      (is (= 3 (count evs)))
      ;; :c4 is at positions 10-13
      (is (= {:from 10 :to 13} (:source (nth evs 0))))
      ;; :e4 is at positions 14-17
      (is (= {:from 14 :to 17} (:source (nth evs 1))))
      ;; :g4 is at positions 18-21
      (is (= {:from 18 :to 21} (:source (nth evs 2)))))))

(deftest lisp-amp-preserves-source
  (testing "(amp v pat) preserves :source in Lisp evaluator"
    (let [code "(amp 0.7 (seq :c4 :e4))"
          ;;    0         1         2
          ;;    0123456789012345678901 2
          ;;    (amp 0.7 (seq :c4 :e4))
          ;;                  ^14  ^18
          env  (make-test-env)
          pat  (:result (lisp/eval-string code env))
          evs  (core/query pat one-cycle)]
      (is (= 2 (count evs)))
      ;; :c4 starts at 14 (after "(amp 0.7 (seq "), ends at 17
      (is (= {:from 14 :to 17} (:source (first evs))))
      ;; :e4 starts at 18, ends at 21
      (is (= {:from 18 :to 21} (:source (second evs)))))))

;;; ── choose / wchoose source propagation ─────────────────────────────

(deftest lisp-choose-attaches-source
  (testing "choose attaches :source of the chosen element to the event"
    (let [code "(choose [:a :b :c :d])"
          ;;    0         1         2
          ;;    0123456789012345678901
          ;;    (choose [:a :b :c :d])
          ;;              ^9 ^12^15^18
          env  (make-test-env)
          pat  (:result (lisp/eval-string code env))
          srcs #{;; :a at 9-11, :b at 12-14, :c at 15-17, :d at 18-20
                 {:from 9 :to 11} {:from 12 :to 14}
                 {:from 15 :to 17} {:from 18 :to 20}}]
      (is (some? pat))
      ;; Every chosen event must carry a :source pointing to one of the keywords
      (doseq [cy (range 20)]
        (let [ev (first (core/query pat (core/cycle-span cy)))]
          (is (contains? srcs (:source ev))))))))

;;; ── partial application of combinators ───────────────────────────────

(deftest fast-partial-returns-transform-fn
  (testing "(fast n) returns a function, not a pattern"
    (let [env (make-test-env)
          ;; (fast 2) should return a fn, not crash
          f   (:result (lisp/eval-string "(fast 2)" env))]
      (is (fn? f)))))

(deftest jux-fast-partial
  (testing "(jux (fast 2) pat) does not crash and produces events"
    (let [env  (make-test-env)
          pat  (:result (lisp/eval-string "(jux (fast 2) (seq :c4 :e4))" env))
          evs  (core/query pat (core/cycle-span 0))]
      (is (some? pat))
      (is (pos? (count evs))))))

(deftest transpose-partial-returns-transform-fn
  (testing "(transpose n) returns a function"
    (let [env (make-test-env)
          f   (:result (lisp/eval-string "(transpose 12)" env))]
      (is (fn? f)))))

;;; ── Phase M: loop/recur ─────────────────────────────────────────────

(deftest loop-basic
  (testing "(loop [i 0] ...) counts to 10"
    (let [env (make-test-env)
          r   (:result (lisp/eval-string "(loop [i 0] (if (>= i 10) i (recur (+ i 1))))" env))]
      (is (= 10 r)))))

(deftest loop-multiple-bindings
  (testing "loop with two bindings sums 0..10"
    (let [env (make-test-env)
          r   (:result (lisp/eval-string "(loop [i 0 s 0] (if (> i 10) s (recur (+ i 1) (+ s i))))" env))]
      (is (= 55 r)))))

(deftest loop-returns-body-value
  (testing "loop returns the last expression in body when no recur"
    (let [env (make-test-env)
          r   (:result (lisp/eval-string "(loop [x 42] x)" env))]
      (is (= 42 (leval/unwrap r))))))

(deftest loop-large-count-no-overflow
  (testing "loop handles 10000 iterations without stack overflow"
    (let [env (make-test-env)
          r   (:result (lisp/eval-string "(loop [i 0] (if (>= i 10000) i (recur (+ i 1))))" env))]
      (is (= 10000 r)))))

(deftest loop-runaway-returns-eval-error
  (testing "runaway loop/recur stops at the evaluator budget"
    (let [env (make-test-env)
          r   (lisp/eval-string "(loop [i 0] (recur (+ i 1)))" env)]
      (is (lisp/eval-error? r))
      (is (= "loop exceeded 10000 iterations" (:message r))))))

;;; ── Phase M: defn ────────────────────────────────────────────────────

(defn- eval-seq
  "Evaluate multiple forms sharing one env. Returns result of last form."
  [src]
  (let [env (make-test-env)
        forms (reader/read-all src)]
    (last (map #(leval/eval-form % env) forms))))

(deftest defn-basic
  (testing "(defn double [x] (* x 2)) works"
    (is (= 6 (eval-seq "(defn double [x] (* x 2)) (double 3)")))))

(deftest defn-recursive
  (testing "(defn fact [n] ...) computes factorial"
    (is (= 120 (eval-seq "(defn fact [n] (if (<= n 1) 1 (* n (fact (- n 1))))) (fact 5)")))))

;;; ── Phase M: defmacro + quasiquote ──────────────────────────────────

(deftest defmacro-simple-wrap
  (testing "macro that doubles a value by expanding to (+ x x)"
    ;; The macro receives the unevaluated argument form (a number literal 5)
    ;; and expands to a list that is then evaluated: (+ 5 5) → 10
    (is (= 10 (eval-seq "(defmacro add-self [x] (list (symbol \"+\") x x)) (add-self 5)")))))

(deftest defmacro-quasiquote-pattern
  (testing "macro using backtick/unquote expanding to a pattern combinator"
    (let [env (make-test-env)
          ;; (swing 0.04 pat) expands to (off 0.04 identity pat)
          code "(defmacro swing [amount pat] `(off ~amount identity ~pat)) (swing 0.04 (seq :bd :sd))"
          r    (:result (lisp/eval-string code env))]
      (is (some? r))
      (is (fn? (:query r))))))

(deftest quasiquote-basic
  (testing "quasiquote with unquote inserts the evaluated value"
    (let [env (make-test-env)
          r   (:result (lisp/eval-string "(let [x 42] (quasiquote (unquote x)))" env))]
      (is (= 42 (leval/unwrap r))))))

(deftest quasiquote-splice
  (testing "quasiquote with splice-unquote splices a list into the template"
    (let [env (make-test-env)
          r   (:result (lisp/eval-string
                         "(let [xs (list 2 3 4)] (quasiquote (1 (splice-unquote xs) 5)))"
                         env))]
      ;; result is a list; elements may be SourcedVals from the template positions
      (is (= '(1 2 3 4 5) (map leval/unwrap r))))))

;;; ── Phase M: rational literals ───────────────────────────────────────

(deftest rational-literal-reads
  (testing "1/4 parses as a two-element vector [1 4]"
    (let [forms (reader/read-all "1/4")
          v     (leval/unwrap (first forms))]
      (is (= [1 4] v)))))

(deftest rational-used-in-arithmetic
  (testing "rational pair coerces to float in arithmetic"
    (let [env (make-test-env)
          r   (:result (lisp/eval-string "(* 1/4 4)" env))]
      (is (= 1.0 r)))))

(deftest rational-in-slow
  (testing "(slow 1/4 (seq :bd :sd)) does not crash"
    (let [env (make-test-env)
          r   (:result (lisp/eval-string "(slow 1/4 (seq :bd :sd))" env))]
      (is (some? r))
      (is (fn? (:query r))))))

;;; ── Phase M: collection helpers ──────────────────────────────────────

(deftest conj-test
  (testing "(conj [1 2] 3) → [1 2 3]"
    (let [env (make-test-env)
          r   (:result (lisp/eval-string "(conj [1 2] 3)" env))]
      (is (= [1 2 3] (mapv leval/unwrap r))))))

(deftest apply-test
  (testing "(apply + (list 1 2 3)) → 6"
    (let [env (make-test-env)
          r   (:result (lisp/eval-string "(apply + (list 1 2 3))" env))]
      (is (= 6 r)))))

(deftest range-test
  (testing "(range 5) → (0 1 2 3 4)"
    (let [env (make-test-env)
          r   (:result (lisp/eval-string "(vec (range 5))" env))]
      (is (= [0 1 2 3 4] r)))))

(deftest apply-seq-from-list
  (testing "(apply seq (list :bd :sd :hh)) creates a pattern"
    (let [env (make-test-env)
          r   (:result (lisp/eval-string "(apply seq (list :bd :sd :hh))" env))]
      (is (some? r))
      (is (fn? (:query r))))))

;;; ── Phase M: defn + loop integration ────────────────────────────────

(deftest defn-with-loop
  (testing "defn using loop to build a note list"
    (let [env  (make-test-env)
          code "(defn gen [n]
                  (loop [notes [] i 0]
                    (if (>= i n)
                      (apply seq notes)
                      (recur (conj notes (+ 200 (* i 30))) (+ i 1)))))
                (gen 4)"
          r    (:result (lisp/eval-string code env))]
      (is (some? r))
      (is (fn? (:query r)))
      ;; pattern should have 4 events per cycle
      (let [evs (core/query r one-cycle)]
        (is (= 4 (count evs)))))))

;;; ── R0: correctness fixes ───────────────────────────────────────────

(deftest and-short-circuits
  (testing "(and) returns true"
    (is (= true (leval/unwrap (:result (lisp/eval-string "(and)" (make-test-env)))))))
  (testing "(and false ...) does not evaluate later operands"
    (is (= false (leval/unwrap (:result (lisp/eval-string "(and false undefined-sym)" (make-test-env)))))))
  (testing "(and ...) returns the last truthy value"
    (is (= :c (leval/unwrap (:result (lisp/eval-string "(and :a :b :c)" (make-test-env))))))))

(deftest or-short-circuits
  (testing "(or) returns nil"
    (is (= nil (:result (lisp/eval-string "(or)" (make-test-env))))))
  (testing "(or truthy ...) does not evaluate later operands"
    (is (= :found (leval/unwrap (:result (lisp/eval-string "(or :found undefined-sym)" (make-test-env)))))))
  (testing "nested combinations preserve short-circuit behaviour"
    (is (= :ok
           (leval/unwrap (:result (lisp/eval-string "(or nil (and true :ok) undefined-sym)" (make-test-env))))))
    (is (= false
           (leval/unwrap (:result (lisp/eval-string "(and true (or nil false) undefined-sym)" (make-test-env))))))))

(deftest eval-error-map-is-data
  (testing "a user map with :error is returned as plain data"
    (let [env    (make-test-env)
          result (lisp/eval-string "(do (def err {:error \"x\"}) err)" env)]
      (is (map? (:result result)))
      (is (= {:error "x"} (:result result))))))

(deftest eval-string-single-form-eval-error-bubbles
  (testing "a single-form EvalError is returned directly"
    (let [env    (assoc (make-test-env) "fail-now" (fn [] (lisp/eval-error "nope")))
          result (lisp/eval-string "(fail-now)" env)]
      (is (lisp/eval-error? result))
      (is (= "nope" (:message result))))))

(deftest eval-string-final-form-eval-error-bubbles
  (testing "a final-form EvalError in a multi-form program is returned directly"
    (let [env    (assoc (make-test-env)
                        "fail-later" (fn [] (lisp/eval-error "nope")))
          result (lisp/eval-string "(def ok 1)\n(fail-later)" env)]
      (is (lisp/eval-error? result))
      (is (= "nope" (:message result))))))
