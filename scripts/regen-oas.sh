#!/bin/bash
set -euo pipefail

# ---------------------------------------------------------------------------
# Regenerate docs/mapi/openapi.yaml using build-time classpath scanning.
#
# Uses swagger-maven-plugin-jakarta (generate-oas Maven profile) to scan
# JAX-RS annotations without starting the management API server.
# The GraviteeApiDefinition ReaderListener is auto-discovered and invoked,
# producing sorted paths, tags, and security schemes identical to the runtime
# output. The license header and info.version are patched post-generation.
#
# Usage:
#   bash scripts/regen-oas.sh [--output-dir <dir>] [--also-make]
#
#   --output-dir <dir>   Directory to write openapi.yaml into.
#                        Default: docs/mapi/ (overwrites the committed spec).
#
#   --also-make          Pass -am to Maven so upstream modules are built first.
#                        Use this if the module is not already compiled locally.
#
# The script patches two things the plugin cannot produce correctly at build
# time:
#
#   info.version  The plugin invokes GraviteeApiDefinition.afterScan() which
#                 reads Version.RUNTIME_VERSION (gravitee-common version, not
#                 the project version). Patched from pom.xml post-generation.
#
#   License header  Extracted from the committed docs/mapi/openapi.yaml and
#                   prepended to the generated file. The committed header is
#                   the canonical rendered form; it is validated by the
#                   license-maven-plugin on every mvn validate, so it is
#                   always correct. This avoids duplicating the template text.
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
SPEC="$REPO_ROOT/docs/mapi/openapi.yaml"
MODULE="gravitee-am-management-api/gravitee-am-management-api-rest"

OUTPUT_DIR="$REPO_ROOT/docs/mapi"
ALSO_MAKE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir) OUTPUT_DIR="${2:?--output-dir requires a path}"; shift 2 ;;
    --also-make)  ALSO_MAKE="-am"; shift ;;
    *) echo "Error: unknown argument: $1" >&2; exit 2 ;;
  esac
done

mkdir -p "$OUTPUT_DIR"
OUTPUT_FILE="$OUTPUT_DIR/openapi.yaml"

echo "=== Regenerating OpenAPI spec ==="
echo "Output: $OUTPUT_FILE"
echo ""

cd "$REPO_ROOT"

# ---------------------------------------------------------------------------
# Step 1: Capture license header from the committed spec
# ---------------------------------------------------------------------------

COMMITTED_SPEC="$REPO_ROOT/docs/mapi/openapi.yaml"
HEADER_LINE_COUNT=0
if [[ -f "$COMMITTED_SPEC" ]]; then
  HEADER_LINE_COUNT=$(awk '/^openapi:/{print NR-1; exit}' "$COMMITTED_SPEC")
  # Capture the header lines into a variable so the source file can be safely overwritten
  HEADER_CONTENT=$(head -n "$HEADER_LINE_COUNT" "$COMMITTED_SPEC")
fi

# ---------------------------------------------------------------------------
# Step 2: Extract project version
# ---------------------------------------------------------------------------

PROJECT_VERSION=$(mvn -B -ntp -pl "$MODULE" help:evaluate \
  -Dexpression=project.version -q -DforceStdout 2>/dev/null | tail -1)

if [[ -z "$PROJECT_VERSION" ]]; then
  echo "Error: could not extract project version from pom.xml — Maven produced no output." >&2
  exit 1
fi

echo "Project version: ${PROJECT_VERSION}"

# ---------------------------------------------------------------------------
# Step 3: Run the swagger-maven-plugin (generate-oas profile)
# ---------------------------------------------------------------------------

echo "Running swagger-maven-plugin-jakarta..."
mvn -B -ntp \
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

# ---------------------------------------------------------------------------
# Step 4: Patch info.version
#
# The plugin invokes GraviteeApiDefinition.afterScan() which sets info.version
# from Version.RUNTIME_VERSION.MAJOR_VERSION (gravitee-common library version).
# Replace it with the actual project version extracted from pom.xml.
# ---------------------------------------------------------------------------

echo "Patching info.version → ${PROJECT_VERSION}"
TMPFILE="$(mktemp)"
sed "s/^  version: .*$/  version: ${PROJECT_VERSION}/" "$OUTPUT_FILE" > "$TMPFILE"

# ---------------------------------------------------------------------------
# Step 5: Prepend license header
# ---------------------------------------------------------------------------

{
  [[ "$HEADER_LINE_COUNT" -gt 0 ]] && printf '%s\n\n' "$HEADER_CONTENT"
  cat "$TMPFILE"
} > "$OUTPUT_FILE"
rm "$TMPFILE"

if [[ "$HEADER_LINE_COUNT" -eq 0 ]]; then
  echo "Warning: could not extract license header from committed spec — header not prepended." >&2
  echo "         Run 'mvn license:format -pl gravitee-am-management-api/gravitee-am-management-api-rest' to add it." >&2
fi

# ---------------------------------------------------------------------------
# Step 6: Report
# ---------------------------------------------------------------------------

echo ""
echo "✅  ${OUTPUT_FILE} written."
echo ""

if [[ "$OUTPUT_FILE" == "$SPEC" ]]; then
  if git diff --quiet HEAD -- "$SPEC" 2>/dev/null; then
    echo "No changes vs HEAD — spec was already up to date."
  else
    echo "Changes vs HEAD:"
    git --no-pager diff --stat HEAD -- "$SPEC"
  fi
fi
