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
        project-fn             @session/project-fn
        fx-chain               @fx/chain
        bank-prefix            @samples/active-bank-prefix
        loaded-sources         @samples/loaded-sources
        cc-mappings            @midi/cc-mappings
        scheduler-state        (snapshot-scheduler-state)]
    (set! (.-localStorage js/globalThis) local-storage)
    (try
      (f)
      (finally
        (restore-atom! session/project-fn project-fn)
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

(defn- single-main [code]
  (fn [] {:main "main" :active "main" :buffers [{:name "main" :code code}]}))

(defn- main-code [loaded]
  (some #(when (= (:main loaded) (:name %)) (:code %)) (:buffers loaded)))

(deftest session-round-trip
  (reset! session/project-fn (single-main "(seq :bd :sd)"))
  (reset! fx/chain [])
  (reset! samples/active-bank-prefix nil)
  (reset! samples/loaded-sources [])
  (reset! midi/cc-mappings {})
  (swap! audio/scheduler-state assoc :muted #{} :tracks {})
  (audio/set-bpm! 132)

  (session/save-session!)

  (let [loaded (session/load-session)]
    (is (= "(seq :bd :sd)" (main-code loaded)))
    (is (= 132 (:bpm loaded)))
    (is (= session/current-version (:v loaded)))))

(deftest session-snapshot-does-not-persist-fx-params
  (reset! session/project-fn (single-main "(fx :distort 20)\n(seq :bd)"))
  (reset! fx/chain [{:name "distort"
                     :plugin #js {:getParams (fn [] #js {:drive 80 :mix 1})}
                     :bypassed? false}])
  (is (= [] (:fx (session/build-session-snapshot)))))

(deftest corrupt-bpm-restore
  (.setItem js/localStorage session/storage-key
            (js/JSON.stringify
             #js {:v session/current-version
                  :main "main"
                  :active "main"
                  :buffers #js [#js {:name "main" :code "(seq :bd)"}]
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
                  :main "main"
                  :active "main"
                  :buffers #js [#js {:name "main" :code "(seq :bd)"}]
                  :bpm 999
                  :fx #js []
                  :bank nil
                  :sources #js []
                  :muted #js []
                  :midi #js {}}))
  (let [loaded (session/load-session)]
    (is (= 640 (:bpm loaded)))))

(deftest malformed-session-is-sanitized-at-load-boundary
  (.setItem js/localStorage session/storage-key
            (js/JSON.stringify
             #js {:v 2
                  :editor 42
                  :bpm 120
                  :fx #js [#js {:name "ignored"}]
                  :bank 10
                  :sources #js [#js {:type "github" :id "owner/repo"}
                                #js {:type "github" :id ""}
                                #js {:type "freesound" :query "kick"}]
                  :muted #js ["kick" 99 "snare"]
                  :midi #js {"1" "wet" "2" 9}}))
  (let [loaded (session/load-session)]
    (is (= 3 (:v loaded)))
    (is (= "" (main-code loaded)))
    (is (= [] (:fx loaded)))
    (is (nil? (:bank loaded)))
    (is (= [{:type "github" :id "owner/repo"}] (:sources loaded)))
    (is (= ["kick" "snare"] (:muted loaded)))
    (is (= {"1" "wet"} (:midi loaded)))))

;;; ── Phase SONG4: v3 sessions, migration and share links ─────────────────

(def ^:private s1-v3
  {:v 3 :main "main" :active "main" :buffers [{:name "main" :code "(seq :bd :sd)"}]
   :bpm 132 :fx [] :bank nil :sources [] :muted ["kick"] :midi {}})

(deftest s1-v2-to-v3
  (is (= s1-v3
         (session/migrate-v2->v3 {:v 2 :editor "(seq :bd :sd)" :bpm 132 :fx [] :bank nil
                                  :sources [] :muted ["kick"] :midi {}})))
  (testing "a v2 blob in localStorage loads as the migrated v3 map"
    (.setItem js/localStorage session/storage-key
              (js/JSON.stringify
               (clj->js {:v 2 :editor "(seq :bd :sd)" :bpm 132 :fx [] :bank nil
                         :sources [] :muted ["kick"] :midi {}})))
    (is (= s1-v3 (session/load-session)))))

(def ^:private s2-project
  {:main "main" :active "drums"
   :buffers [{:name "main" :code "(use :drums)"}
             {:name "drums" :code "(track :kick (seq :bd))"}]})

(defn- reset-session-state! []
  (reset! fx/chain [])
  (reset! samples/active-bank-prefix nil)
  (reset! samples/loaded-sources [])
  (reset! midi/cc-mappings {})
  (swap! audio/scheduler-state assoc :muted #{} :tracks {})
  (audio/set-bpm! 132))

(deftest s2-v3-round-trip
  (reset-session-state!)
  (reset! session/project-fn (constantly s2-project))
  (session/save-session!)
  (let [loaded (session/load-session)]
    (is (= 3 (:v loaded)))
    (is (= s2-project (select-keys loaded [:main :active :buffers])))
    (is (= 132 (:bpm loaded))))
  (testing "a stored :active that names no buffer loads as main"
    (reset! session/project-fn (constantly s2-project))
    (let [snap (assoc (session/build-session-snapshot) :active "gone")]
      (.setItem js/localStorage session/storage-key (js/JSON.stringify (clj->js snap)))
      (is (= "main" (:active (session/load-session)))))))

(deftest s3-legacy-keys
  (.setItem js/localStorage "repulse-editor" "(seq :bd)")
  (.setItem js/localStorage "repulse-bpm" "140")
  (let [expected {:v 3 :main "main" :active "main" :buffers [{:name "main" :code "(seq :bd)"}]
                  :bpm 140 :fx [] :bank nil :sources [] :muted [] :midi {}}]
    (is (= expected (session/migrate-legacy!)))
    (is (= expected (session/load-session)) "the stored blob is v3")
    (is (nil? (.getItem js/localStorage "repulse-editor")))))

(deftest s4-share-round-trip
  (reset-session-state!)
  (reset! session/project-fn (constantly s2-project))
  (let [snap (session/build-session-snapshot)
        hash (session/encode-share-hash snap)]
    (is (= "#v3:" (subs hash 0 4)))
    (is (= snap (session/decode-share-hash hash))))
  (testing "#v2: hash decodes to a single-main v3 project"
    (let [json "{\"v\":2,\"editor\":\"(seq :bd :sd)\",\"bpm\":120,\"fx\":[],\"bank\":null,\"sources\":[],\"muted\":[],\"midi\":{}}"]
      (is (= {:v 3 :main "main" :active "main" :buffers [{:name "main" :code "(seq :bd :sd)"}]
              :bpm 120 :fx [] :bank nil :sources [] :muted [] :midi {}}
             (session/decode-share-hash (str "#v2:" (session/b64-encode json)))))))
  (testing "#v1: hash still decodes, to a single main"
    (let [d (session/decode-share-hash (str "#v1:" (js/btoa "{\"editor\":\"(seq :hh)\",\"bpm\":100}")))]
      (is (= [{:name "main" :code "(seq :hh)"}] (:buffers d)))
      (is (= "main" (:main d)))
      (is (= 100 (:bpm d)))))
  (testing "garbage hashes decode to nil"
    (is (nil? (session/decode-share-hash "#v3:!!!")))
    (is (nil? (session/decode-share-hash "#nothing")))))

(deftest s5-share-size-limit
  (reset-session-state!)
  (reset! session/project-fn
          (constantly {:main "main" :active "main"
                       :buffers [{:name "main" :code (apply str (repeat 60000 "x"))}]}))
  (let [snap (session/build-session-snapshot)
        r    (session/share-link "https://repulse.app/" snap)
        m    (re-matches #"Share failed: link would be (\d+) characters \(limit 64000\)\. Export the project as a \.repulse file instead\."
                         (or (:error r) ""))]
    (is (some? m) (pr-str r))
    (is (= (count (str "https://repulse.app/" (session/encode-share-hash snap)))
           (js/parseInt (second m)))))
  (reset! session/project-fn (constantly s2-project))
  (let [r (session/share-link "https://repulse.app/" (session/build-session-snapshot))]
    (is (string? (:url r)))
    (is (<= (count (:url r)) 64000))))
