(ns repulse.project-eval-test
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [repulse.core :as core]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.project :as project]
            [repulse.project-eval :as pe]
            [repulse.song-transport :as st]))

(def B project/OFFSET-STRIDE)

(def marks (atom []))

(defn- make-env []
  (merge (leval/make-env (fn [] nil) (fn [_] nil))
         {"use"   pe/use-builtin
          "track" (fn [n p]
                    (let [name' (leval/unwrap n) pat' (leval/unwrap p)]
                      (st/collect-track! name' pat' (leval/source-of n))
                      (str "=> track :" (name name') " playing")))
          "mark!" (fn [k] (swap! marks conj (leval/unwrap k)) nil)}))

(defn- store->project [store]
  {:main "main" :active "main"
   :buffers (mapv (fn [[n c]] {:name n :code c}) store)})

(defn- run [store]
  (st/reset-pending!)
  (pe/run-project (store->project store) (make-env)))

(use-fixtures :each
  {:before (fn [] (reset! marks []) (st/reset-pending!))
   :after  (fn [] (st/reset-pending!))})

(def one-cycle (core/span (core/int->rat 0) (core/int->rat 1)))

(deftest e1-order
  (let [r (run [["main" "(use :drums :bass)\n(mark! :main)"]
                ["drums" "(use :kit)\n(mark! :drums)"]
                ["bass" "(use :kit :scale)\n(mark! :bass)"]
                ["kit" "(mark! :kit)"]
                ["scale" "(mark! :scale)"]])]
    (is (not (lisp/eval-error? (:value r))) (:message (:value r)))
    (is (= ["main" "drums" "kit" "bass" "scale"] (:order r)))
    (is (= [:kit :drums :scale :bass :main] @marks))))

(deftest e2-shared-env
  (let [r (run [["main" "(use :drums)\n(fast 2 kick)"] ["drums" "(def kick (seq :bd :sd))"]])
        v (:value r)]
    (is (not (lisp/eval-error? v)) (:message v))
    (is (core/pattern? (:result v)))
    (is (= 4 (count (core/query (:result v) one-cycle)))))
  (is (= {:result "=> used :drums"}
         (:value (run [["main" "(use :drums)"] ["drums" "(def kick (seq :bd :sd))"]]))))
  (testing "event sources from a used buffer carry the buffer's global offset"
    (let [r   (run [["main" "(use :drums)\nkick"] ["drums" "(def kick (seq :bd :sd))"]])
          evs (core/query (:result (:value r)) one-cycle)]
      (is (= [{:buffer "drums" :from 15 :to 18} {:buffer "drums" :from 19 :to 22}]
             (mapv #(project/locate (:bases r) (:from (:source %)) (:to (:source %))) evs))))))

(deftest e3-cycle
  (let [v (:value (run [["main" "(use :drums)"] ["drums" "(use :bass)"] ["bass" "(use :drums)"]]))]
    (is (lisp/eval-error? v))
    (is (= "Circular use: main → drums → bass → drums" (:message v)))
    (is (= {:from (+ (* 2 B) 5) :to (+ (* 2 B) 11)} (:source v)))
    (is (= {:buffer "bass" :from 5 :to 11}
           (project/locate (project/offset-bases (store->project [["main" ""] ["drums" ""] ["bass" ""]]))
                           (:from (:source v)) (:to (:source v))))))
  (is (= "Circular use: main → main" (:message (:value (run [["main" "(use :main)"]]))))))

(deftest e4-unknown-buffer
  (let [v (:value (run [["main" "(use :drumz)"] ["drums" ""]]))]
    (is (= "Unknown buffer :drumz — did you mean :drums?" (:message v)))
    (is (= {:from 5 :to 11} (:source v))))
  (is (= "Unknown buffer :zzz" (:message (:value (run [["main" "(use :zzz)"] ["drums" ""]]))))))

(deftest e5-argument-errors
  (is (= "use needs at least one buffer name, e.g. (use :drums)"
         (:message (:value (run [["main" "(use)"]])))))
  (is (= "use expects buffer names as keywords, e.g. (use :drums) — got \"drums\""
         (:message (:value (run [["main" "(use \"drums\")"] ["drums" ""]])))))
  (is (= "use only works when the project is evaluated (Alt+Enter in the editor)"
         (:message (lisp/eval-string "(use :drums)" (make-env))))))

(deftest e6-error-inside-used-buffer
  (let [v (:value (run [["main" "(use :drums)"] ["drums" "(def k 1)\n(def x (qqqqqqqq 2 k))"]]))]
    (is (= "Undefined symbol: qqqqqqqq" (:message v)))
    (is (= {:from (+ B 18) :to (+ B 26)} (:source v)))
    (is (= {:buffer "drums" :from 18 :to 26}
           (project/locate {"main" 0 "drums" B} (:from (:source v)) (:to (:source v)))))))

(deftest e7-duplicate-tracks
  (let [v (:value (run [["main" "(use :drums)\n(track :kick (seq :sd))"]
                        ["drums" "(track :kick (seq :bd))"]]))]
    (is (= "Duplicate track name :kick — already defined in buffer :drums; track names must be unique across the project"
           (:message v)))
    (is (= {:from 20 :to 25} (:source v))))
  (let [v (:value (run [["main" "(track :kick (seq :sd))\n(use :drums)"]
                        ["drums" "(track :kick (seq :bd))"]]))]
    (is (= "Duplicate track name :kick — already defined in buffer :main; track names must be unique across the project"
           (:message v)))
    (is (= {:from (+ B 7) :to (+ B 12)} (:source v))))
  (is (= "Duplicate track name :kick — each track must have a unique name in the buffer"
         (:message (:value (run [["main" "(track :kick (seq :bd)) (track :kick (seq :sd))"]]))))))

(deftest e8-cleanup
  (run [["main" "(use :drums)"] ["drums" "(def a 1)"]])
  (is (nil? @st/buffer-of))
  (is (= "use only works when the project is evaluated (Alt+Enter in the editor)"
         (:message (lisp/eval-string "(use :drums)" (make-env)))))
  (run [["main" "(use :nope)"]])
  (is (nil? @st/buffer-of))
  (is (= "use only works when the project is evaluated (Alt+Enter in the editor)"
         (:message (lisp/eval-string "(use :drums)" (make-env))))))
