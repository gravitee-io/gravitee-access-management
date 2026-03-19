#!/bin/bash
set -euo pipefail

# Disable git pager to prevent hanging in CI
export GIT_PAGER=cat
export PAGER=cat

# ---------------------------------------------------------------------------
# EE Plugin Schema Backwards-Compatibility Check
#
# Checks schema-form.json backwards compatibility for Enterprise (EE) plugins
# that are distributed as ZIPs and not tracked in this git repository.
#
# Usage:
#   bash ee-schema-compat-check.sh [--base <ref>] [--use-head <true|false>]
#
#   --base <ref>         Any git ref to use as the comparison baseline.
#                        When omitted, uses the same auto-detection logic as
#                        schema-compat-check.sh (merge-base with origin/master
#                        for PR branches; HEAD~1 for master/release branches).
#
#   --use-head <bool>    true  (default): read plugin versions and project version
#                               from HEAD (committed state). Used by CI.
#                        false: read from the working tree pom.xml instead.
#                               Useful locally when version changes are uncommitted.
#                               Note: new plugin ZIPs are always sourced from
#                               target/ regardless of this flag.
#
# Exits 0 if no breaking changes (or minor version bump detected).
# Exits 1 if any EE plugin has breaking changes.
#
# Prerequisites:
#   - A successful `mvn install` so new plugin ZIPs exist under
#     gravitee-am-gateway/.../target/distribution/plugins/ and
#     gravitee-am-management-api/.../target/distribution/plugins/
#   - Maven can resolve artifacts from the Gravitee Artifactory (i.e. you are
#     authenticated and settings.xml is configured)
# ---------------------------------------------------------------------------

REPO_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
SCRIPT="$REPO_ROOT/scripts/schema-compatibility/check-schema-compatibility.mjs"
TMPDIR_WORK="$(mktemp -d)"
cleanup() { rm -rf "$TMPDIR_WORK"; }
trap cleanup EXIT

# Distribution pom.xml files to scan for EE plugin coordinates.
# Both gateway and management-api distributions are checked; duplicates are
# deduplicated by artifactId.
DIST_POMS=(
  "$REPO_ROOT/gravitee-am-gateway/gravitee-am-gateway-standalone/gravitee-am-gateway-standalone-distribution/pom.xml"
  "$REPO_ROOT/gravitee-am-management-api/gravitee-am-management-api-standalone/gravitee-am-management-api-standalone-distribution/pom.xml"
)
# Both distribution target directories are searched for new ZIPs.
# A plugin may be built into gateway, management-api, or both.
PLUGINS_DIRS=(
  "$REPO_ROOT/gravitee-am-gateway/gravitee-am-gateway-standalone/gravitee-am-gateway-standalone-distribution/target/distribution/plugins"
  "$REPO_ROOT/gravitee-am-management-api/gravitee-am-management-api-standalone/gravitee-am-management-api-standalone-distribution/target/distribution/plugins"
)

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

echo "=== EE Plugin Schema Backwards-Compatibility Check ==="
if [[ "$USE_HEAD" == "false" ]]; then
  echo "(comparing against working tree)"
fi
echo ""

# ---------------------------------------------------------------------------
# Determine baseline commit
# ---------------------------------------------------------------------------

if [[ -n "$BASE_REF" ]]; then
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
# Detect minor version bump (same logic as schema-compat-check.sh)
# NOTE: parse_version_from_pom is intentionally duplicated from schema-compat-check.sh.
# Bash has no shared-library mechanism; if this logic changes, update both scripts.
# ---------------------------------------------------------------------------

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

if [[ "$USE_HEAD" == "true" ]]; then
  HEAD_POM_CONTENT="$(git show HEAD:pom.xml 2>/dev/null || true)"
else
  HEAD_POM_CONTENT="$(cat "$REPO_ROOT/pom.xml" 2>/dev/null || true)"
fi
BASE_POM_CONTENT="$(git show "${MERGE_BASE}:pom.xml" 2>/dev/null || true)"

read -r HEAD_MINOR HEAD_PATCH <<< "$(echo "$HEAD_POM_CONTENT" | parse_version_from_pom || true)"
read -r BASE_MINOR BASE_PATCH <<< "$(echo "$BASE_POM_CONTENT" | parse_version_from_pom || true)"

ALLOW_BREAKING=""
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
  fi
fi

# ---------------------------------------------------------------------------
# Discover EE plugin coordinates from distribution pom.xml files
#
# EE plugins are identified by groupId starting with "com.graviteesource".
# For each such dependency we extract:
#   groupId, artifactId, version property name (e.g. gravitee-am-factor-call.version)
#
# Output format: "groupId TAB artifactId TAB versionProp" — one entry per line.
# ---------------------------------------------------------------------------

discover_ee_plugins() {
  local pom="$1"
  # Match both <dependency> and <artifactItem> blocks — EE plugins appear in both
  # contexts within the distribution pom.xml (standard deps and maven-dependency-plugin
  # artifactItems). Both use the same groupId/artifactId/version child structure.
  awk '
    /<dependency>|<artifactItem>/  { in_dep=1; gid=""; aid=""; ver="" }
    /<\/dependency>|<\/artifactItem>/ {
      if (in_dep && gid ~ /^com\.graviteesource/ && ver ~ /^\$\{/) {
        prop = ver
        gsub(/^\$\{/, "", prop)
        gsub(/\}$/,   "", prop)
        print gid "\t" aid "\t" prop
      }
      in_dep = 0
    }
    in_dep && /<groupId>/    { gsub(/.*<groupId>/,    ""); gsub(/<\/groupId>.*/,    ""); gid = $0 }
    in_dep && /<artifactId>/ { gsub(/.*<artifactId>/, ""); gsub(/<\/artifactId>.*/, ""); aid = $0 }
    in_dep && /<version>/    { gsub(/.*<version>/,    ""); gsub(/<\/version>.*/,    ""); ver = $0 }
  ' "$pom"
}

# Resolve a version property value from pom.xml content passed on stdin.
# Property names can contain dots (e.g. gravitee-am-factor-call.version);
# escape them so grep and sed treat them as literal characters, not wildcards.
resolve_version_from_pom() {
  local prop="$1"
  local escaped="${prop//./\\.}"
  grep -m1 "<${escaped}>" | sed "s|.*<${escaped}>\(.*\)</${escaped}>.*|\1|"
}

# Collect unique EE plugin entries across all distribution poms (key: artifactId).
# Deduplication via awk so we stay compatible with bash 3.x (macOS default).
EE_ENTRIES=()
while IFS= read -r line; do
  [[ -n "$line" ]] && EE_ENTRIES+=("$line")
done < <(
  for dist_pom in "${DIST_POMS[@]}"; do
    [[ -f "$dist_pom" ]] && discover_ee_plugins "$dist_pom"
  done | awk -F'\t' '!seen[$2]++'
)

echo "Discovered ${#EE_ENTRIES[@]} EE plugin(s) across distribution pom.xml file(s)"
echo ""

# ---------------------------------------------------------------------------
# Determine which plugins changed version and need checking
# ---------------------------------------------------------------------------

TO_CHECK=()   # entries: "groupId TAB artifactId TAB oldVer TAB newVer"

for entry in "${EE_ENTRIES[@]}"; do
  IFS=$'\t' read -r groupId artifactId versionProp <<< "$entry"

  new_ver="$(echo "$HEAD_POM_CONTENT" | resolve_version_from_pom "$versionProp" || true)"
  old_ver="$(echo "$BASE_POM_CONTENT" | resolve_version_from_pom "$versionProp" || true)"

  if [[ -z "$new_ver" ]]; then
    echo "  ⚠️   $artifactId: could not resolve current version (\${$versionProp}) — skipping"
    continue
  fi
  if [[ -z "$old_ver" ]]; then
    echo "  ℹ️   $artifactId: new plugin (not present at baseline) — skipping"
    continue
  fi
  if [[ "$old_ver" == "$new_ver" ]]; then
    echo "  ➡️   $artifactId: unchanged at $new_ver — skipping"
    continue
  fi

  echo "  🔍  $artifactId: $old_ver → $new_ver"
  TO_CHECK+=("${groupId}	${artifactId}	${old_ver}	${new_ver}")
done

echo ""

if [[ ${#TO_CHECK[@]} -eq 0 ]]; then
  echo "✅  No EE plugin versions changed since baseline. Nothing to check."
  exit 0
fi

echo "Plugins with changed versions: ${#TO_CHECK[@]}"
echo ""

# ---------------------------------------------------------------------------
# Helper: extract schema-form.json from a ZIP
# Tries schemas/schema-form.json first, falls back to schema-form.json at root.
# Returns 0 on success, 1 if no schema found.
# ---------------------------------------------------------------------------

extract_schema_from_zip() {
  local zip="$1"
  local dest="$2"
  if unzip -p "$zip" 'schemas/schema-form.json' > "$dest" 2>/dev/null && [[ -s "$dest" ]]; then
    return 0
  fi
  if unzip -p "$zip" 'schema-form.json' > "$dest" 2>/dev/null && [[ -s "$dest" ]]; then
    return 0
  fi
  rm -f "$dest"
  return 1
}

# ---------------------------------------------------------------------------
# For each changed plugin: locate new ZIP, download old ZIP, compare schemas
# ---------------------------------------------------------------------------

OLD_ZIPS_DIR="$TMPDIR_WORK/old-zips"
OLD_SCHEMAS_DIR="$TMPDIR_WORK/old-schemas"
NEW_SCHEMAS_DIR="$TMPDIR_WORK/new-schemas"
mkdir -p "$OLD_ZIPS_DIR" "$OLD_SCHEMAS_DIR" "$NEW_SCHEMAS_DIR"

FAILURES=0
MISSING_ZIPS=0
DOWNLOAD_FAILURES=0

for entry in "${TO_CHECK[@]}"; do
  IFS=$'\t' read -r groupId artifactId old_ver new_ver <<< "$entry"

  echo "── Checking: $artifactId ($old_ver → $new_ver) ──"

  # --- New schema: search both distribution target directories for the built ZIP ---
  new_zip=""
  for plugins_dir in "${PLUGINS_DIRS[@]}"; do
    candidate="$plugins_dir/${artifactId}-${new_ver}.zip"
    if [[ -f "$candidate" ]]; then
      new_zip="$candidate"
      break
    fi
  done
  new_schema="$NEW_SCHEMAS_DIR/${artifactId}.json"

  if [[ -n "$new_zip" ]]; then
    if ! extract_schema_from_zip "$new_zip" "$new_schema"; then
      echo "  ℹ️   No schema-form.json in new ZIP — plugin has no UI schema, skipping"
      echo ""
      continue
    fi
  else
    echo "  ❌  New ZIP not found in any distribution target directory."
    echo "      Run 'mvn install -P full-bundle' first to build the distributions."
    echo ""
    MISSING_ZIPS=$((MISSING_ZIPS + 1))
    continue
  fi

  # --- Old schema: download via Maven dependency:copy ---
  old_zip="$OLD_ZIPS_DIR/${artifactId}-${old_ver}.zip"
  if ! mvn --batch-mode dependency:copy \
      -Dartifact="${groupId}:${artifactId}:${old_ver}:zip" \
      -DoutputDirectory="$OLD_ZIPS_DIR" \
      -q 2>/dev/null; then
    echo "  ❌  Could not download ${artifactId}:${old_ver} from Maven."
    echo "      Ensure Artifactory credentials are configured in settings.xml."
    echo ""
    DOWNLOAD_FAILURES=$((DOWNLOAD_FAILURES + 1))
    continue
  fi

  old_schema="$OLD_SCHEMAS_DIR/${artifactId}.json"
  if ! extract_schema_from_zip "$old_zip" "$old_schema"; then
    echo "  ℹ️   No schema-form.json in old ZIP (${old_ver}) — nothing to compare"
    echo ""
    continue
  fi

  # --- Run compatibility check ---
  # shellcheck disable=SC2086
  if ! node "$SCRIPT" \
      --old "$old_schema" \
      --new "$new_schema" \
      --plugin "$artifactId" \
      $ALLOW_BREAKING; then
    FAILURES=$((FAILURES + 1))
  fi

  echo ""
done

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------

echo "=== Summary ==="
if [[ $MISSING_ZIPS -gt 0 ]]; then
  echo "❌  $MISSING_ZIPS plugin ZIP(s) were not found. Run 'mvn install -P full-bundle' first."
  exit 1
elif [[ $DOWNLOAD_FAILURES -gt 0 ]]; then
  echo "❌  $DOWNLOAD_FAILURES old plugin ZIP(s) could not be downloaded from Maven."
  echo "    Check that Artifactory credentials are configured in settings.xml."
  exit 1
elif [[ $FAILURES -gt 0 ]]; then
  echo "❌  $FAILURES EE plugin(s) have breaking schema changes."
  echo "    To fix: ensure no required fields are added, no fields removed, no types changed."
  echo "    If this is intentional for a new minor version, bump the minor version (y in x.y.z) of pom.xml <version>."
  exit 1
else
  echo "✅  All EE plugin schema checks passed."
  exit 0
fi
