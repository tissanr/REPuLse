(ns repulse.ai.agent-loop
  "Bounded tool-using agent loop.
   Orchestrates complete! calls, tool execution, and panel updates.
   Exports: run-agent-turn!, cancel!, set-cancel-fn!"
  (:require [repulse.ai.client :as client]
            [repulse.ai.settings :as settings]
            [repulse.ai.system-prompt :as sys-prompt]
            [repulse.ai.tools :as tools]
            [repulse.ai.budget :as budget]
            [repulse.ai.undo :as undo]
            [clojure.string :as str]))

;;; Forward reference to panel functions — set via set-panel-fns! to avoid
;;; circular dependency: agent-loop ← assistant-panel ← agent-loop.
(defonce ^:private panel-add-message!  (atom nil))
(defonce ^:private panel-set-pending!  (atom nil))
(defonce ^:private panel-get-summary   (atom nil))
(defonce ^:private panel-log-tool-call! (atom nil))

(defn set-panel-fns!
  "Wire assistant-panel callbacks. Called from assistant-panel init!."
  [{:keys [add-message! set-pending! get-builtins-summary log-tool-call!]}]
  (reset! panel-add-message!   add-message!)
  (reset! panel-set-pending!   set-pending!)
  (reset! panel-get-summary    get-builtins-summary)
  (reset! panel-log-tool-call! log-tool-call!))

;;; Conversation history — shared with panel for display
(defonce history (atom []))

(defn reset-history! [] (reset! history []))

;;; Abort state
(defonce ^:private cancelled? (atom false))

(defn cancel!
  "Abort the current agent turn (sets a flag checked between tool calls).
   Also dismisses any active diff overlay."
  []
  (reset! cancelled? true)
  (tools/dismiss-overlay!))

;;; Max tool calls per turn
(def max-tool-calls 8)

;; ── Helpers ───────────────────────────────────────────────────────────────────

(defn- add-msg! [role content]
  (when-let [f @panel-add-message!]
    (f role content)))

(defn- set-pending! [v]
  (when-let [f @panel-set-pending!]
    (f v)))

(defn- log-tool-call! [tool-name args result]
  (when-let [f @panel-log-tool-call!]
    (f tool-name args result)))

(defn- truncate-arg [v]
  (if (and (string? v) (> (count v) 60))
    (str (subs v 0 57) "…")
    v))

(defn- summarise-args [args]
  (when (seq args)
    (str " " (pr-str (into {} (map (fn [[k v]] [k (truncate-arg v)]) args))))))

(defn- tool-status-line [call result]
  (str "▸ " (:name call)
       (summarise-args (:args call))
       " → "
       (cond
         (:error result)   (str "error: " (:error result))
         (:applied result) (if (:applied result) "applied" "rejected")
         :else             "done")))

;; ── Token estimation ─────────────────────────────────────────────────────────

(defn- estimate-tokens [s]
  (js/Math.ceil (/ (count (str s)) 4)))

;; ── Tool execution ────────────────────────────────────────────────────────────

(defn- execute-tools! [tool-calls]
  (reduce
    (fn [p call]
      (.then p
             (fn [results]
               (-> (tools/execute! call)
                   (.then (fn [result]
                            (log-tool-call! (:name call) (:args call) result)
                            (conj results
                                  {:id     (:id call)
                                   :name   (:name call)
                                   :result result})))))))
    (js/Promise.resolve [])
    tool-calls))

;; ── Agent loop ────────────────────────────────────────────────────────────────

(defn- run-loop! [messages call-count user-msg turn-id]
  (if (or @cancelled? (>= call-count max-tool-calls))
    (do
      (when (and (not @cancelled?) (>= call-count max-tool-calls))
        (add-msg! "assistant" (str "I've reached the tool-call limit (" max-tool-calls ") for this turn.")))
      (set-pending! false)
      (swap! history conj {:role "user"    :content user-msg}
                          {:role "assistant" :content "(tool-call limit reached)"}))
    (let [provider @settings/provider
          tool-desc (tools/tool-descriptors provider)
          system    (str (sys-prompt/build (if-let [f @panel-get-summary] (f) "")))]
      (-> (client/complete-with-retry! system messages tool-desc)
          (.then
            (fn [response]
              (if @cancelled?
                (set-pending! false)
                (let [;; Estimate tokens from response content and record budget usage.
                      resp-tokens (estimate-tokens (or (:content response) (pr-str (:tool-calls response))))
                      budget-status (budget/record-usage! {:tokens resp-tokens :calls 1})]

                  (when (= budget-status :warn)
                    (add-msg! "system" "⚠️ 50% of your AI budget used this session."))

                  (when (= budget-status :hard-stop)
                    (add-msg! "system" "🛑 AI budget exhausted. Raise the limit in Settings → AI → Budget.")
                    (set-pending! false))

                  (when (not= budget-status :hard-stop)
                    (if-let [tc (:tool-calls response)]
                      ;; Model wants to call tools
                      (do
                        (undo/begin-turn! turn-id)
                        (add-msg! "assistant"
                                  (str "Calling tools: " (str/join ", " (map :name tc)) "…"))
                        (-> (execute-tools! tc)
                            (.then
                              (fn [results]
                                (when-not @cancelled?
                                  (doseq [[call result] (map vector tc results)]
                                    (add-msg! "tool" (tool-status-line call result)))
                                  (let [next-msgs (conj messages
                                                        {:role "assistant" :tool-calls tc}
                                                        {:role "tool"      :results results})]
                                    (run-loop! next-msgs
                                               (+ call-count (count tc))
                                               user-msg
                                               turn-id)))))))
                      ;; Model returned a text reply — done
                      (let [content (:content response "(no response)")]
                        (add-msg! "assistant" content)
                        (swap! history conj {:role "user"      :content user-msg}
                                            {:role "assistant" :content content})
                        (set-pending! false))))))))
          (.catch
            (fn [err]
              (add-msg! "assistant" (str "Error: " (if (string? err) err (or (.-message err) "unknown"))))
              (set-pending! false)))))))

;; ── Public entry point ────────────────────────────────────────────────────────

(defn run-agent-turn!
  "Start an agent turn with user-message.
   Adds user message to the panel, then enters the tool-call loop."
  [user-message]
  (when (seq (str/trim user-message))
    (let [turn-id (str (gensym "turn_"))]
      (reset! cancelled? false)
      (add-msg! "user" user-message)
      (set-pending! true)
      (let [messages (conj (mapv #(select-keys % [:role :content :tool-calls :results]) @history)
                           {:role "user" :content user-message})]
        (run-loop! messages 0 user-message turn-id)))))
