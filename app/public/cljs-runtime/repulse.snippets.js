goog.provide('repulse.snippets');
if((typeof repulse !== 'undefined') && (typeof repulse.snippets !== 'undefined') && (typeof repulse.snippets.library_atom !== 'undefined')){
} else {
repulse.snippets.library_atom = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"version","version",425292698),(1),new cljs.core.Keyword(null,"snippets","snippets",-1201334367),cljs.core.PersistentVector.EMPTY], null));
}
if((typeof repulse !== 'undefined') && (typeof repulse.snippets !== 'undefined') && (typeof repulse.snippets.loaded_QMARK_ !== 'undefined')){
} else {
repulse.snippets.loaded_QMARK_ = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(false);
}
if((typeof repulse !== 'undefined') && (typeof repulse.snippets !== 'undefined') && (typeof repulse.snippets.loading_QMARK_ !== 'undefined')){
} else {
repulse.snippets.loading_QMARK_ = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(false);
}
repulse.snippets.all_snippets = (function repulse$snippets$all_snippets(){
return new cljs.core.Keyword(null,"snippets","snippets",-1201334367).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.snippets.library_atom));
});
repulse.snippets.all_tags = (function repulse$snippets$all_tags(){
return cljs.core.sort.cljs$core$IFn$_invoke$arity$1(cljs.core.distinct.cljs$core$IFn$_invoke$arity$1(cljs.core.mapcat.cljs$core$IFn$_invoke$arity$variadic(new cljs.core.Keyword(null,"tags","tags",1771418977),cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([repulse.snippets.all_snippets()], 0))));
});
repulse.snippets.by_id = (function repulse$snippets$by_id(id){
return cljs.core.first(cljs.core.filter.cljs$core$IFn$_invoke$arity$2((function (p1__8753_SHARP_){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"id","id",-1388402092).cljs$core$IFn$_invoke$arity$1(p1__8753_SHARP_),cljs.core.str.cljs$core$IFn$_invoke$arity$1(id));
}),repulse.snippets.all_snippets()));
});
/**
 * Return snippets matching query (searches title, description, code) and tag.
 */
repulse.snippets.filter_snippets = (function repulse$snippets$filter_snippets(query,tag){
var q = (cljs.core.truth_((function (){var and__5000__auto__ = query;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core.seq(clojure.string.trim(query));
} else {
return and__5000__auto__;
}
})())?clojure.string.lower_case(clojure.string.trim(query)):null);
var snippets = repulse.snippets.all_snippets();
var G__8764 = snippets;
var G__8764__$1 = (cljs.core.truth_(tag)?cljs.core.filter.cljs$core$IFn$_invoke$arity$2((function (p1__8756_SHARP_){
return cljs.core.some(cljs.core.PersistentHashSet.createAsIfByAssoc([tag]),new cljs.core.Keyword(null,"tags","tags",1771418977).cljs$core$IFn$_invoke$arity$1(p1__8756_SHARP_));
}),G__8764):G__8764);
if(cljs.core.truth_(q)){
return cljs.core.filter.cljs$core$IFn$_invoke$arity$2((function (s){
return ((clojure.string.includes_QMARK_(clojure.string.lower_case((function (){var or__5002__auto__ = new cljs.core.Keyword(null,"title","title",636505583).cljs$core$IFn$_invoke$arity$1(s);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return "";
}
})()),q)) || (((clojure.string.includes_QMARK_(clojure.string.lower_case((function (){var or__5002__auto____$1 = new cljs.core.Keyword(null,"description","description",-1428560544).cljs$core$IFn$_invoke$arity$1(s);
if(cljs.core.truth_(or__5002__auto____$1)){
return or__5002__auto____$1;
} else {
return "";
}
})()),q)) || (clojure.string.includes_QMARK_(clojure.string.lower_case((function (){var or__5002__auto____$2 = new cljs.core.Keyword(null,"code","code",1586293142).cljs$core$IFn$_invoke$arity$1(s);
if(cljs.core.truth_(or__5002__auto____$2)){
return or__5002__auto____$2;
} else {
return "";
}
})()),q)))));
}),G__8764__$1);
} else {
return G__8764__$1;
}
});
repulse.snippets.load_from_static_BANG_ = (function repulse$snippets$load_from_static_BANG_(){
return fetch("/snippets/library.json").then((function (p1__8774_SHARP_){
return p1__8774_SHARP_.json();
})).then((function (data){
var d = cljs.core.js__GT_clj.cljs$core$IFn$_invoke$arity$variadic(data,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([new cljs.core.Keyword(null,"keywordize-keys","keywordize-keys",1310784252),true], 0));
cljs.core.reset_BANG_(repulse.snippets.library_atom,d);

cljs.core.reset_BANG_(repulse.snippets.loaded_QMARK_,true);

return cljs.core.reset_BANG_(repulse.snippets.loading_QMARK_,false);
})).catch((function (e){
cljs.core.reset_BANG_(repulse.snippets.loading_QMARK_,false);

return console.warn("[REPuLse] snippet library load failed:",e);
}));
});
repulse.snippets.load_from_api_BANG_ = (function repulse$snippets$load_from_api_BANG_(){
return repulse.api.fetch_snippets.cljs$core$IFn$_invoke$arity$0().then((function (result){
var temp__5802__auto__ = new cljs.core.Keyword(null,"data","data",-232669377).cljs$core$IFn$_invoke$arity$1(result);
if(cljs.core.truth_(temp__5802__auto__)){
var snippets = temp__5802__auto__;
cljs.core.reset_BANG_(repulse.snippets.library_atom,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"version","version",425292698),(2),new cljs.core.Keyword(null,"snippets","snippets",-1201334367),snippets], null));

cljs.core.reset_BANG_(repulse.snippets.loaded_QMARK_,true);

return cljs.core.reset_BANG_(repulse.snippets.loading_QMARK_,false);
} else {
console.warn("[REPuLse] API snippet load failed:",new cljs.core.Keyword(null,"error","error",-978969032).cljs$core$IFn$_invoke$arity$1(result));

return repulse.snippets.load_from_static_BANG_();
}
})).catch((function (e){
console.warn("[REPuLse] API snippet fetch error:",e);

return repulse.snippets.load_from_static_BANG_();
}));
});
/**
 * Populate library-atom from the API (when authenticated) or static JSON.
 * No-ops if already loaded or loading.
 */
repulse.snippets.load_BANG_ = (function repulse$snippets$load_BANG_(){
if(((cljs.core.not(cljs.core.deref(repulse.snippets.loaded_QMARK_))) && (cljs.core.not(cljs.core.deref(repulse.snippets.loading_QMARK_))))){
cljs.core.reset_BANG_(repulse.snippets.loading_QMARK_,true);

if(cljs.core.truth_(repulse.auth.session())){
return repulse.snippets.load_from_api_BANG_();
} else {
return repulse.snippets.load_from_static_BANG_();
}
} else {
return null;
}
});
/**
 * Returns the Lisp `snippet` built-in fn.
 * editor-view-atom — atom holding the current CodeMirror EditorView.
 * evaluate-ref     — atom holding the evaluate! fn (populated after eval-orchestrator init).
 */
repulse.snippets.snippet_builtin = (function repulse$snippets$snippet_builtin(editor_view_atom,evaluate_ref){
return (function() { 
var G__8792__delegate = function (args){
repulse.snippets.load_BANG_();

var id_arg = ((cljs.core.seq(args))?repulse.lisp.eval.unwrap(cljs.core.first(args)):null);
if((id_arg == null)){
if(cljs.core.truth_(cljs.core.deref(repulse.snippets.loaded_QMARK_))){
return ["available snippets: ",clojure.string.join.cljs$core$IFn$_invoke$arity$2(" ",cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p1__8779_SHARP_){
return [":",cljs.core.str.cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"id","id",-1388402092).cljs$core$IFn$_invoke$arity$1(p1__8779_SHARP_))].join('');
}),repulse.snippets.all_snippets()))].join('');
} else {
return "loading snippet library\u2026";
}
} else {
var id = cljs.core.name(id_arg);
var snippet = repulse.snippets.by_id(id);
if(cljs.core.truth_(snippet)){
var temp__5804__auto___8794 = cljs.core.deref(editor_view_atom);
if(cljs.core.truth_(temp__5804__auto___8794)){
var view_8795 = temp__5804__auto___8794;
var code_8796 = new cljs.core.Keyword(null,"code","code",1586293142).cljs$core$IFn$_invoke$arity$1(snippet);
var doc_len_8797 = view_8795.state.doc.length;
view_8795.dispatch(({"changes": ({"from": doc_len_8797, "to": doc_len_8797, "insert": ["\n\n",cljs.core.str.cljs$core$IFn$_invoke$arity$1(code_8796)].join('')})}));

setTimeout((function (){
var temp__5804__auto____$1 = cljs.core.deref(evaluate_ref);
if(cljs.core.truth_(temp__5804__auto____$1)){
var f = temp__5804__auto____$1;
return (f.cljs$core$IFn$_invoke$arity$1 ? f.cljs$core$IFn$_invoke$arity$1("(upd)") : f.call(null,"(upd)"));
} else {
return null;
}
}),(50));
} else {
}

return ["=> inserted snippet :",id].join('');
} else {
return ["unknown snippet :",id," \u2014 try (snippet) to list available"].join('');
}
}
};
var G__8792 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8798__i = 0, G__8798__a = new Array(arguments.length -  0);
while (G__8798__i < G__8798__a.length) {G__8798__a[G__8798__i] = arguments[G__8798__i + 0]; ++G__8798__i;}
  args = new cljs.core.IndexedSeq(G__8798__a,0,null);
} 
return G__8792__delegate.call(this,args);};
G__8792.cljs$lang$maxFixedArity = 0;
G__8792.cljs$lang$applyTo = (function (arglist__8799){
var args = cljs.core.seq(arglist__8799);
return G__8792__delegate(args);
});
G__8792.cljs$core$IFn$_invoke$arity$variadic = G__8792__delegate;
return G__8792;
})()
;
});

//# sourceMappingURL=repulse.snippets.js.map
