#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "== cljs-unit =="
npm test

echo "== rust-audio =="
cargo test --manifest-path packages/audio/Cargo.toml

echo "== browser-offline =="
npm run test:e2e
