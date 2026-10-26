# Manual test protocols

Every implemented phase gets a test protocol here: `docs/test-protocols/<PHASE-ID>.md`.
It is written by whoever implements the phase, including the unattended auto-phase
pipeline, and committed together with the implementation.

The automated suite (`npm run test:all`, see [TESTPLAN.md](../TESTPLAN.md)) checks event
values and offline-rendered audio. A protocol covers what it can't: how the result
sounds, what the app shows, editor behaviour, and regressions a person would notice. A
human works through it in the browser and ticks each test.

## What a protocol contains

1. **Header table:** phase and spec link, the PR that delivered it, which automated tests
   already cover it, and a line for tester, date, commit and browser.
2. **Setup:** how to start the app and anything needed (headphones, console open, MIDI
   device, samples).
3. **Numbered tests**, grouped (e.g. *A. New features*, *B. Warnings and errors*,
   *C. Regressions*, *D. Editor*). Each test has:
   - a REPuLse-Lisp snippet in a ```` ```lisp ```` block that can be pasted and
     evaluated as-is;
   - **Expected:** what you should hear and see, concretely: note names, counts per
     cycle, direction of a sweep, the exact error or warning text. Not "works correctly";
   - `- [ ] Pass  - [ ] Fail  Notes:` for the result.
4. **Known issues:** limitations that are accepted, so a tester doesn't report them again.
5. **Result:** an overall pass/fail line.

## Rules for writing one

- Cover every new built-in, effect or parameter, every new error or warning message, and
  every behaviour the phase removed or changed (with the replacement).
- Add at least two regression tests for nearby features the phase touched.
- Expected values must be real. Check every pattern snippet through the TEST1 harness
  (`window.__REPULSE_TEST__.evalToEvents(code, cycles)` on `test-harness.html`, e.g. from a
  scratch Playwright spec that you delete afterwards) and take note names and values from
  its output. Take message texts from the code. Never invent numbers.
- Snippets must work in the app as written: use only built-ins that exist, real demo
  names, real synth voices.
- For 🤖👂 phases the listen or hardware check named in the spec is part of the protocol,
  and the phase is accepted only after a human has passed it.

## Index

| Phase | Protocol | Status |
|---|---|---|
| TL2 — Continuous signals | [TL2.md](TL2.md) | not yet run |
