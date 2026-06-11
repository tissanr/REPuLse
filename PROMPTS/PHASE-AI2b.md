# Phase AI2b — More AI Providers: OpenRouter & Venice.ai

## Goal

Extend the assistant panel's bring-your-own-key provider list with two new providers:
**OpenRouter** (`openrouter.ai` — an aggregator exposing every major model family,
including free-tier models, behind a single key) and **Venice.ai** (`venice.ai` —
privacy-focused inference with no conversation retention). Both expose OpenAI-compatible
chat-completions APIs, so this phase adds **no new wire format** — it registers two more
openai-like providers across the client, settings, UI dropdown, proxy host allowlist,
and docs. OpenRouter in particular lowers the entry barrier dramatically: one key
unlocks Claude, GPT, Gemini, Llama, DeepSeek, Qwen and free community models.

```lisp
;; Before — provider dropdown in the AI settings drawer:
;;   anthropic | openai | google | groq | xai

;; After:
;;   anthropic | openai | google | groq | xai | openrouter | venice

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
tool schemas; the `case` default is `reg->openai`, so OpenRouter and Venice get correct
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

| Provider   | Chat completions URL                             | Auth           |
|------------|--------------------------------------------------|----------------|
| OpenRouter | `https://openrouter.ai/api/v1/chat/completions`  | `Bearer <key>` |
| Venice.ai  | `https://api.venice.ai/api/v1/chat/completions`  | `Bearer <key>` |

Both are OpenAI-compatible: same request body, same SSE `data:` streaming format with
`[DONE]` terminator, same `choices[0].delta.content` / `choices[0].message.content`
shapes, same `tools` array and `tool_calls` response format.

### Docs

`docs/USAGE.md` documents the provider list (line ~1352), default models (line ~1362),
and a provider-comparison note (line ~1364, "Groq vs xAI"). No new Lisp built-in is
added, so the grammar / completions / `builtin_meta.edn` / `gen:ai-docs` checklist
(CLAUDE.md Rule 5) does **not** apply to this phase.

---

## Implementation

### 1. `app/src/repulse/ai/client.cljs`

Add two branches to `make-request`:

```clojure
"openrouter" (openai-like-request "https://openrouter.ai/api/v1/chat/completions"
                                  system messages model key tools)
"venice"     (openai-like-request "https://api.venice.ai/api/v1/chat/completions"
                                  system messages model key tools)
```

Extend the openai-like group in `text-from-json`:

```clojure
("openai" "groq" "xai" "openrouter" "venice") (let [choice ...] ...)
```

Grep for any other `("openai" "groq" "xai")` groups and extend them identically.
`parse-complete-response` and the SSE parser need no changes (OpenAI shape is the
default path).

**OpenRouter attribution headers (optional but recommended):** OpenRouter asks apps to
send `HTTP-Referer` and `X-Title` headers for attribution. Add them only in the
openrouter branch — e.g. give `openai-like-request` an optional `extra-headers` arg, or
merge into the headers map after construction:

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
```

`openrouter/auto` delegates model choice to OpenRouter's router — a sensible blank-field
default. **Verify the Venice default model ID against their live model list at
implementation time** (`GET https://api.venice.ai/api/v1/models`); their catalogue
changes and a stale ID fails with a 4xx.

### 3. `app/src/repulse/ui/assistant_panel.cljs`

Extend the dropdown vector (line ~229):

```clojure
["anthropic" "openai" "google" "groq" "xai" "openrouter" "venice"]
```

The provider badge and settings persistence work off `@settings/provider` strings —
no other panel changes.

### 4. `api/ai-stream.ts`

Add to `ALLOWED_HOSTS`:

```ts
"openrouter.ai",
"api.venice.ai",
```

Update the file's doc comment listing the providers the proxy serves.

### 5. `app/src/repulse/ai/tools.cljs`

Update the trailing comment on `tool-descriptors`'s default branch to
`; openai, groq, xai, openrouter, venice`. No code change.

### 6. `docs/USAGE.md`

- Provider list (line ~1352): add `openrouter`, `venice`
- Default models line (~1362): add both defaults
- Extend the provider-comparison note: OpenRouter = one key for every model family,
  free-tier models available at [openrouter.ai/keys](https://openrouter.ai/keys);
  Venice = privacy-focused, no conversation retention, keys at
  [venice.ai/settings/api](https://venice.ai/settings/api)
- Note that Venice tool/function-calling support is **model-dependent**: with a
  non-tool model the assistant falls back to plain chat (the AI3 agent loop degrades
  to text answers)

### 7. Manual verification (required — cannot be automated)

Streaming, tool calls, and 429 retry must be exercised against the live APIs with real
keys for both providers. There is no mock that proves compatibility; this is the same
"human verification loop" principle as DSP preset tuning.

No WASM, grammar, or AI-docs build steps are needed. Run `npm test` and
`npx shadow-cljs compile app` before pushing (the panel/client files are app-layer and
only compile under the `:app` target).

---

## Files to change

| File | Change |
|------|--------|
| `app/src/repulse/ai/client.cljs` | `make-request` branches for `"openrouter"` / `"venice"`; extend openai-like `case` groups; OpenRouter attribution headers |
| `app/src/repulse/ai/settings.cljs` | `default-models` entries for both providers |
| `app/src/repulse/ui/assistant_panel.cljs` | provider dropdown vector + both entries |
| `api/ai-stream.ts` | `ALLOWED_HOSTS` += `openrouter.ai`, `api.venice.ai`; doc comment |
| `app/src/repulse/ai/tools.cljs` | comment-only: default schema branch covers the new providers |
| `docs/USAGE.md` | provider list, default models, key-signup links, Venice tool-support caveat |
| `npm test` + `npx shadow-cljs compile app` | pre-push verification (no WASM/grammar steps needed) |

---

## Definition of done

- [ ] Provider dropdown shows `openrouter` and `venice`; selection persists across
      reload via `repulse:ai:provider` localStorage key
- [ ] With provider `openrouter` and a valid key, `(ai "give me a euclidean kick pattern")`
      opens the panel and submitting streams a response token-by-token
- [ ] With provider `venice` and a valid key, `(ai "make a 4-bar hi-hat groove")`
      streams a response token-by-token
- [ ] Blank model override uses `openrouter/auto` (OpenRouter) and the verified Venice
      default; the provider badge shows `openrouter · openrouter/auto`
- [ ] Model override `meta-llama/llama-3.3-70b-instruct:free` works on OpenRouter
      (slash-and-colon model IDs survive the request path unmangled)
- [ ] AI3 agent loop works on OpenRouter with a tools-capable model: asking
      `(ai "read my buffer and double the tempo of the kick track")` produces
      `read_buffer` → `propose_edit` tool calls and an Apply/Reject diff card
- [ ] Venice with a non-tool-capable model degrades gracefully to plain chat —
      no unhandled error in the agent loop
- [ ] `/api/ai-stream` accepts `openrouter.ai` and `api.venice.ai` targets and still
      returns 403 for any non-allowlisted host (no allowlist regression)
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
- **No further hosted providers** (Mistral, DeepSeek, Together, Fireworks, …).
  OpenRouter aggregates all of them; adding them individually is redundant
  maintenance surface.
- **No per-provider model picker UI.** The free-text model override field is the
  existing pattern; don't fetch and render OpenRouter's 300-model catalogue.
- **No server-side key storage.** Keys stay in localStorage — encrypted key relay is
  Phase AI4b. (When AI4b lands, its provider enum must include these two.)
- **No new wire-format abstraction.** Resist refactoring `make-request` into a
  provider registry; two more `case` branches do not justify it.
