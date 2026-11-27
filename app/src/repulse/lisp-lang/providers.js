// Runtime value providers — set by ClojureScript, read by completion sources.
// Setters are called once from app.cljs init; lambdas close over CLJS atoms
// so every call returns the current live state (banks load async after startup).

let _getBankNames = () => [];
let _getFxNames   = () => [];
let _getVoiceNames = () => [];
let _getDrumNames  = () => [];

export function setBankNamesProvider(fn) { _getBankNames = fn; }
export function setFxNamesProvider(fn)   { _getFxNames   = fn; }
export function getBankNames() { return _getBankNames(); }
export function getFxNames()   { return _getFxNames();   }
// Phase SYN6: built-in synth voices and kit drums, from repulse.voices.
export function setVoiceNamesProvider(fn) { _getVoiceNames = fn; }
export function setDrumNamesProvider(fn)  { _getDrumNames  = fn; }
export function getVoiceNames() { return _getVoiceNames(); }
export function getDrumNames()  { return _getDrumNames();  }
