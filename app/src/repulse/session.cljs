(ns repulse.session
  (:require [clojure.string :as str]
            [repulse.audio :as audio]
            [repulse.samples :as samples]
            [repulse.midi :as midi]
            [repulse.specs :as specs]))

(def current-version 3)
(def storage-key "repulse-session")

(defn- normalize-bpm
  [bpm context]
  (let [coerced (audio/coerce-bpm bpm)]
    (when (and (some? bpm) (not= bpm coerced))
      (.warn js/console
             (str "[REPuLse] Coerced invalid BPM from " context ": " bpm " -> " coerced)))
    coerced))

;;; ── Project injection (Phase SONG4) ──────────────────────────────────────
;;; Set by app.cljs init after the buffers are created.
;;; Returns the current project {:main :active :buffers [{:name :code} …]}.

(defonce project-fn (atom nil))

(defn- empty-project []
  {:main "main" :active "main" :buffers [{:name "main" :code ""}]})

(defn- empty-session []
  (merge {:v current-version} (empty-project)
         {:bpm 120 :fx [] :bank nil :sources [] :muted [] :midi {}}))

(defn active-code
  "The active buffer's code (what the AI assistant sees), or \"\"."
  []
  (or (when-let [f @project-fn]
        (let [p (f)]
          (some #(when (= (:active p) (:name %)) (:code %)) (:buffers p))))
      ""))

;;; ── Build snapshot ────────────────────────────────────────────────────────

(defn build-session-snapshot
  "Collect all session state into a serializable v3 map."
  []
  (let [project  (or (when-let [f @project-fn] (f)) (empty-project))
        snapshot {:v       current-version
                  :main    (:main project)
                  :active  (:active project)
                  :buffers (:buffers project)
                  :bpm     (or (audio/get-bpm) 120)
                  ;; FX params are intentionally not persisted. The editor buffers are the
                  ;; source of truth; restored plugin state would make reloads non-reproducible.
                  :fx      []
                  :bank    @samples/active-bank-prefix
                  :sources (mapv (fn [{:keys [type id]}]
                                   {:type (cljs.core/name type) :id id})
                                 (filter #(= :github (:type %)) @samples/loaded-sources))
                  :muted   (mapv cljs.core/name (:muted @audio/scheduler-state))
                  :midi    (into {} (map (fn [[k {:keys [target]}]]
                                           [(str k) (when target (cljs.core/name target))])
                                         @midi/cc-mappings))}]
    (or (specs/sanitize-session-v3 snapshot)
        (do
          (js/console.warn "[REPuLse] Refusing to build invalid session snapshot")
          (empty-session)))))

;;; ── v2 → v3 migration ───────────────────────────────────────────────────

(defn migrate-v2->v3
  "Pure: a sanitized v2 session becomes a v3 session whose main buffer holds
   the old editor text."
  [s2]
  (-> s2
      (dissoc :editor)
      (assoc :v 3 :main "main" :active "main"
             :buffers [{:name "main" :code (:editor s2)}])))

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
  "Read session from localStorage. Returns a keyword-keyed v3 map or nil.
   A v2 session is migrated in memory (the next save writes it back as v3)."
  []
  (try
    (when-let [raw (.getItem js/localStorage storage-key)]
      (let [data    (js->clj (js/JSON.parse raw) :keywordize-keys true)
            coerced (when (map? data) (update data :bpm #(normalize-bpm % "localStorage")))
            result  (case (:v data)
                      3 (specs/sanitize-session-v3 coerced)
                      2 (some-> (specs/sanitize-session-v2 coerced) migrate-v2->v3)
                      ::unsupported)]
        (cond
          (= ::unsupported result) nil
          result                   result
          :else (do (js/console.warn "[REPuLse] Ignoring malformed persisted session")
                    nil))))
    (catch :default _ nil)))

;;; ── Migration from Phase D ────────────────────────────────────────────────

(defn migrate-legacy!
  "Convert Phase D keys (repulse-editor / repulse-bpm) to the v3 session format.
   Returns the migrated session map or nil if no legacy data exists."
  []
  (let [editor (.getItem js/localStorage "repulse-editor")
        bpm    (.getItem js/localStorage "repulse-bpm")]
    (when (or editor bpm)
      (let [session {:v       2
                     :editor  (or editor "(seq :bd :sd :bd :sd)")
                     :bpm     (normalize-bpm (some-> bpm js/parseFloat) "legacy localStorage")
                     :fx      []
                     :bank    nil
                     :sources []
                     :muted   []
                     :midi    {}}]
        (when-let [sanitized (some-> (specs/sanitize-session-v2 session) migrate-v2->v3)]
          (.setItem js/localStorage storage-key
                    (js/JSON.stringify (clj->js sanitized)))
          (.removeItem js/localStorage "repulse-editor")
          (.removeItem js/localStorage "repulse-bpm")
          sanitized)))))

;;; ── Share links (#v3:, decoding #v2: / #v1:) ────────────────────────────

(def max-share-url-length 64000)

(defn b64-encode [s]
  (js/btoa (js/unescape (js/encodeURIComponent s))))

(defn b64-decode [s]
  (js/decodeURIComponent (js/escape (js/atob s))))

(defn encode-share-hash
  "\"#v3:\" + base64(JSON) of a v3 session snapshot."
  [snapshot]
  (str "#v3:" (b64-encode (js/JSON.stringify (clj->js snapshot)))))

(defn decode-share-hash
  "Decode a URL hash (#v3:, #v2: or #v1:) into a v3 session map, or nil."
  [hash]
  (try
    (cond
      (and hash (str/starts-with? hash "#v3:"))
      (let [data (js->clj (js/JSON.parse (b64-decode (subs hash 4))) :keywordize-keys true)]
        (specs/sanitize-session-v3 (update data :bpm audio/coerce-bpm)))

      (and hash (str/starts-with? hash "#v2:"))
      (let [data (js->clj (js/JSON.parse (b64-decode (subs hash 4))) :keywordize-keys true)]
        (some-> (specs/sanitize-session-v2 (update data :bpm audio/coerce-bpm))
                migrate-v2->v3))

      (and hash (str/starts-with? hash "#v1:"))
      (let [data (js->clj (js/JSON.parse (js/atob (subs hash 4))) :keywordize-keys true)]
        (when data
          (some-> (specs/sanitize-session-v2
                   {:v       2
                    :bpm     (audio/coerce-bpm (:bpm data))
                    :editor  (:editor data)
                    :fx      []
                    :bank    nil
                    :sources []
                    :muted   []
                    :midi    {}})
                  migrate-v2->v3)))

      :else nil)
    (catch :default _ nil)))

(defn share-link
  "{:url u} for base + #v3: hash, or {:error msg} when the URL would exceed
   max-share-url-length characters."
  [base snapshot]
  (let [u (str base (encode-share-hash snapshot))
        n (count u)]
    (if (> n max-share-url-length)
      {:error (str "Share failed: link would be " n " characters (limit "
                   max-share-url-length "). Export the project as a .repulse file instead.")}
      {:url u})))

;;; ── Reset ────────────────────────────────────────────────────────────────

(defn wipe!
  "Delete all persisted state (current, legacy, and AI keys)."
  []
  (.removeItem js/localStorage storage-key)
  (.removeItem js/localStorage "repulse-editor")
  (.removeItem js/localStorage "repulse-bpm")
  ;; Collect AI keys first (can't remove while iterating — indices shift)
  (let [ls js/localStorage
        ai-keys (filterv #(= 0 (.indexOf % "repulse:ai:"))
                          (for [i (range (.-length ls))] (.key ls i)))]
    (doseq [k ai-keys]
      (.removeItem ls k))))
