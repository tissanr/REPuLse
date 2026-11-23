#!/usr/bin/env node
/**
 * gen_builtins.mjs — generate everything that lists built-in names from the one
 * hand-written source, app/src/repulse/content/builtin_meta.edn:
 *
 *   app/src/repulse/lisp-lang/builtins.generated.js   BUILTINS (completions) + DOCS (hover)
 *   app/src/repulse/lisp-lang/repulse-lisp.grammar    region between the BEGIN/END GENERATED markers
 *   docs/ai/builtins.json                             AI knowledge base
 *
 * `npm run gen:ai-docs` runs this and then `gen:grammar` (parser.js / parser.terms.js).
 * Exits 1, with one line per problem, when the .edn is incomplete or inconsistent.
 */

import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

// ── 3. Load builtin_meta.edn — hand-rolled parser for the subset we use ──────
function parseEdn(src) {
  // Strip line comments — must skip string contents to avoid stripping `;` inside strings
  {
    let out = "", inStr = false;
    for (let i = 0; i < src.length; i++) {
      const c = src[i];
      if (inStr) {
        out += c;
        if (c === "\\") out += src[++i];
        else if (c === '"') inStr = false;
      } else if (c === '"') {
        inStr = true; out += c;
      } else if (c === ";") {
        while (i < src.length && src[i] !== "\n") i++;
        i--;
      } else {
        out += c;
      }
    }
    src = out;
  }

  let pos = 0;

  function peek() {
    while (pos < src.length && /\s/.test(src[pos])) pos++;
    return src[pos];
  }

  function readToken() {
    peek();
    // String
    if (src[pos] === '"') {
      let s = "";
      pos++; // skip opening "
      while (pos < src.length && src[pos] !== '"') {
        if (src[pos] === "\\") {
          pos++;
          const e = src[pos++];
          if (e === "n") s += "\n";
          else if (e === "t") s += "\t";
          else if (e === "r") s += "\r";
          else if (e === "u") { s += String.fromCharCode(parseInt(src.slice(pos, pos + 4), 16)); pos += 4; }
          else s += e; // \" and \\ (and any other escaped char) stand for themselves
        } else {
          s += src[pos++];
        }
      }
      pos++; // skip closing "
      return { type: "string", val: s };
    }
    // Keyword
    if (src[pos] === ":") {
      let s = "";
      pos++;
      while (pos < src.length && !/[\s,\[\]{}()]/.test(src[pos])) {
        s += src[pos++];
      }
      return { type: "keyword", val: s };
    }
    // Number
    if (/[-\d]/.test(src[pos])) {
      let s = "";
      while (pos < src.length && /[0-9.\-]/.test(src[pos])) {
        s += src[pos++];
      }
      return { type: "number", val: Number(s) };
    }
    // Symbol/boolean
    if (/[a-zA-Z!?]/.test(src[pos])) {
      let s = "";
      while (pos < src.length && /[a-zA-Z0-9!?-]/.test(src[pos])) {
        s += src[pos++];
      }
      if (s === "true") return { type: "bool", val: true };
      if (s === "false") return { type: "bool", val: false };
      if (s === "nil") return { type: "nil", val: null };
      return { type: "symbol", val: s };
    }
    return { type: "punct", val: src[pos++] };
  }

  function readValue() {
    peek();
    const ch = src[pos];
    if (ch === "{") return readMap();
    if (ch === "[") return readVector();
    const tok = readToken();
    return tok.val;
  }

  function readMap() {
    pos++; // skip {
    const result = {};
    while (true) {
      peek();
      if (src[pos] === "}") { pos++; break; }
      const key = readValue();
      const val = readValue();
      result[key] = val;
    }
    return result;
  }

  function readVector() {
    pos++; // skip [
    const result = [];
    while (true) {
      peek();
      if (src[pos] === "]") { pos++; break; }
      result.push(readValue());
    }
    return result;
  }

  return readValue();
}

const ednSrc = readFileSync(
  path.join(root, "app/src/repulse/content/builtin_meta.edn"),
  "utf8"
);
const meta = parseEdn(ednSrc);
const names = Object.keys(meta);

// ── Validate before writing anything ─────────────────────────────────────────
const REQUIRED = [
  "type", "detail", "signature", "doc",
  "category", "returns", "side-effects", "examples", "see-also",
];
const TYPES = new Set(["function", "keyword", "variable"]);
const problems = [];

// parseEdn builds a plain object, so a duplicate key (":x" and "x" included) silently
// collapses; count the entry heads in the source text instead.
const seen = new Map();
for (const m of ednSrc.matchAll(/^ (?::([^\s{]+)|"((?:[^"\\]|\\.)+)")\s+\{/gm)) {
  const n = m[1] ?? m[2];
  seen.set(n, (seen.get(n) ?? 0) + 1);
}
for (const [n, c] of seen) if (c > 1) problems.push(`${n}: defined ${c} times`);

for (const name of names) {
  const e = meta[name];
  if (e === null || typeof e !== "object" || Array.isArray(e)) {
    problems.push(`${name}: entry is not a map`);
    continue;
  }
  for (const k of REQUIRED) {
    if (!(k in e)) problems.push(`${name}: missing :${k}`);
  }
  if ("type" in e && !TYPES.has(e.type)) {
    problems.push(`${name}: :type must be "function", "keyword" or "variable", got ${JSON.stringify(e.type)}`);
  }
  for (const k of ["detail", "signature", "doc", "category", "returns"]) {
    if (k in e && (typeof e[k] !== "string" || e[k].trim() === "")) {
      problems.push(`${name}: :${k} must be a non-empty string`);
    }
  }
  if ("examples" in e && (!Array.isArray(e.examples) || e.examples.length === 0)) {
    problems.push(`${name}: :examples must have at least one item`);
  }
  if ("side-effects" in e && !Array.isArray(e["side-effects"])) {
    problems.push(`${name}: :side-effects must be a vector`);
  }
  if ("see-also" in e) {
    if (!Array.isArray(e["see-also"])) {
      problems.push(`${name}: :see-also must be a vector`);
    } else {
      for (const s of e["see-also"]) {
        if (!(s in meta)) problems.push(`${name}: :see-also names unknown builtin "${s}"`);
      }
    }
  }
}
if (problems.length > 0) {
  console.error("gen:ai-docs FAILED — builtin_meta.edn problems:");
  problems.forEach((p) => console.error(" ", p));
  process.exit(1);
}

// ── Output 1: builtins.generated.js ──────────────────────────────────────────
const js = [];
js.push("// GENERATED by scripts/gen_builtins.mjs — do not edit");
js.push("// Source: app/src/repulse/content/builtin_meta.edn. Run `npm run gen:ai-docs`.");
js.push("");
js.push("/** Completion entries: { label, type, detail }, in builtin_meta.edn order. */");
js.push("export const BUILTINS = [");
for (const n of names) {
  const e = meta[n];
  js.push(`  ${JSON.stringify({ label: n, type: e.type, detail: e.detail })},`);
}
js.push("];");
js.push("");
js.push("/** Hover docs: name → { signature, description, example }. */");
js.push("export const DOCS = {");
for (const n of names) {
  const e = meta[n];
  js.push(
    `  ${JSON.stringify(n)}: ${JSON.stringify({
      signature: e.signature,
      description: e.doc,
      example: e.examples[0],
    })},`
  );
}
js.push("};");
js.push("");
writeFileSync(
  path.join(root, "app/src/repulse/lisp-lang/builtins.generated.js"),
  js.join("\n")
);

// ── Output 2: grammar BuiltinName region ─────────────────────────────────────
const grammarPath = path.join(root, "app/src/repulse/lisp-lang/repulse-lisp.grammar");
const grammarSrc = readFileSync(grammarPath, "utf8");
const BEGIN = "// BEGIN GENERATED BuiltinName";
const END = "// END GENERATED BuiltinName";
const bi = grammarSrc.indexOf(BEGIN);
const ei = grammarSrc.indexOf(END);
if (bi < 0 || ei < 0 || ei < bi) {
  console.error("grammar: BuiltinName markers not found");
  process.exit(1);
}
const lit = (n) => `"${n.replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`;
const gl = [];
let cur = "   ";
names.forEach((n, i) => {
  const piece = lit(n) + (i < names.length - 1 ? " |" : "");
  if (cur.length + piece.length + 1 > 90 && cur.trim() !== "") {
    gl.push(cur);
    cur = "   ";
  }
  cur += " " + piece;
});
gl.push(cur);
const newGrammar =
  grammarSrc.slice(0, bi) +
  BEGIN + "\n" + gl.join("\n") + "\n    " +
  grammarSrc.slice(ei);
writeFileSync(grammarPath, newGrammar);

// ── Output 3: docs/ai/builtins.json ──────────────────────────────────────────
const output = {};
for (const n of names) {
  const e = meta[n];
  output[n] = {
    category: e.category,
    signature: e.signature,
    returns: e.returns,
    "side-effects": e["side-effects"],
    examples: e.examples,
    "see-also": e["see-also"],
    detail: e.detail,
    description: e.doc,
  };
}
mkdirSync(path.join(root, "docs/ai"), { recursive: true });
writeFileSync(
  path.join(root, "docs/ai/builtins.json"),
  JSON.stringify(output, null, 2) + "\n"
);
console.log(
  `gen:ai-docs — ${names.length} builtins → builtins.generated.js, grammar BuiltinName, docs/ai/builtins.json`
);
