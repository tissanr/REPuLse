# Phase 10 — Syntax Highlighting

## Goal

Add a CodeMirror 6 language extension for REPuLse-Lisp that gives the editor:

- **Bracket matching** — clicking next to a `(` or `)` highlights the matching pair
- **Syntax highlighting** — distinct colours for keywords (`:bd`, `:sd`, …), built-in
  function names (`seq`, `stack`, `fast`, …), numbers, strings, comments, and
  structural punctuation
- **Rainbow parens** (optional, lower priority) — nested delimiters in alternating colours
  to make nesting depth visually obvious

The colour scheme must integrate with the existing **oneDark** CodeMirror theme.

---

## Background: CodeMirror 6 language system

CodeMirror 6 uses [Lezer](https://lezer.codemirror.net/) for parsing. A language
extension has three parts:

1. **A Lezer grammar** (`repulse-lisp.grammar`) — describes the token rules and tree
   structure; compiled by `@lezer/generator` to a JS parser module.
2. **A highlight specification** (`highlight.js`) — maps Lezer node types to CodeMirror
   `tags` from `@lezer/highlight`.
3. **A `Language` object** (`index.js`) — wraps the parser + highlighter into a CM6
   `LanguageSupport` value ready to drop into the extensions array.

Because REPuLse currently bundles with shadow-cljs (no webpack/Rollup step for the app
layer), and the grammar compiler is a build-time tool, the generated parser must be
**pre-compiled and committed** to the repo. The workflow is:

```bash
# One-time: install the grammar compiler
npm install -D @lezer/generator

# After editing the .grammar file: regenerate the parser
npx lezer-generator app/src/repulse/lisp-lang/repulse-lisp.grammar \
    -o app/src/repulse/lisp-lang/parser.js
```

The generated `parser.js` is a plain ES module — no build step needed at dev time.

---

## File layout

```
app/src/repulse/lisp-lang/
├── repulse-lisp.grammar    # Lezer grammar source
├── parser.js               # Generated — commit this
├── highlight.js            # Tag → CSS class mapping
└── index.js                # Exports lispLanguage (LanguageSupport)
```

The `index.js` is imported in `app.cljs` alongside the other CodeMirror imports and
added to the `extensions` array in `make-editor`.

---

## Grammar

REPuLse-Lisp has a very small grammar. The token types needed:

| Lezer node name | Examples |
|---|---|
| `LineComment` | `; anything to end of line` |
| `Number` | `42`, `3.14`, `-1`, `0.5` |
| `String` | `"hello"`, `"with \"escapes\""` |
| `Keyword` | `:bd`, `:sd`, `:_`, `:wet`, any `:identifier` |
| `BuiltinName` | `seq`, `stack`, `pure`, `fast`, `slow`, `rev`, `every`, `fmap`, `def`, `let`, `fn`, `lambda`, `if`, `do`, `bpm`, `stop`, `fx`, `load-plugin`, `arrange`, `play-scenes`, `sound` |
| `Symbol` | any other `identifier` (user-defined names, `true`, `false`, `nil`) |
| `List` | `( expr* )` |
| `Vector` | `[ expr* ]` |
| `Map` | `{ expr* }` |

A minimal Lezer grammar:

```lezer
@top Program { expr* }

expr {
  List | Vector | Map |
  LineComment | Number | String | Keyword | BuiltinName | Symbol
}

List   { "(" expr* ")" }
Vector { "[" expr* "]" }
Map    { "{" expr* "}" }

@tokens {
  LineComment { ";" ![\n]* }

  Number { "-"? @digit+ ("." @digit+)? }

  String { '"' (!["\\] | "\\" _)* '"' }

  Keyword { ":" identChar+ }

  BuiltinName {
    "seq" | "stack" | "pure" | "fast" | "slow" | "rev" | "every" | "fmap" |
    "def" | "let" | "fn" | "lambda" | "if" | "do" |
    "bpm" | "stop" | "fx" | "load-plugin" | "arrange" | "play-scenes" | "sound"
  }

  Symbol { identChar+ }

  identChar { @asciiLetter | @digit | "-" | "_" | "!" | "?" | "+" | "*" | "/" | "=" | "<" | ">" | "." | "'" }

  @precedence { BuiltinName, Symbol }

  space { @whitespace+ | "," }
}

@skip { space }
```

> **Note on `@precedence`:** Lezer resolves ambiguity between `BuiltinName` and `Symbol`
> by preferring `BuiltinName` when both match — exactly what we want.

---

## Highlight mapping

```javascript
// highlight.js
import { styleTags, tags } from "@lezer/highlight";

export const repulseLispHighlight = styleTags({
  LineComment:  tags.lineComment,
  Number:       tags.number,
  String:       tags.string,
  Keyword:      tags.atom,          // :bd, :sd, ... — oneDark renders atoms as orange
  BuiltinName:  tags.keyword,       // seq, stack, ... — oneDark renders keywords as purple
  Symbol:       tags.variableName,  // user names — default colour
  "( )":        tags.paren,
  "[ ]":        tags.squareBracket,
  "{ }":        tags.brace,
});
```

Tag → oneDark colour mapping (for reference):

| Tag | oneDark colour |
|---|---|
| `tags.lineComment` | grey |
| `tags.number` | yellow-gold |
| `tags.string` | green |
| `tags.atom` | orange (`:keywords`) |
| `tags.keyword` | purple (`seq`, `stack`, …) |
| `tags.variableName` | default (light grey) |
| `tags.paren` | default or rainbow (see below) |

---

## Language object

```javascript
// index.js
import { parser } from "./parser.js";
import { LRLanguage, LanguageSupport, indentNodeProp, foldNodeProp } from "@codemirror/language";
import { styleTags, tags } from "@lezer/highlight";
import { repulseLispHighlight } from "./highlight.js";

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
  LRLanguage.define({ parser: lispParser, languageData: { commentTokens: { line: ";" } } })
);
```

---

## Integration in `app.cljs`

```clojure
;; Add to the :require block:
["./lisp-lang/index.js" :refer [lispLanguage]]

;; Add to the extensions vector in make-editor:
(defn make-editor [container initial-value on-eval]
  (let [extensions #js [(history)
                        (lineNumbers)
                        oneDark
                        lispLanguage        ; ← add here
                        highlights-field
                        (.-lineWrapping EditorView)
                        (.of keymap ...)]]
    ...))
```

CodeMirror's bracket-matching is enabled automatically when a language with paired
delimiters is present. To also show matching-bracket highlighting add
`(bracketMatching)` from `@codemirror/language` to the extensions array:

```clojure
["@codemirror/language" :refer [... bracketMatching]]

;; in extensions:
(bracketMatching)
```

---

## npm dependencies

```bash
npm install @lezer/highlight @lezer/generator
# @codemirror/language is likely already installed; check package.json
```

Add `@lezer/generator` as a `devDependency` (used only to regenerate the parser).
`@lezer/highlight` is a runtime dependency — add it to the app's `package.json`.

---

## Build step

Add a convenience script to the root `package.json`:

```json
"scripts": {
  "gen:grammar": "lezer-generator app/src/repulse/lisp-lang/repulse-lisp.grammar -o app/src/repulse/lisp-lang/parser.js"
}
```

Run once after editing the grammar:

```bash
npm run gen:grammar
```

The generated `parser.js` is committed. Developers who never edit the grammar do not
need `@lezer/generator`.

---

## Acceptance criteria

- [ ] Keywords (`:bd`, `:sd`, `:_`, …) render in orange in the editor
- [ ] Built-in names (`seq`, `stack`, `fast`, …) render in purple
- [ ] Numbers render in yellow-gold
- [ ] Strings render in green
- [ ] Comments (`;` to end of line) render in grey
- [ ] Clicking next to a `(` or `)` highlights the matching bracket
- [ ] `(bracketMatching)` is included in the extensions
- [ ] Active-event amber flash (Phase 5) still works correctly alongside syntax colours
- [ ] The `oneDark` theme is unchanged — no custom CSS overrides needed
- [ ] `npm run gen:grammar` regenerates `parser.js` cleanly
- [ ] No new runtime errors in Chrome, Firefox, or Safari
- [ ] Update CLAUDE.md, ROADMAP.md, docs/ARCHITECTURE.md phase status tables
