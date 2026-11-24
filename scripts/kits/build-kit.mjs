#!/usr/bin/env node
// KIT1 Part D — build a layered drum kit for REPuLse from an openly licensed source.
//
//   node scripts/kits/build-kit.mjs scripts/kits/JazzKit.json [--fetch] [--dry]
//
// --fetch  download the needed source files from GitHub (raw.githubusercontent.com)
//          into .kit-sources/ (gitignored). Existing files are kept.
// --dry    print the selection and exit without encoding.
//
// Node >= 18, no npm dependencies. Shells out to ffmpeg/ffprobe (build-time tool only).
// Output: app/public/kits/<Kit>/<inst>/v<L>-<T>.<ogg|flac> and the kit's entries merged into
// app/public/kits/kits.json (sorted keys, fixed number formatting → reproducible).
// See PROMPTS/PHASE-KIT1.md (Part D, Formulas).

import { spawnSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { mkdirSync, existsSync, readFileSync, writeFileSync, rmSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const SRC = join(ROOT, '.kit-sources');
const OUT = join(ROOT, 'app', 'public', 'kits');
const SR = 44100;

const BUDGET = { perFile: 300 * 1024, perKit: 6 * 1024 * 1024, total: 12 * 1024 * 1024 };
const LAYER_BOUNDS = { 1: [1], 2: [0.6, 1], 3: [0.4, 0.75, 1], 4: [0.3, 0.6, 0.85, 1] };
const STEREO_DEFAULT = new Set(['cr', 'rd', 'rb', 'oh']);

const args = process.argv.slice(2);
const cfgPath = args.find((a) => !a.startsWith('--'));
const FETCH = args.includes('--fetch');
const DRY = args.includes('--dry');
if (!cfgPath) { console.error('usage: build-kit.mjs <config.json> [--fetch] [--dry]'); process.exit(2); }
const cfg = JSON.parse(readFileSync(resolve(cfgPath), 'utf8'));
const FORMAT = cfg.format ?? 'ogg';

// ── helpers ────────────────────────────────────────────────────────────────

const db = (x) => 20 * Math.log10(Math.max(x, 1e-12));
const fromDb = (d) => Math.pow(10, d / 20);
const fmt = (x, n = 3) => Number(x.toFixed(n));

async function download(url, dest) {
  if (existsSync(dest) && statSync(dest).size > 0) return;
  const r = await fetch(url);
  if (!r.ok) throw new Error(`HTTP ${r.status} for ${url}`);
  mkdirSync(dirname(dest), { recursive: true });
  writeFileSync(dest, Buffer.from(await r.arrayBuffer()));
}

async function pool(items, n, fn) {
  const q = items.slice();
  await Promise.all(Array.from({ length: n }, async () => {
    while (q.length) await fn(q.shift());
  }));
}

const rawUrl = (path) =>
  `https://raw.githubusercontent.com/${cfg.repo}/${cfg.branch}/` +
  path.split('/').map(encodeURIComponent).join('/');
const srcPath = (path) => join(SRC, cfg.repo.split('/')[1], path);

/** Source file as a local path; downloaded first with --fetch. */
async function source(path) {
  const p = srcPath(path);
  if (!existsSync(p)) {
    if (!FETCH) throw new Error(`missing ${p} (run with --fetch)`);
    await download(rawUrl(path), p);
  }
  return p;
}

/** Decode any audio file to {ch, data: Float32Array[]} at 44.1 kHz. */
function decode(path, channels) {
  const r = spawnSync('ffmpeg', ['-nostdin', '-v', 'error', '-i', path, '-ar', String(SR), '-ac', String(channels),
    '-f', 'f32le', '-'], { maxBuffer: 1 << 30 });
  if (r.status !== 0) throw new Error(`ffmpeg decode failed for ${path}: ${r.stderr}`);
  const b = r.stdout;
  const all = new Float32Array(b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength));
  const n = all.length / channels;
  const data = Array.from({ length: channels }, () => new Float32Array(n));
  for (let i = 0; i < n; i++) for (let c = 0; c < channels; c++) data[c][i] = all[i * channels + c];
  return data;
}

/** Encode to FORMAT: "ogg" (libvorbis -q:a 5) or "flac" (16-bit, lossless). */
function encode(data, dest) {
  const ch = data.length, n = data[0].length;
  const inter = new Float32Array(n * ch);
  for (let i = 0; i < n; i++) for (let c = 0; c < ch; c++) inter[i * ch + c] = data[c][i];
  mkdirSync(dirname(dest), { recursive: true });
  // Raw PCM goes through a temp file, not a pipe: a piped ffmpeg occasionally stalled.
  const tmp = join(tmpdir(), `kit-${process.pid}.f32`);
  writeFileSync(tmp, Buffer.from(inter.buffer));
  const r = spawnSync('ffmpeg', ['-nostdin', '-v', 'error', '-y', '-f', 'f32le', '-ar', String(SR), '-ac', String(ch),
    '-i', tmp, ...(FORMAT === 'flac'
      ? ['-c:a', 'flac', '-compression_level', '8', '-sample_fmt', 's16']
      : ['-c:a', 'libvorbis', '-q:a', '5']),
    '-map_metadata', '-1', '-fflags', '+bitexact', '-flags:a', '+bitexact', dest], { maxBuffer: 1 << 24 });
  rmSync(tmp, { force: true });
  if (r.status !== 0) throw new Error(`ffmpeg encode failed for ${dest}: ${r.stderr}`);
}

// ── source adapters: instrument cfg → groups [{files: [...]}] soft → hard ───

/** virtuosity: files <prefix>_vl<N>[_rr<M>].<ext>; group = vl, takes = rr. */
async function groupsVirtuosity(inst) {
  const treeFile = join(SRC, `${cfg.repo.split('/')[1]}.tree.json`);
  if (!existsSync(treeFile)) {
    if (!FETCH) throw new Error(`missing ${treeFile} (run with --fetch)`);
    const r = await fetch(`https://api.github.com/repos/${cfg.repo}/git/trees/${cfg.branch}?recursive=1`);
    if (!r.ok) throw new Error(`tree API HTTP ${r.status}`);
    mkdirSync(SRC, { recursive: true });
    writeFileSync(treeFile, JSON.stringify((await r.json()).tree.map((t) => t.path)));
  }
  const paths = JSON.parse(readFileSync(treeFile, 'utf8'));
  const re = new RegExp(`^${inst.src.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}_vl(\\d+)(?:_rr(\\d+))?\\.(flac|wav)$`);
  const by = new Map();
  for (const p of paths) {
    const m = re.exec(p);
    if (!m) continue;
    const vl = +m[1];
    if (!by.has(vl)) by.set(vl, []);
    by.get(vl).push({ rr: +(m[2] || 1), path: p });
  }
  if (!by.size) throw new Error(`no source files for ${inst.src}`);
  return [...by.entries()].sort((a, b) => a[0] - b[0])
    .map(([, fs]) => ({ files: fs.sort((a, b) => a.rr - b.rr).map((f) => ({ paths: [{ path: f.path }] })) }));
}

/** drs: DrumGizmo SFZ port. The region file lists samples in velocity order; a region
 *  with seq_position=1 starts a new velocity group (its seq_length regions are the
 *  round-robin takes). Each take is a mix of several mic files. */
async function groupsDrs(inst) {
  const text = readFileSync(await source(inst.region), 'utf8');
  const instr = /#define \$instr (\S+)/.exec(text)?.[1] ?? inst.dir;
  const groups = [];
  for (const line of text.split('\n')) {
    if (!line.startsWith('<region>')) continue;
    const sample = /sample=(\S+)/.exec(line)?.[1];
    const pos = +(/seq_position=(\d+)/.exec(line)?.[1] ?? 1);
    if (!sample) continue;
    if (pos === 1 || !groups.length) groups.push({ files: [] });
    const base = sample.replace(/\$instr/g, instr);
    const paths = [];
    for (const [bus, mics] of Object.entries(inst.mics)) {
      for (const [mic, gain] of Object.entries(mics)) {
        paths.push({ path: `${cfg.samples}/${base.replace(/\$mic/g, mic).replace('$ext', 'flac')}`, bus, gain });
      }
    }
    groups.at(-1).files.push({ paths });
  }
  if (!groups.length) throw new Error(`no regions in ${inst.region}`);
  return groups;
}

const ADAPTERS = { virtuosity: groupsVirtuosity, drs: groupsDrs };

// ── selection ───────────────────────────────────────────────────────────────

function selectTakes(groups, layerCount, takeCount, bounds) {
  const G = groups.length;
  return bounds.map((v, j) => {
    const lo = j === 0 ? 0 : bounds[j - 1];
    const centre = j === layerCount - 1 ? 1 : (lo + v) / 2;
    const gi = Math.min(G - 1, Math.max(0, Math.round(centre * (G - 1))));
    const picked = groups[gi].files.slice(0, takeCount);
    // Sources with fewer round-robin takes than wanted borrow from neighbouring groups.
    for (let d = 1; picked.length < takeCount && d < G; d++) {
      for (const k of [gi + d, gi - d]) {
        if (picked.length < takeCount && k >= 0 && k < G) picked.push(groups[k].files[0]);
      }
    }
    return { v, files: picked };
  });
}

// ── DSP ─────────────────────────────────────────────────────────────────────

/** Mix the mic files of one take to `stereo` ? 2 : 1 channels. */
function loadTake(take, stereo) {
  const len0 = take.paths;
  let L, R;
  if (len0.length === 1 && !len0[0].bus) {
    [L, R] = decode(srcPath(len0[0].path), 2);
  } else {
    const mixed = len0.map(({ path, bus, gain }) => ({ bus, gain: gain ?? 1, d: decode(srcPath(path), 1)[0] }));
    const len = Math.max(...mixed.map((m) => m.d.length));
    L = new Float32Array(len); R = new Float32Array(len);
    for (const { bus, gain, d } of mixed) {
      const k = bus === 'c' ? Math.SQRT1_2 : 1;
      for (let i = 0; i < d.length; i++) {
        const x = d[i] * gain * k;
        if (bus !== 'r') L[i] += x;
        if (bus !== 'l') R[i] += x;
      }
    }
  }
  if (stereo) return [L, R];
  const M = new Float32Array(L.length);
  for (let i = 0; i < M.length; i++) M[i] = 0.5 * (L[i] + R[i]);
  return [M];
}

function peakOf(ch) { let p = 0; for (const c of ch) for (let i = 0; i < c.length; i++) p = Math.max(p, Math.abs(c[i])); return p; }

/** Trim lead silence (first sample > −50 dBFS, minus 1 ms), cut the tail at −60 dBFS
 *  (20 ms windows), cap the length, 5 ms fade-out. */
function trim(ch, maxSecs) {
  const n = ch[0].length, thr = fromDb(-50);
  let start = 0;
  outer: for (let i = 0; i < n; i++) for (const c of ch) if (Math.abs(c[i]) > thr) { start = i; break outer; }
  start = Math.max(0, start - Math.round(0.001 * SR));
  const win = Math.round(0.02 * SR), tailThr = fromDb(-60);
  let end = n;
  for (let w = n - win; w > start; w -= win) {
    let s = 0;
    for (const c of ch) for (let i = w; i < w + win; i++) s += c[i] * c[i];
    if (Math.sqrt(s / (win * ch.length)) > tailThr) { end = w + win; break; }
    end = w;
  }
  end = Math.min(end, start + Math.round(maxSecs * SR), n);
  const out = ch.map((c) => c.slice(start, end));
  const fade = Math.min(Math.round(0.005 * SR), out[0].length);
  for (const c of out) for (let i = 0; i < fade; i++) c[c.length - 1 - i] *= i / fade;
  return out;
}

/** RMS of the first 250 ms (all channels), the loudness measure for layer gains. */
function rms250(ch) {
  const n = Math.min(ch[0].length, Math.round(0.25 * SR));
  let s = 0;
  for (const c of ch) for (let i = 0; i < n; i++) s += c[i] * c[i];
  return Math.sqrt(s / (n * ch.length));
}

// ── main ────────────────────────────────────────────────────────────────────

const kitDir = join(OUT, cfg.kit);
const report = [];
const entries = {};
const plan = [];

for (const [name, inst] of Object.entries(cfg.instruments)) {
  const groups = await ADAPTERS[cfg.adapter](inst);
  const layerCount = inst.layers ?? 3, takeCount = inst.takes ?? 2;
  const bounds = inst.bounds ?? LAYER_BOUNDS[layerCount];
  if (!bounds) throw new Error(`${name}: no default bounds for ${layerCount} layers`);
  const sel = selectTakes(groups, layerCount, takeCount, bounds);
  plan.push({ name, inst, sel });
  console.log(`${name}: ${groups.length} source groups → ${layerCount} layers × ${takeCount} takes`);
}
const needed = [...new Set(plan.flatMap((p) => p.sel.flatMap((l) => l.files.flatMap((f) => f.paths.map((x) => x.path)))))];
console.log(`${needed.length} source files`);
if (FETCH) await pool(needed, 8, (p) => source(p));
if (DRY) process.exit(0);

rmSync(kitDir, { recursive: true, force: true });
let kitBytes = 0, over = false;

for (const { name, inst, sel } of plan) {
  const stereo = inst.stereo ?? STEREO_DEFAULT.has(name);
  const maxSecs = inst.maxSecs ?? (stereo ? 4 : 1.5);
  const layers = sel.map((l) => ({
    v: l.v,
    takes: l.files.map((f) => trim(loadTake(f, stereo), maxSecs)),
  }));
  // Layer loudness (power mean over its takes), then gains following L(v) = 40·log10 v
  // relative to the top layer (a v² power law). Soft layers get gain > 1.
  const lay = layers.map((l) => ({ ...l, rms: Math.sqrt(l.takes.reduce((s, t) => s + rms250(t) ** 2, 0) / l.takes.length) }));
  const top = lay.at(-1);
  let gains = lay.map((l) => Math.min(4, Math.max(0.05, (top.rms * l.v ** 2) / l.rms)));
  gains[gains.length - 1] = 1;
  for (let i = 1; i < lay.length; i++) if (lay[i].rms < lay[i - 1].rms * 0.9) {
    console.warn(`  warn ${name}: layer ${i + 1} is quieter than layer ${i} — check the source order`);
  }
  // One scalar per instrument: loudest file after gain peaks at −1 dBFS (+ config trim, ≤ 0).
  const peak = Math.max(...lay.map((l, i) => Math.max(...l.takes.map((t) => peakOf(t))) * gains[i]));
  const scale = fromDb(-1 + Math.min(0, inst.level ?? 0)) / peak;
  const outLayers = [];
  lay.forEach((l, i) => {
    const files = [];
    l.takes.forEach((t, k) => {
      const data = t.map((c) => c.map((x) => x * scale));
      const rel = `${cfg.kit}/${name}/v${i + 1}-${k + 1}.${FORMAT}`;
      const dest = join(OUT, rel);
      encode(data, dest);
      const size = statSync(dest).size;
      kitBytes += size;
      if (size > BUDGET.perFile) { over = true; console.error(`  over file budget: ${rel} ${size} B`); }
      files.push(rel);
    });
    outLayers.push({ v: fmt(l.v), gain: fmt(gains[i]), files });
  });
  const entry = { layers: outLayers };
  if (inst.choke) entry.choke = inst.choke;
  entries[`${cfg.kit}_${name}`] = entry;
  report.push(`${name.padEnd(4)} ${lay.length}×${lay[0].takes.length} ${stereo ? 'stereo' : 'mono  '} gains ${gains.map((g) => fmt(g, 2)).join('/')}`);
}

// merge into kits.json, replacing this kit's entries
const jsonPath = join(OUT, 'kits.json');
const prev = existsSync(jsonPath) ? JSON.parse(readFileSync(jsonPath, 'utf8')) : {};
const merged = {};
for (const [k, v] of Object.entries(prev)) if (!k.startsWith(`${cfg.kit}_`) && !k.startsWith('_')) merged[k] = v;
Object.assign(merged, entries);
const sorted = { _base: 'kits/', _format: 'repulse-kit/1' };
for (const k of Object.keys(merged).sort()) sorted[k] = merged[k];
writeFileSync(jsonPath, JSON.stringify(sorted, null, 1) + '\n');

console.log(report.join('\n'));
console.log(`${cfg.kit}: ${(kitBytes / 1048576).toFixed(2)} MB in ${Object.keys(entries).length} instruments`);
if (kitBytes > BUDGET.perKit) { over = true; console.error(`over kit budget (${BUDGET.perKit} B)`); }
let total = 0;
for (const e of Object.values(sorted)) if (typeof e === 'object') for (const l of e.layers) for (const f of l.files) total += statSync(join(OUT, f)).size;
console.log(`all kits: ${(total / 1048576).toFixed(2)} MB (budget ${BUDGET.total / 1048576} MB)`);
if (total > BUDGET.total) over = true;
process.exit(over ? 1 : 0);
