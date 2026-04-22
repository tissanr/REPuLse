goog.provide('repulse.core');
repulse.core.gcd = (function repulse$core$gcd(a,b){
var a__$1 = Math.abs(a);
var b__$1 = Math.abs(b);
while(true){
if((b__$1 === (0))){
return a__$1;
} else {
var G__8097 = b__$1;
var G__8098 = cljs.core.mod(a__$1,b__$1);
a__$1 = G__8097;
b__$1 = G__8098;
continue;
}
break;
}
});
repulse.core.rat = (function repulse$core$rat(var_args){
var G__7540 = arguments.length;
switch (G__7540) {
case 1:
return repulse.core.rat.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.core.rat.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.core.rat.cljs$core$IFn$_invoke$arity$1 = (function (n){
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [n,(1)], null);
}));

(repulse.core.rat.cljs$core$IFn$_invoke$arity$2 = (function (n,d){
var g = repulse.core.gcd(Math.abs(n),Math.abs(d));
var sign = (((d < (0)))?(-1):(1));
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(sign * (n / g)),(sign * (d / g))], null);
}));

(repulse.core.rat.cljs$lang$maxFixedArity = 2);

repulse.core.rat_PLUS_ = (function repulse$core$rat_PLUS_(p__7548,p__7549){
var vec__7551 = p__7548;
var n1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7551,(0),null);
var d1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7551,(1),null);
var vec__7554 = p__7549;
var n2 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7554,(0),null);
var d2 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7554,(1),null);
return repulse.core.rat.cljs$core$IFn$_invoke$arity$2(((n1 * d2) + (n2 * d1)),(d1 * d2));
});
repulse.core.rat_ = (function repulse$core$rat_(p__7557,p__7558){
var vec__7559 = p__7557;
var n1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7559,(0),null);
var d1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7559,(1),null);
var vec__7562 = p__7558;
var n2 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7562,(0),null);
var d2 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7562,(1),null);
return repulse.core.rat.cljs$core$IFn$_invoke$arity$2(((n1 * d2) - (n2 * d1)),(d1 * d2));
});
repulse.core.rat_STAR_ = (function repulse$core$rat_STAR_(p__7568,p__7569){
var vec__7570 = p__7568;
var n1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7570,(0),null);
var d1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7570,(1),null);
var vec__7573 = p__7569;
var n2 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7573,(0),null);
var d2 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7573,(1),null);
return repulse.core.rat.cljs$core$IFn$_invoke$arity$2((n1 * n2),(d1 * d2));
});
repulse.core.rat_div = (function repulse$core$rat_div(p__7580,p__7581){
var vec__7582 = p__7580;
var n1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7582,(0),null);
var d1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7582,(1),null);
var vec__7585 = p__7581;
var n2 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7585,(0),null);
var d2 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7585,(1),null);
return repulse.core.rat.cljs$core$IFn$_invoke$arity$2((n1 * d2),(d1 * n2));
});
repulse.core.rat_LT_ = (function repulse$core$rat_LT_(p__7588,p__7589){
var vec__7590 = p__7588;
var n1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7590,(0),null);
var d1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7590,(1),null);
var vec__7593 = p__7589;
var n2 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7593,(0),null);
var d2 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7593,(1),null);
return ((n1 * d2) < (n2 * d1));
});
repulse.core.rat_LT__EQ_ = (function repulse$core$rat_LT__EQ_(a,b){
return ((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(a,b)) || (repulse.core.rat_LT_(a,b)));
});
repulse.core.rat_GT_ = (function repulse$core$rat_GT_(a,b){
return repulse.core.rat_LT_(b,a);
});
repulse.core.rat_GT__EQ_ = (function repulse$core$rat_GT__EQ_(a,b){
return repulse.core.rat_LT__EQ_(b,a);
});
repulse.core.rat_min = (function repulse$core$rat_min(a,b){
if(repulse.core.rat_LT_(a,b)){
return a;
} else {
return b;
}
});
repulse.core.rat_max = (function repulse$core$rat_max(a,b){
if(repulse.core.rat_GT_(a,b)){
return a;
} else {
return b;
}
});
repulse.core.rat__GT_float = (function repulse$core$rat__GT_float(p__7598){
var vec__7599 = p__7598;
var n = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7599,(0),null);
var d = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7599,(1),null);
return (n / d);
});
repulse.core.int__GT_rat = (function repulse$core$int__GT_rat(n){
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [n,(1)], null);
});
repulse.core.span = (function repulse$core$span(start,end){
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"start","start",-355208981),start,new cljs.core.Keyword(null,"end","end",-268185958),end], null);
});
repulse.core.span_intersect = (function repulse$core$span_intersect(p__7619,p__7620){
var map__7622 = p__7619;
var map__7622__$1 = cljs.core.__destructure_map(map__7622);
var s1 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7622__$1,new cljs.core.Keyword(null,"start","start",-355208981));
var e1 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7622__$1,new cljs.core.Keyword(null,"end","end",-268185958));
var map__7623 = p__7620;
var map__7623__$1 = cljs.core.__destructure_map(map__7623);
var s2 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7623__$1,new cljs.core.Keyword(null,"start","start",-355208981));
var e2 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7623__$1,new cljs.core.Keyword(null,"end","end",-268185958));
var s = repulse.core.rat_max(s1,s2);
var e = repulse.core.rat_min(e1,e2);
if(repulse.core.rat_LT_(s,e)){
return repulse.core.span(s,e);
} else {
return null;
}
});
repulse.core.cycle_span = (function repulse$core$cycle_span(n){
return repulse.core.span(repulse.core.int__GT_rat(n),repulse.core.int__GT_rat((n + (1))));
});
repulse.core.event = (function repulse$core$event(value,whole,part){
return new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"value","value",305978217),value,new cljs.core.Keyword(null,"whole","whole",-1395468966),whole,new cljs.core.Keyword(null,"part","part",77757738),part], null);
});
repulse.core.pattern = (function repulse$core$pattern(query_fn){
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"query","query",-1288509510),query_fn,new cljs.core.Keyword("repulse.core","pat","repulse.core/pat",-1220810413),true], null);
});
repulse.core.pattern_QMARK_ = (function repulse$core$pattern_QMARK_(x){

return new cljs.core.Keyword("repulse.core","pat","repulse.core/pat",-1220810413).cljs$core$IFn$_invoke$arity$1(x) === true;
});
repulse.core.query = (function repulse$core$query(pat,sp){
var fexpr__7641 = new cljs.core.Keyword(null,"query","query",-1288509510).cljs$core$IFn$_invoke$arity$1(pat);
return (fexpr__7641.cljs$core$IFn$_invoke$arity$1 ? fexpr__7641.cljs$core$IFn$_invoke$arity$1(sp) : fexpr__7641.call(null,sp));
});
repulse.core.pure = (function repulse$core$pure(var_args){
var G__7660 = arguments.length;
switch (G__7660) {
case 1:
return repulse.core.pure.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.core.pure.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.core.pure.cljs$core$IFn$_invoke$arity$1 = (function (value){
return repulse.core.pure.cljs$core$IFn$_invoke$arity$2(value,null);
}));

(repulse.core.pure.cljs$core$IFn$_invoke$arity$2 = (function (value,source){
return repulse.core.pattern((function (p__7666){
var map__7668 = p__7666;
var map__7668__$1 = cljs.core.__destructure_map(map__7668);
var start = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7668__$1,new cljs.core.Keyword(null,"start","start",-355208981));
var end = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7668__$1,new cljs.core.Keyword(null,"end","end",-268185958));
var start_c = (Math.floor(repulse.core.rat__GT_float(start)) | (0));
var end_c = (Math.ceil(repulse.core.rat__GT_float(end)) | (0));
var iter__5480__auto__ = (function repulse$core$iter__7681(s__7682){
return (new cljs.core.LazySeq(null,(function (){
var s__7682__$1 = s__7682;
while(true){
var temp__5804__auto__ = cljs.core.seq(s__7682__$1);
if(temp__5804__auto__){
var s__7682__$2 = temp__5804__auto__;
if(cljs.core.chunked_seq_QMARK_(s__7682__$2)){
var c__5478__auto__ = cljs.core.chunk_first(s__7682__$2);
var size__5479__auto__ = cljs.core.count(c__5478__auto__);
var b__7688 = cljs.core.chunk_buffer(size__5479__auto__);
if((function (){var i__7687 = (0);
while(true){
if((i__7687 < size__5479__auto__)){
var c = cljs.core._nth(c__5478__auto__,i__7687);
var whole = repulse.core.cycle_span(c);
var part = repulse.core.span_intersect(whole,repulse.core.span(start,end));
if(cljs.core.truth_(part)){
cljs.core.chunk_append(b__7688,(function (){var base = repulse.core.event(value,whole,part);
if(cljs.core.truth_(source)){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(base,new cljs.core.Keyword(null,"source","source",-433931539),source);
} else {
return base;
}
})());

var G__8155 = (i__7687 + (1));
i__7687 = G__8155;
continue;
} else {
var G__8160 = (i__7687 + (1));
i__7687 = G__8160;
continue;
}
} else {
return true;
}
break;
}
})()){
return cljs.core.chunk_cons(cljs.core.chunk(b__7688),repulse$core$iter__7681(cljs.core.chunk_rest(s__7682__$2)));
} else {
return cljs.core.chunk_cons(cljs.core.chunk(b__7688),null);
}
} else {
var c = cljs.core.first(s__7682__$2);
var whole = repulse.core.cycle_span(c);
var part = repulse.core.span_intersect(whole,repulse.core.span(start,end));
if(cljs.core.truth_(part)){
return cljs.core.cons((function (){var base = repulse.core.event(value,whole,part);
if(cljs.core.truth_(source)){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(base,new cljs.core.Keyword(null,"source","source",-433931539),source);
} else {
return base;
}
})(),repulse$core$iter__7681(cljs.core.rest(s__7682__$2)));
} else {
var G__8171 = cljs.core.rest(s__7682__$2);
s__7682__$1 = G__8171;
continue;
}
}
} else {
return null;
}
break;
}
}),null,null));
});
return iter__5480__auto__(cljs.core.range.cljs$core$IFn$_invoke$arity$2(start_c,end_c));
}));
}));

(repulse.core.pure.cljs$lang$maxFixedArity = 2);

repulse.core.seq_STAR_ = (function repulse$core$seq_STAR_(var_args){
var G__7721 = arguments.length;
switch (G__7721) {
case 1:
return repulse.core.seq_STAR_.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.core.seq_STAR_.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.core.seq_STAR_.cljs$core$IFn$_invoke$arity$1 = (function (values){
return repulse.core.seq_STAR_.cljs$core$IFn$_invoke$arity$2(values,null);
}));

(repulse.core.seq_STAR_.cljs$core$IFn$_invoke$arity$2 = (function (values,sources){
var n = cljs.core.count(values);
if((n === (0))){
return repulse.core.pattern((function (_){
return cljs.core.PersistentVector.EMPTY;
}));
} else {
return repulse.core.pattern((function (p__7740){
var map__7741 = p__7740;
var map__7741__$1 = cljs.core.__destructure_map(map__7741);
var sp = map__7741__$1;
var start = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7741__$1,new cljs.core.Keyword(null,"start","start",-355208981));
var end = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7741__$1,new cljs.core.Keyword(null,"end","end",-268185958));
var start_c = (Math.floor(repulse.core.rat__GT_float(start)) | (0));
var end_c = (Math.ceil(repulse.core.rat__GT_float(end)) | (0));
var iter__5480__auto__ = (function repulse$core$iter__7747(s__7748){
return (new cljs.core.LazySeq(null,(function (){
var s__7748__$1 = s__7748;
while(true){
var temp__5804__auto__ = cljs.core.seq(s__7748__$1);
if(temp__5804__auto__){
var xs__6360__auto__ = temp__5804__auto__;
var c = cljs.core.first(xs__6360__auto__);
var iterys__5476__auto__ = ((function (s__7748__$1,c,xs__6360__auto__,temp__5804__auto__,start_c,end_c,map__7741,map__7741__$1,sp,start,end,n){
return (function repulse$core$iter__7747_$_iter__7749(s__7750){
return (new cljs.core.LazySeq(null,((function (s__7748__$1,c,xs__6360__auto__,temp__5804__auto__,start_c,end_c,map__7741,map__7741__$1,sp,start,end,n){
return (function (){
var s__7750__$1 = s__7750;
while(true){
var temp__5804__auto____$1 = cljs.core.seq(s__7750__$1);
if(temp__5804__auto____$1){
var s__7750__$2 = temp__5804__auto____$1;
if(cljs.core.chunked_seq_QMARK_(s__7750__$2)){
var c__5478__auto__ = cljs.core.chunk_first(s__7750__$2);
var size__5479__auto__ = cljs.core.count(c__5478__auto__);
var b__7752 = cljs.core.chunk_buffer(size__5479__auto__);
if((function (){var i__7751 = (0);
while(true){
if((i__7751 < size__5479__auto__)){
var i = cljs.core._nth(c__5478__auto__,i__7751);
var s = repulse.core.rat.cljs$core$IFn$_invoke$arity$2(((c * n) + i),n);
var e = repulse.core.rat.cljs$core$IFn$_invoke$arity$2(((c * n) + (i + (1))),n);
var whole = repulse.core.span(s,e);
var part = repulse.core.span_intersect(whole,sp);
if(cljs.core.truth_(part)){
cljs.core.chunk_append(b__7752,(function (){var base = repulse.core.event(cljs.core.nth.cljs$core$IFn$_invoke$arity$2(values,i),whole,part);
var src = (cljs.core.truth_(sources)?cljs.core.nth.cljs$core$IFn$_invoke$arity$3(sources,i,null):null);
if(cljs.core.truth_(src)){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(base,new cljs.core.Keyword(null,"source","source",-433931539),src);
} else {
return base;
}
})());

var G__8191 = (i__7751 + (1));
i__7751 = G__8191;
continue;
} else {
var G__8192 = (i__7751 + (1));
i__7751 = G__8192;
continue;
}
} else {
return true;
}
break;
}
})()){
return cljs.core.chunk_cons(cljs.core.chunk(b__7752),repulse$core$iter__7747_$_iter__7749(cljs.core.chunk_rest(s__7750__$2)));
} else {
return cljs.core.chunk_cons(cljs.core.chunk(b__7752),null);
}
} else {
var i = cljs.core.first(s__7750__$2);
var s = repulse.core.rat.cljs$core$IFn$_invoke$arity$2(((c * n) + i),n);
var e = repulse.core.rat.cljs$core$IFn$_invoke$arity$2(((c * n) + (i + (1))),n);
var whole = repulse.core.span(s,e);
var part = repulse.core.span_intersect(whole,sp);
if(cljs.core.truth_(part)){
return cljs.core.cons((function (){var base = repulse.core.event(cljs.core.nth.cljs$core$IFn$_invoke$arity$2(values,i),whole,part);
var src = (cljs.core.truth_(sources)?cljs.core.nth.cljs$core$IFn$_invoke$arity$3(sources,i,null):null);
if(cljs.core.truth_(src)){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(base,new cljs.core.Keyword(null,"source","source",-433931539),src);
} else {
return base;
}
})(),repulse$core$iter__7747_$_iter__7749(cljs.core.rest(s__7750__$2)));
} else {
var G__8198 = cljs.core.rest(s__7750__$2);
s__7750__$1 = G__8198;
continue;
}
}
} else {
return null;
}
break;
}
});})(s__7748__$1,c,xs__6360__auto__,temp__5804__auto__,start_c,end_c,map__7741,map__7741__$1,sp,start,end,n))
,null,null));
});})(s__7748__$1,c,xs__6360__auto__,temp__5804__auto__,start_c,end_c,map__7741,map__7741__$1,sp,start,end,n))
;
var fs__5477__auto__ = cljs.core.seq(iterys__5476__auto__(cljs.core.range.cljs$core$IFn$_invoke$arity$1(n)));
if(fs__5477__auto__){
return cljs.core.concat.cljs$core$IFn$_invoke$arity$2(fs__5477__auto__,repulse$core$iter__7747(cljs.core.rest(s__7748__$1)));
} else {
var G__8202 = cljs.core.rest(s__7748__$1);
s__7748__$1 = G__8202;
continue;
}
} else {
return null;
}
break;
}
}),null,null));
});
return iter__5480__auto__(cljs.core.range.cljs$core$IFn$_invoke$arity$2(start_c,end_c));
}));
}
}));

(repulse.core.seq_STAR_.cljs$lang$maxFixedArity = 2);

repulse.core.stack_STAR_ = (function repulse$core$stack_STAR_(pats){
return repulse.core.pattern((function (sp){
return cljs.core.mapcat.cljs$core$IFn$_invoke$arity$variadic((function (p1__7777_SHARP_){
return repulse.core.query(p1__7777_SHARP_,sp);
}),cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([pats], 0));
}));
});
repulse.core.fmap = (function repulse$core$fmap(f,pat){
return repulse.core.pattern((function (sp){
return cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (e){
return cljs.core.update.cljs$core$IFn$_invoke$arity$3(e,new cljs.core.Keyword(null,"value","value",305978217),f);
}),repulse.core.query(pat,sp));
}));
});
/**
 * Applicative liftA2: pair events from pat-a and pat-b that overlap in time.
 * For each (ea, eb) pair whose :part spans intersect, produce an event with
 * value (f va vb) at the intersection. Uses eb's :whole.
 */
repulse.core.combine = (function repulse$core$combine(f,pat_a,pat_b){
return repulse.core.pattern((function (sp){
var evs_a = repulse.core.query(pat_a,sp);
var evs_b = repulse.core.query(pat_b,sp);
var iter__5480__auto__ = (function repulse$core$combine_$_iter__7782(s__7783){
return (new cljs.core.LazySeq(null,(function (){
var s__7783__$1 = s__7783;
while(true){
var temp__5804__auto__ = cljs.core.seq(s__7783__$1);
if(temp__5804__auto__){
var xs__6360__auto__ = temp__5804__auto__;
var ea = cljs.core.first(xs__6360__auto__);
var iterys__5476__auto__ = ((function (s__7783__$1,ea,xs__6360__auto__,temp__5804__auto__,evs_a,evs_b){
return (function repulse$core$combine_$_iter__7782_$_iter__7784(s__7785){
return (new cljs.core.LazySeq(null,((function (s__7783__$1,ea,xs__6360__auto__,temp__5804__auto__,evs_a,evs_b){
return (function (){
var s__7785__$1 = s__7785;
while(true){
var temp__5804__auto____$1 = cljs.core.seq(s__7785__$1);
if(temp__5804__auto____$1){
var s__7785__$2 = temp__5804__auto____$1;
if(cljs.core.chunked_seq_QMARK_(s__7785__$2)){
var c__5478__auto__ = cljs.core.chunk_first(s__7785__$2);
var size__5479__auto__ = cljs.core.count(c__5478__auto__);
var b__7787 = cljs.core.chunk_buffer(size__5479__auto__);
if((function (){var i__7786 = (0);
while(true){
if((i__7786 < size__5479__auto__)){
var eb = cljs.core._nth(c__5478__auto__,i__7786);
var isect = repulse.core.span_intersect(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ea),new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(eb));
if(cljs.core.truth_(isect)){
cljs.core.chunk_append(b__7787,(function (){var base = repulse.core.event((function (){var G__7789 = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ea);
var G__7790 = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(eb);
return (f.cljs$core$IFn$_invoke$arity$2 ? f.cljs$core$IFn$_invoke$arity$2(G__7789,G__7790) : f.call(null,G__7789,G__7790));
})(),new cljs.core.Keyword(null,"whole","whole",-1395468966).cljs$core$IFn$_invoke$arity$1(eb),isect);
var temp__5802__auto__ = new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(eb);
if(cljs.core.truth_(temp__5802__auto__)){
var src = temp__5802__auto__;
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(base,new cljs.core.Keyword(null,"source","source",-433931539),src);
} else {
return base;
}
})());

var G__8214 = (i__7786 + (1));
i__7786 = G__8214;
continue;
} else {
var G__8215 = (i__7786 + (1));
i__7786 = G__8215;
continue;
}
} else {
return true;
}
break;
}
})()){
return cljs.core.chunk_cons(cljs.core.chunk(b__7787),repulse$core$combine_$_iter__7782_$_iter__7784(cljs.core.chunk_rest(s__7785__$2)));
} else {
return cljs.core.chunk_cons(cljs.core.chunk(b__7787),null);
}
} else {
var eb = cljs.core.first(s__7785__$2);
var isect = repulse.core.span_intersect(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ea),new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(eb));
if(cljs.core.truth_(isect)){
return cljs.core.cons((function (){var base = repulse.core.event((function (){var G__7796 = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ea);
var G__7797 = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(eb);
return (f.cljs$core$IFn$_invoke$arity$2 ? f.cljs$core$IFn$_invoke$arity$2(G__7796,G__7797) : f.call(null,G__7796,G__7797));
})(),new cljs.core.Keyword(null,"whole","whole",-1395468966).cljs$core$IFn$_invoke$arity$1(eb),isect);
var temp__5802__auto__ = new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(eb);
if(cljs.core.truth_(temp__5802__auto__)){
var src = temp__5802__auto__;
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(base,new cljs.core.Keyword(null,"source","source",-433931539),src);
} else {
return base;
}
})(),repulse$core$combine_$_iter__7782_$_iter__7784(cljs.core.rest(s__7785__$2)));
} else {
var G__8222 = cljs.core.rest(s__7785__$2);
s__7785__$1 = G__8222;
continue;
}
}
} else {
return null;
}
break;
}
});})(s__7783__$1,ea,xs__6360__auto__,temp__5804__auto__,evs_a,evs_b))
,null,null));
});})(s__7783__$1,ea,xs__6360__auto__,temp__5804__auto__,evs_a,evs_b))
;
var fs__5477__auto__ = cljs.core.seq(iterys__5476__auto__(evs_b));
if(fs__5477__auto__){
return cljs.core.concat.cljs$core$IFn$_invoke$arity$2(fs__5477__auto__,repulse$core$combine_$_iter__7782(cljs.core.rest(s__7783__$1)));
} else {
var G__8231 = cljs.core.rest(s__7783__$1);
s__7783__$1 = G__8231;
continue;
}
} else {
return null;
}
break;
}
}),null,null));
});
return iter__5480__auto__(evs_a);
}));
});
repulse.core.fast = (function repulse$core$fast(factor,pat){
var fr = ((cljs.core.vector_QMARK_(factor))?factor:repulse.core.int__GT_rat(factor));
return repulse.core.pattern((function (sp){
var fast_sp = repulse.core.span(repulse.core.rat_STAR_(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(sp),fr),repulse.core.rat_STAR_(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(sp),fr));
var evs = repulse.core.query(pat,fast_sp);
return cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (e){
return cljs.core.update.cljs$core$IFn$_invoke$arity$3(cljs.core.update.cljs$core$IFn$_invoke$arity$3(e,new cljs.core.Keyword(null,"whole","whole",-1395468966),(function (p1__7800_SHARP_){
return repulse.core.span(repulse.core.rat_div(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(p1__7800_SHARP_),fr),repulse.core.rat_div(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(p1__7800_SHARP_),fr));
})),new cljs.core.Keyword(null,"part","part",77757738),(function (p1__7801_SHARP_){
return repulse.core.span(repulse.core.rat_div(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(p1__7801_SHARP_),fr),repulse.core.rat_div(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(p1__7801_SHARP_),fr));
}));
}),evs);
}));
});
repulse.core.slow = (function repulse$core$slow(factor,pat){
var fr = ((cljs.core.vector_QMARK_(factor))?factor:repulse.core.int__GT_rat(factor));
return repulse.core.fast(repulse.core.rat_div(repulse.core.int__GT_rat((1)),fr),pat);
});
repulse.core.rev = (function repulse$core$rev(pat){
return repulse.core.pattern((function (sp){
return cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (e){
var mirror = (function (p__7811){
var map__7812 = p__7811;
var map__7812__$1 = cljs.core.__destructure_map(map__7812);
var start = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7812__$1,new cljs.core.Keyword(null,"start","start",-355208981));
var end = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7812__$1,new cljs.core.Keyword(null,"end","end",-268185958));
var c = (Math.floor(repulse.core.rat__GT_float(start)) | (0));
var c1 = repulse.core.int__GT_rat((c + (1)));
return repulse.core.span(repulse.core.rat_(c1,repulse.core.rat_(end,repulse.core.int__GT_rat(c))),repulse.core.rat_(c1,repulse.core.rat_(start,repulse.core.int__GT_rat(c))));
});
return cljs.core.update.cljs$core$IFn$_invoke$arity$3(cljs.core.update.cljs$core$IFn$_invoke$arity$3(e,new cljs.core.Keyword(null,"whole","whole",-1395468966),mirror),new cljs.core.Keyword(null,"part","part",77757738),mirror);
}),repulse.core.query(pat,sp));
}));
});
repulse.core.every = (function repulse$core$every(n,transform,pat){
return repulse.core.pattern((function (sp){
var cycle = (Math.floor(repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(sp))) | (0));
if((cljs.core.mod(cycle,n) === (0))){
return repulse.core.query((transform.cljs$core$IFn$_invoke$arity$1 ? transform.cljs$core$IFn$_invoke$arity$1(pat) : transform.call(null,pat)),sp);
} else {
return repulse.core.query(pat,sp);
}
}));
});
/**
 * plan: [[pattern cycles] …]
 * Returns a Pattern that plays each section in order, looping after the total duration.
 */
repulse.core.arrange_STAR_ = (function repulse$core$arrange_STAR_(plan){
var timeline = cljs.core.reduce.cljs$core$IFn$_invoke$arity$3((function (acc,p__7853){
var vec__7855 = p__7853;
var pat = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7855,(0),null);
var dur = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7855,(1),null);
var prev = new cljs.core.Keyword(null,"to","to",192099007).cljs$core$IFn$_invoke$arity$2(cljs.core.last(acc),(0));
return cljs.core.conj.cljs$core$IFn$_invoke$arity$2(acc,new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"pat","pat",-1417570164),pat,new cljs.core.Keyword(null,"from","from",1815293044),prev,new cljs.core.Keyword(null,"to","to",192099007),(prev + dur)], null));
}),cljs.core.PersistentVector.EMPTY,plan);
var total = (function (){var or__5002__auto__ = new cljs.core.Keyword(null,"to","to",192099007).cljs$core$IFn$_invoke$arity$1(cljs.core.last(timeline));
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (1);
}
})();
return repulse.core.pattern((function (p__7863){
var map__7864 = p__7863;
var map__7864__$1 = cljs.core.__destructure_map(map__7864);
var start = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7864__$1,new cljs.core.Keyword(null,"start","start",-355208981));
var end = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7864__$1,new cljs.core.Keyword(null,"end","end",-268185958));
var g_cycle = (Math.floor(repulse.core.rat__GT_float(start)) | (0));
var lc = cljs.core.mod(g_cycle,total);
var loop_off = (g_cycle - lc);
var entry = cljs.core.some((function (p1__7842_SHARP_){
if((((lc >= new cljs.core.Keyword(null,"from","from",1815293044).cljs$core$IFn$_invoke$arity$1(p1__7842_SHARP_))) && ((lc < new cljs.core.Keyword(null,"to","to",192099007).cljs$core$IFn$_invoke$arity$1(p1__7842_SHARP_))))){
return p1__7842_SHARP_;
} else {
return null;
}
}),timeline);
if(cljs.core.truth_(entry)){
var sec_off = new cljs.core.Keyword(null,"from","from",1815293044).cljs$core$IFn$_invoke$arity$1(entry);
var offset = (loop_off + sec_off);
var local_start = repulse.core.rat_PLUS_(start,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(- offset),(1)], null));
var local_end = repulse.core.rat_PLUS_(end,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(- offset),(1)], null));
var evs = repulse.core.query(new cljs.core.Keyword(null,"pat","pat",-1417570164).cljs$core$IFn$_invoke$arity$1(entry),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"start","start",-355208981),local_start,new cljs.core.Keyword(null,"end","end",-268185958),local_end], null));
return cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (e){
return cljs.core.update.cljs$core$IFn$_invoke$arity$3(cljs.core.update.cljs$core$IFn$_invoke$arity$3(e,new cljs.core.Keyword(null,"whole","whole",-1395468966),(function (p1__7843_SHARP_){
return repulse.core.span(repulse.core.rat_PLUS_(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(p1__7843_SHARP_),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [offset,(1)], null)),repulse.core.rat_PLUS_(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(p1__7843_SHARP_),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [offset,(1)], null)));
})),new cljs.core.Keyword(null,"part","part",77757738),(function (p1__7844_SHARP_){
return repulse.core.span(repulse.core.rat_PLUS_(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(p1__7844_SHARP_),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [offset,(1)], null)),repulse.core.rat_PLUS_(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(p1__7844_SHARP_),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [offset,(1)], null)));
}));
}),evs);
} else {
return null;
}
}));
});
/**
 * Björklund's algorithm: distribute k onsets across n steps as evenly as possible.
 * Returns a seq pattern of val and :_ rests.
 * (euclidean 5 8 :bd)     — 5 hits in 8 steps
 * (euclidean 5 8 :bd 2)   — rotated 2 steps
 */
repulse.core.euclidean = (function repulse$core$euclidean(var_args){
var G__7903 = arguments.length;
switch (G__7903) {
case 3:
return repulse.core.euclidean.cljs$core$IFn$_invoke$arity$3((arguments[(0)]),(arguments[(1)]),(arguments[(2)]));

break;
case 4:
return repulse.core.euclidean.cljs$core$IFn$_invoke$arity$4((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),(arguments[(3)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.core.euclidean.cljs$core$IFn$_invoke$arity$3 = (function (k,n,val){
return repulse.core.euclidean.cljs$core$IFn$_invoke$arity$4(k,n,val,(0));
}));

(repulse.core.euclidean.cljs$core$IFn$_invoke$arity$4 = (function (k,n,val,rotation){
var result = (function (){var groups = cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.vec(cljs.core.repeat.cljs$core$IFn$_invoke$arity$2(k,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [true], null))),cljs.core.repeat.cljs$core$IFn$_invoke$arity$2((n - k),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [false], null)));
while(true){
var cnt_a = cljs.core.count(cljs.core.filter.cljs$core$IFn$_invoke$arity$2(((function (groups){
return (function (p1__7897_SHARP_){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(cljs.core.first(p1__7897_SHARP_),cljs.core.first(cljs.core.first(groups)));
});})(groups))
,groups));
var cnt_b = (cljs.core.count(groups) - cnt_a);
if((((cnt_b <= (1))) || (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(cljs.core.count(groups),n)))){
return cljs.core.vec(cljs.core.mapcat.cljs$core$IFn$_invoke$arity$variadic(cljs.core.identity,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([groups], 0)));
} else {
var take_n = (function (){var x__5090__auto__ = cnt_a;
var y__5091__auto__ = cnt_b;
return ((x__5090__auto__ < y__5091__auto__) ? x__5090__auto__ : y__5091__auto__);
})();
var head = cljs.core.subvec.cljs$core$IFn$_invoke$arity$3(groups,(0),take_n);
var mid = cljs.core.subvec.cljs$core$IFn$_invoke$arity$3(groups,take_n,cnt_a);
var tail = cljs.core.subvec.cljs$core$IFn$_invoke$arity$2(groups,cnt_a);
var G__8270 = cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.mapv.cljs$core$IFn$_invoke$arity$3(((function (groups,take_n,head,mid,tail,cnt_a,cnt_b){
return (function (a,b){
return cljs.core.into.cljs$core$IFn$_invoke$arity$2(a,b);
});})(groups,take_n,head,mid,tail,cnt_a,cnt_b))
,head,cljs.core.subvec.cljs$core$IFn$_invoke$arity$3(tail,(0),take_n)),cljs.core.into.cljs$core$IFn$_invoke$arity$2(mid,cljs.core.subvec.cljs$core$IFn$_invoke$arity$2(tail,take_n)));
groups = G__8270;
continue;
}
break;
}
})();
var rotated = (function (){var r = cljs.core.mod(rotation,n);
var v = cljs.core.vec(result);
return cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.subvec.cljs$core$IFn$_invoke$arity$2(v,r),cljs.core.subvec.cljs$core$IFn$_invoke$arity$3(v,(0),r));
})();
var values = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__7901_SHARP_){
if(cljs.core.truth_(p1__7901_SHARP_)){
return val;
} else {
return new cljs.core.Keyword(null,"_","_",1453416199);
}
}),rotated);
return repulse.core.seq_STAR_.cljs$core$IFn$_invoke$arity$1(values);
}));

(repulse.core.euclidean.cljs$lang$maxFixedArity = 4);

/**
 * Concatenate patterns: each plays for one full cycle, then the whole sequence loops.
 * Unlike seq* (which subdivides one cycle), cat* gives each pattern its own cycle.
 * (cat* [p1 p2 p3]) — 3-cycle loop: p1 for cycle 0, p2 for cycle 1, p3 for cycle 2.
 */
repulse.core.cat_STAR_ = (function repulse$core$cat_STAR_(pats){
var n = cljs.core.count(pats);
if((n === (0))){
return repulse.core.pattern((function (_){
return cljs.core.PersistentVector.EMPTY;
}));
} else {
return repulse.core.pattern((function (p__7975){
var map__7980 = p__7975;
var map__7980__$1 = cljs.core.__destructure_map(map__7980);
var sp = map__7980__$1;
var start = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7980__$1,new cljs.core.Keyword(null,"start","start",-355208981));
var end = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7980__$1,new cljs.core.Keyword(null,"end","end",-268185958));
var cycle = (Math.floor(repulse.core.rat__GT_float(start)) | (0));
var idx = cljs.core.mod(cycle,n);
var pat = cljs.core.nth.cljs$core$IFn$_invoke$arity$2(pats,idx);
return repulse.core.query(pat,sp);
}));
}
});
/**
 * Shift all events forward in time by amount (fraction of a cycle).
 * Queries the pattern at (start - offset, end - offset), then shifts events
 * back by +offset. Preserves :source for editor highlighting.
 * (late 0.25 pat) — delay by 1/4 cycle
 */
repulse.core.late = (function repulse$core$late(amount,pat){
var off = ((cljs.core.vector_QMARK_(amount))?amount:repulse.core.rat.cljs$core$IFn$_invoke$arity$2(((amount * (1000)) | (0)),(1000)));
return repulse.core.pattern((function (p__7988){
var map__7989 = p__7988;
var map__7989__$1 = cljs.core.__destructure_map(map__7989);
var start = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7989__$1,new cljs.core.Keyword(null,"start","start",-355208981));
var end = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__7989__$1,new cljs.core.Keyword(null,"end","end",-268185958));
var q_start = repulse.core.rat_(start,off);
var q_end = repulse.core.rat_(end,off);
var evs = repulse.core.query(pat,repulse.core.span(q_start,q_end));
return cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (e){
return cljs.core.update.cljs$core$IFn$_invoke$arity$3(cljs.core.update.cljs$core$IFn$_invoke$arity$3(e,new cljs.core.Keyword(null,"whole","whole",-1395468966),(function (p1__7981_SHARP_){
return repulse.core.span(repulse.core.rat_PLUS_(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(p1__7981_SHARP_),off),repulse.core.rat_PLUS_(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(p1__7981_SHARP_),off));
})),new cljs.core.Keyword(null,"part","part",77757738),(function (p1__7982_SHARP_){
return repulse.core.span(repulse.core.rat_PLUS_(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(p1__7982_SHARP_),off),repulse.core.rat_PLUS_(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(p1__7982_SHARP_),off));
}));
}),evs);
}));
});
/**
 * Shift all events backward in time by amount (fraction of a cycle).
 * Equivalent to (late (- amount) pat).
 * (early 0.25 pat) — advance by 1/4 cycle
 */
repulse.core.early = (function repulse$core$early(amount,pat){
var neg_off = ((cljs.core.vector_QMARK_(amount))?new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(- cljs.core.first(amount)),cljs.core.second(amount)], null):repulse.core.rat.cljs$core$IFn$_invoke$arity$2((- ((amount * (1000)) | (0))),(1000)));
return repulse.core.late(neg_off,pat);
});
/**
 * Deterministic hash of a cycle number. Returns 0–99.
 */
repulse.core.cycle_hash = (function repulse$core$cycle_hash(cycle){
return cljs.core.mod(((Math.abs(cycle) * (48271)) + (12345)),(100));
});
/**
 * Apply transform f to pat on cycles where (cycle-hash cycle) < (prob * 100).
 * prob is 0.0–1.0. Deterministic: same cycle number always makes the same choice.
 * (sometimes-by 0.5 rev pat) — reverse ~50% of cycles
 */
repulse.core.sometimes_by = (function repulse$core$sometimes_by(prob,f,pat){
var threshold = ((prob * (100)) | (0));
return repulse.core.pattern((function (sp){
var cycle = (Math.floor(repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(sp))) | (0));
if((repulse.core.cycle_hash(cycle) < threshold)){
return repulse.core.query((f.cljs$core$IFn$_invoke$arity$1 ? f.cljs$core$IFn$_invoke$arity$1(pat) : f.call(null,pat)),sp);
} else {
return repulse.core.query(pat,sp);
}
}));
});
/**
 * Apply transform on ~50% of cycles.
 * (sometimes rev pat)
 */
repulse.core.sometimes = (function repulse$core$sometimes(f,pat){
return repulse.core.sometimes_by(0.5,f,pat);
});
/**
 * Apply transform on ~75% of cycles.
 * (often (fast 2) pat)
 */
repulse.core.often = (function repulse$core$often(f,pat){
return repulse.core.sometimes_by(0.75,f,pat);
});
/**
 * Apply transform on ~25% of cycles.
 * (rarely rev pat)
 */
repulse.core.rarely = (function repulse$core$rarely(f,pat){
return repulse.core.sometimes_by(0.25,f,pat);
});
/**
 * Deterministic hash of an event's start position. Returns 0–99.
 * Uses the :whole start [numerator denominator] to seed.
 */
repulse.core.event_hash = (function repulse$core$event_hash(event){
var vec__8000 = new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"whole","whole",-1395468966).cljs$core$IFn$_invoke$arity$1(event));
var n = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8000,(0),null);
var d = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8000,(1),null);
return cljs.core.mod((((Math.abs(n) * (48271)) + (Math.abs(d) * (22543))) + (9137)),(100));
});
/**
 * Randomly drop events from pat with probability prob (0.0–1.0).
 * Uses deterministic hash of each event's time position.
 * (degrade-by 0.3 pat) — drop ~30% of events
 */
repulse.core.degrade_by = (function repulse$core$degrade_by(prob,pat){
var threshold = ((prob * (100)) | (0));
return repulse.core.pattern((function (sp){
return cljs.core.filter.cljs$core$IFn$_invoke$arity$2((function (p1__8003_SHARP_){
return (repulse.core.event_hash(p1__8003_SHARP_) >= threshold);
}),repulse.core.query(pat,sp));
}));
});
/**
 * Drop ~50% of events randomly. Shorthand for (degrade-by 0.5 pat).
 * (degrade (fast 4 (seq :hh :oh :hh :oh)))
 */
repulse.core.degrade = (function repulse$core$degrade(pat){
return repulse.core.degrade_by(0.5,pat);
});
/**
 * Pick one value from xs per cycle (deterministic based on cycle number).
 * Returns a pattern that produces one event per cycle.
 * (choose [:bd :sd :hh :oh])
 * Optional sources vector attaches :source to events for editor highlighting.
 */
repulse.core.choose = (function repulse$core$choose(var_args){
var G__8024 = arguments.length;
switch (G__8024) {
case 1:
return repulse.core.choose.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.core.choose.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.core.choose.cljs$core$IFn$_invoke$arity$1 = (function (xs){
return repulse.core.choose.cljs$core$IFn$_invoke$arity$2(xs,null);
}));

(repulse.core.choose.cljs$core$IFn$_invoke$arity$2 = (function (xs,sources){
var n = cljs.core.count(xs);
return repulse.core.pattern((function (p__8030){
var map__8032 = p__8030;
var map__8032__$1 = cljs.core.__destructure_map(map__8032);
var sp = map__8032__$1;
var start = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8032__$1,new cljs.core.Keyword(null,"start","start",-355208981));
var end = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8032__$1,new cljs.core.Keyword(null,"end","end",-268185958));
var start_c = (Math.floor(repulse.core.rat__GT_float(start)) | (0));
var end_c = (Math.ceil(repulse.core.rat__GT_float(end)) | (0));
var iter__5480__auto__ = (function repulse$core$iter__8038(s__8039){
return (new cljs.core.LazySeq(null,(function (){
var s__8039__$1 = s__8039;
while(true){
var temp__5804__auto__ = cljs.core.seq(s__8039__$1);
if(temp__5804__auto__){
var s__8039__$2 = temp__5804__auto__;
if(cljs.core.chunked_seq_QMARK_(s__8039__$2)){
var c__5478__auto__ = cljs.core.chunk_first(s__8039__$2);
var size__5479__auto__ = cljs.core.count(c__5478__auto__);
var b__8042 = cljs.core.chunk_buffer(size__5479__auto__);
if((function (){var i__8040 = (0);
while(true){
if((i__8040 < size__5479__auto__)){
var c = cljs.core._nth(c__5478__auto__,i__8040);
var idx = cljs.core.mod(repulse.core.cycle_hash(c),n);
var whole = repulse.core.cycle_span(c);
var part = repulse.core.span_intersect(whole,repulse.core.span(start,end));
if(cljs.core.truth_(part)){
cljs.core.chunk_append(b__8042,(function (){var base = repulse.core.event(cljs.core.nth.cljs$core$IFn$_invoke$arity$2(xs,idx),whole,part);
var src = (cljs.core.truth_(sources)?cljs.core.nth.cljs$core$IFn$_invoke$arity$3(sources,idx,null):null);
if(cljs.core.truth_(src)){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(base,new cljs.core.Keyword(null,"source","source",-433931539),src);
} else {
return base;
}
})());

var G__8338 = (i__8040 + (1));
i__8040 = G__8338;
continue;
} else {
var G__8339 = (i__8040 + (1));
i__8040 = G__8339;
continue;
}
} else {
return true;
}
break;
}
})()){
return cljs.core.chunk_cons(cljs.core.chunk(b__8042),repulse$core$iter__8038(cljs.core.chunk_rest(s__8039__$2)));
} else {
return cljs.core.chunk_cons(cljs.core.chunk(b__8042),null);
}
} else {
var c = cljs.core.first(s__8039__$2);
var idx = cljs.core.mod(repulse.core.cycle_hash(c),n);
var whole = repulse.core.cycle_span(c);
var part = repulse.core.span_intersect(whole,repulse.core.span(start,end));
if(cljs.core.truth_(part)){
return cljs.core.cons((function (){var base = repulse.core.event(cljs.core.nth.cljs$core$IFn$_invoke$arity$2(xs,idx),whole,part);
var src = (cljs.core.truth_(sources)?cljs.core.nth.cljs$core$IFn$_invoke$arity$3(sources,idx,null):null);
if(cljs.core.truth_(src)){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(base,new cljs.core.Keyword(null,"source","source",-433931539),src);
} else {
return base;
}
})(),repulse$core$iter__8038(cljs.core.rest(s__8039__$2)));
} else {
var G__8377 = cljs.core.rest(s__8039__$2);
s__8039__$1 = G__8377;
continue;
}
}
} else {
return null;
}
break;
}
}),null,null));
});
return iter__5480__auto__(cljs.core.range.cljs$core$IFn$_invoke$arity$2(start_c,end_c));
}));
}));

(repulse.core.choose.cljs$lang$maxFixedArity = 2);

/**
 * Weighted random choice per cycle. Takes a vector of [weight value] pairs.
 * Weights are relative (don't need to sum to 1.0).
 * (wchoose [[0.5 :bd] [0.3 :sd] [0.2 :hh]])
 * Optional sources vector attaches :source to events for editor highlighting.
 */
repulse.core.wchoose = (function repulse$core$wchoose(var_args){
var G__8068 = arguments.length;
switch (G__8068) {
case 1:
return repulse.core.wchoose.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.core.wchoose.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.core.wchoose.cljs$core$IFn$_invoke$arity$1 = (function (pairs){
return repulse.core.wchoose.cljs$core$IFn$_invoke$arity$2(pairs,null);
}));

(repulse.core.wchoose.cljs$core$IFn$_invoke$arity$2 = (function (pairs,sources){
var total = cljs.core.reduce.cljs$core$IFn$_invoke$arity$2(cljs.core._PLUS_,cljs.core.map.cljs$core$IFn$_invoke$arity$2(cljs.core.first,pairs));
var cumulative = cljs.core.reductions.cljs$core$IFn$_invoke$arity$2(cljs.core._PLUS_,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p1__8066_SHARP_){
return ((100) * (cljs.core.first(p1__8066_SHARP_) / total));
}),pairs));
var values = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(cljs.core.second,pairs);
return repulse.core.pattern((function (p__8071){
var map__8072 = p__8071;
var map__8072__$1 = cljs.core.__destructure_map(map__8072);
var sp = map__8072__$1;
var start = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8072__$1,new cljs.core.Keyword(null,"start","start",-355208981));
var end = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8072__$1,new cljs.core.Keyword(null,"end","end",-268185958));
var start_c = (Math.floor(repulse.core.rat__GT_float(start)) | (0));
var end_c = (Math.ceil(repulse.core.rat__GT_float(end)) | (0));
var iter__5480__auto__ = (function repulse$core$iter__8074(s__8075){
return (new cljs.core.LazySeq(null,(function (){
var s__8075__$1 = s__8075;
while(true){
var temp__5804__auto__ = cljs.core.seq(s__8075__$1);
if(temp__5804__auto__){
var s__8075__$2 = temp__5804__auto__;
if(cljs.core.chunked_seq_QMARK_(s__8075__$2)){
var c__5478__auto__ = cljs.core.chunk_first(s__8075__$2);
var size__5479__auto__ = cljs.core.count(c__5478__auto__);
var b__8077 = cljs.core.chunk_buffer(size__5479__auto__);
if((function (){var i__8076 = (0);
while(true){
if((i__8076 < size__5479__auto__)){
var c = cljs.core._nth(c__5478__auto__,i__8076);
var h = repulse.core.cycle_hash(c);
var idx = (function (){var or__5002__auto__ = cljs.core.first(cljs.core.keep_indexed.cljs$core$IFn$_invoke$arity$2(((function (i__8076,s__8075__$1,h,c,c__5478__auto__,size__5479__auto__,b__8077,s__8075__$2,temp__5804__auto__,start_c,end_c,map__8072,map__8072__$1,sp,start,end,total,cumulative,values){
return (function (i,thresh){
if((h < thresh)){
return i;
} else {
return null;
}
});})(i__8076,s__8075__$1,h,c,c__5478__auto__,size__5479__auto__,b__8077,s__8075__$2,temp__5804__auto__,start_c,end_c,map__8072,map__8072__$1,sp,start,end,total,cumulative,values))
,cumulative));
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (cljs.core.count(values) - (1));
}
})();
var whole = repulse.core.cycle_span(c);
var part = repulse.core.span_intersect(whole,repulse.core.span(start,end));
if(cljs.core.truth_(part)){
cljs.core.chunk_append(b__8077,(function (){var base = repulse.core.event(cljs.core.nth.cljs$core$IFn$_invoke$arity$2(values,idx),whole,part);
var src = (cljs.core.truth_(sources)?cljs.core.nth.cljs$core$IFn$_invoke$arity$3(sources,idx,null):null);
if(cljs.core.truth_(src)){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(base,new cljs.core.Keyword(null,"source","source",-433931539),src);
} else {
return base;
}
})());

var G__8436 = (i__8076 + (1));
i__8076 = G__8436;
continue;
} else {
var G__8438 = (i__8076 + (1));
i__8076 = G__8438;
continue;
}
} else {
return true;
}
break;
}
})()){
return cljs.core.chunk_cons(cljs.core.chunk(b__8077),repulse$core$iter__8074(cljs.core.chunk_rest(s__8075__$2)));
} else {
return cljs.core.chunk_cons(cljs.core.chunk(b__8077),null);
}
} else {
var c = cljs.core.first(s__8075__$2);
var h = repulse.core.cycle_hash(c);
var idx = (function (){var or__5002__auto__ = cljs.core.first(cljs.core.keep_indexed.cljs$core$IFn$_invoke$arity$2(((function (s__8075__$1,h,c,s__8075__$2,temp__5804__auto__,start_c,end_c,map__8072,map__8072__$1,sp,start,end,total,cumulative,values){
return (function (i,thresh){
if((h < thresh)){
return i;
} else {
return null;
}
});})(s__8075__$1,h,c,s__8075__$2,temp__5804__auto__,start_c,end_c,map__8072,map__8072__$1,sp,start,end,total,cumulative,values))
,cumulative));
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (cljs.core.count(values) - (1));
}
})();
var whole = repulse.core.cycle_span(c);
var part = repulse.core.span_intersect(whole,repulse.core.span(start,end));
if(cljs.core.truth_(part)){
return cljs.core.cons((function (){var base = repulse.core.event(cljs.core.nth.cljs$core$IFn$_invoke$arity$2(values,idx),whole,part);
var src = (cljs.core.truth_(sources)?cljs.core.nth.cljs$core$IFn$_invoke$arity$3(sources,idx,null):null);
if(cljs.core.truth_(src)){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(base,new cljs.core.Keyword(null,"source","source",-433931539),src);
} else {
return base;
}
})(),repulse$core$iter__8074(cljs.core.rest(s__8075__$2)));
} else {
var G__8447 = cljs.core.rest(s__8075__$2);
s__8075__$1 = G__8447;
continue;
}
}
} else {
return null;
}
break;
}
}),null,null));
});
return iter__5480__auto__(cljs.core.range.cljs$core$IFn$_invoke$arity$2(start_c,end_c));
}));
}));

(repulse.core.wchoose.cljs$lang$maxFixedArity = 2);

/**
 * Layer the original pattern with a time-shifted, transformed copy.
 * (off 0.125 (fast 2) pat) — original + 1/8-cycle-shifted double-speed copy
 */
repulse.core.off = (function repulse$core$off(amount,f,pat){
return repulse.core.stack_STAR_(new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [pat,repulse.core.late(amount,(f.cljs$core$IFn$_invoke$arity$1 ? f.cljs$core$IFn$_invoke$arity$1(pat) : f.call(null,pat)))], null));
});

//# sourceMappingURL=repulse.core.js.map
