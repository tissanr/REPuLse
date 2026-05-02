import { createReadStream, existsSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize, resolve } from 'node:path';

const root = resolve(process.argv[2] ?? 'app/public');
const port = Number(process.argv[3] ?? 3000);
const host = process.argv[4] ?? '127.0.0.1';

const types = {
  '.css': 'text/css',
  '.html': 'text/html',
  '.js': 'text/javascript',
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

  res.writeHead(200, {
    'Content-Type': types[extname(file)] ?? 'application/octet-stream',
  });
  createReadStream(file).pipe(res);
}).listen(port, host, () => {
  console.log(`Serving ${root} at http://${host}:${port}`);
});
