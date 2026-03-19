(ns repulse.params
  (:require [repulse.core :as core]))

;;; ── Value map helpers ────────────────────────────────────────────────

(defn- to-map
  "Upgrade a raw event value to a map with :note key, or leave maps as-is."
  [v]
  (if (map? v) v {:note v}))

(defn- pat?
  "True if x is a REPuLse pattern (was created via core/pattern).
   Uses the explicit ::core/pat tag so plain event-value maps
   (which are also Clojure maps) are never misidentified."
  [x]
  (core/pattern? x))

(defn- apply-param
  "Merge parameter kw into each event of note-pat, sourcing values from
   param-val-or-pat. Scalar values are wrapped in (pure …) first.
   note-pat may also be a raw value (e.g. the map returned by (sound …));
   it is coerced to (pure v) so that (rate 2.0 (sound :tabla 0)) works."
  [kw param-val-or-pat note-pat]
  (let [param-pat (if (pat? param-val-or-pat)
                    param-val-or-pat
                    (core/pure param-val-or-pat))
        note-pat' (if (pat? note-pat)
                    note-pat
                    (core/pure note-pat))]
    (core/combine (fn [pv nv] (assoc (to-map nv) kw pv))
                  param-pat
                  note-pat')))

;;; ── Parameter transformers ───────────────────────────────────────────

(defn amp
  "Scale event amplitude. 0.0 = silent, 1.0 = full.
   (amp 0.8 pat)          — apply directly
   (amp (seq 0.9 0.5) pat) — patterned accent
   (amp 0.8)               — return (pat → pat) transformer"
  ([v]     (fn [pat] (amp v pat)))
  ([v pat] (apply-param :amp v pat)))

(defn attack
  "Envelope attack time in seconds (time to reach peak amplitude).
   (attack 0.001 pat) — percussive / instant
   (attack 0.3 pat)   — slow swell
   (attack 0.05)      — return transformer"
  ([t]     (fn [pat] (attack t pat)))
  ([t pat] (apply-param :attack t pat)))

(defn decay
  "Envelope decay time in seconds (time to fade to silence after peak).
   (decay 0.08 pat) — punchy stab
   (decay 2.0 pat)  — long fade
   (decay 0.4)      — return transformer"
  ([t]     (fn [pat] (decay t pat)))
  ([t pat] (apply-param :decay t pat)))

(defn release
  "Envelope release time in seconds. When omitted, the decay value is used.
   (release 0.5 pat) — apply directly
   (release 0.5)     — return transformer"
  ([t]     (fn [pat] (release t pat)))
  ([t pat] (apply-param :release t pat)))

(defn pan
  "Stereo panning. -1.0 = hard left, 0.0 = centre, 1.0 = hard right.
   (pan 0.0 pat)              — centre
   (pan (seq -0.8 0.8) pat)   — alternating left/right
   (pan -0.5)                 — return transformer"
  ([p]     (fn [pat] (pan p pat)))
  ([p pat] (apply-param :pan p pat)))

(defn jux
  "Stack the original pattern panned left with (f pat) panned right.
   Creates stereo width by splitting original and transformed copies.
   (jux rev (seq :c4 :e4 :g4))  — original left, reversed right"
  [f pat]
  (core/stack* [(pan -1 pat) (pan 1 (f pat))]))

(defn rate
  "Playback rate multiplier. 1.0 = normal, 2.0 = double speed (octave up),
   0.5 = half speed (octave down).
   (rate 1.5 pat)  — apply directly
   (rate 1.5)      — return transformer"
  ([r]     (fn [pat] (rate r pat)))
  ([r pat] (apply-param :rate r pat)))

(defn begin
  "Sample start position as a fraction of buffer duration (0.0–1.0).
   (begin 0.25 pat)  — start at 25% into the sample
   (begin 0.25)      — return transformer"
  ([t]     (fn [pat] (begin t pat)))
  ([t pat] (apply-param :begin t pat)))

(defn end*
  "Sample end position as a fraction of buffer duration (0.0–1.0).
   Named end* to avoid conflict with cljs.core/end.
   (end* 0.75 pat)  — stop at 75% into the sample
   (end* 0.75)      — return transformer"
  ([t]     (fn [pat] (end* t pat)))
  ([t pat] (apply-param :end t pat)))

(defn loop-sample
  "Enable sample looping.
   (loop-sample true pat)  — loop the sample
   (loop-sample true)      — return transformer"
  ([on?]     (fn [pat] (loop-sample on? pat)))
  ([on? pat] (apply-param :loop on? pat)))

(defn jux-by
  "Like jux but with adjustable stereo width.
   width 0.0 = both copies centre (mono), 1.0 = full left/right.
   (jux-by 0.5 rev pat) — half stereo width"
  [width f pat]
  (core/stack* [(pan (- width) pat) (pan width (f pat))]))
