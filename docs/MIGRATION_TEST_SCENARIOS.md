# Migration Test Scenarios - Database Version Compatibility Testing

> **Document Navigation**:
> - 📋 **[High-Level Plan](./CI_CD_DATABASE_VERSION_TESTING_RESEARCH.md)** - Architecture, workflow design, and key decisions
> - 🔧 **[Implementation Plan](./MIGRATION_TEST_IMPLEMENTATION_PLAN.md)** - Detailed task breakdown, dependencies, and implementation steps
> - 🧪 **[This Document](./MIGRATION_TEST_SCENARIOS.md)** - Test scenarios, data seeding strategies, and success criteria
> - ☁️ **[cloud-am Setup Plan](./CLOUD_AM_ENVIRONMENT_SETUP_PLAN.md)** - Prerequisite work in cloud-am repository

## Overview

This document defines test scenarios for validating database version backward compatibility during upgrades and downgrades. Tests focus on detecting issues with enum compatibility, data integrity, and application functionality across version transitions.

**Note**: For POC, we use existing Jest tests. The scenarios below describe what we're testing, but implementation uses existing test suites rather than new migration-specific tests.

## High-Level Test Plan

### Test Flow Overview

```
1. Deploy Version N → Seed Data → Run Tests
2. Upgrade to Version N+1 (MAPI) → Run Tests
3. Upgrade to Version N+1 (Gateways) → Run Tests
4. Downgrade to Version N → Run Full Test Suite
```

### Test Categories

1. **Upgrade Tests**: Validate functionality after upgrading from N to N+1
2. **Downgrade Tests**: Validate backward compatibility and data filtering after downgrading
3. **Data Integrity Tests**: Ensure data consistency across version transitions
4. **Enum Compatibility Tests**: Verify handling of incompatible enum values

## Test Scenarios

### Scenario 1: Basic Upgrade Path (N → N+1)

**Objective**: Verify application works correctly after upgrading from version N to N+1.

**Setup**:
- Deploy version N (from_tag)
- Seed baseline data (compatible with both versions)

**Test Steps**:
1. Deploy version N
2. Seed baseline data (roles, memberships, users, applications)
3. Run functional tests (verify baseline functionality)
4. Upgrade MAPI to version N+1
5. Run functional tests (verify MAPI upgrade didn't break functionality)
6. Upgrade Gateways to version N+1
7. Run functional tests (verify full upgrade successful)

**Expected Results**:
- ✅ All baseline data accessible after upgrade
- ✅ New features in N+1 work correctly
- ✅ No data corruption
- ✅ Application starts successfully

**Test Data**:
- 10 roles (mix of ORGANIZATION, DOMAIN, ENVIRONMENT, APPLICATION reference types)
- 20 memberships (linking users to roles)
- 5 users
- 3 applications
- 2 domains

### Scenario 2: Upgrade with Version-Specific Data (N → N+1)

**Objective**: Verify that data created in version N+1 works correctly.

**Setup**:
- Deploy version N
- Seed baseline data
- Upgrade to N+1
- Create N+1-specific data (e.g., roles with PROTECTED_RESOURCE enum)

**Test Steps**:
1. Deploy version N
2. Seed baseline data
3. Upgrade to version N+1
4. Create version N+1-specific data via API:
   - Roles with `PROTECTED_RESOURCE` referenceType (if N+1 is 4.10+)
   - Memberships with `PROTECTED_RESOURCE` referenceType
5. Run functional tests
6. Verify N+1-specific data is accessible

**Expected Results**:
- ✅ N+1-specific data created successfully
- ✅ New enum values work correctly
- ✅ All data accessible via API

**Test Data**:
- Baseline data (from Scenario 1)
- 3 roles with `PROTECTED_RESOURCE` referenceType (N+1 only)
- 5 memberships with `PROTECTED_RESOURCE` referenceType (N+1 only)

### Scenario 3: Downgrade with Incompatible Data (N+1 → N)

**Objective**: Verify graceful handling of incompatible data when downgrading.

**Setup**:
- Deploy version N+1
- Seed baseline data + N+1-specific incompatible data
- Downgrade to version N

**Test Steps**:
1. Deploy version N+1
2. Seed baseline data
3. Create incompatible data (roles/memberships with PROTECTED_RESOURCE)
4. Downgrade to version N
5. Run full test suite (complete all tests, collect all failures)
6. Verify:
   - Application starts (with warnings if applicable)
   - Incompatible data is filtered (not accessible)
   - Compatible data still works
   - No crashes or security vulnerabilities

**Expected Results**:
- ✅ Application starts successfully
- ✅ Incompatible data (PROTECTED_RESOURCE) is filtered out
- ✅ Compatible data still accessible
- ✅ No "assignable to null" security vulnerabilities
- ✅ Warning logs present (if compatibility checker enabled)
- ✅ All test failures collected for analysis

**Test Data**:
- Baseline data (from Scenario 1)
- 5 roles with `PROTECTED_RESOURCE` referenceType
- 10 memberships with `PROTECTED_RESOURCE` referenceType
- Mix of compatible and incompatible data

### Scenario 4: Data Integrity Across Versions

**Objective**: Verify data integrity is maintained across upgrade/downgrade cycles.

**Setup**:
- Deploy version N
- Seed comprehensive dataset

**Test Steps**:
1. Deploy version N
2. Seed comprehensive dataset
3. Record data counts and sample records
4. Upgrade to N+1
5. Verify data counts match (excluding filtered incompatible data)
6. Verify sample records are still accessible
7. Downgrade to N
8. Verify data integrity maintained

**Expected Results**:
- ✅ Data counts match (accounting for filtered data)
- ✅ Sample records accessible
- ✅ No data corruption
- ✅ Relationships maintained (users → memberships → roles)

**Test Data**:
- 50 roles
- 100 memberships
- 20 users
- 10 applications
- 5 domains
- Various reference types and relationships

### Scenario 5: Enum Compatibility - PROTECTED_RESOURCE Filtering

**Objective**: Verify PROTECTED_RESOURCE enum values are properly filtered in version N.

**Setup**:
- Deploy version N+1
- Seed data with PROTECTED_RESOURCE enum values
- Downgrade to version N

**Test Steps**:
1. Deploy version N+1
2. Seed roles and memberships with PROTECTED_RESOURCE
3. Verify data accessible in N+1
4. Downgrade to version N
5. Query roles and memberships
6. Verify PROTECTED_RESOURCE records are filtered
7. Verify compatible records still accessible
8. Check logs for filtering warnings

**Expected Results**:
- ✅ PROTECTED_RESOURCE roles not returned in queries
- ✅ PROTECTED_RESOURCE memberships not returned
- ✅ Compatible roles/memberships still accessible
- ✅ Warning logs present (throttled)
- ✅ No crashes

**Test Data**:
- 10 roles: 5 with PROTECTED_RESOURCE, 5 compatible
- 20 memberships: 10 with PROTECTED_RESOURCE, 10 compatible

### Scenario 6: Pagination with Filtered Data

**Objective**: Verify pagination works correctly when some data is filtered.

**Setup**:
- Deploy version N+1
- Seed large dataset with mix of compatible/incompatible data
- Downgrade to version N

**Test Steps**:
1. Deploy version N+1
2. Seed 50 roles (10 with PROTECTED_RESOURCE, 40 compatible)
3. Downgrade to version N
4. Query roles with pagination (page 1, 10 items)
5. Verify results (should return 8-10 compatible roles)
6. Verify no crashes
7. Accept that count may be inaccurate (known limitation)

**Expected Results**:
- ✅ Pagination works (returns compatible roles)
- ✅ No crashes
- ⚠️ Count may be inaccurate (acceptable limitation)
- ✅ All pages return only compatible data

**Test Data**:
- 50 roles (10 PROTECTED_RESOURCE, 40 compatible)
- Pagination: page size 10

### Scenario 7: Application Startup with Incompatible Data

**Objective**: Verify application startup behavior with incompatible data.

**Setup**:
- Database contains incompatible data (PROTECTED_RESOURCE)
- Deploy version N

**Test Steps**:
1. Seed database with incompatible data (via direct DB insert)
2. Deploy version N
3. Monitor application startup
4. Check startup logs
5. Verify application behavior

**Expected Results**:
- ✅ Application starts successfully (with warnings if compatibility checker enabled)
- ✅ Startup logs show compatibility warnings (if enabled)
- ✅ Application functions normally after startup
- ✅ Incompatible data filtered during runtime

**Test Data**:
- 5 roles with PROTECTED_RESOURCE (inserted directly to DB)
- 10 memberships with PROTECTED_RESOURCE (inserted directly to DB)

## Data Seeding Strategy

### POC Approach: Using Existing Jest Tests

**Current Strategy**:
- Use existing Jest tests from current branch
- Tests will execute against deployed versions
- No custom seeding scripts initially

### 🔮 Future Approach: Migration-Specific Seeding

**Seeding Methods**:

#### Method 1: Direct Database Insertion (MongoDB)

```javascript
// MongoDB seeding script
// Location: tests/migration/seed/mongodb-seed.js

const seedBaselineData = async (mongoClient) => {
  const db = mongoClient.db('gravitee_am');
  
  // Seed roles
  await db.collection('roles').insertMany([
    {
      _id: 'role-org-1',
      name: 'Organization Role 1',
      reference_type: 'ORGANIZATION',
      reference_id: 'org-1',
      assignable_type: 'ORGANIZATION',
      created_at: new Date(),
      updated_at: new Date()
    },
    {
      _id: 'role-domain-1',
      name: 'Domain Role 1',
      reference_type: 'DOMAIN',
      reference_id: 'domain-1',
      assignable_type: 'DOMAIN',
      created_at: new Date(),
      updated_at: new Date()
    },
    // ... more roles
  ]);
  
  // Seed memberships
  await db.collection('memberships').insertMany([
    {
      _id: 'membership-1',
      member_id: 'user-1',
      member_type: 'USER',
      reference_type: 'ORGANIZATION',
      reference_id: 'org-1',
      role_id: 'role-org-1',
      created_at: new Date(),
      updated_at: new Date()
    },
    // ... more memberships
  ]);
  
  // Seed users
  await db.collection('users').insertMany([
    {
      _id: 'user-1',
      username: 'test-user-1',
      email: 'test1@example.com',
      first_name: 'Test',
      last_name: 'User',
      created_at: new Date(),
      updated_at: new Date()
    },
    // ... more users
  ]);
};

const seedIncompatibleData = async (mongoClient) => {
  const db = mongoClient.db('gravitee_am');
  
  // Seed roles with PROTECTED_RESOURCE (incompatible with version N)
  await db.collection('roles').insertMany([
    {
      _id: 'role-protected-1',
      name: 'Protected Role 1',
      reference_type: 'PROTECTED_RESOURCE',  // Incompatible enum
      reference_id: 'resource-1',
      assignable_type: 'PROTECTED_RESOURCE',  // Incompatible enum
      created_at: new Date(),
      updated_at: new Date()
    },
    // ... more incompatible roles
  ]);
  
  // Seed memberships with PROTECTED_RESOURCE
  await db.collection('memberships').insertMany([
    {
      _id: 'membership-protected-1',
      member_id: 'user-1',
      member_type: 'USER',
      reference_type: 'PROTECTED_RESOURCE',  // Incompatible enum
      reference_id: 'resource-1',
      role_id: 'role-protected-1',
      created_at: new Date(),
      updated_at: new Date()
    },
    // ... more incompatible memberships
  ]);
};
```

#### Method 2: API-Based Creation + Database Update

**Strategy**: Create valid records via API, then update enum fields in database.

```javascript
// Jest test seeding
// Location: tests/migration/seed/api-seed.js

const seedViaAPI = async (baseUrl, token) => {
  // Step 1: Create valid records via API
  const role = await createRoleViaAPI(baseUrl, token, {
    name: 'test-role',
    assignableType: 'DOMAIN'
  });
  
  const user = await createUserViaAPI(baseUrl, token, {
    username: 'test-user',
    email: 'test@example.com'
  });
  
  const membership = await createMembershipViaAPI(baseUrl, token, {
    memberId: user.id,
    memberType: 'USER',
    role: role.id
  });
  
  // Step 2: Update to incompatible enum via database
  await updateRoleInDatabase(role.id, {
    reference_type: 'PROTECTED_RESOURCE',
    assignable_type: 'PROTECTED_RESOURCE'
  });
  
  await updateMembershipInDatabase(membership.id, {
    reference_type: 'PROTECTED_RESOURCE'
  });
  
  return { role, user, membership };
};
```

#### Method 3: Idempotent Seeding Functions

**Strategy**: Create reusable, idempotent seeding functions.

```javascript
// Location: tests/migration/seed/seed-utils.js

export const seedBaselineData = async (db, options = {}) => {
  const {
    numRoles = 10,
    numMemberships = 20,
    numUsers = 5,
    numApplications = 3,
    numDomains = 2
  } = options;
  
  // Check if already seeded (idempotent)
  const existingRoles = await db.collection('roles').countDocuments({
    _id: { $regex: /^seed-role-/ }
  });
  
  if (existingRoles > 0 && !options.force) {
    console.log('Baseline data already seeded, skipping...');
    return;
  }
  
  // Seed roles
  const roles = [];
  for (let i = 0; i < numRoles; i++) {
    roles.push({
      _id: `seed-role-${i}`,
      name: `Seed Role ${i}`,
      reference_type: ['ORGANIZATION', 'DOMAIN', 'ENVIRONMENT', 'APPLICATION'][i % 4],
      reference_id: `ref-${i}`,
      assignable_type: ['ORGANIZATION', 'DOMAIN'][i % 2],
      created_at: new Date(),
      updated_at: new Date()
    });
  }
  await db.collection('roles').insertMany(roles);
  
  // Seed users
  const users = [];
  for (let i = 0; i < numUsers; i++) {
    users.push({
      _id: `seed-user-${i}`,
      username: `seed-user-${i}`,
      email: `seed-user-${i}@example.com`,
      first_name: 'Seed',
      last_name: `User${i}`,
      created_at: new Date(),
      updated_at: new Date()
    });
  }
  await db.collection('users').insertMany(users);
  
  // Seed memberships (link users to roles)
  const memberships = [];
  for (let i = 0; i < numMemberships; i++) {
    memberships.push({
      _id: `seed-membership-${i}`,
      member_id: `seed-user-${i % numUsers}`,
      member_type: 'USER',
      reference_type: roles[i % numRoles].reference_type,
      reference_id: roles[i % numRoles].reference_id,
      role_id: `seed-role-${i % numRoles}`,
      created_at: new Date(),
      updated_at: new Date()
    });
  }
  await db.collection('memberships').insertMany(memberships);
  
  console.log(`Seeded: ${numRoles} roles, ${numUsers} users, ${numMemberships} memberships`);
};
```

### Seeding Data Sets

#### Minimal Dataset (POC)
- 5 roles (mix of reference types)
- 10 memberships
- 3 users
- 2 applications
- 1 domain

#### Standard Dataset
- 10 roles
- 20 memberships
- 5 users
- 3 applications
- 2 domains

#### Comprehensive Dataset
- 50 roles
- 100 memberships
- 20 users
- 10 applications
- 5 domains
- Mix of compatible and incompatible data

#### Incompatible Data Set
- 5 roles with PROTECTED_RESOURCE
- 10 memberships with PROTECTED_RESOURCE
- Mixed with compatible data for comparison

## Test Execution Strategy

### POC: Using Existing Jest Tests

**Current Approach**:
- Run existing Jest test suites from current branch
- Tests execute against deployed versions
- No custom migration tests initially

**Test Execution Flow**:
1. After deploying version N → Run existing Jest tests
2. After upgrading MAPI → Run existing Jest tests
3. After upgrading Gateways → Run existing Jest tests
4. After downgrading → Run existing Jest tests (complete full suite)

### 🔮 Future: Migration-Specific Test Suites

**Test Suite Organization**:

```
tests/migration/
├── suites/
│   ├── suite-a-mapi/          # Management API tests
│   │   ├── seeding.spec.ts
│   │   ├── upgrade.spec.ts
│   │   └── downgrade.spec.ts
│   ├── suite-b-gateway/        # Gateway tests
│   │   ├── seeding.spec.ts
│   │   ├── upgrade.spec.ts
│   │   └── downgrade.spec.ts
│   └── suite-c-functional/     # Standard functional tests
│       ├── roles.spec.ts
│       ├── memberships.spec.ts
│       └── users.spec.ts
├── seed/
│   ├── mongodb-seed.js
│   ├── api-seed.js
│   └── seed-utils.js
└── helpers/
    ├── test-data-helpers.js
    └── assertion-helpers.js
```

**Test Execution Commands**:

```bash
# Run all migration tests
yarn test --testPathPattern=migration

# Run specific suite
yarn test --testPathPattern=migration/suites/suite-a-mapi

# Run seeding tests only
yarn test --testPathPattern=migration.*seeding

# Run upgrade tests only
yarn test --testPathPattern=migration.*upgrade

# Run downgrade tests only (complete full suite)
yarn test --testPathPattern=migration.*downgrade
```

## Failure Handling

### Non-Critical Stages (Fail Early)
- Initial deployment tests
- Upgrade tests
- Standard functional tests

**Behavior**: Stop on first failure, collect logs, fail workflow

### Critical Stages (Complete Full Suite)
- Downgrade tests
- Backward compatibility tests

**Behavior**: Continue running all tests, collect all failures, complete full suite

## Success Criteria

### Upgrade Tests
- ✅ Application starts successfully
- ✅ All baseline data accessible
- ✅ New features work correctly
- ✅ No data corruption
- ✅ All tests pass

### Downgrade Tests
- ✅ Application starts (with warnings if applicable)
- ✅ Incompatible data filtered (not accessible)
- ✅ Compatible data still works
- ✅ No crashes or security vulnerabilities
- ✅ All test failures collected for analysis

## Test Data Cleanup

### POC: Database Cleanup at Job Start
- Clean MongoDB database at beginning of each test run
- Drop collections or entire database
- Ensures clean state for each test

### 🔮 Future: Selective Cleanup
- Clean only test data (prefixed with `seed-` or `test-`)
- Preserve other data if needed
- Idempotent cleanup functions

## Next Steps

1. **POC Implementation**: Use existing Jest tests
2. **Define Test Scenarios**: Review and refine scenarios above
3. **Create Seeding Utilities**: Build reusable seeding functions (future)
4. **Create Migration Test Suites**: Build migration-specific tests (future)
5. **Document Test Data**: Maintain catalog of test datasets

## References

- [CI/CD Database Version Testing Plan](./CI_CD_DATABASE_VERSION_TESTING_RESEARCH.md) - High-level architecture, workflow design, and implementation approach
- [Migration Test Implementation Plan](./MIGRATION_TEST_IMPLEMENTATION_PLAN.md) - Detailed task breakdown, dependencies, and parallel work suggestions
- [cloud-am Environment Setup Plan](./CLOUD_AM_ENVIRONMENT_SETUP_PLAN.md) - Prerequisite work in cloud-am repository
- [API-Based Test Data Creation](./API_BASED_TEST_DATA_CREATION.md) - Strategies for creating test data via API
