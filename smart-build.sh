#!/bin/bash
#
# Copyright (C) 2015 The Gravitee team (http://gravitee.io)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


# Smart incremental build script for Gravitee Access Management
# Detects changes and rebuilds only affected modules

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
GATEWAY_PLUGINS="gravitee-am-gateway/gravitee-am-gateway-standalone/gravitee-am-gateway-standalone-distribution/src/main/resources/plugins/"
MANAGEMENT_API_PLUGINS="gravitee-am-management-api/gravitee-am-management-api-standalone/gravitee-am-management-api-standalone-distribution/src/main/resources/plugins/"

# Detect if mvnd (Maven Daemon) is available, fallback to mvn
if command -v mvnd &> /dev/null; then
    MVN_CMD="mvnd"
else
    MVN_CMD="mvn"
fi

# Get version
GIO_AM_VERSION=$(cat .working/.version 2>/dev/null || ${MVN_CMD} org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version 2>/dev/null | grep '^[0-9]\+\.[0-9]\+\.[0-9]\+.*')

echo -e "${BLUE}=== Gravitee AM Smart Build ===${NC}"
echo -e "Version: ${GREEN}${GIO_AM_VERSION}${NC}"
echo -e "Maven: ${GREEN}${MVN_CMD}${NC}\n"

# Function to detect changed modules
detect_changed_modules() {
    echo -e "${YELLOW}Detecting changed files...${NC}"

    # Get list of changed files (unstaged + staged + untracked)
    CHANGED_FILES=$(git diff --name-only HEAD 2>/dev/null; git diff --name-only --cached 2>/dev/null; git ls-files --others --exclude-standard 2>/dev/null)

    if [ -z "$CHANGED_FILES" ]; then
        echo -e "${GREEN}No changes detected${NC}"
        return 1
    fi

    echo -e "${BLUE}Changed files:${NC}"
    echo "$CHANGED_FILES" | head -20
    if [ $(echo "$CHANGED_FILES" | wc -l) -gt 20 ]; then
        echo "... and $(( $(echo "$CHANGED_FILES" | wc -l) - 20 )) more"
    fi
    echo ""

    # Extract unique module paths, excluding UI module
    MODULES=$(echo "$CHANGED_FILES" | grep -E '^gravitee-am-' | grep -v '^gravitee-am-ui' | cut -d'/' -f1 | sort -u)

    if [ -z "$MODULES" ]; then
        echo -e "${YELLOW}No changes in gravitee backend modules (UI changes ignored)${NC}"
        return 1
    fi

    echo -e "${GREEN}Changed modules (excluding UI):${NC}"
    echo "$MODULES"
    echo ""

    return 0
}

# Function to determine which plugins need update
determine_plugins_to_update() {
    local modules="$1"

    GATEWAY_NEEDS_UPDATE=false
    MANAGEMENT_NEEDS_UPDATE=false
    PLUGINS_TO_COPY=()

    while IFS= read -r module; do
        case "$module" in
            gravitee-am-repository*)
                PLUGINS_TO_COPY+=("repository")
                GATEWAY_NEEDS_UPDATE=true
                MANAGEMENT_NEEDS_UPDATE=true
                ;;
            gravitee-am-reporter*)
                PLUGINS_TO_COPY+=("reporter")
                GATEWAY_NEEDS_UPDATE=true
                MANAGEMENT_NEEDS_UPDATE=true
                ;;
            gravitee-am-gateway*)
                GATEWAY_NEEDS_UPDATE=true
                ;;
            gravitee-am-management-api*)
                MANAGEMENT_NEEDS_UPDATE=true
                ;;
            gravitee-am-identityprovider*|gravitee-am-certificate*|gravitee-am-extensiongrant*|gravitee-am-factor*|gravitee-am-resource*|gravitee-am-botdetection*|gravitee-am-deviceidentifier*|gravitee-am-password-dictionary*|gravitee-am-authdevice-notifier*)
                PLUGINS_TO_COPY+=("$(echo $module | cut -d'-' -f3-)")
                GATEWAY_NEEDS_UPDATE=true
                MANAGEMENT_NEEDS_UPDATE=true
                ;;
            gravitee-am-common|gravitee-am-model|gravitee-am-service|gravitee-am-policy|gravitee-am-jwt|gravitee-am-plugins-handlers)
                # Core modules - need to rebuild both gateway and management
                GATEWAY_NEEDS_UPDATE=true
                MANAGEMENT_NEEDS_UPDATE=true
                ;;
        esac
    done <<< "$modules"
}

# Function to build specific modules
build_modules() {
    local modules="$1"
    local skip_tests="${2:-false}"

    # Convert modules to comma-separated list
    local module_list=$(echo "$modules" | tr '\n' ',' | sed 's/,$//')

    echo -e "${YELLOW}Building modules: ${module_list}${NC}"

    # Note: We don't need to exclude gravitee-am-ui here because we already filtered it
    # out during change detection, and -pl only builds specified modules anyway
    if [ "$skip_tests" = "true" ]; then
        ${MVN_CMD} clean install -pl "$module_list" -am -DskipTests
    else
        ${MVN_CMD} clean install -pl "$module_list" -am
    fi
}

# Function to copy plugins
copy_plugins() {
    echo -e "${YELLOW}Copying plugins...${NC}"

    mkdir -p "$GATEWAY_PLUGINS"
    mkdir -p "$MANAGEMENT_API_PLUGINS"

    if [ "$GATEWAY_NEEDS_UPDATE" = true ]; then
        echo -e "${BLUE}Updating Gateway plugins...${NC}"

        # Copy common plugins
        rm -rf "${GATEWAY_PLUGINS}/.work"
        rm -f "${GATEWAY_PLUGINS}"/*.zip
        cp -f gravitee-am-gateway/gravitee-am-gateway-standalone/gravitee-am-gateway-standalone-distribution/target/distribution/plugins/*.zip "$GATEWAY_PLUGINS"

        # Copy specific repository plugins
        if [[ " ${PLUGINS_TO_COPY[@]} " =~ " repository " ]]; then
            echo -e "  ${GREEN}✓${NC} Copying repository plugin"
            cp -f gravitee-am-repository/gravitee-am-repository-mongodb/target/gravitee-am-repository-mongodb-${GIO_AM_VERSION}.zip "$GATEWAY_PLUGINS" 2>/dev/null || true
        fi

        # Copy reporter plugins
        if [[ " ${PLUGINS_TO_COPY[@]} " =~ " reporter " ]]; then
            echo -e "  ${GREEN}✓${NC} Copying reporter plugin"
            cp -f gravitee-am-reporter/gravitee-am-reporter-mongodb/target/gravitee-am-reporter-mongodb-${GIO_AM_VERSION}.zip "$GATEWAY_PLUGINS" 2>/dev/null || true
        fi

        echo -e "${GREEN}Gateway plugins updated${NC}"
    fi

    if [ "$MANAGEMENT_NEEDS_UPDATE" = true ]; then
        echo -e "${BLUE}Updating Management API plugins...${NC}"

        # Copy common plugins
        rm -rf "${MANAGEMENT_API_PLUGINS}/.work"
        rm -f "${MANAGEMENT_API_PLUGINS}"/*.zip
        cp -f gravitee-am-management-api/gravitee-am-management-api-standalone/gravitee-am-management-api-standalone-distribution/target/distribution/plugins/*.zip "$MANAGEMENT_API_PLUGINS"

        # Copy specific repository plugins
        if [[ " ${PLUGINS_TO_COPY[@]} " =~ " repository " ]]; then
            echo -e "  ${GREEN}✓${NC} Copying repository plugin"
            cp -f gravitee-am-repository/gravitee-am-repository-mongodb/target/gravitee-am-repository-mongodb-${GIO_AM_VERSION}.zip "$MANAGEMENT_API_PLUGINS" 2>/dev/null || true
        fi

        # Copy reporter plugins
        if [[ " ${PLUGINS_TO_COPY[@]} " =~ " reporter " ]]; then
            echo -e "  ${GREEN}✓${NC} Copying reporter plugin"
            cp -f gravitee-am-reporter/gravitee-am-reporter-mongodb/target/gravitee-am-reporter-mongodb-${GIO_AM_VERSION}.zip "$MANAGEMENT_API_PLUGINS" 2>/dev/null || true
        fi

        echo -e "${GREEN}Management API plugins updated${NC}"
    fi
}

# Parse command line arguments
SKIP_TESTS=false
FORCE_REBUILD=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-tests|-s)
            SKIP_TESTS=true
            shift
            ;;
        --force|-f)
            FORCE_REBUILD=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -s, --skip-tests    Skip running tests during build"
            echo "  -f, --force         Force rebuild all modules"
            echo "  -h, --help          Show this help message"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

# Main execution
if [ "$FORCE_REBUILD" = true ]; then
    echo -e "${YELLOW}Force rebuild mode - building all modules (excluding UI)${NC}"
    make version
    make install OPTIONS="-DskipTests"
    make plugins

    echo -e "\n${GREEN}=== Build complete ===${NC}"
    echo -e "${BLUE}Note: UI module excluded - use 'cd gravitee-am-ui && yarn serve' for UI development${NC}"
    exit 0
fi

# Detect changes
if detect_changed_modules; then
    determine_plugins_to_update "$MODULES"

    echo -e "${BLUE}Build plan:${NC}"
    echo -e "  Gateway needs update: $([ "$GATEWAY_NEEDS_UPDATE" = true ] && echo "${GREEN}YES${NC}" || echo "${YELLOW}NO${NC}")"
    echo -e "  Management needs update: $([ "$MANAGEMENT_NEEDS_UPDATE" = true ] && echo "${GREEN}YES${NC}" || echo "${YELLOW}NO${NC}")"
    if [ ${#PLUGINS_TO_COPY[@]} -gt 0 ]; then
        echo -e "  Plugins to copy: ${GREEN}${PLUGINS_TO_COPY[*]}${NC}"
    fi
    echo ""

    # Build changed modules
    build_modules "$MODULES" "$SKIP_TESTS"

    # Copy plugins
    copy_plugins

    echo -e "\n${GREEN}=== Smart build complete ===${NC}"
else
    echo -e "${YELLOW}No backend changes to build${NC}"
    echo -e "${BLUE}Tip: UI changes are handled by Angular hot-reload (yarn serve)${NC}"
    exit 0
fi