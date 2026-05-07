#!/usr/bin/env node
/**
 * gen_ai_docs.mjs — Generate docs/ai/builtins.json from three sources:
 *   1. app/src/repulse/lisp-lang/repulse-lisp.grammar  (authoritative name list)
 *   2. app/src/repulse/lisp-lang/completions.js         (detail strings)
 *   3. app/src/repulse/content/builtin_meta.edn         (enriched metadata)
 *   4. app/src/repulse/lisp-lang/hover.js               (signature + description)
 *
 * Exits non-zero if any completions.js name is absent from builtin_meta.edn.
 */

import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");

// ── 1. Load grammar → extract BuiltinName list ────────────────────────────────
const grammarSrc = readFileSync(
  path.join(root, "app/src/repulse/lisp-lang/repulse-lisp.grammar"),
  "utf8"
);

function extractBuiltinNames(src) {
  const match = src.match(/BuiltinName\s*\{([^}]+)\}/s);
  if (!match) throw new Error("Could not find BuiltinName block in grammar");
  return match[1]
    .split(/\||\n/)
    .map((s) => s.trim().replace(/^"|"$/g, ""))
    .filter((s) => s.length > 0);
}

const grammarNames = new Set(extractBuiltinNames(grammarSrc));

// ── 2. Load completions.js ────────────────────────────────────────────────────
// completions.js uses ES module syntax with a CM6 import — mock it for Node.
// We patch the module resolution to shim @codemirror/autocomplete.
const require = createRequire(import.meta.url);
const completionsPath = path.join(
  root,
  "app/src/repulse/lisp-lang/completions.js"
);
const completionsSrc = readFileSync(completionsPath, "utf8");

// Extract BUILTINS array via a lightweight AST-free approach:
// replace the import statement and eval the BUILTINS array literal.
const stripped = completionsSrc
  .replace(/^import.*?;?\s*$/gm, "")
  .replace(/^export\s+const\s+builtinCompletions\s*=.*?;?\s*$/gm, "")
  .replace(/^export\s+/gm, "");

let BUILTINS;
try {
  // eslint-disable-next-line no-new-func
  BUILTINS = new Function(`${stripped}; return BUILTINS;`)();
} catch (e) {
  throw new Error(`Failed to parse completions.js: ${e.message}`);
}

const completionsByLabel = new Map(BUILTINS.map((b) => [b.label, b]));

// ── 3. Load builtin_meta.edn — hand-rolled parser for the subset we use ──────
function parseEdn(src) {
  // Strip line comments
  src = src.replace(/;[^\n]*/g, "");

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
        if (src[pos] === "\\") pos++;
        s += src[pos++];
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

// ── 4. Load hover.js ─────────────────────────────────────────────────────────
const hoverPath = path.join(root, "app/src/repulse/lisp-lang/hover.js");
const hoverSrc = readFileSync(hoverPath, "utf8");

// Strip CM6 imports + exports, extract DOCS map
const hoverStripped = hoverSrc
  .replace(/^import.*?;?\s*$/gm, "")
  .replace(/^export\s+const\s+lispHoverTooltip.*[\s\S]*$/m, "")
  .replace(/^export\s+/gm, "");

let DOCS;
try {
  // eslint-disable-next-line no-new-func
  DOCS = new Function(`${hoverStripped}; return DOCS;`)();
} catch (e) {
  throw new Error(`Failed to parse hover.js: ${e.message}`);
}

// ── 5. Validate: every completions name must be in builtin_meta.edn ──────────
const errors = [];
for (const { label } of BUILTINS) {
  if (!(label in meta)) {
    errors.push(`MISSING from builtin_meta.edn: "${label}"`);
  }
}
if (errors.length > 0) {
  console.error("gen:ai-docs FAILED — metadata drift detected:");
  errors.forEach((e) => console.error(" ", e));
  process.exit(1);
}

// ── 6. Infer category from completion type ───────────────────────────────────
function inferCategory(type) {
  if (type === "keyword") return "special";
  return "util";
}

// ── 7. Build output ──────────────────────────────────────────────────────────
const output = {};
for (const name of grammarNames) {
  const completion = completionsByLabel.get(name);
  if (!completion) {
    // Grammar name not in completions — skip with a note (internal forms)
    continue;
  }
  const hoverDoc = DOCS[name] ?? {};
  const extraMeta = meta[name] ?? {};

  output[name] = {
    category: extraMeta["category"] ?? inferCategory(completion.type),
    signature: hoverDoc.signature ?? completion.detail,
    returns: extraMeta["returns"] ?? "any",
    "side-effects": extraMeta["side-effects"] ?? [],
    examples:
      extraMeta["examples"] ??
      (hoverDoc.example ? [hoverDoc.example] : []),
    "see-also": extraMeta["see-also"] ?? [],
    detail: completion.detail,
    description: hoverDoc.description ?? "",
  };
}

// Also include completions names not in grammar (e.g. samples!, sample-banks)
for (const { label } of BUILTINS) {
  if (label in output) continue; // already written
  const hoverDoc = DOCS[label] ?? {};
  const extraMeta = meta[label] ?? {};
  const completion = completionsByLabel.get(label);
  output[label] = {
    category: extraMeta["category"] ?? inferCategory(completion.type),
    signature: hoverDoc.signature ?? completion.detail,
    returns: extraMeta["returns"] ?? "any",
    "side-effects": extraMeta["side-effects"] ?? [],
    examples:
      extraMeta["examples"] ??
      (hoverDoc.example ? [hoverDoc.example] : []),
    "see-also": extraMeta["see-also"] ?? [],
    detail: completion.detail,
    description: hoverDoc.description ?? "",
  };
}

// ── 8. Write output ──────────────────────────────────────────────────────────
const outDir = path.join(root, "docs/ai");
mkdirSync(outDir, { recursive: true });
const outPath = path.join(outDir, "builtins.json");
writeFileSync(outPath, JSON.stringify(output, null, 2) + "\n");
console.log(
  `gen:ai-docs — wrote ${Object.keys(output).length} entries to docs/ai/builtins.json`
);
