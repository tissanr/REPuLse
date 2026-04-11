/**
 * @fileoverview Closure Compiler externs for @lezer/lr's LRParser.deserialize spec.
 *
 * Shadow-cljs compiles source JS files (parser.js) with Closure advanced
 * optimisation, but npm modules (@lezer/lr) are bundled separately and NOT
 * advanced-compiled.  Closure therefore renames the spec object's property
 * names (e.g. nodeNames → a) while @lezer/lr still accesses them by their
 * original names → undefined → crash.
 *
 * Declaring the spec properties here as externs prevents Closure from
 * renaming them, so both sides agree on the property names at runtime.
 *
 * @externs
 */

/** @type {!Object} */
var LRParserSpec = {
  version: 0,
  states: '',
  stateData: '',
  goto: '',
  nodeNames: '',
  maxTerm: 0,
  skippedNodes: [],
  repeatNodeCount: 0,
  nodeProps: [],
  tokenData: '',
  tokenizers: [],
  topRules: {},
  tokenPrec: 0,
  contextHash: 0,
  specialized: [],
  dynamicPrecedences: {},
  dialects: {}
};
