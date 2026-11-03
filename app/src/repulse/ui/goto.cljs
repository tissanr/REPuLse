(ns repulse.ui.goto
  "Go-to-definition for the main editor (SONG3): Cmd/Ctrl-click and F12 on a
   name jump to its def/defn/defsynth/track form, Shift+F12 jumps back.
   Also owns the reveal helper and the flash decoration shared with the outline
   panel.  Installed only in the main app (never in the <repulse-editor> embed).
   Phase SONG4: when the active buffer has no definition, the other project
   buffers are searched through the `cross-buffer` hooks (set by app.cljs) and
   the jump switches buffers; Shift+F12 switches back.
   Exports: name-at, goto-definition!, go-back!, reveal!, push-jump!, jump-stack,
            cross-buffer, extension."
  (:require [repulse.outline :as outline]
            ["@codemirror/view" :refer [EditorView Decoration keymap]]
            ["@codemirror/state" :refer [StateEffect StateField Prec]]
            ["@codemirror/language" :refer [syntaxTree]]))

(def ^:private MAX-STACK 50)

;; {:buffer name :pos cursor} before each jump, newest last.
(defonce jump-stack (atom []))

;; nil, or hooks for multi-buffer projects (Phase SONG4), set by app.cljs:
;;   :active  (fn [] → active buffer name)
;;   :exists? (fn [name] → boolean)
;;   :find    (fn [kind text] → {:buffer :entry} | nil) — other buffers, tab order
;;   :switch! (fn [name]) — show that buffer in the editor
(defonce cross-buffer (atom nil))

(defn- active-buffer []
  (when-let [f (:active @cross-buffer)] (f)))

(defn push-jump!
  "Push the cursor position (and buffer) of `state` onto the jump stack."
  [state]
  (swap! jump-stack
         (fn [s] (vec (take-last MAX-STACK
                                 (conj s {:buffer (active-buffer)
                                          :pos    (.. state -selection -main -head)}))))))

(defn- mac? []
  (boolean (re-find #"Mac" (str (.-platform js/navigator)))))

;;; Flash decoration

(def ^:private flash-effect (.define StateEffect))
(def ^:private flash-mark (.mark Decoration #js {:class "outline-flash"}))

(def flash-field
  (.define StateField
    #js {:create  (fn [_] (.-none Decoration))
         :update  (fn [decs tr]
                    (let [mapped (.map decs (.-changes tr))]
                      (if-let [eff (some #(when (.is % flash-effect) %)
                                         (array-seq (.-effects tr)))]
                        (let [{:keys [from to]} (.-value eff)]
                          (if (and from (< from to))
                            (.set Decoration #js [(.range flash-mark from to)])
                            (.-none Decoration)))
                        mapped)))
         :provide (fn [f] (.from EditorView.decorations f))}))

(defn- flash! [view from to]
  (let [len (.. view -state -doc -length)
        from (min from len)
        to   (min to len)]
    (.dispatch view #js {:effects #js [(.of flash-effect {:from from :to to})]})
    (js/setTimeout
      (fn []
        (try (.dispatch view #js {:effects #js [(.of flash-effect {})]})
             (catch :default _ nil)))
      600)))

(defn reveal!
  "Move the cursor to `pos`, scroll it to about 1/3 from the top, flash the
   range [from, to] and return focus to the editor."
  [view pos from to]
  (let [len (.. view -state -doc -length)
        pos (max 0 (min pos len))
        margin (/ (.. view -dom -clientHeight) 3)]
    (.dispatch view
      #js {:selection #js {:anchor pos}
           :effects   #js [(.scrollIntoView EditorView pos
                                            #js {:y "start" :yMargin margin})]})
    (flash! view from to)
    (.focus view)))

;;; Name resolution

(def ^:private NAME-NODES #{"Symbol" "BuiltinName" "Keyword"})

(defn name-at
  "{:kind :symbol|:keyword :text s} for the Symbol/BuiltinName/Keyword at pos, or nil."
  [state pos]
  (let [tree (syntaxTree state)
        hit  (some (fn [side]
                     (let [n (.resolveInner tree pos side)]
                       (when (and n (contains? NAME-NODES (.-name n))) n)))
                   [1 -1])]
    (when hit
      {:kind (if (= "Keyword" (.-name hit)) :keyword :symbol)
       :text (.sliceDoc state (.-from hit) (.-to hit))})))

(defn- entries-of [state]
  (:entries (outline/extract (syntaxTree state) (.toString (.-doc state)))))

(defn goto-definition!
  "Jump to the definition of the name at pos — in the active buffer first,
   else in another project buffer.  Unknown names do nothing.
   Returns true when a jump happened."
  [view pos]
  (let [state (.-state view)]
    (when-let [{:keys [kind text]} (name-at state pos)]
      (if-let [target (outline/definition-for (entries-of state) kind text)]
        (when-not (<= (:from target) pos (:to target))
          (push-jump! state)
          (reveal! view (or (:name-from target) (:from target))
                   (:from target) (:to target))
          true)
        (let [{:keys [find switch!]} @cross-buffer]
          (when-let [{:keys [buffer entry]} (when find (find kind text))]
            (push-jump! state)
            (switch! buffer)
            (reveal! view (or (:name-from entry) (:from entry))
                     (:from entry) (:to entry))
            true))))))

(defn go-back!
  "Pop the jump stack and move the cursor there, switching buffers if needed.
   An entry whose buffer no longer exists is dropped."
  [view]
  (when-let [{:keys [buffer pos]} (peek @jump-stack)]
    (swap! jump-stack pop)
    (let [{:keys [exists? switch!]} @cross-buffer]
      (when-not (and buffer exists? (not (exists? buffer)))
        (when (and buffer switch! (not= buffer (active-buffer)))
          (switch! buffer))
        (let [len (.. view -state -doc -length)
              pos (min pos len)]
          (.dispatch view
            #js {:selection #js {:anchor pos}
                 :effects   #js [(.scrollIntoView EditorView pos
                                                  #js {:y "center"})]})
          (.focus view)
          true)))))

;;; Extension

(def extension
  #js [flash-field
       (.high Prec
         (.of keymap
              #js [#js {:key "F12"
                        :run (fn [view]
                               (goto-definition! view (.. view -state -selection -main -head))
                               true)}
                   #js {:key "Shift-F12"
                        :run (fn [view] (go-back! view) true)}]))
       (.domEventHandlers EditorView
         #js {:mousedown
              (fn [^js e view]
                (when (if (mac?) (.-metaKey e) (.-ctrlKey e))
                  (let [pos (.posAtCoords view #js {:x (.-clientX e) :y (.-clientY e)})]
                    (when pos (goto-definition! view pos))
                    (.preventDefault e)
                    true)))})])
