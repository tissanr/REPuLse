#!/bin/bash
# Cloud-session setup: JS deps + WASM build. wasm-pack, wasm-bindgen and
# wasm-opt come from the environment's (cached) setup script — wasm-pack's own
# downloader can't reach GitHub through the session proxy. Local sessions are
# left untouched.
set -euo pipefail

[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0

cd "$CLAUDE_PROJECT_DIR"

if command -v wasm-pack >/dev/null 2>&1; then
  npm run build:wasm
else
  echo "session-start: wasm-pack not found — skipping build:wasm." >&2
  echo "Install wasm-pack, wasm-bindgen-cli and binaryen in the environment setup script." >&2
fi

# ci, not install: npm install rewrites package-lock.json here (drops the
# repulse-audio workspace link); ci installs exactly what is committed.
npm ci --no-audit --no-fund
