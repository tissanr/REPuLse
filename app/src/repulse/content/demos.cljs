(ns repulse.content.demos
  "Demo template data and the `demo` Lisp built-in factory.
   Responsibility: store demo-templates map; provide a factory for the demo builtin fn.
   Exports: demo-templates, demo-builtin."
  (:require [repulse.lisp.eval :as leval]
            [repulse.audio :as audio]
            [clojure.string :as cstr]))

(def demo-templates
  {:techno
   {:bpm 130
    :code
";; TECHNO — four-on-the-floor kick, offbeat hats, snare on 2/4, acid bassline
(bpm 130)

(track :kick
  (seq :bd :bd :bd :bd))

(track :hat
  (->> (fast 2 (seq :_ :oh :_ :oh))
       (amp (seq 0.5 0.7 0.5 0.9))))

(track :snare
  (seq :_ :sd :_ :sd))

(track :bass
  (->> (scale :minor :c2 (seq 0 0 3 5))
       (fast 2)
       (decay 0.15)
       (amp 0.8)))
"}

   :ambient
   {:bpm 72
    :code
";; AMBIENT — slow pad chords with reverb, gentle melodic line
(bpm 72)

(track :pad
  (->> (chord :minor7 :a3)
       (amp 0.3)
       (attack 0.4)
       (decay 3.0)))

(track :melody
  (->> (scale :minor :a4 (seq 0 2 4 7 4 2))
       (slow 2)
       (amp 0.4)
       (attack 0.1)
       (decay 1.5)))

(track :pulse
  (->> (seq :c5 :_ :e5 :_)
       (slow 4)
       (amp 0.15)
       (decay 0.8)))
"}

   :dnb
   {:bpm 174
    :code
";; DRUM & BASS — fast breakbeat, sub bass, amen-style rhythm
(bpm 174)

(track :break
  (seq :bd :_ :_ :bd :_ :_ :sd :_
       :bd :_ :bd :_ :_ :sd :_ :_))

(track :hat
  (->> (fast 2 (seq :hh :hh :oh :hh))
       (amp (seq 0.6 0.4 0.8 0.4))))

(track :sub
  (->> (scale :minor :e1 (seq 0 :_ 0 :_ 3 :_ 5 :_))
       (amp 0.9)
       (decay 0.2)))
"}

   :minimal
   {:bpm 120
    :code
";; MINIMAL — sparse kick, subtle hi-hats, one-note bass
(bpm 120)

(track :kick
  (seq :bd :_ :_ :_ :bd :_ :_ :_))

(track :hat
  (->> (seq :_ :hh :_ :hh :_ :hh :_ :_)
       (amp 0.35)))

(track :bass
  (->> (pure :c2)
       (amp 0.6)
       (decay 0.12)))
"}

   :house
   {:bpm 124
    :code
";; HOUSE — classic four-on-the-floor, organ stab chords, open hat
(bpm 124)

(track :kick
  (seq :bd :bd :bd :bd))

(track :hat
  (->> (seq :_ :oh :_ :oh)
       (amp 0.5)))

(track :clap
  (seq :_ :sd :_ :sd))

(track :chord
  (->> (every 4 (fast 2) (chord :dom7 :c4))
       (amp 0.4)
       (attack 0.02)
       (decay 0.25)))

(track :bass
  (->> (scale :minor :c2 (seq 0 0 3 0 5 0 3 0))
       (amp 0.7)
       (decay 0.1)))
"}

   :dub
   {:bpm 140
    :code
";; DUB — heavy bass, delay-heavy snare, sparse hats
(bpm 140)

(track :kick
  (seq :bd :_ :_ :_ :_ :_ :bd :_))

(track :snare
  (->> (seq :_ :_ :_ :sd :_ :_ :_ :_)
       (amp 0.8)
       (decay 0.3)))

(track :hat
  (->> (seq :_ :hh :_ :_ :_ :_ :hh :_)
       (amp 0.3)))

(track :bass
  (->> (scale :minor :g1 (seq 0 :_ :_ 0 :_ 3 :_ :_))
       (amp 0.9)
       (attack 0.01)
       (decay 0.4)))
"}

   :experimental
   {:bpm 110
    :code
";; EXPERIMENTAL — algorithmic patterns using every, rev, fmap
(bpm 110)

(track :rhythm
  (every 3 rev
    (seq :bd :_ :sd :_ :bd :bd :_ :sd)))

(track :texture
  (->> (every 2 (fast 2) (seq :hh :oh :hh :_))
       (amp (seq 0.3 0.6 0.4 0.8))))

(track :melody
  (->> (scale :dorian :d3 (seq 0 2 4 6 7 4 2 0))
       (every 4 rev)
       (every 3 (fast 2))
       (amp 0.5)
       (decay 0.6)))

(track :drone
  (->> (chord :sus4 :d2)
       (amp 0.2)
       (attack 0.5)
       (decay 2.5)))
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
