var builtinDetails$$module$repulse$lisp_lang$insert_categories = new Map(BUILTINS$$module$repulse$lisp_lang$completions.map(item => [item.label, item.detail]));
function withBuiltinDetail$$module$repulse$lisp_lang$insert_categories(label, template, detail = builtinDetails$$module$repulse$lisp_lang$insert_categories.get(label)) {
  return {label, template, detail:detail || label};
}
function withDetail$$module$repulse$lisp_lang$insert_categories(label, template, detail) {
  return {label, template, detail};
}
var WRAP_CATEGORIES$$module$repulse$lisp_lang$insert_categories = [{title:"Speed", items:[withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("fast", "(fast ¦2 __TARGET__)"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("slow", "(slow ¦2 __TARGET__)")]}, {title:"Structure", items:[withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("rev", "(rev __TARGET__)"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("cat", "(cat __TARGET__ ¦(seq :hh :oh))")]}, 
{title:"Conditional", items:[withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("every", "(every ¦4 rev __TARGET__)"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("sometimes", "(sometimes ¦rev __TARGET__)"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("often", "(often ¦rev __TARGET__)"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("rarely", "(rarely ¦rev __TARGET__)"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("degrade", 
"(degrade __TARGET__)")]}, {title:"Spatial", items:[withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("jux", "(jux ¦rev __TARGET__)"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("off", "(off ¦1/8 rev __TARGET__)")]}, {title:"Layering", items:[withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("stack", "(stack __TARGET__ ¦(seq :hh :oh))")]}, {title:"Combinators", items:[withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("late", "(late ¦1/8 __TARGET__)"), 
withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("early", "(early ¦1/8 __TARGET__)")]}];
var CHAIN_CATEGORIES$$module$repulse$lisp_lang$insert_categories = [{title:"Amplitude", items:[withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("amp", "(amp ¦0.8)"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("attack", "(attack ¦0.01)"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("decay", "(decay ¦0.2)"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("release", "(release ¦0.2)")]}, {title:"Spatial", items:[withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("pan", 
"(pan ¦-0.5)")]}, {title:"Effects", items:[withDetail$$module$repulse$lisp_lang$insert_categories("reverb", "(fx :reverb ¦0.3)", "(fx :reverb mix) — add reverb to this pattern"), withDetail$$module$repulse$lisp_lang$insert_categories("delay", "(fx :delay ¦0.35)", "(fx :delay mix) — add rhythmic delay"), withDetail$$module$repulse$lisp_lang$insert_categories("filter", "(fx :filter ¦1200)", "(fx :filter cutoff) — lowpass the pattern"), withDetail$$module$repulse$lisp_lang$insert_categories("chorus", 
"(fx :chorus ¦0.35)", "(fx :chorus mix) — thicken with chorus"), withDetail$$module$repulse$lisp_lang$insert_categories("phaser", "(fx :phaser ¦0.4)", "(fx :phaser mix) — moving phase sweep"), withDetail$$module$repulse$lisp_lang$insert_categories("tremolo", "(fx :tremolo ¦0.5)", "(fx :tremolo depth) — amplitude modulation"), withDetail$$module$repulse$lisp_lang$insert_categories("overdrive", "(fx :overdrive ¦0.45)", "(fx :overdrive drive) — saturate the signal"), withDetail$$module$repulse$lisp_lang$insert_categories("bitcrusher", 
"(fx :bitcrusher ¦0.35)", "(fx :bitcrusher mix) — lo-fi crush"), withDetail$$module$repulse$lisp_lang$insert_categories("compressor", "(fx :compressor ¦0.5)", "(fx :compressor amount) — tighten dynamics"), withDetail$$module$repulse$lisp_lang$insert_categories("dattorro", "(fx :dattorro ¦0.35)", "(fx :dattorro mix) — spacious algorithmic reverb")]}, {title:"Transitions", items:[withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("tween", "(amp (tween :linear ¦0.0 1.0 2))")]}];
var TOP_CATEGORIES$$module$repulse$lisp_lang$insert_categories = [{title:"Patterns", items:[withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("seq", "(seq ¦:bd :sd :bd :sd)"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("stack", "(stack ¦(seq :bd :_ :bd :_) (seq :_ :sd :_ :sd))"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("pure", "(pure ¦:bd)"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("cat", "(cat ¦(seq :bd :sd) (seq :hh :oh))"), 
withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("euclidean", "(euclidean ¦5 8 :bd)")]}, {title:"Binding", items:[withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("def", "(def ¦kick (seq :bd :_ :bd :_))"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("defn", "(defn ¦swing [pat]\n  (off 1/8 rev pat))"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("defsynth", "(defsynth ¦lead [freq]\n  (-\x3e (saw freq)\n      (env-perc 0.01 0.2)))"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("defmacro", 
"(defmacro ¦twice [form]\n  (list 'do form form))")]}, {title:"Commands", items:[withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("bpm", "(bpm ¦120)"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("stop", "(stop)"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("clear!", "(clear!)"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("reset!", "(reset!)")]}, {title:"Audio", items:[withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("fx", 
"(fx ¦:reverb 0.3)"), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("sound", "(sound ¦:808 0)")]}, {title:"Samples", items:[withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("samples!", '(samples! ¦"github:tidalcycles/Dirt-Samples")'), withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("bank", "(bank ¦:AkaiLinn)")]}, {title:"Arrangement", items:[withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("arrange", "(arrange ¦[[(seq :bd :sd) 4] [(seq :hh :oh) 2]])"), 
withBuiltinDetail$$module$repulse$lisp_lang$insert_categories("play-scenes", "(play-scenes ¦[(seq :bd :sd) (seq :hh :oh)])")]}];
function getInsertCategories$$module$repulse$lisp_lang$insert_categories(kind) {
  if (kind === "wrap") {
    return WRAP_CATEGORIES$$module$repulse$lisp_lang$insert_categories;
  }
  if (kind === "chain") {
    return CHAIN_CATEGORIES$$module$repulse$lisp_lang$insert_categories;
  }
  if (kind === "top") {
    return TOP_CATEGORIES$$module$repulse$lisp_lang$insert_categories;
  }
  return [];
}
/** @const */ 
var module$repulse$lisp_lang$insert_categories = {};
/** @const */ 
module$repulse$lisp_lang$insert_categories.getInsertCategories = getInsertCategories$$module$repulse$lisp_lang$insert_categories;

$CLJS.module$repulse$lisp_lang$insert_categories=module$repulse$lisp_lang$insert_categories;
//# sourceMappingURL=module$repulse$lisp_lang$insert_categories.js.map
