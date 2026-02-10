# AGENTS.md — Gravitee Access Management

Universal AI agent instructions for Gravitee Access Management (AM).
Consumed by: OpenAI Codex, GitHub Copilot, Cursor, Gemini/Jules, Windsurf, Zed, Aider, and others.

---

# Agent Guide

If you are an AI agent operating in this repository:
- You MUST read this file fully before making changes.
- You MUST follow the rules defined here.
- If any instruction conflicts with other files, this file takes precedence.

## 1. Quick Reference Commands

### Prerequisites

- Java 21+, Maven 3.6+, Node.js >= 20.11.1, Yarn 4.1.1, Docker & Docker Compose

### Backend (Java / Maven)

```bash
# Full build (with tests)
mvn clean install

# Build without tests
mvn install -DskipTests

# Build excluding UI module
mvn install -pl '!gravitee-am-ui'

# Clean (excluding UI)
mvn clean -pl '!gravitee-am-ui'
```

### Frontend (Angular / TypeScript)

All commands run from `gravitee-am-ui/`.

```bash
# Install dependencies
yarn install

# Dev server
yarn serve                # or from root: make startUi

# Lint (all: ESLint, Stylelint, license, Prettier)
yarn lint
yarn lint:fix             # auto-fix

# Individual linters
yarn lint:eslint
yarn lint:styles
yarn lint:license
yarn prettier

# Build
yarn build                # development
yarn prod                 # production
```

### API / Contracts

```bash
# OpenAPI spec location
docs/mapi/openapi.yaml

# Validate OpenAPI spec
cd gravitee-am-test && npm install
npx @openapitools/openapi-generator-cli validate -i ../docs/mapi/openapi.yaml

# Regenerate Management API SDK (TypeScript)
cd gravitee-am-test && npm run update:sdk:mapi -- <MANAGEMENT_API_URL>
```

### Build Local Stack

```bash
# Build AM
mvn clean install

# Copy AM to local stack
npm --prefix docker/local-stack run stack:init:copy-am

# Build the docker images
npm --prefix docker/local-stack run stack:init:build

# Start local stack (MongoDB)
npm --prefix docker/local-stack run stack:dev:setup:mongo

# Tear down local stack
npm --prefix docker/local-stack run stack:down
```

### Running Tests

See **Section 8** for full test documentation (all test types, commands, and CI pipeline details).

```bash
# Pre-PR verification
mvn clean install
cd gravitee-am-ui && yarn lint && yarn test

# Quick backend iteration
mvn clean install -pl gravitee-am-service -DskipTests
mvn test -pl gravitee-am-service

# Quick frontend iteration
cd gravitee-am-ui && yarn lint:fix && yarn test
```

---

## 2. Project Context

### What Is Gravitee AM?

Gravitee Access Management is an identity and access management (IAM) platform supporting OAuth 2.0, OpenID Connect, SAML, and MFA. It provides a Management API, a Gateway, and a web UI for managing security domains, applications, identity providers, and policies.

### Tech Stack

| Layer | Technology                                                    |
|-------|---------------------------------------------------------------|
| Backend | Java 21, RxJava 3, Vert.x, Spring                             |
| Frontend | Angular, TypeScript                                           |
| Build | Maven (backend), Yarn 4.1.1 (frontend)                        |
| Databases | MongoDB, PostgreSQL, MySQL, MariaDB, SQL Server               |
| Migrations | Liquibase (JDBC), programmatic (MongoDB)                      |
| API spec | OpenAPI 3 (`docs/mapi/openapi.yaml`)                          |
| Tests | JUnit 5, Mockito, Testcontainers (backend); Jest (integration); Newman (contract); helm-unittest (charts) |
| Containers | Docker, Docker Compose                                        |

### Key Paths

| Path | Description |
|------|-------------|
| `gravitee-am-management-api/` | Management API module |
| `gravitee-am-gateway/` | Gateway module |
| `gravitee-am-service/` | Core business logic |
| `gravitee-am-model/` | Domain model |
| `gravitee-am-repository/` | Repository implementations |
| `gravitee-am-dataplane/` | Reactive data access layer (MongoDB/JDBC) |
| `gravitee-am-identityprovider/` | Identity provider plugins |
| `gravitee-am-factor/` | MFA factor plugins |
| `gravitee-am-ui/` | Angular frontend |
| `gravitee-am-test/` | Integration tests (Jest) |
| `gravitee-am-test/GUIDELINES.md` | Integration test guidelines |
| `postman/collections/` | Postman/Newman API contract tests |
| `helm/` | Helm charts and tests (`helm/tests/`) |
| `docs/mapi/openapi.yaml` | OpenAPI 3 spec for Management API |
| `docs/agent-standards/` | Agent rules, skills, and templates |
| `Makefile` | Docker/local development commands |

### Conventions

- Use **US English** for comments, logs, and user-facing text.
- **Search for similar implementations first** before writing new code.
- Keep changes small and reviewable; avoid unrelated refactors or reformatting.
- Prefer existing patterns over inventing new ones.
- Each new Java/Typescript file should contain the license header

---

## 3. Workflow

### Plan First

- Produce a short plan (goal, steps, files, tests, validation criteria) before implementing.
- **Stop after the plan** unless the user explicitly says "continue".
- For non-trivial tasks (behaviour/API/data changes, unclear requirements, or 3+ files), use structured plan headings.

### Ambiguity Handling

- If requirements are ambiguous or under-specified: **ask clarifying questions** and propose **2–3 options** with a recommended approach.

### Execution Discipline

- **Cite key files inspected** (code + docs) that informed changes.
- Prefer safe/dry-run/diff modes first.
- Freely create, modify, or update code files as part of normal implementation.
- **Ask before deleting any files** (code or documentation).
- **Do not execute commands that modify state, data, or external systems without explicit approval.**
- Safe commands: `git status`, `git diff`, `git log`, read-only searches, typecheck/lint (read-only).
- Always ask before: `git commit`, `git push`, dependency installs, formatter auto-fixes, SDK regeneration, migrations.

### Documentation Discipline

- Keep single source of truth: update existing docs; **do not create new documentation files** (*.md, README) unless explicitly asked.
- JavaDoc and code documentation are always encouraged. 
- Inline comments should only be used if the existing logic is not self-explanatory.
- If recurring caveats are discovered: call them out and propose whether to update existing docs.

---

## 4. Safety and Security

### Must Not

- Invent endpoints, config keys, behaviours, or workflows not present in the codebase.
- Log secrets, tokens, PII, or full request/response bodies; prefer structured logs (`key=value`).
- Run destructive commands (`docker system prune -a`, `git reset --hard`) unless explicitly requested and confirmed.

### Compatibility and Correctness

- Keep public contracts backwards compatible unless a breaking change is explicitly authorized.
- Do not change exception types or HTTP status/exception semantics for the same condition without approval.
- Do not widen method/class visibility without a documented reason.

### Standards Compliance (IAM / OAuth / OIDC)

- Prefer RFC/standard compliance and established implementations over custom logic.
- On validation, authorization, or security uncertainty: **fail closed** rather than allowing access.

---

## 5. Backend Architecture

### Layering

- Respect layer order: **Repository → Service → Resource**.
- **Resources** handle HTTP + permissions; **Services** handle business logic.
- NEVER use blocking operations on request paths; **do not use `blockingGet()`** on reactive chains.

### RxJava Usage

- Use `Single`/`Maybe` for I/O operations and `Completable` for side-effect operations.
- Preserve error intent: expected 4xx must not become generic 5xx.

### Audit Logging (Mandatory for C/U/D)

- Implement in core services/service proxies, **not** in Resources.
- Resources must **pass the authenticated User principal** into service methods that require audit logging.
- Use a dedicated `AuditBuilder` per entity type.
- READ operations do not require audit logging unless explicitly specified.

---

## 6. Data and Migrations

### Repository Patterns

- Repository interfaces extend `CrudRepository<T, String>` unless established patterns differ.
- Prefer domain-scoped queries (e.g., `findByDomainAndId`) over `findById` + manual filtering.
- Do not fetch wide data and filter in memory when a scoped query is appropriate.
- Follow existing repository patterns and naming conventions.

### Repository Implementation Naming (Mandatory)

- **MongoDB:** `Mongo{RepositoryName}Repository`
- **JDBC:** `Jdbc{RepositoryName}Repository`
- Verify naming matches existing repos before finalising.

### DB Migrations (Liquibase)

- Related schema changes **must be grouped** into a single changeset (e.g., table + indexes + constraints).
- Check existing migrations for patterns before creating new ones.

### MongoDB Index Naming (Mandatory)

- Index names **must follow** the established initials+sort-order convention for fields.

### Reference

- Migration checklist: `docs/agent-standards/templates/migration_checklist.template.md`

---

## 7. Frontend (Angular)

### Structure

- Place new files by matching existing feature/module structure; check similar features first.
- Follow existing project structure and patterns; do not introduce new conventions lightly.
- Prefer explicit inputs/outputs and clear component boundaries.

### Quality

- **Fix linting issues promptly**; do not leave the repo in a lint-broken state.
- Avoid brittle selectors in UI tests; prefer stable test identifiers where needed.

---

## 8. Testing and Validation

### Testing Approach

- For backend changes, **prefer TDD**: write tests first, then implement.
- Implement bottom-up by dependency: **Repository → Service → Resource**.
- Cover success **and error paths** when behaviour changes.
- CREATE/UPDATE/DELETE tests **must verify audit logging**.

### Test Types Overview

| Test Type | Framework | Location | Scope | CI Job |
|-----------|-----------|----------|-------|--------|
| **Unit** | JUnit 5 / Mockito | `*/src/test/java/` | Per-module logic | `junit_tests` |
| **Repository** | JUnit + Testcontainers | `gravitee-am-repository/gravitee-am-repository-tests/src/test/java/` | Data layer against real DBs | `run_testcontainer_tests_*` |
| **Jest Management** | Jest / TypeScript | `gravitee-am-test/specs/management/` | Management API endpoints | `run_jest_tests_*` |
| **Jest Gateway** | Jest / TypeScript | `gravitee-am-test/specs/gateway/` | OAuth2/OIDC protocol flows | `run_jest_tests_*` |
| **Postman** | Newman | `postman/collections/` | API contract testing | `run_postman_tests_*` |
| **UI** | Jest / Angular | `gravitee-am-ui/src/**/*.spec.ts` | Angular component/service tests | `ui_tests` |
| **Helm** | helm-unittest | `helm/tests/` | Kubernetes chart rendering | `job-test-am-charts` |

### Running Tests Locally

#### Unit Tests (JUnit)

```bash
mvn test                                       # All modules
mvn test -pl gravitee-am-service               # Specific module
mvn test -Dtest="TestClassName" -pl module-name # Specific class
mvn test -Dtest="TestClass#method" -pl module   # Specific method
```

#### Repository Tests (Testcontainers)

Require Docker running. These test the data layer against real database instances.

The `cicd*` profiles are defined in individual module POMs (e.g., `gravitee-am-repository-mongodb`, `gravitee-am-repository-jdbc`) and toggle `skipTests` from `true` to `false`. You must build the **whole project** (not a specific `-pl` module) so Maven activates the profiles in the correct child modules.

```bash
# MongoDB
mvn install -pl \!gravitee-am-ui -Pcicd -DskipTests

# PostgreSQL
mvn install -pl \!gravitee-am-ui -Pcicd-jdbc -Ppostgres -DskipTests

# MySQL / MariaDB / MSSQL
mvn install -pl \!gravitee-am-ui -Pcicd-jdbc -Pmysql -DskipTests
mvn install -pl \!gravitee-am-ui -Pcicd-jdbc -Pmariadb -DskipTests
mvn install -pl \!gravitee-am-ui -Pcicd-jdbc -Pmssql -DskipTests

# Older DB versions (append -min)
mvn install -pl \!gravitee-am-ui -Pcicd-jdbc -Ppostgres-min -DskipTests

# Redis
mvn install -pl \!gravitee-am-ui -Pcicd-redis -DskipTests
```

**Note:** `-DskipTests` is passed globally but the `cicd*` profiles override it to `false` in the relevant modules, so only those repository tests run.

**Maven profiles:** `cicd` (Mongo), `cicd-jdbc` + DB profile (JDBC), `cicd-redis` (Redis). Profiles are defined in:
- `gravitee-am-repository/gravitee-am-repository-mongodb/pom.xml`
- `gravitee-am-repository/gravitee-am-repository-jdbc/pom.xml`
- `gravitee-am-repository/gravitee-am-repository-redis/pom.xml`
- `gravitee-am-dataplane/gravitee-am-dataplane-mongodb/pom.xml`
- `gravitee-am-dataplane/gravitee-am-dataplane-jdbc/pom.xml`

#### Jest Integration Tests

Require a running local stack (see section 1).

```bash
# All tests
npm --prefix gravitee-am-test run ci

# Management tests (2 parallel workers)
npm --prefix gravitee-am-test run ci:management:parallel

# Gateway tests (sequential — order-sensitive)
npm --prefix gravitee-am-test run ci:gateway

# Single test file
npm --prefix gravitee-am-test run test -- specs/gateway/path/to/test.spec.ts

# Single test by name
npm --prefix gravitee-am-test run test -- specs/gateway/path/to/test.spec.ts -t "test name"

# With PostgreSQL backend
REPOSITORY_TYPE=jdbc npm --prefix gravitee-am-test run ci:management:parallel
```

Follow `gravitee-am-test/GUIDELINES.md` for all integration test work.

#### Postman / Newman Tests

```bash
npm --prefix postman run test:collections
```

#### UI Tests

```bash
cd gravitee-am-ui && yarn test
# Or via Maven:
mvn test -pl gravitee-am-ui -Dlicense.skip=true
```

#### Helm Tests
Refer to [helm/README.md](helm/README.md) for more details on testing Helm charts.

```bash
helm lint helm/
helm unittest -f 'tests/**/*.yaml' helm/
```

### CI Pipeline (CircleCI)

The PR workflow (`pull_requests`) runs the following test jobs:

1. **`junit_tests`** — Backend unit tests (`mvn verify -pl \!gravitee-am-ui`)
2. **`ui_tests`** — Angular tests (`mvn test -pl gravitee-am-ui`)
3. **`run_jest_tests_mongo`** — Jest management (parallel) + gateway tests against MongoDB 4.4 and 8.0
4. **`run_jest_tests_psql`** — Jest management (parallel) + gateway tests against PostgreSQL 11.18 and 17.2
5. **`run_postman_tests_mongo`** — Newman collections against MongoDB 4.4 and 8.0
6. **`run_postman_tests_psql`** — Newman collections against PostgreSQL 11.18 and 17.2
7. **`job-test-am-charts`** — Helm lint + unittest

**Repository testcontainer tests** run in a separate `testcontainer_tests` workflow, triggered when files under `gravitee-am-repository/`, `gravitee-am-reporter/`, `gravitee-am-dataplane/`, or `pom.xml` are modified. This tests against a matrix of MongoDB, Redis, PostgreSQL, MySQL, MariaDB, and MSSQL (current + minimum supported versions).

### Step-by-Step Validation (Mandatory)

After each logical step:

1. **Validate** — run tests/build.
2. **Review** — check for tidiness.
3. **Remove** — delete temporary code.
4. **Fix** — resolve linting issues.
5. **Keep repo lint/test clean** at all times.

### Test Quality

- No ambiguous assertions; make assertions **specific and meaningful**.
- Remove redundant checks once a value is already asserted.

### Completion Checklist

Before marking work complete:

- [ ] Tests pass
- [ ] Lint is clean
- [ ] Patterns are consistent with existing code
- [ ] Docs updated if needed
- [ ] If API layer changed → follow OpenAPI/SDK rules (section 9)

---

## 9. API Contracts and OpenAPI

When API behaviour, endpoints, DTOs, or annotations change:

1. Update the OpenAPI spec (`docs/mapi/openapi.yaml`).
2. Regenerate the SDK/clients as required.
3. Add or update contract/API tests covering the change (success + error paths).

### Reference

- OpenAPI change checklist: `docs/agent-standards/templates/openapi_change_checklist.template.md`

---

## 10. Cross-Agent Consistency

- When multiple agents or iterations work on the same area, converge on a **single agreed approach**.
- Do not introduce competing patterns or parallel abstractions.
- Search for existing implementations before creating new ones.

