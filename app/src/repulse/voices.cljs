(ns repulse.voices
  "Built-in synth voices (Phase SYN6) — the one voice list on the CLJS side.
   Pure: no DOM, no audio. Used by the play-event dispatch (audio.cljs), the
   unknown-voice warning (sound_check.cljs), keyword completion and the tests.

   The Rust engine has the same three name lists (FM_PRESETS, KS_PRESETS,
   DRUM_VOICES in packages/audio/src/lib.rs); voices_test.cljs guards the drift.

   Trigger strings: \"<voice>[:<positional>]…[:<key>=<value>]…\" — named fields
   in any order, written only when the event has the key."
  (:require [clojure.string :as str]
            [repulse.theory :as theory]))

;;; ── Voice lists ──────────────────────────────────────────────────────

(def osc-voices
  "Band-limited oscillator voices (WASM Voice::Osc)."
  #{:saw :square :pulse :tri :sine :supersaw})

(def fm-presets
  "FM preset voices (WASM fm_preset)."
  #{:sax :trumpet :trumpet-muted :trumpet-cup :trombone :synth-brass
    :harmon-out :harmon-in :epiano :bell :marimba :flute})

(def string-presets
  "Karplus-Strong string voices (WASM ks_preset)."
  #{:western :nylon :guitar :harp :koto :pizz :lute :mandolin})

(def drum-voices
  "Synthesized drum kit (WASM drum_voice), as sound names."
  ["bd" "sd" "hh" "oh" "cp" "rim" "lt" "mt" "ht"
   "cb" "cr" "rd" "sh" "tb" "cl" "cg" "zap" "click"])

(def ^:private drum-set (set drum-voices))

(def instrument-presets
  "Synth instruments (Part C): ordinary event parameters merged under the
   event's own keys; :synth is the base voice. A preset only uses parameters a
   user could write by hand."
  {:acid      {:synth :saw :lpf 400 :resonance 12
               :lp-env 3.5 :lp-attack 0.003 :lp-decay 0.18
               :attack 0.003 :decay 0.35}
   :reese     {:synth :saw :unison 2 :detune 30 :spread 0.3
               :lpf 500 :resonance 1.5 :attack 0.01 :decay 2.5}
   :sub-bass  {:synth :sine :attack 0.005 :decay 0.8 :shape 0.3}
   :fm-bass   {:synth :fm :index 3 :ratio 1 :lpf 300 :resonance 1
               :lp-env 3 :lp-attack 0.002 :lp-decay 0.12
               :attack 0.002 :decay 0.5}
   :wobble    {:synth :saw :unison 2 :detune 15 :lpf 400 :resonance 6
               :lp-rate 4 :lp-depth 2 :attack 0.01 :decay 2.0}
   :lead      {:synth :saw :lpf 2800 :resonance 2 :attack 0.01 :decay 0.8
               :vib 5.5 :vib-mod 0.15}
   :sync-lead {:synth :saw :sync 4.5 :lpf 6000 :resonance 1
               :attack 0.005 :decay 0.8}
   :pluck     {:synth :saw :lpf 500 :resonance 1.2
               :lp-env 4 :lp-attack 0.001 :lp-decay 0.12
               :attack 0.001 :decay 0.4}
   :theremin  {:synth :sine :attack 0.06 :decay 1.5
               :glide 0.08 :vib 5 :vib-mod 0.25}
   :reed      {:synth :pulse :pw 0.3 :unison 2 :detune 14 :spread 0.2
               :bpf 1100 :bpq 0.8 :attack 0.03 :decay 1.2}
   :pad       {:synth :saw :unison 3 :detune 18 :spread 0.6
               :lpf 1400 :resonance 0.8 :attack 0.35 :decay 4.0}
   :strings   {:synth :saw :unison 5 :detune 22 :spread 0.8
               :lpf 3200 :hpf 180 :attack 0.3 :decay 4.0}
   :sweep-pad {:synth :saw :unison 7 :detune 20 :spread 0.9
               :lpf 300 :resonance 3
               :lp-env 4 :lp-attack 1.2 :lp-decay 2.0
               :attack 0.4 :decay 6.0}
   :brass-pad {:synth :saw :unison 3 :detune 10 :spread 0.4
               :lpf 500 :resonance 1
               :lp-env 2.5 :lp-attack 0.08 :lp-decay 0.6
               :attack 0.06 :decay 3.0}})

(def synth-voices
  "Every built-in (synth :name) voice."
  (into #{:fm :noise}
        (concat osc-voices fm-presets string-presets (keys instrument-presets))))

(defn osc-voice? [v] (contains? osc-voices v))

(defn drum-name?
  "True for one of the 18 synthesized drum names (string or keyword)."
  [n]
  (contains? drum-set (name n)))

;;; ── Trigger strings ──────────────────────────────────────────────────

(defn- fmt [v]
  (cond (keyword? v) (name v)
        (number? v)  (str (if (integer? v) v (double v)))
        :else        (str v)))

(defn voice-string
  "Pure. \"saw:440:unison=3:detune=12\" from a base, positional values and the
   event keys to pass on (only those present in value, in the order of ks)."
  [base positionals value ks]
  (str/join ":" (concat [base]
                        (map fmt positionals)
                        (for [k ks
                              :let [v (get value k)]
                              :when (some? v)]
                          (str (name k) "=" (fmt v))))))

(def ^:private osc-keys
  [:unison :detune :spread :sub-osc :sync :pw :pw-rate :pw-depth :glide-from :glide])

(def ^:private supersaw-defaults {:unison 7 :detune 20 :spread 0.7})

(defn with-defaults
  "Pure. Fill a voice's default parameters under the event's own keys
   (:supersaw → 7 oscillators, 20 cents, spread 0.7)."
  [value]
  (if (= :supersaw (:synth value))
    (merge supersaw-defaults value)
    value))

(defn osc-base
  "Engine voice name of an oscillator voice keyword."
  [synth]
  (case synth
    (:saw :supersaw) "saw"
    (:square :pulse) "square"
    :tri             "tri"
    :sine            "sine"))

(defn osc-string
  "Pure. Trigger string of an oscillator voice event at hz, e.g.
   \"saw:440:unison=7:detune=20:spread=0.7\"."
  [value hz]
  (voice-string (osc-base (:synth value)) [hz] value osc-keys))

(defn drum-string
  "Pure. \"drum:bd:tune=-5:decay=0.8\" for kit voice name and event value."
  [drum value]
  (voice-string "drum" [drum] (if (map? value) value {}) [:tune :tone :decay]))

(defn noise-string
  "Pure. \"noise\" or \"noise:color=pink:density=100:freq=440\" (freq = the
   event's pitch, the LFSR clock base)."
  [value hz]
  (let [v (cond-> (select-keys value [:color :density])
            hz (assoc :freq hz))]
    (if (empty? v) "noise" (voice-string "noise" [] v [:color :density :freq]))))

;;; ── Drum routing (Part B) ────────────────────────────────────────────
;;
;; DEFAULT KIT DECISION (project owner, 2026-11-21 — replaces SYN6 owner
;; decision 3): the synthesized kit is REPuLse's default drum kit. A bare kit
;; name (:bd :sd :hh :oh :cp … — the 18 names in drum-voices) plays the synth
;; kit, even when a sample bank of that name has loaded.
;;   (bank :dirt)          — back to the Dirt-Samples drums for bare names
;;                           (the default before 2026-11-21)
;;   (bank :RolandTR808) … — a drum-machine sample bank, as before
;;   (bank :synth)         — explicit synth kit (same as no bank)
;;   :synth_bd             — one synth hit under any bank
;;   (sound :bd 2)         — always a sample
;; The four original engine voices ("bd" "sd" "hh" "oh" in lib.rs, the
;; no-sample fallback before SYN6) are no longer reachable from patterns; the
;; engine keeps them only so old trigger strings still play.

(def dirt-prefix
  "Bank prefix that restores the Dirt-Samples drums for bare kit names."
  "dirt")

(defn drum-route
  "Pure. kw — a non-note keyword. prefix — active bank prefix (string or nil).
   has-bank? — (fn [kw]) → bool.
   → {:kind :synth-drum :name \"bd\"}     engine kit voice (\"drum:bd…\")
     {:kind :sample :bank kw}             play this sample bank
     {:kind :unknown}                     440 Hz sine, as before
   Rules, in order (see the default-kit decision above):
   1. :synth_<drum> → the kit voice, whatever bank is active.
   2. A drum-machine prefix (not \"synth\" or \"dirt\") whose <prefix>_<name>
      bank is registered → that sample.
   3. (bank :dirt) and a registered bank of the bare name → that sample.
   4. One of the 18 kit names → the kit voice (the default kit; also the
      fallback while samples are still loading or missing).
   5. Any other registered bank → that sample; else :unknown."
  [kw prefix has-bank?]
  (let [n    (name kw)
        cand (when (and prefix (not= prefix "synth") (not= prefix dirt-prefix))
               (keyword (str prefix "_" n)))]
    (cond
      (and (str/starts-with? n "synth_") (drum-name? (subs n 6)))
      {:kind :synth-drum :name (subs n 6)}

      (and cand (has-bank? cand))                    {:kind :sample :bank cand}
      (and (= prefix dirt-prefix) (has-bank? kw))    {:kind :sample :bank kw}
      (drum-name? n)                                 {:kind :synth-drum :name n}
      (has-bank? kw)                                 {:kind :sample :bank kw}
      :else                                          {:kind :unknown})))

(def ^:private js-drums
  "JS fallback (TEST1 harness, tracks with an fx chain): each kit voice as the
   nearest existing JS voice. The real models are WASM-only (decision 6).
   bd sd hh oh use the constants of the original JS drums (150 → 40 Hz kick,
   0.4 s; 45 ms / 1 s hats at 8 kHz): since the synth kit became the default
   (2026-11-21) they are what bare drum names play on the JS path, and the
   harness tests count onsets on them."
  {"bd"    {:kind :kick :f-start 150 :f-end 40 :sweep 0.06 :dur 0.4}
   "lt"    {:kind :kick :f-start 131 :f-end 82 :sweep 0.06 :dur 0.55}
   "mt"    {:kind :kick :f-start 197 :f-end 123 :sweep 0.06 :dur 0.45}
   "ht"    {:kind :kick :f-start 296 :f-end 185 :sweep 0.06 :dur 0.35}
   "cg"    {:kind :kick :f-start 275 :f-end 220 :sweep 0.06 :dur 0.30}
   "zap"   {:kind :kick :f-start 2400 :f-end 60 :sweep 0.06 :dur 0.25}
   "cl"    {:kind :sine :freq 2500 :dur 0.06}
   "sd"    {:kind :snare :dur 0.2}
   "rim"   {:kind :snare :dur 0.03}
   "cp"    {:kind :snare :dur 0.18}
   "click" {:kind :snare :dur 0.03}
   "hh"    {:kind :hihat :dur 0.045 :gain 0.5 :hpf 8000}
   "sh"    {:kind :hihat :dur 0.08 :gain 0.5 :hpf 6000}
   "tb"    {:kind :hihat :dur 0.18 :gain 0.5 :hpf 6000}
   "oh"    {:kind :hihat :dur 1.0 :gain 0.4 :hpf 8000}
   "cr"    {:kind :hihat :dur 1.8 :gain 0.4 :hpf 3500}
   "rd"    {:kind :hihat :dur 1.4 :gain 0.4 :hpf 4500}
   "cb"    {:kind :square :freq 555 :dur 0.35}})

(defn js-drum
  "Pure. JS fallback plan for kit voice drum and event value: the plan of
   js-drums with tune applied to its frequencies and decay replacing :dur."
  [drum value]
  (when-let [p (get js-drums drum)]
    (let [v    (if (map? value) value {})
          mul  (Math/pow 2 (/ (double (or (:tune v) 0)) 12))
          tune (fn [m k] (if (contains? m k) (update m k * mul) m))]
      (cond-> (-> p (tune :f-start) (tune :f-end) (tune :freq))
        (number? (:decay v)) (assoc :dur (max 0.005 (double (:decay v))))))))

;;; ── Unison (Part A) ──────────────────────────────────────────────────

(defn unison-layout
  "Pure. Detune and pan of n unison oscillators (SYN6 A3), lowest pitch first:
   [{:cents :phase :pan} …]. Same formulas as unison_layout in lib.rs."
  [n detune spread]
  (let [n (max 1 (min 7 (Math/round n)))]
    (mapv (fn [i]
            (let [phase (let [x (* i 0.618034)] (- x (Math/floor x)))]
              (if (= n 1)
                {:cents 0 :phase phase :pan 0}
                (let [h     (/ (dec n) 2)
                      k     (- i h)
                      u     (/ k h)
                      sgn   (fn [x] (cond (pos? x) 1 (neg? x) -1 :else 0))
                      cents (* (/ detune 2) (sgn u) (Math/pow (Math/abs u) 1.5))
                      alt   (if (even? (inc (Math/ceil (Math/abs k)))) 1 -1)
                      pan   (if (zero? k) 0 (* spread (Math/abs u) (sgn k) alt))]
                  {:cents cents :phase phase :pan pan}))))
          (range n))))

;;; ── Instruments (Part C) ─────────────────────────────────────────────

(defn expand-preset
  "Pure. For an event whose :synth names an instrument preset: the preset's
   parameters, overridden by every key the event has itself, with :synth
   replaced by the preset's base voice. Other events are returned unchanged.
   Then accent (any voice): amp × (1 + 0.5a); with a filter envelope also
   lp-env + 1.5a and lp-decay × (1 − 0.4a)."
  [value]
  (let [preset (get instrument-presets (:synth value))
        v      (if preset
                 (assoc (merge preset value) :synth (:synth preset))
                 value)
        a      (:accent v)]
    (if (and (number? a) (pos? a))
      (let [a (min 1 a)]
        (cond-> (assoc v :amp (* (:amp v 1.0) (+ 1 (* 0.5 a))))
          (number? (:lp-env v))
          (assoc :lp-env   (+ (:lp-env v) (* 1.5 a))
                 :lp-decay (* (:lp-decay v 0.2) (- 1 (* 0.4 a))))))
      v)))

(defn lp-lfo
  "Pure. Cutoff LFO of an event for an onset at t seconds with cycle length
   cycle-dur (SYN6 C3): {:rate hz :phase 0–1 :depth octaves}, or nil without
   lp-rate/lp-depth, a low-pass or a cycle length."
  [value t cycle-dur]
  (let [{:keys [lp-rate lp-depth lpf]} value]
    (when (and (number? lp-depth) (pos? lp-depth) (number? lpf) (pos? lpf)
               (number? cycle-dur) (pos? cycle-dur))
      (let [r (max 0 (min 64 (double (or lp-rate 0))))
            x (/ (* r t) cycle-dur)]
        {:rate  (/ r cycle-dur)
         :phase (- x (Math/floor x))
         :depth (min 4 (double lp-depth))}))))

(defn event-hz
  "Pure. Pitch in Hz of an event value (note keyword, number, or a map with
   :note), or nil for drums, samples and rests."
  [value]
  (let [n (if (map? value) (:note value) value)]
    (cond
      (and (keyword? n) (theory/note-keyword? n)) (theory/note->hz n)
      (number? n)                                 n
      :else                                       nil)))

(defn- log-dist [a b] (Math/abs (Math/log (/ a b))))

(defn assign-glide
  "Pure. state — {:t secs :cur [hz…] :prev [hz…]} or nil. events — the onsets of
   one track in one scheduling pass, as [{:t secs :hz hz-or-nil :value v} …].
   Sorts by :t. For every pitched event: if its time is later than (:t state)
   the current group becomes the previous one. An event with :glide > 0 and a
   non-empty previous group gets :glide-from (in its :value) = the member of
   that group nearest in pitch (smallest |log(hz / member)|).
   → [events' state']"
  [state events]
  (reduce
    (fn [[out st] {:keys [t hz value] :as e}]
      (if-not (and (number? hz) (pos? hz))
        [(conj out e) st]
        (let [st (if (or (nil? st) (> t (:t st)))
                   {:t t :cur [] :prev (if st (:cur st) [])}
                   st)
              ;; Preset glide counts too (:theremin).
              g  (when (map? value) (:glide (expand-preset value)))
              e' (if (and (number? g) (pos? g) (seq (:prev st)))
                   (assoc-in e [:value :glide-from]
                             (apply min-key #(log-dist hz %) (:prev st)))
                   e)]
          [(conj out e') (update st :cur conj hz)])))
    [[] state]
    (sort-by :t events)))

(defn glide-all
  "Pure. assign-glide over a whole render (export, test harness): items are
   maps with :t, :value and optionally :track; each track glides from a fresh
   state. → the items sorted by :t within each track, :value with :glide-from."
  [items]
  (mapcat (fn [[_ its]]
            (first (assign-glide nil (map #(assoc % :hz (event-hz (:value %))) its))))
          (group-by :track items)))
