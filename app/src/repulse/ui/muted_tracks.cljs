(ns repulse.ui.muted-tracks
  "Grey out the code of tracks that do not play: every top-level
   (track :name …) form whose name is in the scheduler's muted set (its own
   :mute flag, or silenced by another track's :solo). The set is what the
   scheduler plays right now, so an edited but not yet evaluated :mute does
   not grey anything. Main app only.
   Exports: extension, init!, muted-forms."
  (:require [repulse.audio :as audio]
            ["@codemirror/view" :refer [EditorView Decoration]]
            ["@codemirror/state" :refer [StateField StateEffect]]
            ["@codemirror/language" :refer [syntaxTree]]))

(defn muted-forms
  "Pure. The [from to] ranges among the top-level form ranges of doc that are
   (track :name …) forms with name in muted (a set of keywords)."
  [doc ranges muted]
  (vec
   (keep (fn [[from to]]
           (when-let [[_ n] (re-find #"^\(\s*track\s+:([^\s()\[\]{}\"]+)" (subs doc from to))]
             (when (contains? muted (keyword n)) [from to])))
         ranges)))

(defn- top-level-ranges [^js state]
  (let [^js top (.-topNode (syntaxTree state))]
    (loop [^js n (.-firstChild top) out []]
      (if n
        (recur (.-nextSibling n)
               (if (= "List" (.-name n)) (conj out [(.-from n) (.-to n)]) out))
        out))))

(def ^:private muted-mark
  (.mark Decoration #js {:class "cm-muted-track"
                         :attributes #js {:title "muted — this track does not play"}}))

(defn- build [^js state muted]
  (if (seq muted)
    (let [forms (muted-forms (.. state -doc (toString)) (top-level-ranges state) muted)]
      (if (seq forms)
        (.set Decoration (into-array (for [[f t] forms] (.range muted-mark f t))) true)
        (.-none Decoration)))
    (.-none Decoration)))

;; Carries the scheduler's muted set into the editor state.
(def ^:private set-muted (.define StateEffect))

(defn- current-muted [] (or (:muted @audio/scheduler-state) #{}))

(def ^:private field
  (.define StateField
    #js {:create  (fn [^js state] #js {:muted (current-muted)
                                       :deco  (build state (current-muted))})
         :update  (fn [^js v ^js tr]
                    (let [eff   (some #(when (.is ^js % set-muted) (.-value ^js %))
                                      (array-seq (.-effects tr)))
                          muted (or eff (.-muted v))]
                      (if (or eff
                              (.-docChanged tr)
                              (not (identical? (syntaxTree (.-startState tr))
                                               (syntaxTree (.-state tr)))))
                        #js {:muted muted :deco (build (.-state tr) muted)}
                        v)))
         :provide (fn [f] (.from (.-decorations EditorView) f (fn [^js v] (.-deco v))))}))

(def extension #js [field])

(defn init!
  "Push every change of the muted set into the editor view (an atom holding
   the EditorView)."
  [view-atom]
  (add-watch audio/scheduler-state ::muted-tracks
             (fn [_ _ old new]
               (when (not= (:muted old) (:muted new))
                 (when-let [^js v @view-atom]
                   (.dispatch v #js {:effects (.of set-muted (or (:muted new) #{}))}))))))
