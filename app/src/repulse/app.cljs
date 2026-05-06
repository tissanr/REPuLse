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
            [repulse.ui.snippet-submit-modal :as snippet-submit-modal]
            [repulse.snippets.preview :as snippet-preview]
            [repulse.ui.auth-button :as auth-button]
            [repulse.auth :as auth]
            [repulse.env.builtins :as builtins]
            [repulse.eval-orchestrator :as eo]
            [repulse.plugin-loading :as plugin-loading]
            [repulse.content.first-visit :as first-visit]
            [repulse.snippets :as snippets]
            [clojure.string :as str]
            ["./lisp-lang/providers.js" :refer [setBankNamesProvider setFxNamesProvider]]
            ["@codemirror/commands" :refer [selectAll]]))

;;; DOM helpers

(defn el [id] (.getElementById js/document id))

(defn- strip-console-prefix [msg]
  (str/replace (str msg) #"^=>\s*" ""))

(def ^:private header-icon-svg
  (str "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 128 128\""
       " id=\"header-icon\" class=\"header-icon\" aria-hidden=\"true\">"
       "<defs>"
       "<filter id=\"icon-glow-pink\" x=\"-40%\" y=\"-40%\" width=\"180%\" height=\"180%\">"
       "<feGaussianBlur in=\"SourceGraphic\" stdDeviation=\"2.5\" result=\"blur\"/>"
       "<feMerge><feMergeNode in=\"blur\"/><feMergeNode in=\"blur\"/><feMergeNode in=\"SourceGraphic\"/></feMerge>"
       "</filter>"
       "<filter id=\"icon-glow-cyan\" x=\"-60%\" y=\"-60%\" width=\"220%\" height=\"220%\">"
       "<feGaussianBlur in=\"SourceGraphic\" stdDeviation=\"2\" result=\"blur\"/>"
       "<feMerge><feMergeNode in=\"blur\"/><feMergeNode in=\"blur\"/><feMergeNode in=\"SourceGraphic\"/></feMerge>"
       "</filter>"
       "<clipPath id=\"icon-sweep\"><rect class=\"icon-sweep-rect\" x=\"-128\" y=\"0\" width=\"128\" height=\"128\"/></clipPath>"
       "<style>"
       "@keyframes icon-sweep{0%{transform:translateX(0)}85%{transform:translateX(128px)}86%,100%{transform:translateX(0)}}"
       "@keyframes icon-cursor-x{0%{cx:36}85%{cx:92}86%,100%{cx:36}}"
       "@keyframes icon-cursor-y{0%,32%,85%,86%,100%{cy:64}43%{cy:41}52%{cy:80}57%{cy:64}}"
       "#header-icon.playing .icon-sweep-rect{animation:icon-sweep 2.2s cubic-bezier(.4,0,.2,1) infinite}"
       "#header-icon.playing .icon-cursor{animation:icon-cursor-x 2.2s cubic-bezier(.4,0,.2,1) infinite,icon-cursor-y 2.2s cubic-bezier(.4,0,.2,1) infinite}"
       "</style>"
       "</defs>"
       "<path d=\"M 38,16 C 14,16 9,40 9,64 C 9,88 14,112 38,112\" fill=\"none\" stroke=\"#e94560\" stroke-width=\"7.5\" stroke-linecap=\"round\" filter=\"url(#icon-glow-pink)\"/>"
       "<path d=\"M 90,16 C 114,16 119,40 119,64 C 119,88 114,112 90,112\" fill=\"none\" stroke=\"#e94560\" stroke-width=\"7.5\" stroke-linecap=\"round\" filter=\"url(#icon-glow-pink)\"/>"
       "<path d=\"M 36,64 L 50,64 L 56,41 L 61,80 L 66,64 L 92,64\" fill=\"none\" stroke=\"#56b6c2\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" opacity=\"0.22\"/>"
       "<g clip-path=\"url(#icon-sweep)\" filter=\"url(#icon-glow-cyan)\">"
       "<path d=\"M 36,64 L 50,64 L 56,41 L 61,80 L 66,64 L 92,64\" fill=\"none\" stroke=\"#56b6c2\" stroke-width=\"2.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>"
       "</g>"
       "<circle class=\"icon-cursor\" cx=\"36\" cy=\"64\" r=\"2.2\" fill=\"#56b6c2\" filter=\"url(#icon-glow-cyan)\" opacity=\"0.9\"/>"
       "</svg>"))

(defn set-output! [msg status]
  (when-let [e (el "output")]
    (set! (.-textContent e) (strip-console-prefix msg))
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
      (.remove (.-classList btn) "active")))
  (when-let [icon (el "header-icon")]
    (if playing?
      (.add (.-classList icon) "playing")
      (.remove (.-classList icon) "playing"))))

(defn on-beat []
  (when-let [dot (el "playing-dot")]
    (.add (.-classList dot) "flash")
    (js/setTimeout #(.remove (.-classList dot) "flash") 80)))

;;; Stop factory

(defn make-stop-fn []
  (fn []
    (snippet-preview/stop!)
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
      (and hash (str/starts-with? hash "#v2:"))
      (let [b64  (subs hash 4)
            data (js->clj (js/JSON.parse (b64-decode b64)) :keywordize-keys true)]
        (when (= (:v data) 2) data))

      (and hash (str/starts-with? hash "#v1:"))
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

(defonce ^:private loaded-plugins (atom #{}))

(defn on-play-btn-click []
  (if (audio/playing?)
    (do (snippet-preview/stop!)
        (audio/stop!)
        (editor/clear-highlights!)
        (set-playing! false)
        (set-output! "stopped" :idle))
    (when-let [view @editor/editor-view]
      (eo/evaluate! (.. view -state -doc (toString))))))


(defn build-dom! []
  (let [app (el "app")]
    (set! (.-innerHTML app)
          (str "<header class=\"top-nav\">"
               "  <div class=\"brand-mark\">"
               header-icon-svg
               "    <h1>REP<span class=\"brand-cyan\">u</span>Lse</h1>"
               "  </div>"
               "  <div class=\"header-controls nav-actions\">"
               "    <button id=\"tap-btn\" class=\"tap-btn\">tap</button>"
               "    <button id=\"snippet-toggle-btn\" class=\"snippet-toggle-btn\">lib</button>"
               "    <button id=\"share-btn\" class=\"share-btn\">share</button>"
               "  </div>"
               "  <div class=\"nav-spacer\"></div>"
               "  <div class=\"auth-slot\"></div>"
               "  <div class=\"transport-controls\">"
               "    <button id=\"play-btn\" class=\"play-btn\">&#9654; play</button>"
               "    <div id=\"playing-dot\" class=\"playing-dot\"></div>"
               "  </div>"
               "</header>"
               "<div class=\"main-area app-shell\">"
               "  <div class=\"workbench\">"
               "    <div id=\"editor-container\" class=\"editor-container\"></div>"
               "    <div id=\"snippet-panel\" class=\"snippet-panel hidden\"></div>"
               "    <div id=\"cmd-bar\" class=\"cmd-bar console-panel\">"
               "      <div class=\"console-history\">"
               "        <div class=\"console-line console-line-out\">"
               "          <span class=\"console-prefix\">=&gt;</span>"
               "          <span id=\"output\" class=\"output\">ready &mdash; Alt+Enter or click play</span>"
               "        </div>"
               "      </div>"
               "      <div class=\"console-input-row\">"
               "        <span class=\"cmd-prompt\">&gt;</span>"
               "        <div id=\"cmd-container\" class=\"cmd-container\"></div>"
               "      </div>"
               "    </div>"
               "    <div id=\"track-panel\" class=\"track-panel\"></div>"
               "    <div id=\"plugin-panel\" class=\"plugin-panel hidden\"></div>"
               "    <footer class=\"status-bar\">"
               "      <span class=\"status-spacer\"></span>"
               "      <span class=\"hint\">Alt+Enter to eval</span>"
               "    </footer>"
               "  </div>"
               "  <div id=\"context-panel\" class=\"context-panel\"></div>"
               "</div>"
               )))
  (.addEventListener (el "play-btn")           "click" on-play-btn-click)
  (.addEventListener (el "tap-btn")            "click" (fn [] (eo/evaluate! "(tap!)")))
  (.addEventListener (el "share-btn")          "click" share!)
  (.addEventListener (el "snippet-toggle-btn") "click" snippet-panel/toggle-panel!))

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
                  raw-val    (js/parseFloat (.-value target))
                  values     (some-> (.. target -dataset -values) (.split ","))
                  new-val    (if values
                               (js/parseFloat (aget values (int raw-val)))
                               raw-val)
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
                (eo/slider-patch-and-eval! track-name param-name new-val)))))))
    (.addEventListener panel "change"
      (fn [^js e]
        (let [target (.-target e)]
          (when (and (= "SELECT" (.-tagName target))
                     (.contains (.-classList target) "ctx-select"))
            (let [fx-name    (.. target -dataset -fx)
                  track-name (.. target -dataset -track)
                  param-name (.. target -dataset -param)
                  new-val    (.-value target)]
              (cond
                (and (seq fx-name) (seq track-name))
                (eo/per-track-fx-select-patch-and-eval! track-name fx-name param-name new-val)

                (seq fx-name)
                (eo/fx-select-patch-and-eval! fx-name param-name new-val)))))))))

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
  (auth/init-auth! :on-change-fn (fn [session]
                                    (auth-button/render-auth-btn!)
                                    ;; Re-render snippet panel toolbar so "share" btn appears/disappears
                                    (when @snippet-panel/visible?
                                      (snippet-panel/show-panel!))
                                    ;; Hydrate ratings atom so stars persist across reloads
                                    (when session
                                      (snippets/load-ratings!))))
  (auth-button/init!)
  (snippet-panel/init!)
  (snippet-submit-modal/init!)
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
        ;; Store mutes for application after the user's first eval creates tracks.
        ;; FX params are intentionally NOT restored from localStorage — the buffer's
        ;; source code is the sole source of truth for which effects are active.
        (js/setTimeout
          (fn []
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
  (when-not (contains? @loaded-plugins "spectrum")
    (-> (plugin-loading/dynamic-import! "/plugins/spectrum.js")
        (.then (fn [m]
                 (let [plug (.-default m)]
                   (plugins/register! plug (make-host))
                   (mount-visual! plug)
                   (swap! loaded-plugins conj "spectrum"))))
        (.catch (fn [e]
                  (js/console.warn "[REPuLse] spectrum load failed:" e)))))

  ;; Auto-load built-in effect plugins
  (when-not (contains? @loaded-plugins "compressor")
    (plugins/register! compressor-plugin/plugin (make-host))
    (fx/add-effect!    compressor-plugin/plugin)
    (swap! loaded-plugins conj "compressor"))

  (doseq [url ["/plugins/reverb.js"
               "/plugins/delay.js"
               "/plugins/filter.js"
               "/plugins/dattorro-reverb.js"
               "/plugins/chorus.js"
               "/plugins/phaser.js"
               "/plugins/tremolo.js"
               "/plugins/overdrive.js"
               "/plugins/bitcrusher.js"
               "/plugins/sidechain.js"
               "/plugins/distort.js"
               "/plugins/amp-sim.js"
               "/plugins/waveshape.js"]]
    (when-not (contains? @loaded-plugins url)
      (-> (plugin-loading/dynamic-import! url)
          (.then (fn [m]
                   (let [plug (.-default m)]
                     (plugins/register! plug (make-host))
                     (fx/add-effect! plug)
                     (swap! loaded-plugins conj url))))
          (.catch (fn [e]
                    (js/console.warn "[REPuLse] Effect load failed:" url e))))))

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
  (timeline/render-track-panel!)
  (timeline/start-playhead-raf!))

(defn reload []
  ;; Hot-reload hook: reset env so new/changed built-in bindings take effect
  ;; without a full page reload.  User defs are lost but that is expected.
  (reset! builtins/env-atom nil))
