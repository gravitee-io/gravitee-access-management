#!/bin/bash
set -euo pipefail

# ---------------------------------------------------------------------------
# Regenerate the TypeScript Management API SDK (gravitee-am-test/api/management)
# from an OpenAPI spec, by default the committed docs/mapi/openapi.yaml.
#
# Regenerate the spec first when the Management API changed:
#
#   bash scripts/regen-oas.sh && bash scripts/regen-sdk.sh
#
# Usage:
#   bash scripts/regen-sdk.sh [options]
#
#   --spec <file|url>    OpenAPI spec to generate from.
#                        Default: docs/mapi/openapi.yaml.
#                        A URL that does not end in .json/.yaml/.yml is taken as
#                        the base URL of a running Management API, and
#                        /openapi.json is appended to it.
#
# Requires Java and the gravitee-am-test npm dependencies (npm ci).
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
TEST_DIR="$REPO_ROOT/gravitee-am-test"
SDK_DIR="$TEST_DIR/api/management"
TEMPLATES_DIR="$TEST_DIR/scripts/templates"
OPENAPI_GENERATOR_CLI="@openapitools/openapi-generator-cli@2.23.1"

SPEC="$REPO_ROOT/docs/mapi/openapi.yaml"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --spec) SPEC="${2:?--spec requires a file or URL}"; shift 2 ;;
    *) echo "Error: unknown argument: $1" >&2; exit 2 ;;
  esac
done

case "$SPEC" in
  http://*.json|https://*.json|http://*.yaml|https://*.yaml|http://*.yml|https://*.yml) ;;
  http://*|https://*) SPEC="${SPEC%/}/openapi.json" ;;
  *)
    # INIT_CWD is where `npm run` was invoked from.
    [[ "$SPEC" = /* ]] || SPEC="${INIT_CWD:-$PWD}/$SPEC"
    if [[ ! -f "$SPEC" ]]; then
      echo "Error: spec not found: $SPEC" >&2
      exit 1
    fi
    ;;
esac

echo "=== Regenerating Management API SDK ==="
echo "Spec:   $SPEC"
echo "Output: $SDK_DIR"
echo ""

# The generator version (openapitools.json) and Prettier resolve from here.
cd "$TEST_DIR"

GEN_DIR="$(mktemp -d)"
trap 'rm -rf "$GEN_DIR"' EXIT

# Without it, the generator writes a default ignore file and lists it in FILES.
cp "$SDK_DIR/.openapi-generator-ignore" "$GEN_DIR/"

# ---------------------------------------------------------------------------
# Step 1: Generate into a temporary directory
# ---------------------------------------------------------------------------

npx "$OPENAPI_GENERATOR_CLI" generate \
  -t "$TEMPLATES_DIR" \
  -i "$SPEC" \
  -g typescript-fetch \
  -o "$GEN_DIR" \
  -puseSingleRequestParameter=true \
  -pmodelPropertyNaming=original \
  -psortModelPropertiesByRequiredFlag=false \
  -psortParamsByRequiredFlag=false \
  -plegacyDiscriminatorBehavior=false \
  --import-mappings=DateTime=Date \
  --type-mappings=DateTime=Date,object=any \
  --reserved-words-mappings=configuration=configuration \
  --skip-validate-spec

if ! ls "$GEN_DIR"/apis/*.ts >/dev/null 2>&1; then
  echo "Error: OpenAPI generation produced no files (see the generator output above)." >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Step 2: Post-process
# ---------------------------------------------------------------------------

find "$GEN_DIR" -name "*.ts" -exec sed -i.bak "/* The version of the OpenAPI document/d" {} \;

# The backend serializes ProtectedResourceFeature types in lowercase ('mcp_tool')
# while the discriminator mapping uses uppercase ('MCP_TOOL'): accept both.
FEATURE_MODEL="$GEN_DIR/models/ProtectedResourceFeature.ts"
if [[ -f "$FEATURE_MODEL" ]] && ! grep -q "json\['type'\] === 'MCP_TOOL' || json\['type'\] === 'mcp_tool'" "$FEATURE_MODEL"; then
  sed -i.bak \
    -e "s/json\['type'\] === 'MCP_TOOL'/json['type'] === 'MCP_TOOL' || json['type'] === 'mcp_tool'/g" \
    -e "s/case 'MCP_TOOL':/case 'MCP_TOOL':\\n      case 'mcp_tool':/g" \
    "$FEATURE_MODEL"
fi

find "$GEN_DIR" -name "*.bak" -exec rm -f {} \;
rm -f "$GEN_DIR/index.ts" "$GEN_DIR/apis/index.ts"

# ---------------------------------------------------------------------------
# Step 3: Replace the SDK and format it
# ---------------------------------------------------------------------------

rm -rf "$SDK_DIR/apis" "$SDK_DIR/models" "$SDK_DIR/runtime.ts" "$SDK_DIR/.openapi-generator"
cp -R "$GEN_DIR/apis" "$GEN_DIR/models" "$GEN_DIR/runtime.ts" "$GEN_DIR/.openapi-generator" "$SDK_DIR/"

npx prettier --loglevel warn --write "$SDK_DIR/**/*.ts"

# ---------------------------------------------------------------------------
# Step 4: Report
# ---------------------------------------------------------------------------

echo ""
echo "✅  SDK written to ${SDK_DIR}."
echo ""

if [[ -z "$(git -C "$REPO_ROOT" status --porcelain -- "$SDK_DIR")" ]]; then
  echo "No changes vs HEAD — SDK was already up to date."
else
  echo "Changes vs HEAD:"
  git -C "$REPO_ROOT" status --short -- "$SDK_DIR"
fi
