(ns repulse.ui.context-panel
  "Context panel rendering — status, tracks, FX, MIDI, buses, sources, bindings.
   Also owns slider configuration constants and the schedule-render! debounce.
   Responsibility: build and refresh the sidebar context panel DOM.
   Exports: render-context-panel!, schedule-render!, slider-active?,
            FX-SLIDER-PARAMS, FX-PRIMARY-PARAM, SLIDER-PARAMS."
  (:require [repulse.core :as core]
            [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.midi :as midi]
            [repulse.samples :as samples]
            [repulse.bus :as bus]
            [repulse.env.builtins :as builtins]))

(defn- el [id] (.getElementById js/document id))

;;; Type / value helpers

(defn- infer-type [v]
  (cond
    (core/pattern? v) "pattern"
    (fn? v)          "fn"
    (number? v)      "number"
    (string? v)      "string"
    (keyword? v)     "keyword"
    :else            "value"))

(defn- fmt-pv [v]
  (if (number? v)
    (if (== v (Math/round v)) (str (int v)) (.toFixed v 2))
    (str v)))

;;; Slider configuration constants

(def ^:private TRACK-PARAM-KEYS
  [:amp :pan :decay :attack :release :synth :bank :rate :begin :end])

;; Parameters that get interactive sliders, with their range config
(def SLIDER-PARAMS
  {:amp     {:min 0    :max 1   :step 0.01}
   :pan     {:min -1   :max 1   :step 0.01}
   :decay   {:min 0    :max 4   :step 0.01}
   :attack  {:min 0    :max 2   :step 0.001}
   :release {:min 0    :max 4   :step 0.01}
   :rate    {:min 0.1  :max 4   :step 0.01}
   :begin   {:min 0    :max 1   :step 0.01}
   :end     {:min 0    :max 1   :step 0.01}})

;; FX slider config: {effect-name {param-name {:min :max :step}}}
(def FX-SLIDER-PARAMS
  {"reverb"          {"wet"      {:min 0   :max 1    :step 0.01}}
   "dattorro-reverb" {"wet"      {:min 0   :max 1    :step 0.01}}
   "delay"           {"time"     {:min 0   :max 2    :step 0.01}
                      "feedback" {:min 0   :max 0.95 :step 0.01}
                      "wet"      {:min 0   :max 1    :step 0.01}}
   "filter"          {"freq"     {:min 20  :max 8000 :step 1}
                      "q"        {:min 0.1 :max 20   :step 0.1}}
   "chorus"          {"wet"      {:min 0   :max 1    :step 0.01}
                      "rate"     {:min 0.1 :max 10   :step 0.1}}
   "phaser"          {"wet"      {:min 0   :max 1    :step 0.01}
                      "rate"     {:min 0.1 :max 10   :step 0.1}}
   "tremolo"         {"depth"    {:min 0   :max 1    :step 0.01}
                      "rate"     {:min 0.1 :max 20   :step 0.1}}
   "overdrive"       {"drive"    {:min 0   :max 1    :step 0.01}}
   "bitcrusher"      {"wet"       {:min 0    :max 1   :step 0.01}}
   "sidechain"       {"amount"    {:min 0    :max 1   :step 0.01}}
   "compressor"      {"wet"       {:min 0    :max 1   :step 0.01}
                      "threshold" {:min -60  :max 0   :step 0.5}
                      "ratio"     {:min 1    :max 20  :step 0.5}
                      "attack"    {:min 0    :max 1   :step 0.001}
                      "release"   {:min 0    :max 1   :step 0.01}
                      "knee"      {:min 0    :max 40  :step 0.5}}})

;; Which getParams key corresponds to the positional (fx :name NUMBER) form
(def FX-PRIMARY-PARAM
  {"reverb"          "wet"
   "dattorro-reverb" "wet"
   "delay"           "time"
   "filter"          "freq"
   "chorus"          "wet"
   "phaser"          "wet"
   "tremolo"         "depth"
   "overdrive"       "drive"
   "bitcrusher"      "wet"
   "sidechain"       "amount"
   "compressor"      "wet"})

;;; Track parameter extraction

(defn- extract-track-params
  "Query cycle 0 of a pattern and collect the first value for each known param key."
  [pattern]
  (try
    (let [events (core/query pattern {:start [0 1] :end [1 1]})
          maps   (filter map? (map :value events))]
      (reduce (fn [acc m]
                (reduce (fn [a k]
                          (if (and (contains? m k) (not (contains? a k)))
                            (assoc a k (get m k))
                            a))
                        acc TRACK-PARAM-KEYS))
              {} maps))
    (catch :default _ {})))

;;; Slider HTML renderers

(defn- render-track-slider [track-name param-key value]
  (when-let [{:keys [min max step]} (get SLIDER-PARAMS param-key)]
    (let [tn  (name track-name)
          pn  (name param-key)]
      (str "<div class=\"ctx-slider-row\">"
           "<label class=\"ctx-param-key\">" pn "</label>"
           "<input type=\"range\" class=\"ctx-slider\""
           " data-track=\"" tn "\""
           " data-param=\"" pn "\""
           " min=\"" min "\" max=\"" max "\" step=\"" step "\""
           " value=\"" value "\">"
           "<span class=\"ctx-param-val\">" (fmt-pv value) "</span>"
           "</div>"))))

(defn- render-fx-slider [effect-name param-name value]
  (when-let [{:keys [min max step]} (get-in FX-SLIDER-PARAMS [effect-name param-name])]
    (str "<div class=\"ctx-slider-row\">"
         "<label class=\"ctx-param-key\">" param-name "</label>"
         "<input type=\"range\" class=\"ctx-slider\""
         " data-fx=\"" effect-name "\""
         " data-param=\"" param-name "\""
         " min=\"" min "\" max=\"" max "\" step=\"" step "\""
         " value=\"" value "\">"
         "<span class=\"ctx-param-val\">" (fmt-pv value) "</span>"
         "</div>")))

(defn- render-track-fx-slider [track-name effect-name param-name value]
  (when-let [{:keys [min max step]} (get-in FX-SLIDER-PARAMS [effect-name param-name])]
    (str "<div class=\"ctx-slider-row\">"
         "<label class=\"ctx-param-key\">" param-name "</label>"
         "<input type=\"range\" class=\"ctx-slider\""
         " data-track=\"" (cljs.core/name track-name) "\""
         " data-fx=\"" effect-name "\""
         " data-param=\"" param-name "\""
         " min=\"" min "\" max=\"" max "\" step=\"" step "\""
         " value=\"" value "\">"
         "<span class=\"ctx-param-val\">" (fmt-pv value) "</span>"
         "</div>")))

(defn- render-track-fx-subsection [track-name]
  (when-let [tn (get @audio/track-nodes track-name)]
    (let [active-fx (filterv #(not (:bypassed? %)) (:fx-chain tn))]
      (when (seq active-fx)
        (str "<details open class=\"ctx-track-fx\">"
             "<summary class=\"ctx-track-fx-title\">fx (" (count active-fx) ")</summary>"
             (apply str
               (map (fn [{:keys [name plugin]}]
                      (let [params     (try (js->clj (.getParams ^js plugin))
                                           (catch :default _ {}))
                            fx-sliders (get FX-SLIDER-PARAMS name)
                            sliders    (when fx-sliders
                                         (apply str
                                           (keep (fn [[pname _]]
                                                   (when-let [v (get params pname)]
                                                     (render-track-fx-slider track-name name pname v)))
                                                 fx-sliders)))]
                        (str "<div class=\"ctx-fx-row\">"
                             "<span class=\"ctx-fx-name\">" name "</span>"
                             "</div>"
                             (or sliders ""))))
                    active-fx))
             "</details>")))))

;;; Section renderers

(defn- render-status-section []
  (let [bpm      (Math/round (/ 240.0 (:cycle-dur @audio/scheduler-state)))
        playing? (audio/playing?)
        backend  (if @audio/worklet-ready? "[wasm]" "[js]")
        pfx      @samples/active-bank-prefix]
    (str "<div class=\"ctx-status\">"
         "<span class=\"ctx-bpm\">" bpm " BPM</span>"
         "<span class=\"ctx-backend\">" backend "</span>"
         (when pfx (str "<span class=\"ctx-bank\">" pfx "</span>"))
         "<span class=\"" (if playing? "ctx-playing" "ctx-stopped") "\">"
         (if playing? "&#9679; playing" "&#9675; stopped")
         "</span>"
         "</div>")))

(defn- render-tracks-section []
  (let [state  @audio/scheduler-state
        tracks (:tracks state)
        muted  (:muted state)]
    (when (seq tracks)
      (let [n-active   (- (count tracks) (count muted))
            solo-track (when (and (> (count tracks) 1) (= n-active 1))
                         (first (remove #(contains? muted %) (keys tracks))))]
        (str "<div class=\"ctx-section\">"
             "<div class=\"ctx-section-title\">Tracks</div>"
             (apply str
               (map (fn [[track-name pattern]]
                      (let [muted?       (contains? muted track-name)
                            solo?        (= track-name solo-track)
                            icon         (cond muted? "&#9632;" solo? "&#9733;" :else "&#9654;")
                            params       (when-not muted? (extract-track-params pattern))
                            ;; Text params: non-numeric or not in SLIDER-PARAMS (synth, bank)
                            text-pkeys   (filter #(and (contains? params %)
                                                       (or (not (contains? SLIDER-PARAMS %))
                                                           (not (number? (get params %)))))
                                                 TRACK-PARAM-KEYS)
                            ;; Slider params: numeric values with range config
                            slider-pkeys (filter #(and (contains? params %)
                                                       (contains? SLIDER-PARAMS %)
                                                       (number? (get params %)))
                                                 TRACK-PARAM-KEYS)]
                        (str "<div class=\"ctx-track" (when muted? " ctx-track-muted") "\">"
                             "<span class=\"ctx-track-icon\">" icon "</span>"
                             "<span class=\"ctx-track-name\">:" (name track-name) "</span>"
                             (cond
                               muted? "<span class=\"ctx-track-status\">(muted)</span>"
                               solo?  "<span class=\"ctx-track-status ctx-track-solo\">(solo)</span>"
                               (seq text-pkeys)
                               (str "<span class=\"ctx-track-params\">"
                                    (apply str
                                      (map (fn [k]
                                             (str "<span class=\"ctx-param-key\">" (name k) " </span>"
                                                  "<span class=\"ctx-param-val\">" (fmt-pv (get params k)) "</span>"
                                                  " "))
                                           text-pkeys))
                                    "</span>")
                               :else "")
                             "</div>"
                             ;; Slider rows below track header
                             (when (and (not muted?) (seq slider-pkeys))
                               (apply str
                                 (map #(render-track-slider track-name % (get params %))
                                      slider-pkeys)))
                             ;; Per-track FX subsection
                             (render-track-fx-subsection track-name))))
                    (sort-by (comp name first) tracks)))
             "</div>")))))

(defn- render-fx-section []
  (let [active (filter :active? @fx/chain)]
    (when (seq active)
      (str "<div class=\"ctx-section\">"
           "<div class=\"ctx-section-title\">FX</div>"
           (apply str
             (map (fn [{:keys [name plugin bypassed?]}]
                    (let [params      (when (and plugin (not bypassed?))
                                        (try (js->clj (.getParams ^js plugin))
                                             (catch :default _ {})))
                          fx-sliders  (get FX-SLIDER-PARAMS name)
                          slider-html (when (and (not bypassed?) fx-sliders)
                                        (apply str
                                          (keep (fn [[pname _]]
                                                  (when-let [v (get params pname)]
                                                    (render-fx-slider name pname v)))
                                                fx-sliders)))
                          first-kv    (first (seq (apply dissoc params (keys fx-sliders))))
                          pstr        (when (and first-kv (not (seq slider-html)))
                                        (str (first first-kv) " "
                                             (fmt-pv (second first-kv))))]
                      (str "<div class=\"ctx-row\">"
                           "<span class=\"ctx-name\">" name "</span>"
                           (cond
                             bypassed? "<span class=\"ctx-bypass\">off</span>"
                             pstr      (str "<span class=\"ctx-param\">" pstr "</span>")
                             :else     "")
                           "</div>"
                           (or slider-html ""))))
                  active))
           "</div>"))))

(defn- render-midi-section []
  (let [mappings @midi/cc-mappings]
    (when (seq mappings)
      (str "<div class=\"ctx-section\">"
           "<div class=\"ctx-section-title\">MIDI</div>"
           (apply str
             (map (fn [[cc-num {:keys [target]}]]
                    (str "<div class=\"ctx-row\">"
                         "<span class=\"ctx-name\">CC #" cc-num "</span>"
                         "<span class=\"ctx-type\">"
                         (when target (str "&#8594; " (name target)))
                         "</span>"
                         "</div>"))
                  (sort-by key mappings)))
           "</div>"))))

(defn- render-sources-section []
  (let [sources @samples/loaded-sources]
    (when (seq sources)
      (str "<div class=\"ctx-section\">"
           "<div class=\"ctx-section-title\">Sources</div>"
           (apply str
             (map (fn [{:keys [type id banks query count]}]
                    (str "<div class=\"ctx-source\">&#9835; "
                         (case type
                           :github    (str "github:" id
                                          (when (pos? (or banks 0))
                                            (str " (" banks " banks)")))
                           :freesound (str "freesound: " query
                                          (when count (str " (" count ")")))
                           (str id))
                         "</div>"))
                  sources))
           "</div>"))))

(defn- render-buses-section []
  (let [buses (bus/active-buses)]
    (when (seq buses)
      (str "<div class=\"ctx-section\">"
           "<div class=\"ctx-section-title\">Buses</div>"
           (apply str
             (map (fn [[bus-name {:keys [type]}]]
                    (str "<div class=\"ctx-row\">"
                         "<span class=\"ctx-name\">" bus-name "</span>"
                         "<span class=\"ctx-type\">" (name type) "</span>"
                         "</div>"))
                  (sort-by (comp name key) buses)))
           "</div>"))))

(defn- render-bindings-section []
  (let [env          (or @builtins/env-atom {})
        builtin-set  @builtins/builtin-names
        user-defs    (sort (remove builtin-set (keys env)))]
    (when (seq user-defs)
      (str "<div class=\"ctx-section\">"
           "<div class=\"ctx-section-title\">Bindings</div>"
           (apply str
             (map (fn [k]
                    (str "<div class=\"ctx-row\">"
                         "<span class=\"ctx-name\">" k "</span>"
                         "<span class=\"ctx-type\">" (infer-type (get env k)) "</span>"
                         "</div>"))
                  user-defs))
           "</div>"))))

;;; Render state

(defonce render-scheduled? (atom false))
(defonce slider-active? (atom false))   ; true while any ctx-slider is held

(defn render-context-panel! []
  (when-let [panel-el (el "context-panel")]
    (set! (.-innerHTML panel-el)
          (str (render-status-section)
               (render-tracks-section)
               (render-fx-section)
               (render-midi-section)
               (render-buses-section)
               (render-sources-section)
               (render-bindings-section)))))

(defn schedule-render!
  "Request a context panel repaint via RAF, skipping ticks while a slider is held."
  []
  ;; Skip tick-driven re-renders while the user is dragging a slider —
  ;; replacing the DOM element mid-drag breaks the native range interaction.
  (when (and (not @render-scheduled?) (not @slider-active?))
    (reset! render-scheduled? true)
    (js/requestAnimationFrame
      (fn []
        (reset! render-scheduled? false)
        (render-context-panel!)))))
