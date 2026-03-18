(ns repulse.lisp.eval-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [repulse.core :as core]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]))

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
