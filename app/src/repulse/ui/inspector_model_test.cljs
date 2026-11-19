(ns repulse.ui.inspector-model-test
  (:require [cljs.test :refer [deftest is]]
            [repulse.ui.inspector-model :as im]))

(deftest track-params-test
  (is (= {:amp 0.6 :decay 0.25 :synth :saw}
         (im/track-params [{:note :c2 :amp 0.6 :decay 0.25 :synth :saw} {:amp 0.9} :bd])))
  (is (= {} (im/track-params [:bd :sd]))))

(deftest param-rows-test
  (let [rows (im/param-rows {:amp 0.6 :decay 0.25 :synth :saw})]
    (is (= [:amp :decay] (map first (:sliders rows))))
    (is (= [[:synth "saw"]] (:text rows)))
    (is (= {:min 0 :max 1 :step 0.01} (nth (first (:sliders rows)) 2)))))

(deftest fx-rows-test
  (is (= [["filter" 1] ["reverb" 2]]
         (map (juxt :name :pos)
              (im/fx-rows [{:name "filter" :params {"freq" 2400 "q" 1}}
                           {:name "reverb" :bypassed? true :params {"wet" 0.3}}]))))
  (is (= ["freq" "q"]
         (map first (:sliders (first (im/fx-rows [{:name "filter" :params {"q" 1 "freq" 2400}}]))))))
  (is (= [] (:sliders (second (im/fx-rows [{:name "filter" :params {}}
                                           {:name "reverb" :bypassed? true :params {"wet" 0.3}}])))))
  (is (= [["ir" "4x12" ["1x12" "2x12" "4x12" "di"]]]
         (:selects (first (im/fx-rows [{:name "cab" :params {"mix" 1 "ir" "4x12"}}]))))))

(deftest track-flags-test
  ;; Phase WS5: flags come from the code (declared), not from the muted set
  (let [d {:drums {:mute? false :solo? true} :bass {:mute? false :solo? false}
           :keys {:mute? true :solo? false}}]
    (is (= {:muted? false :solo? true :silenced? false} (im/track-flags d #{:bass :keys} :drums)))
    (is (= {:muted? false :solo? false :silenced? true} (im/track-flags d #{:bass :keys} :bass)))
    (is (= {:muted? true :solo? false :silenced? false} (im/track-flags d #{:bass :keys} :keys)))
    (is (= {:muted? false :solo? false :silenced? false} (im/track-flags {} #{} :drums)))))

(deftest actions-test
  (is (= [:flag :bass :mute false] (im/mute-action {:muted? true} :bass)))
  (is (= [:flag :bass :mute true] (im/mute-action {:muted? false} :bass)))
  (is (= [:flag :drums :solo true] (im/solo-action {:solo? false} :drums)))
  (is (= [:flag :drums :solo false] (im/solo-action {:solo? true} :drums))))

(deftest section-tracks-test
  (is (= [":drums" ":bass"]
         (im/section-tracks {:tracks [":all"]} [{:name ":drums"} {:name ":bass"}])))
  (is (= [":drums"] (im/section-tracks {:tracks [":drums"]} [{:name ":drums"} {:name ":bass"}]))))
