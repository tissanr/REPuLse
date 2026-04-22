var module$node_modules$$codemirror$language$dist$index_cjs = shadow.js.require("module$node_modules$$codemirror$language$dist$index_cjs", {});
var SYMBOL_RE$$module$repulse$lisp_lang$defs_completion = /[a-zA-Z\-_+*\/=<>!?.][a-zA-Z0-9\-_+*\/=<>!?.']*/;
function defsCompletionSource$$module$repulse$lisp_lang$defs_completion(context) {
  const word = context.matchBefore(SYMBOL_RE$$module$repulse$lisp_lang$defs_completion);
  if (!word && !context.explicit) {
    return null;
  }
  const from = word ? word.from : context.pos;
  const prefix = word ? word.text : "";
  const names = new Set();
  (0,module$node_modules$$codemirror$language$dist$index_cjs.syntaxTree)(context.state).iterate({enter(node) {
    if (node.name !== "List") {
      return;
    }
    let child = node.node.firstChild;
    if (!child) {
      return;
    }
    child = child.nextSibling;
    if (!child) {
      return;
    }
    const nameText = context.state.sliceDoc(child.from, child.to);
    if (nameText !== "def") {
      return;
    }
    child = child.nextSibling;
    if (!child) {
      return;
    }
    if (child.name !== "Symbol" && child.name !== "BuiltinName") {
      return;
    }
    names.add(context.state.sliceDoc(child.from, child.to));
  }});
  const options = [...names].filter(n => n.startsWith(prefix)).map(n => ({label:n, type:"variable", detail:"user-defined"}));
  if (options.length === 0 && !word) {
    return null;
  }
  return {from, options};
}
/** @const */ 
var module$repulse$lisp_lang$defs_completion = {};
/** @const */ 
module$repulse$lisp_lang$defs_completion.defsCompletionSource = defsCompletionSource$$module$repulse$lisp_lang$defs_completion;

$CLJS.module$repulse$lisp_lang$defs_completion=module$repulse$lisp_lang$defs_completion;
//# sourceMappingURL=module$repulse$lisp_lang$defs_completion.js.map
