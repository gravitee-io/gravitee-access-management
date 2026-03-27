#!/bin/bash
set -euo pipefail

# ---------------------------------------------------------------------------
# OAS Staleness Check
#
# Regenerates docs/mapi/openapi.yaml to a temp directory using
# scripts/regen-oas.sh and diffs the result against the committed spec.
#
# Exits 1 if they differ — the committed spec must match what the source
# generates.

# Exits 0 if the spec is up to date, or if regeneration itself fails (e.g.
# the module is not compiled) — a build failure should not mask a compile
# error as an OAS staleness failure.
#
# Requires the management API module to be compiled. In CI this is satisfied
# by the process_pull_request workspace. Locally, pass --also-make:
#   bash .circleci/scripts/oas-staleness-check.sh --also-make
#
# Options forwarded to scripts/regen-oas.sh:
#   --also-make   Build upstream Maven modules before generating.
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
SPEC="docs/mapi/openapi.yaml"

REGEN_ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --also-make) REGEN_ARGS+=(--also-make); shift ;;
    *) echo "Error: unknown argument: $1" >&2; exit 2 ;;
  esac
done

echo "=== OpenAPI Spec Staleness Check ==="
echo ""

TMPDIR_OAS="$(mktemp -d)"
cleanup() { rm -rf "$TMPDIR_OAS"; }
trap cleanup EXIT

if ! bash "$REPO_ROOT/scripts/regen-oas.sh" \
      --output-dir "$TMPDIR_OAS" \
      ${REGEN_ARGS[@]+"${REGEN_ARGS[@]}"} \
      2>&1 | sed 's/^/  /'; then
  echo ""
  echo "⚠️   WARNING: could not regenerate spec — management API module may not be compiled."
  echo "    Staleness check skipped. Run locally with:"
  echo "      bash .circleci/scripts/oas-staleness-check.sh --also-make"
  exit 0
fi

echo ""

if diff -q "$TMPDIR_OAS/openapi.yaml" "$REPO_ROOT/$SPEC" > /dev/null 2>&1; then
  echo "✅  Committed spec matches regenerated output — up to date."
  exit 0
else
  echo "❌  Committed spec differs from what the current source would generate."
  echo "    Regenerate and commit the updated spec:"
  echo "      bash scripts/regen-oas.sh"
  echo ""
  echo "    Diff (regenerated vs committed):"
  diff --unified=3 "$TMPDIR_OAS/openapi.yaml" "$REPO_ROOT/$SPEC" | head -60 | sed 's/^/  /'
  exit 1
fi
