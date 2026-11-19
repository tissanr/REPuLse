(ns repulse.tracks-model-test
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [repulse.tracks-model :as tm]))

(def off {:mute? false :solo? false})

(deftest parse-flags-test
  (testing "no flags"
    (is (= off (tm/parse-flags []))))
  (testing "each flag, any order"
    (is (= {:mute? true :solo? false} (tm/parse-flags [:mute])))
    (is (= {:mute? false :solo? true} (tm/parse-flags [:solo])))
    (is (= {:mute? true :solo? true} (tm/parse-flags [:mute :solo])))
    (is (= {:mute? true :solo? true} (tm/parse-flags [:solo :mute]))))
  (testing "unknown keyword gets a fuzzy suggestion"
    (let [r (tm/parse-flags [:mut])]
      (is (= 0 (:index r)))
      (is (= "unknown track option :mut — did you mean :mute?" (:error r))))
    (is (str/includes? (:error (tm/parse-flags [:slo])) "did you mean :solo?"))
    (is (= "unknown track option :fast — expected :mute or :solo"
           (:error (tm/parse-flags [:fast])))))
  (testing "a non-keyword option is an error at its index"
    (let [r (tm/parse-flags [:mute 3])]
      (is (= 1 (:index r)))
      (is (= "track options must be :mute or :solo, got 3" (:error r)))))
  (testing "each flag at most once"
    (is (= {:error "duplicate track option :mute" :index 1}
           (tm/parse-flags [:mute :mute])))))

(deftest muted-set-test
  (testing "no flags → nothing muted"
    (is (= #{} (tm/muted-set [[:a off] [:b off]]))))
  (testing ":mute mutes that track"
    (is (= #{:hats} (tm/muted-set [[:hats {:mute? true :solo? false}] [:k off]]))))
  (testing "solo mutes every unsoloed track; several solos are allowed"
    (is (= #{:h} (tm/muted-set [[:k {:mute? false :solo? true}]
                                [:h off]
                                [:s {:mute? false :solo? true}]]))))
  (testing ":mute beats :solo on the same track"
    (is (= #{:x} (tm/muted-set [[:x {:mute? true :solo? true}]])))
    (is (= #{:x :y} (tm/muted-set [[:x {:mute? true :solo? true}] [:y off]]))))
  (testing ":_ follows the rule like an unflagged track"
    (is (= #{:_} (tm/muted-set [[:k {:mute? false :solo? true}] [:_ off]]))))
  (testing "empty"
    (is (= #{} (tm/muted-set [])))))

(deftest plan-test
  (testing "undeclared tracks retire"
    (is (= {:retire #{:b :_}} (tm/plan #{:a :b :_} #{:a}))))
  (testing "nothing declared retires everything"
    (is (= {:retire #{:a :b}} (tm/plan #{:a :b} #{}))))
  (testing "preview tracks are never retired"
    (is (= {:retire #{:b}}
           (tm/plan #{:a :b :__preview__ :__preview__-kick} #{:a}))))
  (testing "declared but not yet scheduled is not a retire"
    (is (= {:retire #{}} (tm/plan #{} #{:a})))))

(deftest reserved-track-test
  (is (tm/reserved-track? :__preview__-kick))
  (is (not (tm/reserved-track? :_)))
  (is (not (tm/reserved-track? :kick))))
