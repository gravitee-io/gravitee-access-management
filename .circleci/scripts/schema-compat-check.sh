#!/bin/bash
set -euo pipefail

# Disable git pager to prevent hanging in CI
export GIT_PAGER=cat
export PAGER=cat

# ---------------------------------------------------------------------------
# Schema Backwards-Compatibility Check
#
# Compares every schema-form.json changed between a baseline ref and HEAD
# (or the working tree when --use-head=false).
#
# Usage:
#   bash schema-compat-check.sh [--base <ref>] [--use-head <true|false>]
#
#   --base <ref>         Any git ref to use as the comparison baseline.
#                        Examples:
#                          --base origin/master          (merge-base with a branch)
#                          --base 4.11.0                 (tag — changes since a release)
#                          --base abc1234                (specific commit SHA)
#                          --base HEAD~10                (relative to current HEAD)
#                        When omitted, the baseline is determined automatically:
#                          - master / x.y.x branches → HEAD~1
#                          - all other branches       → git merge-base HEAD origin/master
#
#   --use-head <bool>    true  (default): compare baseline → HEAD (committed changes only).
#                               Used by CI where all changes are committed.
#                        false: compare baseline → working tree (includes staged and
#                               unstaged changes). Useful locally before committing.
#
# Exits 0 if no breaking changes (or minor version bump detected).
# Exits 1 if any EE plugin has breaking changes.
# ---------------------------------------------------------------------------

REPO_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
SCRIPT="$REPO_ROOT/scripts/schema-compatibility/check-schema-compatibility.mjs"
TMPDIR_SCHEMAS="$(mktemp -d)"
cleanup() { rm -rf "$TMPDIR_SCHEMAS"; }
trap cleanup EXIT

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------

BASE_REF=""
USE_HEAD="true"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --base)
      BASE_REF="${2:-}"
      if [[ -z "$BASE_REF" ]]; then
        echo "Error: --base requires a git ref argument" >&2
        exit 2
      fi
      shift 2
      ;;
    --use-head)
      USE_HEAD="${2:-}"
      if [[ "$USE_HEAD" != "true" && "$USE_HEAD" != "false" ]]; then
        echo "Error: --use-head requires 'true' or 'false'" >&2
        exit 2
      fi
      shift 2
      ;;
    *)
      echo "Error: unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

echo "=== Schema Backwards-Compatibility Check ==="
if [[ "$USE_HEAD" == "false" ]]; then
  echo "(comparing against working tree)"
fi
echo ""

# ---------------------------------------------------------------------------
# Determine baseline commit
# ---------------------------------------------------------------------------

if [[ -n "$BASE_REF" ]]; then
  # Explicit baseline supplied — resolve it to a commit SHA for clarity
  if ! MERGE_BASE="$(git rev-parse "$BASE_REF" 2>/dev/null)"; then
    echo "Error: --base '$BASE_REF' is not a valid git ref" >&2
    exit 2
  fi
  echo "Baseline: $BASE_REF ($MERGE_BASE)"
else
  CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
  if [[ "$CURRENT_BRANCH" == "master" || "$CURRENT_BRANCH" =~ ^[0-9]+\.[0-9]+\.x$ ]]; then
    echo "Running on release/master branch — comparing HEAD vs HEAD~1"
    MERGE_BASE="HEAD~1"
  else
    # PR branch: use merge-base with origin/master
    if git fetch origin master --depth=50 2>/dev/null; then
      MERGE_BASE="$(git merge-base HEAD origin/master)"
    else
      echo "Warning: could not fetch origin/master; falling back to HEAD~1"
      MERGE_BASE="HEAD~1"
    fi
    echo "PR branch — merge-base: $MERGE_BASE"
  fi
fi

echo ""

# ---------------------------------------------------------------------------
# Detect minor version bump (permits breaking changes)
# ---------------------------------------------------------------------------

ALLOW_BREAKING=""

# Read the y and z components of the project version from a pom.xml on stdin.
# Prints "y z" (space-separated) on a single line.
# Skips the <parent> block to avoid matching the parent artifact's version.
# A single awk process avoids SIGPIPE caused by grep -m1 closing the pipe early.
parse_version_from_pom() {
  awk '
    /<parent>/   { skip=1 }
    /<\/parent>/ { skip=0; next }
    skip         { next }
    /<version>/  {
      gsub(/.*<version>/, "")
      gsub(/<\/version>.*/, "")
      n = split($0, a, ".")
      if (n >= 3) {
        split(a[3], p, "-")   # strip -SNAPSHOT suffix from patch
        print a[2], p[1]; exit
      }
    }
  '
}

extract_version() {
  local commit="$1"
  git show "${commit}:pom.xml" 2>/dev/null | parse_version_from_pom
}

if [[ "$USE_HEAD" == "true" ]]; then
  read -r HEAD_MINOR HEAD_PATCH <<< "$(extract_version HEAD)"
else
  read -r HEAD_MINOR HEAD_PATCH <<< "$(parse_version_from_pom < "$REPO_ROOT/pom.xml" 2>/dev/null || true)"
fi
read -r BASE_MINOR BASE_PATCH <<< "$(extract_version "$MERGE_BASE")"

if [[ -n "$HEAD_MINOR" && -n "$BASE_MINOR" ]]; then
  if [[ "$HEAD_MINOR" != "$BASE_MINOR" ]]; then
    echo "⚠️   Minor version bump detected: $BASE_MINOR → $HEAD_MINOR"
    echo "    Breaking schema changes will be reported but will NOT fail the build."
    echo ""
    ALLOW_BREAKING="--allow-breaking"
  elif [[ "$HEAD_PATCH" == "0" ]]; then
    echo "⚠️   Minor version $HEAD_MINOR patch is 0 — no release of this minor version exists yet."
    echo "    Breaking schema changes will be reported but will NOT fail the build."
    echo ""
    ALLOW_BREAKING="--allow-breaking"
  else
    echo "Version: minor=$HEAD_MINOR patch=$HEAD_PATCH (no bump)"
    echo ""
  fi
else
  echo "Note: could not read version from pom.xml — skipping version-bump check."
  echo ""
fi

# ---------------------------------------------------------------------------
# Find changed schema-form.json files
# ---------------------------------------------------------------------------

CHANGED_SCHEMAS=()
if [[ "$USE_HEAD" == "true" ]]; then
  # Committed changes only: baseline → HEAD
  GIT_DIFF_TARGET="HEAD"
else
  # Include staged and unstaged working tree changes
  GIT_DIFF_TARGET=""
fi
while IFS= read -r line; do
  [[ -n "$line" ]] && CHANGED_SCHEMAS+=("$line")
done < <(git diff --name-only "$MERGE_BASE" $GIT_DIFF_TARGET -- '**/schema-form.json' 2>/dev/null || true)

if [[ ${#CHANGED_SCHEMAS[@]} -eq 0 ]]; then
  echo "✅  No schema-form.json files changed in this diff. Nothing to check."
  exit 0
fi

echo "Changed schema files (${#CHANGED_SCHEMAS[@]}):"
for f in "${CHANGED_SCHEMAS[@]}"; do
  echo "  $f"
done
echo ""

# ---------------------------------------------------------------------------
# Check each changed schema
# ---------------------------------------------------------------------------

FAILURES=0

for SCHEMA_PATH in "${CHANGED_SCHEMAS[@]}"; do
  PLUGIN_NAME="$(echo "$SCHEMA_PATH" | awk -F/ '{
    for (i=1; i<=NF; i++) {
      if ($(i+1) == "src") { print $i; exit }
    }
  }')"

  echo "── Checking: $SCHEMA_PATH ($PLUGIN_NAME) ──"

  # Extract old schema at merge-base
  OLD_SCHEMA="$TMPDIR_SCHEMAS/${PLUGIN_NAME}-old.json"
  if ! git show "${MERGE_BASE}:${SCHEMA_PATH}" > "$OLD_SCHEMA" 2>/dev/null; then
    echo "  ℹ️   File did not exist at merge-base — new plugin, skipping."
    echo ""
    continue
  fi

  # New schema is in the working tree (already checked out)
  NEW_SCHEMA="$REPO_ROOT/$SCHEMA_PATH"
  if [[ ! -f "$NEW_SCHEMA" ]]; then
    echo "  ℹ️   File removed in this diff — no forward-compat check needed."
    echo ""
    continue
  fi

  # Run the diff script
  # shellcheck disable=SC2086
  if ! node "$SCRIPT" \
      --old "$OLD_SCHEMA" \
      --new "$NEW_SCHEMA" \
      --plugin "$PLUGIN_NAME" \
      $ALLOW_BREAKING; then
    FAILURES=$((FAILURES + 1))
  fi

  echo ""
done

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

echo "=== Summary ==="
if [[ $FAILURES -gt 0 ]]; then
  echo "❌  $FAILURES plugin(s) have breaking schema changes."
  echo "    To fix: ensure no required fields are added, no fields removed, no types changed."
  echo "    If this is intentional for a new minor version, bump the minor version (y in x.y.z) of pom.xml <version>."
  exit 1
else
  echo "✅  All schema checks passed."
  exit 0
fi
