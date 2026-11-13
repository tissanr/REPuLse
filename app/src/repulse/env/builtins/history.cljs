(ns repulse.env.builtins.history
  "Snapshot & history builtins (Phase SK2): snapshot!, snapshots, restore!,
   rename-snapshot!, delete-snapshot!, history."
  (:require [repulse.lisp.eval :as leval]
            [repulse.history :as history]
            [repulse.history-store :as hs]
            [repulse.session :as session]
            [repulse.ui.history-panel :as panel]))

(defn- args-error [msg] (throw (ex-info msg {})))

(defn- str-arg [x] (let [v (leval/unwrap x)] (when (string? v) v)))

(defn make-builtins [_ctx]
  {"snapshot!"
   (fn [& args]
     (let [nm (when (= 1 (count args)) (str-arg (first args)))]
       (when-not nm
         (args-error "snapshot! expects one name string, e.g. (snapshot! \"take A\")"))
       (hs/save-snapshot! (session/build-session-snapshot) nm)))

   "snapshots"
   (fn []
     (hs/summary)                        ; readiness check (K13)
     (history/snapshot-names @hs/store))

   "restore!"
   (fn [& args]
     (let [v (when (= 1 (count args)) (leval/unwrap (first args)))]
       (when-not (or (string? v) (and (number? v) (integer? v)))
         (args-error "restore! expects a snapshot name or a history number, e.g. (restore! \"take A\") or (restore! 12)"))
       (let [{:keys [state message]} (hs/begin-restore! v (session/build-session-snapshot))]
         ;; K11: apply after the running evaluation (and its history record)
         (js/setTimeout #(panel/apply-state! state) 0)
         message)))

   "rename-snapshot!"
   (fn [& args]
     (let [[a b] (map str-arg args)]
       (when-not (and (= 2 (count args)) a b)
         (args-error "rename-snapshot! expects two name strings, e.g. (rename-snapshot! \"take A\" \"verse\")"))
       (hs/rename-snapshot! a b)))

   "delete-snapshot!"
   (fn [& args]
     (let [nm (when (= 1 (count args)) (str-arg (first args)))]
       (when-not nm
         (args-error "delete-snapshot! expects one name string, e.g. (delete-snapshot! \"take A\")"))
       (hs/delete-snapshot! nm)))

   "history"
   (fn []
     (let [msg (hs/summary)]
       (panel/open!)
       (str "=> " msg)))})
