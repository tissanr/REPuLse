(ns repulse.env.builtins.midi
  "MIDI builtins: midi-sync!, midi-map, midi-out, midi-clock-out!, midi-export."
  (:require [repulse.core :as core]
            [repulse.audio :as audio]
            [repulse.midi :as midi]
            [repulse.fx :as fx]
            [repulse.theory :as theory]
            [repulse.params :as params]
            [repulse.lisp.eval :as leval]))

(defn make-builtins
  "ctx — {:set-output! f}"
  [{:keys [set-output!]}]
  {"midi-sync!"
   (fn [enabled?]
     (let [on? (leval/unwrap enabled?)]
       (audio/set-midi-sync! on?)
       (str "=> MIDI sync " (if on? "enabled" "disabled"))))

   "midi-map"
   (fn [& args]
     (let [args'  (mapv leval/unwrap args)
           cc-num (int (nth args' 1))
           target (nth args' 2)]
       (-> (midi/ensure-access!)
           (.then (fn [_]
                    (midi/map-cc! cc-num target
                      (fn [tgt val]
                        (case tgt
                          :filter (fx/set-param! "filter" "value" val)
                          :amp    (.setValueAtTime (.-gain @audio/master-gain)
                                                   (float val)
                                                   (.-currentTime (audio/get-ctx)))
                          :bpm    (audio/set-bpm! (+ 60 (* val 180)))
                          nil)))))
           (.catch (fn [e]
                     (set-output! (str "MIDI error: " e) :error))))
       "mapping MIDI CC…"))

   "midi-out"
   (fn
     ([ch]   (params/midi-out (leval/unwrap ch)))
     ([ch p] (params/midi-out (leval/unwrap ch) (leval/unwrap p))))

   "midi-clock-out!"
   (fn [on?]
     (let [on (leval/unwrap on?)]
       (if on
         (-> (midi/ensure-access!)
             (.then (fn [_]
                      (midi/start-clock! (audio/get-bpm))))
             (.catch (fn [e]
                       (set-output! (str "MIDI error: " e) :error))))
         (midi/stop-clock!))
       nil))

   "midi-export"
   (fn [& args]
     (let [args'      (mapv leval/unwrap args)
           track-name (first args')
           n-cycles   (or (second args') 4)
           state      @audio/scheduler-state
           pattern    (get-in state [:tracks track-name])
           bpm        (audio/get-bpm)
           cycle-dur  (:cycle-dur state)]
       (if-not pattern
         (str "Error: no track :" (when track-name (name track-name)))
         (let [events
               (for [c    (range n-cycles)
                     :let [sp {:start [c 1] :end [(inc c) 1]}]
                     ev   (core/query pattern sp)
                     :let [v  (:value ev)
                           hz (cond
                                (number? v) v
                                (and (map? v) (number? (:note v))) (:note v)
                                (and (map? v) (keyword? (:note v))
                                     (theory/note-keyword? (:note v)))
                                (theory/note->hz (:note v))
                                (theory/note-keyword? v) (theory/note->hz v)
                                :else nil)]
                     :when hz
                     :let [ps (core/rat->float (:start (:part ev)))
                           pe (core/rat->float (:end   (:part ev)))]]
                 {:time-sec     (* (- ps c) cycle-dur)
                  :duration-sec (* (- pe ps) cycle-dur)
                  :midi-note    (midi/hz->midi (double hz))
                  :channel      1})
               midi-data (midi/export-midi (vec events) bpm)
               blob      (js/Blob. #js [midi-data] #js {:type "audio/midi"})
               url       (.createObjectURL js/URL blob)
               a         (.createElement js/document "a")]
           (set! (.-href a) url)
           (set! (.-download a) (str "repulse-" (name track-name) ".mid"))
           (.appendChild (.-body js/document) a)
           (.click a)
           (.removeChild (.-body js/document) a)
           (js/setTimeout #(.revokeObjectURL js/URL url) 1000)
           (str "exported " n-cycles " cycles of :" (name track-name) " as MIDI")))))})
