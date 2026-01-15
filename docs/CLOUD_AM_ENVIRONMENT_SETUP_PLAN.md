# cloud-am Repository Environment Setup Plan

> **Note**: This work will be done in the [cloud-am repository](https://github.com/gravitee-io/cloud-am), not in this repository.
>
> **Related Documents**:
> - [CI/CD Database Version Testing Plan](./CI_CD_DATABASE_VERSION_TESTING_RESEARCH.md) - High-level architecture and workflow design
> - [Migration Test Implementation Plan](./MIGRATION_TEST_IMPLEMENTATION_PLAN.md) - Implementation tasks in this repository

## Overview

This document outlines the work needed in the **cloud-am repository** to set up the test environment for database version compatibility testing. This is a **prerequisite** for the migration testing workflow and must be completed before Phase 1 tasks in the AM repository.

## Quick Reference: MongoDB Image Version

**⚠️ IMPORTANT**: Use **`mongo:8.0`** for MongoDB deployment (matches Jest test environment).

- ✅ **Correct**: `image: mongo:8.0`
- ❌ **Avoid**: `image: mongo:latest` or `image: mongo` (no tag)

This matches the version used in:
- Local Jest tests: `docker/local-stack/dev/docker-compose.mongo.yml` → `mongo:${MONGO_VERSION:-8.0}`
- Testcontainers: `mongo:8.0`

If MongoDB pod is stuck in `ContainerCreating`, verify the image tag is `mongo:8.0` (see [Troubleshooting](#troubleshooting-mongodb-containercreating-issues) section below).

## Key Difference: Current Jest Tests vs Migration Tests

### Current Jest Test Setup (Existing CI)
- **Deployment**: Local Docker Compose stack in CircleCI
- **MongoDB**: Local MongoDB container in docker-compose
- **Connection**: `mongodb://mongodb:27017/graviteeam` (docker network)
- **AM Applications**: Deployed as Docker containers in same compose stack
- **Tests**: Connect to localhost AM APIs

### Migration Test Setup (New)
- **Deployment**: ArgoCD-deployed to remote Kubernetes cluster
- **MongoDB**: Deployed via ArgoCD (or accessible from cluster)
- **Connection for AM**: Internal cluster connection (e.g., `mongodb://mongodb-service:27017/graviteeam`)
- **Connection for CircleCI**: External connection (must be accessible from CircleCI network)
- **AM Applications**: Deployed via ArgoCD to test environment
- **Tests**: Connect to remote ArgoCD-deployed AM APIs

**Critical Requirement**: MongoDB must be accessible from CircleCI for cleanup script and potentially for test data operations.

## Prerequisites Status

⚠️ **This work must be completed before starting Phase 1 tasks in AM repository**

The migration testing workflow depends on:
- ArgoCD ApplicationSet configured for test environment
- MongoDB instance deployed via ArgoCD
- Test environment branch in cloud-am repository
- Proper values.yaml structure for MAPI and Gateway image tags

## Required Components

### 1. Test Environment Branch

**Location**: `cloud-am` repository

**Requirements**:
- Create dedicated branch for migration test environment (e.g., `migration-test-env` or similar)
- Branch should follow existing cloud-am branch structure
- Branch should be separate from other environments to avoid conflicts

**Structure**:
```
cloud-am/
└── migration-test-env/          # or similar branch name
    └── values.yaml              # Environment-specific values
```

### 2. ArgoCD ApplicationSet Configuration

**Purpose**: Automatically deploy AM (MAPI + Gateways) to test environment when values.yaml is updated

**Requirements**:
- ApplicationSet that watches the migration test environment branch
- Deploys Management API (MAPI)
- Deploys Gateways
- Uses Helm charts from AM repository
- Supports image tag updates via values.yaml

**Configuration Needs**:
- ApplicationSet manifest (similar to existing ApplicationSets in cloud-am)
- Target namespace for test environment
- Helm chart repository and version
- Values file path (migration-test-env/values.yaml)

### 3. MongoDB ApplicationSet (Optional but Recommended)

**Purpose**: Deploy MongoDB instance for test environment

**Requirements**:
- ApplicationSet for MongoDB deployment
- In-cluster or in-namespace MongoDB instance
- Persistent volume for data (optional - may be cleaned anyway)
- Connection details accessible to AM applications

**Note**: If MongoDB is deployed separately (not via ArgoCD), ensure it's accessible and connection details are documented.

### 4. values.yaml Structure

**Location**: `cloud-am/migration-test-env/values.yaml`

**Requirements**:
- Support for separate MAPI and Gateway image tags
- Structure similar to existing values.yaml files
- Use YAML anchors for imageTag (like existing gitops-deploy.sh pattern)
- Support updating MAPI imageTag independently
- Support updating Gateway imageTag independently
- Support updating both together

**Example Structure**:
```yaml
am:
  imageTag: &imageTag "4.10.5"  # Default/fallback
  
  mapi:
    imageTag: *imageTag          # Can be overridden
    
  gateway:
    imageTag: *imageTag           # Can be overridden
```

**Or Alternative**:
```yaml
am:
  imageTag: "4.10.5"             # Used for both if not specified separately
  
  mapi:
    imageTag: "4.10.5"            # Optional override
    
  gateway:
    imageTag: "4.10.5"            # Optional override
```

### 5. Environment Configuration

**Requirements**:
- Namespace for test environment
- Resource limits/requests
- Environment variables
- MongoDB connection configuration
- Health check endpoints
- Service accounts and RBAC (if needed)

## Implementation Tasks (for cloud-am repository)

### Task 1: Create Test Environment Branch

**Description**: Create dedicated branch for migration test environment

**Steps**:
1. Create new branch in cloud-am repository (e.g., `migration-test-env`)
2. Copy base values.yaml structure from existing environment
3. Configure for test environment (namespace, resources, etc.)
4. Document branch purpose and usage

**Acceptance Criteria**:
- ✅ Branch created and accessible
- ✅ Base values.yaml structure in place
- ✅ Branch documented

**Estimated Time**: 1-2 hours

---

### Task 2: Configure ArgoCD ApplicationSet

**Description**: Set up ApplicationSet to deploy AM to test environment

**Steps**:
1. Create ApplicationSet manifest for test environment
2. Configure to watch migration-test-env branch
3. Set up MAPI application deployment
4. Set up Gateway application deployment
5. Configure Helm chart references
6. Test ApplicationSet creation

**Acceptance Criteria**:
- ✅ ApplicationSet created in ArgoCD
- ✅ Applications (MAPI + Gateways) created automatically
- ✅ Applications sync when values.yaml changes
- ✅ Image tag updates trigger deployments

**Estimated Time**: 3-4 hours

**Dependencies**: Task 1 (branch must exist)

---

### Task 3: Configure MongoDB Deployment

**Description**: Set up MongoDB instance for test environment

**⚠️ CRITICAL REQUIREMENT**: 
- MongoDB must be **accessible from CircleCI** (not just from within the cluster)
- Jest tests run in CircleCI and need to connect to MongoDB remotely
- Current Jest tests use local Docker Compose MongoDB, but migration tests need remote MongoDB

**Current Jest Test Setup** (for reference):
- Uses local Docker Compose stack with MongoDB container
- Connection: `mongodb://mongodb:27017/graviteeam` (local docker network)
- MongoDB runs in same docker-compose as AM applications

**Migration Test Requirements**:
- MongoDB deployed via ArgoCD (or accessible from cluster)
- MongoDB accessible from CircleCI (external connection)
- Connection string must be reachable from CircleCI network
- MongoDB in same namespace/cluster as AM applications (for AM to connect)

**Options**:
- **Option A**: ArgoCD ApplicationSet for MongoDB (recommended)
  - Deploy MongoDB via ArgoCD in same namespace as AM
  - Expose MongoDB service (NodePort, LoadBalancer, or Ingress)
  - Ensure MongoDB is accessible from CircleCI network
- **Option B**: Existing MongoDB instance (document connection details)
  - Must be accessible from CircleCI
  - Document external connection string
  - Ensure network access from CircleCI

**Steps (Option A - Recommended)**:
1. Create MongoDB ApplicationSet or Helm chart reference
2. **Configure MongoDB image version**:
   - **Recommended**: `mongo:8.0` (matches local Jest test setup)
   - Alternative: `mongo:6.0` (also used in some dev setups)
   - **DO NOT use**: `mongo:latest` (unpredictable, may cause compatibility issues)
   - Example: `image: mongo:8.0` in deployment manifest
3. Configure MongoDB service exposure (NodePort/LoadBalancer for external access)
4. Configure persistent volume (optional - may be cleaned anyway)
5. Set up connection details in values.yaml for AM applications
6. Document external MongoDB connection string for CircleCI
7. Test MongoDB deployment and external connectivity
8. Verify AM applications can connect to MongoDB
9. Verify CircleCI can connect to MongoDB (for cleanup script)

**MongoDB Image Version Reference**:
- **Jest tests (local-stack)**: `mongo:${MONGO_VERSION:-8.0}` (defaults to 8.0)
- **Testcontainers**: `mongo:8.0`
- **Dev compose**: `mongo:6.0` or `mongo:8.0`
- **Recommended for migration tests**: `mongo:8.0` (matches test environment)

**Steps (Option B)**:
1. Document existing MongoDB connection details
2. Ensure external accessibility from CircleCI network
3. Update values.yaml with connection string for AM applications
4. Provide external connection string for CircleCI workflow
5. Test connectivity from CircleCI

**MongoDB Connection Requirements**:
- **For AM Applications** (deployed via ArgoCD):
  - Internal connection: `mongodb://mongodb-service:27017/graviteeam` (within cluster)
  - Or external if using external MongoDB
- **For CircleCI Workflow** (cleanup script):
  - External connection string: `mongodb://<external-host>:<port>/graviteeam`
  - Must be accessible from CircleCI network
  - May require network configuration (firewall rules, VPN, etc.)

**Acceptance Criteria**:
- ✅ MongoDB image version specified correctly (`mongo:8.0` recommended)
- ✅ MongoDB pod starts successfully (not stuck in ContainerCreating)
- ✅ MongoDB deployed and accessible from AM applications
- ✅ MongoDB accessible from CircleCI (external connection)
- ✅ Connection details documented (internal for AM, external for CircleCI)
- ✅ Connection string in values.yaml for AM applications
- ✅ External connection string provided for CircleCI workflow
- ✅ MongoDB can be cleaned via external connection (for cleanup script)
- ✅ Network connectivity tested from CircleCI

**Estimated Time**: 3-4 hours (Option A) or 1-2 hours (Option B)

**Dependencies**: Task 1 (branch must exist)

**Network Considerations**:
- May need to configure firewall rules or network policies
- May need LoadBalancer or Ingress for external access
- Consider security (authentication, TLS) for external connections

**Troubleshooting MongoDB ContainerCreating Issues**:

If MongoDB pod is stuck in `ContainerCreating` state, check:

1. **Image Version**:
   ```yaml
   # ✅ CORRECT
   image: mongo:8.0
   
   # ❌ AVOID
   image: mongo:latest
   image: mongo  # no tag specified
   ```

2. **Image Pull Issues**:
   - Verify image exists: `docker pull mongo:8.0`
   - Check if image pull secrets are needed
   - Verify registry access from cluster

3. **Resource Constraints**:
   - Check if cluster has enough resources
   - Verify resource requests/limits in deployment

4. **Network Policies**:
   - Ensure network policies allow MongoDB traffic
   - Check if security policies are blocking

5. **Check Pod Events**:
   ```bash
   kubectl describe pod <mongodb-pod-name> -n <namespace>
   # Look for Events section for specific error messages
   ```

6. **Common Fixes**:
   - Use specific image tag: `mongo:8.0` (not `latest`)
   - Ensure image pull policy: `IfNotPresent` or `Always`
   - Verify namespace and service account permissions

---

### Task 4: Configure values.yaml Structure

**Description**: Set up values.yaml to support separate MAPI/Gateway image tags

**Steps**:
1. Review existing values.yaml structure
2. Add support for separate MAPI imageTag
3. Add support for separate Gateway imageTag
4. Ensure backward compatibility (both use same tag if only one specified)
5. Test with gitops-deploy.sh pattern (or create new script)

**Acceptance Criteria**:
- ✅ values.yaml supports separate MAPI imageTag
- ✅ values.yaml supports separate Gateway imageTag
- ✅ values.yaml supports updating both together
- ✅ Structure compatible with yq updates (used in AM repository scripts)
- ✅ YAML anchors preserved (if used)

**Estimated Time**: 2-3 hours

**Dependencies**: Task 1 (branch must exist)

---

### Task 5: Test End-to-End Deployment

**Description**: Verify complete setup works end-to-end

**Steps**:
1. Update values.yaml with test image tags
2. Commit and push to migration-test-env branch
3. Verify ArgoCD syncs changes
4. Verify MAPI deploys with correct image tag
5. Verify Gateways deploy with correct image tag
6. Verify MongoDB connection works
7. Verify applications are healthy

**Acceptance Criteria**:
- ✅ ArgoCD syncs changes automatically
- ✅ MAPI deploys with specified image tag
- ✅ Gateways deploy with specified image tag
- ✅ Applications reach "Synced" and "Healthy" states
- ✅ MongoDB connection works
- ✅ Health checks pass

**Estimated Time**: 2-3 hours

**Dependencies**: 
- ✅ Task 2 (ApplicationSet)
- ✅ Task 3 (MongoDB)
- ✅ Task 4 (values.yaml)

---

## Dependencies Between Repositories

### cloud-am → AM Repository

**cloud-am must provide**:
- ✅ Test environment branch with values.yaml
- ✅ ArgoCD ApplicationSet configured
- ✅ MongoDB accessible
- ✅ Image tag update mechanism working

**AM repository depends on**:
- ✅ Ability to push image tags to cloud-am branch
- ✅ ArgoCD syncs changes automatically
- ✅ Applications deploy successfully
- ✅ MongoDB connection details available

### AM Repository → cloud-am

**AM repository will**:
- Push image tag updates to cloud-am branch
- Poll ArgoCD sync status
- Clean MongoDB database

**cloud-am must support**:
- ✅ Git push operations (SSH access)
- ✅ YAML updates via scripts (yq compatible)
- ✅ Separate MAPI/Gateway image tags

## Implementation Order

### Phase 0: cloud-am Setup (Prerequisite)

**Must complete before AM repository Phase 1**:

1. **Task 1**: Create test environment branch (1-2h)
2. **Task 2**: Configure ArgoCD ApplicationSet (3-4h)
3. **Task 3**: Configure MongoDB deployment (2-3h)
4. **Task 4**: Configure values.yaml structure (2-3h)
5. **Task 5**: Test end-to-end deployment (2-3h)

**Total Estimated Time**: 10-15 hours

### After cloud-am Setup Complete

**AM repository can proceed with**:
- Phase 1: Foundation Scripts (MongoDB cleanup, ArgoCD sync, GitOps deployment)
- Phase 2: Core Components (CircleCI workflow, zx script)
- Phase 3: Integration

## Key Decisions Needed

### 1. Branch Naming
- What should the test environment branch be named?
- Should it follow existing naming conventions?

### 2. MongoDB Strategy
- Option A: ArgoCD ApplicationSet (recreatable)
- Option B: Existing instance (document connection)

**Recommendation**: Option A for cleaner isolation, but Option B is faster if instance exists

### 3. values.yaml Structure
- Use YAML anchors (like existing pattern)?
- Separate MAPI/Gateway sections?
- How to handle backward compatibility?

**Recommendation**: Follow existing cloud-am patterns for consistency

### 4. Namespace Strategy
- Separate namespace for migration tests?
- Or reuse existing test namespace?

**Recommendation**: Separate namespace for isolation

## Documentation Requirements

### In cloud-am Repository

1. **README or Documentation**:
   - Purpose of migration-test-env branch
   - How to update image tags
   - How ArgoCD ApplicationSet works
   - MongoDB connection details
   - Troubleshooting guide

2. **values.yaml Comments**:
   - Document MAPI imageTag field
   - Document Gateway imageTag field
   - Document MongoDB connection configuration

## Network Access Requirements

### MongoDB External Access

**Critical**: MongoDB must be accessible from CircleCI for:
1. Cleanup script (Task 1.1 in AM repository)
2. Potentially test data operations (future)

**Options for External Access**:
- **Option 1**: LoadBalancer service type
  - Creates external IP
  - Accessible from internet (with security considerations)
- **Option 2**: NodePort service type
  - Exposes on cluster node IPs
  - May require firewall rules
- **Option 3**: Ingress with authentication
  - More secure
  - Requires ingress controller
- **Option 4**: VPN/Tunnel
  - Most secure
  - Requires VPN setup between CircleCI and cluster

**Security Considerations**:
- Use MongoDB authentication (username/password)
- Consider TLS/SSL for external connections
- Restrict network access (firewall rules, IP whitelisting)
- Use secrets for credentials

**Recommendation**: Start with LoadBalancer for POC, add security hardening later.

## Testing Checklist

Before AM repository work can begin:

- [ ] Test environment branch created
- [ ] ArgoCD ApplicationSet configured and working
- [ ] MAPI deploys successfully with image tag from values.yaml
- [ ] Gateways deploy successfully with image tag from values.yaml
- [ ] MongoDB deployed and accessible from AM applications (internal)
- [ ] MongoDB accessible from CircleCI (external connection tested)
- [ ] External MongoDB connection string documented
- [ ] Can update image tags via git push
- [ ] ArgoCD syncs changes automatically
- [ ] Applications reach "Synced" and "Healthy" states
- [ ] Health check endpoints work
- [ ] Network connectivity verified from CircleCI
- [ ] ArgoCD access configured and tested from CircleCI
  - [ ] API token or username/password stored in secret manager
  - [ ] ArgoCD server URL accessible from CircleCI
  - [ ] Test script can authenticate and query application status
- [ ] Documentation complete

## Handoff to AM Repository

Once cloud-am setup is complete, provide to AM repository team:

1. **Branch name**: `migration-test-env` (or actual name)
2. **MongoDB connection details**:
   - **Internal connection** (for AM applications): `mongodb://mongodb-service:27017/graviteeam`
   - **External connection** (for CircleCI cleanup script): `mongodb://<external-host>:<port>/graviteeam`
   - Credentials (if needed)
   - Database name: `graviteeam` (or actual name)
3. **ArgoCD application names**: 
   - MAPI app name (e.g., `am-migration-test-env-mapi`)
   - Gateway app name (e.g., `am-migration-test-env-gateway`)
   - Or single app name if using ApplicationSet
4. **ArgoCD server URL**: 
   - Full URL (e.g., `https://argocd.example.com`)
   - Must be accessible from CircleCI network
   - Note if self-signed certificate (will need `ARGOCD_INSECURE=true`)
5. **ArgoCD access credentials** (for CircleCI):
   - **Option A (Recommended)**: API token
     - Generate token: `argocd account generate-token --account <account-name>`
     - Store in Keeper secret manager
     - Provide secret identifier to AM team
   - **Option B**: Username/password
     - Service account username
     - Service account password
     - Store both in Keeper secret manager
   - **Note**: See [ArgoCD Access Setup](../docs/MIGRATION_TEST_IMPLEMENTATION_PLAN.md#argocd-access-setup) in AM repository docs for details
6. **values.yaml structure**: Documented structure for image tag updates
   - Path to values.yaml file
   - YAML path for MAPI imageTag (e.g., `.management.imageTag`)
   - YAML path for Gateway imageTag (e.g., `.gateway.imageTag`)
7. **SSH access**: Ensure AM repository CI has SSH access to cloud-am
   - SSH key fingerprint (if using CircleCI SSH keys)
   - Or deploy key configured in cloud-am repository
8. **Network access**: 
   - Ensure CircleCI can reach MongoDB (may require firewall/VPN configuration)
   - Ensure CircleCI can reach ArgoCD server (may require firewall/VPN configuration)
   - Document any required network policies or whitelisting

## References

- [CI/CD Database Version Testing Plan](./CI_CD_DATABASE_VERSION_TESTING_RESEARCH.md) - High-level architecture and workflow design
- [Migration Test Implementation Plan](./MIGRATION_TEST_IMPLEMENTATION_PLAN.md) - AM repository tasks that depend on this work
- [gitops-deploy.sh](../.circleci/scripts/gitops-deploy.sh) - Reference for GitOps deployment pattern
- [cloud-am Repository](https://github.com/gravitee-io/cloud-am) - Target repository for this work
