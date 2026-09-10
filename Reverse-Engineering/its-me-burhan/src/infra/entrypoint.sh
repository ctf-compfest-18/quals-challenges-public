#!/usr/bin/env bash

set -euo pipefail

FLAG_ENV="${FLAG_ENV:-FLAG}"
flag="${!FLAG_ENV:-${BURHAN_FLAG:-}}"
if [ -z "$flag" ]; then
  echo "WARN: no flag in \$$FLAG_ENV or \$BURHAN_FLAG; using dev default" >&2
  flag="COMPFEST18{flag_not_injected_check_plugin}"
fi

seed="${BURHAN_SEED:-$(printf '%s' "$flag" | sha256sum | cut -d' ' -f1)}"
export BURHAN_FLAG="$flag"
export BURHAN_SEED="$seed"
export BURHAN_OUT="${BURHAN_OUT:-/app/out}"
PORT="${PORT:-9000}"

if [ "${BURHAN_DRYRUN:-0}" = "1" ]; then echo "flag=$BURHAN_FLAG"; echo "seed=$BURHAN_SEED"; exit 0; fi
exec socat TCP-LISTEN:"$PORT",reuseaddr,fork EXEC:/app/src/infra/run_instance.sh,pty,stderr,setsid,sane
