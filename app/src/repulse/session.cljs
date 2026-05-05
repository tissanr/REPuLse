(ns repulse.session
  (:require [repulse.audio :as audio]
            [repulse.samples :as samples]
            [repulse.midi :as midi]))

(def current-version 2)
(def storage-key "repulse-session")

(defn- normalize-bpm
  [bpm context]
  (let [coerced (audio/coerce-bpm bpm)]
    (when (and (some? bpm) (not= bpm coerced))
      (.warn js/console
             (str "[REPuLse] Coerced invalid BPM from " context ": " bpm " -> " coerced)))
    coerced))

;;; ── Editor text injection ────────────────────────────────────────────────
;;; Set by app.cljs init after the editor view is created.
;;; Returns the current editor buffer as a string.

(defonce editor-text-fn (atom nil))

;;; ── Build snapshot ────────────────────────────────────────────────────────

(defn build-session-snapshot
  "Collect all session state into a serializable map."
  []
  {:v       current-version
   :editor  (if-let [f @editor-text-fn] (f) "")
   :bpm     (or (audio/get-bpm) 120)
   ;; FX params are intentionally not persisted. The editor buffer is the
   ;; source of truth; restored plugin state would make reloads non-reproducible.
   :fx      []
   :bank    @samples/active-bank-prefix
   :sources (mapv (fn [{:keys [type id]}]
                    {:type (cljs.core/name type) :id id})
                  (filter #(= :github (:type %)) @samples/loaded-sources))
   :muted   (mapv cljs.core/name (:muted @audio/scheduler-state))
   :midi    (into {} (map (fn [[k {:keys [target]}]]
                            [(str k) (when target (cljs.core/name target))])
                          @midi/cc-mappings))})

;;; ── Save (debounced) ─────────────────────────────────────────────────────

(defonce save-timeout (atom nil))

(defn save-session! []
  (try
    (let [data (build-session-snapshot)]
      (.setItem js/localStorage storage-key
                (js/JSON.stringify (clj->js data))))
    (catch :default _ nil)))

(defn schedule-save! []
  (when-let [id @save-timeout]
    (js/clearTimeout id))
  (reset! save-timeout
    (js/setTimeout save-session! 300)))

;;; ── Load ─────────────────────────────────────────────────────────────────

(defn load-session
  "Read session from localStorage. Returns a keyword-keyed map or nil."
  []
  (try
    (when-let [raw (.getItem js/localStorage storage-key)]
      (let [data (js->clj (js/JSON.parse raw) :keywordize-keys true)]
        (when (= (:v data) current-version)
          (update data :bpm #(normalize-bpm % "localStorage")))))
    (catch :default _ nil)))

;;; ── Migration from Phase D ────────────────────────────────────────────────

(defn migrate-legacy!
  "Convert Phase D keys (repulse-editor / repulse-bpm) to v2 session format.
   Returns the migrated session map or nil if no legacy data exists."
  []
  (let [editor (.getItem js/localStorage "repulse-editor")
        bpm    (.getItem js/localStorage "repulse-bpm")]
    (when (or editor bpm)
      (let [session {:v       current-version
                     :editor  (or editor "(seq :bd :sd :bd :sd)")
                     :bpm     (normalize-bpm (some-> bpm js/parseFloat) "legacy localStorage")
                     :fx      []
                     :bank    nil
                     :sources []
                     :muted   []
                     :midi    {}}]
        (.setItem js/localStorage storage-key
                  (js/JSON.stringify (clj->js session)))
        (.removeItem js/localStorage "repulse-editor")
        (.removeItem js/localStorage "repulse-bpm")
        session))))

;;; ── Reset ────────────────────────────────────────────────────────────────

(defn wipe!
  "Delete all persisted state (current and legacy keys)."
  []
  (.removeItem js/localStorage storage-key)
  (.removeItem js/localStorage "repulse-editor")
  (.removeItem js/localStorage "repulse-bpm"))
