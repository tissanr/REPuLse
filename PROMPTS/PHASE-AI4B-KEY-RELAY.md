# Phase AI4b — Encrypted AI Key Relay

## Goal

Add optional server-side storage and relay for AI provider keys, so authenticated users
can avoid storing provider keys in browser localStorage.

AI4 covers client-side safety: budgets, prompt-injection guards, auto-apply undo,
retry handling, and activity logging. AI4b is the backend/key-management follow-up.

## Scope

- Add Supabase schema for per-user encrypted AI provider settings.
- Add API helpers in `app/src/repulse/api.cljs` to save/load relay settings.
- Extend the AI settings UI with a server-relay mode for authenticated users.
- Extend `api/ai-stream.ts` or add a dedicated proxy route that retrieves the user's
  server-side key and forwards the provider request.
- Keep BYO localStorage key mode fully supported.

## Security Notes

- Do not log provider keys.
- Do not send provider keys back to the browser after storage.
- Enforce authenticated access and row ownership through Supabase RLS or equivalent
  server-side checks.
- Add a server-side budget/rate guard as a second safety layer.

## Definition Of Done

- [ ] Authenticated user can save a provider key for relay mode.
- [ ] Browser no longer stores or transmits the raw key after relay mode is enabled.
- [ ] AI streaming still works through the chosen proxy route.
- [ ] Local BYO key mode still works when relay mode is off.
- [ ] Server-side access controls prevent one user from reading another user's settings.
- [ ] Deployment docs describe required environment variables and migration steps.
