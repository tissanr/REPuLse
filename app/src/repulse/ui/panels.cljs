(ns repulse.ui.panels
  "Closable bottom panels: the track visuals (#track-panel) and the
   frequency analyser of the visual plugins (#plugin-panel). Each gets a ×;
   a closed panel leaves a restore button in the status bar. Which panels are
   closed is a per-browser preference kept in localStorage.
   Exports: closed?, close!, open!, init!, close-button-html.")

(def storage-key "repulse.closed-panels")

;; :id → {:el dom-id :label restore text :title close-button title}
(def panels
  {:tracks   {:el "track-panel"  :label "track viz" :title "hide track visuals"}
   :spectrum {:el "plugin-panel" :label "spectrum"  :title "hide frequency analyser"}})

(defn- load-closed []
  (try
    (let [raw (.getItem js/localStorage storage-key)]
      (if raw
        (set (keep #(let [k (keyword %)] (when (contains? panels k) k))
                   (js->clj (.parse js/JSON raw))))
        #{}))
    (catch :default _ #{})))

(defonce ^:private closed (atom nil))

(defn closed? [id]
  (contains? (or @closed (reset! closed (load-closed))) id))

(defn- save! []
  (try (.setItem js/localStorage storage-key
                 (.stringify js/JSON (clj->js (map name (sort @closed)))))
       (catch :default _)))

(defn- el [id] (.getElementById js/document id))

(defn apply!
  "Sync the DOM with the closed set: the panel's .panel-closed class and the
   visibility of its restore button."
  []
  (doseq [[id {dom-id :el}] panels]
    (let [c? (closed? id)]
      (when-let [p (el dom-id)]
        (.toggle (.-classList p) "panel-closed" c?))
      (when-let [b (.querySelector js/document (str ".panel-restore[data-panel=\"" (name id) "\"]"))]
        (set! (.-hidden b) (not c?))))))

(defn close! [id]
  (closed? id)
  (swap! closed conj id)
  (save!)
  (apply!))

(defn open! [id]
  (closed? id)
  (swap! closed disj id)
  (save!)
  (apply!))

(defn close-button-html
  "The × of panel id, as HTML (the track panel rebuilds its toolbar)."
  [id]
  (str "<button type=\"button\" class=\"panel-close\" data-panel=\"" (name id)
       "\" title=\"" (get-in panels [id :title]) "\" aria-label=\""
       (get-in panels [id :title]) "\">×</button>"))

(defn restore-buttons-html
  "Status-bar buttons that bring closed panels back."
  []
  (apply str
         (for [[id {:keys [label]}] panels]
           (str "<button type=\"button\" class=\"panel-restore\" data-panel=\"" (name id)
                "\" title=\"show " label "\" hidden><span class=\"panel-restore-icon\">+</span>"
                label "</button>"))))

(defn init!
  "Wire the panel × buttons (event delegation, so rebuilt toolbars keep
   working) and the restore buttons; apply the saved state."
  []
  (.addEventListener js/document "click"
    (fn [^js e]
      (when-let [^js b (some-> (.-target e) (.closest ".panel-close, .panel-restore"))]
        (let [id (keyword (.. b -dataset -panel))]
          (when (contains? panels id)
            (if (.contains (.-classList b) "panel-close") (close! id) (open! id)))))))
  (when-let [p (el "plugin-panel")]
    (when-not (.querySelector p ".panel-close")
      (.insertAdjacentHTML p "afterbegin" (close-button-html :spectrum))))
  (apply!))
