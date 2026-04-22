goog.provide('repulse.env.builtins');
var module$node_modules$$codemirror$lint$dist$index_cjs=shadow.js.require("module$node_modules$$codemirror$lint$dist$index_cjs", {});
if((typeof repulse !== 'undefined') && (typeof repulse.env !== 'undefined') && (typeof repulse.env.builtins !== 'undefined') && (typeof repulse.env.builtins.env_atom !== 'undefined')){
} else {
repulse.env.builtins.env_atom = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
if((typeof repulse !== 'undefined') && (typeof repulse.env !== 'undefined') && (typeof repulse.env.builtins !== 'undefined') && (typeof repulse.env.builtins.builtin_names !== 'undefined')){
} else {
repulse.env.builtins.builtin_names = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentHashSet.EMPTY);
}
if((typeof repulse !== 'undefined') && (typeof repulse.env !== 'undefined') && (typeof repulse.env.builtins !== 'undefined') && (typeof repulse.env.builtins.seen_tracks !== 'undefined')){
} else {
repulse.env.builtins.seen_tracks = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentHashSet.EMPTY);
}
if((typeof repulse !== 'undefined') && (typeof repulse.env !== 'undefined') && (typeof repulse.env.builtins !== 'undefined') && (typeof repulse.env.builtins.freesound_api_key !== 'undefined')){
} else {
repulse.env.builtins.freesound_api_key = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
if((typeof repulse !== 'undefined') && (typeof repulse.env !== 'undefined') && (typeof repulse.env.builtins !== 'undefined') && (typeof repulse.env.builtins.evaluate_ref !== 'undefined')){
} else {
repulse.env.builtins.evaluate_ref = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
if((typeof repulse !== 'undefined') && (typeof repulse.env !== 'undefined') && (typeof repulse.env.builtins !== 'undefined') && (typeof repulse.env.builtins.cbs !== 'undefined')){
} else {
repulse.env.builtins.cbs = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
}
/**
 * Wire app-level callback fns into this module.
 * Must be called before ensure-env!.
 * config — {:on-beat-fn f :set-playing!-fn f :set-output!-fn f :make-stop-fn-fn f :share!-fn f}
 */
repulse.env.builtins.init_BANG_ = (function repulse$env$builtins$init_BANG_(p__10095){
var map__10096 = p__10095;
var map__10096__$1 = cljs.core.__destructure_map(map__10096);
var on_beat_fn = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10096__$1,new cljs.core.Keyword(null,"on-beat-fn","on-beat-fn",567217350));
var set_playing_BANG__fn = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10096__$1,new cljs.core.Keyword(null,"set-playing!-fn","set-playing!-fn",1851430687));
var set_output_BANG__fn = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10096__$1,new cljs.core.Keyword(null,"set-output!-fn","set-output!-fn",1617998012));
var make_stop_fn_fn = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10096__$1,new cljs.core.Keyword(null,"make-stop-fn-fn","make-stop-fn-fn",1610235793));
var share_BANG__fn = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10096__$1,new cljs.core.Keyword(null,"share!-fn","share!-fn",-253867902));
return cljs.core.reset_BANG_(repulse.env.builtins.cbs,new cljs.core.PersistentArrayMap(null, 5, [new cljs.core.Keyword(null,"on-beat","on-beat",1078535864),on_beat_fn,new cljs.core.Keyword(null,"set-playing!","set-playing!",1429676448),set_playing_BANG__fn,new cljs.core.Keyword(null,"set-output!","set-output!",-419558317),set_output_BANG__fn,new cljs.core.Keyword(null,"make-stop-fn","make-stop-fn",-2002471956),make_stop_fn_fn,new cljs.core.Keyword(null,"share!","share!",2082678164),share_BANG__fn], null));
});
repulse.env.builtins.ensure_env_BANG_ = (function repulse$env$builtins$ensure_env_BANG_(){
if((cljs.core.deref(repulse.env.builtins.env_atom) == null)){
var map__10110 = cljs.core.deref(repulse.env.builtins.cbs);
var map__10110__$1 = cljs.core.__destructure_map(map__10110);
var on_beat = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10110__$1,new cljs.core.Keyword(null,"on-beat","on-beat",1078535864));
var set_playing_BANG_ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10110__$1,new cljs.core.Keyword(null,"set-playing!","set-playing!",1429676448));
var set_output_BANG_ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10110__$1,new cljs.core.Keyword(null,"set-output!","set-output!",-419558317));
var make_stop_fn = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10110__$1,new cljs.core.Keyword(null,"make-stop-fn","make-stop-fn",-2002471956));
repulse.samples.init_BANG_();

cljs.core.reset_BANG_(repulse.env.builtins.env_atom,cljs.core.assoc.cljs$core$IFn$_invoke$arity$variadic(repulse.lisp.eval.make_env((make_stop_fn.cljs$core$IFn$_invoke$arity$0 ? make_stop_fn.cljs$core$IFn$_invoke$arity$0() : make_stop_fn.call(null)),repulse.audio.set_bpm_BANG_),new cljs.core.Keyword(null,"*register-synth-fn*","*register-synth-fn*",-395091119),repulse.synth.register_synth_BANG_,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2(["track",(function (track_name,pat){
var name_SINGLEQUOTE_ = repulse.lisp.eval.unwrap(track_name);
var pat_SINGLEQUOTE_ = repulse.lisp.eval.unwrap(pat);
var src = new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(track_name);
if(cljs.core.contains_QMARK_(cljs.core.deref(repulse.env.builtins.seen_tracks),name_SINGLEQUOTE_)){
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2(["Duplicate track name :",cljs.core.name(name_SINGLEQUOTE_)," \u2014 each track must have a unique name in the buffer"].join(''),(function (){var G__10111 = new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"eval-error","eval-error",466139568)], null);
if(cljs.core.truth_(src)){
return cljs.core.merge.cljs$core$IFn$_invoke$arity$variadic(cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([G__10111,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"from","from",1815293044),new cljs.core.Keyword(null,"from","from",1815293044).cljs$core$IFn$_invoke$arity$1(src),new cljs.core.Keyword(null,"to","to",192099007),new cljs.core.Keyword(null,"to","to",192099007).cljs$core$IFn$_invoke$arity$1(src)], null)], 0));
} else {
return G__10111;
}
})());
} else {
}

cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(repulse.env.builtins.seen_tracks,cljs.core.conj,name_SINGLEQUOTE_);

if(repulse.core.pattern_QMARK_(pat_SINGLEQUOTE_)){
repulse.audio.play_track_BANG_(name_SINGLEQUOTE_,pat_SINGLEQUOTE_,on_beat,repulse.ui.editor.highlight_range_BANG_);

repulse.fx.clear_track_effects_BANG_(name_SINGLEQUOTE_);

var seq__10112_10604 = cljs.core.seq(new cljs.core.Keyword(null,"track-fx","track-fx",2100938498).cljs$core$IFn$_invoke$arity$1(pat_SINGLEQUOTE_));
var chunk__10113_10605 = null;
var count__10114_10606 = (0);
var i__10115_10607 = (0);
while(true){
if((i__10115_10607 < count__10114_10606)){
var map__10154_10609 = chunk__10113_10605.cljs$core$IIndexed$_nth$arity$2(null,i__10115_10607);
var map__10154_10610__$1 = cljs.core.__destructure_map(map__10154_10609);
var name_10611 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10154_10610__$1,new cljs.core.Keyword(null,"name","name",1843675177));
var params_10612 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10154_10610__$1,new cljs.core.Keyword(null,"params","params",710516235));
repulse.fx.add_track_effect_BANG_(name_SINGLEQUOTE_,name_10611);

var seq__10155_10614 = cljs.core.seq(params_10612);
var chunk__10156_10615 = null;
var count__10157_10616 = (0);
var i__10158_10617 = (0);
while(true){
if((i__10158_10617 < count__10157_10616)){
var vec__10166_10618 = chunk__10156_10615.cljs$core$IIndexed$_nth$arity$2(null,i__10158_10617);
var k_10619 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10166_10618,(0),null);
var v_10620 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10166_10618,(1),null);
repulse.fx.set_track_param_BANG_(name_SINGLEQUOTE_,name_10611,k_10619,v_10620);


var G__10621 = seq__10155_10614;
var G__10622 = chunk__10156_10615;
var G__10623 = count__10157_10616;
var G__10624 = (i__10158_10617 + (1));
seq__10155_10614 = G__10621;
chunk__10156_10615 = G__10622;
count__10157_10616 = G__10623;
i__10158_10617 = G__10624;
continue;
} else {
var temp__5804__auto___10625 = cljs.core.seq(seq__10155_10614);
if(temp__5804__auto___10625){
var seq__10155_10626__$1 = temp__5804__auto___10625;
if(cljs.core.chunked_seq_QMARK_(seq__10155_10626__$1)){
var c__5525__auto___10627 = cljs.core.chunk_first(seq__10155_10626__$1);
var G__10628 = cljs.core.chunk_rest(seq__10155_10626__$1);
var G__10629 = c__5525__auto___10627;
var G__10630 = cljs.core.count(c__5525__auto___10627);
var G__10631 = (0);
seq__10155_10614 = G__10628;
chunk__10156_10615 = G__10629;
count__10157_10616 = G__10630;
i__10158_10617 = G__10631;
continue;
} else {
var vec__10169_10632 = cljs.core.first(seq__10155_10626__$1);
var k_10633 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10169_10632,(0),null);
var v_10634 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10169_10632,(1),null);
repulse.fx.set_track_param_BANG_(name_SINGLEQUOTE_,name_10611,k_10633,v_10634);


var G__10635 = cljs.core.next(seq__10155_10626__$1);
var G__10636 = null;
var G__10637 = (0);
var G__10638 = (0);
seq__10155_10614 = G__10635;
chunk__10156_10615 = G__10636;
count__10157_10616 = G__10637;
i__10158_10617 = G__10638;
continue;
}
} else {
}
}
break;
}


var G__10639 = seq__10112_10604;
var G__10640 = chunk__10113_10605;
var G__10641 = count__10114_10606;
var G__10642 = (i__10115_10607 + (1));
seq__10112_10604 = G__10639;
chunk__10113_10605 = G__10640;
count__10114_10606 = G__10641;
i__10115_10607 = G__10642;
continue;
} else {
var temp__5804__auto___10643 = cljs.core.seq(seq__10112_10604);
if(temp__5804__auto___10643){
var seq__10112_10644__$1 = temp__5804__auto___10643;
if(cljs.core.chunked_seq_QMARK_(seq__10112_10644__$1)){
var c__5525__auto___10645 = cljs.core.chunk_first(seq__10112_10644__$1);
var G__10646 = cljs.core.chunk_rest(seq__10112_10644__$1);
var G__10647 = c__5525__auto___10645;
var G__10648 = cljs.core.count(c__5525__auto___10645);
var G__10649 = (0);
seq__10112_10604 = G__10646;
chunk__10113_10605 = G__10647;
count__10114_10606 = G__10648;
i__10115_10607 = G__10649;
continue;
} else {
var map__10172_10650 = cljs.core.first(seq__10112_10644__$1);
var map__10172_10651__$1 = cljs.core.__destructure_map(map__10172_10650);
var name_10652 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10172_10651__$1,new cljs.core.Keyword(null,"name","name",1843675177));
var params_10653 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10172_10651__$1,new cljs.core.Keyword(null,"params","params",710516235));
repulse.fx.add_track_effect_BANG_(name_SINGLEQUOTE_,name_10652);

var seq__10173_10657 = cljs.core.seq(params_10653);
var chunk__10174_10658 = null;
var count__10175_10659 = (0);
var i__10176_10660 = (0);
while(true){
if((i__10176_10660 < count__10175_10659)){
var vec__10184_10662 = chunk__10174_10658.cljs$core$IIndexed$_nth$arity$2(null,i__10176_10660);
var k_10663 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10184_10662,(0),null);
var v_10664 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10184_10662,(1),null);
repulse.fx.set_track_param_BANG_(name_SINGLEQUOTE_,name_10652,k_10663,v_10664);


var G__10665 = seq__10173_10657;
var G__10666 = chunk__10174_10658;
var G__10667 = count__10175_10659;
var G__10668 = (i__10176_10660 + (1));
seq__10173_10657 = G__10665;
chunk__10174_10658 = G__10666;
count__10175_10659 = G__10667;
i__10176_10660 = G__10668;
continue;
} else {
var temp__5804__auto___10669__$1 = cljs.core.seq(seq__10173_10657);
if(temp__5804__auto___10669__$1){
var seq__10173_10670__$1 = temp__5804__auto___10669__$1;
if(cljs.core.chunked_seq_QMARK_(seq__10173_10670__$1)){
var c__5525__auto___10671 = cljs.core.chunk_first(seq__10173_10670__$1);
var G__10672 = cljs.core.chunk_rest(seq__10173_10670__$1);
var G__10673 = c__5525__auto___10671;
var G__10674 = cljs.core.count(c__5525__auto___10671);
var G__10675 = (0);
seq__10173_10657 = G__10672;
chunk__10174_10658 = G__10673;
count__10175_10659 = G__10674;
i__10176_10660 = G__10675;
continue;
} else {
var vec__10188_10676 = cljs.core.first(seq__10173_10670__$1);
var k_10677 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10188_10676,(0),null);
var v_10678 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10188_10676,(1),null);
repulse.fx.set_track_param_BANG_(name_SINGLEQUOTE_,name_10652,k_10677,v_10678);


var G__10680 = cljs.core.next(seq__10173_10670__$1);
var G__10681 = null;
var G__10682 = (0);
var G__10683 = (0);
seq__10173_10657 = G__10680;
chunk__10174_10658 = G__10681;
count__10175_10659 = G__10682;
i__10176_10660 = G__10683;
continue;
}
} else {
}
}
break;
}


var G__10684 = cljs.core.next(seq__10112_10644__$1);
var G__10685 = null;
var G__10686 = (0);
var G__10687 = (0);
seq__10112_10604 = G__10684;
chunk__10113_10605 = G__10685;
count__10114_10606 = G__10686;
i__10115_10607 = G__10687;
continue;
}
} else {
}
}
break;
}

(set_playing_BANG_.cljs$core$IFn$_invoke$arity$1 ? set_playing_BANG_.cljs$core$IFn$_invoke$arity$1(true) : set_playing_BANG_.call(null,true));

return ["=> track :",cljs.core.name(name_SINGLEQUOTE_)," playing"].join('');
} else {
return "Error: second argument to track must be a pattern";
}
}),"play",(function() { 
var G__10689__delegate = function (_args){
throw (new Error("play is renamed to track \u2014 use (track :name pattern)"));
};
var G__10689 = function (var_args){
var _args = null;
if (arguments.length > 0) {
var G__10690__i = 0, G__10690__a = new Array(arguments.length -  0);
while (G__10690__i < G__10690__a.length) {G__10690__a[G__10690__i] = arguments[G__10690__i + 0]; ++G__10690__i;}
  _args = new cljs.core.IndexedSeq(G__10690__a,0,null);
} 
return G__10689__delegate.call(this,_args);};
G__10689.cljs$lang$maxFixedArity = 0;
G__10689.cljs$lang$applyTo = (function (arglist__10691){
var _args = cljs.core.seq(arglist__10691);
return G__10689__delegate(_args);
});
G__10689.cljs$core$IFn$_invoke$arity$variadic = G__10689__delegate;
return G__10689;
})()
,"mute!",(function (track_name){
var name_SINGLEQUOTE_ = repulse.lisp.eval.unwrap(track_name);
repulse.audio.mute_track_BANG_(name_SINGLEQUOTE_);

return ["=> muted :",cljs.core.name(name_SINGLEQUOTE_)].join('');
}),"unmute!",(function (track_name){
var name_SINGLEQUOTE_ = repulse.lisp.eval.unwrap(track_name);
repulse.audio.unmute_track_BANG_(name_SINGLEQUOTE_);

return ["=> unmuted :",cljs.core.name(name_SINGLEQUOTE_)].join('');
}),"solo!",(function (track_name){
var name_SINGLEQUOTE_ = repulse.lisp.eval.unwrap(track_name);
repulse.audio.solo_track_BANG_(name_SINGLEQUOTE_);

return ["=> solo :",cljs.core.name(name_SINGLEQUOTE_)].join('');
}),"clear!",(function() {
var G__10694 = null;
var G__10694__0 = (function (){
repulse.audio.stop_BANG_();

repulse.ui.editor.clear_highlights_BANG_();

(set_playing_BANG_.cljs$core$IFn$_invoke$arity$1 ? set_playing_BANG_.cljs$core$IFn$_invoke$arity$1(false) : set_playing_BANG_.call(null,false));

return "=> cleared all tracks";
});
var G__10694__1 = (function (track_name){
var name_SINGLEQUOTE_ = repulse.lisp.eval.unwrap(track_name);
repulse.audio.clear_track_BANG_(name_SINGLEQUOTE_);

if(cljs.core.not(new cljs.core.Keyword(null,"playing?","playing?",-1884542863).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.audio.scheduler_state)))){
(set_playing_BANG_.cljs$core$IFn$_invoke$arity$1 ? set_playing_BANG_.cljs$core$IFn$_invoke$arity$1(false) : set_playing_BANG_.call(null,false));
} else {
}

return ["=> cleared :",cljs.core.name(name_SINGLEQUOTE_)].join('');
});
G__10694 = function(track_name){
switch(arguments.length){
case 0:
return G__10694__0.call(this);
case 1:
return G__10694__1.call(this,track_name);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__10694.cljs$core$IFn$_invoke$arity$0 = G__10694__0;
G__10694.cljs$core$IFn$_invoke$arity$1 = G__10694__1;
return G__10694;
})()
,"tracks",(function (){
var ks = cljs.core.keys(new cljs.core.Keyword(null,"tracks","tracks",-326768501).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.audio.scheduler_state)));
if(cljs.core.seq(ks)){
return ["=> (",clojure.string.join.cljs$core$IFn$_invoke$arity$2(" ",cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p1__10097_SHARP_){
return [":",cljs.core.name(p1__10097_SHARP_)].join('');
}),ks)),")"].join('');
} else {
return "=> ()";
}
}),"upd",(function (){
var temp__5804__auto__ = cljs.core.deref(repulse.ui.editor.editor_view);
if(cljs.core.truth_(temp__5804__auto__)){
var view = temp__5804__auto__;
var code_10696 = view.state.doc.toString();
var env_10697 = cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(cljs.core.deref(repulse.env.builtins.env_atom),"stop",(make_stop_fn.cljs$core$IFn$_invoke$arity$0 ? make_stop_fn.cljs$core$IFn$_invoke$arity$0() : make_stop_fn.call(null)));
var result_10698 = repulse.lisp.core.eval_string(code_10696,env_10697);
if(repulse.lisp.core.eval_error_QMARK_(result_10698)){
repulse.ui.editor.clear_highlights_BANG_();

var map__10199_10700 = new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(result_10698);
var map__10199_10701__$1 = cljs.core.__destructure_map(map__10199_10700);
var from_10702 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10199_10701__$1,new cljs.core.Keyword(null,"from","from",1815293044));
var to_10703 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10199_10701__$1,new cljs.core.Keyword(null,"to","to",192099007));
if(cljs.core.truth_((function (){var and__5000__auto__ = from_10702;
if(cljs.core.truth_(and__5000__auto__)){
var and__5000__auto____$1 = to_10703;
if(cljs.core.truth_(and__5000__auto____$1)){
return (from_10702 < to_10703);
} else {
return and__5000__auto____$1;
}
} else {
return and__5000__auto__;
}
})())){
view.dispatch(module$node_modules$$codemirror$lint$dist$index_cjs.setDiagnostics(view.state,[({"from": from_10702, "to": to_10703, "severity": "error", "message": new cljs.core.Keyword(null,"message","message",-406056002).cljs$core$IFn$_invoke$arity$1(result_10698)})]));
} else {
}

var G__10204_10704 = ["Error: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"message","message",-406056002).cljs$core$IFn$_invoke$arity$1(result_10698))].join('');
var G__10205_10705 = new cljs.core.Keyword(null,"error","error",-978969032);
(set_output_BANG_.cljs$core$IFn$_invoke$arity$2 ? set_output_BANG_.cljs$core$IFn$_invoke$arity$2(G__10204_10704,G__10205_10705) : set_output_BANG_.call(null,G__10204_10704,G__10205_10705));
} else {
var val_10706 = new cljs.core.Keyword(null,"result","result",1415092211).cljs$core$IFn$_invoke$arity$1(result_10698);
if(repulse.core.pattern_QMARK_(val_10706)){
repulse.audio.play_track_BANG_(new cljs.core.Keyword(null,"_","_",1453416199),val_10706,on_beat,repulse.ui.editor.highlight_range_BANG_);

(set_playing_BANG_.cljs$core$IFn$_invoke$arity$1 ? set_playing_BANG_.cljs$core$IFn$_invoke$arity$1(true) : set_playing_BANG_.call(null,true));

(set_output_BANG_.cljs$core$IFn$_invoke$arity$2 ? set_output_BANG_.cljs$core$IFn$_invoke$arity$2("updated",new cljs.core.Keyword(null,"success","success",1890645906)) : set_output_BANG_.call(null,"updated",new cljs.core.Keyword(null,"success","success",1890645906)));
} else {
if((val_10706 == null)){
} else {
if(typeof val_10706 === 'string'){
(set_output_BANG_.cljs$core$IFn$_invoke$arity$2 ? set_output_BANG_.cljs$core$IFn$_invoke$arity$2(val_10706,new cljs.core.Keyword(null,"success","success",1890645906)) : set_output_BANG_.call(null,val_10706,new cljs.core.Keyword(null,"success","success",1890645906)));
} else {
var G__10207_10708 = ["=> ",cljs.core.pr_str.cljs$core$IFn$_invoke$arity$variadic(cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([val_10706], 0))].join('');
var G__10208_10709 = new cljs.core.Keyword(null,"success","success",1890645906);
(set_output_BANG_.cljs$core$IFn$_invoke$arity$2 ? set_output_BANG_.cljs$core$IFn$_invoke$arity$2(G__10207_10708,G__10208_10709) : set_output_BANG_.call(null,G__10207_10708,G__10208_10709));

}
}
}
}

return null;
} else {
return null;
}
}),"tap!",(function (){
var temp__5802__auto__ = repulse.audio.tap_BANG_();
if(cljs.core.truth_(temp__5802__auto__)){
var bpm = temp__5802__auto__;
return ["=> ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(bpm.toFixed((1)))," BPM"].join('');
} else {
return "=> tap again\u2026";
}
}),"midi-sync!",(function (enabled_QMARK_){
var on_QMARK_ = repulse.lisp.eval.unwrap(enabled_QMARK_);
repulse.audio.set_midi_sync_BANG_(on_QMARK_);

return ["=> MIDI sync ",(cljs.core.truth_(on_QMARK_)?"enabled":"disabled")].join('');
}),"samples!",(function (url){
var url_SINGLEQUOTE_ = repulse.lisp.eval.unwrap(url);
repulse.samples.load_external_BANG_(url_SINGLEQUOTE_);

return ["loading ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(url_SINGLEQUOTE_),"\u2026"].join('');
}),"sample-banks",(function (){
return repulse.samples.format_banks();
}),"load-plugin",repulse.plugin_loading.load_plugin_builtin(),"unload-plugin",repulse.plugin_loading.unload_plugin_builtin(),"bank",(function (prefix){
repulse.samples.set_bank_prefix_BANG_(repulse.lisp.eval.unwrap(prefix));

return ["bank: ",(cljs.core.truth_(prefix)?cljs.core.name(repulse.lisp.eval.unwrap(prefix)):"cleared")].join('');
}),"bus",(function() { 
var G__10712__delegate = function (args){
var args_SINGLEQUOTE_ = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,args);
var bus_name = cljs.core.first(args_SINGLEQUOTE_);
var bus_type = (function (){var or__5002__auto__ = cljs.core.second(args_SINGLEQUOTE_);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return new cljs.core.Keyword(null,"control","control",1892578036);
}
})();
if((bus_name instanceof cljs.core.Keyword)){
} else {
throw (new Error("bus: first argument must be a keyword, e.g. (bus :lfo :control)"));
}

if(cljs.core.truth_((function (){var fexpr__10217 = new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"control","control",1892578036),null,new cljs.core.Keyword(null,"audio","audio",1819127321),null], null), null);
return (fexpr__10217.cljs$core$IFn$_invoke$arity$1 ? fexpr__10217.cljs$core$IFn$_invoke$arity$1(bus_type) : fexpr__10217.call(null,bus_type));
})())){
} else {
throw (new Error(["bus: type must be :control or :audio, got ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(bus_type)].join('')));
}

repulse.bus.create_bus_BANG_(repulse.audio.get_ctx(),bus_name,bus_type);

return ["=> bus ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(bus_name)," (",cljs.core.name(bus_type),")"].join('');
};
var G__10712 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__10717__i = 0, G__10717__a = new Array(arguments.length -  0);
while (G__10717__i < G__10717__a.length) {G__10717__a[G__10717__i] = arguments[G__10717__i + 0]; ++G__10717__i;}
  args = new cljs.core.IndexedSeq(G__10717__a,0,null);
} 
return G__10712__delegate.call(this,args);};
G__10712.cljs$lang$maxFixedArity = 0;
G__10712.cljs$lang$applyTo = (function (arglist__10718){
var args = cljs.core.seq(arglist__10718);
return G__10712__delegate(args);
});
G__10712.cljs$core$IFn$_invoke$arity$variadic = G__10712__delegate;
return G__10712;
})()
,"fx",(function() { 
var G__10719__delegate = function (raw_args){
var args_SINGLEQUOTE_ = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,raw_args);
var last_arg = cljs.core.last(args_SINGLEQUOTE_);
var per_track_QMARK_ = (((cljs.core.count(args_SINGLEQUOTE_) > (1))) && (repulse.core.pattern_QMARK_(last_arg)));
if(per_track_QMARK_){
var fx_args = cljs.core.butlast(args_SINGLEQUOTE_);
var pat = last_arg;
var effect_name = cljs.core.name(cljs.core.first(fx_args));
var rest_fx = cljs.core.rest(fx_args);
var params = (((cljs.core.first(rest_fx) instanceof cljs.core.Keyword))?cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.PersistentArrayMap.EMPTY,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p__10223){
var vec__10224 = p__10223;
var k = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10224,(0),null);
var v = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10224,(1),null);
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.name(k),v], null);
}),cljs.core.partition.cljs$core$IFn$_invoke$arity$2((2),rest_fx))):(function (){var named = cljs.core.rest(rest_fx);
return cljs.core.into.cljs$core$IFn$_invoke$arity$2(((cljs.core.seq(rest_fx))?new cljs.core.PersistentArrayMap(null, 1, ["value",cljs.core.first(rest_fx)], null):null),(((cljs.core.first(named) instanceof cljs.core.Keyword))?cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p__10228){
var vec__10229 = p__10228;
var k = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10229,(0),null);
var v = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10229,(1),null);
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.name(k),v], null);
}),cljs.core.partition.cljs$core$IFn$_invoke$arity$2((2),named)):null));
})());
return cljs.core.update.cljs$core$IFn$_invoke$arity$4(pat,new cljs.core.Keyword(null,"track-fx","track-fx",2100938498),cljs.core.fnil.cljs$core$IFn$_invoke$arity$2(cljs.core.conj,cljs.core.PersistentVector.EMPTY),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"name","name",1843675177),effect_name,new cljs.core.Keyword(null,"params","params",710516235),(function (){var or__5002__auto__ = params;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return cljs.core.PersistentArrayMap.EMPTY;
}
})()], null));
} else {
var first_arg_10721 = cljs.core.first(args_SINGLEQUOTE_);
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(first_arg_10721,new cljs.core.Keyword(null,"off","off",606440789))){
repulse.fx.bypass_BANG_(cljs.core.name(cljs.core.second(args_SINGLEQUOTE_)),true);
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(first_arg_10721,new cljs.core.Keyword(null,"on","on",173873944))){
repulse.fx.bypass_BANG_(cljs.core.name(cljs.core.second(args_SINGLEQUOTE_)),false);
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(first_arg_10721,new cljs.core.Keyword(null,"remove","remove",-131428414))){
repulse.fx.remove_effect_BANG_(cljs.core.name(cljs.core.second(args_SINGLEQUOTE_)));
} else {
var effect_name_10722 = cljs.core.name(first_arg_10721);
var rest_args_10723 = cljs.core.rest(args_SINGLEQUOTE_);
if((cljs.core.first(rest_args_10723) instanceof cljs.core.Keyword)){
var seq__10235_10724 = cljs.core.seq(cljs.core.partition.cljs$core$IFn$_invoke$arity$2((2),rest_args_10723));
var chunk__10236_10725 = null;
var count__10237_10726 = (0);
var i__10238_10727 = (0);
while(true){
if((i__10238_10727 < count__10237_10726)){
var vec__10253_10728 = chunk__10236_10725.cljs$core$IIndexed$_nth$arity$2(null,i__10238_10727);
var k_10730 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10253_10728,(0),null);
var v_10731 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10253_10728,(1),null);
repulse.fx.set_param_BANG_(effect_name_10722,cljs.core.name(k_10730),v_10731);


var G__10732 = seq__10235_10724;
var G__10733 = chunk__10236_10725;
var G__10734 = count__10237_10726;
var G__10735 = (i__10238_10727 + (1));
seq__10235_10724 = G__10732;
chunk__10236_10725 = G__10733;
count__10237_10726 = G__10734;
i__10238_10727 = G__10735;
continue;
} else {
var temp__5804__auto___10736 = cljs.core.seq(seq__10235_10724);
if(temp__5804__auto___10736){
var seq__10235_10737__$1 = temp__5804__auto___10736;
if(cljs.core.chunked_seq_QMARK_(seq__10235_10737__$1)){
var c__5525__auto___10738 = cljs.core.chunk_first(seq__10235_10737__$1);
var G__10739 = cljs.core.chunk_rest(seq__10235_10737__$1);
var G__10740 = c__5525__auto___10738;
var G__10741 = cljs.core.count(c__5525__auto___10738);
var G__10742 = (0);
seq__10235_10724 = G__10739;
chunk__10236_10725 = G__10740;
count__10237_10726 = G__10741;
i__10238_10727 = G__10742;
continue;
} else {
var vec__10259_10743 = cljs.core.first(seq__10235_10737__$1);
var k_10744 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10259_10743,(0),null);
var v_10745 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10259_10743,(1),null);
repulse.fx.set_param_BANG_(effect_name_10722,cljs.core.name(k_10744),v_10745);


var G__10746 = cljs.core.next(seq__10235_10737__$1);
var G__10747 = null;
var G__10748 = (0);
var G__10749 = (0);
seq__10235_10724 = G__10746;
chunk__10236_10725 = G__10747;
count__10237_10726 = G__10748;
i__10238_10727 = G__10749;
continue;
}
} else {
}
}
break;
}
} else {
repulse.fx.set_param_BANG_(effect_name_10722,"value",cljs.core.first(rest_args_10723));
}

}
}
}

return null;
}
};
var G__10719 = function (var_args){
var raw_args = null;
if (arguments.length > 0) {
var G__10751__i = 0, G__10751__a = new Array(arguments.length -  0);
while (G__10751__i < G__10751__a.length) {G__10751__a[G__10751__i] = arguments[G__10751__i + 0]; ++G__10751__i;}
  raw_args = new cljs.core.IndexedSeq(G__10751__a,0,null);
} 
return G__10719__delegate.call(this,raw_args);};
G__10719.cljs$lang$maxFixedArity = 0;
G__10719.cljs$lang$applyTo = (function (arglist__10752){
var raw_args = cljs.core.seq(arglist__10752);
return G__10719__delegate(raw_args);
});
G__10719.cljs$core$IFn$_invoke$arity$variadic = G__10719__delegate;
return G__10719;
})()
,"share!",(function (){
var temp__5804__auto___10753 = new cljs.core.Keyword(null,"share!","share!",2082678164).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.env.builtins.cbs));
if(cljs.core.truth_(temp__5804__auto___10753)){
var f_10754 = temp__5804__auto___10753;
(f_10754.cljs$core$IFn$_invoke$arity$0 ? f_10754.cljs$core$IFn$_invoke$arity$0() : f_10754.call(null));
} else {
}

return null;
}),"snippet",repulse.snippets.snippet_builtin(repulse.ui.editor.editor_view,repulse.env.builtins.evaluate_ref),"demo",repulse.content.demos.demo_builtin(repulse.ui.editor.editor_view,repulse.env.builtins.evaluate_ref),"tutorial",repulse.content.tutorial.tutorial_builtin(repulse.ui.editor.editor_view),"load-gist",(function (url){
var url_SINGLEQUOTE_ = repulse.lisp.eval.unwrap(url);
var raw_url = (cljs.core.truth_(cljs.core.re_find(/gist\.githubusercontent\.com/,url_SINGLEQUOTE_))?url_SINGLEQUOTE_:(function (){var vec__10265 = cljs.core.re_find(/\/([a-f0-9]+)\/?$/,url_SINGLEQUOTE_);
var _ = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10265,(0),null);
var gist_id = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10265,(1),null);
return ["https://api.github.com/gists/",cljs.core.str.cljs$core$IFn$_invoke$arity$1(gist_id)].join('');
})());
if(cljs.core.truth_(cljs.core.re_find(/api\.github\.com/,raw_url))){
fetch(raw_url).then((function (p1__10102_SHARP_){
return p1__10102_SHARP_.json();
})).then((function (data){
var files = cljs.core.js__GT_clj.cljs$core$IFn$_invoke$arity$1(data.files);
var first_file = cljs.core.second(cljs.core.first(files));
var content = cljs.core.get.cljs$core$IFn$_invoke$arity$2(first_file,"content");
var temp__5804__auto__ = cljs.core.deref(repulse.ui.editor.editor_view);
if(cljs.core.truth_(temp__5804__auto__)){
var view = temp__5804__auto__;
view.dispatch(({"changes": ({"from": (0), "to": view.state.doc.length, "insert": content})}));

var temp__5804__auto____$1 = cljs.core.deref(repulse.env.builtins.evaluate_ref);
if(cljs.core.truth_(temp__5804__auto____$1)){
var f = temp__5804__auto____$1;
return (f.cljs$core$IFn$_invoke$arity$1 ? f.cljs$core$IFn$_invoke$arity$1(content) : f.call(null,content));
} else {
return null;
}
} else {
return null;
}
})).catch((function (e){
var G__10273 = ["Gist load failed: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(e)].join('');
var G__10274 = new cljs.core.Keyword(null,"error","error",-978969032);
return (set_output_BANG_.cljs$core$IFn$_invoke$arity$2 ? set_output_BANG_.cljs$core$IFn$_invoke$arity$2(G__10273,G__10274) : set_output_BANG_.call(null,G__10273,G__10274));
}));
} else {
fetch(raw_url).then((function (p1__10107_SHARP_){
return p1__10107_SHARP_.text();
})).then((function (text){
var temp__5804__auto__ = cljs.core.deref(repulse.ui.editor.editor_view);
if(cljs.core.truth_(temp__5804__auto__)){
var view = temp__5804__auto__;
view.dispatch(({"changes": ({"from": (0), "to": view.state.doc.length, "insert": text})}));

var temp__5804__auto____$1 = cljs.core.deref(repulse.env.builtins.evaluate_ref);
if(cljs.core.truth_(temp__5804__auto____$1)){
var f = temp__5804__auto____$1;
return (f.cljs$core$IFn$_invoke$arity$1 ? f.cljs$core$IFn$_invoke$arity$1(text) : f.call(null,text));
} else {
return null;
}
} else {
return null;
}
})).catch((function (e){
var G__10280 = ["Gist load failed: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(e)].join('');
var G__10281 = new cljs.core.Keyword(null,"error","error",-978969032);
return (set_output_BANG_.cljs$core$IFn$_invoke$arity$2 ? set_output_BANG_.cljs$core$IFn$_invoke$arity$2(G__10280,G__10281) : set_output_BANG_.call(null,G__10280,G__10281));
}));
}

return "loading gist\u2026";
}),"export",(function() { 
var G__10764__delegate = function (args){
var arg = ((cljs.core.seq(args))?repulse.lisp.eval.unwrap(cljs.core.first(args)):null);
var n_cycles = ((typeof arg === 'number')?(arg | (0)):(4));
var track_kw = (((arg instanceof cljs.core.Keyword))?arg:null);
var state = cljs.core.deref(repulse.audio.scheduler_state);
var all_tracks = new cljs.core.Keyword(null,"tracks","tracks",-326768501).cljs$core$IFn$_invoke$arity$1(state);
var tracks = (((track_kw == null))?all_tracks:((cljs.core.contains_QMARK_(all_tracks,track_kw))?cljs.core.PersistentArrayMap.createAsIfByAssoc([track_kw,cljs.core.get.cljs$core$IFn$_invoke$arity$2(all_tracks,track_kw)]):cljs.core.PersistentArrayMap.EMPTY
));
var cycle_dur = new cljs.core.Keyword(null,"cycle-dur","cycle-dur",1241813230).cljs$core$IFn$_invoke$arity$1(state);
var duration = (n_cycles * cycle_dur);
var sr = (44100);
var n_frames = ((sr * duration) | (0));
var offline = (new OfflineAudioContext((2),n_frames,sr));
if(cljs.core.empty_QMARK_(tracks)){
if(cljs.core.truth_(track_kw)){
return ["Error: no track :",cljs.core.name(track_kw)].join('');
} else {
return "Error: no active tracks to export";
}
} else {
var seq__10287_10770 = cljs.core.seq(cljs.core.range.cljs$core$IFn$_invoke$arity$1(n_cycles));
var chunk__10288_10771 = null;
var count__10289_10772 = (0);
var i__10290_10773 = (0);
while(true){
if((i__10290_10773 < count__10289_10772)){
var c_10774 = chunk__10288_10771.cljs$core$IIndexed$_nth$arity$2(null,i__10290_10773);
var sp_10776 = new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"start","start",-355208981),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(c_10774 | (0)),(1)], null),new cljs.core.Keyword(null,"end","end",-268185958),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [((c_10774 + (1)) | (0)),(1)], null)], null);
var seq__10413_10781 = cljs.core.seq(tracks);
var chunk__10414_10782 = null;
var count__10415_10783 = (0);
var i__10416_10784 = (0);
while(true){
if((i__10416_10784 < count__10415_10783)){
var vec__10450_10785 = chunk__10414_10782.cljs$core$IIndexed$_nth$arity$2(null,i__10416_10784);
var _track_name_10786 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10450_10785,(0),null);
var pattern_10787 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10450_10785,(1),null);
if(cljs.core.truth_(pattern_10787)){
var evs_10788 = repulse.core.query(pattern_10787,sp_10776);
var seq__10453_10789 = cljs.core.seq(evs_10788);
var chunk__10454_10790 = null;
var count__10455_10791 = (0);
var i__10456_10792 = (0);
while(true){
if((i__10456_10792 < count__10455_10791)){
var ev_10793 = chunk__10454_10790.cljs$core$IIndexed$_nth$arity$2(null,i__10456_10792);
var part_start_10794 = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev_10793)));
var t_10795 = ((part_start_10794 - c_10774) * cycle_dur);
var abs_t_10796 = ((c_10774 * cycle_dur) + t_10795);
if((abs_t_10796 >= (0))){
repulse.audio.play_event.cljs$core$IFn$_invoke$arity$3(offline,abs_t_10796,new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_10793));
} else {
}


var G__10799 = seq__10453_10789;
var G__10800 = chunk__10454_10790;
var G__10801 = count__10455_10791;
var G__10802 = (i__10456_10792 + (1));
seq__10453_10789 = G__10799;
chunk__10454_10790 = G__10800;
count__10455_10791 = G__10801;
i__10456_10792 = G__10802;
continue;
} else {
var temp__5804__auto___10804 = cljs.core.seq(seq__10453_10789);
if(temp__5804__auto___10804){
var seq__10453_10805__$1 = temp__5804__auto___10804;
if(cljs.core.chunked_seq_QMARK_(seq__10453_10805__$1)){
var c__5525__auto___10806 = cljs.core.chunk_first(seq__10453_10805__$1);
var G__10807 = cljs.core.chunk_rest(seq__10453_10805__$1);
var G__10808 = c__5525__auto___10806;
var G__10809 = cljs.core.count(c__5525__auto___10806);
var G__10810 = (0);
seq__10453_10789 = G__10807;
chunk__10454_10790 = G__10808;
count__10455_10791 = G__10809;
i__10456_10792 = G__10810;
continue;
} else {
var ev_10812 = cljs.core.first(seq__10453_10805__$1);
var part_start_10816 = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev_10812)));
var t_10817 = ((part_start_10816 - c_10774) * cycle_dur);
var abs_t_10818 = ((c_10774 * cycle_dur) + t_10817);
if((abs_t_10818 >= (0))){
repulse.audio.play_event.cljs$core$IFn$_invoke$arity$3(offline,abs_t_10818,new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_10812));
} else {
}


var G__10819 = cljs.core.next(seq__10453_10805__$1);
var G__10820 = null;
var G__10821 = (0);
var G__10822 = (0);
seq__10453_10789 = G__10819;
chunk__10454_10790 = G__10820;
count__10455_10791 = G__10821;
i__10456_10792 = G__10822;
continue;
}
} else {
}
}
break;
}
} else {
}


var G__10823 = seq__10413_10781;
var G__10824 = chunk__10414_10782;
var G__10825 = count__10415_10783;
var G__10826 = (i__10416_10784 + (1));
seq__10413_10781 = G__10823;
chunk__10414_10782 = G__10824;
count__10415_10783 = G__10825;
i__10416_10784 = G__10826;
continue;
} else {
var temp__5804__auto___10827 = cljs.core.seq(seq__10413_10781);
if(temp__5804__auto___10827){
var seq__10413_10828__$1 = temp__5804__auto___10827;
if(cljs.core.chunked_seq_QMARK_(seq__10413_10828__$1)){
var c__5525__auto___10829 = cljs.core.chunk_first(seq__10413_10828__$1);
var G__10830 = cljs.core.chunk_rest(seq__10413_10828__$1);
var G__10831 = c__5525__auto___10829;
var G__10832 = cljs.core.count(c__5525__auto___10829);
var G__10833 = (0);
seq__10413_10781 = G__10830;
chunk__10414_10782 = G__10831;
count__10415_10783 = G__10832;
i__10416_10784 = G__10833;
continue;
} else {
var vec__10463_10834 = cljs.core.first(seq__10413_10828__$1);
var _track_name_10835 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10463_10834,(0),null);
var pattern_10836 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10463_10834,(1),null);
if(cljs.core.truth_(pattern_10836)){
var evs_10837 = repulse.core.query(pattern_10836,sp_10776);
var seq__10467_10838 = cljs.core.seq(evs_10837);
var chunk__10468_10839 = null;
var count__10469_10840 = (0);
var i__10470_10841 = (0);
while(true){
if((i__10470_10841 < count__10469_10840)){
var ev_10842 = chunk__10468_10839.cljs$core$IIndexed$_nth$arity$2(null,i__10470_10841);
var part_start_10843 = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev_10842)));
var t_10844 = ((part_start_10843 - c_10774) * cycle_dur);
var abs_t_10845 = ((c_10774 * cycle_dur) + t_10844);
if((abs_t_10845 >= (0))){
repulse.audio.play_event.cljs$core$IFn$_invoke$arity$3(offline,abs_t_10845,new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_10842));
} else {
}


var G__10846 = seq__10467_10838;
var G__10847 = chunk__10468_10839;
var G__10848 = count__10469_10840;
var G__10849 = (i__10470_10841 + (1));
seq__10467_10838 = G__10846;
chunk__10468_10839 = G__10847;
count__10469_10840 = G__10848;
i__10470_10841 = G__10849;
continue;
} else {
var temp__5804__auto___10851__$1 = cljs.core.seq(seq__10467_10838);
if(temp__5804__auto___10851__$1){
var seq__10467_10853__$1 = temp__5804__auto___10851__$1;
if(cljs.core.chunked_seq_QMARK_(seq__10467_10853__$1)){
var c__5525__auto___10854 = cljs.core.chunk_first(seq__10467_10853__$1);
var G__10855 = cljs.core.chunk_rest(seq__10467_10853__$1);
var G__10856 = c__5525__auto___10854;
var G__10857 = cljs.core.count(c__5525__auto___10854);
var G__10858 = (0);
seq__10467_10838 = G__10855;
chunk__10468_10839 = G__10856;
count__10469_10840 = G__10857;
i__10470_10841 = G__10858;
continue;
} else {
var ev_10861 = cljs.core.first(seq__10467_10853__$1);
var part_start_10862 = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev_10861)));
var t_10863 = ((part_start_10862 - c_10774) * cycle_dur);
var abs_t_10864 = ((c_10774 * cycle_dur) + t_10863);
if((abs_t_10864 >= (0))){
repulse.audio.play_event.cljs$core$IFn$_invoke$arity$3(offline,abs_t_10864,new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_10861));
} else {
}


var G__10869 = cljs.core.next(seq__10467_10853__$1);
var G__10870 = null;
var G__10871 = (0);
var G__10872 = (0);
seq__10467_10838 = G__10869;
chunk__10468_10839 = G__10870;
count__10469_10840 = G__10871;
i__10470_10841 = G__10872;
continue;
}
} else {
}
}
break;
}
} else {
}


var G__10873 = cljs.core.next(seq__10413_10828__$1);
var G__10874 = null;
var G__10875 = (0);
var G__10876 = (0);
seq__10413_10781 = G__10873;
chunk__10414_10782 = G__10874;
count__10415_10783 = G__10875;
i__10416_10784 = G__10876;
continue;
}
} else {
}
}
break;
}


var G__10877 = seq__10287_10770;
var G__10878 = chunk__10288_10771;
var G__10879 = count__10289_10772;
var G__10880 = (i__10290_10773 + (1));
seq__10287_10770 = G__10877;
chunk__10288_10771 = G__10878;
count__10289_10772 = G__10879;
i__10290_10773 = G__10880;
continue;
} else {
var temp__5804__auto___10881 = cljs.core.seq(seq__10287_10770);
if(temp__5804__auto___10881){
var seq__10287_10882__$1 = temp__5804__auto___10881;
if(cljs.core.chunked_seq_QMARK_(seq__10287_10882__$1)){
var c__5525__auto___10883 = cljs.core.chunk_first(seq__10287_10882__$1);
var G__10884 = cljs.core.chunk_rest(seq__10287_10882__$1);
var G__10885 = c__5525__auto___10883;
var G__10886 = cljs.core.count(c__5525__auto___10883);
var G__10887 = (0);
seq__10287_10770 = G__10884;
chunk__10288_10771 = G__10885;
count__10289_10772 = G__10886;
i__10290_10773 = G__10887;
continue;
} else {
var c_10888 = cljs.core.first(seq__10287_10882__$1);
var sp_10889 = new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"start","start",-355208981),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(c_10888 | (0)),(1)], null),new cljs.core.Keyword(null,"end","end",-268185958),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [((c_10888 + (1)) | (0)),(1)], null)], null);
var seq__10483_10891 = cljs.core.seq(tracks);
var chunk__10484_10892 = null;
var count__10485_10893 = (0);
var i__10486_10894 = (0);
while(true){
if((i__10486_10894 < count__10485_10893)){
var vec__10513_10898 = chunk__10484_10892.cljs$core$IIndexed$_nth$arity$2(null,i__10486_10894);
var _track_name_10899 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10513_10898,(0),null);
var pattern_10900 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10513_10898,(1),null);
if(cljs.core.truth_(pattern_10900)){
var evs_10901 = repulse.core.query(pattern_10900,sp_10889);
var seq__10517_10902 = cljs.core.seq(evs_10901);
var chunk__10518_10903 = null;
var count__10519_10904 = (0);
var i__10520_10905 = (0);
while(true){
if((i__10520_10905 < count__10519_10904)){
var ev_10906 = chunk__10518_10903.cljs$core$IIndexed$_nth$arity$2(null,i__10520_10905);
var part_start_10907 = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev_10906)));
var t_10908 = ((part_start_10907 - c_10888) * cycle_dur);
var abs_t_10909 = ((c_10888 * cycle_dur) + t_10908);
if((abs_t_10909 >= (0))){
repulse.audio.play_event.cljs$core$IFn$_invoke$arity$3(offline,abs_t_10909,new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_10906));
} else {
}


var G__10910 = seq__10517_10902;
var G__10911 = chunk__10518_10903;
var G__10912 = count__10519_10904;
var G__10913 = (i__10520_10905 + (1));
seq__10517_10902 = G__10910;
chunk__10518_10903 = G__10911;
count__10519_10904 = G__10912;
i__10520_10905 = G__10913;
continue;
} else {
var temp__5804__auto___10914__$1 = cljs.core.seq(seq__10517_10902);
if(temp__5804__auto___10914__$1){
var seq__10517_10915__$1 = temp__5804__auto___10914__$1;
if(cljs.core.chunked_seq_QMARK_(seq__10517_10915__$1)){
var c__5525__auto___10916 = cljs.core.chunk_first(seq__10517_10915__$1);
var G__10917 = cljs.core.chunk_rest(seq__10517_10915__$1);
var G__10918 = c__5525__auto___10916;
var G__10919 = cljs.core.count(c__5525__auto___10916);
var G__10920 = (0);
seq__10517_10902 = G__10917;
chunk__10518_10903 = G__10918;
count__10519_10904 = G__10919;
i__10520_10905 = G__10920;
continue;
} else {
var ev_10921 = cljs.core.first(seq__10517_10915__$1);
var part_start_10922 = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev_10921)));
var t_10923 = ((part_start_10922 - c_10888) * cycle_dur);
var abs_t_10924 = ((c_10888 * cycle_dur) + t_10923);
if((abs_t_10924 >= (0))){
repulse.audio.play_event.cljs$core$IFn$_invoke$arity$3(offline,abs_t_10924,new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_10921));
} else {
}


var G__10925 = cljs.core.next(seq__10517_10915__$1);
var G__10926 = null;
var G__10927 = (0);
var G__10928 = (0);
seq__10517_10902 = G__10925;
chunk__10518_10903 = G__10926;
count__10519_10904 = G__10927;
i__10520_10905 = G__10928;
continue;
}
} else {
}
}
break;
}
} else {
}


var G__10929 = seq__10483_10891;
var G__10930 = chunk__10484_10892;
var G__10931 = count__10485_10893;
var G__10932 = (i__10486_10894 + (1));
seq__10483_10891 = G__10929;
chunk__10484_10892 = G__10930;
count__10485_10893 = G__10931;
i__10486_10894 = G__10932;
continue;
} else {
var temp__5804__auto___10933__$1 = cljs.core.seq(seq__10483_10891);
if(temp__5804__auto___10933__$1){
var seq__10483_10934__$1 = temp__5804__auto___10933__$1;
if(cljs.core.chunked_seq_QMARK_(seq__10483_10934__$1)){
var c__5525__auto___10935 = cljs.core.chunk_first(seq__10483_10934__$1);
var G__10936 = cljs.core.chunk_rest(seq__10483_10934__$1);
var G__10937 = c__5525__auto___10935;
var G__10938 = cljs.core.count(c__5525__auto___10935);
var G__10939 = (0);
seq__10483_10891 = G__10936;
chunk__10484_10892 = G__10937;
count__10485_10893 = G__10938;
i__10486_10894 = G__10939;
continue;
} else {
var vec__10535_10940 = cljs.core.first(seq__10483_10934__$1);
var _track_name_10941 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10535_10940,(0),null);
var pattern_10942 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10535_10940,(1),null);
if(cljs.core.truth_(pattern_10942)){
var evs_10943 = repulse.core.query(pattern_10942,sp_10889);
var seq__10539_10944 = cljs.core.seq(evs_10943);
var chunk__10540_10945 = null;
var count__10541_10946 = (0);
var i__10542_10947 = (0);
while(true){
if((i__10542_10947 < count__10541_10946)){
var ev_10948 = chunk__10540_10945.cljs$core$IIndexed$_nth$arity$2(null,i__10542_10947);
var part_start_10951 = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev_10948)));
var t_10952 = ((part_start_10951 - c_10888) * cycle_dur);
var abs_t_10953 = ((c_10888 * cycle_dur) + t_10952);
if((abs_t_10953 >= (0))){
repulse.audio.play_event.cljs$core$IFn$_invoke$arity$3(offline,abs_t_10953,new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_10948));
} else {
}


var G__10954 = seq__10539_10944;
var G__10955 = chunk__10540_10945;
var G__10956 = count__10541_10946;
var G__10957 = (i__10542_10947 + (1));
seq__10539_10944 = G__10954;
chunk__10540_10945 = G__10955;
count__10541_10946 = G__10956;
i__10542_10947 = G__10957;
continue;
} else {
var temp__5804__auto___10958__$2 = cljs.core.seq(seq__10539_10944);
if(temp__5804__auto___10958__$2){
var seq__10539_10959__$1 = temp__5804__auto___10958__$2;
if(cljs.core.chunked_seq_QMARK_(seq__10539_10959__$1)){
var c__5525__auto___10960 = cljs.core.chunk_first(seq__10539_10959__$1);
var G__10961 = cljs.core.chunk_rest(seq__10539_10959__$1);
var G__10962 = c__5525__auto___10960;
var G__10963 = cljs.core.count(c__5525__auto___10960);
var G__10964 = (0);
seq__10539_10944 = G__10961;
chunk__10540_10945 = G__10962;
count__10541_10946 = G__10963;
i__10542_10947 = G__10964;
continue;
} else {
var ev_10965 = cljs.core.first(seq__10539_10959__$1);
var part_start_10966 = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev_10965)));
var t_10967 = ((part_start_10966 - c_10888) * cycle_dur);
var abs_t_10968 = ((c_10888 * cycle_dur) + t_10967);
if((abs_t_10968 >= (0))){
repulse.audio.play_event.cljs$core$IFn$_invoke$arity$3(offline,abs_t_10968,new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_10965));
} else {
}


var G__10973 = cljs.core.next(seq__10539_10959__$1);
var G__10974 = null;
var G__10975 = (0);
var G__10976 = (0);
seq__10539_10944 = G__10973;
chunk__10540_10945 = G__10974;
count__10541_10946 = G__10975;
i__10542_10947 = G__10976;
continue;
}
} else {
}
}
break;
}
} else {
}


var G__10977 = cljs.core.next(seq__10483_10934__$1);
var G__10978 = null;
var G__10979 = (0);
var G__10980 = (0);
seq__10483_10891 = G__10977;
chunk__10484_10892 = G__10978;
count__10485_10893 = G__10979;
i__10486_10894 = G__10980;
continue;
}
} else {
}
}
break;
}


var G__10983 = cljs.core.next(seq__10287_10882__$1);
var G__10984 = null;
var G__10985 = (0);
var G__10986 = (0);
seq__10287_10770 = G__10983;
chunk__10288_10771 = G__10984;
count__10289_10772 = G__10985;
i__10290_10773 = G__10986;
continue;
}
} else {
}
}
break;
}

offline.startRendering().then((function (buffer){
var ch_l = buffer.getChannelData((0));
var ch_r = buffer.getChannelData((1));
var n = ch_l.length;
var bps = (16);
var n_ch = (2);
var data_bytes = ((n * n_ch) * (bps / (8)));
var buf = (new ArrayBuffer(((44) + data_bytes)));
var dv = (new DataView(buf));
var G__10544_10989 = dv;
G__10544_10989.setUint8((0),(82));

G__10544_10989.setUint8((1),(73));

G__10544_10989.setUint8((2),(70));

G__10544_10989.setUint8((3),(70));

G__10544_10989.setUint32((4),((36) + data_bytes),true);

G__10544_10989.setUint8((8),(87));

G__10544_10989.setUint8((9),(65));

G__10544_10989.setUint8((10),(86));

G__10544_10989.setUint8((11),(69));

G__10544_10989.setUint8((12),(102));

G__10544_10989.setUint8((13),(109));

G__10544_10989.setUint8((14),(116));

G__10544_10989.setUint8((15),(32));

G__10544_10989.setUint32((16),(16),true);

G__10544_10989.setUint16((20),(1),true);

G__10544_10989.setUint16((22),n_ch,true);

G__10544_10989.setUint32((24),sr,true);

G__10544_10989.setUint32((28),((sr * n_ch) * (bps / (8))),true);

G__10544_10989.setUint16((32),(n_ch * (bps / (8))),true);

G__10544_10989.setUint16((34),bps,true);

G__10544_10989.setUint8((36),(100));

G__10544_10989.setUint8((37),(97));

G__10544_10989.setUint8((38),(116));

G__10544_10989.setUint8((39),(97));

G__10544_10989.setUint32((40),data_bytes,true);


var n__5593__auto___10990 = n;
var i_10991 = (0);
while(true){
if((i_10991 < n__5593__auto___10990)){
var l_10992 = Math.max((-1),Math.min((1),(ch_l[i_10991])));
var r_10993 = Math.max((-1),Math.min((1),(ch_r[i_10991])));
var offset_10994 = ((44) + (i_10991 * (4)));
dv.setInt16(offset_10994,((l_10992 * (32767)) | (0)),true);

dv.setInt16((offset_10994 + (2)),((r_10993 * (32767)) | (0)),true);

var G__10995 = (i_10991 + (1));
i_10991 = G__10995;
continue;
} else {
}
break;
}

var blob = (new Blob([buf],({"type": "audio/wav"})));
var url = URL.createObjectURL(blob);
var a = document.createElement("a");
(a.href = url);

(a.download = ["repulse-",(cljs.core.truth_(track_kw)?cljs.core.name(track_kw):"all"),"-",cljs.core.str.cljs$core$IFn$_invoke$arity$1(n_cycles),"cycles.wav"].join(''));

document.body.appendChild(a);

a.click();

document.body.removeChild(a);

return setTimeout((function (){
return URL.revokeObjectURL(url);
}),(1000));
})).catch((function (e){
return console.error("[REPuLse] export failed:",e);
}));

return ["exporting ",(cljs.core.truth_(track_kw)?[":",cljs.core.name(track_kw)].join(''):"all tracks")," \u2014 ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(n_cycles)," cycles\u2026"].join('');
}
};
var G__10764 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__10996__i = 0, G__10996__a = new Array(arguments.length -  0);
while (G__10996__i < G__10996__a.length) {G__10996__a[G__10996__i] = arguments[G__10996__i + 0]; ++G__10996__i;}
  args = new cljs.core.IndexedSeq(G__10996__a,0,null);
} 
return G__10764__delegate.call(this,args);};
G__10764.cljs$lang$maxFixedArity = 0;
G__10764.cljs$lang$applyTo = (function (arglist__10997){
var args = cljs.core.seq(arglist__10997);
return G__10764__delegate(args);
});
G__10764.cljs$core$IFn$_invoke$arity$variadic = G__10764__delegate;
return G__10764;
})()
,"midi-map",(function() { 
var G__10998__delegate = function (args){
var args_SINGLEQUOTE_ = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,args);
var cc_num = (cljs.core.nth.cljs$core$IFn$_invoke$arity$2(args_SINGLEQUOTE_,(1)) | (0));
var target = cljs.core.nth.cljs$core$IFn$_invoke$arity$2(args_SINGLEQUOTE_,(2));
repulse.midi.ensure_access_BANG_().then((function (_){
return repulse.midi.map_cc_BANG_(cc_num,target,(function (tgt,val){
var G__10548 = tgt;
var G__10548__$1 = (((G__10548 instanceof cljs.core.Keyword))?G__10548.fqn:null);
switch (G__10548__$1) {
case "filter":
return repulse.fx.set_param_BANG_("filter","value",val);

break;
case "amp":
return cljs.core.deref(repulse.audio.master_gain).gain.setValueAtTime(val,repulse.audio.get_ctx().currentTime);

break;
case "bpm":
return repulse.audio.set_bpm_BANG_(((60) + (val * (180))));

break;
default:
return null;

}
}));
})).catch((function (e){
var G__10551 = ["MIDI error: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(e)].join('');
var G__10552 = new cljs.core.Keyword(null,"error","error",-978969032);
return (set_output_BANG_.cljs$core$IFn$_invoke$arity$2 ? set_output_BANG_.cljs$core$IFn$_invoke$arity$2(G__10551,G__10552) : set_output_BANG_.call(null,G__10551,G__10552));
}));

return "mapping MIDI CC\u2026";
};
var G__10998 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__11004__i = 0, G__11004__a = new Array(arguments.length -  0);
while (G__11004__i < G__11004__a.length) {G__11004__a[G__11004__i] = arguments[G__11004__i + 0]; ++G__11004__i;}
  args = new cljs.core.IndexedSeq(G__11004__a,0,null);
} 
return G__10998__delegate.call(this,args);};
G__10998.cljs$lang$maxFixedArity = 0;
G__10998.cljs$lang$applyTo = (function (arglist__11005){
var args = cljs.core.seq(arglist__11005);
return G__10998__delegate(args);
});
G__10998.cljs$core$IFn$_invoke$arity$variadic = G__10998__delegate;
return G__10998;
})()
,"midi-out",(function() {
var G__11006 = null;
var G__11006__1 = (function (ch){
return repulse.params.midi_out.cljs$core$IFn$_invoke$arity$1(repulse.lisp.eval.unwrap(ch));
});
var G__11006__2 = (function (ch,p){
return repulse.params.midi_out.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap(ch),repulse.lisp.eval.unwrap(p));
});
G__11006 = function(ch,p){
switch(arguments.length){
case 1:
return G__11006__1.call(this,ch);
case 2:
return G__11006__2.call(this,ch,p);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__11006.cljs$core$IFn$_invoke$arity$1 = G__11006__1;
G__11006.cljs$core$IFn$_invoke$arity$2 = G__11006__2;
return G__11006;
})()
,"midi-clock-out!",(function (on_QMARK_){
var on = repulse.lisp.eval.unwrap(on_QMARK_);
if(cljs.core.truth_(on)){
repulse.midi.ensure_access_BANG_().then((function (_){
return repulse.midi.start_clock_BANG_(repulse.audio.get_bpm());
})).catch((function (e){
var G__10554 = ["MIDI error: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(e)].join('');
var G__10555 = new cljs.core.Keyword(null,"error","error",-978969032);
return (set_output_BANG_.cljs$core$IFn$_invoke$arity$2 ? set_output_BANG_.cljs$core$IFn$_invoke$arity$2(G__10554,G__10555) : set_output_BANG_.call(null,G__10554,G__10555));
}));
} else {
repulse.midi.stop_clock_BANG_();
}

return null;
}),"midi-export",(function() { 
var G__11014__delegate = function (args){
var args_SINGLEQUOTE_ = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,args);
var track_name = cljs.core.first(args_SINGLEQUOTE_);
var n_cycles = (function (){var or__5002__auto__ = cljs.core.second(args_SINGLEQUOTE_);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (4);
}
})();
var state = cljs.core.deref(repulse.audio.scheduler_state);
var pattern = cljs.core.get_in.cljs$core$IFn$_invoke$arity$2(state,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"tracks","tracks",-326768501),track_name], null));
var bpm = repulse.audio.get_bpm();
var cycle_dur = new cljs.core.Keyword(null,"cycle-dur","cycle-dur",1241813230).cljs$core$IFn$_invoke$arity$1(state);
if(cljs.core.not(pattern)){
return ["Error: no track :",(cljs.core.truth_(track_name)?cljs.core.name(track_name):null)].join('');
} else {
var events = (function (){var iter__5480__auto__ = (function repulse$env$builtins$ensure_env_BANG__$_iter__10560(s__10561){
return (new cljs.core.LazySeq(null,(function (){
var s__10561__$1 = s__10561;
while(true){
var temp__5804__auto__ = cljs.core.seq(s__10561__$1);
if(temp__5804__auto__){
var xs__6360__auto__ = temp__5804__auto__;
var c = cljs.core.first(xs__6360__auto__);
var sp = new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"start","start",-355208981),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [c,(1)], null),new cljs.core.Keyword(null,"end","end",-268185958),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(c + (1)),(1)], null)], null);
var iterys__5476__auto__ = ((function (s__10561__$1,sp,c,xs__6360__auto__,temp__5804__auto__,args_SINGLEQUOTE_,track_name,n_cycles,state,pattern,bpm,cycle_dur,map__10110,map__10110__$1,on_beat,set_playing_BANG_,set_output_BANG_,make_stop_fn){
return (function repulse$env$builtins$ensure_env_BANG__$_iter__10560_$_iter__10562(s__10563){
return (new cljs.core.LazySeq(null,((function (s__10561__$1,sp,c,xs__6360__auto__,temp__5804__auto__,args_SINGLEQUOTE_,track_name,n_cycles,state,pattern,bpm,cycle_dur,map__10110,map__10110__$1,on_beat,set_playing_BANG_,set_output_BANG_,make_stop_fn){
return (function (){
var s__10563__$1 = s__10563;
while(true){
var temp__5804__auto____$1 = cljs.core.seq(s__10563__$1);
if(temp__5804__auto____$1){
var s__10563__$2 = temp__5804__auto____$1;
if(cljs.core.chunked_seq_QMARK_(s__10563__$2)){
var c__5478__auto__ = cljs.core.chunk_first(s__10563__$2);
var size__5479__auto__ = cljs.core.count(c__5478__auto__);
var b__10565 = cljs.core.chunk_buffer(size__5479__auto__);
if((function (){var i__10564 = (0);
while(true){
if((i__10564 < size__5479__auto__)){
var ev = cljs.core._nth(c__5478__auto__,i__10564);
var v = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev);
var hz = ((typeof v === 'number')?v:((((cljs.core.map_QMARK_(v)) && (typeof new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(v) === 'number')))?new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(v):((((cljs.core.map_QMARK_(v)) && ((((new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(v) instanceof cljs.core.Keyword)) && (repulse.theory.note_keyword_QMARK_(new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(v)))))))?repulse.theory.note__GT_hz(new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(v)):((repulse.theory.note_keyword_QMARK_(v))?repulse.theory.note__GT_hz(v):null
))));
if(cljs.core.truth_(hz)){
var ps = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev)));
var pe = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev)));
cljs.core.chunk_append(b__10565,new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"time-sec","time-sec",1972980092),((ps - c) * cycle_dur),new cljs.core.Keyword(null,"duration-sec","duration-sec",768648959),((pe - ps) * cycle_dur),new cljs.core.Keyword(null,"midi-note","midi-note",1087931036),repulse.midi.hz__GT_midi(hz),new cljs.core.Keyword(null,"channel","channel",734187692),(1)], null));

var G__11029 = (i__10564 + (1));
i__10564 = G__11029;
continue;
} else {
var G__11030 = (i__10564 + (1));
i__10564 = G__11030;
continue;
}
} else {
return true;
}
break;
}
})()){
return cljs.core.chunk_cons(cljs.core.chunk(b__10565),repulse$env$builtins$ensure_env_BANG__$_iter__10560_$_iter__10562(cljs.core.chunk_rest(s__10563__$2)));
} else {
return cljs.core.chunk_cons(cljs.core.chunk(b__10565),null);
}
} else {
var ev = cljs.core.first(s__10563__$2);
var v = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev);
var hz = ((typeof v === 'number')?v:((((cljs.core.map_QMARK_(v)) && (typeof new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(v) === 'number')))?new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(v):((((cljs.core.map_QMARK_(v)) && ((((new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(v) instanceof cljs.core.Keyword)) && (repulse.theory.note_keyword_QMARK_(new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(v)))))))?repulse.theory.note__GT_hz(new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(v)):((repulse.theory.note_keyword_QMARK_(v))?repulse.theory.note__GT_hz(v):null
))));
if(cljs.core.truth_(hz)){
var ps = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev)));
var pe = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev)));
return cljs.core.cons(new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"time-sec","time-sec",1972980092),((ps - c) * cycle_dur),new cljs.core.Keyword(null,"duration-sec","duration-sec",768648959),((pe - ps) * cycle_dur),new cljs.core.Keyword(null,"midi-note","midi-note",1087931036),repulse.midi.hz__GT_midi(hz),new cljs.core.Keyword(null,"channel","channel",734187692),(1)], null),repulse$env$builtins$ensure_env_BANG__$_iter__10560_$_iter__10562(cljs.core.rest(s__10563__$2)));
} else {
var G__11040 = cljs.core.rest(s__10563__$2);
s__10563__$1 = G__11040;
continue;
}
}
} else {
return null;
}
break;
}
});})(s__10561__$1,sp,c,xs__6360__auto__,temp__5804__auto__,args_SINGLEQUOTE_,track_name,n_cycles,state,pattern,bpm,cycle_dur,map__10110,map__10110__$1,on_beat,set_playing_BANG_,set_output_BANG_,make_stop_fn))
,null,null));
});})(s__10561__$1,sp,c,xs__6360__auto__,temp__5804__auto__,args_SINGLEQUOTE_,track_name,n_cycles,state,pattern,bpm,cycle_dur,map__10110,map__10110__$1,on_beat,set_playing_BANG_,set_output_BANG_,make_stop_fn))
;
var fs__5477__auto__ = cljs.core.seq(iterys__5476__auto__(repulse.core.query(pattern,sp)));
if(fs__5477__auto__){
return cljs.core.concat.cljs$core$IFn$_invoke$arity$2(fs__5477__auto__,repulse$env$builtins$ensure_env_BANG__$_iter__10560(cljs.core.rest(s__10561__$1)));
} else {
var G__11044 = cljs.core.rest(s__10561__$1);
s__10561__$1 = G__11044;
continue;
}
} else {
return null;
}
break;
}
}),null,null));
});
return iter__5480__auto__(cljs.core.range.cljs$core$IFn$_invoke$arity$1(n_cycles));
})();
var midi_data = repulse.midi.export_midi(cljs.core.vec(events),bpm);
var blob = (new Blob([midi_data],({"type": "audio/midi"})));
var url = URL.createObjectURL(blob);
var a = document.createElement("a");
(a.href = url);

(a.download = ["repulse-",cljs.core.name(track_name),".mid"].join(''));

document.body.appendChild(a);

a.click();

document.body.removeChild(a);

setTimeout((function (){
return URL.revokeObjectURL(url);
}),(1000));

return ["exported ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(n_cycles)," cycles of :",cljs.core.name(track_name)," as MIDI"].join('');
}
};
var G__11014 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__11046__i = 0, G__11046__a = new Array(arguments.length -  0);
while (G__11046__i < G__11046__a.length) {G__11046__a[G__11046__i] = arguments[G__11046__i + 0]; ++G__11046__i;}
  args = new cljs.core.IndexedSeq(G__11046__a,0,null);
} 
return G__11014__delegate.call(this,args);};
G__11014.cljs$lang$maxFixedArity = 0;
G__11014.cljs$lang$applyTo = (function (arglist__11047){
var args = cljs.core.seq(arglist__11047);
return G__11014__delegate(args);
});
G__11014.cljs$core$IFn$_invoke$arity$variadic = G__11014__delegate;
return G__11014;
})()
,"freesound-key!",(function (key){
cljs.core.reset_BANG_(repulse.env.builtins.freesound_api_key,repulse.lisp.eval.unwrap(key));

return "Freesound API key set";
}),"freesound!",(function (query){
var q = repulse.lisp.eval.unwrap(query);
var key = cljs.core.deref(repulse.env.builtins.freesound_api_key);
if(cljs.core.not(key)){
return "Error: set API key first with (freesound-key! \"your-key\")";
} else {
fetch(["https://freesound.org/apiv2/search/text/","?query=",cljs.core.str.cljs$core$IFn$_invoke$arity$1(encodeURIComponent(q)),"&token=",cljs.core.str.cljs$core$IFn$_invoke$arity$1(key),"&fields=id,name,previews","&page_size=5"].join('')).then((function (p1__10108_SHARP_){
return p1__10108_SHARP_.json();
})).then((function (data){
var results = cljs.core.js__GT_clj.cljs$core$IFn$_invoke$arity$variadic(data.results,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([new cljs.core.Keyword(null,"keywordize-keys","keywordize-keys",1310784252),true], 0));
var seq__10575_11054 = cljs.core.seq(results);
var chunk__10576_11055 = null;
var count__10577_11056 = (0);
var i__10578_11057 = (0);
while(true){
if((i__10578_11057 < count__10577_11056)){
var map__10582_11059 = chunk__10576_11055.cljs$core$IIndexed$_nth$arity$2(null,i__10578_11057);
var map__10582_11060__$1 = cljs.core.__destructure_map(map__10582_11059);
var id_11061 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10582_11060__$1,new cljs.core.Keyword(null,"id","id",-1388402092));
var previews_11062 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10582_11060__$1,new cljs.core.Keyword(null,"previews","previews",-388670715));
var temp__5804__auto___11063 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(previews_11062,new cljs.core.Keyword(null,"preview-hq-mp3","preview-hq-mp3",1810828717));
if(cljs.core.truth_(temp__5804__auto___11063)){
var url_11064 = temp__5804__auto___11063;
repulse.samples.register_url_BANG_(["freesound-",cljs.core.str.cljs$core$IFn$_invoke$arity$1(id_11061)].join(''),url_11064);
} else {
}


var G__11065 = seq__10575_11054;
var G__11066 = chunk__10576_11055;
var G__11067 = count__10577_11056;
var G__11068 = (i__10578_11057 + (1));
seq__10575_11054 = G__11065;
chunk__10576_11055 = G__11066;
count__10577_11056 = G__11067;
i__10578_11057 = G__11068;
continue;
} else {
var temp__5804__auto___11070 = cljs.core.seq(seq__10575_11054);
if(temp__5804__auto___11070){
var seq__10575_11071__$1 = temp__5804__auto___11070;
if(cljs.core.chunked_seq_QMARK_(seq__10575_11071__$1)){
var c__5525__auto___11072 = cljs.core.chunk_first(seq__10575_11071__$1);
var G__11073 = cljs.core.chunk_rest(seq__10575_11071__$1);
var G__11074 = c__5525__auto___11072;
var G__11075 = cljs.core.count(c__5525__auto___11072);
var G__11076 = (0);
seq__10575_11054 = G__11073;
chunk__10576_11055 = G__11074;
count__10577_11056 = G__11075;
i__10578_11057 = G__11076;
continue;
} else {
var map__10586_11077 = cljs.core.first(seq__10575_11071__$1);
var map__10586_11078__$1 = cljs.core.__destructure_map(map__10586_11077);
var id_11079 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10586_11078__$1,new cljs.core.Keyword(null,"id","id",-1388402092));
var previews_11080 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10586_11078__$1,new cljs.core.Keyword(null,"previews","previews",-388670715));
var temp__5804__auto___11081__$1 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(previews_11080,new cljs.core.Keyword(null,"preview-hq-mp3","preview-hq-mp3",1810828717));
if(cljs.core.truth_(temp__5804__auto___11081__$1)){
var url_11082 = temp__5804__auto___11081__$1;
repulse.samples.register_url_BANG_(["freesound-",cljs.core.str.cljs$core$IFn$_invoke$arity$1(id_11079)].join(''),url_11082);
} else {
}


var G__11085 = cljs.core.next(seq__10575_11071__$1);
var G__11086 = null;
var G__11087 = (0);
var G__11088 = (0);
seq__10575_11054 = G__11085;
chunk__10576_11055 = G__11086;
count__10577_11056 = G__11087;
i__10578_11057 = G__11088;
continue;
}
} else {
}
}
break;
}

cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(repulse.samples.loaded_sources,cljs.core.conj,new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"freesound","freesound",9786620),new cljs.core.Keyword(null,"query","query",-1288509510),q,new cljs.core.Keyword(null,"count","count",2139924085),cljs.core.count(results)], null));

var G__10590 = ["loaded ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.count(results))," sounds: ",clojure.string.join.cljs$core$IFn$_invoke$arity$2(", ",cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p1__10109_SHARP_){
return [":freesound-",cljs.core.str.cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"id","id",-1388402092).cljs$core$IFn$_invoke$arity$1(p1__10109_SHARP_))].join('');
}),results))].join('');
var G__10591 = new cljs.core.Keyword(null,"success","success",1890645906);
return (set_output_BANG_.cljs$core$IFn$_invoke$arity$2 ? set_output_BANG_.cljs$core$IFn$_invoke$arity$2(G__10590,G__10591) : set_output_BANG_.call(null,G__10590,G__10591));
})).catch((function (e){
var G__10592 = ["Freesound error: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(e)].join('');
var G__10593 = new cljs.core.Keyword(null,"error","error",-978969032);
return (set_output_BANG_.cljs$core$IFn$_invoke$arity$2 ? set_output_BANG_.cljs$core$IFn$_invoke$arity$2(G__10592,G__10593) : set_output_BANG_.call(null,G__10592,G__10593));
}));

return "searching freesound\u2026";
}
}),"reset!",(function (){
repulse.audio.stop_BANG_();

repulse.session.wipe_BANG_();

window.location.reload();

return null;
})], 0)));

cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.audio.scheduler_state,cljs.core.assoc,new cljs.core.Keyword(null,"on-fx-event","on-fx-event",1435706023),repulse.fx.notify_fx_event_BANG_);

return cljs.core.reset_BANG_(repulse.env.builtins.builtin_names,cljs.core.set(cljs.core.keys(cljs.core.deref(repulse.env.builtins.env_atom))));
} else {
return null;
}
});

//# sourceMappingURL=repulse.env.builtins.js.map
