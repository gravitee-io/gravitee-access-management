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

# Runs a specific spec file or folder in a loop until a failure occurs.
# Usage: ./scripts/run-spec-until-fail.sh <spec_path> [max_runs]
#   spec_path: path to a spec file or folder (relative to gravitee-am-test)
#   max_runs:  optional limit on iterations (default: unlimited)
#
# Examples:
#   ./scripts/run-spec-until-fail.sh specs/gateway/oauth2/token.spec.ts
#   ./scripts/run-spec-until-fail.sh specs/gateway/oauth2 10

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <spec_path> [max_runs]"
  echo "  spec_path: path to spec file or folder (relative to gravitee-am-test)"
  echo "  max_runs:  optional limit on iterations (default: unlimited)"
  exit 1
fi

SPEC_PATH="$1"
MAX_RUNS=${2:-0}
PASS_COUNT=0
LOG_DIR="./.logs/jest-stability"
rm -rf "$LOG_DIR"
mkdir -p "$LOG_DIR"

echo "Starting stability test loop for: $SPEC_PATH"
echo "Max runs: ${MAX_RUNS:-unlimited}"
echo "Logs: $LOG_DIR"
echo "---"

while true; do
  RUN=$((PASS_COUNT + 1))

  if [[ $MAX_RUNS -gt 0 && $RUN -gt $MAX_RUNS ]]; then
    echo "Reached max runs ($MAX_RUNS). All passed."
    break
  fi

  START=$(date +%s)
  START_FMT=$(date -r "$START" +%H:%M)
  echo "[Run $RUN] Started at $START_FMT — $SPEC_PATH"
  if ! npm --prefix gravitee-am-test run ci --silent -- "$SPEC_PATH" > "$LOG_DIR/run-${RUN}.log" 2>&1; then
    DUR=$(( $(date +%s) - START ))
    echo "[Run $RUN] FAILED after ${DUR}s ($PASS_COUNT successful runs)."
    echo "Log: $LOG_DIR/run-${RUN}.log"
    exit 1
  fi
  DUR=$(( $(date +%s) - START ))
  MINS=$((DUR / 60))
  SECS=$((DUR % 60))

  PASS_COUNT=$RUN
  echo "[Run $RUN] PASSED in ${MINS}m${SECS}s ($PASS_COUNT total)"
done
