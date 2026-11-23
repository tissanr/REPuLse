(ns repulse.ui.dialog
  "In-page replacements for js/confirm, js/prompt and js/alert.
   Native dialogs block the main thread, and with it the lookahead audio
   scheduler: playback went silent while one was open. These render a small
   overlay instead and return a js/Promise, so audio keeps running.
   Enter confirms (or activates the focused button), Escape cancels, Tab stays
   inside the dialog (QA3). Only one dialog is shown at a time; opening
   another one cancels the previous one. Focus returns to the element that had
   it when the dialog opened.
   Exports: confirm!, prompt!, alert!."
  (:require [repulse.ui.a11y :as a11y]
            [repulse.ui.modal-stack :as modal-stack]))

;; {:overlay el :resolve f :cancel-value v :opener el} of the open dialog, else nil.
(defonce ^:private current (atom nil))
(defonce ^:private id-counter (atom 0))

(defn- close! [value]
  (when-let [{:keys [overlay resolve ^js opener]} @current]
    (reset! current nil)
    (.remove overlay)
    (modal-stack/remove! :dialog)
    (when (and opener (.-isConnected opener) (fn? (.-focus opener)))
      (.focus opener))
    (resolve value)))

(defn- el [tag cls]
  (let [e (.createElement js/document tag)]
    (when cls (set! (.-className e) cls))
    e))

(defn- button [cls label]
  (let [b (el "button" cls)]
    (set! (.-type b) "button")
    (set! (.-textContent b) label)
    b))

(defn- open!
  "kind — :confirm | :prompt | :alert. Resolves with true/false, the entered
   string/nil, or nil."
  [kind message {:keys [default ok-label cancel-label danger?]}]
  (when-let [{:keys [cancel-value]} @current]
    (close! cancel-value))
  (js/Promise.
   (fn [resolve _]
     (let [cancel-value (when (= kind :confirm) false)
           opener  (.-activeElement js/document)
           msg-id  (str "rp-dialog-msg-" (swap! id-counter inc))
           overlay (el "div" "rp-dialog-overlay")
           box     (el "div" "rp-dialog")
           msg     (el "p" "rp-dialog-message")
           input   (when (= kind :prompt)
                     (doto (el "input" "rp-dialog-input")
                       (-> .-type (set! "text"))
                       (-> .-spellcheck (set! false))
                       (-> .-value (set! (or default "")))))
           actions (el "div" "rp-dialog-actions")
           ok      (button (str "rp-dialog-ok" (when danger? " danger"))
                           (or ok-label "OK"))
           cancel  (when-not (= kind :alert)
                     (button "rp-dialog-cancel" (or cancel-label "Cancel")))
           ok!     #(close! (case kind
                              :confirm true
                              :prompt  (.-value input)
                              nil))
           cancel! #(close! cancel-value)]
       (.setAttribute box "role" (if (= kind :alert) "alertdialog" "dialog"))
       (.setAttribute box "aria-modal" "true")
       (set! (.-id msg) msg-id)
       (.setAttribute box "aria-labelledby" msg-id)
       (set! (.-textContent msg) message)
       (.appendChild box msg)
       (when input (.appendChild box input))
       (when cancel (.appendChild actions cancel))
       (.appendChild actions ok)
       (.appendChild box actions)
       (.appendChild overlay box)
       (.addEventListener ok "click" ok!)
       (when cancel (.addEventListener cancel "click" cancel!))
       ;; click on the backdrop cancels
       (.addEventListener overlay "mousedown"
                          (fn [^js e] (when (= (.-target e) overlay) (cancel!))))
       (.addEventListener overlay "keydown"
                          (fn [^js e]
                            (case (.-key e)
                              ;; Enter on a focused button activates that
                              ;; button (Cancel cancels); elsewhere it confirms.
                              "Enter"  (let [t (.-target e)]
                                         (.preventDefault e)
                                         ;; Alt/Ctrl/Cmd+Enter (evaluate) does
                                         ;; nothing while a dialog is open
                                         (when-not (or (.-altKey e) (.-ctrlKey e) (.-metaKey e))
                                           (if (= "BUTTON" (.-tagName t))
                                             (.click t)
                                             (ok!))))
                              "Escape" (do (.preventDefault e) (cancel!))
                              "Tab"    (a11y/trap-tab! e box)
                              nil)
                            ;; keep keys away from the editor's global handlers
                            (.stopPropagation e)))
       (reset! current {:overlay overlay :resolve resolve :cancel-value cancel-value
                        :opener opener})
       (modal-stack/push! :dialog)
       (.appendChild (.-body js/document) overlay)
       (if input
         (do (.focus input) (.select input))
         (.focus ok))))))

(defn confirm!
  "→ Promise<boolean>. opts — {:ok-label s :cancel-label s :danger? bool}"
  ([message] (confirm! message nil))
  ([message opts] (open! :confirm message opts)))

(defn prompt!
  "→ Promise<string | nil> (nil when cancelled)."
  ([message] (prompt! message nil))
  ([message default] (open! :prompt message {:default default})))

(defn alert!
  "→ Promise<nil>."
  [message]
  (open! :alert message nil))
