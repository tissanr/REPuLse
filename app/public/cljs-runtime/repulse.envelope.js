goog.provide('repulse.envelope');
/**
 * Linear interpolation from `from` to `to` over `n` samples (n >= 2).
 */
repulse.envelope.lin_samples = (function repulse$envelope$lin_samples(from,to,n){
var n__$1 = (function (){var x__5087__auto__ = (2);
var y__5088__auto__ = n;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
return cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (i){
return (from + ((i / (n__$1 - (1))) * (to - from)));
}),cljs.core.range.cljs$core$IFn$_invoke$arity$1(n__$1));
});
/**
 * Sine (ease-in / ease-out) interpolation from `from` to `to` over `n` samples.
 * Uses (1 - cos(π·t)) / 2 so it starts and ends smoothly.
 */
repulse.envelope.sin_samples = (function repulse$envelope$sin_samples(from,to,n){
var n__$1 = (function (){var x__5087__auto__ = (2);
var y__5088__auto__ = n;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
return cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (i){
var phase = (Math.PI * (i / (n__$1 - (1))));
var interp = (((1) - Math.cos(phase)) / (2));
return (from + (interp * (to - from)));
}),cljs.core.range.cljs$core$IFn$_invoke$arity$1(n__$1));
});
/**
 * Welch (quarter-sine) interpolation from `from` to `to` over `n` samples.
 * Uses sin(π/2·t) as the interp coefficient, giving a fast-at-start shape for both
 * rising and falling segments.
 */
repulse.envelope.welch_samples = (function repulse$envelope$welch_samples(from,to,n){
var n__$1 = (function (){var x__5087__auto__ = (2);
var y__5088__auto__ = n;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
return cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (i){
var phase = ((Math.PI / (2)) * (i / (n__$1 - (1))));
var interp = Math.sin(phase);
return (from + (interp * (to - from)));
}),cljs.core.range.cljs$core$IFn$_invoke$arity$1(n__$1));
});
/**
 * Exponential interpolation from `from` to `to` over `n` samples.
 * Values are clamped away from zero; falls back to linear when both endpoints
 * have the same sign and one is zero.
 */
repulse.envelope.exp_samples = (function repulse$envelope$exp_samples(from,to,n){
var n__$1 = (function (){var x__5087__auto__ = (2);
var y__5088__auto__ = n;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
var from_SINGLEQUOTE_ = (((from === (0)))?1.0E-4:from);
var to_SINGLEQUOTE_ = (((to === (0)))?1.0E-4:to);
if(((from_SINGLEQUOTE_ * to_SINGLEQUOTE_) < (0))){
return repulse.envelope.lin_samples(from,to,n__$1);
} else {
return cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (i){
var t = (i / (n__$1 - (1)));
return (from_SINGLEQUOTE_ * Math.pow((to_SINGLEQUOTE_ / from_SINGLEQUOTE_),t));
}),cljs.core.range.cljs$core$IFn$_invoke$arity$1(n__$1));
}
});
/**
 * Power-curve interpolation with numeric curvature `c`.
 * c > 1 → concave-up (slow start), 0 < c < 1 → concave-down (fast start).
 */
repulse.envelope.custom_curve_samples = (function repulse$envelope$custom_curve_samples(from,to,n,c){
var n__$1 = (function (){var x__5087__auto__ = (2);
var y__5088__auto__ = n;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
var c__$1 = (function (){var x__5087__auto__ = 0.01;
var y__5088__auto__ = c;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
return cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (i){
var t = (i / (n__$1 - (1)));
var y = Math.pow(t,c__$1);
return (from + (y * (to - from)));
}),cljs.core.range.cljs$core$IFn$_invoke$arity$1(n__$1));
});
/**
 * Construct an envelope descriptor from breakpoints.
 * levels — N+1 amplitude values  (e.g. [0 1 0.3 0])
 * times  — N segment durations   (e.g. [0.01 0.1 0.5])
 * curves — N curve keywords or numbers; optional (default :lin for each segment)
 *           Supported: :lin :exp :sin :welch :step or a positive number.
 * Returns {:levels [...] :times [...] :curves [...]}
 */
repulse.envelope.make_env = (function repulse$envelope$make_env(var_args){
var G__8228 = arguments.length;
switch (G__8228) {
case 2:
return repulse.envelope.make_env.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
case 3:
return repulse.envelope.make_env.cljs$core$IFn$_invoke$arity$3((arguments[(0)]),(arguments[(1)]),(arguments[(2)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.envelope.make_env.cljs$core$IFn$_invoke$arity$2 = (function (levels,times){
return repulse.envelope.make_env.cljs$core$IFn$_invoke$arity$3(levels,times,cljs.core.PersistentVector.EMPTY);
}));

(repulse.envelope.make_env.cljs$core$IFn$_invoke$arity$3 = (function (levels,times,curves){
var n_segs = cljs.core.count(times);
var filled = cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.vec(curves),cljs.core.repeat.cljs$core$IFn$_invoke$arity$2((function (){var x__5087__auto__ = (0);
var y__5088__auto__ = (n_segs - cljs.core.count(curves));
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})(),new cljs.core.Keyword(null,"lin","lin",1904063437)));
return new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"levels","levels",-950747887),cljs.core.vec(levels),new cljs.core.Keyword(null,"times","times",1671571467),cljs.core.vec(times),new cljs.core.Keyword(null,"curves","curves",-510805378),filled], null);
}));

(repulse.envelope.make_env.cljs$lang$maxFixedArity = 3);

/**
 * Sum of all segment times (seconds) in an envelope.
 */
repulse.envelope.total_duration = (function repulse$envelope$total_duration(env){
return cljs.core.reduce.cljs$core$IFn$_invoke$arity$3(cljs.core._PLUS_,0.0,new cljs.core.Keyword(null,"times","times",1671571467).cljs$core$IFn$_invoke$arity$1(env));
});
/**
 * Compute `n` interpolated Float64 values for one envelope segment.
 * from     — start level
 * to       — end level
 * curve    — :lin | :exp | :sin | :welch | :step | numeric curvature
 * Returns a vector of doubles.
 */
repulse.envelope.segment_samples = (function repulse$envelope$segment_samples(from,to,curve,n){
var G__8245 = curve;
var G__8245__$1 = (((G__8245 instanceof cljs.core.Keyword))?G__8245.fqn:null);
switch (G__8245__$1) {
case "lin":
return repulse.envelope.lin_samples(from,to,n);

break;
case "exp":
return repulse.envelope.exp_samples(from,to,n);

break;
case "sin":
return repulse.envelope.sin_samples(from,to,n);

break;
case "welch":
return repulse.envelope.welch_samples(from,to,n);

break;
case "step":
return cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.vec(cljs.core.repeat.cljs$core$IFn$_invoke$arity$2((n - (1)),from)),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [to], null));

break;
default:
if(typeof curve === 'number'){
return repulse.envelope.custom_curve_samples(from,to,n,curve);
} else {
return repulse.envelope.lin_samples(from,to,n);
}

}
});

//# sourceMappingURL=repulse.envelope.js.map
