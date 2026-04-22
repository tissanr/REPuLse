goog.provide('repulse.content.demos');
repulse.content.demos.demo_templates = new cljs.core.PersistentArrayMap(null, 7, [new cljs.core.Keyword(null,"techno","techno",-322781072),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"bpm","bpm",-1042376389),(130),new cljs.core.Keyword(null,"code","code",1586293142),";; TECHNO \u2014 four-on-the-floor kick, offbeat hats, snare on 2/4, acid bassline\n(bpm 130)\n\n(track :kick\n  (seq :bd :bd :bd :bd))\n\n(track :hat\n  (->> (fast 2 (seq :_ :oh :_ :oh))\n       (amp (seq 0.5 0.7 0.5 0.9))))\n\n(track :snare\n  (seq :_ :sd :_ :sd))\n\n(track :bass\n  (->> (scale :minor :c2 (seq 0 0 3 5))\n       (fast 2)\n       (decay 0.15)\n       (amp 0.8)))\n"], null),new cljs.core.Keyword(null,"ambient","ambient",-983195016),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"bpm","bpm",-1042376389),(72),new cljs.core.Keyword(null,"code","code",1586293142),";; AMBIENT \u2014 slow pad chords with reverb, gentle melodic line\n(bpm 72)\n\n(track :pad\n  (->> (chord :minor7 :a3)\n       (amp 0.3)\n       (attack 0.4)\n       (decay 3.0)))\n\n(track :melody\n  (->> (scale :minor :a4 (seq 0 2 4 7 4 2))\n       (slow 2)\n       (amp 0.4)\n       (attack 0.1)\n       (decay 1.5)))\n\n(track :pulse\n  (->> (seq :c5 :_ :e5 :_)\n       (slow 4)\n       (amp 0.15)\n       (decay 0.8)))\n"], null),new cljs.core.Keyword(null,"dnb","dnb",-97664092),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"bpm","bpm",-1042376389),(174),new cljs.core.Keyword(null,"code","code",1586293142),";; DRUM & BASS \u2014 fast breakbeat, sub bass, amen-style rhythm\n(bpm 174)\n\n(track :break\n  (seq :bd :_ :_ :bd :_ :_ :sd :_\n       :bd :_ :bd :_ :_ :sd :_ :_))\n\n(track :hat\n  (->> (fast 2 (seq :hh :hh :oh :hh))\n       (amp (seq 0.6 0.4 0.8 0.4))))\n\n(track :sub\n  (->> (scale :minor :e1 (seq 0 :_ 0 :_ 3 :_ 5 :_))\n       (amp 0.9)\n       (decay 0.2)))\n"], null),new cljs.core.Keyword(null,"minimal","minimal",1065044499),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"bpm","bpm",-1042376389),(120),new cljs.core.Keyword(null,"code","code",1586293142),";; MINIMAL \u2014 sparse kick, subtle hi-hats, one-note bass\n(bpm 120)\n\n(track :kick\n  (seq :bd :_ :_ :_ :bd :_ :_ :_))\n\n(track :hat\n  (->> (seq :_ :hh :_ :hh :_ :hh :_ :_)\n       (amp 0.35)))\n\n(track :bass\n  (->> (pure :c2)\n       (amp 0.6)\n       (decay 0.12)))\n"], null),new cljs.core.Keyword(null,"house","house",1139589178),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"bpm","bpm",-1042376389),(124),new cljs.core.Keyword(null,"code","code",1586293142),";; HOUSE \u2014 classic four-on-the-floor, organ stab chords, open hat\n(bpm 124)\n\n(track :kick\n  (seq :bd :bd :bd :bd))\n\n(track :hat\n  (->> (seq :_ :oh :_ :oh)\n       (amp 0.5)))\n\n(track :clap\n  (seq :_ :sd :_ :sd))\n\n(track :chord\n  (->> (every 4 (fast 2) (chord :dom7 :c4))\n       (amp 0.4)\n       (attack 0.02)\n       (decay 0.25)))\n\n(track :bass\n  (->> (scale :minor :c2 (seq 0 0 3 0 5 0 3 0))\n       (amp 0.7)\n       (decay 0.1)))\n"], null),new cljs.core.Keyword(null,"dub","dub",363805035),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"bpm","bpm",-1042376389),(140),new cljs.core.Keyword(null,"code","code",1586293142),";; DUB \u2014 heavy bass, delay-heavy snare, sparse hats\n(bpm 140)\n\n(track :kick\n  (seq :bd :_ :_ :_ :_ :_ :bd :_))\n\n(track :snare\n  (->> (seq :_ :_ :_ :sd :_ :_ :_ :_)\n       (amp 0.8)\n       (decay 0.3)))\n\n(track :hat\n  (->> (seq :_ :hh :_ :_ :_ :_ :hh :_)\n       (amp 0.3)))\n\n(track :bass\n  (->> (scale :minor :g1 (seq 0 :_ :_ 0 :_ 3 :_ :_))\n       (amp 0.9)\n       (attack 0.01)\n       (decay 0.4)))\n"], null),new cljs.core.Keyword(null,"experimental","experimental",2003141420),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"bpm","bpm",-1042376389),(110),new cljs.core.Keyword(null,"code","code",1586293142),";; EXPERIMENTAL \u2014 algorithmic patterns using every, rev, fmap\n(bpm 110)\n\n(track :rhythm\n  (every 3 rev\n    (seq :bd :_ :sd :_ :bd :bd :_ :sd)))\n\n(track :texture\n  (->> (every 2 (fast 2) (seq :hh :oh :hh :_))\n       (amp (seq 0.3 0.6 0.4 0.8))))\n\n(track :melody\n  (->> (scale :dorian :d3 (seq 0 2 4 6 7 4 2 0))\n       (every 4 rev)\n       (every 3 (fast 2))\n       (amp 0.5)\n       (decay 0.6)))\n\n(track :drone\n  (->> (chord :sus4 :d2)\n       (amp 0.2)\n       (attack 0.5)\n       (decay 2.5)))\n"], null)], null);
/**
 * Returns the Lisp `demo` built-in fn.
 * editor-view-atom — atom holding the current CodeMirror EditorView.
 * evaluate-ref     — atom holding the evaluate! fn (populated after eval-orchestrator init).
 */
repulse.content.demos.demo_builtin = (function repulse$content$demos$demo_builtin(editor_view_atom,evaluate_ref){
return (function() { 
var G__9167__delegate = function (args){
var kw = ((cljs.core.seq(args))?repulse.lisp.eval.unwrap(cljs.core.first(args)):null);
if((kw == null)){
return ["available demos: ",clojure.string.join.cljs$core$IFn$_invoke$arity$2(" ",cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p1__9088_SHARP_){
return [":",cljs.core.name(p1__9088_SHARP_)].join('');
}),cljs.core.sort.cljs$core$IFn$_invoke$arity$1(cljs.core.keys(repulse.content.demos.demo_templates))))].join('');
} else {
var temp__5802__auto__ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(repulse.content.demos.demo_templates,kw);
if(cljs.core.truth_(temp__5802__auto__)){
var map__9147 = temp__5802__auto__;
var map__9147__$1 = cljs.core.__destructure_map(map__9147);
var bpm = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9147__$1,new cljs.core.Keyword(null,"bpm","bpm",-1042376389));
var code = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9147__$1,new cljs.core.Keyword(null,"code","code",1586293142));
repulse.audio.set_bpm_BANG_(bpm);

var temp__5804__auto___9169 = cljs.core.deref(editor_view_atom);
if(cljs.core.truth_(temp__5804__auto___9169)){
var view_9170 = temp__5804__auto___9169;
view_9170.dispatch(({"changes": ({"from": (0), "to": view_9170.state.doc.length, "insert": code})}));

setTimeout((function (){
var temp__5804__auto____$1 = cljs.core.deref(evaluate_ref);
if(cljs.core.truth_(temp__5804__auto____$1)){
var f = temp__5804__auto____$1;
return (f.cljs$core$IFn$_invoke$arity$1 ? f.cljs$core$IFn$_invoke$arity$1(code) : f.call(null,code));
} else {
return null;
}
}),(50));
} else {
}

return ["=> loaded demo :",cljs.core.name(kw)].join('');
} else {
return ["unknown demo :",cljs.core.name(kw)," \u2014 available: ",clojure.string.join.cljs$core$IFn$_invoke$arity$2(" ",cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p1__9089_SHARP_){
return [":",cljs.core.name(p1__9089_SHARP_)].join('');
}),cljs.core.sort.cljs$core$IFn$_invoke$arity$1(cljs.core.keys(repulse.content.demos.demo_templates))))].join('');
}
}
};
var G__9167 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__9176__i = 0, G__9176__a = new Array(arguments.length -  0);
while (G__9176__i < G__9176__a.length) {G__9176__a[G__9176__i] = arguments[G__9176__i + 0]; ++G__9176__i;}
  args = new cljs.core.IndexedSeq(G__9176__a,0,null);
} 
return G__9167__delegate.call(this,args);};
G__9167.cljs$lang$maxFixedArity = 0;
G__9167.cljs$lang$applyTo = (function (arglist__9178){
var args = cljs.core.seq(arglist__9178);
return G__9167__delegate(args);
});
G__9167.cljs$core$IFn$_invoke$arity$variadic = G__9167__delegate;
return G__9167;
})()
;
});

//# sourceMappingURL=repulse.content.demos.js.map
