(ns repulse.ui.dialog
  "In-page replacements for js/confirm, js/prompt and js/alert.
   Native dialogs block the main thread, and with it the lookahead audio
   scheduler: playback went silent while one was open. These render a small
   overlay instead and return a js/Promise, so audio keeps running.
   Enter confirms, Escape cancels. Only one dialog is shown at a time; opening
   another one cancels the previous one.
   Exports: confirm!, prompt!, alert!.")

;; {:overlay el :resolve f :cancel-value v} of the open dialog, else nil.
(defonce ^:private current (atom nil))

(defn- close! [value]
  (when-let [{:keys [overlay resolve]} @current]
    (reset! current nil)
    (.remove overlay)
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
                              "Enter"  (do (.preventDefault e) (ok!))
                              "Escape" (do (.preventDefault e) (cancel!))
                              nil)
                            ;; keep keys away from the editor's global handlers
                            (.stopPropagation e)))
       (reset! current {:overlay overlay :resolve resolve :cancel-value cancel-value})
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
