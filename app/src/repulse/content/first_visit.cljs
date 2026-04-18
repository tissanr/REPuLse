(ns repulse.content.first-visit
  "First-visit detection and random demo loading.
   Responsibility: load a random demo on first visit or after reset!.
   Exports: set-editor-content!, first-visit-setup!"
  (:require [repulse.audio :as audio]
            [repulse.content.demos :as demos]))

(defn set-editor-content!
  "Replace the editor buffer with text.
   editor-view-atom — atom holding the current CodeMirror EditorView."
  [editor-view-atom text]
  (when-let [view @editor-view-atom]
    (.dispatch view
               #js {:changes #js {:from   0
                                  :to     (.. view -state -doc -length)
                                  :insert text}})))

(defn first-visit-setup!
  "Load a random demo template on first visit (no localStorage) or after reset!.
   editor-view-atom — atom holding the current CodeMirror EditorView.
   set-output!-fn   — fn to display a status message (msg status)."
  [editor-view-atom set-output!-fn]
  (let [picks [:techno :ambient :house :dnb :minimal]
        pick  (rand-nth picks)
        demo  (get demos/demo-templates pick)]
    (set-editor-content! editor-view-atom (:code demo))
    (audio/set-bpm! (:bpm demo))
    (set-output!-fn
      (str "Welcome to REPuLse! Loaded :" (name pick)
           " — press Alt+Enter to play")
      :success)))
