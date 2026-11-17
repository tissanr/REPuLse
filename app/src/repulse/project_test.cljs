(ns repulse.project-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.string :as str]
            [repulse.project :as p]))

(def B p/OFFSET-STRIDE)

(defn- proj [main & names]
  {:main main :active main :buffers (mapv #(hash-map :name % :code "") names)})

(def p1 {:main "main" :active "main" :buffers [{:name "main" :code ""}]})

(deftest offset-bases-p1
  (is (= {"main" 0 "drums" B "bass" (* 2 B) "kit" (* 3 B) "scale" (* 4 B)}
         (p/offset-bases (proj "main" "main" "drums" "bass" "kit" "scale"))))
  (testing "main not first in tab order"
    (is (= {"main" 0 "drums" B} (p/offset-bases (proj "main" "drums" "main"))))))

(deftest locate-p2
  (let [bases {"main" 0 "drums" B}]
    (is (= {:buffer "drums" :from 5 :to 10} (p/locate bases (+ B 5) (+ B 10))))
    (is (= {:buffer "main" :from 3 :to 7} (p/locate bases 3 7)))
    (is (nil? (p/locate bases nil nil)))))

(deftest add-buffer-p3
  (is (= {:ok {:main "main" :active "drums"
               :buffers [{:name "main" :code ""} {:name "drums" :code ""}]}}
         (p/add-buffer p1 "drums" "")))
  (is (= {:error "invalid buffer name \"Drums!\" — use a-z, 0-9 and -, starting with a letter (max 32)"}
         (p/add-buffer p1 "Drums!" "")))
  (is (= {:error "a buffer named \"main\" already exists"} (p/add-buffer p1 "main" "")))
  (let [full (reduce (fn [acc i] (:ok (p/add-buffer acc (str "b" i) ""))) p1 (range 31))]
    (is (= 32 (count (:buffers full))))
    (is (= {:error "too many buffers (max 32)"} (p/add-buffer full "extra" ""))))
  (is (= "buffer-2" (p/next-buffer-name p1)))
  (is (= "buffer-3" (p/next-buffer-name (:ok (p/add-buffer p1 "buffer-2" "")))))
  (testing "name length limit"
    (is (:ok (p/add-buffer p1 (apply str "a" (repeat 31 "b")) "")))
    (is (:error (p/add-buffer p1 (apply str "a" (repeat 32 "b")) "")))))

(deftest rename-buffer-p4
  (is (= {:ok {:main "song" :active "song" :buffers [{:name "song" :code ""}]}}
         (p/rename-buffer p1 "main" "song")))
  (let [p (-> (:ok (p/add-buffer p1 "drums" "(track :kick (seq :bd))"))
              (assoc-in [:buffers 0 :code] "(use :drums)"))
        r (:ok (p/rename-buffer p "drums" "kit"))]
    (is (= ["main" "kit"] (p/buffer-names r)))
    (is (= "kit" (:active r)))
    (is (= "(use :drums)" (p/code-of r "main")) "use references are not rewritten"))
  (is (= {:error "unknown buffer \"foo\""} (p/rename-buffer p1 "foo" "bar")))
  (is (= {:error "invalid buffer name \"X\" — use a-z, 0-9 and -, starting with a letter (max 32)"}
         (p/rename-buffer p1 "main" "X"))))

(deftest remove-buffer-p5
  (is (= {:error "the main buffer can't be deleted"} (p/remove-buffer p1 "main")))
  (let [p3 (:ok (p/add-buffer p1 "drums" ""))]
    (is (= {:ok p1} (p/remove-buffer p3 "drums"))))
  (is (= {:error "unknown buffer \"foo\""} (p/remove-buffer p1 "foo")))
  (is (= {:ok (assoc p1 :active "main")} (p/set-active p1 "main")))
  (is (= {:error "unknown buffer \"foo\""} (p/set-active p1 "foo"))))

(def p6 {:main "main" :active "drums"
         :buffers [{:name "main" :code "(use :drums)\n(song (section :a 4 :all))"}
                   {:name "drums" :code "(track :kick (~ \"bd sd\"))"}]})

(def p6-text
  (str "{:version 3\n"
       " :main \"main\"\n"
       " :buffers [{:name \"main\" :code \"(use :drums)\\n(song (section :a 4 :all))\"}\n"
       "           {:name \"drums\" :code \"(track :kick (~ \\\"bd sd\\\"))\"}]}\n"))

(deftest edn-layout-and-round-trip-p6
  (is (= p6-text (p/project->edn p6)))
  (is (= {:ok (assoc p6 :active "main")} (p/edn->project (p/project->edn p6)))))

(defn- are-errors [pairs]
  (doseq [[text msg] pairs]
    (is (= {:error msg} (p/edn->project text))
        (subs text 0 (min 60 (count text))))))

(deftest edn-import-errors-p7
  (are-errors
   [["{:version 3 :main" "Import failed: could not read EDN"]
    ["[1 2]" "Import failed: expected a map"]
    ["{:version 2 :main \"main\" :buffers [{:name \"main\" :code \"\"}]}"
     "Import failed: unsupported version 2 — expected :version 3"]
    ["{:version 3 :main \"main\" :buffers []}"
     "Import failed: :buffers must be a non-empty vector"]
    ["{:version 3 :main \"main\" :buffers [{:name \"main\"}]}"
     "Import failed: each buffer needs a :name and a :code string"]
    ["{:version 3 :main \"main\" :buffers [{:name \"Drums!\" :code \"\"}]}"
     "Import failed: invalid buffer name \"Drums!\" — use a-z, 0-9 and -, starting with a letter (max 32)"]
    ["{:version 3 :main \"main\" :buffers [{:name \"main\" :code \"\"} {:name \"main\" :code \"\"}]}"
     "Import failed: duplicate buffer name \"main\""]
    ["{:version 3 :main \"song\" :buffers [{:name \"main\" :code \"\"}]}"
     "Import failed: main buffer \"song\" not found"]
    [(apply str (repeat 5000001 "x")) "Import failed: file too large (max 5 MB)"]]))

(deftest edn-extra-keys-are-dropped
  (let [r (p/edn->project "{:version 3 :main \"main\" :evil 1 :buffers [{:name \"main\" :code \"x\" :x 2}]}")]
    (is (= {:ok {:main "main" :active "main" :buffers [{:name "main" :code "x"}]}} r)))
  (is (str/starts-with? (p/project->edn p1) "{:version 3\n")))
