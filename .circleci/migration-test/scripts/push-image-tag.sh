#!/bin/bash
# ---------------------------------------------------------------------------
# Script: push-image-tag.sh
# Description: Updates the Docker image tag in the GitOps repository's values.yaml file
#              and pushes the change to trigger an ArgoCD sync.
#
# Usage: ./push-image-tag.sh <branch> <tag> <component> [repo_url] [repo_branch]
#
# Arguments:
#   1. branch:      The CI branch name (used for logging/logic, e.g., 'master')
#   2. tag:         The Docker image tag to deploy (e.g., '4.10.5')
#   3. component:   The component to update ('mapi', 'gateway', 'all')
#   4. repo_url:    (Optional) GitOps Repo URL. Defaults to MIGRATION_GITOPS_REPO env var or cloud-am.
#   5. repo_branch: (Optional) GitOps Repo Branch. Defaults to MIGRATION_GITOPS_BRANCH env var or test-env-branch.
# ---------------------------------------------------------------------------

set -euo pipefail

# Disable git pager to prevent hanging in non-interactive shells
export GIT_PAGER=cat
export PAGER=cat

BRANCH="${1:-}"
TAG="${2:-}"
COMPONENT="${3:-}"
CLOUD_AM_REPO_URL="${4:-git@github.com:gravitee-io/cloud-am.git}"
CLOUD_AM_BRANCH="${5:-test-env-branch}"

# --- Fallback to Environment Variables ---
# Allow pipeline parameters to override defaults if script args are missing
if [ -z "$CLOUD_AM_REPO_URL" ] && [ -n "${MIGRATION_GITOPS_REPO:-}" ]; then
    CLOUD_AM_REPO_URL="$MIGRATION_GITOPS_REPO"
fi
if [ -z "$CLOUD_AM_BRANCH" ] && [ -n "${MIGRATION_GITOPS_BRANCH:-}" ]; then
    CLOUD_AM_BRANCH="$MIGRATION_GITOPS_BRANCH"
fi

# --- Validation ---
if [[ -z "$BRANCH" || -z "$TAG" || -z "$COMPONENT" ]]; then
    echo "Error: Missing required arguments"
    echo "Usage: $0 <branch> <tag> <component> [repo_url] [repo_branch]"
    echo "Components: mapi, gateway, all"
    exit 1
fi

echo "GitOps Push Initiated:"
echo "  - Branch:       $BRANCH"
echo "  - Tag:          $TAG"
echo "  - Component:    $COMPONENT"
echo "  - Repo:         $CLOUD_AM_REPO_URL"
echo "  - TargetBranch: $CLOUD_AM_BRANCH"

# NOTE: For this POC/Implementation, we assume the values.yaml is located at a standard path.
# In a full multi-environment setup, this might be dynamic based on the branch.
VALUES_FILE_PATH="values.yaml" 

# --- Helpers ---

ensure_yq() {
    if command -v yq &> /dev/null; then return 0; fi
    echo "❌ Error: 'yq' is required but not installed."
    echo "Please install yq (v4+) to modify YAML files."
    exit 1
}

# --- Execution ---

TMP_DIR=$(mktemp -d -t cloud-am-gitops.XXXXXX)
trap 'rm -rf "$TMP_DIR"' EXIT

# 1. Setup SSH for Git
mkdir -p ~/.ssh
chmod 700 ~/.ssh
ssh-keyscan github.com >> ~/.ssh/known_hosts 2>/dev/null || true

# 2. Clone Repository
echo "Cloning repository..."
git clone --depth 1 -b "$CLOUD_AM_BRANCH" "$CLOUD_AM_REPO_URL" "$TMP_DIR" || { 
    echo "❌ Failed to clone repository. Check permissions and branch name."; 
    exit 1; 
}

cd "$TMP_DIR"

# 3. Configure Git Identity
git config user.name "gravitee-ci"
git config user.email "ci@gravitee.io"
git config push.default simple

if [[ ! -f "$VALUES_FILE_PATH" ]]; then
    echo "❌ Values file '$VALUES_FILE_PATH' not found in repository root."
    ls -R
    exit 1
fi

ensure_yq

# 4. Update YAML based on Component
echo "Updating values.yaml..."

case "$COMPONENT" in
    "mapi")
        echo " -> Updating Management API image tag to '$TAG'"
        yq eval ".management.image.tag = \"$TAG\"" -i "$VALUES_FILE_PATH"
        ;;
    "gateway")
        echo " -> Updating Gateway image tag to '$TAG'"
        yq eval ".gateway.image.tag = \"$TAG\"" -i "$VALUES_FILE_PATH"
        ;;
    "all")
        echo " -> Updating ALL components image tag to '$TAG'"
        yq eval ".management.image.tag = \"$TAG\"" -i "$VALUES_FILE_PATH"
        yq eval ".gateway.image.tag = \"$TAG\"" -i "$VALUES_FILE_PATH"
        ;;
    *)
        echo "❌ Error: Unknown component '$COMPONENT'"
        exit 1
        ;;
esac

# 5. Connect & Push
if git diff --quiet "$VALUES_FILE_PATH"; then
    echo "No changes detected. The tag is already set to '$TAG'."
    exit 0
fi

echo "Changes to be committed:"
git --no-pager diff "$VALUES_FILE_PATH"

git add "$VALUES_FILE_PATH"
git commit -m "chore(migration-test): update $COMPONENT to $TAG"

echo "Pushing changes to origin/$CLOUD_AM_BRANCH..."
git push origin "$CLOUD_AM_BRANCH"

echo "✅ GitOps push successful."
