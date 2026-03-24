# Shared helpers for plugin schema compatibility check scripts.
# Do not execute directly; source from those scripts after set -euo pipefail.

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

# Parses --base and --use-head. Sets globals BASE_REF and USE_HEAD.
schema_compat_parse_args() {
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
}

# Uses global BASE_REF. Sets global MERGE_BASE.
schema_compat_resolve_merge_base() {
  if [[ -n "$BASE_REF" ]]; then
    # Explicit baseline supplied — resolve it to a commit SHA for clarity
    if ! MERGE_BASE="$(git rev-parse "$BASE_REF" 2>/dev/null)"; then
      echo "Error: --base '$BASE_REF' is not a valid git ref" >&2
      exit 2
    fi
    echo "Baseline: $BASE_REF ($MERGE_BASE)"
  else
    local current_branch
    current_branch="$(git rev-parse --abbrev-ref HEAD)"
    if [[ "$current_branch" == "master" || "$current_branch" =~ ^[0-9]+\.[0-9]+\.x$ ]]; then
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
}

# Reads globals HEAD_MINOR, HEAD_PATCH, BASE_MINOR. Sets global ALLOW_BREAKING.
schema_compat_evaluate_minor_bump_allow_breaking() {
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
    else
      echo "Version: minor=$HEAD_MINOR patch=$HEAD_PATCH (no bump)"
      echo ""
    fi
  else
    echo "Note: could not read version from pom.xml — skipping version-bump check."
    echo ""
  fi
}
