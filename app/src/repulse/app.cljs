(ns repulse.app
  (:require [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.midi :as midi]
            [repulse.samples :as samples]
            [repulse.plugins :as plugins]
            [repulse.session :as session]
            [repulse.plugins.compressor :as compressor-plugin]
            [repulse.ui.editor :as editor]
            [repulse.ui.timeline :as timeline]
            [repulse.ui.context-panel :as ctx-panel]
            [repulse.ui.snippet-panel :as snippet-panel]
            [repulse.env.builtins :as builtins]
            [repulse.eval-orchestrator :as eo]
            [repulse.plugin-loading :as plugin-loading]
            [repulse.content.first-visit :as first-visit]
            ["./lisp-lang/providers.js" :refer [setBankNamesProvider setFxNamesProvider]]
            ["@codemirror/commands" :refer [selectAll]]))

;;; DOM helpers

(defn el [id] (.getElementById js/document id))

(defn set-output! [msg status]
  (when-let [e (el "output")]
    (set! (.-textContent e) msg)
    (set! (.-className e) (str "output " (name status)))))

(defn set-playing! [playing?]
  (when-let [dot (el "playing-dot")]
    (if playing?
      (.add (.-classList dot) "active")
      (.remove (.-classList dot) "active")))
  (when-let [btn (el "play-btn")]
    (set! (.-textContent btn) (if playing? "■ stop" "▶ play"))
    (if playing?
      (.add (.-classList btn) "active")
      (.remove (.-classList btn) "active"))))

(defn on-beat []
  (when-let [dot (el "playing-dot")]
    (.add (.-classList dot) "flash")
    (js/setTimeout #(.remove (.-classList dot) "flash") 80)))

;;; Stop factory

(defn make-stop-fn []
  (fn []
    (audio/stop!)
    (editor/clear-highlights!)
    (set-playing! false)
    (set-output! "stopped" :idle)))

;;; Plugin host support

(defn make-host []
  #js {:audioCtx    (audio/get-ctx)
       :analyser    @audio/analyser-node
       :masterGain  @audio/master-gain
       :sampleRate  (.-sampleRate (audio/get-ctx))
       :version     "1.0.0"
       :registerLisp (fn [name f]
                       (swap! builtins/env-atom assoc name f))})

(defn mount-visual! [plugin]
  (let [panel (el "plugin-panel")]
    (.mount plugin panel)
    (.remove (.-classList panel) "hidden")))

(defn maybe-hide-visual-panel! []
  (when (empty? (plugins/visual-plugins))
    (.add (.-classList (el "plugin-panel")) "hidden")))

;;; Session persistence

(defn- b64-encode [s]
  (js/btoa (js/unescape (js/encodeURIComponent s))))

(defn- b64-decode [s]
  (js/decodeURIComponent (js/escape (js/atob s))))

(defn- encode-session []
  (let [snap (session/build-session-snapshot)
        obj  (clj->js snap)]
    (str "#v2:" (b64-encode (js/JSON.stringify obj)))))

(defn- decode-session
  "Decode a URL hash into a session map. Handles #v1: and #v2: formats."
  [hash]
  (try
    (cond
      (and hash (clojure.string/starts-with? hash "#v2:"))
      (let [b64  (subs hash 4)
            data (js->clj (js/JSON.parse (b64-decode b64)) :keywordize-keys true)]
        (when (= (:v data) 2) data))

      (and hash (clojure.string/starts-with? hash "#v1:"))
      (let [b64  (subs hash 4)
            data (js->clj (js/JSON.parse (js/atob b64)) :keywordize-keys true)]
        (when data
          {:v       2
           :bpm     (:bpm data)
           :editor  (:editor data)
           :fx      []
           :bank    nil
           :sources []
           :muted   []
           :midi    {}}))

      :else nil)
    (catch :default _ nil)))

(defn share! []
  (let [session (encode-session)
        url     (str (.-origin js/location) (.-pathname js/location) session)]
    (if (.-clipboard js/navigator)
      (-> (.writeText (.-clipboard js/navigator) url)
          (.then (fn [] (set-output! (str "URL copied to clipboard") :success)))
          (.catch (fn [_] (js/prompt "Copy this URL:" url))))
      (js/prompt "Copy this URL:" url))))

;;; App bootstrap

(defn on-play-btn-click []
  (if (audio/playing?)
    (do (audio/stop!)
        (editor/clear-highlights!)
        (set-playing! false)
        (set-output! "stopped" :idle))
    (when-let [view @editor/editor-view]
      (eo/evaluate! (.. view -state -doc (toString))))))

(defn- start-icon-rotation! []
  (let [icons #js ["/icon.png" "/icon1.png" "/icon2.png"]
        idx   (atom 0)]
    (js/setInterval
      (fn []
        (swap! idx #(mod (inc %) 3))
        (when-let [img (el "header-icon")]
          (set! (.-style.-opacity img) "0")
          (js/setTimeout
            (fn []
              (set! (.-src img) (aget icons @idx))
              (set! (.-style.-opacity img) "1"))
            400)))
      8000)))

(defn build-dom! []
  (let [app (el "app")]
    (set! (.-innerHTML app)
          (str "<header>"
               "  <h1><img id=\"header-icon\" src=\"/icon.png\" class=\"header-icon\" alt=\"\"> REPuLse</h1>"
               "  <div class=\"header-controls\">"
               "    <button id=\"tap-btn\" class=\"tap-btn\">tap</button>"
               "    <button id=\"share-btn\" class=\"share-btn\">share</button>"
               "    <button id=\"snippet-toggle-btn\" class=\"snippet-toggle-btn\">lib</button>"
               "    <button id=\"play-btn\" class=\"play-btn\">&#9654; play</button>"
               "    <div id=\"playing-dot\" class=\"playing-dot\"></div>"
               "  </div>"
               "</header>"
               "<div class=\"main-area\">"
               "  <div id=\"editor-container\" class=\"editor-container\"></div>"
               "  <div id=\"context-panel\" class=\"context-panel\"></div>"
               "</div>"
               "<div id=\"cmd-bar\" class=\"cmd-bar\">"
               "  <span class=\"cmd-prompt\">&gt;</span>"
               "  <div id=\"cmd-container\" class=\"cmd-container\"></div>"
               "</div>"
               "<div id=\"track-panel\" class=\"track-panel\"></div>"
               "<div id=\"snippet-panel\" class=\"snippet-panel hidden\"></div>"
               "<div id=\"plugin-panel\" class=\"plugin-panel hidden\"></div>"
               "<footer>"
               "  <span id=\"output\" class=\"output\">ready &mdash; Alt+Enter or click play</span>"
               "  <span class=\"hint\">Alt+Enter to eval</span>"
               "</footer>")))
  (.addEventListener (el "play-btn")           "click" on-play-btn-click)
  (.addEventListener (el "tap-btn")            "click" (fn [] (eo/evaluate! "(tap!)")))
  (.addEventListener (el "share-btn")          "click" share!)
  (.addEventListener (el "snippet-toggle-btn") "click" snippet-panel/toggle-panel!)
  (start-icon-rotation!))

(defn- attach-slider-listener! []
  (when-let [panel (el "context-panel")]
    ;; Pause context panel re-renders while a slider is held down
    (.addEventListener panel "pointerdown"
      (fn [^js e]
        (when (.contains (.-classList (.-target e)) "ctx-slider")
          (reset! ctx-panel/slider-active? true))))
    (.addEventListener js/document "pointerup"
      (fn [_]
        (when @ctx-panel/slider-active?
          (reset! ctx-panel/slider-active? false)
          (js/requestAnimationFrame ctx-panel/render-context-panel!))))
    (.addEventListener panel "input"
      (fn [^js e]
        (let [target (.-target e)]
          (when (and (= "range" (.-type target))
                     (.contains (.-classList target) "ctx-slider"))
            (let [fx-name    (.. target -dataset -fx)
                  track-name (.. target -dataset -track)
                  param-name (.. target -dataset -param)
                  new-val    (js/parseFloat (.-value target))
                  row        (.-parentNode target)
                  val-el     (when row (.querySelector row ".ctx-param-val"))
                  fmtd       (if (== new-val (Math/round new-val))
                               (str (int new-val))
                               (.toFixed new-val 2))]
              (when val-el (set! (.-textContent val-el) fmtd))
              (cond
                ;; Per-track FX slider: has both data-track and data-fx
                (and (seq fx-name) (seq track-name))
                (eo/per-track-fx-slider-patch-and-eval! track-name fx-name param-name new-val)
                ;; Global FX slider: has data-fx only
                (seq fx-name)
                (eo/fx-slider-patch-and-eval! fx-name param-name new-val)
                ;; Track param slider: has data-track only
                :else
                (eo/slider-patch-and-eval! track-name param-name new-val)))))))))

(defn init []
  ;; Wire module-level callbacks before anything else runs
  (builtins/init! {:on-beat-fn      on-beat
                   :set-playing!-fn set-playing!
                   :set-output!-fn  set-output!
                   :make-stop-fn-fn make-stop-fn
                   :share!-fn       share!})
  (eo/init! {:on-beat-fn      on-beat
             :make-stop-fn-fn make-stop-fn
             :set-playing!-fn set-playing!
             :set-output!-fn  set-output!})
  (plugin-loading/init! {:make-host-fn             make-host
                         :mount-visual!-fn          mount-visual!
                         :maybe-hide-visual!-fn     maybe-hide-visual-panel!})
  ;; Wire evaluate-ref so builtins (demo, load-gist) can call evaluate! lazily
  (reset! builtins/evaluate-ref eo/evaluate!)

  (build-dom!)
  (snippet-panel/init!)
  (attach-slider-listener!)
  (builtins/ensure-env!)
  (setBankNamesProvider (fn [] (clj->js (samples/bank-names))))
  (setFxNamesProvider   (fn [] (clj->js (mapv :name @fx/chain))))

  ;; Register global mute-toggle for track panel onclick
  (set! (.-_repulseMuteToggle js/window)
        (fn [track-name-str]
          (let [kw (keyword track-name-str)]
            (if (contains? (:muted @audio/scheduler-state) kw)
              (audio/unmute-track! kw)
              (audio/mute-track! kw)))))

  (reset! editor/cmd-view (editor/make-cmd-editor (el "cmd-container") eo/evaluate!))

  ;; ── Session restore ───────────────────────────────────────────────────────
  ;; Precedence: URL hash (#v2:/#v1:) > localStorage v2 > legacy keys > first visit
  (let [hash        (.-hash js/location)
        url-session (decode-session hash)
        stored      (or (session/load-session) (session/migrate-legacy!))
        active-sess (or url-session stored)
        init-text   (or (:editor active-sess) "")]

    ;; Remove hash from URL (avoid re-consuming on F5)
    (when url-session
      (.replaceState js/history nil nil (.-pathname js/location)))

    ;; Create editor with the session content (or empty for first-visit)
    (let [container (el "editor-container")
          view      (editor/make-editor container init-text eo/evaluate!)]
      (reset! editor/editor-view view)
      (.focus view))

    ;; Register the editor-text getter for session snapshots
    (reset! session/editor-text-fn
      (fn [] (when-let [v @editor/editor-view] (.. v -state -doc (toString)))))

    (if active-sess
      ;; ── Restore existing session ──────────────────────────────────────────
      (do
        (audio/set-bpm! (audio/coerce-bpm (:bpm active-sess)))
        ;; Bank prefix (stored as string, set-bank-prefix! accepts string or keyword)
        (when (:bank active-sess)
          (samples/set-bank-prefix! (:bank active-sess)))
        ;; Re-fetch external github sample sources asynchronously
        (doseq [{:keys [type id]} (or (:sources active-sess) [])]
          (when (and id (= "github" (str type)))
            (samples/load-external! (str "github:" id))))
        ;; Restore effect params + bypass after plugins finish loading (~500ms)
        (js/setTimeout
          (fn []
            (doseq [{:keys [name params bypassed]} (or (:fx active-sess) [])]
              (when name
                (doseq [[k v] (or params {})]
                  (fx/set-param! name (clojure.core/name k) v))
                (when bypassed
                  (fx/bypass! name true))))
            ;; set-param! marks effects :active? true — reset so the context panel
            ;; only shows effects that the user's code explicitly activates via (fx ...).
            (swap! fx/chain (fn [c] (mapv #(assoc % :active? false) c)))
            ;; Store mutes for application after the user's first eval creates tracks
            (reset! eo/pending-mutes (set (map keyword (or (:muted active-sess) [])))))
          500))

      ;; ── First visit / after (reset!) ─────────────────────────────────────
      (first-visit/first-visit-setup! editor/editor-view set-output!)))

  ;; ── Session save watchers ────────────────────────────────────────────────
  (add-watch audio/scheduler-state      :session-save (fn [_ _ _ _] (session/schedule-save!)))
  (add-watch fx/chain                   :session-save (fn [_ _ _ _] (session/schedule-save!)))
  (add-watch samples/active-bank-prefix :session-save (fn [_ _ _ _] (session/schedule-save!)))
  (add-watch samples/loaded-sources     :session-save (fn [_ _ _ _] (session/schedule-save!)))

  ;; Global keyboard shortcuts — capture phase so we fire before any child handler
  (.addEventListener js/document "keydown"
    (fn [e]
      (when-let [view @editor/editor-view]
        (let [target     (.-target e)
              editor-dom (.-dom view)
              cmd-dom    (el "cmd-container")
              in-editor? (.contains editor-dom target)
              in-cmd?    (and cmd-dom (.contains cmd-dom target))]
          (cond
            ;; Alt/Option+Enter — evaluate main buffer from anywhere.
            (and (.-altKey e) (= "Enter" (.-key e)))
            (do (.preventDefault e)
                (eo/evaluate! (.. view -state -doc (toString))))

            ;; Ctrl+. or F9 — run (upd) from anywhere
            (or (and (.-ctrlKey e) (= "." (.-key e)))
                (= "F9" (.-key e)))
            (do (.preventDefault e)
                (eo/evaluate! "(upd)"))

            ;; Ctrl/Cmd+A — focus editor + select all when outside both editors
            (and (or (.-metaKey e) (.-ctrlKey e))
                 (= "a" (.-key e))
                 (not in-editor?)
                 (not in-cmd?))
            (do (.preventDefault e)
                (.focus view)
                (selectAll view))))))
    true) ;; true = capture phase

  ;; Auto-load built-in visual plugins
  (-> (plugin-loading/dynamic-import! "/plugins/spectrum.js")
      (.then (fn [m]
               (let [plug (.-default m)]
                 (plugins/register! plug (make-host))
                 (mount-visual! plug))))
      (.catch (fn [e]
                (js/console.warn "[REPuLse] spectrum load failed:" e))))

  ;; Auto-load built-in effect plugins
  (plugins/register! compressor-plugin/plugin (make-host))
  (fx/add-effect!    compressor-plugin/plugin)
  (doseq [url ["/plugins/reverb.js"
               "/plugins/delay.js"
               "/plugins/filter.js"
               "/plugins/dattorro-reverb.js"
               "/plugins/chorus.js"
               "/plugins/phaser.js"
               "/plugins/tremolo.js"
               "/plugins/overdrive.js"
               "/plugins/bitcrusher.js"
               "/plugins/sidechain.js"]]
    (-> (plugin-loading/dynamic-import! url)
        (.then (fn [m]
                 (let [plug (.-default m)]
                   (plugins/register! plug (make-host))
                   (fx/add-effect! plug))))
        (.catch (fn [e]
                  (js/console.warn "[REPuLse] Effect load failed:" url e)))))

  ;; Reactive context panel + track panel
  (add-watch builtins/env-atom               ::ctx (fn [_ _ _ _] (ctx-panel/schedule-render!)))
  (add-watch fx/chain                        ::ctx (fn [_ _ _ _] (ctx-panel/schedule-render!)))
  (add-watch audio/scheduler-state           ::ctx (fn [_ _ _ _]
                                                     (ctx-panel/schedule-render!)
                                                     (timeline/render-track-panel!)))
  (add-watch samples/active-bank-prefix      ::ctx (fn [_ _ _ _] (ctx-panel/schedule-render!)))
  (add-watch samples/loaded-sources          ::ctx (fn [_ _ _ _] (ctx-panel/schedule-render!)))
  (add-watch midi/cc-mappings                ::ctx (fn [_ _ _ _] (ctx-panel/schedule-render!)))

  (ctx-panel/render-context-panel!)
  (timeline/start-playhead-raf!))

(defn reload []
  ;; Hot-reload hook: reset env so new/changed built-in bindings take effect
  ;; without a full page reload.  User defs are lost but that is expected.
  (reset! builtins/env-atom nil))
