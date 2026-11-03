(ns repulse.content.demos
  "Demo template data and the `demo` Lisp built-in factory.
   Responsibility: store demo-templates map; provide a factory for the demo builtin fn.
   Exports: demo-templates, demo-builtin."
  (:require [repulse.lisp.eval :as leval]
            [repulse.audio :as audio]
            [clojure.string :as cstr]))

;; Each demo is a genre sketch that also showcases one group of features —
;; the `;; Shows:` line names them. Keep that line accurate when editing.
(def demo-templates
  {:techno
   {:bpm 130
    :code
";; TECHNO — mini-notation drums, euclidean percussion, filtered acid line
;; Shows: (~ \"...\") mini-notation, euclidean, synth :saw, per-track fx, tween
;; Try: change the euclidean 5 to 3 or 7, or the filter :freq to 2000
;; Scale degrees are 1-indexed: 1 = root, 8 = root an octave up
(bpm 130)

(track :kick (~ \"bd*4\"))

(track :hat
  (->> (~ \"[~ hh]*4\")
       (amp (~ \"0.4 0.6 0.4 0.9\"))))

(track :clap (~ \"~ cp ~ cp\"))

(track :perc
  (->> (euclidean 5 16 :rm 2)
       (amp 0.35)
       (pan 0.4)))

(track :acid
  (->> (scale :minor :c2 (~ \"1 1 [8 1] 4 1 [6 5] 1 7\"))
       (synth :saw)
       (decay 0.18)
       (amp (tween :linear 0.0 0.6 8))   ; fades in over 8 bars
       (fx :filter :freq 900 :q 8)
       (fx :distort :drive 4)))
"}

   :house
   {:bpm 124
    :code
";; HOUSE — Rhodes stabs, offbeat bass, shuffled hats
;; Shows: FM preset :epiano, def + comp voice presets, off, alt, chorus, synth :square
(bpm 124)

(def stab (comp (amp 0.45) (attack 0.005) (decay 0.3)))

(track :kick (~ \"bd*4\"))

(track :clap (~ \"~ cp ~ cp\"))

(track :hat
  (->> (~ \"[~ hh]*4\")
       (amp 0.5)
       (off 0.0625 (amp 0.15))))   ; ghost hat one 16th later

(track :keys
  (->> (alt (chord :minor7 :a3) (chord :minor7 :d3))
       (fast 2)
       (late 0.125)                ; push the stabs onto the offbeat
       (synth :epiano)
       stab
       (fx :chorus 0.4)))

(track :bass
  (->> (scale :minor :a1 (~ \"~ 1 ~ 1 ~ 1 ~ <4 5>\"))
       (synth :square :pw 0.3)
       (amp 0.55)
       (decay 0.15)
       (fx :filter :freq 500 :q 4)))
"}

   :ambient
   {:bpm 72
    :code
";; AMBIENT — Rhodes pads, bell melody, plate reverb and tape echoes
;; Shows: FM presets :epiano / :bell, dattorro-reverb, delay, degrade-by, tween fade-in
(bpm 72)

(track :keys
  (->> (slow 2 (alt (chord :minor7 :a3) (chord :maj7 :f3)))
       (synth :epiano)
       (amp (tween :sine 0.0 0.45 4))   ; swells in over 4 bars
       (fx :dattorro-reverb :wet 0.5 :decay 0.85)))

(track :bells
  (->> (scale :minor :a4 (~ \"1 ~ 5 ~ <8 10> ~ 5 3\"))
       (degrade-by 0.3)                 ; drop ~30% of notes, differently each cycle
       (synth :bell)
       (amp 0.25)
       (pan (seq -0.6 0.6))
       (fx :delay :wet 0.35 :time 0.42 :feedback 0.5)))

(track :sub
  (->> (slow 2 (~ \"<a1 f1>\"))
       (attack 1.0)
       (decay 3.5)
       (amp 0.3)))
"}

   :dnb
   {:bpm 174
    :code
";; DRUM & BASS — chopped amen break, pitched slices, chorused saw bass
;; Shows: sound + fmap for sample slices, rate, end, sometimes, degrade-by, fx :chorus
(bpm 174)

(track :break
  (->> (~ \"0 1 2 3 4 5 6 7 8 9 10 11 [12 12] 13 <14 30> 15\")
       (fmap (fn [n] (sound :amencutup n)))   ; numbers → amen slices
       (end 0.8)                              ; tighter chops
       (sometimes (rate 1.5))                 ; pitch up the break on some cycles
       (amp 0.8)))

(track :hat
  (->> (~ \"hh*8\")
       (degrade-by 0.2)
       (amp 0.3)
       (pan (~ \"-0.3 0.3\"))))

(track :bass
  (->> (scale :minor :e1 (~ \"1 ~ ~ 1 ~ ~ <4 6> ~\"))
       (synth :saw)
       (attack 0.01)
       (decay 0.6)
       (amp 0.6)
       (fx :filter :freq 400 :q 3)
       (fx :chorus 0.5)))
"}

   :minimal
   {:bpm 122
    :code
";; MINIMAL — euclidean rhythms, probability, small changes every cycle
;; Shows: euclidean with rotation, degrade-by, choose, sometimes, off, FM :marimba
(bpm 122)

(track :kick (~ \"bd*4\"))

(track :rim
  (->> (euclidean 3 8 :rm 1)
       (amp 0.3)))

(track :hat
  (->> (euclidean 7 16 :hh)
       (degrade-by 0.3)
       (amp (choose [0.15 0.25 0.35]))))   ; new level each cycle

(track :blip
  (->> (scale :dorian :d4 (~ \"1 ~ ~ 5 ~ ~ <8 10> ~\"))
       (off 0.1875 (transpose 12))        ; octave echo, three 16ths later
       (sometimes (late 0.0625))
       (synth :marimba)
       (amp 0.35)
       (pan -0.3)))

(track :bass
  (->> (~ \"~ d2 ~ ~ ~ d2 ~ <~ a1>\")
       (synth :fm :index 2 :ratio 1)
       (amp 0.5)
       (decay 0.12)))
"}

   :dub
   {:bpm 76
    :code
";; DUB — offbeat skank, echo-drenched rimshot, deep sub bass, trombone
;; Shows: delay feedback, reverb, late, synth :square + filter, FM :trombone
(bpm 76)

(track :kick (~ \"bd ~ ~ ~ ~ ~ bd ~\"))

(track :rim
  (->> (~ \"~ ~ ~ ~ rm ~ ~ ~\")
       (amp 0.6)
       (fx :delay :wet 0.5 :time 0.59 :feedback 0.65)   ; dotted-8th echo
       (fx :reverb 0.3)))

(track :hat
  (->> (~ \"~ hh ~ hh ~ hh ~ hh\")
       (amp 0.25)))

(track :skank
  (->> (chord :minor :g3)
       (fast 2)
       (late 0.25)                        ; chords on 2 and 4
       (synth :square :pw 0.2)
       (amp 0.25)
       (decay 0.12)
       (fx :filter :freq 1800)
       (fx :delay :wet 0.3 :time 0.395 :feedback 0.4)))

(track :bass
  (->> (scale :minor :g1 (~ \"1 ~ ~ 1 ~ 4 ~ <5 3>\"))
       (attack 0.01)
       (decay 0.5)
       (amp 0.85)))

(track :horn
  (->> (slow 2 (scale :minor :g3 (~ \"~ ~ 5 4 ~ 3 1 ~\")))
       (synth :trombone)
       (amp 0.4)
       (fx :reverb 0.4)))
"}

   :rock
   {:bpm 118
    :code
";; ROCK — plucked-string guitar through a tube amp and a 4x12 cabinet
;; Shows: Karplus-Strong synth :guitar, defn, fx :amp-sim + :cab, <...> alternation
(bpm 118)

(defn power [pat]                        ; power chord: root + fifth
  (stack pat (transpose 7 pat)))

(track :kick  (~ \"bd ~ ~ ~ bd bd ~ ~\"))
(track :snare (~ \"~ ~ sd ~ ~ ~ sd ~\"))
(track :hat   (->> (~ \"hh*8\") (amp 0.3)))
(track :crash (->> (~ \"<cr ~ ~ ~>\") (amp 0.4)))   ; crash every 4th bar

(track :guitar
  (->> (power (~ \"e2 e2 ~ e2 <g2 a2> ~ <d2 b1> ~\"))
       (synth :guitar)
       (amp 0.7)
       (fx :amp-sim :gain 30 :stages 3 :tonestack :mid-scoop)
       (fx :cab :ir :4x12)))

(track :bass
  (->> (~ \"e1 e1 ~ e1 <g1 a1> ~ <d1 b0> ~\")
       (synth :saw)
       (amp 0.5)
       (decay 0.25)
       (fx :filter :freq 300)))
"}

   :modular
   {:bpm 100
    :code
";; MODULAR — design your own instruments from oscillators, filters and envelopes
;; Shows: defsynth, UGens (saw noise mix lpf hpf), env-perc, env-asr, env + env-gen, arrange
(bpm 100)

(defsynth supersaw [freq]
  (->> (mix (saw freq) (saw (* freq 1.007)))   ; two detuned saws
       (lpf (* freq 4))
       (env-asr 0.05 0.6 0.4)))

(defsynth pluck [freq]
  (->> (saw freq)
       (lpf (* freq 3))
       (env-gen (env [0 1 0.3 0.001] [0.002 0.05 0.4] [:lin :exp :exp]))))

(defsynth tick [freq]
  (->> (noise)
       (hpf 7000)
       (env-perc 0.001 0.04)))

(def verse
  (stack (~ \"bd ~ sd ~\")
         (->> (scale :minor :c3 (~ \"1 3 5 8\"))
              (synth :pluck)
              (amp 0.5))))

(def chorus
  (stack (~ \"bd bd sd [~ bd]\")
         (->> (fast 8 (pure :c4)) (synth :tick) (amp 0.25))
         (->> (slow 2 (chord :minor :c4)) (synth :supersaw) (amp 0.25))
         (->> (scale :minor :c4 (~ \"1 3 4 6 8 6 4 3\"))
              (synth :pluck)
              (amp 0.4))))

(track :song
  (arrange [[verse 4] [chorus 4]]))      ; 4 bars each, then loop
"}

   :experimental
   {:bpm 110
    :code
";; EXPERIMENTAL — the Lisp is the instrument: functions that build patterns
;; Shows: defn, let, fn, fmap, cat, jux, every, rev, sometimes, transpose, bitcrusher
(bpm 110)

(defn phrase [root degrees]
  (->> (scale :dorian root degrees)
       (synth :koto)
       (decay 0.8)))

(track :melody
  (->> (cat (phrase :d4 (~ \"1 3 5 7 8 5 3 1\"))
            (phrase :a3 (~ \"1 ~ 5 ~ 8 ~ 10 8\")))
       (every 4 rev)
       (jux rev)                         ; reversed copy in the right ear
       (amp 0.45)))

(track :rhythm
  (->> (seq :bd :_ :sd :_ :bd :bd :_ :sd)
       (every 3 rev)
       (sometimes (fast 2))))

(track :texture
  (->> (~ \"hh*8\")
       (amp (fmap (fn [n] (/ n 10)) (~ \"2 5 3 8 2 6 4 9\")))   ; numbers → levels
       (pan (~ \"-0.7 0.7\"))
       (fx :bitcrusher :bits 6 :wet 0.6)))

(track :drone
  (let [root (pure :d2)]
    (->> (stack root (transpose 7 root))
         (synth :fm :index 1.5 :ratio 0.5)
         (attack 0.5)
         (decay 3)
         (amp 0.2))))
"}})

(defn demo-builtin
  "Returns the Lisp `demo` built-in fn.
   editor-view-atom — atom holding the current CodeMirror EditorView.
   evaluate-ref     — atom holding the evaluate! fn (populated after eval-orchestrator init)."
  [editor-view-atom evaluate-ref]
  (fn [& args]
    (let [kw (when (seq args) (leval/unwrap (first args)))]
      (if (nil? kw)
        (str "available demos: "
             (cstr/join " " (map #(str ":" (name %))
                                 (sort (keys demo-templates)))))
        (if-let [{:keys [bpm code]} (get demo-templates kw)]
          (do
            ;; Drop the previous demo's tracks, buses and per-track fx —
            ;; evaluation only replaces tracks the new code names.
            (audio/stop!)
            (audio/set-bpm! bpm)
            (when-let [view @editor-view-atom]
              (.dispatch view
                         #js {:changes #js {:from 0
                                            :to   (.. view -state -doc -length)
                                            :insert code}})
              (js/setTimeout #(when-let [f @evaluate-ref] (f code)) 50))
            (str "=> loaded demo :" (name kw)))
          (str "unknown demo :" (name kw)
               " — available: "
               (cstr/join " " (map #(str ":" (name %))
                                   (sort (keys demo-templates))))))))))
