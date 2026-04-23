(ns repulse.ui.snippet-submit-modal
  "Modal for submitting the current editor code as a community snippet.
   Exports: init!, open!, close!"
  (:require [repulse.api :as api]
            [repulse.snippets :as snippets]
            [repulse.ui.editor :as editor]
            [clojure.string :as cstr]))

;;; State

(defonce ^:private visible? (atom false))
(defonce ^:private submitting? (atom false))

;;; DOM helpers

(defn- el [id] (.getElementById js/document id))

(defn- current-code []
  (when-let [view @editor/editor-view]
    (.. view -state -doc (toString))))

(defn- current-bpm []
  (when-let [bpm-el (el "bpm-value")]
    (let [v (js/parseInt (.-textContent bpm-el) 10)]
      (when-not (js/isNaN v) v))))

;;; Toast

(defn- show-toast! [msg ok?]
  (let [toast (js/document.createElement "div")]
    (set! (.-className toast) (str "snippet-toast " (if ok? "snippet-toast-ok" "snippet-toast-err")))
    (set! (.-textContent toast) msg)
    (.appendChild js/document.body toast)
    (js/setTimeout (fn [] (when (.contains js/document.body toast)
                            (.removeChild js/document.body toast)))
                   3000)))

;;; Close

(defn close! []
  (reset! visible? false)
  (reset! submitting? false)
  (when-let [overlay (el "snippet-submit-overlay")]
    (.add (.-classList overlay) "hidden")))

;;; Open

(defn open! []
  (reset! visible? true)
  (reset! submitting? false)
  (when-let [overlay (el "snippet-submit-overlay")]
    (.remove (.-classList overlay) "hidden")
    ;; Pre-fill code preview and BPM
    (when-let [code-pre (el "ssm-code-preview")]
      (let [code (or (current-code) "")]
        (set! (.-textContent code-pre) (if (> (count code) 500)
                                         (str (subs code 0 500) "\n…")
                                         code))))
    (when-let [bpm-input (el "ssm-bpm")]
      (when-let [bpm (current-bpm)]
        (set! (.-value bpm-input) bpm)))
    ;; Clear previous error / inputs
    (when-let [err-el (el "ssm-error")] (set! (.-textContent err-el) ""))
    (when-let [t (el "ssm-title")] (set! (.-value t) ""))
    (when-let [d (el "ssm-desc")]  (set! (.-value d) ""))
    (when-let [g (el "ssm-tags")]  (set! (.-value g) ""))
    ;; Focus title
    (js/setTimeout #(when-let [t (el "ssm-title")] (.focus t)) 50)))

;;; Submit

(defn- do-submit! []
  (when (not @submitting?)
    (let [title (cstr/trim (or (some-> (el "ssm-title") .-value) ""))
          desc  (cstr/trim (or (some-> (el "ssm-desc")  .-value) ""))
          tags  (->> (cstr/split (or (some-> (el "ssm-tags") .-value) "") #"[,\s]+")
                     (map cstr/trim)
                     (filter seq))
          bpm   (let [v (some-> (el "ssm-bpm") .-value js/parseInt)]
                  (when (and v (not (js/isNaN v)) (pos? v)) v))
          code  (current-code)]
      (cond
        (empty? title)
        (when-let [err (el "ssm-error")]
          (set! (.-textContent err) "Title is required."))

        (empty? code)
        (when-let [err (el "ssm-error")]
          (set! (.-textContent err) "Editor is empty."))

        :else
        (do
          (reset! submitting? true)
          (when-let [btn (el "ssm-submit-btn")]
            (set! (.-disabled btn) true)
            (set! (.-textContent btn) "submitting…"))
          (when-let [err (el "ssm-error")] (set! (.-textContent err) ""))
          (-> (api/create-snippet! {:title       title
                                     :description (when (seq desc) desc)
                                     :code        code
                                     :tags        tags
                                     :bpm         bpm})
              (.then (fn [result]
                       (reset! submitting? false)
                       (when-let [btn (el "ssm-submit-btn")]
                         (set! (.-disabled btn) false)
                         (set! (.-textContent btn) "submit"))
                       (if (:error result)
                         (when-let [err (el "ssm-error")]
                           (set! (.-textContent err) (:error result)))
                         (do (close!)
                             (show-toast! (str "\"" title "\" shared!") true)
                             (snippets/reload!)))))))))))

;;; Initialization

(defn init!
  "Create the modal overlay in the DOM and wire events.
   Must be called once after page load."
  []
  (let [overlay (js/document.createElement "div")]
    (set! (.-id overlay) "snippet-submit-overlay")
    (set! (.-className overlay) "snippet-submit-overlay hidden")
    (set! (.-innerHTML overlay)
          (str "<div class=\"snippet-submit-modal\" id=\"snippet-submit-modal\">"
               "  <div class=\"ssm-header\">"
               "    <span class=\"ssm-title-label\">Share as snippet</span>"
               "    <button class=\"ssm-close-btn\" id=\"ssm-close-btn\">&times;</button>"
               "  </div>"
               "  <div class=\"ssm-body\">"
               "    <label class=\"ssm-label\">Title <span class=\"ssm-required\">*</span></label>"
               "    <input id=\"ssm-title\" class=\"ssm-input\" type=\"text\" placeholder=\"e.g. Four-on-the-floor groove\" maxlength=\"100\" />"
               "    <label class=\"ssm-label\">Description</label>"
               "    <input id=\"ssm-desc\" class=\"ssm-input\" type=\"text\" placeholder=\"optional\" maxlength=\"280\" />"
               "    <label class=\"ssm-label\">Tags <span class=\"ssm-hint\">(comma separated)</span></label>"
               "    <input id=\"ssm-tags\" class=\"ssm-input\" type=\"text\" placeholder=\"e.g. drums, bass, ambient\" />"
               "    <label class=\"ssm-label\">BPM</label>"
               "    <input id=\"ssm-bpm\" class=\"ssm-input ssm-bpm-input\" type=\"number\" min=\"20\" max=\"300\" placeholder=\"120\" />"
               "    <label class=\"ssm-label\">Code preview</label>"
               "    <pre id=\"ssm-code-preview\" class=\"ssm-code-preview\"></pre>"
               "    <div id=\"ssm-error\" class=\"ssm-error\"></div>"
               "  </div>"
               "  <div class=\"ssm-footer\">"
               "    <button class=\"ssm-btn ssm-cancel-btn\" id=\"ssm-cancel-btn\">cancel</button>"
               "    <button class=\"ssm-btn ssm-submit-btn\" id=\"ssm-submit-btn\">submit</button>"
               "  </div>"
               "</div>"))
    (.appendChild js/document.body overlay)

    ;; Wire events
    (.addEventListener (el "ssm-close-btn")  "click" close!)
    (.addEventListener (el "ssm-cancel-btn") "click" close!)
    (.addEventListener (el "ssm-submit-btn") "click" do-submit!)

    ;; Click outside modal → close
    (.addEventListener overlay "click"
      (fn [^js e]
        (when (= (.-target e) overlay)
          (close!))))

    ;; Escape → close
    (.addEventListener js/document "keydown"
      (fn [^js e]
        (when (and @visible? (= "Escape" (.-key e)))
          (close!))))))
