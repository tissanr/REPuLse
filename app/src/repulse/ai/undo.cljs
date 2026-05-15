(ns repulse.ai.undo
  "Auto-apply undo stack — saves pre-edit editor state so the user can
   revert the last assistant turn after an auto-applied propose_edit."
  (:require [repulse.ui.editor :as editor]))

(def max-stack 20)

(defonce stack       (atom []))
(defonce current-turn (atom nil))

(defn begin-turn!
  "Record which turn ID is about to begin. Call before executing tool calls."
  [id]
  (reset! current-turn id))

(defn record-pre-edit!
  "Snapshot the editor text before an auto-applied edit.
   Called by propose_edit when auto-apply is on."
  []
  (when-let [view @editor/editor-view]
    (let [text (.. view -state -doc toString)]
      (swap! stack
             (fn [s]
               (let [s (conj s {:turn-id @current-turn :text text})]
                 (if (> (count s) max-stack) (subvec s 1) s)))))))

(defn revert-last-turn!
  "Restore the editor to the snapshot at the top of the stack.
   Returns true if a revert happened, nil if the stack was empty."
  []
  (when-let [entry (peek @stack)]
    (when-let [view @editor/editor-view]
      (let [doc-len (.. view -state -doc -length)]
        (.dispatch view #js {:changes #js {:from 0 :to doc-len :insert (:text entry)}})))
    (swap! stack pop)
    true))

(defn stack-size [] (count @stack))
