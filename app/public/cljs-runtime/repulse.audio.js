goog.provide('repulse.audio');
repulse.audio.ctx = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
if((typeof repulse !== 'undefined') && (typeof repulse.audio !== 'undefined') && (typeof repulse.audio.worklet_node !== 'undefined')){
} else {
repulse.audio.worklet_node = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
if((typeof repulse !== 'undefined') && (typeof repulse.audio !== 'undefined') && (typeof repulse.audio.worklet_ready_QMARK_ !== 'undefined')){
} else {
repulse.audio.worklet_ready_QMARK_ = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(false);
}
if((typeof repulse !== 'undefined') && (typeof repulse.audio !== 'undefined') && (typeof repulse.audio.master_gain !== 'undefined')){
} else {
repulse.audio.master_gain = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
if((typeof repulse !== 'undefined') && (typeof repulse.audio !== 'undefined') && (typeof repulse.audio.analyser_node !== 'undefined')){
} else {
repulse.audio.analyser_node = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
if((typeof repulse !== 'undefined') && (typeof repulse.audio !== 'undefined') && (typeof repulse.audio.track_nodes !== 'undefined')){
} else {
repulse.audio.track_nodes = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
}
repulse.audio.build_master_chain_BANG_ = (function repulse$audio$build_master_chain_BANG_(ac){
var gain = (function (){var G__8547 = ac.createGain();
G__8547.gain.setValueAtTime(1.0,ac.currentTime);

return G__8547;
})();
var anl = (function (){var G__8548 = ac.createAnalyser();
(G__8548["fftSize"] = (2048));

(G__8548["smoothingTimeConstant"] = 0.8);

return G__8548;
})();
gain.connect(anl);

anl.connect(ac.destination);

cljs.core.reset_BANG_(repulse.audio.master_gain,gain);

return cljs.core.reset_BANG_(repulse.audio.analyser_node,anl);
});
/**
 * Register the AudioWorkletProcessor. The main thread fetches the WASM bytes
 * and transfers them as an ArrayBuffer; the worklet uses initSync to compile
 * and instantiate synchronously — works on Chrome, Firefox, and Safari.
 * Falls back to JS synthesis if AudioWorklet is unavailable.
 */
repulse.audio.init_worklet_BANG_ = (function repulse$audio$init_worklet_BANG_(ac){
var temp__5802__auto__ = ac.audioWorklet;
if(cljs.core.truth_(temp__5802__auto__)){
var worklet = temp__5802__auto__;
return worklet.addModule("/worklet.js").then((function (){
var node = (new AudioWorkletNode(ac,"repulse-processor",({"outputChannelCount": [(2)]})));
node.connect(cljs.core.deref(repulse.audio.master_gain));

cljs.core.reset_BANG_(repulse.audio.worklet_node,node);

(node.port.onmessage = (function (e){
var d = e.data;
var pred__8556 = cljs.core._EQ_;
var expr__8557 = d.type;
if(cljs.core.truth_((pred__8556.cljs$core$IFn$_invoke$arity$2 ? pred__8556.cljs$core$IFn$_invoke$arity$2("ready",expr__8557) : pred__8556.call(null,"ready",expr__8557)))){
cljs.core.reset_BANG_(repulse.audio.worklet_ready_QMARK_,true);

return console.log("[REPuLse] audio backend: audioworklet+wasm");
} else {
if(cljs.core.truth_((pred__8556.cljs$core$IFn$_invoke$arity$2 ? pred__8556.cljs$core$IFn$_invoke$arity$2("error",expr__8557) : pred__8556.call(null,"error",expr__8557)))){
return console.warn("[REPuLse] Worklet WASM error:",d.message);
} else {
return null;
}
}
}));

return fetch("/repulse_audio_bg.wasm").then((function (resp){
return resp.arrayBuffer();
})).then((function (buf){
node.port.postMessage(({"type": "init", "wasmBytes": buf}),[buf]);

return setTimeout((function (){
if(cljs.core.truth_(cljs.core.deref(repulse.audio.worklet_ready_QMARK_))){
return null;
} else {
return console.warn("[REPuLse] audio backend: clojurescript synthesis (WASM init timed out \u2014 worklet did not send 'ready' after 5 s)");
}
}),(5000));
})).catch((function (e){
return console.warn("[REPuLse] WASM fetch failed:",e);
}));
})).catch((function (e){
return console.warn("[REPuLse] audio backend: clojurescript synthesis (Worklet load failed)",e);
}));
} else {
return console.warn("[REPuLse] audio backend: clojurescript synthesis (AudioWorklet not supported)");
}
});
repulse.audio.make_audio_context = (function repulse$audio$make_audio_context(){
var ctor = (function (){var or__5002__auto__ = window.AudioContext;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return window.webkitAudioContext;
}
})();
return (new ctor());
});
repulse.audio.get_ctx = (function repulse$audio$get_ctx(){
var or__5002__auto__ = cljs.core.deref(repulse.audio.ctx);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
var c = repulse.audio.make_audio_context();
cljs.core.reset_BANG_(repulse.audio.ctx,c);

repulse.audio.build_master_chain_BANG_(c);

repulse.audio.init_worklet_BANG_(c);

return c;
}
});
/**
 * Create a GainNode for a track if it doesn't exist; connect it to masterGain.
 */
repulse.audio.ensure_track_node_BANG_ = (function repulse$audio$ensure_track_node_BANG_(ac,track_name){
if(cljs.core.truth_((function (){var and__5000__auto__ = track_name;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core.not(cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.audio.track_nodes),track_name));
} else {
return and__5000__auto__;
}
})())){
var gain = (function (){var G__8570 = ac.createGain();
G__8570.gain.setValueAtTime(1.0,ac.currentTime);

return G__8570;
})();
gain.connect(cljs.core.deref(repulse.audio.master_gain));

return cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.audio.track_nodes,cljs.core.assoc,track_name,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"gain-node","gain-node",-1178526839),gain,new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234),cljs.core.PersistentVector.EMPTY], null));
} else {
return null;
}
});
/**
 * Returns the AudioNode that a source should connect to for the given track.
 * Falls back to masterGain (or destination for offline) when track-name is nil.
 */
repulse.audio.output_for_track = (function repulse$audio$output_for_track(ac,track_name){
if(cljs.core.truth_((function (){var and__5000__auto__ = track_name;
if(cljs.core.truth_(and__5000__auto__)){
return (!((ac instanceof OfflineAudioContext)));
} else {
return and__5000__auto__;
}
})())){
var temp__5802__auto__ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.audio.track_nodes),track_name);
if(cljs.core.truth_(temp__5802__auto__)){
var tn = temp__5802__auto__;
var temp__5802__auto____$1 = cljs.core.first(new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234).cljs$core$IFn$_invoke$arity$1(tn));
if(cljs.core.truth_(temp__5802__auto____$1)){
var first_fx = temp__5802__auto____$1;
return new cljs.core.Keyword(null,"input","input",556931961).cljs$core$IFn$_invoke$arity$1(first_fx);
} else {
return cljs.core.deref(repulse.audio.master_gain);
}
} else {
var or__5002__auto__ = cljs.core.deref(repulse.audio.master_gain);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return ac.destination;
}
}
} else {
if((ac instanceof OfflineAudioContext)){
return ac.destination;
} else {
var or__5002__auto__ = cljs.core.deref(repulse.audio.master_gain);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return ac.destination;
}
}
}
});
repulse.audio.output_node = (function repulse$audio$output_node(ac){
if((ac instanceof OfflineAudioContext)){
return ac.destination;
} else {
var or__5002__auto__ = cljs.core.deref(repulse.audio.master_gain);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return ac.destination;
}
}
});
repulse.audio.make_kick = (function repulse$audio$make_kick(ac,t,amp,pan,dest){
var osc = ac.createOscillator();
var gain = ac.createGain();
var panner = ac.createStereoPanner();
var pk = amp;
(osc.type = "sine");

osc.frequency.setValueAtTime((150),t);

osc.frequency.exponentialRampToValueAtTime((40),(t + 0.06));

gain.gain.setValueAtTime(pk,t);

gain.gain.exponentialRampToValueAtTime(0.001,(t + 0.4));

panner.pan.setValueAtTime(pan,t);

osc.connect(gain);

gain.connect(panner);

panner.connect(dest);

osc.start(t);

return osc.stop((t + 0.4));
});
repulse.audio.make_snare = (function repulse$audio$make_snare(ac,t,amp,pan,dest){
var buf_size = (4096);
var buf = ac.createBuffer((1),buf_size,ac.sampleRate);
var data = buf.getChannelData((0));
var _ = (function (){var n__5593__auto__ = buf_size;
var i = (0);
while(true){
if((i < n__5593__auto__)){
(data[i] = (((2) * Math.random()) - (1)));

var G__9063 = (i + (1));
i = G__9063;
continue;
} else {
return null;
}
break;
}
})();
var src = ac.createBufferSource();
var bpf = ac.createBiquadFilter();
var gain = ac.createGain();
var panner = ac.createStereoPanner();
var pk = (0.9 * amp);
(src.buffer = buf);

(bpf.type = "bandpass");

bpf.frequency.setValueAtTime((200),t);

gain.gain.setValueAtTime(pk,t);

gain.gain.exponentialRampToValueAtTime(0.001,(t + 0.2));

panner.pan.setValueAtTime(pan,t);

src.connect(bpf);

bpf.connect(gain);

gain.connect(panner);

panner.connect(dest);

src.start(t);

return src.stop((t + 0.2));
});
/**
 * Noise burst through a highpass filter. dur controls envelope length.
 */
repulse.audio.make_hihat = (function repulse$audio$make_hihat(var_args){
var G__8583 = arguments.length;
switch (G__8583) {
case 5:
return repulse.audio.make_hihat.cljs$core$IFn$_invoke$arity$5((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),(arguments[(3)]),(arguments[(4)]));

break;
case 8:
return repulse.audio.make_hihat.cljs$core$IFn$_invoke$arity$8((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),(arguments[(3)]),(arguments[(4)]),(arguments[(5)]),(arguments[(6)]),(arguments[(7)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.audio.make_hihat.cljs$core$IFn$_invoke$arity$5 = (function (ac,t,amp,pan,dest){
return repulse.audio.make_hihat.cljs$core$IFn$_invoke$arity$8(ac,t,amp,pan,dest,0.045,0.5,(8000));
}));

(repulse.audio.make_hihat.cljs$core$IFn$_invoke$arity$8 = (function (ac,t,amp,pan,dest,dur,gain_scale,hpf_freq){
var buf_size = (2048);
var buf = ac.createBuffer((1),buf_size,ac.sampleRate);
var data = buf.getChannelData((0));
var _ = (function (){var n__5593__auto__ = buf_size;
var i = (0);
while(true){
if((i < n__5593__auto__)){
(data[i] = (((2) * Math.random()) - (1)));

var G__9076 = (i + (1));
i = G__9076;
continue;
} else {
return null;
}
break;
}
})();
var src = ac.createBufferSource();
var hpf = ac.createBiquadFilter();
var gain = ac.createGain();
var panner = ac.createStereoPanner();
var pk = (gain_scale * amp);
(src.buffer = buf);

(src.loop = true);

(hpf.type = "highpass");

hpf.frequency.setValueAtTime(hpf_freq,t);

gain.gain.setValueAtTime(pk,t);

gain.gain.exponentialRampToValueAtTime(0.001,(t + dur));

panner.pan.setValueAtTime(pan,t);

src.connect(hpf);

hpf.connect(gain);

gain.connect(panner);

panner.connect(dest);

src.start(t);

return src.stop((t + dur));
}));

(repulse.audio.make_hihat.cljs$lang$maxFixedArity = 8);

/**
 * JS-synthesis fallback for when the AudioWorklet/WASM is unavailable.
 * dur     — decay duration in seconds (default 1.5)
 * amp     — peak amplitude 0.0–1.0 (default 1.0; scaled by 0.5 internally)
 * attack  — linear ramp-up time in seconds (default 0.001 = instant)
 * pan     — stereo position -1.0 (left) to 1.0 (right), default 0.0
 * dest    — AudioNode to connect to (default: output-node)
 */
repulse.audio.make_sine = (function repulse$audio$make_sine(var_args){
var G__8598 = arguments.length;
switch (G__8598) {
case 3:
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$3((arguments[(0)]),(arguments[(1)]),(arguments[(2)]));

break;
case 4:
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$4((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),(arguments[(3)]));

break;
case 5:
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$5((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),(arguments[(3)]),(arguments[(4)]));

break;
case 6:
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$6((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),(arguments[(3)]),(arguments[(4)]),(arguments[(5)]));

break;
case 7:
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$7((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),(arguments[(3)]),(arguments[(4)]),(arguments[(5)]),(arguments[(6)]));

break;
case 8:
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),(arguments[(3)]),(arguments[(4)]),(arguments[(5)]),(arguments[(6)]),(arguments[(7)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$3 = (function (ac,t,freq){
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,freq,1.5,1.0,0.001,0.0,repulse.audio.output_node(ac));
}));

(repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$4 = (function (ac,t,freq,dur){
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,freq,dur,1.0,0.001,0.0,repulse.audio.output_node(ac));
}));

(repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$5 = (function (ac,t,freq,dur,amp){
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,freq,dur,amp,0.001,0.0,repulse.audio.output_node(ac));
}));

(repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$6 = (function (ac,t,freq,dur,amp,attack){
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,freq,dur,amp,attack,0.0,repulse.audio.output_node(ac));
}));

(repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$7 = (function (ac,t,freq,dur,amp,attack,pan){
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,freq,dur,amp,attack,pan,repulse.audio.output_node(ac));
}));

(repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8 = (function (ac,t,freq,dur,amp,attack,pan,dest){
var osc = ac.createOscillator();
var gain = ac.createGain();
var panner = ac.createStereoPanner();
var peak = (0.5 * amp);
var atk = (function (){var x__5087__auto__ = 0.001;
var y__5088__auto__ = attack;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
var stop_t = ((t + atk) + dur);
(osc.type = "sine");

osc.frequency.setValueAtTime(freq,t);

gain.gain.setValueAtTime(1.0E-4,t);

gain.gain.linearRampToValueAtTime(peak,(t + atk));

gain.gain.exponentialRampToValueAtTime(1.0E-4,stop_t);

panner.pan.setValueAtTime(pan,t);

osc.connect(gain);

gain.connect(panner);

panner.connect(dest);

osc.start(t);

return osc.stop(stop_t);
}));

(repulse.audio.make_sine.cljs$lang$maxFixedArity = 8);

repulse.audio.make_saw = (function repulse$audio$make_saw(ac,t,freq,dur,amp,attack,pan,dest){
var osc = ac.createOscillator();
var gain = ac.createGain();
var panner = ac.createStereoPanner();
var peak = (0.5 * amp);
var atk = (function (){var x__5087__auto__ = 0.001;
var y__5088__auto__ = attack;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
var stop_t = ((t + atk) + dur);
(osc.type = "sawtooth");

osc.frequency.setValueAtTime(freq,t);

gain.gain.setValueAtTime(1.0E-4,t);

gain.gain.linearRampToValueAtTime(peak,(t + atk));

gain.gain.exponentialRampToValueAtTime(1.0E-4,stop_t);

panner.pan.setValueAtTime(pan,t);

osc.connect(gain);

gain.connect(panner);

panner.connect(dest);

osc.start(t);

return osc.stop(stop_t);
});
/**
 * Build a PeriodicWave for a pulse wave with duty cycle pw (0.0–1.0).
 * Uses Fourier synthesis so any pulse width is supported — unlike the native
 * OscillatorNode 'square' type which is always 50%.
 * Coefficients: real[n] = 2·sin(2πnD)/(πn), imag[n] = 2·(1−cos(2πnD))/(πn).
 */
repulse.audio.make_pulse_wave = (function repulse$audio$make_pulse_wave(ac,pw){
var n = (64);
var real = (new Float32Array(n));
var imag = (new Float32Array(n));
var d = pw;
(real[(0)] = 0.0);

(imag[(0)] = 0.0);

var n__5593__auto___9104 = (n - (1));
var i_9106 = (0);
while(true){
if((i_9106 < n__5593__auto___9104)){
var k_9110 = (i_9106 + (1));
var pk_9111 = (Math.PI * k_9110);
(real[k_9110] = ((2.0 * Math.sin((((2.0 * Math.PI) * k_9110) * d))) / pk_9111));

(imag[k_9110] = ((2.0 * (1.0 - Math.cos((((2.0 * Math.PI) * k_9110) * d)))) / pk_9111));

var G__9112 = (i_9106 + (1));
i_9106 = G__9112;
continue;
} else {
}
break;
}

return ac.createPeriodicWave(real,imag,({"disableNormalization": false}));
});
repulse.audio.make_square = (function repulse$audio$make_square(ac,t,freq,dur,amp,attack,pan,dest,pw){
var osc = ac.createOscillator();
var gain = ac.createGain();
var panner = ac.createStereoPanner();
var peak = (0.5 * amp);
var atk = (function (){var x__5087__auto__ = 0.001;
var y__5088__auto__ = attack;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
var stop_t = ((t + atk) + dur);
if((pw === 0.5)){
(osc.type = "square");
} else {
osc.setPeriodicWave(repulse.audio.make_pulse_wave(ac,pw));
}

osc.frequency.setValueAtTime(freq,t);

gain.gain.setValueAtTime(1.0E-4,t);

gain.gain.linearRampToValueAtTime(peak,(t + atk));

gain.gain.exponentialRampToValueAtTime(1.0E-4,stop_t);

panner.pan.setValueAtTime(pan,t);

osc.connect(gain);

gain.connect(panner);

panner.connect(dest);

osc.start(t);

return osc.stop(stop_t);
});
repulse.audio.make_noise = (function repulse$audio$make_noise(ac,t,dur,amp,pan,dest){
var buf_size = (function (){var x__5087__auto__ = (1);
var y__5088__auto__ = (ac.sampleRate * (Math.ceil(dur) | (0)));
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
var buf = ac.createBuffer((1),buf_size,ac.sampleRate);
var data = buf.getChannelData((0));
var _ = (function (){var n__5593__auto__ = buf_size;
var i = (0);
while(true){
if((i < n__5593__auto__)){
(data[i] = (((2) * Math.random()) - (1)));

var G__9118 = (i + (1));
i = G__9118;
continue;
} else {
return null;
}
break;
}
})();
var src = ac.createBufferSource();
var gain = ac.createGain();
var panner = ac.createStereoPanner();
var pk = (0.3 * amp);
(src.buffer = buf);

gain.gain.setValueAtTime(pk,t);

gain.gain.exponentialRampToValueAtTime(0.001,(t + dur));

panner.pan.setValueAtTime(pan,t);

src.connect(gain);

gain.connect(panner);

panner.connect(dest);

src.start(t);

return src.stop((t + dur));
});
/**
 * Send a trigger message to the AudioWorklet. Returns true if worklet is ready.
 * dest must be master-gain — worklet always outputs there and cannot be redirected.
 */
repulse.audio.worklet_trigger_BANG_ = (function repulse$audio$worklet_trigger_BANG_(value,t,dest){
if(cljs.core.truth_((function (){var and__5000__auto__ = cljs.core.deref(repulse.audio.worklet_ready_QMARK_);
if(cljs.core.truth_(and__5000__auto__)){
var and__5000__auto____$1 = cljs.core.deref(repulse.audio.worklet_node);
if(cljs.core.truth_(and__5000__auto____$1)){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(dest,cljs.core.deref(repulse.audio.master_gain));
} else {
return and__5000__auto____$1;
}
} else {
return and__5000__auto__;
}
})())){
cljs.core.deref(repulse.audio.worklet_node).port.postMessage(({"type": "trigger", "value": value, "time": t}));

return true;
} else {
return null;
}
});
/**
 * Send a trigger_v2 message with explicit synthesis parameters. Returns true if ready.
 * dest must be master-gain — worklet always outputs there and cannot be redirected.
 */
repulse.audio.worklet_trigger_v2_BANG_ = (function repulse$audio$worklet_trigger_v2_BANG_(value,t,amp,attack,decay,pan,dest){
if(cljs.core.truth_((function (){var and__5000__auto__ = cljs.core.deref(repulse.audio.worklet_ready_QMARK_);
if(cljs.core.truth_(and__5000__auto__)){
var and__5000__auto____$1 = cljs.core.deref(repulse.audio.worklet_node);
if(cljs.core.truth_(and__5000__auto____$1)){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(dest,cljs.core.deref(repulse.audio.master_gain));
} else {
return and__5000__auto____$1;
}
} else {
return and__5000__auto__;
}
})())){
cljs.core.deref(repulse.audio.worklet_node).port.postMessage(({"type": "trigger_v2", "value": value, "time": t, "amp": amp, "attack": attack, "decay": decay, "pan": pan}));

return true;
} else {
return null;
}
});
/**
 * JS synthesis fallback — used when AudioWorklet is unavailable.
 */
repulse.audio.js_synth = (function repulse$audio$js_synth(var_args){
var G__8693 = arguments.length;
switch (G__8693) {
case 4:
return repulse.audio.js_synth.cljs$core$IFn$_invoke$arity$4((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),(arguments[(3)]));

break;
case 6:
return repulse.audio.js_synth.cljs$core$IFn$_invoke$arity$6((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),(arguments[(3)]),(arguments[(4)]),(arguments[(5)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.audio.js_synth.cljs$core$IFn$_invoke$arity$4 = (function (ac,t,value,dest){
return repulse.audio.js_synth.cljs$core$IFn$_invoke$arity$6(ac,t,value,1.0,0.0,dest);
}));

(repulse.audio.js_synth.cljs$core$IFn$_invoke$arity$6 = (function (ac,t,value,amp,pan,dest){
var G__8698 = value;
var G__8698__$1 = (((G__8698 instanceof cljs.core.Keyword))?G__8698.fqn:null);
switch (G__8698__$1) {
case "bd":
return repulse.audio.make_kick(ac,t,amp,pan,dest);

break;
case "sd":
return repulse.audio.make_snare(ac,t,amp,pan,dest);

break;
case "hh":
return repulse.audio.make_hihat.cljs$core$IFn$_invoke$arity$5(ac,t,amp,pan,dest);

break;
case "oh":
return repulse.audio.make_hihat.cljs$core$IFn$_invoke$arity$8(ac,t,amp,pan,dest,1.0,0.4,(8000));

break;
default:
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,(440),1.5,amp,0.001,pan,dest);

}
}));

(repulse.audio.js_synth.cljs$lang$maxFixedArity = 6);

repulse.audio.play_event = (function repulse$audio$play_event(var_args){
var G__8711 = arguments.length;
switch (G__8711) {
case 3:
return repulse.audio.play_event.cljs$core$IFn$_invoke$arity$3((arguments[(0)]),(arguments[(1)]),(arguments[(2)]));

break;
case 4:
return repulse.audio.play_event.cljs$core$IFn$_invoke$arity$4((arguments[(0)]),(arguments[(1)]),(arguments[(2)]),(arguments[(3)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.audio.play_event.cljs$core$IFn$_invoke$arity$3 = (function (ac,t,value){
return repulse.audio.play_event.cljs$core$IFn$_invoke$arity$4(ac,t,value,null);
}));

(repulse.audio.play_event.cljs$core$IFn$_invoke$arity$4 = (function (ac,t,value,track_name){
var offline_QMARK_ = (ac instanceof OfflineAudioContext);
var dest = repulse.audio.output_for_track(ac,track_name);
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(value,new cljs.core.Keyword(null,"_","_",1453416199))){
return null;
} else {
if(cljs.core.truth_((function (){var and__5000__auto__ = cljs.core.map_QMARK_(value);
if(and__5000__auto__){
var and__5000__auto____$1 = new cljs.core.Keyword(null,"synth","synth",-862700847).cljs$core$IFn$_invoke$arity$1(value);
if(cljs.core.truth_(and__5000__auto____$1)){
return repulse.synth.lookup_synth(new cljs.core.Keyword(null,"synth","synth",-862700847).cljs$core$IFn$_invoke$arity$1(value));
} else {
return and__5000__auto____$1;
}
} else {
return and__5000__auto__;
}
})())){
var synth_def = repulse.synth.lookup_synth(new cljs.core.Keyword(null,"synth","synth",-862700847).cljs$core$IFn$_invoke$arity$1(value));
return repulse.synth.play_synth_BANG_(ac,t,synth_def,value,dest);
} else {
if(cljs.core.truth_((function (){var and__5000__auto__ = cljs.core.map_QMARK_(value);
if(and__5000__auto__){
return new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value);
} else {
return and__5000__auto__;
}
})())){
var note = new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value);
var amp_v = new cljs.core.Keyword(null,"amp","amp",271690571).cljs$core$IFn$_invoke$arity$2(value,1.0);
var attack_v = new cljs.core.Keyword(null,"attack","attack",1957061788).cljs$core$IFn$_invoke$arity$2(value,0.001);
var decay_v = new cljs.core.Keyword(null,"decay","decay",1036712184).cljs$core$IFn$_invoke$arity$2(value,1.5);
var pan_v = new cljs.core.Keyword(null,"pan","pan",-307712792).cljs$core$IFn$_invoke$arity$2(value,0.0);
var synth = new cljs.core.Keyword(null,"synth","synth",-862700847).cljs$core$IFn$_invoke$arity$1(value);
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(note,new cljs.core.Keyword(null,"_","_",1453416199))){
return null;
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(synth,new cljs.core.Keyword(null,"saw","saw",-1928018630))){
var hz = ((repulse.theory.note_keyword_QMARK_(note))?repulse.theory.note__GT_hz(note):note);
var or__5002__auto__ = ((offline_QMARK_)?null:repulse.audio.worklet_trigger_v2_BANG_(["saw:",cljs.core.str.cljs$core$IFn$_invoke$arity$1(hz)].join(''),t,amp_v,attack_v,decay_v,pan_v,dest));
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return repulse.audio.make_saw(ac,t,hz,decay_v,amp_v,attack_v,pan_v,dest);
}
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(synth,new cljs.core.Keyword(null,"square","square",812434677))){
var hz = ((repulse.theory.note_keyword_QMARK_(note))?repulse.theory.note__GT_hz(note):note);
var pw_SINGLEQUOTE_ = (function (){var or__5002__auto__ = new cljs.core.Keyword(null,"pw","pw",354220944).cljs$core$IFn$_invoke$arity$1(value);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return 0.5;
}
})();
var or__5002__auto__ = ((offline_QMARK_)?null:repulse.audio.worklet_trigger_v2_BANG_(["square:",cljs.core.str.cljs$core$IFn$_invoke$arity$1(hz),":",cljs.core.str.cljs$core$IFn$_invoke$arity$1(pw_SINGLEQUOTE_)].join(''),t,amp_v,attack_v,decay_v,pan_v,dest));
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return repulse.audio.make_square(ac,t,hz,decay_v,amp_v,attack_v,pan_v,dest,pw_SINGLEQUOTE_);
}
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(synth,new cljs.core.Keyword(null,"fm","fm",1463745501))){
var hz = ((repulse.theory.note_keyword_QMARK_(note))?repulse.theory.note__GT_hz(note):note);
var index = (function (){var or__5002__auto__ = new cljs.core.Keyword(null,"index","index",-1531685915).cljs$core$IFn$_invoke$arity$1(value);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return 1.0;
}
})();
var ratio = (function (){var or__5002__auto__ = new cljs.core.Keyword(null,"ratio","ratio",-926560044).cljs$core$IFn$_invoke$arity$1(value);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return 2.0;
}
})();
var or__5002__auto__ = ((offline_QMARK_)?null:repulse.audio.worklet_trigger_v2_BANG_(["fm:",cljs.core.str.cljs$core$IFn$_invoke$arity$1(hz),":",cljs.core.str.cljs$core$IFn$_invoke$arity$1(index),":",cljs.core.str.cljs$core$IFn$_invoke$arity$1(ratio)].join(''),t,amp_v,attack_v,decay_v,pan_v,dest));
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,hz,decay_v,amp_v,attack_v,pan_v,dest);
}
} else {
if((note instanceof cljs.core.Keyword)){
if(repulse.theory.note_keyword_QMARK_(note)){
var hz = repulse.theory.note__GT_hz(note);
if(offline_QMARK_){
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,hz,decay_v,amp_v,attack_v,pan_v,dest);
} else {
var or__5002__auto__ = repulse.audio.worklet_trigger_v2_BANG_(cljs.core.str.cljs$core$IFn$_invoke$arity$1(hz),t,amp_v,attack_v,decay_v,pan_v,dest);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,hz,decay_v,amp_v,attack_v,pan_v,dest);
}
}
} else {
var resolved = repulse.samples.resolve_keyword(note);
var extra = new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"rate","rate",-1428659698),new cljs.core.Keyword(null,"rate","rate",-1428659698).cljs$core$IFn$_invoke$arity$1(value),new cljs.core.Keyword(null,"begin","begin",-319034319),new cljs.core.Keyword(null,"begin","begin",-319034319).cljs$core$IFn$_invoke$arity$1(value),new cljs.core.Keyword(null,"end","end",-268185958),new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(value),new cljs.core.Keyword(null,"loop","loop",-395552849),new cljs.core.Keyword(null,"loop","loop",-395552849).cljs$core$IFn$_invoke$arity$1(value)], null);
if(repulse.samples.has_bank_QMARK_(resolved)){
return repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$8(ac,t,resolved,(0),amp_v,pan_v,extra,dest);
} else {
if(offline_QMARK_){
return repulse.audio.js_synth.cljs$core$IFn$_invoke$arity$6(ac,t,note,amp_v,pan_v,dest);
} else {
var or__5002__auto__ = repulse.audio.worklet_trigger_v2_BANG_(cljs.core.name(note),t,amp_v,attack_v,decay_v,pan_v,dest);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return repulse.audio.js_synth.cljs$core$IFn$_invoke$arity$6(ac,t,note,amp_v,pan_v,dest);
}
}

}
}
} else {
if(typeof note === 'number'){
if(offline_QMARK_){
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,note,decay_v,amp_v,attack_v,pan_v,dest);
} else {
var or__5002__auto__ = repulse.audio.worklet_trigger_v2_BANG_(cljs.core.str.cljs$core$IFn$_invoke$arity$1(note),t,amp_v,attack_v,decay_v,pan_v,dest);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,note,decay_v,amp_v,attack_v,pan_v,dest);
}
}
} else {
return null;
}
}
}
}
}
}
} else {
if(cljs.core.truth_((function (){var and__5000__auto__ = cljs.core.map_QMARK_(value);
if(and__5000__auto__){
return new cljs.core.Keyword(null,"bank","bank",-1982531798).cljs$core$IFn$_invoke$arity$1(value);
} else {
return and__5000__auto__;
}
})())){
var map__8737 = value;
var map__8737__$1 = cljs.core.__destructure_map(map__8737);
var bank = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8737__$1,new cljs.core.Keyword(null,"bank","bank",-1982531798));
var n = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8737__$1,new cljs.core.Keyword(null,"n","n",562130025));
var amp_v = new cljs.core.Keyword(null,"amp","amp",271690571).cljs$core$IFn$_invoke$arity$2(value,1.0);
var pan_v = new cljs.core.Keyword(null,"pan","pan",-307712792).cljs$core$IFn$_invoke$arity$2(value,0.0);
var extra = new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"rate","rate",-1428659698),new cljs.core.Keyword(null,"rate","rate",-1428659698).cljs$core$IFn$_invoke$arity$1(value),new cljs.core.Keyword(null,"begin","begin",-319034319),new cljs.core.Keyword(null,"begin","begin",-319034319).cljs$core$IFn$_invoke$arity$1(value),new cljs.core.Keyword(null,"end","end",-268185958),new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(value),new cljs.core.Keyword(null,"loop","loop",-395552849),new cljs.core.Keyword(null,"loop","loop",-395552849).cljs$core$IFn$_invoke$arity$1(value)], null);
if(repulse.samples.has_bank_QMARK_(bank)){
return repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$8(ac,t,bank,n,amp_v,pan_v,extra,dest);
} else {
if(offline_QMARK_){
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,(440),1.5,1.0,0.001,0.0,dest);
} else {
var or__5002__auto__ = repulse.audio.worklet_trigger_v2_BANG_(cljs.core.name(bank),t,amp_v,0.001,1.5,pan_v,dest);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,(440),1.5,amp_v,0.001,pan_v,dest);
}
}
}
} else {
if((value instanceof cljs.core.Keyword)){
if(repulse.theory.note_keyword_QMARK_(value)){
var hz = repulse.theory.note__GT_hz(value);
if(offline_QMARK_){
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,hz,1.5,1.0,0.001,0.0,dest);
} else {
var or__5002__auto__ = repulse.audio.worklet_trigger_BANG_(cljs.core.str.cljs$core$IFn$_invoke$arity$1(hz),t,dest);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,hz,1.5,1.0,0.001,0.0,dest);
}
}
} else {
var resolved = repulse.samples.resolve_keyword(value);
if(repulse.samples.has_bank_QMARK_(resolved)){
return repulse.samples.play_BANG_.cljs$core$IFn$_invoke$arity$8(ac,t,resolved,(0),1.0,0.0,cljs.core.PersistentArrayMap.EMPTY,dest);
} else {
if(offline_QMARK_){
return repulse.audio.js_synth.cljs$core$IFn$_invoke$arity$4(ac,t,value,dest);
} else {
var or__5002__auto__ = repulse.audio.worklet_trigger_BANG_(cljs.core.name(value),t,dest);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return repulse.audio.js_synth.cljs$core$IFn$_invoke$arity$4(ac,t,value,dest);
}
}

}
}
} else {
if(((cljs.core.map_QMARK_(value)) && (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"synth","synth",-862700847).cljs$core$IFn$_invoke$arity$1(value),new cljs.core.Keyword(null,"noise","noise",-994696820))))){
var amp_v = new cljs.core.Keyword(null,"amp","amp",271690571).cljs$core$IFn$_invoke$arity$2(value,1.0);
var decay_v = new cljs.core.Keyword(null,"decay","decay",1036712184).cljs$core$IFn$_invoke$arity$2(value,0.3);
var pan_v = new cljs.core.Keyword(null,"pan","pan",-307712792).cljs$core$IFn$_invoke$arity$2(value,0.0);
var or__5002__auto__ = ((offline_QMARK_)?null:repulse.audio.worklet_trigger_v2_BANG_("noise",t,amp_v,0.001,decay_v,pan_v,dest));
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return repulse.audio.make_noise(ac,t,decay_v,amp_v,pan_v,dest);
}
} else {
if(typeof value === 'number'){
if(offline_QMARK_){
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,value,1.5,1.0,0.001,0.0,dest);
} else {
var or__5002__auto__ = repulse.audio.worklet_trigger_BANG_(cljs.core.str.cljs$core$IFn$_invoke$arity$1(value),t,dest);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,value,1.5,1.0,0.001,0.0,dest);
}
}
} else {
return repulse.audio.make_sine.cljs$core$IFn$_invoke$arity$8(ac,t,(440),1.5,1.0,0.001,0.0,dest);

}
}
}
}
}
}
}
}));

(repulse.audio.play_event.cljs$lang$maxFixedArity = 4);

repulse.audio.tween_QMARK_ = (function repulse$audio$tween_QMARK_(v){
return ((cljs.core.map_QMARK_(v)) && (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(v),new cljs.core.Keyword(null,"tween","tween",1743568853))));
});
/**
 * Compute the interpolated value for a tween at audio time t.
 * ts = {:tween pv :start-time t0} stored by arm-transitions!
 * cycle-dur = seconds per bar (cycle).
 */
repulse.audio.interp_tween = (function repulse$audio$interp_tween(p__8747,t,cycle_dur){
var map__8748 = p__8747;
var map__8748__$1 = cljs.core.__destructure_map(map__8748);
var tween = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8748__$1,new cljs.core.Keyword(null,"tween","tween",1743568853));
var start_time = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8748__$1,new cljs.core.Keyword(null,"start-time","start-time",814801386));
var map__8750 = tween;
var map__8750__$1 = cljs.core.__destructure_map(map__8750);
var start = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8750__$1,new cljs.core.Keyword(null,"start","start",-355208981));
var end = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8750__$1,new cljs.core.Keyword(null,"end","end",-268185958));
var duration_bars = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8750__$1,new cljs.core.Keyword(null,"duration-bars","duration-bars",-1993701942));
var curve = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8750__$1,new cljs.core.Keyword(null,"curve","curve",-569677866));
var u = (function (){var x__5090__auto__ = (function (){var x__5087__auto__ = ((t - start_time) / (duration_bars * cycle_dur));
var y__5088__auto__ = 0.0;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
var y__5091__auto__ = 1.0;
return ((x__5090__auto__ < y__5091__auto__) ? x__5090__auto__ : y__5091__auto__);
})();
var k = (function (){var G__8751 = curve;
var G__8751__$1 = (((G__8751 instanceof cljs.core.Keyword))?G__8751.fqn:null);
switch (G__8751__$1) {
case "exp":
return (u * u);

break;
case "sine":
return (0.5 * (1.0 - Math.cos((Math.PI * u))));

break;
default:
return u;

}
})();
return (start + ((end - start) * k));
});
repulse.audio.scheduler_state = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentHashMap.fromArrays([new cljs.core.Keyword(null,"cycle","cycle",710365284),new cljs.core.Keyword(null,"muted","muted",1275109029),new cljs.core.Keyword(null,"lookahead","lookahead",-400102393),new cljs.core.Keyword(null,"on-fx-event","on-fx-event",1435706023),new cljs.core.Keyword(null,"tracks","tracks",-326768501),new cljs.core.Keyword(null,"cycle-dur","cycle-dur",1241813230),new cljs.core.Keyword(null,"interval-id","interval-id",79285360),new cljs.core.Keyword(null,"playing?","playing?",-1884542863),new cljs.core.Keyword(null,"on-event","on-event",1340574774),new cljs.core.Keyword(null,"tween-state","tween-state",-1960595658),new cljs.core.Keyword(null,"on-beat","on-beat",1078535864)],[(0),cljs.core.PersistentHashSet.EMPTY,0.2,null,cljs.core.PersistentArrayMap.EMPTY,2.0,null,false,null,cljs.core.PersistentArrayMap.EMPTY,null]));
/**
 * Clamp BPM to the safe scheduler range [20, 640]. Invalid, NaN, or
 * non-positive input falls back to 120.
 */
repulse.audio.coerce_bpm = (function repulse$audio$coerce_bpm(x){
var n = ((typeof x === 'number')?x:NaN);
if(cljs.core.truth_((function (){var or__5002__auto__ = isNaN(n);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (!((n > (0))));
}
})())){
return (120);
} else {
if((n < (20))){
return (20);
} else {
if((n > (640))){
return (640);
} else {
return n;

}
}
}
});
/**
 * Set the tempo in BPM. One cycle = one bar (4 beats).
 */
repulse.audio.set_bpm_BANG_ = (function repulse$audio$set_bpm_BANG_(bpm){
return cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.audio.scheduler_state,cljs.core.assoc,new cljs.core.Keyword(null,"cycle-dur","cycle-dur",1241813230),(240.0 / repulse.audio.coerce_bpm(bpm)));
});
repulse.audio.get_bpm = (function repulse$audio$get_bpm(){
return (240.0 / new cljs.core.Keyword(null,"cycle-dur","cycle-dur",1241813230).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.audio.scheduler_state)));
});
repulse.audio.schedule_cycle_BANG_ = (function repulse$audio$schedule_cycle_BANG_(ac,state,cycle){
var map__8759 = state;
var map__8759__$1 = cljs.core.__destructure_map(map__8759);
var tracks = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8759__$1,new cljs.core.Keyword(null,"tracks","tracks",-326768501));
var muted = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8759__$1,new cljs.core.Keyword(null,"muted","muted",1275109029));
var cycle_dur = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8759__$1,new cljs.core.Keyword(null,"cycle-dur","cycle-dur",1241813230));
var on_beat = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8759__$1,new cljs.core.Keyword(null,"on-beat","on-beat",1078535864));
var on_event = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8759__$1,new cljs.core.Keyword(null,"on-event","on-event",1340574774));
var on_fx_event = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8759__$1,new cljs.core.Keyword(null,"on-fx-event","on-fx-event",1435706023));
var sp = new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"start","start",-355208981),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [cycle,(1)], null),new cljs.core.Keyword(null,"end","end",-268185958),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(cycle + (1)),(1)], null)], null);
var seq__8760 = cljs.core.seq(tracks);
var chunk__8761 = null;
var count__8762 = (0);
var i__8763 = (0);
while(true){
if((i__8763 < count__8762)){
var vec__8843 = chunk__8761.cljs$core$IIndexed$_nth$arity$2(null,i__8763);
var track_name = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8843,(0),null);
var pattern = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8843,(1),null);
if(cljs.core.truth_((function (){var and__5000__auto__ = pattern;
if(cljs.core.truth_(and__5000__auto__)){
return (!(cljs.core.contains_QMARK_(muted,track_name)));
} else {
return and__5000__auto__;
}
})())){
var evs_9197 = repulse.core.query(pattern,sp);
var seq__8846_9198 = cljs.core.seq(evs_9197);
var chunk__8847_9199 = null;
var count__8848_9200 = (0);
var i__8849_9201 = (0);
while(true){
if((i__8849_9201 < count__8848_9200)){
var ev_9202 = chunk__8847_9199.cljs$core$IIndexed$_nth$arity$2(null,i__8849_9201);
var part_start_9203 = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev_9202)));
var part_end_9204 = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev_9202)));
var cycle_audio_start_9205 = (cycle * cycle_dur);
var event_offset_9206 = ((part_start_9203 - cycle) * cycle_dur);
var t_9207 = (cycle_audio_start_9205 + event_offset_9206);
var dur_9208 = ((part_end_9204 - part_start_9203) * cycle_dur);
if((t_9207 > ac.currentTime)){
var tween_st_9211 = cljs.core.get_in.cljs$core$IFn$_invoke$arity$3(state,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"tween-state","tween-state",-1960595658),track_name], null),cljs.core.PersistentArrayMap.EMPTY);
var raw_9212 = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_9202);
var value_9213 = ((((cljs.core.map_QMARK_(raw_9212)) && (cljs.core.seq(tween_st_9211))))?cljs.core.reduce_kv(((function (seq__8846_9198,chunk__8847_9199,count__8848_9200,i__8849_9201,seq__8760,chunk__8761,count__8762,i__8763,tween_st_9211,raw_9212,part_start_9203,part_end_9204,cycle_audio_start_9205,event_offset_9206,t_9207,dur_9208,ev_9202,evs_9197,vec__8843,track_name,pattern,map__8759,map__8759__$1,tracks,muted,cycle_dur,on_beat,on_event,on_fx_event,sp){
return (function (m,k,ts){
if(repulse.audio.tween_QMARK_(cljs.core.get.cljs$core$IFn$_invoke$arity$2(m,k))){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(m,k,repulse.audio.interp_tween(ts,t_9207,cycle_dur));
} else {
return m;
}
});})(seq__8846_9198,chunk__8847_9199,count__8848_9200,i__8849_9201,seq__8760,chunk__8761,count__8762,i__8763,tween_st_9211,raw_9212,part_start_9203,part_end_9204,cycle_audio_start_9205,event_offset_9206,t_9207,dur_9208,ev_9202,evs_9197,vec__8843,track_name,pattern,map__8759,map__8759__$1,tracks,muted,cycle_dur,on_beat,on_event,on_fx_event,sp))
,raw_9212,tween_st_9211):raw_9212);
repulse.audio.play_event.cljs$core$IFn$_invoke$arity$4(ac,t_9207,value_9213,track_name);

var temp__5804__auto___9215 = new cljs.core.Keyword(null,"midi-ch","midi-ch",-479086655).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_9202));
if(cljs.core.truth_(temp__5804__auto___9215)){
var midi_ch_9216 = temp__5804__auto___9215;
var value_9217__$1 = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_9202);
var hz_9218 = (cljs.core.truth_(new cljs.core.Keyword(null,"freq","freq",-1855845278).cljs$core$IFn$_invoke$arity$1(value_9217__$1))?new cljs.core.Keyword(null,"freq","freq",-1855845278).cljs$core$IFn$_invoke$arity$1(value_9217__$1):(cljs.core.truth_((function (){var and__5000__auto__ = new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9217__$1);
if(cljs.core.truth_(and__5000__auto__)){
return repulse.theory.note_keyword_QMARK_(new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9217__$1));
} else {
return and__5000__auto__;
}
})())?repulse.theory.note__GT_hz(new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9217__$1)):((typeof new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9217__$1) === 'number')?new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9217__$1):((repulse.theory.note_keyword_QMARK_(value_9217__$1))?repulse.theory.note__GT_hz(value_9217__$1):((typeof value_9217__$1 === 'number')?value_9217__$1:null
)))));
var note_num_9219 = (cljs.core.truth_(hz_9218)?repulse.midi.hz__GT_midi(hz_9218):null);
var amp_v_9220 = new cljs.core.Keyword(null,"amp","amp",271690571).cljs$core$IFn$_invoke$arity$2(value_9217__$1,1.0);
var velocity_9221 = (function (){var x__5090__auto__ = (127);
var y__5091__auto__ = (function (){var x__5087__auto__ = (0);
var y__5088__auto__ = Math.round((amp_v_9220 * (127)));
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
return ((x__5090__auto__ < y__5091__auto__) ? x__5090__auto__ : y__5091__auto__);
})();
var ac_now_9222 = ac.currentTime;
var perf_now_9223 = performance.now();
var ts_on_9224 = (perf_now_9223 + ((t_9207 - ac_now_9222) * (1000)));
var ts_off_9225 = (ts_on_9224 + (dur_9208 * (1000)));
if(cljs.core.truth_(note_num_9219)){
repulse.midi.send_note_on_BANG_(midi_ch_9216,note_num_9219,velocity_9221,ts_on_9224);

repulse.midi.send_note_off_BANG_(midi_ch_9216,note_num_9219,ts_off_9225);
} else {
}
} else {
}

if(cljs.core.truth_(on_fx_event)){
var G__8859_9230 = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_9202);
var G__8860_9231 = t_9207;
(on_fx_event.cljs$core$IFn$_invoke$arity$2 ? on_fx_event.cljs$core$IFn$_invoke$arity$2(G__8859_9230,G__8860_9231) : on_fx_event.call(null,G__8859_9230,G__8860_9231));
} else {
}

if(cljs.core.truth_((function (){var and__5000__auto__ = on_event;
if(cljs.core.truth_(and__5000__auto__)){
return new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(ev_9202);
} else {
return and__5000__auto__;
}
})())){
var delay_ms_9235 = (function (){var x__5087__auto__ = (0);
var y__5088__auto__ = ((1000) * (t_9207 - ac.currentTime));
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
setTimeout(((function (seq__8846_9198,chunk__8847_9199,count__8848_9200,i__8849_9201,seq__8760,chunk__8761,count__8762,i__8763,delay_ms_9235,tween_st_9211,raw_9212,value_9213,part_start_9203,part_end_9204,cycle_audio_start_9205,event_offset_9206,t_9207,dur_9208,ev_9202,evs_9197,vec__8843,track_name,pattern,map__8759,map__8759__$1,tracks,muted,cycle_dur,on_beat,on_event,on_fx_event,sp){
return (function (){
var G__8865 = new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(ev_9202);
return (on_event.cljs$core$IFn$_invoke$arity$1 ? on_event.cljs$core$IFn$_invoke$arity$1(G__8865) : on_event.call(null,G__8865));
});})(seq__8846_9198,chunk__8847_9199,count__8848_9200,i__8849_9201,seq__8760,chunk__8761,count__8762,i__8763,delay_ms_9235,tween_st_9211,raw_9212,value_9213,part_start_9203,part_end_9204,cycle_audio_start_9205,event_offset_9206,t_9207,dur_9208,ev_9202,evs_9197,vec__8843,track_name,pattern,map__8759,map__8759__$1,tracks,muted,cycle_dur,on_beat,on_event,on_fx_event,sp))
,delay_ms_9235);
} else {
}

if(cljs.core.truth_(on_beat)){
var delay_ms_9237 = ((1000) * (t_9207 - ac.currentTime));
setTimeout(on_beat,delay_ms_9237);
} else {
}
} else {
}


var G__9238 = seq__8846_9198;
var G__9239 = chunk__8847_9199;
var G__9240 = count__8848_9200;
var G__9241 = (i__8849_9201 + (1));
seq__8846_9198 = G__9238;
chunk__8847_9199 = G__9239;
count__8848_9200 = G__9240;
i__8849_9201 = G__9241;
continue;
} else {
var temp__5804__auto___9242 = cljs.core.seq(seq__8846_9198);
if(temp__5804__auto___9242){
var seq__8846_9243__$1 = temp__5804__auto___9242;
if(cljs.core.chunked_seq_QMARK_(seq__8846_9243__$1)){
var c__5525__auto___9244 = cljs.core.chunk_first(seq__8846_9243__$1);
var G__9246 = cljs.core.chunk_rest(seq__8846_9243__$1);
var G__9247 = c__5525__auto___9244;
var G__9248 = cljs.core.count(c__5525__auto___9244);
var G__9249 = (0);
seq__8846_9198 = G__9246;
chunk__8847_9199 = G__9247;
count__8848_9200 = G__9248;
i__8849_9201 = G__9249;
continue;
} else {
var ev_9250 = cljs.core.first(seq__8846_9243__$1);
var part_start_9251 = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev_9250)));
var part_end_9252 = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev_9250)));
var cycle_audio_start_9253 = (cycle * cycle_dur);
var event_offset_9254 = ((part_start_9251 - cycle) * cycle_dur);
var t_9255 = (cycle_audio_start_9253 + event_offset_9254);
var dur_9256 = ((part_end_9252 - part_start_9251) * cycle_dur);
if((t_9255 > ac.currentTime)){
var tween_st_9260 = cljs.core.get_in.cljs$core$IFn$_invoke$arity$3(state,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"tween-state","tween-state",-1960595658),track_name], null),cljs.core.PersistentArrayMap.EMPTY);
var raw_9261 = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_9250);
var value_9262 = ((((cljs.core.map_QMARK_(raw_9261)) && (cljs.core.seq(tween_st_9260))))?cljs.core.reduce_kv(((function (seq__8846_9198,chunk__8847_9199,count__8848_9200,i__8849_9201,seq__8760,chunk__8761,count__8762,i__8763,tween_st_9260,raw_9261,part_start_9251,part_end_9252,cycle_audio_start_9253,event_offset_9254,t_9255,dur_9256,ev_9250,seq__8846_9243__$1,temp__5804__auto___9242,evs_9197,vec__8843,track_name,pattern,map__8759,map__8759__$1,tracks,muted,cycle_dur,on_beat,on_event,on_fx_event,sp){
return (function (m,k,ts){
if(repulse.audio.tween_QMARK_(cljs.core.get.cljs$core$IFn$_invoke$arity$2(m,k))){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(m,k,repulse.audio.interp_tween(ts,t_9255,cycle_dur));
} else {
return m;
}
});})(seq__8846_9198,chunk__8847_9199,count__8848_9200,i__8849_9201,seq__8760,chunk__8761,count__8762,i__8763,tween_st_9260,raw_9261,part_start_9251,part_end_9252,cycle_audio_start_9253,event_offset_9254,t_9255,dur_9256,ev_9250,seq__8846_9243__$1,temp__5804__auto___9242,evs_9197,vec__8843,track_name,pattern,map__8759,map__8759__$1,tracks,muted,cycle_dur,on_beat,on_event,on_fx_event,sp))
,raw_9261,tween_st_9260):raw_9261);
repulse.audio.play_event.cljs$core$IFn$_invoke$arity$4(ac,t_9255,value_9262,track_name);

var temp__5804__auto___9267__$1 = new cljs.core.Keyword(null,"midi-ch","midi-ch",-479086655).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_9250));
if(cljs.core.truth_(temp__5804__auto___9267__$1)){
var midi_ch_9268 = temp__5804__auto___9267__$1;
var value_9270__$1 = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_9250);
var hz_9271 = (cljs.core.truth_(new cljs.core.Keyword(null,"freq","freq",-1855845278).cljs$core$IFn$_invoke$arity$1(value_9270__$1))?new cljs.core.Keyword(null,"freq","freq",-1855845278).cljs$core$IFn$_invoke$arity$1(value_9270__$1):(cljs.core.truth_((function (){var and__5000__auto__ = new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9270__$1);
if(cljs.core.truth_(and__5000__auto__)){
return repulse.theory.note_keyword_QMARK_(new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9270__$1));
} else {
return and__5000__auto__;
}
})())?repulse.theory.note__GT_hz(new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9270__$1)):((typeof new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9270__$1) === 'number')?new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9270__$1):((repulse.theory.note_keyword_QMARK_(value_9270__$1))?repulse.theory.note__GT_hz(value_9270__$1):((typeof value_9270__$1 === 'number')?value_9270__$1:null
)))));
var note_num_9272 = (cljs.core.truth_(hz_9271)?repulse.midi.hz__GT_midi(hz_9271):null);
var amp_v_9273 = new cljs.core.Keyword(null,"amp","amp",271690571).cljs$core$IFn$_invoke$arity$2(value_9270__$1,1.0);
var velocity_9274 = (function (){var x__5090__auto__ = (127);
var y__5091__auto__ = (function (){var x__5087__auto__ = (0);
var y__5088__auto__ = Math.round((amp_v_9273 * (127)));
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
return ((x__5090__auto__ < y__5091__auto__) ? x__5090__auto__ : y__5091__auto__);
})();
var ac_now_9275 = ac.currentTime;
var perf_now_9276 = performance.now();
var ts_on_9277 = (perf_now_9276 + ((t_9255 - ac_now_9275) * (1000)));
var ts_off_9278 = (ts_on_9277 + (dur_9256 * (1000)));
if(cljs.core.truth_(note_num_9272)){
repulse.midi.send_note_on_BANG_(midi_ch_9268,note_num_9272,velocity_9274,ts_on_9277);

repulse.midi.send_note_off_BANG_(midi_ch_9268,note_num_9272,ts_off_9278);
} else {
}
} else {
}

if(cljs.core.truth_(on_fx_event)){
var G__8866_9286 = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_9250);
var G__8867_9287 = t_9255;
(on_fx_event.cljs$core$IFn$_invoke$arity$2 ? on_fx_event.cljs$core$IFn$_invoke$arity$2(G__8866_9286,G__8867_9287) : on_fx_event.call(null,G__8866_9286,G__8867_9287));
} else {
}

if(cljs.core.truth_((function (){var and__5000__auto__ = on_event;
if(cljs.core.truth_(and__5000__auto__)){
return new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(ev_9250);
} else {
return and__5000__auto__;
}
})())){
var delay_ms_9292 = (function (){var x__5087__auto__ = (0);
var y__5088__auto__ = ((1000) * (t_9255 - ac.currentTime));
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
setTimeout(((function (seq__8846_9198,chunk__8847_9199,count__8848_9200,i__8849_9201,seq__8760,chunk__8761,count__8762,i__8763,delay_ms_9292,tween_st_9260,raw_9261,value_9262,part_start_9251,part_end_9252,cycle_audio_start_9253,event_offset_9254,t_9255,dur_9256,ev_9250,seq__8846_9243__$1,temp__5804__auto___9242,evs_9197,vec__8843,track_name,pattern,map__8759,map__8759__$1,tracks,muted,cycle_dur,on_beat,on_event,on_fx_event,sp){
return (function (){
var G__8868 = new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(ev_9250);
return (on_event.cljs$core$IFn$_invoke$arity$1 ? on_event.cljs$core$IFn$_invoke$arity$1(G__8868) : on_event.call(null,G__8868));
});})(seq__8846_9198,chunk__8847_9199,count__8848_9200,i__8849_9201,seq__8760,chunk__8761,count__8762,i__8763,delay_ms_9292,tween_st_9260,raw_9261,value_9262,part_start_9251,part_end_9252,cycle_audio_start_9253,event_offset_9254,t_9255,dur_9256,ev_9250,seq__8846_9243__$1,temp__5804__auto___9242,evs_9197,vec__8843,track_name,pattern,map__8759,map__8759__$1,tracks,muted,cycle_dur,on_beat,on_event,on_fx_event,sp))
,delay_ms_9292);
} else {
}

if(cljs.core.truth_(on_beat)){
var delay_ms_9295 = ((1000) * (t_9255 - ac.currentTime));
setTimeout(on_beat,delay_ms_9295);
} else {
}
} else {
}


var G__9296 = cljs.core.next(seq__8846_9243__$1);
var G__9297 = null;
var G__9298 = (0);
var G__9299 = (0);
seq__8846_9198 = G__9296;
chunk__8847_9199 = G__9297;
count__8848_9200 = G__9298;
i__8849_9201 = G__9299;
continue;
}
} else {
}
}
break;
}
} else {
}


var G__9300 = seq__8760;
var G__9301 = chunk__8761;
var G__9302 = count__8762;
var G__9303 = (i__8763 + (1));
seq__8760 = G__9300;
chunk__8761 = G__9301;
count__8762 = G__9302;
i__8763 = G__9303;
continue;
} else {
var temp__5804__auto__ = cljs.core.seq(seq__8760);
if(temp__5804__auto__){
var seq__8760__$1 = temp__5804__auto__;
if(cljs.core.chunked_seq_QMARK_(seq__8760__$1)){
var c__5525__auto__ = cljs.core.chunk_first(seq__8760__$1);
var G__9308 = cljs.core.chunk_rest(seq__8760__$1);
var G__9309 = c__5525__auto__;
var G__9310 = cljs.core.count(c__5525__auto__);
var G__9311 = (0);
seq__8760 = G__9308;
chunk__8761 = G__9309;
count__8762 = G__9310;
i__8763 = G__9311;
continue;
} else {
var vec__8869 = cljs.core.first(seq__8760__$1);
var track_name = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8869,(0),null);
var pattern = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8869,(1),null);
if(cljs.core.truth_((function (){var and__5000__auto__ = pattern;
if(cljs.core.truth_(and__5000__auto__)){
return (!(cljs.core.contains_QMARK_(muted,track_name)));
} else {
return and__5000__auto__;
}
})())){
var evs_9318 = repulse.core.query(pattern,sp);
var seq__8875_9319 = cljs.core.seq(evs_9318);
var chunk__8876_9320 = null;
var count__8877_9321 = (0);
var i__8878_9322 = (0);
while(true){
if((i__8878_9322 < count__8877_9321)){
var ev_9329 = chunk__8876_9320.cljs$core$IIndexed$_nth$arity$2(null,i__8878_9322);
var part_start_9331 = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev_9329)));
var part_end_9332 = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev_9329)));
var cycle_audio_start_9333 = (cycle * cycle_dur);
var event_offset_9334 = ((part_start_9331 - cycle) * cycle_dur);
var t_9335 = (cycle_audio_start_9333 + event_offset_9334);
var dur_9336 = ((part_end_9332 - part_start_9331) * cycle_dur);
if((t_9335 > ac.currentTime)){
var tween_st_9343 = cljs.core.get_in.cljs$core$IFn$_invoke$arity$3(state,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"tween-state","tween-state",-1960595658),track_name], null),cljs.core.PersistentArrayMap.EMPTY);
var raw_9344 = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_9329);
var value_9345 = ((((cljs.core.map_QMARK_(raw_9344)) && (cljs.core.seq(tween_st_9343))))?cljs.core.reduce_kv(((function (seq__8875_9319,chunk__8876_9320,count__8877_9321,i__8878_9322,seq__8760,chunk__8761,count__8762,i__8763,tween_st_9343,raw_9344,part_start_9331,part_end_9332,cycle_audio_start_9333,event_offset_9334,t_9335,dur_9336,ev_9329,evs_9318,vec__8869,track_name,pattern,seq__8760__$1,temp__5804__auto__,map__8759,map__8759__$1,tracks,muted,cycle_dur,on_beat,on_event,on_fx_event,sp){
return (function (m,k,ts){
if(repulse.audio.tween_QMARK_(cljs.core.get.cljs$core$IFn$_invoke$arity$2(m,k))){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(m,k,repulse.audio.interp_tween(ts,t_9335,cycle_dur));
} else {
return m;
}
});})(seq__8875_9319,chunk__8876_9320,count__8877_9321,i__8878_9322,seq__8760,chunk__8761,count__8762,i__8763,tween_st_9343,raw_9344,part_start_9331,part_end_9332,cycle_audio_start_9333,event_offset_9334,t_9335,dur_9336,ev_9329,evs_9318,vec__8869,track_name,pattern,seq__8760__$1,temp__5804__auto__,map__8759,map__8759__$1,tracks,muted,cycle_dur,on_beat,on_event,on_fx_event,sp))
,raw_9344,tween_st_9343):raw_9344);
repulse.audio.play_event.cljs$core$IFn$_invoke$arity$4(ac,t_9335,value_9345,track_name);

var temp__5804__auto___9353__$1 = new cljs.core.Keyword(null,"midi-ch","midi-ch",-479086655).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_9329));
if(cljs.core.truth_(temp__5804__auto___9353__$1)){
var midi_ch_9356 = temp__5804__auto___9353__$1;
var value_9357__$1 = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_9329);
var hz_9358 = (cljs.core.truth_(new cljs.core.Keyword(null,"freq","freq",-1855845278).cljs$core$IFn$_invoke$arity$1(value_9357__$1))?new cljs.core.Keyword(null,"freq","freq",-1855845278).cljs$core$IFn$_invoke$arity$1(value_9357__$1):(cljs.core.truth_((function (){var and__5000__auto__ = new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9357__$1);
if(cljs.core.truth_(and__5000__auto__)){
return repulse.theory.note_keyword_QMARK_(new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9357__$1));
} else {
return and__5000__auto__;
}
})())?repulse.theory.note__GT_hz(new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9357__$1)):((typeof new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9357__$1) === 'number')?new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9357__$1):((repulse.theory.note_keyword_QMARK_(value_9357__$1))?repulse.theory.note__GT_hz(value_9357__$1):((typeof value_9357__$1 === 'number')?value_9357__$1:null
)))));
var note_num_9359 = (cljs.core.truth_(hz_9358)?repulse.midi.hz__GT_midi(hz_9358):null);
var amp_v_9360 = new cljs.core.Keyword(null,"amp","amp",271690571).cljs$core$IFn$_invoke$arity$2(value_9357__$1,1.0);
var velocity_9361 = (function (){var x__5090__auto__ = (127);
var y__5091__auto__ = (function (){var x__5087__auto__ = (0);
var y__5088__auto__ = Math.round((amp_v_9360 * (127)));
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
return ((x__5090__auto__ < y__5091__auto__) ? x__5090__auto__ : y__5091__auto__);
})();
var ac_now_9362 = ac.currentTime;
var perf_now_9363 = performance.now();
var ts_on_9364 = (perf_now_9363 + ((t_9335 - ac_now_9362) * (1000)));
var ts_off_9365 = (ts_on_9364 + (dur_9336 * (1000)));
if(cljs.core.truth_(note_num_9359)){
repulse.midi.send_note_on_BANG_(midi_ch_9356,note_num_9359,velocity_9361,ts_on_9364);

repulse.midi.send_note_off_BANG_(midi_ch_9356,note_num_9359,ts_off_9365);
} else {
}
} else {
}

if(cljs.core.truth_(on_fx_event)){
var G__8902_9366 = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_9329);
var G__8903_9367 = t_9335;
(on_fx_event.cljs$core$IFn$_invoke$arity$2 ? on_fx_event.cljs$core$IFn$_invoke$arity$2(G__8902_9366,G__8903_9367) : on_fx_event.call(null,G__8902_9366,G__8903_9367));
} else {
}

if(cljs.core.truth_((function (){var and__5000__auto__ = on_event;
if(cljs.core.truth_(and__5000__auto__)){
return new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(ev_9329);
} else {
return and__5000__auto__;
}
})())){
var delay_ms_9369 = (function (){var x__5087__auto__ = (0);
var y__5088__auto__ = ((1000) * (t_9335 - ac.currentTime));
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
setTimeout(((function (seq__8875_9319,chunk__8876_9320,count__8877_9321,i__8878_9322,seq__8760,chunk__8761,count__8762,i__8763,delay_ms_9369,tween_st_9343,raw_9344,value_9345,part_start_9331,part_end_9332,cycle_audio_start_9333,event_offset_9334,t_9335,dur_9336,ev_9329,evs_9318,vec__8869,track_name,pattern,seq__8760__$1,temp__5804__auto__,map__8759,map__8759__$1,tracks,muted,cycle_dur,on_beat,on_event,on_fx_event,sp){
return (function (){
var G__8904 = new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(ev_9329);
return (on_event.cljs$core$IFn$_invoke$arity$1 ? on_event.cljs$core$IFn$_invoke$arity$1(G__8904) : on_event.call(null,G__8904));
});})(seq__8875_9319,chunk__8876_9320,count__8877_9321,i__8878_9322,seq__8760,chunk__8761,count__8762,i__8763,delay_ms_9369,tween_st_9343,raw_9344,value_9345,part_start_9331,part_end_9332,cycle_audio_start_9333,event_offset_9334,t_9335,dur_9336,ev_9329,evs_9318,vec__8869,track_name,pattern,seq__8760__$1,temp__5804__auto__,map__8759,map__8759__$1,tracks,muted,cycle_dur,on_beat,on_event,on_fx_event,sp))
,delay_ms_9369);
} else {
}

if(cljs.core.truth_(on_beat)){
var delay_ms_9373 = ((1000) * (t_9335 - ac.currentTime));
setTimeout(on_beat,delay_ms_9373);
} else {
}
} else {
}


var G__9374 = seq__8875_9319;
var G__9375 = chunk__8876_9320;
var G__9376 = count__8877_9321;
var G__9377 = (i__8878_9322 + (1));
seq__8875_9319 = G__9374;
chunk__8876_9320 = G__9375;
count__8877_9321 = G__9376;
i__8878_9322 = G__9377;
continue;
} else {
var temp__5804__auto___9378__$1 = cljs.core.seq(seq__8875_9319);
if(temp__5804__auto___9378__$1){
var seq__8875_9379__$1 = temp__5804__auto___9378__$1;
if(cljs.core.chunked_seq_QMARK_(seq__8875_9379__$1)){
var c__5525__auto___9380 = cljs.core.chunk_first(seq__8875_9379__$1);
var G__9381 = cljs.core.chunk_rest(seq__8875_9379__$1);
var G__9382 = c__5525__auto___9380;
var G__9383 = cljs.core.count(c__5525__auto___9380);
var G__9384 = (0);
seq__8875_9319 = G__9381;
chunk__8876_9320 = G__9382;
count__8877_9321 = G__9383;
i__8878_9322 = G__9384;
continue;
} else {
var ev_9386 = cljs.core.first(seq__8875_9379__$1);
var part_start_9387 = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev_9386)));
var part_end_9388 = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev_9386)));
var cycle_audio_start_9389 = (cycle * cycle_dur);
var event_offset_9390 = ((part_start_9387 - cycle) * cycle_dur);
var t_9391 = (cycle_audio_start_9389 + event_offset_9390);
var dur_9392 = ((part_end_9388 - part_start_9387) * cycle_dur);
if((t_9391 > ac.currentTime)){
var tween_st_9396 = cljs.core.get_in.cljs$core$IFn$_invoke$arity$3(state,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"tween-state","tween-state",-1960595658),track_name], null),cljs.core.PersistentArrayMap.EMPTY);
var raw_9397 = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_9386);
var value_9398 = ((((cljs.core.map_QMARK_(raw_9397)) && (cljs.core.seq(tween_st_9396))))?cljs.core.reduce_kv(((function (seq__8875_9319,chunk__8876_9320,count__8877_9321,i__8878_9322,seq__8760,chunk__8761,count__8762,i__8763,tween_st_9396,raw_9397,part_start_9387,part_end_9388,cycle_audio_start_9389,event_offset_9390,t_9391,dur_9392,ev_9386,seq__8875_9379__$1,temp__5804__auto___9378__$1,evs_9318,vec__8869,track_name,pattern,seq__8760__$1,temp__5804__auto__,map__8759,map__8759__$1,tracks,muted,cycle_dur,on_beat,on_event,on_fx_event,sp){
return (function (m,k,ts){
if(repulse.audio.tween_QMARK_(cljs.core.get.cljs$core$IFn$_invoke$arity$2(m,k))){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(m,k,repulse.audio.interp_tween(ts,t_9391,cycle_dur));
} else {
return m;
}
});})(seq__8875_9319,chunk__8876_9320,count__8877_9321,i__8878_9322,seq__8760,chunk__8761,count__8762,i__8763,tween_st_9396,raw_9397,part_start_9387,part_end_9388,cycle_audio_start_9389,event_offset_9390,t_9391,dur_9392,ev_9386,seq__8875_9379__$1,temp__5804__auto___9378__$1,evs_9318,vec__8869,track_name,pattern,seq__8760__$1,temp__5804__auto__,map__8759,map__8759__$1,tracks,muted,cycle_dur,on_beat,on_event,on_fx_event,sp))
,raw_9397,tween_st_9396):raw_9397);
repulse.audio.play_event.cljs$core$IFn$_invoke$arity$4(ac,t_9391,value_9398,track_name);

var temp__5804__auto___9403__$2 = new cljs.core.Keyword(null,"midi-ch","midi-ch",-479086655).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_9386));
if(cljs.core.truth_(temp__5804__auto___9403__$2)){
var midi_ch_9404 = temp__5804__auto___9403__$2;
var value_9405__$1 = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_9386);
var hz_9406 = (cljs.core.truth_(new cljs.core.Keyword(null,"freq","freq",-1855845278).cljs$core$IFn$_invoke$arity$1(value_9405__$1))?new cljs.core.Keyword(null,"freq","freq",-1855845278).cljs$core$IFn$_invoke$arity$1(value_9405__$1):(cljs.core.truth_((function (){var and__5000__auto__ = new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9405__$1);
if(cljs.core.truth_(and__5000__auto__)){
return repulse.theory.note_keyword_QMARK_(new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9405__$1));
} else {
return and__5000__auto__;
}
})())?repulse.theory.note__GT_hz(new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9405__$1)):((typeof new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9405__$1) === 'number')?new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value_9405__$1):((repulse.theory.note_keyword_QMARK_(value_9405__$1))?repulse.theory.note__GT_hz(value_9405__$1):((typeof value_9405__$1 === 'number')?value_9405__$1:null
)))));
var note_num_9407 = (cljs.core.truth_(hz_9406)?repulse.midi.hz__GT_midi(hz_9406):null);
var amp_v_9408 = new cljs.core.Keyword(null,"amp","amp",271690571).cljs$core$IFn$_invoke$arity$2(value_9405__$1,1.0);
var velocity_9409 = (function (){var x__5090__auto__ = (127);
var y__5091__auto__ = (function (){var x__5087__auto__ = (0);
var y__5088__auto__ = Math.round((amp_v_9408 * (127)));
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
return ((x__5090__auto__ < y__5091__auto__) ? x__5090__auto__ : y__5091__auto__);
})();
var ac_now_9410 = ac.currentTime;
var perf_now_9411 = performance.now();
var ts_on_9412 = (perf_now_9411 + ((t_9391 - ac_now_9410) * (1000)));
var ts_off_9413 = (ts_on_9412 + (dur_9392 * (1000)));
if(cljs.core.truth_(note_num_9407)){
repulse.midi.send_note_on_BANG_(midi_ch_9404,note_num_9407,velocity_9409,ts_on_9412);

repulse.midi.send_note_off_BANG_(midi_ch_9404,note_num_9407,ts_off_9413);
} else {
}
} else {
}

if(cljs.core.truth_(on_fx_event)){
var G__8908_9420 = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev_9386);
var G__8909_9421 = t_9391;
(on_fx_event.cljs$core$IFn$_invoke$arity$2 ? on_fx_event.cljs$core$IFn$_invoke$arity$2(G__8908_9420,G__8909_9421) : on_fx_event.call(null,G__8908_9420,G__8909_9421));
} else {
}

if(cljs.core.truth_((function (){var and__5000__auto__ = on_event;
if(cljs.core.truth_(and__5000__auto__)){
return new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(ev_9386);
} else {
return and__5000__auto__;
}
})())){
var delay_ms_9425 = (function (){var x__5087__auto__ = (0);
var y__5088__auto__ = ((1000) * (t_9391 - ac.currentTime));
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
setTimeout(((function (seq__8875_9319,chunk__8876_9320,count__8877_9321,i__8878_9322,seq__8760,chunk__8761,count__8762,i__8763,delay_ms_9425,tween_st_9396,raw_9397,value_9398,part_start_9387,part_end_9388,cycle_audio_start_9389,event_offset_9390,t_9391,dur_9392,ev_9386,seq__8875_9379__$1,temp__5804__auto___9378__$1,evs_9318,vec__8869,track_name,pattern,seq__8760__$1,temp__5804__auto__,map__8759,map__8759__$1,tracks,muted,cycle_dur,on_beat,on_event,on_fx_event,sp){
return (function (){
var G__8910 = new cljs.core.Keyword(null,"source","source",-433931539).cljs$core$IFn$_invoke$arity$1(ev_9386);
return (on_event.cljs$core$IFn$_invoke$arity$1 ? on_event.cljs$core$IFn$_invoke$arity$1(G__8910) : on_event.call(null,G__8910));
});})(seq__8875_9319,chunk__8876_9320,count__8877_9321,i__8878_9322,seq__8760,chunk__8761,count__8762,i__8763,delay_ms_9425,tween_st_9396,raw_9397,value_9398,part_start_9387,part_end_9388,cycle_audio_start_9389,event_offset_9390,t_9391,dur_9392,ev_9386,seq__8875_9379__$1,temp__5804__auto___9378__$1,evs_9318,vec__8869,track_name,pattern,seq__8760__$1,temp__5804__auto__,map__8759,map__8759__$1,tracks,muted,cycle_dur,on_beat,on_event,on_fx_event,sp))
,delay_ms_9425);
} else {
}

if(cljs.core.truth_(on_beat)){
var delay_ms_9436 = ((1000) * (t_9391 - ac.currentTime));
setTimeout(on_beat,delay_ms_9436);
} else {
}
} else {
}


var G__9437 = cljs.core.next(seq__8875_9379__$1);
var G__9438 = null;
var G__9439 = (0);
var G__9440 = (0);
seq__8875_9319 = G__9437;
chunk__8876_9320 = G__9438;
count__8877_9321 = G__9439;
i__8878_9322 = G__9440;
continue;
}
} else {
}
}
break;
}
} else {
}


var G__9442 = cljs.core.next(seq__8760__$1);
var G__9443 = null;
var G__9444 = (0);
var G__9445 = (0);
seq__8760 = G__9442;
chunk__8761 = G__9443;
count__8762 = G__9444;
i__8763 = G__9445;
continue;
}
} else {
return null;
}
}
break;
}
});
repulse.audio.tick_BANG_ = (function repulse$audio$tick_BANG_(){
var ac = repulse.audio.get_ctx();
var state = cljs.core.deref(repulse.audio.scheduler_state);
if(cljs.core.truth_(new cljs.core.Keyword(null,"playing?","playing?",-1884542863).cljs$core$IFn$_invoke$arity$1(state))){
var map__8913 = state;
var map__8913__$1 = cljs.core.__destructure_map(map__8913);
var cycle = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8913__$1,new cljs.core.Keyword(null,"cycle","cycle",710365284));
var cycle_dur = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8913__$1,new cljs.core.Keyword(null,"cycle-dur","cycle-dur",1241813230));
var lookahead = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8913__$1,new cljs.core.Keyword(null,"lookahead","lookahead",-400102393));
var now = ac.currentTime;
var cycle_start = (cycle * cycle_dur);
if(((cycle_start - now) < lookahead)){
repulse.audio.schedule_cycle_BANG_(ac,state,cycle);

return cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.audio.scheduler_state,cljs.core.update,new cljs.core.Keyword(null,"cycle","cycle",710365284),cljs.core.inc);
} else {
return null;
}
} else {
return null;
}
});
/**
 * Start the scheduler tick loop if not already running.
 */
repulse.audio.ensure_running_BANG_ = (function repulse$audio$ensure_running_BANG_(ac,on_beat_fn,on_event_fn){
if(cljs.core.truth_(new cljs.core.Keyword(null,"interval-id","interval-id",79285360).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.audio.scheduler_state)))){
return null;
} else {
var now_9448 = ac.currentTime;
var cycle_dur_9449 = new cljs.core.Keyword(null,"cycle-dur","cycle-dur",1241813230).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.audio.scheduler_state));
var start_cycle_9450 = (Math.floor((now_9448 / cycle_dur_9449)) | (0));
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$variadic(repulse.audio.scheduler_state,cljs.core.assoc,new cljs.core.Keyword(null,"playing?","playing?",-1884542863),true,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([new cljs.core.Keyword(null,"cycle","cycle",710365284),start_cycle_9450,new cljs.core.Keyword(null,"on-beat","on-beat",1078535864),on_beat_fn,new cljs.core.Keyword(null,"on-event","on-event",1340574774),on_event_fn], 0));

var id_9453 = setInterval(repulse.audio.tick_BANG_,(25));
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.audio.scheduler_state,cljs.core.assoc,new cljs.core.Keyword(null,"interval-id","interval-id",79285360),id_9453);

return repulse.audio.tick_BANG_();
}
});
repulse.audio.stop_BANG_ = (function repulse$audio$stop_BANG_(){
var temp__5804__auto___9455 = cljs.core.deref(repulse.audio.worklet_node);
if(cljs.core.truth_(temp__5804__auto___9455)){
var node_9456 = temp__5804__auto___9455;
node_9456.port.postMessage(({"type": "stop"}));
} else {
}

var temp__5804__auto___9458 = new cljs.core.Keyword(null,"interval-id","interval-id",79285360).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.audio.scheduler_state));
if(cljs.core.truth_(temp__5804__auto___9458)){
var id_9461 = temp__5804__auto___9458;
clearInterval(id_9461);
} else {
}

var seq__8914_9462 = cljs.core.seq(cljs.core.deref(repulse.audio.track_nodes));
var chunk__8915_9463 = null;
var count__8916_9464 = (0);
var i__8917_9465 = (0);
while(true){
if((i__8917_9465 < count__8916_9464)){
var vec__8956_9468 = chunk__8915_9463.cljs$core$IIndexed$_nth$arity$2(null,i__8917_9465);
var __9469 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8956_9468,(0),null);
var map__8959_9470 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8956_9468,(1),null);
var map__8959_9471__$1 = cljs.core.__destructure_map(map__8959_9470);
var gain_node_9472 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8959_9471__$1,new cljs.core.Keyword(null,"gain-node","gain-node",-1178526839));
var fx_chain_9473 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8959_9471__$1,new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234));
try{gain_node_9472.disconnect();
}catch (e8960){var __9474__$1 = e8960;
}
var seq__8961_9488 = cljs.core.seq(fx_chain_9473);
var chunk__8962_9489 = null;
var count__8963_9490 = (0);
var i__8964_9491 = (0);
while(true){
if((i__8964_9491 < count__8963_9490)){
var map__8974_9498 = chunk__8962_9489.cljs$core$IIndexed$_nth$arity$2(null,i__8964_9491);
var map__8974_9499__$1 = cljs.core.__destructure_map(map__8974_9498);
var plugin_9500 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8974_9499__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
try{plugin_9500.destroy();
}catch (e8975){var __9504__$1 = e8975;
}

var G__9505 = seq__8961_9488;
var G__9506 = chunk__8962_9489;
var G__9507 = count__8963_9490;
var G__9508 = (i__8964_9491 + (1));
seq__8961_9488 = G__9505;
chunk__8962_9489 = G__9506;
count__8963_9490 = G__9507;
i__8964_9491 = G__9508;
continue;
} else {
var temp__5804__auto___9512 = cljs.core.seq(seq__8961_9488);
if(temp__5804__auto___9512){
var seq__8961_9515__$1 = temp__5804__auto___9512;
if(cljs.core.chunked_seq_QMARK_(seq__8961_9515__$1)){
var c__5525__auto___9521 = cljs.core.chunk_first(seq__8961_9515__$1);
var G__9526 = cljs.core.chunk_rest(seq__8961_9515__$1);
var G__9527 = c__5525__auto___9521;
var G__9528 = cljs.core.count(c__5525__auto___9521);
var G__9529 = (0);
seq__8961_9488 = G__9526;
chunk__8962_9489 = G__9527;
count__8963_9490 = G__9528;
i__8964_9491 = G__9529;
continue;
} else {
var map__8976_9531 = cljs.core.first(seq__8961_9515__$1);
var map__8976_9533__$1 = cljs.core.__destructure_map(map__8976_9531);
var plugin_9534 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8976_9533__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
try{plugin_9534.destroy();
}catch (e8980){var __9537__$1 = e8980;
}

var G__9540 = cljs.core.next(seq__8961_9515__$1);
var G__9541 = null;
var G__9542 = (0);
var G__9543 = (0);
seq__8961_9488 = G__9540;
chunk__8962_9489 = G__9541;
count__8963_9490 = G__9542;
i__8964_9491 = G__9543;
continue;
}
} else {
}
}
break;
}


var G__9546 = seq__8914_9462;
var G__9547 = chunk__8915_9463;
var G__9548 = count__8916_9464;
var G__9549 = (i__8917_9465 + (1));
seq__8914_9462 = G__9546;
chunk__8915_9463 = G__9547;
count__8916_9464 = G__9548;
i__8917_9465 = G__9549;
continue;
} else {
var temp__5804__auto___9551 = cljs.core.seq(seq__8914_9462);
if(temp__5804__auto___9551){
var seq__8914_9552__$1 = temp__5804__auto___9551;
if(cljs.core.chunked_seq_QMARK_(seq__8914_9552__$1)){
var c__5525__auto___9553 = cljs.core.chunk_first(seq__8914_9552__$1);
var G__9555 = cljs.core.chunk_rest(seq__8914_9552__$1);
var G__9556 = c__5525__auto___9553;
var G__9557 = cljs.core.count(c__5525__auto___9553);
var G__9558 = (0);
seq__8914_9462 = G__9555;
chunk__8915_9463 = G__9556;
count__8916_9464 = G__9557;
i__8917_9465 = G__9558;
continue;
} else {
var vec__8981_9559 = cljs.core.first(seq__8914_9552__$1);
var __9560 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8981_9559,(0),null);
var map__8984_9561 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8981_9559,(1),null);
var map__8984_9562__$1 = cljs.core.__destructure_map(map__8984_9561);
var gain_node_9563 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8984_9562__$1,new cljs.core.Keyword(null,"gain-node","gain-node",-1178526839));
var fx_chain_9564 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8984_9562__$1,new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234));
try{gain_node_9563.disconnect();
}catch (e8985){var __9565__$1 = e8985;
}
var seq__8986_9566 = cljs.core.seq(fx_chain_9564);
var chunk__8987_9567 = null;
var count__8988_9568 = (0);
var i__8989_9569 = (0);
while(true){
if((i__8989_9569 < count__8988_9568)){
var map__8996_9571 = chunk__8987_9567.cljs$core$IIndexed$_nth$arity$2(null,i__8989_9569);
var map__8996_9572__$1 = cljs.core.__destructure_map(map__8996_9571);
var plugin_9573 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8996_9572__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
try{plugin_9573.destroy();
}catch (e8997){var __9576__$1 = e8997;
}

var G__9577 = seq__8986_9566;
var G__9578 = chunk__8987_9567;
var G__9579 = count__8988_9568;
var G__9580 = (i__8989_9569 + (1));
seq__8986_9566 = G__9577;
chunk__8987_9567 = G__9578;
count__8988_9568 = G__9579;
i__8989_9569 = G__9580;
continue;
} else {
var temp__5804__auto___9582__$1 = cljs.core.seq(seq__8986_9566);
if(temp__5804__auto___9582__$1){
var seq__8986_9583__$1 = temp__5804__auto___9582__$1;
if(cljs.core.chunked_seq_QMARK_(seq__8986_9583__$1)){
var c__5525__auto___9584 = cljs.core.chunk_first(seq__8986_9583__$1);
var G__9585 = cljs.core.chunk_rest(seq__8986_9583__$1);
var G__9586 = c__5525__auto___9584;
var G__9587 = cljs.core.count(c__5525__auto___9584);
var G__9588 = (0);
seq__8986_9566 = G__9585;
chunk__8987_9567 = G__9586;
count__8988_9568 = G__9587;
i__8989_9569 = G__9588;
continue;
} else {
var map__8999_9589 = cljs.core.first(seq__8986_9583__$1);
var map__8999_9590__$1 = cljs.core.__destructure_map(map__8999_9589);
var plugin_9591 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8999_9590__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
try{plugin_9591.destroy();
}catch (e9000){var __9593__$1 = e9000;
}

var G__9594 = cljs.core.next(seq__8986_9583__$1);
var G__9595 = null;
var G__9596 = (0);
var G__9597 = (0);
seq__8986_9566 = G__9594;
chunk__8987_9567 = G__9595;
count__8988_9568 = G__9596;
i__8989_9569 = G__9597;
continue;
}
} else {
}
}
break;
}


var G__9598 = cljs.core.next(seq__8914_9552__$1);
var G__9599 = null;
var G__9600 = (0);
var G__9601 = (0);
seq__8914_9462 = G__9598;
chunk__8915_9463 = G__9599;
count__8916_9464 = G__9600;
i__8917_9465 = G__9601;
continue;
}
} else {
}
}
break;
}

cljs.core.reset_BANG_(repulse.audio.track_nodes,cljs.core.PersistentArrayMap.EMPTY);

repulse.bus.cleanup_all_BANG_();

return cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$variadic(repulse.audio.scheduler_state,cljs.core.assoc,new cljs.core.Keyword(null,"playing?","playing?",-1884542863),false,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([new cljs.core.Keyword(null,"interval-id","interval-id",79285360),null,new cljs.core.Keyword(null,"tracks","tracks",-326768501),cljs.core.PersistentArrayMap.EMPTY,new cljs.core.Keyword(null,"muted","muted",1275109029),cljs.core.PersistentHashSet.EMPTY,new cljs.core.Keyword(null,"tween-state","tween-state",-1960595658),cljs.core.PersistentArrayMap.EMPTY], 0));
});
/**
 * Detect tween descriptors in the first cycle of pattern and record their start time.
 * schedule-cycle! uses this to interpolate values per event at schedule time,
 * which works uniformly for WASM synths, JS synths, and sample-based sounds.
 */
repulse.audio.arm_transitions_BANG_ = (function repulse$audio$arm_transitions_BANG_(track_name,pattern,ac){
var sp = new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"start","start",-355208981),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(0),(1)], null),new cljs.core.Keyword(null,"end","end",-268185958),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(1)], null)], null);
var evs = (function (){try{return repulse.core.query(pattern,sp);
}catch (e9007){var _ = e9007;
return cljs.core.PersistentVector.EMPTY;
}})();
var now = ac.currentTime;
var ts = cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.PersistentArrayMap.EMPTY,(function (){var iter__5480__auto__ = (function repulse$audio$arm_transitions_BANG__$_iter__9008(s__9009){
return (new cljs.core.LazySeq(null,(function (){
var s__9009__$1 = s__9009;
while(true){
var temp__5804__auto__ = cljs.core.seq(s__9009__$1);
if(temp__5804__auto__){
var xs__6360__auto__ = temp__5804__auto__;
var ev = cljs.core.first(xs__6360__auto__);
var v = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(ev);
if(cljs.core.map_QMARK_(v)){
var iterys__5476__auto__ = ((function (s__9009__$1,v,ev,xs__6360__auto__,temp__5804__auto__,sp,evs,now){
return (function repulse$audio$arm_transitions_BANG__$_iter__9008_$_iter__9010(s__9011){
return (new cljs.core.LazySeq(null,((function (s__9009__$1,v,ev,xs__6360__auto__,temp__5804__auto__,sp,evs,now){
return (function (){
var s__9011__$1 = s__9011;
while(true){
var temp__5804__auto____$1 = cljs.core.seq(s__9011__$1);
if(temp__5804__auto____$1){
var s__9011__$2 = temp__5804__auto____$1;
if(cljs.core.chunked_seq_QMARK_(s__9011__$2)){
var c__5478__auto__ = cljs.core.chunk_first(s__9011__$2);
var size__5479__auto__ = cljs.core.count(c__5478__auto__);
var b__9013 = cljs.core.chunk_buffer(size__5479__auto__);
if((function (){var i__9012 = (0);
while(true){
if((i__9012 < size__5479__auto__)){
var vec__9014 = cljs.core._nth(c__5478__auto__,i__9012);
var k = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9014,(0),null);
var pv = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9014,(1),null);
if(repulse.audio.tween_QMARK_(pv)){
cljs.core.chunk_append(b__9013,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [k,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"tween","tween",1743568853),pv,new cljs.core.Keyword(null,"start-time","start-time",814801386),now], null)], null));

var G__9614 = (i__9012 + (1));
i__9012 = G__9614;
continue;
} else {
var G__9615 = (i__9012 + (1));
i__9012 = G__9615;
continue;
}
} else {
return true;
}
break;
}
})()){
return cljs.core.chunk_cons(cljs.core.chunk(b__9013),repulse$audio$arm_transitions_BANG__$_iter__9008_$_iter__9010(cljs.core.chunk_rest(s__9011__$2)));
} else {
return cljs.core.chunk_cons(cljs.core.chunk(b__9013),null);
}
} else {
var vec__9017 = cljs.core.first(s__9011__$2);
var k = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9017,(0),null);
var pv = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9017,(1),null);
if(repulse.audio.tween_QMARK_(pv)){
return cljs.core.cons(new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [k,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"tween","tween",1743568853),pv,new cljs.core.Keyword(null,"start-time","start-time",814801386),now], null)], null),repulse$audio$arm_transitions_BANG__$_iter__9008_$_iter__9010(cljs.core.rest(s__9011__$2)));
} else {
var G__9630 = cljs.core.rest(s__9011__$2);
s__9011__$1 = G__9630;
continue;
}
}
} else {
return null;
}
break;
}
});})(s__9009__$1,v,ev,xs__6360__auto__,temp__5804__auto__,sp,evs,now))
,null,null));
});})(s__9009__$1,v,ev,xs__6360__auto__,temp__5804__auto__,sp,evs,now))
;
var fs__5477__auto__ = cljs.core.seq(iterys__5476__auto__(v));
if(fs__5477__auto__){
return cljs.core.concat.cljs$core$IFn$_invoke$arity$2(fs__5477__auto__,repulse$audio$arm_transitions_BANG__$_iter__9008(cljs.core.rest(s__9009__$1)));
} else {
var G__9636 = cljs.core.rest(s__9009__$1);
s__9009__$1 = G__9636;
continue;
}
} else {
var G__9639 = cljs.core.rest(s__9009__$1);
s__9009__$1 = G__9639;
continue;
}
} else {
return null;
}
break;
}
}),null,null));
});
return iter__5480__auto__(evs);
})());
return cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$variadic(repulse.audio.scheduler_state,cljs.core.update,new cljs.core.Keyword(null,"tween-state","tween-state",-1960595658),cljs.core.assoc,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([track_name,ts], 0));
});
/**
 * Add or replace a named track. Creates a per-track GainNode if needed.
 * Starts the scheduler if not already running.
 */
repulse.audio.play_track_BANG_ = (function repulse$audio$play_track_BANG_(track_name,pattern,on_beat_fn,on_event_fn){
var ac = repulse.audio.get_ctx();
ac.resume();

repulse.audio.ensure_track_node_BANG_(ac,track_name);

repulse.audio.arm_transitions_BANG_(track_name,pattern,ac);

cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$variadic(repulse.audio.scheduler_state,cljs.core.update,new cljs.core.Keyword(null,"tracks","tracks",-326768501),cljs.core.assoc,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([track_name,pattern], 0));

return repulse.audio.ensure_running_BANG_(ac,on_beat_fn,on_event_fn);
});
repulse.audio.mute_track_BANG_ = (function repulse$audio$mute_track_BANG_(track_name){
return cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$variadic(repulse.audio.scheduler_state,cljs.core.update,new cljs.core.Keyword(null,"muted","muted",1275109029),cljs.core.conj,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([track_name], 0));
});
repulse.audio.unmute_track_BANG_ = (function repulse$audio$unmute_track_BANG_(track_name){
return cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$variadic(repulse.audio.scheduler_state,cljs.core.update,new cljs.core.Keyword(null,"muted","muted",1275109029),cljs.core.disj,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([track_name], 0));
});
repulse.audio.solo_track_BANG_ = (function repulse$audio$solo_track_BANG_(track_name){
var others = cljs.core.remove.cljs$core$IFn$_invoke$arity$2(cljs.core.PersistentHashSet.createAsIfByAssoc([track_name]),cljs.core.keys(new cljs.core.Keyword(null,"tracks","tracks",-326768501).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.audio.scheduler_state))));
return cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.audio.scheduler_state,cljs.core.assoc,new cljs.core.Keyword(null,"muted","muted",1275109029),cljs.core.set(others));
});
repulse.audio.clear_track_BANG_ = (function repulse$audio$clear_track_BANG_(track_name){
var temp__5804__auto___9670 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.audio.track_nodes),track_name);
if(cljs.core.truth_(temp__5804__auto___9670)){
var tn_9671 = temp__5804__auto___9670;
try{new cljs.core.Keyword(null,"gain-node","gain-node",-1178526839).cljs$core$IFn$_invoke$arity$1(tn_9671).disconnect();
}catch (e9023){var __9672 = e9023;
}
var seq__9024_9673 = cljs.core.seq(new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234).cljs$core$IFn$_invoke$arity$1(tn_9671));
var chunk__9025_9674 = null;
var count__9026_9675 = (0);
var i__9027_9676 = (0);
while(true){
if((i__9027_9676 < count__9026_9675)){
var map__9032_9677 = chunk__9025_9674.cljs$core$IIndexed$_nth$arity$2(null,i__9027_9676);
var map__9032_9678__$1 = cljs.core.__destructure_map(map__9032_9677);
var plugin_9679 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9032_9678__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
try{plugin_9679.destroy();
}catch (e9033){var __9680 = e9033;
}

var G__9682 = seq__9024_9673;
var G__9683 = chunk__9025_9674;
var G__9684 = count__9026_9675;
var G__9685 = (i__9027_9676 + (1));
seq__9024_9673 = G__9682;
chunk__9025_9674 = G__9683;
count__9026_9675 = G__9684;
i__9027_9676 = G__9685;
continue;
} else {
var temp__5804__auto___9687__$1 = cljs.core.seq(seq__9024_9673);
if(temp__5804__auto___9687__$1){
var seq__9024_9689__$1 = temp__5804__auto___9687__$1;
if(cljs.core.chunked_seq_QMARK_(seq__9024_9689__$1)){
var c__5525__auto___9691 = cljs.core.chunk_first(seq__9024_9689__$1);
var G__9692 = cljs.core.chunk_rest(seq__9024_9689__$1);
var G__9693 = c__5525__auto___9691;
var G__9694 = cljs.core.count(c__5525__auto___9691);
var G__9695 = (0);
seq__9024_9673 = G__9692;
chunk__9025_9674 = G__9693;
count__9026_9675 = G__9694;
i__9027_9676 = G__9695;
continue;
} else {
var map__9036_9696 = cljs.core.first(seq__9024_9689__$1);
var map__9036_9697__$1 = cljs.core.__destructure_map(map__9036_9696);
var plugin_9698 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9036_9697__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
try{plugin_9698.destroy();
}catch (e9038){var __9699 = e9038;
}

var G__9700 = cljs.core.next(seq__9024_9689__$1);
var G__9701 = null;
var G__9702 = (0);
var G__9703 = (0);
seq__9024_9673 = G__9700;
chunk__9025_9674 = G__9701;
count__9026_9675 = G__9702;
i__9027_9676 = G__9703;
continue;
}
} else {
}
}
break;
}

cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(repulse.audio.track_nodes,cljs.core.dissoc,track_name);
} else {
}

cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$2(repulse.audio.scheduler_state,(function (s){
return cljs.core.update.cljs$core$IFn$_invoke$arity$4(cljs.core.update.cljs$core$IFn$_invoke$arity$4(s,new cljs.core.Keyword(null,"tracks","tracks",-326768501),cljs.core.dissoc,track_name),new cljs.core.Keyword(null,"muted","muted",1275109029),cljs.core.disj,track_name);
}));

if(cljs.core.empty_QMARK_(new cljs.core.Keyword(null,"tracks","tracks",-326768501).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.audio.scheduler_state)))){
return repulse.audio.stop_BANG_();
} else {
return null;
}
});
/**
 * Legacy single-pattern start: stops all tracks, starts fresh with one anonymous pattern.
 */
repulse.audio.start_BANG_ = (function repulse$audio$start_BANG_(pattern,on_beat_fn,on_event_fn){
repulse.audio.stop_BANG_();

var ac = repulse.audio.get_ctx();
ac.resume();

repulse.audio.arm_transitions_BANG_(new cljs.core.Keyword(null,"_","_",1453416199),pattern,ac);

var now_9707 = ac.currentTime;
var cycle_dur_9708 = new cljs.core.Keyword(null,"cycle-dur","cycle-dur",1241813230).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.audio.scheduler_state));
var start_cycle_9709 = (Math.floor((now_9707 / cycle_dur_9708)) | (0));
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$variadic(repulse.audio.scheduler_state,cljs.core.assoc,new cljs.core.Keyword(null,"playing?","playing?",-1884542863),true,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([new cljs.core.Keyword(null,"tracks","tracks",-326768501),new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"_","_",1453416199),pattern], null),new cljs.core.Keyword(null,"muted","muted",1275109029),cljs.core.PersistentHashSet.EMPTY,new cljs.core.Keyword(null,"cycle","cycle",710365284),start_cycle_9709,new cljs.core.Keyword(null,"on-beat","on-beat",1078535864),on_beat_fn,new cljs.core.Keyword(null,"on-event","on-event",1340574774),on_event_fn], 0));

var id_9711 = setInterval(repulse.audio.tick_BANG_,(25));
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.audio.scheduler_state,cljs.core.assoc,new cljs.core.Keyword(null,"interval-id","interval-id",79285360),id_9711);

return repulse.audio.tick_BANG_();
});
repulse.audio.playing_QMARK_ = (function repulse$audio$playing_QMARK_(){
return new cljs.core.Keyword(null,"playing?","playing?",-1884542863).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.audio.scheduler_state));
});
if((typeof repulse !== 'undefined') && (typeof repulse.audio !== 'undefined') && (typeof repulse.audio.tap_times !== 'undefined')){
} else {
repulse.audio.tap_times = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentVector.EMPTY);
}
/**
 * Register a tap for BPM detection. Returns computed BPM or nil if fewer than 2 taps.
 */
repulse.audio.tap_BANG_ = (function repulse$audio$tap_BANG_(){
var now = Date.now();
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$2(repulse.audio.tap_times,(function (ts){
return cljs.core.vec(cljs.core.take_last((8),cljs.core.filterv((function (p1__9048_SHARP_){
return (p1__9048_SHARP_ > (now - (4000)));
}),cljs.core.conj.cljs$core$IFn$_invoke$arity$2(ts,now))));
}));

var ts = cljs.core.deref(repulse.audio.tap_times);
if((cljs.core.count(ts) >= (2))){
var diffs = cljs.core.map.cljs$core$IFn$_invoke$arity$3(cljs.core._,cljs.core.rest(ts),ts);
var avg = (cljs.core.reduce.cljs$core$IFn$_invoke$arity$2(cljs.core._PLUS_,diffs) / cljs.core.count(diffs));
var bpm = (60000.0 / avg);
repulse.audio.set_bpm_BANG_(bpm);

return bpm;
} else {
return null;
}
});
if((typeof repulse !== 'undefined') && (typeof repulse.audio !== 'undefined') && (typeof repulse.audio.midi_sync_enabled_QMARK_ !== 'undefined')){
} else {
repulse.audio.midi_sync_enabled_QMARK_ = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(false);
}
if((typeof repulse !== 'undefined') && (typeof repulse.audio !== 'undefined') && (typeof repulse.audio.clock_pulses !== 'undefined')){
} else {
repulse.audio.clock_pulses = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentVector.EMPTY);
}
repulse.audio.handle_clock_pulse_BANG_ = (function repulse$audio$handle_clock_pulse_BANG_(){
var now = Date.now();
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$2(repulse.audio.clock_pulses,(function (ps){
return cljs.core.vec(cljs.core.take_last((48),cljs.core.filterv((function (p1__9053_SHARP_){
return (p1__9053_SHARP_ > (now - (2000)));
}),cljs.core.conj.cljs$core$IFn$_invoke$arity$2(ps,now))));
}));

var ps = cljs.core.deref(repulse.audio.clock_pulses);
if((cljs.core.count(ps) >= (25))){
var diffs = cljs.core.map.cljs$core$IFn$_invoke$arity$3(cljs.core._,cljs.core.rest(ps),ps);
var avg = (cljs.core.reduce.cljs$core$IFn$_invoke$arity$2(cljs.core._PLUS_,diffs) / cljs.core.count(diffs));
var bpm = (60000.0 / (avg * (24)));
return repulse.audio.set_bpm_BANG_(bpm);
} else {
return null;
}
});
repulse.audio.handle_clock_start_BANG_ = (function repulse$audio$handle_clock_start_BANG_(){
return cljs.core.reset_BANG_(repulse.audio.clock_pulses,cljs.core.PersistentVector.EMPTY);
});
repulse.audio.handle_clock_stop_BANG_ = (function repulse$audio$handle_clock_stop_BANG_(){
return cljs.core.reset_BANG_(repulse.audio.clock_pulses,cljs.core.PersistentVector.EMPTY);
});
repulse.audio.handle_midi_msg_BANG_ = (function repulse$audio$handle_midi_msg_BANG_(event){
var data = event.data;
var G__9054 = (data[(0)]);
switch (G__9054) {
case (248):
return repulse.audio.handle_clock_pulse_BANG_();

break;
case (250):
return repulse.audio.handle_clock_start_BANG_();

break;
case (252):
return repulse.audio.handle_clock_stop_BANG_();

break;
default:
return null;

}
});
/**
 * Enable or disable MIDI clock sync. Requests MIDI access on first enable.
 */
repulse.audio.set_midi_sync_BANG_ = (function repulse$audio$set_midi_sync_BANG_(enabled_QMARK_){
cljs.core.reset_BANG_(repulse.audio.midi_sync_enabled_QMARK_,enabled_QMARK_);

if(cljs.core.truth_(enabled_QMARK_)){
if(cljs.core.truth_(navigator.requestMIDIAccess)){
return navigator.requestMIDIAccess().then((function (access){
var seq__9055_9758 = cljs.core.seq(cljs.core.array_seq.cljs$core$IFn$_invoke$arity$1(access.inputs.values()));
var chunk__9056_9759 = null;
var count__9057_9760 = (0);
var i__9058_9761 = (0);
while(true){
if((i__9058_9761 < count__9057_9760)){
var input_9762 = chunk__9056_9759.cljs$core$IIndexed$_nth$arity$2(null,i__9058_9761);
(input_9762.onmidimessage = repulse.audio.handle_midi_msg_BANG_);


var G__9763 = seq__9055_9758;
var G__9764 = chunk__9056_9759;
var G__9765 = count__9057_9760;
var G__9766 = (i__9058_9761 + (1));
seq__9055_9758 = G__9763;
chunk__9056_9759 = G__9764;
count__9057_9760 = G__9765;
i__9058_9761 = G__9766;
continue;
} else {
var temp__5804__auto___9767 = cljs.core.seq(seq__9055_9758);
if(temp__5804__auto___9767){
var seq__9055_9768__$1 = temp__5804__auto___9767;
if(cljs.core.chunked_seq_QMARK_(seq__9055_9768__$1)){
var c__5525__auto___9771 = cljs.core.chunk_first(seq__9055_9768__$1);
var G__9773 = cljs.core.chunk_rest(seq__9055_9768__$1);
var G__9774 = c__5525__auto___9771;
var G__9775 = cljs.core.count(c__5525__auto___9771);
var G__9776 = (0);
seq__9055_9758 = G__9773;
chunk__9056_9759 = G__9774;
count__9057_9760 = G__9775;
i__9058_9761 = G__9776;
continue;
} else {
var input_9781 = cljs.core.first(seq__9055_9768__$1);
(input_9781.onmidimessage = repulse.audio.handle_midi_msg_BANG_);


var G__9785 = cljs.core.next(seq__9055_9768__$1);
var G__9786 = null;
var G__9787 = (0);
var G__9788 = (0);
seq__9055_9758 = G__9785;
chunk__9056_9759 = G__9786;
count__9057_9760 = G__9787;
i__9058_9761 = G__9788;
continue;
}
} else {
}
}
break;
}

return console.log("[REPuLse] MIDI clock sync enabled");
})).catch((function (e){
return console.warn("[REPuLse] MIDI access failed:",e);
}));
} else {
return console.warn("[REPuLse] Web MIDI not supported");
}
} else {
return cljs.core.reset_BANG_(repulse.audio.clock_pulses,cljs.core.PersistentVector.EMPTY);
}
});

//# sourceMappingURL=repulse.audio.js.map
