(ns repulse.session-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.midi :as midi]
            [repulse.samples :as samples]
            [repulse.session :as session]))

(defn- mock-local-storage []
  (let [store (atom {})]
    #js {:getItem (fn [k] (get @store k))
         :setItem (fn [k v] (swap! store assoc k v))
         :removeItem (fn [k] (swap! store dissoc k))
         :clear (fn [] (reset! store {}))}))

(defn- restore-atom! [atm value]
  (reset! atm value))

(defn- snapshot-scheduler-state []
  (select-keys @audio/scheduler-state [:cycle-dur :tracks :muted :on-beat :on-event :on-fx-event :tween-state]))

(defn- restore-scheduler-state! [state]
  (reset! audio/scheduler-state state))

(defn- with-session-state [f]
  (let [original-local-storage (.-localStorage js/globalThis)
        local-storage          (mock-local-storage)
        editor-text            @session/editor-text-fn
        fx-chain               @fx/chain
        bank-prefix            @samples/active-bank-prefix
        loaded-sources         @samples/loaded-sources
        cc-mappings            @midi/cc-mappings
        scheduler-state        (snapshot-scheduler-state)]
    (set! (.-localStorage js/globalThis) local-storage)
    (try
      (f)
      (finally
        (restore-atom! session/editor-text-fn editor-text)
        (restore-atom! fx/chain fx-chain)
        (restore-atom! samples/active-bank-prefix bank-prefix)
        (restore-atom! samples/loaded-sources loaded-sources)
        (restore-atom! midi/cc-mappings cc-mappings)
        (restore-scheduler-state! scheduler-state)
        (set! (.-localStorage js/globalThis) original-local-storage)))))

(use-fixtures :each with-session-state)

(deftest coerce-bpm-bounds
  (testing "coerce-bpm clamps invalid and out-of-range values"
    (is (= 120 (audio/coerce-bpm 120)))
    (is (= 120 (audio/coerce-bpm 0)))
    (is (= 120 (audio/coerce-bpm -50)))
    (is (= 120 (audio/coerce-bpm nil)))
    (is (= 120 (audio/coerce-bpm "abc")))
    (is (= 120 (audio/coerce-bpm js/NaN)))
    (is (= 20 (audio/coerce-bpm 10)))
    (is (= 500 (audio/coerce-bpm 500)))
    (is (= 640 (audio/coerce-bpm 650)))))

(deftest session-round-trip
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
    (is (= session/current-version (:v loaded)))))

(deftest corrupt-bpm-restore
  (.setItem js/localStorage session/storage-key
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

  (.setItem js/localStorage session/storage-key
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
    (is (= 640 (:bpm loaded)))))
