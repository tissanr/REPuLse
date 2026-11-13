(ns repulse.ui.inline-slider
  "Inline (slider v min max) widgets in the main editor (Phase WS3): a StateField
   replaces each well-formed slider form with a draggable bar unless a selection
   touches it; ⌘K S / Ctrl-K S wraps or unwraps. Main app only (not the embed).
   Exports: extension, toggle-at!."
  (:require [shadow.cljs.modern :refer [defclass]]
            [repulse.lisp-patcher :as patcher]
            [repulse.ui.slider-model :as slm]
            [repulse.eval-orchestrator :as eo]
            ["@codemirror/view" :refer [EditorView Decoration WidgetType keymap]]
            ["@codemirror/state" :refer [StateField Prec]]))

;; Active widget drag: {:view :from :cfg :t0 :acc :x :last :fine?} or nil.
(defonce ^:private drag (atom nil))

;;; Widget DOM

(defn- pct [cfg]
  (/ (Math/round (* 10000 (slm/inline-t cfg (:v cfg)))) 100))

(defn- title-of [{:keys [cfg form-text]}]
  (str form-text " · " (slm/inline-range-text cfg) " · drag, ⇧ fine"))

(defn- fill-dom! [^js dom {:keys [from cfg] :as spec}]
  (.setAttribute dom "data-from" (str from))
  (.setAttribute dom "title" (title-of spec))
  (when-let [^js f (.querySelector dom ".cm-inline-slider-fill")]
    (set! (.. f -style -width) (str (pct cfg) "%")))
  (when-let [^js v (.querySelector dom ".cm-inline-slider-val")]
    (set! (.-textContent v) (slm/inline-fmt cfg (:v cfg)))))

(defn- slider-el [^js view from]
  (.querySelector (.-dom view) (str ".cm-inline-slider[data-from=\"" from "\"]")))

;;; Drag

(declare on-move on-up)

(defn- stop-drag! []
  (when-let [{:keys [view from]} @drag]
    (reset! drag nil)
    (.removeEventListener js/window "pointermove" on-move)
    (.removeEventListener js/window "pointerup" on-up)
    (.removeEventListener js/window "pointercancel" on-up)
    (eo/end-slider-drag!)
    (when-let [^js el (slider-el view from)]
      (.remove (.-classList el) "dragging")
      (.remove (.-classList el) "fine"))))

(defn- on-move [^js e]
  (when-let [{:keys [view from cfg t0 acc x last]} @drag]
    (let [fine? (.-shiftKey e)
          acc   (+ acc (* (- (.-clientX e) x) (if fine? 0.1 1)))
          text  (slm/inline-fmt cfg (slm/inline-value cfg (slm/inline-drag-t t0 acc)))]
      (swap! drag assoc :acc acc :x (.-clientX e) :last text :fine? (boolean fine?))
      (when (not= text last)
        (eo/inline-slider-patch-and-eval! from text))
      ;; after the patch: CodeMirror may have redrawn the widget
      (when-let [^js el (slider-el view from)]
        (.add (.-classList el) "dragging")
        (.toggle (.-classList el) "fine" (boolean fine?))))))

(defn- on-up [_] (stop-drag!))

(defn- on-pointerdown [^js view ^js e]
  (.preventDefault e)
  (.stopPropagation e)
  (when (zero? (.-button e))
    (let [^js el (.-currentTarget e)
          from   (js/parseInt (.getAttribute el "data-from") 10)
          doc    (.. view -state -doc (toString))
          form   (patcher/slider-form-at doc from)
          cfg    (when form (slm/inline-cfg form))]
      (.focus view)
      (when cfg
        (stop-drag!)
        (eo/begin-slider-drag!)
        (reset! drag {:view view :from from :cfg cfg
                      :t0 (slm/inline-t cfg (:v cfg)) :acc 0
                      :x (.-clientX e) :last (:text (:value form))
                      :fine? (boolean (.-shiftKey e))})
        (.add (.-classList el) "dragging")
        (.toggle (.-classList el) "fine" (boolean (.-shiftKey e)))
        (.addEventListener js/window "pointermove" on-move)
        (.addEventListener js/window "pointerup" on-up)
        (.addEventListener js/window "pointercancel" on-up)))))

(defn- on-dblclick [^js view ^js e]
  (.preventDefault e)
  (let [from (js/parseInt (.getAttribute (.-currentTarget e) "data-from") 10)]
    (when-let [{:keys [value]} (patcher/slider-form-at (.. view -state -doc (toString)) from)]
      (.dispatch view #js {:selection #js {:anchor (:from value) :head (:to value)}})
      (.focus view))))

(defclass SliderWidget
  (extends WidgetType)
  (field spec)
  (constructor [this s]
    (super)
    (set! spec s))
  Object
  (eq [this ^js other] (= spec (.-spec other)))
  (toDOM [this ^js view]
    (let [dom  (.createElement js/document "span")
          fill (.createElement js/document "span")
          val  (.createElement js/document "span")
          fine (.createElement js/document "span")]
      (set! (.-className dom) (str "cm-inline-slider"
                                   (when (= (:from @drag) (:from spec))
                                     (str " dragging" (when (:fine? @drag) " fine")))))
      (set! (.-className fill) "cm-inline-slider-fill")
      (set! (.-className val) "cm-inline-slider-val")
      (set! (.-className fine) "cm-inline-slider-fine")
      (set! (.-textContent fine) "×0.1")
      (.appendChild dom fill)
      (.appendChild dom val)
      (.appendChild dom fine)
      (fill-dom! dom spec)
      (.addEventListener dom "pointerdown" #(on-pointerdown view %))
      (.addEventListener dom "dblclick" #(on-dblclick view %))
      dom))
  (updateDOM [this ^js dom ^js _view]
    (fill-dom! dom spec)
    true)
  (ignoreEvent [this _e] true))

;;; Decorations

(defn- selection-ranges [^js state]
  (mapv (fn [^js r] [(.-from r) (.-to r)]) (array-seq (.. state -selection -ranges))))

(defn- build-deco [doc forms ^js state]
  (let [vis (patcher/visible-of-forms doc forms (selection-ranges state))]
    (if (seq vis)
      (.set Decoration
            (into-array
              (for [{:keys [form cfg]} vis]
                (.range (.replace Decoration
                                  #js {:widget (SliderWidget.
                                                 {:from       (:from form)
                                                  :value-text (:text (:value form))
                                                  :cfg        cfg
                                                  :form-text  (subs doc (:from form) (:to form))})})
                        (:from form) (:to form))))
            true)
      (.-none Decoration))))

(defn- field-value [^js state doc forms]
  #js {:doc doc :forms forms :deco (build-deco doc forms state)})

(def ^:private field
  (.define StateField
    #js {:create  (fn [^js state]
                    (let [doc (.. state -doc (toString))]
                      (field-value state doc (patcher/find-slider-forms doc))))
         :update  (fn [^js v ^js tr]
                    (cond
                      (.-docChanged tr)
                      (let [doc (.. tr -state -doc (toString))]
                        (field-value (.-state tr) doc (patcher/find-slider-forms doc)))

                      (.-selection tr)
                      (field-value (.-state tr) (.-doc v) (.-forms v))

                      :else v))
         :provide (fn [f] (.from (.-decorations EditorView) f (fn [^js v] (.-deco v))))}))

;;; ⌘K S

(defn toggle-at!
  "Wrap the number at `pos` in (slider …) or unwrap the slider there (one undo
   step, no re-evaluation). The cursor moves to the toggled literal unless
   opts has :keep-cursor? true (inspector ≋). Returns true when it changed."
  ([view pos] (toggle-at! view pos nil))
  ([^js view pos {:keys [keep-cursor?]}]
   (if-let [{:keys [from to insert cursor]}
            (when view (patcher/slider-toggle-edit (.. view -state -doc (toString)) pos))]
     (do (.dispatch view (if keep-cursor?
                           #js {:changes #js {:from from :to to :insert insert}}
                           #js {:changes   #js {:from from :to to :insert insert}
                                :selection #js {:anchor cursor}}))
         true)
     false)))

(def extension
  #js [field
       (.high Prec (.of keymap #js [#js {:key "Mod-k s"
                                         :run (fn [^js view]
                                                (toggle-at! view (.. view -state -selection -main -head))
                                                true)}]))])
