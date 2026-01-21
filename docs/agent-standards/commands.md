# commands.md (canonical commands)

This file lists the canonical, **verified** commands for building, testing, linting, and generating artefacts for Gravitee Access Management.

## Prerequisites
- Java 17 or higher
- Maven 3.6+
- Node.js >= 20.11.1
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
mvn test -Dtest="TestClassName"
```

### Integration tests
```bash
# Run integration tests
mvn verify

# Run integration tests for specific module
mvn verify -pl gravitee-am-repository
```

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
```

### Build
```bash
cd gravitee-am-ui

# Development build
yarn build

# Production build
yarn prod
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
# Management API SDK (TypeScript)
cd gravitee-am-test
npm run update:sdk:mapi -- <MANAGEMENT_API_URL>
```

### Contract/API tests
```bash
# Run all Postman collections via Makefile
make postman
```

## Local development with Docker

### Full local environment
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
- `gravitee-am-test/package.json` (Test scripts)
- `postman/package.json` (Newman/Postman tests)
- `gravitee-am-test/openapitools.json` (OpenAPI generator)

## Owner & update policy
- Owner(s): Gravitee AM team
- Update when tooling, scripts, or mandatory checks change
- Keep in sync with CI/CD pipeline requirements
- Only include commands that have been verified to exist and work
