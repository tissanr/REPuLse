import { Decoration, ViewPlugin, WidgetType } from "@codemirror/view";
import { syntaxTree } from "@codemirror/language";
import { RangeSetBuilder, StateEffect, StateField } from "@codemirror/state";
import { getInsertCategories } from "./insert-categories.js";

const HOVER_DELAY_MS = 50;
const CURSOR_MARKER = "¦";
const SLOT_MARKER = "__TARGET__";
const hoverTimers = new WeakMap();
const NON_PATTERN_HEADS = new Set([
  "def", "defn", "defmacro", "defsynth", "let", "fn", "lambda", "if", "do",
  "bpm", "stop", "fx", "load-plugin", "unload-plugin", "track",
  "mute!", "unmute!", "solo!", "clear!", "tracks", "tap!", "midi-sync!",
  "midi-map", "midi-out", "midi-clock-out!", "midi-export",
  "freesound!", "freesound-key!", "samples!", "sample-banks", "bank", "sound",
  "upd", "demo", "tutorial", "share!", "load-gist", "export", "reset!",
  "loop", "recur", "quote", "quasiquote", "bus", "out", "in", "kr",
]);

const setHoverTarget = StateEffect.define();
const openInsertMenu = StateEffect.define();
const closeInsertMenu = StateEffect.define();

function clearHoverTimer(view) {
  const timer = hoverTimers.get(view);
  if (timer) {
    clearTimeout(timer);
    hoverTimers.delete(view);
  }
}

function scheduleHover(view, target) {
  if (view.state.field(insertStateField).menu) return;
  clearHoverTimer(view);
  const timer = setTimeout(() => {
    const current = view.state.field(insertStateField).hover;
    if (!sameTarget(current, target)) {
      view.dispatch({ effects: setHoverTarget.of(target) });
    }
  }, HOVER_DELAY_MS);
  hoverTimers.set(view, timer);
}

function closestFromNode(node, selector) {
  if (!node) return null;
  const element = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
  return element && element.closest ? element.closest(selector) : null;
}

function handlePointerMove(view, event) {
  if (closestFromNode(event.target, ".insert-plus-btn") || closestFromNode(event.target, ".insert-dropdown")) {
    return;
  }
  scheduleHover(view, hoverTargetAtCoords(view, event.clientX, event.clientY));
}

function handlePointerLeave(view, event) {
  if (closestFromNode(event.relatedTarget, ".insert-plus-btn")) {
    return;
  }
  scheduleHover(view, null);
}

function handlePointerDown(view, event) {
  const button = closestFromNode(event.target, ".insert-plus-btn");
  if (button) {
    event.preventDefault();
    event.stopPropagation();
    const target = targetFromButton(button);
    if (target) view.dispatch({ effects: openInsertMenu.of(target) });
    return;
  }

  const insideDropdown = closestFromNode(event.target, ".insert-dropdown");
  if (view.state.field(insertStateField).menu && !insideDropdown) {
    view.dispatch({ effects: closeInsertMenu.of(null) });
  }
}

function sameTarget(a, b) {
  if (a === b) return true;
  if (!a || !b) return false;
  return a.kind === b.kind && a.from === b.from && a.to === b.to;
}

function normalizeTarget(value) {
  if (!value) return null;
  return { kind: value.kind, from: value.from, to: value.to };
}

const insertStateField = StateField.define({
  create() {
    return { hover: null, menu: null };
  },
  update(value, tr) {
    if (tr.docChanged) {
      value = { hover: null, menu: null };
    }
    for (const effect of tr.effects) {
      if (effect.is(setHoverTarget)) {
        value = { ...value, hover: normalizeTarget(effect.value) };
      } else if (effect.is(openInsertMenu)) {
        const target = normalizeTarget(effect.value);
        value = { hover: target, menu: target };
      } else if (effect.is(closeInsertMenu)) {
        value = { hover: value.hover, menu: null };
      }
    }
    return value;
  },
});

function activeTarget(state) {
  const value = state.field(insertStateField, false);
  return value ? (value.menu || value.hover) : null;
}

class InsertButtonWidget extends WidgetType {
  constructor(target) {
    super();
    this.target = target;
  }

  eq(other) {
    return sameTarget(this.target, other.target);
  }

  toDOM() {
    const wrap = document.createElement("span");
    wrap.className = `insert-plus-anchor insert-plus-anchor-${this.target.kind}`;

    const button = document.createElement("button");
    button.type = "button";
    button.className = "insert-plus-btn";
    button.dataset.kind = this.target.kind;
    button.dataset.from = String(this.target.from);
    button.dataset.to = String(this.target.to);
    button.setAttribute("aria-label", ariaLabelForTarget(this.target.kind));
    button.innerHTML =
      '<svg width="9" height="9" viewBox="0 0 9 9" fill="none" aria-hidden="true">' +
      '<line x1="4.5" y1="1" x2="4.5" y2="8" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>' +
      '<line x1="1" y1="4.5" x2="8" y2="4.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>' +
      '</svg>';

    wrap.appendChild(button);
    return wrap;
  }

  ignoreEvent() {
    return false;
  }
}

function ariaLabelForTarget(kind) {
  switch (kind) {
    case "wrap": return "Wrap expression";
    case "chain": return "Chain after expression";
    default: return "Insert top level form";
  }
}

function buildDecorations(state) {
  const target = activeTarget(state);
  const builder = new RangeSetBuilder();
  if (!target) return builder.finish();

  const position = target.kind === "chain" ? target.to : target.from;
  const side = target.kind === "wrap" ? -1 : 1;
  builder.add(
    position,
    position,
    Decoration.widget({
      widget: new InsertButtonWidget(target),
      side,
    })
  );
  return builder.finish();
}

function clampPos(state, pos) {
  return Math.max(0, Math.min(pos, state.doc.length));
}

function lineTargetAt(state, pos) {
  const line = state.doc.lineAt(clampPos(state, pos));
  if (!/^\s*$/.test(line.text)) return null;
  return { kind: "top", from: line.from, to: line.to };
}

function enclosingListAt(state, pos, side = -1) {
  let node = syntaxTree(state).resolveInner(clampPos(state, pos), side);
  while (node && node.name !== "List") node = node.parent;
  return node;
}

function listEdgeTargetAt(state, pos) {
  const candidates = [pos, pos - 1, pos + 1];
  for (const candidate of candidates) {
    if (candidate < 0 || candidate > state.doc.length) continue;
    // Wrap: use side=1 so resolveInner finds the list STARTING at this position.
    const wrapNode = enclosingListAt(state, candidate, 1);
    if (wrapNode && candidate === wrapNode.from && listAllowsInsertion(state, wrapNode)) {
      return { kind: "wrap", from: wrapNode.from, to: wrapNode.to };
    }
    // Chain: use side=-1 so resolveInner finds the list ENDING here.
    const chainNode = enclosingListAt(state, candidate, -1);
    if (chainNode && candidate === chainNode.to - 1 && listAllowsInsertion(state, chainNode)) {
      return { kind: "chain", from: chainNode.from, to: chainNode.to };
    }
  }
  return null;
}

function hoverTargetAtCoords(view, x, y) {
  const pos = view.posAtCoords({ x, y });
  if (pos == null) return null;
  return listEdgeTargetAt(view.state, pos) || lineTargetAt(view.state, pos);
}

function listHead(state, node) {
  const inner = state.sliceDoc(node.from + 1, node.to - 1);
  const match = inner.match(/^\s*([^\s()[\]{}]+)/);
  return match ? match[1] : null;
}

function listAllowsInsertion(state, node) {
  const head = listHead(state, node);
  return !head || !NON_PATTERN_HEADS.has(head);
}

function findListNode(state, target) {
  // Resolve one char inside the list so Lezer's side=-1 doesn't skip to the parent
  const inner = clampPos(state, target.from + 1);
  let node = syntaxTree(state).resolveInner(inner, -1);
  while (node && node.name !== "List") node = node.parent;
  if (node && node.from === target.from && node.to === target.to) return node;
  return null;
}

function findThreadAncestor(state, node) {
  let current = node;
  while (current) {
    if (current.name === "List" && listHead(state, current) === "->>") return current;
    current = current.parent;
  }
  return null;
}

function listChildren(node) {
  const children = [];
  for (let child = node.firstChild; child; child = child.nextSibling) {
    children.push(child);
  }
  return children;
}

function usedChainItems(state, target) {
  const node = findListNode(state, target);
  if (!node) return new Set();

  const threadNode = findThreadAncestor(state, node);
  if (!threadNode) return new Set();

  const used = new Set();
  const children = listChildren(threadNode);

  for (const child of children) {
    if (child.name !== "List") continue;
    const head = listHead(state, child);
    if (!head) continue;

    if (head === "fx") {
      const stepChildren = listChildren(child);
      const effectName = stepChildren[1] ? state.sliceDoc(stepChildren[1].from, stepChildren[1].to) : "";
      if (effectName.startsWith(":")) {
        used.add(effectName.slice(1));
      }
      continue;
    }

    used.add(head);

    const stepText = state.sliceDoc(child.from, child.to);
    if (/\(tween\b/.test(stepText)) {
      used.add("tween");
    }
  }

  return used;
}

function materializeTemplate(template, replacement = "") {
  const cursorToken = "__REPuLse_CURSOR__";
  const slotToken = "__REPuLse_SLOT__";
  let source = template.replace(CURSOR_MARKER, cursorToken).replace(SLOT_MARKER, slotToken);
  source = source.replace(slotToken, replacement);
  const cursor = source.indexOf(cursorToken);
  const text = source.replace(cursorToken, "");
  return { text, cursor: cursor >= 0 ? cursor : text.length };
}

function dispatchChange(view, spec) {
  view.dispatch({
    changes: spec.changes,
    selection: { anchor: spec.cursor },
    scrollIntoView: true,
  });
  view.focus();
}

function applyWrapInsertion(view, target, item) {
  const original = view.state.sliceDoc(target.from, target.to);
  const built = materializeTemplate(item.template, original);
  dispatchChange(view, {
    changes: { from: target.from, to: target.to, insert: built.text },
    cursor: target.from + built.cursor,
  });
}

function applyChainInsertion(view, target, item) {
  const state = view.state;
  const node = findListNode(state, target);
  if (!node) return;

  const built = materializeTemplate(item.template);
  const threadNode = findThreadAncestor(state, node);

  if (threadNode) {
    const insertAt = threadNode.to - 1;
    dispatchChange(view, {
      changes: { from: insertAt, insert: ` ${built.text}` },
      cursor: insertAt + 1 + built.cursor,
    });
    return;
  }

  const original = state.sliceDoc(target.from, target.to);
  const wrapped = `(->> ${original} ${built.text})`;
  const cursor = "(->> ".length + original.length + 1 + built.cursor;
  dispatchChange(view, {
    changes: { from: target.from, to: target.to, insert: wrapped },
    cursor: target.from + cursor,
  });
}

function applyTopLevelInsertion(view, target, item) {
  const state = view.state;
  const line = state.doc.lineAt(target.from);
  const indent = (line.text.match(/^\s*/) || [""])[0];
  const built = materializeTemplate(item.template);
  dispatchChange(view, {
    changes: { from: line.from, to: line.to, insert: indent + built.text },
    cursor: line.from + indent.length + built.cursor,
  });
}

function applyInsertion(view, target, item) {
  if (target.kind === "wrap") applyWrapInsertion(view, target, item);
  else if (target.kind === "chain") applyChainInsertion(view, target, item);
  else applyTopLevelInsertion(view, target, item);
}

function targetFromButton(button) {
  if (!button) return null;
  return {
    kind: button.dataset.kind,
    from: Number(button.dataset.from),
    to: Number(button.dataset.to),
  };
}

const insertHelperPlugin = ViewPlugin.fromClass(class {
    constructor(view) {
      this.view = view;
      this.dropdown = null;
      // Closure-safe DOM listeners: capture `view` in closure, never rely on `this` binding
      this._onMouseMove = (event) => handlePointerMove(view, event);
      this._onMouseLeave = (event) => handlePointerLeave(view, event);
      this._onMouseDown = (event) => handlePointerDown(view, event);
      view.contentDOM.addEventListener("mousemove", this._onMouseMove);
      view.contentDOM.addEventListener("mouseleave", this._onMouseLeave);
      view.contentDOM.addEventListener("mousedown", this._onMouseDown);
      this.boundDocMouseDown = event => {
        if (!this.dropdown) return;
        if (this.dropdown.contains(event.target)) return;
        if (closestFromNode(event.target, ".insert-plus-btn")) return;
        this.closeMenu();
      };
      this.boundKeyDown = event => {
        if (event.key === "Escape") this.closeMenu();
      };
      this.boundScroll = () => this.closeMenu();
      this.boundResize = () => this.closeMenu();
    }

    update(update) {
      const prev = update.startState.field(insertStateField);
      const next = update.state.field(insertStateField);
      if (!sameTarget(prev.menu, next.menu)) {
        if (next.menu) this.openDropdown(next.menu);
        else this.destroyDropdown();
      } else if (next.menu && (update.viewportChanged || update.geometryChanged)) {
        this.positionDropdown(next.menu);
      }
    }

    destroy() {
      clearHoverTimer(this.view);
      this.destroyDropdown();
      this.view.contentDOM.removeEventListener("mousemove", this._onMouseMove);
      this.view.contentDOM.removeEventListener("mouseleave", this._onMouseLeave);
      this.view.contentDOM.removeEventListener("mousedown", this._onMouseDown);
    }

    closeMenu() {
      if (this.view.state.field(insertStateField).menu) {
        this.view.dispatch({ effects: closeInsertMenu.of(null) });
      }
    }

    openDropdown(target) {
      this.destroyDropdown();

      const dropdown = document.createElement("div");
      dropdown.className = "insert-dropdown";
      const usedItems = target.kind === "chain" ? usedChainItems(this.view.state, target) : new Set();

      for (const category of getInsertCategories(target.kind)) {
        const items = category.items.filter(item => !usedItems.has(item.label));
        if (!items.length) continue;

        const header = document.createElement("div");
        header.className = "insert-category-header";
        header.textContent = category.title;
        dropdown.appendChild(header);

        for (const item of items) {
          const row = document.createElement("button");
          row.type = "button";
          row.className = "insert-dropdown-item";
          row.dataset.label = item.label;

          const name = document.createElement("span");
          name.className = "insert-fn-name";
          name.textContent = item.label;
          row.appendChild(name);

          const detail = document.createElement("span");
          detail.className = "insert-fn-detail";
          detail.textContent = item.detail;
          row.appendChild(detail);

          row.addEventListener("mousedown", event => {
            event.preventDefault();
            event.stopPropagation();
            applyInsertion(this.view, target, item);
            this.closeMenu();
          });

          dropdown.appendChild(row);
        }
      }

      document.body.appendChild(dropdown);
      this.dropdown = dropdown;
      document.addEventListener("mousedown", this.boundDocMouseDown, true);
      window.addEventListener("keydown", this.boundKeyDown, true);
      this.view.scrollDOM.addEventListener("scroll", this.boundScroll, true);
      window.addEventListener("resize", this.boundResize, true);
      this.positionDropdown(target);
    }

    destroyDropdown() {
      if (this.dropdown && this.dropdown.parentNode) {
        this.dropdown.parentNode.removeChild(this.dropdown);
      }
      this.dropdown = null;
      document.removeEventListener("mousedown", this.boundDocMouseDown, true);
      window.removeEventListener("keydown", this.boundKeyDown, true);
      this.view.scrollDOM.removeEventListener("scroll", this.boundScroll, true);
      window.removeEventListener("resize", this.boundResize, true);
    }

    positionDropdown(target) {
      if (!this.dropdown) return;
      const selector = `.insert-plus-btn[data-kind="${target.kind}"][data-from="${target.from}"][data-to="${target.to}"]`;
      const button = this.view.dom.querySelector(selector);
      if (!button) {
        this.closeMenu();
        return;
      }

      const rect = button.getBoundingClientRect();
      // Force layout so getBoundingClientRect returns real dimensions.
      void this.dropdown.offsetHeight;
      const menuRect = this.dropdown.getBoundingClientRect();
      const gap = 6;
      const maxLeft = Math.max(12, window.innerWidth - menuRect.width - 12);
      const left = Math.min(Math.max(12, rect.left), maxLeft);
      let top = rect.bottom + gap;
      if (top + menuRect.height > window.innerHeight - 12) {
        top = Math.max(12, rect.top - menuRect.height - gap);
      }

      this.dropdown.style.left = `${left}px`;
      this.dropdown.style.top = `${top}px`;
    }
  }, {
    decorations: plugin => buildDecorations(plugin.view.state),
  });

export const insertHelper = [
  insertStateField,
  insertHelperPlugin,
];
