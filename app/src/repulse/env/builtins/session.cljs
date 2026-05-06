(ns repulse.env.builtins.session
  "Session builtins: share!, reset!, help-export"
  (:require [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.samples :as samples]
            [repulse.session :as session]))

(defn make-builtins
  "ctx — {:share! f}"
  [{:keys [share!]}]
  {"share!"
   (fn [] (when share! (share!)) nil)

   "reset!"
   (fn []
     (audio/stop!)
     (session/wipe!)
     (.reload js/window.location)
     nil)

   "help-export"
   (fn []
     (let [state       @audio/scheduler-state
           track-names (into {} (map (fn [k] [(cljs.core/name k) true]) (keys (:tracks state))))
           muted-names (mapv cljs.core/name (:muted state))
           bpm         (audio/get-bpm)
           fx-list     (mapv (fn [{:keys [name plugin bypassed?]}]
                               {:name     name
                                :params   (try (js->clj (.getParams ^js plugin)) (catch :default _ {}))
                                :bypassed (boolean bypassed?)})
                             (filter :active? @fx/chain))
           bank        @samples/active-bank-prefix
           sources     (mapv (fn [{:keys [type id]}]
                               {:type (cljs.core/name type) :id id})
                             (filter #(= :github (:type %)) @samples/loaded-sources))]
       {:bpm     bpm
        :tracks  track-names
        :muted   muted-names
        :fx      fx-list
        :bank    bank
        :sources sources})))})
