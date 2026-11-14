(ns repulse.ui.history-panel
  "Eval history & snapshots panel (Phase SK2).
   Lists snapshots and evaluations, previews a read-only line diff against the
   current project, restores, copies share links, renames and deletes.
   Must not require repulse.eval-orchestrator or repulse.env.builtins
   (builtins require this ns); the mute setter is injected via init!.
   Exports: init!, open!, close!, toggle!, render!, restore!, apply-state!, visible?"
  (:require [repulse.history :as history]
            [repulse.history-store :as hs]
            [repulse.session :as session]
            [repulse.audio :as audio]
            [repulse.samples :as samples]
            [repulse.ui.buffers :as buffers]
            [repulse.ui.dialog :as dialog]
            [repulse.ui.html :refer [escape-html]]))

(defonce visible? (atom false))
(defonce ^:private cbs (atom {}))
(defonce ^:private selected (atom nil))       ; nil | [:entry id] | [:snapshot name]
(defonce ^:private shown (atom 100))
(defonce ^:private summary-cache (atom {}))   ; {[old-hash new-hash] "+a -b"}

(defn- el [id] (.getElementById js/document id))
(defn- out! [msg status] (when-let [f (:set-output! @cbs)] (f msg status)))
(defn- err! [e] (out! (str "Error: " (or (ex-message e) (str e))) :error))

;; ── applying a state (K10) ───────────────────────────────────────────────────

(defn apply-state!
  "Apply a restored project state: buffers, BPM, bank, sample sources, mutes.
   Does not evaluate, stop playback or touch MIDI mappings."
  [state]
  (buffers/replace-project! {:main    (:main state)
                             :active  (:main state)
                             :buffers (:buffers state)})
  (audio/set-bpm! (audio/coerce-bpm (:bpm state)))
  (samples/set-bank-prefix! (:bank state))
  (let [loaded (set (map :id @samples/loaded-sources))]
    (doseq [{:keys [type id]} (:sources state)]
      (when (and id (= "github" (str type)) (not (contains? loaded id)))
        (samples/load-external! (str "github:" id)))))
  (when-let [f (:set-pending-mutes! @cbs)]
    (f (set (map keyword (:muted state)))))
  (session/schedule-save!))

(defn restore!
  "Restore a snapshot name or history id now (panel path)."
  [target]
  (try
    (let [{:keys [state message]} (hs/begin-restore! target (session/build-session-snapshot))]
      (apply-state! state)
      (reset! selected nil)
      (out! message :success))
    (catch :default e (err! e))))

;; ── rendering ────────────────────────────────────────────────────────────────

(defn- fmt-time [t] (.toLocaleTimeString (js/Date. t)))

(defn- entry-state [e]
  (history/blob->state (get-in @hs/store [:blobs (:hash e)])))

(defn- row-summary
  "Summary of an entry against the next older entry (oldest vs empty project)."
  [older e]
  (let [k [(:hash older) (:hash e)]]
    (or (get @summary-cache k)
        (let [txt (history/summary-text
                    (history/diff-stats
                      (history/project-diff (some-> older entry-state) (entry-state e))))]
          (swap! summary-cache assoc k txt)
          txt))))

(defn- snap-by-name [nm]
  (first (filter #(= nm (:name %)) (:snapshots @hs/store))))

(defn- sel-state [sel]
  (when sel
    (case (first sel)
      :entry (some-> (history/entry-by-id @hs/store (second sel)) entry-state)
      :snapshot (some->> (snap-by-name (second sel)) :hash
                         (get (:blobs @hs/store)) history/blob->state))))

(defn- line-html [[op l]]
  (case op
    :same (str "<div class=\"hist-line hist-same\">  " (escape-html l) "</div>")
    :del  (str "<div class=\"hist-line hist-del\">- " (escape-html l) "</div>")
    :add  (str "<div class=\"hist-line hist-add\">+ " (escape-html l) "</div>")))

(defn- buf-html [{:keys [buffer status lines]}]
  (if (= status :same)
    (str "<div class=\"hist-buf hist-buf-unchanged\">" (escape-html buffer) " — unchanged</div>")
    (str "<div class=\"hist-buf\"><div class=\"hist-buf-name\">" (escape-html buffer)
         (case status :added " (added)" :removed " (removed)" "")
         "</div>" (apply str (map line-html lines)) "</div>")))

(defn- preview-html []
  (if-let [sel @selected]
    (if-let [st (sel-state sel)]
      (let [label (if (= :entry (first sel)) (str "#" (second sel)) (pr-str (second sel)))
            d     (history/project-diff st (session/build-session-snapshot))]
        (str "<div class=\"hist-preview\"><div class=\"hist-preview-bar\">"
             (escape-html label) " → now "
             "<button data-act=\"restore-selected\">restore</button></div>"
             (apply str (map buf-html d))
             "</div>"))
      "")
    ""))

(defn- snap-html [{:keys [name t]}]
  (str "<div class=\"hist-snap" (when (= [:snapshot name] @selected) " selected")
       "\" data-name=\"" (escape-html name) "\">"
       "<span class=\"hist-name\">" (escape-html name) "</span>"
       "<span class=\"hist-time\">" (escape-html (fmt-time t)) "</span>"
       "<button data-act=\"restore\">restore</button>"
       "<button data-act=\"link\">link</button>"
       "<button data-act=\"rename\">rename</button>"
       "<button data-act=\"delete\">delete</button></div>"))

(defn- entry-html [older {:keys [id t kind ok? error] :as e}]
  (str "<div class=\"hist-entry "
       (cond (= kind :restore-point) "hist-rp" ok? "hist-ok" :else "hist-err")
       (when (= [:entry id] @selected) " selected")
       "\" data-id=\"" id "\" title=\"" (escape-html (or error "")) "\">"
       "<span class=\"hist-id\">#" id "</span>"
       "<span class=\"hist-time\">" (escape-html (fmt-time t)) "</span>"
       (when (= kind :restore-point) "<span class=\"hist-kind\">before restore</span>")
       "<span class=\"hist-sum\">" (escape-html (row-summary older e)) "</span></div>"))

(defn render! []
  (when-let [panel (el "history-panel")]
    (let [s     @hs/store
          snaps (reverse (:snapshots s))
          hist  (:history s)                       ; oldest first
          rows  (take @shown (reverse (map-indexed vector hist)))]
      (set! (.-innerHTML panel)
            (str "<div class=\"hist-bar\"><span class=\"hist-title\">history</span>"
                 "<button data-act=\"clear\">clear history</button>"
                 "<button data-act=\"close\">×</button></div>"
                 "<div class=\"hist-body\">"
                 (when (seq snaps)
                   (str "<div class=\"hist-section\">snapshots</div>"
                        (apply str (map snap-html snaps))))
                 "<div class=\"hist-section\">evaluations</div>"
                 (apply str (map (fn [[i e]] (entry-html (when (pos? i) (nth hist (dec i))) e)) rows))
                 (when (> (count hist) @shown)
                   "<button data-act=\"older\">show older</button>")
                 (preview-html)
                 "</div>")))))

(defn open! []
  (reset! visible? true)
  (when-let [p (el "history-panel")] (.remove (.-classList p) "hidden"))
  (when-let [b (el "history-toggle-btn")] (.add (.-classList b) "active"))
  (render!))

(defn close! []
  (reset! visible? false)
  (when-let [p (el "history-panel")] (.add (.-classList p) "hidden"))
  (when-let [b (el "history-toggle-btn")] (.remove (.-classList b) "active")))

(defn toggle! [] (if @visible? (close!) (open!)))

;; ── actions ──────────────────────────────────────────────────────────────────

(defn- copy-link! [nm]
  (let [st   (sel-state [:snapshot nm])
        base (str (.-origin js/location) (.-pathname js/location))]
    (if-not st
      (out! (str "Error: snapshot " (pr-str nm) " is unreadable (corrupt data)") :error)
      (let [{:keys [url error]} (session/share-link base st)
            msg (str "link to snapshot " (pr-str nm) " copied to clipboard")]
        (cond
          error (out! error :error)
          (.-clipboard js/navigator)
          (-> (.writeText (.-clipboard js/navigator) url)
              (.then (fn [] (out! msg :success)))
              (.catch (fn [_] (dialog/prompt! "Copy this URL:" url))))
          :else (dialog/prompt! "Copy this URL:" url))))))

(defn- on-click [^js e]
  (let [t    (.-target e)
        act  (some-> (.closest t "[data-act]") (.getAttribute "data-act"))
        snap (some-> (.closest t ".hist-snap") (.getAttribute "data-name"))
        ent  (some-> (.closest t ".hist-entry") (.getAttribute "data-id") (js/parseInt 10))]
    (try
      (case act
        "close" (close!)
        "older" (do (swap! shown + 100) (render!))
        "clear" (-> (dialog/confirm! "Clear the evaluation history? Snapshots are kept."
                                     {:ok-label "Clear history" :danger? true})
                    (.then (fn [ok?]
                             (when ok?
                               (try
                                 (hs/clear-history!)
                                 (reset! selected nil)
                                 (out! "history cleared — snapshots kept" :success)
                                 (catch :default ex (err! ex)))))))
        "restore" (when snap (restore! snap))
        "restore-selected" (when-let [sel @selected] (restore! (second sel)))
        "link" (when snap (copy-link! snap))
        "rename" (when snap
                   (-> (dialog/prompt! "Rename snapshot:" snap)
                       (.then (fn [n]
                                (when n
                                  (try
                                    (reset! selected nil)
                                    (out! (hs/rename-snapshot! snap n) :success)
                                    (catch :default ex (err! ex))))))))
        "delete" (when snap
                   (-> (dialog/confirm! (str "Delete snapshot " (pr-str snap) "? This can't be undone.")
                                        {:ok-label "Delete" :danger? true})
                       (.then (fn [ok?]
                                (when ok?
                                  (try
                                    (reset! selected nil)
                                    (out! (hs/delete-snapshot! snap) :success)
                                    (catch :default ex (err! ex))))))))
        ;; no button: select a row
        (cond
          snap (do (reset! selected [:snapshot snap]) (render!))
          ent  (do (reset! selected [:entry ent]) (render!))))
      (catch :default ex (err! ex)))))

(defn init! [{:keys [set-output! set-pending-mutes!]}]
  (reset! cbs {:set-output! set-output! :set-pending-mutes! set-pending-mutes!})
  (when-let [p (el "history-panel")]
    (.addEventListener p "click" on-click))
  (add-watch hs/store ::render (fn [_ _ _ _] (when @visible? (render!))))
  (set! (.-__REPULSE_HISTORY__ js/window)
        #js {:ready      (fn [] (= :ready @hs/status))
             :persistent (fn [] @hs/persistent?)
             :entries    (fn []
                           (clj->js (mapv (fn [e] {:id (:id e) :kind (name (:kind e))
                                                   :ok (:ok? e) :hash (:hash e)
                                                   :error (:error e)})
                                          (history/entries-newest-first @hs/store))))
             :snapshots  (fn [] (clj->js (history/snapshot-names @hs/store)))
             :open       open!}))
