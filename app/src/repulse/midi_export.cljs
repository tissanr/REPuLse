(ns repulse.midi-export
  "Phase SK1 — turn pattern events into the spec of a Standard MIDI File.
   Pure: patterns in, midi/midi-file spec out. Pitch and drum mapping (K12,
   K13), channels (K14), velocity from amp with tweens resolved (K15), exact
   ticks from rational time (K10)."
  (:require [clojure.string :as str]
            [repulse.audio :as audio]
            [repulse.core :as core]
            [repulse.midi :as midi]
            [repulse.song :as song]
            [repulse.song-transport :as st]
            [repulse.theory :as theory]))

;; K13 — GM Level 1 percussion key map (Strudel / tidal-drum-machines names)
(def gm-drum-notes
  {:bd 36 :rim 37 :sd 38 :cp 39 :hh 42 :lt 45 :oh 46 :mt 47
   :cr 49 :ht 50 :rd 51 :tb 54 :cb 56 :sh 70})

(defn drum-note
  "Drum map lookup: the full name, then (for names with `_`) the part after the
   last `_`. overrides is merged over the GM map. → note number or nil."
  [kw overrides]
  (let [m (merge gm-drum-notes overrides)]
    (or (get m kw)
        (let [s (name kw)
              i (str/last-index-of s "_")]
          (when (and i (< (inc i) (count s)))
            (get m (keyword (subs s (inc i)))))))))

(defn- clamp-note [n] (-> n (Math/min 127) (Math/max 0)))

(defn- hz-note [hz] (midi/hz->midi (double hz)))

(defn- keyword-note
  "A keyword → pitch / drum / skip / unmapped. A drum-map entry wins over a
   note name (:cb is a cowbell, not C-flat)."
  [kw overrides]
  (cond
    (= :_ kw) {:kind :skip}

    (get (merge gm-drum-notes overrides) kw)
    {:kind :drum :note (get (merge gm-drum-notes overrides) kw)}

    (theory/note-keyword? kw)
    {:kind :pitch :note (clamp-note (theory/note->midi kw))}

    :else
    (if-let [n (drum-note kw overrides)]
      {:kind :drum :note n}
      {:kind :unmapped :name kw})))

(defn event-note
  "K12. Event value → {:kind :pitch :note n} | {:kind :drum :note n}
   | {:kind :skip} | {:kind :unmapped :name kw}."
  [value drum-map]
  (cond
    (keyword? value) (keyword-note value drum-map)
    (number? value)  {:kind :pitch :note (hz-note value)}

    (map? value)
    (let [{:keys [note freq bank synth]} value]
      (cond
        (keyword? note) (keyword-note note drum-map)
        (number? note)  {:kind :pitch :note (hz-note note)}
        (number? freq)  {:kind :pitch :note (hz-note freq)}
        (keyword? bank) (keyword-note bank drum-map)
        :else           {:kind :skip}))

    :else {:kind :skip}))

(defn rat->tick
  "K10. Rational [n d] cycles → ticks, rounded."
  [[n d]]
  (Math/round (/ (* n midi/ticks-per-bar) d)))

(def ^:private melodic-channels [1 2 3 4 5 6 7 8 9 11 12 13 14 15 16])

(defn melodic-channel
  "K14. Channel of the i-th exported track's melodic events (never 10)."
  [i]
  (nth melodic-channels (mod i 15)))

(def ^:private drum-channel 10)

(defn- event->note
  "→ {:note map-or-nil :unmapped kw-or-nil} for one onset event."
  [{:keys [whole value]} pat tween-st idx cycle-dur drum-map]
  (let [t   (* (core/rat->float (:start whole)) cycle-dur)
        v   (audio/resolve-tweens value tween-st t cycle-dur)
        res (event-note v drum-map)]
    (case (:kind res)
      (:pitch :drum)
      (let [tick  (rat->tick (:start whole))
            end   (rat->tick (:end whole))]
        {:note {:tick     tick
                :dur      (Math/max 1 (- end tick))
                :note     (:note res)
                :velocity (midi/amp->velocity (when (map? v) (:amp v)))
                :channel  (if (= :drum (:kind res)) drum-channel (melodic-channel idx))}})
      :unmapped {:unmapped (:name res)}
      {})))

(defn- track-result
  "events — onset events of one track in song/cycle time → {:name :notes :skipped}."
  [kw pat events idx cycle-dur drum-map]
  (let [tween-st (audio/tween-state-for pat 0)
        rs       (map #(event->note % pat tween-st idx cycle-dur drum-map) events)]
    {:name    (name kw)
     :notes   (vec (keep :note rs))
     :skipped (frequencies (keep :unmapped rs))}))

(defn- finish [results spec]
  {:spec    (assoc spec :tracks (mapv #(select-keys % [:name :notes]) results))
   :skipped (apply merge-with + {} (map :skipped results))})

(defn cycles->midi
  "{:tracks [[kw pat] …] :n n :cycle-dur cd :drum-map m}
   → {:spec midi-file-spec :skipped {kw count}}. Cycles 0…n−1 via core/query-onsets."
  [{:keys [tracks n cycle-dur drum-map]}]
  (finish
   (map-indexed
    (fn [idx [kw pat]]
      (track-result kw pat
                    (for [c (range n)
                          ev (core/query-onsets pat {:start [c 1] :end [(inc c) 1]})]
                      ev)
                    idx cycle-dur drum-map))
    tracks)
   {:cycle-dur cycle-dur :end-tick (* n midi/ticks-per-bar) :markers []}))

(defn song->midi
  "{:song s :tracks {kw pat} :cycle-dur cd :drum-map m} → same shape. One pass
   of the song via song/song-onsets (mute ignored); section markers (K16)."
  [{s :song :keys [tracks cycle-dur drum-map]}]
  (let [kws   (st/stem-tracks s)
        by-tr (group-by :track
                        (for [bar (range (song/song-length s))
                              o   (song/song-onsets s tracks #{} bar)]
                          o))]
    (finish
     (map-indexed
      (fn [idx kw]
        (track-result kw (get tracks kw) (map :event (get by-tr kw)) idx cycle-dur drum-map))
      kws)
     {:cycle-dur cycle-dur
      :end-tick  (* (song/song-length s) midi/ticks-per-bar)
      :markers   (mapv (fn [{:keys [name start]}]
                         {:tick (* start midi/ticks-per-bar)
                          :text (cljs.core/name name)})
                       (:sections s))})))

(defn skipped-suffix
  "\"\" or \" — skipped N events with no GM drum note (:a :b); map them with :drum-map\"."
  [skipped]
  (if (empty? skipped)
    ""
    (str " — skipped " (reduce + (vals skipped)) " events with no GM drum note ("
         (str/join " " (map #(str %) (sort-by name (keys skipped))))
         "); map them with :drum-map")))
