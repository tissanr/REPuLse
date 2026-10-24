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
         (:error result)             (str "error: " (:error result))
         (contains? result :applied) (if (:applied result) "applied" "rejected")
         :else                       "done")))

;; ── Token accounting ─────────────────────────────────────────────────────────

(defn- estimate-tokens
  "Fallback when the provider reports no usage: ~4 characters per token over
   everything sent (system prompt + history) and received."
  [system messages response]
  (js/Math.ceil (/ (+ (count system)
                      (count (pr-str messages))
                      (count (str (:content response)))
                      (count (pr-str (:tool-calls response))))
                   4)))

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

(def ^:private budget-exhausted-msg
  "🛑 AI budget exhausted. Raise the limit in AI settings or reset the session from the tool log.")

(defn- finish!
  "End the turn: show `content` (when given), record the exchange in history
   so the next turn keeps context, and clear the pending indicator."
  [user-msg content]
  (when content (add-msg! "assistant" content))
  (swap! history conj {:role "user"      :content user-msg}
                      {:role "assistant" :content (or content "(stopped)")})
  (set-pending! false))

(defn- run-loop! [messages call-count user-msg turn-id]
  (cond
    @cancelled?
    (set-pending! false)

    (>= call-count max-tool-calls)
    (finish! user-msg (str "I've reached the tool-call limit (" max-tool-calls ") for this turn."))

    ;; Checked before the request so an exhausted budget never costs another call.
    (budget/exhausted?)
    (do (add-msg! "system" budget-exhausted-msg)
        (finish! user-msg nil))

    :else
    (let [provider  @settings/provider
          tool-desc (tools/tool-descriptors provider)
          system    (str (sys-prompt/build (if-let [f @panel-get-summary] (f) "")))]
      (-> (client/complete-with-retry! system messages tool-desc)
          (.then
            (fn [response]
              (cond
                @cancelled?
                (set-pending! false)

                (:error response)
                (do (add-msg! "assistant" (str "Error: " (:error response)))
                    (set-pending! false))

                :else
                (let [tc     (:tool-calls response)
                      tokens (or (:usage response) (estimate-tokens system messages response))
                      status (budget/record-usage! {:tokens tokens :calls (count tc)})]
                  (when (= status :warn)
                    (add-msg! "system" "⚠️ 50% of your AI budget used this session."))
                  (cond
                    ;; Text reply — already paid for, so always show it.
                    (not tc)
                    (finish! user-msg (:content response "(no response)"))

                    (= status :hard-stop)
                    (do (add-msg! "system" budget-exhausted-msg)
                        (finish! user-msg nil))

                    :else
                    (do
                      (undo/begin-turn! turn-id)
                      (add-msg! "assistant"
                                (str "Calling tools: " (str/join ", " (map :name tc)) "…"))
                      (-> (execute-tools! tc)
                          (.then
                            (fn [results]
                              (when-not @cancelled?
                                (doseq [[call {:keys [result]}] (map vector tc results)]
                                  (add-msg! "tool" (tool-status-line call result)))
                                (run-loop! (conj messages
                                                 {:role "assistant" :tool-calls tc}
                                                 {:role "tool"      :results results})
                                           (+ call-count (count tc))
                                           user-msg
                                           turn-id)))))))))))
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
