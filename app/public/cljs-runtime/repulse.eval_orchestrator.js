goog.provide('repulse.eval_orchestrator');
var module$node_modules$$codemirror$lint$dist$index_cjs=shadow.js.require("module$node_modules$$codemirror$lint$dist$index_cjs", {});
if((typeof repulse !== 'undefined') && (typeof repulse.eval_orchestrator !== 'undefined') && (typeof repulse.eval_orchestrator.pending_mutes !== 'undefined')){
} else {
repulse.eval_orchestrator.pending_mutes = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentHashSet.EMPTY);
}
if((typeof repulse !== 'undefined') && (typeof repulse.eval_orchestrator !== 'undefined') && (typeof repulse.eval_orchestrator.slider_timeout !== 'undefined')){
} else {
repulse.eval_orchestrator.slider_timeout = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
if((typeof repulse !== 'undefined') && (typeof repulse.eval_orchestrator !== 'undefined') && (typeof repulse.eval_orchestrator.cbs !== 'undefined')){
} else {
repulse.eval_orchestrator.cbs = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
}
/**
 * Wire app-level callback fns into this module.
 * Must be called during app init before the first evaluation.
 * config — {:on-beat-fn f :make-stop-fn-fn f :set-playing!-fn f :set-output!-fn f}
 */
repulse.eval_orchestrator.init_BANG_ = (function repulse$eval_orchestrator$init_BANG_(p__11109){
var map__11110 = p__11109;
var map__11110__$1 = cljs.core.__destructure_map(map__11110);
var on_beat_fn = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11110__$1,new cljs.core.Keyword(null,"on-beat-fn","on-beat-fn",567217350));
var make_stop_fn_fn = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11110__$1,new cljs.core.Keyword(null,"make-stop-fn-fn","make-stop-fn-fn",1610235793));
var set_playing_BANG__fn = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11110__$1,new cljs.core.Keyword(null,"set-playing!-fn","set-playing!-fn",1851430687));
var set_output_BANG__fn = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11110__$1,new cljs.core.Keyword(null,"set-output!-fn","set-output!-fn",1617998012));
return cljs.core.reset_BANG_(repulse.eval_orchestrator.cbs,new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"on-beat","on-beat",1078535864),on_beat_fn,new cljs.core.Keyword(null,"make-stop-fn","make-stop-fn",-2002471956),make_stop_fn_fn,new cljs.core.Keyword(null,"set-playing!","set-playing!",1429676448),set_playing_BANG__fn,new cljs.core.Keyword(null,"set-output!","set-output!",-419558317),set_output_BANG__fn], null));
});
/**
 * Push a single error diagnostic into the editor, or clear all diagnostics.
 * Pass nil for from/to/message to clear.
 */
repulse.eval_orchestrator.set_diagnostics_BANG_ = (function repulse$eval_orchestrator$set_diagnostics_BANG_(view,from,to,message){
var diags = (cljs.core.truth_((function (){var and__5000__auto__ = from;
if(cljs.core.truth_(and__5000__auto__)){
var and__5000__auto____$1 = to;
if(cljs.core.truth_(and__5000__auto____$1)){
return (from < to);
} else {
return and__5000__auto____$1;
}
} else {
return and__5000__auto__;
}
})())?[({"from": from, "to": to, "severity": "error", "message": message})]:[]);
return view.dispatch(module$node_modules$$codemirror$lint$dist$index_cjs.setDiagnostics(view.state,diags));
});
repulse.eval_orchestrator.evaluate_BANG_ = (function repulse$eval_orchestrator$evaluate_BANG_(code){
var map__11112 = cljs.core.deref(repulse.eval_orchestrator.cbs);
var map__11112__$1 = cljs.core.__destructure_map(map__11112);
var on_beat = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11112__$1,new cljs.core.Keyword(null,"on-beat","on-beat",1078535864));
var make_stop_fn = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11112__$1,new cljs.core.Keyword(null,"make-stop-fn","make-stop-fn",-2002471956));
var set_playing_BANG_ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11112__$1,new cljs.core.Keyword(null,"set-playing!","set-playing!",1429676448));
var set_output_BANG_ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11112__$1,new cljs.core.Keyword(null,"set-output!","set-output!",-419558317));
repulse.env.builtins.ensure_env_BANG_();

cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$2(repulse.fx.chain,(function (c){
return cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__11111_SHARP_){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(p1__11111_SHARP_,new cljs.core.Keyword(null,"active?","active?",459499776),false);
}),c);
}));

cljs.core.reset_BANG_(repulse.env.builtins.seen_tracks,cljs.core.PersistentHashSet.EMPTY);

var env = cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(cljs.core.deref(repulse.env.builtins.env_atom),"stop",(make_stop_fn.cljs$core$IFn$_invoke$arity$0 ? make_stop_fn.cljs$core$IFn$_invoke$arity$0() : make_stop_fn.call(null)));
var result = repulse.lisp.core.eval_string(code,env);
if(repulse.lisp.core.eval_error_QMARK_(result)){
repulse.ui.editor.clear_highlights_BANG_();

var temp__5804__auto___11137 = cljs.core.deref(repulse.ui.editor.editor_view);
if(cljs.core.truth_(temp__5804__auto___11137)){
var view_11138 = temp__5804__auto___11137;
var map__11115_11139 = new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(result);
var map__11115_11140__$1 = cljs.core.__destructure_map(map__11115_11139);
var from_11141 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11115_11140__$1,new cljs.core.Keyword(null,"from","from",1815293044));
var to_11142 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11115_11140__$1,new cljs.core.Keyword(null,"to","to",192099007));
repulse.eval_orchestrator.set_diagnostics_BANG_(view_11138,from_11141,to_11142,new cljs.core.Keyword(null,"message","message",-406056002).cljs$core$IFn$_invoke$arity$1(result));
} else {
}

var G__11116 = ["Error: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"message","message",-406056002).cljs$core$IFn$_invoke$arity$1(result))].join('');
var G__11117 = new cljs.core.Keyword(null,"error","error",-978969032);
return (set_output_BANG_.cljs$core$IFn$_invoke$arity$2 ? set_output_BANG_.cljs$core$IFn$_invoke$arity$2(G__11116,G__11117) : set_output_BANG_.call(null,G__11116,G__11117));
} else {
var val = new cljs.core.Keyword(null,"result","result",1415092211).cljs$core$IFn$_invoke$arity$1(result);
if((!((val == null)))){
var temp__5804__auto___11143 = cljs.core.deref(repulse.ui.editor.editor_view);
if(cljs.core.truth_(temp__5804__auto___11143)){
var view_11144 = temp__5804__auto___11143;
repulse.eval_orchestrator.set_diagnostics_BANG_(view_11144,null,null,null);
} else {
}
} else {
}

if(repulse.core.pattern_QMARK_(val)){
repulse.audio.stop_BANG_();

repulse.ui.editor.clear_highlights_BANG_();

repulse.audio.start_BANG_(val,on_beat,repulse.ui.editor.highlight_range_BANG_);

(set_playing_BANG_.cljs$core$IFn$_invoke$arity$1 ? set_playing_BANG_.cljs$core$IFn$_invoke$arity$1(true) : set_playing_BANG_.call(null,true));

(set_output_BANG_.cljs$core$IFn$_invoke$arity$2 ? set_output_BANG_.cljs$core$IFn$_invoke$arity$2("playing pattern \u2014 Alt+Enter to re-evaluate, (stop) to stop",new cljs.core.Keyword(null,"success","success",1890645906)) : set_output_BANG_.call(null,"playing pattern \u2014 Alt+Enter to re-evaluate, (stop) to stop",new cljs.core.Keyword(null,"success","success",1890645906)));
} else {
if(cljs.core.truth_((function (){var and__5000__auto__ = cljs.core.map_QMARK_(val);
if(and__5000__auto__){
var or__5002__auto__ = new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(val);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
var or__5002__auto____$1 = new cljs.core.Keyword(null,"bank","bank",-1982531798).cljs$core$IFn$_invoke$arity$1(val);
if(cljs.core.truth_(or__5002__auto____$1)){
return or__5002__auto____$1;
} else {
return new cljs.core.Keyword(null,"synth","synth",-862700847).cljs$core$IFn$_invoke$arity$1(val);
}
}
} else {
return and__5000__auto__;
}
})())){
repulse.audio.stop_BANG_();

repulse.ui.editor.clear_highlights_BANG_();

repulse.audio.start_BANG_(repulse.core.pure.cljs$core$IFn$_invoke$arity$1(val),on_beat,repulse.ui.editor.highlight_range_BANG_);

(set_playing_BANG_.cljs$core$IFn$_invoke$arity$1 ? set_playing_BANG_.cljs$core$IFn$_invoke$arity$1(true) : set_playing_BANG_.call(null,true));

(set_output_BANG_.cljs$core$IFn$_invoke$arity$2 ? set_output_BANG_.cljs$core$IFn$_invoke$arity$2("playing \u2014 Alt+Enter to re-evaluate, (stop) to stop",new cljs.core.Keyword(null,"success","success",1890645906)) : set_output_BANG_.call(null,"playing \u2014 Alt+Enter to re-evaluate, (stop) to stop",new cljs.core.Keyword(null,"success","success",1890645906)));
} else {
if((val == null)){
} else {
if(typeof val === 'string'){
(set_output_BANG_.cljs$core$IFn$_invoke$arity$2 ? set_output_BANG_.cljs$core$IFn$_invoke$arity$2(val,new cljs.core.Keyword(null,"success","success",1890645906)) : set_output_BANG_.call(null,val,new cljs.core.Keyword(null,"success","success",1890645906)));
} else {
var G__11121_11145 = ["=> ",cljs.core.pr_str.cljs$core$IFn$_invoke$arity$variadic(cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([val], 0))].join('');
var G__11122_11146 = new cljs.core.Keyword(null,"success","success",1890645906);
(set_output_BANG_.cljs$core$IFn$_invoke$arity$2 ? set_output_BANG_.cljs$core$IFn$_invoke$arity$2(G__11121_11145,G__11122_11146) : set_output_BANG_.call(null,G__11121_11145,G__11122_11146));

}
}
}
}

if((((!(repulse.core.pattern_QMARK_(val)))) && (cljs.core.contains_QMARK_(new cljs.core.Keyword(null,"tracks","tracks",-326768501).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.audio.scheduler_state)),new cljs.core.Keyword(null,"_","_",1453416199))))){
var defs_vals_11147 = cljs.core.vals(cljs.core.deref(new cljs.core.Keyword(null,"*defs*","*defs*",1742801364).cljs$core$IFn$_invoke$arity$1(env)));
var pats_11148 = cljs.core.filter.cljs$core$IFn$_invoke$arity$2(repulse.core.pattern_QMARK_,defs_vals_11147);
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2((1),cljs.core.count(pats_11148))){
repulse.audio.play_track_BANG_(new cljs.core.Keyword(null,"_","_",1453416199),cljs.core.first(pats_11148),on_beat,repulse.ui.editor.highlight_range_BANG_);
} else {
}
} else {
}

if(cljs.core.seq(cljs.core.deref(repulse.eval_orchestrator.pending_mutes))){
var tracks = cljs.core.set(cljs.core.keys(new cljs.core.Keyword(null,"tracks","tracks",-326768501).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.audio.scheduler_state))));
if(cljs.core.seq(tracks)){
var seq__11123_11149 = cljs.core.seq(cljs.core.deref(repulse.eval_orchestrator.pending_mutes));
var chunk__11124_11150 = null;
var count__11125_11151 = (0);
var i__11126_11152 = (0);
while(true){
if((i__11126_11152 < count__11125_11151)){
var tk_11153 = chunk__11124_11150.cljs$core$IIndexed$_nth$arity$2(null,i__11126_11152);
if(cljs.core.contains_QMARK_(tracks,tk_11153)){
repulse.audio.mute_track_BANG_(tk_11153);
} else {
}


var G__11154 = seq__11123_11149;
var G__11155 = chunk__11124_11150;
var G__11156 = count__11125_11151;
var G__11157 = (i__11126_11152 + (1));
seq__11123_11149 = G__11154;
chunk__11124_11150 = G__11155;
count__11125_11151 = G__11156;
i__11126_11152 = G__11157;
continue;
} else {
var temp__5804__auto___11158 = cljs.core.seq(seq__11123_11149);
if(temp__5804__auto___11158){
var seq__11123_11159__$1 = temp__5804__auto___11158;
if(cljs.core.chunked_seq_QMARK_(seq__11123_11159__$1)){
var c__5525__auto___11160 = cljs.core.chunk_first(seq__11123_11159__$1);
var G__11161 = cljs.core.chunk_rest(seq__11123_11159__$1);
var G__11162 = c__5525__auto___11160;
var G__11163 = cljs.core.count(c__5525__auto___11160);
var G__11164 = (0);
seq__11123_11149 = G__11161;
chunk__11124_11150 = G__11162;
count__11125_11151 = G__11163;
i__11126_11152 = G__11164;
continue;
} else {
var tk_11165 = cljs.core.first(seq__11123_11159__$1);
if(cljs.core.contains_QMARK_(tracks,tk_11165)){
repulse.audio.mute_track_BANG_(tk_11165);
} else {
}


var G__11166 = cljs.core.next(seq__11123_11159__$1);
var G__11167 = null;
var G__11168 = (0);
var G__11169 = (0);
seq__11123_11149 = G__11166;
chunk__11124_11150 = G__11167;
count__11125_11151 = G__11168;
i__11126_11152 = G__11169;
continue;
}
} else {
}
}
break;
}

return cljs.core.reset_BANG_(repulse.eval_orchestrator.pending_mutes,cljs.core.PersistentHashSet.EMPTY);
} else {
return null;
}
} else {
return null;
}
}
});
/**
 * Find the first `(param-name NUMBER` in editor text, starting from the position
 * of `:track-name`, and replace the number with new-val.
 */
repulse.eval_orchestrator.patch_param_in_editor_BANG_ = (function repulse$eval_orchestrator$patch_param_in_editor_BANG_(track_name,param_name,new_val){
var temp__5804__auto__ = cljs.core.deref(repulse.ui.editor.editor_view);
if(cljs.core.truth_(temp__5804__auto__)){
var view = temp__5804__auto__;
var doc = view.state.doc.toString();
var track_kw = [":",cljs.core.str.cljs$core$IFn$_invoke$arity$1(track_name)].join('');
var track_pos = (function (){var p = doc.indexOf(track_kw);
if((p >= (0))){
return p;
} else {
return (0);
}
})();
var sub_doc = doc.substring(track_pos);
var re = (new RegExp(["\\(",cljs.core.str.cljs$core$IFn$_invoke$arity$1(param_name),"\\s+(-?[0-9]*\\.?[0-9]+)"].join('')));
var match = re.exec(sub_doc);
if(cljs.core.truth_(match)){
var full = (match[(0)]);
var num = (match[(1)]);
var start = ((track_pos + match.index) + (full.length - num.length));
var end = (start + num.length);
var fmtd = (((new_val === Math.round(new_val)))?cljs.core.str.cljs$core$IFn$_invoke$arity$1((new_val | (0))):new_val.toFixed((2)));
return view.dispatch(({"changes": ({"from": start, "to": end, "insert": fmtd})}));
} else {
return null;
}
} else {
return null;
}
});
repulse.eval_orchestrator.slider_patch_and_eval_BANG_ = (function repulse$eval_orchestrator$slider_patch_and_eval_BANG_(track_name,param_name,new_val){
repulse.eval_orchestrator.patch_param_in_editor_BANG_(track_name,param_name,new_val);

if(cljs.core.truth_(cljs.core.deref(repulse.eval_orchestrator.slider_timeout))){
clearTimeout(cljs.core.deref(repulse.eval_orchestrator.slider_timeout));
} else {
}

return cljs.core.reset_BANG_(repulse.eval_orchestrator.slider_timeout,setTimeout((function (){
cljs.core.reset_BANG_(repulse.eval_orchestrator.slider_timeout,null);

var temp__5804__auto__ = cljs.core.deref(repulse.ui.editor.editor_view);
if(cljs.core.truth_(temp__5804__auto__)){
var view = temp__5804__auto__;
return repulse.eval_orchestrator.evaluate_BANG_(view.state.doc.toString());
} else {
return null;
}
}),(150)));
});
/**
 * Update or insert a param in a (fx :effect-name ...) call.
 * Named :param-name NUMBER exists -> replace the number.
 * Positional (fx :effect-name NUMBER) for primary param -> replace.
 * Not found -> insert :param-name value before the closing ).
 */
repulse.eval_orchestrator.patch_fx_param_in_editor_BANG_ = (function repulse$eval_orchestrator$patch_fx_param_in_editor_BANG_(effect_name,param_name,new_val){
var temp__5804__auto__ = cljs.core.deref(repulse.ui.editor.editor_view);
if(cljs.core.truth_(temp__5804__auto__)){
var view = temp__5804__auto__;
var doc = view.state.doc.toString();
var primary_QMARK_ = cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(param_name,cljs.core.get.cljs$core$IFn$_invoke$arity$2(repulse.ui.context_panel.FX_PRIMARY_PARAM,effect_name));
var fmtd = (((new_val === Math.round(new_val)))?cljs.core.str.cljs$core$IFn$_invoke$arity$1((new_val | (0))):new_val.toFixed((2)));
var re_named = (new RegExp(["\\(fx\\s+:",cljs.core.str.cljs$core$IFn$_invoke$arity$1(effect_name),"[^)]*:",cljs.core.str.cljs$core$IFn$_invoke$arity$1(param_name),"\\s+(-?[0-9]*\\.?[0-9]+)"].join('')));
var match = re_named.exec(doc);
var re_pos = ((((cljs.core.not(match)) && (primary_QMARK_)))?(new RegExp(["\\(fx\\s+:",cljs.core.str.cljs$core$IFn$_invoke$arity$1(effect_name),"\\s+(-?[0-9]*\\.?[0-9]+)"].join(''))):null);
var match__$1 = (function (){var or__5002__auto__ = match;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
if(cljs.core.truth_(re_pos)){
return re_pos.exec(doc);
} else {
return null;
}
}
})();
if(cljs.core.truth_(match__$1)){
var full = (match__$1[(0)]);
var num = (match__$1[(1)]);
var start = (match__$1.index + (full.length - num.length));
var end = (start + num.length);
return view.dispatch(({"changes": ({"from": start, "to": end, "insert": fmtd})}));
} else {
var re_fx = (new RegExp(["\\(fx\\s+:",cljs.core.str.cljs$core$IFn$_invoke$arity$1(effect_name),"[^)]*\\)"].join('')));
var fx_match = re_fx.exec(doc);
if(cljs.core.truth_(fx_match)){
var close_pos = (fx_match.index + ((fx_match[(0)]).length - (1)));
return view.dispatch(({"changes": ({"from": close_pos, "to": close_pos, "insert": [" :",cljs.core.str.cljs$core$IFn$_invoke$arity$1(param_name)," ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(fmtd)].join('')})}));
} else {
return null;
}
}
} else {
return null;
}
});
repulse.eval_orchestrator.fx_slider_patch_and_eval_BANG_ = (function repulse$eval_orchestrator$fx_slider_patch_and_eval_BANG_(effect_name,param_name,new_val){
repulse.fx.set_param_BANG_(effect_name,param_name,new_val);

repulse.eval_orchestrator.patch_fx_param_in_editor_BANG_(effect_name,param_name,new_val);

if(cljs.core.truth_(cljs.core.deref(repulse.eval_orchestrator.slider_timeout))){
clearTimeout(cljs.core.deref(repulse.eval_orchestrator.slider_timeout));
} else {
}

return cljs.core.reset_BANG_(repulse.eval_orchestrator.slider_timeout,setTimeout((function (){
cljs.core.reset_BANG_(repulse.eval_orchestrator.slider_timeout,null);

var temp__5804__auto__ = cljs.core.deref(repulse.ui.editor.editor_view);
if(cljs.core.truth_(temp__5804__auto__)){
var view = temp__5804__auto__;
return repulse.eval_orchestrator.evaluate_BANG_(view.state.doc.toString());
} else {
return null;
}
}),(150)));
});
/**
 * Update or insert a param in (fx :effect-name ...) scoped to (track :track-name ...).
 */
repulse.eval_orchestrator.patch_per_track_fx_param_in_editor_BANG_ = (function repulse$eval_orchestrator$patch_per_track_fx_param_in_editor_BANG_(track_name,effect_name,param_name,new_val){
var temp__5804__auto__ = cljs.core.deref(repulse.ui.editor.editor_view);
if(cljs.core.truth_(temp__5804__auto__)){
var view = temp__5804__auto__;
var doc = view.state.doc.toString();
var track_kw = ["(track :",cljs.core.str.cljs$core$IFn$_invoke$arity$1(track_name)].join('');
var track_start = doc.indexOf(track_kw);
var next_start = (function (){var p = doc.indexOf("(track :",(track_start + (1)));
if((p >= (0))){
return p;
} else {
return doc.length;
}
})();
var sub_doc = (((track_start >= (0)))?doc.substring(track_start,next_start):null);
var primary_QMARK_ = cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(param_name,cljs.core.get.cljs$core$IFn$_invoke$arity$2(repulse.ui.context_panel.FX_PRIMARY_PARAM,effect_name));
var fmtd = (((new_val === Math.round(new_val)))?cljs.core.str.cljs$core$IFn$_invoke$arity$1((new_val | (0))):new_val.toFixed((2)));
if(cljs.core.truth_(sub_doc)){
var re_named = (new RegExp(["\\(fx\\s+:",cljs.core.str.cljs$core$IFn$_invoke$arity$1(effect_name),"[^)]*:",cljs.core.str.cljs$core$IFn$_invoke$arity$1(param_name),"\\s+(-?[0-9]*\\.?[0-9]+)"].join('')));
var match = re_named.exec(sub_doc);
var re_pos = ((((cljs.core.not(match)) && (primary_QMARK_)))?(new RegExp(["\\(fx\\s+:",cljs.core.str.cljs$core$IFn$_invoke$arity$1(effect_name),"\\s+(-?[0-9]*\\.?[0-9]+)"].join(''))):null);
var match__$1 = (function (){var or__5002__auto__ = match;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
if(cljs.core.truth_(re_pos)){
return re_pos.exec(sub_doc);
} else {
return null;
}
}
})();
if(cljs.core.truth_(match__$1)){
var full = (match__$1[(0)]);
var num = (match__$1[(1)]);
var start = ((track_start + match__$1.index) + (full.length - num.length));
var end = (start + num.length);
return view.dispatch(({"changes": ({"from": start, "to": end, "insert": fmtd})}));
} else {
var re_fx = (new RegExp(["\\(fx\\s+:",cljs.core.str.cljs$core$IFn$_invoke$arity$1(effect_name),"[^)]*\\)"].join('')));
var fx_match = re_fx.exec(sub_doc);
if(cljs.core.truth_(fx_match)){
var close_pos = ((track_start + fx_match.index) + ((fx_match[(0)]).length - (1)));
return view.dispatch(({"changes": ({"from": close_pos, "to": close_pos, "insert": [" :",cljs.core.str.cljs$core$IFn$_invoke$arity$1(param_name)," ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(fmtd)].join('')})}));
} else {
return null;
}
}
} else {
return null;
}
} else {
return null;
}
});
repulse.eval_orchestrator.per_track_fx_slider_patch_and_eval_BANG_ = (function repulse$eval_orchestrator$per_track_fx_slider_patch_and_eval_BANG_(track_name,effect_name,param_name,new_val){
repulse.fx.set_track_param_BANG_(cljs.core.keyword.cljs$core$IFn$_invoke$arity$1(track_name),effect_name,param_name,new_val);

repulse.eval_orchestrator.patch_per_track_fx_param_in_editor_BANG_(track_name,effect_name,param_name,new_val);

if(cljs.core.truth_(cljs.core.deref(repulse.eval_orchestrator.slider_timeout))){
clearTimeout(cljs.core.deref(repulse.eval_orchestrator.slider_timeout));
} else {
}

return cljs.core.reset_BANG_(repulse.eval_orchestrator.slider_timeout,setTimeout((function (){
cljs.core.reset_BANG_(repulse.eval_orchestrator.slider_timeout,null);

var temp__5804__auto__ = cljs.core.deref(repulse.ui.editor.editor_view);
if(cljs.core.truth_(temp__5804__auto__)){
var view = temp__5804__auto__;
return repulse.eval_orchestrator.evaluate_BANG_(view.state.doc.toString());
} else {
return null;
}
}),(150)));
});

//# sourceMappingURL=repulse.eval_orchestrator.js.map
