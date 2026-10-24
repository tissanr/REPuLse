(ns repulse.sound-check-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.core :as core]
            [repulse.sound-check :as sc]))

(def banks ["bd" "sd" "hh" "cp" "tabla" "RolandTR808_bd" "RolandTR808_sd"])
(def bank-set (set banks))
(defn- known-bank? [kw] (contains? bank-set (name kw)))

(deftest sound-keywords-test
  (testing "collects bare keywords, skipping rests and note keywords"
    (is (= #{:kick :snare}
           (set (sc/sound-keywords (core/seq* [:kick :_ :snare :c4 :eb3]))))))
  (testing "collects :note of synth-less maps and :bank of sound maps"
    (is (= #{:kick :tabla}
           (set (sc/sound-keywords (core/seq* [{:note :kick :amp 0.5}
                                               {:bank :tabla :n 0}]))))))
  (testing "ignores :note of synth maps (pitch, not a sound name)"
    (is (empty? (sc/sound-keywords (core/pure {:note :foo :synth :saw})))))
  (testing "looks past the first cycle"
    (is (= #{:bd :kick}
           (set (sc/sound-keywords (core/cat* [(core/pure :bd) (core/pure :kick)])))))))

(deftest suggest-test
  (testing "common aliases map to built-in drums"
    (is (= :bd (sc/suggest :kick banks)))
    (is (= :sd (sc/suggest :snare banks)))
    (is (= :hh (sc/suggest :hat banks)))
    (is (= :hh (sc/suggest :hihat banks)))
    (is (= :oh (sc/suggest :openhat banks)))
    (is (= :bd (sc/suggest :Kick banks)) "aliases are case-insensitive"))
  (testing "typos fall back to the closest bank name"
    (is (= :tabla (sc/suggest :tabal banks)))
    (is (= :RolandTR808_bd (sc/suggest :RolandTR808_b banks))))
  (testing "no suggestion when nothing is plausibly close"
    (is (nil? (sc/suggest :xylophone banks)))
    (is (nil? (sc/suggest :x banks)) "short names don't match any two-letter bank")))

(deftest unknown-sounds-test
  (testing "the kick/snare/hat mistake is reported with suggestions"
    (is (= [{:name :kick :suggestion :bd}
            {:name :snare :suggestion :sd}]
           (sc/unknown-sounds [(core/seq* [:kick :_ :snare :_])] known-bank? banks))))
  (testing "valid patterns produce no warnings"
    (is (empty? (sc/unknown-sounds [(core/seq* [:bd :sd :hh :oh])
                                    (core/seq* [:c3 :eb3 :g3])
                                    (core/seq* [:tabla :cp :_])]
                                   known-bank? banks))))
  (testing "built-in drums are known even before any bank is loaded"
    (is (empty? (sc/unknown-sounds [(core/seq* [:bd :sd :hh :oh])]
                                   (constantly false) []))))
  (testing "each name is reported once across events and patterns"
    (is (= [:kick]
           (map :name (sc/unknown-sounds [(core/seq* [:kick :kick :kick])
                                          (core/pure :kick)]
                                         known-bank? banks))))))

(deftest warning-message-test
  (is (= "unknown sound :kick — did you mean :bd?"
         (sc/warning-message {:name :kick :suggestion :bd})))
  (is (re-find #"unknown sound :xylophone — plays a 440 Hz sine"
               (sc/warning-message {:name :xylophone :suggestion nil}))))

(deftest keyword-ranges-test
  (let [code "(track :boom (seq :kick :_ :kicker :kick))"]
    (is (= [{:from 18 :to 23} {:from 35 :to 40}]
           (sc/keyword-ranges code :kick))))
  (is (= [] (sc/keyword-ranges "(seq :bd)" :kick))))
