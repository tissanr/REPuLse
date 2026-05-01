# Phase AI1 — AI-Ready Knowledge Base

## Goal

Generate a set of machine-readable JSON files that give any AI assistant — in-app
or external — a precise, grounded description of REPuLse's language, vocabulary,
and live session state. The files serve both the upcoming in-app AI co-pilot (AI2+)
and external tools such as Claude Code, IDE agents, and CI-based assistants that
already use this repository today.

This phase ships **standalone** — no in-app chat UI is required. Delivering the
knowledge base first means every subsequent use of an AI in this codebase is
immediately better-informed.

```
;; Before — an LLM working from README/USAGE.md prose has:
;;   - inconsistent signatures and no formal arity information
;;   - no side-effect tags (what calls Web Audio? what touches DOM?)
;;   - no machine-readable session snapshot
;;   - four divergent sources of truth (completions.js / hover.js /
;;     repulse-lisp.grammar / eval.cljs) that may silently disagree

;; After — /docs/ai/builtins.json contains one entry per built-in:
{
  "seq": {
    "category": "source",
    "signature": "(seq value...)",
    "arity": { "min": 1, "rest": true },
    "returns": "Pattern",
    "side-effects": [],
    "examples": ["(seq :bd :sd :bd :sd)", "(seq 220 330 440)"],
    "see-also": ["stack", "fast", "alt"]
  },
  "fx": {
    "category": "effect",
    "signature": "(fx :name param) or (fx :name :key val ...)",
    "arity": { "min": 2 },
    "returns": "nil",
    "side-effects": ["audio", "dom"],
    "examples": ["(fx :reverb 0.4)", "(fx :delay :wet 0.3 :time 0.25)"],
    "see-also": ["track", "amp"]
  }
}

;; And (help-export) from the command bar emits the live session:
{
  "bpm": 130,
  "tracks": { "kick": true, "bass": true },
  "muted": [],
  "fx": [{ "name": "reverb", "params": { "wet": 0.4 }, "bypassed": false }],
  "bank": null,
  "sources": []
}
```

---

## Background

### Four divergent sources of built-in metadata (today)

All four must agree — and today they can drift silently:

| Source | Location | What it provides |
|---|---|---|
| `completions.js` | `app/src/repulse/lisp-lang/completions.js` | `{label, type, detail}` per built-in — one-line detail string |
| `hover.js` | `app/src/repulse/lisp-lang/hover.js` | `{signature, description, example}` — richer, but hand-authored |
| `repulse-lisp.grammar` | `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Authoritative name list — drives syntax highlighting |
| `eval.cljs` / `builtins.cljs` | `packages/lisp/src/repulse/lisp/eval.cljs`, `app/src/repulse/env/builtins.cljs` | True runtime binding set |

CI1 added a grammar-drift check (`npm run gen:grammar` + `git diff`). AI1 extends
that discipline to the full metadata set: `gen:ai-docs` fails CI if any source disagrees.

### Session snapshot (existing)

`app/src/repulse/session.cljs` already builds a versioned JSON snapshot for URL
sharing and localStorage persistence. `build-session-snapshot` returns:

```clojure
{:v      2
 :editor "..."   ; full editor buffer text
 :bpm    130
 :fx     [{:name "reverb" :params {...} :bypassed false}]
 :bank   nil
 :sources [...]
 :muted  []}
```

The `:editor` field contains full Lisp code — user `def` bodies and all. The AI
session view must strip `:editor` and emit only structural metadata (track names,
BPM, active FX, bank, sources) by default. Full code sharing is opt-in.

### `scheduler-state` atom (audio.cljs)

The live track list lives in `repulse.audio/scheduler-state`:

```clojure
{:tracks  {:kick <Pattern> :bass <Pattern>}   ; active patterns
 :muted   #{:kick}                             ; muted track keywords
 :cycle-dur 0.5                                ; seconds per cycle
 ...}
```

`(help-export)` reads this to produce the `"tracks"` and `"muted"` fields
(track names only — no Pattern objects, which are ClojureScript functions).

---

## Implementation

### 1. `app/src/repulse/content/builtin_meta.edn`

New hand-authored companion file that records what `completions.js` and `hover.js`
cannot (side-effects, return type, arity, see-also links, up to 3 examples):

```edn
{:seq     {:category "source"
            :returns "Pattern"
            :side-effects []
            :examples ["(seq :bd :sd :bd :sd)" "(seq 220 330 440)"]
            :see-also ["stack" "fast" "alt"]}

 :stack   {:category "source"
            :returns "Pattern"
            :side-effects []
            :examples ["(stack (seq :bd :_ :bd :_) (seq :_ :sd :_ :sd))"]
            :see-also ["seq" "track"]}

 :fx      {:category "effect"
            :returns "nil"
            :side-effects ["audio" "dom"]
            :examples ["(fx :reverb 0.4)" "(fx :delay :wet 0.3 :time 0.25)" "(fx :off :reverb)"]
            :see-also ["track"]}

 ;; ... one entry per built-in listed in completions.js
}
```

Side-effect tags: `"audio"` (touches Web Audio graph), `"dom"` (modifies DOM),
`"network"` (fetches URLs), `"storage"` (reads/writes localStorage), `"midi"`
(touches Web MIDI), `"none"` / `[]` (pure).

### 2. `scripts/gen_ai_docs.mjs`

Node.js ESM script (no build step required — runs with `node scripts/gen_ai_docs.mjs`).
Reads three inputs and writes `/docs/ai/builtins.json`:

```javascript
import { readFileSync, writeFileSync } from 'node:fs';
import { parse as parseEdn } from '@noel/edn';  // or hand-roll trivial EDN subset

// 1. Load grammar to get the authoritative name list
const grammar = readFileSync('app/src/repulse/lisp-lang/repulse-lisp.grammar', 'utf8');
const builtinNames = extractBuiltinNames(grammar); // regex over BuiltinName { ... }

// 2. Load completions.js to get detail strings
const { BUILTINS } = await import('../app/src/repulse/lisp-lang/completions.js');

// 3. Load builtin_meta.edn for enriched metadata
const meta = parseEdn(readFileSync('app/src/repulse/content/builtin_meta.edn', 'utf8'));

// 4. Load hover.js for signature + description
const { DOCS } = await import('../app/src/repulse/lisp-lang/hover.js');

// 5. Merge and validate — fail if any grammar name is missing from completions
const output = {};
for (const name of builtinNames) {
  const completion = BUILTINS.find(b => b.label === name);
  if (!completion) throw new Error(`MISSING: ${name} in completions.js`);
  const hoverDoc = DOCS[name] ?? {};
  const extraMeta = meta[`:${name}`] ?? {};
  output[name] = {
    category:     extraMeta[':category']     ?? inferCategory(completion.type),
    signature:    hoverDoc.signature         ?? completion.detail,
    returns:      extraMeta[':returns']      ?? 'any',
    'side-effects': extraMeta[':side-effects'] ?? [],
    examples:     extraMeta[':examples']     ?? (hoverDoc.example ? [hoverDoc.example] : []),
    'see-also':   extraMeta[':see-also']     ?? [],
    detail:       completion.detail,
    description:  hoverDoc.description       ?? '',
  };
}

writeFileSync('docs/ai/builtins.json', JSON.stringify(output, null, 2));
console.log(`Wrote ${Object.keys(output).length} built-in entries`);
```

**No new npm dependency is strictly required.** The EDN subset used in
`builtin_meta.edn` (a map of keyword→map) is trivial to parse with a small
hand-written regex + `JSON.parse`-style converter. Add `@noel/edn` only if the
file grows complex enough to warrant it.

### 3. `docs/ai/concepts.json`

Hand-authored JSON describing REPuLse's semantic model. Written once, maintained
alongside language changes. Schema:

```json
{
  "pattern-model": {
    "summary": "A Pattern is a pure function from a TimeSpan to a list of Events.",
    "time": "Rational arithmetic. TimeSpan = {start, end} as [numerator, denominator] pairs.",
    "cycle": "The fundamental time unit. One cycle = one bar at the current BPM.",
    "event-shape": { "value": "any", "whole": "TimeSpan", "part": "TimeSpan", "source": "optional int offset pair" }
  },
  "value-types": {
    "keyword": "Sample trigger or note name: :bd, :sd, :c4, :eb3",
    "number": "Hz frequency: 440, 220.5",
    "note-keyword": "Pitch in equal temperament A4=440. Format: :note[sharp|flat]octave — :c4, :fs3, :bb5",
    "rest": ":_ — silence; skips the event",
    "map": "Per-event parameter map: {:note :c4 :amp 0.8 :synth :saw}"
  },
  "transformer-vs-pattern": {
    "pattern": "A value that generates events when queried. (seq :bd :sd) is a Pattern.",
    "transformer": "(amp 0.8) with one arg returns a function Pattern→Pattern. Use ->> to chain.",
    "thread-last": "(->> pat (amp 0.8) (attack 0.02)) threads pat as the last arg to each step."
  },
  "mini-notation": {
    "syntax": "Space-separated tokens inside a string: (~ \"bd sd [hh hh] bd\")",
    "tokens": {
      "space": "sequence",
      "[…]": "subdivision — shares one parent slot",
      "*N": "repeat N times",
      "<…>": "alternation — cycle N picks element mod N",
      "~": "rest",
      "?": "50% probability",
      ":N": "sample index",
      "@N": "weight"
    }
  },
  "track-model": {
    "named-tracks": "(track :name pattern) — registers a pattern under a keyword name",
    "anonymous-track": "A bare pattern (not wrapped in track) runs on the :_ anonymous track",
    "mute": "(mute! :name) — silences without removing; (unmute! :name) re-enables",
    "update": "(upd) — hot-swap re-evaluates buffer and updates tracks without restarting"
  }
}
```

### 4. `docs/ai/session-schema.json`

JSON Schema describing the AI session snapshot (the output of `(help-export)`).
Not the same as the full `build-session-snapshot` — this is the stripped, safe view:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "REPuLse AI Session Snapshot",
  "type": "object",
  "required": ["bpm", "tracks", "muted", "fx"],
  "properties": {
    "bpm":    { "type": "number", "minimum": 20, "maximum": 400 },
    "tracks": {
      "type": "object",
      "description": "Active track names (keys) with boolean true. Values are never Pattern functions.",
      "additionalProperties": { "type": "boolean" }
    },
    "muted":  { "type": "array", "items": { "type": "string" } },
    "fx": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["name", "params", "bypassed"],
        "properties": {
          "name":     { "type": "string" },
          "params":   { "type": "object" },
          "bypassed": { "type": "boolean" }
        }
      }
    },
    "bank":    { "type": ["string", "null"] },
    "sources": {
      "type": "array",
      "items": { "type": "object", "properties": { "type": { "type": "string" }, "id": { "type": "string" } } }
    }
  }
}
```

### 5. `docs/ai/cookbook.json`

Structured versions of the cookbook recipes from DOC1 (hand-authored or seeded
from the snippet library). Each entry has a goal tag so an LLM can say "show me
a drum recipe":

```json
[
  {
    "id": "four-on-the-floor",
    "title": "Four on the floor",
    "tags": ["drums", "kick", "techno"],
    "bpm": 130,
    "code": "(track :kick (seq :bd :bd :bd :bd))\n(track :snare (seq :_ :sd :_ :sd))"
  }
]
```

This file can initially seed from `app/public/snippets/library.json` (existing
snippet library) since the schema is compatible with small additions.

### 6. `docs/ai/README.md`

Short guide for consumers of the AI docs (agents, CI scripts, IDE integrations):
what each file contains, the stability guarantee, and the update cadence.

### 7. `app/src/repulse/env/builtins.cljs` — `(help-export)` built-in

Add to `ensure-env!` in `app/src/repulse/env/builtins.cljs`:

```clojure
"help-export"
(fn []
  (let [state @audio/scheduler-state
        track-names (into {} (map (fn [k] [(name k) true]) (keys (:tracks state))))
        muted-names (mapv name (:muted state))
        bpm         (audio/get-bpm)
        fx-list     (mapv (fn [{:keys [name-kw plugin bypassed?]}]
                            {:name     (cljs.core/name name-kw)
                             :params   (try (js->clj (.getParams ^js plugin)) (catch :default _ {}))
                             :bypassed (boolean bypassed?)})
                          (filter :active? @fx/chain))
        bank        @samples/active-bank-prefix
        sources     (mapv (fn [{:keys [type id]}]
                            {:type (cljs.core/name type) :id id})
                          (filter #(= :github (:type %)) @samples/loaded-sources))]
    (clj->js {:bpm     bpm
              :tracks  track-names
              :muted   muted-names
              :fx      fx-list
              :bank    bank
              :sources sources})))
```

The return value is a plain JS object that prints as JSON in the output panel.

Note: `help-export` intentionally omits `:editor` (full code). Users who want to
share code can copy-paste from the editor manually, or enable a future "include
code" opt-in setting (AI2+).

### 8. `package.json` — new scripts

```json
"gen:ai-docs":   "node scripts/gen_ai_docs.mjs",
"check:ai-docs": "node scripts/gen_ai_docs.mjs && git diff --exit-code docs/ai/builtins.json"
```

### 9. `.github/workflows/ci.yml` — drift check job

Add a third job `ai-docs` to the existing CI workflow:

```yaml
ai-docs:
  name: AI docs drift check
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-node@v4
      with:
        node-version: '20'
        cache: 'npm'
    - run: npm ci
    - run: npm run check:ai-docs
```

This fails if `builtins.json` is stale relative to `completions.js`,
`repulse-lisp.grammar`, or `builtin_meta.edn`.

### 10. `CLAUDE.md` — Rule 4

Add after Rule 3:

```markdown
### Rule 4 — AI docs are part of done

When adding a new built-in name that should be highlighted and autocompleted:

1. (existing) Add the name to `BuiltinName` in the grammar
2. (existing) Run `npm run gen:grammar`
3. (existing) Add a `{ label, type, detail }` entry in `completions.js`
4. **Add an entry to `app/src/repulse/content/builtin_meta.edn`** with
   `category`, `returns`, `side-effects`, `examples`, and `see-also`
5. **Run `npm run gen:ai-docs`** — overwrites `docs/ai/builtins.json`
6. Commit grammar, parser.js, completions, builtin_meta.edn, and
   builtins.json together
```

---

## Files to change

| File | Change |
|---|---|
| `docs/ai/builtins.json` | **New** — generated by `gen:ai-docs`; committed to repo |
| `docs/ai/concepts.json` | **New** — hand-authored REPuLse semantic model |
| `docs/ai/session-schema.json` | **New** — JSON Schema for AI session snapshot |
| `docs/ai/cookbook.json` | **New** — structured recipes (seeded from snippet library) |
| `docs/ai/README.md` | **New** — consumer guide for the AI docs directory |
| `app/src/repulse/content/builtin_meta.edn` | **New** — enriched metadata companion to `completions.js` |
| `scripts/gen_ai_docs.mjs` | **New** — generator script |
| `app/src/repulse/env/builtins.cljs` | Add `(help-export)` built-in |
| `app/src/repulse/lisp-lang/completions.js` | Add `help-export` entry |
| `app/src/repulse/lisp-lang/hover.js` | Add `help-export` hover doc |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `help-export` to `BuiltinName` |
| `package.json` | Add `gen:ai-docs` and `check:ai-docs` scripts |
| `.github/workflows/ci.yml` | Add `ai-docs` drift-check job |
| `CLAUDE.md` | Add Rule 4 — AI docs maintenance step |
| `docs/PLUGINS.md` | Note that `gen:ai-docs` should run alongside `gen:grammar` |
| `ROADMAP.md` + `CLAUDE.md` | Mark AI1 ✓ delivered |

---

## Definition of done

- [ ] `npm run gen:ai-docs` completes without error and writes `docs/ai/builtins.json`
- [ ] `builtins.json` contains one entry for every name in the `BuiltinName` rule of `repulse-lisp.grammar`
- [ ] Every entry has: `category`, `signature`, `returns`, `side-effects`, `examples` (≥1), `see-also`
- [ ] `npm run check:ai-docs` exits non-zero on CI when `completions.js` contains a name absent from `builtin_meta.edn`
- [ ] `concepts.json` covers: pattern model, rational time, transformer vs. pattern, `->>` semantics, mini-notation syntax, all value types (keyword, number, note-keyword, rest, map)
- [ ] `session-schema.json` is a valid JSON Schema Draft 2020-12 document that validates against three real `(help-export)` outputs
- [ ] `(help-export)` built-in is accessible from the command bar and returns a JS object with `bpm`, `tracks`, `muted`, `fx`, `bank`, `sources` — no editor code, no Pattern functions
- [ ] `(help-export)` omits `:editor` text (pattern code stays private by default)
- [ ] `cookbook.json` contains ≥ 12 entries, each with `id`, `title`, `tags`, `bpm`, and `code`; all code is playable
- [ ] `docs/ai/README.md` explains the schema, stability guarantee, and how to update the files
- [ ] CI `ai-docs` job passes on the `main` branch PR
- [ ] CI `ai-docs` job fails when `completions.js` has an entry not in `builtin_meta.edn` (smoke-tested by adding a dummy name and reverting)
- [ ] CLAUDE.md Rule 4 exists and matches the actual workflow
- [ ] `npm run test` still passes — no regressions

---

## What NOT to do

- **Do not add any in-app chat UI.** That is Phase AI2. This phase is data and tooling only.
- **Do not include editor buffer text (`def` bodies) in `(help-export)`.** Full code sharing is an opt-in feature deferred to AI2.
- **Do not add a new npm LLM SDK dependency.** No Anthropic/OpenAI/Vercel AI SDK here. The JSON files are static data consumed by external tools.
- **Do not replace `completions.js` or `hover.js`.** They remain the primary sources for the in-editor UX. `builtin_meta.edn` is additive enrichment, not a replacement.
- **Do not auto-generate `concepts.json` or `cookbook.json`.** These are hand-authored. Only `builtins.json` is generated by the script.
- **Do not write a separate documentation website.** The JSON files live in the repo and are consumed directly by agents and the future in-app panel.
