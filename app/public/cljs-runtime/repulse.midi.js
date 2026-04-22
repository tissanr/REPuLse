goog.provide('repulse.midi');
if((typeof repulse !== 'undefined') && (typeof repulse.midi !== 'undefined') && (typeof repulse.midi.midi_access !== 'undefined')){
} else {
repulse.midi.midi_access = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
if((typeof repulse !== 'undefined') && (typeof repulse.midi !== 'undefined') && (typeof repulse.midi.cc_mappings !== 'undefined')){
} else {
repulse.midi.cc_mappings = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
}
if((typeof repulse !== 'undefined') && (typeof repulse.midi !== 'undefined') && (typeof repulse.midi.clock_interval_id !== 'undefined')){
} else {
repulse.midi.clock_interval_id = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(null);
}
if((typeof repulse !== 'undefined') && (typeof repulse.midi !== 'undefined') && (typeof repulse.midi.midi_outputs !== 'undefined')){
} else {
repulse.midi.midi_outputs = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentVector.EMPTY);
}
/**
 * Request MIDI access if not already granted. Returns a Promise.
 */
repulse.midi.ensure_access_BANG_ = (function repulse$midi$ensure_access_BANG_(){
if(cljs.core.truth_(cljs.core.deref(repulse.midi.midi_access))){
return Promise.resolve(cljs.core.deref(repulse.midi.midi_access));
} else {
if(cljs.core.not(navigator.requestMIDIAccess)){
return Promise.reject((new Error("MIDI not supported in this browser \u2014 use Chrome or Edge")));
} else {
return navigator.requestMIDIAccess(({"sysex": false})).then((function (access){
cljs.core.reset_BANG_(repulse.midi.midi_access,access);

var outs_8420 = [];
access.outputs.forEach((function (p1__8288_SHARP_){
return outs_8420.push(p1__8288_SHARP_);
}));

cljs.core.reset_BANG_(repulse.midi.midi_outputs,cljs.core.vec(outs_8420));

return access;
}));
}
}
});
repulse.midi.on_midi_message = (function repulse$midi$on_midi_message(event){
var data = event.data;
var status = (data[(0)]);
var cmd = (status & (240));
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(cmd,(176))){
var cc_num = (data[(1)]);
var value = (data[(2)]);
var temp__5804__auto__ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.midi.cc_mappings),cc_num);
if(cljs.core.truth_(temp__5804__auto__)){
var mapping = temp__5804__auto__;
var map__8294 = mapping;
var map__8294__$1 = cljs.core.__destructure_map(map__8294);
var min = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8294__$1,new cljs.core.Keyword(null,"min","min",444991522));
var max = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8294__$1,new cljs.core.Keyword(null,"max","max",61366548));
var on_change = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8294__$1,new cljs.core.Keyword(null,"on-change","on-change",-732046149));
var normalized = (value / 127.0);
var scaled = (min + (normalized * (max - min)));
if(cljs.core.truth_(on_change)){
var G__8295 = new cljs.core.Keyword(null,"target","target",253001721).cljs$core$IFn$_invoke$arity$1(mapping);
var G__8296 = scaled;
return (on_change.cljs$core$IFn$_invoke$arity$2 ? on_change.cljs$core$IFn$_invoke$arity$2(G__8295,G__8296) : on_change.call(null,G__8295,G__8296));
} else {
return null;
}
} else {
return null;
}
} else {
return null;
}
});
/**
 * Attach CC listener to all currently known MIDI inputs.
 */
repulse.midi.register_cc_listener_BANG_ = (function repulse$midi$register_cc_listener_BANG_(){
var temp__5804__auto__ = cljs.core.deref(repulse.midi.midi_access);
if(cljs.core.truth_(temp__5804__auto__)){
var access = temp__5804__auto__;
return access.inputs.forEach((function (input){
return (input.onmidimessage = repulse.midi.on_midi_message);
}));
} else {
return null;
}
});
/**
 * Map a MIDI CC number to a parameter target.
 * on-change-fn is called with (target-kw scaled-value) on each CC message.
 */
repulse.midi.map_cc_BANG_ = (function repulse$midi$map_cc_BANG_(cc_num,target_kw,on_change_fn){
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.midi.cc_mappings,cljs.core.assoc,cc_num,new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"target","target",253001721),target_kw,new cljs.core.Keyword(null,"min","min",444991522),0.0,new cljs.core.Keyword(null,"max","max",61366548),1.0,new cljs.core.Keyword(null,"on-change","on-change",-732046149),on_change_fn], null));

return repulse.midi.register_cc_listener_BANG_();
});
/**
 * Convert Hz frequency to MIDI note number (0–127).
 */
repulse.midi.hz__GT_midi = (function repulse$midi$hz__GT_midi(hz){
var m = ((69) + ((12) * (Math.log((hz / 440.0)) / Math.log((2)))));
return Math.round(Math.max((0),Math.min((127),m)));
});
/**
 * Send MIDI Note On on the given channel (1-indexed) to all outputs.
 * timestamp is a performance.now() value in ms (nil = send immediately).
 */
repulse.midi.send_note_on_BANG_ = (function repulse$midi$send_note_on_BANG_(channel,note,velocity,timestamp){
var status = ((144) + (channel - (1)));
var msg = (new Uint8Array([status,note,velocity]));
var seq__8309 = cljs.core.seq(cljs.core.deref(repulse.midi.midi_outputs));
var chunk__8310 = null;
var count__8311 = (0);
var i__8312 = (0);
while(true){
if((i__8312 < count__8311)){
var output = chunk__8310.cljs$core$IIndexed$_nth$arity$2(null,i__8312);
if(cljs.core.truth_(timestamp)){
output.send(msg,timestamp);
} else {
output.send(msg);
}


var G__8429 = seq__8309;
var G__8430 = chunk__8310;
var G__8431 = count__8311;
var G__8432 = (i__8312 + (1));
seq__8309 = G__8429;
chunk__8310 = G__8430;
count__8311 = G__8431;
i__8312 = G__8432;
continue;
} else {
var temp__5804__auto__ = cljs.core.seq(seq__8309);
if(temp__5804__auto__){
var seq__8309__$1 = temp__5804__auto__;
if(cljs.core.chunked_seq_QMARK_(seq__8309__$1)){
var c__5525__auto__ = cljs.core.chunk_first(seq__8309__$1);
var G__8433 = cljs.core.chunk_rest(seq__8309__$1);
var G__8434 = c__5525__auto__;
var G__8435 = cljs.core.count(c__5525__auto__);
var G__8437 = (0);
seq__8309 = G__8433;
chunk__8310 = G__8434;
count__8311 = G__8435;
i__8312 = G__8437;
continue;
} else {
var output = cljs.core.first(seq__8309__$1);
if(cljs.core.truth_(timestamp)){
output.send(msg,timestamp);
} else {
output.send(msg);
}


var G__8439 = cljs.core.next(seq__8309__$1);
var G__8440 = null;
var G__8441 = (0);
var G__8442 = (0);
seq__8309 = G__8439;
chunk__8310 = G__8440;
count__8311 = G__8441;
i__8312 = G__8442;
continue;
}
} else {
return null;
}
}
break;
}
});
/**
 * Send MIDI Note Off on the given channel to all outputs.
 */
repulse.midi.send_note_off_BANG_ = (function repulse$midi$send_note_off_BANG_(channel,note,timestamp){
var status = ((128) + (channel - (1)));
var msg = (new Uint8Array([status,note,(0)]));
var seq__8317 = cljs.core.seq(cljs.core.deref(repulse.midi.midi_outputs));
var chunk__8318 = null;
var count__8319 = (0);
var i__8320 = (0);
while(true){
if((i__8320 < count__8319)){
var output = chunk__8318.cljs$core$IIndexed$_nth$arity$2(null,i__8320);
if(cljs.core.truth_(timestamp)){
output.send(msg,timestamp);
} else {
output.send(msg);
}


var G__8443 = seq__8317;
var G__8444 = chunk__8318;
var G__8445 = count__8319;
var G__8446 = (i__8320 + (1));
seq__8317 = G__8443;
chunk__8318 = G__8444;
count__8319 = G__8445;
i__8320 = G__8446;
continue;
} else {
var temp__5804__auto__ = cljs.core.seq(seq__8317);
if(temp__5804__auto__){
var seq__8317__$1 = temp__5804__auto__;
if(cljs.core.chunked_seq_QMARK_(seq__8317__$1)){
var c__5525__auto__ = cljs.core.chunk_first(seq__8317__$1);
var G__8448 = cljs.core.chunk_rest(seq__8317__$1);
var G__8449 = c__5525__auto__;
var G__8450 = cljs.core.count(c__5525__auto__);
var G__8451 = (0);
seq__8317 = G__8448;
chunk__8318 = G__8449;
count__8319 = G__8450;
i__8320 = G__8451;
continue;
} else {
var output = cljs.core.first(seq__8317__$1);
if(cljs.core.truth_(timestamp)){
output.send(msg,timestamp);
} else {
output.send(msg);
}


var G__8452 = cljs.core.next(seq__8317__$1);
var G__8453 = null;
var G__8454 = (0);
var G__8455 = (0);
seq__8317 = G__8452;
chunk__8318 = G__8453;
count__8319 = G__8454;
i__8320 = G__8455;
continue;
}
} else {
return null;
}
}
break;
}
});
repulse.midi.send_clock_byte_BANG_ = (function repulse$midi$send_clock_byte_BANG_(byte_val){
var msg = (new Uint8Array([byte_val]));
var seq__8324 = cljs.core.seq(cljs.core.deref(repulse.midi.midi_outputs));
var chunk__8325 = null;
var count__8326 = (0);
var i__8327 = (0);
while(true){
if((i__8327 < count__8326)){
var output = chunk__8325.cljs$core$IIndexed$_nth$arity$2(null,i__8327);
output.send(msg);


var G__8460 = seq__8324;
var G__8461 = chunk__8325;
var G__8462 = count__8326;
var G__8463 = (i__8327 + (1));
seq__8324 = G__8460;
chunk__8325 = G__8461;
count__8326 = G__8462;
i__8327 = G__8463;
continue;
} else {
var temp__5804__auto__ = cljs.core.seq(seq__8324);
if(temp__5804__auto__){
var seq__8324__$1 = temp__5804__auto__;
if(cljs.core.chunked_seq_QMARK_(seq__8324__$1)){
var c__5525__auto__ = cljs.core.chunk_first(seq__8324__$1);
var G__8465 = cljs.core.chunk_rest(seq__8324__$1);
var G__8466 = c__5525__auto__;
var G__8467 = cljs.core.count(c__5525__auto__);
var G__8468 = (0);
seq__8324 = G__8465;
chunk__8325 = G__8466;
count__8326 = G__8467;
i__8327 = G__8468;
continue;
} else {
var output = cljs.core.first(seq__8324__$1);
output.send(msg);


var G__8469 = cljs.core.next(seq__8324__$1);
var G__8470 = null;
var G__8471 = (0);
var G__8472 = (0);
seq__8324 = G__8469;
chunk__8325 = G__8470;
count__8326 = G__8471;
i__8327 = G__8472;
continue;
}
} else {
return null;
}
}
break;
}
});
/**
 * Start sending 24ppqn MIDI clock at the given BPM.
 * Sends MIDI Start (0xFA) then begins 24ppqn pulse interval.
 */
repulse.midi.start_clock_BANG_ = (function repulse$midi$start_clock_BANG_(bpm){
(repulse.midi.stop_clock_BANG_.cljs$core$IFn$_invoke$arity$0 ? repulse.midi.stop_clock_BANG_.cljs$core$IFn$_invoke$arity$0() : repulse.midi.stop_clock_BANG_.call(null));

repulse.midi.send_clock_byte_BANG_((250));

var interval_ms = (((60.0 / bpm) / 24.0) * (1000));
return cljs.core.reset_BANG_(repulse.midi.clock_interval_id,setInterval((function (){
return repulse.midi.send_clock_byte_BANG_((248));
}),interval_ms));
});
/**
 * Stop MIDI clock output, sending MIDI Stop (0xFC).
 */
repulse.midi.stop_clock_BANG_ = (function repulse$midi$stop_clock_BANG_(){
var temp__5804__auto__ = cljs.core.deref(repulse.midi.clock_interval_id);
if(cljs.core.truth_(temp__5804__auto__)){
var id = temp__5804__auto__;
clearInterval(id);

cljs.core.reset_BANG_(repulse.midi.clock_interval_id,null);

return repulse.midi.send_clock_byte_BANG_((252));
} else {
return null;
}
});
/**
 * Update clock tempo in-place (restarts the interval).
 */
repulse.midi.update_clock_bpm_BANG_ = (function repulse$midi$update_clock_bpm_BANG_(bpm){
if(cljs.core.truth_(cljs.core.deref(repulse.midi.clock_interval_id))){
return repulse.midi.start_clock_BANG_(bpm);
} else {
return null;
}
});
/**
 * Encode n as a MIDI variable-length quantity.
 */
repulse.midi.write_vlq = (function repulse$midi$write_vlq(n){
if((n < (128))){
return new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [n], null);
} else {
var v = n;
var acc = cljs.core.PersistentVector.EMPTY;
while(true){
if((v === (0))){
var rev = cljs.core.vec(cljs.core.reverse(acc));
return cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.mapv.cljs$core$IFn$_invoke$arity$2(((function (v,acc,rev){
return (function (p1__8347_SHARP_){
return (p1__8347_SHARP_ | (128));
});})(v,acc,rev))
,cljs.core.butlast(rev)),new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [cljs.core.last(rev)], null));
} else {
var G__8474 = (v >>> (7));
var G__8475 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(acc,(v & (127)));
v = G__8474;
acc = G__8475;
continue;
}
break;
}
}
});
repulse.midi.write_u16 = (function repulse$midi$write_u16(n){
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [((n >>> (8)) & (255)),(n & (255))], null);
});
repulse.midi.write_u32 = (function repulse$midi$write_u32(n){
return new cljs.core.PersistentVector(null, 4, 5, cljs.core.PersistentVector.EMPTY_NODE, [((n >>> (24)) & (255)),((n >>> (16)) & (255)),((n >>> (8)) & (255)),(n & (255))], null);
});
/**
 * Export a list of note events to a Standard MIDI File (Type 0).
 * Each event: {:time-sec f :duration-sec f :midi-note n :channel n}
 * Returns a Uint8Array.
 */
repulse.midi.export_midi = (function repulse$midi$export_midi(events,bpm){
var ppqn = (480);
var us_per_beat = ((6.0E7 / bpm) | (0));
var sorted = cljs.core.sort_by.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"time-sec","time-sec",1972980092),events);
var sec__GT_tick = (function (s){
return (((s * (bpm / 60.0)) * ppqn) | (0));
});
var tempo_evt = cljs.core.concat.cljs$core$IFn$_invoke$arity$2(new cljs.core.PersistentVector(null, 4, 5, cljs.core.PersistentVector.EMPTY_NODE, [(0),(255),(81),(3)], null),new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [((us_per_beat >>> (16)) & (255)),((us_per_beat >>> (8)) & (255)),(us_per_beat & (255))], null));
var track_bytes = new cljs.core.Keyword(null,"bytes","bytes",1175866680).cljs$core$IFn$_invoke$arity$1(cljs.core.reduce.cljs$core$IFn$_invoke$arity$3((function (p__8384,p__8385){
var map__8391 = p__8384;
var map__8391__$1 = cljs.core.__destructure_map(map__8391);
var bytes = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8391__$1,new cljs.core.Keyword(null,"bytes","bytes",1175866680));
var last_tick = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8391__$1,new cljs.core.Keyword(null,"last-tick","last-tick",355829290));
var map__8395 = p__8385;
var map__8395__$1 = cljs.core.__destructure_map(map__8395);
var time_sec = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8395__$1,new cljs.core.Keyword(null,"time-sec","time-sec",1972980092));
var duration_sec = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8395__$1,new cljs.core.Keyword(null,"duration-sec","duration-sec",768648959));
var midi_note = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8395__$1,new cljs.core.Keyword(null,"midi-note","midi-note",1087931036));
var channel = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8395__$1,new cljs.core.Keyword(null,"channel","channel",734187692));
var ch = ((function (){var or__5002__auto__ = channel;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (1);
}
})() - (1));
var on_tick = sec__GT_tick(time_sec);
var off_tick = sec__GT_tick((time_sec + duration_sec));
var delta_on = (on_tick - last_tick);
var on_bytes = cljs.core.concat.cljs$core$IFn$_invoke$arity$2(repulse.midi.write_vlq(delta_on),new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [((144) + ch),midi_note,(100)], null));
var delta_off = (off_tick - on_tick);
var off_bytes = cljs.core.concat.cljs$core$IFn$_invoke$arity$2(repulse.midi.write_vlq(delta_off),new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [((128) + ch),midi_note,(0)], null));
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"bytes","bytes",1175866680),cljs.core.into.cljs$core$IFn$_invoke$arity$2(bytes,cljs.core.concat.cljs$core$IFn$_invoke$arity$2(on_bytes,off_bytes)),new cljs.core.Keyword(null,"last-tick","last-tick",355829290),off_tick], null);
}),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"bytes","bytes",1175866680),cljs.core.PersistentVector.EMPTY,new cljs.core.Keyword(null,"last-tick","last-tick",355829290),(0)], null),sorted));
var eot = new cljs.core.PersistentVector(null, 4, 5, cljs.core.PersistentVector.EMPTY_NODE, [(0),(255),(47),(0)], null);
var mtk_data = cljs.core.vec(cljs.core.concat.cljs$core$IFn$_invoke$arity$variadic(tempo_evt,track_bytes,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([eot], 0)));
var header = cljs.core.concat.cljs$core$IFn$_invoke$arity$variadic(new cljs.core.PersistentVector(null, 4, 5, cljs.core.PersistentVector.EMPTY_NODE, [(77),(84),(104),(100)], null),repulse.midi.write_u32((6)),cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([repulse.midi.write_u16((0)),repulse.midi.write_u16((1)),repulse.midi.write_u16(ppqn)], 0));
var trk_header = cljs.core.concat.cljs$core$IFn$_invoke$arity$2(new cljs.core.PersistentVector(null, 4, 5, cljs.core.PersistentVector.EMPTY_NODE, [(77),(84),(114),(107)], null),repulse.midi.write_u32(cljs.core.count(mtk_data)));
return (new Uint8Array(cljs.core.clj__GT_js(cljs.core.concat.cljs$core$IFn$_invoke$arity$variadic(header,trk_header,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([mtk_data], 0)))));
});

//# sourceMappingURL=repulse.midi.js.map
