import { styleTags, tags } from "@lezer/highlight";

export const repulseLispHighlight = styleTags({
  "LineComment":  tags.lineComment,
  "Number":       tags.number,
  "String":       tags.string,
  "Keyword":      tags.atom,         // :bd, :sd, ... — oneDark renders atoms as orange
  "BuiltinName":  tags.keyword,      // seq, stack, ... — oneDark renders keywords as purple
  "Symbol":       tags.variableName, // user-defined names — default colour
  "( )":          tags.paren,
  "[ ]":          tags.squareBracket,
  "{ }":          tags.brace,
});
