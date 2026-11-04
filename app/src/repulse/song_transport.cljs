(ns repulse.song-transport
  "Collect/activate for track + song buffers (Phase SONG1).
   During evaluation `track` only collects name → pattern and `song` registers a
   song value; once the whole buffer has evaluated, activate-buffer! starts the
   collected tracks under the song (or free-running when there is no song),
   then applies a pending (play-from …) and (export …).
   Exports (Phase EXP1) render offline (repulse.offline-render) and fall back
   to the real-time capture when the offline path can't run.
   Depends only on audio, fx, offline-render, repulse.song, wav and the Lisp
   typo-hint, so the TEST1 harness can use it."
  (:require [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.offline-render :as offline-render]
            [repulse.song :as song]
            [repulse.wav :as wav]
            [repulse.lisp.eval :as leval]))

;;; Per-evaluation pending state

(defonce pending-tracks    (atom []))   ; [[kw pattern] …] in buffer order
(defonce pending-song      (atom nil))
(defonce pending-play-from (atom nil))  ; integer bar
;; {:kind :cycles :n n :track kw-or-nil :set-output! f}
;; {:kind :song :tail secs :realtime? bool :set-output! f}
(defonce pending-export    (atom nil))
(defonce pending-track-srcs (atom {}))  ; {kw src} — first definition's source

;; nil, or (fn [src] → buffer name) while a multi-buffer project evaluates
;; (set only by repulse.project-eval/run-project, Phase SONG4).
(defonce buffer-of (atom nil))

;; True while an export (offline render or real-time capture) runs (E14).
(defonce export-running? (atom false))

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
  (reset! pending-export nil)
  (reset! pending-track-srcs {}))

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
    (let [f (or @buffer-of (constantly nil))
          a (f (get @pending-track-srcs name'))
          b (f src)]
      (throw (ex-info (if (and a b (not= a b))
                        (str "Duplicate track name :" (name name')
                             " — already defined in buffer :" a
                             "; track names must be unique across the project")
                        (str "Duplicate track name :" (name name')
                             " — each track must have a unique name in the buffer"))
                      (merge {:type :eval-error} (src-data src))))))
  (when @pending-song
    (throw (ex-info (str "track :" (name name') " is defined after (song …) — define tracks above the song")
                    (merge {:type :eval-error} (src-data src)))))
  (swap! pending-track-srcs assoc name' src)
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

(defn replay-buffer!
  "Restart song buffer {:tracks [[kw pat] …] :song s} from start-bar.
   opts — {:end :stop|:loop} overrides the song's :end for this run (export)."
  [buffer start-bar opts]
  (when-let [{:keys [tracks] s :song} buffer]
    (let [cbs @activation-cbs]
      (audio/stop!)
      (audio/set-song! (merge s (select-keys opts [:end])) {:start-bar start-bar})
      (play-tracks! tracks cbs)
      (when-let [f (:set-playing! cbs)] (f true))
      true)))

(defn replay-song-buffer!
  "Restart the last activated song buffer from start-bar.
   opts — {:end :stop|:loop} overrides the song's :end for this run (export)."
  [start-bar opts]
  (replay-buffer! @song-buffer start-bar opts))

(defn- apply-play-from! [bar song-activated?]
  (cond
    song-activated? nil   ; already passed as :start-bar to set-song!
    (and (audio/playing?) (:song @audio/scheduler-state)) (audio/relocate-song! bar)
    :else (replay-song-buffer! bar {})))

(defn cue-bar!
  "Cue `bar` like (play-from bar) issued while a song is active: playing → it
   becomes the next scheduled bar; stopped → the last song buffer restarts
   there. WS1 (status-head progress segments, inspector ▶ cue)."
  [bar]
  (apply-play-from! bar false))

(defn- secs-str [x] (.toFixed (double x) 1))

(defn song-summary
  "\"24 bars, 50.0 s\" for a song at the current BPM plus tail seconds."
  [s tail]
  (str (song/song-length s) " bars, "
       (secs-str (+ (song/song-duration-secs s (audio/get-bpm)) tail)) " s"))

(defn capture-song!
  "Replay song buffer {:tracks :song} once from bar 0 (as :end :stop) and
   capture the master output in real time.
   Resolves with {:left :right :sample-rate :frames :late}.
   The one-argument form captures the last activated song buffer."
  ([opts] (capture-song! @song-buffer opts))
  ([buffer {:keys [tail]}]
   (-> (audio/recorder-ready)
       (.then (fn [node]
                (cond
                  (nil? node)   (throw (ex-info "(export :song) needs AudioWorklet support for real-time capture" {}))
                  (nil? buffer) (throw (ex-info "no song" {}))
                  :else (do (replay-buffer! buffer 0 {:end :stop})
                            (audio/capture! {:tail tail}))))))))

(defn synthetic-song-buffer
  "E9: a one-section song that plays cycles 0…n−1 of tracks once, so the
   real-time capture can stand in for an offline (export n)."
  [tracks n]
  (let [kws (mapv first tracks)]
    {:tracks (vec tracks)
     :song   (song/make-song [(song/make-section :export n kws)] {:end :stop} kws nil)}))

(defn cycles-label
  "\"all tracks\" or \":kick\" for an (export n) / (export :track) job."
  [track]
  (if track (str ":" (name track)) "all tracks"))

(defn cycles-filename [track n]
  (str "repulse-" (if track (name track) "all") "-" n "cycles.wav"))

(defn fallback-notice [reason]
  (str "offline render unavailable (" reason ") — capturing in real time instead…"))

(defn- export-tracks
  "Tracks of an (export n) / (export :track) job, after activation."
  [track]
  (let [all (:tracks @audio/scheduler-state)]
    (if track
      (select-keys all [track])
      all)))

(defn- download! [{:keys [left right sample-rate]} filename]
  (wav/download-wav! (wav/encode-wav-16 left right sample-rate) filename))

(defn- export-job
  "→ {:job offline-render job :filename str :done str :capture f} for a
   pending export (nil job when there is nothing to render)."
  [{:keys [kind n track tail]}]
  (let [cd (:cycle-dur @audio/scheduler-state)]
    (case kind
      :cycles
      (let [tracks (export-tracks track)]
        {:job      (when (seq tracks)
                     {:kind :cycles :tracks tracks :n n :cycle-dur cd})
         :filename (cycles-filename track n)
         :done     (str "exported " (cycles-label track) " — " n " cycles")
         :capture  #(capture-song! (synthetic-song-buffer tracks n) {:tail 0})})

      :song
      (let [buf @song-buffer
            s   (:song buf)]
        {:job      (when s
                     {:kind :song :tracks (into {} (:tracks buf)) :song s
                      :tail tail :cycle-dur cd})
         :filename (str "repulse-song-" (song/song-length s) "bars.wav")
         :done     (str "exported song — " (song-summary s tail))
         :capture  #(capture-song! buf {:tail tail})}))))

(defn- report-failure! [out e]
  (cond
    (:cancelled (ex-data e)) (out "export cancelled" :idle)
    :else (do (js/console.error "[REPuLse] export failed:" e)
              (out (str "Error: export failed — " (ex-message e)) :error))))

(defn start-export!
  "Run a pending export after the buffer has evaluated (E5): render offline
   (or capture in real time for (export :song :realtime) and as the fallback)
   and download the WAV."
  [{:keys [kind realtime? set-output!] :as pending}]
  (let [out (or set-output! (fn [_ _]))
        {:keys [job filename done capture]} (export-job pending)
        run-capture (fn []
                      (-> (capture)
                          (.then (fn [res]
                                   (download! res filename)
                                   (out (if (= kind :song) done (str done " (real time)"))
                                        :success)))))]
    (cond
      (nil? job)
      (out (if (= kind :song)
             "Error: no song — evaluate a buffer that ends with (song …) first"
             "Error: no active tracks to export")
           :error)

      @export-running?
      (out "Error: an export is already running — (stop) cancels it" :error)

      :else
      (do
        (reset! export-running? true)
        (-> (if realtime?
              (run-capture)
              (-> (offline-render/render! job)
                  (.then (fn [res]
                           (download! res filename)
                           (out (str done " (offline)") :success)))
                  (.catch (fn [e]
                            (if (:fallback (ex-data e))
                              (do (js/console.warn "[REPuLse] offline export unavailable:" (ex-message e))
                                  (out (fallback-notice (ex-message e)) :idle)
                                  (run-capture))
                              (throw e))))))
            (.catch (fn [e] (report-failure! out e)))
            (.finally (fn [] (reset! export-running? false))))))))

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
