# Phase AI2c — Custom & Local AI Endpoints

## Goal

Add a `custom` provider that talks to **any OpenAI-compatible endpoint the user
names**: a local model server (LM Studio, Ollama, llama.cpp, vLLM), a self-hosted
gateway (LiteLLM), or a hosted provider REPuLse doesn't list (Together, Fireworks,
DeepSeek, …). One feature covers all of them, and local models make the assistant free
and fully offline-capable.

```text
Provider:   custom
Base URL:   http://localhost:1234/v1          ← LM Studio
API key:    (optional — blank for local servers)
Model:      [ qwen3-coder-30b  ▾ ]            ← fetched from <base>/models (AI2b catalogue)
            [ Test connection ]  ✓ 4 models · tools: on
```

```lisp
;; Same Lisp surface — nothing changes for the user's code:
(ai "write a dub techno chord stab pattern")
```

**Depends on [Phase AI2b](PHASE-AI2b.md)** (endpoint table, `models-request`, model
catalogue and datalist UI). Implement AI2b first.

---

## Why this is its own phase

Every other provider goes through the `/api/ai-stream` server proxy, whose host
allowlist is the SSRF defence. A user-supplied URL **cannot** go through that proxy:

- Allowing arbitrary hosts would turn the proxy into an open relay / SSRF vector.
- The proxy runs on Vercel and could never reach the user's `localhost` anyway.

So `custom` requests are made **directly from the browser** to the user's endpoint.
That is a different trust and transport model, with three consequences that need
their own design work:

1. **CORS** — the endpoint must allow the REPuLse origin. Local servers usually need a
   flag or setting for this.
2. **CSP** — production CSP (`vercel.json`) has `connect-src 'self' https: wss:`.
   That allows remote HTTPS endpoints but **blocks `http://localhost`**.
3. **Browser local-network rules** — Chrome's Local Network Access asks the user for
   permission when a public site connects to `localhost`/LAN addresses. Plain-HTTP LAN
   addresses (`http://192.168.x.x`) are blocked as mixed content on an HTTPS page;
   `localhost`/`127.0.0.1`/`[::1]` are exempt because browsers treat them as
   potentially trustworthy.

---

## Background

- `app/src/repulse/ai/client.cljs` — after AI2b: `openai-like` endpoint table,
  pure `build-request` / `models-request`, and `complete!`, which **always** posts to
  `/api/ai-stream`. Responses use the OpenAI shape (default parser branch).
- `app/src/repulse/ai/models.cljs` (AI2b) — fetch, normalise, cache, `pick-default`.
  Its fetches go through `/api/ai-models`.
- `app/src/repulse/ai/agent_loop.cljs` — the AI3 tool loop. It assumes the model
  either answers in text or returns well-formed `tool_calls`.
- `app/src/repulse/ai/budget.cljs` — AI4 token budget; relies on `:usage` when the
  provider reports it.
- `vercel.json` — production headers including the CSP.
- AI4 safety (injection guard, budget, approval path) applies to every provider and
  must keep applying to `custom`.

---

## Implementation

### 1. Settings — `app/src/repulse/ai/settings.cljs`

- New persisted atoms: `custom-base-url` (`repulse:ai:custom-url`) and
  `custom-tools?` (`repulse:ai:custom-tools`, default `true`).
- The existing `api-key` atom is per-session today; make keys **per provider**
  (`repulse:ai:key:<provider>`) so switching between `custom` and a hosted provider
  doesn't wipe or leak a key. Migrate the existing single `repulse:ai:key` value to
  the currently selected provider on first load.
- `default-models` has no entry for `custom` — the default comes only from the
  catalogue (`pick-default` → first model the server lists).

### 2. URL validation — pure, unit tested

`normalise-base-url` → `{:ok url}` or `{:error msg}`:

- `https://…` — any host.
- `http://…` — **only** `localhost`, `127.0.0.1`, `[::1]` (any port). Anything else →
  error explaining mixed-content blocking and suggesting HTTPS or a local tunnel.
- Strip a trailing `/`, and strip a trailing `/chat/completions` if the user pasted
  the full chat URL.
- Chat URL = `<base>/chat/completions`; list URL = `<base>/models`.

### 3. Direct transport — `app/src/repulse/ai/client.cljs`

- `build-request` / `models-request` get a `"custom"` branch built from the
  normalised base URL; `Authorization: Bearer <key>` **only when a key is set**.
- `complete!` and the catalogue fetch choose transport by provider:
  `custom` → `js/fetch` directly to the endpoint; everyone else → the proxy, as today.
  Keep this a single, explicit branch point (e.g. `direct-provider?`) so no other
  provider can accidentally go direct.
- When `custom-tools?` is false, send no `tools` / `tool_choice`.
- Map failures to actionable messages:
  - `TypeError` from fetch (CORS, server down, LNA denied) → "Couldn't reach
    `<base>`. Is the server running, and does it allow requests from this site?
    See *Local models* in the manual." Browsers don't expose whether CORS was the
    cause, so say both.
  - 401/403 → key problem; 404 → wrong base URL (missing `/v1`?).

### 4. Robust tool handling — `app/src/repulse/ai/agent_loop.cljs`

Local models vary widely in tool-calling quality. The loop must never get stuck or
throw on bad output:

- Malformed `tool_calls` (unparseable JSON arguments, unknown tool name) → feed a
  tool-error result back to the model once; on a second failure, stop the loop and
  show the model's text reply.
- A model that ignores `tools` and answers in text is fine — that's a normal text
  turn.
- These rules apply to all providers, but `custom` is what exercises them; add tests
  with fixture responses.

### 5. Budget — `app/src/repulse/ai/budget.cljs`

Local servers may omit `usage`. When `:usage` is missing, count an estimate
(characters ÷ 4 of request + response) so the AI4 budget still bounds runaway loops.
Label it as an estimate in the budget display.

### 6. UI — `app/src/repulse/ui/assistant_panel.cljs`

When provider is `custom`, the settings drawer shows:

- **Base URL** field with quick-fill chips: `LM Studio` (`http://localhost:1234/v1`),
  `Ollama` (`http://localhost:11434/v1`), `llama.cpp` (`http://localhost:8080/v1`).
  Verify these default ports against each project's current docs at implementation
  time.
- **API key** labelled "optional".
- **Send tools** checkbox (`custom-tools?`), hint: "turn off if your model returns
  errors or garbage when given tools".
- **Test connection** button → fetches `<base>/models`, reports
  `✓ N models` or the mapped error from step 3.
- A one-line warning: "Requests go straight from your browser to this URL, including
  your key and the code you share. Only use endpoints you trust."
- Model field and datalist come from the AI2b catalogue, unchanged.

### 7. CSP — `vercel.json`

Extend `connect-src` with `http://localhost:* http://127.0.0.1:* http://[::1]:*`.
Remote HTTPS endpoints are already covered by `https:`. Check that the embeddable
component build (Phase O1) and any meta-tag CSP in `app/public/` stay consistent.

### 8. Docs — `docs/USAGE.md` "Local models" section

Per server, the setting that enables CORS for the REPuLse origin (verify each against
current docs at implementation time):

- **LM Studio** — Developer tab → server settings → enable CORS.
- **Ollama** — set `OLLAMA_ORIGINS` to the REPuLse origin (or `*`) before starting
  the server.
- **llama.cpp `llama-server`**, **vLLM**, **LiteLLM** — their respective
  allowed-origins / CORS options.

Also document: Chrome's local-network permission prompt (allow it), why plain-HTTP LAN
IPs don't work (mixed content — use HTTPS or run the server on the same machine), the
"Send tools" toggle, and that tool-calling quality depends heavily on the model
(recommend a model known to handle function calling).

### 9. Tests

- `normalise-base-url` — https any host; http localhost/127.0.0.1/[::1] ok; http LAN
  IP rejected; trailing `/` and `/chat/completions` stripped.
- `build-request` / `models-request` `custom` branch — URL, Bearer only with a key,
  no tools when `custom-tools?` is false.
- Transport selection — only `custom` is direct.
- Agent loop — malformed tool call recovery; text-only reply with tools offered.
- Budget — estimate used when `:usage` is absent.

### 10. Manual verification (required)

Against a real local server (LM Studio or Ollama) from **both** `npm run dev`
(localhost:3000) and a **Vercel preview deployment** (HTTPS origin, production CSP,
Chrome local-network prompt). Also one remote HTTPS OpenAI-compatible endpoint.

---

## Files to change

| File | Change |
|------|--------|
| `app/src/repulse/ai/settings.cljs` | `custom-base-url`, `custom-tools?`, per-provider keys + migration |
| `app/src/repulse/ai/client.cljs` | `custom` request branches, `normalise-base-url`, direct-vs-proxy transport, error mapping |
| `app/src/repulse/ai/models.cljs` | catalogue fetch uses direct transport for `custom` |
| `app/src/repulse/ai/agent_loop.cljs` | malformed tool-call recovery |
| `app/src/repulse/ai/budget.cljs` | usage estimate when `:usage` is absent |
| `app/src/repulse/ui/assistant_panel.cljs` | base URL + presets, optional key, Send tools, Test connection, trust warning |
| `vercel.json` | CSP `connect-src` += localhost http origins |
| `app/src/repulse/ai/*_test.cljs` | tests listed in §9 |
| `docs/USAGE.md` | "Local models" section |

---

## Definition of done

- [ ] Provider `custom` with LM Studio or Ollama on localhost: Test connection lists the
      models, and `(ai "…")` gets an answer — in dev **and** on a Vercel preview
- [ ] A remote HTTPS OpenAI-compatible endpoint works with a key
- [ ] `http://192.168.…` is rejected in the settings with an explanation; nothing is
      sent
- [ ] Unreachable server / missing CORS shows the actionable message, not a raw
      `TypeError`
- [ ] With a tools-capable local model, the agent loop produces `read_buffer` →
      `propose_edit` and a diff card
- [ ] With "Send tools" off, no `tools` field is sent and plain chat works
- [ ] A model emitting malformed tool calls ends in a text reply, not a hung loop or
      uncaught error
- [ ] Budget keeps counting when the server omits `usage`
- [ ] Switching between `custom` and a hosted provider keeps each provider's key
- [ ] No hosted provider's traffic goes direct; `/api/ai-stream` and
      `/api/ai-models` allowlists unchanged by this phase
- [ ] Production CSP allows `http://localhost:*` and still blocks other plain-HTTP
      origins
- [ ] `docs/USAGE.md` "Local models" section written
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass

---

## What NOT to do

- **Never route `custom` through the server proxies** and never relax their
  allowlists. The proxies stay closed; `custom` is browser-direct only.
- **No LAN/plain-HTTP workaround** (no proxying, no `upgrade-insecure-requests`
  tricks). HTTPS or localhost.
- **No non-OpenAI wire formats** (Ollama's native `/api/chat`, Anthropic-compatible
  servers, …). OpenAI-compatible `/v1` only — every server listed above offers it.
- **No multiple saved custom endpoints / profiles.** One base URL. Profiles can come
  later if people ask.
- **No bundled or in-browser models** (WebLLM / WebGPU). Separate idea, separate
  phase.
