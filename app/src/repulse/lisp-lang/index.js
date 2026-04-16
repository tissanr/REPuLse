import { parser } from "./parser.js";
import { LRLanguage, LanguageSupport, indentNodeProp, foldNodeProp } from "@codemirror/language";
import { autocompletion } from "@codemirror/autocomplete";
import { repulseLispHighlight } from "./highlight.js";
import { rainbowBrackets } from "./rainbow.js";
import { builtinCompletions } from "./completions.js";
import { defsCompletionSource } from "./defs-completion.js";
import { keywordsCompletionSource } from "./keywords-completion.js";
import { lispHoverTooltip } from "./hover.js";
import { insertHelper } from "./insert-helper.js";

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
    insertHelper,
    lispHoverTooltip,
    autocompletion({ override: [builtinCompletions, defsCompletionSource, keywordsCompletionSource] }),
  ]
);
