# Phase AI2b — Live Model Catalogue, OpenRouter & Mistral

## Goal

Stop hard-coding model IDs that go stale, and add the two providers that are still
worth adding as direct integrations:

1. **Live model catalogue** — the assistant fetches each provider's model list
   (`GET …/models`), caches it, refreshes it regularly, and offers it as suggestions
   in the model field. The default model for a provider is picked from what the
   provider actually serves today, not from a constant written months ago.
2. **OpenRouter** (`openrouter.ai`) — one key for almost every model family
   (467 models at the time of writing), free-tier models, and a public catalogue that
   reports which models support tool calling.
3. **Mistral** (`mistral.ai`) — an established EU provider with first-party models,
   OpenAI-compatible chat completions and function calling. This is the EU /
   data-residency option.

Both new providers are OpenAI-compatible — this phase adds **no new wire format**.

```lisp
;; Before — provider dropdown:   anthropic | openai | google | groq | xai
;; After:                        anthropic | openai | google | groq | xai | openrouter | mistral

;; Same Lisp surface — the (ai) built-in works unchanged:
(ai "give me a euclidean kick pattern at 130 bpm")

;; Model field: free text as before, now with a suggestion list fetched from the
;; provider. Slash-and-colon IDs must survive the request path unmangled:
;;   anthropic/claude-sonnet-4.5
;;   meta-llama/llama-3.3-70b-instruct:free
```

---

## Why the scope changed (October 2026 review)

The first draft of this phase (June 2026) planned four providers: OpenRouter, Venice.ai,
DeepSeek and EUrouter. A review four months later found:

- **The existing defaults are already stale.** `settings.cljs` ships `gpt-4o`,
  `gemini-2.0-flash` and `grok-3`. Any constant we write today will age the same
  way. A live catalogue fixes the cause rather than the symptom.
- **DeepSeek** — the planned default alias `deepseek-chat` was announced for
  deprecation (24 July 2026) in favour of the V4 models. DeepSeek is reachable through
  OpenRouter, so a direct integration adds a provider to maintain for little gain.
  **Dropped.**
- **Venice.ai** — tool calling is model-dependent, which conflicts with the AI3 agent
  loop (it needs tools), and its privacy claims are mostly documented by third
  parties. Reachable through OpenRouter for users who want it. **Dropped.**
- **EUrouter** — a small vendor whose own pages disagree on the base URL
  (`/api/v1` vs `/v1`). For an EU option, Mistral is the stronger, first-party
  choice. **Replaced by Mistral.**
- **Custom / local endpoints** (LM Studio, Ollama, vLLM, …) — the most-requested
  power-user feature, but it needs direct browser fetches that bypass the proxy
  allowlist and a CSP change. That is an architecture decision of its own and lives
  in **[Phase AI2c](PHASE-AI2c.md)**.

---

## Background

### Provider plumbing (delivered in AI2/AI3/AI4)

`app/src/repulse/ai/client.cljs` is the single integration point for wire formats:

- `build-request` (line ~135) is a pure function `{:provider :key :model}` →
  `{:url :headers :body}`. It `case`-dispatches on the provider; `openai`, `groq` and
  `xai` each call `openai-like-request` with their own URL.
- `complete!` posts the envelope to `/api/ai-stream`; `parse-complete-response` and
  `extract-usage` use the OpenAI shape as their **default** branch, so new
  OpenAI-compatible providers need no parser changes.
- `complete-with-retry!` retries HTTP 429 with exponential back-off (3 attempts) —
  important for OpenRouter free-tier models, which rate-limit aggressively.

`app/src/repulse/ai/settings.cljs` — localStorage-backed atoms (`provider`, `api-key`,
`model-override`, …) and the `default-models` map (line ~29); `effective-model` falls
back to it when the model field is blank.

`app/src/repulse/ai/tools.cljs` — `tool-descriptors` builds provider-specific tool
schemas; the `case` default is `reg->openai`, so both new providers get correct tool
schemas with no code change.

`app/src/repulse/ui/assistant_panel.cljs` — the settings drawer is built from HTML
strings. The provider `<select>` comes from a literal vector (line ~237); the model
field is a plain `<input>` with the hint "Leave blank for provider default".

### The proxy — `/api/ai-stream`

All provider traffic goes through `api/ai-stream.ts`, because the providers reject
cross-origin browser requests. It is **POST-only**, checks the request `Origin`
(`originAllowed` in `api/_lib.ts`), and validates the target host against
`ALLOWED_HOSTS`. A host missing from the allowlist fails with HTTP 403
`Host not allowed`. **Client and proxy must change in the same commit.**

The model catalogue needs `GET` requests, which this proxy does not forward.

### Model-list endpoints (all verified to exist, October 2026)

| Provider   | List endpoint                                            | Auth                               | Useful metadata |
|------------|----------------------------------------------------------|------------------------------------|-----------------|
| anthropic  | `https://api.anthropic.com/v1/models`                    | `x-api-key` + `anthropic-version`  | `id`, `display_name`, `created_at` |
| openai     | `https://api.openai.com/v1/models`                       | Bearer                             | `id`, `created` only — no capability flags |
| google     | `https://generativelanguage.googleapis.com/v1beta/models`| `?key=` (or `x-goog-api-key`)      | `name`, `supportedGenerationMethods` |
| groq       | `https://api.groq.com/openai/v1/models`                  | Bearer                             | `id`, `active`, `context_window` |
| xai        | `https://api.x.ai/v1/models`                             | Bearer                             | `id`, `created` |
| openrouter | `https://openrouter.ai/api/v1/models`                    | **none — public**                  | `id`, `supported_parameters` (contains `"tools"`), `pricing`, `context_length` |
| mistral    | `https://api.mistral.ai/v1/models`                       | Bearer                             | `id`, `capabilities.function_calling` |

All of them except OpenRouter return 401/403 without a key, so the catalogue for those
providers can only be fetched once a key is set.

### Chat endpoints for the new providers

| Provider   | Chat completions URL                              | Auth           |
|------------|---------------------------------------------------|----------------|
| OpenRouter | `https://openrouter.ai/api/v1/chat/completions`   | `Bearer <key>` |
| Mistral    | `https://api.mistral.ai/v1/chat/completions`      | `Bearer <key>` |

---

## Implementation

### 1. `api/ai-models.ts` — GET-only catalogue proxy (new)

A separate, deliberately narrow handler rather than a `method` switch on
`/api/ai-stream`:

- Accepts `POST { url, headers }` from the browser (POST so the key stays out of the
  query string and server logs), performs a **`GET`** upstream.
- Same `originAllowed` check as `ai-stream.ts`.
- Same host allowlist — move `ALLOWED_HOSTS` into `api/_lib.ts` as
  `AI_ALLOWED_HOSTS` and import it from both handlers so they can never drift.
- **Path restriction:** only URLs whose pathname ends in `/models` are forwarded.
  Anything else → 403. This keeps the GET path from becoming a general fetcher.
- HTTPS only; hop-by-hop headers stripped (reuse the `HOP_BY_HOP` set — move it to
  `_lib.ts` too).
- Response size cap (e.g. 2 MB — OpenRouter's catalogue is a few hundred KB); return
  502 if exceeded.
- Keys are never logged.

### 2. `api/ai-stream.ts`

- Import the shared allowlist from `_lib.ts`.
- Add `openrouter.ai` and `api.mistral.ai`.
- Update the doc comment listing the providers.

### 3. `app/src/repulse/ai/client.cljs` — endpoint table

Replace the per-provider `case` branches for OpenAI-compatible providers with one map
that holds both endpoints per provider:

```clojure
(def openai-like
  {"openai"     {:chat   "https://api.openai.com/v1/chat/completions"
                 :models "https://api.openai.com/v1/models"}
   "groq"       {:chat   "https://api.groq.com/openai/v1/chat/completions"
                 :models "https://api.groq.com/openai/v1/models"}
   "xai"        {:chat   "https://api.x.ai/v1/chat/completions"
                 :models "https://api.x.ai/v1/models"}
   "openrouter" {:chat   "https://openrouter.ai/api/v1/chat/completions"
                 :models "https://openrouter.ai/api/v1/models"}
   "mistral"    {:chat   "https://api.mistral.ai/v1/chat/completions"
                 :models "https://api.mistral.ai/v1/models"}})
```

`build-request` becomes: `"anthropic"` branch, `"google"` branch, then
`(if-let [{:keys [chat]} (openai-like provider)] (openai-like-request chat …) (throw …))`.

**OpenRouter attribution headers:** when `provider = "openrouter"`, merge
`HTTP-Referer` (the production origin — check `ALLOWED_ORIGINS` / the Vercel project
domain) and `X-Title "REPuLse"` into the request headers. OpenRouter's CORS preflight
currently also lists `X-Openrouter-Title`; `X-Title` remains accepted.

Add a pure `models-request` function next to `build-request`:
`{:provider :key}` → `{:url :headers}` for the list endpoint (anthropic and google get
their own branches, the rest come from `openai-like`). Keep it pure so it is unit
testable like `build-request`.

### 4. `app/src/repulse/ai/models.cljs` — catalogue (new namespace)

Responsible for fetching, normalising, caching and choosing models. Keeps
`client.cljs` focused on chat.

**Normalised model record:**

```clojure
{:id     "mistral-medium-latest"
 :label  "Mistral Medium"   ; display_name where the provider gives one, else id
 :tools? true               ; true / false / nil (= provider doesn't say)
 :created 1747000000}       ; for "newest first" sorting; nil if unknown
```

**Per-provider normalisers** (pure, unit tested with captured fixture JSON):

- `openrouter` — `:tools?` from `supported_parameters` containing `"tools"`.
- `mistral` — `:tools?` from `capabilities.function_calling`.
- `google` — keep only models whose `supportedGenerationMethods` contains
  `generateContent`; strip the `models/` prefix from `name`.
- `groq` — drop `active: false`.
- `openai` — no capability flags; filter out obvious non-chat families by ID
  (`embedding`, `whisper`, `tts`, `dall-e`, `moderation`, `image`, `audio`,
  `realtime`, `transcribe`). `:tools?` = nil.
- `anthropic`, `xai` — keep all; `:tools?` = nil.

**Cache:** localStorage key `repulse:ai:models:<provider>` →
`{:fetched-at <ms> :models [...]}`. TTL **24 hours**.

**Refresh triggers** (this is the "initialised regularly" part):

- When the assistant panel opens and the cached list is older than the TTL.
- When the provider changes, or the API key changes (debounced ~800 ms after typing
  stops) — a new key may see different models.
- A manual **↻ refresh** button next to the model field ignores the TTL.
- OpenRouter needs no key, so its catalogue is fetched even before a key is entered.

Failures are non-fatal: keep the previous cache, show a small "couldn't load models"
hint, and fall back to the static defaults. Never block chat on the catalogue.

**Default model selection** — `pick-default [provider models]`:

`settings/default-models` stays as the **offline fallback**. Add a
`preferred-models` map of *ordered prefix patterns* per provider, e.g.

```clojure
(def preferred-models
  {"anthropic"  ["claude-sonnet-" "claude-opus-" "claude-haiku-"]
   "openrouter" ["openrouter/auto"]
   "mistral"    ["mistral-medium-latest" "mistral-large-latest" "mistral-small-latest"]
   ...})
```

`pick-default` returns the first catalogue entry that matches the first pattern that
matches anything, preferring the newest `:created`, and prefers `:tools? true` over
`nil` over `false`. If nothing matches, it returns the static fallback.
`effective-model` becomes: override → `pick-default` from cache → static fallback.
The provider badge keeps showing the resolved ID.

Fill in `preferred-models` and refresh the static `default-models` fallbacks **against
each provider's live list at implementation time** — do not copy IDs from this
document or from memory.

### 5. `app/src/repulse/ai/settings.cljs`

- Add `"openrouter"` and `"mistral"` fallbacks to `default-models`
  (`openrouter/auto` is OpenRouter's own router and a safe fallback).
- Refresh the stale fallbacks for `openai`, `google` and `xai`.
- `effective-model` delegates to `models/pick-default` as described above.

### 6. `app/src/repulse/ui/assistant_panel.cljs`

- Provider vector: `["anthropic" "openai" "google" "groq" "xai" "openrouter" "mistral"]`.
- Model field gets `list="ai-model-list"` plus a `<datalist id="ai-model-list">`
  populated from the cache. Free text still works — the datalist only suggests.
  Escape every ID/label with `escape-html` (catalogue data is third-party input).
- Mark models with `:tools? false` in the label (e.g. `… (no tools)`): the AI3 agent
  loop needs tool calling, so these models only work for plain chat.
- Hint line under the field: `"N models · updated 3 h ago"` with the ↻ button;
  `"Leave blank for <resolved default>"` instead of the generic text.
- If the saved override is not in a freshly fetched list, show a non-blocking
  warning (`"not in <provider>'s current list"`). Don't clear it — the user may know
  better, or the list may be filtered.

### 7. `app/src/repulse/ai/tools.cljs`

Comment-only: the default schema branch now reads
`; all openai-compatible providers`.

### 8. Tests

- `client_test.cljs` — `build-request` for `openrouter` (URL, Bearer, attribution
  headers) and `mistral`; `models-request` for every provider.
- `models_test.cljs` (new) — each normaliser against a small captured fixture;
  `pick-default` ordering (pattern order, newest first, tools preference, fallback when
  the catalogue is empty); TTL check.
- Add the new test namespace to the `:test` build if the runner needs it listed.

### 9. `docs/USAGE.md`

- Provider list and default-model text: describe the live catalogue, the 24 h refresh,
  the ↻ button, and the static fallback.
- Provider notes: OpenRouter = one key for every model family, free-tier models
  (rate-limited), keys at [openrouter.ai/keys](https://openrouter.ai/keys);
  Mistral = EU-based provider, keys at
  [console.mistral.ai](https://console.mistral.ai).
- Note that models marked "(no tools)" cannot run the agent tools (read buffer,
  propose edit, …) and fall back to plain chat.

No new Lisp built-in is added, so the grammar / completions / `builtin_meta.edn` /
`gen:ai-docs` checklist (CLAUDE.md Rule 5) does **not** apply.

### 10. Manual verification (required — cannot be automated)

Chat, tool calls, catalogue fetch and 429 retry must be exercised against the live
APIs with real keys for OpenRouter and Mistral, plus at least one existing provider
(anthropic) as a regression check. If a key for a new provider cannot be obtained, drop
that provider from the phase rather than ship it unverified.

---

## Files to change

| File | Change |
|------|--------|
| `api/_lib.ts` | shared `AI_ALLOWED_HOSTS` + `HOP_BY_HOP` |
| `api/ai-models.ts` | **new** — GET-only `/models` proxy, path-restricted, size-capped |
| `api/ai-stream.ts` | import shared allowlist; `+ openrouter.ai`, `api.mistral.ai`; doc comment |
| `app/src/repulse/ai/client.cljs` | `openai-like` endpoint table; `models-request`; OpenRouter attribution headers |
| `app/src/repulse/ai/models.cljs` | **new** — fetch, normalise, cache (24 h TTL), `pick-default` |
| `app/src/repulse/ai/models_test.cljs` | **new** — normalisers, `pick-default`, TTL |
| `app/src/repulse/ai/client_test.cljs` | new provider + `models-request` cases |
| `app/src/repulse/ai/settings.cljs` | fallback defaults refreshed + two new; `effective-model` via catalogue |
| `app/src/repulse/ui/assistant_panel.cljs` | provider vector; model `<datalist>`, ↻ refresh, status hint, stale-override warning |
| `app/src/repulse/ai/tools.cljs` | comment only |
| `docs/USAGE.md` | providers, live catalogue, signup links, "(no tools)" caveat |

---

## Definition of done

- [ ] Provider dropdown shows `openrouter` and `mistral`; selection persists across
      reload via `repulse:ai:provider`
- [ ] Opening the panel with a stale or empty cache fetches the provider's model list;
      the model field offers it as suggestions; "N models · updated …" is shown
- [ ] OpenRouter's list loads with **no key set**
- [ ] Changing provider or key refetches; ↻ forces a refetch inside the TTL
- [ ] A failed catalogue fetch leaves chat working with the static fallback model and
      shows a non-blocking hint
- [ ] Blank model field resolves to a model **from the live list** for every provider
      that has a key; the badge shows the resolved ID
- [ ] Models with `:tools? false` are labelled "(no tools)"
- [ ] A saved override missing from the fresh list shows a warning and is kept
- [ ] `/api/ai-models` rejects non-allowlisted hosts (403), non-`/models` paths (403),
      non-HTTPS URLs (403) and foreign origins (403)
- [ ] `/api/ai-stream` accepts both new hosts and still 403s everything else
- [ ] With provider `openrouter`: a chat answer arrives; model override
      `meta-llama/llama-3.3-70b-instruct:free` works (IDs survive unmangled);
      requests carry `HTTP-Referer` and `X-Title`
- [ ] AI3 agent loop on OpenRouter with a tools-capable model:
      `(ai "read my buffer and double the tempo of the kick track")` produces
      `read_buffer` → `propose_edit` and an Apply/Reject diff card
- [ ] With provider `mistral`: chat works and the same agent-loop prompt produces tool
      calls
- [ ] HTTP 429 from OpenRouter triggers the existing back-off and surfaces the
      "rate limited" message after 3 attempts
- [ ] Anthropic still works end-to-end (regression)
- [ ] `docs/USAGE.md` updated
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass

---

## What NOT to do

- **No custom / local endpoints here** (LM Studio, Ollama, vLLM, LiteLLM, …) — that is
  [Phase AI2c](PHASE-AI2c.md).
- **No more direct providers** (DeepSeek, Venice, EUrouter, Together, Fireworks,
  Moonshot, Zhipu, Qwen, …). OpenRouter covers them. Seven providers is the ceiling
  for this phase.
- **No rich model-browser UI** — no pricing tables, search dialog or capability
  matrix. A `<datalist>` on the existing free-text field is the whole UI.
- **No server-side catalogue cache.** Lists are fetched with the user's own key and
  cached in their browser; a shared cache would mean server-side keys (that's AI4b's
  territory).
- **No server-side key storage.** Keys stay in localStorage. When AI4b lands, its
  provider enum must include `openrouter` and `mistral`.
- **No wire-format abstraction beyond the endpoint table.** anthropic and google keep
  their dedicated builders and parsers.
