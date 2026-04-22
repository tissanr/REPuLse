goog.provide('repulse.synth');
if((typeof repulse !== 'undefined') && (typeof repulse.synth !== 'undefined') && (typeof repulse.synth.synth_defs !== 'undefined')){
} else {
repulse.synth.synth_defs = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
}
/**
 * Called by defsynth (via app-level callback) to register a synth.
 * param-names is a vector of strings. body is a vector of AST forms.
 * closed-env is the eval env at defsynth time.
 */
repulse.synth.register_synth_BANG_ = (function repulse$synth$register_synth_BANG_(synth_name,param_names,body,closed_env){
var build_fn = (repulse.synth.make_build_fn.cljs$core$IFn$_invoke$arity$4 ? repulse.synth.make_build_fn.cljs$core$IFn$_invoke$arity$4(synth_name,param_names,body,closed_env) : repulse.synth.make_build_fn.call(null,synth_name,param_names,body,closed_env));
return cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.synth.synth_defs,cljs.core.assoc,synth_name,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"params","params",710516235),param_names,new cljs.core.Keyword(null,"build-fn","build-fn",845748249),build_fn], null));
});
repulse.synth.lookup_synth = (function repulse$synth$lookup_synth(synth_name){
return cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.synth.synth_defs),synth_name);
});
repulse.synth.sin_osc = (function repulse$synth$sin_osc(ac,t,freq){
var osc = ac.createOscillator();
(osc.type = "sine");

osc.frequency.setValueAtTime(freq,(0));

osc.start(t);

return osc;
});
repulse.synth.saw_osc = (function repulse$synth$saw_osc(ac,t,freq){
var osc = ac.createOscillator();
(osc.type = "sawtooth");

osc.frequency.setValueAtTime(freq,(0));

osc.start(t);

return osc;
});
repulse.synth.square_osc = (function repulse$synth$square_osc(ac,t,freq){
var osc = ac.createOscillator();
(osc.type = "square");

osc.frequency.setValueAtTime(freq,(0));

osc.start(t);

return osc;
});
repulse.synth.tri_osc = (function repulse$synth$tri_osc(ac,t,freq){
var osc = ac.createOscillator();
(osc.type = "triangle");

osc.frequency.setValueAtTime(freq,(0));

osc.start(t);

return osc;
});
repulse.synth.noise_src = (function repulse$synth$noise_src(ac,t){
var buf_size = (8192);
var buf = ac.createBuffer((1),buf_size,ac.sampleRate);
var data = buf.getChannelData((0));
var _ = (function (){var n__5593__auto__ = buf_size;
var i = (0);
while(true){
if((i < n__5593__auto__)){
(data[i] = (((2) * Math.random()) - (1)));

var G__8542 = (i + (1));
i = G__8542;
continue;
} else {
return null;
}
break;
}
})();
var src = ac.createBufferSource();
(src.buffer = buf);

src.start(t);

return src;
});
/**
 * Lowpass filter: (lpf cutoff source) — source-last for ->> threading.
 */
repulse.synth.lpf_node = (function repulse$synth$lpf_node(ac,cutoff,source){
var flt = ac.createBiquadFilter();
(flt.type = "lowpass");

flt.frequency.setValueAtTime(cutoff,(0));

source.connect(flt);

return flt;
});
/**
 * Highpass filter: (hpf cutoff source).
 */
repulse.synth.hpf_node = (function repulse$synth$hpf_node(ac,cutoff,source){
var flt = ac.createBiquadFilter();
(flt.type = "highpass");

flt.frequency.setValueAtTime(cutoff,(0));

source.connect(flt);

return flt;
});
/**
 * Bandpass filter: (bpf freq source).
 */
repulse.synth.bpf_node = (function repulse$synth$bpf_node(ac,freq,source){
var flt = ac.createBiquadFilter();
(flt.type = "bandpass");

flt.frequency.setValueAtTime(freq,(0));

source.connect(flt);

return flt;
});
/**
 * Static gain: (gain level source).
 */
repulse.synth.gain_node = (function repulse$synth$gain_node(ac,level,source){
var g = ac.createGain();
g.gain.setValueAtTime(level,(0));

source.connect(g);

return g;
});
/**
 * Delay: (delay-node time source).
 */
repulse.synth.delay_line = (function repulse$synth$delay_line(ac,time,source){
var d = ac.createDelay(5.0);
d.delayTime.setValueAtTime(time,(0));

source.connect(d);

return d;
});
/**
 * Mix two signals: (mix a b) — connects both into a single GainNode.
 */
repulse.synth.mix_node = (function repulse$synth$mix_node(ac,source_a,source_b){
var g = ac.createGain();
g.gain.setValueAtTime(1.0,(0));

source_a.connect(g);

source_b.connect(g);

return g;
});
/**
 * Percussive envelope: (env-perc attack decay source).
 */
repulse.synth.env_perc_node = (function repulse$synth$env_perc_node(ac,t,attack,decay,source){
var g = ac.createGain();
var atk = (function (){var x__5087__auto__ = 0.001;
var y__5088__auto__ = attack;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
g.gain.setValueAtTime(1.0E-4,t);

g.gain.linearRampToValueAtTime(1.0,(t + atk));

g.gain.exponentialRampToValueAtTime(1.0E-4,((t + atk) + decay));

source.connect(g);

return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"node","node",581201198),g,new cljs.core.Keyword(null,"duration","duration",1444101068),(atk + decay)], null);
});
/**
 * ASR envelope: (env-asr attack sustain release source).
 * Sustain hold = 1.0 s. Returns {:node g :duration (atk + 1.0 + rel)}.
 */
repulse.synth.env_asr_node = (function repulse$synth$env_asr_node(ac,t,attack,sustain,release,source){
var g = ac.createGain();
var atk = (function (){var x__5087__auto__ = 0.001;
var y__5088__auto__ = attack;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
var rel = (function (){var x__5087__auto__ = 0.001;
var y__5088__auto__ = release;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
var sustain_hold = 1.0;
var total = ((atk + sustain_hold) + rel);
g.gain.setValueAtTime(1.0E-4,t);

g.gain.linearRampToValueAtTime(sustain,(t + atk));

g.gain.setValueAtTime(sustain,((t + atk) + sustain_hold));

g.gain.exponentialRampToValueAtTime(1.0E-4,(t + total));

source.connect(g);

return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"node","node",581201198),g,new cljs.core.Keyword(null,"duration","duration",1444101068),total], null);
});
/**
 * Automate `param` according to envelope data, starting at audio time `t0`.
 * Uses the pure envelope/segment-samples fn to build Float32Arrays for
 * curve-based segments; direct Web Audio ramp methods for :lin/:exp/:step.
 */
repulse.synth.apply_env_automation_BANG_ = (function repulse$synth$apply_env_automation_BANG_(param,t0,levels,times,curves){
var n_segs = cljs.core.count(times);
param.setValueAtTime((function (){var x__5087__auto__ = 1.0E-4;
var y__5088__auto__ = cljs.core.first(levels);
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})(),t0);

var i = (0);
var cur_t = t0;
while(true){
if((i < n_segs)){
var from_val = cljs.core.nth.cljs$core$IFn$_invoke$arity$2(levels,i);
var to_val = cljs.core.nth.cljs$core$IFn$_invoke$arity$2(levels,(i + (1)));
var seg_time = cljs.core.nth.cljs$core$IFn$_invoke$arity$2(times,i);
var curve = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(curves,i,new cljs.core.Keyword(null,"lin","lin",1904063437));
var end_t = (cur_t + seg_time);
var G__8497_8565 = curve;
var G__8497_8566__$1 = (((G__8497_8565 instanceof cljs.core.Keyword))?G__8497_8565.fqn:null);
switch (G__8497_8566__$1) {
case "lin":
param.linearRampToValueAtTime(to_val,end_t);

break;
case "exp":
param.exponentialRampToValueAtTime((function (){var x__5087__auto__ = 1.0E-4;
var y__5088__auto__ = to_val;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})(),end_t);

break;
case "step":
param.setValueAtTime(to_val,end_t);

break;
default:
var samples_8571 = repulse.envelope.segment_samples(from_val,to_val,curve,(32));
var arr_8572 = (new Float32Array(cljs.core.count(samples_8571)));
var n__5593__auto___8573 = cljs.core.count(samples_8571);
var j_8576 = (0);
while(true){
if((j_8576 < n__5593__auto___8573)){
(arr_8572[j_8576] = cljs.core.nth.cljs$core$IFn$_invoke$arity$2(samples_8571,j_8576));

var G__8577 = (j_8576 + (1));
j_8576 = G__8577;
continue;
} else {
}
break;
}

param.setValueCurveAtTime(arr_8572,cur_t,seg_time);

}

var G__8578 = (i + (1));
var G__8579 = end_t;
i = G__8578;
cur_t = G__8579;
continue;
} else {
return null;
}
break;
}
});
/**
 * General envelope: (env-gen env-data source).
 * env-data is a map produced by (env levels times curves?).
 * Applies the envelope to source via a GainNode; returns {:node g :duration d}.
 */
repulse.synth.env_gen_node = (function repulse$synth$env_gen_node(ac,t,env_data,source){
var map__8503 = env_data;
var map__8503__$1 = cljs.core.__destructure_map(map__8503);
var levels = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8503__$1,new cljs.core.Keyword(null,"levels","levels",-950747887));
var times = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8503__$1,new cljs.core.Keyword(null,"times","times",1671571467));
var curves = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8503__$1,new cljs.core.Keyword(null,"curves","curves",-510805378));
var g = ac.createGain();
var curves__$1 = (function (){var or__5002__auto__ = curves;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return cljs.core.vec(cljs.core.repeat.cljs$core$IFn$_invoke$arity$2(cljs.core.count(times),new cljs.core.Keyword(null,"lin","lin",1904063437)));
}
})();
repulse.synth.apply_env_automation_BANG_(g.gain,t,levels,times,curves__$1);

source.connect(g);

return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"node","node",581201198),g,new cljs.core.Keyword(null,"duration","duration",1444101068),repulse.envelope.total_duration(env_data)], null);
});
/**
 * Write a signal to a named bus.
 * synth-name tracks the sending synth so re-triggers replace (not accumulate)
 * the previous oscillator on that bus.
 * Returns {:node nil :bus-writer true} — no audio output to the track.
 */
repulse.synth.out_node = (function repulse$synth$out_node(synth_name,bus_name,signal){
var kw_8580 = (((bus_name instanceof cljs.core.Keyword))?bus_name:cljs.core.keyword.cljs$core$IFn$_invoke$arity$1(bus_name));
if(cljs.core.truth_(repulse.bus.get_bus_node(kw_8580))){
repulse.bus.replace_synth_writer_BANG_(synth_name,kw_8580,signal);
} else {
console.warn("[REPuLse] bus",cljs.core.str.cljs$core$IFn$_invoke$arity$1(kw_8580),"not found \u2014 declare it with (bus :name) first");
}

return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"node","node",581201198),null,new cljs.core.Keyword(null,"bus-writer","bus-writer",1063196251),true], null);
});
/**
 * Read from a named bus, returning its AudioNode as a UGen source.
 * If the bus does not exist, logs a warning and returns a silent GainNode.
 */
repulse.synth.in_node = (function repulse$synth$in_node(ac,bus_name){
var kw = (((bus_name instanceof cljs.core.Keyword))?bus_name:cljs.core.keyword.cljs$core$IFn$_invoke$arity$1(bus_name));
var or__5002__auto__ = repulse.bus.get_bus_node(kw);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
console.warn("[REPuLse] bus",cljs.core.str.cljs$core$IFn$_invoke$arity$1(kw),"not found \u2014 returning silence");

var g = ac.createGain();
g.gain.setValueAtTime(0.0,(0));

return g;
}
});
/**
 * Control-rate pass-through.
 * Web Audio does not expose control-rate processing to JS, so this is a
 * transparent identity wrapper — the signal is returned unchanged.
 */
repulse.synth.kr_node = (function repulse$synth$kr_node(_rate,signal){
return signal;
});
repulse.synth.unwrap = (function repulse$synth$unwrap(x){
return repulse.lisp.eval.unwrap(x);
});
/**
 * Creates the Web Audio graph builder function for a defsynth.
 * When called with [ac t param-map] it evaluates the synth body
 * with UGen functions and param bindings in scope.
 * synth-name is used by out-node for replace-on-retrigger tracking.
 */
repulse.synth.make_build_fn = (function repulse$synth$make_build_fn(synth_name,param_names,body,closed_env){
return (function (ac,t,param_map){
var param_bindings = cljs.core.zipmap(param_names,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p1__8508_SHARP_){
return cljs.core.get.cljs$core$IFn$_invoke$arity$2(param_map,cljs.core.keyword.cljs$core$IFn$_invoke$arity$1(p1__8508_SHARP_));
}),param_names));
var ugen_env = cljs.core.PersistentHashMap.fromArrays(["env-asr","delay-node","env-gen","kr","hpf","env-perc","noise","sin","saw","lpf","out","square","bpf","mix","tri","gain","in"],[(function (atk,sus,rel,src){
return repulse.synth.env_asr_node(ac,t,repulse.synth.unwrap(atk),repulse.synth.unwrap(sus),repulse.synth.unwrap(rel),repulse.synth.unwrap(src));
}),(function (time,src){
return repulse.synth.delay_line(ac,repulse.synth.unwrap(time),repulse.synth.unwrap(src));
}),(function (env_data,src){
return repulse.synth.env_gen_node(ac,t,repulse.synth.unwrap(env_data),repulse.synth.unwrap(src));
}),(function (rate_v,signal_v){
return repulse.synth.kr_node(repulse.synth.unwrap(rate_v),repulse.synth.unwrap(signal_v));
}),(function (cutoff,src){
return repulse.synth.hpf_node(ac,repulse.synth.unwrap(cutoff),repulse.synth.unwrap(src));
}),(function (atk,dec,src){
return repulse.synth.env_perc_node(ac,t,repulse.synth.unwrap(atk),repulse.synth.unwrap(dec),repulse.synth.unwrap(src));
}),(function (){
return repulse.synth.noise_src(ac,t);
}),(function (freq){
return repulse.synth.sin_osc(ac,t,repulse.synth.unwrap(freq));
}),(function (freq){
return repulse.synth.saw_osc(ac,t,repulse.synth.unwrap(freq));
}),(function (cutoff,src){
return repulse.synth.lpf_node(ac,repulse.synth.unwrap(cutoff),repulse.synth.unwrap(src));
}),(function (bus_name_v,signal_v){
return repulse.synth.out_node(synth_name,repulse.synth.unwrap(bus_name_v),repulse.synth.unwrap(signal_v));
}),(function (freq){
return repulse.synth.square_osc(ac,t,repulse.synth.unwrap(freq));
}),(function (freq,src){
return repulse.synth.bpf_node(ac,repulse.synth.unwrap(freq),repulse.synth.unwrap(src));
}),(function (a,b){
return repulse.synth.mix_node(ac,repulse.synth.unwrap(a),repulse.synth.unwrap(b));
}),(function (freq){
return repulse.synth.tri_osc(ac,t,repulse.synth.unwrap(freq));
}),(function (level,src){
return repulse.synth.gain_node(ac,repulse.synth.unwrap(level),repulse.synth.unwrap(src));
}),(function (bus_name_v){
return repulse.synth.in_node(ac,repulse.synth.unwrap(bus_name_v));
})]);
var local = cljs.core.merge.cljs$core$IFn$_invoke$arity$variadic(cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([closed_env,param_bindings,ugen_env], 0));
return cljs.core.last(cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p1__8509_SHARP_){
return repulse.lisp.eval.eval_form(p1__8509_SHARP_,local);
}),body));
});
});
/**
 * Instantiate a user-defined synth at scheduled time t.
 * dest is the AudioNode to connect to (e.g. master-gain or track gain node).
 * Schedules disconnection after the envelope completes.
 */
repulse.synth.play_synth_BANG_ = (function repulse$synth$play_synth_BANG_(ac,t,synth_def,param_map,dest){
var map__8524 = synth_def;
var map__8524__$1 = cljs.core.__destructure_map(map__8524);
var build_fn = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8524__$1,new cljs.core.Keyword(null,"build-fn","build-fn",845748249));
var result = (build_fn.cljs$core$IFn$_invoke$arity$3 ? build_fn.cljs$core$IFn$_invoke$arity$3(ac,t,param_map) : build_fn.call(null,ac,t,param_map));
if(cljs.core.truth_((function (){var and__5000__auto__ = result;
if(cljs.core.truth_(and__5000__auto__)){
var and__5000__auto____$1 = cljs.core.map_QMARK_(result);
if(and__5000__auto____$1){
return new cljs.core.Keyword(null,"bus-writer","bus-writer",1063196251).cljs$core$IFn$_invoke$arity$1(result);
} else {
return and__5000__auto____$1;
}
} else {
return and__5000__auto__;
}
})())){
return null;
} else {
if(cljs.core.truth_((function (){var and__5000__auto__ = result;
if(cljs.core.truth_(and__5000__auto__)){
var and__5000__auto____$1 = cljs.core.map_QMARK_(result);
if(and__5000__auto____$1){
return new cljs.core.Keyword(null,"node","node",581201198).cljs$core$IFn$_invoke$arity$1(result);
} else {
return and__5000__auto____$1;
}
} else {
return and__5000__auto__;
}
})())){
var out_n = new cljs.core.Keyword(null,"node","node",581201198).cljs$core$IFn$_invoke$arity$1(result);
var dur = new cljs.core.Keyword(null,"duration","duration",1444101068).cljs$core$IFn$_invoke$arity$1(result);
out_n.connect(dest);

return setTimeout((function (){
try{return out_n.disconnect();
}catch (e8532){var _ = e8532;
return null;
}}),((dur + 0.1) * (1000)));
} else {
if(cljs.core.truth_(result)){
result.connect(dest);

return setTimeout((function (){
try{return result.disconnect();
}catch (e8533){var _ = e8533;
return null;
}}),((1.5 + 0.1) * (1000)));
} else {
return null;
}
}
}
});

//# sourceMappingURL=repulse.synth.js.map
