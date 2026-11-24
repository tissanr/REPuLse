(ns repulse.kits-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.kits :as kits]))

(def ^:private layers
  (mapv (fn [v] {:v v :gain 1.0 :urls ["a" "b" "c"]}) [0.3 0.6 0.85 1.0]))

(defn- entry [ls & {:as more}]
  (merge {"layers" ls} more))

(def ^:private good
  [{"v" 0.5 "gain" 1.4 "files" ["s1.ogg" "s2.ogg"]}
   {"v" 1 "files" ["h1.ogg"]}])

(deftest parse-entry-valid
  (let [e (kits/parse-entry (entry good "choke" "hat") "kits/")]
    (is (nil? (:error e)))
    (is (= [0.5 1.0] (mapv :v (:layers e))))
    (is (= [1.4 1.0] (mapv :gain (:layers e))) "gain defaults to 1")
    (is (= ["kits/s1.ogg" "kits/s2.ogg"] (:urls (first (:layers e)))) "_base applied")
    (is (= "hat" (:choke e))))
  (is (nil? (:choke (kits/parse-entry (entry good) ""))))
  (is (nil? (kits/parse-entry ["a.wav"] "")) "array values are not layered entries")
  (is (nil? (kits/parse-entry {"foo" 1} "")) "objects without layers are ignored"))

(deftest parse-entry-invalid
  (doseq [[why bad] {"empty layers"      (entry [])
                     "layers not vector" {"layers" "x"}
                     "descending v"      (entry [{"v" 1 "files" ["a"]} {"v" 0.5 "files" ["b"]}])
                     "equal v"           (entry [{"v" 0.5 "files" ["a"]} {"v" 0.5 "files" ["b"]} {"v" 1 "files" ["c"]}])
                     "last v not 1"      (entry [{"v" 0.4 "files" ["a"]} {"v" 0.9 "files" ["b"]}])
                     "v zero"            (entry [{"v" 0 "files" ["a"]} {"v" 1 "files" ["b"]}])
                     "v above 1"         (entry [{"v" 1.5 "files" ["a"]}])
                     "gain 0"            (entry [{"v" 1 "gain" 0 "files" ["a"]}])
                     "gain 5"            (entry [{"v" 1 "gain" 5 "files" ["a"]}])
                     "no files"          (entry [{"v" 1 "files" []}])
                     "non-string file"   (entry [{"v" 1 "files" ["a" 2]}])
                     "choke not string"  (entry good "choke" 3)}]
    (testing why
      (is (string? (:error (kits/parse-entry bad "")))))))

(deftest pick-layer-bounds
  (is (= 0 (first (kits/pick-layer layers 0.1))))
  (is (= 0 (first (kits/pick-layer layers 0.3))) "v is the layer's upper bound")
  (is (= 1 (first (kits/pick-layer layers 0.31))))
  (is (= 1 (first (kits/pick-layer layers 0.6))))
  (is (= 3 (first (kits/pick-layer layers 0.9))))
  (is (= 3 (first (kits/pick-layer layers 1.0))))
  (is (= 3 (first (kits/pick-layer layers nil))) "absent velocity = top layer")
  (is (= 3 (first (kits/pick-layer layers 7))) "clamped"))

(deftest layer-scale-formulas
  (is (< (Math/abs (- 0.5 (kits/layer-scale layers 0 0.15))) 1e-9))
  (is (< (Math/abs (- 1.0 (kits/layer-scale layers 0 0.3))) 1e-9))
  (is (< (Math/abs (- 0.6 (kits/layer-scale layers 1 0.3000001))) 1e-4))
  (is (< (Math/abs (- 1.0 (kits/layer-scale layers 1 0.6))) 1e-9))
  (is (< (Math/abs (- 1.0 (kits/layer-scale layers 3 1.0))) 1e-9)))

(deftest level-multiplies
  (let [ls [{:v 0.5 :gain 2.0 :urls ["a"]} {:v 1.0 :gain 1.0 :urls ["b"]}]]
    (is (< (Math/abs (- (* 0.5 2.0 0.5) (kits/level ls 0 0.25 0.5))) 1e-9))))

(deftest pick-take-rules
  (testing "one take"
    (is (every? zero? (for [i (range 20)] (kits/pick-take 1 0 [i 16] "K_bd")))))
  (testing "k = 3 over 100 onsets: never the previous take, all takes used, deterministic"
    (let [run #(loop [i 0 last nil acc []]
                 (if (= i 100)
                   acc
                   (let [r (kits/pick-take 3 last [i 16] "K_bd")]
                     (recur (inc i) r (conj acc r)))))
          a (run)]
      (is (= a (run)))
      (is (= #{0 1 2} (set a)))
      (is (every? (fn [[x y]] (not= x y)) (partition 2 1 a)))))
  (testing "no previous take uses all k"
    (is (every? #(< -1 % 3) (for [i (range 50)] (kits/pick-take 3 nil [i 16] "K_bd")))))
  (testing "nil onset behaves as 0"
    (is (= (kits/pick-take 3 nil nil "K_bd") (kits/pick-take 3 nil [0 1] "K_bd"))))
  (testing "different banks at one onset can differ"
    (is (> (count (set (for [b ["K_bd" "K_sd" "K_hh" "K_oh" "K_rd" "K_cr"]]
                         (kits/pick-take 5 nil [3 4] b))))
           1))))

(deftest helpers
  (is (= ["a" "b" "c" "d"] (kits/flatten-urls [{:urls ["a" "b"]} {:urls ["c" "d"]}])))
  (is (= "JazzKit" (kits/kit-prefix :JazzKit_bd)))
  (is (= [:kit "JazzKit" "hat"] (kits/choke-group "JazzKit_hh" {:choke "hat"})))
  (is (nil? (kits/choke-group "JazzKit_bd" {:choke nil})))
  (is (= 0.0 (kits/clamp-velocity -1)))
  (is (= 1.0 (kits/clamp-velocity "x"))))
