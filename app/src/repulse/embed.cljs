(ns repulse.embed
  "Embeddable <repulse-editor> custom element.
   Provides a self-contained REPuLse editor that can be dropped into any HTML page.
   Uses Shadow DOM for style isolation."
  (:require [repulse.ui.editor :as editor]
            [repulse.env.builtins :as builtins]
            [repulse.eval-orchestrator :as eo]
            [repulse.audio :as audio]
            [repulse.snippets :as snippets]
            [repulse.embed-css :refer [EMBED_CSS]]))

;;; Instance counter for unique track namespacing
(defonce instance-counter (atom 0))

;;; Per-instance editor views (instance-id -> EditorView)
(defonce instance-views (atom {}))

(defn- mount-instance!
  "Create Shadow DOM root with styles and editor container.
   Returns the container element."
  [host-el]
  (let [shadow (.attachShadow host-el #js {:mode "open"})
        style  (doto (.createElement js/document "style")
                 (-> .-textContent (set! EMBED_CSS)))
        wrap   (doto (.createElement js/document "div")
                 (-> .-classList (.add "editor-wrap")))]
    (.appendChild shadow style)
    (.appendChild shadow wrap)
    wrap))

(defn- make-eval-fn
  "Create an evaluation function for an embed instance.
   Captures the instance ID for potential future namespacing."
  [instance-id]
  (fn [code]
    (eo/evaluate! code)))

(defn- connect-callback
  "Called when a <repulse-editor> element is connected to the DOM."
  [^js this]
  (let [code-attr    (.getAttribute this "code")
        snippet-attr (.getAttribute this "snippet")
        bpm-attr     (js/parseInt (or (.getAttribute this "bpm") "120"))
        height       (or (.getAttribute this "height") "220px")
        autoplay?    (.hasAttribute this "autoplay")
        instance-id  (swap! instance-counter inc)]
    
    ;; Set host display style
    (set! (.. this -style -display) "block")
    
    ;; Mount Shadow DOM
    (let [wrap (mount-instance! this)]
      (set! (.-style wrap) (str "height:" height))
      
      ;; Initialize builtins with minimal callbacks
      ;; (embed doesn't need UI state updates)
      (builtins/init! {:on-beat-fn      (fn [])
                       :set-playing!-fn (fn [_])
                       :set-output!-fn  (fn [_ _])
                       :make-stop-fn-fn (fn [] (fn [] (audio/stop!)))
                       :share!-fn       (fn [])})
      
      ;; Ensure Lisp environment is ready
      (builtins/ensure-env!)
      
      ;; Set initial BPM
      (audio/set-bpm! bpm-attr)
      
      ;; Create evaluation function for this instance
      (let [eval-fn (make-eval-fn instance-id)]
        
        (if snippet-attr
          ;; --- Load from snippet library ---
          (do
            (snippets/load!)
            (add-watch snippets/library-atom ::embed-load
              (fn [_ _ _ _]
                (remove-watch snippets/library-atom ::embed-load)
                (when-let [s (snippets/by-id snippet-attr)]
                  (let [code (:code s)
                        ;; Apply snippet BPM if specified
                        _     (when-let [snippet-bpm (:bpm s)]
                                (audio/set-bpm! snippet-bpm))
                        view  (editor/make-editor wrap code eval-fn)]
                    (swap! instance-views assoc instance-id view)
                    (when autoplay?
                      (js/setTimeout #(eval-fn code) 150)))))))
          
          ;; --- Use code attribute directly ---
          (let [code (or code-attr "")
                view (editor/make-editor wrap code eval-fn)]
            (swap! instance-views assoc instance-id view)
            (when (and autoplay? (seq code))
              (js/setTimeout #(eval-fn code) 150))))))))

(defn init!
  "Register the <repulse-editor> custom element.
   Called automatically when embed.js loads."
  []
  (when (.-customElements js/window)
    ;; Use js* to define a real ES6 class — the only reliable way to extend
    ;; HTMLElement in browsers. ClojureScript's fn-based approach cannot satisfy
    ;; the browser's requirement that the constructor returns a proper HTMLElement.
    (let [cb connect-callback
          klass (js* "class extends HTMLElement {
                        constructor() { super(); }
                        connectedCallback() { ~{}(this); }
                      }" cb)]
      (.define (.-customElements js/window) "repulse-editor" klass))))
