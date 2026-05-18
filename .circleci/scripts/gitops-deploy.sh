#!/bin/bash
set -euo pipefail

# Disable git pager to prevent hanging in CI
export GIT_PAGER=cat
export PAGER=cat

# Arguments: <branch> <tag> [repo_url] [repo_branch]
BRANCH="${1:-}"
TAG="${2:-}"
CLOUD_AM_REPO_URL="${3:-git@github.com:gravitee-io/cloud-am.git}"
CLOUD_AM_BRANCH="${4:-devs-preprod}"

# Functions
determine_target_dir() {
    if [[ "$BRANCH" == "master" ]]; then
        echo "master"
    elif [[ "$BRANCH" =~ ^[0-9] ]]; then
        # Version branches: convert dots to dashes (e.g., 4.7.x -> 4-7-x)
        echo "${BRANCH//./-}"
    else
        echo "❌ Error: Unsupported branch for deployment: $BRANCH" >&2
        echo "Supported branches: master, or version branches matching /^[0-9]/ (e.g., 4.7.x)" >&2
        exit 1
    fi
}

ensure_yq() {
    if command -v yq &> /dev/null; then
        return 0
    fi
    
    YQ_VERSION="v4.44.2"
    local OS
    OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
    case "$OS" in
      linux|darwin) ;;
      *) echo "❌ Unsupported OS: $OS"; return 1 ;;
    esac
    
    local RAW_ARCH
    RAW_ARCH=$(uname -m)
    local ARCH="amd64"
    if [[ "$RAW_ARCH" == "arm64" || "$RAW_ARCH" == "aarch64" ]]; then
        ARCH="arm64"
    fi
    
    local YQ_URL="https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/yq_${OS}_${ARCH}"
    echo "Downloading yq $YQ_VERSION for ${OS}/${ARCH}..."
    
    YQ_BIN="/tmp/yq"
    if command -v wget &>/dev/null; then
        wget -q --timeout=10 --tries=3 "$YQ_URL" -O "$YQ_BIN" || return 1
    elif command -v curl &>/dev/null; then
        curl -fsSL "$YQ_URL" -o "$YQ_BIN" || return 1
    else
        echo "❌ Neither wget nor curl found"
        return 1
    fi
    
    chmod +x "$YQ_BIN"
    export PATH="/tmp:$PATH"
    
    "$YQ_BIN" --version &> /dev/null || { echo "❌ yq binary is corrupted or incompatible"; return 1; }
}

update_yaml_content() {
    # Update imageTag value while preserving YAML anchor syntax (&imageTag) and comments
    # Anchor syntax allows single-point updates that all components reference via aliases (*imageTag)
    echo "Updating tag to: $TAG"
    if yq eval ".am.imageTag = \"${TAG}\"" -i "$VALUES_FILE"; then
        # Validate anchor is still present
        if ! grep -q "imageTag: &imageTag" "$VALUES_FILE"; then
            echo "❌ Error: Anchor &imageTag was lost during update."
            return 1
        fi
        return 0
    else
        echo "❌ Error: yq update failed"
        return 1
    fi
}

update_yaml_file() {
    ensure_yq || { echo "❌ yq is required for validation"; return 1; }
    
    # Validate YAML structure before modification
    if [[ "$(yq eval '(.am | type) != "!!map"' "$VALUES_FILE" 2>/dev/null)" == "true" ]]; then
        echo "❌ Error: .am key missing or is not a map in $VALUES_FILE"
        return 1
    fi
    
    update_yaml_content
}

git_push_with_timeout() {
    if command -v timeout &> /dev/null; then
        timeout 60 git push --set-upstream origin "HEAD:$CLOUD_AM_BRANCH" 2>&1
        return $?
    fi
    
    # Fallback timeout implementation for environments without timeout command
    local EXIT_CODE_FILE="$TMP_DIR/git_push_exit_code"
    (
        set +e
        git push --set-upstream origin "HEAD:$CLOUD_AM_BRANCH" 2>&1
        echo $? > "$EXIT_CODE_FILE"
    ) &
    local PUSH_PID=$!
    
    local WAIT_COUNT=0
    while kill -0 "$PUSH_PID" 2>/dev/null && [ "$WAIT_COUNT" -lt 60 ]; do
        sleep 1
        WAIT_COUNT=$((WAIT_COUNT + 1))
    done
    
    if kill -0 "$PUSH_PID" 2>/dev/null; then
        kill -9 "$PUSH_PID" 2>/dev/null || true
        return 124
    fi
    
    wait "$PUSH_PID"
    if [ -f "$EXIT_CODE_FILE" ]; then
        local CODE
        CODE=$(cat "$EXIT_CODE_FILE")
        rm -f "$EXIT_CODE_FILE"
        return "$CODE"
    fi
    return 1
}

# Main execution
if [[ -z "$BRANCH" || -z "$TAG" ]]; then
    echo "Usage: $0 <branch> <tag> [repo_url] [repo_branch]"
    exit 1
fi

TARGET_DIR=$(determine_target_dir)

TMP_DIR=$(mktemp -d -t cloud-am-gitops.XXXXXX)
trap 'rm -rf "$TMP_DIR"' EXIT
VALUES_FILE="$TARGET_DIR/values.yaml"
echo "GitOps Deployment: branch=$BRANCH, tag=$TAG, target=$TARGET_DIR, cloud-am-branch=$CLOUD_AM_BRANCH"

# Ensure SSH known_hosts is configured
mkdir -p ~/.ssh
chmod 700 ~/.ssh
ssh-keyscan github.com >> ~/.ssh/known_hosts 2>/dev/null || true

# Check remote branch existence
if command -v timeout &>/dev/null; then
    timeout 30 git ls-remote --exit-code --heads "$CLOUD_AM_REPO_URL" "$CLOUD_AM_BRANCH" >/dev/null 2>&1 || { echo "❌ Branch '$CLOUD_AM_BRANCH' not found or timeout"; exit 1; }
else
    git ls-remote --exit-code --heads "$CLOUD_AM_REPO_URL" "$CLOUD_AM_BRANCH" >/dev/null 2>&1 || { echo "❌ Branch '$CLOUD_AM_BRANCH' not found"; exit 1; }
fi

# Clone repository
echo "Cloning cloud-am repository (branch: $CLOUD_AM_BRANCH)..."
if command -v timeout &>/dev/null; then
    timeout 120 git clone --depth 1 -b "$CLOUD_AM_BRANCH" "$CLOUD_AM_REPO_URL" "$TMP_DIR" || { echo "❌ Failed to clone cloud-am repository"; exit 1; }
else
    git clone --depth 1 -b "$CLOUD_AM_BRANCH" "$CLOUD_AM_REPO_URL" "$TMP_DIR" || { echo "❌ Failed to clone cloud-am repository"; exit 1; }
fi

cd "$TMP_DIR"

# Configure git for this repository
git config user.name "gravitee-ci"
git config user.email "ci@gravitee.io"
git config push.default simple
export GIT_SSH_COMMAND="ssh -o LogLevel=ERROR -o ConnectTimeout=10"
export GIT_TERMINAL_PROMPT=0

# Validate paths
[[ -d "$TARGET_DIR" ]] || { echo "❌ Target directory $TARGET_DIR does not exist"; exit 1; }
[[ -f "$VALUES_FILE" ]] || { echo "❌ $VALUES_FILE not found"; exit 1; }

echo "Updating tags in $VALUES_FILE..."
update_yaml_file

git add "$VALUES_FILE"

if git diff --cached --quiet "$VALUES_FILE"; then
    echo "No changes detected (tags already set to $TAG)"
    exit 0
fi

echo "Changes to be committed:"
git --no-pager diff --cached "$VALUES_FILE"

git commit -m "chore($TARGET_DIR): deploy image ${TAG}"

echo "Pushing to cloud-am/$CLOUD_AM_BRANCH..."
if ! git_push_with_timeout; then
    EXIT_CODE=$?
    [[ $EXIT_CODE -eq 124 ]] && echo "❌ Git push timed out after 60 seconds" || echo "❌ Failed to push (exit code: $EXIT_CODE)"
    exit 1
fi

echo "✅ GitOps deployment complete: cloud-am/$CLOUD_AM_BRANCH/$TARGET_DIR/values.yaml"
