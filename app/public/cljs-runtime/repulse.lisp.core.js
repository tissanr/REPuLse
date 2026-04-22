goog.provide('repulse.lisp.core');

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
repulse.lisp.core.EvalError = (function (message,source,__meta,__extmap,__hash){
this.message = message;
this.source = source;
this.__meta = __meta;
this.__extmap = __extmap;
this.__hash = __hash;
this.cljs$lang$protocol_mask$partition0$ = 2230716170;
this.cljs$lang$protocol_mask$partition1$ = 139264;
});
(repulse.lisp.core.EvalError.prototype.cljs$core$ILookup$_lookup$arity$2 = (function (this__5300__auto__,k__5301__auto__){
var self__ = this;
var this__5300__auto____$1 = this;
return this__5300__auto____$1.cljs$core$ILookup$_lookup$arity$3(null,k__5301__auto__,null);
}));

(repulse.lisp.core.EvalError.prototype.cljs$core$ILookup$_lookup$arity$3 = (function (this__5302__auto__,k8493,else__5303__auto__){
var self__ = this;
var this__5302__auto____$1 = this;
var G__8498 = k8493;
var G__8498__$1 = (((G__8498 instanceof cljs.core.Keyword))?G__8498.fqn:null);
switch (G__8498__$1) {
case "message":
return self__.message;

break;
case "source":
return self__.source;

break;
default:
return cljs.core.get.cljs$core$IFn$_invoke$arity$3(self__.__extmap,k8493,else__5303__auto__);

}
}));

(repulse.lisp.core.EvalError.prototype.cljs$core$IKVReduce$_kv_reduce$arity$3 = (function (this__5320__auto__,f__5321__auto__,init__5322__auto__){
var self__ = this;
var this__5320__auto____$1 = this;
return cljs.core.reduce.cljs$core$IFn$_invoke$arity$3((function (ret__5323__auto__,p__8499){
var vec__8500 = p__8499;
var k__5324__auto__ = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8500,(0),null);
var v__5325__auto__ = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8500,(1),null);
return (f__5321__auto__.cljs$core$IFn$_invoke$arity$3 ? f__5321__auto__.cljs$core$IFn$_invoke$arity$3(ret__5323__auto__,k__5324__auto__,v__5325__auto__) : f__5321__auto__.call(null,ret__5323__auto__,k__5324__auto__,v__5325__auto__));
}),init__5322__auto__,this__5320__auto____$1);
}));

(repulse.lisp.core.EvalError.prototype.cljs$core$IPrintWithWriter$_pr_writer$arity$3 = (function (this__5315__auto__,writer__5316__auto__,opts__5317__auto__){
var self__ = this;
var this__5315__auto____$1 = this;
var pr_pair__5318__auto__ = (function (keyval__5319__auto__){
return cljs.core.pr_sequential_writer(writer__5316__auto__,cljs.core.pr_writer,""," ","",opts__5317__auto__,keyval__5319__auto__);
});
return cljs.core.pr_sequential_writer(writer__5316__auto__,pr_pair__5318__auto__,"#repulse.lisp.core.EvalError{",", ","}",opts__5317__auto__,cljs.core.concat.cljs$core$IFn$_invoke$arity$2(new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(new cljs.core.PersistentVector(null,2,(5),cljs.core.PersistentVector.EMPTY_NODE,[new cljs.core.Keyword(null,"message","message",-406056002),self__.message],null)),(new cljs.core.PersistentVector(null,2,(5),cljs.core.PersistentVector.EMPTY_NODE,[new cljs.core.Keyword(null,"source","source",-433931539),self__.source],null))], null),self__.__extmap));
}));

(repulse.lisp.core.EvalError.prototype.cljs$core$IIterable$_iterator$arity$1 = (function (G__8492){
var self__ = this;
var G__8492__$1 = this;
return (new cljs.core.RecordIter((0),G__8492__$1,2,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"message","message",-406056002),new cljs.core.Keyword(null,"source","source",-433931539)], null),(cljs.core.truth_(self__.__extmap)?cljs.core._iterator(self__.__extmap):cljs.core.nil_iter())));
}));

(repulse.lisp.core.EvalError.prototype.cljs$core$IMeta$_meta$arity$1 = (function (this__5298__auto__){
var self__ = this;
var this__5298__auto____$1 = this;
return self__.__meta;
}));

(repulse.lisp.core.EvalError.prototype.cljs$core$ICloneable$_clone$arity$1 = (function (this__5295__auto__){
var self__ = this;
var this__5295__auto____$1 = this;
return (new repulse.lisp.core.EvalError(self__.message,self__.source,self__.__meta,self__.__extmap,self__.__hash));
}));

(repulse.lisp.core.EvalError.prototype.cljs$core$ICounted$_count$arity$1 = (function (this__5304__auto__){
var self__ = this;
var this__5304__auto____$1 = this;
return (2 + cljs.core.count(self__.__extmap));
}));

(repulse.lisp.core.EvalError.prototype.cljs$core$IHash$_hash$arity$1 = (function (this__5296__auto__){
var self__ = this;
var this__5296__auto____$1 = this;
var h__5111__auto__ = self__.__hash;
if((!((h__5111__auto__ == null)))){
return h__5111__auto__;
} else {
var h__5111__auto____$1 = (function (coll__5297__auto__){
return (-1040946180 ^ cljs.core.hash_unordered_coll(coll__5297__auto__));
})(this__5296__auto____$1);
(self__.__hash = h__5111__auto____$1);

return h__5111__auto____$1;
}
}));

(repulse.lisp.core.EvalError.prototype.cljs$core$IEquiv$_equiv$arity$2 = (function (this8494,other8495){
var self__ = this;
var this8494__$1 = this;
return (((!((other8495 == null)))) && ((((this8494__$1.constructor === other8495.constructor)) && (((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(this8494__$1.message,other8495.message)) && (((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(this8494__$1.source,other8495.source)) && (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(this8494__$1.__extmap,other8495.__extmap)))))))));
}));

(repulse.lisp.core.EvalError.prototype.cljs$core$IMap$_dissoc$arity$2 = (function (this__5310__auto__,k__5311__auto__){
var self__ = this;
var this__5310__auto____$1 = this;
if(cljs.core.contains_QMARK_(new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"source","source",-433931539),null,new cljs.core.Keyword(null,"message","message",-406056002),null], null), null),k__5311__auto__)){
return cljs.core.dissoc.cljs$core$IFn$_invoke$arity$2(cljs.core._with_meta(cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.PersistentArrayMap.EMPTY,this__5310__auto____$1),self__.__meta),k__5311__auto__);
} else {
return (new repulse.lisp.core.EvalError(self__.message,self__.source,self__.__meta,cljs.core.not_empty(cljs.core.dissoc.cljs$core$IFn$_invoke$arity$2(self__.__extmap,k__5311__auto__)),null));
}
}));

(repulse.lisp.core.EvalError.prototype.cljs$core$IAssociative$_contains_key_QMARK_$arity$2 = (function (this__5307__auto__,k8493){
var self__ = this;
var this__5307__auto____$1 = this;
var G__8511 = k8493;
var G__8511__$1 = (((G__8511 instanceof cljs.core.Keyword))?G__8511.fqn:null);
switch (G__8511__$1) {
case "message":
case "source":
return true;

break;
default:
return cljs.core.contains_QMARK_(self__.__extmap,k8493);

}
}));

(repulse.lisp.core.EvalError.prototype.cljs$core$IAssociative$_assoc$arity$3 = (function (this__5308__auto__,k__5309__auto__,G__8492){
var self__ = this;
var this__5308__auto____$1 = this;
var pred__8513 = cljs.core.keyword_identical_QMARK_;
var expr__8515 = k__5309__auto__;
if(cljs.core.truth_((pred__8513.cljs$core$IFn$_invoke$arity$2 ? pred__8513.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"message","message",-406056002),expr__8515) : pred__8513.call(null,new cljs.core.Keyword(null,"message","message",-406056002),expr__8515)))){
return (new repulse.lisp.core.EvalError(G__8492,self__.source,self__.__meta,self__.__extmap,null));
} else {
if(cljs.core.truth_((pred__8513.cljs$core$IFn$_invoke$arity$2 ? pred__8513.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"source","source",-433931539),expr__8515) : pred__8513.call(null,new cljs.core.Keyword(null,"source","source",-433931539),expr__8515)))){
return (new repulse.lisp.core.EvalError(self__.message,G__8492,self__.__meta,self__.__extmap,null));
} else {
return (new repulse.lisp.core.EvalError(self__.message,self__.source,self__.__meta,cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(self__.__extmap,k__5309__auto__,G__8492),null));
}
}
}));

(repulse.lisp.core.EvalError.prototype.cljs$core$ISeqable$_seq$arity$1 = (function (this__5313__auto__){
var self__ = this;
var this__5313__auto____$1 = this;
return cljs.core.seq(cljs.core.concat.cljs$core$IFn$_invoke$arity$2(new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(new cljs.core.MapEntry(new cljs.core.Keyword(null,"message","message",-406056002),self__.message,null)),(new cljs.core.MapEntry(new cljs.core.Keyword(null,"source","source",-433931539),self__.source,null))], null),self__.__extmap));
}));

(repulse.lisp.core.EvalError.prototype.cljs$core$IWithMeta$_with_meta$arity$2 = (function (this__5299__auto__,G__8492){
var self__ = this;
var this__5299__auto____$1 = this;
return (new repulse.lisp.core.EvalError(self__.message,self__.source,G__8492,self__.__extmap,self__.__hash));
}));

(repulse.lisp.core.EvalError.prototype.cljs$core$ICollection$_conj$arity$2 = (function (this__5305__auto__,entry__5306__auto__){
var self__ = this;
var this__5305__auto____$1 = this;
if(cljs.core.vector_QMARK_(entry__5306__auto__)){
return this__5305__auto____$1.cljs$core$IAssociative$_assoc$arity$3(null,cljs.core._nth(entry__5306__auto__,(0)),cljs.core._nth(entry__5306__auto__,(1)));
} else {
return cljs.core.reduce.cljs$core$IFn$_invoke$arity$3(cljs.core._conj,this__5305__auto____$1,entry__5306__auto__);
}
}));

(repulse.lisp.core.EvalError.getBasis = (function (){
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Symbol(null,"message","message",1234475525,null),new cljs.core.Symbol(null,"source","source",1206599988,null)], null);
}));

(repulse.lisp.core.EvalError.cljs$lang$type = true);

(repulse.lisp.core.EvalError.cljs$lang$ctorPrSeq = (function (this__5346__auto__){
return (new cljs.core.List(null,"repulse.lisp.core/EvalError",null,(1),null));
}));

(repulse.lisp.core.EvalError.cljs$lang$ctorPrWriter = (function (this__5346__auto__,writer__5347__auto__){
return cljs.core._write(writer__5347__auto__,"repulse.lisp.core/EvalError");
}));

/**
 * Positional factory function for repulse.lisp.core/EvalError.
 */
repulse.lisp.core.__GT_EvalError = (function repulse$lisp$core$__GT_EvalError(message,source){
return (new repulse.lisp.core.EvalError(message,source,null,null,null));
});

/**
 * Factory function for repulse.lisp.core/EvalError, taking a map of keywords to field values.
 */
repulse.lisp.core.map__GT_EvalError = (function repulse$lisp$core$map__GT_EvalError(G__8496){
var extmap__5342__auto__ = (function (){var G__8523 = cljs.core.dissoc.cljs$core$IFn$_invoke$arity$variadic(G__8496,new cljs.core.Keyword(null,"message","message",-406056002),cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([new cljs.core.Keyword(null,"source","source",-433931539)], 0));
if(cljs.core.record_QMARK_(G__8496)){
return cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.PersistentArrayMap.EMPTY,G__8523);
} else {
return G__8523;
}
})();
return (new repulse.lisp.core.EvalError(new cljs.core.Keyword(null,"message","message",-406056002).cljs$core$IFn$_invoke$arity$1(G__8496),new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(G__8496),null,cljs.core.not_empty(extmap__5342__auto__),null));
});

repulse.lisp.core.eval_error = (function repulse$lisp$core$eval_error(var_args){
var G__8526 = arguments.length;
switch (G__8526) {
case 1:
return repulse.lisp.core.eval_error.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.lisp.core.eval_error.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.lisp.core.eval_error.cljs$core$IFn$_invoke$arity$1 = (function (message){
return repulse.lisp.core.__GT_EvalError(message,null);
}));

(repulse.lisp.core.eval_error.cljs$core$IFn$_invoke$arity$2 = (function (message,source){
return repulse.lisp.core.__GT_EvalError(message,source);
}));

(repulse.lisp.core.eval_error.cljs$lang$maxFixedArity = 2);

repulse.lisp.core.eval_error_QMARK_ = (function repulse$lisp$core$eval_error_QMARK_(x){
return (x instanceof repulse.lisp.core.EvalError);
});
/**
 * Parse and evaluate a string in the given environment.
 * Returns {:result v} or an EvalError record.
 */
repulse.lisp.core.eval_string = (function repulse$lisp$core$eval_string(src,env){
try{var forms = repulse.lisp.reader.read_all(src);
if(cljs.core.empty_QMARK_(forms)){
return new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"result","result",1415092211),null], null);
} else {
var final_result = (function (){var remaining = forms;
var last_result = null;
while(true){
if(cljs.core.empty_QMARK_(remaining)){
return last_result;
} else {
var result = repulse.lisp.eval.eval_form(cljs.core.first(remaining),env);
if(repulse.lisp.core.eval_error_QMARK_(result)){
return result;
} else {
var G__8574 = cljs.core.rest(remaining);
var G__8575 = result;
remaining = G__8574;
last_result = G__8575;
continue;
}
}
break;
}
})();
if(repulse.lisp.core.eval_error_QMARK_(final_result)){
return final_result;
} else {
return new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"result","result",1415092211),final_result], null);
}
}
}catch (e8534){var e = e8534;
var data = cljs.core.ex_data(e);
return repulse.lisp.core.eval_error.cljs$core$IFn$_invoke$arity$2((function (){var or__5002__auto__ = e.message;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return cljs.core.str.cljs$core$IFn$_invoke$arity$1(e);
}
})(),(cljs.core.truth_(new cljs.core.Keyword(null,"from","from",1815293044).cljs$core$IFn$_invoke$arity$1(data))?new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"from","from",1815293044),new cljs.core.Keyword(null,"from","from",1815293044).cljs$core$IFn$_invoke$arity$1(data),new cljs.core.Keyword(null,"to","to",192099007),new cljs.core.Keyword(null,"to","to",192099007).cljs$core$IFn$_invoke$arity$1(data)], null):null));
}});

//# sourceMappingURL=repulse.lisp.core.js.map
