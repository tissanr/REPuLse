(ns repulse.env.builtins
  "Lisp environment assembly — creates and caches the Lisp env atom with all
   app-layer built-in functions.
   Responsibility: own env-atom, builtin-names, and seen-tracks; assemble the full
   Lisp environment on first call to ensure-env!.
   Exports: env-atom, builtin-names, seen-tracks, evaluate-ref, ensure-env!, init!."
  (:require [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.core :as core]
            [repulse.audio :as audio]
            [repulse.bus :as bus]
            [repulse.midi :as midi]
            [repulse.theory :as theory]
            [repulse.params :as params]
            [repulse.synth :as synth]
            [repulse.samples :as samples]
            [repulse.fx :as fx]
            [repulse.session :as session]
            [repulse.ui.editor :as editor]
            [repulse.content.demos :as demos]
            [repulse.content.tutorial :as tutorial]
            [repulse.snippets :as snippets]
            [repulse.plugin-loading :as plugin-loading]
            [clojure.string :as cstr]
            ["@codemirror/lint" :refer [setDiagnostics]]))

;;; Owned atoms

;; The main Lisp environment — created once, reused across evaluations.
(defonce env-atom (atom nil))

;; Keys present in the initial env — used to filter built-ins from user defs.
(defonce builtin-names (atom #{}))

;; Tracks which track names have been defined in the current evaluation pass.
;; Reset by evaluate! before each eval; checked by the `track` builtin to
;; detect duplicate track names in the same buffer.
(defonce seen-tracks (atom #{}))

;; Freesound API key — set via (freesound-key! "...")
(defonce ^:private freesound-api-key (atom nil))

;;; Lazy reference to evaluate! — set by app.cljs after eval-orchestrator loads.
;; This breaks the ensure-env! ↔ evaluate! circular dependency:
;; builtins that call evaluate (demo, load-gist) deref this atom at call time,
;; not at construction time — so the fn is always bound when actually needed.
(defonce evaluate-ref (atom nil))

;;; Callbacks from app.cljs — populated via init! before ensure-env! is called.
(defonce ^:private cbs (atom {}))

(defn init!
  "Wire app-level callback fns into this module.
   Must be called before ensure-env!.
   config — {:on-beat-fn f :set-playing!-fn f :set-output!-fn f :make-stop-fn-fn f :share!-fn f}"
  [{:keys [on-beat-fn set-playing!-fn set-output!-fn make-stop-fn-fn share!-fn]}]
  (reset! cbs {:on-beat       on-beat-fn
               :set-playing!  set-playing!-fn
               :set-output!   set-output!-fn
               :make-stop-fn  make-stop-fn-fn
               :share!        share!-fn}))

;;; Environment construction

(defn ensure-env! []
  (when (nil? @env-atom)
    (let [{:keys [on-beat set-playing! set-output! make-stop-fn]} @cbs]
      (samples/init!)
      (reset! env-atom
              (assoc (leval/make-env (make-stop-fn) audio/set-bpm!)
                     :*register-synth-fn* synth/register-synth!
                     ;; --- Multi-track ---
                     "track"
                     (fn [track-name pat]
                       (let [name' (leval/unwrap track-name)
                             pat'  (leval/unwrap pat)
                             ;; Source position of the track-name keyword (for squiggle).
                             ;; Keywords are SourcedVal records; :source holds {:from N :to N}.
                             src   (:source track-name)]
                         ;; Duplicate track name in this evaluation pass → hard error.
                         (when (contains? @seen-tracks name')
                           (throw (ex-info (str "Duplicate track name :" (cljs.core/name name')
                                               " — each track must have a unique name in the buffer")
                                           (cond-> {:type :eval-error}
                                             src (merge {:from (:from src) :to (:to src)})))))
                         (swap! seen-tracks conj name')
                         (if (core/pattern? pat')
                           (do
                             (audio/play-track! name' pat' on-beat editor/highlight-range!)
                             ;; Apply per-track FX from pattern metadata (clear old chain first)
                             (fx/apply-track-effects! name' (:track-fx pat'))
                             (set-playing! true)
                             (str "=> track :" (cljs.core/name name') " playing"))
                           "Error: second argument to track must be a pattern")))
                     "play" (fn [& _args] (throw (js/Error. "play is renamed to track — use (track :name pattern)")))
                     "mute!"
                     (fn [track-name]
                       (let [name' (leval/unwrap track-name)]
                         (audio/mute-track! name')
                         (str "=> muted :" (name name'))))
                     "unmute!"
                     (fn [track-name]
                       (let [name' (leval/unwrap track-name)]
                         (audio/unmute-track! name')
                         (str "=> unmuted :" (name name'))))
                     "solo!"
                     (fn [track-name]
                       (let [name' (leval/unwrap track-name)]
                         (audio/solo-track! name')
                         (str "=> solo :" (name name'))))
                     "clear!"
                     (fn
                       ([]
                        (audio/stop!)
                        (editor/clear-highlights!)
                        (set-playing! false)
                        "=> cleared all tracks")
                       ([track-name]
                        (let [name' (leval/unwrap track-name)]
                          (audio/clear-track! name')
                          (when (not (:playing? @audio/scheduler-state))
                            (set-playing! false))
                          (str "=> cleared :" (name name')))))
                     "tracks"
                     (fn []
                       (let [ks (keys (:tracks @audio/scheduler-state))]
                         (if (seq ks)
                           (str "=> (" (cstr/join " " (map #(str ":" (name %)) ks)) ")")
                           "=> ()")))
                     ;; --- Hot-swap update ---
                     "upd"
                     (fn []
                       (when-let [view @editor/editor-view]
                         (let [code   (.. view -state -doc (toString))
                               env    (assoc @env-atom "stop" (make-stop-fn))
                               result (lisp/eval-string code env)]
                           (if (lisp/eval-error? result)
                             (do (editor/clear-highlights!)
                                 (let [{:keys [from to]} (:source result)]
                                   (when (and from to (< from to))
                                     (.dispatch view
                                                (setDiagnostics (.-state view)
                                                                #js [#js {:from from :to to
                                                                          :severity "error"
                                                                          :message (:message result)}]))))
                                 (set-output! (str "Error: " (:message result)) :error))
                             (do
                               (let [val (:result result)]
                                 (cond
                                   (core/pattern? val)
                                   (do (audio/play-track! :_ val on-beat editor/highlight-range!)
                                       (fx/apply-track-effects! :_ (:track-fx val))
                                       (set-playing! true)
                                       (set-output! "updated" :success))
                                   (nil? val) nil
                                   (string? val) (set-output! val :success)
                                   :else (set-output! (str "=> " (pr-str val)) :success))))))
                       ;; Always return nil so evaluate! does not re-process upd's output
                       nil))
                     ;; --- Tap tempo ---
                     "tap!"
                     (fn []
                       (if-let [bpm (audio/tap!)]
                         (str "=> " (.toFixed bpm 1) " BPM")
                         "=> tap again…"))
                     ;; --- MIDI clock ---
                     "midi-sync!"
                     (fn [enabled?]
                       (let [on? (leval/unwrap enabled?)]
                         (audio/set-midi-sync! on?)
                         (str "=> MIDI sync " (if on? "enabled" "disabled"))))
                     ;; --- Samples ---
                     "samples!"
                     (fn [url]
                       (let [url' (leval/unwrap url)]
                         (samples/load-external! url')
                         (str "loading " url' "…")))
                     "sample-banks"
                     (fn [] (samples/format-banks))
                     ;; --- Plugins ---
                     "load-plugin"  (plugin-loading/load-plugin-builtin)
                     "unload-plugin" (plugin-loading/unload-plugin-builtin)
                     "bank"
                     (fn [prefix]
                       (samples/set-bank-prefix! (leval/unwrap prefix))
                       (str "bank: " (if prefix (name (leval/unwrap prefix)) "cleared")))
                     ;; --- Named audio/control buses ---
                     "bus"
                     (fn [& args]
                       (let [args'    (mapv leval/unwrap args)
                             bus-name (first args')
                             bus-type (or (second args') :control)]
                         (when-not (keyword? bus-name)
                           (throw (js/Error. "bus: first argument must be a keyword, e.g. (bus :lfo :control)")))
                         (when-not (#{:control :audio} bus-type)
                           (throw (js/Error. (str "bus: type must be :control or :audio, got " bus-type))))
                         (bus/create-bus! (audio/get-ctx) bus-name bus-type)
                         (str "=> bus " bus-name " (" (name bus-type) ")")))
                     ;; fx: context-aware — per-track transformer when called from ->>, global otherwise
                     "fx"
                     (fn [& raw-args]
                       (let [args'    (mapv leval/unwrap raw-args)
                             last-arg (last args')
                             ;; When ->> passes the pattern as last arg, route per-track
                             per-track? (and (> (count args') 1)
                                            (core/pattern? last-arg))]
                         (if per-track?
                           ;; ── Per-track mode: annotate pattern with FX metadata ──────────
                           (let [fx-args     (butlast args')
                                 pat         last-arg
                                 effect-name (cljs.core/name (first fx-args))
                                 rest-fx     (rest fx-args)
                                 params      (if (keyword? (first rest-fx))
                                               ;; All named: (fx :reverb :wet 0.3)
                                               (into {} (map (fn [[k v]] [(cljs.core/name k) v])
                                                             (partition 2 rest-fx)))
                                               ;; Positional first, then optional named: (fx :delay 0.25 :feedback 0.4 :wet 0.5)
                                               (let [named (rest rest-fx)]
                                                 (into (when (seq rest-fx) {"value" (first rest-fx)})
                                                       (when (keyword? (first named))
                                                         (map (fn [[k v]] [(cljs.core/name k) v])
                                                              (partition 2 named))))))]
                             (update pat :track-fx (fnil conj []) {:name effect-name :params (or params {})}))
                           ;; ── Global chain mode: apply to master chain ─────────────────
                           (do
                             (let [first-arg (first args')]
                               (cond
                                 (= first-arg :off)
                                 (fx/bypass! (cljs.core/name (second args')) true)

                                 (= first-arg :on)
                                 (fx/bypass! (cljs.core/name (second args')) false)

                                 (= first-arg :remove)
                                 (fx/remove-effect! (cljs.core/name (second args')))

                                 :else
                                 (let [effect-name (cljs.core/name first-arg)
                                       rest-args   (rest args')]
                                   (if (keyword? (first rest-args))
                                     (doseq [[k v] (partition 2 rest-args)]
                                       (fx/set-param! effect-name (cljs.core/name k) v))
                                     (fx/set-param! effect-name "value" (first rest-args))))))
                             nil))))
                     ;; --- Share ---
                     "share!"
                     (fn [] (when-let [f (:share! @cbs)] (f)) nil)
                     ;; --- Snippet library ---
                     "snippet" (snippets/snippet-builtin editor/editor-view evaluate-ref)
                     ;; --- Demo templates ---
                     "demo" (demos/demo-builtin editor/editor-view evaluate-ref)
                     ;; --- Interactive tutorial ---
                     "tutorial" (tutorial/tutorial-builtin editor/editor-view)
                     ;; --- Gist import ---
                     "load-gist"
                     (fn [url]
                       (let [url'    (leval/unwrap url)
                             raw-url (if (re-find #"gist\.githubusercontent\.com" url')
                                       url'
                                       ;; Convert gist.github.com/user/id → API URL
                                       (let [[_ gist-id] (re-find #"/([a-f0-9]+)/?$" url')]
                                         (str "https://api.github.com/gists/" gist-id)))]
                         (if (re-find #"api\.github\.com" raw-url)
                           ;; API path — fetch JSON, extract first file's content
                           (-> (js/fetch raw-url)
                               (.then #(.json %))
                               (.then (fn [data]
                                        (let [files      (js->clj (.-files data))
                                              first-file (second (first files))
                                              content    (get first-file "content")]
                                          (when-let [view @editor/editor-view]
                                            (.dispatch view
                                                       #js {:changes #js {:from   0
                                                                          :to     (.. view -state -doc -length)
                                                                          :insert content}})
                                            (when-let [f @evaluate-ref] (f content))))))
                               (.catch (fn [e]
                                         (set-output! (str "Gist load failed: " e) :error))))
                           ;; Raw URL — fetch text directly
                           (-> (js/fetch raw-url)
                               (.then #(.text %))
                               (.then (fn [text]
                                        (when-let [view @editor/editor-view]
                                          (.dispatch view
                                                     #js {:changes #js {:from   0
                                                                        :to     (.. view -state -doc -length)
                                                                        :insert text}})
                                          (when-let [f @evaluate-ref] (f text)))))
                               (.catch (fn [e]
                                         (set-output! (str "Gist load failed: " e) :error)))))
                         (str "loading gist…")))
                     ;; --- WAV export ---
                     "export"
                     (fn [& args]
                       (let [arg       (when (seq args) (leval/unwrap (first args)))
                             n-cycles  (if (number? arg) (int arg) 4)
                             track-kw  (when (keyword? arg) arg)
                             state     @audio/scheduler-state
                             all-tracks (:tracks state)
                             tracks    (cond
                                         (nil? track-kw) all-tracks
                                         (contains? all-tracks track-kw) {track-kw (get all-tracks track-kw)}
                                         :else {})
                             cycle-dur (:cycle-dur state)
                             duration  (* n-cycles cycle-dur)
                             sr        44100
                             n-frames  (int (* sr duration))
                             offline   (js/OfflineAudioContext. 2 n-frames sr)]
                         (if (empty? tracks)
                           (if track-kw
                             (str "Error: no track :" (name track-kw))
                             "Error: no active tracks to export")
                           (do
                             ;; Schedule all events for N cycles into the offline context
                             (doseq [c (range n-cycles)]
                               (let [sp {:start [(int c) 1] :end [(int (inc c)) 1]}]
                                 (doseq [[_track-name pattern] tracks]
                                   (when pattern
                                     (let [evs (core/query pattern sp)]
                                       (doseq [ev evs]
                                         (let [part-start (core/rat->float (:start (:part ev)))
                                               t          (* (- part-start c) cycle-dur)
                                               abs-t      (+ (* c cycle-dur) t)]
                                           (when (>= abs-t 0)
                                             (audio/play-event offline abs-t (:value ev))))))))))
                             ;; Render and encode as WAV
                             (-> (.startRendering offline)
                                 (.then (fn [buffer]
                                          (let [ch-l       (.getChannelData buffer 0)
                                                ch-r       (.getChannelData buffer 1)
                                                n          (.-length ch-l)
                                                bps        16
                                                n-ch       2
                                                data-bytes (* n n-ch (/ bps 8))
                                                buf        (js/ArrayBuffer. (+ 44 data-bytes))
                                                dv         (js/DataView. buf)]
                                            ;; RIFF header
                                            (doto dv
                                              (.setUint8   0 82) (.setUint8   1 73)  ; R I
                                              (.setUint8   2 70) (.setUint8   3 70)  ; F F
                                              (.setUint32  4 (+ 36 data-bytes) true)
                                              (.setUint8   8 87) (.setUint8   9 65)  ; W A
                                              (.setUint8  10 86) (.setUint8  11 69)  ; V E
                                              ;; fmt chunk
                                              (.setUint8  12 102) (.setUint8 13 109) ; f m
                                              (.setUint8  14 116) (.setUint8 15 32)  ; t _
                                              (.setUint32 16 16 true)                ; chunk size
                                              (.setUint16 20 1 true)                 ; PCM
                                              (.setUint16 22 n-ch true)
                                              (.setUint32 24 sr true)
                                              (.setUint32 28 (* sr n-ch (/ bps 8)) true)
                                              (.setUint16 32 (* n-ch (/ bps 8)) true)
                                              (.setUint16 34 bps true)
                                              ;; data chunk
                                              (.setUint8  36 100) (.setUint8 37 97)  ; d a
                                              (.setUint8  38 116) (.setUint8 39 97)  ; t a
                                              (.setUint32 40 data-bytes true))
                                            ;; Interleaved 16-bit PCM samples
                                            (dotimes [i n]
                                              (let [l      (Math/max -1 (Math/min 1 (aget ch-l i)))
                                                    r      (Math/max -1 (Math/min 1 (aget ch-r i)))
                                                    offset (+ 44 (* i 4))]
                                                (.setInt16 dv offset       (int (* l 32767)) true)
                                                (.setInt16 dv (+ offset 2) (int (* r 32767)) true)))
                                            ;; Trigger download
                                            (let [blob (js/Blob. #js [buf] #js {:type "audio/wav"})
                                                  url  (.createObjectURL js/URL blob)
                                                  a    (.createElement js/document "a")]
                                              (set! (.-href a) url)
                                              (set! (.-download a) (str "repulse-"
                                                                         (if track-kw (name track-kw) "all")
                                                                         "-" n-cycles "cycles.wav"))
                                              (.appendChild (.-body js/document) a)
                                              (.click a)
                                              (.removeChild (.-body js/document) a)
                                              (js/setTimeout #(.revokeObjectURL js/URL url) 1000)))))
                                 (.catch (fn [e]
                                           (js/console.error "[REPuLse] export failed:" e))))
                             (str "exporting "
                                  (if track-kw (str ":" (name track-kw)) "all tracks")
                                  " — " n-cycles " cycles…")))))
                     ;; --- MIDI controller input ---
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
                     ;; --- MIDI note output ---
                     "midi-out"
                     (fn
                       ([ch]   (params/midi-out (leval/unwrap ch)))
                       ([ch p] (params/midi-out (leval/unwrap ch) (leval/unwrap p))))
                     ;; --- MIDI clock output ---
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
                     ;; --- MIDI file export ---
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
                             (str "exported " n-cycles " cycles of :" (name track-name) " as MIDI")))))
                     ;; --- Freesound ---
                     "freesound-key!"
                     (fn [key]
                       (reset! freesound-api-key (leval/unwrap key))
                       "Freesound API key set")
                     "freesound!"
                     (fn [query]
                       (let [q   (leval/unwrap query)
                             key @freesound-api-key]
                         (if-not key
                           "Error: set API key first with (freesound-key! \"your-key\")"
                           (do
                             (-> (js/fetch (str "https://freesound.org/apiv2/search/text/"
                                                "?query=" (js/encodeURIComponent q)
                                                "&token=" key
                                                "&fields=id,name,previews"
                                                "&page_size=5"))
                                 (.then #(.json %))
                                 (.then (fn [data]
                                          (let [results (js->clj (.-results data) :keywordize-keys true)]
                                            (doseq [{:keys [id previews]} results]
                                              (when-let [url (get previews :preview-hq-mp3)]
                                                (samples/register-url! (str "freesound-" id) url)))
                                            (swap! samples/loaded-sources conj
                                                   {:type :freesound :query q :count (count results)})
                                            (set-output!
                                              (str "loaded " (count results) " sounds: "
                                                   (clojure.string/join ", "
                                                     (map #(str ":freesound-" (:id %)) results)))
                                              :success))))
                                 (.catch (fn [e]
                                           (set-output! (str "Freesound error: " e) :error))))
                             "searching freesound…"))))
                     ;; --- Session reset ---
                     "reset!"
                     (fn []
                       (audio/stop!)
                       (session/wipe!)
                       (.reload js/window.location)
                       nil)))
      ;; Wire the FX event notification callback (used by sidechain plugin)
      (swap! audio/scheduler-state assoc :on-fx-event fx/notify-fx-event!)
      ;; Snapshot built-in names so render-context-panel! can filter them out
      (reset! builtin-names (set (keys @env-atom))))))
