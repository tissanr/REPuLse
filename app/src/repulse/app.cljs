(ns repulse.app
  (:require [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.audio :as audio]
            ["@codemirror/view" :refer [EditorView keymap lineNumbers]]
            ["@codemirror/state" :refer [EditorState]]
            ["@codemirror/commands" :refer [defaultKeymap historyKeymap history]]
            ["@codemirror/theme-one-dark" :refer [oneDark]]))

;;; DOM helpers

(defn el [id] (.getElementById js/document id))

(defn set-output! [msg status]
  (when-let [e (el "output")]
    (set! (.-textContent e) msg)
    (set! (.-className e) (str "output " (name status)))))

(defn set-playing! [playing?]
  (when-let [dot (el "playing-dot")]
    (if playing?
      (.add (.-classList dot) "active")
      (.remove (.-classList dot) "active"))))

(defn on-beat []
  (when-let [dot (el "playing-dot")]
    (.add (.-classList dot) "flash")
    (js/setTimeout #(.remove (.-classList dot) "flash") 80)))

;;; Environment — created once, reused across evaluations

(defonce env-atom
  (atom nil))

(defn make-stop-fn []
  (fn []
    (audio/stop!)
    (set-playing! false)
    (set-output! "stopped" :idle)))

(defn ensure-env! []
  (when (nil? @env-atom)
    (reset! env-atom (leval/make-env (make-stop-fn)))))

;;; Evaluation

(defn evaluate! [code]
  (ensure-env!)
  ;; Keep stop fn up to date (in case env was reset)
  (let [env (assoc @env-atom "stop" (make-stop-fn))
        result (lisp/eval-string code env)]
    (if-let [err (:error result)]
      (set-output! (str "Error: " err) :error)
      (let [val (:result result)]
        (cond
          ;; Pattern — start playing
          (and (map? val) (fn? (:query val)))
          (do
            (audio/stop!)
            (audio/start! val on-beat)
            (set-playing! true)
            (set-output! "playing pattern — Ctrl+Enter to re-evaluate, (stop) to stop" :success))

          ;; stop fn was called directly — handled inside stop fn
          (nil? val)
          nil

          :else
          (set-output! (str "=> " (pr-str val)) :success))))))

;;; CodeMirror editor

(defn make-editor [container initial-value on-eval]
  (let [eval-cmd (fn [view]
                   (on-eval (.. view -state -doc (toString)))
                   true)
        eval-binding #js {:key "Mod-Enter" :run eval-cmd}
        extensions #js [(history)
                        (lineNumbers)
                        oneDark
                        (.-lineWrapping EditorView)
                        (.of keymap (.concat
                                     (clj->js defaultKeymap)
                                     (clj->js historyKeymap)
                                     #js [eval-binding]))]
        state (.. EditorState
                  (create #js {:doc initial-value
                               :extensions extensions}))
        view (EditorView. #js {:state state :parent container})]
    view))

;;; App bootstrap

(defn build-dom! []
  (let [app (el "app")]
    (set! (.-innerHTML app)
          (str "<header>"
               "  <h1>REPuLse</h1>"
               "  <div id=\"playing-dot\" class=\"playing-dot\"></div>"
               "</header>"
               "<div id=\"editor-container\" class=\"editor-container\"></div>"
               "<footer>"
               "  <span id=\"output\" class=\"output\">ready &mdash; Ctrl+Enter to evaluate</span>"
               "  <span class=\"hint\">Ctrl+Enter to eval &nbsp;&nbsp; (stop) to stop</span>"
               "</footer>"))))

(defonce editor-view (atom nil))

(defn init []
  (build-dom!)
  (ensure-env!)
  (let [container (el "editor-container")
        view (make-editor container "(seq :bd :sd :bd :sd)" evaluate!)]
    (reset! editor-view view)
    ;; Focus editor
    (.focus view)))

(defn reload []
  ;; Hot-reload hook: re-attach evaluate! without rebuilding the DOM
  ;; The editor persists; we just need to make sure the env is fresh.
  )
