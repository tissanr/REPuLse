# Phase AI3b — AI Sample Discovery & Web Search Tools

## Goal

Extend the AI3 tool registry with sample-discovery and web-search capabilities so the
in-app assistant can find Freesound samples, list available banks, and (optionally)
search the web for music-theory context — all without the user leaving the editor.
The Freesound API key is stored once in the AI settings panel (no `(freesound-key! "…")`
required per session). Every sample load and edit proposal still requires explicit user
confirmation, as in AI3.

```
;; Before (AI3) — assistant can describe but not act on samples:
;;   User: "Find me an analog kick from Freesound"
;;   AI: "Run (freesound-key! \"your-key\") then (freesound! \"analog kick 808\")"
;;   User: copies/pastes, evaluates two expressions, reads the output
;;   User: adds :freesound-NNNNN to pattern manually

;; After (AI3b) — assistant searches and proposes:
;;   User: "Find me an analog kick from Freesound"
;;   AI: calls freesound_search("analog kick 808")
;;       → [{:id 123456 :name "Analog Kick Tight" :duration 0.42}
;;           {:id 234567 :name "808 Kick Hard"     :duration 0.61} …]
;;   AI: "I found 5 analog kicks — loading #123456 (Analog Kick Tight, 0.4 s)"
;;   AI: calls freesound_load(123456)
;;       → registers URL, then calls propose_edit:
;;         + (seq :freesound-123456)
;;   User clicks Apply → sample is loaded and plays immediately
```

---

## Background

### Existing Freesound integration

`app/src/repulse/env/builtins/routing.cljs` — `freesound-key!` stores the key in a
`defonce ^:private freesound-api-key` atom; `freesound!` fetches from
`https://freesound.org/apiv2/search/text/` with fields `id,name,previews` and
calls `samples/register-url!` per result. AI3b bypasses the Lisp-facing `freesound-key!`
and reads the key directly from the AI settings atom instead.

`app/src/repulse/samples.cljs` — `register-url! [name url]` adds an entry to the
`registry` atom and clears `buffer-cache`/`in-flight` for that key. The `loaded-sources`
atom (vector of `{:type :freesound :query … :count …}`) is updated by the caller.
`(seq :freesound-123456)` resolves because `registry` maps `"freesound-123456"` to a
preview MP3 URL at lookup time.

### AI settings (AI2 layer)

`app/src/repulse/ai/settings.cljs` — localStorage-backed atoms: `enabled?`, `provider`,
`api-key`, `model-override`, `include-code?`. AI3b adds two fields:
- `:freesound-api-key` — Freesound OAuth2 token (visible in settings modal)
- `:search-api-key` (optional) — Brave Search API key; `web_search` tool is silently
  omitted from the tool descriptor list when this atom is nil

### AI3 tool registry

`app/src/repulse/ai/tools.cljs` (new in AI3) — `tool-registry` map of keyword → descriptor
`{:description … :params … :side-effects #{…} :execute fn}`. AI3b adds four entries to
this map; no structural changes needed.

### Sample bank registry

`app/src/repulse/samples.cljs` — `@registry` is a map of bank-name (string) →
`[url …]`. `list_banks` reads its keys; `list_samples_in_bank` reads its values.
Bank names follow the pattern `"AkaiLinn"`, `"freesound-123456"`, `"github-owner/repo"`.

---

## Implementation

### 1. `app/src/repulse/ai/settings.cljs` — add two keys

Add atoms alongside the existing ones:

```clojure
(defonce freesound-api-key  (r/atom (ls-get "repulse:ai:freesound-key" nil)))
(defonce search-api-key     (r/atom (ls-get "repulse:ai:search-key" nil)))
```

Persist on change (same pattern as `api-key`):

```clojure
(add-watch freesound-api-key :persist #(ls-set "repulse:ai:freesound-key" %4))
(add-watch search-api-key    :persist #(ls-set "repulse:ai:search-key" %4))
```

Add both fields to the settings modal UI: labelled text inputs with a password-mask
toggle, placed in a "Sample & Search" section below the AI provider section.

### 2. `app/src/repulse/ai/tools.cljs` — four new tools

Add to `tool-registry`:

```clojure
:freesound_search
{:description "Search Freesound for audio samples. Returns up to page_size results with id, name, duration, and tags."
 :params {:query     {:type "string"  :description "Search keywords, e.g. \"analog kick 808\""}
          :tags      {:type "string"  :description "Optional tag filter, e.g. \"kick bass\""}
          :page_size {:type "integer" :description "Number of results (1–10, default 5)"}}
 :side-effects #{:network}
 :execute freesound-search!}

:freesound_load
{:description "Register a Freesound sample by ID so it can be used as :freesound-<id> in patterns."
 :params {:id {:type "integer" :description "Freesound sound ID from freesound_search"}}
 :side-effects #{:network :session}
 :execute freesound-load!}

:list_banks
{:description "List all registered sample bank names available in the current session."
 :params {}
 :side-effects #{:none}
 :execute (fn [_] {:banks (vec (keys @samples/registry))})}

:list_samples_in_bank
{:description "List sample keywords available in a named bank."
 :params {:bank {:type "string" :description "Bank name from list_banks"}}
 :side-effects #{:none}
 :execute (fn [{:keys [bank]}]
            (if-let [urls (get @samples/registry bank)]
              {:bank bank :count (count urls) :keywords (mapv #(str ":" bank "-" %) (range (count urls)))}
              {:error (str "Unknown bank: " bank)}))}
```

`web_search` is added only when `@settings/search-api-key` is non-nil:

```clojure
;; In the function that builds the active tool descriptor list for each turn:
(when @settings/search-api-key
  {:web_search
   {:description "Search the web for music theory, scales, rhythm patterns, or genre context."
    :params {:query {:type "string"}}
    :side-effects #{:network}
    :execute web-search!}})
```

### 3. Executor functions

**`freesound-search!`** — fetches Freesound API directly from the browser (same URL as
the existing `freesound!` builtin). Uses `@settings/freesound-api-key`. Returns a vector
of `{:id :name :duration :tags}` maps. Errors return `{:error "…"}`.

```clojure
(defn freesound-search! [{:keys [query tags page_size]}]
  (let [key @settings/freesound-api-key]
    (if-not key
      (js/Promise.resolve {:error "Freesound API key not set — add it in AI Settings."})
      (-> (js/fetch (str "https://freesound.org/apiv2/search/text/"
                         "?query=" (js/encodeURIComponent (str query (when tags (str " " tags))))
                         "&token=" key
                         "&fields=id,name,duration,tags"
                         "&page_size=" (or page_size 5)))
          (.then #(.json %))
          (.then (fn [data]
                   (let [results (js->clj (.-results data) :keywordize-keys true)]
                     {:results (mapv #(select-keys % [:id :name :duration :tags]) results)})))
          (.catch (fn [e] {:error (str "Freesound error: " (.-message e))}))))))
```

**`freesound-load!`** — fetches preview URL for a single sound ID, calls
`samples/register-url!`, then calls `propose_edit` (via the agent loop's tool dispatch)
to suggest a `(seq :freesound-<id>)` insertion at the cursor position.

```clojure
(defn freesound-load! [{:keys [id]}]
  (let [key @settings/freesound-api-key]
    (if-not key
      (js/Promise.resolve {:error "Freesound API key not set."})
      (-> (js/fetch (str "https://freesound.org/apiv2/sounds/" id "/?token=" key
                         "&fields=id,name,previews"))
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
```

**`web-search!`** — calls the Brave Search API (simple REST, no SDK):

```clojure
(defn web-search! [{:keys [query]}]
  (-> (js/fetch "https://api.search.brave.com/res/v1/web/search"
                #js {:headers #js {"Accept"            "application/json"
                                   "Accept-Encoding"   "gzip"
                                   "X-Subscription-Token" @settings/search-api-key}
                     :method "GET"})
      ;; URL must include ?q= parameter — build it separately
      ;; (use js/URL + js/URLSearchParams for clean encoding)
      (.then #(.json %))
      (.then (fn [data]
               (let [results (js->clj (.. data -web -results) :keywordize-keys true)]
                 {:results (mapv #(select-keys % [:title :url :description]) (take 5 results))})))
      (.catch (fn [e] {:error (str "Search error: " (.-message e))}))))
```

Note: Brave Search requires the full URL with `?q=` included in the `fetch` call above —
build the URL using `(str "https://api.search.brave.com/res/v1/web/search?q=" (js/encodeURIComponent query))`.

### 4. Settings modal — two new fields

In `app/src/repulse/ui/assistant_panel.cljs` or a dedicated settings component, add a
"Sample & Search" section with:
- **Freesound API key** — link to `freesound.org/apiv2/` for key generation
- **Web Search API key (optional)** — link to `brave.com/search/api/` for key generation

Both fields use password-type inputs with a show/hide toggle (same pattern as the
existing AI provider key field). A "Clear" button resets to nil.

No build steps required — no grammar changes, no WASM rebuild.

---

## Files to change

| File | Change |
|------|--------|
| `app/src/repulse/ai/settings.cljs` | Add `freesound-api-key` and `search-api-key` atoms + localStorage persistence |
| `app/src/repulse/ai/tools.cljs` | Add `freesound_search`, `freesound_load`, `list_banks`, `list_samples_in_bank`, `web_search` to tool registry |
| `app/src/repulse/ui/assistant_panel.cljs` | Add "Sample & Search" section to settings modal |

---

## Definition of done

- [ ] Setting a Freesound API key in the AI settings panel persists across reloads (stored as `repulse:ai:freesound-key` in localStorage)
- [ ] `freesound_search` tool call with query `"analog kick 808"` returns ≥1 result with `:id`, `:name`, `:duration` fields when a valid API key is set
- [ ] `freesound_search` returns `{:error "Freesound API key not set …"}` when no key is configured, without throwing
- [ ] `freesound_load(id)` registers the sample and returns `{:ok true :keyword ":freesound-<id>"}` so the agent can suggest `(seq :freesound-<id>)` in a follow-up `propose_edit`
- [ ] After `freesound_load`, evaluating `(seq :freesound-<id>)` in the editor produces audible playback of the loaded sample
- [ ] `list_banks` returns a vector of bank name strings including `"AkaiLinn"` and `"freesound-<id>"` entries for any loaded Freesound sounds
- [ ] `list_samples_in_bank` with bank `"AkaiLinn"` returns a map with `:count` and `:keywords` listing indexed sample keywords
- [ ] When `search-api-key` is nil, `web_search` is not present in the tool descriptor list sent to the model
- [ ] When a valid Brave Search API key is set, `web_search("pentatonic scale")` returns ≥1 result with `:title` and `:url`
- [ ] All four new tools are listed in the tool-call status display in the assistant panel (same style as AI3 tools)
- [ ] `freesound_load` failure (invalid ID, network error) returns `{:error "…"}` and the agent reports the error in the chat — it does not silently stop
- [ ] `npm run test` passes with no regressions in existing AI tool tests

---

## What NOT to do

- Do not auto-apply `freesound_load` without user confirmation — the agent must follow
  the AI3 pattern: propose, wait, apply only on user click.
- Do not add audio preview playback of Freesound results in the assistant panel — that
  belongs to a future sample-browser phase.
- Do not store the Freesound API key in the Lisp environment or `routing.cljs` state;
  AI settings localStorage is the single source of truth for AI3b.
- Do not add Freesound download/purchase flows — only the free HQ MP3 preview URLs.
- Do not add a Freesound browser UI (browse by category, waveform thumbnails) — that is
  a separate visual feature.
- Do not add web search to the system prompt automatically — only trigger it on explicit
  `web_search` tool calls within a turn.
