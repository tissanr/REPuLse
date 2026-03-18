(ns repulse.app
  (:require [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.core :as core]
            [repulse.audio :as audio]
            [repulse.samples :as samples]
            [repulse.plugins :as plugins]
            [repulse.fx :as fx]
            [repulse.plugins.compressor :as compressor-plugin]
            [clojure.string :as cstr]
            ["@codemirror/view" :refer [EditorView Decoration keymap lineNumbers]]
            ["@codemirror/state" :refer [EditorState StateEffect StateField]]
            ["@codemirror/commands" :refer [defaultKeymap historyKeymap history selectAll]]
            ["@codemirror/language" :refer [bracketMatching]]
            ["@codemirror/theme-one-dark" :refer [oneDark]]
            ["./lisp-lang/index.js" :refer [lispLanguage]]
            ["./lisp-lang/providers.js" :refer [setBankNamesProvider setFxNamesProvider]]))

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

;;; Active code highlighting — CodeMirror 6 decoration infrastructure

;; A StateEffect that replaces the entire active-highlights DecorationSet.
(def set-highlights-effect (.define StateEffect))

;; A StateField that holds the current DecorationSet of active event highlights.
(def highlights-field
  (.define StateField
    #js {:create   (fn [_] (.-none Decoration))
         :update   (fn [decs tr]
                     (let [mapped (.map decs (.-changes tr))]
                       (if-let [eff (some #(when (.is % set-highlights-effect) %)
                                          (array-seq (.-effects tr)))]
                         (.-value eff)
                         mapped)))
         :provide  (fn [f] (.from EditorView.decorations f))}))

;; The CSS class applied to active tokens.
(def active-mark (.mark Decoration #js {:class "active-event"}))

;; Atom holding currently active source ranges: [{:from N :to N} ...]
(defonce active-ranges (atom []))

(defonce editor-view (atom nil))
(defonce cmd-view    (atom nil))

(defn- rebuild-decorations! [view]
  (let [ranges  (sort-by :from @active-ranges)
        range-objs (keep (fn [{:keys [from to]}]
                           (try (.range active-mark from to)
                                (catch :default _ nil)))
                         ranges)
        deco-set (if (seq range-objs)
                   (.set Decoration (clj->js range-objs))
                   (.-none Decoration))]
    (.dispatch view #js {:effects #js [(.of set-highlights-effect deco-set)]})))

(defn highlight-range! [{:keys [from to]}]
  (when-let [view @editor-view]
    (let [doc-len (.. view -state -doc -length)
          from'   (min from doc-len)
          to'     (min to   doc-len)]
      (when (< from' to')
        (swap! active-ranges conj {:from from' :to to'})
        (rebuild-decorations! view)
        ;; Remove this range after 120 ms
        (js/setTimeout
          (fn []
            (swap! active-ranges
                   (fn [rs]
                     (filterv #(not (and (= (:from %) from') (= (:to %) to'))) rs)))
            (when-let [v @editor-view]
              (rebuild-decorations! v)))
          120)))))

(defn clear-highlights! []
  (reset! active-ranges [])
  (when-let [view @editor-view]
    (rebuild-decorations! view)))

;;; Track timeline rendering

(defn- render-track-row [track-name pattern cycle muted?]
  (let [sp   {:start [cycle 1] :end [(inc cycle) 1]}
        evs  (try (core/query pattern sp) (catch :default _ []))
        bars (mapv (fn [ev]
                     (let [part (:part ev)
                           pos  (- (core/rat->float (:start part)) cycle)
                           dur  (- (core/rat->float (:end part))
                                   (core/rat->float (:start part)))]
                       {:pos pos :dur (max 0.01 dur)}))
                   evs)
        kw-name (name track-name)]
    (str "<div class=\"track-row" (when muted? " track-muted") "\">"
         "<span class=\"track-name\" onclick=\"window._repulseMuteToggle('" kw-name "')\">"
         kw-name
         "</span>"
         "<div class=\"track-timeline-wrap\">"
         "<svg class=\"track-timeline\" viewBox=\"0 0 100 10\" preserveAspectRatio=\"none\">"
         (apply str (map (fn [{:keys [pos dur]}]
                           (str "<rect x=\"" (* pos 100) "\" y=\"0.5\" "
                                "width=\"" (* dur 100) "\" height=\"9\" "
                                "rx=\"0.5\"/>"))
                         bars))
         "</svg>"
         "<div class=\"track-playhead\" id=\"ph-" kw-name "\"></div>"
         "</div>"
         "</div>")))

(defn- render-track-panel! []
  (when-let [panel (el "track-panel")]
    (let [{:keys [tracks muted playing? cycle]} @audio/scheduler-state]
      (if (or (not playing?) (empty? tracks))
        (set! (.-innerHTML panel) "")
        (set! (.-innerHTML panel)
              (apply str (map (fn [[track-name pattern]]
                                (render-track-row track-name pattern cycle
                                                  (contains? muted track-name)))
                              tracks)))))))

;;; Playhead RAF loop

(defn- start-playhead-raf! []
  (letfn [(frame []
    (let [ac    (when (audio/playing?) @audio/ctx)
          state @audio/scheduler-state]
      (when ac
        (let [now      (.-currentTime ac)
              cyc-dur  (:cycle-dur state)
              frac     (mod (/ now cyc-dur) 1.0)
              pct      (str (* frac 100) "%")]
          (doseq [ph (array-seq (.querySelectorAll js/document ".track-playhead"))]
            (set! (.. ph -style -left) pct)))))
    (js/requestAnimationFrame frame))]
    (js/requestAnimationFrame frame)))

;;; Session persistence

(defn- encode-session []
  (let [bpm    (js/Math.round (audio/get-bpm))
        editor (when-let [v @editor-view]
                 (.. v -state -doc (toString)))
        obj    (clj->js {:v 1 :bpm bpm :editor (or editor "")})]
    (str "#v1:" (js/btoa (js/JSON.stringify obj)))))

(defn- decode-session [hash]
  (try
    (when (and hash (cstr/starts-with? hash "#v1:"))
      (let [b64 (subs hash 4)
            obj (js->clj (js/JSON.parse (js/atob b64)))]
        obj))
    (catch :default _ nil)))

(defn- restore-session! [session]
  (when session
    (when-let [bpm (get session "bpm")]
      (audio/set-bpm! bpm))
    (when-let [ed (get session "editor")]
      (when-let [view @editor-view]
        (.dispatch view
                   #js {:changes #js {:from   0
                                      :to     (.. view -state -doc -length)
                                      :insert ed}})))))

(defn share! []
  (let [session (encode-session)
        url     (str (.-origin js/location) (.-pathname js/location) session)]
    (if (.-clipboard js/navigator)
      (-> (.writeText (.-clipboard js/navigator) url)
          (.then (fn [] (set-output! (str "URL copied to clipboard") :success)))
          (.catch (fn [_] (js/prompt "Copy this URL:" url))))
      (js/prompt "Copy this URL:" url))))

;;; Environment — created once, reused across evaluations

(defonce env-atom
  (atom nil))

;; Keys present in the initial env — used to filter built-ins from user defs
(defonce ^:private builtin-names (atom #{}))

;;; Plugin support

(defn make-host []
  #js {:audioCtx    (audio/get-ctx)
       :analyser    @audio/analyser-node
       :masterGain  @audio/master-gain
       :sampleRate  (.-sampleRate (audio/get-ctx))
       :version     "1.0.0"
       :registerLisp (fn [name f]
                       (swap! env-atom assoc name f))})

(defn mount-visual! [plugin]
  (let [panel (el "plugin-panel")]
    (.mount plugin panel)
    (.remove (.-classList panel) "hidden")))

(defn make-stop-fn []
  (fn []
    (audio/stop!)
    (clear-highlights!)
    (set-playing! false)
    (set-output! "stopped" :idle)))

(defn ensure-env! []
  (when (nil? @env-atom)
    (samples/init!)
    (reset! env-atom
            (assoc (leval/make-env (make-stop-fn) audio/set-bpm!)
                   ;; --- Multi-track ---
                   "play"
                   (fn [track-name pat]
                     (let [name' (leval/unwrap track-name)
                           pat'  (leval/unwrap pat)]
                       (if (and (map? pat') (fn? (:query pat')))
                         (do
                           (audio/play-track! name' pat' on-beat highlight-range!)
                           (set-playing! true)
                           (str "=> track :" (name name') " playing"))
                         "Error: second argument to play must be a pattern")))
                   "mute!"
                   (fn [track-name]
                     (let [name' (leval/unwrap track-name)]
                       (audio/mute-track! name')
                       (str "=> muted :" (name name'))))
                   "unmute!"
                   (fn [track-name]
                     (let [name' (leval/unwrap track-name)]
                       (audio/unmute-track! name')
                       (str "=> unmuted :" (name name'))))
                   "solo!"
                   (fn [track-name]
                     (let [name' (leval/unwrap track-name)]
                       (audio/solo-track! name')
                       (str "=> solo :" (name name'))))
                   "clear!"
                   (fn
                     ([]
                      (audio/stop!)
                      (clear-highlights!)
                      (set-playing! false)
                      "=> cleared all tracks")
                     ([track-name]
                      (let [name' (leval/unwrap track-name)]
                        (audio/clear-track! name')
                        (when (not (:playing? @audio/scheduler-state))
                          (set-playing! false))
                        (str "=> cleared :" (name name')))))
                   "tracks"
                   (fn []
                     (let [ks (keys (:tracks @audio/scheduler-state))]
                       (if (seq ks)
                         (str "=> (" (cstr/join " " (map #(str ":" (name %)) ks)) ")")
                         "=> ()")))
                   ;; --- Hot-swap update ---
                   "upd"
                   (fn []
                     (when-let [view @editor-view]
                       (let [code   (.. view -state -doc (toString))
                             env    (assoc @env-atom "stop" (make-stop-fn))
                             result (lisp/eval-string code env)]
                         (if-let [err (:error result)]
                           (do (clear-highlights!)
                               (set-output! (str "Error: " err) :error))
                           (let [val (:result result)]
                             (cond
                               (and (map? val) (fn? (:query val)))
                               (do (audio/play-track! :_ val on-beat highlight-range!)
                                   (set-playing! true)
                                   (set-output! "updated" :success))
                               (nil? val) nil
                               (string? val) (set-output! val :success)
                               :else (set-output! (str "=> " (pr-str val)) :success)))))))
                   ;; --- Tap tempo ---
                   "tap!"
                   (fn []
                     (if-let [bpm (audio/tap!)]
                       (str "=> " (.toFixed bpm 1) " BPM")
                       "=> tap again…"))
                   ;; --- MIDI clock ---
                   "midi-sync!"
                   (fn [enabled?]
                     (let [on? (leval/unwrap enabled?)]
                       (audio/set-midi-sync! on?)
                       (str "=> MIDI sync " (if on? "enabled" "disabled"))))
                   ;; --- Samples ---
                   "samples!"
                   (fn [url]
                     (let [url' (leval/unwrap url)]
                       (samples/load-external! url')
                       (str "loading " url' "…")))
                   "sample-banks"
                   (fn [] (samples/format-banks))
                   ;; --- Plugins ---
                   "load-plugin"
                   (fn [url]
                     (let [url' (leval/unwrap url)]
                       (-> (js* "import(~{})" url')
                           (.then (fn [m]
                                    (let [plug (.-default m)]
                                      (when (= "effect" (.-type plug))
                                        (fx/remove-effect! (.-name plug)))
                                      (plugins/register! plug (make-host))
                                      (if (= "visual" (.-type plug))
                                        (mount-visual! plug)
                                        (when (= "effect" (.-type plug))
                                          (fx/add-effect! plug))))))
                           (.catch (fn [e]
                                     (js/console.warn "[REPuLse] Plugin load failed:" e))))
                       nil))
                   "bank"
                   (fn [prefix]
                     (samples/set-bank-prefix! (leval/unwrap prefix))
                     (str "bank: " (if prefix (name (leval/unwrap prefix)) "cleared")))
                   "fx"
                   (fn [& args]
                     (let [args'     (mapv leval/unwrap args)
                           first-arg (first args')]
                       (cond
                         (= first-arg :off)
                         (fx/bypass! (cljs.core/name (second args')) true)

                         (= first-arg :on)
                         (fx/bypass! (cljs.core/name (second args')) false)

                         (= first-arg :remove)
                         (fx/remove-effect! (cljs.core/name (second args')))

                         :else
                         (let [effect-name (cljs.core/name first-arg)
                               rest-args   (rest args')]
                           (if (keyword? (first rest-args))
                             (doseq [[k v] (partition 2 rest-args)]
                               (fx/set-param! effect-name (cljs.core/name k) v))
                             (fx/set-param! effect-name "value" (first rest-args))))))
                     nil)))
    ;; Snapshot built-in names so render-context-panel! can filter them out
    (reset! builtin-names (set (keys @env-atom)))))

;;; Evaluation

(defn evaluate! [code]
  (ensure-env!)
  ;; Keep stop fn up to date (in case env was reset)
  (let [env (assoc @env-atom "stop" (make-stop-fn))
        result (lisp/eval-string code env)]
    (if-let [err (:error result)]
      (do
        (clear-highlights!)
        (set-output! (str "Error: " err) :error))
      (let [val (:result result)]
        (cond
          ;; Pattern — start playing (legacy single-pattern mode)
          (and (map? val) (fn? (:query val)))
          (do
            (audio/stop!)
            (clear-highlights!)
            (audio/start! val on-beat highlight-range!)
            (set-playing! true)
            (set-output! "playing pattern — Ctrl+Enter to re-evaluate, (stop) to stop" :success))

          ;; stop fn was called directly — handled inside stop fn
          (nil? val)
          nil

          ;; Pre-formatted string (e.g. sample-banks, track ops) — display as-is
          (string? val)
          (set-output! val :success)

          :else
          (set-output! (str "=> " (pr-str val)) :success))))))

;;; Context panel

(defn- infer-type [v]
  (cond
    (and (map? v) (fn? (:query v))) "pattern"
    (fn? v)                          "fn"
    (number? v)                      "number"
    (string? v)                      "string"
    (keyword? v)                     "keyword"
    :else                            "value"))

(defn- fmt-pv [v]
  (if (number? v)
    (if (== v (Math/round v)) (str (int v)) (.toFixed v 2))
    (str v)))

(defn- render-context-panel! []
  (when-let [status-el (el "ctx-status")]
    (let [bpm     (Math/round (/ 240.0 (:cycle-dur @audio/scheduler-state)))
          playing? (audio/playing?)]
      (set! (.-innerHTML status-el)
            (str "<span class=\"ctx-bpm\">" bpm " BPM</span>"
                 (when-let [pfx @samples/active-bank-prefix]
                   (str "<span class=\"ctx-bank\">" pfx "</span>"))
                 "<span class=\"" (if playing? "ctx-playing" "ctx-stopped") "\">"
                 (if playing? "&#9679; playing" "&#9675; stopped")
                 "</span>"))))
  (when-let [bindings-el (el "ctx-bindings")]
    (let [env       (or @env-atom {})
          builtins  @builtin-names
          user-defs (sort (remove builtins (keys env)))]
      (set! (.-innerHTML bindings-el)
            (str "<div class=\"ctx-section-title\">Bindings</div>"
                 (if (empty? user-defs)
                   "<div class=\"ctx-empty\">—</div>"
                   (apply str
                          (map (fn [k]
                                 (str "<div class=\"ctx-row\">"
                                      "<span class=\"ctx-name\">" k "</span>"
                                      "<span class=\"ctx-type\">" (infer-type (get env k)) "</span>"
                                      "</div>"))
                               user-defs)))))))
  (when-let [effects-el (el "ctx-effects")]
    (let [chain @fx/chain]
      (set! (.-innerHTML effects-el)
            (str "<div class=\"ctx-section-title\">Effects</div>"
                 (if (empty? chain)
                   "<div class=\"ctx-empty\">—</div>"
                   (apply str
                          (map (fn [{:keys [name plugin bypassed?]}]
                                 (let [params    (when plugin
                                                   (try (js->clj (.getParams ^js plugin))
                                                        (catch :default _ {})))
                                       first-kv  (first (seq params))
                                       param-str (when first-kv
                                                   (str (first first-kv) ": "
                                                        (fmt-pv (second first-kv))))]
                                   (str "<div class=\"ctx-row\">"
                                        "<span class=\"ctx-name\">" name "</span>"
                                        (cond
                                          bypassed?  "<span class=\"ctx-bypass\">off</span>"
                                          param-str  (str "<span class=\"ctx-param\">" param-str "</span>")
                                          :else      "")
                                        "</div>")))
                               chain))))))))

;;; CodeMirror editor

(def ^:private storage-key "repulse-editor")
(def ^:private bpm-storage-key "repulse-bpm")

(defn- load-editor-content [fallback]
  (or (try (.getItem js/localStorage storage-key) (catch :default _ nil))
      fallback))

(defn- load-saved-bpm []
  (try
    (when-let [s (.getItem js/localStorage bpm-storage-key)]
      (let [n (js/parseFloat s)]
        (when-not (js/isNaN n) n)))
    (catch :default _ nil)))

(def ^:private save-listener
  (.of EditorView.updateListener
       (fn [^js update]
         (when (.-docChanged update)
           (try (.setItem js/localStorage storage-key
                          (.. update -state -doc (toString)))
                (catch :default _))))))

(defn make-cmd-editor 
  "Single-line CodeMirror editor for the command bar. Enter evaluates + clears."
  [container]
  (let [clear-view! (fn [view]
                      (.dispatch view
                                 #js {:changes #js {:from 0
                                                    :to   (.. view -state -doc -length)
                                                    :insert ""}})
                      true)
        eval-cmd    (fn [view]
                      (let [code (.. view -state -doc (toString))]
                        (when (seq (cstr/trim code))
                          (evaluate! code)
                          (clear-view! view)))
                      true)
        clear+return! (fn [view]
                        (clear-view! view)
                        (when-let [ev @editor-view] (.focus ev))
                        true)
        extensions  #js [oneDark
                         lispLanguage
                         (.of keymap #js [#js {:key "Mod-a"  :run selectAll}
                                          #js {:key "Enter"  :run eval-cmd}
                                          #js {:key "Escape" :run clear+return!}])]
        state (.. EditorState (create #js {:doc "" :extensions extensions}))
        view  (EditorView. #js {:state state :parent container})]
    view))

(defn make-editor [container initial-value on-eval]
  (let [eval-cmd (fn [view]
                   (on-eval (.. view -state -doc (toString)))
                   true)
        eval-binding    #js {:key "Mod-Enter" :run eval-cmd}
        escape-binding  #js {:key "Escape"
                             :run (fn [_]
                                    (when-let [cv @cmd-view] (.focus cv))
                                    true)}
        extensions #js [(history)
                        (lineNumbers)
                        oneDark
                        lispLanguage
                        (bracketMatching)
                        highlights-field
                        save-listener
                        (.-lineWrapping EditorView)
                        (.of keymap (.concat
                                     #js [escape-binding]
                                     (clj->js defaultKeymap)
                                     (clj->js historyKeymap)
                                     #js [eval-binding]))]
        state (.. EditorState
                  (create #js {:doc (load-editor-content initial-value)
                               :extensions extensions}))
        view (EditorView. #js {:state state :parent container})]
    view))

;;; App bootstrap

(defn on-play-btn-click []
  (if (audio/playing?)
    ;; Stop
    (do (audio/stop!)
        (clear-highlights!)
        (set-playing! false)
        (set-output! "stopped" :idle))
    ;; Evaluate current editor content and start
    (when-let [view @editor-view]
      (evaluate! (.. view -state -doc (toString))))))

(defn build-dom! []
  (let [app (el "app")]
    (set! (.-innerHTML app)
          (str "<header>"
               "  <h1>REPuLse</h1>"
               "  <div class=\"header-controls\">"
               "    <button id=\"tap-btn\" class=\"tap-btn\">tap</button>"
               "    <button id=\"share-btn\" class=\"share-btn\">share</button>"
               "    <button id=\"play-btn\" class=\"play-btn\">&#9654; play</button>"
               "    <div id=\"playing-dot\" class=\"playing-dot\"></div>"
               "  </div>"
               "</header>"
               "<div class=\"main-area\">"
               "  <div id=\"editor-container\" class=\"editor-container\"></div>"
               "  <div id=\"context-panel\" class=\"context-panel\">"
               "    <div id=\"ctx-status\" class=\"ctx-status\"></div>"
               "    <div id=\"ctx-bindings\" class=\"ctx-section\"></div>"
               "    <div id=\"ctx-effects\" class=\"ctx-section\"></div>"
               "  </div>"
               "</div>"
               "<div id=\"cmd-bar\" class=\"cmd-bar\">"
               "  <span class=\"cmd-prompt\">&gt;</span>"
               "  <div id=\"cmd-container\" class=\"cmd-container\"></div>"
               "</div>"
               "<div id=\"track-panel\" class=\"track-panel\"></div>"
               "<div id=\"plugin-panel\" class=\"plugin-panel hidden\"></div>"
               "<footer>"
               "  <span id=\"output\" class=\"output\">ready &mdash; Ctrl+Enter or click play</span>"
               "  <span class=\"hint\">Ctrl+Enter to eval</span>"
               "</footer>")))
  (.addEventListener (el "play-btn")  "click" on-play-btn-click)
  (.addEventListener (el "tap-btn")   "click" (fn [] (evaluate! "(tap!)")))
  (.addEventListener (el "share-btn") "click" share!))

(defn init []
  (build-dom!)
  (ensure-env!)
  (setBankNamesProvider (fn [] (clj->js (samples/bank-names))))
  (setFxNamesProvider   (fn [] (clj->js (mapv :name @fx/chain))))

  ;; Restore saved BPM from localStorage
  (when-let [bpm (load-saved-bpm)]
    (audio/set-bpm! bpm))

  ;; Restore session from URL hash if present
  (let [hash (.-hash js/location)]
    (when-let [session (decode-session hash)]
      ;; Remove hash from URL without reload
      (.replaceState js/history nil nil (.-pathname js/location))
      ;; Editor isn't mounted yet — defer restore
      (js/setTimeout #(restore-session! session) 0)))

  ;; Register global mute-toggle for track panel onclick
  (set! (.-_repulseMuteToggle js/window)
        (fn [track-name-str]
          (let [kw (keyword track-name-str)]
            (if (contains? (:muted @audio/scheduler-state) kw)
              (audio/unmute-track! kw)
              (audio/mute-track! kw)))))

  (reset! cmd-view (make-cmd-editor (el "cmd-container")))

  (let [container (el "editor-container")
        view (make-editor container "(seq :bd :sd :bd :sd)" evaluate!)]
    (reset! editor-view view)
    (.focus view))

  ;; Global Cmd/Ctrl+A — focus main editor and select all when pressed
  ;; outside both the main editor and the command bar.
  (.addEventListener js/document "keydown"
    (fn [e]
      (when (and (or (.-metaKey e) (.-ctrlKey e))
                 (= "a" (.-key e)))
        (when-let [view @editor-view]
          (let [target     (.-target e)
                editor-dom (.-dom view)
                cmd-dom    (el "cmd-container")]
            (when-not (or (.contains editor-dom target)
                          (and cmd-dom (.contains cmd-dom target)))
              (.preventDefault e)
              (.focus view)
              (selectAll view)))))))

  ;; Auto-load built-in visual plugins
  (-> (js* "import('/plugins/oscilloscope.js')")
      (.then (fn [m]
               (let [plug (.-default m)]
                 (plugins/register! plug (make-host))
                 (mount-visual! plug))))
      (.catch (fn [e]
                (js/console.warn "[REPuLse] oscilloscope load failed:" e))))

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
               "/plugins/bitcrusher.js"]]
    (-> (js* "import(~{})" url)
        (.then (fn [m]
                 (let [plug (.-default m)]
                   (plugins/register! plug (make-host))
                   (fx/add-effect! plug))))
        (.catch (fn [e]
                  (js/console.warn "[REPuLse] Effect load failed:" url e)))))

  ;; Reactive context panel + track panel
  (add-watch env-atom               ::ctx (fn [_ _ _ _] (render-context-panel!)))
  (add-watch fx/chain               ::ctx (fn [_ _ _ _] (render-context-panel!)))
  (add-watch audio/scheduler-state  ::ctx (fn [_ _ _ _]
                                            (render-context-panel!)
                                            (render-track-panel!)
                                            ;; Persist BPM
                                            (try
                                              (.setItem js/localStorage bpm-storage-key
                                                        (str (audio/get-bpm)))
                                              (catch :default _))))
  (add-watch samples/active-bank-prefix ::ctx (fn [_ _ _ _] (render-context-panel!)))

  (render-context-panel!)
  (start-playhead-raf!))

(defn reload []
  ;; Hot-reload hook: re-attach evaluate! without rebuilding the DOM
  )
