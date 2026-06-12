(ns repulse.ai.budget
  "Session-level token and tool-call budget.
   Pure state — no panel deps. Callers check the return value of record-usage!
   and act on :warn / :hard-stop themselves.")

(def default-limits {:tokens 50000 :calls 100})

(defonce state
  (atom {:tokens-used 0 :calls-used 0 :warned? false}))

(defn limits
  "Return current budget limits from localStorage, falling back to defaults."
  []
  (let [raw (try (.getItem js/localStorage "repulse:ai:budget") (catch :default _ nil))]
    (if raw
      (try (js->clj (js/JSON.parse raw) :keywordize-keys true)
           (catch :default _ default-limits))
      default-limits)))

(defn save-limits!
  "Persist custom token and call limits to localStorage."
  [{:keys [tokens calls]}]
  (try
    (.setItem js/localStorage "repulse:ai:budget"
              (js/JSON.stringify #js {:tokens tokens :calls calls}))
    (catch :default _ nil)))

(defn record-usage!
  "Record token + call usage for one LLM round-trip.
   Returns :ok, :warn (first time 50% is crossed), or :hard-stop."
  [{:keys [tokens calls]}]
  (swap! state update :tokens-used + (or tokens 0))
  (swap! state update :calls-used  + (or calls 0))
  (let [{:keys [tokens-used calls-used warned?]} @state
        lims (limits)]
    (cond
      (or (>= tokens-used (:tokens lims)) (>= calls-used (:calls lims)))
      :hard-stop

      (and (not warned?)
           (or (>= tokens-used (* 0.5 (:tokens lims)))
               (>= calls-used  (* 0.5 (:calls lims)))))
      (do (swap! state assoc :warned? true)
          :warn)

      :else :ok)))

(defn get-state [] @state)

(defn reset-budget!
  "Reset usage counters for a new session."
  []
  (swap! state assoc :tokens-used 0 :calls-used 0 :warned? false))
