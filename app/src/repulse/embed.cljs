(ns repulse.embed
  "Entry point for the <repulse-editor> custom element.
   Produces app/public/embed.js via the :embed shadow-cljs build target.

   Each <repulse-editor> mounts a CodeMirror 6 editor inside its own Shadow DOM.
   Multiple instances on the same page share the global audio scheduler and Lisp
   environment — track name collisions across instances are a known limitation."
  (:require [repulse.ui.editor :as editor]
            [repulse.env.builtins :as builtins]
            [repulse.eval-orchestrator :as eo]
            [repulse.audio :as audio]
            [repulse.snippets :as snippets]
            [repulse.embed-css :refer [EMBED_CSS]]))

;;; One-time module initialisation — idempotent via defonce guards in each module.

(defn- ensure-initialized! []
  (builtins/init! {:on-beat-fn      (fn [])
                   :set-playing!-fn (fn [_])
                   :set-output!-fn  (fn [_ _])
                   :make-stop-fn-fn (fn [] (fn [] (audio/stop!)))
                   :share!-fn       (fn [])})
  (eo/init! {:on-beat-fn      (fn [])
             :make-stop-fn-fn (fn [] (fn [] (audio/stop!)))
             :set-playing!-fn (fn [_])
             :set-output!-fn  (fn [_ _])})
  (reset! builtins/evaluate-ref eo/evaluate!)
  (builtins/ensure-env!))

;;; Shadow DOM mounting

(defn- mount-shadow! [^js host-el height]
  (let [shadow (.attachShadow host-el #js {:mode "open"})
        style  (.createElement js/document "style")
        wrap   (.createElement js/document "div")]
    (set! (.-textContent style) EMBED_CSS)
    (set! (.-className wrap) "embed-wrap")
    (set! (.. wrap -style -height) height)
    (.appendChild shadow style)
    (.appendChild shadow wrap)
    wrap))

;;; Attribute-driven connection logic

(defn- connect! [^js this]
  (ensure-initialized!)
  (let [code-attr    (.getAttribute this "code")
        snippet-attr (.getAttribute this "snippet")
        bpm-attr     (.getAttribute this "bpm")
        height       (or (.getAttribute this "height") "220px")
        autoplay?    (.hasAttribute this "autoplay")
        wrap         (mount-shadow! this height)]
    (set! (.. this -style -display) "block")
    (when bpm-attr
      (let [bpm (js/parseInt bpm-attr 10)]
        (when (and (js/isFinite bpm) (> bpm 0))
          (audio/set-bpm! bpm))))
    (if (and snippet-attr (seq snippet-attr))
      ;; ── Snippet mode: fetch library, seed editor, optionally autoplay ──────
      (do
        (snippets/load!)
        (let [seed-from-library!
              (fn []
                (if-let [s (snippets/by-id snippet-attr)]
                  (let [code (:code s)
                        view (editor/make-editor wrap code eo/evaluate!)]
                    (reset! editor/editor-view view)
                    (when autoplay?
                      (js/setTimeout #(eo/evaluate! code) 150)))
                  ;; Snippet not found — mount empty editor
                  (reset! editor/editor-view
                          (editor/make-editor wrap (or code-attr "") eo/evaluate!))))]
          (if @snippets/loaded?
            (seed-from-library!)
            (add-watch snippets/library-atom ::embed-load
              (fn [_ _ _ _]
                (remove-watch snippets/library-atom ::embed-load)
                (seed-from-library!))))))
      ;; ── Code-attribute mode ────────────────────────────────────────────────
      (let [code (or code-attr "")
            view (editor/make-editor wrap code eo/evaluate!)]
        (reset! editor/editor-view view)
        (when (and autoplay? (seq code))
          (js/setTimeout #(eo/evaluate! code) 150))))))

;;; Custom element class — use js* to emit a raw ES6 class expression
;; js/class is not a valid CLJS form; js* lets us splice connect! as a JS value.

(def ^:private RepulseEditor
  (js* "class extends HTMLElement { connectedCallback() { ~{}(this); } }" connect!))

;;; Module entry point — called by shadow-cljs :init-fn

(defn init! []
  (when (and (.-customElements js/window)
             (not (.get (.-customElements js/window) "repulse-editor")))
    (.define (.-customElements js/window) "repulse-editor" RepulseEditor)))
