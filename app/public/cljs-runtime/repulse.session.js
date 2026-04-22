goog.provide('repulse.session');
repulse.session.current_version = (2);
repulse.session.storage_key = "repulse-session";
repulse.session.normalize_bpm = (function repulse$session$normalize_bpm(bpm,context){
var coerced = repulse.audio.coerce_bpm(bpm);
if((((!((bpm == null)))) && (cljs.core.not_EQ_.cljs$core$IFn$_invoke$arity$2(bpm,coerced)))){
console.warn(["[REPuLse] Coerced invalid BPM from ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(context),": ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(bpm)," -> ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(coerced)].join(''));
} else {
}

return coerced;
});
if((typeof repulse !== 'undefined') && (typeof repulse.session !== 'undefined') && (typeof repulse.session.editor_text_fn !== 'undefined')){
} else {
repulse.session.editor_text_fn = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
/**
 * Collect all session state into a serializable map.
 */
repulse.session.build_session_snapshot = (function repulse$session$build_session_snapshot(){
return new cljs.core.PersistentArrayMap(null, 8, [new cljs.core.Keyword(null,"v","v",21465059),repulse.session.current_version,new cljs.core.Keyword(null,"editor","editor",-989377770),(function (){var temp__5802__auto__ = cljs.core.deref(repulse.session.editor_text_fn);
if(cljs.core.truth_(temp__5802__auto__)){
var f = temp__5802__auto__;
return (f.cljs$core$IFn$_invoke$arity$0 ? f.cljs$core$IFn$_invoke$arity$0() : f.call(null));
} else {
return "";
}
})(),new cljs.core.Keyword(null,"bpm","bpm",-1042376389),(function (){var or__5002__auto__ = repulse.audio.get_bpm();
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (120);
}
})(),new cljs.core.Keyword(null,"fx","fx",-1237829572),cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p__9873){
var map__9874 = p__9873;
var map__9874__$1 = cljs.core.__destructure_map(map__9874);
var name = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9874__$1,new cljs.core.Keyword(null,"name","name",1843675177));
var plugin = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9874__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
var bypassed_QMARK_ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9874__$1,new cljs.core.Keyword(null,"bypassed?","bypassed?",132826625));
return new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"name","name",1843675177),name,new cljs.core.Keyword(null,"params","params",710516235),(function (){try{return cljs.core.js__GT_clj.cljs$core$IFn$_invoke$arity$1(plugin.getParams());
}catch (e9877){var _ = e9877;
return cljs.core.PersistentArrayMap.EMPTY;
}})(),new cljs.core.Keyword(null,"bypassed","bypassed",-1325949349),cljs.core.boolean$(bypassed_QMARK_)], null);
}),cljs.core.deref(repulse.fx.chain)),new cljs.core.Keyword(null,"bank","bank",-1982531798),cljs.core.deref(repulse.samples.active_bank_prefix),new cljs.core.Keyword(null,"sources","sources",-321166424),cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p__9878){
var map__9879 = p__9878;
var map__9879__$1 = cljs.core.__destructure_map(map__9879);
var type = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9879__$1,new cljs.core.Keyword(null,"type","type",1174270348));
var id = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9879__$1,new cljs.core.Keyword(null,"id","id",-1388402092));
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"type","type",1174270348),cljs.core.name(type),new cljs.core.Keyword(null,"id","id",-1388402092),id], null);
}),cljs.core.filter.cljs$core$IFn$_invoke$arity$2((function (p1__9870_SHARP_){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"github","github",567794498),new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(p1__9870_SHARP_));
}),cljs.core.deref(repulse.samples.loaded_sources))),new cljs.core.Keyword(null,"muted","muted",1275109029),cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(cljs.core.name,new cljs.core.Keyword(null,"muted","muted",1275109029).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.audio.scheduler_state))),new cljs.core.Keyword(null,"midi","midi",1256960668),cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.PersistentArrayMap.EMPTY,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p__9908){
var vec__9909 = p__9908;
var k = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9909,(0),null);
var map__9912 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9909,(1),null);
var map__9912__$1 = cljs.core.__destructure_map(map__9912);
var target = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9912__$1,new cljs.core.Keyword(null,"target","target",253001721));
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.str.cljs$core$IFn$_invoke$arity$1(k),(cljs.core.truth_(target)?cljs.core.name(target):null)], null);
}),cljs.core.deref(repulse.midi.cc_mappings)))], null);
});
if((typeof repulse !== 'undefined') && (typeof repulse.session !== 'undefined') && (typeof repulse.session.save_timeout !== 'undefined')){
} else {
repulse.session.save_timeout = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
repulse.session.save_session_BANG_ = (function repulse$session$save_session_BANG_(){
try{var data = repulse.session.build_session_snapshot();
return localStorage.setItem(repulse.session.storage_key,JSON.stringify(cljs.core.clj__GT_js(data)));
}catch (e9916){var _ = e9916;
return null;
}});
repulse.session.schedule_save_BANG_ = (function repulse$session$schedule_save_BANG_(){
var temp__5804__auto___10004 = cljs.core.deref(repulse.session.save_timeout);
if(cljs.core.truth_(temp__5804__auto___10004)){
var id_10005 = temp__5804__auto___10004;
clearTimeout(id_10005);
} else {
}

return cljs.core.reset_BANG_(repulse.session.save_timeout,setTimeout(repulse.session.save_session_BANG_,(300)));
});
/**
 * Read session from localStorage. Returns a keyword-keyed map or nil.
 */
repulse.session.load_session = (function repulse$session$load_session(){
try{var temp__5804__auto__ = localStorage.getItem(repulse.session.storage_key);
if(cljs.core.truth_(temp__5804__auto__)){
var raw = temp__5804__auto__;
var data = cljs.core.js__GT_clj.cljs$core$IFn$_invoke$arity$variadic(JSON.parse(raw),cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([new cljs.core.Keyword(null,"keywordize-keys","keywordize-keys",1310784252),true], 0));
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"v","v",21465059).cljs$core$IFn$_invoke$arity$1(data),repulse.session.current_version)){
return cljs.core.update.cljs$core$IFn$_invoke$arity$3(data,new cljs.core.Keyword(null,"bpm","bpm",-1042376389),(function (p1__9918_SHARP_){
return repulse.session.normalize_bpm(p1__9918_SHARP_,"localStorage");
}));
} else {
return null;
}
} else {
return null;
}
}catch (e9919){var _ = e9919;
return null;
}});
/**
 * Convert Phase D keys (repulse-editor / repulse-bpm) to v2 session format.
 * Returns the migrated session map or nil if no legacy data exists.
 */
repulse.session.migrate_legacy_BANG_ = (function repulse$session$migrate_legacy_BANG_(){
var editor = localStorage.getItem("repulse-editor");
var bpm = localStorage.getItem("repulse-bpm");
if(cljs.core.truth_((function (){var or__5002__auto__ = editor;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return bpm;
}
})())){
var session = new cljs.core.PersistentArrayMap(null, 8, [new cljs.core.Keyword(null,"v","v",21465059),repulse.session.current_version,new cljs.core.Keyword(null,"editor","editor",-989377770),(function (){var or__5002__auto__ = editor;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return "(seq :bd :sd :bd :sd)";
}
})(),new cljs.core.Keyword(null,"bpm","bpm",-1042376389),repulse.session.normalize_bpm((function (){var G__9928 = bpm;
if((G__9928 == null)){
return null;
} else {
return parseFloat(G__9928);
}
})(),"legacy localStorage"),new cljs.core.Keyword(null,"fx","fx",-1237829572),cljs.core.PersistentVector.EMPTY,new cljs.core.Keyword(null,"bank","bank",-1982531798),null,new cljs.core.Keyword(null,"sources","sources",-321166424),cljs.core.PersistentVector.EMPTY,new cljs.core.Keyword(null,"muted","muted",1275109029),cljs.core.PersistentVector.EMPTY,new cljs.core.Keyword(null,"midi","midi",1256960668),cljs.core.PersistentArrayMap.EMPTY], null);
localStorage.setItem(repulse.session.storage_key,JSON.stringify(cljs.core.clj__GT_js(session)));

localStorage.removeItem("repulse-editor");

localStorage.removeItem("repulse-bpm");

return session;
} else {
return null;
}
});
/**
 * Delete all persisted state (current and legacy keys).
 */
repulse.session.wipe_BANG_ = (function repulse$session$wipe_BANG_(){
localStorage.removeItem(repulse.session.storage_key);

localStorage.removeItem("repulse-editor");

return localStorage.removeItem("repulse-bpm");
});

//# sourceMappingURL=repulse.session.js.map
