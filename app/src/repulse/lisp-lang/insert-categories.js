import { BUILTINS } from "./completions.js";

const builtinDetails = new Map(BUILTINS.map(item => [item.label, item.detail]));

function withBuiltinDetail(label, template, detail = builtinDetails.get(label)) {
  return { label, template, detail: detail || label };
}

function withDetail(label, template, detail) {
  return { label, template, detail };
}

// Each array is a separate constant so Closure can't conflate the lookup key
// with a renamed property.  getInsertCategories() uses an explicit switch so
// the kind string never goes through bracket-notation on a renamed object.

const WRAP_CATEGORIES = [
  {
    title: "Speed",
    items: [
      withBuiltinDetail("fast", "(fast ¦2 __TARGET__)"),
      withBuiltinDetail("slow", "(slow ¦2 __TARGET__)"),
    ],
  },
  {
    title: "Structure",
    items: [
      withBuiltinDetail("rev", "(rev __TARGET__)"),
      withBuiltinDetail("cat", "(cat __TARGET__ ¦(seq :hh :oh))"),
    ],
  },
  {
    title: "Conditional",
    items: [
      withBuiltinDetail("every", "(every ¦4 rev __TARGET__)"),
      withBuiltinDetail("sometimes", "(sometimes ¦rev __TARGET__)"),
      withBuiltinDetail("often", "(often ¦rev __TARGET__)"),
      withBuiltinDetail("rarely", "(rarely ¦rev __TARGET__)"),
      withBuiltinDetail("degrade", "(degrade __TARGET__)"),
    ],
  },
  {
    title: "Spatial",
    items: [
      withBuiltinDetail("jux", "(jux ¦rev __TARGET__)"),
      withBuiltinDetail("off", "(off ¦1/8 rev __TARGET__)"),
    ],
  },
  {
    title: "Layering",
    items: [
      withBuiltinDetail("stack", "(stack __TARGET__ ¦(seq :hh :oh))"),
    ],
  },
  {
    title: "Combinators",
    items: [
      withBuiltinDetail("late", "(late ¦1/8 __TARGET__)"),
      withBuiltinDetail("early", "(early ¦1/8 __TARGET__)"),
    ],
  },
];

const CHAIN_CATEGORIES = [
  {
    title: "Amplitude",
    items: [
      withBuiltinDetail("amp", "(amp ¦0.8)"),
      withBuiltinDetail("attack", "(attack ¦0.01)"),
      withBuiltinDetail("decay", "(decay ¦0.2)"),
      withBuiltinDetail("release", "(release ¦0.2)"),
    ],
  },
  {
    title: "Spatial",
    items: [
      withBuiltinDetail("pan", "(pan ¦-0.5)"),
    ],
  },
  {
    title: "Effects",
    items: [
      withDetail("reverb", "(fx :reverb ¦0.3)", "(fx :reverb mix) — add reverb to this pattern"),
      withDetail("delay", "(fx :delay ¦0.35)", "(fx :delay mix) — add rhythmic delay"),
      withDetail("filter", "(fx :filter ¦1200)", "(fx :filter cutoff) — lowpass the pattern"),
      withDetail("chorus", "(fx :chorus ¦0.35)", "(fx :chorus mix) — thicken with chorus"),
      withDetail("phaser", "(fx :phaser ¦0.4)", "(fx :phaser mix) — moving phase sweep"),
      withDetail("tremolo", "(fx :tremolo ¦0.5)", "(fx :tremolo depth) — amplitude modulation"),
      withDetail("overdrive", "(fx :overdrive ¦0.45)", "(fx :overdrive drive) — saturate the signal"),
      withDetail("distort", "(fx :distort :drive ¦8 :asym 0.3)", "(fx :distort :drive n :asym n) — soft clipping with tone and asymmetry control"),
      withDetail("amp-sim", "(fx :amp-sim :gain ¦12 :stages 2)", "(fx :amp-sim :gain n :stages n) — multi-stage tube amp simulation"),
      withDetail("cab", "(fx :cab :ir ¦:4x12)", "(fx :cab :ir k) — speaker cabinet simulation"),
      withDetail("bitcrusher", "(fx :bitcrusher ¦0.35)", "(fx :bitcrusher mix) — lo-fi crush"),
      withDetail("compressor", "(fx :compressor ¦0.5)", "(fx :compressor amount) — tighten dynamics"),
      withDetail("dattorro", "(fx :dattorro ¦0.35)", "(fx :dattorro mix) — spacious algorithmic reverb"),
    ],
  },
  {
    title: "Transitions",
    items: [
      withBuiltinDetail("tween", "(amp (tween :linear ¦0.0 1.0 2))"),
    ],
  },
];

const TOP_CATEGORIES = [
  {
    title: "Patterns",
    items: [
      withBuiltinDetail("seq", "(seq ¦:bd :sd :bd :sd)"),
      withBuiltinDetail("stack", "(stack ¦(seq :bd :_ :bd :_) (seq :_ :sd :_ :sd))"),
      withBuiltinDetail("pure", "(pure ¦:bd)"),
      withBuiltinDetail("cat", "(cat ¦(seq :bd :sd) (seq :hh :oh))"),
      withBuiltinDetail("euclidean", "(euclidean ¦5 8 :bd)"),
    ],
  },
  {
    title: "Binding",
    items: [
      withBuiltinDetail("def", "(def ¦kick (seq :bd :_ :bd :_))"),
      withBuiltinDetail("defn", "(defn ¦swing [pat]\n  (off 1/8 rev pat))"),
      withBuiltinDetail("defsynth", "(defsynth ¦lead [freq]\n  (-> (saw freq)\n      (env-perc 0.01 0.2)))"),
      withBuiltinDetail("defmacro", "(defmacro ¦twice [form]\n  (list 'do form form))"),
    ],
  },
  {
    title: "Commands",
    items: [
      withBuiltinDetail("bpm", "(bpm ¦120)"),
      withBuiltinDetail("stop", "(stop)"),
      withBuiltinDetail("clear!", "(clear!)"),
      withBuiltinDetail("reset!", "(reset!)"),
    ],
  },
  {
    title: "Audio",
    items: [
      withBuiltinDetail("fx", "(fx ¦:reverb 0.3)"),
      withBuiltinDetail("sound", "(sound ¦:808 0)"),
    ],
  },
  {
    title: "Samples",
    items: [
      withBuiltinDetail("samples!", "(samples! ¦\"github:tidalcycles/Dirt-Samples\")"),
      withBuiltinDetail("bank", "(bank ¦:AkaiLinn)"),
    ],
  },
  {
    title: "Arrangement",
    items: [
      withBuiltinDetail("arrange", "(arrange ¦[[(seq :bd :sd) 4] [(seq :hh :oh) 2]])"),
      withBuiltinDetail("play-scenes", "(play-scenes ¦[(seq :bd :sd) (seq :hh :oh)])"),
    ],
  },
];

export function getInsertCategories(kind) {
  if (kind === "wrap") return WRAP_CATEGORIES;
  if (kind === "chain") return CHAIN_CATEGORIES;
  if (kind === "top") return TOP_CATEGORIES;
  return [];
}
