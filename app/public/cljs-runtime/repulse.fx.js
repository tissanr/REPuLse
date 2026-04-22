goog.provide('repulse.fx');
if((typeof repulse !== 'undefined') && (typeof repulse.fx !== 'undefined') && (typeof repulse.fx.chain !== 'undefined')){
} else {
repulse.fx.chain = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentVector.EMPTY);
}
/**
 * Reconnect the chain: masterGain → effect1 → effect2 → ... → analyser.
 * Disconnects all existing outputs first, then rebuilds in order.
 */
repulse.fx.rewire_BANG_ = (function repulse$fx$rewire_BANG_(){
var gain = cljs.core.deref(repulse.audio.master_gain);
var anl = cljs.core.deref(repulse.audio.analyser_node);
var effects = cljs.core.deref(repulse.fx.chain);
gain.disconnect();

var seq__9059_9607 = cljs.core.seq(effects);
var chunk__9060_9608 = null;
var count__9061_9609 = (0);
var i__9062_9610 = (0);
while(true){
if((i__9062_9610 < count__9061_9609)){
var map__9074_9611 = chunk__9060_9608.cljs$core$IIndexed$_nth$arity$2(null,i__9062_9610);
var map__9074_9612__$1 = cljs.core.__destructure_map(map__9074_9611);
var output_9613 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9074_9612__$1,new cljs.core.Keyword(null,"output","output",-1105869043));
output_9613.disconnect();


var G__9616 = seq__9059_9607;
var G__9617 = chunk__9060_9608;
var G__9618 = count__9061_9609;
var G__9619 = (i__9062_9610 + (1));
seq__9059_9607 = G__9616;
chunk__9060_9608 = G__9617;
count__9061_9609 = G__9618;
i__9062_9610 = G__9619;
continue;
} else {
var temp__5804__auto___9620 = cljs.core.seq(seq__9059_9607);
if(temp__5804__auto___9620){
var seq__9059_9621__$1 = temp__5804__auto___9620;
if(cljs.core.chunked_seq_QMARK_(seq__9059_9621__$1)){
var c__5525__auto___9622 = cljs.core.chunk_first(seq__9059_9621__$1);
var G__9623 = cljs.core.chunk_rest(seq__9059_9621__$1);
var G__9624 = c__5525__auto___9622;
var G__9625 = cljs.core.count(c__5525__auto___9622);
var G__9626 = (0);
seq__9059_9607 = G__9623;
chunk__9060_9608 = G__9624;
count__9061_9609 = G__9625;
i__9062_9610 = G__9626;
continue;
} else {
var map__9075_9627 = cljs.core.first(seq__9059_9621__$1);
var map__9075_9628__$1 = cljs.core.__destructure_map(map__9075_9627);
var output_9629 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9075_9628__$1,new cljs.core.Keyword(null,"output","output",-1105869043));
output_9629.disconnect();


var G__9631 = cljs.core.next(seq__9059_9621__$1);
var G__9632 = null;
var G__9633 = (0);
var G__9634 = (0);
seq__9059_9607 = G__9631;
chunk__9060_9608 = G__9632;
count__9061_9609 = G__9633;
i__9062_9610 = G__9634;
continue;
}
} else {
}
}
break;
}

if(cljs.core.empty_QMARK_(effects)){
return gain.connect(anl);
} else {
gain.connect(new cljs.core.Keyword(null,"input","input",556931961).cljs$core$IFn$_invoke$arity$1(cljs.core.first(effects)));

var seq__9077_9640 = cljs.core.seq(cljs.core.partition.cljs$core$IFn$_invoke$arity$3((2),(1),effects));
var chunk__9078_9641 = null;
var count__9079_9642 = (0);
var i__9080_9643 = (0);
while(true){
if((i__9080_9643 < count__9079_9642)){
var vec__9090_9645 = chunk__9078_9641.cljs$core$IIndexed$_nth$arity$2(null,i__9080_9643);
var a_9646 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9090_9645,(0),null);
var b_9647 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9090_9645,(1),null);
new cljs.core.Keyword(null,"output","output",-1105869043).cljs$core$IFn$_invoke$arity$1(a_9646).connect(new cljs.core.Keyword(null,"input","input",556931961).cljs$core$IFn$_invoke$arity$1(b_9647));


var G__9648 = seq__9077_9640;
var G__9649 = chunk__9078_9641;
var G__9650 = count__9079_9642;
var G__9651 = (i__9080_9643 + (1));
seq__9077_9640 = G__9648;
chunk__9078_9641 = G__9649;
count__9079_9642 = G__9650;
i__9080_9643 = G__9651;
continue;
} else {
var temp__5804__auto___9652 = cljs.core.seq(seq__9077_9640);
if(temp__5804__auto___9652){
var seq__9077_9653__$1 = temp__5804__auto___9652;
if(cljs.core.chunked_seq_QMARK_(seq__9077_9653__$1)){
var c__5525__auto___9654 = cljs.core.chunk_first(seq__9077_9653__$1);
var G__9655 = cljs.core.chunk_rest(seq__9077_9653__$1);
var G__9656 = c__5525__auto___9654;
var G__9657 = cljs.core.count(c__5525__auto___9654);
var G__9658 = (0);
seq__9077_9640 = G__9655;
chunk__9078_9641 = G__9656;
count__9079_9642 = G__9657;
i__9080_9643 = G__9658;
continue;
} else {
var vec__9093_9659 = cljs.core.first(seq__9077_9653__$1);
var a_9660 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9093_9659,(0),null);
var b_9661 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9093_9659,(1),null);
new cljs.core.Keyword(null,"output","output",-1105869043).cljs$core$IFn$_invoke$arity$1(a_9660).connect(new cljs.core.Keyword(null,"input","input",556931961).cljs$core$IFn$_invoke$arity$1(b_9661));


var G__9666 = cljs.core.next(seq__9077_9653__$1);
var G__9667 = null;
var G__9668 = (0);
var G__9669 = (0);
seq__9077_9640 = G__9666;
chunk__9078_9641 = G__9667;
count__9079_9642 = G__9668;
i__9080_9643 = G__9669;
continue;
}
} else {
}
}
break;
}

return new cljs.core.Keyword(null,"output","output",-1105869043).cljs$core$IFn$_invoke$arity$1(cljs.core.last(effects)).connect(anl);
}
});
repulse.fx.add_effect_BANG_ = (function repulse$fx$add_effect_BANG_(plugin){
var ac = repulse.audio.get_ctx();
var nodes = plugin.createNodes(ac);
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$3(repulse.fx.chain,cljs.core.conj,new cljs.core.PersistentArrayMap(null, 6, [new cljs.core.Keyword(null,"name","name",1843675177),plugin.name,new cljs.core.Keyword(null,"plugin","plugin",-1688841923),plugin,new cljs.core.Keyword(null,"input","input",556931961),nodes.inputNode,new cljs.core.Keyword(null,"output","output",-1105869043),nodes.outputNode,new cljs.core.Keyword(null,"bypassed?","bypassed?",132826625),false,new cljs.core.Keyword(null,"active?","active?",459499776),false], null));

return repulse.fx.rewire_BANG_();
});
repulse.fx.remove_effect_BANG_ = (function repulse$fx$remove_effect_BANG_(effect_name){
var temp__5804__auto__ = cljs.core.some((function (p1__9099_SHARP_){
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(effect_name,new cljs.core.Keyword(null,"name","name",1843675177).cljs$core$IFn$_invoke$arity$1(p1__9099_SHARP_))){
return p1__9099_SHARP_;
} else {
return null;
}
}),cljs.core.deref(repulse.fx.chain));
if(cljs.core.truth_(temp__5804__auto__)){
var entry = temp__5804__auto__;
new cljs.core.Keyword(null,"plugin","plugin",-1688841923).cljs$core$IFn$_invoke$arity$1(entry).destroy();

cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$2(repulse.fx.chain,(function (p1__9100_SHARP_){
return cljs.core.filterv((function (e){
return cljs.core.not_EQ_.cljs$core$IFn$_invoke$arity$2(effect_name,new cljs.core.Keyword(null,"name","name",1843675177).cljs$core$IFn$_invoke$arity$1(e));
}),p1__9100_SHARP_);
}));

return repulse.fx.rewire_BANG_();
} else {
return null;
}
});
repulse.fx.set_param_BANG_ = (function repulse$fx$set_param_BANG_(effect_name,param_name,value){
if(cljs.core.truth_(cljs.core.some((function (p1__9102_SHARP_){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(effect_name,new cljs.core.Keyword(null,"name","name",1843675177).cljs$core$IFn$_invoke$arity$1(p1__9102_SHARP_));
}),cljs.core.deref(repulse.fx.chain)))){
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$2(repulse.fx.chain,(function (c){
return cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__9103_SHARP_){
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(effect_name,new cljs.core.Keyword(null,"name","name",1843675177).cljs$core$IFn$_invoke$arity$1(p1__9103_SHARP_))){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(p1__9103_SHARP_,new cljs.core.Keyword(null,"active?","active?",459499776),true);
} else {
return p1__9103_SHARP_;
}
}),c);
}));

var temp__5804__auto__ = cljs.core.some((function (p1__9105_SHARP_){
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(effect_name,new cljs.core.Keyword(null,"name","name",1843675177).cljs$core$IFn$_invoke$arity$1(p1__9105_SHARP_))){
return p1__9105_SHARP_;
} else {
return null;
}
}),cljs.core.deref(repulse.fx.chain));
if(cljs.core.truth_(temp__5804__auto__)){
var entry = temp__5804__auto__;
return new cljs.core.Keyword(null,"plugin","plugin",-1688841923).cljs$core$IFn$_invoke$arity$1(entry).setParam(param_name,value);
} else {
return null;
}
} else {
return null;
}
});
repulse.fx.bypass_BANG_ = (function repulse$fx$bypass_BANG_(effect_name,enabled){
var temp__5804__auto__ = cljs.core.some((function (p1__9116_SHARP_){
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(effect_name,new cljs.core.Keyword(null,"name","name",1843675177).cljs$core$IFn$_invoke$arity$1(p1__9116_SHARP_))){
return p1__9116_SHARP_;
} else {
return null;
}
}),cljs.core.deref(repulse.fx.chain));
if(cljs.core.truth_(temp__5804__auto__)){
var entry = temp__5804__auto__;
new cljs.core.Keyword(null,"plugin","plugin",-1688841923).cljs$core$IFn$_invoke$arity$1(entry).bypass(enabled);

return cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$2(repulse.fx.chain,(function (c){
return cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__9117_SHARP_){
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(effect_name,new cljs.core.Keyword(null,"name","name",1843675177).cljs$core$IFn$_invoke$arity$1(p1__9117_SHARP_))){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(p1__9117_SHARP_,new cljs.core.Keyword(null,"bypassed?","bypassed?",132826625),enabled);
} else {
return p1__9117_SHARP_;
}
}),c);
}));
} else {
return null;
}
});
/**
 * Reconnect a track's FX chain: trackGain → fx1 → fx2 → ... → masterGain.
 */
repulse.fx.rewire_track_BANG_ = (function repulse$fx$rewire_track_BANG_(track_name){
var temp__5804__auto__ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.audio.track_nodes),track_name);
if(cljs.core.truth_(temp__5804__auto__)){
var tn = temp__5804__auto__;
var gain = new cljs.core.Keyword(null,"gain-node","gain-node",-1178526839).cljs$core$IFn$_invoke$arity$1(tn);
var effects = new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234).cljs$core$IFn$_invoke$arity$1(tn);
var master = cljs.core.deref(repulse.audio.master_gain);
gain.disconnect();

var seq__9119_9712 = cljs.core.seq(effects);
var chunk__9120_9713 = null;
var count__9121_9714 = (0);
var i__9122_9715 = (0);
while(true){
if((i__9122_9715 < count__9121_9714)){
var map__9129_9716 = chunk__9120_9713.cljs$core$IIndexed$_nth$arity$2(null,i__9122_9715);
var map__9129_9717__$1 = cljs.core.__destructure_map(map__9129_9716);
var output_9718 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9129_9717__$1,new cljs.core.Keyword(null,"output","output",-1105869043));
try{output_9718.disconnect();
}catch (e9130){var __9719 = e9130;
}

var G__9720 = seq__9119_9712;
var G__9721 = chunk__9120_9713;
var G__9722 = count__9121_9714;
var G__9723 = (i__9122_9715 + (1));
seq__9119_9712 = G__9720;
chunk__9120_9713 = G__9721;
count__9121_9714 = G__9722;
i__9122_9715 = G__9723;
continue;
} else {
var temp__5804__auto___9724__$1 = cljs.core.seq(seq__9119_9712);
if(temp__5804__auto___9724__$1){
var seq__9119_9725__$1 = temp__5804__auto___9724__$1;
if(cljs.core.chunked_seq_QMARK_(seq__9119_9725__$1)){
var c__5525__auto___9726 = cljs.core.chunk_first(seq__9119_9725__$1);
var G__9727 = cljs.core.chunk_rest(seq__9119_9725__$1);
var G__9728 = c__5525__auto___9726;
var G__9729 = cljs.core.count(c__5525__auto___9726);
var G__9730 = (0);
seq__9119_9712 = G__9727;
chunk__9120_9713 = G__9728;
count__9121_9714 = G__9729;
i__9122_9715 = G__9730;
continue;
} else {
var map__9133_9731 = cljs.core.first(seq__9119_9725__$1);
var map__9133_9732__$1 = cljs.core.__destructure_map(map__9133_9731);
var output_9733 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9133_9732__$1,new cljs.core.Keyword(null,"output","output",-1105869043));
try{output_9733.disconnect();
}catch (e9136){var __9734 = e9136;
}

var G__9735 = cljs.core.next(seq__9119_9725__$1);
var G__9736 = null;
var G__9737 = (0);
var G__9738 = (0);
seq__9119_9712 = G__9735;
chunk__9120_9713 = G__9736;
count__9121_9714 = G__9737;
i__9122_9715 = G__9738;
continue;
}
} else {
}
}
break;
}

if(cljs.core.empty_QMARK_(effects)){
return gain.connect(master);
} else {
gain.connect(new cljs.core.Keyword(null,"input","input",556931961).cljs$core$IFn$_invoke$arity$1(cljs.core.first(effects)));

var seq__9138_9739 = cljs.core.seq(cljs.core.partition.cljs$core$IFn$_invoke$arity$3((2),(1),effects));
var chunk__9139_9740 = null;
var count__9140_9741 = (0);
var i__9141_9742 = (0);
while(true){
if((i__9141_9742 < count__9140_9741)){
var vec__9152_9744 = chunk__9139_9740.cljs$core$IIndexed$_nth$arity$2(null,i__9141_9742);
var a_9745 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9152_9744,(0),null);
var b_9746 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9152_9744,(1),null);
new cljs.core.Keyword(null,"output","output",-1105869043).cljs$core$IFn$_invoke$arity$1(a_9745).connect(new cljs.core.Keyword(null,"input","input",556931961).cljs$core$IFn$_invoke$arity$1(b_9746));


var G__9750 = seq__9138_9739;
var G__9751 = chunk__9139_9740;
var G__9752 = count__9140_9741;
var G__9753 = (i__9141_9742 + (1));
seq__9138_9739 = G__9750;
chunk__9139_9740 = G__9751;
count__9140_9741 = G__9752;
i__9141_9742 = G__9753;
continue;
} else {
var temp__5804__auto___9769__$1 = cljs.core.seq(seq__9138_9739);
if(temp__5804__auto___9769__$1){
var seq__9138_9770__$1 = temp__5804__auto___9769__$1;
if(cljs.core.chunked_seq_QMARK_(seq__9138_9770__$1)){
var c__5525__auto___9772 = cljs.core.chunk_first(seq__9138_9770__$1);
var G__9777 = cljs.core.chunk_rest(seq__9138_9770__$1);
var G__9778 = c__5525__auto___9772;
var G__9779 = cljs.core.count(c__5525__auto___9772);
var G__9780 = (0);
seq__9138_9739 = G__9777;
chunk__9139_9740 = G__9778;
count__9140_9741 = G__9779;
i__9141_9742 = G__9780;
continue;
} else {
var vec__9155_9782 = cljs.core.first(seq__9138_9770__$1);
var a_9783 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9155_9782,(0),null);
var b_9784 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9155_9782,(1),null);
new cljs.core.Keyword(null,"output","output",-1105869043).cljs$core$IFn$_invoke$arity$1(a_9783).connect(new cljs.core.Keyword(null,"input","input",556931961).cljs$core$IFn$_invoke$arity$1(b_9784));


var G__9789 = cljs.core.next(seq__9138_9770__$1);
var G__9790 = null;
var G__9791 = (0);
var G__9792 = (0);
seq__9138_9739 = G__9789;
chunk__9139_9740 = G__9790;
count__9140_9741 = G__9791;
i__9141_9742 = G__9792;
continue;
}
} else {
}
}
break;
}

return new cljs.core.Keyword(null,"output","output",-1105869043).cljs$core$IFn$_invoke$arity$1(cljs.core.last(effects)).connect(master);
}
} else {
return null;
}
});
/**
 * Instantiate an effect plugin on a specific track by re-using a registered plugin.
 */
repulse.fx.add_track_effect_BANG_ = (function repulse$fx$add_track_effect_BANG_(track_name,effect_name){
var temp__5804__auto__ = cljs.core.some((function (p1__9158_SHARP_){
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(effect_name,new cljs.core.Keyword(null,"name","name",1843675177).cljs$core$IFn$_invoke$arity$1(p1__9158_SHARP_))){
return p1__9158_SHARP_;
} else {
return null;
}
}),cljs.core.deref(repulse.fx.chain));
if(cljs.core.truth_(temp__5804__auto__)){
var entry = temp__5804__auto__;
var ac = repulse.audio.get_ctx();
var p = new cljs.core.Keyword(null,"plugin","plugin",-1688841923).cljs$core$IFn$_invoke$arity$1(entry);
var fresh = ((cljs.core.fn_QMARK_(p.clone))?p.clone():(function (){var o = Object.create(Object.getPrototypeOf(p));
Object.assign(o,p);

return o;
})());
var nodes = fresh.createNodes(ac);
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$variadic(repulse.audio.track_nodes,cljs.core.update_in,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [track_name,new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234)], null),cljs.core.conj,cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([new cljs.core.PersistentArrayMap(null, 5, [new cljs.core.Keyword(null,"name","name",1843675177),effect_name,new cljs.core.Keyword(null,"plugin","plugin",-1688841923),fresh,new cljs.core.Keyword(null,"input","input",556931961),nodes.inputNode,new cljs.core.Keyword(null,"output","output",-1105869043),nodes.outputNode,new cljs.core.Keyword(null,"bypassed?","bypassed?",132826625),false], null)], 0));

return repulse.fx.rewire_track_BANG_(track_name);
} else {
return null;
}
});
/**
 * Remove a specific effect from a track's FX chain.
 */
repulse.fx.remove_track_effect_BANG_ = (function repulse$fx$remove_track_effect_BANG_(track_name,effect_name){
var temp__5804__auto__ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.audio.track_nodes),track_name);
if(cljs.core.truth_(temp__5804__auto__)){
var tn = temp__5804__auto__;
var temp__5804__auto____$1 = cljs.core.some((function (p1__9164_SHARP_){
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(effect_name,new cljs.core.Keyword(null,"name","name",1843675177).cljs$core$IFn$_invoke$arity$1(p1__9164_SHARP_))){
return p1__9164_SHARP_;
} else {
return null;
}
}),new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234).cljs$core$IFn$_invoke$arity$1(tn));
if(cljs.core.truth_(temp__5804__auto____$1)){
var entry = temp__5804__auto____$1;
try{new cljs.core.Keyword(null,"plugin","plugin",-1688841923).cljs$core$IFn$_invoke$arity$1(entry).destroy();
}catch (e9168){var __9802 = e9168;
}
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.audio.track_nodes,cljs.core.update_in,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [track_name,new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234)], null),(function (c){
return cljs.core.filterv((function (p1__9165_SHARP_){
return cljs.core.not_EQ_.cljs$core$IFn$_invoke$arity$2(effect_name,new cljs.core.Keyword(null,"name","name",1843675177).cljs$core$IFn$_invoke$arity$1(p1__9165_SHARP_));
}),c);
}));

return repulse.fx.rewire_track_BANG_(track_name);
} else {
return null;
}
} else {
return null;
}
});
/**
 * Remove all effects from a track's FX chain.
 */
repulse.fx.clear_track_effects_BANG_ = (function repulse$fx$clear_track_effects_BANG_(track_name){
var temp__5804__auto__ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.audio.track_nodes),track_name);
if(cljs.core.truth_(temp__5804__auto__)){
var tn = temp__5804__auto__;
var seq__9172_9809 = cljs.core.seq(new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234).cljs$core$IFn$_invoke$arity$1(tn));
var chunk__9173_9810 = null;
var count__9174_9811 = (0);
var i__9175_9812 = (0);
while(true){
if((i__9175_9812 < count__9174_9811)){
var map__9185_9813 = chunk__9173_9810.cljs$core$IIndexed$_nth$arity$2(null,i__9175_9812);
var map__9185_9814__$1 = cljs.core.__destructure_map(map__9185_9813);
var plugin_9815 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9185_9814__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
try{plugin_9815.destroy();
}catch (e9186){var __9818 = e9186;
}

var G__9819 = seq__9172_9809;
var G__9820 = chunk__9173_9810;
var G__9821 = count__9174_9811;
var G__9822 = (i__9175_9812 + (1));
seq__9172_9809 = G__9819;
chunk__9173_9810 = G__9820;
count__9174_9811 = G__9821;
i__9175_9812 = G__9822;
continue;
} else {
var temp__5804__auto___9823__$1 = cljs.core.seq(seq__9172_9809);
if(temp__5804__auto___9823__$1){
var seq__9172_9824__$1 = temp__5804__auto___9823__$1;
if(cljs.core.chunked_seq_QMARK_(seq__9172_9824__$1)){
var c__5525__auto___9829 = cljs.core.chunk_first(seq__9172_9824__$1);
var G__9830 = cljs.core.chunk_rest(seq__9172_9824__$1);
var G__9831 = c__5525__auto___9829;
var G__9832 = cljs.core.count(c__5525__auto___9829);
var G__9833 = (0);
seq__9172_9809 = G__9830;
chunk__9173_9810 = G__9831;
count__9174_9811 = G__9832;
i__9175_9812 = G__9833;
continue;
} else {
var map__9188_9837 = cljs.core.first(seq__9172_9824__$1);
var map__9188_9838__$1 = cljs.core.__destructure_map(map__9188_9837);
var plugin_9839 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9188_9838__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
try{plugin_9839.destroy();
}catch (e9190){var __9841 = e9190;
}

var G__9842 = cljs.core.next(seq__9172_9824__$1);
var G__9843 = null;
var G__9844 = (0);
var G__9845 = (0);
seq__9172_9809 = G__9842;
chunk__9173_9810 = G__9843;
count__9174_9811 = G__9844;
i__9175_9812 = G__9845;
continue;
}
} else {
}
}
break;
}

cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.audio.track_nodes,cljs.core.assoc_in,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [track_name,new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234)], null),cljs.core.PersistentVector.EMPTY);

return repulse.fx.rewire_track_BANG_(track_name);
} else {
return null;
}
});
/**
 * Set a parameter on a named effect in a track's chain.
 */
repulse.fx.set_track_param_BANG_ = (function repulse$fx$set_track_param_BANG_(track_name,effect_name,param_name,value){
var temp__5804__auto__ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.audio.track_nodes),track_name);
if(cljs.core.truth_(temp__5804__auto__)){
var tn = temp__5804__auto__;
var temp__5804__auto____$1 = cljs.core.some((function (p1__9192_SHARP_){
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(effect_name,new cljs.core.Keyword(null,"name","name",1843675177).cljs$core$IFn$_invoke$arity$1(p1__9192_SHARP_))){
return p1__9192_SHARP_;
} else {
return null;
}
}),new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234).cljs$core$IFn$_invoke$arity$1(tn));
if(cljs.core.truth_(temp__5804__auto____$1)){
var entry = temp__5804__auto____$1;
return new cljs.core.Keyword(null,"plugin","plugin",-1688841923).cljs$core$IFn$_invoke$arity$1(entry).setParam(param_name,value);
} else {
return null;
}
} else {
return null;
}
});
/**
 * Bypass or un-bypass an effect on a specific track.
 */
repulse.fx.bypass_track_effect_BANG_ = (function repulse$fx$bypass_track_effect_BANG_(track_name,effect_name,enabled){
var temp__5804__auto__ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.audio.track_nodes),track_name);
if(cljs.core.truth_(temp__5804__auto__)){
var tn = temp__5804__auto__;
var temp__5804__auto____$1 = cljs.core.some((function (p1__9194_SHARP_){
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(effect_name,new cljs.core.Keyword(null,"name","name",1843675177).cljs$core$IFn$_invoke$arity$1(p1__9194_SHARP_))){
return p1__9194_SHARP_;
} else {
return null;
}
}),new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234).cljs$core$IFn$_invoke$arity$1(tn));
if(cljs.core.truth_(temp__5804__auto____$1)){
var entry = temp__5804__auto____$1;
try{new cljs.core.Keyword(null,"plugin","plugin",-1688841923).cljs$core$IFn$_invoke$arity$1(entry).bypass(enabled);
}catch (e9210){var __9866 = e9210;
}
return cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.audio.track_nodes,cljs.core.update_in,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [track_name,new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234)], null),(function (c){
return cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__9195_SHARP_){
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(effect_name,new cljs.core.Keyword(null,"name","name",1843675177).cljs$core$IFn$_invoke$arity$1(p1__9195_SHARP_))){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(p1__9195_SHARP_,new cljs.core.Keyword(null,"bypassed?","bypassed?",132826625),enabled);
} else {
return p1__9195_SHARP_;
}
}),c);
}));
} else {
return null;
}
} else {
return null;
}
});
/**
 * Extract the sound name from an event value for plugin matching.
 */
repulse.fx.event_name = (function repulse$fx$event_name(value){
if((value instanceof cljs.core.Keyword)){
return cljs.core.name(value);
} else {
if(cljs.core.truth_((function (){var and__5000__auto__ = cljs.core.map_QMARK_(value);
if(and__5000__auto__){
return new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value);
} else {
return and__5000__auto__;
}
})())){
var n = new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(value);
if((n instanceof cljs.core.Keyword)){
return cljs.core.name(n);
} else {
return null;
}
} else {
if(cljs.core.truth_((function (){var and__5000__auto__ = cljs.core.map_QMARK_(value);
if(and__5000__auto__){
return new cljs.core.Keyword(null,"synth","synth",-862700847).cljs$core$IFn$_invoke$arity$1(value);
} else {
return and__5000__auto__;
}
})())){
return cljs.core.name(new cljs.core.Keyword(null,"synth","synth",-862700847).cljs$core$IFn$_invoke$arity$1(value));
} else {
return null;

}
}
}
});
/**
 * Notify all plugins (global and per-track) that implement onEvent of a fired event.
 * Called via :on-fx-event in the scheduler — fx → audio dependency is one-way.
 */
repulse.fx.notify_fx_event_BANG_ = (function repulse$fx$notify_fx_event_BANG_(value,t){
var temp__5804__auto__ = repulse.fx.event_name(value);
if(cljs.core.truth_(temp__5804__auto__)){
var evt = temp__5804__auto__;
var seq__9263_9880 = cljs.core.seq(cljs.core.deref(repulse.fx.chain));
var chunk__9264_9881 = null;
var count__9265_9882 = (0);
var i__9266_9883 = (0);
while(true){
if((i__9266_9883 < count__9265_9882)){
var map__9293_9884 = chunk__9264_9881.cljs$core$IIndexed$_nth$arity$2(null,i__9266_9883);
var map__9293_9885__$1 = cljs.core.__destructure_map(map__9293_9884);
var plugin_9886 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9293_9885__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
if(cljs.core.truth_(plugin_9886.onEvent)){
try{plugin_9886.onEvent(evt,t);
}catch (e9294){var __9887 = e9294;
}} else {
}


var G__9888 = seq__9263_9880;
var G__9889 = chunk__9264_9881;
var G__9890 = count__9265_9882;
var G__9891 = (i__9266_9883 + (1));
seq__9263_9880 = G__9888;
chunk__9264_9881 = G__9889;
count__9265_9882 = G__9890;
i__9266_9883 = G__9891;
continue;
} else {
var temp__5804__auto___9892__$1 = cljs.core.seq(seq__9263_9880);
if(temp__5804__auto___9892__$1){
var seq__9263_9893__$1 = temp__5804__auto___9892__$1;
if(cljs.core.chunked_seq_QMARK_(seq__9263_9893__$1)){
var c__5525__auto___9920 = cljs.core.chunk_first(seq__9263_9893__$1);
var G__9921 = cljs.core.chunk_rest(seq__9263_9893__$1);
var G__9922 = c__5525__auto___9920;
var G__9923 = cljs.core.count(c__5525__auto___9920);
var G__9924 = (0);
seq__9263_9880 = G__9921;
chunk__9264_9881 = G__9922;
count__9265_9882 = G__9923;
i__9266_9883 = G__9924;
continue;
} else {
var map__9307_9925 = cljs.core.first(seq__9263_9893__$1);
var map__9307_9926__$1 = cljs.core.__destructure_map(map__9307_9925);
var plugin_9927 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9307_9926__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
if(cljs.core.truth_(plugin_9927.onEvent)){
try{plugin_9927.onEvent(evt,t);
}catch (e9312){var __9929 = e9312;
}} else {
}


var G__9930 = cljs.core.next(seq__9263_9893__$1);
var G__9931 = null;
var G__9932 = (0);
var G__9933 = (0);
seq__9263_9880 = G__9930;
chunk__9264_9881 = G__9931;
count__9265_9882 = G__9932;
i__9266_9883 = G__9933;
continue;
}
} else {
}
}
break;
}

var seq__9313 = cljs.core.seq(cljs.core.deref(repulse.audio.track_nodes));
var chunk__9314 = null;
var count__9315 = (0);
var i__9316 = (0);
while(true){
if((i__9316 < count__9315)){
var vec__9479 = chunk__9314.cljs$core$IIndexed$_nth$arity$2(null,i__9316);
var _ = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9479,(0),null);
var map__9482 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9479,(1),null);
var map__9482__$1 = cljs.core.__destructure_map(map__9482);
var fx_chain = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9482__$1,new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234));
var seq__9483_9934 = cljs.core.seq(fx_chain);
var chunk__9484_9935 = null;
var count__9485_9936 = (0);
var i__9486_9937 = (0);
while(true){
if((i__9486_9937 < count__9485_9936)){
var map__9502_9938 = chunk__9484_9935.cljs$core$IIndexed$_nth$arity$2(null,i__9486_9937);
var map__9502_9939__$1 = cljs.core.__destructure_map(map__9502_9938);
var plugin_9940 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9502_9939__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
if(cljs.core.truth_(plugin_9940.onEvent)){
try{plugin_9940.onEvent(evt,t);
}catch (e9503){var __9941__$1 = e9503;
}} else {
}


var G__9942 = seq__9483_9934;
var G__9943 = chunk__9484_9935;
var G__9944 = count__9485_9936;
var G__9945 = (i__9486_9937 + (1));
seq__9483_9934 = G__9942;
chunk__9484_9935 = G__9943;
count__9485_9936 = G__9944;
i__9486_9937 = G__9945;
continue;
} else {
var temp__5804__auto___9946__$1 = cljs.core.seq(seq__9483_9934);
if(temp__5804__auto___9946__$1){
var seq__9483_9947__$1 = temp__5804__auto___9946__$1;
if(cljs.core.chunked_seq_QMARK_(seq__9483_9947__$1)){
var c__5525__auto___9948 = cljs.core.chunk_first(seq__9483_9947__$1);
var G__9949 = cljs.core.chunk_rest(seq__9483_9947__$1);
var G__9950 = c__5525__auto___9948;
var G__9951 = cljs.core.count(c__5525__auto___9948);
var G__9952 = (0);
seq__9483_9934 = G__9949;
chunk__9484_9935 = G__9950;
count__9485_9936 = G__9951;
i__9486_9937 = G__9952;
continue;
} else {
var map__9509_9953 = cljs.core.first(seq__9483_9947__$1);
var map__9509_9954__$1 = cljs.core.__destructure_map(map__9509_9953);
var plugin_9955 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9509_9954__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
if(cljs.core.truth_(plugin_9955.onEvent)){
try{plugin_9955.onEvent(evt,t);
}catch (e9511){var __9957__$1 = e9511;
}} else {
}


var G__9958 = cljs.core.next(seq__9483_9947__$1);
var G__9959 = null;
var G__9960 = (0);
var G__9961 = (0);
seq__9483_9934 = G__9958;
chunk__9484_9935 = G__9959;
count__9485_9936 = G__9960;
i__9486_9937 = G__9961;
continue;
}
} else {
}
}
break;
}


var G__9962 = seq__9313;
var G__9963 = chunk__9314;
var G__9964 = count__9315;
var G__9965 = (i__9316 + (1));
seq__9313 = G__9962;
chunk__9314 = G__9963;
count__9315 = G__9964;
i__9316 = G__9965;
continue;
} else {
var temp__5804__auto____$1 = cljs.core.seq(seq__9313);
if(temp__5804__auto____$1){
var seq__9313__$1 = temp__5804__auto____$1;
if(cljs.core.chunked_seq_QMARK_(seq__9313__$1)){
var c__5525__auto__ = cljs.core.chunk_first(seq__9313__$1);
var G__9966 = cljs.core.chunk_rest(seq__9313__$1);
var G__9967 = c__5525__auto__;
var G__9968 = cljs.core.count(c__5525__auto__);
var G__9969 = (0);
seq__9313 = G__9966;
chunk__9314 = G__9967;
count__9315 = G__9968;
i__9316 = G__9969;
continue;
} else {
var vec__9517 = cljs.core.first(seq__9313__$1);
var _ = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9517,(0),null);
var map__9520 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__9517,(1),null);
var map__9520__$1 = cljs.core.__destructure_map(map__9520);
var fx_chain = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9520__$1,new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234));
var seq__9522_9970 = cljs.core.seq(fx_chain);
var chunk__9523_9971 = null;
var count__9524_9972 = (0);
var i__9525_9973 = (0);
while(true){
if((i__9525_9973 < count__9524_9972)){
var map__9550_9974 = chunk__9523_9971.cljs$core$IIndexed$_nth$arity$2(null,i__9525_9973);
var map__9550_9975__$1 = cljs.core.__destructure_map(map__9550_9974);
var plugin_9976 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9550_9975__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
if(cljs.core.truth_(plugin_9976.onEvent)){
try{plugin_9976.onEvent(evt,t);
}catch (e9554){var __9977__$1 = e9554;
}} else {
}


var G__9978 = seq__9522_9970;
var G__9979 = chunk__9523_9971;
var G__9980 = count__9524_9972;
var G__9981 = (i__9525_9973 + (1));
seq__9522_9970 = G__9978;
chunk__9523_9971 = G__9979;
count__9524_9972 = G__9980;
i__9525_9973 = G__9981;
continue;
} else {
var temp__5804__auto___9982__$2 = cljs.core.seq(seq__9522_9970);
if(temp__5804__auto___9982__$2){
var seq__9522_9983__$1 = temp__5804__auto___9982__$2;
if(cljs.core.chunked_seq_QMARK_(seq__9522_9983__$1)){
var c__5525__auto___9985 = cljs.core.chunk_first(seq__9522_9983__$1);
var G__9986 = cljs.core.chunk_rest(seq__9522_9983__$1);
var G__9987 = c__5525__auto___9985;
var G__9988 = cljs.core.count(c__5525__auto___9985);
var G__9989 = (0);
seq__9522_9970 = G__9986;
chunk__9523_9971 = G__9987;
count__9524_9972 = G__9988;
i__9525_9973 = G__9989;
continue;
} else {
var map__9592_9990 = cljs.core.first(seq__9522_9983__$1);
var map__9592_9991__$1 = cljs.core.__destructure_map(map__9592_9990);
var plugin_9992 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__9592_9991__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
if(cljs.core.truth_(plugin_9992.onEvent)){
try{plugin_9992.onEvent(evt,t);
}catch (e9602){var __9994__$1 = e9602;
}} else {
}


var G__9995 = cljs.core.next(seq__9522_9983__$1);
var G__9996 = null;
var G__9997 = (0);
var G__9998 = (0);
seq__9522_9970 = G__9995;
chunk__9523_9971 = G__9996;
count__9524_9972 = G__9997;
i__9525_9973 = G__9998;
continue;
}
} else {
}
}
break;
}


var G__9999 = cljs.core.next(seq__9313__$1);
var G__10000 = null;
var G__10001 = (0);
var G__10002 = (0);
seq__9313 = G__9999;
chunk__9314 = G__10000;
count__9315 = G__10001;
i__9316 = G__10002;
continue;
}
} else {
return null;
}
}
break;
}
} else {
return null;
}
});

//# sourceMappingURL=repulse.fx.js.map
