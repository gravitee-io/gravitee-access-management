#!/bin/bash
set -euo pipefail

# ---------------------------------------------------------------------------
# Regenerate docs/automation/openapi.yaml using build-time classpath scanning.
#
# Uses swagger-maven-plugin-jakarta (generate-oas Maven profile on the
# gravitee-am-management-api-automation-rest module) to scan the Automation API
# JAX-RS annotations without starting the management server. The
# AutomationApiDefinition ReaderListener is auto-discovered and filters the spec
# to automation-only paths, schemas, and tags, producing output identical to the
# runtime. The license header and info.version are patched post-generation.
#
# Usage:
#   bash scripts/regen-automation-oas.sh [options]
#
#   --output-dir <dir>   Directory to write openapi.yaml into.
#                        Default: docs/automation/ (overwrites the committed spec).
#
#   --also-make          Pass -am to Maven so upstream modules are built first.
#                        Use this if the module is not already compiled locally.
#
#   --maven-settings <file>
#                        Pass -s <file> to every Maven invocation (CI Artifactory).
#
#   --version-only       Skip generation: read project.version from Maven, strip
#                        a trailing -SNAPSHOT for public info.version, and patch
#                        only the info.version line in <output-dir>/openapi.yaml.
#                        Cannot be combined with --also-make.
#
# The script patches two things the full-regeneration path cannot leave to the plugin:
#
#   info.version  The plugin invokes AutomationApiDefinition.afterScan() which
#                 reads Version.RUNTIME_VERSION (gravitee-common version, not
#                 the project version). Patched from pom.xml post-generation.
#                 Trimmed trailing -SNAPSHOT for the committed spec line.
#
#   License header  Extracted from the committed docs/automation/openapi.yaml and
#                   prepended to the generated file. The committed header is
#                   the canonical rendered form; it is validated by the
#                   license-maven-plugin on every mvn validate, so it is
#                   always correct. This avoids duplicating the template text.
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
MODULE="gravitee-am-management-api/gravitee-am-management-api-automation/gravitee-am-management-api-automation-rest"
DEFAULT_OUTPUT_DIR="$REPO_ROOT/docs/automation"
COMMITTED_SPEC="$REPO_ROOT/docs/automation/openapi.yaml"

OUTPUT_DIR=""
ALSO_MAKE=""
MAVEN_SETTINGS_FILE=""
VERSION_ONLY=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir) OUTPUT_DIR="${2:?--output-dir requires a path}"; shift 2 ;;
    --also-make)  ALSO_MAKE="-am"; shift ;;
    --maven-settings) MAVEN_SETTINGS_FILE="${2:?--maven-settings requires a path}"; shift 2 ;;
    --version-only) VERSION_ONLY=true; shift ;;
    *) echo "Error: unknown argument: $1" >&2; exit 2 ;;
  esac
done

[[ -z "$OUTPUT_DIR" ]] && OUTPUT_DIR="$DEFAULT_OUTPUT_DIR"
OUTPUT_FILE="${OUTPUT_DIR}/openapi.yaml"

MAVEN_ARGS=(-B -ntp)
if [[ -n "$MAVEN_SETTINGS_FILE" ]]; then
  MAVEN_ARGS+=(-s "$MAVEN_SETTINGS_FILE")
fi

patch_info_version() {
  local target_file="$1"
  local oas_version="$2"
  local tmp
  tmp="$(mktemp)"
  awk -v ver="$oas_version" '
    /^info:/                         { in_info=1 }
    in_info && /^[^ ]/ && !/^info:/ { in_info=0 }
    in_info && /^  version: /        { print "  version: " ver; next }
                                     { print }
  ' "$target_file" > "$tmp"
  mv "$tmp" "$target_file"
}

resolve_project_version() {
  mvn "${MAVEN_ARGS[@]}" -pl "$MODULE" help:evaluate \
    -Dexpression=project.version -q -DforceStdout | tail -1
}

if [[ "$VERSION_ONLY" == true ]]; then
  echo "=== Patching Automation OpenAPI info.version only ==="
else
  echo "=== Regenerating Automation OpenAPI spec ==="
fi
echo "Output: $OUTPUT_FILE"
echo ""

# ---------------------------------------------------------------------------
# Step 1: Validate
# ---------------------------------------------------------------------------

if [[ -n "$MAVEN_SETTINGS_FILE" && ! -f "$MAVEN_SETTINGS_FILE" ]]; then
  echo "Error: maven settings file not found: ${MAVEN_SETTINGS_FILE}" >&2
  exit 1
fi

if [[ "$VERSION_ONLY" == true && -n "$ALSO_MAKE" ]]; then
  echo "Error: --version-only cannot be combined with --also-make" >&2
  exit 2
fi

if [[ "$VERSION_ONLY" == true && ! -f "$OUTPUT_FILE" ]]; then
  echo "Error: spec not found: ${OUTPUT_FILE}" >&2
  exit 1
fi

[[ "$VERSION_ONLY" == false ]] && mkdir -p "$OUTPUT_DIR"
cd "$REPO_ROOT"

# ---------------------------------------------------------------------------
# Step 2 [full regen]: Capture license header from the committed spec
# ---------------------------------------------------------------------------

if [[ "$VERSION_ONLY" == false ]]; then
  HEADER_LINE_COUNT=0
  if [[ -f "$COMMITTED_SPEC" ]]; then
    HEADER_LINE_COUNT=$(awk '/^openapi:/{print NR-1; found=1; exit} END {if (!found) print 0}' "$COMMITTED_SPEC")
    HEADER_CONTENT=$(head -n "$HEADER_LINE_COUNT" "$COMMITTED_SPEC")
  fi
fi

# ---------------------------------------------------------------------------
# Step 3: Extract project version
# ---------------------------------------------------------------------------

PROJECT_VERSION="$(resolve_project_version)"

if [[ -z "$PROJECT_VERSION" ]] || [[ "$PROJECT_VERSION" == *"[ERROR]"* ]]; then
  echo "Error: could not extract project.version from Maven (see Maven output)." >&2
  exit 1
fi

OAS_INFO_VERSION="${PROJECT_VERSION%-SNAPSHOT}"
echo "Project version: ${PROJECT_VERSION}"
echo "Public info.version: ${OAS_INFO_VERSION}"

# ---------------------------------------------------------------------------
# Step 4 [full regen]: Run the swagger-maven-plugin (generate-oas profile)
# ---------------------------------------------------------------------------

if [[ "$VERSION_ONLY" == false ]]; then
  echo "Running swagger-maven-plugin-jakarta..."
  mvn "${MAVEN_ARGS[@]}" \
      -pl "$MODULE" \
      process-classes \
      -Pgenerate-oas \
      -Doas.outputPath="$OUTPUT_DIR" \
      -DskipTests \
      $ALSO_MAKE

  if [[ ! -f "$OUTPUT_FILE" ]]; then
    echo "Error: expected ${OUTPUT_FILE} to be written by the plugin but it was not found." >&2
    exit 1
  fi
fi

# ---------------------------------------------------------------------------
# Step 5: Patch info.version
#
# Replaces the runtime-library version written by the plugin with the Maven
# project version, trimmed of a trailing -SNAPSHOT.
# ---------------------------------------------------------------------------

echo "Patching info.version → ${OAS_INFO_VERSION}"
patch_info_version "$OUTPUT_FILE" "$OAS_INFO_VERSION"

# ---------------------------------------------------------------------------
# Step 6 [full regen]: Prepend license header
# ---------------------------------------------------------------------------

if [[ "$VERSION_ONLY" == false ]]; then
  TMPFILE="$(mktemp)"
  {
    [[ "$HEADER_LINE_COUNT" -gt 0 ]] && printf '%s\n\n' "$HEADER_CONTENT"
    cat "$OUTPUT_FILE"
  } > "$TMPFILE"
  mv "$TMPFILE" "$OUTPUT_FILE"

  if [[ "$HEADER_LINE_COUNT" -eq 0 ]]; then
    echo "Warning: could not extract license header from committed spec — header not prepended." >&2
    echo "         Run 'mvn license:format -pl ${MODULE}' to add it." >&2
  fi
fi

# ---------------------------------------------------------------------------
# Step 7: Report
# ---------------------------------------------------------------------------

echo ""
echo "✅  ${OUTPUT_FILE} written."
echo ""

if [[ "$VERSION_ONLY" == false && "$OUTPUT_FILE" == "$COMMITTED_SPEC" ]]; then
  if git diff --quiet HEAD -- "$COMMITTED_SPEC" 2>/dev/null; then
    echo "No changes vs HEAD — spec was already up to date."
  else
    echo "Changes vs HEAD:"
    git --no-pager diff --stat HEAD -- "$COMMITTED_SPEC"
  fi
fi
