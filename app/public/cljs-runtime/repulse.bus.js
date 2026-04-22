goog.provide('repulse.bus');
if((typeof repulse !== 'undefined') && (typeof repulse.bus !== 'undefined') && (typeof repulse.bus.bus_nodes !== 'undefined')){
} else {
repulse.bus.bus_nodes = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
}
if((typeof repulse !== 'undefined') && (typeof repulse.bus !== 'undefined') && (typeof repulse.bus.synth_bus_writers !== 'undefined')){
} else {
repulse.bus.synth_bus_writers = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
}
/**
 * Create (or recreate) a named bus.
 * type is :control (default) or :audio.
 */
repulse.bus.create_bus_BANG_ = (function repulse$bus$create_bus_BANG_(ac,name,type){
var temp__5804__auto___7791 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.bus.bus_nodes),name);
if(cljs.core.truth_(temp__5804__auto___7791)){
var existing_7792 = temp__5804__auto___7791;
var node_7793 = new cljs.core.Keyword(null,"node","node",581201198).cljs$core$IFn$_invoke$arity$1(existing_7792);
try{if(cljs.core.truth_(node_7793.stop)){
node_7793.stop();
} else {
}
}catch (e7546){var __7795 = e7546;
}
try{node_7793.disconnect();
}catch (e7547){var __7798 = e7547;
}} else {
}

var node = ((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(type,new cljs.core.Keyword(null,"audio","audio",1819127321)))?(function (){var g = ac.createGain();
g.gain.setValueAtTime(1.0,ac.currentTime);

return g;
})():(function (){var cs = ac.createConstantSource();
cs.offset.setValueAtTime(0.0,ac.currentTime);

cs.start();

return cs;
})());
return cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.bus.bus_nodes,cljs.core.assoc,name,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"type","type",1174270348),(function (){var or__5002__auto__ = type;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return new cljs.core.Keyword(null,"control","control",1892578036);
}
})(),new cljs.core.Keyword(null,"node","node",581201198),node], null));
});
/**
 * Return the AudioNode backing a bus, or nil if it does not exist.
 */
repulse.bus.get_bus_node = (function repulse$bus$get_bus_node(name){
return new cljs.core.Keyword(null,"node","node",581201198).cljs$core$IFn$_invoke$arity$1(cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.bus.bus_nodes),name));
});
/**
 * Return :audio or :control for a named bus, or nil if it does not exist.
 */
repulse.bus.get_bus_type = (function repulse$bus$get_bus_type(name){
return new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.bus.bus_nodes),name));
});
/**
 * Connect `writer-node` to the named bus.
 * For control buses, connects to the ConstantSourceNode's .offset AudioParam.
 * For audio buses, connects to the GainNode input.
 */
repulse.bus.connect_to_bus_BANG_ = (function repulse$bus$connect_to_bus_BANG_(bus_name,writer_node){
var temp__5804__auto__ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.bus.bus_nodes),bus_name);
if(cljs.core.truth_(temp__5804__auto__)){
var bus = temp__5804__auto__;
var node = new cljs.core.Keyword(null,"node","node",581201198).cljs$core$IFn$_invoke$arity$1(bus);
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(bus),new cljs.core.Keyword(null,"control","control",1892578036))){
return writer_node.connect(node.offset);
} else {
return writer_node.connect(node);
}
} else {
return null;
}
});
/**
 * Start a new writer connection for `synth-name` → `bus-name`, stopping
 * the previous instance of this synth on that bus (if any).
 * This prevents oscillator accumulation when a synth fires once per cycle.
 */
repulse.bus.replace_synth_writer_BANG_ = (function repulse$bus$replace_synth_writer_BANG_(synth_name,bus_name,writer_node){
var temp__5804__auto___7818 = cljs.core.get_in.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.bus.synth_bus_writers),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [synth_name,bus_name], null));
if(cljs.core.truth_(temp__5804__auto___7818)){
var old_7823 = temp__5804__auto___7818;
try{if(cljs.core.truth_(old_7823.stop)){
old_7823.stop((0));
} else {
}
}catch (e7596){var __7824 = e7596;
}
try{old_7823.disconnect();
}catch (e7597){var __7825 = e7597;
}} else {
}

cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.bus.synth_bus_writers,cljs.core.assoc_in,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [synth_name,bus_name], null),writer_node);

return repulse.bus.connect_to_bus_BANG_(bus_name,writer_node);
});
/**
 * Stop and disconnect all bus nodes and all tracked synth writers.
 * Called by audio/stop! and audio/clear-track! when all tracks are removed.
 */
repulse.bus.cleanup_all_BANG_ = (function repulse$bus$cleanup_all_BANG_(){
var seq__7604_7826 = cljs.core.seq(cljs.core.deref(repulse.bus.synth_bus_writers));
var chunk__7609_7827 = null;
var count__7610_7828 = (0);
var i__7611_7829 = (0);
while(true){
if((i__7611_7829 < count__7610_7828)){
var vec__7675_7830 = chunk__7609_7827.cljs$core$IIndexed$_nth$arity$2(null,i__7611_7829);
var __7831 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7675_7830,(0),null);
var bus_map_7832 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7675_7830,(1),null);
var seq__7612_7833 = cljs.core.seq(bus_map_7832);
var chunk__7613_7834 = null;
var count__7614_7835 = (0);
var i__7615_7836 = (0);
while(true){
if((i__7615_7836 < count__7614_7835)){
var vec__7700_7839 = chunk__7613_7834.cljs$core$IIndexed$_nth$arity$2(null,i__7615_7836);
var __7840__$1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7700_7839,(0),null);
var node_7841 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7700_7839,(1),null);
try{if(cljs.core.truth_(node_7841.stop)){
node_7841.stop((0));
} else {
}
}catch (e7703){var __7845__$2 = e7703;
}
try{node_7841.disconnect();
}catch (e7707){var __7847__$2 = e7707;
}

var G__7848 = seq__7612_7833;
var G__7849 = chunk__7613_7834;
var G__7850 = count__7614_7835;
var G__7851 = (i__7615_7836 + (1));
seq__7612_7833 = G__7848;
chunk__7613_7834 = G__7849;
count__7614_7835 = G__7850;
i__7615_7836 = G__7851;
continue;
} else {
var temp__5804__auto___7852 = cljs.core.seq(seq__7612_7833);
if(temp__5804__auto___7852){
var seq__7612_7854__$1 = temp__5804__auto___7852;
if(cljs.core.chunked_seq_QMARK_(seq__7612_7854__$1)){
var c__5525__auto___7858 = cljs.core.chunk_first(seq__7612_7854__$1);
var G__7865 = cljs.core.chunk_rest(seq__7612_7854__$1);
var G__7866 = c__5525__auto___7858;
var G__7867 = cljs.core.count(c__5525__auto___7858);
var G__7868 = (0);
seq__7612_7833 = G__7865;
chunk__7613_7834 = G__7866;
count__7614_7835 = G__7867;
i__7615_7836 = G__7868;
continue;
} else {
var vec__7710_7869 = cljs.core.first(seq__7612_7854__$1);
var __7870__$1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7710_7869,(0),null);
var node_7871 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7710_7869,(1),null);
try{if(cljs.core.truth_(node_7871.stop)){
node_7871.stop((0));
} else {
}
}catch (e7713){var __7872__$2 = e7713;
}
try{node_7871.disconnect();
}catch (e7714){var __7873__$2 = e7714;
}

var G__7874 = cljs.core.next(seq__7612_7854__$1);
var G__7875 = null;
var G__7876 = (0);
var G__7877 = (0);
seq__7612_7833 = G__7874;
chunk__7613_7834 = G__7875;
count__7614_7835 = G__7876;
i__7615_7836 = G__7877;
continue;
}
} else {
}
}
break;
}

var G__7878 = seq__7604_7826;
var G__7879 = chunk__7609_7827;
var G__7880 = count__7610_7828;
var G__7881 = (i__7611_7829 + (1));
seq__7604_7826 = G__7878;
chunk__7609_7827 = G__7879;
count__7610_7828 = G__7880;
i__7611_7829 = G__7881;
continue;
} else {
var temp__5804__auto___7882 = cljs.core.seq(seq__7604_7826);
if(temp__5804__auto___7882){
var seq__7604_7883__$1 = temp__5804__auto___7882;
if(cljs.core.chunked_seq_QMARK_(seq__7604_7883__$1)){
var c__5525__auto___7885 = cljs.core.chunk_first(seq__7604_7883__$1);
var G__7886 = cljs.core.chunk_rest(seq__7604_7883__$1);
var G__7887 = c__5525__auto___7885;
var G__7888 = cljs.core.count(c__5525__auto___7885);
var G__7889 = (0);
seq__7604_7826 = G__7886;
chunk__7609_7827 = G__7887;
count__7610_7828 = G__7888;
i__7611_7829 = G__7889;
continue;
} else {
var vec__7722_7890 = cljs.core.first(seq__7604_7883__$1);
var __7891 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7722_7890,(0),null);
var bus_map_7892 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7722_7890,(1),null);
var seq__7605_7893 = cljs.core.seq(bus_map_7892);
var chunk__7606_7894 = null;
var count__7607_7895 = (0);
var i__7608_7896 = (0);
while(true){
if((i__7608_7896 < count__7607_7895)){
var vec__7735_7898 = chunk__7606_7894.cljs$core$IIndexed$_nth$arity$2(null,i__7608_7896);
var __7899__$1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7735_7898,(0),null);
var node_7900 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7735_7898,(1),null);
try{if(cljs.core.truth_(node_7900.stop)){
node_7900.stop((0));
} else {
}
}catch (e7738){var __7904__$2 = e7738;
}
try{node_7900.disconnect();
}catch (e7739){var __7905__$2 = e7739;
}

var G__7907 = seq__7605_7893;
var G__7908 = chunk__7606_7894;
var G__7909 = count__7607_7895;
var G__7910 = (i__7608_7896 + (1));
seq__7605_7893 = G__7907;
chunk__7606_7894 = G__7908;
count__7607_7895 = G__7909;
i__7608_7896 = G__7910;
continue;
} else {
var temp__5804__auto___7911__$1 = cljs.core.seq(seq__7605_7893);
if(temp__5804__auto___7911__$1){
var seq__7605_7913__$1 = temp__5804__auto___7911__$1;
if(cljs.core.chunked_seq_QMARK_(seq__7605_7913__$1)){
var c__5525__auto___7914 = cljs.core.chunk_first(seq__7605_7913__$1);
var G__7915 = cljs.core.chunk_rest(seq__7605_7913__$1);
var G__7916 = c__5525__auto___7914;
var G__7917 = cljs.core.count(c__5525__auto___7914);
var G__7918 = (0);
seq__7605_7893 = G__7915;
chunk__7606_7894 = G__7916;
count__7607_7895 = G__7917;
i__7608_7896 = G__7918;
continue;
} else {
var vec__7742_7919 = cljs.core.first(seq__7605_7913__$1);
var __7920__$1 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7742_7919,(0),null);
var node_7921 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7742_7919,(1),null);
try{if(cljs.core.truth_(node_7921.stop)){
node_7921.stop((0));
} else {
}
}catch (e7745){var __7925__$2 = e7745;
}
try{node_7921.disconnect();
}catch (e7746){var __7926__$2 = e7746;
}

var G__7928 = cljs.core.next(seq__7605_7913__$1);
var G__7929 = null;
var G__7930 = (0);
var G__7931 = (0);
seq__7605_7893 = G__7928;
chunk__7606_7894 = G__7929;
count__7607_7895 = G__7930;
i__7608_7896 = G__7931;
continue;
}
} else {
}
}
break;
}

var G__7932 = cljs.core.next(seq__7604_7883__$1);
var G__7933 = null;
var G__7934 = (0);
var G__7935 = (0);
seq__7604_7826 = G__7932;
chunk__7609_7827 = G__7933;
count__7610_7828 = G__7934;
i__7611_7829 = G__7935;
continue;
}
} else {
}
}
break;
}

var seq__7753_7936 = cljs.core.seq(cljs.core.deref(repulse.bus.bus_nodes));
var chunk__7754_7937 = null;
var count__7755_7938 = (0);
var i__7756_7939 = (0);
while(true){
if((i__7756_7939 < count__7755_7938)){
var vec__7767_7944 = chunk__7754_7937.cljs$core$IIndexed$_nth$arity$2(null,i__7756_7939);
var __7945 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7767_7944,(0),null);
var bus_7946 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7767_7944,(1),null);
var node_7951 = new cljs.core.Keyword(null,"node","node",581201198).cljs$core$IFn$_invoke$arity$1(bus_7946);
try{if(cljs.core.truth_(node_7951.stop)){
node_7951.stop();
} else {
}
}catch (e7770){var __7952__$1 = e7770;
}
try{node_7951.disconnect();
}catch (e7771){var __7953__$1 = e7771;
}

var G__7954 = seq__7753_7936;
var G__7955 = chunk__7754_7937;
var G__7956 = count__7755_7938;
var G__7957 = (i__7756_7939 + (1));
seq__7753_7936 = G__7954;
chunk__7754_7937 = G__7955;
count__7755_7938 = G__7956;
i__7756_7939 = G__7957;
continue;
} else {
var temp__5804__auto___7958 = cljs.core.seq(seq__7753_7936);
if(temp__5804__auto___7958){
var seq__7753_7959__$1 = temp__5804__auto___7958;
if(cljs.core.chunked_seq_QMARK_(seq__7753_7959__$1)){
var c__5525__auto___7961 = cljs.core.chunk_first(seq__7753_7959__$1);
var G__7962 = cljs.core.chunk_rest(seq__7753_7959__$1);
var G__7963 = c__5525__auto___7961;
var G__7964 = cljs.core.count(c__5525__auto___7961);
var G__7965 = (0);
seq__7753_7936 = G__7962;
chunk__7754_7937 = G__7963;
count__7755_7938 = G__7964;
i__7756_7939 = G__7965;
continue;
} else {
var vec__7772_7966 = cljs.core.first(seq__7753_7959__$1);
var __7967 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7772_7966,(0),null);
var bus_7968 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7772_7966,(1),null);
var node_7972 = new cljs.core.Keyword(null,"node","node",581201198).cljs$core$IFn$_invoke$arity$1(bus_7968);
try{if(cljs.core.truth_(node_7972.stop)){
node_7972.stop();
} else {
}
}catch (e7775){var __7973__$1 = e7775;
}
try{node_7972.disconnect();
}catch (e7776){var __7974__$1 = e7776;
}

var G__7976 = cljs.core.next(seq__7753_7959__$1);
var G__7977 = null;
var G__7978 = (0);
var G__7979 = (0);
seq__7753_7936 = G__7976;
chunk__7754_7937 = G__7977;
count__7755_7938 = G__7978;
i__7756_7939 = G__7979;
continue;
}
} else {
}
}
break;
}

cljs.core.reset_BANG_(repulse.bus.synth_bus_writers,cljs.core.PersistentArrayMap.EMPTY);

return cljs.core.reset_BANG_(repulse.bus.bus_nodes,cljs.core.PersistentArrayMap.EMPTY);
});
/**
 * Return a map of bus-name → {:type :audio|:control} for the context panel.
 */
repulse.bus.active_buses = (function repulse$bus$active_buses(){
return cljs.core.into.cljs$core$IFn$_invoke$arity$2(cljs.core.PersistentArrayMap.EMPTY,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p__7778){
var vec__7779 = p__7778;
var k = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7779,(0),null);
var v = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__7779,(1),null);
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [k,cljs.core.select_keys(v,new cljs.core.PersistentVector(null, 1, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"type","type",1174270348)], null))], null);
}),cljs.core.deref(repulse.bus.bus_nodes)));
});

//# sourceMappingURL=repulse.bus.js.map
