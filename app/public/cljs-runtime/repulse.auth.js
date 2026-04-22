goog.provide('repulse.auth');
var module$node_modules$$supabase$supabase_js$dist$index_cjs=shadow.js.require("module$node_modules$$supabase$supabase_js$dist$index_cjs", {});
if((typeof repulse !== 'undefined') && (typeof repulse.auth !== 'undefined') && (typeof repulse.auth.auth_atom !== 'undefined')){
} else {
repulse.auth.auth_atom = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
if((typeof repulse !== 'undefined') && (typeof repulse.auth !== 'undefined') && (typeof repulse.auth.client_atom !== 'undefined')){
} else {
repulse.auth.client_atom = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
repulse.auth.supabase_client = (function repulse$auth$supabase_client(){
return cljs.core.deref(repulse.auth.client_atom);
});
repulse.auth.session = (function repulse$auth$session(){
return new cljs.core.Keyword(null,"session","session",1008279103).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.auth.auth_atom));
});
repulse.auth.user_display_name = (function repulse$auth$user_display_name(){
var or__5002__auto__ = cljs.core.get_in.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.auth.auth_atom),new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"user","user",1532431356),new cljs.core.Keyword(null,"user_metadata","user_metadata",1153475143),new cljs.core.Keyword(null,"full_name","full_name",1257415930)], null));
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
var or__5002__auto____$1 = cljs.core.get_in.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.auth.auth_atom),new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"user","user",1532431356),new cljs.core.Keyword(null,"user_metadata","user_metadata",1153475143),new cljs.core.Keyword(null,"user_name","user_name",-1796639078)], null));
if(cljs.core.truth_(or__5002__auto____$1)){
return or__5002__auto____$1;
} else {
return cljs.core.get_in.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.auth.auth_atom),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"user","user",1532431356),new cljs.core.Keyword(null,"email","email",1415816706)], null));
}
}
});
repulse.auth.avatar_url = (function repulse$auth$avatar_url(){
return cljs.core.get_in.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.auth.auth_atom),new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"user","user",1532431356),new cljs.core.Keyword(null,"user_metadata","user_metadata",1153475143),new cljs.core.Keyword(null,"avatar_url","avatar_url",1520721439)], null));
});
repulse.auth.login_BANG_ = (function repulse$auth$login_BANG_(){
var temp__5804__auto__ = cljs.core.deref(repulse.auth.client_atom);
if(cljs.core.truth_(temp__5804__auto__)){
var sb = temp__5804__auto__;
return sb.auth.signInWithOAuth(({"provider": "github", "options": ({"redirectTo": location.href})})).catch((function (e){
return console.error("[REPuLse/auth] login failed:",e);
}));
} else {
return null;
}
});
repulse.auth.logout_BANG_ = (function repulse$auth$logout_BANG_(){
var temp__5804__auto__ = cljs.core.deref(repulse.auth.client_atom);
if(cljs.core.truth_(temp__5804__auto__)){
var sb = temp__5804__auto__;
return sb.auth.signOut().then((function (_){
return cljs.core.reset_BANG_(repulse.auth.auth_atom,null);
})).catch((function (e){
return console.error("[REPuLse/auth] logout failed:",e);
}));
} else {
return null;
}
});
repulse.auth.apply_session_BANG_ = (function repulse$auth$apply_session_BANG_(session){
if(cljs.core.truth_(session)){
return cljs.core.reset_BANG_(repulse.auth.auth_atom,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"session","session",1008279103),session,new cljs.core.Keyword(null,"user","user",1532431356),session.user], null));
} else {
return cljs.core.reset_BANG_(repulse.auth.auth_atom,null);
}
});
/**
 * Fetch Supabase credentials from /api/env, create the client, restore any
 * existing session, and subscribe to future auth state changes.
 * Calls on-change-fn (fn [auth-map-or-nil]) whenever auth state changes.
 */
repulse.auth.init_auth_BANG_ = (function repulse$auth$init_auth_BANG_(var_args){
var args__5732__auto__ = [];
var len__5726__auto___6051 = arguments.length;
var i__5727__auto___6052 = (0);
while(true){
if((i__5727__auto___6052 < len__5726__auto___6051)){
args__5732__auto__.push((arguments[i__5727__auto___6052]));

var G__6053 = (i__5727__auto___6052 + (1));
i__5727__auto___6052 = G__6053;
continue;
} else {
}
break;
}

var argseq__5733__auto__ = ((((0) < args__5732__auto__.length))?(new cljs.core.IndexedSeq(args__5732__auto__.slice((0)),(0),null)):null);
return repulse.auth.init_auth_BANG_.cljs$core$IFn$_invoke$arity$variadic(argseq__5733__auto__);
});

(repulse.auth.init_auth_BANG_.cljs$core$IFn$_invoke$arity$variadic = (function (p__6047){
var map__6048 = p__6047;
var map__6048__$1 = cljs.core.__destructure_map(map__6048);
var on_change_fn = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__6048__$1,new cljs.core.Keyword(null,"on-change-fn","on-change-fn",-1237394275));
return fetch("/api/env").then((function (r){
if(cljs.core.truth_(r.ok)){
return r.json();
} else {
throw (new Error(["env fetch failed: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(r.status)].join('')));
}
})).then((function (env){
var url = env.url;
var key = env.key;
if(cljs.core.truth_((function (){var and__5000__auto__ = url;
if(cljs.core.truth_(and__5000__auto__)){
return key;
} else {
return and__5000__auto__;
}
})())){
var sb = module$node_modules$$supabase$supabase_js$dist$index_cjs.createClient(url,key);
cljs.core.reset_BANG_(repulse.auth.client_atom,sb);

sb.auth.getSession().then((function (result){
repulse.auth.apply_session_BANG_(result.data.session);

if(cljs.core.truth_(on_change_fn)){
var G__6049 = cljs.core.deref(repulse.auth.auth_atom);
return (on_change_fn.cljs$core$IFn$_invoke$arity$1 ? on_change_fn.cljs$core$IFn$_invoke$arity$1(G__6049) : on_change_fn.call(null,G__6049));
} else {
return null;
}
}));

return sb.auth.onAuthStateChange((function (_event,session){
repulse.auth.apply_session_BANG_(session);

if(cljs.core.truth_(on_change_fn)){
var G__6050 = cljs.core.deref(repulse.auth.auth_atom);
return (on_change_fn.cljs$core$IFn$_invoke$arity$1 ? on_change_fn.cljs$core$IFn$_invoke$arity$1(G__6050) : on_change_fn.call(null,G__6050));
} else {
return null;
}
}));
} else {
return null;
}
})).catch((function (e){
return console.info("[REPuLse/auth] running without backend:",e.message);
}));
}));

(repulse.auth.init_auth_BANG_.cljs$lang$maxFixedArity = (0));

/** @this {Function} */
(repulse.auth.init_auth_BANG_.cljs$lang$applyTo = (function (seq6046){
var self__5712__auto__ = this;
return self__5712__auto__.cljs$core$IFn$_invoke$arity$variadic(cljs.core.seq(seq6046));
}));


//# sourceMappingURL=repulse.auth.js.map
