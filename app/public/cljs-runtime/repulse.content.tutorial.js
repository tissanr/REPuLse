goog.provide('repulse.content.tutorial');
repulse.content.tutorial.tutorial_chapters = new cljs.core.PersistentVector(null, 8, 5, cljs.core.PersistentVector.EMPTY_NODE, [";; === Tutorial 1/8 \u2014 First Sound ===\n;;\n;; Welcome to REPuLse! Let's make some noise.\n;;\n;; `seq` creates a sequence of sounds.  Each value plays\n;; for one equal subdivision of the cycle:\n;;   :bd = bass drum   :sd = snare   :hh = hi-hat\n;;\n;; Press Alt+Enter (Option+Enter on Mac) to hear this:\n\n(seq :bd :sd :bd :sd)\n\n;; Try changing :sd to :hh and press Alt+Enter again.\n;; When you're ready, type (tutorial 2) in the command bar.",";; === Tutorial 2/8 \u2014 Layering with stack ===\n;;\n;; `stack` plays multiple patterns at the same time.\n;; Each pattern runs in parallel, like tracks in a mixer.\n\n(stack\n  (seq :bd :_ :bd :_)\n  (seq :_ :sd :_ :sd)\n  (seq :hh :hh :hh :hh))\n\n;; :_ is a rest \u2014 silence for that step.\n;; Try adding a fourth layer!\n;; Next: (tutorial 3)",";; === Tutorial 3/8 \u2014 Speed: fast & slow ===\n;;\n;; `fast` speeds up a pattern by a factor.\n;; `slow` does the opposite.\n\n(stack\n  (seq :bd :_ :bd :_)\n  (fast 2 (seq :hh :oh))\n  (slow 2 (seq :sd :_ :_ :_)))\n\n;; (fast 2 pat) plays pat twice per cycle.\n;; (slow 2 pat) stretches pat over two cycles.\n;; Try (fast 4 (seq :hh :oh)) for rapid hi-hats.\n;; Next: (tutorial 4)",";; === Tutorial 4/8 \u2014 Evolution: every ===\n;;\n;; `every` applies a transformation only on certain cycles.\n;; (every N transform pattern) \u2014 transform every Nth cycle.\n\n(stack\n  (every 4 (fast 2) (seq :bd :_ :bd :_))\n  (seq :_ :sd :_ :sd)\n  (every 3 rev (seq :hh :oh :hh :_)))\n\n;; The kick doubles speed every 4th cycle.\n;; The hats reverse every 3rd cycle.\n;; This is how patterns stay alive without manual changes.\n;; Next: (tutorial 5)",";; === Tutorial 5/8 \u2014 Naming: def ===\n;;\n;; `def` binds a name to a value. Use it to build\n;; a vocabulary of reusable parts.\n\n(def kick  (seq :bd :_ :bd :_))\n(def snare (seq :_ :sd :_ :sd))\n(def hat   (fast 2 (seq :hh :oh)))\n\n(stack kick snare hat)\n\n;; Now you can refer to `kick`, `snare`, `hat` by name.\n;; Try: (def kick (seq :bd :bd :_ :bd)) and re-evaluate.\n;; Next: (tutorial 6)",";; === Tutorial 6/8 \u2014 Multi-Track: track ===\n;;\n;; `track` defines a named track.  Each track runs\n;; independently \u2014 you can update one without stopping others.\n\n(track :kick\n  (seq :bd :_ :bd :bd))\n\n(track :snare\n  (seq :_ :sd :_ :sd))\n\n(track :hat\n  (fast 2 (seq :hh :oh)))\n\n;; In the command bar, try:\n;;   (mute! :hat)     \u2014 silence the hats\n;;   (unmute! :hat)   \u2014 bring them back\n;;   (solo! :kick)    \u2014 hear only the kick\n;;   (clear!)         \u2014 stop everything\n;; Next: (tutorial 7)",";; === Tutorial 7/8 \u2014 Melody: scale & chord ===\n;;\n;; Note keywords like :c4 play pitched tones.\n;; `scale` maps degree numbers (1, 2, 3, \u2026) to a musical scale.\n;; `chord` stacks the tones of a chord.\n\n(track :bass\n  (scale :minor :c3 (seq 1 1 4 6)))\n\n(track :chords\n  (slow 2 (chord :minor :c4)))\n\n(track :melody\n  (scale :minor :c4 (seq 1 3 5 8 5 3)))\n\n(track :kick\n  (seq :bd :bd :bd :bd))\n\n;; Try changing :minor to :dorian or :blues.\n;; Try (transpose 5 ...) around the melody.\n;; Next: (tutorial 8)",";; === Tutorial 8/8 \u2014 Expression: amp, decay, ->> ===\n;;\n;; Per-event parameters make patterns expressive.\n;; `->>` threads a pattern through a chain of transformers.\n\n(track :kick\n  (->> (seq :bd :bd :bd :bd)\n       (amp (seq 0.9 0.5 0.7 0.5))))\n\n(track :lead\n  (->> (scale :minor :c4 (seq 0 2 4 7 4 2 0 :_))\n       (amp 0.6)\n       (attack 0.02)\n       (decay 0.5)))\n\n(track :pad\n  (->> (chord :minor7 :c3)\n       (amp 0.25)\n       (attack 0.3)\n       (decay 2.0)))\n\n;; (amp val) sets amplitude 0.0\u20131.0\n;; (attack secs) sets onset time\n;; (decay secs) sets fade time\n;; (pan pos) sets stereo position -1.0 to 1.0\n;;\n;; That's the basics! Try (demo :techno) or (demo :experimental)\n;; to hear full compositions, or start writing your own."], null);
/**
 * Returns the Lisp `tutorial` built-in fn.
 * editor-view-atom — atom holding the current CodeMirror EditorView.
 */
repulse.content.tutorial.tutorial_builtin = (function repulse$content$tutorial$tutorial_builtin(editor_view_atom){
return (function() { 
var G__8584__delegate = function (args){
var n = ((cljs.core.seq(args))?(repulse.lisp.eval.unwrap(cljs.core.first(args)) | (0)):(1));
var idx = (n - (1));
if((((idx >= (0))) && ((idx < cljs.core.count(repulse.content.tutorial.tutorial_chapters))))){
var code = cljs.core.nth.cljs$core$IFn$_invoke$arity$2(repulse.content.tutorial.tutorial_chapters,idx);
var temp__5804__auto___8586 = cljs.core.deref(editor_view_atom);
if(cljs.core.truth_(temp__5804__auto___8586)){
var view_8587 = temp__5804__auto___8586;
view_8587.dispatch(({"changes": ({"from": (0), "to": view_8587.state.doc.length, "insert": code})}));
} else {
}

return ["=> tutorial chapter ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(n),"/",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.count(repulse.content.tutorial.tutorial_chapters))," \u2014 press Alt+Enter to play"].join('');
} else {
return ["tutorial has chapters 1\u2013",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.count(repulse.content.tutorial.tutorial_chapters))," \u2014 try (tutorial 1)"].join('');
}
};
var G__8584 = function (var_args){
var args = null;
if (arguments.length > 0) {
var G__8588__i = 0, G__8588__a = new Array(arguments.length -  0);
while (G__8588__i < G__8588__a.length) {G__8588__a[G__8588__i] = arguments[G__8588__i + 0]; ++G__8588__i;}
  args = new cljs.core.IndexedSeq(G__8588__a,0,null);
} 
return G__8584__delegate.call(this,args);};
G__8584.cljs$lang$maxFixedArity = 0;
G__8584.cljs$lang$applyTo = (function (arglist__8590){
var args = cljs.core.seq(arglist__8590);
return G__8584__delegate(args);
});
G__8584.cljs$core$IFn$_invoke$arity$variadic = G__8584__delegate;
return G__8584;
})()
;
});

//# sourceMappingURL=repulse.content.tutorial.js.map
