(ns repulse.ui.selection-test
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            ["../lisp-lang/parser.js" :refer [parser]]
            [repulse.outline :as outline]
            [repulse.ui.selection :as sel]))

(def W
  (str/join "\n"
    ["(def kick (seq :bd :_ :bd :_))"
     "(track :drums (->> kick (amp 0.8) (fx :filter :freq 2400)))"
     "(track :bass (->> (seq :c2 :eb2) (synth :saw) (amp 0.6) (decay 0.25)))"
     "(fx :reverb 0.3)"
     "(song"
     "  (section :intro 4 [:drums])"
     "  (section :verse 8 [:drums :bass])"
     "  (section :chorus 8 :all))"]))

(def entries (:entries (outline/extract (.parse parser W) W)))
(def idx (sel/index-entries entries))

(deftest buffer-w
  (is (= 278 (count W))))

(deftest index-entries-test
  (is (= ["def:kick" "fx" "fx:0" "section:0" "section:1" "section:2" "track::bass" "track::drums"]
         (sort (keys idx))))
  (is (= :fx-group (:kind (idx "fx")))))

(deftest pick-test
  (is (= ["track::drums"] (sel/pick [] "track::drums" false)))
  (is (= [] (sel/pick ["track::drums"] "track::drums" false)))
  (is (= ["track::drums" "track::bass"] (sel/pick ["track::drums"] "track::bass" true)))
  (is (= ["track::bass"] (sel/pick ["track::drums" "track::bass"] "track::drums" true)))
  (is (= ["track::bass"] (sel/pick ["track::drums" "track::bass"] "track::bass" false)))
  (is (= ["track::bass"] (sel/pick ["def:kick"] "track::bass" true)))
  (is (= ["section:1"] (sel/pick ["track::drums"] "section:1" true))))

(deftest ranges-test
  (is (= [{:from 91 :to 161}] (sel/ranges ["track::bass"] idx)))
  (is (= [{:from 31 :to 90} {:from 91 :to 161}] (sel/ranges ["track::bass" "track::drums"] idx)))
  (is (= [{:from 217 :to 250}] (sel/ranges ["section:1"] idx)))
  (is (= [{:from 162 :to 178}] (sel/ranges ["fx"] idx)))
  (is (= [{:from 0 :to 30}] (sel/ranges ["def:kick"] idx)))
  (testing "overlapping ranges merge"
    (is (= [{:from 0 :to 10}]
           (sel/ranges ["a" "b"] {"a" {:from 0 :to 6} "b" {:from 4 :to 10}})))))

(deftest prune-primary-test
  (is (= ["track::bass"] (sel/prune ["track::bass" "def:gone"] idx)))
  (is (= 91 (:from (sel/primary ["track::bass" "def:gone"] idx))))
  (is (= 31 (:from (sel/primary ["track::bass" "track::drums"] idx))))
  (is (nil? (sel/primary [] idx))))

(deftest header-test
  (is (= {:kind "track" :title ":bass" :from 91} (sel/header ["track::bass"] idx)))
  (is (= {:kind "tracks" :title "2" :from nil} (sel/header ["track::drums" "track::bass"] idx)))
  (is (= {:kind "section" :title ":verse" :from 217} (sel/header ["section:1"] idx)))
  (is (= "def" (:kind (sel/header ["def:kick"] idx))))
  (is (= {:kind "fx" :title ":reverb" :from 162} (sel/header ["fx:0"] idx)))
  (is (nil? (sel/header [] idx))))
