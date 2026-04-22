goog.provide('repulse.lisp.eval');
repulse.lisp.eval.sourced_QMARK_ = (function repulse$lisp$eval$sourced_QMARK_(x){
return (x instanceof repulse.lisp.reader.SourcedVal);
});
repulse.lisp.eval.unwrap = (function repulse$lisp$eval$unwrap(x){
if(repulse.lisp.eval.sourced_QMARK_(x)){
return new cljs.core.Keyword(null,"v","v",21465059).cljs$core$IFn$_invoke$arity$1(x);
} else {
return x;
}
});
repulse.lisp.eval.source_of = (function repulse$lisp$eval$source_of(x){
if(repulse.lisp.eval.sourced_QMARK_(x)){
return new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(x);
} else {
return new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(cljs.core.meta(x));
}
});
/**
 * Coerce a value to a number. Rational pairs [n d] → n/d (float).
 */
repulse.lisp.eval.__GT_num = (function repulse$lisp$eval$__GT_num(x){
var v = repulse.lisp.eval.unwrap(x);
if(((cljs.core.vector_QMARK_(v)) && (((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2((2),cljs.core.count(v))) && (((typeof cljs.core.first(v) === 'number') && (typeof cljs.core.second(v) === 'number'))))))){
return (cljs.core.first(v) / cljs.core.second(v));
} else {
return v;
}
});
repulse.lisp.eval.levenshtein = (function repulse$lisp$eval$levenshtein(a,b){
var m = cljs.core.count(a);
var n = cljs.core.count(b);
var row = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.vec(cljs.core.range.cljs$core$IFn$_invoke$arity$1((n + (1)))));
var n__5593__auto___8480 = m;
var i_8481 = (0);
while(true){
if((i_8481 < n__5593__auto___8480)){
var prev_8482 = cljs.core.deref(row);
var cur_8483 = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [(i_8481 + (1))], null));
var n__5593__auto___8484__$1 = n;
var j_8485 = (0);
while(true){
if((j_8485 < n__5593__auto___8484__$1)){
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(cur_8483,cljs.core.conj,((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(cljs.core.nth.cljs$core$IFn$_invoke$arity$2(a,i_8481),cljs.core.nth.cljs$core$IFn$_invoke$arity$2(b,j_8485)))?cljs.core.nth.cljs$core$IFn$_invoke$arity$2(prev_8482,j_8485):((function (){var x__5090__auto__ = (function (){var x__5090__auto__ = cljs.core.nth.cljs$core$IFn$_invoke$arity$2(prev_8482,(j_8485 + (1)));
var y__5091__auto__ = cljs.core.last(cljs.core.deref(cur_8483));
return ((x__5090__auto__ < y__5091__auto__) ? x__5090__auto__ : y__5091__auto__);
})();
var y__5091__auto__ = cljs.core.nth.cljs$core$IFn$_invoke$arity$2(prev_8482,j_8485);
return ((x__5090__auto__ < y__5091__auto__) ? x__5090__auto__ : y__5091__auto__);
})() + (1))));

var G__8486 = (j_8485 + (1));
j_8485 = G__8486;
continue;
} else {
}
break;
}

cljs.core.reset_BANG_(row,cljs.core.deref(cur_8483));

var G__8487 = (i_8481 + (1));
i_8481 = G__8487;
continue;
} else {
}
break;
}

return cljs.core.last(cljs.core.deref(row));
});
repulse.lisp.eval.typo_hint = (function repulse$lisp$eval$typo_hint(name,known){
if(cljs.core.seq(known)){
var best = cljs.core.apply.cljs$core$IFn$_invoke$arity$3(cljs.core.min_key,(function (p1__8300_SHARP_){
return repulse.lisp.eval.levenshtein(name,cljs.core.str.cljs$core$IFn$_invoke$arity$1(p1__8300_SHARP_));
}),known);
if((repulse.lisp.eval.levenshtein(name,cljs.core.str.cljs$core$IFn$_invoke$arity$1(best)) <= (3))){
return best;
} else {
return null;
}
} else {
return null;
}
});
repulse.lisp.eval.make_closure = (function repulse$lisp$eval$make_closure(params,body,env){
return (function() { 
var G__8488__delegate = function (args){
var bound = cljs.core.zipmap(cljs.core.map.cljs$core$IFn$_invoke$arity$2(cljs.core.str,params),args);
var local = cljs.core.merge.cljs$core$IFn$_invoke$arity$variadic(cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([env,bound], 0));
return cljs.core.last(cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p1__8305_SHARP_){
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(p1__8305_SHARP_,local) : repulse.lisp.eval.eval_form.call(null,p1__8305_SHARP_,local));
}),body));
};
var G__8488 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8489__i = 0, G__8489__a = new Array(arguments.length -  0);
while (G__8489__i < G__8489__a.length) {G__8489__a[G__8489__i] = arguments[G__8489__i + 0]; ++G__8489__i;}
  args = new cljs.core.IndexedSeq(G__8489__a,0,null);
} 
return G__8488__delegate.call(this,args);};
G__8488.cljs$lang$maxFixedArity = 0;
G__8488.cljs$lang$applyTo = (function (arglist__8490){
var args = cljs.core.seq(arglist__8490);
return G__8488__delegate(args);
});
G__8488.cljs$core$IFn$_invoke$arity$variadic = G__8488__delegate;
return G__8488;
})()
;
});
repulse.lisp.eval.recur_sentinel_type = new cljs.core.Keyword("repulse.lisp.eval","recur-sentinel","repulse.lisp.eval/recur-sentinel",1737056620);
repulse.lisp.eval.recur_sentinel = (function repulse$lisp$eval$recur_sentinel(args){
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword("repulse.lisp.eval","type","repulse.lisp.eval/type",1737178212),repulse.lisp.eval.recur_sentinel_type,new cljs.core.Keyword("repulse.lisp.eval","args","repulse.lisp.eval/args",-404281384),args], null);
});
repulse.lisp.eval.recur_sentinel_QMARK_ = (function repulse$lisp$eval$recur_sentinel_QMARK_(x){
return ((cljs.core.map_QMARK_(x)) && (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword("repulse.lisp.eval","type","repulse.lisp.eval/type",1737178212).cljs$core$IFn$_invoke$arity$1(x),repulse.lisp.eval.recur_sentinel_type)));
});
/**
 * Walk a quasiquoted form. (unquote x) → evaluate x.
 * (splice-unquote x) → splice evaluated x into surrounding list/vector.
 */
repulse.lisp.eval.expand_quasiquote = (function repulse$lisp$eval$expand_quasiquote(form,env){
if(((cljs.core.seq_QMARK_(form)) && (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(cljs.core.first(form),cljs.core.symbol.cljs$core$IFn$_invoke$arity$1("unquote"))))){
var G__8313 = cljs.core.second(form);
var G__8314 = env;
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(G__8313,G__8314) : repulse.lisp.eval.eval_form.call(null,G__8313,G__8314));
} else {
if(cljs.core.seq_QMARK_(form)){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.list,cljs.core.reduce.cljs$core$IFn$_invoke$arity$3((function (acc,item){
if(((cljs.core.seq_QMARK_(item)) && (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(cljs.core.first(item),cljs.core.symbol.cljs$core$IFn$_invoke$arity$1("splice-unquote"))))){
return cljs.core.into.cljs$core$IFn$_invoke$arity$2(acc,(function (){var G__8321 = cljs.core.second(item);
var G__8322 = env;
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(G__8321,G__8322) : repulse.lisp.eval.eval_form.call(null,G__8321,G__8322));
})());
} else {
return cljs.core.conj.cljs$core$IFn$_invoke$arity$2(acc,(repulse.lisp.eval.expand_quasiquote.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.expand_quasiquote.cljs$core$IFn$_invoke$arity$2(item,env) : repulse.lisp.eval.expand_quasiquote.call(null,item,env)));
}
}),cljs.core.PersistentVector.EMPTY,form));
} else {
if(cljs.core.vector_QMARK_(form)){
return cljs.core.reduce.cljs$core$IFn$_invoke$arity$3((function (acc,item){
if(((cljs.core.seq_QMARK_(item)) && (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(cljs.core.first(item),cljs.core.symbol.cljs$core$IFn$_invoke$arity$1("splice-unquote"))))){
return cljs.core.into.cljs$core$IFn$_invoke$arity$2(acc,(function (){var G__8328 = cljs.core.second(item);
var G__8329 = env;
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(G__8328,G__8329) : repulse.lisp.eval.eval_form.call(null,G__8328,G__8329));
})());
} else {
return cljs.core.conj.cljs$core$IFn$_invoke$arity$2(acc,(repulse.lisp.eval.expand_quasiquote.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.expand_quasiquote.cljs$core$IFn$_invoke$arity$2(item,env) : repulse.lisp.eval.expand_quasiquote.call(null,item,env)));
}
}),cljs.core.PersistentVector.EMPTY,form);
} else {
return form;

}
}
}
});
repulse.lisp.eval.eval_form = (function repulse$lisp$eval$eval_form(form,env){
if(repulse.lisp.eval.sourced_QMARK_(form)){
return form;
} else {
if(((typeof form === 'number') || (((typeof form === 'string') || ((((form instanceof cljs.core.Keyword)) || (((form === true) || (((form === false) || ((form == null)))))))))))){
return form;
} else {
if(cljs.core.vector_QMARK_(form)){
return cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__8330_SHARP_){
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(p1__8330_SHARP_,env) : repulse.lisp.eval.eval_form.call(null,p1__8330_SHARP_,env));
}),form);
} else {
if(cljs.core.map_QMARK_(form)){
return cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.PersistentArrayMap.EMPTY,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p__8340){
var vec__8341 = p__8340;
var k = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8341,(0),null);
var v = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8341,(1),null);
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [repulse.lisp.eval.unwrap((repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(k,env) : repulse.lisp.eval.eval_form.call(null,k,env))),repulse.lisp.eval.unwrap((repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(v,env) : repulse.lisp.eval.eval_form.call(null,v,env)))], null);
}),form));
} else {
if((form instanceof cljs.core.Symbol)){
var n = cljs.core.str.cljs$core$IFn$_invoke$arity$1(form);
var defs = (function (){var G__8346 = new cljs.core.Keyword(null,"*defs*","*defs*",1742801364).cljs$core$IFn$_invoke$arity$1(env);
if((G__8346 == null)){
return null;
} else {
return cljs.core.deref(G__8346);
}
})();
var src = repulse.lisp.eval.source_of(form);
if(cljs.core.contains_QMARK_(env,n)){
return cljs.core.get.cljs$core$IFn$_invoke$arity$2(env,n);
} else {
if(cljs.core.contains_QMARK_(defs,n)){
return cljs.core.get.cljs$core$IFn$_invoke$arity$2(defs,n);
} else {
var known = cljs.core.concat.cljs$core$IFn$_invoke$arity$2(cljs.core.filter.cljs$core$IFn$_invoke$arity$2(cljs.core.string_QMARK_,cljs.core.keys(env)),cljs.core.filter.cljs$core$IFn$_invoke$arity$2(cljs.core.string_QMARK_,cljs.core.keys(defs)));
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2(["Undefined symbol: ",n,(function (){var temp__5804__auto__ = repulse.lisp.eval.typo_hint(n,known);
if(cljs.core.truth_(temp__5804__auto__)){
var h = temp__5804__auto__;
return [" \u2014 did you mean ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(h),"?"].join('');
} else {
return null;
}
})()].join(''),new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"eval-error","eval-error",466139568),new cljs.core.Keyword(null,"from","from",1815293044),new cljs.core.Keyword(null,"from","from",1815293044).cljs$core$IFn$_invoke$arity$1(src),new cljs.core.Keyword(null,"to","to",192099007),new cljs.core.Keyword(null,"to","to",192099007).cljs$core$IFn$_invoke$arity$1(src)], null));

}
}
} else {
if(cljs.core.seq_QMARK_(form)){
var vec__8348 = form;
var seq__8349 = cljs.core.seq(vec__8348);
var first__8350 = cljs.core.first(seq__8349);
var seq__8349__$1 = cljs.core.next(seq__8349);
var head = first__8350;
var tail = seq__8349__$1;
var G__8351 = (((head instanceof cljs.core.Symbol))?cljs.core.str.cljs$core$IFn$_invoke$arity$1(head):null);
switch (G__8351) {
case "def":
var vec__8352 = tail;
var sym = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8352,(0),null);
var val_form = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8352,(1),null);
var v = (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(val_form,env) : repulse.lisp.eval.eval_form.call(null,val_form,env));
var temp__5804__auto___8512 = new cljs.core.Keyword(null,"*defs*","*defs*",1742801364).cljs$core$IFn$_invoke$arity$1(env);
if(cljs.core.truth_(temp__5804__auto___8512)){
var defs_8517 = temp__5804__auto___8512;
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(defs_8517,cljs.core.assoc,cljs.core.str.cljs$core$IFn$_invoke$arity$1(sym),v);
} else {
}

return v;

break;
case "defn":
var vec__8355 = tail;
var seq__8356 = cljs.core.seq(vec__8355);
var first__8357 = cljs.core.first(seq__8356);
var seq__8356__$1 = cljs.core.next(seq__8356);
var name_sym = first__8357;
var first__8357__$1 = cljs.core.first(seq__8356__$1);
var seq__8356__$2 = cljs.core.next(seq__8356__$1);
var params = first__8357__$1;
var body = seq__8356__$2;
var f = repulse.lisp.eval.make_closure(params,body,env);
var temp__5804__auto___8520 = new cljs.core.Keyword(null,"*defs*","*defs*",1742801364).cljs$core$IFn$_invoke$arity$1(env);
if(cljs.core.truth_(temp__5804__auto___8520)){
var defs_8522 = temp__5804__auto___8520;
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(defs_8522,cljs.core.assoc,cljs.core.str.cljs$core$IFn$_invoke$arity$1(name_sym),f);
} else {
}

return f;

break;
case "let":
var vec__8358 = tail;
var seq__8359 = cljs.core.seq(vec__8358);
var first__8360 = cljs.core.first(seq__8359);
var seq__8359__$1 = cljs.core.next(seq__8359);
var bindings = first__8360;
var body = seq__8359__$1;
var pairs = cljs.core.partition.cljs$core$IFn$_invoke$arity$2((2),bindings);
var local = env;
while(true){
if(cljs.core.empty_QMARK_(pairs)){
return cljs.core.last(cljs.core.map.cljs$core$IFn$_invoke$arity$2(((function (pairs,local,vec__8358,seq__8359,first__8360,seq__8359__$1,bindings,body,G__8351,vec__8348,seq__8349,first__8350,seq__8349__$1,head,tail){
return (function (p1__8331_SHARP_){
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(p1__8331_SHARP_,local) : repulse.lisp.eval.eval_form.call(null,p1__8331_SHARP_,local));
});})(pairs,local,vec__8358,seq__8359,first__8360,seq__8359__$1,bindings,body,G__8351,vec__8348,seq__8349,first__8350,seq__8349__$1,head,tail))
,body));
} else {
var vec__8361 = cljs.core.first(pairs);
var s = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8361,(0),null);
var vf = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8361,(1),null);
var G__8535 = cljs.core.rest(pairs);
var G__8536 = cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(local,cljs.core.str.cljs$core$IFn$_invoke$arity$1(s),(repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(vf,local) : repulse.lisp.eval.eval_form.call(null,vf,local)));
pairs = G__8535;
local = G__8536;
continue;
}
break;
}

break;
case "fn":
case "lambda":
var vec__8364 = tail;
var seq__8365 = cljs.core.seq(vec__8364);
var first__8366 = cljs.core.first(seq__8365);
var seq__8365__$1 = cljs.core.next(seq__8365);
var params = first__8366;
var body = seq__8365__$1;
return repulse.lisp.eval.make_closure(params,body,env);

break;
case "if":
var vec__8367 = tail;
var c = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8367,(0),null);
var t = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8367,(1),null);
var e = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8367,(2),null);
if(cljs.core.truth_(repulse.lisp.eval.unwrap((repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(c,env) : repulse.lisp.eval.eval_form.call(null,c,env))))){
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(t,env) : repulse.lisp.eval.eval_form.call(null,t,env));
} else {
if(cljs.core.truth_(e)){
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(e,env) : repulse.lisp.eval.eval_form.call(null,e,env));
} else {
return null;
}
}

break;
case "and":
var forms = tail;
while(true){
if(cljs.core.empty_QMARK_(forms)){
return true;
} else {
var value = (function (){var G__8370 = cljs.core.first(forms);
var G__8371 = env;
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(G__8370,G__8371) : repulse.lisp.eval.eval_form.call(null,G__8370,G__8371));
})();
if(cljs.core.empty_QMARK_(cljs.core.rest(forms))){
return value;
} else {
if(cljs.core.truth_(repulse.lisp.eval.unwrap(value))){
var G__8540 = cljs.core.rest(forms);
forms = G__8540;
continue;
} else {
return value;
}
}
}
break;
}

break;
case "or":
var forms = tail;
while(true){
if(cljs.core.empty_QMARK_(forms)){
return null;
} else {
var value = (function (){var G__8372 = cljs.core.first(forms);
var G__8373 = env;
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(G__8372,G__8373) : repulse.lisp.eval.eval_form.call(null,G__8372,G__8373));
})();
if(cljs.core.truth_((function (){var or__5002__auto__ = repulse.lisp.eval.unwrap(value);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return cljs.core.empty_QMARK_(cljs.core.rest(forms));
}
})())){
return value;
} else {
var G__8543 = cljs.core.rest(forms);
forms = G__8543;
continue;
}
}
break;
}

break;
case "quote":
return cljs.core.first(tail);

break;
case "do":
return cljs.core.last(cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p1__8332_SHARP_){
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(p1__8332_SHARP_,env) : repulse.lisp.eval.eval_form.call(null,p1__8332_SHARP_,env));
}),tail));

break;
case "->>":
return cljs.core.reduce.cljs$core$IFn$_invoke$arity$3((function (acc,form__$1){
if(cljs.core.seq_QMARK_(form__$1)){
var vec__8374 = form__$1;
var seq__8375 = cljs.core.seq(vec__8374);
var first__8376 = cljs.core.first(seq__8375);
var seq__8375__$1 = cljs.core.next(seq__8375);
var fhead = first__8376;
var fargs = seq__8375__$1;
var f = (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(fhead,env) : repulse.lisp.eval.eval_form.call(null,fhead,env));
var args = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__8333_SHARP_){
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(p1__8333_SHARP_,env) : repulse.lisp.eval.eval_form.call(null,p1__8333_SHARP_,env));
}),fargs);
if(cljs.core.fn_QMARK_(f)){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(f,cljs.core.concat.cljs$core$IFn$_invoke$arity$2(args,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [acc], null)));
} else {
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2([cljs.core.pr_str.cljs$core$IFn$_invoke$arity$variadic(cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([fhead], 0))," is not a function"].join(''),new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"eval-error","eval-error",466139568)], null));
}
} else {
var f = (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(form__$1,env) : repulse.lisp.eval.eval_form.call(null,form__$1,env));
if(cljs.core.fn_QMARK_(f)){
return (f.cljs$core$IFn$_invoke$arity$1 ? f.cljs$core$IFn$_invoke$arity$1(acc) : f.call(null,acc));
} else {
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2([cljs.core.pr_str.cljs$core$IFn$_invoke$arity$variadic(cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([form__$1], 0))," is not a function"].join(''),new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"eval-error","eval-error",466139568)], null));
}
}
}),(function (){var G__8378 = cljs.core.first(tail);
var G__8379 = env;
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(G__8378,G__8379) : repulse.lisp.eval.eval_form.call(null,G__8378,G__8379));
})(),cljs.core.rest(tail));

break;
case "->":
return cljs.core.reduce.cljs$core$IFn$_invoke$arity$3((function (acc,form__$1){
if(cljs.core.seq_QMARK_(form__$1)){
var vec__8380 = form__$1;
var seq__8381 = cljs.core.seq(vec__8380);
var first__8382 = cljs.core.first(seq__8381);
var seq__8381__$1 = cljs.core.next(seq__8381);
var fhead = first__8382;
var fargs = seq__8381__$1;
var f = (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(fhead,env) : repulse.lisp.eval.eval_form.call(null,fhead,env));
var args = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__8334_SHARP_){
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(p1__8334_SHARP_,env) : repulse.lisp.eval.eval_form.call(null,p1__8334_SHARP_,env));
}),fargs);
if(cljs.core.fn_QMARK_(f)){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$3(f,acc,args);
} else {
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2([cljs.core.pr_str.cljs$core$IFn$_invoke$arity$variadic(cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([fhead], 0))," is not a function"].join(''),new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"eval-error","eval-error",466139568)], null));
}
} else {
var f = (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(form__$1,env) : repulse.lisp.eval.eval_form.call(null,form__$1,env));
if(cljs.core.fn_QMARK_(f)){
return (f.cljs$core$IFn$_invoke$arity$1 ? f.cljs$core$IFn$_invoke$arity$1(acc) : f.call(null,acc));
} else {
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2([cljs.core.pr_str.cljs$core$IFn$_invoke$arity$variadic(cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([form__$1], 0))," is not a function"].join(''),new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"eval-error","eval-error",466139568)], null));
}
}
}),(function (){var G__8386 = cljs.core.first(tail);
var G__8387 = env;
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(G__8386,G__8387) : repulse.lisp.eval.eval_form.call(null,G__8386,G__8387));
})(),cljs.core.rest(tail));

break;
case "quasiquote":
return repulse.lisp.eval.expand_quasiquote(cljs.core.first(tail),env);

break;
case "defmacro":
var vec__8392 = tail;
var seq__8393 = cljs.core.seq(vec__8392);
var first__8394 = cljs.core.first(seq__8393);
var seq__8393__$1 = cljs.core.next(seq__8393);
var name_sym = first__8394;
var first__8394__$1 = cljs.core.first(seq__8393__$1);
var seq__8393__$2 = cljs.core.next(seq__8393__$1);
var params = first__8394__$1;
var body = seq__8393__$2;
var macro_fn = (function() { 
var G__8549__delegate = function (args){
var bound = cljs.core.zipmap(cljs.core.map.cljs$core$IFn$_invoke$arity$2(cljs.core.str,params),args);
var local = cljs.core.merge.cljs$core$IFn$_invoke$arity$variadic(cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([env,bound], 0));
return cljs.core.last(cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p1__8335_SHARP_){
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(p1__8335_SHARP_,local) : repulse.lisp.eval.eval_form.call(null,p1__8335_SHARP_,local));
}),body));
};
var G__8549 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8551__i = 0, G__8551__a = new Array(arguments.length -  0);
while (G__8551__i < G__8551__a.length) {G__8551__a[G__8551__i] = arguments[G__8551__i + 0]; ++G__8551__i;}
  args = new cljs.core.IndexedSeq(G__8551__a,0,null);
} 
return G__8549__delegate.call(this,args);};
G__8549.cljs$lang$maxFixedArity = 0;
G__8549.cljs$lang$applyTo = (function (arglist__8552){
var args = cljs.core.seq(arglist__8552);
return G__8549__delegate(args);
});
G__8549.cljs$core$IFn$_invoke$arity$variadic = G__8549__delegate;
return G__8549;
})()
;
var temp__5804__auto___8553 = new cljs.core.Keyword(null,"*macros*","*macros*",1376644506).cljs$core$IFn$_invoke$arity$1(env);
if(cljs.core.truth_(temp__5804__auto___8553)){
var macros_8554 = temp__5804__auto___8553;
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(macros_8554,cljs.core.assoc,cljs.core.str.cljs$core$IFn$_invoke$arity$1(name_sym),macro_fn);
} else {
}

return null;

break;
case "defsynth":
var vec__8396 = tail;
var seq__8397 = cljs.core.seq(vec__8396);
var first__8398 = cljs.core.first(seq__8397);
var seq__8397__$1 = cljs.core.next(seq__8397);
var name_sym = first__8398;
var first__8398__$1 = cljs.core.first(seq__8397__$1);
var seq__8397__$2 = cljs.core.next(seq__8397__$1);
var params = first__8398__$1;
var body = seq__8397__$2;
var synth_name = cljs.core.keyword.cljs$core$IFn$_invoke$arity$1(cljs.core.str.cljs$core$IFn$_invoke$arity$1(name_sym));
var param_names = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(cljs.core.str,params);
var temp__5804__auto___8559 = new cljs.core.Keyword(null,"*synths*","*synths*",591067875).cljs$core$IFn$_invoke$arity$1(env);
if(cljs.core.truth_(temp__5804__auto___8559)){
var synths_8560 = temp__5804__auto___8559;
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(synths_8560,cljs.core.assoc,synth_name,new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"params","params",710516235),param_names,new cljs.core.Keyword(null,"body","body",-2049205669),body,new cljs.core.Keyword(null,"env","env",-1815813235),env], null));
} else {
}

var temp__5804__auto___8561 = new cljs.core.Keyword(null,"*register-synth-fn*","*register-synth-fn*",-395091119).cljs$core$IFn$_invoke$arity$1(env);
if(cljs.core.truth_(temp__5804__auto___8561)){
var reg_fn_8562 = temp__5804__auto___8561;
(reg_fn_8562.cljs$core$IFn$_invoke$arity$4 ? reg_fn_8562.cljs$core$IFn$_invoke$arity$4(synth_name,param_names,body,env) : reg_fn_8562.call(null,synth_name,param_names,body,env));
} else {
}

var temp__5804__auto___8563 = new cljs.core.Keyword(null,"*defs*","*defs*",1742801364).cljs$core$IFn$_invoke$arity$1(env);
if(cljs.core.truth_(temp__5804__auto___8563)){
var defs_8564 = temp__5804__auto___8563;
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(defs_8564,cljs.core.assoc,cljs.core.str.cljs$core$IFn$_invoke$arity$1(name_sym),synth_name);
} else {
}

return synth_name;

break;
case "loop":
var vec__8400 = tail;
var seq__8401 = cljs.core.seq(vec__8400);
var first__8402 = cljs.core.first(seq__8401);
var seq__8401__$1 = cljs.core.next(seq__8401);
var bindings = first__8402;
var body = seq__8401__$1;
var pairs = cljs.core.partition.cljs$core$IFn$_invoke$arity$2((2),bindings);
var binding_names = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p__8403){
var vec__8404 = p__8403;
var s = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8404,(0),null);
var _ = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8404,(1),null);
return cljs.core.str.cljs$core$IFn$_invoke$arity$1(s);
}),pairs);
var current_vals = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(((function (vec__8400,seq__8401,first__8402,seq__8401__$1,bindings,body,pairs,binding_names,G__8351,vec__8348,seq__8349,first__8350,seq__8349__$1,head,tail){
return (function (p__8416){
var vec__8417 = p__8416;
var _ = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8417,(0),null);
var vf = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8417,(1),null);
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(vf,env) : repulse.lisp.eval.eval_form.call(null,vf,env));
});})(vec__8400,seq__8401,first__8402,seq__8401__$1,bindings,body,pairs,binding_names,G__8351,vec__8348,seq__8349,first__8350,seq__8349__$1,head,tail))
,pairs);
while(true){
var local = cljs.core.reduce.cljs$core$IFn$_invoke$arity$3(((function (current_vals,vec__8400,seq__8401,first__8402,seq__8401__$1,bindings,body,pairs,binding_names,G__8351,vec__8348,seq__8349,first__8350,seq__8349__$1,head,tail){
return (function (e,p__8421){
var vec__8422 = p__8421;
var n = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8422,(0),null);
var v = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8422,(1),null);
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(e,n,v);
});})(current_vals,vec__8400,seq__8401,first__8402,seq__8401__$1,bindings,body,pairs,binding_names,G__8351,vec__8348,seq__8349,first__8350,seq__8349__$1,head,tail))
,env,cljs.core.map.cljs$core$IFn$_invoke$arity$3(cljs.core.vector,binding_names,current_vals));
var result = cljs.core.reduce.cljs$core$IFn$_invoke$arity$3(((function (current_vals,local,vec__8400,seq__8401,first__8402,seq__8401__$1,bindings,body,pairs,binding_names,G__8351,vec__8348,seq__8349,first__8350,seq__8349__$1,head,tail){
return (function (_,form__$1){
var r = (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(form__$1,local) : repulse.lisp.eval.eval_form.call(null,form__$1,local));
if(repulse.lisp.eval.recur_sentinel_QMARK_(r)){
return cljs.core.reduced(r);
} else {
return r;
}
});})(current_vals,local,vec__8400,seq__8401,first__8402,seq__8401__$1,bindings,body,pairs,binding_names,G__8351,vec__8348,seq__8349,first__8350,seq__8349__$1,head,tail))
,null,body);
if(repulse.lisp.eval.recur_sentinel_QMARK_(result)){
var G__8581 = new cljs.core.Keyword("repulse.lisp.eval","args","repulse.lisp.eval/args",-404281384).cljs$core$IFn$_invoke$arity$1(result);
current_vals = G__8581;
continue;
} else {
return result;
}
break;
}

break;
case "recur":
return repulse.lisp.eval.recur_sentinel(cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__8336_SHARP_){
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(p1__8336_SHARP_,env) : repulse.lisp.eval.eval_form.call(null,p1__8336_SHARP_,env));
}),tail));

break;
default:
var head_name = (((head instanceof cljs.core.Symbol))?cljs.core.str.cljs$core$IFn$_invoke$arity$1(head):null);
var macros = (function (){var G__8425 = new cljs.core.Keyword(null,"*macros*","*macros*",1376644506).cljs$core$IFn$_invoke$arity$1(env);
if((G__8425 == null)){
return null;
} else {
return cljs.core.deref(G__8425);
}
})();
var temp__5802__auto__ = (function (){var and__5000__auto__ = head_name;
if(cljs.core.truth_(and__5000__auto__)){
var and__5000__auto____$1 = macros;
if(cljs.core.truth_(and__5000__auto____$1)){
return cljs.core.get.cljs$core$IFn$_invoke$arity$2(macros,head_name);
} else {
return and__5000__auto____$1;
}
} else {
return and__5000__auto__;
}
})();
if(cljs.core.truth_(temp__5802__auto__)){
var macro_fn = temp__5802__auto__;
var expanded = cljs.core.apply.cljs$core$IFn$_invoke$arity$2(macro_fn,tail);
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(expanded,env) : repulse.lisp.eval.eval_form.call(null,expanded,env));
} else {
var f = (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(head,env) : repulse.lisp.eval.eval_form.call(null,head,env));
var src = repulse.lisp.eval.source_of(head);
if(cljs.core.fn_QMARK_(f)){
try{return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(f,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p1__8337_SHARP_){
return (repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.eval.eval_form.cljs$core$IFn$_invoke$arity$2(p1__8337_SHARP_,env) : repulse.lisp.eval.eval_form.call(null,p1__8337_SHARP_,env));
}),tail));
}catch (e8427){var e = e8427;
var data = cljs.core.ex_data(e);
var loc = ((cljs.core.contains_QMARK_(data,new cljs.core.Keyword(null,"from","from",1815293044)))?cljs.core.select_keys(data,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"from","from",1815293044),new cljs.core.Keyword(null,"to","to",192099007)], null)):new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"from","from",1815293044),new cljs.core.Keyword(null,"from","from",1815293044).cljs$core$IFn$_invoke$arity$1(src),new cljs.core.Keyword(null,"to","to",192099007),new cljs.core.Keyword(null,"to","to",192099007).cljs$core$IFn$_invoke$arity$1(src)], null));
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2(e.message,cljs.core.merge.cljs$core$IFn$_invoke$arity$variadic(cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"eval-error","eval-error",466139568)], null),loc], 0)));
}} else {
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2([cljs.core.pr_str.cljs$core$IFn$_invoke$arity$variadic(cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([head], 0))," is not a function"].join(''),new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"eval-error","eval-error",466139568),new cljs.core.Keyword(null,"from","from",1815293044),new cljs.core.Keyword(null,"from","from",1815293044).cljs$core$IFn$_invoke$arity$1(src),new cljs.core.Keyword(null,"to","to",192099007),new cljs.core.Keyword(null,"to","to",192099007).cljs$core$IFn$_invoke$arity$1(src)], null));
}
}

}
} else {
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2(["Cannot evaluate: ",cljs.core.pr_str.cljs$core$IFn$_invoke$arity$variadic(cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([form], 0))].join(''),new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"eval-error","eval-error",466139568)], null));

}
}
}
}
}
}
});
repulse.lisp.eval.make_env = (function repulse$lisp$eval$make_env(stop_fn,bpm_fn){
var defs = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
var macros = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
var synths = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
return cljs.core.PersistentHashMap.fromArrays(["release","sound","stop","map","seq","range","vec","begin","min","=","list","*","keys",new cljs.core.Keyword(null,"*synths*","*synths*",591067875),"rarely","<=","off","euclidean","play-scenes","number?","scale","vector?","not","~","synth","every","jux","rev","/","str","degrade","-","fm","max","nth","slow","comp","arrange","noise","rest","count","pan","empty?","cons","apply","loop-sample","bpm","name","nil?","rate","vals","often","not=","pure","identity","alt","late","keyword?","keyword","sometimes","map?",">=","saw","fast","amp","concat","filter","symbol","choose","degrade-by",new cljs.core.Keyword(null,"*defs*","*defs*",1742801364),"string?","chord","mod","square","env","<","abs","conj","attack","fmap","jux-by","early","decay","tween","seq?","quot",new cljs.core.Keyword(null,"*macros*","*macros*",1376644506),"reduce","wchoose","sometimes-by","assoc","get","merge","+","transpose","cat","end","stack","first",">"],[(function() {
var G__8589 = null;
var G__8589__1 = (function (v){
return repulse.params.release.cljs$core$IFn$_invoke$arity$1(repulse.lisp.eval.unwrap(v));
});
var G__8589__2 = (function (v,p){
return repulse.params.release.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap(v),repulse.lisp.eval.unwrap(p));
});
G__8589 = function(v,p){
switch(arguments.length){
case 1:
return G__8589__1.call(this,v);
case 2:
return G__8589__2.call(this,v,p);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8589.cljs$core$IFn$_invoke$arity$1 = G__8589__1;
G__8589.cljs$core$IFn$_invoke$arity$2 = G__8589__2;
return G__8589;
})()
,(function (bank,n){
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"bank","bank",-1982531798),repulse.lisp.eval.unwrap(bank),new cljs.core.Keyword(null,"n","n",562130025),(function (){var or__5002__auto__ = repulse.lisp.eval.unwrap(n);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (0);
}
})()], null);
}),stop_fn,(function (f,coll){
return cljs.core.map.cljs$core$IFn$_invoke$arity$2(f,repulse.lisp.eval.unwrap(coll));
}),(function() { 
var G__8592__delegate = function (vs){
var srcs = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.source_of,vs);
var vals = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,vs);
return repulse.core.seq_STAR_.cljs$core$IFn$_invoke$arity$2(vals,srcs);
};
var G__8592 = function (var_args){
var vs = null;
if (arguments.length > 0) {
var G__8593__i = 0, G__8593__a = new Array(arguments.length -  0);
while (G__8593__i < G__8593__a.length) {G__8593__a[G__8593__i] = arguments[G__8593__i + 0]; ++G__8593__i;}
  vs = new cljs.core.IndexedSeq(G__8593__a,0,null);
} 
return G__8592__delegate.call(this,vs);};
G__8592.cljs$lang$maxFixedArity = 0;
G__8592.cljs$lang$applyTo = (function (arglist__8594){
var vs = cljs.core.seq(arglist__8594);
return G__8592__delegate(vs);
});
G__8592.cljs$core$IFn$_invoke$arity$variadic = G__8592__delegate;
return G__8592;
})()
,(function() {
var G__8595 = null;
var G__8595__1 = (function (n){
return cljs.core.range.cljs$core$IFn$_invoke$arity$1(repulse.lisp.eval.unwrap(n));
});
var G__8595__2 = (function (a,b){
return cljs.core.range.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap(a),repulse.lisp.eval.unwrap(b));
});
var G__8595__3 = (function (a,b,step){
return cljs.core.range.cljs$core$IFn$_invoke$arity$3(repulse.lisp.eval.unwrap(a),repulse.lisp.eval.unwrap(b),repulse.lisp.eval.unwrap(step));
});
G__8595 = function(a,b,step){
switch(arguments.length){
case 1:
return G__8595__1.call(this,a);
case 2:
return G__8595__2.call(this,a,b);
case 3:
return G__8595__3.call(this,a,b,step);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8595.cljs$core$IFn$_invoke$arity$1 = G__8595__1;
G__8595.cljs$core$IFn$_invoke$arity$2 = G__8595__2;
G__8595.cljs$core$IFn$_invoke$arity$3 = G__8595__3;
return G__8595;
})()
,(function (coll){
return cljs.core.vec(repulse.lisp.eval.unwrap(coll));
}),(function() {
var G__8599 = null;
var G__8599__1 = (function (v){
return repulse.params.begin.cljs$core$IFn$_invoke$arity$1(repulse.lisp.eval.unwrap(v));
});
var G__8599__2 = (function (v,p){
return repulse.params.begin.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap(v),repulse.lisp.eval.unwrap(p));
});
G__8599 = function(v,p){
switch(arguments.length){
case 1:
return G__8599__1.call(this,v);
case 2:
return G__8599__2.call(this,v,p);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8599.cljs$core$IFn$_invoke$arity$1 = G__8599__1;
G__8599.cljs$core$IFn$_invoke$arity$2 = G__8599__2;
return G__8599;
})()
,(function() { 
var G__8600__delegate = function (args){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.min,cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.__GT_num,args));
};
var G__8600 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8601__i = 0, G__8601__a = new Array(arguments.length -  0);
while (G__8601__i < G__8601__a.length) {G__8601__a[G__8601__i] = arguments[G__8601__i + 0]; ++G__8601__i;}
  args = new cljs.core.IndexedSeq(G__8601__a,0,null);
} 
return G__8600__delegate.call(this,args);};
G__8600.cljs$lang$maxFixedArity = 0;
G__8600.cljs$lang$applyTo = (function (arglist__8602){
var args = cljs.core.seq(arglist__8602);
return G__8600__delegate(args);
});
G__8600.cljs$core$IFn$_invoke$arity$variadic = G__8600__delegate;
return G__8600;
})()
,(function() { 
var G__8603__delegate = function (args){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core._EQ_,cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,args));
};
var G__8603 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8604__i = 0, G__8604__a = new Array(arguments.length -  0);
while (G__8604__i < G__8604__a.length) {G__8604__a[G__8604__i] = arguments[G__8604__i + 0]; ++G__8604__i;}
  args = new cljs.core.IndexedSeq(G__8604__a,0,null);
} 
return G__8603__delegate.call(this,args);};
G__8603.cljs$lang$maxFixedArity = 0;
G__8603.cljs$lang$applyTo = (function (arglist__8605){
var args = cljs.core.seq(arglist__8605);
return G__8603__delegate(args);
});
G__8603.cljs$core$IFn$_invoke$arity$variadic = G__8603__delegate;
return G__8603;
})()
,(function() { 
var G__8606__delegate = function (vs){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.list,cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,vs));
};
var G__8606 = function (var_args){
var vs = null;
if (arguments.length > 0) {
var G__8607__i = 0, G__8607__a = new Array(arguments.length -  0);
while (G__8607__i < G__8607__a.length) {G__8607__a[G__8607__i] = arguments[G__8607__i + 0]; ++G__8607__i;}
  vs = new cljs.core.IndexedSeq(G__8607__a,0,null);
} 
return G__8606__delegate.call(this,vs);};
G__8606.cljs$lang$maxFixedArity = 0;
G__8606.cljs$lang$applyTo = (function (arglist__8608){
var vs = cljs.core.seq(arglist__8608);
return G__8606__delegate(vs);
});
G__8606.cljs$core$IFn$_invoke$arity$variadic = G__8606__delegate;
return G__8606;
})()
,(function() { 
var G__8609__delegate = function (args){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core._STAR_,cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.__GT_num,args));
};
var G__8609 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8610__i = 0, G__8610__a = new Array(arguments.length -  0);
while (G__8610__i < G__8610__a.length) {G__8610__a[G__8610__i] = arguments[G__8610__i + 0]; ++G__8610__i;}
  args = new cljs.core.IndexedSeq(G__8610__a,0,null);
} 
return G__8609__delegate.call(this,args);};
G__8609.cljs$lang$maxFixedArity = 0;
G__8609.cljs$lang$applyTo = (function (arglist__8611){
var args = cljs.core.seq(arglist__8611);
return G__8609__delegate(args);
});
G__8609.cljs$core$IFn$_invoke$arity$variadic = G__8609__delegate;
return G__8609;
})()
,(function (m){
return cljs.core.keys(repulse.lisp.eval.unwrap(m));
}),synths,(function (f,p){
return repulse.core.rarely(repulse.lisp.eval.unwrap(f),repulse.lisp.eval.unwrap(p));
}),(function() { 
var G__8613__delegate = function (args){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core._LT__EQ_,cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.__GT_num,args));
};
var G__8613 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8614__i = 0, G__8614__a = new Array(arguments.length -  0);
while (G__8614__i < G__8614__a.length) {G__8614__a[G__8614__i] = arguments[G__8614__i + 0]; ++G__8614__i;}
  args = new cljs.core.IndexedSeq(G__8614__a,0,null);
} 
return G__8613__delegate.call(this,args);};
G__8613.cljs$lang$maxFixedArity = 0;
G__8613.cljs$lang$applyTo = (function (arglist__8615){
var args = cljs.core.seq(arglist__8615);
return G__8613__delegate(args);
});
G__8613.cljs$core$IFn$_invoke$arity$variadic = G__8613__delegate;
return G__8613;
})()
,(function (a,f,p){
return repulse.core.off(repulse.lisp.eval.unwrap(a),repulse.lisp.eval.unwrap(f),repulse.lisp.eval.unwrap(p));
}),(function() {
var G__8616 = null;
var G__8616__3 = (function (k,n,v){
return repulse.core.euclidean.cljs$core$IFn$_invoke$arity$3(repulse.lisp.eval.unwrap(k),repulse.lisp.eval.unwrap(n),repulse.lisp.eval.unwrap(v));
});
var G__8616__4 = (function (k,n,v,r){
return repulse.core.euclidean.cljs$core$IFn$_invoke$arity$4(repulse.lisp.eval.unwrap(k),repulse.lisp.eval.unwrap(n),repulse.lisp.eval.unwrap(v),repulse.lisp.eval.unwrap(r));
});
G__8616 = function(k,n,v,r){
switch(arguments.length){
case 3:
return G__8616__3.call(this,k,n,v);
case 4:
return G__8616__4.call(this,k,n,v,r);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8616.cljs$core$IFn$_invoke$arity$3 = G__8616__3;
G__8616.cljs$core$IFn$_invoke$arity$4 = G__8616__4;
return G__8616;
})()
,(function (sections){
return repulse.core.arrange_STAR_(cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (pat){
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [repulse.lisp.eval.unwrap(pat),(1)], null);
}),sections));
}),(function (x){
return typeof repulse.lisp.eval.unwrap(x) === 'number';
}),(function (kw,root,pat){
return repulse.theory.scale(repulse.lisp.eval.unwrap(kw),repulse.lisp.eval.unwrap(root),repulse.lisp.eval.unwrap(pat));
}),(function (x){
return cljs.core.vector_QMARK_(repulse.lisp.eval.unwrap(x));
}),(function (x){
return cljs.core.not(repulse.lisp.eval.unwrap(x));
}),(function (s){
var src = repulse.lisp.eval.source_of(s);
var base_offset = (cljs.core.truth_(src)?(new cljs.core.Keyword(null,"from","from",1815293044).cljs$core$IFn$_invoke$arity$1(src) + (1)):null);
return repulse.lisp.mini.parse.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap(s),base_offset);
}),(function() { 
var G__8618__delegate = function (voice_arg,rest_args){
var voice = repulse.lisp.eval.unwrap(voice_arg);
var args_SINGLEQUOTE_ = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,rest_args);
var last_a = cljs.core.last(args_SINGLEQUOTE_);
var has_pat_QMARK_ = ((cljs.core.seq(args_SINGLEQUOTE_)) && (((cljs.core.map_QMARK_(last_a)) && (cljs.core.fn_QMARK_(new cljs.core.Keyword(null,"query","query",-1288509510).cljs$core$IFn$_invoke$arity$1(last_a))))));
if(((cljs.core.seq(args_SINGLEQUOTE_)) && (((cljs.core.fn_QMARK_(last_a)) && ((!(has_pat_QMARK_))))))){
throw (new Error("amp, pan and other transformers must be chained with ->>, not passed as synth arguments.\nUse: (->> (synth :saw pattern) (amp 0.7))"));
} else {
}

var opts_map = cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.hash_map,((has_pat_QMARK_)?cljs.core.butlast(args_SINGLEQUOTE_):args_SINGLEQUOTE_));
var apply_xf = (function (pat){
return repulse.core.fmap((function (v){
var base = ((cljs.core.map_QMARK_(v))?v:new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"note","note",1426297904),v], null));
var freq = (function (){var or__5002__auto__ = new cljs.core.Keyword(null,"freq","freq",-1855845278).cljs$core$IFn$_invoke$arity$1(base);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
var or__5002__auto____$1 = ((typeof new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(base) === 'number')?new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(base):null);
if(cljs.core.truth_(or__5002__auto____$1)){
return or__5002__auto____$1;
} else {
if((new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(base) instanceof cljs.core.Keyword)){
return repulse.theory.note__GT_hz(new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(base));
} else {
return null;
}
}
}
})();
return cljs.core.merge.cljs$core$IFn$_invoke$arity$variadic(cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([base,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"synth","synth",-862700847),voice,new cljs.core.Keyword(null,"freq","freq",-1855845278),freq], null),opts_map], 0));
}),pat);
});
if(has_pat_QMARK_){
return apply_xf(last_a);
} else {
return apply_xf;
}
};
var G__8618 = function (voice_arg,var_args){
var rest_args = null;
if (arguments.length > 1) {
var G__8619__i = 0, G__8619__a = new Array(arguments.length -  1);
while (G__8619__i < G__8619__a.length) {G__8619__a[G__8619__i] = arguments[G__8619__i + 1]; ++G__8619__i;}
  rest_args = new cljs.core.IndexedSeq(G__8619__a,0,null);
} 
return G__8618__delegate.call(this,voice_arg,rest_args);};
G__8618.cljs$lang$maxFixedArity = 1;
G__8618.cljs$lang$applyTo = (function (arglist__8620){
var voice_arg = cljs.core.first(arglist__8620);
var rest_args = cljs.core.rest(arglist__8620);
return G__8618__delegate(voice_arg,rest_args);
});
G__8618.cljs$core$IFn$_invoke$arity$variadic = G__8618__delegate;
return G__8618;
})()
,(function (n,t,p){
return repulse.core.every(repulse.lisp.eval.unwrap(n),t,repulse.lisp.eval.unwrap(p));
}),(function (f,p){
return repulse.params.jux(repulse.lisp.eval.unwrap(f),repulse.lisp.eval.unwrap(p));
}),(function (p){
return repulse.core.rev(repulse.lisp.eval.unwrap(p));
}),(function() { 
var G__8621__delegate = function (args){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core._SLASH_,cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.__GT_num,args));
};
var G__8621 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8622__i = 0, G__8622__a = new Array(arguments.length -  0);
while (G__8622__i < G__8622__a.length) {G__8622__a[G__8622__i] = arguments[G__8622__i + 0]; ++G__8622__i;}
  args = new cljs.core.IndexedSeq(G__8622__a,0,null);
} 
return G__8621__delegate.call(this,args);};
G__8621.cljs$lang$maxFixedArity = 0;
G__8621.cljs$lang$applyTo = (function (arglist__8623){
var args = cljs.core.seq(arglist__8623);
return G__8621__delegate(args);
});
G__8621.cljs$core$IFn$_invoke$arity$variadic = G__8621__delegate;
return G__8621;
})()
,(function() { 
var G__8624__delegate = function (args){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,args));
};
var G__8624 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8625__i = 0, G__8625__a = new Array(arguments.length -  0);
while (G__8625__i < G__8625__a.length) {G__8625__a[G__8625__i] = arguments[G__8625__i + 0]; ++G__8625__i;}
  args = new cljs.core.IndexedSeq(G__8625__a,0,null);
} 
return G__8624__delegate.call(this,args);};
G__8624.cljs$lang$maxFixedArity = 0;
G__8624.cljs$lang$applyTo = (function (arglist__8626){
var args = cljs.core.seq(arglist__8626);
return G__8624__delegate(args);
});
G__8624.cljs$core$IFn$_invoke$arity$variadic = G__8624__delegate;
return G__8624;
})()
,(function (p){
return repulse.core.degrade(repulse.lisp.eval.unwrap(p));
}),(function() { 
var G__8627__delegate = function (args){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core._,cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.__GT_num,args));
};
var G__8627 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8628__i = 0, G__8628__a = new Array(arguments.length -  0);
while (G__8628__i < G__8628__a.length) {G__8628__a[G__8628__i] = arguments[G__8628__i + 0]; ++G__8628__i;}
  args = new cljs.core.IndexedSeq(G__8628__a,0,null);
} 
return G__8627__delegate.call(this,args);};
G__8627.cljs$lang$maxFixedArity = 0;
G__8627.cljs$lang$applyTo = (function (arglist__8629){
var args = cljs.core.seq(arglist__8629);
return G__8627__delegate(args);
});
G__8627.cljs$core$IFn$_invoke$arity$variadic = G__8627__delegate;
return G__8627;
})()
,(function() { 
var G__8630__delegate = function (note,opts){
var n = repulse.lisp.eval.unwrap(note);
var opts_SINGLEQUOTE_ = cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.hash_map,cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,opts));
var idx = cljs.core.get.cljs$core$IFn$_invoke$arity$3(opts_SINGLEQUOTE_,new cljs.core.Keyword(null,"index","index",-1531685915),1.0);
var ratio = cljs.core.get.cljs$core$IFn$_invoke$arity$3(opts_SINGLEQUOTE_,new cljs.core.Keyword(null,"ratio","ratio",-926560044),2.0);
return new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"note","note",1426297904),n,new cljs.core.Keyword(null,"synth","synth",-862700847),new cljs.core.Keyword(null,"fm","fm",1463745501),new cljs.core.Keyword(null,"index","index",-1531685915),idx,new cljs.core.Keyword(null,"ratio","ratio",-926560044),ratio], null);
};
var G__8630 = function (note,var_args){
var opts = null;
if (arguments.length > 1) {
var G__8632__i = 0, G__8632__a = new Array(arguments.length -  1);
while (G__8632__i < G__8632__a.length) {G__8632__a[G__8632__i] = arguments[G__8632__i + 1]; ++G__8632__i;}
  opts = new cljs.core.IndexedSeq(G__8632__a,0,null);
} 
return G__8630__delegate.call(this,note,opts);};
G__8630.cljs$lang$maxFixedArity = 1;
G__8630.cljs$lang$applyTo = (function (arglist__8633){
var note = cljs.core.first(arglist__8633);
var opts = cljs.core.rest(arglist__8633);
return G__8630__delegate(note,opts);
});
G__8630.cljs$core$IFn$_invoke$arity$variadic = G__8630__delegate;
return G__8630;
})()
,(function() { 
var G__8634__delegate = function (args){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.max,cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.__GT_num,args));
};
var G__8634 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8635__i = 0, G__8635__a = new Array(arguments.length -  0);
while (G__8635__i < G__8635__a.length) {G__8635__a[G__8635__i] = arguments[G__8635__i + 0]; ++G__8635__i;}
  args = new cljs.core.IndexedSeq(G__8635__a,0,null);
} 
return G__8634__delegate.call(this,args);};
G__8634.cljs$lang$maxFixedArity = 0;
G__8634.cljs$lang$applyTo = (function (arglist__8636){
var args = cljs.core.seq(arglist__8636);
return G__8634__delegate(args);
});
G__8634.cljs$core$IFn$_invoke$arity$variadic = G__8634__delegate;
return G__8634;
})()
,(function (coll,i){
return cljs.core.nth.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap(coll),repulse.lisp.eval.unwrap(i));
}),(function() {
var G__8637 = null;
var G__8637__1 = (function (f){
return (function (p){
return repulse.core.slow(repulse.lisp.eval.__GT_num(f),repulse.lisp.eval.unwrap(p));
});
});
var G__8637__2 = (function (f,p){
return repulse.core.slow(repulse.lisp.eval.__GT_num(f),repulse.lisp.eval.unwrap(p));
});
G__8637 = function(f,p){
switch(arguments.length){
case 1:
return G__8637__1.call(this,f);
case 2:
return G__8637__2.call(this,f,p);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8637.cljs$core$IFn$_invoke$arity$1 = G__8637__1;
G__8637.cljs$core$IFn$_invoke$arity$2 = G__8637__2;
return G__8637;
})()
,(function() { 
var G__8638__delegate = function (fs){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.comp,fs);
};
var G__8638 = function (var_args){
var fs = null;
if (arguments.length > 0) {
var G__8639__i = 0, G__8639__a = new Array(arguments.length -  0);
while (G__8639__i < G__8639__a.length) {G__8639__a[G__8639__i] = arguments[G__8639__i + 0]; ++G__8639__i;}
  fs = new cljs.core.IndexedSeq(G__8639__a,0,null);
} 
return G__8638__delegate.call(this,fs);};
G__8638.cljs$lang$maxFixedArity = 0;
G__8638.cljs$lang$applyTo = (function (arglist__8640){
var fs = cljs.core.seq(arglist__8640);
return G__8638__delegate(fs);
});
G__8638.cljs$core$IFn$_invoke$arity$variadic = G__8638__delegate;
return G__8638;
})()
,(function (plan){
return repulse.core.arrange_STAR_(cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p__8456){
var vec__8457 = p__8456;
var pat = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8457,(0),null);
var dur = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8457,(1),null);
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [repulse.lisp.eval.unwrap(pat),repulse.lisp.eval.unwrap(dur)], null);
}),plan));
}),(function (){
return new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"synth","synth",-862700847),new cljs.core.Keyword(null,"noise","noise",-994696820)], null);
}),(function (coll){
return cljs.core.rest(repulse.lisp.eval.unwrap(coll));
}),(function (coll){
return cljs.core.count(repulse.lisp.eval.unwrap(coll));
}),(function() {
var G__8642 = null;
var G__8642__1 = (function (v){
return repulse.params.pan.cljs$core$IFn$_invoke$arity$1(repulse.lisp.eval.unwrap(v));
});
var G__8642__2 = (function (v,p){
return repulse.params.pan.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap(v),repulse.lisp.eval.unwrap(p));
});
G__8642 = function(v,p){
switch(arguments.length){
case 1:
return G__8642__1.call(this,v);
case 2:
return G__8642__2.call(this,v,p);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8642.cljs$core$IFn$_invoke$arity$1 = G__8642__1;
G__8642.cljs$core$IFn$_invoke$arity$2 = G__8642__2;
return G__8642;
})()
,(function (coll){
return cljs.core.empty_QMARK_(repulse.lisp.eval.unwrap(coll));
}),(function (x,coll){
return cljs.core.cons(repulse.lisp.eval.unwrap(x),repulse.lisp.eval.unwrap(coll));
}),(function() { 
var G__8646__delegate = function (f,args){
var last_arg = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,repulse.lisp.eval.unwrap(cljs.core.last(args)));
var init_args = cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,cljs.core.butlast(args));
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(f,cljs.core.concat.cljs$core$IFn$_invoke$arity$2(init_args,last_arg));
};
var G__8646 = function (f,var_args){
var args = null;
if (arguments.length > 1) {
var G__8647__i = 0, G__8647__a = new Array(arguments.length -  1);
while (G__8647__i < G__8647__a.length) {G__8647__a[G__8647__i] = arguments[G__8647__i + 1]; ++G__8647__i;}
  args = new cljs.core.IndexedSeq(G__8647__a,0,null);
} 
return G__8646__delegate.call(this,f,args);};
G__8646.cljs$lang$maxFixedArity = 1;
G__8646.cljs$lang$applyTo = (function (arglist__8648){
var f = cljs.core.first(arglist__8648);
var args = cljs.core.rest(arglist__8648);
return G__8646__delegate(f,args);
});
G__8646.cljs$core$IFn$_invoke$arity$variadic = G__8646__delegate;
return G__8646;
})()
,(function() {
var G__8649 = null;
var G__8649__1 = (function (v){
return repulse.params.loop_sample.cljs$core$IFn$_invoke$arity$1(repulse.lisp.eval.unwrap(v));
});
var G__8649__2 = (function (v,p){
return repulse.params.loop_sample.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap(v),repulse.lisp.eval.unwrap(p));
});
G__8649 = function(v,p){
switch(arguments.length){
case 1:
return G__8649__1.call(this,v);
case 2:
return G__8649__2.call(this,v,p);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8649.cljs$core$IFn$_invoke$arity$1 = G__8649__1;
G__8649.cljs$core$IFn$_invoke$arity$2 = G__8649__2;
return G__8649;
})()
,(function (b){
var G__8464_8651 = repulse.lisp.eval.__GT_num(b);
(bpm_fn.cljs$core$IFn$_invoke$arity$1 ? bpm_fn.cljs$core$IFn$_invoke$arity$1(G__8464_8651) : bpm_fn.call(null,G__8464_8651));

return null;
}),(function (k){
return cljs.core.name(repulse.lisp.eval.unwrap(k));
}),(function (x){
return (repulse.lisp.eval.unwrap(x) == null);
}),(function() {
var G__8652 = null;
var G__8652__1 = (function (v){
return repulse.params.rate.cljs$core$IFn$_invoke$arity$1(repulse.lisp.eval.unwrap(v));
});
var G__8652__2 = (function (v,p){
return repulse.params.rate.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap(v),repulse.lisp.eval.unwrap(p));
});
G__8652 = function(v,p){
switch(arguments.length){
case 1:
return G__8652__1.call(this,v);
case 2:
return G__8652__2.call(this,v,p);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8652.cljs$core$IFn$_invoke$arity$1 = G__8652__1;
G__8652.cljs$core$IFn$_invoke$arity$2 = G__8652__2;
return G__8652;
})()
,(function (m){
return cljs.core.vals(repulse.lisp.eval.unwrap(m));
}),(function (f,p){
return repulse.core.often(repulse.lisp.eval.unwrap(f),repulse.lisp.eval.unwrap(p));
}),(function() { 
var G__8654__delegate = function (args){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.not_EQ_,cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,args));
};
var G__8654 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8655__i = 0, G__8655__a = new Array(arguments.length -  0);
while (G__8655__i < G__8655__a.length) {G__8655__a[G__8655__i] = arguments[G__8655__i + 0]; ++G__8655__i;}
  args = new cljs.core.IndexedSeq(G__8655__a,0,null);
} 
return G__8654__delegate.call(this,args);};
G__8654.cljs$lang$maxFixedArity = 0;
G__8654.cljs$lang$applyTo = (function (arglist__8657){
var args = cljs.core.seq(arglist__8657);
return G__8654__delegate(args);
});
G__8654.cljs$core$IFn$_invoke$arity$variadic = G__8654__delegate;
return G__8654;
})()
,(function (v){
return repulse.core.pure.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap(v),repulse.lisp.eval.source_of(v));
}),(function (x){
return x;
}),(function() { 
var G__8658__delegate = function (pats){
return repulse.lisp.mini.alt_STAR_(cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,pats));
};
var G__8658 = function (var_args){
var pats = null;
if (arguments.length > 0) {
var G__8659__i = 0, G__8659__a = new Array(arguments.length -  0);
while (G__8659__i < G__8659__a.length) {G__8659__a[G__8659__i] = arguments[G__8659__i + 0]; ++G__8659__i;}
  pats = new cljs.core.IndexedSeq(G__8659__a,0,null);
} 
return G__8658__delegate.call(this,pats);};
G__8658.cljs$lang$maxFixedArity = 0;
G__8658.cljs$lang$applyTo = (function (arglist__8660){
var pats = cljs.core.seq(arglist__8660);
return G__8658__delegate(pats);
});
G__8658.cljs$core$IFn$_invoke$arity$variadic = G__8658__delegate;
return G__8658;
})()
,(function() {
var G__8662 = null;
var G__8662__1 = (function (a){
return (function (p){
return repulse.core.late(repulse.lisp.eval.unwrap(a),repulse.lisp.eval.unwrap(p));
});
});
var G__8662__2 = (function (a,p){
return repulse.core.late(repulse.lisp.eval.unwrap(a),repulse.lisp.eval.unwrap(p));
});
G__8662 = function(a,p){
switch(arguments.length){
case 1:
return G__8662__1.call(this,a);
case 2:
return G__8662__2.call(this,a,p);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8662.cljs$core$IFn$_invoke$arity$1 = G__8662__1;
G__8662.cljs$core$IFn$_invoke$arity$2 = G__8662__2;
return G__8662;
})()
,(function (x){
return (repulse.lisp.eval.unwrap(x) instanceof cljs.core.Keyword);
}),(function (s){
return cljs.core.keyword.cljs$core$IFn$_invoke$arity$1(repulse.lisp.eval.unwrap(s));
}),(function (f,p){
return repulse.core.sometimes(repulse.lisp.eval.unwrap(f),repulse.lisp.eval.unwrap(p));
}),(function (x){
return cljs.core.map_QMARK_(repulse.lisp.eval.unwrap(x));
}),(function() { 
var G__8664__delegate = function (args){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core._GT__EQ_,cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.__GT_num,args));
};
var G__8664 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8665__i = 0, G__8665__a = new Array(arguments.length -  0);
while (G__8665__i < G__8665__a.length) {G__8665__a[G__8665__i] = arguments[G__8665__i + 0]; ++G__8665__i;}
  args = new cljs.core.IndexedSeq(G__8665__a,0,null);
} 
return G__8664__delegate.call(this,args);};
G__8664.cljs$lang$maxFixedArity = 0;
G__8664.cljs$lang$applyTo = (function (arglist__8666){
var args = cljs.core.seq(arglist__8666);
return G__8664__delegate(args);
});
G__8664.cljs$core$IFn$_invoke$arity$variadic = G__8664__delegate;
return G__8664;
})()
,(function (note){
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"note","note",1426297904),repulse.lisp.eval.unwrap(note),new cljs.core.Keyword(null,"synth","synth",-862700847),new cljs.core.Keyword(null,"saw","saw",-1928018630)], null);
}),(function() {
var G__8668 = null;
var G__8668__1 = (function (f){
return (function (p){
return repulse.core.fast(repulse.lisp.eval.__GT_num(f),repulse.lisp.eval.unwrap(p));
});
});
var G__8668__2 = (function (f,p){
return repulse.core.fast(repulse.lisp.eval.__GT_num(f),repulse.lisp.eval.unwrap(p));
});
G__8668 = function(f,p){
switch(arguments.length){
case 1:
return G__8668__1.call(this,f);
case 2:
return G__8668__2.call(this,f,p);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8668.cljs$core$IFn$_invoke$arity$1 = G__8668__1;
G__8668.cljs$core$IFn$_invoke$arity$2 = G__8668__2;
return G__8668;
})()
,(function() {
var G__8669 = null;
var G__8669__1 = (function (v){
return repulse.params.amp.cljs$core$IFn$_invoke$arity$1(repulse.lisp.eval.unwrap(v));
});
var G__8669__2 = (function (v,p){
return repulse.params.amp.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap(v),repulse.lisp.eval.unwrap(p));
});
G__8669 = function(v,p){
switch(arguments.length){
case 1:
return G__8669__1.call(this,v);
case 2:
return G__8669__2.call(this,v,p);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8669.cljs$core$IFn$_invoke$arity$1 = G__8669__1;
G__8669.cljs$core$IFn$_invoke$arity$2 = G__8669__2;
return G__8669;
})()
,(function() { 
var G__8670__delegate = function (colls){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.concat,cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,colls));
};
var G__8670 = function (var_args){
var colls = null;
if (arguments.length > 0) {
var G__8671__i = 0, G__8671__a = new Array(arguments.length -  0);
while (G__8671__i < G__8671__a.length) {G__8671__a[G__8671__i] = arguments[G__8671__i + 0]; ++G__8671__i;}
  colls = new cljs.core.IndexedSeq(G__8671__a,0,null);
} 
return G__8670__delegate.call(this,colls);};
G__8670.cljs$lang$maxFixedArity = 0;
G__8670.cljs$lang$applyTo = (function (arglist__8672){
var colls = cljs.core.seq(arglist__8672);
return G__8670__delegate(colls);
});
G__8670.cljs$core$IFn$_invoke$arity$variadic = G__8670__delegate;
return G__8670;
})()
,(function (f,coll){
return cljs.core.filter.cljs$core$IFn$_invoke$arity$2(f,repulse.lisp.eval.unwrap(coll));
}),(function (s){
return cljs.core.symbol.cljs$core$IFn$_invoke$arity$1(repulse.lisp.eval.unwrap(s));
}),(function (xs){
var xs_SINGLEQUOTE_ = repulse.lisp.eval.unwrap(xs);
return repulse.core.choose.cljs$core$IFn$_invoke$arity$2(cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,xs_SINGLEQUOTE_),cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.source_of,xs_SINGLEQUOTE_));
}),(function() {
var G__8673 = null;
var G__8673__1 = (function (prob){
return (function (p){
return repulse.core.degrade_by(repulse.lisp.eval.unwrap(prob),repulse.lisp.eval.unwrap(p));
});
});
var G__8673__2 = (function (prob,p){
return repulse.core.degrade_by(repulse.lisp.eval.unwrap(prob),repulse.lisp.eval.unwrap(p));
});
G__8673 = function(prob,p){
switch(arguments.length){
case 1:
return G__8673__1.call(this,prob);
case 2:
return G__8673__2.call(this,prob,p);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8673.cljs$core$IFn$_invoke$arity$1 = G__8673__1;
G__8673.cljs$core$IFn$_invoke$arity$2 = G__8673__2;
return G__8673;
})()
,defs,(function (x){
return typeof repulse.lisp.eval.unwrap(x) === 'string';
}),(function (kw,root){
return repulse.theory.chord.cljs$core$IFn$_invoke$arity$3(repulse.lisp.eval.unwrap(kw),repulse.lisp.eval.unwrap(root),repulse.lisp.eval.source_of(kw));
}),(function (a,b){
return cljs.core.mod(repulse.lisp.eval.__GT_num(a),repulse.lisp.eval.__GT_num(b));
}),(function() { 
var G__8674__delegate = function (note,opts){
var n = repulse.lisp.eval.unwrap(note);
var opts_SINGLEQUOTE_ = cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.hash_map,cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,opts));
var pw = cljs.core.get.cljs$core$IFn$_invoke$arity$3(opts_SINGLEQUOTE_,new cljs.core.Keyword(null,"pw","pw",354220944),0.5);
return new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"note","note",1426297904),n,new cljs.core.Keyword(null,"synth","synth",-862700847),new cljs.core.Keyword(null,"square","square",812434677),new cljs.core.Keyword(null,"pw","pw",354220944),pw], null);
};
var G__8674 = function (note,var_args){
var opts = null;
if (arguments.length > 1) {
var G__8675__i = 0, G__8675__a = new Array(arguments.length -  1);
while (G__8675__i < G__8675__a.length) {G__8675__a[G__8675__i] = arguments[G__8675__i + 1]; ++G__8675__i;}
  opts = new cljs.core.IndexedSeq(G__8675__a,0,null);
} 
return G__8674__delegate.call(this,note,opts);};
G__8674.cljs$lang$maxFixedArity = 1;
G__8674.cljs$lang$applyTo = (function (arglist__8676){
var note = cljs.core.first(arglist__8676);
var opts = cljs.core.rest(arglist__8676);
return G__8674__delegate(note,opts);
});
G__8674.cljs$core$IFn$_invoke$arity$variadic = G__8674__delegate;
return G__8674;
})()
,(function() { 
var G__8677__delegate = function (args){
var args_SINGLEQUOTE_ = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,args);
var levels = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,cljs.core.nth.cljs$core$IFn$_invoke$arity$3(args_SINGLEQUOTE_,(0),cljs.core.PersistentVector.EMPTY));
var times = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,cljs.core.nth.cljs$core$IFn$_invoke$arity$3(args_SINGLEQUOTE_,(1),cljs.core.PersistentVector.EMPTY));
var curves = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,cljs.core.nth.cljs$core$IFn$_invoke$arity$3(args_SINGLEQUOTE_,(2),cljs.core.PersistentVector.EMPTY));
if(cljs.core.not_EQ_.cljs$core$IFn$_invoke$arity$2(cljs.core.count(times),(cljs.core.count(levels) - (1)))){
throw (new Error(["env: times must have exactly (count levels - 1) elements. ","Got ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.count(levels))," levels and ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.count(times))," times."].join('')));
} else {
}

return new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"envelope","envelope",-236796318),new cljs.core.Keyword(null,"levels","levels",-950747887),levels,new cljs.core.Keyword(null,"times","times",1671571467),times,new cljs.core.Keyword(null,"curves","curves",-510805378),cljs.core.into.cljs$core$IFn$_invoke$arity$2(curves,cljs.core.repeat.cljs$core$IFn$_invoke$arity$2((function (){var x__5087__auto__ = (0);
var y__5088__auto__ = (cljs.core.count(times) - cljs.core.count(curves));
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})(),new cljs.core.Keyword(null,"lin","lin",1904063437)))], null);
};
var G__8677 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8682__i = 0, G__8682__a = new Array(arguments.length -  0);
while (G__8682__i < G__8682__a.length) {G__8682__a[G__8682__i] = arguments[G__8682__i + 0]; ++G__8682__i;}
  args = new cljs.core.IndexedSeq(G__8682__a,0,null);
} 
return G__8677__delegate.call(this,args);};
G__8677.cljs$lang$maxFixedArity = 0;
G__8677.cljs$lang$applyTo = (function (arglist__8683){
var args = cljs.core.seq(arglist__8683);
return G__8677__delegate(args);
});
G__8677.cljs$core$IFn$_invoke$arity$variadic = G__8677__delegate;
return G__8677;
})()
,(function() { 
var G__8684__delegate = function (args){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core._LT_,cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.__GT_num,args));
};
var G__8684 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8685__i = 0, G__8685__a = new Array(arguments.length -  0);
while (G__8685__i < G__8685__a.length) {G__8685__a[G__8685__i] = arguments[G__8685__i + 0]; ++G__8685__i;}
  args = new cljs.core.IndexedSeq(G__8685__a,0,null);
} 
return G__8684__delegate.call(this,args);};
G__8684.cljs$lang$maxFixedArity = 0;
G__8684.cljs$lang$applyTo = (function (arglist__8686){
var args = cljs.core.seq(arglist__8686);
return G__8684__delegate(args);
});
G__8684.cljs$core$IFn$_invoke$arity$variadic = G__8684__delegate;
return G__8684;
})()
,(function (x){
return Math.abs(repulse.lisp.eval.__GT_num(x));
}),(function (coll,v){
return cljs.core.conj.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap(coll),repulse.lisp.eval.unwrap(v));
}),(function() {
var G__8687 = null;
var G__8687__1 = (function (v){
return repulse.params.attack.cljs$core$IFn$_invoke$arity$1(repulse.lisp.eval.unwrap(v));
});
var G__8687__2 = (function (v,p){
return repulse.params.attack.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap(v),repulse.lisp.eval.unwrap(p));
});
G__8687 = function(v,p){
switch(arguments.length){
case 1:
return G__8687__1.call(this,v);
case 2:
return G__8687__2.call(this,v,p);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8687.cljs$core$IFn$_invoke$arity$1 = G__8687__1;
G__8687.cljs$core$IFn$_invoke$arity$2 = G__8687__2;
return G__8687;
})()
,(function (f,p){
return repulse.core.fmap((function (v){
return repulse.lisp.eval.unwrap((f.cljs$core$IFn$_invoke$arity$1 ? f.cljs$core$IFn$_invoke$arity$1(v) : f.call(null,v)));
}),repulse.lisp.eval.unwrap(p));
}),(function (w,f,p){
return repulse.params.jux_by(repulse.lisp.eval.unwrap(w),repulse.lisp.eval.unwrap(f),repulse.lisp.eval.unwrap(p));
}),(function() {
var G__8692 = null;
var G__8692__1 = (function (a){
return (function (p){
return repulse.core.early(repulse.lisp.eval.unwrap(a),repulse.lisp.eval.unwrap(p));
});
});
var G__8692__2 = (function (a,p){
return repulse.core.early(repulse.lisp.eval.unwrap(a),repulse.lisp.eval.unwrap(p));
});
G__8692 = function(a,p){
switch(arguments.length){
case 1:
return G__8692__1.call(this,a);
case 2:
return G__8692__2.call(this,a,p);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8692.cljs$core$IFn$_invoke$arity$1 = G__8692__1;
G__8692.cljs$core$IFn$_invoke$arity$2 = G__8692__2;
return G__8692;
})()
,(function() {
var G__8694 = null;
var G__8694__1 = (function (v){
return repulse.params.decay.cljs$core$IFn$_invoke$arity$1(repulse.lisp.eval.unwrap(v));
});
var G__8694__2 = (function (v,p){
return repulse.params.decay.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap(v),repulse.lisp.eval.unwrap(p));
});
G__8694 = function(v,p){
switch(arguments.length){
case 1:
return G__8694__1.call(this,v);
case 2:
return G__8694__2.call(this,v,p);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8694.cljs$core$IFn$_invoke$arity$1 = G__8694__1;
G__8694.cljs$core$IFn$_invoke$arity$2 = G__8694__2;
return G__8694;
})()
,(function (curve_arg,start_arg,end_arg,dur_arg){
var curve = repulse.lisp.eval.unwrap(curve_arg);
var start = repulse.lisp.eval.__GT_num(start_arg);
var end = repulse.lisp.eval.__GT_num(end_arg);
var dur = repulse.lisp.eval.__GT_num(dur_arg);
if(cljs.core.truth_((function (){var fexpr__8473 = new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"exp","exp",-261706262),null,new cljs.core.Keyword(null,"sine","sine",-619916490),null,new cljs.core.Keyword(null,"linear","linear",872268697),null], null), null);
return (fexpr__8473.cljs$core$IFn$_invoke$arity$1 ? fexpr__8473.cljs$core$IFn$_invoke$arity$1(curve) : fexpr__8473.call(null,curve));
})())){
} else {
throw (new Error(["Unknown curve type ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(curve),". Available: :linear, :exp, :sine"].join('')));
}

if((dur > (0))){
} else {
throw (new Error("Transition duration must be > 0"));
}

return new cljs.core.PersistentArrayMap(null, 5, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"tween","tween",1743568853),new cljs.core.Keyword(null,"curve","curve",-569677866),curve,new cljs.core.Keyword(null,"start","start",-355208981),start,new cljs.core.Keyword(null,"end","end",-268185958),end,new cljs.core.Keyword(null,"duration-bars","duration-bars",-1993701942),dur], null);
}),(function (x){
return cljs.core.seq_QMARK_(repulse.lisp.eval.unwrap(x));
}),(function (a,b){
return cljs.core.quot(repulse.lisp.eval.__GT_num(a),repulse.lisp.eval.__GT_num(b));
}),macros,(function() {
var G__8709 = null;
var G__8709__2 = (function (f,coll){
return cljs.core.reduce.cljs$core$IFn$_invoke$arity$2(f,repulse.lisp.eval.unwrap(coll));
});
var G__8709__3 = (function (f,init,coll){
return cljs.core.reduce.cljs$core$IFn$_invoke$arity$3(f,repulse.lisp.eval.unwrap(init),repulse.lisp.eval.unwrap(coll));
});
G__8709 = function(f,init,coll){
switch(arguments.length){
case 2:
return G__8709__2.call(this,f,init);
case 3:
return G__8709__3.call(this,f,init,coll);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8709.cljs$core$IFn$_invoke$arity$2 = G__8709__2;
G__8709.cljs$core$IFn$_invoke$arity$3 = G__8709__3;
return G__8709;
})()
,(function (pairs){
var pairs_SINGLEQUOTE_ = repulse.lisp.eval.unwrap(pairs);
var srcs = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__8428_SHARP_){
return repulse.lisp.eval.source_of(cljs.core.second(p1__8428_SHARP_));
}),pairs_SINGLEQUOTE_);
var vecs = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p__8476){
var vec__8477 = p__8476;
var w = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8477,(0),null);
var v = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8477,(1),null);
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [repulse.lisp.eval.unwrap(w),repulse.lisp.eval.unwrap(v)], null);
}),pairs_SINGLEQUOTE_);
return repulse.core.wchoose.cljs$core$IFn$_invoke$arity$2(vecs,srcs);
}),(function (prob,f,p){
return repulse.core.sometimes_by(repulse.lisp.eval.unwrap(prob),repulse.lisp.eval.unwrap(f),repulse.lisp.eval.unwrap(p));
}),(function (m,k,v){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(repulse.lisp.eval.unwrap(m),repulse.lisp.eval.unwrap(k),repulse.lisp.eval.unwrap(v));
}),(function() { 
var G__8712__delegate = function (m,k,rest){
var m_SINGLEQUOTE_ = repulse.lisp.eval.unwrap(m);
var k_SINGLEQUOTE_ = repulse.lisp.eval.unwrap(k);
if(cljs.core.seq(rest)){
return cljs.core.get.cljs$core$IFn$_invoke$arity$3(m_SINGLEQUOTE_,k_SINGLEQUOTE_,repulse.lisp.eval.unwrap(cljs.core.first(rest)));
} else {
return cljs.core.get.cljs$core$IFn$_invoke$arity$2(m_SINGLEQUOTE_,k_SINGLEQUOTE_);
}
};
var G__8712 = function (m,k,var_args){
var rest = null;
if (arguments.length > 2) {
var G__8715__i = 0, G__8715__a = new Array(arguments.length -  2);
while (G__8715__i < G__8715__a.length) {G__8715__a[G__8715__i] = arguments[G__8715__i + 2]; ++G__8715__i;}
  rest = new cljs.core.IndexedSeq(G__8715__a,0,null);
} 
return G__8712__delegate.call(this,m,k,rest);};
G__8712.cljs$lang$maxFixedArity = 2;
G__8712.cljs$lang$applyTo = (function (arglist__8716){
var m = cljs.core.first(arglist__8716);
arglist__8716 = cljs.core.next(arglist__8716);
var k = cljs.core.first(arglist__8716);
var rest = cljs.core.rest(arglist__8716);
return G__8712__delegate(m,k,rest);
});
G__8712.cljs$core$IFn$_invoke$arity$variadic = G__8712__delegate;
return G__8712;
})()
,(function() { 
var G__8717__delegate = function (ms){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.merge,cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,ms));
};
var G__8717 = function (var_args){
var ms = null;
if (arguments.length > 0) {
var G__8718__i = 0, G__8718__a = new Array(arguments.length -  0);
while (G__8718__i < G__8718__a.length) {G__8718__a[G__8718__i] = arguments[G__8718__i + 0]; ++G__8718__i;}
  ms = new cljs.core.IndexedSeq(G__8718__a,0,null);
} 
return G__8717__delegate.call(this,ms);};
G__8717.cljs$lang$maxFixedArity = 0;
G__8717.cljs$lang$applyTo = (function (arglist__8719){
var ms = cljs.core.seq(arglist__8719);
return G__8717__delegate(ms);
});
G__8717.cljs$core$IFn$_invoke$arity$variadic = G__8717__delegate;
return G__8717;
})()
,(function() { 
var G__8720__delegate = function (args){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core._PLUS_,cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.__GT_num,args));
};
var G__8720 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8721__i = 0, G__8721__a = new Array(arguments.length -  0);
while (G__8721__i < G__8721__a.length) {G__8721__a[G__8721__i] = arguments[G__8721__i + 0]; ++G__8721__i;}
  args = new cljs.core.IndexedSeq(G__8721__a,0,null);
} 
return G__8720__delegate.call(this,args);};
G__8720.cljs$lang$maxFixedArity = 0;
G__8720.cljs$lang$applyTo = (function (arglist__8722){
var args = cljs.core.seq(arglist__8722);
return G__8720__delegate(args);
});
G__8720.cljs$core$IFn$_invoke$arity$variadic = G__8720__delegate;
return G__8720;
})()
,(function() {
var G__8723 = null;
var G__8723__1 = (function (n){
return (function (p){
return repulse.theory.transpose(repulse.lisp.eval.unwrap(n),repulse.lisp.eval.unwrap(p));
});
});
var G__8723__2 = (function (n,pat){
return repulse.theory.transpose(repulse.lisp.eval.unwrap(n),repulse.lisp.eval.unwrap(pat));
});
G__8723 = function(n,pat){
switch(arguments.length){
case 1:
return G__8723__1.call(this,n);
case 2:
return G__8723__2.call(this,n,pat);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8723.cljs$core$IFn$_invoke$arity$1 = G__8723__1;
G__8723.cljs$core$IFn$_invoke$arity$2 = G__8723__2;
return G__8723;
})()
,(function() { 
var G__8724__delegate = function (ps){
return repulse.core.cat_STAR_(cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,ps));
};
var G__8724 = function (var_args){
var ps = null;
if (arguments.length > 0) {
var G__8726__i = 0, G__8726__a = new Array(arguments.length -  0);
while (G__8726__i < G__8726__a.length) {G__8726__a[G__8726__i] = arguments[G__8726__i + 0]; ++G__8726__i;}
  ps = new cljs.core.IndexedSeq(G__8726__a,0,null);
} 
return G__8724__delegate.call(this,ps);};
G__8724.cljs$lang$maxFixedArity = 0;
G__8724.cljs$lang$applyTo = (function (arglist__8727){
var ps = cljs.core.seq(arglist__8727);
return G__8724__delegate(ps);
});
G__8724.cljs$core$IFn$_invoke$arity$variadic = G__8724__delegate;
return G__8724;
})()
,(function() {
var G__8728 = null;
var G__8728__1 = (function (v){
return repulse.params.end_STAR_.cljs$core$IFn$_invoke$arity$1(repulse.lisp.eval.unwrap(v));
});
var G__8728__2 = (function (v,p){
return repulse.params.end_STAR_.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap(v),repulse.lisp.eval.unwrap(p));
});
G__8728 = function(v,p){
switch(arguments.length){
case 1:
return G__8728__1.call(this,v);
case 2:
return G__8728__2.call(this,v,p);
}
throw(new Error('Invalid arity: ' + arguments.length));
};
G__8728.cljs$core$IFn$_invoke$arity$1 = G__8728__1;
G__8728.cljs$core$IFn$_invoke$arity$2 = G__8728__2;
return G__8728;
})()
,(function() { 
var G__8729__delegate = function (ps){
return repulse.core.stack_STAR_(cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.unwrap,ps));
};
var G__8729 = function (var_args){
var ps = null;
if (arguments.length > 0) {
var G__8730__i = 0, G__8730__a = new Array(arguments.length -  0);
while (G__8730__i < G__8730__a.length) {G__8730__a[G__8730__i] = arguments[G__8730__i + 0]; ++G__8730__i;}
  ps = new cljs.core.IndexedSeq(G__8730__a,0,null);
} 
return G__8729__delegate.call(this,ps);};
G__8729.cljs$lang$maxFixedArity = 0;
G__8729.cljs$lang$applyTo = (function (arglist__8731){
var ps = cljs.core.seq(arglist__8731);
return G__8729__delegate(ps);
});
G__8729.cljs$core$IFn$_invoke$arity$variadic = G__8729__delegate;
return G__8729;
})()
,(function (coll){
return cljs.core.first(repulse.lisp.eval.unwrap(coll));
}),(function() { 
var G__8732__delegate = function (args){
return cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core._GT_,cljs.core.map.cljs$core$IFn$_invoke$arity$2(repulse.lisp.eval.__GT_num,args));
};
var G__8732 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8733__i = 0, G__8733__a = new Array(arguments.length -  0);
while (G__8733__i < G__8733__a.length) {G__8733__a[G__8733__i] = arguments[G__8733__i + 0]; ++G__8733__i;}
  args = new cljs.core.IndexedSeq(G__8733__a,0,null);
} 
return G__8732__delegate.call(this,args);};
G__8732.cljs$lang$maxFixedArity = 0;
G__8732.cljs$lang$applyTo = (function (arglist__8734){
var args = cljs.core.seq(arglist__8734);
return G__8732__delegate(args);
});
G__8732.cljs$core$IFn$_invoke$arity$variadic = G__8732__delegate;
return G__8732;
})()
]);
});

//# sourceMappingURL=repulse.lisp.eval.js.map
