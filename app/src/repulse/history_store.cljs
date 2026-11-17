(ns repulse.history-store
  "In-memory mirror + write-behind coordinator for the SK2 history store.
   All logic is in repulse.history (pure); this ns holds the atoms and writes
   the resulting ops to a HistoryStorage adapter."
  (:require [repulse.history :as history]
            [clojure.string :as str]))

(defonce store (atom history/empty-store))
(defonce status (atom :uninitialized))   ; :uninitialized | :loading | :ready
(defonce persistent? (atom false))
(defonce ^:private adapter (atom nil))
(defonce ^:private queued (atom []))     ; [[text now ok? error] …] while :loading

(defn- commit! [{s :store ops :ops :as r}]
  (reset! store s)
  (when (seq ops)
    (try (history/-write! @adapter ops)
         (catch :default e
           (js/console.warn "[REPuLse] Could not save history:" (.-name e)))))
  r)

(defn- do-append! [text now ok? error]
  (commit! (history/append-eval @store text now ok? error)))

(defn init!
  "Load the persisted store from adapter-impl, repair it, replay queued
   evaluations, then become :ready."
  [adapter-impl persistent]
  (reset! adapter adapter-impl)
  (reset! persistent? (boolean persistent))
  (reset! status :loading)
  (history/-load
    adapter-impl
    (fn [loaded]
      (let [{s :store ops :ops} (history/load->store (:meta loaded) (:blobs loaded))]
        (reset! store s)
        (when (and loaded (seq ops)) (history/-write! adapter-impl ops))
        (when (nil? loaded)
          (js/console.warn "[REPuLse] Could not load history — starting empty"))
        (let [q @queued]
          (reset! queued [])
          (doseq [[text now ok? error] q] (do-append! text now ok? error)))
        (reset! status :ready)))))

(defn- check-ready! []
  (case @status
    :uninitialized (throw (ex-info "snapshots and history are only available in the full app" {}))
    :loading (throw (ex-info "snapshot store is still loading — try again in a moment" {}))
    nil))

(defn record-eval!
  "Record a project evaluation. Never throws."
  [state ok? error]
  (try
    (let [text (history/state->blob state)
          now  (js/Date.now)]
      (case @status
        :uninitialized nil
        :loading (swap! queued conj [text now ok? error])
        (do-append! text now ok? error)))
    (catch :default e
      (js/console.warn "[REPuLse] Could not record history:" e))))

(defn- fail-on-error! [r]
  (when-let [e (:error r)] (throw (ex-info e {})))
  r)

(defn save-snapshot! [state nm]
  (check-ready!)
  (let [r (fail-on-error! (history/save-snapshot @store nm (history/state->blob state) (js/Date.now)))]
    (commit! r)
    (str "=> snapshot " (pr-str (:name r)) " " (name (:status r)))))

(defn delete-snapshot! [nm]
  (check-ready!)
  (let [r (fail-on-error! (history/delete-snapshot @store nm))]
    (commit! r)
    (str "=> snapshot " (pr-str (:name r)) " deleted")))

(defn rename-snapshot! [old new]
  (check-ready!)
  (let [r (fail-on-error! (history/rename-snapshot @store old new))]
    (commit! r)
    (str "=> snapshot " (pr-str (:old r)) " renamed to " (pr-str (:new r)))))

(defn begin-restore!
  "Resolve target, record the restore point, return {:state :message}."
  [target state]
  (check-ready!)
  (let [r (fail-on-error! (history/prepare-restore @store target (history/state->blob state) (js/Date.now)))
        st (some-> (:text r) history/blob->state)]
    (when-not st
      (throw (ex-info (str (:label r) " is unreadable (corrupt data)") {})))
    (commit! r)
    {:state   st
     :message (history/restore-message (:label r) (:restore-point r))}))

(defn clear-history! []
  (check-ready!)
  (commit! (history/clear-history @store)))

(defn- plural [n one many] (str n " " (if (= 1 n) one many)))

(defn summary []
  (check-ready!)
  (str "history: " (plural (count (:history @store)) "evaluation" "evaluations")
       ", " (plural (count (:snapshots @store)) "snapshot" "snapshots")
       (when-not @persistent? " — not saved across reloads (IndexedDB unavailable)")))
