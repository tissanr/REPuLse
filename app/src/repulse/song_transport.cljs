(ns repulse.song-transport
  "Collect/activate for track + song buffers (Phase SONG1).
   During evaluation `track` only collects name → pattern and `song` registers a
   song value; once the whole buffer has evaluated, activate-buffer! starts the
   collected tracks under the song (or free-running when there is no song),
   then applies a pending (play-from …) and (export :song).
   Depends only on audio, fx, repulse.song, wav and the Lisp typo-hint, so the
   TEST1 harness can use it."
  (:require [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.song :as song]
            [repulse.wav :as wav]
            [repulse.lisp.eval :as leval]))

;;; Per-evaluation pending state

(defonce pending-tracks    (atom []))   ; [[kw pattern] …] in buffer order
(defonce pending-song      (atom nil))
(defonce pending-play-from (atom nil))  ; integer bar
(defonce pending-export    (atom nil))  ; {:tail secs :set-output! f}

;; Last activated song buffer {:tracks [[kw pat] …] :song song} — used by
;; play-from / export after (stop).
(defonce song-buffer (atom nil))

;; Callbacks of the most recent activation {:on-beat :on-event :set-playing!},
;; reused when play-from / export re-activate the song buffer.
(defonce ^:private activation-cbs (atom {}))

(defn reset-pending! []
  (reset! pending-tracks [])
  (reset! pending-song nil)
  (reset! pending-play-from nil)
  (reset! pending-export nil))

(defn current-song
  "The song of this evaluation, else the last activated one."
  []
  (or @pending-song (:song @song-buffer)))

(defn- src-data [src]
  (when (and src (:from src)) {:from (:from src) :to (:to src)}))

;;; Collect

(defn collect-track!
  "Register a track during evaluation. Throws on duplicates and on tracks
   defined below the song."
  [name' pat' src]
  (when (some #(= name' (first %)) @pending-tracks)
    (throw (ex-info (str "Duplicate track name :" (name name')
                         " — each track must have a unique name in the buffer")
                    (merge {:type :eval-error} (src-data src)))))
  (when @pending-song
    (throw (ex-info (str "track :" (name name') " is defined after (song …) — define tracks above the song")
                    (merge {:type :eval-error} (src-data src)))))
  (swap! pending-tracks conj [name' pat']))

(defn- hint-fn [name-str known-strs]
  (leval/typo-hint name-str known-strs))

(defn register-song!
  "Build and store this buffer's song value from section maps."
  [sections opts src]
  (when @pending-song
    (throw (ex-info "Only one (song …) per buffer"
                    (merge {:type :eval-error} (src-data src)))))
  (let [s (try
            (song/make-song sections opts (mapv first @pending-tracks) hint-fn)
            (catch :default e
              (let [data (ex-data e)]
                (if (= :song-error (:type data))
                  (let [sec    (some #(when (= (:section data) (:name %)) %) sections)
                        src'   (or (get-in sec [:track-sources (:track data)])
                                   (:source sec)
                                   src)]
                    (throw (ex-info (ex-message e)
                                    (merge (assoc data :type :eval-error) (src-data src')))))
                  (throw e)))))]
    (reset! pending-song s)
    s))

;;; Activation

(defn- play-tracks! [tracks {:keys [on-beat on-event]}]
  (doseq [[nm pat] tracks]
    (audio/play-track! nm pat on-beat on-event)
    (fx/apply-track-effects! nm (:track-fx pat))))

(defn replay-song-buffer!
  "Restart the last activated song buffer from start-bar.
   opts — {:end :stop|:loop} overrides the song's :end for this run (export)."
  [start-bar opts]
  (when-let [{:keys [tracks] s :song} @song-buffer]
    (let [cbs @activation-cbs]
      (audio/stop!)
      (audio/set-song! (merge s (select-keys opts [:end])) {:start-bar start-bar})
      (play-tracks! tracks cbs)
      (when-let [f (:set-playing! cbs)] (f true))
      true)))

(defn- apply-play-from! [bar song-activated?]
  (cond
    song-activated? nil   ; already passed as :start-bar to set-song!
    (and (audio/playing?) (:song @audio/scheduler-state)) (audio/relocate-song! bar)
    :else (replay-song-buffer! bar {})))

(defn- secs-str [x] (.toFixed (double x) 1))

(defn song-summary
  "\"24 bars, 50.0 s\" for a song at the current BPM plus tail seconds."
  [s tail]
  (str (song/song-length s) " bars, "
       (secs-str (+ (song/song-duration-secs s (audio/get-bpm)) tail)) " s"))

(defn capture-song!
  "Replay the song buffer once from bar 0 (as :end :stop) and capture the master
   output in real time. Resolves with {:left :right :sample-rate :frames :late}."
  [{:keys [tail]}]
  (-> (audio/recorder-ready)
      (.then (fn [node]
               (cond
                 (nil? node)   (throw (ex-info "(export :song) needs AudioWorklet support for real-time capture" {}))
                 (nil? @song-buffer) (throw (ex-info "no song" {}))
                 :else (do (replay-song-buffer! 0 {:end :stop})
                           (audio/capture! {:tail tail})))))))

(defn start-export!
  "Run (export :song): capture one pass and download it as a WAV."
  [{:keys [tail set-output!]}]
  (let [s (:song @song-buffer)
        out (or set-output! (fn [_ _]))]
    (-> (capture-song! {:tail tail})
        (.then (fn [{:keys [left right sample-rate]}]
                 (let [buf (wav/encode-wav-16 left right sample-rate)]
                   (wav/download-wav! buf (str "repulse-song-" (song/song-length s) "bars.wav"))
                   (out (str "exported song — " (song-summary s tail)) :success))))
        (.catch (fn [e]
                  (if (:cancelled (ex-data e))
                    (out "export cancelled" :idle)
                    (do (js/console.error "[REPuLse] song export failed:" e)
                        (out (str "Error: song export failed — " (ex-message e)) :error))))))))

(defn activate-buffer!
  "Called after every successful evaluation. Starts the collected tracks (under
   the song, if any), then applies (play-from …) and (export :song)."
  [{:keys [set-playing!] :as cbs}]
  (let [tracks    @pending-tracks
        s         @pending-song
        play-from @pending-play-from
        export    @pending-export]
    (reset-pending!)
    (when (or (seq tracks) s)
      (reset! activation-cbs cbs)
      (if s
        (audio/set-song! s {:start-bar play-from})
        (audio/clear-song!))
      (play-tracks! tracks cbs)
      (when (and (seq tracks) set-playing!) (set-playing! true))
      (reset! song-buffer (when s {:tracks tracks :song s})))
    (when (some? play-from)
      (apply-play-from! play-from (some? s)))
    (when export
      (start-export! export))))
