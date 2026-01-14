# Migration Test Implementation Plan - POC

> **Document Navigation**:
> - 📋 **[High-Level Plan](./CI_CD_DATABASE_VERSION_TESTING_RESEARCH.md)** - Architecture, workflow design, and key decisions
> - 🔧 **[This Document](./MIGRATION_TEST_IMPLEMENTATION_PLAN.md)** - Detailed task breakdown, dependencies, and implementation steps
> - 🧪 **[Test Scenarios](./MIGRATION_TEST_SCENARIOS.md)** - Test scenarios, data seeding strategies, and success criteria
> - ☁️ **[cloud-am Setup Plan](./CLOUD_AM_ENVIRONMENT_SETUP_PLAN.md)** - Prerequisite work in cloud-am repository

## Overview

This document breaks down the POC implementation into discrete, parallelizable tasks with clear dependencies. Tasks are organized to enable two developers to work in parallel after initial dependencies are completed.

**See [CI/CD Database Version Testing Plan](./CI_CD_DATABASE_VERSION_TESTING_RESEARCH.md) for high-level architecture and design decisions.**

## Review Feedback & Updates

### Critical Blocker Identified

**Issue**: Jest test configuration (`gravitee-am-test/api/config/ci.setup.js`) hardcodes localhost URLs, overwriting environment variables. This would cause tests to fail when connecting to ArgoCD-deployed environment.

**Solution**: Added **Task 1.4: Jest Config Refactoring** to create dedicated migration setup files that respect environment variables.

**Status**: ✅ Addressed in Phase 1 (Task 1.4)

### Review Validation

- ✅ **Division of Labor**: Verified optimal - Phase 2 tasks can be parallelized
- ✅ **CircleCI Workflow Integration**: API trigger approach confirmed correct
- ✅ **Parameter Validation**: Added explicit validation requirements in Task 2.2

### Quick Reference to High-Level Plan

- **Workflow Steps**: See [Workflow Steps (POC)](./CI_CD_DATABASE_VERSION_TESTING_RESEARCH.md#workflow-steps-poc---using-current-branch)
- **ArgoCD Deployment**: See [ArgoCD GitOps Deployment](./CI_CD_DATABASE_VERSION_TESTING_RESEARCH.md#4-argocd-gitops-deployment)
- **MongoDB Cleanup**: See [Environment Setup](./CI_CD_DATABASE_VERSION_TESTING_RESEARCH.md#environment-setup)
- **POC Scope**: See [POC Scope](./CI_CD_DATABASE_VERSION_TESTING_RESEARCH.md#poc-scope-what-were-building)

## Task Dependencies Overview

```
Phase 0 (Prerequisite - cloud-am Repository):
  ⚠️ MUST COMPLETE FIRST
  - cloud-am environment setup (see CLOUD_AM_ENVIRONMENT_SETUP_PLAN.md)
  - ArgoCD ApplicationSet configuration
  - MongoDB deployment
  - values.yaml structure

Phase 1 (Foundation - Sequential):
  Task 1.1: MongoDB Cleanup Script
  Task 1.2: ArgoCD Sync Status Script
  Task 1.3: GitOps Deployment Script (MAPI/GW)
  Task 1.4: Jest Config Refactoring ✅ **COMPLETED**

Phase 2 (Core Components - Can Parallelize):
  Task 2.1: CircleCI Workflow Structure
  Task 2.2: zx Script for Workflow Trigger
  
Phase 3 (Integration - Sequential):
  Task 3.1: End-to-End Integration
  Task 3.2: Documentation
```

⚠️ **Note**: Phase 0 (cloud-am setup) must be completed before starting Phase 1.

## Detailed Task Breakdown

### Phase 1: Foundation Scripts (Sequential - Must Complete First)

These tasks provide the building blocks needed by the workflow. Complete in order.

---

#### Task 1.1: MongoDB Cleanup Script ⚠️ **CRITICAL DEPENDENCY**

**Priority**: HIGH (Blocks all other tasks)

**Description**: Create script to clean MongoDB database at job start.

**Location**: `.circleci/migration-test/scripts/clean-mongodb.sh`

**Requirements**:
- Connect to MongoDB instance
- Drop collections or entire database
- Handle connection errors gracefully
- Support MongoDB connection string from environment variables
- Idempotent (safe to run multiple times)

**Implementation Details**:
```bash
#!/bin/bash
# Usage: clean-mongodb.sh <mongodb-connection-string>
# Example: clean-mongodb.sh "mongodb://user:pass@host:27017/gravitee_am"

# Connect and drop database or collections
# Handle errors gracefully
# Log cleanup actions
```

**Acceptance Criteria**:
- ✅ Script connects to MongoDB successfully
- ✅ Database/collections are dropped
- ✅ Script handles connection failures gracefully
- ✅ Idempotent (can run multiple times safely)
- ✅ Logs cleanup actions

**Estimated Time**: 2-3 hours

**Dependencies**: None (can start immediately)

**Blocks**: Task 2.1 (CircleCI Workflow)

---

#### Task 1.2: ArgoCD Sync Status Script ⚠️ **CRITICAL DEPENDENCY**

**Priority**: HIGH (Blocks workflow execution)

**Description**: Create script to poll ArgoCD sync status until application is "Synced" and "Healthy".

**Location**: `.circleci/migration-test/scripts/wait-argocd-sync.sh`

**Requirements**:
- Poll ArgoCD API or CLI for application status
- Check both `sync.status == "Synced"` AND `health.status == "Healthy"`
- Poll at intervals (e.g., every 10-30 seconds)
- Handle ArgoCD API authentication
- Provide clear status output
- Exit with error if sync fails (manual monitoring for now)

**Implementation Details**:
```bash
#!/bin/bash
# Usage: wait-argocd-sync.sh <app-name> [argocd-server-url] [max-wait-minutes]
# Example: wait-argocd-sync.sh "am-test-env" "https://argocd.example.com" 10

# Options:
# 1. Use ArgoCD CLI: argocd app get <app-name> and parse output
# 2. Use ArgoCD REST API: GET /api/v1/applications/<app-name>

# Poll until:
# - status.sync.status == "Synced"
# - status.health.status == "Healthy"
```

**Acceptance Criteria**:
- ✅ Script polls ArgoCD successfully
- ✅ Detects "Synced" and "Healthy" states
- ✅ Provides clear status output
- ✅ Handles authentication (API token or CLI login)
- ✅ Exits with appropriate codes

**Estimated Time**: 3-4 hours

**Dependencies**: None (can start immediately)

**Blocks**: Task 2.1 (CircleCI Workflow)

---

#### Task 1.3: GitOps Deployment Script (MAPI/GW) ⚠️ **CRITICAL DEPENDENCY**

**Priority**: HIGH (Blocks workflow execution)

**Description**: Create script to push image tags to cloud-am repository (similar to `gitops-deploy.sh` but supports MAPI and Gateway separately).

**Location**: `.circleci/migration-test/scripts/push-image-tag.sh`

**Requirements**:
- Clone cloud-am repository
- Update `values.yaml` with target imageTag
- Support updating MAPI imageTag only, Gateway imageTag only, or both
- Commit and push changes
- Reuse logic from existing `gitops-deploy.sh` where possible
- Handle SSH authentication

**Implementation Details**:
```bash
#!/bin/bash
# Usage: push-image-tag.sh <branch> <tag> <component> [repo_url] [repo_branch]
# Example: push-image-tag.sh "master" "4.10.5" "mapi" 
# Example: push-image-tag.sh "master" "latest" "gateway"
# Example: push-image-tag.sh "master" "4.10.5" "all"  # both MAPI and GW
# 
# component: "mapi" | "gateway" | "all"

# Similar to gitops-deploy.sh:
# 1. Clone cloud-am repo
# 2. Update values.yaml with yq
# 3. Commit and push
```

**Acceptance Criteria**:
- ✅ Script updates MAPI imageTag correctly
- ✅ Script updates Gateway imageTag correctly
- ✅ Script updates both when component="all"
- ✅ Preserves YAML structure and anchors
- ✅ Handles SSH authentication
- ✅ Commits and pushes successfully

**Estimated Time**: 4-5 hours (can reuse gitops-deploy.sh logic)

**Dependencies**: None (can start immediately, but can reference gitops-deploy.sh)

**Blocks**: Task 2.1 (CircleCI Workflow)

---

#### Task 1.4: Jest Config Refactoring for Remote Targeting ✅ **COMPLETED**

**Priority**: HIGH (Blocks test execution)

**Description**: Refactor Jest test configuration to support remote environment URLs instead of hardcoded localhost.

**Problem**: 
- `gravitee-am-test/api/config/ci.setup.js` hardcodes `localhost` URLs
- Tests will fail when trying to connect to ArgoCD-deployed environment
- Current code: `process.env.AM_MANAGEMENT_URL = 'http://localhost:8093';` (overwrites env vars)

**Location**: `gravitee-am-test/api/config/`

**Requirements**:
- **Option A (Recommended for POC)**: Create dedicated migration config files
  - Create `migration.setup.js` - Migration-specific setup that respects environment variables
  - Create `migration.config.js` - Jest config for migration tests
  - Avoid modifying existing `ci.setup.js` to prevent destabilizing current CI
- **Option B (Alternative)**: Refactor `ci.setup.js` to use OR logic
  - Change to: `process.env.AM_MANAGEMENT_URL = process.env.AM_MANAGEMENT_URL || 'http://localhost:8093';`
  - Risk: May affect existing CI tests

**Implementation Details (Option A - Recommended)**:

**File**: `gravitee-am-test/api/config/migration.setup.js`
```javascript
// Migration test setup - respects environment variables
// Falls back to localhost only if env vars not set

process.env.AM_MANAGEMENT_URL = process.env.AM_MANAGEMENT_URL || 'http://localhost:8093';
process.env.AM_MANAGEMENT_ENDPOINT = process.env.AM_MANAGEMENT_ENDPOINT || 
  (process.env.AM_MANAGEMENT_URL + '/management');
process.env.AM_GATEWAY_URL = process.env.AM_GATEWAY_URL || 'http://localhost:8092';
process.env.AM_INTERNAL_GATEWAY_URL = process.env.AM_INTERNAL_GATEWAY_URL || 'http://gateway:8092';
// ... other env vars with fallbacks
```

**File**: `gravitee-am-test/api/config/migration.config.js` (if needed)
```javascript
// Jest config for migration tests
// Uses migration.setup.js instead of ci.setup.js
```

**Usage in CircleCI Workflow**:
- Set environment variables with ArgoCD-deployed URLs
- Use migration.setup.js instead of ci.setup.js
- Example: `AM_MANAGEMENT_URL=https://mapi-test.example.com yarn test --config migration.config.js`

**Acceptance Criteria**:
- ✅ Migration setup file created (migration.setup.js)
- ✅ Environment variables are respected (not overwritten)
- ✅ Falls back to localhost if env vars not set (for local development)
- ✅ Existing CI tests unaffected (ci.setup.js unchanged)
- ✅ Tests can connect to remote ArgoCD-deployed environment
- ✅ Documentation on how to use migration config

**Estimated Time**: 2-3 hours

**Dependencies**: None (can start immediately)

**Blocks**: Task 2.1 (CircleCI Workflow - needs this to run tests)

**Review Notes**: 
- This is a critical blocker identified in code review
- Without this, tests will fail when trying to connect to ArgoCD environment
- Option A (dedicated files) is safer for POC and doesn't risk existing CI

---

### Phase 2: Core Components (Can Parallelize After Phase 1)

These tasks can be worked on in parallel once Phase 1 is complete.

---

#### Task 2.1: CircleCI Workflow Structure 🔄 **CAN PARALLELIZE WITH 2.2**

**Priority**: HIGH

**Description**: Create CircleCI workflow with modular structure for migration testing.

**Location**: `.circleci/migration-test/`

**Requirements**:
- Create modular directory structure:
  ```
  .circleci/migration-test/
  ├── jobs/
  │   └── migration-test-job.yml
  ├── commands/
  │   ├── clean-environment.yml
  │   ├── deploy-version.yml
  │   ├── wait-argocd-sync.yml
  │   └── run-tests.yml
  └── scripts/
      ├── clean-mongodb.sh (from Task 1.1)
      ├── wait-argocd-sync.sh (from Task 1.2)
      └── push-image-tag.sh (from Task 1.3)
  ```
- Define pipeline parameters (from_tag, to_tag, db_type)
- Create reusable commands for each workflow step
- Create main job that orchestrates the workflow
- Add workflow to `.circleci/workflows.yml`

**Workflow Steps to Implement**:
1. Checkout codebase
2. Clean environment (use command)
3. Clean MongoDB (use script from Task 1.1)
4. Deploy version N (use command + script from Task 1.3)
5. Wait for ArgoCD sync (use script from Task 1.2)
6. Run Jest tests (use command)
7. Upgrade MAPI (use command + script from Task 1.3)
8. Wait for ArgoCD sync
9. Run Jest tests
10. Upgrade Gateways (use command + script from Task 1.3)
11. Wait for ArgoCD sync
12. Run Jest tests
13. Downgrade (use command + script from Task 1.3)
14. Wait for ArgoCD sync
15. Run Jest tests (complete full suite)
16. Store artifacts

**Acceptance Criteria**:
- ✅ Modular structure created
- ✅ Reusable commands defined
- ✅ Main job orchestrates all steps
- ✅ Workflow added to workflows.yml
- ✅ Pipeline parameters defined
- ✅ Artifacts stored correctly

**Estimated Time**: 6-8 hours

**Dependencies**: 
- ✅ Task 1.1 (MongoDB cleanup script)
- ✅ Task 1.2 (ArgoCD sync script)
- ✅ Task 1.3 (GitOps deployment script)
- ✅ Task 1.4 (Jest config refactoring) - **CRITICAL**: Tests won't work without this

**Can Work In Parallel With**: Task 2.2 (after dependencies met)

---

#### Task 2.2: zx Script for Workflow Trigger 🔄 **CAN PARALLELIZE WITH 2.1**

**Priority**: HIGH

**Description**: Create zx script to trigger CircleCI workflow via API.

**Location**: `scripts/migration-test.mjs`

**Requirements**:
- Accept command-line arguments: `--from-tag`, `--to-tag`, `--db-type`
- Trigger CircleCI workflow via API with pipeline parameters
- Handle CircleCI API authentication
- Return workflow URL for monitoring
- Provide clear error messages
- Similar pattern to existing release scripts
- **⚠️ CRITICAL**: Ensure parameters match exactly what CircleCI workflow expects

**Implementation Details**:
```javascript
// Usage: yarn run migration-test -- --from-tag 4.10.5 --to-tag latest --db-type mongodb

// Steps:
// 1. Parse command-line arguments
// 2. Validate parameters (non-empty, valid format)
// 3. Authenticate with CircleCI API
// 4. Trigger pipeline with parameters (must match workflow parameter names exactly)
// 5. Return workflow URL
```

**Parameter Validation**:
- Validate `from-tag` is non-empty and valid format
- Validate `to-tag` is non-empty and valid format
- Validate `db-type` (default to "mongodb" if not provided)
- Ensure parameter names match CircleCI workflow parameters exactly:
  - `from_tag` (not `from-tag`)
  - `to_tag` (not `to-tag`)
  - `db_type` (not `db-type`)

**Acceptance Criteria**:
- ✅ Script accepts required parameters
- ✅ Validates parameters (non-empty, valid format)
- ✅ Parameter names match CircleCI workflow exactly
- ✅ Authenticates with CircleCI API successfully
- ✅ Triggers workflow with correct parameters
- ✅ Returns workflow URL
- ✅ Handles errors gracefully

**Estimated Time**: 3-4 hours

**Dependencies**: None (can start immediately, but needs workflow from Task 2.1 to test)

**Can Work In Parallel With**: Task 2.1 (independent implementation)

---

### Phase 3: Integration & Documentation (Sequential)

These tasks require all previous tasks to be complete.

---

#### Task 3.1: End-to-End Integration Testing

**Priority**: MEDIUM

**Description**: Test complete workflow end-to-end and fix integration issues.

**Requirements**:
- Test workflow trigger via zx script
- Test each workflow step in sequence
- Verify MongoDB cleanup works
- Verify ArgoCD sync detection works
- Verify GitOps deployment works
- Verify Jest tests execute correctly
- Fix any integration issues
- Test with real versions (e.g., 4.10.5 → latest)

**Acceptance Criteria**:
- ✅ Workflow can be triggered successfully
- ✅ All steps execute in correct order
- ✅ MongoDB cleanup works
- ✅ ArgoCD sync detection works
- ✅ GitOps deployment works
- ✅ Jest tests execute against deployed versions
- ✅ Artifacts are stored correctly
- ✅ Complete workflow runs successfully

**Estimated Time**: 4-6 hours (depends on issues found)

**Dependencies**: 
- ✅ Task 2.1 (CircleCI Workflow)
- ✅ Task 2.2 (zx Script)

**Blocks**: Task 3.2 (Documentation)

---

#### Task 3.2: Documentation & Cleanup

**Priority**: LOW

**Description**: Update documentation and clean up code.

**Requirements**:
- Update main implementation plan document with completion status
- Add usage instructions for zx script
- Document workflow parameters
- Add troubleshooting guide
- Code cleanup and review
- Add comments to scripts

**Acceptance Criteria**:
- ✅ Documentation updated
- ✅ Usage instructions clear
- ✅ Code reviewed and cleaned
- ✅ Comments added where needed

**Estimated Time**: 2-3 hours

**Dependencies**: 
- ✅ Task 3.1 (End-to-End Integration)

---

## Parallel Work Suggestions

### After Phase 1 Complete (Two Developers)

**Developer A**:
- Task 2.1: CircleCI Workflow Structure
  - Can work independently
  - Uses scripts from Phase 1
  - Creates workflow definition

**Developer B**:
- Task 2.2: zx Script for Workflow Trigger
  - Can work independently
  - No dependencies on Task 2.1 for implementation
  - Can test integration once Task 2.1 is ready

### Coordination Points

1. **After Phase 1**: Both developers review completed scripts together
2. **During Phase 2**: Regular sync to ensure compatibility
3. **Before Phase 3**: Both developers review their components before integration

---

## Task Summary Table

| Task | Priority | Est. Time | Dependencies | Blocks | Can Parallelize? |
|------|----------|-----------|--------------|--------|------------------|
| 1.1 MongoDB Cleanup | HIGH | 2-3h | None | 2.1 | No |
| 1.2 ArgoCD Sync | HIGH | 3-4h | None | 2.1 | No |
| 1.3 GitOps Deploy | HIGH | 4-5h | None | 2.1 | No |
| 1.4 Jest Config | HIGH | 2-3h | None | 2.1 | Yes (with 1.1-1.3) |
| 2.1 CircleCI Workflow | HIGH | 6-8h | 1.1, 1.2, 1.3, 1.4 | 3.1 | Yes (with 2.2) |
| 2.2 zx Script | HIGH | 3-4h | None | 3.1 | Yes (with 2.1) |
| 3.1 Integration | MEDIUM | 4-6h | 2.1, 2.2 | 3.2 | No |
| 3.2 Documentation | LOW | 2-3h | 3.1 | None | No |

**Total Estimated Time**: 26-36 hours

---

## Implementation Order Recommendation

### Week 0: Prerequisites (cloud-am Repository)
**⚠️ MUST COMPLETE FIRST**: See [cloud-am Environment Setup Plan](./CLOUD_AM_ENVIRONMENT_SETUP_PLAN.md)
- Complete all 5 tasks in cloud-am repository
- Verify end-to-end deployment works
- Hand off environment details to AM repository team

### Week 1: Foundation (Sequential + Parallel)
**Day 1-2**: Complete Phase 1 tasks
- **Developer A**: Tasks 1.1, 1.2, and 1.4 (can work on 1.4 in parallel with 1.1/1.2)
- **Developer B**: Task 1.3
- **Note**: Task 1.4 (Jest config) can be done in parallel with other Phase 1 tasks
- Review together before moving to Phase 2

### Week 1-2: Core Components (Parallel)
**Day 3-5**: Complete Phase 2 tasks in parallel
- Developer A: Task 2.1 (CircleCI Workflow)
- Developer B: Task 2.2 (zx Script)
- Daily sync to ensure compatibility

### Week 2: Integration (Sequential)
**Day 6-7**: Complete Phase 3
- Developer A & B: Task 3.1 (Integration testing together)
- Developer A or B: Task 3.2 (Documentation)

---

## Prerequisites

⚠️ **CRITICAL**: The following prerequisites must be completed **before** starting Phase 1 tasks.

### Prerequisite 0: cloud-am Repository Setup (MUST COMPLETE FIRST)

**See**: [cloud-am Environment Setup Plan](./CLOUD_AM_ENVIRONMENT_SETUP_PLAN.md)

**Required**:
- ✅ Test environment branch created in cloud-am repository
- ✅ ArgoCD ApplicationSet configured for test environment
- ✅ MongoDB instance deployed and accessible
- ✅ values.yaml structure supports separate MAPI/Gateway image tags
- ✅ End-to-end deployment tested and working

**Estimated Time**: 10-15 hours (work in cloud-am repository)

**Blocks**: All Phase 1 tasks

### Prerequisites for AM Repository Work

1. **Access Requirements**:
   - ✅ CircleCI API token
   - ✅ ArgoCD API access or CLI configured
   - ✅ SSH access to cloud-am repository
   - ✅ MongoDB connection details for test environment (from cloud-am setup)

2. **Environment Setup** (from cloud-am setup):
   - ✅ Test environment with ArgoCD application configured
   - ✅ cloud-am repository with test environment branch
   - ✅ MongoDB instance accessible from CircleCI
   - ✅ ArgoCD application names (MAPI and Gateway)
   - ✅ ArgoCD server URL

3. **Knowledge Requirements**:
   - ✅ Understanding of CircleCI workflows
   - ✅ Understanding of ArgoCD sync status
   - ✅ Understanding of GitOps deployment pattern (gitops-deploy.sh)
   - ✅ Basic bash scripting
   - ✅ zx script knowledge (or JavaScript)

---

## Risk Mitigation

### Potential Issues

1. **Jest Config Hardcoded URLs** ⚠️ **CRITICAL - IDENTIFIED IN REVIEW**
   - **Issue**: `ci.setup.js` hardcodes localhost, overwrites environment variables
   - **Impact**: Tests will fail when connecting to ArgoCD-deployed environment
   - **Mitigation**: Task 1.4 - Create dedicated migration.setup.js (Option A recommended)
   - **Status**: Addressed in Task 1.4

2. **ArgoCD API Access**: May need to use CLI instead of API
   - **Mitigation**: Implement both options, use CLI as fallback

3. **MongoDB Connection**: Connection string format may vary
   - **Mitigation**: Support multiple connection string formats

4. **GitOps Push Conflicts**: Multiple pushes may conflict
   - **Mitigation**: Add retry logic, check for existing changes

5. **CircleCI Workflow Filtering**: Setup config may filter out new workflow
   - **Mitigation**: Using CircleCI API trigger bypasses standard filtering

---

## Success Criteria

The POC is complete when:

- ✅ All Phase 1 scripts work independently
- ✅ CircleCI workflow can be triggered via zx script
- ✅ Workflow executes all steps successfully
- ✅ Jest tests run against deployed versions
- ✅ Complete upgrade/downgrade cycle works
- ✅ Artifacts are stored correctly
- ✅ Documentation is complete

---

## Next Steps

1. **Review this plan** with team
2. **Assign tasks** to developers
3. **Set up access** (CircleCI API, ArgoCD, MongoDB)
4. **Start Phase 1** (sequential)
5. **Begin Phase 2** (parallel after Phase 1)

---

## References

- [CI/CD Database Version Testing Plan](./CI_CD_DATABASE_VERSION_TESTING_RESEARCH.md) - High-level architecture, workflow design, and key considerations
- [Migration Test Scenarios](./MIGRATION_TEST_SCENARIOS.md) - Detailed test scenarios, data seeding strategies, and success criteria
- [cloud-am Environment Setup Plan](./CLOUD_AM_ENVIRONMENT_SETUP_PLAN.md) - Prerequisite work in cloud-am repository
- [gitops-deploy.sh](../.circleci/scripts/gitops-deploy.sh) - Reference implementation for GitOps deployment pattern
