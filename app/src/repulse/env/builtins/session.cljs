(ns repulse.env.builtins.session
  "Session builtins: share!, reset!"
  (:require [repulse.audio :as audio]
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
     nil)})
