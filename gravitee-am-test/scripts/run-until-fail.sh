#!/usr/bin/env bash
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

  echo "[Run $RUN] Starting gateway tests..."
  if ! npm --prefix gravitee-am-test run ci:gateway --silent -- --bail > "$LOG_DIR/run-${RUN}-gateway.log" 2>&1; then
    echo "[Run $RUN] GATEWAY FAILED after $PASS_COUNT successful runs."
    echo "Log: $LOG_DIR/run-${RUN}-gateway.log"
    exit 1
  fi

  echo "[Run $RUN] Starting management tests (parallel)..."
  if ! npm --prefix gravitee-am-test run ci:management:parallel --silent -- > "$LOG_DIR/run-${RUN}-management.log" 2>&1; then
    echo "[Run $RUN] MANAGEMENT FAILED after $PASS_COUNT successful runs."
    echo "Log: $LOG_DIR/run-${RUN}-management.log"
    exit 1
  fi

  PASS_COUNT=$RUN
  echo "[Run $RUN] PASSED ($PASS_COUNT total)"
done
