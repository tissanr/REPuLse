/**
 * @fileoverview Closure Compiler externs for @lezer/lr, @lezer/highlight,
 * @codemirror/view, @codemirror/language, @codemirror/state and
 * @codemirror/autocomplete.
 *
 * WHY THIS FILE EXISTS
 * --------------------
 * shadow-cljs compiles source JS files (rainbow.js, index.js, hover.js, etc.)
 * with Closure advanced optimisation.  npm modules (@lezer/lr, @codemirror/*)
 * are bundled separately by shadow-cljs's own module system and are NOT
 * advanced-compiled.  Closure therefore renames property accesses in the
 * source JS files (e.g. ViewPlugin.fromClass → ViewPlugin.Yc) while the
 * actual npm module objects still carry the original names → TypeError at
 * runtime.
 *
 * Declaring every npm-facing property name here as an extern prevents Closure
 * from renaming it, so both sides agree on the name at runtime.
 *
 * HOW TO EXTEND
 * -------------
 * If a new source JS file is added that calls an npm module API not already
 * listed here, add the missing property name with an Object.prototype stub.
 *
 * @externs
 */

// ---------------------------------------------------------------------------
// @lezer/lr — LRParser.deserialize spec fields
// ---------------------------------------------------------------------------
Object.prototype.version;
Object.prototype.states;
Object.prototype.stateData;
Object.prototype.goto;
Object.prototype.nodeNames;
Object.prototype.maxTerm;
Object.prototype.skippedNodes;
Object.prototype.repeatNodeCount;
Object.prototype.nodeProps;
Object.prototype.tokenData;
Object.prototype.tokenizers;
Object.prototype.topRules;
Object.prototype.tokenPrec;
Object.prototype.contextHash;
Object.prototype.specialized;
Object.prototype.dynamicPrecedences;
Object.prototype.dialects;

// ---------------------------------------------------------------------------
// @lezer/lr — LRParser.prototype.configure spec
// ---------------------------------------------------------------------------
Object.prototype.props;

// ---------------------------------------------------------------------------
// @lezer/lr — static methods on LRParser
// ---------------------------------------------------------------------------
Object.prototype.deserialize;
Object.prototype.configure;

// ---------------------------------------------------------------------------
// @lezer/highlight — styleTags / tags
// ---------------------------------------------------------------------------
Object.prototype.lineComment;
Object.prototype.number;
Object.prototype.string;
Object.prototype.keyword;
Object.prototype.variableName;
Object.prototype.paren;
Object.prototype.squareBracket;
Object.prototype.brace;

// ---------------------------------------------------------------------------
// @codemirror/view — ViewPlugin
// ---------------------------------------------------------------------------
Object.prototype.fromClass;
Object.prototype.decorations;
Object.prototype.eventHandlers;
Object.prototype.provide;

// ---------------------------------------------------------------------------
// @codemirror/view — Decoration
// ---------------------------------------------------------------------------
Object.prototype.mark;

// ---------------------------------------------------------------------------
// @codemirror/view — ViewUpdate properties accessed in update() methods
// ---------------------------------------------------------------------------
Object.prototype.docChanged;
Object.prototype.viewportChanged;

// ---------------------------------------------------------------------------
// @codemirror/view — EditorView / EditorState properties
// ---------------------------------------------------------------------------
Object.prototype.state;
Object.prototype.sliceDoc;

// ---------------------------------------------------------------------------
// @codemirror/view — hoverTooltip return spec
// ---------------------------------------------------------------------------
Object.prototype.pos;
Object.prototype.end;
Object.prototype.above;
Object.prototype.create;
Object.prototype.dom;

// ---------------------------------------------------------------------------
// @codemirror/language — LRLanguage
// ---------------------------------------------------------------------------
Object.prototype.define;
Object.prototype.languageData;
Object.prototype.commentTokens;
Object.prototype.line;

// ---------------------------------------------------------------------------
// @codemirror/language — NodeProp (indentNodeProp, foldNodeProp)
// ---------------------------------------------------------------------------
Object.prototype.add;

// ---------------------------------------------------------------------------
// @codemirror/language — syntaxTree / Tree / SyntaxNode
// ---------------------------------------------------------------------------
Object.prototype.iterate;
Object.prototype.resolveInner;
Object.prototype.enter;
Object.prototype.leave;
Object.prototype.name;
Object.prototype.from;
Object.prototype.to;
Object.prototype.parent;

// ---------------------------------------------------------------------------
// @codemirror/language — IndentContext
// ---------------------------------------------------------------------------
Object.prototype.baseIndent;

// ---------------------------------------------------------------------------
// @codemirror/state — RangeSetBuilder
// ---------------------------------------------------------------------------
Object.prototype.finish;

// ---------------------------------------------------------------------------
// @codemirror/autocomplete — autocompletion options, CompletionContext
// ---------------------------------------------------------------------------
Object.prototype.override;
Object.prototype.matchBefore;
Object.prototype.label;
Object.prototype.type;
Object.prototype.detail;
