(ns repulse.ai.tool-calls
  "Validation of model-issued tool calls, so the agent loop survives models
   (often local ones) that emit malformed calls. Pure — no tool registry or
   DOM dependency; the caller passes the set of known tool names.")

(def max-malformed-rounds
  "Malformed tool-call responses tolerated per turn. The first gets error
   results fed back; the next one ends the turn with the model's text."
  1)

(defn call-error
  "Why a single tool call can't be executed, or nil when it's well-formed."
  [known-names {:keys [name args-error]}]
  (cond
    (not (seq name))                   "tool call has no function name"
    (not (contains? known-names name)) (str "unknown tool: " name)
    args-error                         args-error))

(defn invalid-calls
  "[{:call c :error msg}] for every malformed call in `calls`."
  [known-names calls]
  (keep (fn [c] (when-let [e (call-error known-names c)] {:call c :error e})) calls))

(defn next-action
  "What the agent loop does with a tool-call response: :execute when all calls
   are well-formed, :retry to feed errors back once, :give-up after that."
  [known-names calls malformed-rounds]
  (cond
    (empty? (invalid-calls known-names calls)) :execute
    (< malformed-rounds max-malformed-rounds)  :retry
    :else                                      :give-up))

(defn error-results
  "Tool results telling the model which calls were rejected and why. Nothing
   was executed, so well-formed calls in the same batch are reported as skipped."
  [known-names calls]
  (mapv (fn [c]
          {:id     (:id c)
           :name   (:name c)
           :result {:ok    false
                    :error (or (some->> (call-error known-names c)
                                        (str "Invalid tool call — "))
                               "Not executed because another call in this batch was invalid. Retry with valid calls.")}})
        calls))
