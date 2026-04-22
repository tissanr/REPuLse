goog.provide('repulse.api');
repulse.api.auth_headers = (function repulse$api$auth_headers(){
var temp__5802__auto__ = repulse.auth.session();
if(cljs.core.truth_(temp__5802__auto__)){
var sess = temp__5802__auto__;
return ({"Authorization": ["Bearer ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(sess.access_token)].join(''), "Content-Type": "application/json"});
} else {
return ({"Content-Type": "application/json"});
}
});
repulse.api.parse_response = (function repulse$api$parse_response(resp){
return resp.json().then((function (data){
if(cljs.core.truth_(resp.ok)){
return new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"data","data",-232669377),cljs.core.js__GT_clj.cljs$core$IFn$_invoke$arity$variadic(data,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([new cljs.core.Keyword(null,"keywordize-keys","keywordize-keys",1310784252),true], 0))], null);
} else {
return new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"error","error",-978969032),(function (){var or__5002__auto__ = new cljs.core.Keyword(null,"error","error",-978969032).cljs$core$IFn$_invoke$arity$1(cljs.core.js__GT_clj.cljs$core$IFn$_invoke$arity$variadic(data,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([new cljs.core.Keyword(null,"keywordize-keys","keywordize-keys",1310784252),true], 0)));
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return ["HTTP ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(resp.status)].join('');
}
})()], null);
}
}));
});
/**
 * GET /api/snippets — returns Promise<{:data [...] | :error str}>.
 */
repulse.api.fetch_snippets = (function repulse$api$fetch_snippets(var_args){
var G__8689 = arguments.length;
switch (G__8689) {
case 0:
return repulse.api.fetch_snippets.cljs$core$IFn$_invoke$arity$0();

break;
case 1:
return repulse.api.fetch_snippets.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.api.fetch_snippets.cljs$core$IFn$_invoke$arity$0 = (function (){
return repulse.api.fetch_snippets.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
}));

(repulse.api.fetch_snippets.cljs$core$IFn$_invoke$arity$1 = (function (p__8695){
var map__8696 = p__8695;
var map__8696__$1 = cljs.core.__destructure_map(map__8696);
var tag = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8696__$1,new cljs.core.Keyword(null,"tag","tag",-1290361223));
var q = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8696__$1,new cljs.core.Keyword(null,"q","q",689001697));
var limit = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8696__$1,new cljs.core.Keyword(null,"limit","limit",-1355822363));
var params = (function (){var G__8697 = cljs.core.PersistentArrayMap.EMPTY;
var G__8697__$1 = (cljs.core.truth_(tag)?cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(G__8697,new cljs.core.Keyword(null,"tag","tag",-1290361223),tag):G__8697);
var G__8697__$2 = (cljs.core.truth_(q)?cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(G__8697__$1,new cljs.core.Keyword(null,"q","q",689001697),q):G__8697__$1);
if(cljs.core.truth_(limit)){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(G__8697__$2,new cljs.core.Keyword(null,"limit","limit",-1355822363),limit);
} else {
return G__8697__$2;
}
})();
var qs = ((cljs.core.seq(params))?["?",clojure.string.join.cljs$core$IFn$_invoke$arity$2("&",cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p__8704){
var vec__8705 = p__8704;
var k = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8705,(0),null);
var v = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8705,(1),null);
return [cljs.core.name(k),"=",cljs.core.str.cljs$core$IFn$_invoke$arity$1(encodeURIComponent(v))].join('');
}),params))].join(''):null);
return fetch(["/api/snippets",qs].join(''),({"headers": repulse.api.auth_headers()})).then(repulse.api.parse_response).catch((function (e){
return new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"error","error",-978969032),e.message], null);
}));
}));

(repulse.api.fetch_snippets.cljs$lang$maxFixedArity = 1);

/**
 * POST /api/snippets — returns Promise<{:data snippet | :error str}>.
 */
repulse.api.create_snippet_BANG_ = (function repulse$api$create_snippet_BANG_(snippet){
return fetch("/api/snippets",({"method": "POST", "headers": repulse.api.auth_headers(), "body": JSON.stringify(cljs.core.clj__GT_js(snippet))})).then(repulse.api.parse_response).catch((function (e){
return new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"error","error",-978969032),e.message], null);
}));
});
/**
 * POST /api/snippets/:id/star — returns Promise<{:data {:starred bool} | :error str}>.
 */
repulse.api.toggle_star_BANG_ = (function repulse$api$toggle_star_BANG_(snippet_id){
return fetch(["/api/snippets/",cljs.core.str.cljs$core$IFn$_invoke$arity$1(snippet_id),"/star"].join(''),({"method": "POST", "headers": repulse.api.auth_headers()})).then(repulse.api.parse_response).catch((function (e){
return new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"error","error",-978969032),e.message], null);
}));
});

//# sourceMappingURL=repulse.api.js.map
