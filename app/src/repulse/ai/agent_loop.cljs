(ns repulse.ai.agent-loop
  "Bounded tool-using agent loop.
   Orchestrates complete! calls, tool execution, and panel updates.
   Exports: run-agent-turn!, cancel!, set-cancel-fn!"
  (:require [repulse.ai.client :as client]
            [repulse.ai.settings :as settings]
            [repulse.ai.system-prompt :as sys-prompt]
            [repulse.ai.tools :as tools]
            [clojure.string :as str]))

;;; Forward reference to panel functions — set via set-panel-fns! to avoid
;;; circular dependency: agent-loop ← assistant-panel ← agent-loop.
(defonce ^:private panel-add-message!  (atom nil))
(defonce ^:private panel-set-pending!  (atom nil))
(defonce ^:private panel-get-summary   (atom nil))

(defn set-panel-fns!
  "Wire assistant-panel callbacks. Called from assistant-panel init!."
  [{:keys [add-message! set-pending! get-builtins-summary]}]
  (reset! panel-add-message!  add-message!)
  (reset! panel-set-pending!  set-pending!)
  (reset! panel-get-summary   get-builtins-summary))

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

(defn- tool-status-line [call result]
  (str "▸ " (:name call)
       (when-let [args (and (seq (:args call)) (:args call))]
         (str " " (pr-str args)))
       " → "
       (cond
         (:error result)   (str "error: " (:error result))
         (:applied result) (if (:applied result) "applied" "rejected")
         :else             "done")))

;; ── Tool execution ────────────────────────────────────────────────────────────

(defn- execute-tools! [tool-calls]
  (reduce
    (fn [p call]
      (.then p
             (fn [results]
               (-> (tools/execute! call)
                   (.then (fn [result]
                            (conj results
                                  {:id     (:id call)
                                   :name   (:name call)
                                   :result result})))))))
    (js/Promise.resolve [])
    tool-calls))

;; ── Agent loop ────────────────────────────────────────────────────────────────

(defn- run-loop! [messages call-count user-msg]
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
      (-> (client/complete! system messages tool-desc)
          (.then
            (fn [response]
              (if @cancelled?
                (set-pending! false)
                (if-let [tc (:tool-calls response)]
                  ;; Model wants to call tools
                  (do
                    (add-msg! "assistant"
                               (str "Calling tools: " (str/join ", " (map :name tc)) "…"))
                    (-> (execute-tools! tc)
                        (.then
                          (fn [results]
                            (when-not @cancelled?
                              ;; Show per-tool status
                              (doseq [[call result] (map vector tc results)]
                                (add-msg! "tool" (tool-status-line call result)))
                              (let [next-msgs (conj messages
                                                    {:role "assistant" :tool-calls tc}
                                                    {:role "tool"      :results results})]
                                (run-loop! next-msgs
                                           (+ call-count (count tc))
                                           user-msg)))))))
                  ;; Model returned a text reply — done
                  (let [content (:content response "(no response)")]
                    (add-msg! "assistant" content)
                    (swap! history conj {:role "user"      :content user-msg}
                                        {:role "assistant" :content content})
                    (set-pending! false))))))
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
    (reset! cancelled? false)
    (add-msg! "user" user-message)
    (set-pending! true)
    (let [messages (conj (mapv #(select-keys % [:role :content :tool-calls :results]) @history)
                         {:role "user" :content user-message})]
      (run-loop! messages 0 user-message))))
