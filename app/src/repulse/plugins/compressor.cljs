(ns repulse.plugins.compressor)

;; Dynamics compressor effect plugin — ClojureScript implementation.
;;
;; State is kept in a plain Clojure atom (a closure over `make`), so the
;; plugin object has no mutable fields of its own — all mutation goes through
;; the atom.  This is idiomatic CLJS and requires no `this` interop.
;;
;; Signal graph:
;;   input ──┬────────────────────── dry ──┬── out
;;           └── DynamicsCompressor ── wet ──┘
;;
;; Default: dry=0, wet=1 (fully compressed).
;; (fx :off :compressor) swaps to dry=1, wet=0 for transparent bypass.

(defn make []
  (let [state (atom nil)]

    #js {:type    "effect"
         :name    "compressor"
         :version "1.0.0"

         :init (fn [_host] nil)

         :createNodes
         (fn [ctx]
           (let [input (.createGain ctx)
                 comp  (.createDynamicsCompressor ctx)
                 dry   (.createGain ctx)
                 wet   (.createGain ctx)
                 out   (.createGain ctx)]
             (set! (.. comp -threshold -value) -24)
             (set! (.. comp -knee      -value) 10)
             (set! (.. comp -ratio     -value) 4)
             (set! (.. comp -attack    -value) 0.003)
             (set! (.. comp -release   -value) 0.25)
             (set! (.. dry  -gain -value) 0.0)   ;; fully compressed by default
             (set! (.. wet  -gain -value) 1.0)
             ;; wire the graph
             (.connect input dry)
             (.connect input comp)
             (.connect comp  wet)
             (.connect dry   out)
             (.connect wet   out)
             (reset! state {:input input :comp comp :dry dry :wet wet :out out})
             #js {:inputNode input :outputNode out}))

         :setParam
         (fn [param-name value]
           (when-let [{:keys [comp wet]} @state]
             (case param-name
               ("wet" "value") (set! (.. wet -gain -value) (max 0 (min 1 value)))
               "threshold"     (set! (.. comp -threshold -value) value)
               "ratio"         (set! (.. comp -ratio     -value) value)
               "attack"        (set! (.. comp -attack    -value) value)
               "release"       (set! (.. comp -release   -value) value)
               "knee"          (set! (.. comp -knee      -value) value)
               nil)))

         :bypass
         (fn [on]
           (when-let [{:keys [wet dry]} @state]
             (if on
               (do (swap! state assoc :saved-wet (.. wet -gain -value))
                   (set! (.. wet -gain -value) 0)
                   (set! (.. dry -gain -value) 1))
               (do (set! (.. wet -gain -value) (or (:saved-wet @state) 1.0))
                   (set! (.. dry -gain -value) 0)))))

         :getParams
         (fn []
           (if-let [{:keys [comp wet]} @state]
             #js {:wet       (.. wet  -gain      -value)
                  :threshold (.. comp -threshold -value)
                  :ratio     (.. comp -ratio     -value)
                  :attack    (.. comp -attack    -value)
                  :release   (.. comp -release   -value)
                  :knee      (.. comp -knee      -value)}
             #js {}))

         :destroy
         (fn []
           (when-let [{:keys [input out]} @state]
             (.disconnect input)
             (.disconnect out))
           (reset! state nil))}))

(def plugin (make))
