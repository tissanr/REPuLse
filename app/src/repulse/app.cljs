(ns repulse.app
  (:require [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.audio :as audio]
            [repulse.samples :as samples]
            [repulse.plugins :as plugins]
            [repulse.fx :as fx]
            [repulse.plugins.compressor :as compressor-plugin]
            ["@codemirror/view" :refer [EditorView Decoration keymap lineNumbers]]
            ["@codemirror/state" :refer [EditorState StateEffect StateField]]
            ["@codemirror/commands" :refer [defaultKeymap historyKeymap history]]
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

;;; Environment — created once, reused across evaluations

(defonce env-atom
  (atom nil))

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
                   "samples!"
                   (fn [url]
                     (let [url' (leval/unwrap url)]
                       (samples/load-external! url')
                       (str "loading " url' "…")))
                   "sample-banks"
                   (fn [] (samples/format-banks))
                   "load-plugin"
                   (fn [url]
                     (let [url' (leval/unwrap url)]
                       (-> (js* "import(~{})" url')
                           (.then (fn [m]
                                    (let [plug (.-default m)]
                                      ;; If replacing an existing effect, remove it first
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
                             ;; Named params: (fx :reverb :wet 0.6 :dry 0.7)
                             (doseq [[k v] (partition 2 rest-args)]
                               (fx/set-param! effect-name (cljs.core/name k) v))
                             ;; Positional: (fx :reverb 0.4) → set "value"
                             (fx/set-param! effect-name "value" (first rest-args))))))
                     nil)))))

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
          ;; Pattern — start playing
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

          ;; Pre-formatted string (e.g. sample-banks, samples!) — display as-is
          (string? val)
          (set-output! val :success)

          :else
          (set-output! (str "=> " (pr-str val)) :success))))))

;;; CodeMirror editor

(defn make-editor [container initial-value on-eval]
  (let [eval-cmd (fn [view]
                   (on-eval (.. view -state -doc (toString)))
                   true)
        eval-binding #js {:key "Mod-Enter" :run eval-cmd}
        extensions #js [(history)
                        (lineNumbers)
                        oneDark
                        lispLanguage
                        (bracketMatching)
                        highlights-field
                        (.-lineWrapping EditorView)
                        (.of keymap (.concat
                                     (clj->js defaultKeymap)
                                     (clj->js historyKeymap)
                                     #js [eval-binding]))]
        state (.. EditorState
                  (create #js {:doc initial-value
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
               "    <button id=\"play-btn\" class=\"play-btn\">&#9654; play</button>"
               "    <div id=\"playing-dot\" class=\"playing-dot\"></div>"
               "  </div>"
               "</header>"
               "<div id=\"editor-container\" class=\"editor-container\"></div>"
               "<div id=\"plugin-panel\" class=\"plugin-panel hidden\"></div>"
               "<footer>"
               "  <span id=\"output\" class=\"output\">ready &mdash; Ctrl+Enter or click play</span>"
               "  <span class=\"hint\">Ctrl+Enter to eval</span>"
               "</footer>")))
  (.addEventListener (el "play-btn") "click" on-play-btn-click))

(defn init []
  (build-dom!)
  (ensure-env!)
  (setBankNamesProvider (fn [] (clj->js (samples/bank-names))))
  (setFxNamesProvider   (fn [] (clj->js (mapv :name @fx/chain))))
  (let [container (el "editor-container")
        view (make-editor container "(seq :bd :sd :bd :sd)" evaluate!)]
    (reset! editor-view view)
    (.focus view))
  ;; Auto-load built-in visual plugins
  (-> (js* "import('/plugins/oscilloscope.js')")
      (.then (fn [m]
               (let [plug (.-default m)]
                 (plugins/register! plug (make-host))
                 (mount-visual! plug))))
      (.catch (fn [e]
                (js/console.warn "[REPuLse] oscilloscope load failed:" e))))
  ;; Auto-load built-in effect plugins (wet mix starts at 0 — silent until (fx ...) is called)
  ;; CLJS compressor registered synchronously — no dynamic import needed
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
                  (js/console.warn "[REPuLse] Effect load failed:" url e))))))

(defn reload []
  ;; Hot-reload hook: re-attach evaluate! without rebuilding the DOM
  ;; The editor persists; we just need to make sure the env is fresh.
  )
