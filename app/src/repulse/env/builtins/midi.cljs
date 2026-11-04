(ns repulse.env.builtins.midi
  "MIDI builtins: midi-sync!, midi-map, midi-out, midi-clock-out!, midi-export."
  (:require             [repulse.audio :as audio]
            [repulse.midi :as midi]
            [repulse.fx :as fx]
            [repulse.params :as params]
            [repulse.lisp.eval :as leval]
            [repulse.download :as download]
            [repulse.midi-export :as midi-export]
            [repulse.song :as song]
            [repulse.song-transport :as song-transport]))

(def ^:private usage-error
  "Error: usage — (midi-export :track [cycles]) or (midi-export :song), optionally followed by :drum-map {…}")
(def ^:private cycles-error "Error: midi-export needs at least 1 cycle")
(def ^:private drum-map-error
  "Error: :drum-map must map keywords to MIDI notes 0–127, e.g. {:perc 60}")
(def ^:private no-song-error
  "Error: no song — evaluate a buffer that ends with (song …) first")

(defn- drum-map? [m]
  (and (map? m)
       (every? (fn [[k v]] (and (keyword? k) (integer? v) (<= 0 v 127))) m)))

(defn parse-midi-args
  "Pure. Unwrapped args of (midi-export …) → {:target :song|kw :n n :drum-map m}
   or {:error str}. Forms: (:song [:drum-map m]), (:track [n] [:drum-map m])."
  [args]
  (let [[target & more] args
        song?           (= :song target)
        [n more]        (if (and (not song?) (number? (first more)))
                          [(first more) (rest more)]
                          [nil more])
        [k m & extra]   more]
    (cond
      (not (keyword? target)) {:error usage-error}
      (and (seq more) (or (not= :drum-map k) (< (count more) 2) (seq extra)))
      {:error usage-error}
      (and (some? n) (or (js/isNaN n) (< n 1))) {:error cycles-error}
      (and (seq more) (not (drum-map? m))) {:error drum-map-error}
      :else {:target target :n (if song? nil (int (or n 4))) :drum-map (or m {})})))

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
                      (midi/start-clock! (audio/get-bpm))
                      ;; Follow later (bpm …) changes while the clock runs
                      (add-watch audio/scheduler-state ::clock-bpm
                                 (fn [_ _ old new]
                                   (when (not= (:cycle-dur old) (:cycle-dur new))
                                     (midi/update-clock-bpm! (audio/get-bpm)))))))
             (.catch (fn [e]
                       (set-output! (str "MIDI error: " e) :error))))
         (do (remove-watch audio/scheduler-state ::clock-bpm)
             (midi/stop-clock!)))
       nil))

   "midi-export"
   (fn [& args]
     (let [{:keys [error target n drum-map]}
           (parse-midi-args (mapv leval/deep-unwrap args))]
       (if error
         error
         (let [cycle-dur (:cycle-dur @audio/scheduler-state)]
           (if (= :song target)
             (if-let [{s :song tracks :tracks} (song-transport/current-song-buffer)]
               (let [{:keys [spec skipped]}
                     (midi-export/song->midi {:song s :tracks (into {} tracks)
                                              :cycle-dur cycle-dur :drum-map drum-map})
                     bars (song/song-length s)]
                 (download/download-parts! #js [(midi/midi-file spec)] "audio/midi"
                                           (str "repulse-song-" bars "bars.mid"))
                 (str "exported song as MIDI — " (count (:tracks spec)) " tracks, "
                      bars " bars" (midi-export/skipped-suffix skipped)))
               no-song-error)
             (let [all     (merge (:tracks @audio/scheduler-state)
                                  (into {} @song-transport/pending-tracks))
                   pattern (get all target)]
               (if-not pattern
                 (str "Error: no track :" (name target))
                 (let [{:keys [spec skipped]}
                       (midi-export/cycles->midi {:tracks [[target pattern]] :n n
                                                  :cycle-dur cycle-dur :drum-map drum-map})]
                   (download/download-parts! #js [(midi/midi-file spec)] "audio/midi"
                                             (str "repulse-" (name target) ".mid"))
                   (str "exported " n " cycles of :" (name target) " as MIDI"
                        (midi-export/skipped-suffix skipped))))))))))})
