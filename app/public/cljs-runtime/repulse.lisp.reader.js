goog.provide('repulse.lisp.reader');

/**
* @constructor
 * @implements {cljs.core.IRecord}
 * @implements {cljs.core.IKVReduce}
 * @implements {cljs.core.IEquiv}
 * @implements {cljs.core.IHash}
 * @implements {cljs.core.ICollection}
 * @implements {cljs.core.ICounted}
 * @implements {cljs.core.ISeqable}
 * @implements {cljs.core.IMeta}
 * @implements {cljs.core.ICloneable}
 * @implements {cljs.core.IPrintWithWriter}
 * @implements {cljs.core.IIterable}
 * @implements {cljs.core.IWithMeta}
 * @implements {cljs.core.IAssociative}
 * @implements {cljs.core.IMap}
 * @implements {cljs.core.ILookup}
*/
repulse.lisp.reader.SourcedVal = (function (v,source,__meta,__extmap,__hash){
this.v = v;
this.source = source;
this.__meta = __meta;
this.__extmap = __extmap;
this.__hash = __hash;
this.cljs$lang$protocol_mask$partition0$ = 2230716170;
this.cljs$lang$protocol_mask$partition1$ = 139264;
});
(repulse.lisp.reader.SourcedVal.prototype.cljs$core$ILookup$_lookup$arity$2 = (function (this__5300__auto__,k__5301__auto__){
var self__ = this;
var this__5300__auto____$1 = this;
return this__5300__auto____$1.cljs$core$ILookup$_lookup$arity$3(null,k__5301__auto__,null);
}));

(repulse.lisp.reader.SourcedVal.prototype.cljs$core$ILookup$_lookup$arity$3 = (function (this__5302__auto__,k7543,else__5303__auto__){
var self__ = this;
var this__5302__auto____$1 = this;
var G__7567 = k7543;
var G__7567__$1 = (((G__7567 instanceof cljs.core.Keyword))?G__7567.fqn:null);
switch (G__7567__$1) {
case "v":
return self__.v;

break;
case "source":
return self__.source;

break;
default:
return cljs.core.get.cljs$core$IFn$_invoke$arity$3(self__.__extmap,k7543,else__5303__auto__);

}
}));

(repulse.lisp.reader.SourcedVal.prototype.cljs$core$IKVReduce$_kv_reduce$arity$3 = (function (this__5320__auto__,f__5321__auto__,init__5322__auto__){
var self__ = this;
var this__5320__auto____$1 = this;
return cljs.core.reduce.cljs$core$IFn$_invoke$arity$3((function (ret__5323__auto__,p__7576){
var vec__7577 = p__7576;
var k__5324__auto__ = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7577,(0),null);
var v__5325__auto__ = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7577,(1),null);
return (f__5321__auto__.cljs$core$IFn$_invoke$arity$3 ? f__5321__auto__.cljs$core$IFn$_invoke$arity$3(ret__5323__auto__,k__5324__auto__,v__5325__auto__) : f__5321__auto__.call(null,ret__5323__auto__,k__5324__auto__,v__5325__auto__));
}),init__5322__auto__,this__5320__auto____$1);
}));

(repulse.lisp.reader.SourcedVal.prototype.cljs$core$IPrintWithWriter$_pr_writer$arity$3 = (function (this__5315__auto__,writer__5316__auto__,opts__5317__auto__){
var self__ = this;
var this__5315__auto____$1 = this;
var pr_pair__5318__auto__ = (function (keyval__5319__auto__){
return cljs.core.pr_sequential_writer(writer__5316__auto__,cljs.core.pr_writer,""," ","",opts__5317__auto__,keyval__5319__auto__);
});
return cljs.core.pr_sequential_writer(writer__5316__auto__,pr_pair__5318__auto__,"#repulse.lisp.reader.SourcedVal{",", ","}",opts__5317__auto__,cljs.core.concat.cljs$core$IFn$_invoke$arity$2(new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(new cljs.core.PersistentVector(null,2,(5),cljs.core.PersistentVector.EMPTY_NODE,[new cljs.core.Keyword(null,"v","v",21465059),self__.v],null)),(new cljs.core.PersistentVector(null,2,(5),cljs.core.PersistentVector.EMPTY_NODE,[new cljs.core.Keyword(null,"source","source",-433931539),self__.source],null))], null),self__.__extmap));
}));

(repulse.lisp.reader.SourcedVal.prototype.cljs$core$IIterable$_iterator$arity$1 = (function (G__7542){
var self__ = this;
var G__7542__$1 = this;
return (new cljs.core.RecordIter((0),G__7542__$1,2,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"v","v",21465059),new cljs.core.Keyword(null,"source","source",-433931539)], null),(cljs.core.truth_(self__.__extmap)?cljs.core._iterator(self__.__extmap):cljs.core.nil_iter())));
}));

(repulse.lisp.reader.SourcedVal.prototype.cljs$core$IMeta$_meta$arity$1 = (function (this__5298__auto__){
var self__ = this;
var this__5298__auto____$1 = this;
return self__.__meta;
}));

(repulse.lisp.reader.SourcedVal.prototype.cljs$core$ICloneable$_clone$arity$1 = (function (this__5295__auto__){
var self__ = this;
var this__5295__auto____$1 = this;
return (new repulse.lisp.reader.SourcedVal(self__.v,self__.source,self__.__meta,self__.__extmap,self__.__hash));
}));

(repulse.lisp.reader.SourcedVal.prototype.cljs$core$ICounted$_count$arity$1 = (function (this__5304__auto__){
var self__ = this;
var this__5304__auto____$1 = this;
return (2 + cljs.core.count(self__.__extmap));
}));

(repulse.lisp.reader.SourcedVal.prototype.cljs$core$IHash$_hash$arity$1 = (function (this__5296__auto__){
var self__ = this;
var this__5296__auto____$1 = this;
var h__5111__auto__ = self__.__hash;
if((!((h__5111__auto__ == null)))){
return h__5111__auto__;
} else {
var h__5111__auto____$1 = (function (coll__5297__auto__){
return (-508043845 ^ cljs.core.hash_unordered_coll(coll__5297__auto__));
})(this__5296__auto____$1);
(self__.__hash = h__5111__auto____$1);

return h__5111__auto____$1;
}
}));

(repulse.lisp.reader.SourcedVal.prototype.cljs$core$IEquiv$_equiv$arity$2 = (function (this7544,other7545){
var self__ = this;
var this7544__$1 = this;
return (((!((other7545 == null)))) && ((((this7544__$1.constructor === other7545.constructor)) && (((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(this7544__$1.v,other7545.v)) && (((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(this7544__$1.source,other7545.source)) && (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(this7544__$1.__extmap,other7545.__extmap)))))))));
}));

(repulse.lisp.reader.SourcedVal.prototype.cljs$core$IMap$_dissoc$arity$2 = (function (this__5310__auto__,k__5311__auto__){
var self__ = this;
var this__5310__auto____$1 = this;
if(cljs.core.contains_QMARK_(new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"v","v",21465059),null,new cljs.core.Keyword(null,"source","source",-433931539),null], null), null),k__5311__auto__)){
return cljs.core.dissoc.cljs$core$IFn$_invoke$arity$2(cljs.core._with_meta(cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.PersistentArrayMap.EMPTY,this__5310__auto____$1),self__.__meta),k__5311__auto__);
} else {
return (new repulse.lisp.reader.SourcedVal(self__.v,self__.source,self__.__meta,cljs.core.not_empty(cljs.core.dissoc.cljs$core$IFn$_invoke$arity$2(self__.__extmap,k__5311__auto__)),null));
}
}));

(repulse.lisp.reader.SourcedVal.prototype.cljs$core$IAssociative$_contains_key_QMARK_$arity$2 = (function (this__5307__auto__,k7543){
var self__ = this;
var this__5307__auto____$1 = this;
var G__7621 = k7543;
var G__7621__$1 = (((G__7621 instanceof cljs.core.Keyword))?G__7621.fqn:null);
switch (G__7621__$1) {
case "v":
case "source":
return true;

break;
default:
return cljs.core.contains_QMARK_(self__.__extmap,k7543);

}
}));

(repulse.lisp.reader.SourcedVal.prototype.cljs$core$IAssociative$_assoc$arity$3 = (function (this__5308__auto__,k__5309__auto__,G__7542){
var self__ = this;
var this__5308__auto____$1 = this;
var pred__7627 = cljs.core.keyword_identical_QMARK_;
var expr__7628 = k__5309__auto__;
if(cljs.core.truth_((pred__7627.cljs$core$IFn$_invoke$arity$2 ? pred__7627.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"v","v",21465059),expr__7628) : pred__7627.call(null,new cljs.core.Keyword(null,"v","v",21465059),expr__7628)))){
return (new repulse.lisp.reader.SourcedVal(G__7542,self__.source,self__.__meta,self__.__extmap,null));
} else {
if(cljs.core.truth_((pred__7627.cljs$core$IFn$_invoke$arity$2 ? pred__7627.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"source","source",-433931539),expr__7628) : pred__7627.call(null,new cljs.core.Keyword(null,"source","source",-433931539),expr__7628)))){
return (new repulse.lisp.reader.SourcedVal(self__.v,G__7542,self__.__meta,self__.__extmap,null));
} else {
return (new repulse.lisp.reader.SourcedVal(self__.v,self__.source,self__.__meta,cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(self__.__extmap,k__5309__auto__,G__7542),null));
}
}
}));

(repulse.lisp.reader.SourcedVal.prototype.cljs$core$ISeqable$_seq$arity$1 = (function (this__5313__auto__){
var self__ = this;
var this__5313__auto____$1 = this;
return cljs.core.seq(cljs.core.concat.cljs$core$IFn$_invoke$arity$2(new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(new cljs.core.MapEntry(new cljs.core.Keyword(null,"v","v",21465059),self__.v,null)),(new cljs.core.MapEntry(new cljs.core.Keyword(null,"source","source",-433931539),self__.source,null))], null),self__.__extmap));
}));

(repulse.lisp.reader.SourcedVal.prototype.cljs$core$IWithMeta$_with_meta$arity$2 = (function (this__5299__auto__,G__7542){
var self__ = this;
var this__5299__auto____$1 = this;
return (new repulse.lisp.reader.SourcedVal(self__.v,self__.source,G__7542,self__.__extmap,self__.__hash));
}));

(repulse.lisp.reader.SourcedVal.prototype.cljs$core$ICollection$_conj$arity$2 = (function (this__5305__auto__,entry__5306__auto__){
var self__ = this;
var this__5305__auto____$1 = this;
if(cljs.core.vector_QMARK_(entry__5306__auto__)){
return this__5305__auto____$1.cljs$core$IAssociative$_assoc$arity$3(null,cljs.core._nth(entry__5306__auto__,(0)),cljs.core._nth(entry__5306__auto__,(1)));
} else {
return cljs.core.reduce.cljs$core$IFn$_invoke$arity$3(cljs.core._conj,this__5305__auto____$1,entry__5306__auto__);
}
}));

(repulse.lisp.reader.SourcedVal.getBasis = (function (){
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"v","v",1661996586,null),new cljs.core.Symbol(null,"source","source",1206599988,null)], null);
}));

(repulse.lisp.reader.SourcedVal.cljs$lang$type = true);

(repulse.lisp.reader.SourcedVal.cljs$lang$ctorPrSeq = (function (this__5346__auto__){
return (new cljs.core.List(null,"repulse.lisp.reader/SourcedVal",null,(1),null));
}));

(repulse.lisp.reader.SourcedVal.cljs$lang$ctorPrWriter = (function (this__5346__auto__,writer__5347__auto__){
return cljs.core._write(writer__5347__auto__,"repulse.lisp.reader/SourcedVal");
}));

/**
 * Positional factory function for repulse.lisp.reader/SourcedVal.
 */
repulse.lisp.reader.__GT_SourcedVal = (function repulse$lisp$reader$__GT_SourcedVal(v,source){
return (new repulse.lisp.reader.SourcedVal(v,source,null,null,null));
});

/**
 * Factory function for repulse.lisp.reader/SourcedVal, taking a map of keywords to field values.
 */
repulse.lisp.reader.map__GT_SourcedVal = (function repulse$lisp$reader$map__GT_SourcedVal(G__7550){
var extmap__5342__auto__ = (function (){var G__7683 = cljs.core.dissoc.cljs$core$IFn$_invoke$arity$variadic(G__7550,new cljs.core.Keyword(null,"v","v",21465059),cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([new cljs.core.Keyword(null,"source","source",-433931539)], 0));
if(cljs.core.record_QMARK_(G__7550)){
return cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.PersistentArrayMap.EMPTY,G__7683);
} else {
return G__7683;
}
})();
return (new repulse.lisp.reader.SourcedVal(new cljs.core.Keyword(null,"v","v",21465059).cljs$core$IFn$_invoke$arity$1(G__7550),new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(G__7550),null,cljs.core.not_empty(extmap__5342__auto__),null));
});

repulse.lisp.reader.whitespace_QMARK_ = (function repulse$lisp$reader$whitespace_QMARK_(ch){
return cljs.core.contains_QMARK_(new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 5, [" ",null,"\t",null,"\n",null,",",null,"\r",null], null), null),ch);
});
repulse.lisp.reader.digit_QMARK_ = (function repulse$lisp$reader$digit_QMARK_(ch){
return cljs.core.boolean$(cljs.core.re_matches(/[0-9]/,cljs.core.str.cljs$core$IFn$_invoke$arity$1(ch)));
});
repulse.lisp.reader.sym_char_QMARK_ = (function repulse$lisp$reader$sym_char_QMARK_(ch){
return cljs.core.boolean$(cljs.core.re_matches(/[a-zA-Z0-9\-_+*\/=<>!?.~]/,cljs.core.str.cljs$core$IFn$_invoke$arity$1(ch)));
});
repulse.lisp.reader.skip_ws_comments = (function repulse$lisp$reader$skip_ws_comments(p__7697){
var map__7698 = p__7697;
var map__7698__$1 = cljs.core.__destructure_map(map__7698);
var src = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7698__$1,new cljs.core.Keyword(null,"src","src",-1651076051));
var pos = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7698__$1,new cljs.core.Keyword(null,"pos","pos",-864607220));
while(true){
var ch = (((cljs.core.deref(pos) < cljs.core.count(src)))?cljs.core.nth.cljs$core$IFn$_invoke$arity$2(src,cljs.core.deref(pos)):null);
if(repulse.lisp.reader.whitespace_QMARK_(ch)){
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$2(pos,cljs.core.inc);

continue;
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(";",ch)){
while(true){
var c_8008 = (((cljs.core.deref(pos) < cljs.core.count(src)))?cljs.core.nth.cljs$core$IFn$_invoke$arity$2(src,cljs.core.deref(pos)):null);
if(cljs.core.truth_((function (){var and__5000__auto__ = c_8008;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core.not_EQ_.cljs$core$IFn$_invoke$arity$2("\n",c_8008);
} else {
return and__5000__auto__;
}
})())){
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$2(pos,cljs.core.inc);

continue;
} else {
}
break;
}

continue;
} else {
return null;
}
}
break;
}
});
repulse.lisp.reader.peek_char = (function repulse$lisp$reader$peek_char(p__7704){
var map__7705 = p__7704;
var map__7705__$1 = cljs.core.__destructure_map(map__7705);
var src = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7705__$1,new cljs.core.Keyword(null,"src","src",-1651076051));
var pos = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7705__$1,new cljs.core.Keyword(null,"pos","pos",-864607220));
if((cljs.core.deref(pos) < cljs.core.count(src))){
return cljs.core.nth.cljs$core$IFn$_invoke$arity$2(src,cljs.core.deref(pos));
} else {
return null;
}
});
repulse.lisp.reader.advance = (function repulse$lisp$reader$advance(p__7706){
var map__7708 = p__7706;
var map__7708__$1 = cljs.core.__destructure_map(map__7708);
var src = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7708__$1,new cljs.core.Keyword(null,"src","src",-1651076051));
var pos = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7708__$1,new cljs.core.Keyword(null,"pos","pos",-864607220));
var ch = cljs.core.nth.cljs$core$IFn$_invoke$arity$2(src,cljs.core.deref(pos));
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$2(pos,cljs.core.inc);

return ch;
});
repulse.lisp.reader.read_string_STAR_ = (function repulse$lisp$reader$read_string_STAR_(r){
var open_pos = cljs.core.deref(new cljs.core.Keyword(null,"pos","pos",-864607220).cljs$core$IFn$_invoke$arity$1(r));
repulse.lisp.reader.advance(r);

var acc = cljs.core.PersistentVector.EMPTY;
while(true){
var ch = repulse.lisp.reader.peek_char(r);
if((ch == null)){
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2("Unterminated string \u2014 missing closing \"",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"read-error","read-error",1254709471),new cljs.core.Keyword(null,"from","from",1815293044),open_pos,new cljs.core.Keyword(null,"to","to",192099007),(open_pos + (1))], null));
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("\"",ch)){
repulse.lisp.reader.advance(r);

return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,acc);
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("\\",ch)){
repulse.lisp.reader.advance(r);

var e = repulse.lisp.reader.advance(r);
var G__8029 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(acc,(function (){var G__7717 = e;
switch (G__7717) {
case "n":
return "\n";

break;
case "t":
return "\t";

break;
case "\\":
return "\\";

break;
case "\"":
return "\"";

break;
default:
return e;

}
})());
acc = G__8029;
continue;
} else {
var G__8033 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(acc,repulse.lisp.reader.advance(r));
acc = G__8033;
continue;

}
}
}
break;
}
});
/**
 * Read a numeric literal. Returns:
 * - integer or float for plain numbers
 * - [numerator denominator] for N/D rational literals (e.g. 1/4)
 * - (list (symbol "bpm") n) for NNNbpm suffix literals (e.g. 120bpm)
 */
repulse.lisp.reader.read_number = (function repulse$lisp$reader$read_number(r){
var acc = cljs.core.PersistentVector.EMPTY;
while(true){
var ch = repulse.lisp.reader.peek_char(r);
if(cljs.core.truth_((function (){var and__5000__auto__ = ch;
if(cljs.core.truth_(and__5000__auto__)){
return ((repulse.lisp.reader.digit_QMARK_(ch)) || (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(".",ch)));
} else {
return and__5000__auto__;
}
})())){
var G__8065 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(acc,repulse.lisp.reader.advance(r));
acc = G__8065;
continue;
} else {
var s = cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,acc);
var n = (cljs.core.truth_(cljs.core.re_find(/\./,s))?parseFloat(s):parseInt(s,(10)));
var next_ch = repulse.lisp.reader.peek_char(r);
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("/",next_ch)){
repulse.lisp.reader.advance(r);

var denom_acc = (function (){var dacc = cljs.core.PersistentVector.EMPTY;
while(true){
var c = repulse.lisp.reader.peek_char(r);
if(cljs.core.truth_((function (){var and__5000__auto__ = c;
if(cljs.core.truth_(and__5000__auto__)){
return repulse.lisp.reader.digit_QMARK_(c);
} else {
return and__5000__auto__;
}
})())){
var G__8069 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(dacc,repulse.lisp.reader.advance(r));
dacc = G__8069;
continue;
} else {
return dacc;
}
break;
}
})();
if(cljs.core.empty_QMARK_(denom_acc)){
return n;
} else {
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [n,parseInt(cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,denom_acc),(10))], null);
}
} else {
if(cljs.core.truth_((function (){var and__5000__auto__ = next_ch;
if(cljs.core.truth_(and__5000__auto__)){
var remaining = cljs.core.subs.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"src","src",-1651076051).cljs$core$IFn$_invoke$arity$1(r),cljs.core.deref(new cljs.core.Keyword(null,"pos","pos",-864607220).cljs$core$IFn$_invoke$arity$1(r)));
return remaining.startsWith("bpm");
} else {
return and__5000__auto__;
}
})())){
var n__5593__auto___8078 = (3);
var __8079 = (0);
while(true){
if((__8079 < n__5593__auto___8078)){
repulse.lisp.reader.advance(r);

var G__8080 = (__8079 + (1));
__8079 = G__8080;
continue;
} else {
}
break;
}

return (new cljs.core.List(null,cljs.core.symbol.cljs$core$IFn$_invoke$arity$1("bpm"),(new cljs.core.List(null,n,null,(1),null)),(2),null));
} else {
return n;

}
}
}
break;
}
});
repulse.lisp.reader.read_keyword = (function repulse$lisp$reader$read_keyword(r){
repulse.lisp.reader.advance(r);

var acc = cljs.core.PersistentVector.EMPTY;
while(true){
var ch = repulse.lisp.reader.peek_char(r);
if(cljs.core.truth_((function (){var and__5000__auto__ = ch;
if(cljs.core.truth_(and__5000__auto__)){
return repulse.lisp.reader.sym_char_QMARK_(ch);
} else {
return and__5000__auto__;
}
})())){
var G__8094 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(acc,repulse.lisp.reader.advance(r));
acc = G__8094;
continue;
} else {
return cljs.core.keyword.cljs$core$IFn$_invoke$arity$1(cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,acc));
}
break;
}
});
repulse.lisp.reader.read_symbol = (function repulse$lisp$reader$read_symbol(r){
var acc = cljs.core.PersistentVector.EMPTY;
while(true){
var ch = repulse.lisp.reader.peek_char(r);
if(cljs.core.truth_((function (){var and__5000__auto__ = ch;
if(cljs.core.truth_(and__5000__auto__)){
return repulse.lisp.reader.sym_char_QMARK_(ch);
} else {
return and__5000__auto__;
}
})())){
var G__8100 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(acc,repulse.lisp.reader.advance(r));
acc = G__8100;
continue;
} else {
var s = cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,acc);
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(s,"true")){
return true;
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(s,"false")){
return false;
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(s,"nil")){
return null;
} else {
return cljs.core.symbol.cljs$core$IFn$_invoke$arity$1(s);

}
}
}
}
break;
}
});
repulse.lisp.reader.read_list = (function repulse$lisp$reader$read_list(r){
var open_pos = cljs.core.deref(new cljs.core.Keyword(null,"pos","pos",-864607220).cljs$core$IFn$_invoke$arity$1(r));
repulse.lisp.reader.advance(r);

var forms = cljs.core.PersistentVector.EMPTY;
while(true){
repulse.lisp.reader.skip_ws_comments(r);

var ch = repulse.lisp.reader.peek_char(r);
if((ch == null)){
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2("Unterminated list \u2014 missing closing )",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"read-error","read-error",1254709471),new cljs.core.Keyword(null,"from","from",1815293044),open_pos,new cljs.core.Keyword(null,"to","to",192099007),(open_pos + (1))], null));
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(")",ch)){
repulse.lisp.reader.advance(r);

return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.list,forms);
} else {
var G__8105 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(forms,(repulse.lisp.reader.read_form.cljs$core$IFn$_invoke$arity$1 ? repulse.lisp.reader.read_form.cljs$core$IFn$_invoke$arity$1(r) : repulse.lisp.reader.read_form.call(null,r)));
forms = G__8105;
continue;

}
}
break;
}
});
repulse.lisp.reader.read_vector = (function repulse$lisp$reader$read_vector(r){
var open_pos = cljs.core.deref(new cljs.core.Keyword(null,"pos","pos",-864607220).cljs$core$IFn$_invoke$arity$1(r));
repulse.lisp.reader.advance(r);

var forms = cljs.core.PersistentVector.EMPTY;
while(true){
repulse.lisp.reader.skip_ws_comments(r);

var ch = repulse.lisp.reader.peek_char(r);
if((ch == null)){
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2("Unterminated vector \u2014 missing closing ]",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"read-error","read-error",1254709471),new cljs.core.Keyword(null,"from","from",1815293044),open_pos,new cljs.core.Keyword(null,"to","to",192099007),(open_pos + (1))], null));
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("]",ch)){
repulse.lisp.reader.advance(r);

return forms;
} else {
var G__8111 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(forms,(repulse.lisp.reader.read_form.cljs$core$IFn$_invoke$arity$1 ? repulse.lisp.reader.read_form.cljs$core$IFn$_invoke$arity$1(r) : repulse.lisp.reader.read_form.call(null,r)));
forms = G__8111;
continue;

}
}
break;
}
});
repulse.lisp.reader.read_map = (function repulse$lisp$reader$read_map(r){
var open_pos = cljs.core.deref(new cljs.core.Keyword(null,"pos","pos",-864607220).cljs$core$IFn$_invoke$arity$1(r));
repulse.lisp.reader.advance(r);

var m = cljs.core.PersistentArrayMap.EMPTY;
while(true){
repulse.lisp.reader.skip_ws_comments(r);

var ch = repulse.lisp.reader.peek_char(r);
if((ch == null)){
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2("Unterminated map \u2014 missing closing }",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"read-error","read-error",1254709471),new cljs.core.Keyword(null,"from","from",1815293044),open_pos,new cljs.core.Keyword(null,"to","to",192099007),(open_pos + (1))], null));
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("}",ch)){
repulse.lisp.reader.advance(r);

return m;
} else {
var k = (repulse.lisp.reader.read_form.cljs$core$IFn$_invoke$arity$1 ? repulse.lisp.reader.read_form.cljs$core$IFn$_invoke$arity$1(r) : repulse.lisp.reader.read_form.call(null,r));
var _ = repulse.lisp.reader.skip_ws_comments(r);
var v = (repulse.lisp.reader.read_form.cljs$core$IFn$_invoke$arity$1 ? repulse.lisp.reader.read_form.cljs$core$IFn$_invoke$arity$1(r) : repulse.lisp.reader.read_form.call(null,r));
var G__8114 = cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(m,k,v);
m = G__8114;
continue;

}
}
break;
}
});
repulse.lisp.reader.read_form_STAR_ = (function repulse$lisp$reader$read_form_STAR_(r){
var ch = repulse.lisp.reader.peek_char(r);
if((ch == null)){
return new cljs.core.Keyword("repulse.lisp.reader","eof","repulse.lisp.reader/eof",-1146864404);
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("\"",ch)){
return repulse.lisp.reader.read_string_STAR_(r);
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(":",ch)){
return repulse.lisp.reader.read_keyword(r);
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("(",ch)){
return repulse.lisp.reader.read_list(r);
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("[",ch)){
return repulse.lisp.reader.read_vector(r);
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("{",ch)){
return repulse.lisp.reader.read_map(r);
} else {
if(repulse.lisp.reader.digit_QMARK_(ch)){
return repulse.lisp.reader.read_number(r);
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("`",ch)){
repulse.lisp.reader.advance(r);

var inner = (repulse.lisp.reader.read_form.cljs$core$IFn$_invoke$arity$1 ? repulse.lisp.reader.read_form.cljs$core$IFn$_invoke$arity$1(r) : repulse.lisp.reader.read_form.call(null,r));
return (new cljs.core.List(null,cljs.core.symbol.cljs$core$IFn$_invoke$arity$1("quasiquote"),(new cljs.core.List(null,inner,null,(1),null)),(2),null));
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("~",ch)){
repulse.lisp.reader.advance(r);

var next_ch = repulse.lisp.reader.peek_char(r);
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("@",next_ch)){
repulse.lisp.reader.advance(r);

var inner = (repulse.lisp.reader.read_form.cljs$core$IFn$_invoke$arity$1 ? repulse.lisp.reader.read_form.cljs$core$IFn$_invoke$arity$1(r) : repulse.lisp.reader.read_form.call(null,r));
return (new cljs.core.List(null,cljs.core.symbol.cljs$core$IFn$_invoke$arity$1("splice-unquote"),(new cljs.core.List(null,inner,null,(1),null)),(2),null));
} else {
if((((next_ch == null)) || (((repulse.lisp.reader.whitespace_QMARK_(next_ch)) || (((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("\"",next_ch)) || (((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(")",next_ch)) || (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("]",next_ch)))))))))){
return cljs.core.symbol.cljs$core$IFn$_invoke$arity$1("~");
} else {
var inner = (repulse.lisp.reader.read_form.cljs$core$IFn$_invoke$arity$1 ? repulse.lisp.reader.read_form.cljs$core$IFn$_invoke$arity$1(r) : repulse.lisp.reader.read_form.call(null,r));
return (new cljs.core.List(null,cljs.core.symbol.cljs$core$IFn$_invoke$arity$1("unquote"),(new cljs.core.List(null,inner,null,(1),null)),(2),null));

}
}
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2("-",ch)){
repulse.lisp.reader.advance(r);

if(repulse.lisp.reader.digit_QMARK_(repulse.lisp.reader.peek_char(r))){
var n = repulse.lisp.reader.read_number(r);
if(cljs.core.vector_QMARK_(n)){
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(- cljs.core.first(n)),cljs.core.second(n)], null);
} else {
if(cljs.core.seq_QMARK_(n)){
return n;
} else {
return (- n);

}
}
} else {
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"pos","pos",-864607220).cljs$core$IFn$_invoke$arity$1(r),cljs.core.dec);

return repulse.lisp.reader.read_symbol(r);
}
} else {
if(repulse.lisp.reader.sym_char_QMARK_(ch)){
return repulse.lisp.reader.read_symbol(r);
} else {
var p = cljs.core.deref(new cljs.core.Keyword(null,"pos","pos",-864607220).cljs$core$IFn$_invoke$arity$1(r));
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2(["Unexpected character: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(ch)].join(''),new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"read-error","read-error",1254709471),new cljs.core.Keyword(null,"from","from",1815293044),p,new cljs.core.Keyword(null,"to","to",192099007),(p + (1))], null));

}
}
}
}
}
}
}
}
}
}
}
});
repulse.lisp.reader.read_form = (function repulse$lisp$reader$read_form(r){
repulse.lisp.reader.skip_ws_comments(r);

var from = cljs.core.deref(new cljs.core.Keyword(null,"pos","pos",-864607220).cljs$core$IFn$_invoke$arity$1(r));
var result = repulse.lisp.reader.read_form_STAR_(r);
var to = cljs.core.deref(new cljs.core.Keyword(null,"pos","pos",-864607220).cljs$core$IFn$_invoke$arity$1(r));
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(result,new cljs.core.Keyword("repulse.lisp.reader","eof","repulse.lisp.reader/eof",-1146864404))){
return new cljs.core.Keyword("repulse.lisp.reader","eof","repulse.lisp.reader/eof",-1146864404);
} else {
if(((cljs.core.seq_QMARK_(result)) || (((cljs.core.vector_QMARK_(result)) || (((cljs.core.map_QMARK_(result)) || ((result instanceof cljs.core.Symbol)))))))){
return cljs.core.with_meta(result,new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"source","source",-433931539),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"from","from",1815293044),from,new cljs.core.Keyword(null,"to","to",192099007),to], null)], null));
} else {
return repulse.lisp.reader.__GT_SourcedVal(result,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"from","from",1815293044),from,new cljs.core.Keyword(null,"to","to",192099007),to], null));

}
}
});
repulse.lisp.reader.read_all = (function repulse$lisp$reader$read_all(src){
var r = new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"src","src",-1651076051),src,new cljs.core.Keyword(null,"pos","pos",-864607220),cljs.core.atom.cljs$core$IFn$_invoke$arity$1((0))], null);
var forms = cljs.core.PersistentVector.EMPTY;
while(true){
repulse.lisp.reader.skip_ws_comments(r);

if((repulse.lisp.reader.peek_char(r) == null)){
return forms;
} else {
var f = repulse.lisp.reader.read_form(r);
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(f,new cljs.core.Keyword("repulse.lisp.reader","eof","repulse.lisp.reader/eof",-1146864404))){
return forms;
} else {
var G__8161 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(forms,f);
forms = G__8161;
continue;
}
}
break;
}
});
repulse.lisp.reader.read_one = (function repulse$lisp$reader$read_one(src){
return cljs.core.first(repulse.lisp.reader.read_all(src));
});

//# sourceMappingURL=repulse.lisp.reader.js.map
