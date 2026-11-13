(ns repulse.ui.selection-highlight
  "Editor highlight for the outline selection (Phase WS1): every line touched
   by a selected entry gets a tinted background (.ws-sel-line) and a gutter
   bar (.ws-sel-gutter).  The decorations map through edits; the outline panel
   re-sends the ranges after each re-extraction.  Installed only in the main
   app (never in the <repulse-editor> embed).
   Exports: set-ranges!, extension."
  (:require ["@codemirror/view" :refer [EditorView Decoration GutterMarker gutterLineClass]]
            ["@codemirror/state" :refer [StateEffect StateField RangeSet]]))

(def ^:private set-ranges (.define StateEffect))

(def ^:private line-deco (.line Decoration #js {:class "ws-sel-line"}))

;; One shared marker instance (GutterMarker.compare is identity first).
(def ^:private gutter-marker
  (let [^js m (js/Object.create (.-prototype GutterMarker))]
    (set! (.-elementClass m) "ws-sel-gutter")
    m))

(def ^:private empty-value
  #js {:deco (.-none Decoration) :gutter (.-empty RangeSet)})

(defn- line-starts
  "Sorted distinct line-start positions of every line touched by ranges."
  [^js doc ranges]
  (let [len (.-length doc)]
    (->> ranges
         (mapcat (fn [{:keys [from to]}]
                   (let [from (max 0 (min from len))
                         to   (max from (min to len))
                         a    (.-number (.lineAt doc from))
                         b    (.-number (.lineAt doc (max from (dec to))))]
                     (map #(.-from (.line doc %)) (range a (inc b))))))
         distinct
         sort)))

(defn- build [^js doc ranges]
  (let [starts (line-starts doc ranges)]
    (if (empty? starts)
      empty-value
      #js {:deco   (.set Decoration (into-array (map #(.range line-deco %) starts)))
           :gutter (.of RangeSet (into-array (map #(.range gutter-marker %) starts)))})))

(def ^:private field
  (.define StateField
    #js {:create  (fn [_] empty-value)
         :update  (fn [^js v ^js tr]
                    (if-let [eff (some #(when (.is % set-ranges) %) (array-seq (.-effects tr)))]
                      (build (.. tr -state -doc) (.-value eff))
                      (if (.-docChanged tr)
                        #js {:deco   (.map (.-deco v) (.-changes tr))
                             :gutter (.map (.-gutter v) (.-changes tr))}
                        v)))
         :provide (fn [f]
                    #js [(.from (.-decorations EditorView) f (fn [^js v] (.-deco v)))
                         (.from gutterLineClass f (fn [^js v] (.-gutter v)))])}))

(defn set-ranges!
  "Highlight the lines of ranges ([{:from :to}]) in view; [] clears."
  [^js view ranges]
  (when view
    (.dispatch view #js {:effects #js [(.of set-ranges (vec ranges))]})))

(def extension #js [field])
