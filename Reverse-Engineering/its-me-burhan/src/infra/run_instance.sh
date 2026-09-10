#!/usr/bin/env bash
# One JVM per TCP connection, in a throwaway working dir.
# Expects env: BURHAN_OUT (compiled classes dir), BURHAN_SEED, BURHAN_FLAG.
set -euo pipefail
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
cd "$WORK"
: > guild_history.log
exec java -cp "$BURHAN_OUT" Main