#!/usr/bin/env bash
# Run all Postman collections with bounded concurrency.
#
# Each collection provisions its own uniquely-named domain (flows-app-version,
# oauth2-app-version, dcr-app-version, ...), so collections are independent and
# safe to run in parallel. Concurrency is capped (NEWMAN_PARALLELISM, default 4)
# to avoid overwhelming the single-container gateway with simultaneous domain
# deploys. Each collection logs to its own file; on failure the failing logs are
# printed and the script exits non-zero.
set -uo pipefail

cd "$(dirname "$0")/.."

PARALLELISM="${NEWMAN_PARALLELISM:-4}"
LOGDIR="$(mktemp -d)"
export LOGDIR

run_one() {
  local f="$1" base start rc
  base="$(basename "$f")"
  start="$(date +%s)"
  if newman run "$f" -e environment/docker.json --ignore-redirects --insecure --bail \
        > "$LOGDIR/$base.log" 2>&1; then
    echo "PASS $base ($(($(date +%s) - start))s)"
  else
    rc=$?
    echo "FAIL $base rc=$rc ($(($(date +%s) - start))s)"
    touch "$LOGDIR/$base.failed"
  fi
}
export -f run_one

echo "Running $(ls collections/*.json | wc -l | tr -d ' ') collections, ${PARALLELISM} at a time"
ls collections/*.json | xargs -P "$PARALLELISM" -I{} bash -c 'run_one "$@"' _ {}

if compgen -G "$LOGDIR/*.failed" > /dev/null; then
  echo "=== Failed collection logs ==="
  for ff in "$LOGDIR"/*.failed; do
    b="$(basename "$ff" .failed)"
    echo "----- $b -----"
    cat "$LOGDIR/$b.log"
  done
  exit 1
fi

echo "All collections passed."
