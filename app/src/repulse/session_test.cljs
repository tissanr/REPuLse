(ns repulse.session-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.midi :as midi]
            [repulse.samples :as samples]
            [repulse.session :as session]))

(defn- mock-local-storage []
  (let [store (atom {})]
    #js {:getItem (fn [k] (get @store k nil))
         :setItem (fn [k v] (swap! store assoc k v))
         :removeItem (fn [k] (swap! store dissoc k))
         :clear (fn [] (reset! store {}))}))

(defn- with-local-storage [f]
  (let [original (.-localStorage js/globalThis)
        mock     (mock-local-storage)]
    (set! (.-localStorage js/globalThis) mock)
    (try
      (f mock)
      (finally
        (set! (.-localStorage js/globalThis) original)))))

(deftest coerce-bpm-bounds
  (testing "coerce-bpm clamps invalid and out-of-range values"
    (is (= 120 (audio/coerce-bpm 120)))
    (is (= 120 (audio/coerce-bpm 0)))
    (is (= 120 (audio/coerce-bpm -50)))
    (is (= 120 (audio/coerce-bpm nil)))
    (is (= 120 (audio/coerce-bpm "abc")))
    (is (= 120 (audio/coerce-bpm js/NaN)))
    (is (= 20 (audio/coerce-bpm 10)))
    (is (= 400 (audio/coerce-bpm 500)))))

(deftest session-round-trip
  (with-local-storage
    (fn [_]
      (reset! session/editor-text-fn (fn [] "(seq :bd :sd)"))
      (reset! fx/chain [])
      (reset! samples/active-bank-prefix nil)
      (reset! samples/loaded-sources [])
      (reset! midi/cc-mappings {})
      (swap! audio/scheduler-state assoc :muted #{} :tracks {})
      (audio/set-bpm! 132)

      (session/save-session!)

      (let [loaded (session/load-session)]
        (is (= "(seq :bd :sd)" (:editor loaded)))
        (is (= 132 (:bpm loaded)))
        (is (= session/current-version (:v loaded)))))))

(deftest corrupt-bpm-restore
  (with-local-storage
    (fn [storage]
      (.setItem storage session/storage-key
                (js/JSON.stringify
                 #js {:v session/current-version
                      :editor "(seq :bd)"
                      :bpm 0
                      :fx #js []
                      :bank nil
                      :sources #js []
                      :muted #js []
                      :midi #js {}}))
      (let [loaded (session/load-session)]
        (is (= 120 (:bpm loaded))))

      (.setItem storage session/storage-key
                (js/JSON.stringify
                 #js {:v session/current-version
                      :editor "(seq :bd)"
                      :bpm 999
                      :fx #js []
                      :bank nil
                      :sources #js []
                      :muted #js []
                      :midi #js {}}))
      (let [loaded (session/load-session)]
        (is (= 400 (:bpm loaded)))))))
