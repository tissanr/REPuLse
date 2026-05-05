(ns repulse.lisp.builtins.synth
  "Synth-voice builtins: saw, square, noise, fm, synth, sound."
  (:require [repulse.core :as core]
            [repulse.theory :as theory]
            [repulse.lisp.util :as u]))

(defn make-builtins []
  {"saw"    (fn [note]
              {:note (u/unwrap note) :synth :saw})
   "square" (fn [note & opts]
              (let [n     (u/unwrap note)
                    opts' (apply hash-map (mapv u/unwrap opts))
                    pw    (get opts' :pw 0.5)]
                {:note n :synth :square :pw pw}))
   "noise"  (fn [] {:synth :noise})
   "fm"     (fn [note & opts]
              (let [n     (u/unwrap note)
                    opts' (apply hash-map (mapv u/unwrap opts))
                    idx   (get opts' :index 1.0)
                    ratio (get opts' :ratio 2.0)]
                {:note n :synth :fm :index idx :ratio ratio}))
   "synth"  (fn [voice-arg & rest-args]
              (let [voice    (u/unwrap voice-arg)
                    args'    (mapv u/unwrap rest-args)
                    last-a   (last args')
                    has-pat? (and (seq args')
                                  (map? last-a)
                                  (fn? (:query last-a)))]
                ;; Detect common mistake: transformer (amp, pan, …) passed directly
                ;; as a synth argument instead of chained via ->>.
                (when (and (seq args') (fn? last-a) (not has-pat?))
                  (throw (js/Error. "amp, pan and other transformers must be chained with ->>, not passed as synth arguments.\nUse: (->> (synth :saw pattern) (amp 0.7))")))
                (let [opts-map (apply hash-map (if has-pat? (butlast args') args'))
                      apply-xf (fn [pat]
                                  (core/fmap
                                   (fn [v]
                                     (let [base (if (map? v) v {:note v})
                                           freq (or (:freq base)
                                                    (when (number? (:note base)) (:note base))
                                                    (when (keyword? (:note base))
                                                      (theory/note->hz (:note base))))]
                                       (merge base {:synth voice :freq freq} opts-map)))
                                   pat))]
                  (if has-pat?
                    (apply-xf last-a)
                    apply-xf))))
   "sound"  (fn [bank n] {:bank (u/unwrap bank) :n (or (u/unwrap n) 0)})})
