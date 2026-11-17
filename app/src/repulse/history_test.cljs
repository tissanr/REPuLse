(ns repulse.history-test
  (:require [cljs.test :refer [deftest is testing]]
            [repulse.history :as h]))

(defn st [code]
  {:v 3 :main "main" :active "main" :buffers [{:name "main" :code code}]
   :bpm 120 :fx [] :bank nil :sources [] :muted [] :midi {}})

(def tA (h/state->blob (st "A")))
(def tB (h/state->blob (st "B")))
(def tC (h/state->blob (st "C")))
(def tD (h/state->blob (st "D")))
(def hA (h/content-hash tA))
(def hB (h/content-hash tB))
(def hC (h/content-hash tC))
(def hD (h/content-hash tD))
(def s0 h/empty-store)

(defn- m [s] (pr-str (h/meta-of s)))
(defn- ids [s] (mapv :id (:history s)))
(defn- app [s [t now ok? err]] (:store (h/append-eval s t now ok? err)))

(deftest h1-hashing
  (is (= 0x811c9dc5 (h/fnv1a32 "")))
  (is (= 0xe40c292c (h/fnv1a32 "a")))
  (is (= 0xbf9cf968 (h/fnv1a32 "foobar")))
  (is (= "811c9dc5811c9dc5" (h/content-hash "")))
  (is (= "e40c292ce40c292c" (h/content-hash "a")))
  (is (= "bf9cf9686f5d6112" (h/content-hash "foobar"))))

(deftest h2-blobs
  (let [t (h/state->blob (st "(seq :bd :sd)"))]
    (is (= "{:active \"main\", :bank nil, :bpm 120, :buffers [{:code \"(seq :bd :sd)\", :name \"main\"}], :fx [], :main \"main\", :midi {}, :muted [], :sources [], :v 3}" t))
    (is (= 149 (count t)))
    (is (= "d76c73930618fa63" (h/content-hash t)))
    (is (= t (h/state->blob (assoc (st "(seq :bd :sd)") :active "x"))))
    (is (= t (h/state->blob (into {} (reverse (seq (st "(seq :bd :sd)"))))))))
  (is (= 137 (count tA)))
  (is (= "55af15e1d7cae5c1" hA))
  (is (= "82552244434d49bc" hB))
  (is (= "133ff32f9f2214cf" hC))
  (is (= "ba436872783b1bb2" hD))
  (is (= "{:active \"main\", :bank nil, :bpm 120, :buffers [{:code \"x\", :name \"main\"} {:code \"y\\n\\\"q\\\"\", :name \"drums\"}], :fx [], :main \"main\", :midi {\"1\" \"amp\"}, :muted [], :sources [{:id \"a/b\", :type \"github\"}], :v 3}"
         (h/state->blob (assoc (st "x")
                               :buffers [{:name "main" :code "x"} {:name "drums" :code "y\n\"q\""}]
                               :midi {"1" "amp"}
                               :sources [{:type "github" :id "a/b"}])))))

(deftest h3-blob->state
  (is (= (st "A") (h/blob->state tA)))
  (is (nil? (h/blob->state "{:v 2}")))
  (is (nil? (h/blob->state "{{"))))

(deftest h4-collapse
  (let [r1 (h/append-eval s0 tA 1000 true nil)
        r2 (h/append-eval (:store r1) tA 2000 true nil)
        r3 (h/append-eval (:store r2) tB 3000 false "Undefined symbol: qqq")
        r4 (h/append-eval (:store r3) tB 4000 true nil)
        r5 (h/append-eval (:store r4) tA 5000 true nil)
        s  (:store r5)]
    (is (= [{:id 1 :t 2000 :hash hA :kind :eval :ok? true :error nil}
            {:id 2 :t 4000 :hash hB :kind :eval :ok? true :error nil}
            {:id 3 :t 5000 :hash hA :kind :eval :ok? true :error nil}]
           (:history s)))
    (is (= 4 (:next-id s)))
    (is (= #{hA hB} (set (keys (:blobs s)))))
    (is (= [[:put-blob hA tA] [:put-meta (m (:store r1))]] (:ops r1)))
    (is (= [[:put-meta (m (:store r2))]] (:ops r2)))
    (is (= 2 (:id r3)))
    (is (= "Undefined symbol: qqq" (:error (h/entry-by-id (:store r3) 2))))
    (is (= 200 (count (:error (h/entry-by-id
                               (app s0 [tA 1 false (apply str (repeat 300 "e"))]) 1)))))))

(deftest h5-count-eviction
  (let [lim {:max-entries 3 :max-chars 20000000}
        a (fn [s t now] (h/append-eval s t now true nil lim))
        r1 (a s0 tA 1) r2 (a (:store r1) tB 2) r3 (a (:store r2) tC 3) r4 (a (:store r3) tD 4)]
    (is (= [2 3 4] (ids (:store r4))))
    (is (= #{hB hC hD} (set (keys (:blobs (:store r4))))))
    (is (= [[:put-blob hD tD] [:put-meta (m (:store r4))] [:delete-blob hA]] (:ops r4))))
  (let [lim {:max-entries 3 :max-chars 20000000}
        a (fn [s t now] (h/append-eval s t now true nil lim))
        s (:store (h/save-snapshot s0 "keep" tA 0))
        r1 (a s tA 1) r2 (a (:store r1) tB 2) r3 (a (:store r2) tC 3) r4 (a (:store r3) tD 4)]
    (is (= [2 3 4] (ids (:store r4))))
    (is (= #{hA hB hC hD} (set (keys (:blobs (:store r4))))))
    (is (= [[:put-blob hD tD] [:put-meta (m (:store r4))]] (:ops r4))))
  (let [s (reduce (fn [s i] (app s [(h/state->blob (st (str i))) i true nil]))
                  s0 (range 501))]
    (is (= 500 (count (:history s))))
    (is (= (range 2 502) (ids s)))))

(deftest h6-size-eviction
  (let [lim {:max-entries 500 :max-chars 300}
        a (fn [s t now] (:store (h/append-eval s t now true nil lim)))
        s (-> s0 (a tA 1) (a tB 2) (a tC 3))]
    (is (= [2 3] (ids s)))
    (is (= #{hB hC} (set (keys (:blobs s)))))
    (is (= 274 (h/total-chars s))))
  (let [lim {:max-entries 500 :max-chars 300}
        a (fn [s t now] (:store (h/append-eval s t now true nil lim)))
        s (-> (:store (h/save-snapshot s0 "keep" tA 0)) (a tA 1) (a tB 2) (a tC 3))]
    (is (= [3] (ids s)))
    (is (= #{hA hC} (set (keys (:blobs s))))))
  (let [s (:store (h/append-eval s0 tA 1 true nil {:max-entries 500 :max-chars 100}))]
    (is (= [1] (ids s)))))

(deftest h7-snapshots
  (let [r1 (h/save-snapshot s0 "a" tA 10)
        r2 (h/save-snapshot (:store r1) "b" tB 20)
        s2 (:store r2)
        r3 (h/save-snapshot s2 "a" tC 30)
        s3 (:store r3)]
    (is (= :saved (:status r1)))
    (is (= ["b" "a"] (h/snapshot-names s2)))
    (is (= :updated (:status r3)))
    (is (= ["a" "b"] (h/snapshot-names s3)))
    (is (= [[:put-blob hC tC] [:put-meta (m s3)] [:delete-blob hA]] (:ops r3)))
    (is (= ["a" "c"] (h/snapshot-names (:store (h/rename-snapshot s3 "b" "c")))))
    (let [s4 (:store (h/rename-snapshot s3 "b" "c"))]
      (is (= {:error "a snapshot named \"c\" already exists"} (h/rename-snapshot s4 "a" "c")))
      (let [d (h/delete-snapshot s4 "a")]
        (is (= ["c"] (h/snapshot-names (:store d))))
        (is (= [[:put-meta (m (:store d))] [:delete-blob hC]] (:ops d)))))
    (is (= {:error "unknown snapshot \"zzz\" — (snapshots) lists the saved names"}
           (h/delete-snapshot s3 "zzz"))))
  (is (= "x" (:name (h/save-snapshot s0 "  x  " tA 1))))
  (is (= {:error "snapshot name must be 1–64 characters"} (h/save-snapshot s0 "   " tA 1)))
  (is (= {:error "snapshot name must be 1–64 characters"}
         (h/save-snapshot s0 (apply str (repeat 65 "n")) tA 1)))
  (let [s (reduce (fn [s i] (:store (h/save-snapshot s (str "s" i) tA i))) s0 (range 100))]
    (is (= {:error "too many snapshots (max 100) — delete one with (delete-snapshot! \"name\")"}
           (h/save-snapshot s "s100" tA 1)))
    (is (= :updated (:status (h/save-snapshot s "s5" tA 1)))))
  (let [s (:store (h/save-snapshot s0 "take A" tA 1))]
    (is (= {:error "unknown snapshot \"take B\" — did you mean \"take A\"?"}
           (h/resolve-target s "take B")))))

(defn- h8-store []
  (-> s0
      (app [tA 1000 true nil])
      (as-> s (:store (h/save-snapshot s "take A" tA 2000)))
      (app [tB 3000 true nil])))

(deftest h8-restore
  (let [s1 (h8-store)
        r  (h/prepare-restore s1 "take A" tC 4000)
        s2 (:store r)]
    (is (= tA (:text r)))
    (is (= "snapshot \"take A\"" (:label r)))
    (is (= 3 (:restore-point r)))
    (is (= [1 2 3] (ids s2)))
    (is (= {:id 3 :t 4000 :hash hC :kind :restore-point :ok? nil :error nil}
           (h/entry-by-id s2 3)))
    (is (= "=> restored snapshot \"take A\" — previous state is history #3"
           (h/restore-message "snapshot \"take A\"" 3)))
    (let [r2 (h/prepare-restore s2 3 tA 5000)]
      (is (= tC (:text r2)))
      (is (= "history #3" (:label r2)))
      (is (= 4 (:restore-point r2)))
      (is (= hA (:hash (h/entry-by-id (:store r2) 4))))
      (is (= :restore-point (:kind (h/entry-by-id (:store r2) 4))))))
  (let [s1 (h8-store)
        r  (h/prepare-restore s1 "take A" tB 4000)]
    (is (= 2 (:restore-point r)))
    (is (= (:history s1) (:history (:store r))))
    (is (= [] (:ops r))))
  (let [s1 (h8-store)]
    (is (= {:error "unknown history entry #99"} (h/prepare-restore s1 99 tC 4000)))
    (let [r (h/prepare-restore s1 "nope" tC 4000)]
      (is (string? (:error r)))
      (is (nil? (:store r))))))

(deftest h9-clear
  (let [lim {:max-entries 3 :max-chars 20000000}
        a (fn [s t now] (:store (h/append-eval s t now true nil lim)))
        s (-> (:store (h/save-snapshot s0 "keep" tA 0)) (a tA 1) (a tB 2) (a tC 3) (a tD 4))
        c (:store (h/clear-history s))]
    (is (= [] (:history c)))
    (is (= #{hA} (set (keys (:blobs c)))))
    (is (= 5 (:next-id c)))
    (is (= 5 (:id (h/append-eval c tA 9 true nil))))))

(deftest h10-load-repair
  (let [s (-> s0
              (as-> s (:store (h/save-snapshot s "a" tA 10)))
              (as-> s (:store (h/save-snapshot s "b" tB 20)))
              (as-> s (:store (h/save-snapshot s "a" tC 30)))
              (as-> s (:store (h/rename-snapshot s "b" "c")))
              (as-> s (:store (h/delete-snapshot s "a"))))]
    (is (= {:store s :ops []} (h/load->store (m s) (:blobs s)))))
  (let [meta (pr-str {:version 1 :next-id 3
                      :history [{:id 1 :t 1 :hash hA :kind :eval :ok? true :error nil}
                                {:id 2 :t 2 :hash "deadbeefdeadbeef" :kind :eval :ok? true :error nil}]
                      :snapshots []})
        r (h/load->store meta {hA tA hB tB})]
    (is (= [1] (ids (:store r))))
    (is (= 3 (:next-id (:store r))))
    (is (= [[:put-meta (m (:store r))] [:delete-blob hB]] (:ops r))))
  (doseq [bad ["{{" "[1 2]"]]
    (is (= {:store (assoc h/empty-store :blobs {})
            :ops [[:put-meta (m h/empty-store)] [:delete-blob hA]]}
           (h/load->store bad {hA tA})))))

(deftest h10-memory-round-trip
  (let [mem (h/memory-storage)
        w   (fn [r] (h/-write! mem (:ops r)) (:store r))
        s   (-> s0
                (as-> s (w (h/append-eval s tA 1000 true nil)))
                (as-> s (w (h/save-snapshot s "take A" tA 2000)))
                (as-> s (w (h/append-eval s tB 3000 true nil)))
                (as-> s (w (h/prepare-restore s "take A" tC 4000)))
                (as-> s (w (h/prepare-restore s 3 tA 5000))))
        loaded (atom nil)]
    (h/-load mem #(reset! loaded %))
    (is (= {:store s :ops []} (h/load->store (:meta @loaded) (:blobs @loaded))))))

(deftest h11-line-diff
  (is (= [[:same "a"] [:del "b"] [:same "c"] [:add "d"]] (h/line-diff "a\nb\nc" "a\nc\nd")))
  (is (= [[:add "x"] [:add "y"]] (h/line-diff "" "x\ny")))
  (is (= [[:del "x"]] (h/line-diff "x" "")))
  (is (= [[:same "a"] [:same "b"]] (h/line-diff "a\nb" "a\nb")))
  (is (= [[:del "a"] [:add "b"]] (h/line-diff "a" "b")))
  (is (= [[:same "a"] [:del ""]] (h/line-diff "a\n" "a")))
  (is (= [[:same "(def a 1)"] [:del "(def b 2)"] [:same "(def c 3)"]
          [:del "(seq :bd)"] [:add "(def b 2)"] [:add "(seq :sd)"]]
         (h/line-diff "(def a 1)\n(def b 2)\n(def c 3)\n(seq :bd)"
                      "(def a 1)\n(def c 3)\n(def b 2)\n(seq :sd)")))
  (let [mk (fn [pre n] (map #(str pre %) (range 1 (inc n))))
        join (fn [ls] (apply str (interpose "\n" ls)))
        d1 (h/line-diff (join (cons "c" (mk "o" 1000))) (join (concat (mk "n" 1000) ["c"])))]
    (is (= 2002 (count d1)))
    (is (not-any? #(= :same (first %)) d1))
    (is (= [:del "c"] (nth d1 0)))
    (is (= [:add "n1"] (nth d1 1001))))
  (let [mk (fn [pre n] (map #(str pre %) (range 1 (inc n))))
        join (fn [ls] (apply str (interpose "\n" ls)))
        d2 (h/line-diff (join (cons "c" (mk "o" 998))) (join (concat (mk "n" 998) ["c"])))]
    (is (= 1997 (count d2)))
    (is (= [[:same "c"]] (filter #(= :same (first %)) d2)))
    (is (= [:add "n1"] (nth d2 0)))))

(deftest h12-project-diff
  (let [old {:buffers [{:name "main" :code "(use :drums)\n(fast 2 kick)"}
                       {:name "drums" :code "(def kick (seq :bd :sd))"}]}
        new {:buffers [{:name "main" :code "(use :drums)\n(fast 4 kick)"}
                       {:name "bass" :code "(def b 1)"}]}
        d (h/project-diff old new)]
    (is (= [{:buffer "main" :status :changed
             :lines [[:same "(use :drums)"] [:del "(fast 2 kick)"] [:add "(fast 4 kick)"]]}
            {:buffer "bass" :status :added :lines [[:add "(def b 1)"]]}
            {:buffer "drums" :status :removed :lines [[:del "(def kick (seq :bd :sd))"]]}]
           d))
    (is (= {:added 2 :removed 2} (h/diff-stats d)))
    (is (= "+2 -2" (h/summary-text (h/diff-stats d)))))
  (is (= [{:buffer "main" :status :added :lines [[:add "A"] [:add "B"]]}]
         (h/project-diff nil (st "A\nB"))))
  (let [d (h/project-diff (st "A") (st "A"))]
    (is (every? #(= :same (:status %)) d))
    (is (= "no changes" (h/summary-text (h/diff-stats d))))))
