import { parser } from "./parser.js";
import { LRLanguage, LanguageSupport, indentNodeProp, foldNodeProp } from "@codemirror/language";
import { repulseLispHighlight } from "./highlight.js";
import { rainbowBrackets } from "./rainbow.js";

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
  [rainbowBrackets]
);
