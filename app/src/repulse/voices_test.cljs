(ns repulse.voices-test
  (:require [cljs.test :refer [deftest is]]
            [repulse.voices :as voices]))

;;; ── Trigger strings ──────────────────────────────────────────────────

(deftest voice-string-fields
  (is (= "saw:440" (voices/voice-string "saw" [440] {} [:unison :detune])))
  (is (= "saw:440:unison=3:detune=12"
         (voices/voice-string "saw" [440] {:detune 12 :unison 3 :pan 0.5} [:unison :detune]))
      "order of ks, absent and unlisted keys omitted")
  (is (= "noise:color=pink" (voices/voice-string "noise" [] {:color :pink} [:color]))
      "keywords by name"))

(deftest osc-string-defaults
  (is (= "saw:440" (voices/osc-string {:synth :saw} 440)))
  (is (= "saw:440:unison=7:detune=20:spread=0.7"
         (voices/osc-string (voices/with-defaults {:synth :supersaw}) 440)))
  (is (= "saw:440:unison=7:detune=18:spread=0.7"
         (voices/osc-string (voices/with-defaults {:synth :supersaw :detune 18}) 440))
      "explicit detune overrides the supersaw default")
  (is (= "square:220:pw=0.25" (voices/osc-string {:synth :pulse :pw 0.25} 220)))
  (is (= "tri:440:sub-osc=0.5" (voices/osc-string {:synth :tri :sub-osc 0.5} 440)))
  (is (= "saw:261.63:glide-from=130.81:glide=0.1"
         (voices/osc-string {:synth :saw :glide 0.1 :glide-from 130.81} 261.63))))

(deftest drum-and-noise-strings
  (is (= "drum:bd" (voices/drum-string "bd" nil)))
  (is (= "drum:bd:tune=-5:decay=0.8"
         (voices/drum-string "bd" {:note :bd :decay 0.8 :tune -5 :amp 0.5})))
  (is (= "drum:hh:tone=0.2" (voices/drum-string "hh" {:tone 0.2})))
  (is (= "noise" (voices/noise-string {:synth :noise} nil)))
  (is (= "noise:color=lfsr:freq=440"
         (voices/noise-string {:synth :noise :color :lfsr} 440))))

;;; ── Unison layout (worked rows of SYN6 A3) ───────────────────────────

(defn- close? [a b] (< (Math/abs (- a b)) 0.01))

(deftest unison-layout-worked-rows
  (let [row (fn [n] (mapv (juxt :cents :pan) (voices/unison-layout n 20 1)))
        same? (fn [a b] (every? true? (map (fn [[c q] [c' q']] (and (close? c c') (close? q q'))) a b)))]
    (is (same? (row 1) [[0 0]]))
    (is (same? (row 2) [[-10 -1] [10 1]]))
    (is (same? (row 3) [[-10 -1] [0 0] [10 1]]))
    (is (same? (row 7) [[-10 -1] [-5.44 0.6667] [-1.92 -0.3333] [0 0]
                        [1.92 0.3333] [5.44 -0.6667] [10 1]])))
  (is (every? #(zero? (:pan %)) (voices/unison-layout 7 20 0)) "spread 0 = mono"))

;;; ── Drum routing ─────────────────────────────────────────────────────

(deftest drum-route-rules
  ;; Default-kit decision 2026-11-21: bare kit names play the synth kit.
  (let [banks (fn [& ks] (fn [k] (contains? (set ks) k)))]
    (is (= {:kind :synth-drum :name "bd"} (voices/drum-route :bd nil (banks :bd)))
        "the synth kit is the default, even with a bd sample bank loaded")
    (is (= {:kind :synth-drum :name "bd"} (voices/drum-route :bd nil (banks))))
    (is (= {:kind :synth-drum :name "bd"} (voices/drum-route :bd "synth" (banks :bd))))
    (is (= {:kind :synth-drum :name "cp"} (voices/drum-route :cp nil (banks :cp))))
    (is (= {:kind :sample :bank :bd} (voices/drum-route :bd "dirt" (banks :bd)))
        "(bank :dirt) restores the Dirt-Samples drums")
    (is (= {:kind :synth-drum :name "bd"} (voices/drum-route :bd "dirt" (banks)))
        "(bank :dirt) before the samples have loaded")
    (is (= {:kind :sample :bank :RolandTR808_bd}
           (voices/drum-route :bd "RolandTR808" (banks :RolandTR808_bd :bd))))
    (is (= {:kind :synth-drum :name "hh"}
           (voices/drum-route :hh "RolandTR808" (banks :RolandTR808_bd :hh)))
        "a machine without that drum falls back to the default kit")
    (is (= {:kind :synth-drum :name "bd"}
           (voices/drum-route :synth_bd "RolandTR808" (banks :RolandTR808_bd :synth_bd))))
    (is (= {:kind :sample :bank :tabla} (voices/drum-route :tabla "synth" (banks :tabla))))
    (is (= {:kind :sample :bank :tabla} (voices/drum-route :tabla nil (banks :tabla))))
    (is (= {:kind :sample :bank :tabla} (voices/drum-route :tabla "dirt" (banks :tabla))))
    (is (= {:kind :unknown} (voices/drum-route :foo nil (banks))))))

(deftest js-drum-plan
  (is (= {:kind :kick :f-start 150 :f-end 40 :sweep 0.06 :dur 0.4} (voices/js-drum "bd" nil)))
  (let [p (voices/js-drum "lt" {:tune 12 :decay 2})]
    (is (close? 262 (:f-start p)))
    (is (close? 164 (:f-end p)))
    (is (= 2 (:dur p))))
  (is (every? some? (map #(voices/js-drum % nil) voices/drum-voices))
      "every kit voice has a JS fallback"))

;;; ── Presets and accent ───────────────────────────────────────────────

(deftest expand-preset-overrides
  (let [v (voices/expand-preset {:note :f3 :synth :pad :lpf 900})]
    (is (= :saw (:synth v)))
    (is (= 900 (:lpf v)) "explicit parameter wins")
    (is (= 3 (:unison v)))
    (is (= 0.35 (:attack v))))
  (is (= {:note :c4 :synth :saw} (voices/expand-preset {:note :c4 :synth :saw}))
      "non-preset events unchanged")
  (is (= :fm (:synth (voices/expand-preset {:note :c2 :synth :fm-bass})))))

(deftest accent-expansion
  (let [v (voices/expand-preset {:note :c2 :synth :acid :accent 1})]
    (is (close? 1.5 (:amp v)))
    (is (close? 5.0 (:lp-env v)))
    (is (close? 0.108 (:lp-decay v))))
  (let [v (voices/expand-preset {:note :c2 :synth :saw :accent 0.5 :amp 0.8})]
    (is (close? 1.0 (:amp v)))
    (is (nil? (:lp-env v)) "no filter envelope → amp only")))

(deftest lp-lfo-phase-locked
  (is (nil? (voices/lp-lfo {:lp-rate 4 :lp-depth 2} 0 2)) "needs a low-pass")
  (let [l (voices/lp-lfo {:lpf 400 :lp-rate 4 :lp-depth 2} 0.75 2)]
    (is (= 2 (:rate l)) "4 periods per 2-second cycle = 2 Hz")
    (is (close? 0.5 (:phase l)))
    (is (= 2 (:depth l)))))

;;; ── Glide ────────────────────────────────────────────────────────────

(defn- ev [t hz & [glide]]
  {:t t :hz hz :value (cond-> {:note hz :synth :saw} glide (assoc :glide glide))})

(deftest assign-glide-line
  (let [[es st] (voices/assign-glide nil [(ev 0 100 0.1) (ev 1 200 0.1) (ev 2 300)])]
    (is (nil? (get-in (first es) [:value :glide-from])) "first note after empty state")
    (is (= 100 (get-in (second es) [:value :glide-from])))
    (is (nil? (get-in (nth es 2) [:value :glide-from])) "no :glide → no glide")
    (is (= {:t 2 :cur [300] :prev [200]} st))))

(deftest assign-glide-chords-pair-by-pitch
  (let [chord1 [(ev 0 100) (ev 0 200) (ev 0 300)]
        chord2 [(ev 1 210 0.1) (ev 1 110 0.1) (ev 1 290 0.1)]
        [es _]  (voices/assign-glide nil (concat chord2 chord1))
        froms   (into {} (for [e (drop 3 es)] [(:hz e) (get-in e [:value :glide-from])]))]
    (is (= {210 200 110 100 290 300} froms) "unsorted input, nearest member")))

(deftest assign-glide-carries-state
  (let [[_ st]  (voices/assign-glide nil [(ev 0 100)])
        [es _]  (voices/assign-glide st [(ev 1 150 0.2)])]
    (is (= 100 (get-in (first es) [:value :glide-from])))))

(deftest preset-glide-counts
  (let [[es _] (voices/assign-glide nil [{:t 0 :hz 200 :value {:note 200 :synth :theremin}}
                                          {:t 1 :hz 300 :value {:note 300 :synth :theremin}}])]
    (is (= 200 (get-in (second es) [:value :glide-from])))))

;;; ── Drift guards ─────────────────────────────────────────────────────

(def ^:private fs (js/require "fs"))

(defn- slurp' [path] (.readFileSync fs path "utf8"))

(defn- rust-list [src const]
  (let [m (re-find (re-pattern (str "const " const ": \\[&str; \\d+\\] = \\[([^\\]]*)\\]")) src)]
    (mapv second (re-seq #"\"([^\"]+)\"" (second m)))))

(deftest rust-voice-lists-match
  (let [src (slurp' "packages/audio/src/lib.rs")]
    (is (= (set (map name voices/fm-presets)) (set (rust-list src "FM_PRESETS"))))
    (is (= (set (map name voices/string-presets)) (set (rust-list src "KS_PRESETS"))))
    (is (= voices/drum-voices (rust-list src "DRUM_VOICES")))))

(deftest every-voice-is-registered
  (let [labels (set (map second (re-seq #"label: \"([^\"]+)\""
                                         (slurp' "app/src/repulse/lisp-lang/completions.js"))))
        metas  (set (map second (re-seq #"(?m)^[\s{]*:([a-z0-9-]+)\s+\{"
                                        (slurp' "app/src/repulse/content/builtin_meta.edn"))))
        names  (map name voices/synth-voices)]
    (is (= [] (remove labels names)) "voices missing in completions.js")
    (is (= [] (remove metas names)) "voices missing in builtin_meta.edn")))
