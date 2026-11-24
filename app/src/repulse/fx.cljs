(ns repulse.fx
  (:require [clojure.string :as str]
            [repulse.audio :as audio]
            [repulse.lisp.util :as util]
            [repulse.specs :as specs]))

;; Ordered vector of {:name "reverb" :plugin js-obj :input node :output node}
(defonce chain (atom []))

;; Phase EXP1 (decision E6): effect-name → [[param value] …], the set-param!
;; calls of the current evaluation in order. Replayed by the offline export after
;; getParams, because some plugins (bitcrusher, dattorro-reverb) only report
;; {wet}. Kept beside the chain entries (not in them) so chain entries keep
;; their documented shape.
(defonce param-log (atom {}))

(defn validate-effect-nodes!
  [effect-name nodes]
  (when-not (specs/effect-nodes? nodes)
    (throw (js/Error. (str "[REPuLse] Effect plugin \"" effect-name
                           "\" createNodes(ctx) must return "
                           "{inputNode, outputNode} with connect/disconnect methods"))))
  nodes)

(defn- make-effect-entry [effect-name plugin ^js nodes]
  (let [entry {:name      effect-name
               :plugin    plugin
               :input     (.-inputNode nodes)
               :output    (.-outputNode nodes)
               :bypassed? false
               :active?   false}]
    (when-not (specs/fx-entry? entry)
      (throw (js/Error. (str "[REPuLse] Invalid FX chain entry for \"" effect-name "\""))))
    entry))

(defn- rewire!
  "Reconnect the chain: masterGain → effect1 → effect2 → ... → master-tap.
   master-tap is the fixed end of the master FX chain (it feeds the recorder
   and the analyser). Disconnects all existing outputs first, then rebuilds."
  []
  (let [gain    @audio/master-gain
        anl     @audio/master-tap
        effects @chain]
    (.disconnect gain)
    (doseq [{:keys [output]} effects]
      (.disconnect output))
    (if (empty? effects)
      (.connect gain anl)
      (do
        (.connect gain (:input (first effects)))
        (doseq [[a b] (partition 2 1 effects)]
          (.connect (:output a) (:input b)))
        (.connect (:output (last effects)) anl)))))

;;; ── Chain order (Phase FX1, D12) ─────────────────────────────────────────

(def default-order
  "Documented master-chain order. Plugins load asynchronously, so add-effect!
   sorts by this instead of relying on the order the imports resolve."
  ["gate" "eq" "compressor" "reverb" "delay" "filter" "dattorro-reverb"
   "chorus" "flanger" "vibrato" "phaser" "tremolo" "ring-mod"
   "overdrive" "bitcrusher" "sidechain" "distort" "amp-sim" "waveshape" "cab"
   ;; user-loaded plugins sort here
   "auto-pan" "width" "limiter"])

(def ^:private order-index
  (into {} (map-indexed (fn [i n] [n i]) default-order)))

(defn chain-rank
  "Position of effect-name in default-order; an unknown (user-loaded) plugin
   ranks between cab and auto-pan."
  [effect-name]
  (or (get order-index effect-name)
      (- (get order-index "auto-pan") 0.5)))

(defn sort-chain
  "Stable sort of chain entries by chain-rank of their :name."
  [entries]
  (vec (sort-by (comp chain-rank :name) entries)))

;;; ── Tempo (Phase FX1, D1) ────────────────────────────────────────────────

(defn- current-cycle-dur []
  (:cycle-dur @audio/scheduler-state))

(defn call-set-tempo!
  "Call plugin.setTempo(cycleDur, origin) when the plugin has it."
  [^js plugin cycle-dur origin]
  (when (and plugin cycle-dur (fn? (.-setTempo plugin)))
    (try (.setTempo plugin cycle-dur origin)
         (catch :default e
           (js/console.warn "[REPuLse] setTempo failed:" e)))))

(defn- all-plugins
  "Every live plugin instance: the global chain and every track chain."
  []
  (concat (keep :plugin @chain)
          (for [[_ {:keys [fx-chain]}] @audio/track-nodes
                {:keys [plugin]} fx-chain
                :when plugin]
            plugin)))

(defn notify-tempo!
  "Tell every live plugin instance the new cycle duration (origin 0: the real-time
   scheduler places cycle c at c × cycle-dur)."
  [cycle-dur]
  (doseq [p (all-plugins)]
    (call-set-tempo! p cycle-dur 0)))

(defn tempo-watch-fn
  "Watch fn on audio/scheduler-state: notify only when :cycle-dur changes."
  [_ _ o n]
  (when (not= (:cycle-dur o) (:cycle-dur n))
    (notify-tempo! (:cycle-dur n))))

(defonce ^:private tempo-watch
  (add-watch audio/scheduler-state ::fx-tempo
             (fn [k r o n] (tempo-watch-fn k r o n))))

(defn add-effect! [^js plugin]
  (let [ac    (audio/get-ctx)
        nodes (validate-effect-nodes! (.-name plugin) (.createNodes plugin ac))]
    (call-set-tempo! plugin (current-cycle-dur) 0)
    (swap! chain #(sort-chain (conj % (make-effect-entry (.-name plugin) plugin nodes))))
    (rewire!)))

(defn remove-effect! [effect-name]
  (when-let [entry (some #(when (= effect-name (:name %)) %) @chain)]
    (.destroy ^js (:plugin entry))
    (swap! chain #(filterv (fn [e] (not= effect-name (:name e))) %))
    (swap! param-log dissoc effect-name)
    (rewire!)))

(defn set-param! [effect-name param-name value]
  (when (some #(= effect-name (:name %)) @chain)
    (swap! chain (fn [c] (mapv #(if (= effect-name (:name %)) (assoc % :active? true) %) c)))
    (swap! param-log update effect-name (fnil conj []) [param-name value])
    (when-let [entry (some #(when (= effect-name (:name %)) %) @chain)]
      (.setParam ^js (:plugin entry) param-name value))))

(defn reset-global-effects!
  "Return global effects to their plugin defaults before evaluating source code.
   This keeps the editor buffer as the source of truth instead of carrying
   parameter values from an earlier eval or hot-reload session."
  []
  (doseq [{:keys [plugin]} @chain]
    (when (and plugin (fn? (.-resetParams ^js plugin)))
      (.resetParams ^js plugin)))
  (reset! param-log {})
  (swap! chain (fn [c] (mapv #(assoc % :active? false :bypassed? false) c))))

(defn bypass! [effect-name enabled]
  (when-let [entry (some #(when (= effect-name (:name %)) %) @chain)]
    (.bypass ^js (:plugin entry) enabled)
    (swap! chain (fn [c] (mapv #(if (= effect-name (:name %)) (assoc % :bypassed? enabled) %) c)))))

;;; ── fx arguments and parameter descriptors (Phase FX1, D4) ──────────────

(defn- key-arg? [x] (keyword? (util/unwrap x)))

(defn parse-fx-args
  "(fx :name 0.4 :dry 1) → [[\"value\" 0.4] [\"dry\" 1]] — ordered pairs,
   positional first when present. args are the arguments after the effect name
   (raw or unwrapped); values are deep-unwrapped. Each pair carries the raw key
   argument (the positional value itself for \"value\") as {:arg x} metadata, so
   callers can find its source range. Used by both fx built-ins."
  [args]
  (let [args (vec args)
        named (fn [xs]
                (vec (for [[k v] (partition 2 xs)
                           :when (key-arg? k)]
                       (with-meta [(name (util/unwrap k)) (util/deep-unwrap v)]
                         {:arg k}))))]
    (cond
      (empty? args) []
      (key-arg? (first args)) (named args)
      :else (into [(with-meta ["value" (util/deep-unwrap (first args))]
                     {:arg (first args)})]
                  (named (rest args))))))

(defn- descriptor [^js plugin]
  (when plugin
    (let [d (.-params plugin)]
      (when (and d (object? d)) d))))

(defn- param-names
  "Descriptor keys in order, without \"value\"."
  [^js d]
  (vec (remove #{"value"} (js/Object.keys d))))

(defn- show-val [v]
  (cond
    (keyword? v) (str v)
    (string? v)  (pr-str v)
    :else        (pr-str v)))

(defn- valid-number? [n]
  (and (number? n) (not (js/isNaN n))))

(defn- ->list [v]
  (let [v (util/deep-unwrap v)]
    (cond
      (or (vector? v) (seq? v)) (into-array (map util/->num v))
      (array? v)                (into-array (map util/->num v))
      :else                     #js [(util/->num v)])))

(defn prepare-param
  "→ {:name n :value v :warning msg-or-nil :send? bool}. A plugin without a
   `params` descriptor gets name and value unchanged, :send? true (the
   behaviour before FX1)."
  [^js plugin effect-name pname v]
  (if-let [d (descriptor plugin)]
    (let [params   (param-names d)
          plist    (str/join ", " params)
          kind     (aget d pname)
          skip     (fn [msg] {:name pname :value v :warning msg :send? false})]
      (cond
        (= pname "value")
        (if-let [target (aget d "value")]
          (prepare-param plugin effect-name target v)
          (skip (str "fx :" effect-name " takes no positional value — parameters: " plist)))

        (nil? kind)
        (skip (str "fx :" effect-name " has no parameter :" pname " — parameters: " plist))

        (= kind "number")
        (let [n (util/->num v)]
          (if (valid-number? n)
            {:name pname :value n :warning nil :send? true}
            (skip (str "fx :" effect-name " :" pname " expects a number — got " (show-val v)))))

        (= kind "list")
        {:name pname :value (->list v) :warning nil :send? true}

        (array? kind)
        (let [s (cond (keyword? v) (name v)
                      (string? v)  v
                      :else        nil)]
          (if (and s (some #(= s %) kind))
            {:name pname :value s :warning nil :send? true}
            (skip (str "fx :" effect-name " :" pname " has no value " (show-val v)
                       " — values: " (str/join ", " kind)))))

        :else
        {:name pname :value v :warning nil :send? true}))
    {:name pname :value v :warning nil :send? true}))

(defn needs-param-warning
  "Warning for (fx :limiter) with no parameters, when the plugin has a
   descriptor; nil otherwise."
  [^js plugin effect-name]
  (when-let [d (descriptor plugin)]
    (str "fx :" effect-name " needs a parameter — parameters: "
         (str/join ", " (param-names d)))))

(defn find-plugin
  "The registered (global chain) plugin for effect-name, or nil."
  [effect-name]
  (some #(when (= effect-name (:name %)) (:plugin %)) @chain))

(defn prepare-fx-args
  "parse-fx-args + prepare-param for effect-name. → {:pairs [[n v] …] (only the
   ones to send) :warnings [{:message :arg} …]}. :arg is the raw argument whose
   source range marks the warning (nil → the effect keyword)."
  [effect-name args]
  (let [plugin (find-plugin effect-name)
        pairs  (parse-fx-args args)]
    (if (and (empty? pairs) (needs-param-warning plugin effect-name))
      {:pairs [] :warnings [{:message (needs-param-warning plugin effect-name) :arg nil}]}
      (reduce (fn [acc pair]
                (let [[pname v] pair
                      {:keys [name value warning send?]} (prepare-param plugin effect-name pname v)]
                  (cond-> acc
                    send?   (update :pairs conj [name value])
                    warning (update :warnings conj {:message warning
                                                    :arg (:arg (meta pair))}))))
              {:pairs [] :warnings []}
              pairs))))

;;; ── Staging of global (fx …) calls (Phase QA3 A4) ────────────────────────
;; While an evaluation runs, global (fx …) calls are recorded here instead of
;; being applied: a failed evaluation must not touch the master effects, and a
;; one-off command such as (tap!) must not reset them. nil = apply at once
;; (outside evaluate*: tests, plugin UI), else a vector of ops
;; [:set name param v] | [:bypass name bool] | [:remove name].

(defonce staging (atom nil))

(defn- apply-op! [[kind effect-name a b]]
  (case kind
    :set    (set-param! effect-name a b)
    :bypass (bypass! effect-name a)
    :remove (remove-effect! effect-name)))

(defn global-op!
  "Apply a global effect op now, or record it while an evaluation is staging."
  [op]
  (if (some? @staging)
    (swap! staging conj op)
    (apply-op! op)))

(defn begin-staging!
  "Start recording global ops (evaluate*)."
  []
  (reset! staging []))

(defn commit-staging!
  "End staging. When ops were recorded, or the evaluation was a whole program
   (whole? — the code is the source of truth for the master chain), reset the
   global effects to their defaults and apply the ops in order; otherwise
   change nothing."
  [whole?]
  (let [ops @staging]
    (reset! staging nil)
    (when (or (seq ops) whole?)
      (reset-global-effects!)
      (doseq [op ops] (apply-op! op)))))

(defn discard-staging!
  "End staging and drop the recorded ops (failed evaluation)."
  []
  (reset! staging nil))

;;; ── Per-track FX chains ───────────────────────────────────────────────────

(defn- rewire-track!
  "Reconnect a track's FX chain: trackGain → fx1 → fx2 → ... → masterGain.
   Phase VIZ1: the track's tap feeds the same first node (and its analyser)."
  [track-name]
  (when-let [tn (get @audio/track-nodes track-name)]
    (let [gain    (:gain-node tn)
          ^js tap (:tap tn)
          effects (:fx-chain tn)
          master  @audio/master-gain
          head    (if (empty? effects) master (:input (first effects)))]
      (.disconnect gain)
      (when tap
        (.disconnect tap)
        (when-let [anl (:analyser tn)] (.connect tap anl))
        (.connect tap head))
      (doseq [{:keys [output]} effects]
        (try (.disconnect output) (catch :default _)))
      (if (empty? effects)
        (.connect gain master)
        (do
          (.connect gain (:input (first effects)))
          (doseq [[a b] (partition 2 1 effects)]
            (.connect (:output a) (:input b)))
          (.connect (:output (last effects)) master))))))

(defn instantiate-plugin
  "Fresh instance of a registered effect plugin (EXP1 decision 2, shared by
   per-track FX and the offline export): clone() when present, else
   Object.create + Object.assign."
  [^js p]
  ;; Prefer a plugin-provided clone() factory (needed for CLJS plugins
  ;; whose state lives in a closure atom rather than on `this`).
  ;; Fall back to Object.create/assign for plain JS object plugins.
  (if (fn? (.-clone p))
    (.clone p)
    (let [o (js/Object.create (js/Object.getPrototypeOf p))]
      (js/Object.assign o p)
      o)))

(defn add-track-effect!
  "Instantiate an effect plugin on a specific track by re-using a registered plugin."
  [track-name effect-name]
  (when-let [entry (some #(when (= effect-name (:name %)) %) @chain)]
    (let [ac     (audio/get-ctx)
          ^js fresh (instantiate-plugin (:plugin entry))
          nodes  (validate-effect-nodes! effect-name (.createNodes fresh ac))]
      (call-set-tempo! fresh (current-cycle-dur) 0)
      (swap! audio/track-nodes update-in [track-name :fx-chain]
             conj (dissoc (make-effect-entry effect-name fresh nodes) :active?))
      (rewire-track! track-name))))

(defn clear-track-effects!
  "Remove all effects from a track's FX chain."
  [track-name]
  (when-let [tn (get @audio/track-nodes track-name)]
    (doseq [{:keys [plugin]} (:fx-chain tn)]
      (try (.destroy ^js plugin) (catch :default _)))
    (swap! audio/track-nodes assoc-in [track-name :fx-chain] [])
    (rewire-track! track-name)))

(defn set-track-param!
  "Set a parameter on a named effect in a track's chain."
  [track-name effect-name param-name value]
  (when-let [tn (get @audio/track-nodes track-name)]
    (when-let [entry (some #(when (= effect-name (:name %)) %) (:fx-chain tn))]
      (.setParam ^js (:plugin entry) param-name value))))

(defn apply-track-effects!
  "Replace a track's FX chain from pattern metadata."
  [track-name track-fx]
  (clear-track-effects! track-name)
  (doseq [{:keys [name params]} track-fx]
    (if-not (specs/track-fx-entry? {:name name :params params})
      (js/console.warn "[REPuLse] Ignoring invalid track FX entry:" (pr-str {:name name :params params}))
      (do
        (add-track-effect! track-name name)
        (doseq [[k v] params]
          (set-track-param! track-name name k v))))))

;;; ── Sidechain event notification ─────────────────────────────────────────

(defn- event-name
  "Extract the sound name from an event value for plugin matching."
  [value]
  (cond
    (keyword? value) (name value)
    (and (map? value) (:note value)) (let [n (:note value)]
                                       (when (keyword? n) (name n)))
    (and (map? value) (:synth value)) (cljs.core/name (:synth value))
    :else nil))

;; QA3 A7: plugin names whose onEvent threw — logged once each.
(defonce ^:private onevent-warned (atom #{}))

(defn notify-plugins!
  "Call onEvent(name, t) on every plugin in plugins that implements it. The
   first failure of each plugin is logged (QA3 A7), later ones stay quiet."
  [plugins value t]
  (when-let [evt (event-name value)]
    (doseq [^js plugin plugins]
      (when (and plugin (.-onEvent plugin))
        (try (.onEvent plugin evt t)
             (catch :default e
               (let [nm (str (.-name plugin))]
                 (when-not (contains? @onevent-warned nm)
                   (swap! onevent-warned conj nm)
                   (js/console.warn (str "[REPuLse] plugin " nm " onEvent failed (further errors from it are not logged):") e)))))))))

(defn notify-fx-event!
  "Notify all plugins (global and per-track) that implement onEvent of a fired event.
   Called via :on-fx-event in the scheduler — fx → audio dependency is one-way."
  [value t]
  (notify-plugins! (concat (map :plugin @chain)
                           (for [[_ {:keys [fx-chain]}] @audio/track-nodes
                                 {:keys [plugin]} fx-chain]
                             plugin))
                   value t))
