(ns repulse.ui.editor
  "CodeMirror editor setup and active-event highlighting infrastructure.
   Responsibility: own the editor-view and cmd-view atoms, manage highlight decorations,
   and provide make-editor / make-cmd-editor factory functions.
   Exports: editor-view, cmd-view, active-ranges, highlights-field,
            highlight-range!, clear-highlights!, make-editor, make-cmd-editor."
  (:require [repulse.session :as session]
            ["@codemirror/view" :refer [EditorView Decoration keymap lineNumbers]]
            ["@codemirror/state" :refer [EditorState StateEffect StateField]]
            ["@codemirror/commands" :refer [defaultKeymap historyKeymap history selectAll]]
            ["@codemirror/language" :refer [bracketMatching syntaxHighlighting]]
            ["@codemirror/lint" :refer [setDiagnostics lintGutter]]
            ["@codemirror/theme-one-dark" :refer [oneDarkTheme]]
            ["../lisp-lang/index.js" :refer [lispLanguage]]
            ["../lisp-lang/highlight.js" :refer [repulseLispSyntaxTheme]]
            [clojure.string :as cstr]))

;;; Atoms

(defonce editor-view (atom nil))
(defonce cmd-view    (atom nil))

;;; Active code highlighting — CodeMirror 6 decoration infrastructure

;; A StateEffect that replaces the entire active-highlights DecorationSet.
(def set-highlights-effect (.define StateEffect))

;; A StateField that holds the current DecorationSet of active event highlights.
(def highlights-field
  (.define StateField
    #js {:create   (fn [_] (.-none Decoration))
         :update   (fn [decs tr]
                     (let [mapped (.map decs (.-changes tr))]
                       (if-let [eff (some #(when (.is % set-highlights-effect) %)
                                          (array-seq (.-effects tr)))]
                         (.-value eff)
                         mapped)))
         :provide  (fn [f] (.from EditorView.decorations f))}))

;; The CSS class applied to active tokens.
(def active-mark (.mark Decoration #js {:class "active-event"}))

;; Atom holding currently active source ranges: [{:from N :to N} ...]
(defonce active-ranges (atom []))

(defn- rebuild-decorations! [view]
  (let [ranges     (sort-by :from @active-ranges)
        range-objs (keep (fn [{:keys [from to]}]
                           (try (.range active-mark from to)
                                (catch :default _ nil)))
                         ranges)
        deco-set   (if (seq range-objs)
                     (.set Decoration (clj->js range-objs))
                     (.-none Decoration))]
    (.dispatch view #js {:effects #js [(.of set-highlights-effect deco-set)]})))

(defn highlight-range! [{:keys [from to]}]
  (when-let [view @editor-view]
    (let [doc-len (.. view -state -doc -length)
          from'   (min from doc-len)
          to'     (min to   doc-len)]
      (when (< from' to')
        (swap! active-ranges conj {:from from' :to to'})
        (rebuild-decorations! view)
        ;; Remove this range after 120 ms
        (js/setTimeout
          (fn []
            (swap! active-ranges
                   (fn [rs]
                     (filterv #(not (and (= (:from %) from') (= (:to %) to'))) rs)))
            (when-let [v @editor-view]
              (rebuild-decorations! v)))
          120)))))

(defn clear-highlights! []
  (reset! active-ranges [])
  (when-let [view @editor-view]
    (rebuild-decorations! view)))

;;; Editor listeners

;; On every document change, schedule a debounced full-session save.
(def ^:private save-listener
  (.of EditorView.updateListener
       (fn [^js update]
         (when (.-docChanged update)
           (session/schedule-save!)))))

;; Clear diagnostics immediately when the user starts editing, so stale
;; squiggles don't linger after the source has changed.
(def ^:private clear-diag-listener
  (.of EditorView.updateListener
       (fn [^js update]
         (when (.-docChanged update)
           (.dispatch (.-view update)
                      (setDiagnostics (.-state update) #js []))))))

;;; Editor creation

(defn make-cmd-editor
  "Single-line CodeMirror editor for the command bar. Enter evaluates + clears.
   container   — DOM element to mount into.
   evaluate-fn — fn called with the code string to evaluate."
  [container evaluate-fn]
  (let [clear-view! (fn [view]
                      (.dispatch view
                                 #js {:changes #js {:from 0
                                                    :to   (.. view -state -doc -length)
                                                    :insert ""}})
                      true)
        eval-cmd    (fn [view]
                      (let [raw (cstr/trim (.. view -state -doc (toString)))]
                        (when (seq raw)
                          (let [code (if (cstr/starts-with? raw "(") raw (str "(" raw ")"))]
                            (evaluate-fn code)
                            (clear-view! view))))
                      true)
        clear+return! (fn [view]
                        (clear-view! view)
                        (when-let [ev @editor-view] (.focus ev))
                        true)
        extensions  #js [oneDarkTheme
                         lispLanguage
                         (syntaxHighlighting repulseLispSyntaxTheme)
                         (.of keymap #js [#js {:key "Mod-a"  :run selectAll}
                                          #js {:key "Enter"  :run eval-cmd}
                                          #js {:key "Escape" :run clear+return!}])]
        state (.. EditorState (create #js {:doc "" :extensions extensions}))
        view  (EditorView. #js {:state state :parent container})]
    view))

(defn make-editor
  "Create the main multi-line CodeMirror editor.
   container     — DOM element to mount into.
   initial-value — initial text content.
   on-eval       — fn called with code string on Alt+Enter / F9 / Ctrl+."
  [container initial-value on-eval]
  (let [eval-cmd (fn [view]
                   (on-eval (.. view -state -doc (toString)))
                   true)
        eval-binding    #js {:key "Alt-Enter" :run eval-cmd}
        upd-fn          (fn [_] (on-eval "(upd)") true)
        upd-binding     #js {:key "Ctrl-." :run upd-fn}
        upd-f9-binding  #js {:key "F9"     :run upd-fn}
        escape-binding  #js {:key "Escape"
                             :run (fn [_]
                                    (when-let [cv @cmd-view] (.focus cv))
                                    true)}
        extensions #js [(history)
                        (lineNumbers)
                        oneDarkTheme
                        lispLanguage
                        (syntaxHighlighting repulseLispSyntaxTheme)
                        (bracketMatching)
                        highlights-field
                        (lintGutter)
                        save-listener
                        clear-diag-listener
                        (.-lineWrapping EditorView)
                        (.of keymap (.concat
                                     #js [escape-binding eval-binding upd-binding upd-f9-binding]
                                     (clj->js defaultKeymap)
                                     (clj->js historyKeymap)))]
        state (.. EditorState
                  (create #js {:doc initial-value
                               :extensions extensions}))
        view (EditorView. #js {:state state :parent container})]
    view))
