import { styleTags, tags } from "@lezer/highlight";
import { HighlightStyle } from "@codemirror/language";

export const repulseLispHighlight = styleTags({
  "LineComment":  tags.lineComment,
  "Number":       tags.number,
  "String":       tags.string,
  "Keyword":      tags.atom,
  "BuiltinName":  tags.keyword,
  "Symbol":       tags.variableName,
  "OpenParen CloseParen":      tags.paren,
  "OpenBracket CloseBracket":  tags.squareBracket,
  "OpenBrace CloseBrace":      tags.brace,
});

export const repulseLispSyntaxTheme = HighlightStyle.define([
  { tag: tags.lineComment, color: "#5c6880" },
  { tag: tags.paren, color: "#e94560" },
  { tag: tags.squareBracket, color: "#c678dd" },
  { tag: tags.brace, color: "#c678dd" },
  { tag: tags.string, color: "#98c379" },
  { tag: tags.atom, color: "#56b6c2" },
  { tag: tags.number, color: "#d19a66" },
  { tag: tags.keyword, color: "#e5c07b" },
  { tag: tags.variableName, color: "#abb2bf" },
]);
