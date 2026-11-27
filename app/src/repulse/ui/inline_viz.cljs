(ns repulse.ui.inline-viz
  "Phase VIZ1 — inline visualizer widgets in the main editor. A StateField
   finds every top-level form that calls pianoroll / punchcard / spiral /
   scope / spectrum and places one block widget after the line where that form
   ends. The widget holds a canvas that repulse.ui.timeline draws in its one
   animation loop: the running track's visualizers stacked, or a placeholder
   while the track isn't playing. The track is the form's (track :name …)
   name, or :_ for an anonymous top-level pattern; definitions (def, defn,
   song, …) get no widget. Main app only (not the embed).
   Exports: extension, viz-forms."
  (:require [shadow.cljs.modern :refer [defclass]]
            [clojure.string :as str]
            [repulse.ui.viz-model :as vm]
            [repulse.ui.timeline :as timeline]
            ["@codemirror/view" :refer [EditorView Decoration WidgetType]]
            ["@codemirror/state" :refer [StateField]]
            ["@codemirror/language" :refer [syntaxTree]]))

(def ^:private viz-call-re
  (js/RegExp. (str "\\((" (str/join "|" (map name vm/viz-kinds)) ")(?=[\\s)])") "g"))

(def ^:private no-widget-heads
  #{"def" "defn" "defsynth" "defmacro" "song" "section" "master" "fx" "bpm"
    "use" "samples!" "export" "play-from" "comment"})

(defn viz-forms
  "Pure. [{:from :to :track kw :kinds [kw …]}] for the top-level form ranges
   [[from to] …] of doc that call a visualizer."
  [doc ranges]
  (vec
   (keep (fn [[from to]]
           (let [text  (subs doc from to)
                 head  (second (re-find #"^\(\s*([^\s()\[\]{}\"]+)" text))
                 kinds (mapv (comp keyword second) (re-seq viz-call-re text))]
             (when (and (seq kinds) (not (contains? no-widget-heads head)))
               {:from  from
                :to    to
                :track (if (= head "track")
                         (if-let [[_ n] (re-find #"^\(\s*track\s+:([^\s()\[\]{}\"]+)" text)]
                           (keyword n)
                           :_)
                         :_)
                :kinds kinds})))
         ranges)))

(defn- top-level-ranges [^js state]
  (let [^js top (.-topNode (syntaxTree state))]
    (loop [^js n (.-firstChild top) out []]
      (if n
        (recur (.-nextSibling n)
               (if (= "List" (.-name n)) (conj out [(.-from n) (.-to n)]) out))
        out))))

(defn- default-height [kinds]
  (reduce + (map #(:height (vm/default-opts %) 64) (take 3 kinds))))

;;; Widget

(defonce ^:private next-id (atom 0))

(defclass VizWidget
  (extends WidgetType)
  (field spec)
  (field wid)
  (field observer)
  (constructor [this s]
    (super)
    (set! spec s)
    (set! wid nil)
    (set! observer nil)
    ;; WidgetType.estimatedHeight is a prototype getter: shadow it per instance
    (js/Object.defineProperty this "estimatedHeight"
                              #js {:value (+ 6 (default-height (:kinds s)))}))
  Object
  (eq [this ^js other] (= spec (.-spec other)))
  (toDOM [this ^js _view]
    (let [dom    (.createElement js/document "div")
          canvas (.createElement js/document "canvas")
          id     (swap! next-id inc)]
      (set! (.-className dom) "cm-inline-viz")
      (.setAttribute dom "data-track" (name (:track spec)))
      (set! (.-className canvas) "cm-inline-viz-canvas")
      (set! (.. canvas -style -height) (str (default-height (:kinds spec)) "px"))
      (.appendChild dom canvas)
      (set! wid id)
      (timeline/register-canvas! id canvas (:track spec))
      (when (exists? js/IntersectionObserver)
        (let [obs (js/IntersectionObserver.
                   (fn [entries]
                     (doseq [^js e (array-seq entries)]
                       (timeline/set-canvas-visible! id (.-isIntersecting e)))))]
          (.observe obs dom)
          (set! observer obs)))
      (js/requestAnimationFrame (fn [_] (timeline/redraw!)))
      dom))
  (destroy [this ^js _dom]
    (when observer (.disconnect ^js observer))
    (when wid (timeline/unregister-canvas! wid)))
  (ignoreEvent [this _e] true))

;;; Decorations

(defn- build-deco [^js state]
  (let [doc   (.. state -doc (toString))
        forms (viz-forms doc (top-level-ranges state))]
    (if (seq forms)
      (.set Decoration
            (into-array
             (for [{:keys [to track kinds]} forms]
               (let [line-end (.-to (.. state -doc (lineAt to)))]
                 (.range (.widget Decoration
                                  #js {:widget (VizWidget. {:track track :kinds kinds})
                                       :block  true
                                       :side   1})
                         line-end))))
            true)
      (.-none Decoration))))

(def ^:private field
  (.define StateField
    #js {:create  (fn [^js state] (build-deco state))
         :update  (fn [deco ^js tr]
                    (if (or (.-docChanged tr)
                            ;; the parser may finish after the doc changed
                            (not (identical? (syntaxTree (.-startState tr)) (syntaxTree (.-state tr)))))
                      (build-deco (.-state tr))
                      deco))
         :provide (fn [f] (.from (.-decorations EditorView) f))}))

(def extension #js [field])
