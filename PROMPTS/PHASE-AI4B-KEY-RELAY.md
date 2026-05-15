# Phase AI4b — Encrypted AI Key Relay

## Goal

Add optional server-side storage and relay for AI provider keys, so authenticated users
can avoid storing provider keys in browser localStorage.

AI4 covers client-side safety: budgets, prompt-injection guards, auto-apply undo,
retry handling, and activity logging. AI4b is the backend/key-management follow-up.

**Dependencies:** requires AI4 and Phase S2 (Supabase + GitHub OAuth backend) to be
delivered first. `app/src/repulse/api.cljs` and `app/src/repulse/auth.cljs` from S2
provide the Supabase client and session management that AI4b builds on.

---

## Scope

- Add Supabase schema for per-user encrypted AI provider settings.
- Add API helpers in `app/src/repulse/api.cljs` to save/load relay settings.
- Extend the AI settings UI with a server-relay mode for authenticated users.
- Extend `api/ai-stream.ts` or add a dedicated proxy route that retrieves the user's
  server-side key and forwards the provider request.
- Keep BYO localStorage key mode fully supported.

---

## Supabase schema

```sql
-- supabase/migrations/YYYYMMDD_user_ai_settings.sql
create table user_ai_settings (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  provider    text not null,                 -- e.g. 'anthropic', 'openai'
  api_key_enc text not null,                 -- encrypted server-side; never returned to browser
  created_at  timestamptz default now(),
  updated_at  timestamptz default now(),
  unique (user_id, provider)
);

-- RLS: each user can only read/write their own row
alter table user_ai_settings enable row level security;
create policy "owner only" on user_ai_settings
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
```

Encryption of `api_key_enc` must happen server-side (in the Vercel route), not in the
browser. Use `crypto.subtle` or a KMS-backed approach. The browser sends the raw key
once to save it; the key is never returned from storage.

---

## Proxy route

Extend the existing streaming route or add a dedicated proxy:

```typescript
// api/ai/proxy.ts  (or extend api/ai-stream.ts)
import { createClient } from "@supabase/supabase-js";

export const config = { runtime: "nodejs" };

export default async function handler(req: Request) {
  // 1. Verify session from Authorization header (S2 Supabase auth)
  const supabase = createClient(process.env.SUPABASE_URL!, process.env.SUPABASE_SERVICE_KEY!);
  const { data: { user }, error } = await supabase.auth.getUser(
    req.headers.get("authorization")?.replace("Bearer ", "") ?? ""
  );
  if (error || !user) return new Response("Unauthorized", { status: 401 });

  // 2. Fetch encrypted key from user_ai_settings, decrypt server-side
  const { data } = await supabase
    .from("user_ai_settings")
    .select("api_key_enc, provider")
    .eq("user_id", user.id)
    .single();
  const apiKey = decrypt(data.api_key_enc);  // server-side decryption only

  // 3. Forward to provider, stream SSE back
  const body = await req.json();
  const upstream = await fetch(providerUrl(data.provider), {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return new Response(upstream.body, { headers: { "Content-Type": "text/event-stream" } });
}
```

The key never appears in browser localStorage or network responses after initial save.

---

## Files to change

| File | Change |
|------|--------|
| `supabase/migrations/YYYYMMDD_user_ai_settings.sql` | **New** — `user_ai_settings` table with encrypted `api_key_enc` column and RLS |
| `api/ai/proxy.ts` | **New** — Vercel serverless proxy route (or extend `api/ai-stream.ts`) |
| `app/src/repulse/api.cljs` | Add `save-ai-relay-settings!` and `delete-ai-relay-settings!` Supabase calls |
| `app/src/repulse/ui/ai_settings_modal.cljs` | Add server-relay toggle (only visible when logged in); relay mode sets endpoint to `/api/ai/proxy` |
| `docs/DEPLOYMENT.md` | Document required env vars (`SUPABASE_SERVICE_KEY`, encryption key/KMS config) and migration steps |

---

## Security Notes

- Do not log provider keys at any layer.
- Do not return the stored key to the browser after initial save.
- The RLS policy ensures one user cannot read another user's `api_key_enc`.
- Add a server-side token/rate guard in the proxy as a second budget layer (reusing AI4's budget logic if extractable, or a simple per-user counter in Supabase).
- The relay toggle must only be available to authenticated users (S2 auth).

---

## Definition Of Done

- [ ] Authenticated user can save a provider key for relay mode.
- [ ] Browser no longer stores or transmits the raw key after relay mode is enabled.
- [ ] AI streaming still works through the proxy route.
- [ ] Local BYO key mode still works when relay mode is off.
- [ ] Server-side RLS prevents one user from reading another user's settings.
- [ ] Proxy enforces authenticated access before forwarding any request.
- [ ] `docs/DEPLOYMENT.md` describes required environment variables and migration steps.
- [ ] `npm run test` and `npx shadow-cljs compile app` pass.
