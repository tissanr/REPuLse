import { createReadStream, existsSync, readFileSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { dirname, extname, join, normalize, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// Usage: node scripts/serve-static.mjs [root] [port] [host] [--no-headers] [--dev-eval]
// By default the `headers` of vercel.json (CSP, X-Frame-Options, …) are sent for
// matching `source` patterns, so CSP violations show up in the e2e suite (TEST3).
//   --no-headers  send nothing from vercel.json (debugging)
//   --dev-eval    add 'unsafe-eval' to the CSP script-src. Only for serving a
//                 shadow-cljs *dev* build (`compile app`), which loads its modules
//                 through eval(); the release build the deployment serves does not.
//                 Every other CSP directive stays exactly as in vercel.json.
const flags = process.argv.slice(2).filter((a) => a.startsWith('--'));
const args = process.argv.slice(2).filter((a) => !a.startsWith('--'));
const root = resolve(args[0] ?? 'app/public');
const port = Number(args[1] ?? 3000);
const host = args[2] ?? '127.0.0.1';
const sendHeaders = !flags.includes('--no-headers');
const devEval = flags.includes('--dev-eval');

/** vercel.json `source` → RegExp. Supports exact paths and `(.*)` / `(…)` groups. */
function sourceToRegExp(source) {
  try {
    return new RegExp(`^${source}$`);
  } catch {
    return null;
  }
}

function loadHeaderRules() {
  if (!sendHeaders) return [];
  const file = join(dirname(fileURLToPath(import.meta.url)), '..', 'vercel.json');
  if (!existsSync(file)) return [];
  const config = JSON.parse(readFileSync(file, 'utf8'));
  return (config.headers ?? [])
    .map((rule) => ({ re: sourceToRegExp(rule.source), headers: rule.headers ?? [] }))
    .filter((rule) => rule.re);
}

const headerRules = loadHeaderRules();

function headersFor(pathname) {
  const out = {};
  for (const rule of headerRules) {
    if (!rule.re.test(pathname)) continue;
    for (const { key, value } of rule.headers) {
      out[key] =
        devEval && key.toLowerCase() === 'content-security-policy'
          ? value.replace('script-src ', "script-src 'unsafe-eval' ")
          : value;
    }
  }
  return out;
}

const types = {
  '.css': 'text/css',
  '.html': 'text/html',
  '.js': 'text/javascript',
  '.mjs': 'text/javascript',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.json': 'application/json',
  '.wasm': 'application/wasm',
};

function resolvePath(url) {
  const pathname = decodeURIComponent(new URL(url, `http://${host}:${port}`).pathname);
  const requested = normalize(pathname).replace(/^(\.\.[/\\])+/, '');
  const file = resolve(join(root, requested));
  if (!file.startsWith(root)) return null;
  if (existsSync(file) && statSync(file).isDirectory()) {
    return join(file, 'index.html');
  }
  return file;
}

createServer((req, res) => {
  const file = resolvePath(req.url ?? '/');
  if (!file || !existsSync(file) || !statSync(file).isFile()) {
    res.writeHead(404);
    res.end('Not found');
    return;
  }

  const pathname = new URL(req.url ?? '/', `http://${host}:${port}`).pathname;
  res.writeHead(200, {
    ...headersFor(pathname),
    'Content-Type': types[extname(file)] ?? 'application/octet-stream',
  });
  createReadStream(file).pipe(res);
}).listen(port, host, () => {
  console.log(`Serving ${root} at http://${host}:${port}`);
});
