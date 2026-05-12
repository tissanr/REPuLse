(ns repulse.ai.tools
  "AI agent tool registry — all tools the AI can call.
   Tool executors run in the browser; edit-type tools require user confirmation."
  (:require [repulse.ui.editor :as editor]
            [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.snippets :as snippets]
            [clojure.string :as str]))

;;; eval-preview dependency injected from eval-orchestrator to avoid circular deps
(defonce eval-preview-fn (atom nil))

;;; open-snippet-search injected from snippet-panel to avoid circular deps
(defonce open-snippet-search-fn (atom nil))

;; ── Diff overlay ─────────────────────────────────────────────────────────────

(defonce ^:private active-proposal (atom nil))

(defn- el [id] (.getElementById js/document id))

(defn- build-diff-text [view from to replacement]
  (let [doc   (.. view -state -doc (toString))
        before (subs doc (min from (.-length doc)) (min to (.-length doc)))]
    {:before before :after replacement}))

(defn- remove-overlay! []
  (when-let [overlay (el "ai-proposal-overlay")]
    (.remove overlay))
  (reset! active-proposal nil))

(defn show-diff-overlay!
  "Show a proposal card in the editor area. Returns a Promise that resolves
   {:ok true :applied bool} when the user clicks Apply or Reject."
  [from to replacement]
  (js/Promise.
    (fn [resolve _reject]
      ;; Remove any lingering overlay first
      (remove-overlay!)
      (let [view    @editor/editor-view
            {:keys [before after]} (if view
                                     (build-diff-text view from to replacement)
                                     {:before "" :after replacement})
            overlay (.createElement js/document "div")]
        (set! (.-id overlay) "ai-proposal-overlay")
        (set! (.-className overlay) "ai-proposal-overlay")
        (set! (.-innerHTML overlay)
              (str "<div class=\"ai-proposal-header\">AI proposed edit</div>"
                   "<div class=\"ai-proposal-diff\">"
                   "<div class=\"ai-proposal-before\"><span class=\"ai-diff-label\">before</span><pre>"
                   (-> before
                       (str/replace "&" "&amp;")
                       (str/replace "<" "&lt;")
                       (str/replace ">" "&gt;"))
                   "</pre></div>"
                   "<div class=\"ai-proposal-after\"><span class=\"ai-diff-label\">after</span><pre>"
                   (-> after
                       (str/replace "&" "&amp;")
                       (str/replace "<" "&lt;")
                       (str/replace ">" "&gt;"))
                   "</pre></div>"
                   "</div>"
                   "<div class=\"ai-proposal-btns\">"
                   "<button id=\"ai-apply-btn\" class=\"ai-btn\">Apply</button>"
                   "<button id=\"ai-reject-btn\" class=\"ai-btn ai-btn--secondary\">Reject</button>"
                   "</div>"))
        ;; Insert overlay below the editor container
        (let [editor-el (or (.getElementById js/document "editor-container")
                            (.-body js/document))
              parent    (.-parentNode editor-el)]
          (if parent
            (.insertBefore parent overlay (.-nextSibling editor-el))
            (.appendChild (.-body js/document) overlay)))
        (reset! active-proposal {:resolve-fn resolve :from from :to to :replacement replacement})
        ;; Wire buttons
        (.addEventListener overlay "click"
          (fn [^js e]
            (let [id (.-id (.-target e))]
              (cond
                (= id "ai-apply-btn")
                (do
                  (when-let [v @editor/editor-view]
                    (.dispatch v #js {:changes #js {:from from :to to :insert replacement}}))
                  (remove-overlay!)
                  (resolve {:ok true :applied true}))

                (= id "ai-reject-btn")
                (do
                  (remove-overlay!)
                  (resolve {:ok true :applied false :reason "user rejected"}))))))))))

(defn dismiss-overlay!
  "Remove any active diff overlay and reject its promise."
  []
  (when-let [{:keys [resolve-fn]} @active-proposal]
    (remove-overlay!)
    (resolve-fn {:ok false :applied false :reason "cancelled"})))

;; ── Session query helpers ─────────────────────────────────────────────────────

(defn- query-session []
  (let [state @audio/scheduler-state
        bpm   (audio/get-bpm)
        global-fx (mapv #(select-keys % [:name :active? :bypassed?]) @fx/chain)]
    {:bpm        bpm
     :playing?   (:playing? state)
     :tracks     (mapv name (keys (:tracks state)))
     :muted      (mapv name (:muted state))
     :global-fx  global-fx}))

(defn- query-track [track-kw]
  (let [state    @audio/scheduler-state
        track-nodes @audio/track-nodes
        pattern  (get-in state [:tracks track-kw])
        tn       (get track-nodes track-kw)
        fx-chain (mapv #(select-keys % [:name :active? :bypassed?])
                       (get tn :fx-chain []))]
    (if pattern
      {:name    (name track-kw)
       :active? true
       :muted?  (contains? (:muted state) track-kw)
       :fx-chain fx-chain}
      {:name (name track-kw) :active? false})))

;; ── Tool executors ────────────────────────────────────────────────────────────

(defn- exec-read-buffer [_args]
  (if-let [view @editor/editor-view]
    {:ok true :text (.. view -state -doc (toString))}
    {:ok false :error "Editor not initialized"}))

(defn- exec-propose-edit [{:keys [from to replacement]}]
  (show-diff-overlay! from to replacement))

(defn- exec-eval-preview [{:keys [code]}]
  (js/Promise.
    (fn [resolve _reject]
      (if-let [f @eval-preview-fn]
        (let [result (f code)]
          (resolve result))
        (resolve {:ok false :error "eval-preview not available"})))))

(defn- exec-query-session [_args]
  {:ok true :session (query-session)})

(defn- exec-query-track [{:keys [name]}]
  {:ok true :track (query-track (keyword name))})

(defn- exec-find-snippet [{:keys [q limit]}]
  (let [results (snippets/filter-snippets q nil)
        n       (or limit 5)
        trimmed (take n results)]
    (when-let [f @open-snippet-search-fn] (f q))
    {:ok      true
     :results (mapv #(select-keys % [:id :title :description :tags]) trimmed)}))

(defn- exec-insert-snippet [{:keys [id]}]
  (if-let [snippet (snippets/by-id id)]
    (do
      (when-let [view @editor/editor-view]
        (let [code    (:code snippet)
              doc-len (.. view -state -doc -length)]
          (.dispatch view #js {:changes #js {:from doc-len :to doc-len
                                             :insert (str "\n\n" code)}})))
      {:ok true :inserted id})
    {:ok false :error (str "Unknown snippet: " id)}))

(defn- exec-set-bpm-proposal [{:keys [bpm]}]
  (js/Promise.
    (fn [resolve _reject]
      (if (js/confirm (str "AI suggests BPM: " (js/Math.round bpm) ". Apply?"))
        (do (audio/set-bpm! bpm) (resolve {:ok true :applied true}))
        (resolve {:ok true :applied false :reason "user rejected"})))))

;; ── Tool registry ─────────────────────────────────────────────────────────────

(def registry
  {:read_buffer
   {:description "Read the current editor buffer text."
    :params      {}
    :execute     exec-read-buffer}

   :propose_edit
   {:description "Propose a text edit to the editor buffer. Shows a diff overlay; the user must click Apply."
    :params      {:from        {:type "integer" :description "Start character offset (inclusive)"}
                  :to          {:type "integer" :description "End character offset (exclusive)"}
                  :replacement {:type "string"  :description "Replacement text"}}
    :execute     exec-propose-edit}

   :eval_preview
   {:description "Evaluate REPuLse-Lisp code silently and return event count and duration."
    :params      {:code {:type "string" :description "REPuLse-Lisp expression to evaluate"}}
    :execute     exec-eval-preview}

   :query_session
   {:description "Return current session state: BPM, track names, muted tracks, active FX."
    :params      {}
    :execute     exec-query-session}

   :query_track
   {:description "Return details for one track: activity, mute state, FX chain."
    :params      {:name {:type "string" :description "Track name (without leading colon)"}}
    :execute     exec-query-track}

   :find_snippet
   {:description "Search the snippet library by text query. Returns up to `limit` results."
    :params      {:q     {:type "string"  :description "Search query"}
                  :limit {:type "integer" :description "Max results (default 5)"}}
    :execute     exec-find-snippet}

   :insert_snippet
   {:description "Insert a snippet by ID into the editor at the end of the buffer."
    :params      {:id {:type "string" :description "Snippet ID"}}
    :execute     exec-insert-snippet}

   :set_bpm_proposal
   {:description "Propose a BPM change. Shows a confirm dialog; the user must approve."
    :params      {:bpm {:type "number" :description "Desired BPM"}}
    :execute     exec-set-bpm-proposal}})

;; ── Provider schema builders ──────────────────────────────────────────────────

(defn- params->properties [params]
  (into {} (map (fn [[k {:keys [type description]}]]
                  [(name k) (cond-> {:type type}
                              description (assoc :description description))])
                params)))

(defn ->openai-tools []
  (mapv (fn [[k {:keys [description params]}]]
          {:type     "function"
           :function {:name        (name k)
                      :description description
                      :parameters  {:type       "object"
                                    :properties (params->properties params)}}})
        registry))

(defn ->anthropic-tools []
  (mapv (fn [[k {:keys [description params]}]]
          {:name         (name k)
           :description  description
           :input_schema {:type       "object"
                          :properties (params->properties params)}})
        registry))

(defn ->google-tools []
  [{:functionDeclarations
    (mapv (fn [[k {:keys [description params]}]]
            {:name        (name k)
             :description description
             :parameters  {:type       "object"
                           :properties (params->properties params)}})
          registry)}])

(defn tool-descriptors
  "Return tool list in the correct schema for the current provider."
  [provider]
  (case provider
    "anthropic" (->anthropic-tools)
    "google"    (->google-tools)
    (->openai-tools)))  ; openai, groq, xai

;; ── Dispatch ─────────────────────────────────────────────────────────────────

(defn execute!
  "Execute a tool call. Returns a Promise resolving to the result map."
  [{:keys [name args]}]
  (let [k   (keyword name)
        {:keys [execute]} (get registry k)]
    (if execute
      (let [result (execute (or args {}))]
        (if (instance? js/Promise result)
          result
          (js/Promise.resolve result)))
      (js/Promise.resolve {:ok false :error (str "Unknown tool: " name)}))))
