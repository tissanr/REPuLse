var module$node_modules$$codemirror$view$dist$index_cjs = shadow.js.require("module$node_modules$$codemirror$view$dist$index_cjs", {});
var module$node_modules$$codemirror$language$dist$index_cjs = shadow.js.require("module$node_modules$$codemirror$language$dist$index_cjs", {});
var module$node_modules$$codemirror$state$dist$index_cjs = shadow.js.require("module$node_modules$$codemirror$state$dist$index_cjs", {});
var HOVER_DELAY_MS$$module$repulse$lisp_lang$insert_helper = 50;
var CURSOR_MARKER$$module$repulse$lisp_lang$insert_helper = "¦";
var SLOT_MARKER$$module$repulse$lisp_lang$insert_helper = "__TARGET__";
var hoverTimers$$module$repulse$lisp_lang$insert_helper = new WeakMap();
var NON_PATTERN_HEADS$$module$repulse$lisp_lang$insert_helper = new Set(["def", "defn", "defmacro", "defsynth", "let", "fn", "lambda", "if", "do", "bpm", "stop", "fx", "load-plugin", "unload-plugin", "track", "mute!", "unmute!", "solo!", "clear!", "tracks", "tap!", "midi-sync!", "midi-map", "midi-out", "midi-clock-out!", "midi-export", "freesound!", "freesound-key!", "samples!", "sample-banks", "bank", "sound", "upd", "demo", "tutorial", "share!", "load-gist", "export", "reset!", "loop", "recur", 
"quote", "quasiquote", "bus", "out", "in", "kr"]);
var setHoverTarget$$module$repulse$lisp_lang$insert_helper = module$node_modules$$codemirror$state$dist$index_cjs.StateEffect.define();
var openInsertMenu$$module$repulse$lisp_lang$insert_helper = module$node_modules$$codemirror$state$dist$index_cjs.StateEffect.define();
var closeInsertMenu$$module$repulse$lisp_lang$insert_helper = module$node_modules$$codemirror$state$dist$index_cjs.StateEffect.define();
function clearHoverTimer$$module$repulse$lisp_lang$insert_helper(view) {
  const timer = hoverTimers$$module$repulse$lisp_lang$insert_helper.get(view);
  if (timer) {
    clearTimeout(timer);
    hoverTimers$$module$repulse$lisp_lang$insert_helper.delete(view);
  }
}
function scheduleHover$$module$repulse$lisp_lang$insert_helper(view, target) {
  if (view.state.field(insertStateField$$module$repulse$lisp_lang$insert_helper).menu) {
    return;
  }
  clearHoverTimer$$module$repulse$lisp_lang$insert_helper(view);
  const timer = setTimeout(() => {
    const current = view.state.field(insertStateField$$module$repulse$lisp_lang$insert_helper).hover;
    if (!sameTarget$$module$repulse$lisp_lang$insert_helper(current, target)) {
      view.dispatch({effects:setHoverTarget$$module$repulse$lisp_lang$insert_helper.of(target)});
    }
  }, HOVER_DELAY_MS$$module$repulse$lisp_lang$insert_helper);
  hoverTimers$$module$repulse$lisp_lang$insert_helper.set(view, timer);
}
function closestFromNode$$module$repulse$lisp_lang$insert_helper(node, selector) {
  if (!node) {
    return null;
  }
  const element = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
  return element && element.closest ? element.closest(selector) : null;
}
function handlePointerMove$$module$repulse$lisp_lang$insert_helper(view, event) {
  if (closestFromNode$$module$repulse$lisp_lang$insert_helper(event.target, ".insert-plus-btn") || closestFromNode$$module$repulse$lisp_lang$insert_helper(event.target, ".insert-dropdown")) {
    return;
  }
  scheduleHover$$module$repulse$lisp_lang$insert_helper(view, hoverTargetAtCoords$$module$repulse$lisp_lang$insert_helper(view, event.clientX, event.clientY));
}
function handlePointerLeave$$module$repulse$lisp_lang$insert_helper(view, event) {
  if (closestFromNode$$module$repulse$lisp_lang$insert_helper(event.relatedTarget, ".insert-plus-btn")) {
    return;
  }
  scheduleHover$$module$repulse$lisp_lang$insert_helper(view, null);
}
function handlePointerDown$$module$repulse$lisp_lang$insert_helper(view, event) {
  const button = closestFromNode$$module$repulse$lisp_lang$insert_helper(event.target, ".insert-plus-btn");
  if (button) {
    event.preventDefault();
    event.stopPropagation();
    const target = targetFromButton$$module$repulse$lisp_lang$insert_helper(button);
    if (target) {
      view.dispatch({effects:openInsertMenu$$module$repulse$lisp_lang$insert_helper.of(target)});
    }
    return;
  }
  const insideDropdown = closestFromNode$$module$repulse$lisp_lang$insert_helper(event.target, ".insert-dropdown");
  if (view.state.field(insertStateField$$module$repulse$lisp_lang$insert_helper).menu && !insideDropdown) {
    view.dispatch({effects:closeInsertMenu$$module$repulse$lisp_lang$insert_helper.of(null)});
  }
}
function sameTarget$$module$repulse$lisp_lang$insert_helper(a, b) {
  if (a === b) {
    return true;
  }
  if (!a || !b) {
    return false;
  }
  return a.kind === b.kind && a.from === b.from && a.to === b.to;
}
function normalizeTarget$$module$repulse$lisp_lang$insert_helper(value) {
  if (!value) {
    return null;
  }
  return {kind:value.kind, from:value.from, to:value.to};
}
var insertStateField$$module$repulse$lisp_lang$insert_helper = module$node_modules$$codemirror$state$dist$index_cjs.StateField.define({create() {
  return {hover:null, menu:null};
}, update(value, tr) {
  if (tr.docChanged) {
    value = {hover:null, menu:null};
  }
  for (const effect of tr.effects) {
    if (effect.is(setHoverTarget$$module$repulse$lisp_lang$insert_helper)) {
      value = {...value, hover:normalizeTarget$$module$repulse$lisp_lang$insert_helper(effect.value)};
    } else if (effect.is(openInsertMenu$$module$repulse$lisp_lang$insert_helper)) {
      const target = normalizeTarget$$module$repulse$lisp_lang$insert_helper(effect.value);
      value = {hover:target, menu:target};
    } else if (effect.is(closeInsertMenu$$module$repulse$lisp_lang$insert_helper)) {
      value = {hover:value.hover, menu:null};
    }
  }
  return value;
}});
function activeTarget$$module$repulse$lisp_lang$insert_helper(state) {
  const value = state.field(insertStateField$$module$repulse$lisp_lang$insert_helper, false);
  return value ? value.menu || value.hover : null;
}
class InsertButtonWidget$$module$repulse$lisp_lang$insert_helper extends module$node_modules$$codemirror$view$dist$index_cjs.WidgetType {
  constructor(target) {
    super();
    this.target = target;
  }
  eq(other) {
    return sameTarget$$module$repulse$lisp_lang$insert_helper(this.target, other.target);
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
    button.setAttribute("aria-label", ariaLabelForTarget$$module$repulse$lisp_lang$insert_helper(this.target.kind));
    button.innerHTML = '\x3csvg width\x3d"7" height\x3d"7" viewBox\x3d"0 0 7 7" fill\x3d"none" aria-hidden\x3d"true"\x3e' + '\x3cline x1\x3d"3.5" y1\x3d"0.5" x2\x3d"3.5" y2\x3d"6.5" stroke\x3d"currentColor" stroke-width\x3d"1.4" stroke-linecap\x3d"round"/\x3e' + '\x3cline x1\x3d"0.5" y1\x3d"3.5" x2\x3d"6.5" y2\x3d"3.5" stroke\x3d"currentColor" stroke-width\x3d"1.4" stroke-linecap\x3d"round"/\x3e' + "\x3c/svg\x3e";
    wrap.appendChild(button);
    return wrap;
  }
  ignoreEvent() {
    return false;
  }
}
function ariaLabelForTarget$$module$repulse$lisp_lang$insert_helper(kind) {
  switch(kind) {
    case "wrap":
      return "Wrap expression";
    case "chain":
      return "Chain after expression";
    default:
      return "Insert top level form";
  }
}
function buildDecorations$$module$repulse$lisp_lang$insert_helper(state) {
  const target = activeTarget$$module$repulse$lisp_lang$insert_helper(state);
  const builder = new module$node_modules$$codemirror$state$dist$index_cjs.RangeSetBuilder();
  if (!target) {
    return builder.finish();
  }
  const position = target.kind === "chain" ? target.to : target.from;
  const side = target.kind === "wrap" ? -1 : 1;
  builder.add(position, position, module$node_modules$$codemirror$view$dist$index_cjs.Decoration.widget({widget:new InsertButtonWidget$$module$repulse$lisp_lang$insert_helper(target), side}));
  return builder.finish();
}
function clampPos$$module$repulse$lisp_lang$insert_helper(state, pos) {
  return Math.max(0, Math.min(pos, state.doc.length));
}
function lineTargetAt$$module$repulse$lisp_lang$insert_helper(state, pos) {
  const line = state.doc.lineAt(clampPos$$module$repulse$lisp_lang$insert_helper(state, pos));
  if (!/^\s*$/.test(line.text)) {
    return null;
  }
  return {kind:"top", from:line.from, to:line.to};
}
function enclosingListAt$$module$repulse$lisp_lang$insert_helper(state, pos, side = -1) {
  let node = (0,module$node_modules$$codemirror$language$dist$index_cjs.syntaxTree)(state).resolveInner(clampPos$$module$repulse$lisp_lang$insert_helper(state, pos), side);
  for (; node && node.name !== "List";) {
    node = node.parent;
  }
  return node;
}
function listEdgeTargetAt$$module$repulse$lisp_lang$insert_helper(state, pos) {
  const candidates = [pos, pos - 1, pos + 1];
  for (const candidate of candidates) {
    if (candidate < 0 || candidate > state.doc.length) {
      continue;
    }
    const wrapNode = enclosingListAt$$module$repulse$lisp_lang$insert_helper(state, candidate, 1);
    if (wrapNode && candidate === wrapNode.from && listAllowsInsertion$$module$repulse$lisp_lang$insert_helper(state, wrapNode)) {
      return {kind:"wrap", from:wrapNode.from, to:wrapNode.to};
    }
    const chainNode = enclosingListAt$$module$repulse$lisp_lang$insert_helper(state, candidate, -1);
    if (chainNode && candidate === chainNode.to - 1 && listAllowsInsertion$$module$repulse$lisp_lang$insert_helper(state, chainNode)) {
      return {kind:"chain", from:chainNode.from, to:chainNode.to};
    }
  }
  return null;
}
function hoverTargetAtCoords$$module$repulse$lisp_lang$insert_helper(view, x, y) {
  const pos = view.posAtCoords({x, y});
  if (pos == null) {
    return null;
  }
  return listEdgeTargetAt$$module$repulse$lisp_lang$insert_helper(view.state, pos) || lineTargetAt$$module$repulse$lisp_lang$insert_helper(view.state, pos);
}
function listHead$$module$repulse$lisp_lang$insert_helper(state, node) {
  const inner = state.sliceDoc(node.from + 1, node.to - 1);
  const match = inner.match(/^\s*([^\s()[\]{}]+)/);
  return match ? match[1] : null;
}
function listAllowsInsertion$$module$repulse$lisp_lang$insert_helper(state, node) {
  const head = listHead$$module$repulse$lisp_lang$insert_helper(state, node);
  return !head || !NON_PATTERN_HEADS$$module$repulse$lisp_lang$insert_helper.has(head);
}
function findListNode$$module$repulse$lisp_lang$insert_helper(state, target) {
  const inner = clampPos$$module$repulse$lisp_lang$insert_helper(state, target.from + 1);
  let node = (0,module$node_modules$$codemirror$language$dist$index_cjs.syntaxTree)(state).resolveInner(inner, -1);
  for (; node && node.name !== "List";) {
    node = node.parent;
  }
  if (node && node.from === target.from && node.to === target.to) {
    return node;
  }
  return null;
}
function findThreadAncestor$$module$repulse$lisp_lang$insert_helper(state, node) {
  let current = node;
  for (; current;) {
    if (current.name === "List" && listHead$$module$repulse$lisp_lang$insert_helper(state, current) === "-\x3e\x3e") {
      return current;
    }
    current = current.parent;
  }
  return null;
}
function listChildren$$module$repulse$lisp_lang$insert_helper(node) {
  const children = [];
  for (let child = node.firstChild; child; child = child.nextSibling) {
    children.push(child);
  }
  return children;
}
function usedChainItems$$module$repulse$lisp_lang$insert_helper(state, target) {
  const node = findListNode$$module$repulse$lisp_lang$insert_helper(state, target);
  if (!node) {
    return new Set();
  }
  const threadNode = findThreadAncestor$$module$repulse$lisp_lang$insert_helper(state, node);
  if (!threadNode) {
    return new Set();
  }
  const used = new Set();
  const children = listChildren$$module$repulse$lisp_lang$insert_helper(threadNode);
  for (const child of children) {
    if (child.name !== "List") {
      continue;
    }
    const head = listHead$$module$repulse$lisp_lang$insert_helper(state, child);
    if (!head) {
      continue;
    }
    if (head === "fx") {
      const stepChildren = listChildren$$module$repulse$lisp_lang$insert_helper(child);
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
function materializeTemplate$$module$repulse$lisp_lang$insert_helper(template, replacement = "") {
  const cursorToken = "__REPuLse_CURSOR__";
  const slotToken = "__REPuLse_SLOT__";
  let source = template.replace(CURSOR_MARKER$$module$repulse$lisp_lang$insert_helper, cursorToken).replace(SLOT_MARKER$$module$repulse$lisp_lang$insert_helper, slotToken);
  source = source.replace(slotToken, replacement);
  const cursor = source.indexOf(cursorToken);
  const text = source.replace(cursorToken, "");
  return {text, cursor:cursor >= 0 ? cursor : text.length};
}
function dispatchChange$$module$repulse$lisp_lang$insert_helper(view, spec) {
  view.dispatch({changes:spec.changes, selection:{anchor:spec.cursor}, scrollIntoView:true});
  view.focus();
}
function applyWrapInsertion$$module$repulse$lisp_lang$insert_helper(view, target, item) {
  const original = view.state.sliceDoc(target.from, target.to);
  const built = materializeTemplate$$module$repulse$lisp_lang$insert_helper(item.template, original);
  dispatchChange$$module$repulse$lisp_lang$insert_helper(view, {changes:{from:target.from, to:target.to, insert:built.text}, cursor:target.from + built.cursor});
}
function applyChainInsertion$$module$repulse$lisp_lang$insert_helper(view, target, item) {
  const state = view.state;
  const node = findListNode$$module$repulse$lisp_lang$insert_helper(state, target);
  if (!node) {
    return;
  }
  const built = materializeTemplate$$module$repulse$lisp_lang$insert_helper(item.template);
  const threadNode = findThreadAncestor$$module$repulse$lisp_lang$insert_helper(state, node);
  if (threadNode) {
    const insertAt = threadNode.to - 1;
    dispatchChange$$module$repulse$lisp_lang$insert_helper(view, {changes:{from:insertAt, insert:` ${built.text}`}, cursor:insertAt + 1 + built.cursor});
    return;
  }
  const original = state.sliceDoc(target.from, target.to);
  const wrapped = `(->> ${original} ${built.text})`;
  const cursor = "(-\x3e\x3e ".length + original.length + 1 + built.cursor;
  dispatchChange$$module$repulse$lisp_lang$insert_helper(view, {changes:{from:target.from, to:target.to, insert:wrapped}, cursor:target.from + cursor});
}
function applyTopLevelInsertion$$module$repulse$lisp_lang$insert_helper(view, target, item) {
  const state = view.state;
  const line = state.doc.lineAt(target.from);
  const indent = (line.text.match(/^\s*/) || [""])[0];
  const built = materializeTemplate$$module$repulse$lisp_lang$insert_helper(item.template);
  dispatchChange$$module$repulse$lisp_lang$insert_helper(view, {changes:{from:line.from, to:line.to, insert:indent + built.text}, cursor:line.from + indent.length + built.cursor});
}
function applyInsertion$$module$repulse$lisp_lang$insert_helper(view, target, item) {
  if (target.kind === "wrap") {
    applyWrapInsertion$$module$repulse$lisp_lang$insert_helper(view, target, item);
  } else if (target.kind === "chain") {
    applyChainInsertion$$module$repulse$lisp_lang$insert_helper(view, target, item);
  } else {
    applyTopLevelInsertion$$module$repulse$lisp_lang$insert_helper(view, target, item);
  }
}
function targetFromButton$$module$repulse$lisp_lang$insert_helper(button) {
  if (!button) {
    return null;
  }
  return {kind:button.dataset.kind, from:Number(button.dataset.from), to:Number(button.dataset.to)};
}
var repulse$lisp_lang$insert_helper$classdecl$var1 = class {
  constructor(view) {
    this.view = view;
    this.dropdown = null;
    this._onMouseMove = event => handlePointerMove$$module$repulse$lisp_lang$insert_helper(view, event);
    this._onMouseLeave = event => handlePointerLeave$$module$repulse$lisp_lang$insert_helper(view, event);
    this._onMouseDown = event => handlePointerDown$$module$repulse$lisp_lang$insert_helper(view, event);
    view.contentDOM.addEventListener("mousemove", this._onMouseMove);
    view.contentDOM.addEventListener("mouseleave", this._onMouseLeave);
    view.contentDOM.addEventListener("mousedown", this._onMouseDown);
    this.boundDocMouseDown = event => {
      if (!this.dropdown) {
        return;
      }
      if (this.dropdown.contains(event.target)) {
        return;
      }
      if (closestFromNode$$module$repulse$lisp_lang$insert_helper(event.target, ".insert-plus-btn")) {
        return;
      }
      this.closeMenu();
    };
    this.boundKeyDown = event => {
      if (event.key === "Escape") {
        this.closeMenu();
      }
    };
    this.boundScroll = () => this.closeMenu();
    this.boundResize = () => this.closeMenu();
  }
  update(update) {
    const prev = update.startState.field(insertStateField$$module$repulse$lisp_lang$insert_helper);
    const next = update.state.field(insertStateField$$module$repulse$lisp_lang$insert_helper);
    if (!sameTarget$$module$repulse$lisp_lang$insert_helper(prev.menu, next.menu)) {
      if (next.menu) {
        this.openDropdown(next.menu);
      } else {
        this.destroyDropdown();
      }
    } else if (next.menu && (update.viewportChanged || update.geometryChanged)) {
      this.positionDropdown(next.menu);
    }
  }
  destroy() {
    clearHoverTimer$$module$repulse$lisp_lang$insert_helper(this.view);
    this.destroyDropdown();
    this.view.contentDOM.removeEventListener("mousemove", this._onMouseMove);
    this.view.contentDOM.removeEventListener("mouseleave", this._onMouseLeave);
    this.view.contentDOM.removeEventListener("mousedown", this._onMouseDown);
  }
  closeMenu() {
    if (this.view.state.field(insertStateField$$module$repulse$lisp_lang$insert_helper).menu) {
      this.view.dispatch({effects:closeInsertMenu$$module$repulse$lisp_lang$insert_helper.of(null)});
    }
  }
  openDropdown(target) {
    this.destroyDropdown();
    const dropdown = document.createElement("div");
    dropdown.className = "insert-dropdown";
    const usedItems = target.kind === "chain" ? usedChainItems$$module$repulse$lisp_lang$insert_helper(this.view.state, target) : new Set();
    for (const category of getInsertCategories$$module$repulse$lisp_lang$insert_categories(target.kind)) {
      const items = category.items.filter(item => !usedItems.has(item.label));
      if (!items.length) {
        continue;
      }
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
          applyInsertion$$module$repulse$lisp_lang$insert_helper(this.view, target, item);
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
    if (!this.dropdown) {
      return;
    }
    const selector = `.insert-plus-btn[data-kind="${target.kind}"][data-from="${target.from}"][data-to="${target.to}"]`;
    const button = this.view.dom.querySelector(selector);
    if (!button) {
      this.closeMenu();
      return;
    }
    const rect = button.getBoundingClientRect();
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
};
var insertHelperPlugin$$module$repulse$lisp_lang$insert_helper = module$node_modules$$codemirror$view$dist$index_cjs.ViewPlugin.fromClass(repulse$lisp_lang$insert_helper$classdecl$var1, {decorations:plugin => buildDecorations$$module$repulse$lisp_lang$insert_helper(plugin.view.state)});
var insertHelper$$module$repulse$lisp_lang$insert_helper = [insertStateField$$module$repulse$lisp_lang$insert_helper, insertHelperPlugin$$module$repulse$lisp_lang$insert_helper];
/** @const */ 
var module$repulse$lisp_lang$insert_helper = {};
/** @const */ 
module$repulse$lisp_lang$insert_helper.insertHelper = insertHelper$$module$repulse$lisp_lang$insert_helper;

$CLJS.module$repulse$lisp_lang$insert_helper=module$repulse$lisp_lang$insert_helper;
//# sourceMappingURL=module$repulse$lisp_lang$insert_helper.js.map
