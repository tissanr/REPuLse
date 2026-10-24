(ns repulse.ai.undo
  "Auto-apply undo stack — saves the pre-turn editor state so the user can
   revert the last assistant turn after auto-applied propose_edit calls.
   One entry per turn: the snapshot taken before that turn's first edit."
  (:require [repulse.ui.editor :as editor]))

(def max-stack 20)

(defonce stack       (atom []))
(defonce current-turn (atom nil))

(defn begin-turn!
  "Record which turn ID is about to begin. Call before executing tool calls."
  [id]
  (reset! current-turn id))

(defn push-snapshot
  "Pure stack update: add {:turn-id :text} unless this turn already has a
   snapshot (later edits in the same turn must not overwrite the pre-turn
   text). Keeps at most max-stack turns."
  [s turn-id text]
  (if (= turn-id (:turn-id (peek s)))
    s
    (let [s (conj s {:turn-id turn-id :text text})]
      (if (> (count s) max-stack) (subvec s 1) s))))

(defn record-pre-edit!
  "Snapshot the editor text before an auto-applied edit.
   Called by propose_edit when auto-apply is on."
  []
  (when-let [view @editor/editor-view]
    (swap! stack push-snapshot @current-turn (.. view -state -doc toString))))

(defn revert-last-turn!
  "Restore the editor to the text it had before the most recent assistant turn.
   Returns true if a revert happened, nil if the stack was empty."
  []
  (when-let [entry (peek @stack)]
    (when-let [view @editor/editor-view]
      (let [doc-len (.. view -state -doc -length)]
        (.dispatch view #js {:changes #js {:from 0 :to doc-len :insert (:text entry)}})))
    (swap! stack pop)
    true))

(defn stack-size [] (count @stack))
