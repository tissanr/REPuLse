(ns repulse.ai.budget
  "Session-level token and tool-call budget.
   Pure state — no panel deps. Callers check exhausted? before spending, and
   act on the :warn / :hard-stop return value of record-usage! themselves.")

(def default-limits {:tokens 50000 :calls 100})

(defonce state
  (atom {:tokens-used 0 :calls-used 0 :warned? false}))

(defn limits
  "Return current budget limits from localStorage, falling back to defaults."
  []
  (let [raw    (try (.getItem js/localStorage "repulse:ai:budget") (catch :default _ nil))
        stored (when raw
                 (try (js->clj (js/JSON.parse raw) :keywordize-keys true)
                      (catch :default _ nil)))]
    (merge default-limits
           (select-keys stored [:tokens :calls]))))

(defn save-limits!
  "Persist custom token and call limits to localStorage."
  [{:keys [tokens calls]}]
  (try
    (.setItem js/localStorage "repulse:ai:budget"
              (js/JSON.stringify #js {:tokens tokens :calls calls}))
    (catch :default _ nil)))

(defn exhausted?
  "True when either limit has been reached. Check before every LLM request so
   the hard stop happens before money is spent, not after."
  ([] (exhausted? @state (limits)))
  ([{:keys [tokens-used calls-used]} lims]
   (or (>= tokens-used (:tokens lims))
       (>= calls-used (:calls lims)))))

(defn status
  "Classify a usage state against limits: :hard-stop, :warn (first time 50% of
   either limit is crossed), or :ok."
  [{:keys [tokens-used calls-used warned?] :as st} lims]
  (cond
    (exhausted? st lims) :hard-stop

    (and (not warned?)
         (or (>= tokens-used (* 0.5 (:tokens lims)))
             (>= calls-used  (* 0.5 (:calls lims)))))
    :warn

    :else :ok))

(defn record-usage!
  "Record token usage for one LLM round-trip and the number of tool calls it
   requested. Returns :ok, :warn (first time 50% is crossed), or :hard-stop."
  [{:keys [tokens calls]}]
  (let [st (swap! state #(-> %
                             (update :tokens-used + (or tokens 0))
                             (update :calls-used  + (or calls 0))))
        s  (status st (limits))]
    (when (= s :warn) (swap! state assoc :warned? true))
    s))

(defn get-state [] @state)

(defn reset-budget!
  "Reset usage counters for a new session."
  []
  (swap! state assoc :tokens-used 0 :calls-used 0 :warned? false))
