goog.provide('repulse.ui.editor');
var module$node_modules$$codemirror$view$dist$index_cjs=shadow.js.require("module$node_modules$$codemirror$view$dist$index_cjs", {});
var module$node_modules$$codemirror$state$dist$index_cjs=shadow.js.require("module$node_modules$$codemirror$state$dist$index_cjs", {});
var module$node_modules$$codemirror$commands$dist$index_cjs=shadow.js.require("module$node_modules$$codemirror$commands$dist$index_cjs", {});
var module$node_modules$$codemirror$language$dist$index_cjs=shadow.js.require("module$node_modules$$codemirror$language$dist$index_cjs", {});
var module$node_modules$$codemirror$lint$dist$index_cjs=shadow.js.require("module$node_modules$$codemirror$lint$dist$index_cjs", {});
var module$node_modules$$codemirror$theme_one_dark$dist$index_cjs=shadow.js.require("module$node_modules$$codemirror$theme_one_dark$dist$index_cjs", {});
if((typeof repulse !== 'undefined') && (typeof repulse.ui !== 'undefined') && (typeof repulse.ui.editor !== 'undefined') && (typeof repulse.ui.editor.editor_view !== 'undefined')){
} else {
repulse.ui.editor.editor_view = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
if((typeof repulse !== 'undefined') && (typeof repulse.ui !== 'undefined') && (typeof repulse.ui.editor !== 'undefined') && (typeof repulse.ui.editor.cmd_view !== 'undefined')){
} else {
repulse.ui.editor.cmd_view = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
repulse.ui.editor.set_highlights_effect = module$node_modules$$codemirror$state$dist$index_cjs.StateEffect.define();
repulse.ui.editor.highlights_field = module$node_modules$$codemirror$state$dist$index_cjs.StateField.define(({"create": (function (_){
return module$node_modules$$codemirror$view$dist$index_cjs.Decoration.none;
}), "update": (function (decs,tr){
var mapped = decs.map(tr.changes);
var temp__5802__auto__ = cljs.core.some((function (p1__10028_SHARP_){
if(cljs.core.truth_(p1__10028_SHARP_.is(repulse.ui.editor.set_highlights_effect))){
return p1__10028_SHARP_;
} else {
return null;
}
}),cljs.core.array_seq.cljs$core$IFn$_invoke$arity$1(tr.effects));
if(cljs.core.truth_(temp__5802__auto__)){
var eff = temp__5802__auto__;
return eff.value;
} else {
return mapped;
}
}), "provide": (function (f){
return module$node_modules$$codemirror$view$dist$index_cjs.EditorView.decorations.from(f);
})}));
repulse.ui.editor.active_mark = module$node_modules$$codemirror$view$dist$index_cjs.Decoration.mark(({"class": "active-event"}));
if((typeof repulse !== 'undefined') && (typeof repulse.ui !== 'undefined') && (typeof repulse.ui.editor !== 'undefined') && (typeof repulse.ui.editor.active_ranges !== 'undefined')){
} else {
repulse.ui.editor.active_ranges = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentVector.EMPTY);
}
repulse.ui.editor.rebuild_decorations_BANG_ = (function repulse$ui$editor$rebuild_decorations_BANG_(view){
var ranges = cljs.core.sort_by.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"from","from",1815293044),cljs.core.deref(repulse.ui.editor.active_ranges));
var range_objs = cljs.core.keep.cljs$core$IFn$_invoke$arity$2((function (p__10052){
var map__10054 = p__10052;
var map__10054__$1 = cljs.core.__destructure_map(map__10054);
var from = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10054__$1,new cljs.core.Keyword(null,"from","from",1815293044));
var to = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10054__$1,new cljs.core.Keyword(null,"to","to",192099007));
try{return repulse.ui.editor.active_mark.range(from,to);
}catch (e10058){var _ = e10058;
return null;
}}),ranges);
var deco_set = ((cljs.core.seq(range_objs))?module$node_modules$$codemirror$view$dist$index_cjs.Decoration.set(cljs.core.clj__GT_js(range_objs)):module$node_modules$$codemirror$view$dist$index_cjs.Decoration.none);
return view.dispatch(({"effects": [repulse.ui.editor.set_highlights_effect.of(deco_set)]}));
});
repulse.ui.editor.highlight_range_BANG_ = (function repulse$ui$editor$highlight_range_BANG_(p__10064){
var map__10065 = p__10064;
var map__10065__$1 = cljs.core.__destructure_map(map__10065);
var from = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10065__$1,new cljs.core.Keyword(null,"from","from",1815293044));
var to = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10065__$1,new cljs.core.Keyword(null,"to","to",192099007));
var temp__5804__auto__ = cljs.core.deref(repulse.ui.editor.editor_view);
if(cljs.core.truth_(temp__5804__auto__)){
var view = temp__5804__auto__;
var doc_len = view.state.doc.length;
var from_SINGLEQUOTE_ = (function (){var x__5090__auto__ = from;
var y__5091__auto__ = doc_len;
return ((x__5090__auto__ < y__5091__auto__) ? x__5090__auto__ : y__5091__auto__);
})();
var to_SINGLEQUOTE_ = (function (){var x__5090__auto__ = to;
var y__5091__auto__ = doc_len;
return ((x__5090__auto__ < y__5091__auto__) ? x__5090__auto__ : y__5091__auto__);
})();
if((from_SINGLEQUOTE_ < to_SINGLEQUOTE_)){
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(repulse.ui.editor.active_ranges,cljs.core.conj,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"from","from",1815293044),from_SINGLEQUOTE_,new cljs.core.Keyword(null,"to","to",192099007),to_SINGLEQUOTE_], null));

repulse.ui.editor.rebuild_decorations_BANG_(view);

return setTimeout((function (){
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$2(repulse.ui.editor.active_ranges,(function (rs){
return cljs.core.filterv((function (p1__10059_SHARP_){
return (!(((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"from","from",1815293044).cljs$core$IFn$_invoke$arity$1(p1__10059_SHARP_),from_SINGLEQUOTE_)) && (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"to","to",192099007).cljs$core$IFn$_invoke$arity$1(p1__10059_SHARP_),to_SINGLEQUOTE_)))));
}),rs);
}));

var temp__5804__auto____$1 = cljs.core.deref(repulse.ui.editor.editor_view);
if(cljs.core.truth_(temp__5804__auto____$1)){
var v = temp__5804__auto____$1;
return repulse.ui.editor.rebuild_decorations_BANG_(v);
} else {
return null;
}
}),(120));
} else {
return null;
}
} else {
return null;
}
});
repulse.ui.editor.clear_highlights_BANG_ = (function repulse$ui$editor$clear_highlights_BANG_(){
cljs.core.reset_BANG_(repulse.ui.editor.active_ranges,cljs.core.PersistentVector.EMPTY);

var temp__5804__auto__ = cljs.core.deref(repulse.ui.editor.editor_view);
if(cljs.core.truth_(temp__5804__auto__)){
var view = temp__5804__auto__;
return repulse.ui.editor.rebuild_decorations_BANG_(view);
} else {
return null;
}
});
repulse.ui.editor.save_listener = module$node_modules$$codemirror$view$dist$index_cjs.EditorView.updateListener.of((function (update){
if(cljs.core.truth_(update.docChanged)){
return repulse.session.schedule_save_BANG_();
} else {
return null;
}
}));
repulse.ui.editor.clear_diag_listener = module$node_modules$$codemirror$view$dist$index_cjs.EditorView.updateListener.of((function (update){
if(cljs.core.truth_(update.docChanged)){
return update.view.dispatch(module$node_modules$$codemirror$lint$dist$index_cjs.setDiagnostics(update.state,[]));
} else {
return null;
}
}));
/**
 * Single-line CodeMirror editor for the command bar. Enter evaluates + clears.
 * container   — DOM element to mount into.
 * evaluate-fn — fn called with the code string to evaluate.
 */
repulse.ui.editor.make_cmd_editor = (function repulse$ui$editor$make_cmd_editor(container,evaluate_fn){
var clear_view_BANG_ = (function (view){
view.dispatch(({"changes": ({"from": (0), "to": view.state.doc.length, "insert": ""})}));

return true;
});
var eval_cmd = (function (view){
var raw_10082 = clojure.string.trim(view.state.doc.toString());
if(cljs.core.seq(raw_10082)){
var code_10083 = ((clojure.string.starts_with_QMARK_(raw_10082,"("))?raw_10082:["(",raw_10082,")"].join(''));
(evaluate_fn.cljs$core$IFn$_invoke$arity$1 ? evaluate_fn.cljs$core$IFn$_invoke$arity$1(code_10083) : evaluate_fn.call(null,code_10083));

clear_view_BANG_(view);
} else {
}

return true;
});
var clear_PLUS_return_BANG_ = (function (view){
clear_view_BANG_(view);

var temp__5804__auto___10084 = cljs.core.deref(repulse.ui.editor.editor_view);
if(cljs.core.truth_(temp__5804__auto___10084)){
var ev_10085 = temp__5804__auto___10084;
ev_10085.focus();
} else {
}

return true;
});
var extensions = [module$node_modules$$codemirror$theme_one_dark$dist$index_cjs.oneDark,module$repulse$lisp_lang$index.lispLanguage,module$node_modules$$codemirror$view$dist$index_cjs.keymap.of([({"key": "Mod-a", "run": module$node_modules$$codemirror$commands$dist$index_cjs.selectAll}),({"key": "Enter", "run": eval_cmd}),({"key": "Escape", "run": clear_PLUS_return_BANG_})])];
var state = module$node_modules$$codemirror$state$dist$index_cjs.EditorState.create(({"doc": "", "extensions": extensions}));
var view = (new module$node_modules$$codemirror$view$dist$index_cjs.EditorView(({"state": state, "parent": container})));
return view;
});
/**
 * Create the main multi-line CodeMirror editor.
 * container     — DOM element to mount into.
 * initial-value — initial text content.
 * on-eval       — fn called with code string on Alt+Enter / F9 / Ctrl+.
 */
repulse.ui.editor.make_editor = (function repulse$ui$editor$make_editor(container,initial_value,on_eval){
var eval_cmd = (function (view){
var G__10080_10086 = view.state.doc.toString();
(on_eval.cljs$core$IFn$_invoke$arity$1 ? on_eval.cljs$core$IFn$_invoke$arity$1(G__10080_10086) : on_eval.call(null,G__10080_10086));

return true;
});
var eval_binding = ({"key": "Alt-Enter", "run": eval_cmd});
var upd_fn = (function (_){
(on_eval.cljs$core$IFn$_invoke$arity$1 ? on_eval.cljs$core$IFn$_invoke$arity$1("(upd)") : on_eval.call(null,"(upd)"));

return true;
});
var upd_binding = ({"key": "Ctrl-.", "run": upd_fn});
var upd_f9_binding = ({"key": "F9", "run": upd_fn});
var escape_binding = ({"key": "Escape", "run": (function (_){
var temp__5804__auto___10087 = cljs.core.deref(repulse.ui.editor.cmd_view);
if(cljs.core.truth_(temp__5804__auto___10087)){
var cv_10088 = temp__5804__auto___10087;
cv_10088.focus();
} else {
}

return true;
})});
var extensions = [module$node_modules$$codemirror$commands$dist$index_cjs.history(),module$node_modules$$codemirror$view$dist$index_cjs.lineNumbers(),module$node_modules$$codemirror$theme_one_dark$dist$index_cjs.oneDark,module$repulse$lisp_lang$index.lispLanguage,module$node_modules$$codemirror$language$dist$index_cjs.bracketMatching(),repulse.ui.editor.highlights_field,module$node_modules$$codemirror$lint$dist$index_cjs.lintGutter(),repulse.ui.editor.save_listener,repulse.ui.editor.clear_diag_listener,module$node_modules$$codemirror$view$dist$index_cjs.EditorView.lineWrapping,module$node_modules$$codemirror$view$dist$index_cjs.keymap.of([escape_binding,eval_binding,upd_binding,upd_f9_binding].concat(cljs.core.clj__GT_js(module$node_modules$$codemirror$commands$dist$index_cjs.defaultKeymap),cljs.core.clj__GT_js(module$node_modules$$codemirror$commands$dist$index_cjs.historyKeymap)))];
var state = module$node_modules$$codemirror$state$dist$index_cjs.EditorState.create(({"doc": initial_value, "extensions": extensions}));
var view = (new module$node_modules$$codemirror$view$dist$index_cjs.EditorView(({"state": state, "parent": container})));
return view;
});

//# sourceMappingURL=repulse.ui.editor.js.map
