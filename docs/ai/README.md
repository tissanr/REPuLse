# REPuLse AI Documentation

Machine-readable files for AI assistants, IDE agents, and CI-based tools working with REPuLse.

## Files

| File | Content | Authored by |
|---|---|---|
| `builtins.json` | One entry per built-in name: category, signature, return type, side-effects, examples, see-also | **Generated** — run `npm run gen:ai-docs` |
| `concepts.json` | REPuLse semantic model: pattern algebra, time model, value types, track model, effects, synthesis | Hand-authored |
| `session-schema.json` | JSON Schema (Draft 2020-12) for the output of `(help-export)` | Hand-authored |
| `cookbook.json` | 28+ playable recipes with id, title, tags, BPM, and code | Hand-authored |

## Quick start for agents

```js
// 1. Load the knowledge base
const builtins  = JSON.parse(fs.readFileSync('docs/ai/builtins.json'));
const concepts  = JSON.parse(fs.readFileSync('docs/ai/concepts.json'));
const cookbook  = JSON.parse(fs.readFileSync('docs/ai/cookbook.json'));

// 2. Look up a built-in
builtins['seq'];
// → { category: 'source', signature: '(seq val ...)', returns: 'Pattern',
//     'side-effects': [], examples: ['(seq :bd :sd :bd :sd)'], 'see-also': ['stack', 'fast', 'alt'], ... }

// 3. Get recipes tagged 'drums'
cookbook.filter(r => r.tags.includes('drums'));

// 4. Read the live session snapshot from the REPL
// In the REPuLse editor, evaluate: (help-export)
// Paste the JSON here for the agent to inspect.
```

## `builtins.json` schema

Each key is a built-in name. Each value is:

```json
{
  "category":    "source | transform | effect | audio | midi | io | special | util | ugen | sample | content | session",
  "signature":   "(name args…)",
  "returns":     "Pattern | nil | number | boolean | any | …",
  "side-effects": ["audio", "dom", "network", "storage", "midi"],
  "examples":    ["(name arg1 arg2)", "…"],
  "see-also":    ["other-name", "…"],
  "detail":      "one-line description from completions.js",
  "description": "longer description from hover.js (may be empty)"
}
```

Side-effect tags:
- `"audio"` — modifies the Web Audio graph or scheduler
- `"dom"` — updates the DOM (output panel, session panel, editor)
- `"network"` — fetches URLs (GitHub, Freesound, Gists)
- `"storage"` — reads or writes localStorage
- `"midi"` — uses the Web MIDI API
- `[]` — pure function with no side effects

## `session-schema.json`

Validates the output of `(help-export)` against JSON Schema Draft 2020-12.

```js
import Ajv from 'ajv/dist/2020.js';
const ajv = new Ajv();
const validate = ajv.compile(schema);
const ok = validate(helpExportOutput);
```

## `cookbook.json` schema

Each recipe:

```json
{
  "id":    "unique-kebab-case-id",
  "title": "Human-readable title",
  "tags":  ["drums", "techno", "…"],
  "bpm":   130,
  "code":  "(track :name (seq …))"
}
```

All code in `cookbook.json` is playable in a fresh REPuLse session without any external samples or configuration.

## Stability guarantee

- `builtins.json` tracks the editor's completion list. It changes when a new built-in is added. Run `npm run check:ai-docs` to verify it is up to date; CI fails if it drifts.
- `concepts.json` and `session-schema.json` are updated manually when the language model changes.
- `cookbook.json` is stable; recipes are additive.

## Updating builtins.json

When you add a new built-in name (see CLAUDE.md Rule 5):

```bash
npm run gen:grammar          # regenerate Lezer parser
# edit completions.js        # add { label, type, detail } entry
# edit builtin_meta.edn      # add metadata entry
npm run gen:ai-docs          # regenerate builtins.json
# commit all four files together
```

CI runs `npm run check:ai-docs` on every PR and fails if `builtins.json` is stale.
