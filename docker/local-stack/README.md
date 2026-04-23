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
```

### MongoDB

```bash
npm --prefix docker/local-stack run stack:dev:mongo
```

### PostgreSQL

```bash
npm --prefix docker/local-stack run stack:dev:psql
```

## Optional Stacks

- **[Kerberos SPNEGO Lab](dev/kerberos/KERBEROS-LAB-HOWTO.md)** — KDC container for testing SPNEGO authentication

## Stopping and Cleaning Up

```bash
npm --prefix docker/local-stack run stack:down
```
