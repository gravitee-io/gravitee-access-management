#!/bin/bash
set -euo pipefail

# ---------------------------------------------------------------------------
# Automation OAS Lint
#
# Lints the committed docs/automation/openapi.yaml with Speakeasy for
# provider-generation readiness.
#
# Exits non-zero if Speakeasy reports an error.
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
SPEC="$REPO_ROOT/docs/automation/openapi.yaml"

# v1.793.0 broke `lint openapi` for unauthenticated runs:
# "generation access state is required". Keep pinned until upstream fixes it.
SPEAKEASY_CLI_VERSION="${SPEAKEASY_CLI_VERSION:-1.792.0}"

echo "=== Automation OpenAPI Spec Lint ==="
echo ""

if [[ ! -f "$SPEC" ]]; then
  echo "❌  Spec not found: $SPEC" >&2
  exit 1
fi

if ! command -v speakeasy &>/dev/null; then
  echo "Installing speakeasy CLI v$SPEAKEASY_CLI_VERSION..."
  curl -fsSL https://raw.githubusercontent.com/speakeasy-api/speakeasy/main/install.sh \
    | VERSION="$SPEAKEASY_CLI_VERSION" sh
fi
speakeasy lint openapi -s "$SPEC" --non-interactive
echo ""

echo "✅  Lint passed."
