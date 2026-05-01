(ns repulse.env.builtins.routing
  "Routing builtins: bus, load-plugin, unload-plugin, freesound-key!, freesound!"
  (:require [repulse.audio :as audio]
            [repulse.bus :as bus]
            [repulse.samples :as samples]
            [repulse.plugin-loading :as plugin-loading]
            [repulse.lisp.eval :as leval]))

;;; Private state — Freesound API key, owned by this namespace.
(defonce ^:private freesound-api-key (atom nil))

(defn make-builtins
  "ctx — {:set-output! f}"
  [{:keys [set-output!]}]
  {"bus"
   (fn [& args]
     (let [args'    (mapv leval/unwrap args)
           bus-name (first args')
           bus-type (or (second args') :control)]
       (when-not (keyword? bus-name)
         (throw (js/Error. "bus: first argument must be a keyword, e.g. (bus :lfo :control)")))
       (when-not (#{:control :audio} bus-type)
         (throw (js/Error. (str "bus: type must be :control or :audio, got " bus-type))))
       (bus/create-bus! (audio/get-ctx) bus-name bus-type)
       (str "=> bus " bus-name " (" (name bus-type) ")")))

   "load-plugin"   (plugin-loading/load-plugin-builtin)
   "unload-plugin" (plugin-loading/unload-plugin-builtin)

   "freesound-key!"
   (fn [key]
     (reset! freesound-api-key (leval/unwrap key))
     "Freesound API key set")

   "freesound!"
   (fn [query]
     (let [q   (leval/unwrap query)
           key @freesound-api-key]
       (if-not key
         "Error: set API key first with (freesound-key! \"your-key\")"
         (do
           (-> (js/fetch (str "https://freesound.org/apiv2/search/text/"
                              "?query=" (js/encodeURIComponent q)
                              "&token=" key
                              "&fields=id,name,previews"
                              "&page_size=5"))
               (.then #(.json %))
               (.then (fn [data]
                        (let [results (js->clj (.-results data) :keywordize-keys true)]
                          (doseq [{:keys [id previews]} results]
                            (when-let [url (get previews :preview-hq-mp3)]
                              (samples/register-url! (str "freesound-" id) url)))
                          (swap! samples/loaded-sources conj
                                 {:type :freesound :query q :count (count results)})
                          (set-output!
                            (str "loaded " (count results) " sounds: "
                                 (clojure.string/join ", "
                                   (map #(str ":freesound-" (:id %)) results)))
                            :success))))
               (.catch (fn [e]
                         (set-output! (str "Freesound error: " e) :error))))
           "searching freesound…"))))})
