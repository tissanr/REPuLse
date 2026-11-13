(ns repulse.env.builtins.export
  "WAV export builtin (Phase EXP1, SK1). (export n), (export :track [n]),
   (export :stems …) and (export :song [:tail secs]) render offline, faster than real time, through
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

(def ^:private usage-error
  "Error: usage — (export [cycles]), (export :track [cycles]), (export :stems …) or (export :song …)")
(def ^:private no-song-error
  "Error: no song — evaluate a buffer that ends with (song …) first")
(def ^:private cycles-error "Error: export needs at least 1 cycle")
(def ^:private stems-args-error "Error: (export :stems) takes a cycle count or :tail secs")

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
      no-song-error

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

(defn- cycles-shape
  "Valid argument shapes of (export …) without :song / :stems: [], [n], [kw],
   [kw n] → {:track kw-or-nil :n n-or-nil}; anything else nil."
  [args]
  (case (count args)
    0 {}
    1 (let [a (first args)]
        (cond (number? a)  {:n a}
              (keyword? a) {:track a}))
    2 (let [[a b] args]
        (when (and (keyword? a) (number? b)) {:track a :n b}))
    nil))

(defn- export-cycles
  "(export [n]) / (export :track [n]). Returns a status or \"Error: …\" string."
  [args set-output!]
  (let [shape    (cycles-shape args)
        n        (or (:n shape) default-cycles)
        track-kw (:track shape)
        ;; tracks defined earlier in this same buffer are activated only after
        ;; evaluation (SONG1) — include them so (export n) sees them
        all      (merge (:tracks @audio/scheduler-state)
                        (into {} @song-transport/pending-tracks))]
    (cond
      (some #{:realtime} args)
      realtime-error

      (nil? shape)
      usage-error

      (and (:n shape) (or (js/isNaN n) (< n 1)))
      cycles-error

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

(defn parse-stems-opts
  "Pure (K3). Options after :stems → {:n n} | {:tail secs} | {} or {:error str}.
   Valid: [], [n], [:tail s]. :realtime is never accepted."
  [opts]
  (let [[a b] opts]
    (cond
      (some #{:realtime} opts) {:error realtime-error}
      (empty? opts) {}
      (and (= 1 (count opts)) (number? a))
      (if (or (js/isNaN a) (< a 1)) {:error cycles-error} {:n (int a)})
      (= :tail a)
      (if (and (= 2 (count opts)) (number? b) (not (js/isNaN b)) (<= 0 b 30))
        {:tail b}
        {:error tail-error})
      :else {:error stems-args-error})))

(defn- export-stems
  "(export :stems [n | :tail secs]). Returns a status or \"Error: …\" string."
  [opts set-output!]
  (let [{:keys [error n tail]} (parse-stems-opts opts)
        s       (song-transport/current-song)
        all     (merge (:tracks @audio/scheduler-state)
                       (into {} @song-transport/pending-tracks))
        song?   (and s (nil? n))
        count-t (if song? (count (song-transport/stem-tracks s)) (count all))]
    (cond
      error error

      (and tail (nil? s))
      no-song-error

      (zero? count-t)
      "Error: no active tracks to export"

      (export-running?)
      running-error

      :else
      (let [tail' (or tail default-tail-secs)]
        (reset! song-transport/pending-export
                {:kind :stems :n n :tail tail' :set-output! set-output!})
        (str "rendering stems — " count-t " tracks, "
             (if song?
               (song-transport/song-summary s tail')
               (str (or n default-cycles) " cycles"))
             " offline…")))))

(defn make-builtins
  "ctx — {:set-output! f}"
  [{:keys [set-output!]}]
  {"export"
   (fn [& args]
     (let [args' (mapv leval/unwrap args)]
       (case (first args')
         :song  (export-song (vec (rest args')) set-output!)
         :stems (export-stems (vec (rest args')) set-output!)
         (export-cycles args' set-output!))))})
