# Smart Build - Incremental Build System

## Overview

The Smart Build system automatically detects which modules have changed and only rebuilds those modules plus their dependencies. This dramatically reduces build time during development by focusing only on what actually changed.

## Problem Solved

Previously, you had to:
```bash
mvn clean install -DskipTests  # Rebuilds ALL 20+ modules (5-10 minutes)
make plugins                    # Copy all plugins
# restart services manually
```

This rebuilds **everything**, even if you only changed one module (like `gravitee-am-repository`).

## Solution

The `smart-build.sh` script intelligently rebuilds only what changed:
1. **Detects changes** using Git (unstaged, staged, and untracked files)
2. **Identifies affected modules** - filters out UI changes automatically
3. **Builds only changed modules** using Maven's `-pl` (project list) with `-am` (also make dependencies)
4. **Copies only necessary plugins** to the distribution folders
5. **Optionally restarts only affected services** (Gateway, Management API, or both)
6. **Supports Maven Daemon (mvnd)** for even faster builds (2-10x speedup)

## Usage

### Quick Start

After making changes to your code:

```bash
# Only rebuild changed modules (skip tests)
make smart-build

# Only rebuild changed modules with tests
make smart-build-test

# Force rebuild ALL modules (like the old way)
make smart-build-force
```

**Key Point**: All commands only rebuild changed modules (except `smart-build-force`)

### Direct Script Usage

You can also call the script directly for more control:

```bash
# Basic usage (skip tests)
./smart-build.sh --skip-tests

# With tests
./smart-build.sh

# Force rebuild all
./smart-build.sh --force --skip-tests

# Help
./smart-build.sh --help
```

## How It Works

### Change Detection

The script uses Git to detect changes:
- Unstaged changes (`git diff HEAD`)
- Staged changes (`git diff --cached`)
- Untracked files (`git ls-files --others`)

### Module Mapping

The script knows which modules affect which services:

| Module Pattern | Affects Gateway | Affects Management | Notes |
|----------------|----------------|-------------------|-------|
| `gravitee-am-repository-*` | ✓ | ✓ | Rebuilds repository plugins |
| `gravitee-am-reporter-*` | ✓ | ✓ | Rebuilds reporter plugins |
| `gravitee-am-gateway*` | ✓ | | Gateway-only changes |
| `gravitee-am-management-api*` | | ✓ | Management-only changes |
| `gravitee-am-common` | ✓ | ✓ | Core dependency |
| `gravitee-am-service` | ✓ | ✓ | Core dependency |
| `gravitee-am-identityprovider-*` | ✓ | ✓ | Plugin changes |
| `gravitee-am-factor-*` | ✓ | ✓ | Plugin changes |

### Build Optimization

Maven's reactor build with:
- `-pl <modules>`: Build only specified modules
- `-am`: Also build required dependencies
- `-DskipTests`: Skip tests (optional)

## Examples

### Scenario 1: Repository Change

```bash
# You modify: gravitee-am-repository/gravitee-am-repository-mongodb/src/.../MongoRepository.java

./smart-build.sh --skip-tests

# Output:
# === Gravitee AM Smart Build ===
# Version: 4.10.0-SNAPSHOT
#
# Detecting changed files...
# Changed files:
# gravitee-am-repository/gravitee-am-repository-mongodb/src/main/java/.../MongoRepository.java
#
# Changed modules:
# gravitee-am-repository
#
# Build plan:
#   Gateway needs update: YES
#   Management needs update: YES
#   Plugins to copy: repository
#
# Building modules: gravitee-am-repository
# [Maven output...]
#
# Copying plugins...
# Updating Gateway plugins...
#   ✓ Copying repository plugin
# Gateway plugins updated
# Updating Management API plugins...
#   ✓ Copying repository plugin
# Management API plugins updated
#
#
# === Smart build complete ===
```

**Time saved**: ~90% (builds only 1 module instead of 20+)

### Scenario 2: Gateway-Only Change

```bash
# You modify: gravitee-am-gateway/src/.../SomeGatewayClass.java

./smart-build.sh --skip-tests

# Build plan:
#   Gateway needs update: YES
#   Management needs update: NO
#
```

**Time saved**: Builds 1 module only Gateway

### Scenario 3: No Changes

```bash
./smart-build.sh

# Output:
# === Gravitee AM Smart Build ===
# Version: 4.10.0-SNAPSHOT
#
# Detecting changed files...
# No changes detected
```

## Command Reference

### Makefile Targets

```bash
make smart-build              # Only rebuild changed modules (skip tests)
make smart-build-test         # Only rebuild changed modules (with tests)
make smart-build-force        # Force rebuild ALL modules (not just changed ones)
```

### Script Options

```bash
./smart-build.sh [OPTIONS]

Options:
  -s, --skip-tests    Skip running tests during buildafter build
  -f, --force         Force rebuild all modules
  -h, --help          Show help message
```

## Comparison: Old vs New Workflow

### Old Workflow (Full Rebuild)

```bash
# Make a small change to repository module
vim gravitee-am-repository/.../MongoRepository.java

# Rebuild EVERYTHING (all 20+ modules)
mvn clean install -DskipTests                    # ~5-10 minutes
make plugins                                      # ~30 seconds

# Total: ~6-11 minutes
```

### New Workflow (Smart Incremental Build)

```bash
# Make the same change
vim gravitee-am-repository/.../MongoRepository.java

# Only rebuild the changed module
make smart-build                          # ~1-2 minutes

# Total: ~1-2 minutes
```

**Time saved per iteration**: ~5-9 minutes (80-90% reduction)

### With Maven Daemon (mvnd)

```bash
# Install once
brew install mvnd

# Same command, even faster!
make smart-build                          # ~30 seconds - 1 minute

# Total: ~30 seconds - 1 minute
```

**Time saved with mvnd**: ~6-10 minutes (90-95% reduction)

## Tips

1. **Commit frequently**: The script detects changes using Git, so commit stable code regularly
2. **Use `--skip-tests` during rapid iteration**: Run full tests before committing
3. **Run `make smart-build-force` occasionally**: To ensure everything is synchronized
4. **Check `make help`**: See all available targets

## Troubleshooting

### "No changes detected" but I changed files

- Make sure files are in a `gravitee-am-*` module
- Check if files are git-ignored
- Try `git status` to see if Git tracks your changes

### Build fails with "Cannot find module"

- Run `make smart-build-force` to do a full rebuild
- Make sure you have run `make install` at least once

### Plugins not updating

- Check if the build actually completed successfully
- Verify Maven built the plugin JAR in `target/`
- Try `make plugins` to force a full plugin copy

## Future Enhancements

Potential improvements:
- Watch mode: automatically rebuild on file changes
- Parallel module builds
- Build cache using timestamps
- Integration with hot-reload for faster development
- Support for building specific modules by name

## Contributing

To extend the module mapping in `smart-build.sh`, edit the `determine_plugins_to_update()` function:

```bash
case "$module" in
    gravitee-am-your-new-module*)
        PLUGINS_TO_COPY+=("your-plugin")
        GATEWAY_NEEDS_UPDATE=true
        MANAGEMENT_NEEDS_UPDATE=true
        ;;
esac
```