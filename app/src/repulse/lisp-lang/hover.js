// hover.js — Hover documentation tooltips for REPuLse-Lisp built-ins
import { hoverTooltip } from "@codemirror/view";
import { syntaxTree } from "@codemirror/language";

/**
 * Documentation map: name → { signature, description, example }.
 * A superset of completions.js — same labels but richer content.
 */
const DOCS = {
  // --- Pattern constructors ---
  "seq": {
    signature: "(seq val ...)",
    description: "Create a sequence of values, each occupying one equal step per cycle.",
    example: "(seq :bd :sd :bd :sd)",
  },
  "stack": {
    signature: "(stack pat ...)",
    description: "Play multiple patterns simultaneously in parallel.",
    example: "(stack (seq :bd :_ :bd :_) (seq :_ :sd :_ :sd))",
  },
  "pure": {
    signature: "(pure val)",
    description: "A constant pattern that plays a single value for the entire cycle.",
    example: "(pure :bd)",
  },

  // --- Transformations ---
  "fast": {
    signature: "(fast factor pat)",
    description: "Speed up a pattern by the given factor. 2 = twice as fast. The factor may be a pattern or a mini-notation string: (fast \"<1 2>\" pat).",
    example: "(fast 2 (seq :bd :sd))",
  },
  "slow": {
    signature: "(slow factor pat)",
    description: "Slow down a pattern by the given factor. 2 = half speed. The factor may be a pattern or a mini-notation string.",
    example: "(slow 2 (seq :bd :sd :hh :oh))",
  },
  "rev": {
    signature: "(rev pat)",
    description: "Reverse the order of events within each cycle.",
    example: "(rev (seq :bd :sd :hh :oh))",
  },
  "every": {
    signature: "(every n transform pat)",
    description: "Apply a transformation every nth cycle. Other cycles play unmodified. n may be a pattern.",
    example: "(every 4 (fast 2) (seq :bd :sd))",
  },
  "fmap": {
    signature: "(fmap f pat)",
    description: "Map a function over every value in the pattern.",
    example: "(fmap (fn [x] (* x 2)) (seq 1 2 3))",
  },

  // --- Pattern combinators ---
  "euclidean": {
    signature: "(euclidean k n val) or (euclidean k n val rot)",
    description: "Distribute k onsets evenly across n steps (Tidal's Björklund). Optional rot rotates left. val a pattern gives the rhythm to that pattern: (euclidean 3 8 (seq :bd :sd)). k, n and rot may be patterns: (euclidean \"<3 5>\" 8 :bd).",
    example: "(euclidean 5 8 :bd)",
  },
  "cat": {
    signature: "(cat pat ...)",
    description: "Concatenate patterns: each plays for one full cycle in sequence, then loops.",
    example: "(cat (seq :bd :sd) (seq :hh :oh))",
  },
  "late": {
    signature: "(late amount pat)",
    description: "Shift events forward in time by a fraction of a cycle. The amount may be a pattern: (late \"<0 1/8>\" pat).",
    example: "(late 0.25 (seq :hh :hh :hh :hh))",
  },
  "early": {
    signature: "(early amount pat)",
    description: "Shift events backward in time by a fraction of a cycle. The amount may be a pattern.",
    example: "(early 0.125 (seq :sd :_ :sd :_))",
  },
  "sometimes": {
    signature: "(sometimes f pat)",
    description: "Apply transform f on approximately 50% of cycles (deterministic per cycle).",
    example: "(sometimes (fast 2) (seq :bd :sd))",
  },
  "often": {
    signature: "(often f pat)",
    description: "Apply transform f on approximately 75% of cycles.",
    example: "(often rev (seq :bd :sd :hh :oh))",
  },
  "rarely": {
    signature: "(rarely f pat)",
    description: "Apply transform f on approximately 25% of cycles.",
    example: "(rarely (fast 2) (seq :bd :sd))",
  },
  "sometimes-by": {
    signature: "(sometimes-by prob f pat)",
    description: "Apply transform f with probability prob (0.0–1.0) per cycle. prob may be a pattern.",
    example: "(sometimes-by 0.3 rev (seq :bd :sd :hh :oh))",
  },
  "degrade": {
    signature: "(degrade pat)",
    description: "Randomly drop approximately 50% of events per cycle.",
    example: "(degrade (seq :hh :hh :hh :hh))",
  },
  "degrade-by": {
    signature: "(degrade-by prob pat)",
    description: "Drop events with the given probability (0.0 = keep all, 1.0 = drop all). prob may be a pattern.",
    example: "(degrade-by 0.3 (seq :hh :hh :hh :hh))",
  },
  "choose": {
    signature: "(choose [val ...])",
    description: "Pick one value from the vector per cycle. Deterministic: same cycle always picks the same value.",
    example: "(choose [:bd :sd :hh :oh])",
  },
  "wchoose": {
    signature: "(wchoose [[val weight] ...])",
    description: "Weighted random choice: pick one value per cycle, biased by weights.",
    example: "(wchoose [[:bd 3] [:sd 1] [:hh 2]])",
  },
  "jux": {
    signature: "(jux f pat)",
    description: "Juxtapose: play pat panned left, and (f pat) panned right, simultaneously.",
    example: "(jux (fast 2) (seq :bd :sd :hh :oh))",
  },
  "jux-by": {
    signature: "(jux-by width f pat)",
    description: "Like jux with adjustable stereo width (0.0 = mono, 1.0 = hard pan). width may be a pattern.",
    example: "(jux-by 0.5 rev (seq :bd :sd))",
  },
  "off": {
    signature: "(off amount f pat)",
    description: "Layer the original with a time-shifted transformed copy: (stack pat (late amount (f pat))).",
    example: "(off 0.25 (fast 2) (seq :bd :sd))",
  },

  // --- Time & structure (TL3) ---
  "struct": {
    signature: "(struct bools pat)",
    description: "Structure from a boolean pattern: every true step of bools plays the value(s) of pat over that step. false, 0, ~, f steps are silent. bools may be a mini-notation string.",
    example: "(struct \"t ~ t t\" (pure :bd))",
  },
  "mask": {
    signature: "(mask bools pat)",
    description: "Keep the structure of pat, but only the parts that overlap true steps of bools.",
    example: "(mask \"1 0 1 1\" (fast 8 (pure :hh)))",
  },
  "invert": {
    signature: "(invert pat)",
    description: "Boolean not on every value: true becomes false and the other way round. Rests stay rests.",
    example: "(struct (invert \"t f t f\") (pure :x))",
  },
  "euclid-legato": {
    signature: "(euclid-legato k n pat)",
    description: "Euclidean rhythm where each hit lasts until the next hit (the last one to the end of the cycle). k and n may be patterns.",
    example: "(euclid-legato 3 8 (pure :x))",
  },
  "euclid-legato-rot": {
    signature: "(euclid-legato-rot k n r pat)",
    description: "euclid-legato with the rhythm rotated left by r steps; steps before the first hit rest. k, n and r may be patterns.",
    example: "(euclid-legato-rot 3 8 2 (pure :x))",
  },
  "ply": {
    signature: "(ply n pat)",
    description: "Repeat every event n times inside its own slot. n may be a pattern.",
    example: "(ply 2 (seq :bd :sd))",
  },
  "ply-with": {
    signature: "(ply-with n f pat)",
    description: "Like ply, but repetition i has f applied i times: each event becomes ceil(n) sub-steps. n may be a pattern.",
    example: "(ply-with 3 (add 12) (seq 0 7))",
  },
  "inside": {
    signature: "(inside n f pat)",
    description: "Apply f as if each 1/n of a cycle were a whole cycle: (fast n (f (slow n pat))). n may be a pattern.",
    example: "(inside 2 rev (seq 0 1 2 3 4 5 6 7))",
  },
  "outside": {
    signature: "(outside n f pat)",
    description: "Apply f across n cycles at once: (inside 1/n f pat). n may be a pattern.",
    example: "(outside 2 rev (cat (seq :a :b) (seq :c :d)))",
  },
  "within": {
    signature: "(within start end f pat)",
    description: "Apply f only to the events whose start lies in the cycle window [start, end); the others play unchanged. start and end may be patterns.",
    example: "(within 0 1/2 (fast 2) (seq :a :b :c :d))",
  },
  "swing-by": {
    signature: "(swing-by x n pat)",
    description: "Delay events in the second half of each 1/n slice by x of that slice. x and n may be patterns.",
    example: "(swing-by 1/3 4 (fast 8 (pure :hh)))",
  },
  "swing": {
    signature: "(swing n pat)",
    description: "Shuffle feel: (swing-by 1/3 n pat). n may be a pattern.",
    example: "(swing 4 (fast 8 (pure :hh)))",
  },
  "brak": {
    signature: "(brak pat)",
    description: "Breakbeat feel: on every odd cycle squeeze the cycle into its first half and shift it right by a quarter.",
    example: "(brak (seq :bd :sd))",
  },
  "iter": {
    signature: "(iter n pat)",
    description: "Shift the start of the pattern earlier by 1/n each cycle, so it rotates through n positions. A cycle-varying pat is held for n cycles. n may be a pattern.",
    example: "(iter 4 (seq :a :b :c :d))",
  },
  "iter-back": {
    signature: "(iter-back n pat)",
    description: "Like iter, but rotating the other way (the start moves later each cycle). n may be a pattern.",
    example: "(iter-back 4 (seq :a :b :c :d))",
  },
  "palindrome": {
    signature: "(palindrome pat)",
    description: "Alternate pat and (rev pat) cycle by cycle. A cycle-varying pat is held for two cycles.",
    example: "(palindrome (seq :a :b :c))",
  },
  "rot": {
    signature: "(rot n pat)",
    description: "Rotate the values of each cycle n places to the left while keeping the rhythm. Rests keep their place. n may be a pattern.",
    example: "(rot 1 (seq :a :b :c :d))",
  },
  "chunk": {
    signature: "(chunk n f pat)",
    description: "Apply f to one n-th of the cycle, moving to the next chunk every cycle (cycle c transforms chunk c mod n). n may be a pattern.",
    example: "(chunk 4 (add 10) (seq 0 1 2 3))",
  },
  "chunk-back": {
    signature: "(chunk-back n f pat)",
    description: "Like chunk, but the transformed chunk moves backwards: 0, n-1, n-2 … n may be a pattern.",
    example: "(chunk-back 4 (add 10) (seq 0 1 2 3))",
  },
  "fast-chunk": {
    signature: "(fast-chunk n f pat)",
    description: "Like chunk, but a cycle-varying pat is not held: it plays at the real cycle. n may be a pattern.",
    example: "(fast-chunk 2 (add 10) (cat (seq 0 1) (seq 4 5)))",
  },
  "polymeter": {
    signature: "(polymeter [steps] layer ...)",
    description: "Layer step vectors of different lengths against one steps-per-cycle grid. Each layer is a vector of values or patterns. An optional leading number, string or pattern gives the steps per cycle (default: the first layer's length).",
    example: "(polymeter [:bd :sd :hh] [:c3 :e3])",
  },
  "timecat": {
    signature: "(timecat [weight x] ...)",
    description: "Concatenate within one cycle, each entry taking a share of the cycle proportional to its weight. x is a value or a pattern.",
    example: "(timecat [3 :e3] [1 :g3])",
  },
  "first-of": {
    signature: "(first-of n f pat)",
    description: "Apply f on cycles 0, n, 2n … (a synonym of every). n may be a pattern.",
    example: "(first-of 4 (add 10) (seq 0 1))",
  },
  "last-of": {
    signature: "(last-of n f pat)",
    description: "Apply f on the last cycle of every n (cycles n-1, 2n-1 …). n may be a pattern.",
    example: "(last-of 4 (add 10) (seq 0 1))",
  },
  "compress": {
    signature: "(compress s e pat)",
    description: "Squeeze each cycle into the window [s, e) of the cycle (0 <= s < e <= 1); silence elsewhere. s and e may be patterns.",
    example: "(compress 1/4 3/4 (seq :bd :sd))",
  },
  "zoom": {
    signature: "(zoom s e pat)",
    description: "Play only the window [s, e) of each cycle, stretched to fill the whole cycle. s and e may be patterns.",
    example: "(zoom 1/4 3/4 (seq :a :b :c :d))",
  },
  "focus": {
    signature: "(focus s e pat)",
    description: "Squeeze the cycle into [s, e) and keep playing, so neighbouring cycles fill the rest (no gaps, unlike compress). s and e may be patterns.",
    example: "(focus 1/4 3/4 (seq :a :b :c :d))",
  },
  "fast-gap": {
    signature: "(fast-gap n pat)",
    description: "Play n times faster, then stay silent until the next cycle. n may be a pattern.",
    example: "(fast-gap 2 (seq :bd :sd))",
  },
  "linger": {
    signature: "(linger n pat)",
    description: "Repeat the first n of every cycle to fill it (the last |n| for negative n). Restarts every cycle. n may be a pattern.",
    example: "(linger 1/4 (seq 0 1 2 3 4 5 6 7))",
  },
  "bite": {
    signature: "(bite n ipat pat)",
    description: "Cut each cycle of pat into n slices; each event of ipat (an index) plays that slice squeezed into its own length. n may be a pattern.",
    example: "(bite 4 (seq 0 2) (seq :a :b :c :d :e :f :g :h))",
  },
  "ribbon": {
    signature: "(ribbon offset cycles pat)",
    description: "Loop `cycles` cycles of pat starting at cycle `offset`. offset and cycles may be patterns.",
    example: "(ribbon 1 2 (cat (pure :a) (pure :b) (pure :c) (pure :d)))",
  },
  "hurry": {
    signature: "(hurry r pat)",
    description: "Like fast, and also multiplies every event's :rate by r (a missing rate counts as 1), so samples speed up in pitch too. r may be a pattern.",
    example: "(hurry 2 (seq :bd :sd))",
  },
  "revv": {
    signature: "(revv pat)",
    description: "Reverse time across cycles (t becomes -t): cycle c plays source cycle -c-1 reversed.",
    example: "(revv (seq :a :b :c))",
  },
  "repeat-cycles": {
    signature: "(repeat-cycles n pat)",
    description: "Play each source cycle n times before moving to the next. n may be a pattern.",
    example: "(repeat-cycles 2 (cat (seq :a) (seq :b)))",
  },
  "trunc": {
    signature: "(trunc t pat)",
    description: "Play only the first t of every cycle; events crossing the cut keep their length but are cut short. t may be a pattern.",
    example: "(trunc 3/4 (seq :a :b :c :d))",
  },
  "press": {
    signature: "(press pat)",
    description: "Shift every event to start half way through its own length: (press-by 1/2 pat).",
    example: "(press (seq :a :b))",
  },
  "press-by": {
    signature: "(press-by r pat)",
    description: "Shift every event to start r of the way through its own length and shorten it accordingly. r may be a pattern.",
    example: "(press-by 1/4 (seq :a :b))",
  },
  "chunk-into": {
    signature: "(chunk-into n f pat)",
    description: "Like chunk, but f is applied to the chunk as if it were a full looping cycle, then squeezed back. Chunk index is c mod n. n may be a pattern.",
    example: "(chunk-into 2 rev (seq 0 1 2 3))",
  },
  "chunk-back-into": {
    signature: "(chunk-back-into n f pat)",
    description: "Like chunk-into, but the chunk moves backwards: n-1, n-2 \u2026 0. n may be a pattern.",
    example: "(chunk-back-into 2 rev (seq 0 1 2 3))",
  },
  "euclidish": {
    signature: "(euclidish k n g pat)",
    description: "A Euclidean rhythm whose hits are morphed towards perfectly even spacing by g (0 = euclidean, 1 = even). k, n and g may be patterns.",
    example: "(euclidish 3 8 1/2 (pure :x))",
  },
  "ply-for-each": {
    signature: "(ply-for-each n f pat)",
    description: "Each event becomes ceil(n) sub-steps; sub-step i >= 1 is (f (pure v) i), where f takes the pattern and the integer index. n may be a pattern.",
    example: "(ply-for-each 3 (fn [p i] (add (* i 12) p)) (seq 0 7))",
  },
  "filter-events": {
    signature: "(filter-events pred pat)",
    description: "Keep only the events whose value satisfies pred. (Named filter-events because filter works on sequences.)",
    example: "(filter-events (fn [v] (> v 2)) (seq 1 2 3 4))",
  },
  "filter-when": {
    signature: "(filter-when pred pat)",
    description: "Keep only the events whose start time satisfies pred. The time is an integer when whole, otherwise an exact rational like 1/4.",
    example: "(filter-when (fn [t] (< t 1/2)) (seq :a :b :c :d))",
  },
  "bypass": {
    signature: "(bypass on pat)",
    description: "When on is true (1), silence pat; otherwise play it unchanged. on may be a pattern.",
    example: "(bypass 0 (seq :a :b))",
  },
  "beat": {
    signature: "(beat t div pat)",
    description: "Place every value of pat on step t (mod div) of div steps in the cycle. t may be stacked: \"0,7,10\". t and div may be patterns.",
    example: "(beat \"0,7,10\" 16 (pure :bd))",
  },
  "sew": {
    signature: "(sew bools a b)",
    description: "Switch between patterns a and b: a plays where bools is true, b where it is false. Rests in bools select neither.",
    example: "(sew \"t f\" (seq :a :a :a :a) (seq :b :b :b :b))",
  },
  "stitch": {
    signature: "(stitch bools a b)",
    description: "Structure from bools: true steps take a's value, false steps take b's. Rests select neither.",
    example: "(stitch \"t f t t\" (pure :bd) (pure :sn))",
  },
  "binary": {
    signature: "(binary n)",
    description: "The bits of n, most significant first, as a true/false step sequence: (binary 5) = true false true. n may be a pattern.",
    example: "(struct (binary 5) (pure :x))",
  },
  "binary-n": {
    signature: "(binary-n n [bits])",
    description: "The low `bits` bits of n (default 16), most significant first, as a true/false step sequence. n and bits may be patterns.",
    example: "(struct (binary-n 5 4) (pure :x))",
  },
  "~": {
    signature: "(~ \"mini-notation\")",
    description: "Parse a mini-notation string into a pattern: [ ] groups, < > alternation, * ? : @, bd!3 repeats, a . b groups, a, b layers, {a b c}%4 polymeter, bd(3,8,2) euclid. _ and ~ are rests; use @ to elongate.",
    example: "(~ \"bd!3 sd . hh hh hh, cp(3,8)\")",
  },

  // --- Pattern arithmetic (TL1) ---
  "add": {
    signature: "(add [mode] amt pat)",
    description: "Add amt to every value. amt may be a number, a pattern or a mini-notation string. Note keywords move by semitones; maps change :note; rests pass through. mode picks where the rhythm comes from: :in (default, from pat), :out (from amt), :mix (both), :squeeze.",
    example: "(->> (seq 1 3 5 8) (add \"<0 2 4>\") (scale :minor :c3))",
  },
  "sub": {
    signature: "(sub [mode] amt pat)",
    description: "Subtract amt from every value. Same modes and argument types as add.",
    example: "(sub 12 (seq :c4 :e4 :g4))",
  },
  "mul": {
    signature: "(mul [mode] amt pat)",
    description: "Multiply every value by amt. Same modes and argument types as add.",
    example: "(mul 2 (seq 220 330))",
  },
  "div": {
    signature: "(div [mode] amt pat)",
    description: "Divide every value by amt (division by zero leaves the value unchanged). Same modes as add.",
    example: "(div 2 (seq 440 660))",
  },
  "set": {
    signature: "(set [mode] ctrl pat)",
    description: "Merge control maps into every event; ctrl values win (Tidal #). ctrl may be a map or a pattern of maps.",
    example: "(set {:amp 0.5 :pan -0.5} (seq :c4 :e4))",
  },

  // --- Music theory ---
  "scale": {
    signature: "(scale scale-kw root pat)",
    description: "Map degree integers (1, 2, 3, ...) to Hz frequencies using a named scale. 1 = root, 2 = second tone, etc. Degrees outside the scale wrap into higher/lower octaves.",
    example: "(scale :minor :c4 (seq 1 3 5 8))",
  },
  "chord": {
    signature: "(chord chord-kw root)",
    description: "Stack the tones of a chord as simultaneous Hz values.",
    example: "(chord :minor7 :a3)",
  },
  "transpose": {
    signature: "(transpose semitones pat)",
    description: "Shift all Hz values in a pattern up or down by n semitones. Note keywords pass through. Non-note keywords (drums, rests) are unchanged.",
    example: "(transpose 7 (scale :major :c4 (seq 0 1 2 3)))",
  },

  // --- Continuous signals (TL2) ---
  "signal": {
    signature: "(signal f)",
    description: "Build a continuous pattern from a function of absolute cycle time (a number, e.g. 0.25 for a quarter into cycle 0). Sampled at the start of each structure event.",
    example: "(segment 4 (signal (fn [t] (* t 2))))",
  },
  "steady": {
    signature: "(steady v)",
    description: "A continuous pattern that always has the value v.",
    example: "(segment 4 (steady 3))",
  },
  "time": {
    signature: "time",
    description: "Continuous signal whose value is the absolute cycle time: 0, 0.25, 0.5, 0.75 in cycle 0 and 1, 1.25, ... in cycle 1.",
    example: "(segment 4 time)",
  },
  "sine": {
    signature: "sine",
    description: "Continuous signal: sine wave 0..1, 0.5 at t=0. Values at t = 0, 1/4, 1/2, 3/4: 0.5 1 0.5 0. Has no rhythm of its own: give it structure with (segment n sine) or use it as a parameter, e.g. (pan sine pat).",
    example: "(segment 8 (range 200 2000 sine))",
  },
  "cosine": {
    signature: "cosine",
    description: "Continuous signal: cosine wave 0..1 (0 at t=0, Tidal phase). Values at t = 0, 1/4, 1/2, 3/4: 0 0.5 1 0.5. Has no rhythm of its own: give it structure with (segment n cosine) or use it as a parameter, e.g. (pan cosine pat).",
    example: "(segment 8 cosine)",
  },
  "isaw": {
    signature: "isaw",
    description: "Continuous signal: inverted saw, 1→0 each cycle. Values at t = 0, 1/4, 1/2, 3/4: 1 0.75 0.5 0.25. Has no rhythm of its own: give it structure with (segment n isaw) or use it as a parameter, e.g. (pan isaw pat).",
    example: "(segment 4 isaw)",
  },
  "itri": {
    signature: "itri",
    description: "Continuous signal: inverted triangle wave. Values at t = 0, 1/4, 1/2, 3/4: 1 0.5 0 0.5. Has no rhythm of its own: give it structure with (segment n itri) or use it as a parameter, e.g. (pan itri pat).",
    example: "(segment 4 itri)",
  },
  "isquare": {
    signature: "isquare",
    description: "Continuous signal: inverted square wave, 1 then 0. Values at t = 0, 1/4, 1/2, 3/4: 1 1 0 0. Has no rhythm of its own: give it structure with (segment n isquare) or use it as a parameter, e.g. (pan isquare pat).",
    example: "(segment 4 isquare)",
  },
  "sine2": {
    signature: "sine2",
    description: "Bipolar (-1..1) version of sine: (to-bipolar sine). Handy for pan and other signed parameters.",
    example: "(pan sine2 (fast 8 (seq :hh)))",
  },
  "cosine2": {
    signature: "cosine2",
    description: "Bipolar (-1..1) version of cosine: (to-bipolar cosine). Handy for pan and other signed parameters.",
    example: "(pan cosine2 (fast 8 (seq :hh)))",
  },
  "saw2": {
    signature: "saw2",
    description: "Bipolar (-1..1) version of saw: (to-bipolar saw). Handy for pan and other signed parameters.",
    example: "(pan saw2 (fast 8 (seq :hh)))",
  },
  "isaw2": {
    signature: "isaw2",
    description: "Bipolar (-1..1) version of isaw: (to-bipolar isaw). Handy for pan and other signed parameters.",
    example: "(pan isaw2 (fast 8 (seq :hh)))",
  },
  "tri2": {
    signature: "tri2",
    description: "Bipolar (-1..1) version of tri: (to-bipolar tri). Handy for pan and other signed parameters.",
    example: "(pan tri2 (fast 8 (seq :hh)))",
  },
  "itri2": {
    signature: "itri2",
    description: "Bipolar (-1..1) version of itri: (to-bipolar itri). Handy for pan and other signed parameters.",
    example: "(pan itri2 (fast 8 (seq :hh)))",
  },
  "square2": {
    signature: "square2",
    description: "Bipolar (-1..1) version of square: (to-bipolar square). Handy for pan and other signed parameters.",
    example: "(pan square2 (fast 8 (seq :hh)))",
  },
  "isquare2": {
    signature: "isquare2",
    description: "Bipolar (-1..1) version of isquare: (to-bipolar isquare). Handy for pan and other signed parameters.",
    example: "(pan isquare2 (fast 8 (seq :hh)))",
  },
  "rand2": {
    signature: "rand2",
    description: "Bipolar (-1..1) version of rand: (to-bipolar rand). Handy for pan and other signed parameters.",
    example: "(pan rand2 (fast 8 (seq :hh)))",
  },
  "rand": {
    signature: "rand",
    description: "Deterministic random signal in [0,1): Tidal's timeToRand of the sampling time, so it is identical on every run. It is exactly 0 at t = 0. Vary it with a time offset: (early 7 rand).",
    example: "(segment 8 (range 1 8 rand))",
  },
  "irand": {
    signature: "(irand n)",
    description: "Random integers 0 to n-1 as a continuous signal (floor of rand times n). n may be a pattern.",
    example: "(segment 8 (irand 8))",
  },
  "perlin": {
    signature: "perlin",
    description: "Smooth random signal in [0,1): Perlin-style noise that interpolates between one random value per cycle with a smootherstep curve.",
    example: "(pan (range -0.5 0.5 perlin) (fast 4 (seq :hh)))",
  },
  "segment": {
    signature: "(segment n pat)",
    description: "Discretise a pattern or signal: n events per cycle, each with the value of pat at its onset. n may be a pattern; one-arg form returns a transformer.",
    example: "(segment 4 saw)",
  },
  "rangex": {
    signature: "(rangex lo hi pat)",
    description: "Scale a 0..1 signal exponentially from lo to hi (good for frequencies). lo and hi must be positive. Two-argument form returns a transformer.",
    example: "(segment 4 (rangex 20 2000 saw))",
  },
  "range2": {
    signature: "(range2 lo hi pat)",
    description: "Like range but for a bipolar -1..1 input such as sine2. Two-argument form returns a transformer.",
    example: "(segment 4 (range2 200 2000 sine2))",
  },
  "to-bipolar": {
    signature: "(to-bipolar pat)",
    description: "Convert numeric values from 0..1 to -1..1.",
    example: "(segment 4 (to-bipolar saw))",
  },
  "from-bipolar": {
    signature: "(from-bipolar pat)",
    description: "Convert numeric values from -1..1 to 0..1.",
    example: "(segment 4 (from-bipolar sine2))",
  },
  "run": {
    signature: "(run n)",
    description: "Plays the integers 0 to n-1, one per step, in one cycle. n may be a pattern.",
    example: "(run 4)",
  },
  "saw": {
    signature: "saw",
    description: "Continuous signal: rising ramp 0→1 each cycle (values 0 0.25 0.5 0.75 at quarter points). Not a function: use it bare. The saw synth voice is (synth :saw pat); inside defsynth, (saw freq) is the sawtooth UGen.",
    example: "(segment 4 saw)",
  },
  "square": {
    signature: "square",
    description: "Continuous signal: 0 for the first half of the cycle, 1 for the second. Not a function: use it bare. The square synth voice is (synth :square pat), with :pw for pulse width; inside defsynth, (square freq) is the UGen.",
    example: "(segment 4 square)",
  },
  "tri": {
    signature: "tri",
    description: "Continuous signal: triangle wave rising 0→1 in the first half-cycle and falling back in the second. Top-level tri is the signal; inside defsynth bodies (tri freq) is the triangle-wave UGen.",
    example: "(segment 4 tri)",
  },
  "range": {
    signature: "(range n) / (range a b step) / (range lo hi pat)",
    description: "With numbers, the Lisp number sequence (range 8), (range 0 8 2). With a pattern or signal as the last argument it is Tidal's range: scale values from 0..1 to lo..hi, e.g. (range 200 2000 saw). lo and hi may be patterns.",
    example: "(segment 4 (range 200 2000 saw))",
  },

  // --- Per-event parameters ---
  "->>": {
    signature: "(->> pat (f args) ...)",
    description: "Thread-last: pass the pattern as the last argument of each successive form. The natural way to chain parameter transforms.",
    example: "(->> (seq :c4 :e4) (amp 0.7) (decay 0.5))",
  },
  "tween": {
    signature: "(tween curve start end bars)",
    description: "Interpolate a parameter from start to end over the given duration in bars. The audio engine handles per-sample interpolation — a single message is sent; no polling occurs. After the transition completes, the end value is held. Re-evaluating code restarts the transition.\n\ncurve: :linear — constant rate of change\n       :exp    — slow start, fast end (good for volume fades)\n       :sine   — S-curve: slow at both ends, fast in the middle",
    example: "(->> (seq :c4 :e4 :g4) (synth :saw) (amp (tween :linear 0.0 1.0 2)))",
  },
  "amp": {
    signature: "(amp val pat) or (amp val)",
    description: "Set event amplitude (0.0 = silent, 1.0 = full). One-arg form returns a transformer for use with ->> or comp.",
    example: "(amp 0.8 (seq :c4 :e4 :g4))",
  },
  "attack": {
    signature: "(attack secs pat) or (attack secs)",
    description: "Set envelope attack time in seconds. 0.001 = percussive, 0.3 = slow swell.",
    example: "(attack 0.1 (pure :c4))",
  },
  "decay": {
    signature: "(decay secs pat) or (decay secs)",
    description: "Set envelope decay time in seconds. 0.08 = short stab, 2.0 = long tone.",
    example: "(decay 0.5 (seq :c4 :e4))",
  },
  "release": {
    signature: "(release secs pat) or (release secs)",
    description: "Set envelope release time in seconds. Defaults to decay value when omitted.",
    example: "(release 0.3 (seq :c4 :e4))",
  },
  "pan": {
    signature: "(pan pos pat) or (pan pos)",
    description: "Set stereo panning. -1.0 = hard left, 0.0 = centre, 1.0 = hard right.",
    example: "(pan -0.5 (seq :c4 :e4))",
  },
  "comp": {
    signature: "(comp f g ...)",
    description: "Compose transformers right-to-left. Useful for building named presets.",
    example: "(def pluck (comp (amp 0.8) (attack 0.003) (decay 0.15)))",
  },

  // --- Sound & playback ---
  "bpm": {
    signature: "(bpm n)",
    description: "Set the tempo in beats per minute.",
    example: "(bpm 130)",
  },
  "stop": {
    signature: "(stop)",
    description: "Stop all playback immediately.",
    example: "(stop)",
  },
  "track": {
    signature: "(track :name pattern)",
    description: "Define or replace a named track. Each track runs independently.",
    example: "(track :kick (seq :bd :_ :bd :_))",
  },
  "play": {
    signature: "(play :name pattern)",
    description: "Renamed to track — use (track :name pattern) instead.",
    example: "(track :kick (seq :bd :_ :bd :_))",
  },
  "mute!": {
    signature: "(mute! :name)",
    description: "Silence a track without removing it. Use in the command bar.",
    example: "(mute! :kick)",
  },
  "unmute!": {
    signature: "(unmute! :name)",
    description: "Re-enable a muted track.",
    example: "(unmute! :kick)",
  },
  "solo!": {
    signature: "(solo! :name)",
    description: "Play only this track, muting all others.",
    example: "(solo! :bass)",
  },
  "clear!": {
    signature: "(clear! :name) or (clear!)",
    description: "Remove a track by name, or remove all tracks.",
    example: "(clear! :kick)",
  },
  "tracks": {
    signature: "(tracks)",
    description: "List all currently active track names.",
    example: "(tracks)",
  },
  "upd": {
    signature: "(upd)",
    description: "Hot-swap: re-evaluate the editor buffer and update running tracks without stopping playback.",
    example: "(upd)",
  },
  "tap!": {
    signature: "(tap!)",
    description: "Register a BPM tap. Four consecutive taps set the tempo.",
    example: "(tap!)",
  },
  "midi-sync!": {
    signature: "(midi-sync! true/false)",
    description: "Enable or disable MIDI clock synchronisation.",
    example: "(midi-sync! true)",
  },

  // --- MIDI output ---
  "midi-map": {
    signature: "(midi-map :cc N :target)",
    description: "Map MIDI CC number N from any connected controller to a named parameter. Supported targets: :filter (master lowpass cutoff), :amp (master gain), :bpm (tempo 60–240). Requires Chrome or Edge.",
    example: "(midi-map :cc 1 :filter)",
  },
  "midi-out": {
    signature: "(midi-out ch pat) or (midi-out ch)",
    description: "Route pattern events as MIDI Note On/Off messages on the given channel (1–16). Notes are derived from Hz values; amp maps to MIDI velocity. Combine with scale, chord, transpose. Requires Chrome or Edge.",
    example: "(->> (scale :minor :c4 (seq 0 2 4 7)) (midi-out 1))",
  },
  "midi-clock-out!": {
    signature: "(midi-clock-out! true/false)",
    description: "Start or stop broadcasting a 24ppqn MIDI clock at the current BPM. Sends MIDI Start (0xFA) when enabled, MIDI Stop (0xFC) when disabled. Locks external DAWs and hardware to REPuLse's tempo. Requires Chrome or Edge.",
    example: "(midi-clock-out! true)",
  },
  "midi-export": {
    signature: "(midi-export :track n)",
    description: "Export n cycles of the named track as a Standard MIDI File (.mid) and trigger a browser download. The file contains correct pitch, timing, duration, and tempo. Works in all browsers — no MIDI hardware required.",
    example: "(midi-export :bass 4)",
  },

  // --- Freesound ---
  "freesound-key!": {
    signature: '(freesound-key! "api-key")',
    description: "Set your Freesound API key. Required before using freesound!. Get a free key at freesound.org.",
    example: '(freesound-key! "abc123xyz")',
  },
  "freesound!": {
    signature: '(freesound! "query")',
    description: "Search Freesound.org and load up to 5 results as sample banks named :freesound-ID. Use them in patterns like any other keyword.",
    example: '(do (freesound! "kick 808") (seq :freesound-12345))',
  },

  // --- Samples ---
  "samples!": {
    signature: '(samples! "github:owner/repo")',
    description: "Load an external sample bank from a GitHub repository.",
    example: '(samples! "github:tidalcycles/Dirt-Samples")',
  },
  "sample-banks": {
    signature: "(sample-banks)",
    description: "List all registered sample bank names.",
    example: "(sample-banks)",
  },
  "bank": {
    signature: "(bank :prefix)",
    description: "Set a default bank prefix for all subsequent keyword lookups.",
    example: "(bank :AkaiLinn)",
  },
  "sound": {
    signature: "(sound bank n)",
    description: "Select sample number n from the named bank.",
    example: "(sound :808 0)",
  },

  // --- Effects ---
  "fx": {
    signature: "(fx :name param) or (->> pat (fx :name param))",
    description: "Global: sets a master-chain effect parameter. Per-track: use inside ->> to route a track through its own effect chain — re-evaluating play without (fx ...) removes the effect automatically. Use (fx :off :name) / (fx :on :name) to bypass/restore global effects.",
    example: "(->> (seq :bd :_ :bd :_) (fx :filter 1000))",
  },
  "distort": {
    signature: "(fx :distort [:drive N] [:tone Hz] [:mix N] [:asym N] [:algo kw] [:oversample 1|2|4])",
    description: "Soft-clipping waveshaper distortion. :drive sets pre-clip gain from 1.0–100.0 (default 4.0). :tone sets the post-clip lowpass from 200–20000 Hz (default 3000). :mix blends dry/wet from 0.0–1.0 (default 1.0). :asym sets half-wave asymmetry from -1.0–1.0 for even-harmonic warmth (default 0.0). :algo chooses :tanh, :sigmoid, or :atan clipping curves. :oversample reduces aliasing at high drive (default 1).",
    example: "(->> (seq :c2 :g2 :c3 :g2) (synth :saw) (fx :distort :drive 25 :oversample 4))",
  },
  "amp-sim": {
    signature: "(fx :amp-sim [:gain N] [:stages N] [:tone Hz] [:tonestack kw] [:sag N] [:mix N] [:oversample 1|2|4])",
    description: "Multi-stage tube preamp simulation. :gain sets total preamp gain from 1.0–100.0 (default 8.0). :stages sets number of gain stages from 1–4 (default 3). :tone sets post-amp lowpass Hz (default 4000). :tonestack chooses :neutral, :bright, :dark, :mid-scoop, or :mid-hump presets. :sag adds power supply sag / transient compression from 0.0–1.0 (default 0.0). :oversample reduces aliasing (default 1).",
    example: "(->> (seq :e2 :_ :e2 :g2) (synth :saw) (fx :amp-sim :gain 40 :stages 2 :oversample 2))",
  },
  "waveshape": {
    signature: "(fx :waveshape :curve C [:drive N] [:tone Hz] [:mix N])",
    description: "Arbitrary waveshaper distortion via a user-defined transfer function. :curve accepts a Float32Array, a vector of floats, or a generator function. :drive sets pre-shaper gain from 1.0–20.0 (default 1.0). :tone sets the post-shaper lowpass cutoff (default 20000).",
    example: "(fx :waveshape :curve (chebyshev 3) :drive 2)",
  },
  "cab": {
    signature: "(fx :cab [:ir kw] [:mix N])",
    description: "Speaker cabinet impulse response convolution. :ir chooses :1x12, :2x12, :4x12, or :di (dry passthrough). :mix blends dry/wet from 0.0–1.0. Pairs well with (fx :amp-sim ...) or (fx :distort ...).",
    example: "(->> (seq :e2 :_ :e2 :g2) (synth :saw) (fx :amp-sim :gain 30 :stages 3 :tonestack :mid-scoop) (fx :cab :ir :4x12))",
  },
  "chebyshev": {
    signature: "(chebyshev N)",
    description: "Generate a Chebyshev polynomial curve of order N (1–8) for use with (fx :waveshape). Order N adds primarily the Nth harmonic. Use as a :curve value.",
    example: "(fx :waveshape :curve (chebyshev 2) :drive 4)",
  },
  "fold": {
    signature: "(fold)",
    description: "Generate a wavefolder transfer function for use with (fx :waveshape). Folds the waveform back on itself, producing rich harmonics.",
    example: "(fx :waveshape :curve (fold) :drive 8)",
  },
  "bitcrush": {
    signature: "(bitcrush N)",
    description: "Generate a quantization staircase curve with 2^N steps for use with (fx :waveshape). N is bit depth (1–16). Low N = aggressive quantization = lo-fi grit.",
    example: "(fx :waveshape :curve (bitcrush 4))",
  },
  "load-plugin": {
    signature: '(load-plugin "url")',
    description: "Load a REPuLse plugin from a URL (visual or effect).",
    example: '(load-plugin "/plugins/reverb.js")',
  },

  // --- Arrangement ---
  "arrange": {
    signature: "(arrange [[pat cycles] ...])",
    description: "Sequence patterns by duration. Each [pattern cycles] pair plays for the given number of cycles.",
    example: "(arrange [[(seq :bd :sd) 4] [(seq :hh :oh) 2]])",
  },
  "play-scenes": {
    signature: "(play-scenes [pat ...])",
    description: "Play patterns as sequential 1-cycle scenes.",
    example: "(play-scenes [(seq :bd :sd) (seq :hh :oh)])",
  },

  // --- Special forms ---
  "def": {
    signature: "(def name value)",
    description: "Bind a name in the global environment. The name persists across evaluations.",
    example: "(def kick (seq :bd :_ :bd :_))",
  },
  "let": {
    signature: "(let [name val ...] body)",
    description: "Create local bindings. Names are only visible inside the let body.",
    example: "(let [x (seq :bd :sd)] (fast 2 x))",
  },
  "fn": {
    signature: "(fn [params ...] body)",
    description: "Create an anonymous function.",
    example: "(fn [p] (fast 2 p))",
  },
  "lambda": {
    signature: "(lambda [params ...] body)",
    description: "Create an anonymous function (alias for fn).",
    example: "(lambda [p] (fast 2 p))",
  },
  "if": {
    signature: "(if condition then else)",
    description: "Conditional expression. Evaluates then if condition is truthy, else otherwise.",
    example: "(if (> x 0) :bd :sd)",
  },
  "do": {
    signature: "(do expr ...)",
    description: "Evaluate expressions in sequence, return the value of the last one.",
    example: "(do (def x 1) (+ x 2))",
  },

  // --- Demo & Tutorial ---
  "demo": {
    signature: "(demo :name) or (demo)",
    description: "Load a starter template into the editor and play it. With no arguments, lists available demos.",
    example: "(demo :techno)",
  },
  "tutorial": {
    signature: "(tutorial) or (tutorial n)",
    description: "Load tutorial chapter n into the editor without auto-playing. Defaults to chapter 1. Press Alt+Enter to hear it.",
    example: "(tutorial 3)",
  },
  "reset!": {
    signature: "(reset!)",
    description: "Stop playback, clear all persisted state (editor, BPM, effects, bank, sample sources, mute/solo, MIDI mappings), and reload the page with a fresh demo. Use when you want a clean slate.",
    example: "(reset!)",
  },

  // --- General envelopes ---
  "env": {
    signature: "(env levels times) or (env levels times curves)",
    description: "Construct a general envelope descriptor from breakpoint levels and segment durations. Pass to env-gen inside a defsynth body. Supported curves: :lin :exp :sin :welch :step or a positive number.",
    example: "(env [0 1 0.3 0] [0.01 0.1 0.5] [:lin :exp :exp])",
  },
  "env-gen": {
    signature: "(env-gen env-data signal)",
    description: "Apply a general envelope (created with env) to a UGen signal. Returns the enveloped signal with duration equal to the total envelope time.",
    example: "(-> (sin freq) (env-gen (env [0 1 0] [0.01 0.5] [:lin :exp])))",
  },

  // --- Snippet library ---
  "snippet": {
    signature: "(snippet :id)",
    description: "Insert a snippet from the curated library by ID. The snippet code is appended to the editor and the session is hot-swapped with (upd). Call (snippet) with no args to list all available IDs.",
    example: "(snippet :four-on-the-floor)",
  },

  // --- Bus system ---
  "bus": {
    signature: "(bus :name) or (bus :name :control|:audio)",
    description: "Declare a named bus. :control (default) uses a ConstantSourceNode — ideal for LFOs and modulation signals. :audio uses a GainNode for mixing audio-rate signals.",
    example: "(bus :lfo :control)",
  },
  "out": {
    signature: "(out :bus-name signal)",
    description: "Write a UGen signal to a named bus. Use inside defsynth bodies. Re-triggers replace the previous connection for the same synth→bus pair.",
    example: "(out :lfo (sin 4))",
  },
  "in": {
    signature: "(in :bus-name)",
    description: "Read from a named bus as a UGen audio source. Returns silence and logs a warning if the bus does not exist.",
    example: "(lpf (* 2000 (in :lfo)) (saw freq))",
  },
  "kr": {
    signature: "(kr rate signal)",
    description: "Control-rate pass-through. In Web Audio, control-rate is not separately exposed — this is an informational wrapper that returns the signal unchanged.",
    example: "(kr 100 (in :lfo))",
  },

  // --- Session ---
  "help-export": {
    signature: "(help-export)",
    description: "Return the live session state as a map. Fields: :bpm (number), :tracks (keyword→{:fx [...]}), :muted (vector of strings), :fx (global FX chain, vector of {:name :params :bypassed}), :bank, :sources. Access per-track FX with (:fx (:kick (:tracks (help-export)))).",
    example: "(:kick (:tracks (help-export)))",
  },

  // --- AI assistant ---
  "ai": {
    signature: "(ai) or (ai \"prompt\")",
    description: "Open the AI assistant panel. With no args, just opens the panel. With a string argument, opens the panel and immediately sends the prompt.\n\nSupported providers (configured in ⚙ settings):\n  anthropic — claude-sonnet-4-6 (default)\n  openai    — gpt-4o (default)\n  google    — gemini-2.0-flash (default)\n  groq      — llama-3.3-70b-versatile (Groq cloud, fast Llama)\n  xai       — grok-3 (xAI / Grok; key from console.x.ai)\n\nAPI keys are stored in localStorage; requests are relayed through the /api/ai-stream proxy, which never logs or stores them. When AI is disabled, returns an informational string.",
    example: "(ai \"Give me a euclidean kick pattern at 130 bpm\")",
  },
};

/**
 * Find a BuiltinName or Symbol node at the given position.
 * Returns { from, to, word } or null.
 */
function wordAt(state, pos) {
  const tree = syntaxTree(state);
  let node = tree.resolveInner(pos, 0);
  while (node) {
    if (node.name === "BuiltinName" || node.name === "Symbol") {
      const word = state.sliceDoc(node.from, node.to);
      return { from: node.from, to: node.to, word };
    }
    if (node.name === "List" || node.name === "Vector" || node.name === "Program") break;
    node = node.parent;
  }
  return null;
}

/**
 * CM6 hoverTooltip provider for REPuLse-Lisp built-ins.
 */
export const lispHoverTooltip = hoverTooltip((view, pos) => {
  const hit = wordAt(view.state, pos);
  if (!hit) return null;

  const doc = DOCS[hit.word];
  if (!doc) return null;

  return {
    pos: hit.from,
    end: hit.to,
    above: true,
    create() {
      const container = document.createElement("div");
      container.className = "repulse-hover-doc";

      const sig = document.createElement("div");
      sig.className = "repulse-hover-sig";
      sig.textContent = doc.signature;
      container.appendChild(sig);

      const desc = document.createElement("div");
      desc.className = "repulse-hover-desc";
      desc.textContent = doc.description;
      container.appendChild(desc);

      if (doc.example) {
        const ex = document.createElement("div");
        ex.className = "repulse-hover-example";
        ex.textContent = doc.example;
        container.appendChild(ex);
      }

      return { dom: container };
    },
  };
});
