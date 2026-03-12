# Phase C — Code Completion

## Goal

Add context-aware autocompletion to the REPuLse editor.

Two completion sources, combined into a single `@codemirror/autocomplete` extension:

1. **Static built-in completions** — every built-in name with a short docstring, always available
2. **Live `def` tracking** — scans the Lezer syntax tree for `(def name …)` forms in the
   current document and adds the bound names as user-symbol completions, updated as you type

Completions trigger when the cursor follows one or more symbol characters
(`a–z A–Z 0–9 - _ + * / = < > ! ? . '`) or immediately after an opening paren.

---

## Background: CM6 autocomplete

`@codemirror/autocomplete` provides two building blocks:

- **`autocompletion([options])`** — the extension; adds the completion popup and key bindings
  (`Tab` / `Enter` to accept, `Escape` to dismiss).
- **`CompletionSource`** — `(context: CompletionContext) => CompletionResult | null`
  A function that receives the current editor state + cursor position and returns
  `{ from, options }` or `null`.
- **`completeFromList(options)`** — convenience wrapper that takes a static array and
  handles the word-matching for you.

Multiple sources are composed with the `override` option (to replace the default sources)
or simply listed in an array — CM6 merges results from all sources.

---

## File layout

```
app/src/repulse/lisp-lang/
├── completions.js          ← NEW  static built-in list
├── defs-completion.js      ← NEW  live def-tracking source
└── index.js                ← MODIFIED  add autocompletion extension
```

No changes to `app.cljs`, `eval.cljs`, or any ClojureScript files.

---

## `completions.js` — static built-in list

Every entry has a `label` (the completion text), a `type` (drives the icon in the popup),
and a `detail` (one-line description shown to the right of the label).

```javascript
// completions.js
import { completeFromList } from "@codemirror/autocomplete";

const BUILTINS = [
  // --- Pattern constructors ---
  { label: "seq",         type: "function", detail: "(seq val …) — sequence of values, one per cycle step" },
  { label: "stack",       type: "function", detail: "(stack pat …) — play patterns in parallel" },
  { label: "pure",        type: "function", detail: "(pure val) — a single constant value" },
  // --- Transformations ---
  { label: "fast",        type: "function", detail: "(fast factor pat) — speed up a pattern" },
  { label: "slow",        type: "function", detail: "(slow factor pat) — slow down a pattern" },
  { label: "rev",         type: "function", detail: "(rev pat) — reverse a pattern" },
  { label: "every",       type: "function", detail: "(every n f pat) — apply f every n cycles" },
  { label: "fmap",        type: "function", detail: "(fmap f pat) — map a function over pattern values" },
  // --- Sound ---
  { label: "sound",       type: "function", detail: "(sound bank n) — select sample n from bank" },
  { label: "bpm",         type: "function", detail: "(bpm n) — set tempo in beats per minute" },
  { label: "stop",        type: "function", detail: "(stop) — stop all playback" },
  // --- Samples ---
  { label: "samples!",    type: "function", detail: "(samples! url) — load external sample bank" },
  { label: "sample-banks",type: "function", detail: "(sample-banks) — list all registered bank names" },
  // --- Effects ---
  { label: "fx",          type: "function", detail: "(fx :name param val …) — set effect parameters" },
  { label: "load-plugin", type: "function", detail: "(load-plugin url) — load a REPuLse plugin from URL" },
  // --- Arrangement ---
  { label: "arrange",     type: "function", detail: "(arrange [[pat cycles] …]) — sequence patterns by duration" },
  { label: "play-scenes", type: "function", detail: "(play-scenes [pat …]) — play patterns as 1-cycle scenes" },
  // --- Special forms ---
  { label: "def",         type: "keyword",  detail: "(def name val) — bind a name in the global environment" },
  { label: "let",         type: "keyword",  detail: "(let [name val …] body) — local bindings" },
  { label: "fn",          type: "keyword",  detail: "(fn [params] body) — anonymous function" },
  { label: "lambda",      type: "keyword",  detail: "(lambda [params] body) — anonymous function (alias for fn)" },
  { label: "if",          type: "keyword",  detail: "(if cond then else) — conditional" },
  { label: "do",          type: "keyword",  detail: "(do expr …) — evaluate expressions, return last" },
  // --- Arithmetic ---
  { label: "+",           type: "function", detail: "(+ a b …) — addition" },
  { label: "-",           type: "function", detail: "(- a b …) — subtraction" },
  { label: "*",           type: "function", detail: "(* a b …) — multiplication" },
  { label: "/",           type: "function", detail: "(/ a b …) — division" },
  // --- Comparison ---
  { label: "=",           type: "function", detail: "(= a b …) — equality" },
  { label: "not=",        type: "function", detail: "(not= a b …) — inequality" },
  { label: "<",           type: "function", detail: "(< a b …) — less than" },
  { label: ">",           type: "function", detail: "(> a b …) — greater than" },
  { label: "<=",          type: "function", detail: "(<= a b …) — less than or equal" },
  { label: ">=",          type: "function", detail: "(>= a b …) — greater than or equal" },
  { label: "not",         type: "function", detail: "(not x) — logical negation" },
  // --- Map operations ---
  { label: "get",         type: "function", detail: "(get m k default?) — look up key in map" },
  { label: "assoc",       type: "function", detail: "(assoc m k v) — add or replace key in map" },
  { label: "merge",       type: "function", detail: "(merge m …) — merge maps" },
  { label: "keys",        type: "function", detail: "(keys m) — list of map keys" },
  { label: "vals",        type: "function", detail: "(vals m) — list of map values" },
];

export const builtinCompletions = completeFromList(BUILTINS);
```

---

## `defs-completion.js` — live `def` tracking

Walk the Lezer syntax tree at each `update` call. Find `List` nodes whose first token is
the `BuiltinName` `"def"` and whose second token is a `Symbol`. Collect those symbol texts
as user-defined name completions.

```javascript
// defs-completion.js
import { syntaxTree } from "@codemirror/language";

// Regex matching any REPuLse-Lisp symbol character
const SYMBOL_RE = /[a-zA-Z\-_+*\/=<>!?.][a-zA-Z0-9\-_+*\/=<>!?.']*/;

export function defsCompletionSource(context) {
  // Only complete when the cursor follows a symbol character (or is after '(')
  const word = context.matchBefore(SYMBOL_RE);
  if (!word && !context.explicit) return null;

  const from   = word ? word.from : context.pos;
  const prefix = word ? word.text : "";

  const names = new Set();
  syntaxTree(context.state).iterate({
    enter(node) {
      // Look for List nodes: ( def <Symbol> <expr> )
      if (node.name !== "List") return;
      const children = [];
      let child = node.node.firstChild;
      while (child) { children.push(child); child = child.nextSibling; }

      // children[0] = "("
      // children[1] = BuiltinName "def"  (or Symbol "def")
      // children[2] = Symbol (the name being bound)
      // children[3] = value form
      // children[4] = ")"
      const nameNode = children[1];
      const symNode  = children[2];
      if (!nameNode || !symNode) return;
      const nameText = context.state.sliceDoc(nameNode.from, nameNode.to);
      if (nameText !== "def") return;
      if (symNode.name !== "Symbol" && symNode.name !== "BuiltinName") return;
      names.add(context.state.sliceDoc(symNode.from, symNode.to));
    },
  });

  const options = [...names]
    .filter(n => n.startsWith(prefix))
    .map(n => ({ label: n, type: "variable", detail: "user-defined" }));

  if (options.length === 0 && !word) return null;
  return { from, options };
}
```

---

## `index.js` — wire up the extension

```javascript
// index.js
import { parser } from "./parser.js";
import { LRLanguage, LanguageSupport, indentNodeProp, foldNodeProp } from "@codemirror/language";
import { autocompletion } from "@codemirror/autocomplete";
import { repulseLispHighlight } from "./highlight.js";
import { rainbowBrackets } from "./rainbow.js";
import { builtinCompletions } from "./completions.js";
import { defsCompletionSource } from "./defs-completion.js";

const lispParser = parser.configure({
  props: [
    repulseLispHighlight,
    indentNodeProp.add({
      List:   cx => cx.baseIndent + 2,
      Vector: cx => cx.baseIndent + 2,
      Map:    cx => cx.baseIndent + 2,
    }),
    foldNodeProp.add({
      List:   node => ({ from: node.from + 1, to: node.to - 1 }),
      Vector: node => ({ from: node.from + 1, to: node.to - 1 }),
    }),
  ],
});

export const lispLanguage = new LanguageSupport(
  LRLanguage.define({ parser: lispParser, languageData: { commentTokens: { line: ";" } } }),
  [
    rainbowBrackets,
    autocompletion({
      override: [builtinCompletions, defsCompletionSource],
    }),
  ]
);
```

No changes to `app.cljs` — the extension is bundled inside `lispLanguage`.

---

## npm dependency

`@codemirror/autocomplete` is likely already a transitive dependency from `@codemirror/commands`.
Check `app/node_modules/@codemirror/autocomplete/` and add explicitly if absent:

```bash
cd app && npm install @codemirror/autocomplete
```

Add to `app/package.json` dependencies:

```json
"@codemirror/autocomplete": "^6.0.0"
```

---

## Key bindings

CM6's `autocompletion()` registers its own key bindings by default:

| Key | Action |
|---|---|
| `Tab` | Accept selected completion |
| `Enter` | Accept selected completion |
| `Escape` | Dismiss popup |
| `↑` / `↓` | Navigate completions |
| `Ctrl+Space` | Open completion explicitly |

These do not conflict with REPuLse's eval shortcut (`Shift+Enter`).

---

## Styling

The completion popup inherits CodeMirror's built-in popup styles. No custom CSS is required;
the `oneDark` theme already covers the popup background and selection colour.

If the popup feels too wide, a single CSS override is enough:

```css
.cm-tooltip-autocomplete { max-width: 420px; }
```

Add to `app/public/css/main.css` only if needed.

---

## What NOT to do in this phase

- No signature / parameter hints popup — that is a separate, larger feature
- No completion for `:keywords` (sample bank names are not enumerable at edit time)
- No fuzzy matching — prefix matching only (CM6's default `completeFromList` behaviour)
- No changes to the evaluator, reader, or core pattern engine
- No new ClojureScript files

---

## Acceptance criteria

- [ ] Typing the first letters of any built-in (`se`, `fa`, `ev`, …) opens the completion popup
- [ ] Each entry shows a one-line `detail` string beside the label
- [ ] `(def my-kick (seq :bd :bd :sd))` causes `my-kick` to appear in completions in that same session
- [ ] Removing a `def` removes the name from completions (next keystroke)
- [ ] `Escape` dismisses the popup without side effects
- [ ] `Tab` or `Enter` inserts the selected completion
- [ ] Active-event amber flash (Phase 5) still works
- [ ] Rainbow brackets (Phase 10) still works
- [ ] No console errors in Chrome, Firefox, or Safari
- [ ] Update CLAUDE.md, ROADMAP.md phase status tables
