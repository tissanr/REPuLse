(ns repulse.inserts-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [repulse.inserts :as ins]))

(defn- near? [a b] (< (Math/abs (- a b)) 1e-6))

(deftest insert-params-nil-without-inserts
  (is (nil? (ins/insert-params {:note :c4 :amp 0.5})))
  (is (nil? (ins/insert-params :bd)))
  (is (nil? (ins/insert-params {:note :c4 :resonance 4})) "Q alone has no effect")
  (is (nil? (ins/insert-params {:note :c4 :shape 0 :crush 16})) "off values count as absent")
  (is (nil? (ins/insert-params {:note :c4 :gain 0.5 :velocity 0.3})) "gain is not an insert"))

(deftest insert-params-defaults
  (is (= {:lpf 800.0 :resonance 0.707 :hpf nil :hpq 0.707 :bpf nil :bpq 1.0
          :shape 0.0 :crush 16.0 :lp-env nil :hp-env nil :bp-env nil
          :vowel nil :coarse nil :postgain 1.0}
         (ins/insert-params {:note :c4 :lpf 800}))))

(deftest insert-params-clamps
  (let [p (ins/insert-params {:lpf 5 :resonance 100 :hpf 99999 :hpq 0
                              :bpf 1000 :bpq -3 :shape 2 :crush 0})]
    (is (= 20.0 (:lpf p)))
    (is (= 30.0 (:resonance p)))
    (is (= 20000.0 (:hpf p)))
    (is (= 0.1 (:hpq p)))
    (is (= 0.1 (:bpq p)))
    (is (= 0.99 (:shape p)))
    (is (= 1.0 (:crush p)))))

(deftest insert-params-rationals-and-junk
  (is (= 500.0 (:lpf (ins/insert-params {:lpf [1000 2]}))))
  (is (nil? (ins/insert-params {:lpf :high})) "non-numbers are ignored")
  (is (= 4.0 (:crush (ins/insert-params {:crush 4})))))

(deftest amp-factor-model
  (is (= 1.0 (ins/amp-factor {:note :c4})))
  (is (= 1.0 (ins/amp-factor :bd)))
  (is (= 0.5 (ins/amp-factor {:gain 0.5})))
  (is (= 1.0 (ins/amp-factor {:gain 2 :velocity 0.5})))
  (is (= 2.0 (ins/amp-factor {:gain 9})) "gain clamps to 2")
  (is (= 1.0 (ins/amp-factor {:velocity 3})) "velocity clamps to 1")
  (is (= 0.0 (ins/amp-factor {:gain -1}))))

(deftest shape-curve-formula
  (let [c (ins/shape-curve 0.5 5)            ; k = 2
        k 2.0]
    (is (= 5 (.-length c)))
    (is (near? -1.0 (aget c 0)) "ends map to ±1")
    (is (near? 0.0 (aget c 2)))
    (is (near? 1.0 (aget c 4)))
    (is (near? (/ (* (+ 1 k) 0.5) (+ 1 (* k 0.5))) (aget c 3)))))

(deftest shape-curve-zero-is-identity
  (let [c (ins/shape-curve 0 9)]
    (doseq [i (range 9)]
      (is (near? (- (/ (* 2.0 i) 8) 1) (aget c i))))))

(deftest crush-curve-staircase
  (let [c (ins/crush-curve 4 65536)
        vals (set (array-seq c))]
    (is (<= (count vals) 17) "4 bits → levels 8 → values −1, −7/8 … 1")
    (is (every? #(near? (* 8 %) (Math/round (* 8 %))) vals)))
  (is (near? 0.2 (aget (ins/crush-curve 16 11) 6)) "bits 16 bypass — identity curve"))

(deftest crush-levels-bypass
  (is (nil? (ins/crush-levels 16)))
  (is (= 8 (ins/crush-levels 4))))

(deftest q-conversion
  (is (near? 0 (ins/q->webaudio "lowpass" 1)))
  (is (near? 12.0412 (ins/q->webaudio "highpass" 4)) "20·log10 4")
  (is (= 5 (ins/q->webaudio "bandpass" 5))))

(deftest wasm-slots-order
  (is (= [800.0 4.0 0 0.707 0 1.0 0.0 16.0]
         (ins/wasm-slots (ins/insert-params {:lpf 800 :resonance 4})))))

;;; ── Phase TL7c ───────────────────────────────────────────────────────

(def ^:private env {:attack 0.1 :decay 0.2 :sustain 0.5 :release 0.4})

(deftest env-params-defaults-and-clamps
  (is (nil? (ins/env-params {:lp-attack 0.5} :lp-env "lp" 10)) "depth 0/absent = off")
  (is (= {:depth 4.0 :attack 0.001 :decay 0.2 :sustain 0.0 :release 0.1}
         (ins/env-params {:lp-env 4} :lp-env "lp" 10)))
  (is (= {:depth -10.0 :attack 10.0 :decay 0.0 :sustain 1.0 :release 0.0}
         (ins/env-params {:hp-env -99 :hp-attack 99 :hp-decay -1 :hp-sustain 3 :hp-release -2}
                         :hp-env "hp" 10)))
  (is (= 48.0 (:depth (ins/env-params {:penv 99} :penv "p" 48)))))

(deftest insert-params-envelopes-need-their-filter
  (is (nil? (ins/insert-params {:note :c4 :lp-env 4})) "envelope without lpf has no effect")
  (is (nil? (:hp-env (ins/insert-params {:lpf 300 :hp-env 4}))))
  (is (= 4.0 (get-in (ins/insert-params {:lpf 300 :lp-env 4}) [:lp-env :depth])))
  (is (= 2.0 (get-in (ins/insert-params {:bpf 900 :bp-env 2}) [:bp-env :depth]))))

(deftest env-value-breakpoints
  (testing "gate after attack + decay"
    (doseq [[t want] [[0 0] [0.05 0.5] [0.1 1] [0.2 0.75] [0.3 0.5] [1.0 0.5]
                      [1.2 0.25] [1.4 0] [2 0]]]
      (is (near? want (ins/env-value env 1.0 t)) (str "t=" t))))
  (testing "gate inside the attack"
    (is (near? 0.5 (ins/env-value env 0.05 0.05)))
    (is (near? 0.25 (ins/env-value env 0.05 0.25)))
    (is (near? 0 (ins/env-value env 0.05 0.45))))
  (testing "infinite gate holds the sustain"
    (is (near? 0.5 (ins/env-value env nil 100))))
  (testing "zero attack / decay / release jump"
    (let [e {:attack 0 :decay 0 :sustain 0.3 :release 0}]
      (is (near? 0.3 (ins/env-value e 1 0)))
      (is (near? 0 (ins/env-value e 1 1))))))

(deftest env-points-schedule
  (is (= [[0 0.0 :set] [0.1 1.0 :ramp] [0.30000000000000004 0.5 :ramp]]
         (ins/env-points env nil)))
  (is (= [[0 0.0 :set] [0.1 1.0 :ramp] [0.30000000000000004 0.5 :ramp]
          [1.0 0.5 :ramp] [1.4 0.0 :ramp]]
         (ins/env-points env 1.0)))
  (let [[p0 p1 p2 :as pts] (ins/env-points env 0.05)]
    (is (= 3 (count pts)) "gate inside the attack cuts the later points")
    (is (= [0 0.0 :set] p0))
    (is (= 0.05 (first p1)))
    (is (near? 0.5 (second p1)))
    (is (= [(+ 0.05 0.4) 0.0 :ramp] p2)))
  (is (= [[0 1.0 :set] [0 0.3 :set] [1 0.3 :ramp] [1 0.0 :set]]
         (ins/env-points {:attack 0 :decay 0 :sustain 0.3 :release 0} 1))))

(deftest env-points-match-env-value
  ;; Between the points the schedule is a straight line; check its ends.
  (doseq [gate [nil 0.05 0.25 1.0]
          [t e] (ins/env-points env gate)]
    (when (pos? t)
      (is (near? e (ins/env-value env gate (- t 1e-9))) (str "gate " gate " t " t)))))

(deftest effective-depth-clamps-the-peak
  (is (near? 4 (ins/effective-depth 300 4 44100)))
  (is (near? (/ (Math/log (/ 20000 1000)) (Math/log 2)) (ins/effective-depth 1000 10 44100)))
  (is (near? (/ (Math/log (/ (* 0.49 22050) 1000)) (Math/log 2))
             (ins/effective-depth 1000 10 22050)) "below Nyquist at low sample rates")
  (is (near? -5.643856189774724 (ins/effective-depth 1000 -10 44100)) "20 Hz floor"))

(deftest djf-folds-into-filters
  (is (= {} (ins/djf-filters 0.5)))
  (is (near? 20 (:lpf (ins/djf-filters 0))))
  (is (near? 20000 (:hpf (ins/djf-filters 1))))
  (is (near? (* 20 (Math/pow 1000 0.4)) (:lpf (ins/djf-filters 0.2))))
  (is (nil? (ins/insert-params {:note :c4 :djf 0.5})) "0.5 = off")
  (is (near? (* 20 (Math/pow 1000 0.4)) (:lpf (ins/insert-params {:djf 0.2}))))
  (is (= 800.0 (:lpf (ins/insert-params {:djf 0.2 :lpf 800}))) "explicit lpf wins")
  (is (some? (:hpf (ins/insert-params {:djf 0.8})))))

(deftest vowel-coarse-postgain
  (is (= :a (:vowel (ins/insert-params {:vowel :a}))))
  (is (= :o (:vowel (ins/insert-params {:vowel "o"}))))
  (is (nil? (ins/insert-params {:vowel :x})) "unknown vowel is ignored")
  (is (= [1 2 3 4 5 0] (mapv ins/vowel-index [:a :e :i :o :u :y])))
  (is (= 8 (:coarse (ins/insert-params {:coarse 8.4}))))
  (is (= 64 (:coarse (ins/insert-params {:coarse 500}))))
  (is (nil? (ins/insert-params {:coarse 1})) "coarse 1 = off")
  (is (= 0.5 (:postgain (ins/insert-params {:postgain 0.5}))))
  (is (= 4.0 (:postgain (ins/insert-params {:postgain 9}))))
  (is (nil? (ins/insert-params {:postgain 1})) "postgain 1 = off"))

(deftest pitch-params-model
  (is (nil? (ins/pitch-params {:note :c4 :vib-mod 1})) "vib-mod without vib")
  (is (nil? (ins/pitch-params {:note :c4 :p-decay 0.3})) "times without depth")
  (is (= {:penv nil :vib 6.0 :vib-mod 0.5} (ins/pitch-params {:vib 6})))
  (is (= 12.0 (get-in (ins/pitch-params {:penv 12}) [:penv :depth]))))

(deftest nudge-clamps
  (is (= 0.0 (ins/nudge {:note :c4})))
  (is (= 0.0 (ins/nudge :bd)))
  (is (= 0.02 (ins/nudge {:nudge 0.02})))
  (is (= -1.0 (ins/nudge {:nudge -5}))))

(deftest tl7c-slots
  (is (false? (ins/tl7c? (ins/insert-params {:lpf 800}) nil)))
  (is (true? (ins/tl7c? (ins/insert-params {:lpf 800 :lp-env 2}) nil)))
  (is (true? (ins/tl7c? nil (ins/pitch-params {:vib 5}))))
  (is (true? (ins/tl7c? (ins/insert-params {:postgain 0.5}) nil)))
  (let [s (ins/wasm-slots-tl7c (ins/insert-params {:lpf 300 :lp-env 4 :lp-decay 0.2
                                                    :vowel :i :coarse 8 :postgain 2})
                               (ins/pitch-params {:penv 12 :vib 5 :vib-mod 1})
                               0.5)]
    (is (= 26 (count s)) "slots 15–40")
    (is (= 0.5 (nth s 0)) "gate")
    (is (= [4.0 0.001 0.2 0.0 0.1] (subvec s 1 6)) "lp env")
    (is (= ins/env-off (subvec s 6 11)) "hp env off")
    (is (= ins/env-off (subvec s 11 16)) "bp env off")
    (is (= [12.0 0.001 0.2 0.0 0.1] (subvec s 16 21)) "pitch env")
    (is (= [5.0 1.0 3 8 2.0] (subvec s 21 26)) "vib vib-mod vowel coarse postgain"))
  (is (= 0 (first (ins/wasm-slots-tl7c nil (ins/pitch-params {:vib 5}) nil))) "no gate = 0"))

(deftest gain-factor-ignores-velocity
  (is (= 1.0 (ins/gain-factor :bd)))
  (is (= 1.0 (ins/gain-factor {:velocity 0.2})))
  (is (= 0.5 (ins/gain-factor {:gain 0.5 :velocity 0.2})))
  (is (= 2.0 (ins/gain-factor {:gain 9}))))
