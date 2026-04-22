goog.provide('repulse.plugins');
if((typeof repulse !== 'undefined') && (typeof repulse.plugins !== 'undefined') && (typeof repulse.plugins.registry !== 'undefined')){
} else {
repulse.plugins.registry = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
}
repulse.plugins.visual_methods = new cljs.core.PersistentVector(null, 4, 5, cljs.core.PersistentVector.EMPTY_NODE, ["init","mount","unmount","destroy"], null);
repulse.plugins.effect_methods = new cljs.core.PersistentVector(null, 6, 5, cljs.core.PersistentVector.EMPTY_NODE, ["init","createNodes","setParam","bypass","getParams","destroy"], null);
repulse.plugins.validate_BANG_ = (function repulse$plugins$validate_BANG_(plugin){
var ptype = plugin.type;
var pname = (function (){var or__5002__auto__ = plugin.name;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return "<unnamed>";
}
})();
var required = (function (){var G__8506 = ptype;
switch (G__8506) {
case "visual":
return repulse.plugins.visual_methods;

break;
case "effect":
return repulse.plugins.effect_methods;

break;
default:
throw (new Error(["[REPuLse] Unknown plugin type: '",cljs.core.str.cljs$core$IFn$_invoke$arity$1(ptype),"' \u2014 expected \"visual\" or \"effect\""].join('')));

}
})();
var missing = cljs.core.filterv((function (p1__8505_SHARP_){
return (!(cljs.core.fn_QMARK_((plugin[p1__8505_SHARP_]))));
}),required);
if(cljs.core.seq(missing)){
throw (new Error(["[REPuLse] Plugin \"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(pname),"\" is missing required method(s): ",clojure.string.join.cljs$core$IFn$_invoke$arity$2(", ",missing)].join('')));
} else {
return null;
}
});
repulse.plugins.register_BANG_ = (function repulse$plugins$register_BANG_(plugin,host){
repulse.plugins.validate_BANG_(plugin);

var n = plugin.name;
var temp__5804__auto___8527 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.plugins.registry),n);
if(cljs.core.truth_(temp__5804__auto___8527)){
var map__8510_8529 = temp__5804__auto___8527;
var map__8510_8530__$1 = cljs.core.__destructure_map(map__8510_8529);
var old_8531 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8510_8530__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
old_8531.destroy();
} else {
}

cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.plugins.registry,cljs.core.assoc,n,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"plugin","plugin",-1688841923),plugin,new cljs.core.Keyword(null,"type","type",1174270348),cljs.core.keyword.cljs$core$IFn$_invoke$arity$1(plugin.type)], null));

return plugin.init(host);
});
repulse.plugins.unregister_BANG_ = (function repulse$plugins$unregister_BANG_(plugin_name){
var temp__5804__auto__ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.plugins.registry),plugin_name);
if(cljs.core.truth_(temp__5804__auto__)){
var map__8514 = temp__5804__auto__;
var map__8514__$1 = cljs.core.__destructure_map(map__8514);
var old = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8514__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
old.destroy();

return cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(repulse.plugins.registry,cljs.core.dissoc,plugin_name);
} else {
return null;
}
});
repulse.plugins.visual_plugins = (function repulse$plugins$visual_plugins(){
return cljs.core.filter.cljs$core$IFn$_invoke$arity$2((function (p1__8518_SHARP_){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"visual","visual",942787224),new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(p1__8518_SHARP_));
}),cljs.core.vals(cljs.core.deref(repulse.plugins.registry)));
});

//# sourceMappingURL=repulse.plugins.js.map
