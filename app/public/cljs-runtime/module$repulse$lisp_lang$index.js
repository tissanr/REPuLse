var module$node_modules$$codemirror$language$dist$index_cjs = shadow.js.require("module$node_modules$$codemirror$language$dist$index_cjs", {});
var module$node_modules$$codemirror$autocomplete$dist$index_cjs = shadow.js.require("module$node_modules$$codemirror$autocomplete$dist$index_cjs", {});
var lispParser$$module$repulse$lisp_lang$index = parser$$module$repulse$lisp_lang$parser.configure({props:[repulseLispHighlight$$module$repulse$lisp_lang$highlight, module$node_modules$$codemirror$language$dist$index_cjs.indentNodeProp.add({"List":cx => cx.baseIndent + 2, "Vector":cx => cx.baseIndent + 2, "Map":cx => cx.baseIndent + 2}), module$node_modules$$codemirror$language$dist$index_cjs.foldNodeProp.add({"List":node => ({from:node.from + 1, to:node.to - 1}), "Vector":node => ({from:node.from + 
1, to:node.to - 1})})]});
var lispLanguage$$module$repulse$lisp_lang$index = new module$node_modules$$codemirror$language$dist$index_cjs.LanguageSupport(module$node_modules$$codemirror$language$dist$index_cjs.LRLanguage.define({parser:lispParser$$module$repulse$lisp_lang$index, languageData:{commentTokens:{line:";"}}}), [rainbowBrackets$$module$repulse$lisp_lang$rainbow, insertHelper$$module$repulse$lisp_lang$insert_helper, lispHoverTooltip$$module$repulse$lisp_lang$hover, (0,module$node_modules$$codemirror$autocomplete$dist$index_cjs.autocompletion)({override:[builtinCompletions$$module$repulse$lisp_lang$completions, 
defsCompletionSource$$module$repulse$lisp_lang$defs_completion, keywordsCompletionSource$$module$repulse$lisp_lang$keywords_completion]})]);
/** @const */ 
var module$repulse$lisp_lang$index = {};
/** @const */ 
module$repulse$lisp_lang$index.lispLanguage = lispLanguage$$module$repulse$lisp_lang$index;

$CLJS.module$repulse$lisp_lang$index=module$repulse$lisp_lang$index;
//# sourceMappingURL=module$repulse$lisp_lang$index.js.map
