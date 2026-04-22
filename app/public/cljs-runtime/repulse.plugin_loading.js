goog.provide('repulse.plugin_loading');
repulse.plugin_loading.dynamic_import_BANG_ = (new Function("url","return import(url)"));
if((typeof repulse !== 'undefined') && (typeof repulse.plugin_loading !== 'undefined') && (typeof repulse.plugin_loading.plugin_consent !== 'undefined')){
} else {
repulse.plugin_loading.plugin_consent = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
}
repulse.plugin_loading.plugin_origin = (function repulse$plugin_loading$plugin_origin(url){
try{return (new URL(url,location.href)).origin;
}catch (e9868){var _ = e9868;
return null;
}});
repulse.plugin_loading.plugin_denied_error = (function repulse$plugin_loading$plugin_denied_error(origin){
return repulse.lisp.core.eval_error.cljs$core$IFn$_invoke$arity$1(["Plugin from ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(origin)," was previously denied. Reload the page to reconsider."].join(''));
});
repulse.plugin_loading.confirm_plugin_origin_BANG_ = (function repulse$plugin_loading$confirm_plugin_origin_BANG_(origin){
var G__9869 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.plugin_loading.plugin_consent),origin);
var G__9869__$1 = (((G__9869 instanceof cljs.core.Keyword))?G__9869.fqn:null);
switch (G__9869__$1) {
case "granted":
return true;

break;
case "denied":
return false;

break;
default:
var allowed_QMARK_ = confirm(["This code wants to load a plugin from ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(origin),". Plugins run JavaScript in your session. Only load from ","sources you trust. Load?"].join(''));
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.plugin_loading.plugin_consent,cljs.core.assoc,origin,(cljs.core.truth_(allowed_QMARK_)?new cljs.core.Keyword(null,"granted","granted",-1093389318):new cljs.core.Keyword(null,"denied","denied",-1141109291)));

return allowed_QMARK_;

}
});
if((typeof repulse !== 'undefined') && (typeof repulse.plugin_loading !== 'undefined') && (typeof repulse.plugin_loading.make_host_ref !== 'undefined')){
} else {
repulse.plugin_loading.make_host_ref = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
if((typeof repulse !== 'undefined') && (typeof repulse.plugin_loading !== 'undefined') && (typeof repulse.plugin_loading.mount_visual_BANG__ref !== 'undefined')){
} else {
repulse.plugin_loading.mount_visual_BANG__ref = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
if((typeof repulse !== 'undefined') && (typeof repulse.plugin_loading !== 'undefined') && (typeof repulse.plugin_loading.maybe_hide_visual_BANG__ref !== 'undefined')){
} else {
repulse.plugin_loading.maybe_hide_visual_BANG__ref = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
/**
 * Wire app-level host/visual fns into this module.
 * Must be called before any `load-plugin` or `unload-plugin` builtin is invoked.
 * config — {:make-host-fn f :mount-visual!-fn f :maybe-hide-visual!-fn f}
 */
repulse.plugin_loading.init_BANG_ = (function repulse$plugin_loading$init_BANG_(p__9875){
var map__9876 = p__9875;
var map__9876__$1 = cljs.core.__destructure_map(map__9876);
var make_host_fn = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9876__$1,new cljs.core.Keyword(null,"make-host-fn","make-host-fn",1656062645));
var mount_visual_BANG__fn = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9876__$1,new cljs.core.Keyword(null,"mount-visual!-fn","mount-visual!-fn",-769830191));
var maybe_hide_visual_BANG__fn = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9876__$1,new cljs.core.Keyword(null,"maybe-hide-visual!-fn","maybe-hide-visual!-fn",-1447580479));
cljs.core.reset_BANG_(repulse.plugin_loading.make_host_ref,make_host_fn);

cljs.core.reset_BANG_(repulse.plugin_loading.mount_visual_BANG__ref,mount_visual_BANG__fn);

return cljs.core.reset_BANG_(repulse.plugin_loading.maybe_hide_visual_BANG__ref,maybe_hide_visual_BANG__fn);
});
/**
 * Returns the Lisp `load-plugin` built-in fn.
 */
repulse.plugin_loading.load_plugin_builtin = (function repulse$plugin_loading$load_plugin_builtin(){
return (function (url){
var url_SINGLEQUOTE_ = repulse.lisp.eval.unwrap(url);
var origin = repulse.plugin_loading.plugin_origin(url_SINGLEQUOTE_);
if((origin == null)){
return repulse.lisp.core.eval_error.cljs$core$IFn$_invoke$arity$1(["Invalid plugin URL: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(url_SINGLEQUOTE_)].join(''));
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"denied","denied",-1141109291),cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.plugin_loading.plugin_consent),origin))){
return repulse.plugin_loading.plugin_denied_error(origin);
} else {
if(cljs.core.not(repulse.plugin_loading.confirm_plugin_origin_BANG_(origin))){
return repulse.plugin_loading.plugin_denied_error(origin);
} else {
(repulse.plugin_loading.dynamic_import_BANG_.cljs$core$IFn$_invoke$arity$1 ? repulse.plugin_loading.dynamic_import_BANG_.cljs$core$IFn$_invoke$arity$1(url_SINGLEQUOTE_) : repulse.plugin_loading.dynamic_import_BANG_.call(null,url_SINGLEQUOTE_)).then((function (m){
var plug = m.default;
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("effect",plug.type)){
repulse.fx.remove_effect_BANG_(plug.name);
} else {
}

repulse.plugins.register_BANG_(plug,(function (){var fexpr__9900 = cljs.core.deref(repulse.plugin_loading.make_host_ref);
return (fexpr__9900.cljs$core$IFn$_invoke$arity$0 ? fexpr__9900.cljs$core$IFn$_invoke$arity$0() : fexpr__9900.call(null));
})());

if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("visual",plug.type)){
var fexpr__9902 = cljs.core.deref(repulse.plugin_loading.mount_visual_BANG__ref);
return (fexpr__9902.cljs$core$IFn$_invoke$arity$1 ? fexpr__9902.cljs$core$IFn$_invoke$arity$1(plug) : fexpr__9902.call(null,plug));
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("effect",plug.type)){
return repulse.fx.add_effect_BANG_(plug);
} else {
return null;
}
}
})).catch((function (e){
return console.warn("[REPuLse] Plugin load failed:",e);
}));

return null;

}
}
}
});
});
/**
 * Returns the Lisp `unload-plugin` built-in fn.
 */
repulse.plugin_loading.unload_plugin_builtin = (function repulse$plugin_loading$unload_plugin_builtin(){
return (function (name){
var name_SINGLEQUOTE_ = repulse.lisp.eval.unwrap(name);
if(cljs.core.truth_(cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.plugins.registry),name_SINGLEQUOTE_))){
repulse.plugins.unregister_BANG_(name_SINGLEQUOTE_);

var fexpr__9904_9907 = cljs.core.deref(repulse.plugin_loading.maybe_hide_visual_BANG__ref);
(fexpr__9904_9907.cljs$core$IFn$_invoke$arity$0 ? fexpr__9904_9907.cljs$core$IFn$_invoke$arity$0() : fexpr__9904_9907.call(null));

return ["unloaded: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(name_SINGLEQUOTE_)].join('');
} else {
return repulse.lisp.core.eval_error.cljs$core$IFn$_invoke$arity$1(["no plugin named \"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(name_SINGLEQUOTE_),"\""].join(''));
}
});
});

//# sourceMappingURL=repulse.plugin_loading.js.map
