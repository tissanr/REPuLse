var module$node_modules$$codemirror$language$dist$index_cjs = shadow.js.require("module$node_modules$$codemirror$language$dist$index_cjs", {});
var KEYWORD_RE$$module$repulse$lisp_lang$keywords_completion = /:[a-zA-Z0-9\-_]*/;
function keywordsCompletionSource$$module$repulse$lisp_lang$keywords_completion(context) {
  const word = context.matchBefore(KEYWORD_RE$$module$repulse$lisp_lang$keywords_completion);
  if (!word) {
    return null;
  }
  const node = (0,module$node_modules$$codemirror$language$dist$index_cjs.syntaxTree)(context.state).resolveInner(context.pos);
  if (node.name === "String" || node.name === "LineComment") {
    return null;
  }
  const prefix = word.text.slice(1);
  const bankOptions = getBankNames$$module$repulse$lisp_lang$providers().map(b => ({label:":" + b, type:"keyword", detail:"sample bank"}));
  const fxOptions = getFxNames$$module$repulse$lisp_lang$providers().map(n => ({label:":" + n, type:"variable", detail:"effect"}));
  const staticOptions = [{label:":linear", type:"keyword", detail:"tween curve — linear ramp"}, {label:":exp", type:"keyword", detail:"tween curve — quadratic ease-in"}, {label:":sine", type:"keyword", detail:"tween curve — S-curve (sine ease)"}];
  const options = [...staticOptions, ...bankOptions, ...fxOptions].filter(opt => opt.label.slice(1).startsWith(prefix));
  if (options.length === 0) {
    return null;
  }
  return {from:word.from, options};
}
/** @const */ 
var module$repulse$lisp_lang$keywords_completion = {};
/** @const */ 
module$repulse$lisp_lang$keywords_completion.keywordsCompletionSource = keywordsCompletionSource$$module$repulse$lisp_lang$keywords_completion;

$CLJS.module$repulse$lisp_lang$keywords_completion=module$repulse$lisp_lang$keywords_completion;
//# sourceMappingURL=module$repulse$lisp_lang$keywords_completion.js.map
