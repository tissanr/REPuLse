(ns repulse.env.builtins.tracks
  "Multi-track transport builtins: track, mute!, unmute!, solo!, clear!, tracks, upd, tap!"
  (:require [repulse.core :as core]
            [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.ui.editor :as editor]
            ["@codemirror/lint" :refer [setDiagnostics]]))

(defn make-builtins
  "ctx — {:on-beat f :set-playing! f :set-output! f :make-stop-fn f
           :env-atom atom :evaluate-ref atom :seen-tracks atom}"
  [{:keys [on-beat set-playing! set-output! make-stop-fn env-atom evaluate-ref seen-tracks]}]
  {"track"
   (fn [track-name pat]
     (let [name' (leval/unwrap track-name)
           pat'  (leval/unwrap pat)
           src   (:source track-name)]
       (when (contains? @seen-tracks name')
         (throw (ex-info (str "Duplicate track name :" (cljs.core/name name')
                              " — each track must have a unique name in the buffer")
                         (cond-> {:type :eval-error}
                           src (merge {:from (:from src) :to (:to src)})))))
       (swap! seen-tracks conj name')
       (if (core/pattern? pat')
         (do
           (audio/play-track! name' pat' on-beat editor/highlight-range!)
           (fx/apply-track-effects! name' (:track-fx pat'))
           (set-playing! true)
           (str "=> track :" (cljs.core/name name') " playing"))
         "Error: second argument to track must be a pattern")))

   "play"
   (fn [& _args]
     (throw (js/Error. "play is renamed to track — use (track :name pattern)")))

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
         (str "=> (" (clojure.string/join " " (map #(str ":" (name %)) ks)) ")")
         "=> ()")))

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
                 :else (set-output! (str "=> " (pr-str val)) :success)))))))
     ;; Always return nil so evaluate! does not re-process upd's output
     nil)

   "tap!"
   (fn []
     (if-let [bpm (audio/tap!)]
       (str "=> " (.toFixed bpm 1) " BPM")
       "=> tap again…"))})
