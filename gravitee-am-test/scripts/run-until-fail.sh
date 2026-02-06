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

# Runs gateway and management tests in a loop until a failure occurs.
# Usage: ./scripts/run-until-fail.sh [max_runs]
#   max_runs: optional limit on iterations (default: unlimited)

set -euo pipefail

MAX_RUNS=${1:-0}
PASS_COUNT=0
LOG_DIR="./.logs/jest-stability"
rm -rf "$LOG_DIR"
mkdir -p "$LOG_DIR"

echo "Starting stability test loop (max_runs=${MAX_RUNS:-unlimited})..."
echo "Logs: $LOG_DIR"
echo "---"

while true; do
  RUN=$((PASS_COUNT + 1))

  if [[ $MAX_RUNS -gt 0 && $RUN -gt $MAX_RUNS ]]; then
    echo "Reached max runs ($MAX_RUNS). All passed."
    break
  fi

  echo 'Resetting docker env...'
  npm --prefix docker/local-stack run stack:down --silent > /dev/null 2>&1
  npm --prefix docker/local-stack run stack:dev:setup:mongo --silent > /dev/null 2>&1

  GW_START=$(date +%s)
  GW_START_FMT=$(date -r "$GW_START" +%H:%M)
  GW_ETA_FMT=$(date -r $((GW_START + 9 * 60)) +%H:%M)
  echo "[Run $RUN] Gateway: Started at $GW_START_FMT, finishes at ~$GW_ETA_FMT"
  if ! npm --prefix gravitee-am-test run ci:gateway --silent -- > "$LOG_DIR/run-${RUN}-gateway.log" 2>&1; then
    GW_DUR=$(( $(date +%s) - GW_START ))
    echo "[Run $RUN] GATEWAY FAILED after ${GW_DUR}s ($PASS_COUNT successful runs)."
    echo "Log: $LOG_DIR/run-${RUN}-gateway.log"
    exit 1
  fi
  GW_DUR=$(( $(date +%s) - GW_START ))
  GW_MINS=$((GW_DUR / 60))
  GW_SECS=$((GW_DUR % 60))
  echo "[Run $RUN] Gateway PASSED in ${GW_MINS}m${GW_SECS}s"

  echo 'Resetting docker env...'
  npm --prefix docker/local-stack run stack:down --silent > /dev/null 2>&1
  npm --prefix docker/local-stack run stack:dev:setup:mongo --silent > /dev/null 2>&1

  MGMT_START=$(date +%s)
  MGMT_START_FMT=$(date -r "$MGMT_START" +%H:%M)
  MGMT_ETA_FMT=$(date -r $((MGMT_START + 3 * 60)) +%H:%M)
  echo "[Run $RUN] Management: Started at $MGMT_START_FMT, finishes at ~$MGMT_ETA_FMT"
  if ! npm --prefix gravitee-am-test run ci:management:parallel --silent -- > "$LOG_DIR/run-${RUN}-management.log" 2>&1; then
    MGMT_DUR=$(( $(date +%s) - MGMT_START ))
    echo "[Run $RUN] MANAGEMENT FAILED after ${MGMT_DUR}s ($PASS_COUNT successful runs)."
    echo "Log: $LOG_DIR/run-${RUN}-management.log"
    exit 1
  fi
  MGMT_DUR=$(( $(date +%s) - MGMT_START ))
  MGMT_MINS=$((MGMT_DUR / 60))
  MGMT_SECS=$((MGMT_DUR % 60))
  echo "[Run $RUN] Management PASSED in ${MGMT_MINS}m${MGMT_SECS}s"

  PASS_COUNT=$RUN
  echo "[Run $RUN] PASSED ($PASS_COUNT total)"
done
