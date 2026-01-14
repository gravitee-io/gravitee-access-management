#!/bin/bash
# Usage: push-image-tag.sh <branch> <tag> <component> [repo_url] [repo_branch]
# component: "mapi" | "gateway" | "all"

set -euo pipefail

# Disable git pager
export GIT_PAGER=cat
export PAGER=cat

BRANCH="${1:-}"
TAG="${2:-}"
COMPONENT="${3:-}"
CLOUD_AM_REPO_URL="${4:-git@github.com:gravitee-io/cloud-am.git}"
CLOUD_AM_BRANCH="${5:-test-env-branch}"

# Fallback to env vars if arguments not provided (Task 2.2 compatibility)
if [ -z "$CLOUD_AM_REPO_URL" ] && [ -n "${MIGRATION_GITOPS_REPO:-}" ]; then
    CLOUD_AM_REPO_URL="$MIGRATION_GITOPS_REPO"
fi
if [ -z "$CLOUD_AM_BRANCH" ] && [ -n "${MIGRATION_GITOPS_BRANCH:-}" ]; then
    CLOUD_AM_BRANCH="$MIGRATION_GITOPS_BRANCH"
fi

if [[ -z "$BRANCH" || -z "$TAG" || -z "$COMPONENT" ]]; then
    echo "Usage: $0 <branch> <tag> <component> [repo_url] [repo_branch]"
    echo "Components: mapi, gateway, all"
    exit 1
fi

echo "GitOps Push: Branch=$BRANCH, Tag=$TAG, Component=$COMPONENT, Repo=$CLOUD_AM_REPO_URL, TargetBranch=$CLOUD_AM_BRANCH"

# NOTE: For POC, we assume the values file is at the root or a specific location.
# Adapting logic from gitops-deploy.sh to handle component-specific updates.

# Functions
ensure_yq() {
    if command -v yq &> /dev/null; then return 0; fi
    # (Simplified yq install - assuming CI environment often has it, or use download logic from gitops-deploy.sh if needed)
    echo "❌ yq is required. Please install yq."
    exit 1
}

determine_values_file() {
    # For POC, assuming a fixed path for the test environment
    # In reality, this might depend on $BRANCH like in gitops-deploy.sh
    # But for migration test, we likely target a specific environment folder
    
    # Placeholder: assuming the structure is simply at root or 'migration-test' folder
    # Adjust this path based on actual cloud-am structure for the test environment
    echo "migration-test/values.yaml" 
}

# Values file path relative to repo root
VALUES_FILE_PATH="values.yaml" 

TMP_DIR=$(mktemp -d -t cloud-am-gitops.XXXXXX)
trap 'rm -rf "$TMP_DIR"' EXIT

# Setup SSH
mkdir -p ~/.ssh
chmod 700 ~/.ssh
ssh-keyscan github.com >> ~/.ssh/known_hosts 2>/dev/null || true

# Clone
echo "Cloning cloud-am repository..."
git clone --depth 1 -b "$CLOUD_AM_BRANCH" "$CLOUD_AM_REPO_URL" "$TMP_DIR" || { echo "❌ Failed to clone"; exit 1; }

cd "$TMP_DIR"

# Configure Git
git config user.name "gravitee-ci"
git config user.email "ci@gravitee.io"
git config push.default simple

if [[ ! -f "$VALUES_FILE_PATH" ]]; then
    echo "❌ Values file '$VALUES_FILE_PATH' not found in repo."
    # List files to help debugging
    ls -R
    exit 1
fi

ensure_yq

# Update logic based on component
# Assumptions on keys based on typical Helm values
case "$COMPONENT" in
    "mapi")
        echo "Updating Management API image tag to $TAG..."
        # Update MAPI tag
        yq eval ".management.image.tag = \"$TAG\"" -i "$VALUES_FILE_PATH"
        ;;
    "gateway")
        echo "Updating Gateway image tag to $TAG..."
        # Update Gateway tag
        yq eval ".gateway.image.tag = \"$TAG\"" -i "$VALUES_FILE_PATH"
        ;;
    "all")
        echo "Updating ALL components image tag to $TAG..."
        # Update both or global tag
        # Assuming there is a global tag or we update both
        yq eval ".management.image.tag = \"$TAG\"" -i "$VALUES_FILE_PATH"
        yq eval ".gateway.image.tag = \"$TAG\"" -i "$VALUES_FILE_PATH"
        # Also update global/shared tag if it exists (like .am.imageTag in gitops-deploy.sh)
        # yq eval ".am.imageTag = \"$TAG\"" -i "$VALUES_FILE_PATH"
        ;;
    *)
        echo "❌ Unknown component: $COMPONENT"
        exit 1
        ;;
esac

# Check changes
if git diff --quiet "$VALUES_FILE_PATH"; then
    echo "No changes detected."
    exit 0
fi

echo "Changes to be committed:"
git --no-pager diff "$VALUES_FILE_PATH"

git add "$VALUES_FILE_PATH"
git commit -m "chore(migration-test): update $COMPONENT to $TAG"

echo "Pushing changes..."
git push origin "$CLOUD_AM_BRANCH"

echo "✅ GitOps push successful."
