goog.provide('repulse.lisp.mini');
/**
 * Convert a floating-point number to a rational [n d].
 * Uses denominator 100000 — sufficient precision for music timing.
 */
repulse.lisp.mini.float__GT_rat = (function repulse$lisp$mini$float__GT_rat(f){
var n = (Math.round((f * (100000))) | (0));
return repulse.core.rat.cljs$core$IFn$_invoke$arity$2(n,(100000));
});
repulse.lisp.mini.whitespace_QMARK_ = (function repulse$lisp$mini$whitespace_QMARK_(ch){
return cljs.core.contains_QMARK_(new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 4, [" ",null,"\t",null,"\n",null,"\r",null], null), null),ch);
});
repulse.lisp.mini.special_QMARK_ = (function repulse$lisp$mini$special_QMARK_(ch){
return cljs.core.contains_QMARK_(new cljs.core.PersistentHashSet(null, new cljs.core.PersistentArrayMap(null, 8, ["@",null,"*",null,":",null,"[",null,"<",null,"]",null,">",null,"?",null], null), null),ch);
});
/**
 * Scan a mini-notation string into a flat sequence of tokens.
 * :atom tokens carry :str-from/:str-to — character offsets within s.
 */
repulse.lisp.mini.tokenise = (function repulse$lisp$mini$tokenise(s){
var i = (0);
var tokens = cljs.core.PersistentVector.EMPTY;
while(true){
if((i >= cljs.core.count(s))){
return tokens;
} else {
var ch = cljs.core.nth.cljs$core$IFn$_invoke$arity$2(s,i);
if(repulse.lisp.mini.whitespace_QMARK_(ch)){
var G__8218 = (i + (1));
var G__8219 = tokens;
i = G__8218;
tokens = G__8219;
continue;
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(ch,"[")){
var G__8220 = (i + (1));
var G__8221 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(tokens,new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"open-bracket","open-bracket",1003608237)], null));
i = G__8220;
tokens = G__8221;
continue;
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(ch,"]")){
var G__8223 = (i + (1));
var G__8224 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(tokens,new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"close-bracket","close-bracket",335972294)], null));
i = G__8223;
tokens = G__8224;
continue;
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(ch,"<")){
var G__8226 = (i + (1));
var G__8227 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(tokens,new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"open-angle","open-angle",-904519238)], null));
i = G__8226;
tokens = G__8227;
continue;
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(ch,">")){
var G__8229 = (i + (1));
var G__8230 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(tokens,new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"close-angle","close-angle",-439331248)], null));
i = G__8229;
tokens = G__8230;
continue;
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(ch,"*")){
var G__8232 = (i + (1));
var G__8233 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(tokens,new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"star","star",279424429)], null));
i = G__8232;
tokens = G__8233;
continue;
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(ch,"?")){
var G__8234 = (i + (1));
var G__8235 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(tokens,new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"question","question",-1411720117)], null));
i = G__8234;
tokens = G__8235;
continue;
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(ch,":")){
var G__8236 = (i + (1));
var G__8237 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(tokens,new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"colon","colon",-965200945)], null));
i = G__8236;
tokens = G__8237;
continue;
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(ch,"@")){
var G__8238 = (i + (1));
var G__8239 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(tokens,new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"at","at",1476951349)], null));
i = G__8238;
tokens = G__8239;
continue;
} else {
var end = (function (){var j = i;
while(true){
if((((j >= cljs.core.count(s))) || (((repulse.lisp.mini.whitespace_QMARK_(cljs.core.nth.cljs$core$IFn$_invoke$arity$2(s,j))) || (repulse.lisp.mini.special_QMARK_(cljs.core.nth.cljs$core$IFn$_invoke$arity$2(s,j))))))){
return j;
} else {
var G__8241 = (j + (1));
j = G__8241;
continue;
}
break;
}
})();
var word = cljs.core.subs.cljs$core$IFn$_invoke$arity$3(s,i,end);
var G__8242 = end;
var G__8243 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(tokens,new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"atom","atom",-397043653),new cljs.core.Keyword(null,"text","text",-1790561697),word,new cljs.core.Keyword(null,"str-from","str-from",452772628),i,new cljs.core.Keyword(null,"str-to","str-to",182633460),end], null));
i = G__8242;
tokens = G__8243;
continue;

}
}
}
}
}
}
}
}
}
}
break;
}
});
repulse.lisp.mini.peek_token = (function repulse$lisp$mini$peek_token(tokens,pos){
if((pos < cljs.core.count(tokens))){
return cljs.core.nth.cljs$core$IFn$_invoke$arity$2(tokens,pos);
} else {
return null;
}
});
/**
 * Parse a string as a number. Returns nil if not a number.
 */
repulse.lisp.mini.parse_number_text = (function repulse$lisp$mini$parse_number_text(s){
var n = parseFloat(s);
if(cljs.core.truth_(isNaN(n))){
return null;
} else {
return n;
}
});
/**
 * True if s looks like a note name: a-g, optional accidental (s/b), then digit(s).
 */
repulse.lisp.mini.note_name_QMARK_ = (function repulse$lisp$mini$note_name_QMARK_(s){
return cljs.core.boolean$(cljs.core.re_matches(/[a-g][sb]?\d+/,s));
});
/**
 * Convert a raw text token to its REPuLse value: keyword, number, or note keyword.
 */
repulse.lisp.mini.atom_value = (function repulse$lisp$mini$atom_value(text){
if(((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(text,"~")) || (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(text,"_")))){
return new cljs.core.Keyword(null,"_","_",1453416199);
} else {
if(cljs.core.truth_(repulse.lisp.mini.parse_number_text(text))){
return repulse.lisp.mini.parse_number_text(text);
} else {
if(repulse.lisp.mini.note_name_QMARK_(text)){
return cljs.core.keyword.cljs$core$IFn$_invoke$arity$1(text);
} else {
return cljs.core.keyword.cljs$core$IFn$_invoke$arity$1(text);

}
}
}
});
/**
 * Parse a single atom token (not brackets/angles), then consume any suffixes.
 * Returns [ast-node next-pos].
 * :str-from/:str-to on the node are the character offsets of the base atom within
 * the mini-notation string (used to compute per-token highlight ranges).
 */
repulse.lisp.mini.parse_atom = (function repulse$lisp$mini$parse_atom(tokens,pos){
var tok = cljs.core.nth.cljs$core$IFn$_invoke$arity$2(tokens,pos);
var base_val = repulse.lisp.mini.atom_value(new cljs.core.Keyword(null,"text","text",-1790561697).cljs$core$IFn$_invoke$arity$1(tok));
var str_from = new cljs.core.Keyword(null,"str-from","str-from",452772628).cljs$core$IFn$_invoke$arity$1(tok);
var str_to = new cljs.core.Keyword(null,"str-to","str-to",182633460).cljs$core$IFn$_invoke$arity$1(tok);
var pos__$1 = (pos + (1));
var node = new cljs.core.PersistentArrayMap(null, 5, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"atom","atom",-397043653),new cljs.core.Keyword(null,"value","value",305978217),base_val,new cljs.core.Keyword(null,"weight","weight",-1262796205),(1),new cljs.core.Keyword(null,"str-from","str-from",452772628),str_from,new cljs.core.Keyword(null,"str-to","str-to",182633460),str_to], null);
var p = pos__$1;
while(true){
var tok__$1 = repulse.lisp.mini.peek_token(tokens,p);
if(cljs.core.truth_((function (){var and__5000__auto__ = tok__$1;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(tok__$1),new cljs.core.Keyword(null,"star","star",279424429));
} else {
return and__5000__auto__;
}
})())){
var next_tok = repulse.lisp.mini.peek_token(tokens,(p + (1)));
if(cljs.core.truth_((function (){var and__5000__auto__ = next_tok;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(next_tok),new cljs.core.Keyword(null,"atom","atom",-397043653));
} else {
return and__5000__auto__;
}
})())){
var n = (function (){var or__5002__auto__ = repulse.lisp.mini.parse_number_text(new cljs.core.Keyword(null,"text","text",-1790561697).cljs$core$IFn$_invoke$arity$1(next_tok));
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (1);
}
})();
var G__8248 = new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"repeat","repeat",832692087),new cljs.core.Keyword(null,"child","child",623967545),node,new cljs.core.Keyword(null,"times","times",1671571467),(n | (0)),new cljs.core.Keyword(null,"weight","weight",-1262796205),new cljs.core.Keyword(null,"weight","weight",-1262796205).cljs$core$IFn$_invoke$arity$1(node)], null);
var G__8249 = (p + (2));
node = G__8248;
p = G__8249;
continue;
} else {
var G__8251 = new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"repeat","repeat",832692087),new cljs.core.Keyword(null,"child","child",623967545),node,new cljs.core.Keyword(null,"times","times",1671571467),(2),new cljs.core.Keyword(null,"weight","weight",-1262796205),new cljs.core.Keyword(null,"weight","weight",-1262796205).cljs$core$IFn$_invoke$arity$1(node)], null);
var G__8252 = (p + (1));
node = G__8251;
p = G__8252;
continue;
}
} else {
if(cljs.core.truth_((function (){var and__5000__auto__ = tok__$1;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(tok__$1),new cljs.core.Keyword(null,"question","question",-1411720117));
} else {
return and__5000__auto__;
}
})())){
var G__8253 = new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"degrade","degrade",2120271629),new cljs.core.Keyword(null,"child","child",623967545),node,new cljs.core.Keyword(null,"weight","weight",-1262796205),new cljs.core.Keyword(null,"weight","weight",-1262796205).cljs$core$IFn$_invoke$arity$1(node)], null);
var G__8254 = (p + (1));
node = G__8253;
p = G__8254;
continue;
} else {
if(cljs.core.truth_((function (){var and__5000__auto__ = tok__$1;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(tok__$1),new cljs.core.Keyword(null,"colon","colon",-965200945));
} else {
return and__5000__auto__;
}
})())){
var next_tok = repulse.lisp.mini.peek_token(tokens,(p + (1)));
if(cljs.core.truth_((function (){var and__5000__auto__ = next_tok;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(next_tok),new cljs.core.Keyword(null,"atom","atom",-397043653));
} else {
return and__5000__auto__;
}
})())){
var n = (function (){var or__5002__auto__ = repulse.lisp.mini.parse_number_text(new cljs.core.Keyword(null,"text","text",-1790561697).cljs$core$IFn$_invoke$arity$1(next_tok));
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (0);
}
})();
var G__8261 = cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(node,new cljs.core.Keyword(null,"sample-index","sample-index",1397068236),(n | (0)));
var G__8262 = (p + (2));
node = G__8261;
p = G__8262;
continue;
} else {
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [node,p], null);
}
} else {
if(cljs.core.truth_((function (){var and__5000__auto__ = tok__$1;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(tok__$1),new cljs.core.Keyword(null,"at","at",1476951349));
} else {
return and__5000__auto__;
}
})())){
var next_tok = repulse.lisp.mini.peek_token(tokens,(p + (1)));
if(cljs.core.truth_((function (){var and__5000__auto__ = next_tok;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(next_tok),new cljs.core.Keyword(null,"atom","atom",-397043653));
} else {
return and__5000__auto__;
}
})())){
var w = (function (){var or__5002__auto__ = repulse.lisp.mini.parse_number_text(new cljs.core.Keyword(null,"text","text",-1790561697).cljs$core$IFn$_invoke$arity$1(next_tok));
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (1);
}
})();
var G__8267 = cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(node,new cljs.core.Keyword(null,"weight","weight",-1262796205),(w | (0)));
var G__8268 = (p + (2));
node = G__8267;
p = G__8268;
continue;
} else {
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [node,p], null);
}
} else {
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [node,p], null);

}
}
}
}
break;
}
});
/**
 * Parse one element: a bracketed group, an angle-bracket alternation, or an atom.
 */
repulse.lisp.mini.parse_element = (function repulse$lisp$mini$parse_element(tokens,pos){
var tok = repulse.lisp.mini.peek_token(tokens,pos);
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(tok),new cljs.core.Keyword(null,"open-bracket","open-bracket",1003608237))){
var vec__8143 = (function (){var G__8146 = tokens;
var G__8147 = (pos + (1));
return (repulse.lisp.mini.parse_sequence.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.mini.parse_sequence.cljs$core$IFn$_invoke$arity$2(G__8146,G__8147) : repulse.lisp.mini.parse_sequence.call(null,G__8146,G__8147));
})();
var seq_node = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8143,(0),null);
var next_pos = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8143,(1),null);
var close = repulse.lisp.mini.peek_token(tokens,next_pos);
if(cljs.core.truth_((function (){var and__5000__auto__ = close;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(close),new cljs.core.Keyword(null,"close-bracket","close-bracket",335972294));
} else {
return and__5000__auto__;
}
})())){
} else {
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2("Expected ]",new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"mini-parse-error","mini-parse-error",-1083301723)], null));
}

var node = seq_node;
var p = (next_pos + (1));
while(true){
var tok__$1 = repulse.lisp.mini.peek_token(tokens,p);
if(cljs.core.truth_((function (){var and__5000__auto__ = tok__$1;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(tok__$1),new cljs.core.Keyword(null,"star","star",279424429));
} else {
return and__5000__auto__;
}
})())){
var next_tok = repulse.lisp.mini.peek_token(tokens,(p + (1)));
if(cljs.core.truth_((function (){var and__5000__auto__ = next_tok;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(next_tok),new cljs.core.Keyword(null,"atom","atom",-397043653));
} else {
return and__5000__auto__;
}
})())){
var n = (function (){var or__5002__auto__ = repulse.lisp.mini.parse_number_text(new cljs.core.Keyword(null,"text","text",-1790561697).cljs$core$IFn$_invoke$arity$1(next_tok));
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (2);
}
})();
var G__8298 = new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"repeat","repeat",832692087),new cljs.core.Keyword(null,"child","child",623967545),node,new cljs.core.Keyword(null,"times","times",1671571467),(n | (0)),new cljs.core.Keyword(null,"weight","weight",-1262796205),(1)], null);
var G__8299 = (p + (2));
node = G__8298;
p = G__8299;
continue;
} else {
var G__8302 = new cljs.core.PersistentArrayMap(null, 4, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"repeat","repeat",832692087),new cljs.core.Keyword(null,"child","child",623967545),node,new cljs.core.Keyword(null,"times","times",1671571467),(2),new cljs.core.Keyword(null,"weight","weight",-1262796205),(1)], null);
var G__8303 = (p + (1));
node = G__8302;
p = G__8303;
continue;
}
} else {
if(cljs.core.truth_((function (){var and__5000__auto__ = tok__$1;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(tok__$1),new cljs.core.Keyword(null,"question","question",-1411720117));
} else {
return and__5000__auto__;
}
})())){
var G__8306 = new cljs.core.PersistentArrayMap(null, 3, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"degrade","degrade",2120271629),new cljs.core.Keyword(null,"child","child",623967545),node,new cljs.core.Keyword(null,"weight","weight",-1262796205),(1)], null);
var G__8307 = (p + (1));
node = G__8306;
p = G__8307;
continue;
} else {
if(cljs.core.truth_((function (){var and__5000__auto__ = tok__$1;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(tok__$1),new cljs.core.Keyword(null,"at","at",1476951349));
} else {
return and__5000__auto__;
}
})())){
var next_tok = repulse.lisp.mini.peek_token(tokens,(p + (1)));
if(cljs.core.truth_((function (){var and__5000__auto__ = next_tok;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(next_tok),new cljs.core.Keyword(null,"atom","atom",-397043653));
} else {
return and__5000__auto__;
}
})())){
var w = (function (){var or__5002__auto__ = repulse.lisp.mini.parse_number_text(new cljs.core.Keyword(null,"text","text",-1790561697).cljs$core$IFn$_invoke$arity$1(next_tok));
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (1);
}
})();
var G__8315 = cljs.core.assoc.cljs$core$IFn$_invoke$arity$3(node,new cljs.core.Keyword(null,"weight","weight",-1262796205),(w | (0)));
var G__8316 = (p + (2));
node = G__8315;
p = G__8316;
continue;
} else {
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [node,p], null);
}
} else {
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [node,p], null);

}
}
}
break;
}
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(tok),new cljs.core.Keyword(null,"open-angle","open-angle",-904519238))){
var vec__8150 = (function (){var G__8153 = tokens;
var G__8154 = (pos + (1));
return (repulse.lisp.mini.parse_sequence.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.mini.parse_sequence.cljs$core$IFn$_invoke$arity$2(G__8153,G__8154) : repulse.lisp.mini.parse_sequence.call(null,G__8153,G__8154));
})();
var seq_node = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8150,(0),null);
var next_pos = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8150,(1),null);
var close = repulse.lisp.mini.peek_token(tokens,next_pos);
if(cljs.core.truth_((function (){var and__5000__auto__ = close;
if(cljs.core.truth_(and__5000__auto__)){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(close),new cljs.core.Keyword(null,"close-angle","close-angle",-439331248));
} else {
return and__5000__auto__;
}
})())){
} else {
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2("Expected >",new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"mini-parse-error","mini-parse-error",-1083301723)], null));
}

return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"alt","alt",-3214426),new cljs.core.Keyword(null,"children","children",-940561982),new cljs.core.Keyword(null,"children","children",-940561982).cljs$core$IFn$_invoke$arity$1(seq_node)], null),(next_pos + (1))], null);
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(tok),new cljs.core.Keyword(null,"atom","atom",-397043653))){
return repulse.lisp.mini.parse_atom(tokens,pos);
} else {
throw cljs.core.ex_info.cljs$core$IFn$_invoke$arity$2(["Unexpected token: ",cljs.core.pr_str.cljs$core$IFn$_invoke$arity$variadic(cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([tok], 0))].join(''),new cljs.core.PersistentArrayMap(null, 1, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"mini-parse-error","mini-parse-error",-1083301723)], null));

}
}
}
});
/**
 * Parse a sequence of elements until we hit ], >, or end of tokens.
 * Returns [{:type :seq :children [...]} next-pos].
 */
repulse.lisp.mini.parse_sequence = (function repulse$lisp$mini$parse_sequence(tokens,pos){
var children = cljs.core.PersistentVector.EMPTY;
var p = pos;
while(true){
var tok = repulse.lisp.mini.peek_token(tokens,p);
if((((tok == null)) || (((cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(tok),new cljs.core.Keyword(null,"close-bracket","close-bracket",335972294))) || (cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2(new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(tok),new cljs.core.Keyword(null,"close-angle","close-angle",-439331248))))))){
return new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"type","type",1174270348),new cljs.core.Keyword(null,"seq","seq",-1817803783),new cljs.core.Keyword(null,"children","children",-940561982),children], null),p], null);
} else {
var vec__8163 = repulse.lisp.mini.parse_element(tokens,p);
var node = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8163,(0),null);
var next_pos = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8163,(1),null);
var G__8344 = cljs.core.conj.cljs$core$IFn$_invoke$arity$2(children,node);
var G__8345 = next_pos;
children = G__8344;
p = G__8345;
continue;
}
break;
}
});
/**
 * Alternation: on cycle N, play the (mod N count)-th pattern.
 */
repulse.lisp.mini.alt_STAR_ = (function repulse$lisp$mini$alt_STAR_(pats){
var n = cljs.core.count(pats);
return repulse.core.pattern((function (sp){
var cycle = (Math.floor(repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(sp))) | (0));
var idx = cljs.core.mod(cycle,n);
return repulse.core.query(cljs.core.nth.cljs$core$IFn$_invoke$arity$2(pats,idx),sp);
}));
});
/**
 * Query pat within [0,1) and scale/shift event times into [slot-sf, slot-ef).
 * Clips the :part span to [sp-sf, sp-ef). Preserves :source on events.
 */
repulse.lisp.mini.scale_events = (function repulse$lisp$mini$scale_events(pat,slot_sf,slot_ef,sp_sf,sp_ef){
var raw_evs = repulse.core.query(pat,new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"start","start",-355208981),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(0),(1)], null),new cljs.core.Keyword(null,"end","end",-268185958),new cljs.core.PersistentVector(null, 2, 5, cljs.core.PersistentVector.EMPTY_NODE, [(1),(1)], null)], null));
var dur = (slot_ef - slot_sf);
return cljs.core.keep.cljs$core$IFn$_invoke$arity$2((function (ev){
var ws_f = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"whole","whole",-1395468966).cljs$core$IFn$_invoke$arity$1(ev)));
var we_f = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"whole","whole",-1395468966).cljs$core$IFn$_invoke$arity$1(ev)));
var ps_f = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev)));
var pe_f = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"part","part",77757738).cljs$core$IFn$_invoke$arity$1(ev)));
var nws_f = (slot_sf + (ws_f * dur));
var nwe_f = (slot_sf + (we_f * dur));
var nps_f = (slot_sf + (ps_f * dur));
var npe_f = (slot_sf + (pe_f * dur));
var cls_f = (function (){var x__5087__auto__ = nps_f;
var y__5088__auto__ = sp_sf;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
var cle_f = (function (){var x__5090__auto__ = npe_f;
var y__5091__auto__ = sp_ef;
return ((x__5090__auto__ < y__5091__auto__) ? x__5090__auto__ : y__5091__auto__);
})();
if((cls_f < cle_f)){
return cljs.core.assoc.cljs$core$IFn$_invoke$arity$variadic(ev,new cljs.core.Keyword(null,"whole","whole",-1395468966),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"start","start",-355208981),repulse.lisp.mini.float__GT_rat(nws_f),new cljs.core.Keyword(null,"end","end",-268185958),repulse.lisp.mini.float__GT_rat(nwe_f)], null),cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([new cljs.core.Keyword(null,"part","part",77757738),new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"start","start",-355208981),repulse.lisp.mini.float__GT_rat(cls_f),new cljs.core.Keyword(null,"end","end",-268185958),repulse.lisp.mini.float__GT_rat(cle_f)], null)], 0));
} else {
return null;
}
}),raw_evs);
});
/**
 * Sequence n patterns: pattern i plays in slot [i/n, (i+1)/n) of each cycle.
 * Queries each child as if it owns [0,1), then scales event times to its slot.
 */
repulse.lisp.mini.seq_of_pats = (function repulse$lisp$mini$seq_of_pats(pats){
var n = cljs.core.count(pats);
return repulse.core.pattern((function (sp){
var sp_sf = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(sp));
var sp_ef = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(sp));
var c0 = (Math.floor(sp_sf) | (0));
var c1 = (Math.ceil(sp_ef) | (0));
return cljs.core.mapcat.cljs$core$IFn$_invoke$arity$variadic((function (c){
return cljs.core.mapcat.cljs$core$IFn$_invoke$arity$variadic((function (p__8173){
var vec__8174 = p__8173;
var i = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8174,(0),null);
var pat = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8174,(1),null);
var slot_sf = (((c * n) + i) / n);
var slot_ef = (((c * n) + (i + (1))) / n);
var ol_sf = (function (){var x__5087__auto__ = slot_sf;
var y__5088__auto__ = sp_sf;
return ((x__5087__auto__ > y__5088__auto__) ? x__5087__auto__ : y__5088__auto__);
})();
var ol_ef = (function (){var x__5090__auto__ = slot_ef;
var y__5091__auto__ = sp_ef;
return ((x__5090__auto__ < y__5091__auto__) ? x__5090__auto__ : y__5091__auto__);
})();
if((ol_sf < ol_ef)){
return repulse.lisp.mini.scale_events(pat,slot_sf,slot_ef,sp_sf,sp_ef);
} else {
return null;
}
}),cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([cljs.core.map_indexed.cljs$core$IFn$_invoke$arity$2(cljs.core.vector,pats)], 0));
}),cljs.core.prim_seq.cljs$core$IFn$_invoke$arity$2([cljs.core.range.cljs$core$IFn$_invoke$arity$2(c0,c1)], 0));
}));
});
/**
 * Proportional sequence: child i plays for (weight_i / total) of the cycle.
 */
repulse.lisp.mini.weighted_seq_STAR_ = (function repulse$lisp$mini$weighted_seq_STAR_(pairs,total_weight){
return repulse.core.pattern((function (sp){
var sp_sf = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"start","start",-355208981).cljs$core$IFn$_invoke$arity$1(sp));
var sp_ef = repulse.core.rat__GT_float(new cljs.core.Keyword(null,"end","end",-268185958).cljs$core$IFn$_invoke$arity$1(sp));
var cycle_start = Math.floor(sp_sf);
var remaining = pairs;
var offset_f = 0.0;
var result = cljs.core.PersistentVector.EMPTY;
while(true){
if(cljs.core.empty_QMARK_(remaining)){
return result;
} else {
var map__8185 = cljs.core.first(remaining);
var map__8185__$1 = cljs.core.__destructure_map(map__8185);
var pat = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8185__$1,new cljs.core.Keyword(null,"pat","pat",-1417570164));
var weight = cljs.core.get.cljs$core$IFn$_invoke$arity$2(map__8185__$1,new cljs.core.Keyword(null,"weight","weight",-1262796205));
var frac = (weight / total_weight);
var slot_sf = (cycle_start + offset_f);
var slot_ef = ((cycle_start + offset_f) + frac);
var G__8388 = cljs.core.rest(remaining);
var G__8389 = (offset_f + frac);
var G__8390 = cljs.core.into.cljs$core$IFn$_invoke$arity$2(result,(((((slot_sf < sp_ef)) && ((slot_ef > sp_sf))))?repulse.lisp.mini.scale_events(pat,slot_sf,slot_ef,sp_sf,sp_ef):cljs.core.PersistentVector.EMPTY));
remaining = G__8388;
offset_f = G__8389;
result = G__8390;
continue;
}
break;
}
}));
});
/**
 * Randomly gate events — each event has a ~50% chance of being dropped.
 */
repulse.lisp.mini.degrade = (function repulse$lisp$mini$degrade(pat){
return repulse.core.pattern((function (sp){
return cljs.core.filter.cljs$core$IFn$_invoke$arity$2((function (_){
return (Math.random() > 0.5);
}),repulse.core.query(pat,sp));
}));
});
/**
 * Compile an AST node to a Pattern.
 * base-offset: absolute editor position after the opening quote of the
 * mini-notation string. When provided, :atom events carry per-token
 * :source ranges so individual tokens flash during playback.
 */
repulse.lisp.mini.compile_ast = (function repulse$lisp$mini$compile_ast(node,base_offset){
var G__8190 = new cljs.core.Keyword(null,"type","type",1174270348).cljs$core$IFn$_invoke$arity$1(node);
var G__8190__$1 = (((G__8190 instanceof cljs.core.Keyword))?G__8190.fqn:null);
switch (G__8190__$1) {
case "atom":
var v = new cljs.core.Keyword(null,"value","value",305978217).cljs$core$IFn$_invoke$arity$1(node);
var source = (cljs.core.truth_((function (){var and__5000__auto__ = base_offset;
if(cljs.core.truth_(and__5000__auto__)){
return new cljs.core.Keyword(null,"str-from","str-from",452772628).cljs$core$IFn$_invoke$arity$1(node);
} else {
return and__5000__auto__;
}
})())?new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"from","from",1815293044),(base_offset + new cljs.core.Keyword(null,"str-from","str-from",452772628).cljs$core$IFn$_invoke$arity$1(node)),new cljs.core.Keyword(null,"to","to",192099007),(base_offset + new cljs.core.Keyword(null,"str-to","str-to",182633460).cljs$core$IFn$_invoke$arity$1(node))], null):null);
if(cljs.core.truth_(new cljs.core.Keyword(null,"sample-index","sample-index",1397068236).cljs$core$IFn$_invoke$arity$1(node))){
return repulse.core.pure.cljs$core$IFn$_invoke$arity$2(new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"bank","bank",-1982531798),v,new cljs.core.Keyword(null,"n","n",562130025),new cljs.core.Keyword(null,"sample-index","sample-index",1397068236).cljs$core$IFn$_invoke$arity$1(node)], null),source);
} else {
return repulse.core.pure.cljs$core$IFn$_invoke$arity$2(v,source);
}

break;
case "rest":
return repulse.core.pure.cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"_","_",1453416199));

break;
case "seq":
var children = new cljs.core.Keyword(null,"children","children",-940561982).cljs$core$IFn$_invoke$arity$1(node);
if((cljs.core.count(children) === (0))){
return repulse.core.pattern((function (_){
return cljs.core.PersistentVector.EMPTY;
}));
} else {
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2((1),cljs.core.count(children))){
var G__8193 = cljs.core.first(children);
var G__8194 = base_offset;
return (repulse.lisp.mini.compile_ast.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.mini.compile_ast.cljs$core$IFn$_invoke$arity$2(G__8193,G__8194) : repulse.lisp.mini.compile_ast.call(null,G__8193,G__8194));
} else {
var total_weight = cljs.core.reduce.cljs$core$IFn$_invoke$arity$2(cljs.core._PLUS_,cljs.core.map.cljs$core$IFn$_invoke$arity$2((function (p1__8186_SHARP_){
var or__5002__auto__ = new cljs.core.Keyword(null,"weight","weight",-1262796205).cljs$core$IFn$_invoke$arity$1(p1__8186_SHARP_);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (1);
}
}),children));
var all_unit_QMARK_ = cljs.core.every_QMARK_((function (p1__8187_SHARP_){
return cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2((1),(function (){var or__5002__auto__ = new cljs.core.Keyword(null,"weight","weight",-1262796205).cljs$core$IFn$_invoke$arity$1(p1__8187_SHARP_);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (1);
}
})());
}),children);
if(all_unit_QMARK_){
return repulse.lisp.mini.seq_of_pats(cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__8188_SHARP_){
return (repulse.lisp.mini.compile_ast.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.mini.compile_ast.cljs$core$IFn$_invoke$arity$2(p1__8188_SHARP_,base_offset) : repulse.lisp.mini.compile_ast.call(null,p1__8188_SHARP_,base_offset));
}),children));
} else {
var pairs = cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (child){
return new cljs.core.PersistentArrayMap(null, 2, [new cljs.core.Keyword(null,"pat","pat",-1417570164),(repulse.lisp.mini.compile_ast.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.mini.compile_ast.cljs$core$IFn$_invoke$arity$2(child,base_offset) : repulse.lisp.mini.compile_ast.call(null,child,base_offset)),new cljs.core.Keyword(null,"weight","weight",-1262796205),(function (){var or__5002__auto__ = new cljs.core.Keyword(null,"weight","weight",-1262796205).cljs$core$IFn$_invoke$arity$1(child);
if(cljs.core.truth_(or__5002__auto__)){
return or__5002__auto__;
} else {
return (1);
}
})()], null);
}),children);
return repulse.lisp.mini.weighted_seq_STAR_(pairs,total_weight);
}

}
}

break;
case "alt":
var children = new cljs.core.Keyword(null,"children","children",-940561982).cljs$core$IFn$_invoke$arity$1(node);
if(cljs.core._EQ_.cljs$core$IFn$_invoke$arity$2((1),cljs.core.count(children))){
var G__8200 = cljs.core.first(children);
var G__8201 = base_offset;
return (repulse.lisp.mini.compile_ast.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.mini.compile_ast.cljs$core$IFn$_invoke$arity$2(G__8200,G__8201) : repulse.lisp.mini.compile_ast.call(null,G__8200,G__8201));
} else {
return repulse.lisp.mini.alt_STAR_(cljs.core.mapv.cljs$core$IFn$_invoke$arity$2((function (p1__8189_SHARP_){
return (repulse.lisp.mini.compile_ast.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.mini.compile_ast.cljs$core$IFn$_invoke$arity$2(p1__8189_SHARP_,base_offset) : repulse.lisp.mini.compile_ast.call(null,p1__8189_SHARP_,base_offset));
}),children));
}

break;
case "repeat":
var child_pat = (function (){var G__8203 = new cljs.core.Keyword(null,"child","child",623967545).cljs$core$IFn$_invoke$arity$1(node);
var G__8204 = base_offset;
return (repulse.lisp.mini.compile_ast.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.mini.compile_ast.cljs$core$IFn$_invoke$arity$2(G__8203,G__8204) : repulse.lisp.mini.compile_ast.call(null,G__8203,G__8204));
})();
var times = new cljs.core.Keyword(null,"times","times",1671571467).cljs$core$IFn$_invoke$arity$1(node);
return repulse.core.fast(times,child_pat);

break;
case "degrade":
return repulse.lisp.mini.degrade((function (){var G__8205 = new cljs.core.Keyword(null,"child","child",623967545).cljs$core$IFn$_invoke$arity$1(node);
var G__8206 = base_offset;
return (repulse.lisp.mini.compile_ast.cljs$core$IFn$_invoke$arity$2 ? repulse.lisp.mini.compile_ast.cljs$core$IFn$_invoke$arity$2(G__8205,G__8206) : repulse.lisp.mini.compile_ast.call(null,G__8205,G__8206));
})());

break;
default:
return repulse.core.pure.cljs$core$IFn$_invoke$arity$1(new cljs.core.Keyword(null,"_","_",1453416199));

}
});
/**
 * Parse a mini-notation string and return a Pattern.
 * (parse "bd sd [hh hh] bd")  → pattern equivalent to (seq :bd :sd (seq :hh :hh) :bd)
 * 
 * base-offset (optional): absolute editor position of the first character
 * inside the string (i.e. one past the opening quote). When provided,
 * each token in the pattern carries a :source range pointing to its exact
 * position in the editor so it highlights individually during playback.
 */
repulse.lisp.mini.parse = (function repulse$lisp$mini$parse(var_args){
var G__8208 = arguments.length;
switch (G__8208) {
case 1:
return repulse.lisp.mini.parse.cljs$core$IFn$_invoke$arity$1((arguments[(0)]));

break;
case 2:
return repulse.lisp.mini.parse.cljs$core$IFn$_invoke$arity$2((arguments[(0)]),(arguments[(1)]));

break;
default:
throw (new Error(["Invalid arity: ",cljs.core.str.cljs$core$IFn$_invoke$arity$1(arguments.length)].join('')));

}
});

(repulse.lisp.mini.parse.cljs$core$IFn$_invoke$arity$1 = (function (s){
return repulse.lisp.mini.parse.cljs$core$IFn$_invoke$arity$2(s,null);
}));

(repulse.lisp.mini.parse.cljs$core$IFn$_invoke$arity$2 = (function (s,base_offset){
var tokens = repulse.lisp.mini.tokenise(s);
var vec__8209 = repulse.lisp.mini.parse_sequence(tokens,(0));
var ast = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8209,(0),null);
var _ = cljs.core.nth.cljs$core$IFn$_invoke$arity$3(vec__8209,(1),null);
return repulse.lisp.mini.compile_ast(ast,base_offset);
}));

(repulse.lisp.mini.parse.cljs$lang$maxFixedArity = 2);


//# sourceMappingURL=repulse.lisp.mini.js.map
