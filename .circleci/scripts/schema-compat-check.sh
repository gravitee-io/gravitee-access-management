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
# Exits 1 if any OSS (Open Source) plugin in this repository has breaking changes.
#
# Shared CLI, merge-base, and POM version helpers: _schema_compat_common.sh
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_schema_compat_common.sh
source "$SCRIPT_DIR/_schema_compat_common.sh"

REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
SCRIPT="$REPO_ROOT/scripts/schema-compatibility/check-schema-compatibility.mjs"
TMPDIR_SCHEMAS="$(mktemp -d)"
cleanup() { rm -rf "$TMPDIR_SCHEMAS"; }
trap cleanup EXIT

schema_compat_parse_args "$@"

echo "=== Schema Backwards-Compatibility Check ==="
if [[ "$USE_HEAD" == "false" ]]; then
  echo "(comparing against working tree)"
fi
echo ""

schema_compat_resolve_merge_base

echo ""

# ---------------------------------------------------------------------------
# Detect minor version bump (permits breaking changes)
# ---------------------------------------------------------------------------

if [[ "$USE_HEAD" == "true" ]]; then
  read -r HEAD_MINOR HEAD_PATCH <<< "$(extract_version HEAD)"
else
  read -r HEAD_MINOR HEAD_PATCH <<< "$(parse_version_from_pom < "$REPO_ROOT/pom.xml" 2>/dev/null || true)"
fi
read -r BASE_MINOR BASE_PATCH <<< "$(extract_version "$MERGE_BASE")"

schema_compat_evaluate_minor_bump_allow_breaking

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
