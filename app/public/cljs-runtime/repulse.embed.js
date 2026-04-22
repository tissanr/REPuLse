goog.provide('repulse.embed');
if((typeof repulse !== 'undefined') && (typeof repulse.embed !== 'undefined') && (typeof repulse.embed.instance_counter !== 'undefined')){
} else {
repulse.embed.instance_counter = cljs.core.atom.cljs$core$IFn$_invoke$arity$1((0));
}
if((typeof repulse !== 'undefined') && (typeof repulse.embed !== 'undefined') && (typeof repulse.embed.instance_views !== 'undefined')){
} else {
repulse.embed.instance_views = cljs.core.atom.cljs$core$IFn$_invoke$arity$1(cljs.core.PersistentArrayMap.EMPTY);
}
/**
 * Create Shadow DOM root with styles and editor container.
 * Returns the container element.
 */
repulse.embed.mount_instance_BANG_ = (function repulse$embed$mount_instance_BANG_(host_el){
var shadow__$1 = host_el.attachShadow(({"mode": "open"}));
var style = (function (){var G__24690 = document.createElement("style");
(G__24690.textContent = repulse.embed_css.EMBED_CSS);

return G__24690;
})();
var wrap = (function (){var G__24691 = document.createElement("div");
G__24691.classList.add("editor-wrap");

return G__24691;
})();
shadow__$1.appendChild(style);

shadow__$1.appendChild(wrap);

return wrap;
});
/**
 * Create an evaluation function for an embed instance.
 * Captures the instance ID for potential future namespacing.
 */
repulse.embed.make_eval_fn = (function repulse$embed$make_eval_fn(instance_id){
return (function (code){
return repulse.eval_orchestrator.evaluate_BANG_(code);
});
});
/**
 * Called when a <repulse-editor> element is connected to the DOM.
 */
repulse.embed.connect_callback = (function repulse$embed$connect_callback(this$){
var code_attr = this$.getAttribute("code");
var snippet_attr = this$.getAttribute("snippet");
var bpm_attr = parseInt((function (){var or__5002__auto__ = this$.getAttribute("bpm");
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return "120";
}
})());
var height = (function (){var or__5002__auto__ = this$.getAttribute("height");
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return "220px";
}
})();
var autoplay_QMARK_ = this$.hasAttribute("autoplay");
var instance_id = cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$2(repulse.embed.instance_counter,cljs.core.inc);
(this$.style.display = "block");

var wrap = repulse.embed.mount_instance_BANG_(this$);
(wrap.style = ["height:",cljs.core.str.cljs$core$IFn$_invoke$arity$1(height)].join(''));

repulse.env.builtins.init_BANG_(new cljs.core.PersistentArrayMap(null, 5, [new cljs.core.Keyword(null,"on-beat-fn","on-beat-fn",567217350),(function (){
return null;
}),new cljs.core.Keyword(null,"set-playing!-fn","set-playing!-fn",1851430687),(function (_){
return null;
}),new cljs.core.Keyword(null,"set-output!-fn","set-output!-fn",1617998012),(function (_,___$1){
return null;
}),new cljs.core.Keyword(null,"make-stop-fn-fn","make-stop-fn-fn",1610235793),(function (){
return (function (){
return repulse.audio.stop_BANG_();
});
}),new cljs.core.Keyword(null,"share!-fn","share!-fn",-253867902),(function (){
return null;
})], null));

repulse.env.builtins.ensure_env_BANG_();

repulse.audio.set_bpm_BANG_(bpm_attr);

var eval_fn = repulse.embed.make_eval_fn(instance_id);
if(cljs.core.truth_(snippet_attr)){
repulse.snippets.load_BANG_();

return cljs.core.add_watch(repulse.snippets.library_atom,new cljs.core.Keyword("repulse.embed","embed-load","repulse.embed/embed-load",-2143501316),(function (_,___$1,___$2,___$3){
cljs.core.remove_watch(repulse.snippets.library_atom,new cljs.core.Keyword("repulse.embed","embed-load","repulse.embed/embed-load",-2143501316));

var temp__5804__auto__ = repulse.snippets.by_id(snippet_attr);
if(cljs.core.truth_(temp__5804__auto__)){
var s = temp__5804__auto__;
var code = new cljs.core.Keyword(null,"code","code",1586293142).cljs$core$IFn$_invoke$arity$1(s);
var ___$4 = (function (){var temp__5804__auto____$1 = new cljs.core.Keyword(null,"bpm","bpm",-1042376389).cljs$core$IFn$_invoke$arity$1(s);
if(cljs.core.truth_(temp__5804__auto____$1)){
var snippet_bpm = temp__5804__auto____$1;
return repulse.audio.set_bpm_BANG_(snippet_bpm);
} else {
return null;
}
})();
var view = repulse.ui.editor.make_editor(wrap,code,eval_fn);
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.embed.instance_views,cljs.core.assoc,instance_id,view);

if(cljs.core.truth_(autoplay_QMARK_)){
return setTimeout((function (){
return eval_fn(code);
}),(150));
} else {
return null;
}
} else {
return null;
}
}));
} else {
var code = (function (){var or__5002__auto__ = code_attr;
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return "";
}
})();
var view = repulse.ui.editor.make_editor(wrap,code,eval_fn);
cljs.core.swap_BANG_.cljs$core$IFn$_invoke$arity$4(repulse.embed.instance_views,cljs.core.assoc,instance_id,view);

if(cljs.core.truth_((function (){var and__5000__auto__ = autoplay_QMARK_;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core.seq(code);
} else {
return and__5000__auto__;
}
})())){
return setTimeout((function (){
return eval_fn(code);
}),(150));
} else {
return null;
}
}
});
/**
 * Register the <repulse-editor> custom element.
 * Called automatically when embed.js loads.
 */
repulse.embed.init_BANG_ = (function repulse$embed$init_BANG_(){
if(cljs.core.truth_(window.customElements)){
var cb = repulse.embed.connect_callback;
var klass = class extends HTMLElement {
                        constructor() { super(); }
                        connectedCallback() { cb(this); }
                      };
return window.customElements.define("repulse-editor",klass);
} else {
return null;
}
});

//# sourceMappingURL=repulse.embed.js.map
