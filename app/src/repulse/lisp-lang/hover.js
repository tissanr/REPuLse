// hover.js — Hover documentation tooltips for REPuLse-Lisp built-ins
import { hoverTooltip } from "@codemirror/view";
import { syntaxTree } from "@codemirror/language";
import { DOCS } from "./builtins.generated.js";

// Docs (name → { signature, description, example }) are generated from
// app/src/repulse/content/builtin_meta.edn (npm run gen:ai-docs). Do not add names here.

/**
 * Find a BuiltinName or Symbol node at the given position.
 * Returns { from, to, word } or null.
 */
/** Is `node` (a Keyword) the effect name of an (fx …) form, i.e. the argument
 * right after `fx`? Then its hover shows the effect's doc (Phase FX1). */
function isFxEffectKeyword(state, node) {
  const list = node.parent;
  if (!list || list.name !== "List") return false;
  const args = [];
  for (let c = list.firstChild; c; c = c.nextSibling) {
    if (c.name === "OpenParen" || c.name === "CloseParen" || c.name === "LineComment") continue;
    args.push(c);
  }
  return args.length >= 2 &&
    state.sliceDoc(args[0].from, args[0].to) === "fx" &&
    args[1].from === node.from;
}

function wordAt(state, pos) {
  const tree = syntaxTree(state);
  let node = tree.resolveInner(pos, 0);
  while (node) {
    if (node.name === "Keyword" && isFxEffectKeyword(state, node)) {
      // `:limiter` in (fx :limiter -1) → "limiter"; :dattorro is the reverb alias
      const name = state.sliceDoc(node.from + 1, node.to);
      return { from: node.from, to: node.to, word: name === "dattorro" ? "dattorro-reverb" : name };
    }
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
