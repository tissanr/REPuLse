import { ViewPlugin, Decoration } from "@codemirror/view";
import { syntaxTree } from "@codemirror/language";
import { RangeSetBuilder } from "@codemirror/state";

const LEVELS = 6;

// Pre-build one Decoration per level to avoid allocating on every update.
const MARKS = Array.from({ length: LEVELS }, (_, i) =>
  Decoration.mark({ class: `rainbow-${i + 1}` })
);

const BRACKET_NODES = new Set(["List", "Vector", "Map"]);

function buildDecorations(view) {
  const builder = new RangeSetBuilder();
  const tree = syntaxTree(view.state);

  // Collect [from, to, mark] triples — tree.iterate visits in document order,
  // but we need to emit opening and closing brackets sorted by position.
  const items = [];
  let depth = 0;

  tree.iterate({
    enter(node) {
      if (BRACKET_NODES.has(node.name)) {
        const mark = MARKS[depth % LEVELS];
        items.push([node.from,     node.from + 1, mark]); // opening bracket
        items.push([node.to   - 1, node.to,       mark]); // closing bracket
        depth++;
      }
    },
    leave(node) {
      if (BRACKET_NODES.has(node.name)) {
        depth--;
      }
    },
  });

  // RangeSetBuilder requires ranges added in ascending order.
  items.sort((a, b) => a[0] - b[0]);
  for (const [from, to, mark] of items) {
    builder.add(from, to, mark);
  }
  return builder.finish();
}

export const rainbowBrackets = ViewPlugin.fromClass(
  class {
    constructor(view) {
      this.decorations = buildDecorations(view);
    }
    update(update) {
      if (update.docChanged || update.viewportChanged) {
        this.decorations = buildDecorations(update.view);
      }
    }
  },
  { decorations: v => v.decorations }
);
