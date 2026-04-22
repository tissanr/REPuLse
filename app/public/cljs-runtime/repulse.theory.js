goog.provide('repulse.theory');
repulse.theory.note_semitones = new cljs.core.PersistentArrayMap(null, 7, ["c",(0),"d",(2),"e",(4),"f",(5),"g",(7),"a",(9),"b",(11)], null);
/**
 * True if kw looks like a note name: a letter a–g, optional accidental (s=sharp, b=flat),
 * and an optional octave number (defaults to 4 if omitted).
 * Examples: :c4, :eb3, :fs5, :bb4, :cs-1, :a, :g, :bb.
 */
repulse.theory.note_keyword_QMARK_ = (function repulse$theory$note_keyword_QMARK_(kw){
return (((kw instanceof cljs.core.Keyword)) && (cljs.core.boolean$(cljs.core.re_matches(/[a-g][sb]?(-?\d+)?/,cljs.core.name(kw)))));
});
/**
 * Convert a note keyword to a MIDI note number.
 * Convention: C4 = 60, A4 = 69.
 * Accidentals: s = sharp (+1), b = flat (−1).
 * Octave defaults to 4 if omitted.
 * Examples: :c4 → 60, :a4 → 69, :a → 69, :eb3 → 51, :fs5 → 78, :bb4 → 70.
 */
repulse.theory.note__GT_midi = (function repulse$theory$note__GT_midi(kw){
var vec__8106 = cljs.core.re_matches(/([a-g])([sb])?(-?\d+)?/,cljs.core.name(kw));
var _ = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8106,(0),null);
var letter = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8106,(1),null);
var acc = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8106,(2),null);
var oct_str = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8106,(3),null);
var semitone = cljs.core.get.cljs$core$IFn$_invoke$arity$3(repulse.theory.note_semitones,letter,(0));
var accidental = (function (){var G__8109 = acc;
switch (G__8109) {
case "s":
return (1);

break;
case "b":
return (-1);

break;
default:
return (0);

}
})();
var octave = (cljs.core.truth_(oct_str)?parseInt(oct_str,(10)):(4));
return ((semitone + accidental) + ((octave + (1)) * (12)));
});
/**
 * Convert a MIDI note number to a frequency in Hz.
 * Uses equal temperament with A4 (MIDI 69) = 440 Hz.
 */
repulse.theory.midi__GT_hz = (function repulse$theory$midi__GT_hz(midi){
return (440.0 * Math.pow((2),((midi - (69)) / (12))));
});
/**
 * Convert a note keyword directly to Hz. (:c4 → 261.63, :a4 → 440.0)
 */
repulse.theory.note__GT_hz = (function repulse$theory$note__GT_hz(kw){
return repulse.theory.midi__GT_hz(repulse.theory.note__GT_midi(kw));
});
repulse.theory.interval__GT_semitones = cljs.core.PersistentHashMap.fromArrays([new cljs.core.Keyword(null,"b2","b2",1108940514),(7),(1),(4),new cljs.core.Keyword(null,"bb7","bb7",1279883373),new cljs.core.Keyword(null,"s4","s4",-2002104499),(6),new cljs.core.Keyword(null,"b7","b7",-1868108045),(3),(2),new cljs.core.Keyword(null,"b3","b3",1128981270),(5),new cljs.core.Keyword(null,"b6","b6",1762223416),new cljs.core.Keyword(null,"s5","s5",25049692),new cljs.core.Keyword(null,"b5","b5",-1961609154),(8)],[(1),(11),(0),(5),(9),(6),(9),(10),(4),(2),(3),(7),(8),(8),(6),(12)]);
/**
 * Convert a vector of interval names to semitone offsets.
 */
repulse.theory.resolve_intervals = (function repulse$theory$resolve_intervals(ivs){
return cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__8110_SHARP_){
return cljs.core.get.cljs$core$IFn$_invoke$arity$3(repulse.theory.interval__GT_semitones,p1__8110_SHARP_,(0));
}),ivs);
});
repulse.theory.scale_intervals = cljs.core.PersistentHashMap.fromArrays([new cljs.core.Keyword(null,"locrian","locrian",-1730875806),new cljs.core.Keyword(null,"blues","blues",474831586),new cljs.core.Keyword(null,"ionian","ionian",1354569383),new cljs.core.Keyword(null,"dorian","dorian",1704789324),new cljs.core.Keyword(null,"mixolydian","mixolydian",590368684),new cljs.core.Keyword(null,"major","major",-27376078),new cljs.core.Keyword(null,"lydian","lydian",-1174357037),new cljs.core.Keyword(null,"phrygian","phrygian",175263412),new cljs.core.Keyword(null,"minor-pentatonic","minor-pentatonic",2096817591),new cljs.core.Keyword(null,"minor","minor",-608536071),new cljs.core.Keyword(null,"aeolian","aeolian",-1281514341),new cljs.core.Keyword(null,"pentatonic","pentatonic",1003535103)],[new cljs.core.PersistentVector(null, 7, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),new cljs.core.Keyword(null,"b2","b2",1108940514),new cljs.core.Keyword(null,"b3","b3",1128981270),(4),new cljs.core.Keyword(null,"b5","b5",-1961609154),new cljs.core.Keyword(null,"b6","b6",1762223416),new cljs.core.Keyword(null,"b7","b7",-1868108045)], null),new cljs.core.PersistentVector(null, 6, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),new cljs.core.Keyword(null,"b3","b3",1128981270),(4),new cljs.core.Keyword(null,"s4","s4",-2002104499),(5),new cljs.core.Keyword(null,"b7","b7",-1868108045)], null),new cljs.core.PersistentVector(null, 7, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(2),(3),(4),(5),(6),(7)], null),new cljs.core.PersistentVector(null, 7, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(2),new cljs.core.Keyword(null,"b3","b3",1128981270),(4),(5),(6),new cljs.core.Keyword(null,"b7","b7",-1868108045)], null),new cljs.core.PersistentVector(null, 7, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(2),(3),(4),(5),(6),new cljs.core.Keyword(null,"b7","b7",-1868108045)], null),new cljs.core.PersistentVector(null, 7, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(2),(3),(4),(5),(6),(7)], null),new cljs.core.PersistentVector(null, 7, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(2),(3),new cljs.core.Keyword(null,"s4","s4",-2002104499),(5),(6),(7)], null),new cljs.core.PersistentVector(null, 7, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),new cljs.core.Keyword(null,"b2","b2",1108940514),new cljs.core.Keyword(null,"b3","b3",1128981270),(4),(5),new cljs.core.Keyword(null,"b6","b6",1762223416),new cljs.core.Keyword(null,"b7","b7",-1868108045)], null),new cljs.core.PersistentVector(null, 5, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),new cljs.core.Keyword(null,"b3","b3",1128981270),(4),(5),new cljs.core.Keyword(null,"b7","b7",-1868108045)], null),new cljs.core.PersistentVector(null, 7, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(2),new cljs.core.Keyword(null,"b3","b3",1128981270),(4),(5),new cljs.core.Keyword(null,"b6","b6",1762223416),new cljs.core.Keyword(null,"b7","b7",-1868108045)], null),new cljs.core.PersistentVector(null, 7, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(2),new cljs.core.Keyword(null,"b3","b3",1128981270),(4),(5),new cljs.core.Keyword(null,"b6","b6",1762223416),new cljs.core.Keyword(null,"b7","b7",-1868108045)], null),new cljs.core.PersistentVector(null, 5, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(2),(3),(5),(6)], null)]);
repulse.theory.chord_intervals = cljs.core.PersistentHashMap.fromArrays([new cljs.core.Keyword(null,"m7b5","m7b5",-1015394654),new cljs.core.Keyword(null,"dim7","dim7",627538437),new cljs.core.Keyword(null,"dim","dim",-497244536),new cljs.core.Keyword(null,"major7","major7",1132243338),new cljs.core.Keyword(null,"major","major",-27376078),new cljs.core.Keyword(null,"dom7","dom7",-1983894251),new cljs.core.Keyword(null,"aug7","aug7",1637340246),new cljs.core.Keyword(null,"maj7s11","maj7s11",1310291254),new cljs.core.Keyword(null,"sus2","sus2",-1622612903),new cljs.core.Keyword(null,"minor","minor",-608536071),new cljs.core.Keyword(null,"sus4","sus4",1329265307),new cljs.core.Keyword(null,"minor7","minor7",939616733),new cljs.core.Keyword(null,"aug","aug",-172132737)],[new cljs.core.PersistentVector(null, 4, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),new cljs.core.Keyword(null,"b3","b3",1128981270),new cljs.core.Keyword(null,"b5","b5",-1961609154),new cljs.core.Keyword(null,"b7","b7",-1868108045)], null),new cljs.core.PersistentVector(null, 4, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),new cljs.core.Keyword(null,"b3","b3",1128981270),new cljs.core.Keyword(null,"b5","b5",-1961609154),new cljs.core.Keyword(null,"bb7","bb7",1279883373)], null),new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),new cljs.core.Keyword(null,"b3","b3",1128981270),new cljs.core.Keyword(null,"b5","b5",-1961609154)], null),new cljs.core.PersistentVector(null, 4, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(3),(5),(7)], null),new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(3),(5)], null),new cljs.core.PersistentVector(null, 4, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(3),(5),new cljs.core.Keyword(null,"b7","b7",-1868108045)], null),new cljs.core.PersistentVector(null, 4, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(3),new cljs.core.Keyword(null,"s5","s5",25049692),new cljs.core.Keyword(null,"b7","b7",-1868108045)], null),new cljs.core.PersistentVector(null, 5, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(3),new cljs.core.Keyword(null,"s4","s4",-2002104499),(5),(7)], null),new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(2),(5)], null),new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),new cljs.core.Keyword(null,"b3","b3",1128981270),(5)], null),new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(4),(5)], null),new cljs.core.PersistentVector(null, 4, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),new cljs.core.Keyword(null,"b3","b3",1128981270),(5),new cljs.core.Keyword(null,"b7","b7",-1868108045)], null),new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(3),new cljs.core.Keyword(null,"s5","s5",25049692)], null)]);
/**
 * Map scale degree integers in pat to Hz frequencies.
 * Degrees are one-indexed from the root (1 = root, 2 = second, …); values
 * outside [1, n] wrap into higher/lower octaves
 * (e.g. degree 8 in a 7-note scale = root + 1 octave).
 * 
 * (scale :minor :c4 (seq 1 3 5 8))
 * (scale :pentatonic :g3 (fast 2 (seq 1 2 3 4 5)))
 */
repulse.theory.scale = (function repulse$theory$scale(scale_kw,root,pat){
var root_midi = repulse.theory.note__GT_midi(root);
var semitones = repulse.theory.resolve_intervals(cljs.core.get.cljs$core$IFn$_invoke$arity$3(repulse.theory.scale_intervals,scale_kw,new cljs.core.PersistentVector(null, 7, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(2),(3),(4),(5),(6),(7)], null)));
var n = cljs.core.count(semitones);
var degree__GT_hz = (function (degree){
var degree__$1 = ((degree | (0)) - (1));
var oct = (Math.floor((degree__$1 / n)) | (0));
var idx = (degree__$1 - (oct * n));
return repulse.theory.midi__GT_hz(((root_midi + (oct * (12))) + cljs.core.nth.cljs$core$IFn$_invoke$arity$2(semitones,idx)));
});
return repulse.core.fmap(degree__GT_hz,pat);
});
/**
 * Return a stacked pattern of the chord tones as Hz values.
 * Each tone is a (pure hz) pattern lasting one full cycle.
 * Optional source map is attached to every event for editor highlighting.
 * 
 * (chord :major :c4)    ; C E G stacked
 * (chord :m7b5 :b3)     ; half-diminished on B3
 */
repulse.theory.chord = (function repulse$theory$chord(var_args){
var G__8116 = arguments.length;
switch (G__8116) {
case 2:
return repulse.theory.chord.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
case 3:
return repulse.theory.chord.cljs$core$IFn$_invoke$arity$3((arguments[(0)]),(arguments[(1)]),(arguments[(2)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.theory.chord.cljs$core$IFn$_invoke$arity$2 = (function (chord_kw,root){
return repulse.theory.chord.cljs$core$IFn$_invoke$arity$3(chord_kw,root,null);
}));

(repulse.theory.chord.cljs$core$IFn$_invoke$arity$3 = (function (chord_kw,root,source){
var root_midi = repulse.theory.note__GT_midi(root);
var semitones = repulse.theory.resolve_intervals(cljs.core.get.cljs$core$IFn$_invoke$arity$3(repulse.theory.chord_intervals,chord_kw,new cljs.core.PersistentVector(null, 3, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(3),(5)], null)));
var freqs = cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p1__8112_SHARP_){
return repulse.theory.midi__GT_hz((root_midi + p1__8112_SHARP_));
}),semitones);
return repulse.core.stack_STAR_(cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__8113_SHARP_){
return repulse.core.pure.cljs$core$IFn$_invoke$arity$2(p1__8113_SHARP_,source);
}),freqs));
}));

(repulse.theory.chord.cljs$lang$maxFixedArity = 3);

/**
 * Shift note values in pat up or down by n semitones.
 * Works on Hz numbers, note keywords (:c4, :eb3, …), and parameter maps
 * with a :note key. Non-note keywords (drums, rests) are passed through.
 * 
 * (transpose 12 (seq :c4 :e4 :g4))   ; up one octave
 * (transpose -7 (scale :major :c5 (seq 0 1 2 3)))
 */
repulse.theory.transpose = (function repulse$theory$transpose(semitones,pat){
var ratio = Math.pow((2),(semitones / (12)));
var shift = (function (v){
if(typeof v === 'number'){
return (v * ratio);
} else {
if(repulse.theory.note_keyword_QMARK_(v)){
return (repulse.theory.note__GT_hz(v) * ratio);
} else {
return v;

}
}
});
return repulse.core.fmap((function (v){
if(cljs.core.truth_((function (){var and__5000__auto__ = cljs.core.map_QMARK_(v);
if(and__5000__auto__){
return new cljs.core.Keyword(null,"note","note",1426297904).cljs$core$IFn$_invoke$arity$1(v);
} else {
return and__5000__auto__;
}
})())){
return cljs.core.update.cljs$core$IFn$_invoke$arity$3(v,new cljs.core.Keyword(null,"note","note",1426297904),shift);
} else {
return shift(v);
}
}),pat);
});

//# sourceMappingURL=repulse.theory.js.map
