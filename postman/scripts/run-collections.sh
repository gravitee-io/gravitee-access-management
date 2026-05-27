#!/bin/bash
#
# Copyright (C) 2015 The Gravitee team (http://gravitee.io)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

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

# Run with bounded concurrency using background jobs (forks of THIS shell), so
# each worker inherits the exact environment npm set up for the script - notably
# node on PATH. (A previous xargs/`bash -c` approach spawned fresh shells that
# re-sourced CI's BASH_ENV and lost node, breaking newman's env-node shebang.)
echo "Running $(ls collections/*.json | wc -l | tr -d ' ') collections, ${PARALLELISM} at a time"
i=0
for f in collections/*.json; do
  run_one "$f" &
  i=$((i + 1))
  # Wait for the current batch to drain before launching the next. Plain `wait`
  # (vs `wait -n`) keeps this portable across bash versions.
  if [ $((i % PARALLELISM)) -eq 0 ]; then
    wait
  fi
done
wait

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
