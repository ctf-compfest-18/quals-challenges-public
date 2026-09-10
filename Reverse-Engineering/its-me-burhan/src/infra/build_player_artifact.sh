#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
rm -rf "$ROOT/out" "$ROOT/dist"
mkdir -p "$ROOT/out" "$ROOT/dist"
javac --release 17 -d "$ROOT/out" $(find "$ROOT" -name '*.java' -not -path '*/test/*')
( cd "$ROOT/out" && jar cfe "$ROOT/dist/burhanquest.jar" Main . )
echo "[build] wrote dist/burhanquest.jar"