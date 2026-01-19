## Table of Contents

1. [Overview](#overview)
1. [Core Principles](#core-principles)
1. [Fixture Pattern](#fixture-pattern)
1. [File Structure](#file-structure)
1. [Templates](#templates)
1. [Best Practices](#best-practices)
1. [Common Patterns](#common-patterns)
1. [Migration Guide](#migration-guide)
1. [Examples](#examples)
1. [Checklist](#checklist)

---

## Overview

This document defines the standards and best practices for writing Jest integration tests in the Gravitee AM test suite. All new tests should follow these guidelines, and existing tests should be migrated incrementally.

### Key Requirements

- **Use the Fixture Pattern**: All tests must use fixtures for setup and teardown
- **Single Responsibility**: Each test file focuses on a single feature
- **Isolation**: Tests are independent and runnable in parallel
- **Maintainability**: Code is clear, well-organized, and easy to understand
- **Reusability**: Common setup patterns are extracted to fixtures

---

## Core Principles

### 1. Fixture Pattern First

All tests should use a fixture pattern for setup and teardown. This provides:

- Centralized setup logic
- Guaranteed cleanup
- Reusable test environments
- Type safety through TypeScript interfaces

### 2. Single Responsibility

Each test file should focus on testing one feature or functionality area.

### 3. Test Isolation

Tests should be:

- Independent (can run in any order)
- Isolated (don't share state)
- Parallelizable (use `uniqueName()` for resources)

### 4. Clear and Descriptive

- Test names clearly describe what is being tested
- Code is self-documenting
- Complex logic is extracted to helper functions

---

## Fixture Pattern

### Why Use Fixtures?

Fixtures provide:

- **Centralized Setup**: All test environment setup in one place
- **Automatic Cleanup**: Guaranteed cleanup even if tests fail
- **Reusability**: Share setup logic across multiple test files
- **Type Safety**: TypeScript interfaces ensure correct usage
- **Test Helpers**: Include common operations as fixture methods

### Fixture File Structure

Fixtures should be placed in a `fixtures/` directory adjacent to the test files:

```
<code_block_to_apply_changes_from>
specs/
  gateway/
    feature-name/
      feature-name.jest.spec.ts
      fixtures/
        feature-name-fixture.ts
        test-utils.ts (optional)
```

### Fixture Interface Requirements

Every fixture must export:

1. **Interface**: TypeScript interface defining the fixture structure
1. **Constants**: Test constants used by the fixture (if needed)
1. **Setup Function**: Main function that creates and returns the fixture
1. **Helper Functions**: Reusable functions for common operations (optional)

### Fixture Interface Template

```typescript
export interface FeatureFixture {
  // Core resources (required)
  domain: Domain;
  application?: Application;  // Optional for management tests
  user?: User;                // Optional for management tests
  accessToken: string;
  
  // Configuration (optional)
  openIdConfiguration?: any;
  defaultIdp?: IdentityProvider;
  
  // Cleanup function (REQUIRED)
  cleanup: () => Promise<void>;
  
  // Helper methods (optional)
  performOperation?: (params: any) => Promise<any>;
  getAccessToken?: () => Promise<string>;
}
```

### Fixture Setup Function Template

```typescript
export const setupFeatureFixture = async (): Promise<FeatureFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;
  
  try {
    // 1. Setup environment
    accessToken = await requestAdminAccessToken();
    domain = await createDomain(
      accessToken,
      uniqueName('feature-name', true), // Always use uniqueName with forceRandom
      'Test domain description'
    );
    
    // 2. Start domain if needed
    const startedDomain = await startDomain(domain.id, accessToken);
    const domainReady = await waitForDomainStart(startedDomain);
    await waitForDomainSync(domainReady.domain.id, accessToken);
    
    // 3. Create other resources
    // ... application, user, etc.
    
    // 4. Get OIDC config if needed (gateway tests)
    let openIdConfiguration: any;
    try {
      const oidcResponse = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);
      openIdConfiguration = oidcResponse.body;
    } catch (error) {
      // OK for management-only tests
      openIdConfiguration = undefined;
    }
    
    // 5. Define cleanup function
    const cleanup = async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    };
    
    // 6. Return fixture
    return {
      domain,
      accessToken,
      openIdConfiguration,
      cleanup,
      // ... other properties
    };
  } catch (error) {
    // Cleanup on failure
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};
```

---

## File Structure

### Standard Test File Layout

```typescript
// 1. IMPORTS
import fetch from 'cross-fetch';
import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';
import { setupFeatureFixture, FeatureFixture } from './fixtures/feature-name-fixture';

globalThis.fetch = fetch;
jest.setTimeout(200000);

// 2. FIXTURE SETUP
let fixture: FeatureFixture;

beforeAll(async () => {
  fixture = await setupFeatureFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

// 3. TEST SUITES
describe('Feature Name - Primary Functionality', () => {
  it('should perform basic operation', async () => {
    // Test implementation
  });
});
```

---

## Templates

### Gateway Test Template

```typescript
import fetch from 'cross-fetch';
import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';
import { setupFeatureFixture, FeatureFixture } from './fixtures/feature-name-fixture';

globalThis.fetch = fetch;
jest.setTimeout(200000);

let fixture: FeatureFixture;

beforeAll(async () => {
  fixture = await setupFeatureFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Feature Name - Primary Functionality', () => {
  it('should perform basic operation successfully', async () => {
    // Arrange
    const testParam = 'test-value';

    // Act
    const result = await fixture.performOperation(testParam);

    // Assert
    expect(result).toBeDefined();
    expect(result.status).toBe(200);
  });

  it('should handle valid input correctly', async () => {
    // Test implementation
  });
});

describe('Feature Name - Error Handling', () => {
  it('should reject invalid input with appropriate error', async () => {
    // Test implementation
  });
});
```

### Management Test Template

```typescript
import fetch from 'cross-fetch';
import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';
import { setupResourceFixture, ResourceFixture } from './fixtures/resource-name-fixture';

globalThis.fetch = fetch;
jest.setTimeout(200000);

let fixture: ResourceFixture;

beforeAll(async () => {
  fixture = await setupResourceFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Resource Name - CRUD Operations', () => {
  let createdResourceId: string;

  it('should create a new resource', async () => {
    const resource = await fixture.createResource({
      name: 'test-resource',
    });

    expect(resource).toBeDefined();
    expect(resource.id).toBeDefined();
    createdResourceId = resource.id;
  });

  it('should retrieve resource by ID', async () => {
    const resource = await fixture.getResource(createdResourceId);
    expect(resource.id).toEqual(createdResourceId);
  });

  it('should update resource', async () => {
    const updated = await fixture.updateResource(createdResourceId, {
      name: 'updated-name',
    });
    expect(updated.name).toEqual('updated-name');
  });

  it('should delete resource', async () => {
    await fixture.deleteResource(createdResourceId);
    await expect(fixture.getResource(createdResourceId)).rejects.toThrow();
  });
});
```

### Fixture Template

```typescript
import { expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { createUser } from '@management-commands/user-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { User } from '@management-models/User';
import { uniqueName } from '@utils-commands/misc';
import { getWellKnownOpenIdConfiguration } from '@gateway-commands/oauth-oidc-commands';

/**
 * Fixture interface defining all resources and helper methods available to tests
 */
export interface FeatureFixture {
  // Core resources
  domain: Domain;
  application: Application;
  user: User;
  defaultIdp: IdentityProvider;
  accessToken: string;
  
  // Configuration
  openIdConfiguration?: any;
  
  // Cleanup function (REQUIRED)
  cleanup: () => Promise<void>;
  
  // Helper methods (optional)
  performOperation?: (params: any) => Promise<any>;
}

/**
 * Test constants used by the fixture
 */
export const FEATURE_TEST = {
  DOMAIN_NAME_PREFIX: 'feature-name',
  DOMAIN_DESCRIPTION: 'Feature test domain',
  APP_NAME: 'feature-app',
  APP_TYPE: 'WEB' as const,
  USER_PASSWORD: 'FeatureP@ssw0rd123!',
  USER_USERNAME: 'featureuser',
  USER_EMAIL: 'feature.user@test.com',
  USER_FIRST_NAME: 'Feature',
  USER_LAST_NAME: 'User',
  REDIRECT_URI: 'https://example.com/callback',
} as const;

/**
 * Helper function to setup test environment (domain, IDP)
 */
async function setupTestEnvironment() {
  const accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  const domain = await createDomain(
    accessToken,
    uniqueName(FEATURE_TEST.DOMAIN_NAME_PREFIX, true),
    FEATURE_TEST.DOMAIN_DESCRIPTION,
  );
  expect(domain).toBeDefined();
  expect(domain.id).toBeDefined();

  const startedDomain = await startDomain(domain.id, accessToken);
  const domainReady = await waitForDomainStart(startedDomain);
  await waitForDomainSync(domainReady.domain.id, accessToken);

  const idpSet = await getAllIdps(domainReady.domain.id, accessToken);
  const defaultIdp = idpSet.values().next().value;
  expect(defaultIdp).toBeDefined();

  return { domain: domainReady.domain, defaultIdp, accessToken };
}

/**
 * Helper function to create test application
 */
async function createTestApplication(
  domain: Domain,
  defaultIdp: IdentityProvider,
  accessToken: string,
): Promise<Application> {
  const appName = uniqueName(FEATURE_TEST.APP_NAME, true);
  const application = await createTestApp(appName, domain, accessToken, FEATURE_TEST.APP_TYPE, {
    settings: {
      oauth: {
        redirectUris: [FEATURE_TEST.REDIRECT_URI],
        grantTypes: ['authorization_code', 'password'],
        scopeSettings: [{ scope: 'openid', defaultScope: true }],
      },
      advanced: {
        skipConsent: true,
      },
    },
    identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
  });

  expect(application).toBeDefined();
  expect(application.id).toBeDefined();
  return application;
}

/**
 * Helper function to create test user
 */
async function createTestUser(
  domain: Domain,
  application: Application,
  defaultIdp: IdentityProvider,
  accessToken: string,
): Promise<User> {
  const username = uniqueName(FEATURE_TEST.USER_USERNAME, true);
  const testUser = await createUser(domain.id, accessToken, {
    firstName: FEATURE_TEST.USER_FIRST_NAME,
    lastName: FEATURE_TEST.USER_LAST_NAME,
    email: `${username}@test.com`,
    username: username,
    password: FEATURE_TEST.USER_PASSWORD,
    client: application.id,
    source: defaultIdp.id,
    preRegistration: false,
  });

  expect(testUser).toBeDefined();
  return testUser;
}

/**
 * Main fixture setup function
 */
export const setupFeatureFixture = async (): Promise<FeatureFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    // Setup test environment
    const envResult = await setupTestEnvironment();
    domain = envResult.domain;
    accessToken = envResult.accessToken;
    const { defaultIdp } = envResult;

    // Create test application
    const application = await createTestApplication(domain, defaultIdp, accessToken);

    // Create test user
    const user = await createTestUser(domain, application, defaultIdp, accessToken);

    // Wait for sync
    await waitForDomainSync(domain.id, accessToken);

    // Get OIDC configuration (if needed)
    let openIdConfiguration: any;
    try {
      const openIdConfigurationResponse = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);
      openIdConfiguration = openIdConfigurationResponse.body;
      expect(openIdConfiguration).toBeDefined();
    } catch (error) {
      openIdConfiguration = undefined;
    }

    // Optional: Add helper methods
    const performOperation = async (params: any) => {
      // Implementation
      return { status: 200, data: params };
    };

    // Cleanup function
    const cleanup = async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    };

    return {
      domain,
      application,
      user,
      defaultIdp,
      accessToken,
      openIdConfiguration,
      cleanup,
      performOperation,
    };
  } catch (error) {
    // Cleanup on failure
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup domain after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};

/**
 * Optional: Export helper functions for use across test files
 */
export function buildFeatureUrl(endpoint: string, params: Record<string, string>): string {
  const urlParams = new URLSearchParams(params);
  return `${endpoint}?${urlParams.toString()}`;
}
```

---

## Best Practices

### 1. Unique Naming

**Always** use `uniqueName()` for resources to support parallel execution:

```typescript
// GOOD
const domain = await createDomain(
  accessToken,
  uniqueName('feature-name', true), // forceRandom = true
  'Test domain description'
);

// BAD
const domain = await createDomain(accessToken, 'my-test-domain', 'Description');
```

### 2. Cleanup

**Always** cleanup resources, even if domain deletion cascades:

```typescript
afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});
```

### 3. Wait for Sync

When creating resources that need to be available in the gateway, **always** wait for sync:

```typescript
await waitForDomainSync(domain.id, accessToken);
```

### 4. Error Handling

Handle errors gracefully in fixtures with try/catch:

```typescript
try {
  // Setup code
} catch (error) {
  // Cleanup on failure
  if (domain?.id && accessToken) {
    await safeDeleteDomain(domain.id, accessToken);
  }
  throw error;
}
```

### 5. Test Constants

Define constants at the top of fixture files:

```typescript
export const FEATURE_TEST = {
  DOMAIN_NAME_PREFIX: 'feature-name',
  USER_PASSWORD: 'TestP@ssw0rd123!',
  REDIRECT_URI: 'https://example.com/callback',
} as const;
```

### 6. Helper Functions

Extract common operations to helper functions in fixture files:

```typescript
export function buildAuthorizationUrl(endpoint: string, clientId: string): string {
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: clientId,
  });
  return `${endpoint}?${params.toString()}`;
}
```

### 7. Dynamic Test Generation

**Recommendation: Avoid dynamic test generation in most cases**

#### Problems with Dynamic Test Generation

1. **Debugging Difficulty**: Hard to run individual failing tests
1. **Test Output Clarity**: Generated test names can be unclear
1. **Maintenance**: Logic hidden in loops makes tests harder to understand

#### When It Might Be Acceptable

- You have 10+ nearly identical tests (only input differs)
- Test names are clear and descriptive
- You document why dynamic generation is used
- You can still run individual tests easily

#### Better Alternatives

**Option 1: Test Data Arrays with Explicit Tests**

```typescript
// GOOD: Clear, explicit tests
describe('Password dictionary validation', () => {
  const dictionaryWords = ['password', 'passw0rd', 'pass123'];
  
  dictionaryWords.forEach((word) => {
    it(`should reject dictionary word: ${word}`, async () => {
      const result = await validatePassword(word);
      expect(result.error).toBe('dictionary_word');
    });
  });
});
```

**Option 2: Shared Test Function**

```typescript
// GOOD: Reusable test function
function testPasswordRejection(password: string, expectedError: string) {
  it(`should reject password: ${password}`, async () => {
    const result = await validatePassword(password);
    expect(result.error).toBe(expectedError);
  });
}

describe('Password validation', () => {
  testPasswordRejection('password', 'dictionary_word');
  testPasswordRejection('passw0rd', 'dictionary_word');
  testPasswordRejection('pass123', 'dictionary_word');
});
```

**Option 3: Split into Separate Files**

```typescript
// Split complex test files into multiple focused files
// specs/gateway/forgot-password-app-settings.jest.spec.ts
// specs/gateway/forgot-password-domain-settings.jest.spec.ts
```

### 8. File Length

Keep test files under **300 lines**. If a file grows longer:

- Split into multiple test files by feature area
- Extract complex setup to fixtures
- Move helper functions to separate utility files

### 9. Test Descriptions

Test descriptions should be:

- **Descriptive**: Clearly state what is being tested
- **Action-oriented**: Use "should" format
- **Specific**: Include relevant context

**Good:**

```typescript
it('should reject invalid resource after login with error redirect', async () => {
  // ...
});
```

**Bad:**

```typescript
it('test 1', async () => {
  // ...
});
```

### 10. Test Organization

Use `describe` blocks to group related tests:

```typescript
describe('Feature Name - Primary Functionality', () => {
  // Happy path tests
});

describe('Feature Name - Error Handling', () => {
  // Error case tests
});

describe('Feature Name - Edge Cases', () => {
  // Edge case tests
});
```

---

## Common Patterns

### Pattern 1: Basic Gateway Test

```typescript
// fixture.ts
export interface BasicGatewayFixture {
  domain: Domain;
  application: Application;
  user: User;
  openIdConfiguration: any;
  cleanup: () => Promise<void>;
}

export const setupBasicGatewayFixture = async (): Promise<BasicGatewayFixture> => {
  // Setup domain, app, user, OIDC config
  // Return fixture with cleanup
};

// spec.ts
let fixture: BasicGatewayFixture;

beforeAll(async () => {
  fixture = await setupBasicGatewayFixture();
});

afterAll(async () => {
  await fixture.cleanup();
});
```

### Pattern 2: Management CRUD Test

```typescript
// fixture.ts
export interface ResourceFixture {
  domain: Domain;
  accessToken: string;
  cleanup: () => Promise<void>;
}

export const setupResourceFixture = async (): Promise<ResourceFixture> => {
  // Setup domain and access token
  // Return fixture with cleanup
};

// spec.ts
let fixture: ResourceFixture;

beforeAll(async () => {
  fixture = await setupResourceFixture();
});

afterAll(async () => {
  await fixture.cleanup();
});
```

### Pattern 3: Test with Helper Methods

```typescript
// fixture.ts
export interface FeatureFixture {
  domain: Domain;
  application: Application;
  user: User;
  cleanup: () => Promise<void>;
  getAccessToken: () => Promise<string>;
  performOAuthFlow: () => Promise<string>;
}

export const setupFeatureFixture = async (): Promise<FeatureFixture> => {
  // ... setup code ...
  
  const getAccessToken = async (): Promise<string> => {
    // Helper implementation
  };
  
  const performOAuthFlow = async (): Promise<string> => {
    // Helper implementation
  };
  
  return {
    // ... resources ...
    getAccessToken,
    performOAuthFlow,
    cleanup,
  };
};
```

---

## Gateway vs Management Tests

### Gateway Tests

Gateway tests typically need:

- Domain with OIDC configuration
- Application with OAuth settings
- User for authentication
- OpenID configuration endpoint
- Helper methods for OAuth flows

**Example:** `specs/gateway/protected-resources/authorization-endpoint-resource-indicators.jest.spec.ts`

### Management Tests

Management tests typically need:

- Domain (may or may not be started)
- Access token
- Resources being tested (applications, users, etc.)
- Helper methods for CRUD operations

**Example:** `specs/management/applications.jest.spec.ts`

---

## Migration Guide

When migrating existing tests to use fixtures:

### Step 1: Identify Setup Code

Find all `beforeAll` setup code in the test file.

### Step 2: Create Fixture File

Create a new fixture file in `fixtures/` directory:

```
specs/gateway/feature-name/
  fixtures/
    feature-name-fixture.ts
```

### Step 3: Extract Setup to Fixture

Move setup code to fixture file:

- Create interface for fixture
- Create setup function
- Add cleanup function
- Extract helper functions if needed

### Step 4: Update Test File

Replace setup code with fixture usage:

```typescript
// Before
let domain, application, user;
beforeAll(async () => {
  domain = await createDomain(...);
  application = await createApplication(...);
  user = await createUser(...);
});

// After
let fixture: FeatureFixture;
beforeAll(async () => {
  fixture = await setupFeatureFixture();
});
```

### Step 5: Update Test References

Replace direct resource access with fixture properties:

```typescript
// Before
const result = await performOperation(domain.id, application.id);

// After
const result = await performOperation(fixture.domain.id, fixture.application.id);
```

### Step 6: Add Cleanup

Ensure cleanup is in `afterAll`:

```typescript
afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});
```

### Step 7: Test

Run tests to verify they still pass.

---

## Examples

### Reference Implementations

**Excellent Examples:**

1. **Gateway Test with Fixture:**
    - `specs/gateway/protected-resources/authorization-endpoint-resource-indicators.jest.spec.ts`
    - `specs/gateway/protected-resources/fixtures/protected-resources-fixture.ts`
1. **Gateway Test with Simple Fixture:**
    - `specs/gateway/fixtures/cors-fixture.ts`
    - `specs/gateway/cors.jest.spec.ts`
1. **Management Test:**
    - `specs/management/applications.jest.spec.ts`
    - `specs/management/domains.jest.spec.ts`
1. **Simple Gateway Test:**
    - `specs/gateway/refresh-token.jest.spec.ts`

### What Makes These Good?

1. **Clear Structure**: Well-organized with logical grouping
1. **Fixture Pattern**: Uses fixtures for setup/teardown
1. **Descriptive Names**: Test names clearly describe what's being tested
1. **Proper Cleanup**: All resources are cleaned up
1. **Unique Naming**: Uses `uniqueName()` for parallel execution
1. **Helper Functions**: Common operations extracted to helpers

---

## Checklist

Before submitting a test file, ensure:

### Structure

- [ ] Uses fixture pattern for setup/teardown
- [ ] Fixture includes cleanup function
- [ ] File is under 300 lines (or split appropriately)
- [ ] Test descriptions are clear and descriptive
- [ ] Tests are grouped logically with `describe` blocks

### Setup & Teardown

- [ ] Uses `uniqueName()` for all resource names
- [ ] Waits for domain sync when needed
- [ ] Error handling in fixture setup
- [ ] Cleanup in `afterAll` (even if domain deletion cascades)

### Code Quality

- [ ] TypeScript types are properly used
- [ ] Constants defined at top of fixture file
- [ ] Helper functions extracted to fixture/utility files
- [ ] No dynamic test generation (or documented exception)
- [ ] No conditional tests based on environment (jdbc/mongo)

### Maintainability

- [ ] Reusable fixtures for common patterns
- [ ] Consistent naming conventions
- [ ] Comments for complex logic
- [ ] Error handling in cleanup

### Testing

- [ ] Tests pass individually
- [ ] Tests can run in parallel
- [ ] Tests are independent
- [ ] No shared state between tests

---

## Quick Reference

### Essential Commands

```typescript
// Create domain with unique name
const domain = await createDomain(
  accessToken,
  uniqueName('feature-name', true),
  'Description'
);

// Start domain and wait for sync
const started = await startDomain(domain.id, accessToken);
const ready = await waitForDomainStart(started);
await waitForDomainSync(ready.domain.id, accessToken);

// Get OIDC config
const oidcResponse = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);
const oidcConfig = oidcResponse.body;

// Cleanup
await safeDeleteDomain(domain.id, accessToken);
```

### Common Imports

```typescript
// Core
import fetch from 'cross-fetch';
import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';

// Management Commands
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, startDomain, waitForDomainStart, waitForDomainSync } from '@management-commands/domain-management-commands';
import { createApplication } from '@management-commands/application-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';

// Gateway Commands
import { getWellKnownOpenIdConfiguration, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';

// Utils
import { uniqueName } from '@utils-commands/misc';
import { createTestApp } from '@utils-commands/application-commands';

// Models
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { User } from '@management-models/User';
```