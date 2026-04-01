#!/bin/bash
set -euo pipefail

# Disable git pager to prevent hanging in CI
export GIT_PAGER=cat
export PAGER=cat

# ---------------------------------------------------------------------------
# OAS Breaking-Change Check
#
# Compares docs/mapi/openapi.yaml at the merge-base against HEAD (or the
# working tree when --use-head false) using oasdiff. Exits 1 if breaking
# changes are detected, unless a version-boundary exemption applies.
#
# Usage:
#   bash oas-compat-check.sh [--base <ref>] [--use-head <true|false>]
#
#   --base <ref>         Any git ref to use as the comparison baseline.
#                        When omitted, uses auto-detection: merge-base with the tracking
#                        branch (@{u}), then GitHub API for PRs, with fallback to HEAD~1.
#
#   --use-head <bool>    true  (default): compare baseline → HEAD.
#                        false: compare baseline → working tree (staged + unstaged).
#                               Useful locally before committing.
#
# Exits 0 if: spec unchanged since merge-base, or no breaking changes, or the
#             minor version bumped / patch is zero (version-boundary exemption).
# Exits 1 if: breaking OAS changes detected on a non-exempt version boundary.
#
# Requires oasdiff on PATH. Install: https://github.com/tufin/oasdiff
#
# Shared CLI, merge-base, and POM version helpers: _compat_common.sh
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=_compat_common.sh
source "$SCRIPT_DIR/_compat_common.sh"

REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
SPEC="docs/mapi/openapi.yaml"

compat_parse_args "$@"

echo "=== OpenAPI Spec Backward-Compatibility Check ==="
if [[ "$USE_HEAD" == "false" ]]; then
  echo "(comparing against working tree)"
fi
echo ""

compat_resolve_merge_base

echo ""

# ---------------------------------------------------------------------------
# Version-boundary exemption
# ---------------------------------------------------------------------------

if [[ "$USE_HEAD" == "true" ]]; then
  read -r HEAD_MINOR HEAD_PATCH <<< "$(extract_version HEAD)"
else
  read -r HEAD_MINOR HEAD_PATCH <<< "$(parse_version_from_pom < "$REPO_ROOT/pom.xml" 2>/dev/null || true)"
fi
read -r BASE_MINOR BASE_PATCH <<< "$(extract_version "$MERGE_BASE")"

compat_evaluate_minor_bump_allow_breaking

# ---------------------------------------------------------------------------
# Check for spec changes
# ---------------------------------------------------------------------------

if [[ "$USE_HEAD" == "true" ]]; then
  GIT_DIFF_TARGET="HEAD"
else
  GIT_DIFF_TARGET=""
fi

if ! git diff --name-only "$MERGE_BASE" $GIT_DIFF_TARGET -- "$SPEC" 2>/dev/null | grep -q .; then
  echo "✅  Spec unchanged since merge-base. Nothing to check."
  exit 0
fi

# ---------------------------------------------------------------------------
# Run oasdiff
# ---------------------------------------------------------------------------

TMPDIR_OAS="$(mktemp -d)"
cleanup() { rm -rf "$TMPDIR_OAS"; }
trap cleanup EXIT

OLD_SPEC="$TMPDIR_OAS/old-openapi.yaml"

if ! git show "${MERGE_BASE}:${SPEC}" > "$OLD_SPEC" 2>/dev/null; then
  echo "ℹ️   Spec did not exist at merge-base — new file, skipping breaking-change check."
  exit 0
fi

NEW_SPEC="$REPO_ROOT/$SPEC"
if [[ "$USE_HEAD" == "false" && ! -f "$NEW_SPEC" ]]; then
  echo "ℹ️   Spec removed in working tree — no forward-compat check needed."
  exit 0
fi

if [[ -n "$ALLOW_BREAKING" ]]; then
  # Version-boundary exemption: print findings but do not fail.
  oasdiff breaking "$OLD_SPEC" "$NEW_SPEC" --fail-on ERR || true
  echo ""
  echo "✅  Breaking changes exempted (version boundary)."
  exit 0
fi

if ! oasdiff breaking "$OLD_SPEC" "$NEW_SPEC" --fail-on ERR; then
  echo ""
  echo "❌  Breaking OAS changes detected."
  echo "    To fix: keep backward compatibility or bump the minor version in pom.xml."
  exit 1
fi

echo ""
echo "✅  No breaking OAS changes."
exit 0
