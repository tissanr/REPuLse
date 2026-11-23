(ns repulse.samples-test
  (:require [cljs.test :refer [deftest is async testing]]
            [clojure.string :as str]
            [repulse.samples :as samples]))

(defn- fake-response [status body]
  #js {:ok         (= 200 status)
       :status     status
       :statusText (if (= 200 status) "OK" "Not Found")
       :json       (fn [] (js/Promise.resolve (clj->js body)))})

(defn- with-fake-fetch
  "Replace js/fetch with handler (url → [status body]) while (f done') runs;
   restores the original fetch and registry before calling done."
  [done handler f]
  (let [orig-fetch    (.-fetch js/globalThis)
        orig-registry @samples/registry
        orig-sources  @samples/loaded-sources
        requested     (atom [])]
    (set! (.-fetch js/globalThis)
          (fn [url]
            (swap! requested conj url)
            (let [[status body] (handler url)]
              (js/Promise.resolve (fake-response status body)))))
    (f requested
       (fn []
         (set! (.-fetch js/globalThis) orig-fetch)
         (reset! samples/registry orig-registry)
         (reset! samples/loaded-sources orig-sources)
         (done)))))

(def ^:private tree
  {:tree [{:type "blob" :path "drums/kick.wav"}]})

(deftest github-falls-back-to-master
  (async done
    (with-fake-fetch done
      (fn [url] (if (str/includes? url "/trees/master") [200 tree] [404 {}]))
      (fn [requested finish]
        (-> (samples/load-external! "github:someone/kit")
            (.then (fn []
                     (is (= 2 (count @requested)) "tries main, then master")
                     (is (= ["https://raw.githubusercontent.com/someone/kit/master/drums/kick.wav"]
                            (get @samples/registry "drums")))
                     (finish))))))))

(deftest github-explicit-branch-does-not-fall-back
  (async done
    (with-fake-fetch done
      (fn [_] [404 {}])
      (fn [requested finish]
        (-> (samples/load-external! "github:someone/kit/dev")
            (.then (fn []
                     (is (= 1 (count @requested)))
                     (is (nil? (get @samples/registry "drums")))
                     (finish))))))))

;;; ── Phase TL6: playback-plan / cut-plan ─────────────────────────────

(defn- approx= [a b] (< (Math/abs (- a b)) 1e-9))

(deftest playback-plan-defaults
  (let [p (samples/playback-plan {} 2.0)]
    (is (= false (:reverse? p)))
    (is (= 1.0 (:rate p)))
    (is (= 0.0 (:offset p)))
    (is (= 2.0 (:content p)))
    (is (nil? (:stop p)))
    (is (= 2.0 (:length p)))))

(deftest playback-plan-begin-end
  (let [p (samples/playback-plan {:begin 0.25 :end 0.5 :rate 2} 4.0)]
    (is (= 1.0 (:offset p)))
    (is (= 1.0 (:content p)))
    (is (= 0.5 (:length p))))
  (is (nil? (samples/playback-plan {:begin 0.5 :end 0.5} 1.0)))
  (is (nil? (samples/playback-plan {:rate 0} 1.0)))
  (testing "[n d] rationals from Lisp literals"
    (is (= 0.5 (:offset (samples/playback-plan {:begin [1 4]} 2.0))))))

(deftest playback-plan-unit-c-splice
  ;; 3 s sample, 2 s cycles: (splice 4 …) gives rate 1 unit :c for 1/4-cycle
  ;; slots → playback rate 1·3/2 = 1.5, and slice 3/4 s / 1.5 = 0.5 s = the slot
  (let [p (samples/playback-plan {:begin 0.25 :end 0.5 :rate 1.0 :unit :c
                                  :cycle-dur 2.0} 3.0)]
    (is (approx= 1.5 (:rate p)))
    (is (approx= 0.5 (:length p))))
  (testing "loop-at 2 on a 1 s sample at 0.25 s cycles → rate 2"
    (is (approx= 2.0 (:rate (samples/playback-plan {:rate 0.5 :unit :c :cycle-dur 0.25} 1.0)))))
  (testing "no cycle length → :c falls back to plain rate (S5)"
    (is (= 1.0 (:rate (samples/playback-plan {:rate 1 :unit :c} 3.0))))))

(deftest playback-plan-reverse
  (let [p (samples/playback-plan {:rate -1 :begin 0.25 :end 0.5} 4.0)]
    (is (true? (:reverse? p)))
    (is (= 1.0 (:rate p)))
    ;; forward 1–2 s → reversed buffer 2–3 s
    (is (= 2.0 (:offset p)))
    (is (= 1.0 (:content p)))))

(deftest playback-plan-legato-sustain-loop
  (is (= 0.25 (:stop (samples/playback-plan {:legato 0.5 :dur 0.5} 2.0))))
  (is (= 0.1 (:stop (samples/playback-plan {:legato 1 :dur 0.5 :sustain 0.1} 2.0))))
  (is (nil? (:stop (samples/playback-plan {:legato 0.5} 2.0))) "no event duration known")
  (testing "looped samples stop at the event end (S9)"
    (let [p (samples/playback-plan {:loop true :dur 0.75} 2.0)]
      (is (nil? (:content p)))
      (is (= 0.75 (:stop p)))
      (is (= 0.75 (:length p)))))
  (testing "loop region"
    (let [p (samples/playback-plan {:loop true :loop-begin 0.25 :loop-end 0.5} 2.0)]
      (is (= [0.5 1.0] [(:loop-start p) (:loop-end p)]))
      (is (= ##Inf (:length p))))))

(deftest playback-plan-accelerate
  (is (= 0.5 (:accel-to (samples/playback-plan {:accelerate -0.5} 1.0))))
  (is (= 0.01 (:accel-to (samples/playback-plan {:accelerate -2} 1.0))))
  (is (nil? (:accel-to (samples/playback-plan {} 1.0)))))

(deftest cut-plan-chokes-earlier-voices
  (let [vs [{:id 1 :start 0.0 :end 1.0}
            {:id 2 :start 0.2 :end 0.3}
            {:id 3 :start 0.4 :end 0.9}
            {:id 5 :start 0.5 :end 0.9}
            {:id 4 :start 2.0 :end 3.0}]]
    (is (= {:choke [1 3] :self-stop 2.0} (samples/cut-plan vs 0.5))))
  (is (= {:choke [] :self-stop nil} (samples/cut-plan [] 1.0))))

;;; ── KIT1: layered manifest entries ───────────────────────────────────

(def ^:private kit-manifest
  {"_format" "repulse-kit/1"
   "_base"   "http://x/kits/"
   "FixKit_bd" {"layers" [{"v" 0.4 "files" ["bd/s1.wav" "bd/s2.wav"]}
                          {"v" 1 "gain" 1.2 "files" ["bd/h1.wav" "bd/h2.wav" "bd/h3.wav"]}]}
   "FixKit_hh" {"choke" "hat" "layers" [{"v" 1 "files" ["hh/1.wav"]}]}
   "FixKit_bad" {"layers" [{"v" 0.5 "files" ["x.wav"]}]}
   "plain"     ["p1.wav" "p2.wav"]})

(deftest parse-manifest-layered-and-flat
  (let [{:keys [banks entries]} (samples/parse-manifest kit-manifest)]
    (is (= ["http://x/kits/bd/s1.wav" "http://x/kits/bd/s2.wav"
            "http://x/kits/bd/h1.wav" "http://x/kits/bd/h2.wav" "http://x/kits/bd/h3.wav"]
           (get banks "FixKit_bd")) "flattened low → high layer")
    (is (= ["http://x/kits/p1.wav" "http://x/kits/p2.wav"] (get banks "plain")))
    (is (= #{"FixKit_bd" "FixKit_hh"} (set (keys entries))))
    (is (not (contains? banks "FixKit_bad")) "malformed entry skipped, rest loads")
    (is (= "hat" (:choke (get entries "FixKit_hh"))))))

(deftest array-manifests-parse-as-before
  (let [raw {"_base" "http://x/" "bd" ["a.wav" "b.wav"] "sd" ["c.wav"]}
        {:keys [banks entries]} (samples/parse-manifest raw)]
    (is (= {"bd" ["http://x/a.wav" "http://x/b.wav"] "sd" ["http://x/c.wav"]} banks))
    (is (empty? entries))))

(deftest unknown-format-skips-object-entries
  (let [{:keys [banks entries]}
        (samples/parse-manifest (assoc kit-manifest "_format" "repulse-kit/2"))]
    (is (empty? entries))
    (is (not (contains? banks "FixKit_bd")))
    (is (contains? banks "plain") "array entries still load")))

(deftest layered-bank-registration-and-kit-names
  (let [ro @samples/registry
        lo @samples/layers
        {:keys [banks entries]} (samples/parse-manifest kit-manifest)]
    (reset! samples/registry (merge banks {"bd" ["d.wav"]}))
    (reset! samples/layers entries)
    (is (samples/layered? :FixKit_bd))
    (is (not (samples/layered? "plain")))
    (is (samples/has-bank? :FixKit_bd))
    (is (= ["FixKit"] (samples/kit-names)))
    (let [s (samples/format-banks)]
      (is (str/includes? s "kits (1)"))
      (is (str/includes? s "FixKit: bd  hh  (2 vel × 3 takes)")))
    (reset! samples/registry ro)
    (reset! samples/layers lo)))

(deftest layered-plan-velocity-and-no-repeat
  (let [lo @samples/layers
        ac #js {}
        {:keys [entries]} (samples/parse-manifest kit-manifest)]
    (reset! samples/layers entries)
    (let [soft (samples/layered-plan ac :FixKit_bd 0.1 1.0 {:onset [0 1]})
          hard (samples/layered-plan ac :FixKit_bd 0.9 1.0 {:onset [0 1]})]
      (is (str/includes? (:url soft) "/s"))
      (is (str/includes? (:url hard) "/h")))
    (is (nil? (samples/layered-plan ac :FixKit_bd 0 1.0 {})) "velocity 0 plays nothing")
    (let [urls (mapv #(:url (samples/layered-plan ac :FixKit_bd 0.9 1.0 {:onset [% 8]})) (range 40))]
      (is (every? (fn [[a b]] (not= a b)) (partition 2 1 urls)))
      (is (> (count (set urls)) 1)))
    (is (= [:kit "FixKit" "hat"] (:cut (samples/layered-plan ac :FixKit_hh 1 1.0 {}))))
    (is (nil? (:cut (samples/layered-plan ac :FixKit_hh 1 1.0 {:cut 3}))) "explicit :cut wins")
    (reset! samples/layers lo)))

(deftest layered-plan-reproducible-per-context
  (let [lo @samples/layers
        {:keys [entries]} (samples/parse-manifest kit-manifest)
        run #(let [ac #js {}]
               (mapv (fn [i] (:url (samples/layered-plan ac :FixKit_bd 0.9 1.0 {:onset [i 4]})))
                     (range 16)))]
    (reset! samples/layers entries)
    (is (= (run) (run)) "a fresh context replays the same takes")
    (reset! samples/layers lo)))

(deftest format-banks-filter
  (let [ro @samples/registry]
    (reset! samples/registry {"tk_bd" ["a.wav"] "tk_sd" ["b.wav"] "bd" ["c.wav"]})
    (is (str/includes? (samples/format-banks "tk") "tk"))
    (is (not (str/includes? (samples/format-banks "tk") "general")))
    (is (str/includes? (samples/format-banks :TK) "2 sample banks"))
    (is (str/includes? (samples/format-banks "zzz") "No sample banks match"))
    (reset! samples/registry ro)))
