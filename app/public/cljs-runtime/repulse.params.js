goog.provide('repulse.params');
/**
 * Upgrade a raw event value to a map with :note key, or leave maps as-is.
 */
repulse.params.to_map = (function repulse$params$to_map(v){
if(cljs.core.map_QMARK_(v)){
return v;
} else {
return new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"note","note",1426297904),v], null);
}
});
/**
 * True if x is a REPuLse pattern (was created via core/pattern).
 * Uses the explicit ::core/pat tag so plain event-value maps
 * (which are also Clojure maps) are never misidentified.
 */
repulse.params.pat_QMARK_ = (function repulse$params$pat_QMARK_(x){
return repulse.core.pattern_QMARK_(x);
});
/**
 * Merge parameter kw into each event of note-pat, sourcing values from
 * param-val-or-pat. Scalar values are wrapped in (pure …) first.
 * note-pat may also be a raw value (e.g. the map returned by (sound …));
 * it is coerced to (pure v) so that (rate 2.0 (sound :tabla 0)) works.
 */
repulse.params.apply_param = (function repulse$params$apply_param(kw,param_val_or_pat,note_pat){
var param_pat = ((repulse.params.pat_QMARK_(param_val_or_pat))?param_val_or_pat:repulse.core.pure.cljs$core$IFn$_invoke$arity$1(param_val_or_pat));
var note_pat_SINGLEQUOTE_ = ((repulse.params.pat_QMARK_(note_pat))?note_pat:repulse.core.pure.cljs$core$IFn$_invoke$arity$1(note_pat));
return repulse.core.combine((function (pv,nv){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(repulse.params.to_map(nv),kw,pv);
}),param_pat,note_pat_SINGLEQUOTE_);
});
/**
 * Scale event amplitude. 0.0 = silent, 1.0 = full.
 * (amp 0.8 pat)          — apply directly
 * (amp (seq 0.9 0.5) pat) — patterned accent
 * (amp 0.8)               — return (pat → pat) transformer
 */
repulse.params.amp = (function repulse$params$amp(var_args){
var G__8118 = arguments.length;
switch (G__8118) {
case 1:
return repulse.params.amp.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.params.amp.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.params.amp.cljs$core$IFn$_invoke$arity$1 = (function (v){
return (function (pat){
return repulse.params.amp.cljs$core$IFn$_invoke$arity$2(v,pat);
});
}));

(repulse.params.amp.cljs$core$IFn$_invoke$arity$2 = (function (v,pat){
return repulse.params.apply_param(new cljs.core.Keyword(null,"amp","amp",271690571),v,pat);
}));

(repulse.params.amp.cljs$lang$maxFixedArity = 2);

/**
 * Envelope attack time in seconds (time to reach peak amplitude).
 * (attack 0.001 pat) — percussive / instant
 * (attack 0.3 pat)   — slow swell
 * (attack 0.05)      — return transformer
 */
repulse.params.attack = (function repulse$params$attack(var_args){
var G__8130 = arguments.length;
switch (G__8130) {
case 1:
return repulse.params.attack.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.params.attack.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.params.attack.cljs$core$IFn$_invoke$arity$1 = (function (t){
return (function (pat){
return repulse.params.attack.cljs$core$IFn$_invoke$arity$2(t,pat);
});
}));

(repulse.params.attack.cljs$core$IFn$_invoke$arity$2 = (function (t,pat){
return repulse.params.apply_param(new cljs.core.Keyword(null,"attack","attack",1957061788),t,pat);
}));

(repulse.params.attack.cljs$lang$maxFixedArity = 2);

/**
 * Envelope decay time in seconds (time to fade to silence after peak).
 * (decay 0.08 pat) — punchy stab
 * (decay 2.0 pat)  — long fade
 * (decay 0.4)      — return transformer
 */
repulse.params.decay = (function repulse$params$decay(var_args){
var G__8149 = arguments.length;
switch (G__8149) {
case 1:
return repulse.params.decay.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.params.decay.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.params.decay.cljs$core$IFn$_invoke$arity$1 = (function (t){
return (function (pat){
return repulse.params.decay.cljs$core$IFn$_invoke$arity$2(t,pat);
});
}));

(repulse.params.decay.cljs$core$IFn$_invoke$arity$2 = (function (t,pat){
return repulse.params.apply_param(new cljs.core.Keyword(null,"decay","decay",1036712184),t,pat);
}));

(repulse.params.decay.cljs$lang$maxFixedArity = 2);

/**
 * Envelope release time in seconds. When omitted, the decay value is used.
 * (release 0.5 pat) — apply directly
 * (release 0.5)     — return transformer
 */
repulse.params.release = (function repulse$params$release(var_args){
var G__8166 = arguments.length;
switch (G__8166) {
case 1:
return repulse.params.release.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.params.release.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.params.release.cljs$core$IFn$_invoke$arity$1 = (function (t){
return (function (pat){
return repulse.params.release.cljs$core$IFn$_invoke$arity$2(t,pat);
});
}));

(repulse.params.release.cljs$core$IFn$_invoke$arity$2 = (function (t,pat){
return repulse.params.apply_param(new cljs.core.Keyword(null,"release","release",-1534371381),t,pat);
}));

(repulse.params.release.cljs$lang$maxFixedArity = 2);

/**
 * Stereo panning. -1.0 = hard left, 0.0 = centre, 1.0 = hard right.
 * (pan 0.0 pat)              — centre
 * (pan (seq -0.8 0.8) pat)   — alternating left/right
 * (pan -0.5)                 — return transformer
 */
repulse.params.pan = (function repulse$params$pan(var_args){
var G__8177 = arguments.length;
switch (G__8177) {
case 1:
return repulse.params.pan.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.params.pan.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.params.pan.cljs$core$IFn$_invoke$arity$1 = (function (p){
return (function (pat){
return repulse.params.pan.cljs$core$IFn$_invoke$arity$2(p,pat);
});
}));

(repulse.params.pan.cljs$core$IFn$_invoke$arity$2 = (function (p,pat){
return repulse.params.apply_param(new cljs.core.Keyword(null,"pan","pan",-307712792),p,pat);
}));

(repulse.params.pan.cljs$lang$maxFixedArity = 2);

/**
 * Stack the original pattern panned left with (f pat) panned right.
 * Creates stereo width by splitting original and transformed copies.
 * (jux rev (seq :c4 :e4 :g4))  — original left, reversed right
 */
repulse.params.jux = (function repulse$params$jux(f,pat){
return repulse.core.stack_STAR_(new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [repulse.params.pan.cljs$core$IFn$_invoke$arity$2((-1),pat),repulse.params.pan.cljs$core$IFn$_invoke$arity$2((1),(f.cljs$core$IFn$_invoke$arity$1 ? f.cljs$core$IFn$_invoke$arity$1(pat) : f.call(null,pat)))], null));
});
/**
 * Playback rate multiplier. 1.0 = normal, 2.0 = double speed (octave up),
 * 0.5 = half speed (octave down).
 * (rate 1.5 pat)  — apply directly
 * (rate 1.5)      — return transformer
 */
repulse.params.rate = (function repulse$params$rate(var_args){
var G__8199 = arguments.length;
switch (G__8199) {
case 1:
return repulse.params.rate.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.params.rate.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.params.rate.cljs$core$IFn$_invoke$arity$1 = (function (r){
return (function (pat){
return repulse.params.rate.cljs$core$IFn$_invoke$arity$2(r,pat);
});
}));

(repulse.params.rate.cljs$core$IFn$_invoke$arity$2 = (function (r,pat){
return repulse.params.apply_param(new cljs.core.Keyword(null,"rate","rate",-1428659698),r,pat);
}));

(repulse.params.rate.cljs$lang$maxFixedArity = 2);

/**
 * Sample start position as a fraction of buffer duration (0.0–1.0).
 * (begin 0.25 pat)  — start at 25% into the sample
 * (begin 0.25)      — return transformer
 */
repulse.params.begin = (function repulse$params$begin(var_args){
var G__8213 = arguments.length;
switch (G__8213) {
case 1:
return repulse.params.begin.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.params.begin.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.params.begin.cljs$core$IFn$_invoke$arity$1 = (function (t){
return (function (pat){
return repulse.params.begin.cljs$core$IFn$_invoke$arity$2(t,pat);
});
}));

(repulse.params.begin.cljs$core$IFn$_invoke$arity$2 = (function (t,pat){
return repulse.params.apply_param(new cljs.core.Keyword(null,"begin","begin",-319034319),t,pat);
}));

(repulse.params.begin.cljs$lang$maxFixedArity = 2);

/**
 * Sample end position as a fraction of buffer duration (0.0–1.0).
 * Named end* to avoid conflict with cljs.core/end.
 * (end* 0.75 pat)  — stop at 75% into the sample
 * (end* 0.75)      — return transformer
 */
repulse.params.end_STAR_ = (function repulse$params$end_STAR_(var_args){
var G__8217 = arguments.length;
switch (G__8217) {
case 1:
return repulse.params.end_STAR_.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.params.end_STAR_.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.params.end_STAR_.cljs$core$IFn$_invoke$arity$1 = (function (t){
return (function (pat){
return repulse.params.end_STAR_.cljs$core$IFn$_invoke$arity$2(t,pat);
});
}));

(repulse.params.end_STAR_.cljs$core$IFn$_invoke$arity$2 = (function (t,pat){
return repulse.params.apply_param(new cljs.core.Keyword(null,"end","end",-268185958),t,pat);
}));

(repulse.params.end_STAR_.cljs$lang$maxFixedArity = 2);

/**
 * Enable sample looping.
 * (loop-sample true pat)  — loop the sample
 * (loop-sample true)      — return transformer
 */
repulse.params.loop_sample = (function repulse$params$loop_sample(var_args){
var G__8244 = arguments.length;
switch (G__8244) {
case 1:
return repulse.params.loop_sample.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.params.loop_sample.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.params.loop_sample.cljs$core$IFn$_invoke$arity$1 = (function (on_QMARK_){
return (function (pat){
return repulse.params.loop_sample.cljs$core$IFn$_invoke$arity$2(on_QMARK_,pat);
});
}));

(repulse.params.loop_sample.cljs$core$IFn$_invoke$arity$2 = (function (on_QMARK_,pat){
return repulse.params.apply_param(new cljs.core.Keyword(null,"loop","loop",-395552849),on_QMARK_,pat);
}));

(repulse.params.loop_sample.cljs$lang$maxFixedArity = 2);

/**
 * Like jux but with adjustable stereo width.
 * width 0.0 = both copies centre (mono), 1.0 = full left/right.
 * (jux-by 0.5 rev pat) — half stereo width
 */
repulse.params.jux_by = (function repulse$params$jux_by(width,f,pat){
return repulse.core.stack_STAR_(new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [repulse.params.pan.cljs$core$IFn$_invoke$arity$2((- width),pat),repulse.params.pan.cljs$core$IFn$_invoke$arity$2(width,(f.cljs$core$IFn$_invoke$arity$1 ? f.cljs$core$IFn$_invoke$arity$1(pat) : f.call(null,pat)))], null));
});
/**
 * Tag events for MIDI note output on the given channel (1–16).
 * (midi-out 1 pat)  — send events as MIDI notes on channel 1
 * (midi-out 1)      — return (pat → pat) transformer
 */
repulse.params.midi_out = (function repulse$params$midi_out(var_args){
var G__8247 = arguments.length;
switch (G__8247) {
case 1:
return repulse.params.midi_out.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.params.midi_out.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.params.midi_out.cljs$core$IFn$_invoke$arity$1 = (function (ch){
return (function (pat){
return repulse.params.midi_out.cljs$core$IFn$_invoke$arity$2(ch,pat);
});
}));

(repulse.params.midi_out.cljs$core$IFn$_invoke$arity$2 = (function (ch,pat){
return repulse.params.apply_param(new cljs.core.Keyword(null,"midi-ch","midi-ch",-479086655),ch,pat);
}));

(repulse.params.midi_out.cljs$lang$maxFixedArity = 2);


//# sourceMappingURL=repulse.params.js.map
