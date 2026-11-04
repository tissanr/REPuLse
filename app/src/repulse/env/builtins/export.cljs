(ns repulse.env.builtins.export
  "WAV export builtin (Phase EXP1). (export n), (export :track) and
   (export :song [:tail secs]) render offline, faster than real time, through
   the same WASM voices and FX as playback; (export :song … :realtime) captures
   the song in real time (SONG1). The call only validates its arguments and
   sets song-transport/pending-export; the export starts once the buffer has
   evaluated (E5), so (fx …) / (master …) forms below it are included."
  (:require [repulse.audio :as audio]
            [repulse.song-transport :as song-transport]
            [repulse.lisp.eval :as leval]))

(def ^:private default-tail-secs 2)
(def ^:private default-cycles 4)

(def ^:private realtime-error "Error: :realtime only applies to (export :song)")
(def ^:private tail-error "Error: :tail must be a number of seconds between 0 and 30")
(def ^:private running-error "Error: an export is already running — (stop) cancels it")

(defn- export-running? []
  (or @song-transport/export-running?
      (some? (:capture @audio/scheduler-state))))

(defn parse-song-opts
  "Pure. Options after :song → {:tail secs :realtime? bool} or {:error str}.
   Valid: [], [:tail s], [:realtime], [:tail s :realtime], [:realtime :tail s]."
  [opts]
  (loop [opts opts acc {:tail default-tail-secs :realtime? false} seen #{}]
    (if (empty? opts)
      acc
      (let [[k & more] opts]
        (cond
          (and (= :realtime k) (not (seen :realtime)))
          (recur more (assoc acc :realtime? true) (conj seen :realtime))

          (and (= :tail k) (not (seen :tail)) (seq more))
          (let [v (first more)]
            (if (or (not (number? v)) (js/isNaN v) (< v 0) (> v 30))
              {:error tail-error}
              (recur (rest more) (assoc acc :tail v) (conj seen :tail))))

          :else {:error tail-error})))))

(defn- export-song
  "(export :song [:tail secs] [:realtime]). Returns a status or \"Error: …\" string."
  [opts set-output!]
  (let [{:keys [error tail realtime?]} (parse-song-opts opts)]
    (cond
      error error

      (nil? (song-transport/current-song))
      "Error: no song — evaluate a buffer that ends with (song …) first"

      (and realtime?
           (or (not (.-AudioWorkletNode js/globalThis))
               (and @audio/ctx (nil? (.-audioWorklet ^js @audio/ctx)))))
      "Error: (export :song) needs AudioWorklet support for real-time capture"

      (export-running?)
      running-error

      :else
      (let [s (song-transport/current-song)]
        (reset! song-transport/pending-export
                {:kind :song :tail tail :realtime? realtime? :set-output! set-output!})
        (if realtime?
          (str "exporting song — " (song-transport/song-summary s tail)
               " in real time… (stop) cancels")
          (str "rendering song — " (song-transport/song-summary s tail) " offline…"))))))

(defn- export-cycles
  "(export n) / (export :track) / (export). Returns a status or \"Error: …\" string."
  [args set-output!]
  (let [arg      (first args)
        n        (if (number? arg) arg default-cycles)
        track-kw (when (keyword? arg) arg)
        ;; tracks defined earlier in this same buffer are activated only after
        ;; evaluation (SONG1) — include them so (export n) sees them
        all      (merge (:tracks @audio/scheduler-state)
                        (into {} @song-transport/pending-tracks))]
    (cond
      (some #{:realtime} args)
      realtime-error

      (and (number? arg) (or (js/isNaN arg) (< arg 1)))
      "Error: export needs at least 1 cycle"

      (and track-kw (not (contains? all track-kw)))
      (str "Error: no track :" (name track-kw))

      (empty? all)
      "Error: no active tracks to export"

      (export-running?)
      running-error

      :else
      (let [n' (int n)]
        (reset! song-transport/pending-export
                {:kind :cycles :n n' :track track-kw :set-output! set-output!})
        (str "rendering " (song-transport/cycles-label track-kw)
             " — " n' " cycles offline…")))))

(defn make-builtins
  "ctx — {:set-output! f}"
  [{:keys [set-output!]}]
  {"export"
   (fn [& args]
     (let [args' (mapv leval/unwrap args)]
       (if (= :song (first args'))
         (export-song (vec (rest args')) set-output!)
         (export-cycles args' set-output!))))})
