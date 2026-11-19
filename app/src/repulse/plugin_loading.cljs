(ns repulse.plugin-loading
  "Plugin consent management and load/unload Lisp built-in factories.
   Responsibility: gate plugin loading behind a per-origin confirmation dialog;
   provide the `load-plugin` and `unload-plugin` built-in fn factories.
   Exports: dynamic-import!, plugin-consent, plugin-origin, confirm-plugin-origin!,
            load-plugin-builtin, unload-plugin-builtin."
  (:require [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.plugins :as plugins]
            [repulse.fx :as fx]
            [repulse.ui.dialog :as dialog]))

;; Closure Compiler rewrites bare import() to require() under :advanced.
;; index.html exposes window.__import__ from a plain <script> tag so Closure
;; never sees the import() syntax — no new Function() / unsafe-eval needed.
(def dynamic-import! js/window.__import__)

;; Session-scoped plugin consent: origin string -> :granted | :denied
(defonce plugin-consent (atom {}))

(defn plugin-origin [url]
  (try
    (.-origin (js/URL. url (.-href js/location)))
    (catch :default _ nil)))

(defn plugin-denied-error [origin]
  (lisp/eval-error
   (str "Plugin from " origin
        " was previously denied. Reload the page to reconsider.")))

(defn confirm-plugin-origin!
  "→ Promise<boolean>. Asks once per origin and session with an in-page
   dialog (a native confirm would block the audio scheduler)."
  [origin]
  (case (get @plugin-consent origin)
    :granted (js/Promise.resolve true)
    :denied  (js/Promise.resolve false)
    (-> (dialog/confirm!
         (str "This code wants to load a plugin from " origin
              ". Plugins run JavaScript in your session. Only load from "
              "sources you trust. Load?")
         {:ok-label "Load plugin"})
        (.then (fn [allowed?]
                 (swap! plugin-consent assoc origin (if allowed? :granted :denied))
                 allowed?)))))

;;; Callbacks wired by app.cljs at init time

(defonce ^:private make-host-ref             (atom nil))
(defonce ^:private mount-visual!-ref         (atom nil))
(defonce ^:private maybe-hide-visual!-ref    (atom nil))
(defonce ^:private set-output!-ref           (atom nil))

(defn init!
  "Wire app-level host/visual fns into this module.
   Must be called before any `load-plugin` or `unload-plugin` builtin is invoked.
   config — {:make-host-fn f :mount-visual!-fn f :maybe-hide-visual!-fn f
             :set-output!-fn f (optional; reports the consent outcome)}"
  [{:keys [make-host-fn mount-visual!-fn maybe-hide-visual!-fn set-output!-fn]}]
  (reset! make-host-ref          make-host-fn)
  (reset! mount-visual!-ref      mount-visual!-fn)
  (reset! maybe-hide-visual!-ref maybe-hide-visual!-fn)
  (reset! set-output!-ref        set-output!-fn))

;;; Built-in fn factories

(defn- import-plugin! [url]
  (-> (dynamic-import! url)
      (.then (fn [m]
               (let [plug (.-default m)]
                 (when (= "effect" (.-type plug))
                   (fx/remove-effect! (.-name plug)))
                 (plugins/register! plug (@make-host-ref))
                 (if (= "visual" (.-type plug))
                   (@mount-visual!-ref plug)
                   (when (= "effect" (.-type plug))
                     (fx/add-effect! plug)))
                 plug)))
      (.catch (fn [e]
                (js/console.warn "[REPuLse] Plugin load failed:" e)))))

(defn load-plugin-builtin
  "Returns the Lisp `load-plugin` built-in fn."
  []
  (fn [url]
    (let [url'   (leval/unwrap url)
          origin (plugin-origin url')]
      (cond
        (nil? origin)
        (lisp/eval-error (str "Invalid plugin URL: " url'))

        (= :denied (get @plugin-consent origin))
        (plugin-denied-error origin)

        (= :granted (get @plugin-consent origin))
        (do (import-plugin! url') nil)

        :else
        ;; First plugin from this origin: ask without blocking evaluation or
        ;; audio; the plugin loads once the user agrees.
        (do
          (-> (confirm-plugin-origin! origin)
              (.then (fn [allowed?]
                       (if allowed?
                         (-> (import-plugin! url')
                             (.then (fn [plug]
                                      (when-let [f @set-output!-ref]
                                        (if plug
                                          (f (str "plugin loaded: " (.-name plug)) :success)
                                          (f (str "Plugin from " origin " failed to load — see the console.")
                                             :error))))))
                         (when-let [f @set-output!-ref]
                           (f (str "Plugin from " origin " was not loaded.") :error))))))
          (str "=> waiting for permission to load a plugin from " origin " …"))))))

(defn unload-plugin-builtin
  "Returns the Lisp `unload-plugin` built-in fn."
  []
  (fn [name]
    (let [name' (leval/unwrap name)]
      (if (get @plugins/registry name')
        (do (plugins/unregister! name')
            (@maybe-hide-visual!-ref)
            (str "unloaded: " name'))
        (lisp/eval-error (str "no plugin named \"" name' "\""))))))
