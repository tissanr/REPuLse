(ns repulse.params
  (:require [repulse.core :as core]))

;;; ── Value map helpers ────────────────────────────────────────────────

(defn- to-map
  "Upgrade a raw event value to a map with :note key, or leave maps as-is."
  [v]
  (if (map? v) v {:note v}))

(defn- pat?
  "True if x is a REPuLse pattern (a map with a :query function)."
  [x]
  (and (map? x) (fn? (:query x))))

(defn- apply-param
  "Merge parameter kw into each event of note-pat, sourcing values from
   param-val-or-pat. Scalar values are wrapped in (pure …) first."
  [kw param-val-or-pat note-pat]
  (let [param-pat (if (pat? param-val-or-pat)
                    param-val-or-pat
                    (core/pure param-val-or-pat))]
    (core/combine (fn [pv nv] (assoc (to-map nv) kw pv))
                  param-pat
                  note-pat)))

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
