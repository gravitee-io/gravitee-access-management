# commands.md (canonical commands)

This file lists the canonical, **verified** commands for building, testing, linting, and generating artefacts for Gravitee Access Management.

## Prerequisites
- Java 25 or higher
- Maven 3.6+
- Node.js >= 20.11.1 (`gravitee-am-console` needs Node 24, see its `.nvmrc`)
- Yarn 4.1.1 (packageManager)
- Docker & Docker Compose (for integration tests and local development)

## Quick start
```bash
# Install backend dependencies (Maven handles this automatically)
mvn clean install

# Install frontend dependencies
cd gravitee-am-ui && yarn install
```

## Backend (Java/Maven)

### Build
```bash
# Full build (includes tests)
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Build excluding UI module
mvn clean install -pl '!gravitee-am-ui'
```

### Unit tests
```bash
# Run all unit tests
mvn test

# Run tests for specific module
mvn test -pl gravitee-am-service

# Run specific test class
mvn test -Dtest="TestClassName" -pl module-name

# Run specific test method
mvn test -Dtest="TestClass#method" -pl module
```

### Repository tests (Testcontainers)

Require Docker running. These test the data layer against real database instances.

The `cicd*` profiles are defined in individual module POMs (e.g., `gravitee-am-repository-mongodb`, `gravitee-am-repository-jdbc`) and toggle `skipTests` from `true` to `false`. You must build the **whole project** (not a specific `-pl` module) so Maven activates the profiles in the correct child modules.

```bash
# MongoDB
mvn install -pl '!gravitee-am-ui' -Pcicd -DskipTests

# PostgreSQL
mvn install -pl '!gravitee-am-ui' -Pcicd-jdbc -Ppostgres -DskipTests

# MySQL / MariaDB / MSSQL
mvn install -pl '!gravitee-am-ui' -Pcicd-jdbc -Pmysql -DskipTests
mvn install -pl '!gravitee-am-ui' -Pcicd-jdbc -Pmariadb -DskipTests
mvn install -pl '!gravitee-am-ui' -Pcicd-jdbc -Pmssql -DskipTests

# Older DB versions (append -min)
mvn install -pl '!gravitee-am-ui' -Pcicd-jdbc -Ppostgres-min -DskipTests

# Redis
mvn install -pl '!gravitee-am-ui' -Pcicd-redis -DskipTests
```

**Note:** `-DskipTests` is passed globally but the `cicd*` profiles override it to `false` in the relevant modules, so only those repository tests run.

**Maven profiles:** `cicd` (Mongo), `cicd-jdbc` + DB profile (JDBC), `cicd-redis` (Redis). Profiles are defined in:
- `gravitee-am-repository/gravitee-am-repository-mongodb/pom.xml`
- `gravitee-am-repository/gravitee-am-repository-jdbc/pom.xml`
- `gravitee-am-repository/gravitee-am-repository-redis/pom.xml`
- `gravitee-am-dataplane/gravitee-am-dataplane-mongodb/pom.xml`
- `gravitee-am-dataplane/gravitee-am-dataplane-jdbc/pom.xml`

### Clean
```bash
# Clean build artefacts (excluding UI)
mvn clean -pl '!gravitee-am-ui'

# Or using Makefile
make clean
```

## Frontend (Angular/TypeScript)

Location: `gravitee-am-ui/`

### Install dependencies
```bash
cd gravitee-am-ui
yarn install
```

### Dev server
```bash
# From UI directory
cd gravitee-am-ui
yarn serve

# Or from project root using Makefile
make startUi
```

### Lint
```bash
cd gravitee-am-ui

# Run all linters (ESLint, Stylelint, license check, Prettier)
yarn lint

# Fix all linting issues
yarn lint:fix

# Individual linters
yarn lint:eslint          # ESLint only
yarn lint:styles          # Stylelint only
yarn lint:license         # License headers
yarn prettier             # Prettier check
```

### Unit tests
```bash
cd gravitee-am-ui
yarn test

# Or via Maven
mvn test -pl gravitee-am-ui -Dlicense.skip=true
```

### Build
```bash
cd gravitee-am-ui

# Development build
yarn build

# Production build
yarn prod
```

## Console (React)

Location: `gravitee-am-console/`. Not a Maven module: `mvn install` from the root does not build it. Run every command from that folder; Node 24 (`.nvmrc`), Yarn 4.1.1 from `.yarn/releases/`. The rules for working in it are in `gravitee-am-console/AGENTS.md`.

```bash
cd gravitee-am-console
yarn install
yarn serve        # dev server on :4201, proxies /management to AM_MAPI_URL (default :8093)
yarn typecheck
yarn lint         # typecheck, ESLint with the Graphene config, Prettier check
yarn test         # Vitest
yarn build        # typecheck, then rsbuild production build into dist/

# Before a commit, all four must pass
yarn typecheck && yarn lint && yarn test && yarn build
```

## API / Contracts

### OpenAPI spec location
```
docs/mapi/openapi.yaml
```

### Validate OpenAPI
```bash
# First time: install OpenAPI tools
cd gravitee-am-test
npm install

# Validate spec
npx @openapitools/openapi-generator-cli validate -i ../docs/mapi/openapi.yaml
```

### Regenerate SDK/clients
```bash
# Management API SDK (TypeScript), from docs/mapi/openapi.yaml (needs `npm ci` in gravitee-am-test)
bash scripts/regen-oas.sh && bash scripts/regen-sdk.sh
```

### Contract/API tests
```bash
# Run all Postman collections via Makefile
make postman

# Or directly
npm --prefix postman run test:collections
```

## Integration, migration and chart tests

### Integration tests (Jest)

Require a running local stack: start it with `docker/local-stack/local-stack.sh up` (use `--full` for the gateway protocol specs, `--db psql` for the JDBC backend). See "Local development with Docker" below and the `local-stack` skill. Follow `gravitee-am-test/GUIDELINES.md` for all integration test work.

```bash
# All tests
npm --prefix gravitee-am-test run ci

# Management tests (2 parallel workers)
npm --prefix gravitee-am-test run ci:management:parallel

# Gateway tests (sequential, order-sensitive)
npm --prefix gravitee-am-test run ci:gateway

# Single test file
npm --prefix gravitee-am-test run test -- specs/gateway/path/to/test.spec.ts

# Single test by name
npm --prefix gravitee-am-test run test -- specs/gateway/path/to/test.spec.ts -t "test name"

# With PostgreSQL backend
REPOSITORY_TYPE=jdbc npm --prefix gravitee-am-test run ci:management:parallel
```

### Migration tests

Seed data with one AM version, upgrade/downgrade the Management API and gateways, and verify it survived. Driven by `scripts/migration-test.mjs` (see `scripts/migration-tool/README.md`). Follow `gravitee-am-test/MIGRATION_TEST_GUIDELINES.md` for any change under `gravitee-am-test/migration-seeding/` or `gravitee-am-test/specs/migration/`.

### Helm tests

Refer to [helm/README.md](../../helm/README.md) for more details on testing Helm charts.

```bash
helm lint helm/
helm unittest -f 'tests/**/*.yaml' helm/
```

## Test types

| Test Type | Framework | Location | Scope | CI Job |
|-----------|-----------|----------|-------|--------|
| **Unit** | JUnit 5 / Mockito | `*/src/test/java/` | Per-module logic | `junit_tests` |
| **Repository** | JUnit + Testcontainers | `gravitee-am-repository/gravitee-am-repository-tests/src/test/java/` | Data layer against real DBs | `run_testcontainer_tests_*` |
| **Jest Management** | Jest / TypeScript | `gravitee-am-test/specs/management/` | Management API endpoints | `run_jest_tests_*` |
| **Jest Gateway** | Jest / TypeScript | `gravitee-am-test/specs/gateway/` | OAuth2/OIDC protocol flows | `run_jest_tests_*` |
| **Migration** | Jest / TypeScript | `gravitee-am-test/migration-seeding/`, `gravitee-am-test/specs/migration/` | Data and behaviour across AM upgrades/downgrades | migration workflow (`workflows-migration.yml`) |
| **Playwright** | Playwright | `gravitee-am-test/playwright/` | Console UI E2E flows | `run_playwright_tests_*` |
| **Postman** | Newman | `postman/collections/` | API contract testing | `run_postman_tests_*` |
| **UI** | Jest / Angular | `gravitee-am-ui/src/**/*.spec.ts` | Angular component/service tests | `ui_tests` |
| **Helm** | helm-unittest | `helm/tests/` | Kubernetes chart rendering | `job-test-am-charts` |

## CI pipeline (CircleCI)

The PR workflow (`pull_requests`) runs the following test jobs:

1. **`junit_tests`**: backend unit tests (`mvn verify -pl '!gravitee-am-ui'`)
2. **`ui_tests`**: Angular tests (`mvn test -pl gravitee-am-ui`)
3. **`run_jest_tests_mongo`**: Jest management (parallel) + gateway tests against MongoDB 9.0
4. **`run_jest_tests_psql`**: Jest management (parallel) + gateway tests against PostgreSQL 18.4
5. **`run_postman_tests_mongo`**: Newman collections against MongoDB 9.0
6. **`run_postman_tests_psql`**: Newman collections against PostgreSQL 18.4
7. **`run_playwright_tests_mongo`**: Playwright E2E tests against MongoDB 9.0
8. **`run_playwright_tests_psql`**: Playwright E2E tests against PostgreSQL 18.4
9. **`job-test-am-charts`**: Helm lint + unittest

**Repository testcontainer tests** run in a separate `testcontainer_tests` workflow, triggered when files under `gravitee-am-repository/`, `gravitee-am-reporter/`, `gravitee-am-dataplane/`, or `pom.xml` are modified. This tests against a matrix of MongoDB, Redis, PostgreSQL, MySQL, MariaDB, and MSSQL (current + minimum supported versions).

## Local development with Docker

### Local stack (recommended) — `docker/local-stack/local-stack.sh`

One command builds (or pulls) AM and its dependencies for jest/playwright tests and manual
API/Console work. See `docker/local-stack/README.md` and the `local-stack` skill.

```bash
cd docker/local-stack

# Build from the CURRENT code state and start (lean: gateway, management, mongo, smtp, wiremock)
./local-stack.sh up

# Everything the e2e/jest suites need + Console UI
./local-stack.sh up --full

# PostgreSQL backend + Console UI
./local-stack.sh up --db psql --ui

# Start a specific RELEASED/nightly version instead of building (no Maven)
./local-stack.sh up --version 4.12.x-latest --ui

# Status / logs / teardown
./local-stack.sh status
./local-stack.sh logs gateway
./local-stack.sh down
```

URLs once up: Gateway `:8092`, Management API `:8093/management`, Console UI `:4200`
(with `--ui`/`--full`), Mailbox `:5080`. Admin login `admin` / `adminadmin` (org/env `DEFAULT`).

### Full local environment (Makefile alternative)
```bash
# First time: install, build, and start
make gravitee

# Or step by step
make install              # Build and package
make run                  # Build Docker images and start containers
```

### Start/Stop
```bash
make start                # Start existing containers
make stop                 # Stop running containers
make status               # Check container status
```

### Reset environment
```bash
make reset                # Stop, delete data, and restart
```

### Database options
```bash
# Start specific database containers
make startMongo
make startPostgres
make startPostgres13
make startMySQL
make startMySQL8
make startMariaDB
make startMariaDB_10_5
make startSQLServer
make startSQLServer_2019

# Stop databases
make stopDatabase
```

## Common workflows

### Pre-PR verification (local)
```bash
# Backend: build and test
mvn clean install

# Frontend: lint and test
cd gravitee-am-ui && yarn lint && yarn test
```

### CI-equivalent verification
```bash
# Full build with all tests
mvn clean install

# Postman API tests
make postman
```

### Quick iteration (backend only)
```bash
# Build specific module without tests
mvn clean install -pl gravitee-am-service -DskipTests

# Run tests for that module
mvn test -pl gravitee-am-service
```

### Quick iteration (frontend only)
```bash
cd gravitee-am-ui

# Fix lint issues and test
yarn lint:fix && yarn test
```

## Validation notes

All commands in this file have been verified against:
- `pom.xml` (Maven projects)
- `Makefile` (Docker/local development)
- `gravitee-am-ui/package.json` (Frontend scripts)
- `gravitee-am-console/package.json` (Console scripts)
- `gravitee-am-test/package.json` (Test scripts)
- `postman/package.json` (Newman/Postman tests)
- `gravitee-am-test/openapitools.json` (OpenAPI generator)

## Owner & update policy
- Owner(s): Gravitee AM team
- Update when tooling, scripts, or mandatory checks change
- Keep in sync with CI/CD pipeline requirements
- Only include commands that have been verified to exist and work
