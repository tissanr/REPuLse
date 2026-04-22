goog.provide('repulse.ui.context_panel');
repulse.ui.context_panel.el = (function repulse$ui$context_panel$el(id){
return document.getElementById(id);
});
repulse.ui.context_panel.infer_type = (function repulse$ui$context_panel$infer_type(v){
if(repulse.core.pattern_QMARK_(v)){
return "pattern";
} else {
if(cljs.core.fn_QMARK_(v)){
return "fn";
} else {
if(typeof v === 'number'){
return "number";
} else {
if(typeof v === 'string'){
return "string";
} else {
if((v instanceof cljs.core.Keyword)){
return "keyword";
} else {
return "value";

}
}
}
}
}
});
repulse.ui.context_panel.fmt_pv = (function repulse$ui$context_panel$fmt_pv(v){
if(typeof v === 'number'){
if((v === Math.round(v))){
return cljs.core.str.cljs$core$IFn$_invoke$arity$1((v | (0)));
} else {
return v.toFixed((2));
}
} else {
return cljs.core.str.cljs$core$IFn$_invoke$arity$1(v);
}
});
repulse.ui.context_panel.TRACK_PARAM_KEYS = new cljs.core.PersistentVector(null, 10, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.Keyword(null,"amp","amp",271690571),new cljs.core.Keyword(null,"pan","pan",-307712792),new cljs.core.Keyword(null,"decay","decay",1036712184),new cljs.core.Keyword(null,"attack","attack",1957061788),new cljs.core.Keyword(null,"release","release",-1534371381),new cljs.core.Keyword(null,"synth","synth",-862700847),new cljs.core.Keyword(null,"bank","bank",-1982531798),new cljs.core.Keyword(null,"rate","rate",-1428659698),new cljs.core.Keyword(null,"begin","begin",-319034319),new cljs.core.Keyword(null,"end","end",-268185958)], null);
repulse.ui.context_panel.SLIDER_PARAMS = new cljs.core.PersistentArrayMap(null, 8, [new cljs.core.Keyword(null,"amp","amp",271690571),new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(1),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null),new cljs.core.Keyword(null,"pan","pan",-307712792),new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(-1),new cljs.core.Keyword(null,"max","max",61366548),(1),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null),new cljs.core.Keyword(null,"decay","decay",1036712184),new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(4),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null),new cljs.core.Keyword(null,"attack","attack",1957061788),new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(2),new cljs.core.Keyword(null,"step","step",1288888124),0.001], null),new cljs.core.Keyword(null,"release","release",-1534371381),new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(4),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null),new cljs.core.Keyword(null,"rate","rate",-1428659698),new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),0.1,new cljs.core.Keyword(null,"max","max",61366548),(4),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null),new cljs.core.Keyword(null,"begin","begin",-319034319),new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(1),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null),new cljs.core.Keyword(null,"end","end",-268185958),new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(1),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null)], null);
repulse.ui.context_panel.FX_SLIDER_PARAMS = cljs.core.PersistentHashMap.fromArrays(["reverb","overdrive","tremolo","bitcrusher","sidechain","filter","delay","dattorro-reverb","phaser","compressor","chorus"],[new cljs.core.PersistentArrayMap(null, 1, ["wet",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(1),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null)], null),new cljs.core.PersistentArrayMap(null, 1, ["drive",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(1),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null)], null),new cljs.core.PersistentArrayMap(null, 2, ["depth",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(1),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null),"rate",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),0.1,new cljs.core.Keyword(null,"max","max",61366548),(20),new cljs.core.Keyword(null,"step","step",1288888124),0.1], null)], null),new cljs.core.PersistentArrayMap(null, 1, ["wet",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(1),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null)], null),new cljs.core.PersistentArrayMap(null, 1, ["amount",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(1),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null)], null),new cljs.core.PersistentArrayMap(null, 2, ["freq",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(20),new cljs.core.Keyword(null,"max","max",61366548),(8000),new cljs.core.Keyword(null,"step","step",1288888124),(1)], null),"q",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),0.1,new cljs.core.Keyword(null,"max","max",61366548),(20),new cljs.core.Keyword(null,"step","step",1288888124),0.1], null)], null),new cljs.core.PersistentArrayMap(null, 3, ["time",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(2),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null),"feedback",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),0.95,new cljs.core.Keyword(null,"step","step",1288888124),0.01], null),"wet",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(1),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null)], null),new cljs.core.PersistentArrayMap(null, 1, ["wet",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(1),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null)], null),new cljs.core.PersistentArrayMap(null, 2, ["wet",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(1),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null),"rate",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),0.1,new cljs.core.Keyword(null,"max","max",61366548),(10),new cljs.core.Keyword(null,"step","step",1288888124),0.1], null)], null),new cljs.core.PersistentArrayMap(null, 6, ["wet",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(1),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null),"threshold",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(-60),new cljs.core.Keyword(null,"max","max",61366548),(0),new cljs.core.Keyword(null,"step","step",1288888124),0.5], null),"ratio",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(1),new cljs.core.Keyword(null,"max","max",61366548),(20),new cljs.core.Keyword(null,"step","step",1288888124),0.5], null),"attack",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(1),new cljs.core.Keyword(null,"step","step",1288888124),0.001], null),"release",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(1),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null),"knee",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(40),new cljs.core.Keyword(null,"step","step",1288888124),0.5], null)], null),new cljs.core.PersistentArrayMap(null, 2, ["wet",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),(0),new cljs.core.Keyword(null,"max","max",61366548),(1),new cljs.core.Keyword(null,"step","step",1288888124),0.01], null),"rate",new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"min","min",444991522),0.1,new cljs.core.Keyword(null,"max","max",61366548),(10),new cljs.core.Keyword(null,"step","step",1288888124),0.1], null)], null)]);
repulse.ui.context_panel.FX_PRIMARY_PARAM = cljs.core.PersistentHashMap.fromArrays(["reverb","overdrive","tremolo","bitcrusher","sidechain","filter","delay","dattorro-reverb","phaser","compressor","chorus"],["wet","drive","depth","wet","amount","freq","time","wet","wet","wet","wet"]);
/**
 * Query cycle 0 of a pattern and collect the first value for each known param key.
 */
repulse.ui.context_panel.extract_track_params = (function repulse$ui$context_panel$extract_track_params(pattern){
try{var events = repulse.core.query(pattern,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"start","start",-355208981),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(0),(1)], null),new cljs.core.Keyword(null,"end","end",-268185958),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(1)], null)], null));
var maps = cljs.core.filter.cljs$core$IFn$_invoke$arity$2(cljs.core.map_QMARK_,cljs.core.map.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"value","value",305978217),events));
return cljs.core.reduce.cljs$core$IFn$_invoke$arity$3((function (acc,m){
return cljs.core.reduce.cljs$core$IFn$_invoke$arity$3((function (a,k){
if(((cljs.core.contains_QMARK_(m,k)) && ((!(cljs.core.contains_QMARK_(a,k)))))){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(a,k,cljs.core.get.cljs$core$IFn$_invoke$arity$2(m,k));
} else {
return a;
}
}),acc,repulse.ui.context_panel.TRACK_PARAM_KEYS);
}),cljs.core.PersistentArrayMap.EMPTY,maps);
}catch (e10710){var _ = e10710;
return cljs.core.PersistentArrayMap.EMPTY;
}});
repulse.ui.context_panel.render_track_slider = (function repulse$ui$context_panel$render_track_slider(track_name,param_key,value){
var temp__5804__auto__ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(repulse.ui.context_panel.SLIDER_PARAMS,param_key);
if(cljs.core.truth_(temp__5804__auto__)){
var map__10729 = temp__5804__auto__;
var map__10729__$1 = cljs.core.__destructure_map(map__10729);
var min = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10729__$1,new cljs.core.Keyword(null,"min","min",444991522));
var max = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10729__$1,new cljs.core.Keyword(null,"max","max",61366548));
var step = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10729__$1,new cljs.core.Keyword(null,"step","step",1288888124));
var tn = cljs.core.name(track_name);
var pn = cljs.core.name(param_key);
return ["<div class=\"ctx-slider-row\">","<label class=\"ctx-param-key\">",pn,"</label>","<input type=\"range\" class=\"ctx-slider\""," data-track=\"",tn,"\""," data-param=\"",pn,"\""," min=\"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(min),"\" max=\"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(max),"\" step=\"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(step),"\""," value=\"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(value),"\">","<span class=\"ctx-param-val\">",cljs.core.str.cljs$core$IFn$_invoke$arity$1(repulse.ui.context_panel.fmt_pv(value)),"</span>","</div>"].join('');
} else {
return null;
}
});
repulse.ui.context_panel.render_fx_slider = (function repulse$ui$context_panel$render_fx_slider(effect_name,param_name,value){
var temp__5804__auto__ = cljs.core.get_in.cljs$core$IFn$_invoke$arity$2(repulse.ui.context_panel.FX_SLIDER_PARAMS,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [effect_name,param_name], null));
if(cljs.core.truth_(temp__5804__auto__)){
var map__10755 = temp__5804__auto__;
var map__10755__$1 = cljs.core.__destructure_map(map__10755);
var min = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10755__$1,new cljs.core.Keyword(null,"min","min",444991522));
var max = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10755__$1,new cljs.core.Keyword(null,"max","max",61366548));
var step = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10755__$1,new cljs.core.Keyword(null,"step","step",1288888124));
return ["<div class=\"ctx-slider-row\">","<label class=\"ctx-param-key\">",cljs.core.str.cljs$core$IFn$_invoke$arity$1(param_name),"</label>","<input type=\"range\" class=\"ctx-slider\""," data-fx=\"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(effect_name),"\""," data-param=\"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(param_name),"\""," min=\"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(min),"\" max=\"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(max),"\" step=\"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(step),"\""," value=\"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(value),"\">","<span class=\"ctx-param-val\">",cljs.core.str.cljs$core$IFn$_invoke$arity$1(repulse.ui.context_panel.fmt_pv(value)),"</span>","</div>"].join('');
} else {
return null;
}
});
repulse.ui.context_panel.render_track_fx_slider = (function repulse$ui$context_panel$render_track_fx_slider(track_name,effect_name,param_name,value){
var temp__5804__auto__ = cljs.core.get_in.cljs$core$IFn$_invoke$arity$2(repulse.ui.context_panel.FX_SLIDER_PARAMS,new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [effect_name,param_name], null));
if(cljs.core.truth_(temp__5804__auto__)){
var map__10761 = temp__5804__auto__;
var map__10761__$1 = cljs.core.__destructure_map(map__10761);
var min = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10761__$1,new cljs.core.Keyword(null,"min","min",444991522));
var max = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10761__$1,new cljs.core.Keyword(null,"max","max",61366548));
var step = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10761__$1,new cljs.core.Keyword(null,"step","step",1288888124));
return ["<div class=\"ctx-slider-row\">","<label class=\"ctx-param-key\">",cljs.core.str.cljs$core$IFn$_invoke$arity$1(param_name),"</label>","<input type=\"range\" class=\"ctx-slider\""," data-track=\"",cljs.core.name(track_name),"\""," data-fx=\"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(effect_name),"\""," data-param=\"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(param_name),"\""," min=\"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(min),"\" max=\"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(max),"\" step=\"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(step),"\""," value=\"",cljs.core.str.cljs$core$IFn$_invoke$arity$1(value),"\">","<span class=\"ctx-param-val\">",cljs.core.str.cljs$core$IFn$_invoke$arity$1(repulse.ui.context_panel.fmt_pv(value)),"</span>","</div>"].join('');
} else {
return null;
}
});
repulse.ui.context_panel.render_track_fx_subsection = (function repulse$ui$context_panel$render_track_fx_subsection(track_name){
var temp__5804__auto__ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(cljs.core.deref(repulse.audio.track_nodes),track_name);
if(cljs.core.truth_(temp__5804__auto__)){
var tn = temp__5804__auto__;
var active_fx = cljs.core.filterv((function (p1__10763_SHARP_){
return cljs.core.not(new cljs.core.Keyword(null,"bypassed?","bypassed?",132826625).cljs$core$IFn$_invoke$arity$1(p1__10763_SHARP_));
}),new cljs.core.Keyword(null,"fx-chain","fx-chain",-828419234).cljs$core$IFn$_invoke$arity$1(tn));
if(cljs.core.seq(active_fx)){
return ["<details open class=\"ctx-track-fx\">","<summary class=\"ctx-track-fx-title\">fx (",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.count(active_fx)),")</summary>",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p__10797){
var map__10798 = p__10797;
var map__10798__$1 = cljs.core.__destructure_map(map__10798);
var name = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10798__$1,new cljs.core.Keyword(null,"name","name",1843675177));
var plugin = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__10798__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
var params = (function (){try{return cljs.core.js__GT_clj.cljs$core$IFn$_invoke$arity$1(plugin.getParams());
}catch (e10803){var _ = e10803;
return cljs.core.PersistentArrayMap.EMPTY;
}})();
var fx_sliders = cljs.core.get.cljs$core$IFn$_invoke$arity$2(repulse.ui.context_panel.FX_SLIDER_PARAMS,name);
var sliders = (cljs.core.truth_(fx_sliders)?cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,cljs.core.keep.cljs$core$IFn$_invoke$arity$2((function (p__10811){
var vec__10813 = p__10811;
var pname = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10813,(0),null);
var _ = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10813,(1),null);
var temp__5804__auto____$1 = cljs.core.get.cljs$core$IFn$_invoke$arity$2(params,pname);
if(cljs.core.truth_(temp__5804__auto____$1)){
var v = temp__5804__auto____$1;
return repulse.ui.context_panel.render_track_fx_slider(track_name,name,pname,v);
} else {
return null;
}
}),fx_sliders)):null);
return ["<div class=\"ctx-fx-row\">","<span class=\"ctx-fx-name\">",cljs.core.str.cljs$core$IFn$_invoke$arity$1(name),"</span>","</div>",cljs.core.str.cljs$core$IFn$_invoke$arity$1((function (){var or__5002__auto__ = sliders;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return "";
}
})())].join('');
}),active_fx))),"</details>"].join('');
} else {
return null;
}
} else {
return null;
}
});
repulse.ui.context_panel.render_status_section = (function repulse$ui$context_panel$render_status_section(){
var bpm = Math.round((240.0 / new cljs.core.Keyword(null,"cycle-dur","cycle-dur",1241813230).cljs$core$IFn$_invoke$arity$1(cljs.core.deref(repulse.audio.scheduler_state))));
var playing_QMARK_ = repulse.audio.playing_QMARK_();
var backend = (cljs.core.truth_(cljs.core.deref(repulse.audio.worklet_ready_QMARK_))?"[wasm]":"[js]");
var pfx = cljs.core.deref(repulse.samples.active_bank_prefix);
return ["<div class=\"ctx-status\">","<span class=\"ctx-bpm\">",cljs.core.str.cljs$core$IFn$_invoke$arity$1(bpm)," BPM</span>","<span class=\"ctx-backend\">",backend,"</span>",(cljs.core.truth_(pfx)?["<span class=\"ctx-bank\">",cljs.core.str.cljs$core$IFn$_invoke$arity$1(pfx),"</span>"].join(''):null),"<span class=\"",(cljs.core.truth_(playing_QMARK_)?"ctx-playing":"ctx-stopped"),"\">",(cljs.core.truth_(playing_QMARK_)?"&#9679; playing":"&#9675; stopped"),"</span>","</div>"].join('');
});
repulse.ui.context_panel.render_tracks_section = (function repulse$ui$context_panel$render_tracks_section(){
var state = cljs.core.deref(repulse.audio.scheduler_state);
var tracks = new cljs.core.Keyword(null,"tracks","tracks",-326768501).cljs$core$IFn$_invoke$arity$1(state);
var muted = new cljs.core.Keyword(null,"muted","muted",1275109029).cljs$core$IFn$_invoke$arity$1(state);
if(cljs.core.seq(tracks)){
var n_active = (cljs.core.count(tracks) - cljs.core.count(muted));
var solo_track = (((((cljs.core.count(tracks) > (1))) && (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(n_active,(1)))))?cljs.core.first(cljs.core.remove.cljs$core$IFn$_invoke$arity$2((function (p1__10852_SHARP_){
return cljs.core.contains_QMARK_(muted,p1__10852_SHARP_);
}),cljs.core.keys(tracks))):null);
return ["<div class=\"ctx-section\">","<div class=\"ctx-section-title\">Tracks</div>",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p__10969){
var vec__10970 = p__10969;
var track_name = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10970,(0),null);
var pattern = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__10970,(1),null);
var muted_QMARK_ = cljs.core.contains_QMARK_(muted,track_name);
var solo_QMARK_ = cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(track_name,solo_track);
var icon = ((muted_QMARK_)?"&#9632;":((solo_QMARK_)?"&#9733;":"&#9654;"
));
var params = ((muted_QMARK_)?null:repulse.ui.context_panel.extract_track_params(pattern));
var text_pkeys = cljs.core.filter.cljs$core$IFn$_invoke$arity$2((function (p1__10859_SHARP_){
return ((cljs.core.contains_QMARK_(params,p1__10859_SHARP_)) && ((((!(cljs.core.contains_QMARK_(repulse.ui.context_panel.SLIDER_PARAMS,p1__10859_SHARP_)))) || ((!(typeof cljs.core.get.cljs$core$IFn$_invoke$arity$2(params,p1__10859_SHARP_) === 'number'))))));
}),repulse.ui.context_panel.TRACK_PARAM_KEYS);
var slider_pkeys = cljs.core.filter.cljs$core$IFn$_invoke$arity$2((function (p1__10860_SHARP_){
return ((cljs.core.contains_QMARK_(params,p1__10860_SHARP_)) && (((cljs.core.contains_QMARK_(repulse.ui.context_panel.SLIDER_PARAMS,p1__10860_SHARP_)) && (typeof cljs.core.get.cljs$core$IFn$_invoke$arity$2(params,p1__10860_SHARP_) === 'number'))));
}),repulse.ui.context_panel.TRACK_PARAM_KEYS);
return ["<div class=\"ctx-track",((muted_QMARK_)?" ctx-track-muted":null),"\">","<span class=\"ctx-track-icon\">",icon,"</span>","<span class=\"ctx-track-name\">:",cljs.core.name(track_name),"</span>",((muted_QMARK_)?"<span class=\"ctx-track-status\">(muted)</span>":((solo_QMARK_)?"<span class=\"ctx-track-status ctx-track-solo\">(solo)</span>":((cljs.core.seq(text_pkeys))?["<span class=\"ctx-track-params\">",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (k){
return ["<span class=\"ctx-param-key\">",cljs.core.name(k)," </span>","<span class=\"ctx-param-val\">",cljs.core.str.cljs$core$IFn$_invoke$arity$1(repulse.ui.context_panel.fmt_pv(cljs.core.get.cljs$core$IFn$_invoke$arity$2(params,k))),"</span>"," "].join('');
}),text_pkeys))),"</span>"].join(''):""
))),"</div>",cljs.core.str.cljs$core$IFn$_invoke$arity$1((((((!(muted_QMARK_))) && (cljs.core.seq(slider_pkeys))))?cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p1__10867_SHARP_){
return repulse.ui.context_panel.render_track_slider(track_name,p1__10867_SHARP_,cljs.core.get.cljs$core$IFn$_invoke$arity$2(params,p1__10867_SHARP_));
}),slider_pkeys)):null)),repulse.ui.context_panel.render_track_fx_subsection(track_name)].join('');
}),cljs.core.sort_by.cljs$core$IFn$_invoke$arity$2(cljs.core.comp.cljs$core$IFn$_invoke$arity$2(cljs.core.name,cljs.core.first),tracks)))),"</div>"].join('');
} else {
return null;
}
});
repulse.ui.context_panel.render_fx_section = (function repulse$ui$context_panel$render_fx_section(){
var active = cljs.core.filter.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"active?","active?",459499776),cljs.core.deref(repulse.fx.chain));
if(cljs.core.seq(active)){
return ["<div class=\"ctx-section\">","<div class=\"ctx-section-title\">FX</div>",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p__11017){
var map__11018 = p__11017;
var map__11018__$1 = cljs.core.__destructure_map(map__11018);
var name = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11018__$1,new cljs.core.Keyword(null,"name","name",1843675177));
var plugin = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11018__$1,new cljs.core.Keyword(null,"plugin","plugin",-1688841923));
var bypassed_QMARK_ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11018__$1,new cljs.core.Keyword(null,"bypassed?","bypassed?",132826625));
var params = (cljs.core.truth_((function (){var and__5000__auto__ = plugin;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core.not(bypassed_QMARK_);
} else {
return and__5000__auto__;
}
})())?(function (){try{return cljs.core.js__GT_clj.cljs$core$IFn$_invoke$arity$1(plugin.getParams());
}catch (e11019){var _ = e11019;
return cljs.core.PersistentArrayMap.EMPTY;
}})():null);
var fx_sliders = cljs.core.get.cljs$core$IFn$_invoke$arity$2(repulse.ui.context_panel.FX_SLIDER_PARAMS,name);
var slider_html = (cljs.core.truth_((function (){var and__5000__auto__ = cljs.core.not(bypassed_QMARK_);
if(and__5000__auto__){
return fx_sliders;
} else {
return and__5000__auto__;
}
})())?cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,cljs.core.keep.cljs$core$IFn$_invoke$arity$2((function (p__11020){
var vec__11021 = p__11020;
var pname = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__11021,(0),null);
var _ = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__11021,(1),null);
var temp__5804__auto__ = cljs.core.get.cljs$core$IFn$_invoke$arity$2(params,pname);
if(cljs.core.truth_(temp__5804__auto__)){
var v = temp__5804__auto__;
return repulse.ui.context_panel.render_fx_slider(name,pname,v);
} else {
return null;
}
}),fx_sliders)):null);
var first_kv = cljs.core.first(cljs.core.seq(cljs.core.apply.cljs$core$IFn$_invoke$arity$3(cljs.core.dissoc,params,cljs.core.keys(fx_sliders))));
var pstr = (cljs.core.truth_((function (){var and__5000__auto__ = first_kv;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core.not(cljs.core.seq(slider_html));
} else {
return and__5000__auto__;
}
})())?[cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.first(first_kv))," ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(repulse.ui.context_panel.fmt_pv(cljs.core.second(first_kv)))].join(''):null);
return ["<div class=\"ctx-row\">","<span class=\"ctx-name\">",cljs.core.str.cljs$core$IFn$_invoke$arity$1(name),"</span>",(cljs.core.truth_(bypassed_QMARK_)?"<span class=\"ctx-bypass\">off</span>":(cljs.core.truth_(pstr)?["<span class=\"ctx-param\">",pstr,"</span>"].join(''):""
)),"</div>",cljs.core.str.cljs$core$IFn$_invoke$arity$1((function (){var or__5002__auto__ = slider_html;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return "";
}
})())].join('');
}),active))),"</div>"].join('');
} else {
return null;
}
});
repulse.ui.context_panel.render_midi_section = (function repulse$ui$context_panel$render_midi_section(){
var mappings = cljs.core.deref(repulse.midi.cc_mappings);
if(cljs.core.seq(mappings)){
return ["<div class=\"ctx-section\">","<div class=\"ctx-section-title\">MIDI</div>",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p__11035){
var vec__11036 = p__11035;
var cc_num = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__11036,(0),null);
var map__11039 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__11036,(1),null);
var map__11039__$1 = cljs.core.__destructure_map(map__11039);
var target = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11039__$1,new cljs.core.Keyword(null,"target","target",253001721));
return ["<div class=\"ctx-row\">","<span class=\"ctx-name\">CC #",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cc_num),"</span>","<span class=\"ctx-type\">",(cljs.core.truth_(target)?["&#8594; ",cljs.core.name(target)].join(''):null),"</span>","</div>"].join('');
}),cljs.core.sort_by.cljs$core$IFn$_invoke$arity$2(cljs.core.key,mappings)))),"</div>"].join('');
} else {
return null;
}
});
repulse.ui.context_panel.render_sources_section = (function repulse$ui$context_panel$render_sources_section(){
var sources = cljs.core.deref(repulse.samples.loaded_sources);
if(cljs.core.seq(sources)){
return ["<div class=\"ctx-section\">","<div class=\"ctx-section-title\">Sources</div>",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p__11050){
var map__11051 = p__11050;
var map__11051__$1 = cljs.core.__destructure_map(map__11051);
var type = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11051__$1,new cljs.core.Keyword(null,"type","type",1174270348));
var id = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11051__$1,new cljs.core.Keyword(null,"id","id",-1388402092));
var banks = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11051__$1,new cljs.core.Keyword(null,"banks","banks",-1417977624));
var query = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11051__$1,new cljs.core.Keyword(null,"query","query",-1288509510));
var count = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11051__$1,new cljs.core.Keyword(null,"count","count",2139924085));
return ["<div class=\"ctx-source\">&#9835; ",cljs.core.str.cljs$core$IFn$_invoke$arity$1((function (){var G__11069 = type;
var G__11069__$1 = (((G__11069 instanceof cljs.core.Keyword))?G__11069.fqn:null);
switch (G__11069__$1) {
case "github":
return ["github:",cljs.core.str.cljs$core$IFn$_invoke$arity$1(id),((((function (){var or__5002__auto__ = banks;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (0);
}
})() > (0)))?[" (",cljs.core.str.cljs$core$IFn$_invoke$arity$1(banks)," banks)"].join(''):null)].join('');

break;
case "freesound":
return ["freesound: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(query),(cljs.core.truth_(count)?[" (",cljs.core.str.cljs$core$IFn$_invoke$arity$1(count),")"].join(''):null)].join('');

break;
default:
return cljs.core.str.cljs$core$IFn$_invoke$arity$1(id);

}
})()),"</div>"].join('');
}),sources))),"</div>"].join('');
} else {
return null;
}
});
repulse.ui.context_panel.render_buses_section = (function repulse$ui$context_panel$render_buses_section(){
var buses = repulse.bus.active_buses();
if(cljs.core.seq(buses)){
return ["<div class=\"ctx-section\">","<div class=\"ctx-section-title\">Buses</div>",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p__11096){
var vec__11097 = p__11096;
var bus_name = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__11097,(0),null);
var map__11100 = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__11097,(1),null);
var map__11100__$1 = cljs.core.__destructure_map(map__11100);
var type = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__11100__$1,new cljs.core.Keyword(null,"type","type",1174270348));
return ["<div class=\"ctx-row\">","<span class=\"ctx-name\">",cljs.core.str.cljs$core$IFn$_invoke$arity$1(bus_name),"</span>","<span class=\"ctx-type\">",cljs.core.name(type),"</span>","</div>"].join('');
}),cljs.core.sort_by.cljs$core$IFn$_invoke$arity$2(cljs.core.comp.cljs$core$IFn$_invoke$arity$2(cljs.core.name,cljs.core.key),buses)))),"</div>"].join('');
} else {
return null;
}
});
repulse.ui.context_panel.render_bindings_section = (function repulse$ui$context_panel$render_bindings_section(){
var env = (function (){var or__5002__auto__ = cljs.core.deref(repulse.env.builtins.env_atom);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return cljs.core.PersistentArrayMap.EMPTY;
}
})();
var builtin_set = cljs.core.deref(repulse.env.builtins.builtin_names);
var user_defs = cljs.core.sort.cljs$core$IFn$_invoke$arity$1(cljs.core.remove.cljs$core$IFn$_invoke$arity$2(builtin_set,cljs.core.keys(env)));
if(cljs.core.seq(user_defs)){
return ["<div class=\"ctx-section\">","<div class=\"ctx-section-title\">Bindings</div>",cljs.core.str.cljs$core$IFn$_invoke$arity$1(cljs.core.apply.cljs$core$IFn$_invoke$arity$2(cljs.core.str,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (k){
return ["<div class=\"ctx-row\">","<span class=\"ctx-name\">",cljs.core.str.cljs$core$IFn$_invoke$arity$1(k),"</span>","<span class=\"ctx-type\">",repulse.ui.context_panel.infer_type(cljs.core.get.cljs$core$IFn$_invoke$arity$2(env,k)),"</span>","</div>"].join('');
}),user_defs))),"</div>"].join('');
} else {
return null;
}
});
if((typeof repulse !== 'undefined') && (typeof repulse.ui !== 'undefined') && (typeof repulse.ui.context_panel !== 'undefined') && (typeof repulse.ui.context_panel.render_scheduled_QMARK_ !== 'undefined')){
} else {
repulse.ui.context_panel.render_scheduled_QMARK_ = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(false);
}
if((typeof repulse !== 'undefined') && (typeof repulse.ui !== 'undefined') && (typeof repulse.ui.context_panel !== 'undefined') && (typeof repulse.ui.context_panel.slider_active_QMARK_ !== 'undefined')){
} else {
repulse.ui.context_panel.slider_active_QMARK_ = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(false);
}
repulse.ui.context_panel.render_context_panel_BANG_ = (function repulse$ui$context_panel$render_context_panel_BANG_(){
var temp__5804__auto__ = repulse.ui.context_panel.el("context-panel");
if(cljs.core.truth_(temp__5804__auto__)){
var panel_el = temp__5804__auto__;
return (panel_el.innerHTML = [repulse.ui.context_panel.render_status_section(),repulse.ui.context_panel.render_tracks_section(),repulse.ui.context_panel.render_fx_section(),repulse.ui.context_panel.render_midi_section(),repulse.ui.context_panel.render_buses_section(),repulse.ui.context_panel.render_sources_section(),repulse.ui.context_panel.render_bindings_section()].join(''));
} else {
return null;
}
});
/**
 * Request a context panel repaint via RAF, skipping ticks while a slider is held.
 */
repulse.ui.context_panel.schedule_render_BANG_ = (function repulse$ui$context_panel$schedule_render_BANG_(){
if(((cljs.core.not(cljs.core.deref(repulse.ui.context_panel.render_scheduled_QMARK_))) && (cljs.core.not(cljs.core.deref(repulse.ui.context_panel.slider_active_QMARK_))))){
cljs.core.reset_BANG_(repulse.ui.context_panel.render_scheduled_QMARK_,true);

return requestAnimationFrame((function (){
cljs.core.reset_BANG_(repulse.ui.context_panel.render_scheduled_QMARK_,false);

return repulse.ui.context_panel.render_context_panel_BANG_();
}));
} else {
return null;
}
});

//# sourceMappingURL=repulse.ui.context_panel.js.map
