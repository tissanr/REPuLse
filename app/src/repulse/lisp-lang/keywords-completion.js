import { syntaxTree } from "@codemirror/language";
import { getBankNames, getFxNames } from "./providers.js";

// Matches ':' followed by any identifier chars (empty suffix is valid — triggers on ':')
const KEYWORD_RE = /:[a-zA-Z0-9\-_]*/;

export function keywordsCompletionSource(context) {
  const word = context.matchBefore(KEYWORD_RE);
  if (!word) return null;

  // Don't complete inside strings or comments
  const node = syntaxTree(context.state).resolveInner(context.pos);
  if (node.name === "String" || node.name === "LineComment") return null;

  const prefix = word.text.slice(1); // strip leading ':'

  const bankOptions = getBankNames().map(b => ({
    label:  ":" + b,
    type:   "keyword",
    detail: "sample bank",
  }));

  const fxOptions = getFxNames().map(n => ({
    label:  ":" + n,
    type:   "variable",
    detail: "effect",
  }));

  const staticOptions = [
    { label: ":linear", type: "keyword", detail: "tween curve — linear ramp" },
    { label: ":exp",    type: "keyword", detail: "tween curve — quadratic ease-in" },
    { label: ":sine",   type: "keyword", detail: "tween curve — S-curve (sine ease)" },
  ];

  const options = [...staticOptions, ...bankOptions, ...fxOptions]
    .filter(opt => opt.label.slice(1).startsWith(prefix));

  if (options.length === 0) return null;
  return { from: word.from, options };
}
