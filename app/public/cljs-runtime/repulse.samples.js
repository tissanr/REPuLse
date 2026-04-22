goog.provide('repulse.samples');
repulse.samples.registry = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
repulse.samples.buffer_cache = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
repulse.samples.in_flight = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
if((typeof repulse !== 'undefined') && (typeof repulse.samples !== 'undefined') && (typeof repulse.samples.active_bank_prefix !== 'undefined')){
} else {
repulse.samples.active_bank_prefix = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
if((typeof repulse !== 'undefined') && (typeof repulse.samples !== 'undefined') && (typeof repulse.samples.loaded_sources !== 'undefined')){
} else {
repulse.samples.loaded_sources = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentVector.EMPTY);
}
/**
 * Set the global drum machine prefix. Pass nil to clear.
 */
repulse.samples.set_bank_prefix_BANG_ = (function repulse$samples$set_bank_prefix_BANG_(prefix){
cljs.core.reset_BANG_(repulse.samples.active_bank_prefix,(cljs.core.truth_(prefix)?cljs.core.name(prefix):null));

return console.log(["[REPuLse] bank prefix: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1((function (){var or__5002__auto__ = cljs.core.deref(repulse.samples.active_bank_prefix);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return "none";
}
})())].join(''));
});
repulse.samples.DEFAULT_MANIFESTS = new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, ["https://raw.githubusercontent.com/felixroos/dough-samples/main/Dirt-Samples.json","https://raw.githubusercontent.com/felixroos/dough-samples/main/tidal-drum-machines.json","https://raw.githubusercontent.com/tidalcycles/Dirt-Samples/master/strudel.json"], null);
/**
 * Parse a manifest JS object into {bank-name -> [full-url ...]}.
 */
repulse.samples.parse_manifest = (function repulse$samples$parse_manifest(js_data){
var raw = cljs.core.js__GT_clj.cljs$core$IFn$_invoke$arity$1(js_data);
var base = cljs.core.get.cljs$core$IFn$_invoke$arity$3(raw,"_base","");
return cljs.core.reduce_kv((function (acc,k,v){
if(((cljs.core.not_EQ_.cljs$core$IFn$_invoke$arity$2(k,"_base")) && (((cljs.core.vector_QMARK_(v)) && (cljs.core.seq(v)))))){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(acc,k,cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__7912_SHARP_){
return [cljs.core.str.cljs$core$IFn$_invoke$arity$1(base),cljs.core.str.cljs$core$IFn$_invoke$arity$1(p1__7912_SHARP_)].join('');
}),v));
} else {
return acc;
}
}),cljs.core.PersistentArrayMap.EMPTY,raw);
});
/**
 * Fetch a JSON manifest and merge its banks into the registry.
 */
repulse.samples.load_manifest_BANG_ = (function repulse$samples$load_manifest_BANG_(url){
return fetch(url).then((function (p1__7927_SHARP_){
return p1__7927_SHARP_.json();
})).then((function (data){
var banks = repulse.samples.parse_manifest(data);
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(repulse.samples.registry,cljs.core.merge,banks);

return console.log(["[REPuLse] loaded ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.count(banks))," sample banks from ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(url)].join(''));
})).catch((function (e){
return console.warn(["[REPuLse] manifest load failed: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(url)].join(''),e);
}));
});
/**
 * Load all default sample manifests. Call once on app startup.
 */
repulse.samples.init_BANG_ = (function repulse$samples$init_BANG_(){
var seq__7947 = cljs.core.seq(repulse.samples.DEFAULT_MANIFESTS);
var chunk__7948 = null;
var count__7949 = (0);
var i__7950 = (0);
while(true){
if((i__7950 < count__7949)){
var url = chunk__7948.cljs$core$IIndexed$_nth$arity$2(null,i__7950);
repulse.samples.load_manifest_BANG_(url);


var G__8126 = seq__7947;
var G__8127 = chunk__7948;
var G__8128 = count__7949;
var G__8129 = (i__7950 + (1));
seq__7947 = G__8126;
chunk__7948 = G__8127;
count__7949 = G__8128;
i__7950 = G__8129;
continue;
} else {
var temp__5804__auto__ = cljs.core.seq(seq__7947);
if(temp__5804__auto__){
var seq__7947__$1 = temp__5804__auto__;
if(cljs.core.chunked_seq_QMARK_(seq__7947__$1)){
var c__5525__auto__ = cljs.core.chunk_first(seq__7947__$1);
var G__8131 = cljs.core.chunk_rest(seq__7947__$1);
var G__8132 = c__5525__auto__;
var G__8133 = cljs.core.count(c__5525__auto__);
var G__8134 = (0);
seq__7947 = G__8131;
chunk__7948 = G__8132;
count__7949 = G__8133;
i__7950 = G__8134;
continue;
} else {
var url = cljs.core.first(seq__7947__$1);
repulse.samples.load_manifest_BANG_(url);


var G__8135 = cljs.core.next(seq__7947__$1);
var G__8136 = null;
var G__8137 = (0);
var G__8138 = (0);
seq__7947 = G__8135;
chunk__7948 = G__8136;
count__7949 = G__8137;
i__7950 = G__8138;
continue;
}
} else {
return null;
}
}
break;
}
});
/**
 * Returns true if the named bank is registered.
 */
repulse.samples.has_bank_QMARK_ = (function repulse$samples$has_bank_QMARK_(bank){
return cljs.core.boolean$(cljs.core.seq(cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.samples.registry),cljs.core.name(bank))));
});
/**
 * Resolve a keyword against the active prefix.
 * If a prefix is active and "<prefix>_<kw>" is a registered bank, returns that
 * prefixed keyword. Otherwise returns kw unchanged.
 */
repulse.samples.resolve_keyword = (function repulse$samples$resolve_keyword(kw){
var temp__5802__auto__ = cljs.core.deref(repulse.samples.active_bank_prefix);
if(cljs.core.truth_(temp__5802__auto__)){
var pfx = temp__5802__auto__;
var candidate = cljs.core.keyword.cljs$core$IFn$_invoke$arity$1([cljs.core.str.cljs$core$IFn$_invoke$arity$1(pfx),"_",cljs.core.name(kw)].join(''));
if(repulse.samples.has_bank_QMARK_(candidate)){
return candidate;
} else {
return kw;
}
} else {
return kw;
}
});
/**
 * Get the URL for the nth sample in a bank. Wraps around if n >= count.
 */
repulse.samples.get_url = (function repulse$samples$get_url(bank,n){
var temp__5804__auto__ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.samples.registry),cljs.core.name(bank));
if(cljs.core.truth_(temp__5804__auto__)){
var urls = temp__5804__auto__;
if(cljs.core.seq(urls)){
return cljs.core.nth.cljs$core$IFn$_invoke$arity$2(urls,cljs.core.mod((function (){var x__5087__auto__ = (0);
var y__5088__auto__ = (function (){var or__5002__auto__ = n;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (0);
}
})();
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})(),cljs.core.count(urls)));
} else {
return null;
}
} else {
return null;
}
});
/**
 * Returns a Promise<AudioBuffer> for the given URL, using cache.
 */
repulse.samples.get_buffer_BANG_ = (function repulse$samples$get_buffer_BANG_(url,ac){
var temp__5802__auto__ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.samples.buffer_cache),url);
if(cljs.core.truth_(temp__5802__auto__)){
var buf = temp__5802__auto__;
return Promise.resolve(buf);
} else {
var temp__5802__auto____$1 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.samples.in_flight),url);
if(cljs.core.truth_(temp__5802__auto____$1)){
var p = temp__5802__auto____$1;
return p;
} else {
var p = fetch(url).then((function (p1__7970_SHARP_){
return p1__7970_SHARP_.arrayBuffer();
})).then((function (p1__7971_SHARP_){
return ac.decodeAudioData(p1__7971_SHARP_);
})).then((function (buf){
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.samples.buffer_cache,cljs.core.assoc,url,buf);

cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(repulse.samples.in_flight,cljs.core.dissoc,url);

return buf;
})).catch((function (e){
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(repulse.samples.in_flight,cljs.core.dissoc,url);

return Promise.reject(e);
}));
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.samples.in_flight,cljs.core.assoc,url,p);

return p;
}
}
});
/**
 * Schedule playback of the nth sample from bank at audio time t.
 * amp        — 0.0–1.0 gain (default 1.0)
 * pan        — -1.0–1.0 stereo position (default 0.0)
 * extra      — map with optional keys:
 *                :rate  — playback rate multiplier (default 1.0)
 *                :begin — start offset as fraction 0.0–1.0 (default 0.0)
 *                :end   — end offset as fraction 0.0–1.0 (default 1.0)
 *                :loop  — boolean, loop the sample (default false)
 * dest       — AudioNode to connect to (default: ac.destination)
 * 
 * NOTE: envelope parameters (attack, decay, release) from per-event transformers
 * are NOT applied to samples — they only affect synthesised sounds (note keywords,
 * saw, square, sine, noise). To shorten a sample use the :end key in extra.
 */
repulse.samples.play_BANG_ = (function repulse$samples$play_BANG_(var_args){
var G__7986 = arguments.length;
switch (G__7986) {
case 4:
return repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$4((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),(arguments[(3)]));

break;
case 5:
return repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$5((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),(arguments[(3)]),(arguments[(4)]));

break;
case 6:
return repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$6((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),(arguments[(3)]),(arguments[(4)]),(arguments[(5)]));

break;
case 7:
return repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$7((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),(arguments[(3)]),(arguments[(4)]),(arguments[(5)]),(arguments[(6)]));

break;
case 8:
return repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$8((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),(arguments[(3)]),(arguments[(4)]),(arguments[(5)]),(arguments[(6)]),(arguments[(7)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$4 = (function (ac,t,bank,n){
return repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$8(ac,t,bank,n,1.0,0.0,cljs.core.PersistentArrayMap.EMPTY,null);
}));

(repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$5 = (function (ac,t,bank,n,amp){
return repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$8(ac,t,bank,n,amp,0.0,cljs.core.PersistentArrayMap.EMPTY,null);
}));

(repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$6 = (function (ac,t,bank,n,amp,pan){
return repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$8(ac,t,bank,n,amp,pan,cljs.core.PersistentArrayMap.EMPTY,null);
}));

(repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$7 = (function (ac,t,bank,n,amp,pan,extra){
return repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$8(ac,t,bank,n,amp,pan,extra,null);
}));

(repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$8 = (function (ac,t,bank,n,amp,pan,extra,dest){
var temp__5804__auto__ = repulse.samples.get_url(bank,n);
if(cljs.core.truth_(temp__5804__auto__)){
var url = temp__5804__auto__;
return repulse.samples.get_buffer_BANG_(url,ac).then((function (buf){
var src = ac.createBufferSource();
var gain = ac.createGain();
var panner = ac.createStereoPanner();
var t_SINGLEQUOTE_ = (function (){var x__5087__auto__ = t;
var y__5088__auto__ = ac.currentTime;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
var rate_v = (function (){var or__5002__auto__ = new cljs.core.Keyword(null,"rate","rate",-1428659698).cljs$core$IFn$_invoke$arity$1(extra);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return 1.0;
}
})();
var begin_v = (function (){var or__5002__auto__ = new cljs.core.Keyword(null,"begin","begin",-319034319).cljs$core$IFn$_invoke$arity$1(extra);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return 0.0;
}
})();
var end_v = (function (){var or__5002__auto__ = new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(extra);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return 1.0;
}
})();
var loop_QMARK_ = cljs.core.boolean$(new cljs.core.Keyword(null,"loop","loop",-395552849).cljs$core$IFn$_invoke$arity$1(extra));
var buf_dur = buf.duration;
var offset = (begin_v * buf_dur);
var dur = ((end_v - begin_v) * buf_dur);
var out = (function (){var or__5002__auto__ = dest;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return ac.destination;
}
})();
(src.buffer = buf);

(src.playbackRate.value = rate_v);

if(loop_QMARK_){
(src.loop = true);

(src.loopStart = offset);

(src.loopEnd = (end_v * buf_dur));
} else {
}

gain.gain.setValueAtTime(amp,t_SINGLEQUOTE_);

panner.pan.setValueAtTime(pan,t_SINGLEQUOTE_);

src.connect(gain);

gain.connect(panner);

panner.connect(out);

if(loop_QMARK_){
return src.start(t_SINGLEQUOTE_,offset);
} else {
return src.start(t_SINGLEQUOTE_,offset,dur);
}
})).catch((function (e){
return console.debug("[REPuLse] sample play failed:",cljs.core.name(bank),e);
}));
} else {
return null;
}
}));

(repulse.samples.play_BANG_.cljs$lang$maxFixedArity = 8);

repulse.samples.unwrap_sv = (function repulse$samples$unwrap_sv(x){
if((x instanceof repulse.lisp.reader.SourcedVal)){
return x.v;
} else {
return x;
}
});
/**
 * Parse a REPuLse Lisp (.edn) manifest string into {bank-name [url ...]} map.
 * Keys in the reader output are SourcedVal-wrapped keywords; values are vectors
 * of SourcedVal-wrapped strings. Unwraps both layers.
 */
repulse.samples.parse_lisp_manifest = (function repulse$samples$parse_lisp_manifest(text){
try{var form = repulse.lisp.reader.read_one(text);
if(cljs.core.map_QMARK_(form)){
var raw_map = cljs.core.reduce_kv((function (acc,k,v){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(acc,repulse.samples.unwrap_sv(k),v);
}),cljs.core.PersistentArrayMap.EMPTY,form);
var base = repulse.samples.unwrap_sv(cljs.core.get.cljs$core$IFn$_invoke$arity$3(raw_map,new cljs.core.Keyword(null,"_base","_base",1387474048),""));
var banks = cljs.core.dissoc.cljs$core$IFn$_invoke$arity$2(raw_map,new cljs.core.Keyword(null,"_base","_base",1387474048));
return cljs.core.reduce_kv((function (acc,k,v){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(acc,cljs.core.name(k),cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__8009_SHARP_){
return [cljs.core.str.cljs$core$IFn$_invoke$arity$1(base),cljs.core.str.cljs$core$IFn$_invoke$arity$1(repulse.samples.unwrap_sv(p1__8009_SHARP_))].join('');
}),v));
}),cljs.core.PersistentArrayMap.EMPTY,banks);
} else {
return null;
}
}catch (e8014){var _ = e8014;
return null;
}});
/**
 * Fetch a REPuLse Lisp (.edn) manifest, parse it, and register the banks.
 */
repulse.samples.load_lisp_manifest_BANG_ = (function repulse$samples$load_lisp_manifest_BANG_(url){
return fetch(url).then((function (p1__8017_SHARP_){
return p1__8017_SHARP_.text();
})).then((function (text){
var temp__5802__auto__ = repulse.samples.parse_lisp_manifest(text);
if(cljs.core.truth_(temp__5802__auto__)){
var banks = temp__5802__auto__;
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(repulse.samples.registry,cljs.core.merge,banks);

return console.log(["[REPuLse] loaded ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.count(banks))," banks from ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(url)].join(''));
} else {
return console.warn("[REPuLse] Lisp manifest parse failed:",url);
}
})).catch((function (e){
return console.warn("[REPuLse] Lisp manifest load failed:",url,e);
}));
});
repulse.samples.AUDIO_EXTS = new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 5, ["ogg",null,"flac",null,"mp3",null,"wav",null,"aiff",null], null), null);
repulse.samples.audio_ext_QMARK_ = (function repulse$samples$audio_ext_QMARK_(path){
return cljs.core.contains_QMARK_(repulse.samples.AUDIO_EXTS,clojure.string.lower_case(cljs.core.last(clojure.string.split.cljs$core$IFn$_invoke$arity$2(path,/\./))));
});
/**
 * Discover audio files in a public GitHub repo and register them as sample banks.
 * Groups by immediate parent folder; files in the repo root go under repo-name.
 */
repulse.samples.load_github_BANG_ = (function repulse$samples$load_github_BANG_(owner,repo,branch){
var api_url = ["https://api.github.com/repos/",cljs.core.str.cljs$core$IFn$_invoke$arity$1(owner),"/",cljs.core.str.cljs$core$IFn$_invoke$arity$1(repo),"/git/trees/",cljs.core.str.cljs$core$IFn$_invoke$arity$1(branch),"?recursive=1"].join('');
var raw_base = ["https://raw.githubusercontent.com/",cljs.core.str.cljs$core$IFn$_invoke$arity$1(owner),"/",cljs.core.str.cljs$core$IFn$_invoke$arity$1(repo),"/",cljs.core.str.cljs$core$IFn$_invoke$arity$1(branch),"/"].join('');
return fetch(api_url).then((function (p1__8026_SHARP_){
return p1__8026_SHARP_.json();
})).then((function (data){
var tree = cljs.core.js__GT_clj.cljs$core$IFn$_invoke$arity$variadic(data.tree,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([new cljs.core.Keyword(null,"keywordize-keys","keywordize-keys",1310784252),true], 0));
var blobs = cljs.core.filter.cljs$core$IFn$_invoke$arity$2((function (p1__8027_SHARP_){
return ((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(p1__8027_SHARP_),"blob")) && (repulse.samples.audio_ext_QMARK_(new cljs.core.Keyword(null,"path","path",-188191168).cljs$core$IFn$_invoke$arity$1(p1__8027_SHARP_))));
}),tree);
var grouped = cljs.core.group_by((function (p__8050){
var map__8052 = p__8050;
var map__8052__$1 = cljs.core.__destructure_map(map__8052);
var path = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8052__$1,new cljs.core.Keyword(null,"path","path",-188191168));
var parts = clojure.string.split.cljs$core$IFn$_invoke$arity$2(path,/\//);
if((cljs.core.count(parts) > (1))){
return cljs.core.nth.cljs$core$IFn$_invoke$arity$2(parts,(cljs.core.count(parts) - (2)));
} else {
return repo;
}
}),blobs);
var banks = cljs.core.reduce_kv((function (acc,folder,files){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(acc,folder,cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__8028_SHARP_){
return [raw_base,cljs.core.str.cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"path","path",-188191168).cljs$core$IFn$_invoke$arity$1(p1__8028_SHARP_))].join('');
}),files));
}),cljs.core.PersistentArrayMap.EMPTY,grouped);
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(repulse.samples.registry,cljs.core.merge,banks);

cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(repulse.samples.loaded_sources,cljs.core.conj,new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"github","github",567794498),new cljs.core.Keyword(null,"id","id",-1388402092),[cljs.core.str.cljs$core$IFn$_invoke$arity$1(owner),"/",cljs.core.str.cljs$core$IFn$_invoke$arity$1(repo)].join(''),new cljs.core.Keyword(null,"banks","banks",-1417977624),cljs.core.count(banks)], null));

return console.log(["[REPuLse] loaded ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.count(banks))," banks from github:",cljs.core.str.cljs$core$IFn$_invoke$arity$1(owner),"/",cljs.core.str.cljs$core$IFn$_invoke$arity$1(repo)].join(''));
})).catch((function (e){
return console.warn("[REPuLse] GitHub load failed:",[cljs.core.str.cljs$core$IFn$_invoke$arity$1(owner),"/",cljs.core.str.cljs$core$IFn$_invoke$arity$1(repo)].join(''),e);
}));
});
/**
 * Load samples from url.
 * Dispatch:
 *   'github:owner/repo'         — GitHub tree API, tries main then master
 *   'github:owner/repo/branch'  — GitHub tree API, specific branch
 *   'https://…/samples.edn'     — REPuLse Lisp manifest
 *   anything else               — Strudel-compatible JSON manifest
 */
repulse.samples.load_external_BANG_ = (function repulse$samples$load_external_BANG_(url){
if(clojure.string.starts_with_QMARK_(url,"github:")){
var parts = clojure.string.split.cljs$core$IFn$_invoke$arity$2(cljs.core.subs.cljs$core$IFn$_invoke$arity$2(url,(7)),/\//);
var owner = cljs.core.first(parts);
var repo = cljs.core.second(parts);
var branch = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(parts,(2),null);
if(cljs.core.truth_(branch)){
return repulse.samples.load_github_BANG_(owner,repo,branch);
} else {
return repulse.samples.load_github_BANG_(owner,repo,"main").catch((function (_){
return repulse.samples.load_github_BANG_(owner,repo,"master");
}));
}
} else {
if(clojure.string.ends_with_QMARK_(clojure.string.lower_case(url),".edn")){
return repulse.samples.load_lisp_manifest_BANG_(url);
} else {
return repulse.samples.load_manifest_BANG_(url);

}
}
});
/**
 * Register a single audio URL as a one-sample bank under bank-name (string or keyword).
 */
repulse.samples.register_url_BANG_ = (function repulse$samples$register_url_BANG_(bank_name,url){
return cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.samples.registry,cljs.core.assoc,cljs.core.name(bank_name),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [url], null));
});
/**
 * Returns a sorted list of all registered bank names.
 */
repulse.samples.bank_names = (function repulse$samples$bank_names(){
return cljs.core.sort.cljs$core$IFn$_invoke$arity$1(cljs.core.keys(cljs.core.deref(repulse.samples.registry)));
});
repulse.samples.mfr_of = (function repulse$samples$mfr_of(name){
return cljs.core.second(cljs.core.re_find(/^([A-Z][a-z]+)/,name));
});
/**
 * Returns a human-readable grouped string of all registered sample banks.
 */
repulse.samples.format_banks = (function repulse$samples$format_banks(){
var all = cljs.core.sort.cljs$core$IFn$_invoke$arity$1(cljs.core.keys(cljs.core.deref(repulse.samples.registry)));
if(cljs.core.empty_QMARK_(all)){
return "No sample banks loaded yet.";
} else {
var simple = cljs.core.filterv((function (p1__8081_SHARP_){
return (!(clojure.string.includes_QMARK_(p1__8081_SHARP_,"_")));
}),all);
var compound = cljs.core.filterv((function (p1__8082_SHARP_){
return clojure.string.includes_QMARK_(p1__8082_SHARP_,"_");
}),all);
var by_mfr = cljs.core.group_by((function (p1__8083_SHARP_){
var or__5002__auto__ = repulse.samples.mfr_of(p1__8083_SHARP_);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return "misc";
}
}),compound);
var mfrs = cljs.core.sort.cljs$core$IFn$_invoke$arity$1(cljs.core.keys(by_mfr));
var sections = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentVector.EMPTY);
if(cljs.core.seq(simple)){
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(sections,cljs.core.conj,["general (",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.count(simple)),"):\n  ",clojure.string.join.cljs$core$IFn$_invoke$arity$2("  ",simple)].join(''));
} else {
}

var seq__8090_8256 = cljs.core.seq(mfrs);
var chunk__8091_8257 = null;
var count__8092_8258 = (0);
var i__8093_8259 = (0);
while(true){
if((i__8093_8259 < count__8092_8258)){
var mfr_8263 = chunk__8091_8257.cljs$core$IIndexed$_nth$arity$2(null,i__8093_8259);
var banks_8264 = cljs.core.sort.cljs$core$IFn$_invoke$arity$1(cljs.core.get.cljs$core$IFn$_invoke$arity$2(by_mfr,mfr_8263));
var by_model_8265 = cljs.core.group_by(((function (seq__8090_8256,chunk__8091_8257,count__8092_8258,i__8093_8259,banks_8264,mfr_8263,simple,compound,by_mfr,mfrs,sections,all){
return (function (p1__8084_SHARP_){
return cljs.core.first(clojure.string.split.cljs$core$IFn$_invoke$arity$3(p1__8084_SHARP_,/_/,(2)));
});})(seq__8090_8256,chunk__8091_8257,count__8092_8258,i__8093_8259,banks_8264,mfr_8263,simple,compound,by_mfr,mfrs,sections,all))
,banks_8264);
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(sections,cljs.core.conj,[cljs.core.str.cljs$core$IFn$_invoke$arity$1(mfr_8263)," (",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.count(banks_8264)),"):\n",clojure.string.join.cljs$core$IFn$_invoke$arity$2("\n",cljs.core.map.cljs$core$IFn$_invoke$arity$2(((function (seq__8090_8256,chunk__8091_8257,count__8092_8258,i__8093_8259,banks_8264,by_model_8265,mfr_8263,simple,compound,by_mfr,mfrs,sections,all){
return (function (model){
var insts = cljs.core.sort.cljs$core$IFn$_invoke$arity$1(cljs.core.map.cljs$core$IFn$_invoke$arity$2(((function (seq__8090_8256,chunk__8091_8257,count__8092_8258,i__8093_8259,banks_8264,by_model_8265,mfr_8263,simple,compound,by_mfr,mfrs,sections,all){
return (function (p1__8085_SHARP_){
return cljs.core.second(clojure.string.split.cljs$core$IFn$_invoke$arity$3(p1__8085_SHARP_,/_/,(2)));
});})(seq__8090_8256,chunk__8091_8257,count__8092_8258,i__8093_8259,banks_8264,by_model_8265,mfr_8263,simple,compound,by_mfr,mfrs,sections,all))
,cljs.core.get.cljs$core$IFn$_invoke$arity$2(by_model_8265,model)));
return ["  ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(model),": ",clojure.string.join.cljs$core$IFn$_invoke$arity$2("  ",insts)].join('');
});})(seq__8090_8256,chunk__8091_8257,count__8092_8258,i__8093_8259,banks_8264,by_model_8265,mfr_8263,simple,compound,by_mfr,mfrs,sections,all))
,cljs.core.sort.cljs$core$IFn$_invoke$arity$1(cljs.core.keys(by_model_8265))))].join(''));


var G__8271 = seq__8090_8256;
var G__8272 = chunk__8091_8257;
var G__8273 = count__8092_8258;
var G__8274 = (i__8093_8259 + (1));
seq__8090_8256 = G__8271;
chunk__8091_8257 = G__8272;
count__8092_8258 = G__8273;
i__8093_8259 = G__8274;
continue;
} else {
var temp__5804__auto___8276 = cljs.core.seq(seq__8090_8256);
if(temp__5804__auto___8276){
var seq__8090_8277__$1 = temp__5804__auto___8276;
if(cljs.core.chunked_seq_QMARK_(seq__8090_8277__$1)){
var c__5525__auto___8278 = cljs.core.chunk_first(seq__8090_8277__$1);
var G__8279 = cljs.core.chunk_rest(seq__8090_8277__$1);
var G__8280 = c__5525__auto___8278;
var G__8281 = cljs.core.count(c__5525__auto___8278);
var G__8282 = (0);
seq__8090_8256 = G__8279;
chunk__8091_8257 = G__8280;
count__8092_8258 = G__8281;
i__8093_8259 = G__8282;
continue;
} else {
var mfr_8283 = cljs.core.first(seq__8090_8277__$1);
var banks_8284 = cljs.core.sort.cljs$core$IFn$_invoke$arity$1(cljs.core.get.cljs$core$IFn$_invoke$arity$2(by_mfr,mfr_8283));
var by_model_8285 = cljs.core.group_by(((function (seq__8090_8256,chunk__8091_8257,count__8092_8258,i__8093_8259,banks_8284,mfr_8283,seq__8090_8277__$1,temp__5804__auto___8276,simple,compound,by_mfr,mfrs,sections,all){
return (function (p1__8084_SHARP_){
return cljs.core.first(clojure.string.split.cljs$core$IFn$_invoke$arity$3(p1__8084_SHARP_,/_/,(2)));
});})(seq__8090_8256,chunk__8091_8257,count__8092_8258,i__8093_8259,banks_8284,mfr_8283,seq__8090_8277__$1,temp__5804__auto___8276,simple,compound,by_mfr,mfrs,sections,all))
,banks_8284);
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(sections,cljs.core.conj,[cljs.core.str.cljs$core$IFn$_invoke$arity$1(mfr_8283)," (",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.count(banks_8284)),"):\n",clojure.string.join.cljs$core$IFn$_invoke$arity$2("\n",cljs.core.map.cljs$core$IFn$_invoke$arity$2(((function (seq__8090_8256,chunk__8091_8257,count__8092_8258,i__8093_8259,banks_8284,by_model_8285,mfr_8283,seq__8090_8277__$1,temp__5804__auto___8276,simple,compound,by_mfr,mfrs,sections,all){
return (function (model){
var insts = cljs.core.sort.cljs$core$IFn$_invoke$arity$1(cljs.core.map.cljs$core$IFn$_invoke$arity$2(((function (seq__8090_8256,chunk__8091_8257,count__8092_8258,i__8093_8259,banks_8284,by_model_8285,mfr_8283,seq__8090_8277__$1,temp__5804__auto___8276,simple,compound,by_mfr,mfrs,sections,all){
return (function (p1__8085_SHARP_){
return cljs.core.second(clojure.string.split.cljs$core$IFn$_invoke$arity$3(p1__8085_SHARP_,/_/,(2)));
});})(seq__8090_8256,chunk__8091_8257,count__8092_8258,i__8093_8259,banks_8284,by_model_8285,mfr_8283,seq__8090_8277__$1,temp__5804__auto___8276,simple,compound,by_mfr,mfrs,sections,all))
,cljs.core.get.cljs$core$IFn$_invoke$arity$2(by_model_8285,model)));
return ["  ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(model),": ",clojure.string.join.cljs$core$IFn$_invoke$arity$2("  ",insts)].join('');
});})(seq__8090_8256,chunk__8091_8257,count__8092_8258,i__8093_8259,banks_8284,by_model_8285,mfr_8283,seq__8090_8277__$1,temp__5804__auto___8276,simple,compound,by_mfr,mfrs,sections,all))
,cljs.core.sort.cljs$core$IFn$_invoke$arity$1(cljs.core.keys(by_model_8285))))].join(''));


var G__8289 = cljs.core.next(seq__8090_8277__$1);
var G__8290 = null;
var G__8291 = (0);
var G__8292 = (0);
seq__8090_8256 = G__8289;
chunk__8091_8257 = G__8290;
count__8092_8258 = G__8291;
i__8093_8259 = G__8292;
continue;
}
} else {
}
}
break;
}

return [cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.count(all))," sample banks\n\n",clojure.string.join.cljs$core$IFn$_invoke$arity$2("\n\n",cljs.core.deref(sections))].join('');
}
});

//# sourceMappingURL=repulse.samples.js.map
