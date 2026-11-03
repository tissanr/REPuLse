(ns repulse.outline-test
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            ["./lisp-lang/parser.js" :refer [parser]]
            [repulse.outline :as outline]
            [repulse.song :as song]))

(def R
  (str/join "\n"
    ["(def kick (seq :bd :_ :bd :_))"
     "(def snare (seq :_ :sd))"
     "; (def ghost (seq :x)) — commented out, must not appear"
     "(defn humanize [p] (amp 0.9 p))"
     "(defsynth pad [freq] (saw freq))"
     "(track :drums (stack kick snare (seq \"(not a paren)\")))"
     "(track :bass (seq :c2 :eb2))"
     "(song"
     "  (section :intro 4 [:drums])"
     "  (section :verse 8 [:drums :bass])"
     "  (section :outro 2 :all))"]))

(defn- ex [text] (outline/extract (.parse parser text) text))

(defn- line-of [text pos] (inc (count (filter #(= % "\n") (subs text 0 pos)))))

(deftest extract-reference-buffer
  (let [{:keys [entries partial?]} (ex R)
        summary (mapv (fn [e] [(:kind e) (:name e) (line-of R (:from e))]) entries)]
    (is (false? partial?))
    (is (= [[:def "kick" 1] [:def "snare" 2] [:defn "humanize" 4]
            [:defsynth "pad" 5] [:track ":drums" 6] [:track ":bass" 7]
            [:song nil 8]]
           summary))
    (is (not-any? #(= "ghost" (:name %)) entries))
    (let [song-e (last entries)
          secs   (:children song-e)]
      (is (= [[":intro" 4 [":drums"]] [":verse" 8 [":drums" ":bass"]] [":outro" 2 [":all"]]]
             (mapv (juxt :name :bars :tracks) secs)))
      (is (= 14 (outline/song-bars song-e))))
    (let [kick (first entries)]
      (is (= "kick" (subs R (:name-from kick) (:name-to kick))))
      (is (= 0 (:from kick))))))

(deftest parens-in-strings
  (is (= ["kick" "snare" "humanize" "pad" ":drums" ":bass"]
         (mapv :name (remove #(= :song (:kind %)) (:entries (ex R))))))
  (let [t "(def a (seq \"((( \"))\n(def b 1)"]
    (is (= ["a" "b"] (mapv :name (:entries (ex t)))))))

(deftest nested-def-not-listed
  (is (= ["x"] (mapv :name (:entries (ex "(def x (def y 1))"))))))

(deftest partial-tree
  (let [broken (str/replace R "(seq \"(not a paren)\")))" "(seq \"(not a paren)\"))")
        {:keys [entries partial?]} (ex broken)
        names (set (map (juxt :kind :name) entries))]
    (is (not= broken R))
    (is (true? partial?))
    (doseq [n [[:def "kick"] [:def "snare"] [:defn "humanize"] [:defsynth "pad"]]]
      (is (contains? names n) (str n)))))

(deftest empty-and-unnamed
  (is (= {:entries [] :partial? false} (ex "")))
  (is (= [] (:entries (ex "(def)\n(track 1 2)\n(seq :bd)")))))

(deftest unclosed-last-form
  (let [{:keys [entries partial?]} (ex "(def a 1)\n(def b (seq :bd")]
    (is (true? partial?))
    (is (= ["a" "b"] (mapv :name entries)))))

(deftest definition-for-last-wins
  (let [t "(def kick 1)\n(def snare 2)\n(def kick 3)"
        es (:entries (ex t))
        d  (outline/definition-for es :symbol "kick")]
    (is (= (:from (second es)) (:from (outline/definition-for es :symbol "snare"))))
    (is (= (:from (nth es 2)) (:from d)))
    (is (nil? (outline/definition-for es :symbol "nope")))
    (is (nil? (outline/definition-for es :keyword "kick"))))
  (let [es (:entries (ex R))]
    (is (= :track (:kind (outline/definition-for es :keyword ":bass"))))
    (is (nil? (outline/definition-for es :symbol ":bass")))))

(deftest entry-at-test
  (let [es  (:entries (ex R))
        pos (+ (str/index-of R ":verse") 2)
        e   (outline/entry-at es pos)]
    (is (= :section (:kind e)))
    (is (= ":verse" (:name e)))
    (is (= :song (:kind (outline/entry-at es (+ (str/index-of R "(song") 1)))))
    (is (= "kick" (:name (outline/entry-at es 3))))
    (is (nil? (outline/entry-at es (str/index-of R "; (def ghost") ))
        "comment between forms")))

(deftest playing-section-test
  (let [sections (:sections
                   (song/make-song [(song/make-section :intro 4 [:drums])
                                    (song/make-section :verse 8 [:drums :bass])
                                    (song/make-section :outro 2 :all)]
                                   {} [:drums :bass] (fn [_ _] nil)))
        s        (song/make-song [(song/make-section :intro 4 [:drums])
                                  (song/make-section :verse 8 [:drums :bass])
                                  (song/make-section :outro 2 :all)]
                                 {} [:drums :bass] (fn [_ _] nil))
        outline-secs (:children (last (:entries (ex R))))
        at (fn [bar] (outline/playing-section s bar outline-secs))]
    (is (= 3 (count sections)))
    (is (= {:index 0 :section-bar 0 :section-bars 4} (at 0)))
    (is (= 0 (:index (at 3))))
    (is (= 1 (:index (at 4))))
    (is (= {:index 1 :section-bar 7 :section-bars 8} (at 11)))
    (is (= {:index 2 :section-bar 0 :section-bars 2} (at 12)))
    (is (= 2 (:index (at 13))))
    (is (nil? (at 14)) "past end of a :stop song")
    (is (nil? (at -1)))
    (testing "name mismatch (text edited since eval) → nil"
      (is (nil? (outline/playing-section s 0 [{:name ":other"}]))))))
