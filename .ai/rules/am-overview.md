---
name: AM overview
layer: local
description: Tech stack and key paths beyond the shared AM rule
---

# AM overview

## Tech stack

| Layer | Technology |
|-------|------------|
| Backend | Java 25, Vert.x, Spring |
| Frontend | Angular and TypeScript (`gravitee-am-ui`); React and TypeScript (`gravitee-am-console`) |
| Build | Maven (backend), Yarn 4.1.1 (frontend) |
| Databases | MongoDB, PostgreSQL, MySQL, MariaDB, SQL Server |
| Migrations | Liquibase (JDBC), programmatic (MongoDB) |
| Tests | JUnit 5, Mockito, Testcontainers (backend); Jest (integration); Newman (contract); helm-unittest (charts) |
| Containers | Docker, Docker Compose |

## More key paths

| Path | Description |
|------|-------------|
| `helm/` | Helm charts and tests (`helm/tests/`) |
| `docs/agent-standards/` | Agent skills, templates and the command reference |
| `Makefile` | Docker/local development commands |

`docs/agent-standards/commands.md` is the canonical reference for build, test and validation commands.
