# Automation API Testing Guide

## Overview

These tests validate the AM Automation API endpoints for declarative, GitOps-style management of Environments, Domains, and Identity Providers.

## Prerequisites

1. **Local stack running** with automation API enabled:
   ```bash
   npm --prefix docker/local-stack run stack:dev:mongo
   ```

2. **Automation API enabled** in `gravitee.yml`:
   ```yaml
   http:
     api:
       automation:
         enabled: true
   ```

3. **Test dependencies installed**:
   ```bash
   cd gravitee-am-test && npm install
   ```

## Running the Tests

```bash
# Run all automation tests
npm --prefix gravitee-am-test run ci:management:parallel -- --testPathPattern=automation

# Run a specific test file
npx jest --config=api/config/ci.config.js specs/management/automation/environments.jest.spec.ts
npx jest --config=api/config/ci.config.js specs/management/automation/domains.jest.spec.ts
npx jest --config=api/config/ci.config.js specs/management/automation/identity-providers.jest.spec.ts
```

## API Base URL

The Automation API is served at:
```
http://localhost:8093/management/automation
```

Authentication uses the same admin Bearer token as the Management API (obtained via `/management/auth/token`).

## Endpoints Under Test

| Method | Path | Description |
|--------|------|-------------|
| GET | `/organizations/{orgId}/environments` | List environments |
| GET | `/organizations/{orgId}/environments/{envId}` | Get environment |
| PUT | `/organizations/{orgId}/environments/{envId}` | Create/update environment |
| GET | `.../environments/{envId}/domains` | List domains |
| GET | `.../domains/{domainHrid}` | Get domain by HRID |
| PUT | `.../domains/{domainHrid}` | Create/update domain |
| DELETE | `.../domains/{domainHrid}` | Delete domain |
| GET | `.../domains/{domainHrid}/identity-providers` | List IDPs |
| GET | `.../identity-providers/{idpId}` | Get IDP |
| PUT | `.../identity-providers/{idpId}` | Create/update IDP |
| DELETE | `.../identity-providers/{idpId}` | Delete IDP |

## Test Structure

Each test file follows the project fixture pattern:
- `beforeAll`: Acquire admin token, set up resources
- `afterAll`: Clean up created resources
- Tests use `uniqueName()` for parallel safety
- Idempotent PUT is verified by calling it twice and checking the result is the same
