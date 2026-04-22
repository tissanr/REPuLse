var module$node_modules$$codemirror$view$dist$index_cjs = shadow.js.require("module$node_modules$$codemirror$view$dist$index_cjs", {});
var module$node_modules$$codemirror$language$dist$index_cjs = shadow.js.require("module$node_modules$$codemirror$language$dist$index_cjs", {});
var module$node_modules$$codemirror$state$dist$index_cjs = shadow.js.require("module$node_modules$$codemirror$state$dist$index_cjs", {});
var LEVELS$$module$repulse$lisp_lang$rainbow = 6;
var MARKS$$module$repulse$lisp_lang$rainbow = Array.from({length:LEVELS$$module$repulse$lisp_lang$rainbow}, (_, i) => module$node_modules$$codemirror$view$dist$index_cjs.Decoration.mark({class:`rainbow-${i + 1}`}));
var BRACKET_NODES$$module$repulse$lisp_lang$rainbow = new Set(["List", "Vector", "Map"]);
function buildDecorations$$module$repulse$lisp_lang$rainbow(view) {
  const builder = new module$node_modules$$codemirror$state$dist$index_cjs.RangeSetBuilder();
  const tree = (0,module$node_modules$$codemirror$language$dist$index_cjs.syntaxTree)(view.state);
  const items = [];
  let depth = 0;
  tree.iterate({enter(node) {
    if (BRACKET_NODES$$module$repulse$lisp_lang$rainbow.has(node.name)) {
      const mark = MARKS$$module$repulse$lisp_lang$rainbow[depth % LEVELS$$module$repulse$lisp_lang$rainbow];
      items.push([node.from, node.from + 1, mark]);
      items.push([node.to - 1, node.to, mark]);
      depth++;
    }
  }, leave(node) {
    if (BRACKET_NODES$$module$repulse$lisp_lang$rainbow.has(node.name)) {
      depth--;
    }
  }});
  items.sort((a, b) => a[0] - b[0]);
  for (const [from, to, mark] of items) {
    builder.add(from, to, mark);
  }
  return builder.finish();
}
var repulse$lisp_lang$rainbow$classdecl$var0 = class {
  constructor(view) {
    this.decorations = buildDecorations$$module$repulse$lisp_lang$rainbow(view);
  }
  update(update) {
    if (update.docChanged || update.viewportChanged) {
      this.decorations = buildDecorations$$module$repulse$lisp_lang$rainbow(update.view);
    }
  }
};
var rainbowBrackets$$module$repulse$lisp_lang$rainbow = module$node_modules$$codemirror$view$dist$index_cjs.ViewPlugin.fromClass(repulse$lisp_lang$rainbow$classdecl$var0, {decorations:v => v.decorations});
/** @const */ 
var module$repulse$lisp_lang$rainbow = {};
/** @const */ 
module$repulse$lisp_lang$rainbow.rainbowBrackets = rainbowBrackets$$module$repulse$lisp_lang$rainbow;

$CLJS.module$repulse$lisp_lang$rainbow=module$repulse$lisp_lang$rainbow;
//# sourceMappingURL=module$repulse$lisp_lang$rainbow.js.map
