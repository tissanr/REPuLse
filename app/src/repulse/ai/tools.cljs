(ns repulse.ai.tools
  "AI agent tool registry — all tools the AI can call.
   Tool executors run in the browser; edit-type tools require user confirmation."
  (:require [repulse.ui.editor :as editor]
            [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.snippets :as snippets]
            [repulse.samples :as samples]
            [repulse.ai.settings :as settings]
            [repulse.ai.injection-guard :as injection-guard]
            [repulse.ai.undo :as undo]
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

(defn- clamp-range
  "Clamp from/to to the actual document length so CodeMirror never rejects the range."
  [view from to]
  (let [doc-len (.. view -state -doc -length)]
    [(min (max 0 from) doc-len)
     (min (max 0 to)   doc-len)]))

(defn- exec-propose-edit [{:keys [from to replacement]}]
  (if @settings/auto-apply?
    (do
      (undo/record-pre-edit!)
      (when-let [v @editor/editor-view]
        (let [[f t] (clamp-range v from to)]
          (.dispatch v #js {:changes #js {:from f :to t :insert replacement}})))
      (js/Promise.resolve {:ok true :applied true :auto-applied true}))
    (if-let [v @editor/editor-view]
      (let [[f t] (clamp-range v from to)]
        (show-diff-overlay! f t replacement))
      (show-diff-overlay! from to replacement))))

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

(defn- snippets-ready []
  "Return a Promise that resolves once the snippet library is loaded."
  (if @snippets/loaded?
    (js/Promise.resolve nil)
    (js/Promise.
      (fn [resolve _]
        (snippets/load!)
        (let [key (str ::snippet-wait (js/Date.now))]
          (add-watch snippets/loaded? key
            (fn [_ _ _ v]
              (when v
                (remove-watch snippets/loaded? key)
                (resolve nil)))))))))

(defn- exec-find-snippet [{:keys [q limit]}]
  (-> (snippets-ready)
      (.then (fn [_]
               (let [n       (or limit 5)
                     results (snippets/filter-snippets q nil)
                     trimmed (take n results)]
                 (when-let [f @open-snippet-search-fn] (f q))
                 {:ok      true
                  :results (mapv #(select-keys % [:id :title :description :tags])
                                 trimmed)})))))

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

;; ── Freesound + bank executors ────────────────────────────────────────────────

(defn- freesound-search! [{:keys [query tags page_size]}]
  (let [key @settings/freesound-api-key]
    (if-not (seq key)
      (js/Promise.resolve {:error "Freesound API key not set — add it in AI Settings."})
      (-> (js/fetch (str "https://freesound.org/apiv2/search/text/"
                         "?query=" (js/encodeURIComponent (str query (when (seq tags) (str " " tags))))
                         "&token=" key
                         "&fields=id,name,duration,tags"
                         "&page_size=" (or page_size 5)))
          (.then #(.json %))
          (.then (fn [data]
                   (let [results (js->clj (.-results data) :keywordize-keys true)]
                     {:results (mapv #(select-keys % [:id :name :duration :tags]) results)})))
          (.catch (fn [e] {:error (str "Freesound error: " (.-message e))}))))))

(defn- freesound-load! [{:keys [id]}]
  (let [key @settings/freesound-api-key]
    (if-not (seq key)
      (js/Promise.resolve {:error "Freesound API key not set."})
      (-> (js/fetch (str "https://freesound.org/apiv2/sounds/" id
                         "/?token=" key "&fields=id,name,previews"))
          (.then #(.json %))
          (.then (fn [data]
                   (let [d       (js->clj data :keywordize-keys true)
                         url     (get-in d [:previews :preview-hq-mp3])
                         kw-name (str "freesound-" id)]
                     (if url
                       (do (samples/register-url! kw-name url)
                           (swap! samples/loaded-sources conj
                                  {:type :freesound :query (str "id:" id) :count 1})
                           {:ok true :keyword (str ":" kw-name)
                            :hint (str "Use (seq :" kw-name ") in your pattern")})
                       {:error (str "No HQ preview for sound " id)}))))
          (.catch (fn [e] {:error (str "Freesound error: " (.-message e))}))))))

(defn- list-banks! [_]
  {:banks (samples/format-banks)})

(defn- list-samples-in-bank! [{:keys [bank]}]
  (if-let [urls (get @samples/registry bank)]
    {:bank bank :count (count urls)
     :keywords (mapv #(str ":" bank "-" %) (range (count urls)))}
    {:error (str "Unknown bank: " bank)}))

(defn- web-search! [{:keys [query]}]
  (let [key @settings/search-api-key]
    (if-not (seq key)
      (js/Promise.resolve {:error "Web search API key not set."})
      (-> (js/fetch (str "https://api.search.brave.com/res/v1/web/search"
                         "?q=" (js/encodeURIComponent query))
                    #js {:headers #js {"Accept"               "application/json"
                                       "Accept-Encoding"      "gzip"
                                       "X-Subscription-Token" key}})
          (.then #(.json %))
          (.then (fn [data]
                   (let [results (js->clj (.. ^js data -web -results) :keywordize-keys true)]
                     {:results (mapv #(select-keys % [:title :url :description])
                                     (take 5 results))})))
          (.catch (fn [e] {:error (str "Search error: " (.-message e))}))))))

(def ^:private freesound-tools
  {:freesound_search
   {:description "Search Freesound for audio samples. Returns up to page_size results with id, name, duration, and tags."
    :params      {:query     {:type "string"  :description "Search keywords, e.g. \"analog kick 808\""}
                  :tags      {:type "string"  :description "Optional tag filter, e.g. \"kick bass\""}
                  :page_size {:type "integer" :description "Number of results (1–10, default 5)"}}
    :execute     freesound-search!}

   :freesound_load
   {:description "Register a Freesound sample by ID so it can be used as :freesound-<id> in patterns."
    :params      {:id {:type "integer" :description "Freesound sound ID from freesound_search"}}
    :execute     freesound-load!}

   :list_banks
   {:description "List all registered sample banks grouped by manufacturer. Returns a formatted string — report it verbatim to the user without adding examples."
    :params      {}
    :execute     list-banks!}

   :list_samples_in_bank
   {:description "List sample keywords available in a named bank."
    :params      {:bank {:type "string" :description "Bank name from list_banks"}}
    :execute     list-samples-in-bank!}

   :web_search
   {:description "Search the web for music theory, scales, rhythm patterns, or genre context."
    :params      {:query {:type "string" :description "Search query"}}
    :execute     web-search!}})

;; ── Tool registry ─────────────────────────────────────────────────────────────

(def registry
  (merge
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
     :execute     exec-set-bpm-proposal}}
   freesound-tools))

;; ── Provider schema builders ──────────────────────────────────────────────────

(defn- params->properties [params]
  (into {} (map (fn [[k {:keys [type description]}]]
                  [(name k) (cond-> {:type type}
                              description (assoc :description description))])
                params)))

(defn- reg->openai [reg]
  (mapv (fn [[k {:keys [description params]}]]
          {:type     "function"
           :function {:name        (name k)
                      :description description
                      :parameters  {:type       "object"
                                    :properties (params->properties params)}}})
        reg))

(defn- reg->anthropic [reg]
  (mapv (fn [[k {:keys [description params]}]]
          {:name         (name k)
           :description  description
           :input_schema {:type       "object"
                          :properties (params->properties params)}})
        reg))

(defn- reg->google [reg]
  [{:functionDeclarations
    (mapv (fn [[k {:keys [description params]}]]
            {:name        (name k)
             :description description
             :parameters  {:type       "object"
                           :properties (params->properties params)}})
          reg)}])

(defn- active-registry []
  (cond-> registry
    (not (seq @settings/search-api-key)) (dissoc :web_search)))

(defn tool-descriptors
  "Return tool list for the current provider, omitting web_search when no key is set."
  [provider]
  (let [reg (active-registry)]
    (case provider
      "anthropic" (reg->anthropic reg)
      "google"    (reg->google reg)
      (reg->openai reg))))  ; openai, groq, xai

;; ── Dispatch ─────────────────────────────────────────────────────────────────

(defn execute!
  "Execute a tool call. Returns a Promise resolving to the result map.
   All results are passed through the injection guard before returning."
  [{:keys [name args]}]
  (let [k   (keyword name)
        {:keys [execute]} (get registry k)]
    (if execute
      (let [result (execute (or args {}))]
        (-> (if (instance? js/Promise result) result (js/Promise.resolve result))
            (.then (fn [r] (injection-guard/guard-tool-result k r)))))
      (js/Promise.resolve {:ok false :error (str "Unknown tool: " name)}))))
