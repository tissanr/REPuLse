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
implementation must choose the signaling approach explicitly before coding:

- public signaling service
- self-hosted signaling endpoint
- Supabase realtime
- no collaboration until a backend decision is made

## Definition Of Done

- [ ] Two browser tabs can join the same room and synchronize editor text.
- [ ] Disconnect/reconnect does not corrupt the buffer.
- [ ] Presence/connection state is visible.
- [ ] Remote edits do not auto-evaluate audio by default.
- [ ] Dependencies and signaling/privacy behavior are documented.
- [ ] `npm run test` and `npx shadow-cljs compile app` pass.
