# AM Migration Test Tool

A modular, TDD-refactored tool for orchestrating Gravitee AM migration tests against local Kubernetes (Kind) or Docker Compose environments.

## Prerequisite (K8s)
- `kind` cluster running.
- `kubectl` and `helm` installed.
- Valid Gravitee license in your environment (or uses a default mock for tests).

## Commands

### 🛠️ One-Time Setup
Prepares the environment, installs MongoDB, and deploys the initial "from" version of AM.
```bash
./scripts/migration-test.mjs --provider k8s setup
```

### 🚀 Run Full Migration
Runs the entire orchestration flow (Clean -> Setup -> Deploy -> Verify -> Upgrade -> Verify).
```bash
./scripts/migration-test.mjs --provider k8s run
```

### 🎯 Run Specific Stage
Helpful for debugging specific parts of the migration.
```bash
./scripts/migration-test.mjs --provider k8s run --stage upgrade-mapi
```

## Options
- `--provider`: `k8s` (Kind) or `docker-compose` (default).
- `--from-tag`: The version to migrate from (default: `4.10.0`).
- `--to-tag`: The version to migrate to (default: `latest`).
- `--db-type`: `mongodb` (default) or `postgres`.
- `--test-filter`: Regex to filter Jest tests in `gravitee-am-test`.

## PostgreSQL (K8s)

- **Minimal internal chart**: The tool uses a local Helm chart (`env/k8s/db/postgres`) with the **official postgres:16.6** image (no Bitnami chart), so Liquibase’s `md5()` works and image/tag churn is avoided.
- **Two databases**: Same Postgres instance, two DBs: `gravitee-am` (Management API repos) and `gravitee-am-gateway` (Gateway dataplane). The second DB is created by an init script in `/docker-entrypoint-initdb.d` when the data dir is empty.
- **Two dataplanes**: `am-postgres.yaml` defines dataplane `default` → `gravitee-am`, dataplane `gateway` → `gravitee-am-gateway`. The Gateway uses `gravitee_repositories_gateway_dataPlane_id=gateway` so it has its own database.
- **Run from repo root** so the Postgres values path and chart path resolve when Helm is invoked.
- Postgres is deployed as a **StatefulSet**. Verify: `kubectl get statefulset,pods,svc -n gravitee-am | grep postgres`. Test md5 (use `-h 127.0.0.1` for TCP; socket may be unavailable in container): `kubectl -n gravitee-am exec -it postgres-postgresql-0 -- psql -h 127.0.0.1 -U gravitee -d gravitee-am -c "select md5('test');"`.
- **Chart-native config**: The official Gravitee AM chart (`graviteeio/am`) injects repository config into **gravitee.yml** (ConfigMap) from values `management.type`, `oauth2.type`, `gateway.type` and `jdbc.*` (host, port, database, username, password, drivers). Do not rely on env vars for repository config; use chart values only. See `helm/templates/common/_configmap-default-repositories.tpl` and `_configmap-default-database.tpl`.
- **PostgreSQL JDBC drivers**: Set `jdbc.drivers` to Maven Central URLs for the blocking JDBC driver (`org.postgresql:postgresql`) and R2DBC (`org.postgresql:r2dbc-postgresql`). The chart’s init container `get-jdbc-ext` downloads them; main containers mount at `plugins/ext/repository-am-jdbc` (and dataplane-am-jdbc, reporter-am-jdbc).
- **Postgres host**: Same namespace → `postgres-postgresql`. Cross-namespace → FQDN `postgres-postgresql.<namespace>.svc.cluster.local`. Verify: `kubectl -n gravitee-am get svc | grep postgres` and `kubectl -n gravitee-am get endpoints postgres-postgresql`.
- **Verification after deploy**: Pods healthy: `kubectl -n gravitee-am get pods`. Logs show JDBC/Postgres: `kubectl -n gravitee-am logs deploy/am-management-api | grep -iE 'jdbc|postgres|r2dbc|repository'`. No connection errors: `kubectl -n gravitee-am logs deploy/am-management-api | grep -iE 'refused|timeout|password|authentication|connect'`.
- **Check JDBC JARs are in the right place**: The chart mounts volume `graviteeio-am-jdbc-ext` at `.../plugins/ext/repository-am-jdbc` (and reporter-am-jdbc, dataplane-am-jdbc). From a running API pod: `kubectl -n gravitee-am exec deploy/am-management-api -- ls -la /opt/graviteeio-am-management-api/plugins/ext/repository-am-jdbc` — you should see `postgresql-42.7.3.jar` and `r2dbc-postgresql-1.0.7.RELEASE.jar`.
- **Env and config**: `am-postgres.yaml` matches `docker/local-stack/dev/docker-compose.postgres.yml`: same uppercase `GRAVITEE_*` env vars and chart-native `jdbc.*` / `management.type` so both ConfigMap and pod env are set. No AM application code changes.
- **Port-forward**: To reach gateway locally: `kubectl -n gravitee-am port-forward svc/am-gateway 8092:82`. Verify: `lsof -nP -iTCP:8092 -sTCP:LISTEN` or `curl -v http://127.0.0.1:8092/`. Background: `kubectl -n gravitee-am port-forward svc/am-gateway 8092:82 > /tmp/am-gateway.pf.log 2>&1 & disown`.

## Internal Architecture
The tool is located in `scripts/migration-tool/` and uses:
- **`Orchestrator`**: Manages stages and test execution.
- **`Providers`**: abstracted infrastructure (K8s, Docker).
- **`Strategies`**: specialized logic for databases (MongoDB, PostgreSQL).
- **`Wrappers`**: clean interfaces for `helm`, `kubectl`, and `port-forward`.

## Testing
Run unit tests for the tool itself:
```bash
cd scripts/migration-tool
npm install
npm test
```
