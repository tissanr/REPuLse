var _getBankNames$$module$repulse$lisp_lang$providers = () => [];
var _getFxNames$$module$repulse$lisp_lang$providers = () => [];
function setBankNamesProvider$$module$repulse$lisp_lang$providers(fn) {
  _getBankNames$$module$repulse$lisp_lang$providers = fn;
}
function setFxNamesProvider$$module$repulse$lisp_lang$providers(fn) {
  _getFxNames$$module$repulse$lisp_lang$providers = fn;
}
function getBankNames$$module$repulse$lisp_lang$providers() {
  return _getBankNames$$module$repulse$lisp_lang$providers();
}
function getFxNames$$module$repulse$lisp_lang$providers() {
  return _getFxNames$$module$repulse$lisp_lang$providers();
}
/** @const */ 
var module$repulse$lisp_lang$providers = {};
/** @const */ 
module$repulse$lisp_lang$providers.getBankNames = getBankNames$$module$repulse$lisp_lang$providers;
/** @const */ 
module$repulse$lisp_lang$providers.getFxNames = getFxNames$$module$repulse$lisp_lang$providers;
/** @const */ 
module$repulse$lisp_lang$providers.setBankNamesProvider = setBankNamesProvider$$module$repulse$lisp_lang$providers;
/** @const */ 
module$repulse$lisp_lang$providers.setFxNamesProvider = setFxNamesProvider$$module$repulse$lisp_lang$providers;

$CLJS.module$repulse$lisp_lang$providers=module$repulse$lisp_lang$providers;
//# sourceMappingURL=module$repulse$lisp_lang$providers.js.map
