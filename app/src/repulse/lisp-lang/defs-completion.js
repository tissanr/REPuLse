import { syntaxTree } from "@codemirror/language";

// Matches any REPuLse-Lisp symbol character sequence
const SYMBOL_RE = /[a-zA-Z\-_+*\/=<>!?.][a-zA-Z0-9\-_+*\/=<>!?.']*/;

export function defsCompletionSource(context) {
  const word = context.matchBefore(SYMBOL_RE);
  if (!word && !context.explicit) return null;

  const from   = word ? word.from : context.pos;
  const prefix = word ? word.text : "";

  const names = new Set();
  syntaxTree(context.state).iterate({
    enter(node) {
      // Look for List nodes: ( def <Symbol> <expr> )
      if (node.name !== "List") return;
      let child = node.node.firstChild; // "("
      if (!child) return;
      child = child.nextSibling;        // should be BuiltinName or Symbol "def"
      if (!child) return;
      const nameText = context.state.sliceDoc(child.from, child.to);
      if (nameText !== "def") return;
      child = child.nextSibling;        // the bound Symbol
      if (!child) return;
      if (child.name !== "Symbol" && child.name !== "BuiltinName") return;
      names.add(context.state.sliceDoc(child.from, child.to));
    },
  });

  const options = [...names]
    .filter(n => n.startsWith(prefix))
    .map(n => ({ label: n, type: "variable", detail: "user-defined" }));

  if (options.length === 0 && !word) return null;
  return { from, options };
}
