#!/usr/bin/env bash
set -euo pipefail

# shadow-cljs 3.x requires Java 21; Vercel's build image ships Java 11.
# Download Temurin 21 and prepend it to PATH for this build session.
JDK_DIR=/tmp/jdk21
if ! java -version 2>&1 | grep -q 'version "21'; then
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" \
    -o /tmp/jdk21.tar.gz
  mkdir -p "$JDK_DIR"
  tar xzf /tmp/jdk21.tar.gz -C "$JDK_DIR" --strip-components=1
  export JAVA_HOME="$JDK_DIR"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

cargo install --locked wasm-pack@0.14.0
npm run build:wasm
npx shadow-cljs release app
