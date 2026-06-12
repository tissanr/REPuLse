# Phase AI2b — More AI Providers: OpenRouter, Venice.ai, DeepSeek & EUrouter

## Goal

Extend the assistant panel's bring-your-own-key provider list with four new providers,
all OpenAI-compatible — so this phase adds **no new wire format**:

- **OpenRouter** (`openrouter.ai`) — aggregator exposing every major model family,
  including free-tier models, behind a single key
- **Venice.ai** (`venice.ai`) — privacy-focused inference with no conversation retention
- **DeepSeek** (`deepseek.com`) — very cheap direct access to the popular DeepSeek models
- **EUrouter** (`eurouter.ai`) — EU-hosted aggregator with EU data residency (GDPR)

The resulting lineup has a clean logic: direct keys for the majors (Anthropic, OpenAI,
Google, DeepSeek), fast/cheap inference (Groq), a general aggregator (OpenRouter), and
two privacy/residency options (Venice, EUrouter). Other model families (Moonshot/Kimi,
Zhipu/GLM, Alibaba/Qwen, Mistral, …) stay reachable through aggregator model IDs and are
deliberately **not** added as direct providers.

```lisp
;; Before — provider dropdown in the AI settings drawer:
;;   anthropic | openai | google | groq | xai

;; After:
;;   anthropic | openai | google | groq | xai | openrouter | venice | deepseek | eurouter

;; Same Lisp surface — the (ai) built-in works unchanged with the new providers:
(ai "give me a euclidean kick pattern at 130 bpm")

;; Model override examples that must work with provider = openrouter:
;;   anthropic/claude-sonnet-4.5
;;   deepseek/deepseek-chat
;;   meta-llama/llama-3.3-70b-instruct:free
```

---

## Background

### Provider plumbing (delivered in AI2/AI3)

`app/src/repulse/ai/client.cljs` is the single integration point for provider wire
formats:

- `make-request` (line ~142) dispatches on the provider string. OpenAI, Groq, and xAI
  all flow through `openai-like-request` (line ~122): Bearer auth header, system prompt
  as first message, `:tools`/`:tool_choice "auto"` passthrough, `stream: true`.
- `text-from-json` (line ~172) parses streaming deltas; the openai-like providers are
  grouped in one case branch: `("openai" "groq" "xai")`.
- `parse-complete-response` (line ~361) handles non-streaming tool-call responses; the
  **default** branch is already the OpenAI extractor, so openai-like providers need no
  change there.
- `complete-with-retry!` retries HTTP 429 with exponential back-off (3 attempts) —
  important for OpenRouter free-tier models, which rate-limit aggressively.

`app/src/repulse/ai/settings.cljs` — `default-models` map (line ~27) keyed by provider
string; `effective-model` falls back to it when the model override field is blank.

`app/src/repulse/ai/tools.cljs` — `tool-descriptors` (line ~384) builds provider-specific
tool schemas; the `case` default is `reg->openai`, so all four new providers get correct
tool schemas with no change. Only the trailing comment (`; openai, groq, xai`) needs
updating.

`app/src/repulse/ui/assistant_panel.cljs` — the settings drawer builds the provider
`<select>` from a literal vector (line ~229):

```clojure
["anthropic" "openai" "google" "groq" "xai"]
```

### The proxy allowlist — easy to forget, fails loudly

All AI requests are routed through `/api/ai-stream` (`api/ai-stream.ts`), a server-side
proxy that exists because the providers reject cross-origin browser requests. It
validates the target host against a strict SSRF allowlist (line ~18):

```ts
const ALLOWED_HOSTS = new Set([
  "api.anthropic.com",
  "api.openai.com",
  "api.groq.com",
  "api.x.ai",
  "generativelanguage.googleapis.com",
]);
```

A provider wired correctly in the client but missing here fails with HTTP 403
`Host not allowed`. **Client and proxy must change in the same commit.**

### Endpoints

| Provider   | Chat completions URL                               | Auth           |
|------------|----------------------------------------------------|----------------|
| OpenRouter | `https://openrouter.ai/api/v1/chat/completions`    | `Bearer <key>` |
| Venice.ai  | `https://api.venice.ai/api/v1/chat/completions`    | `Bearer <key>` |
| DeepSeek   | `https://api.deepseek.com/chat/completions`        | `Bearer <key>` |
| EUrouter   | `https://api.eurouter.ai/v1/chat/completions` (?)  | `Bearer <key>` |

All four are OpenAI-compatible: same request body, same SSE `data:` streaming format
with `[DONE]` terminator, same `choices[0].delta.content` / `choices[0].message.content`
shapes, same `tools` array and `tool_calls` response format.

**Verify the EUrouter base URL and hostname against their live docs at implementation
time** — it is the least-established of the four and marked `?` above.

### Docs

`docs/USAGE.md` documents the provider list (line ~1352), default models (line ~1362),
and a provider-comparison note (line ~1364, "Groq vs xAI"). No new Lisp built-in is
added, so the grammar / completions / `builtin_meta.edn` / `gen:ai-docs` checklist
(CLAUDE.md Rule 5) does **not** apply to this phase.

---

## Implementation

### 1. `app/src/repulse/ai/client.cljs` — data-driven openai-like dispatch

With seven openai-like providers, individual `case` branches stop being the clean
option. Replace them with a small endpoint map:

```clojure
(def ^:private openai-like-endpoints
  {"openai"     "https://api.openai.com/v1/chat/completions"
   "groq"       "https://api.groq.com/openai/v1/chat/completions"
   "xai"        "https://api.x.ai/v1/chat/completions"
   "openrouter" "https://openrouter.ai/api/v1/chat/completions"
   "venice"     "https://api.venice.ai/api/v1/chat/completions"
   "deepseek"   "https://api.deepseek.com/chat/completions"
   "eurouter"   "https://api.eurouter.ai/v1/chat/completions"})  ; verify URL
```

`make-request` becomes: anthropic branch, google branch, then
`(if-let [url (openai-like-endpoints provider)] (openai-like-request url ...) (throw ...))`.

`text-from-json` (and any other `("openai" "groq" "xai")` case group — grep for it):
replace the case-group match with a `(contains? openai-like-endpoints provider)` check,
e.g. restructure the `case` into a `cond`. `parse-complete-response` and the SSE parser
need no changes (OpenAI shape is the default path).

**OpenRouter attribution headers (optional but recommended):** OpenRouter asks apps to
send `HTTP-Referer` and `X-Title` headers for attribution. Add them only when
`provider = "openrouter"`:

```clojure
(update req :headers merge {"HTTP-Referer" "https://repulse.app"
                            "X-Title"      "REPuLse"})
```

(Use the real production origin; check `api/_lib.ts` `originAllowed` for the canonical
domain.)

### 2. `app/src/repulse/ai/settings.cljs`

Add defaults to `default-models`:

```clojure
"openrouter" "openrouter/auto"
"venice"     "llama-3.3-70b"
"deepseek"   "deepseek-chat"
"eurouter"   "?"               ; verify — likely OpenRouter-style slash IDs
```

`openrouter/auto` delegates model choice to OpenRouter's router — a sensible blank-field
default. **Verify the Venice default model ID against their live model list**
(`GET https://api.venice.ai/api/v1/models`) **and the EUrouter default against their
docs at implementation time** — both catalogues change and a stale ID fails with a 4xx.
`deepseek-chat` is DeepSeek's stable alias for their current flagship chat model.

### 3. `app/src/repulse/ui/assistant_panel.cljs`

Extend the dropdown vector (line ~229):

```clojure
["anthropic" "openai" "google" "groq" "xai" "openrouter" "venice" "deepseek" "eurouter"]
```

The provider badge and settings persistence work off `@settings/provider` strings —
no other panel changes.

### 4. `api/ai-stream.ts`

Add to `ALLOWED_HOSTS`:

```ts
"openrouter.ai",
"api.venice.ai",
"api.deepseek.com",
"api.eurouter.ai",   // verify hostname against live EUrouter docs
```

Update the file's doc comment listing the providers the proxy serves.

### 5. `app/src/repulse/ai/tools.cljs`

Update the trailing comment on `tool-descriptors`'s default branch to
`; all openai-compatible providers`. No code change.

### 6. `docs/USAGE.md`

- Provider list (line ~1352): add `openrouter`, `venice`, `deepseek`, `eurouter`
- Default models line (~1362): add all four defaults
- Extend the provider-comparison note: OpenRouter = one key for every model family,
  free-tier models, keys at [openrouter.ai/keys](https://openrouter.ai/keys);
  Venice = privacy-focused, no conversation retention, keys at
  [venice.ai/settings/api](https://venice.ai/settings/api); DeepSeek = cheap direct
  access, keys at [platform.deepseek.com](https://platform.deepseek.com);
  EUrouter = EU data residency aggregator, keys at [eurouter.ai](https://eurouter.ai)
- Note that Venice tool/function-calling support is **model-dependent**: with a
  non-tool model the assistant falls back to plain chat (the AI3 agent loop degrades
  to text answers)

### 7. Manual verification (required — cannot be automated)

Streaming, tool calls, and 429 retry must be exercised against the live APIs with real
keys for **each** of the four providers. There is no mock that proves compatibility;
this is the same "human verification loop" principle as DSP preset tuning. If a key
for one provider cannot be obtained, that provider must be dropped from the phase
rather than shipped unverified.

No WASM, grammar, or AI-docs build steps are needed. Run `npm test` and
`npx shadow-cljs compile app` before pushing (the panel/client files are app-layer and
only compile under the `:app` target).

---

## Files to change

| File | Change |
|------|--------|
| `app/src/repulse/ai/client.cljs` | `openai-like-endpoints` map replaces per-provider case branches; openai-like `case` groups become map-membership checks; OpenRouter attribution headers |
| `app/src/repulse/ai/settings.cljs` | `default-models` entries for all four providers |
| `app/src/repulse/ui/assistant_panel.cljs` | provider dropdown vector + four entries |
| `api/ai-stream.ts` | `ALLOWED_HOSTS` += `openrouter.ai`, `api.venice.ai`, `api.deepseek.com`, `api.eurouter.ai`; doc comment |
| `app/src/repulse/ai/tools.cljs` | comment-only: default schema branch covers the new providers |
| `docs/USAGE.md` | provider list, default models, key-signup links, Venice tool-support caveat |
| `npm test` + `npx shadow-cljs compile app` | pre-push verification (no WASM/grammar steps needed) |

---

## Definition of done

- [ ] Provider dropdown shows `openrouter`, `venice`, `deepseek`, and `eurouter`;
      selection persists across reload via `repulse:ai:provider` localStorage key
- [ ] With provider `openrouter` and a valid key, `(ai "give me a euclidean kick pattern")`
      opens the panel and submitting streams a response token-by-token
- [ ] With provider `venice` and a valid key, `(ai "make a 4-bar hi-hat groove")`
      streams a response token-by-token
- [ ] With provider `deepseek` and a valid key, `(ai "write a polyrhythmic clave pattern")`
      streams a response token-by-token
- [ ] With provider `eurouter` and a valid key, streaming works end-to-end (endpoint
      and default model verified against live EUrouter docs first)
- [ ] Blank model override uses each provider's `default-models` entry; the provider
      badge shows e.g. `openrouter · openrouter/auto`, `deepseek · deepseek-chat`
- [ ] Model override `meta-llama/llama-3.3-70b-instruct:free` works on OpenRouter
      (slash-and-colon model IDs survive the request path unmangled)
- [ ] AI3 agent loop works on OpenRouter with a tools-capable model: asking
      `(ai "read my buffer and double the tempo of the kick track")` produces
      `read_buffer` → `propose_edit` tool calls and an Apply/Reject diff card
- [ ] AI3 agent loop tool calls verified on DeepSeek (`deepseek-chat` supports
      OpenAI-style function calling)
- [ ] Venice with a non-tool-capable model degrades gracefully to plain chat —
      no unhandled error in the agent loop
- [ ] `/api/ai-stream` accepts all four new hosts and still returns 403 for any
      non-allowlisted host (no allowlist regression)
- [ ] OpenRouter requests include `HTTP-Referer` and `X-Title` headers
- [ ] HTTP 429 from OpenRouter triggers the existing exponential back-off retry and
      surfaces the inline "rate limited" message after 3 failed attempts
- [ ] All five existing providers still stream correctly (regression check on at least
      one: e.g. anthropic)
- [ ] `docs/USAGE.md` updated: provider list, default models, signup links, Venice
      tool-support caveat
- [ ] `npm test` and `npx shadow-cljs compile app` pass

---

## What NOT to do

- **No local model support (Ollama / LM Studio).** The `/api/ai-stream` proxy cannot
  reach the user's `localhost` and rejects non-HTTPS URLs; local inference needs a
  direct-fetch bypass path and is a separate architecture decision — file it as its
  own phase idea if wanted.
- **No further hosted providers** (Mistral, Together, Fireworks, Requesty, Moonshot,
  Zhipu/GLM, Alibaba/Qwen, Opper, …). The aggregators cover their models; Requesty is
  a second OpenRouter; Opper's primary API is task-based rather than chat-completions
  and would need a new wire format. Nine providers is the ceiling for this phase.
- **No per-provider model picker UI.** The free-text model override field is the
  existing pattern; don't fetch and render OpenRouter's 300-model catalogue.
- **No server-side key storage.** Keys stay in localStorage — encrypted key relay is
  Phase AI4b. (When AI4b lands, its provider enum must include all four new providers.)
- **No wire-format abstraction beyond the endpoint map.** The `openai-like-endpoints`
  map is as far as the refactor goes — anthropic and google keep their dedicated
  request builders and parsers; don't unify the three formats behind a protocol.
