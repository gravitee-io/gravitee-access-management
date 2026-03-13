# Local Development Stack

Docker Compose-based local environment for Gravitee Access Management.

## Prerequisites

- Docker Desktop running
- Enterprise license at `dev/license/gravitee-universe-v4.key`
- AM built from source:
  ```bash
  cd gravitee-access-management
  mvn clean install -pl '!gravitee-am-ui' -DskipTests
  make plugins
  ```

## Quick Start

### Initialize (first time or after rebuild)

```bash
npm --prefix docker/local-stack run stack:init:copy-am
npm --prefix docker/local-stack run stack:init:build
```

### MongoDB

```bash
npm --prefix docker/local-stack run stack:dev:setup:mongo
```

### PostgreSQL

```bash
npm --prefix docker/local-stack run stack:init:plugins:psql
npm --prefix docker/local-stack run stack:dev:setup:psql
```

## Optional Stacks

- **[Kerberos SPNEGO Lab](dev/kerberos/KERBEROS-LAB-HOWTO.md)** — KDC container for testing SPNEGO authentication

## Stopping and Cleaning Up

```bash
npm --prefix docker/local-stack run stack:down
```
