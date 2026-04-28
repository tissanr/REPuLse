(ns repulse.ui.snippet-submit-modal
  "Modal for submitting the current editor code as a community snippet.
   Exports: init!, open!, close!"
  (:require [repulse.api :as api]
            [repulse.snippets :as snippets]
            [repulse.ui.editor :as editor]
            [clojure.string :as cstr]))

;;; State

(defonce ^:private visible?      (atom false))
(defonce ^:private submitting?   (atom false))
(defonce ^:private selected-tags (atom #{}))   ; set of lowercase strings

;;; DOM helpers

(defn- el [id] (.getElementById js/document id))

(defn- current-code []
  (when-let [view @editor/editor-view]
    (.. view -state -doc (toString))))

(defn- current-bpm []
  (when-let [bpm-el (el "bpm-value")]
    (let [v (js/parseInt (.-textContent bpm-el) 10)]
      (when-not (js/isNaN v) v))))

(defn- normalize-tag [s]
  (-> s cstr/trim cstr/lower-case))

;;; Toast

(defn- show-toast! [msg ok?]
  (let [toast (js/document.createElement "div")]
    (set! (.-className toast) (str "snippet-toast " (if ok? "snippet-toast-ok" "snippet-toast-err")))
    (set! (.-textContent toast) msg)
    (.appendChild js/document.body toast)
    (js/setTimeout (fn [] (when (.contains js/document.body toast)
                            (.removeChild js/document.body toast)))
                   3000)))

;;; Tag chip rendering

(defn- render-tag-section! []
  (when-let [container (el "ssm-tag-section")]
    (let [all-tags   (snippets/all-tags)
          selected   @selected-tags
          ;; custom tags = selected tags not in the global list
          custom     (sort (filter #(not (some #{%} all-tags)) selected))]
      (set! (.-innerHTML container)
            (str
              ;; Suggestion chips from existing tags
              "<div class=\"ssm-tag-suggestions\" id=\"ssm-tag-suggestions\">"
              (apply str
                (map (fn [t]
                       (str "<button type=\"button\" class=\"ssm-tag-chip"
                            (when (contains? selected t) " ssm-tag-chip--on")
                            "\" data-tag=\"" t "\">"
                            t "</button>"))
                     all-tags))
              "</div>"
              ;; Custom (user-typed) tags shown as removable chips
              (when (seq custom)
                (str "<div class=\"ssm-tag-custom\" id=\"ssm-tag-custom\">"
                     (apply str
                       (map (fn [t]
                              (str "<span class=\"ssm-tag-chip ssm-tag-chip--on ssm-tag-chip--custom\">"
                                   t
                                   "<button type=\"button\" class=\"ssm-tag-remove\" data-tag=\"" t "\">&times;</button>"
                                   "</span>"))
                            custom))
                     "</div>"))
              ;; Input for new tags
              "<div class=\"ssm-tag-new-row\">"
              "<input id=\"ssm-tag-input\" class=\"ssm-tag-input\" type=\"text\""
              " placeholder=\"add tag…\" maxlength=\"40\" />"
              "</div>"))
      ;; Wire chip clicks
      (when-let [suggestions (el "ssm-tag-suggestions")]
        (.addEventListener suggestions "click"
          (fn [^js e]
            (let [btn (.closest (.-target e) "[data-tag]")]
              (when btn
                (let [t (.. btn -dataset -tag)]
                  (swap! selected-tags
                         (fn [s] (if (contains? s t) (disj s t) (conj s t))))
                  (render-tag-section!)))))))
      ;; Wire custom remove buttons
      (doseq [btn (array-seq (.querySelectorAll container ".ssm-tag-remove"))]
        (.addEventListener btn "click"
          (fn [^js e]
            (.stopPropagation e)
            (let [t (.. btn -dataset -tag)]
              (swap! selected-tags disj t)
              (render-tag-section!)))))
      ;; Wire new-tag input
      (when-let [inp (el "ssm-tag-input")]
        (.addEventListener inp "keydown"
          (fn [^js e]
            (when (or (= "Enter" (.-key e)) (= "," (.-key e)))
              (.preventDefault e)
              (let [raw (normalize-tag (.-value inp))]
                (when (seq raw)
                  (swap! selected-tags conj raw)
                  (set! (.-value inp) "")
                  (render-tag-section!))))))
        ;; Also add on blur so user can tab away
        (.addEventListener inp "blur"
          (fn [_]
            (let [raw (normalize-tag (.-value inp))]
              (when (seq raw)
                (swap! selected-tags conj raw)
                (set! (.-value inp) "")
                (render-tag-section!)))))))))

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
  (reset! selected-tags #{})
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
    ;; Render tag chips (loaded tags available after snippets/load!)
    (render-tag-section!)
    ;; Focus title
    (js/setTimeout #(when-let [t (el "ssm-title")] (.focus t)) 50)))

;;; Submit

(defn- do-submit! []
  (when (not @submitting?)
    (let [title (cstr/trim (or (some-> (el "ssm-title") .-value) ""))
          desc  (cstr/trim (or (some-> (el "ssm-desc")  .-value) ""))
          tags  (vec @selected-tags)
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
               "    <label class=\"ssm-label\">Tags</label>"
               "    <div id=\"ssm-tag-section\" class=\"ssm-tag-section\"></div>"
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
