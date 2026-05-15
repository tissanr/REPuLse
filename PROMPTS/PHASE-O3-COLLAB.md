# Phase O3 — Collaborative Sessions

## Goal

Add shared editor sessions so multiple users can collaborate on the same REPuLse
buffer. Audio remains local; only code and lightweight presence state sync.

## Scope

- Add CRDT-backed editor synchronization.
- Add user-facing start/join controls and Lisp commands, e.g. `(collab-start!)` and
  `(collab-join! "room")`.
- Show connection and peer presence state in the UI.
- Ensure local evaluation remains explicit: receiving code changes should not
  automatically start audio unless the local user opts into that behavior.

## Design Notes

The old umbrella O prompt suggested Yjs + WebRTC. That is still plausible, but the
implementation must choose the signaling approach explicitly before coding.

**Recommended approach: Supabase Realtime as the signaling channel.** It reuses the
existing S2 backend (no new external dependency), works with Supabase auth, and keeps
the stack uniform. Yjs with `y-supabase` (Supabase Realtime broadcast/presence) or
`y-webrtc` with Supabase Realtime for signaling are both viable transports.

Alternative signaling options (evaluate if Supabase Realtime proves unsuitable):

- Public signaling service (e.g. `wss://y-webrtc-signaling.netlify.app`) — no server
  cost, but introduces an external dependency and has no auth
- Self-hosted signaling endpoint — maximum control, adds operational overhead
- No collaboration until the team makes a deliberate signaling/privacy decision

## Definition Of Done

- [ ] Two browser tabs can join the same room and synchronize editor text.
- [ ] Disconnect/reconnect does not corrupt the buffer.
- [ ] Presence/connection state is visible.
- [ ] Remote edits do not auto-evaluate audio by default.
- [ ] Dependencies and signaling/privacy behavior are documented.
- [ ] `npm run test` and `npx shadow-cljs compile app` pass.
