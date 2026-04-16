(ns repulse.content.tutorial
  "Tutorial chapter data and the `tutorial` Lisp built-in factory.
   Responsibility: store tutorial-chapters vector; provide a factory for the tutorial builtin fn.
   Exports: tutorial-chapters, tutorial-builtin."
  (:require [repulse.lisp.eval :as leval]))

(def tutorial-chapters
  [
   ;; Chapter 1: First sound
   ";; === Tutorial 1/8 — First Sound ===
;;
;; Welcome to REPuLse! Let's make some noise.
;;
;; `seq` creates a sequence of sounds.  Each value plays
;; for one equal subdivision of the cycle:
;;   :bd = bass drum   :sd = snare   :hh = hi-hat
;;
;; Press Alt+Enter (Option+Enter on Mac) to hear this:

(seq :bd :sd :bd :sd)

;; Try changing :sd to :hh and press Alt+Enter again.
;; When you're ready, type (tutorial 2) in the command bar."

   ;; Chapter 2: Layering
   ";; === Tutorial 2/8 — Layering with stack ===
;;
;; `stack` plays multiple patterns at the same time.
;; Each pattern runs in parallel, like tracks in a mixer.

(stack
  (seq :bd :_ :bd :_)
  (seq :_ :sd :_ :sd)
  (seq :hh :hh :hh :hh))

;; :_ is a rest — silence for that step.
;; Try adding a fourth layer!
;; Next: (tutorial 3)"

   ;; Chapter 3: Speed
   ";; === Tutorial 3/8 — Speed: fast & slow ===
;;
;; `fast` speeds up a pattern by a factor.
;; `slow` does the opposite.

(stack
  (seq :bd :_ :bd :_)
  (fast 2 (seq :hh :oh))
  (slow 2 (seq :sd :_ :_ :_)))

;; (fast 2 pat) plays pat twice per cycle.
;; (slow 2 pat) stretches pat over two cycles.
;; Try (fast 4 (seq :hh :oh)) for rapid hi-hats.
;; Next: (tutorial 4)"

   ;; Chapter 4: Evolution
   ";; === Tutorial 4/8 — Evolution: every ===
;;
;; `every` applies a transformation only on certain cycles.
;; (every N transform pattern) — transform every Nth cycle.

(stack
  (every 4 (fast 2) (seq :bd :_ :bd :_))
  (seq :_ :sd :_ :sd)
  (every 3 rev (seq :hh :oh :hh :_)))

;; The kick doubles speed every 4th cycle.
;; The hats reverse every 3rd cycle.
;; This is how patterns stay alive without manual changes.
;; Next: (tutorial 5)"

   ;; Chapter 5: Naming
   ";; === Tutorial 5/8 — Naming: def ===
;;
;; `def` binds a name to a value. Use it to build
;; a vocabulary of reusable parts.

(def kick  (seq :bd :_ :bd :_))
(def snare (seq :_ :sd :_ :sd))
(def hat   (fast 2 (seq :hh :oh)))

(stack kick snare hat)

;; Now you can refer to `kick`, `snare`, `hat` by name.
;; Try: (def kick (seq :bd :bd :_ :bd)) and re-evaluate.
;; Next: (tutorial 6)"

   ;; Chapter 6: Multi-track
   ";; === Tutorial 6/8 — Multi-Track: track ===
;;
;; `track` defines a named track.  Each track runs
;; independently — you can update one without stopping others.

(track :kick
  (seq :bd :_ :bd :bd))

(track :snare
  (seq :_ :sd :_ :sd))

(track :hat
  (fast 2 (seq :hh :oh)))

;; In the command bar, try:
;;   (mute! :hat)     — silence the hats
;;   (unmute! :hat)   — bring them back
;;   (solo! :kick)    — hear only the kick
;;   (clear!)         — stop everything
;; Next: (tutorial 7)"

   ;; Chapter 7: Melody
   ";; === Tutorial 7/8 — Melody: scale & chord ===
;;
;; Note keywords like :c4 play pitched tones.
;; `scale` maps degree numbers (1, 2, 3, …) to a musical scale.
;; `chord` stacks the tones of a chord.

(track :bass
  (scale :minor :c3 (seq 1 1 4 6)))

(track :chords
  (slow 2 (chord :minor :c4)))

(track :melody
  (scale :minor :c4 (seq 1 3 5 8 5 3)))

(track :kick
  (seq :bd :bd :bd :bd))

;; Try changing :minor to :dorian or :blues.
;; Try (transpose 5 ...) around the melody.
;; Next: (tutorial 8)"

   ;; Chapter 8: Expression
   ";; === Tutorial 8/8 — Expression: amp, decay, ->> ===
;;
;; Per-event parameters make patterns expressive.
;; `->>` threads a pattern through a chain of transformers.

(track :kick
  (->> (seq :bd :bd :bd :bd)
       (amp (seq 0.9 0.5 0.7 0.5))))

(track :lead
  (->> (scale :minor :c4 (seq 0 2 4 7 4 2 0 :_))
       (amp 0.6)
       (attack 0.02)
       (decay 0.5)))

(track :pad
  (->> (chord :minor7 :c3)
       (amp 0.25)
       (attack 0.3)
       (decay 2.0)))

;; (amp val) sets amplitude 0.0–1.0
;; (attack secs) sets onset time
;; (decay secs) sets fade time
;; (pan pos) sets stereo position -1.0 to 1.0
;;
;; That's the basics! Try (demo :techno) or (demo :experimental)
;; to hear full compositions, or start writing your own."
])

(defn tutorial-builtin
  "Returns the Lisp `tutorial` built-in fn.
   editor-view-atom — atom holding the current CodeMirror EditorView."
  [editor-view-atom]
  (fn [& args]
    (let [n   (if (seq args) (int (leval/unwrap (first args))) 1)
          idx (dec n)]
      (if (and (>= idx 0) (< idx (count tutorial-chapters)))
        (let [code (nth tutorial-chapters idx)]
          (when-let [view @editor-view-atom]
            (.dispatch view
                       #js {:changes #js {:from 0
                                          :to   (.. view -state -doc -length)
                                          :insert code}}))
          (str "=> tutorial chapter " n "/" (count tutorial-chapters)
               " — press Alt+Enter to play"))
        (str "tutorial has chapters 1–" (count tutorial-chapters)
             " — try (tutorial 1)")))))
