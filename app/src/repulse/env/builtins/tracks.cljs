(ns repulse.env.builtins.tracks
  "Multi-track transport builtins: track, mute!, unmute!, solo!, clear!, tracks, upd, tap!
   Phase WS5: `track` takes trailing :mute / :solo flags; mute!/unmute!/solo!
   edit the code (command bar only)."
  (:require [repulse.core :as core]
            [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.song-transport :as song-transport]
            [repulse.tracks-model :as tracks-model]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.ui.editor :as editor]
            [clojure.string :as cstr]
            ["@codemirror/lint" :refer [setDiagnostics]]))

;; True while a reconciling (whole-code) evaluation runs — set by
;; repulse.eval-orchestrator/evaluate*. mute!/unmute!/solo! are code edits and
;; refuse to run inside the code they would rewrite (Phase WS5).
(defonce reconciling? (atom false))

;; (fn [] → bool): true when the project has a single buffer, so (upd) sees
;; the whole program. Set by repulse.eval-orchestrator/init!.
(defonce single-buffer-ref (atom nil))

;; Set by (upd) after it evaluated the editor buffer of a single-buffer
;; project: {:underscore? bool}. The orchestrator then reconciles the tracks
;; to the buffer like a whole-code evaluation (Phase WS5).
(defonce whole-buffer-upd (atom nil))

;; (fn [track-kw flag on?] → true | false) — patches the track's form and
;; schedules the re-evaluation; false when no (track :name …) form exists.
;; Set by repulse.eval-orchestrator/init! (breaks the require cycle).
(defonce flag-edit-ref (atom nil))

(defn- src-data [x]
  (when-let [{:keys [from to]} (:source x)]
    (when from {:from from :to to})))

(defn- flag-edit-builtin
  "mute! / unmute! / solo! (Phase WS5, decision 9)."
  [fname flag on? in-code-hint done-msg]
  (fn [track-name]
    (let [name' (leval/unwrap track-name)
          tn    (str ":" (name name'))]
      (cond
        @reconciling?
        (throw (ex-info (str fname " edits the code — " (in-code-hint tn) " instead")
                        (merge {:type :eval-error} (src-data track-name))))

        (not (and @flag-edit-ref (@flag-edit-ref name' flag on?)))
        (throw (ex-info (str "track " tn " is not in the code")
                        (merge {:type :eval-error} (src-data track-name))))

        :else (str "=> " done-msg " " tn)))))

(defn make-builtins
  "ctx — {:on-beat f :set-playing! f :set-output! f :make-stop-fn f
           :env-atom atom :seen-tracks atom}"
  [{:keys [on-beat set-playing! set-output! make-stop-fn env-atom seen-tracks]}]
  {"track"
   (fn [track-name pat & flag-args]
     (let [name' (leval/unwrap track-name)
           pat'  (leval/unwrap pat)
           src   (:source track-name)
           flags (tracks-model/parse-flags (mapv leval/unwrap flag-args))]
       (when-let [msg (:error flags)]
         ;; WS5: underline the offending option
         (throw (ex-info msg (merge {:type :eval-error}
                                    (or (src-data (nth flag-args (:index flags)))
                                        (src-data track-name))))))
       (if (core/pattern? pat')
         (do
           ;; SONG1: tracks are only collected here; eval-orchestrator activates
           ;; them (under the buffer's song, if any) once the whole buffer has
           ;; evaluated. collect-track! rejects duplicates and tracks below a song.
           (song-transport/collect-track! name' pat' src flags)
           (swap! seen-tracks conj name')
           (str "=> track :" (cljs.core/name name') " playing"))
         "Error: second argument to track must be a pattern")))

   "play"
   (fn [& _args]
     (throw (js/Error. "play is renamed to track — use (track :name pattern)")))

   ;; WS5: code edits — from the command bar they patch the track's form
   ;; (like the inspector M/S buttons) and re-evaluate.
   "mute!"
   (flag-edit-builtin "mute!" :mute true
                      #(str "write (track " % " … :mute)") "muted")

   "unmute!"
   (flag-edit-builtin "unmute!" :mute false
                      #(str "remove :mute from (track " % " …)") "unmuted")

   "solo!"
   (flag-edit-builtin "solo!" :solo true
                      #(str "write (track " % " … :solo)") "solo")

   "clear!"
   (fn
     ([]
      (audio/stop!)
      (reset! song-transport/song-buffer nil)
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

   "upd"
   (fn []
     (when-let [view @editor/editor-view]
       (let [code   (.. view -state -doc (toString))
             env    (assoc @env-atom "stop" (make-stop-fn))
             ;; In a single-buffer project the buffer is the whole program:
             ;; reconcile like Alt+Enter, so mute!/solo! may not run inside it.
             whole? (boolean (some-> @single-buffer-ref (apply [])))
             prev   @reconciling?
             result (do (reset! reconciling? (or prev whole?))
                        (try (lisp/eval-string code env)
                             (finally (reset! reconciling? prev))))]
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
               (when whole?
                 (reset! whole-buffer-upd {:underscore? (core/pattern? val)}))
               (cond
                 (core/pattern? val)
                 (do (audio/play-track! :_ val on-beat
                                        ;; keep a running project highlighter
                                        (or (:on-event @audio/scheduler-state)
                                            editor/highlight-range!))
                     (fx/apply-track-effects! :_ (:track-fx val))
                     (set-playing! true)
                     (set-output! "updated" :success))
                 (nil? val) nil
                 (string? val) (set-output! val :success)
                 :else (set-output! (str "=> " (pr-str val)) :success)))))))
     ;; Always return nil so evaluate! does not re-process upd's output
     nil)

   "tap!"
   (fn []
     (if-let [bpm (audio/tap!)]
       (str "=> " (.toFixed bpm 1) " BPM")
       "=> tap again…"))})
