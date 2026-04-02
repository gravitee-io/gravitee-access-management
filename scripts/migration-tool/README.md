# AM Migration Test Tool

Orchestrates Gravitee AM migration tests: deploy an initial version (“from” tag), run verification tests, upgrade Management API and Gateways to a target version (“to” tag), verify again, and optionally downgrade back and re-verify.

**Supported environments:** Kubernetes (Kind) and Docker Compose (Docker Compose is **not functional** at the moment; see [Limitations and known issues](#limitations-and-known-issues)).  
**Supported databases (K8s):** MongoDB and PostgreSQL.

All commands must be run from the **repository root** (e.g. `./scripts/migration-test.mjs`) so paths to Helm charts, values files, and `gravitee-am-test` resolve correctly.

---

## Prerequisites

### Kubernetes (Kind)
- `kubectl`, `helm` and `kind` installed.
- Gravitee license: set `GRAVITEE_LICENSE` (base64) or place a license file and set path via config (see `LicenseManager`).

### Docker Compose
- `docker-compose` and Docker daemon.  
- **Note:** The Docker Compose provider is not functional at the moment; use K8s for migration runs until it is fixed (see [Limitations and known issues](#limitations-and-known-issues)).

### All
- Node.js (for the tool and for running `gravitee-am-test` Jest suites).
- From repo root: `gravitee-am-test` and its dependencies (e.g. `npm install` in `gravitee-am-test` if needed).

---

## Workflow and design

### Stages (in order)
1. **clean** – Remove existing releases, secrets, namespace (K8s) or compose stack (Docker).
2. **k8s:setup** – (K8s only) Create namespace, deploy DB (MongoDB or PostgreSQL), create auth/license secrets; skip for Docker Compose.
3. **deploy-from** – Deploy AM at “from” tag (K8s: 1 Management API + 2 Gateways; Docker: 1 API + 1 Gateway). K8s starts port-forwards.
4. **seed** – Seed test data for the “from” version. Runs `npm run migration:seed -- --to-version <from-tag>` in `gravitee-am-test`.
5. **verify-baseline** – Run management Jest tests (`gravitee-am-test`) against the deployed “from” version.
6. **upgrade-mapi** – Upgrade Management API (and UI) to “to” tag.
7. **verify-mapi** – Run management tests again.
8. **upgrade-gw** – Upgrade Gateways to “to” tag.
9. **seed-upgrade** – Seed test data for the “to” version. Runs `npm run migration:seed -- --to-version <to-tag>` in `gravitee-am-test`. This data is used to verify rollback handles N+1 data correctly.
10. **verify-all** – Run gateway Jest tests (K8s: targets dp1 on port 8091).
11. *Optional, with `--with-downgrade`:* **downgrade-mapi** → **verify-after-downgrade-mapi** → **downgrade-gw** → **verify-after-downgrade**.

### K8s multi-dataplane
- One Management API release and two Gateway releases (dp1, dp2) per run.
- Ports (local port-forward): Management API **8093**, UI **8002** (local browser testing only; Jest uses 8093), Gateway dp1 **8091**, Gateway dp2 **8092**, Gateway dp1 node/monitoring **18092** (`AM_GATEWAY_NODE_MONITORING_URL`; used for domain readiness polling).
- `AM_GATEWAY_URL` is set to `http://localhost:8091` for verify-all and verify-after-downgrade so gateway tests hit dp1.

### System diagram (K8s)

When running with `--provider k8s`, the tool deploys the following into a Kind cluster. Port-forwards expose services on localhost: Jest (gravitee-am-test) uses **8093** (Management API), **8091** (Gateway dp1), and **18092** (Gateway dp1 node/monitoring for domain readiness). Port **8002** (Management UI) is for local browser testing only; Jest does not access it.

```mermaid
flowchart LR
    subgraph host["Host (repo root)"]
        Jest["gravitee-am-test\n(Jest)"]
    end

    subgraph kind["Kind cluster"]
        subgraph ns["namespace: gravitee-am"]
            DB[(MongoDB or\nPostgreSQL)]
            MAPI["am-mapi\nManagement API"]
            UI["am-mapi\nManagement UI\n(8002: local UI only)"]
            DP1["am-gateway-dp1"]
            DP2["am-gateway-dp2"]
        end
    end

    MAPI --> DB
    DP1 --> DB
    DP2 --> DB

    Jest -->|"localhost:8093"| MAPI
    Jest -->|"localhost:8091\n(AM_GATEWAY_URL)"| DP1
    Jest -->|"localhost:18092\n(node/monitoring)"| DP1
    Jest -.->|"localhost:8092"| DP2
```

*(Dashed line: dp2 is forwarded but gateway tests currently target dp1 only. UI on 8002 is for manual/browser testing, not Jest.)*

### Common and specific values (base + override)

For **MAPI** (Management API + UI), shared configuration lives in a **common** values file so the long env list is not duplicated:

- **MongoDB:** `am-mongodb-common.yaml` (labels, mongodb-replicaset, full `commonEnv`, api/gateway disabled, license) + `am-mongodb-mapi.yaml` (only api enabled, ui, gateway disabled).
- **PostgreSQL:** `am-postgres-common.yaml` (labels, jdbc, full `commonEnv`, api/gateway disabled, license) + `am-postgres-mapi.yaml` (only api enabled, ui, gateway disabled).

**How they are loaded:** The tool passes **multiple values files** to Helm in order: `helm upgrade … -f <common> -f <override>`. Helm **merges** them: the first file is the base, the second overrides. For nested keys (e.g. `api.enabled`), the override replaces only what it sets; keys not set in the override (e.g. `api.env`) stay from the base. So MAPI gets `api.env` from the common file and only enables api/ui in the override.

**Gateways** (dp1, dp2) still use a **single** values file each (`am-mongodb-gateway-dp1.yaml`, etc.) because each adds data-plane–specific env (e.g. `gravitee_repositories_gateway_dataPlane_id`, gateway/oauth2 URIs to the data-plane DB). They do not use the common file so each file is self-contained with its own env list.

Override via env: `AM_HELM_VALUES_PATH_MONGO_MAPI=path1,path2` or `AM_HELM_VALUES_PATH_POSTGRES_MAPI=path1,path2` (comma-separated for MAPI base + override).

### Limitations and known issues

- **Docker Compose:** The Docker Compose provider is **not functional** at the moment. The full migration flow (deploy → verify → upgrade → verify) does not work with `--provider docker-compose`. Use `--provider k8s` for migration runs. Next step: fix Docker Compose (compose file, env, and provider logic) so it can run the same stages as K8s.
- **Jest with K8s:** Jest tests (gravitee-am-test) are **not fully working** with the K8s environment; some environment or wiring fix is required (e.g. URLs, ports, dataplane IDs, or test timeout/readiness). Management and gateway specs may pass with a test filter or after manual env tweaks, but the full suite is not yet reliable. Next step: identify and fix the remaining environment/configuration gaps so all relevant Jest tests pass against the K8s-deployed AM.

### Configuration
- Release definitions (which Helm releases and values files to use) come from `Config.getK8sReleases(dbType)` in `lib/core/Config.mjs`.
- Paths for charts and values can be overridden via environment variables (see `Config.mjs`).

---

## Commands

### Help
```bash
./scripts/migration-test.mjs --help
```

### One-time setup (K8s)
Clean, run K8s setup (DB + namespace + secrets), deploy “from” version. No cleanup at the end. Does **not** run `seed`; use `--stage seed` separately if you need seeded data for manual testing.
```bash
./scripts/migration-test.mjs --provider k8s setup
```

### Run full migration
Runs all stages (clean → setup → deploy-from → seed → verify-baseline → … → seed-upgrade → verify-all, and optionally downgrade stages).
```bash
./scripts/migration-test.mjs --provider k8s run
./scripts/migration-test.mjs --provider k8s run --db-type postgres --with-downgrade
```
Docker Compose (single API + single Gateway). **Not functional at the moment**; use K8s.
```bash
./scripts/migration-test.mjs --provider docker-compose run
```

### Run a single stage
Useful for debugging.
```bash
./scripts/migration-test.mjs --provider k8s run --stage deploy-from
./scripts/migration-test.mjs --provider k8s run --stage verify-baseline --test-filter specs/management/certificates/certificates.jest.spec.ts
```

### Trigger CircleCI migration pipeline
Requires `CIRCLECI_TOKEN`. Sends parameters (from-tag, to-tag, db-type, provider, test-filter, with-downgrade) to CircleCI.
```bash
./scripts/migration-test.mjs trigger --from-tag 4.10.0 --to-tag latest --provider k8s --db-type postgres --with-downgrade
```

---

## Options

| Option | Description | Default |
|-------|-------------|---------|
| `--from-tag` | Initial AM version (Docker image tag) | `4.10.0` |
| `--to-tag` | Target AM version after upgrade | `latest` |
| `--db-type` | Database: `mongodb` or `postgres` (K8s) | `mongodb` |
| `--provider` | Infrastructure: `k8s` or `docker-compose` | `docker-compose` |
| `--stage` | Run only this stage (see list above) | (all stages) |
| `--test-filter` | Jest path pattern (e.g. `specs/management/certificates/certificates.jest.spec.ts`) | (none) |
| `--with-downgrade` | After verify-all, downgrade to from-tag and run downgrade verification | `false` (CLI); CircleCI migration workflow may default to `true` |
| `--test-dir` | Test suite directory (relative or absolute); overrides config default | From `Config.test.dir` or `AM_MIGRATION_TEST_DIR` |
| `--registry` | Image registry host, K8s only (e.g. `graviteeio.azurecr.io`). Overrides image repositories via Helm `--set` and skips Docker Hub tag validation. CI trigger does not yet forward this parameter. | (none; uses Docker Hub) |

---

## Running a single stage (--stage)

**`--stage <name>`** runs **only that one stage** instead of the full pipeline. Use it to re-run or debug a single step without redoing earlier stages.

- **Without `--stage`:** the tool runs the full list of stages (clean → k8s:setup → deploy-from → seed → verify-baseline → upgrade-mapi → verify-mapi → upgrade-gw → seed-upgrade → verify-all, and optionally the downgrade stages if you pass `--with-downgrade`).
- **With `--stage <name>`:** only the stage you name runs; no other stages run.

**Valid stage names:**

| Stage | What it does |
|-------|----------------|
| `clean` | Tear down: uninstall Helm releases, delete secrets/namespace (K8s) or compose stack (Docker). |
| `k8s:setup` | (K8s only) Create namespace, deploy DB (Mongo/Postgres), create auth secrets. Skipped for Docker Compose. |
| `deploy-from` | Deploy AM at `--from-tag` (e.g. 4.10.0). K8s also starts port-forwards. |
| `seed` | Seed test data for the “from” version (`npm run migration:seed -- --to-version <from-tag>`). |
| `verify-baseline` | Run Jest management tests against the “from” version. |
| `upgrade-mapi` | Upgrade Management API (and UI) to `--to-tag`. |
| `verify-mapi` | Run Jest management tests after MAPI upgrade. |
| `upgrade-gw` | Upgrade Gateways to `--to-tag`. |
| `seed-upgrade` | Seed test data for the “to” version (`npm run migration:seed -- --to-version <to-tag>`). For rollback testing. |
| `verify-all` | Run Jest gateway tests (e.g. against dp1). |
| `downgrade-mapi` | Downgrade MAPI back to `--from-tag`. |
| `verify-after-downgrade-mapi` | Run Jest management tests after MAPI downgrade. |
| `downgrade-gw` | Downgrade Gateways back to `--from-tag`. |
| `verify-after-downgrade` | Run Jest gateway tests after full downgrade. |

**Caveat:** `--stage` does **not** run previous stages. If you use `--stage verify-baseline`, the tool assumes the cluster is already set up and “from” is already deployed (e.g. you ran `deploy-from` earlier or used `setup`). It is mainly for re-running or debugging one step in an already-prepared environment.

**Examples:**

```bash
# Only tear down and redeploy “from” version (no tests, no upgrade)
./scripts/migration-test.mjs run --provider k8s --stage deploy-from

# Only run baseline Jest tests (assumes env already deployed)
./scripts/migration-test.mjs run --provider k8s --stage verify-baseline --test-filter specs/management/certificates/certificates.jest.spec.ts

# Only run the “upgrade MAPI” step (assumes deploy-from already done)
./scripts/migration-test.mjs run --provider k8s --stage upgrade-mapi
```

---

## Secrets and Aqua scan

To avoid hardcoded secrets and Aqua (or similar) scan findings:

- **License:** Inject via `GRAVITEE_LICENSE` (base64) in CI; do not commit license files. Path override: `AM_LICENSE_PATH`.
- **MongoDB (K8s):** Inject `MONGODB_ROOT_PASSWORD` and `MONGODB_GRAVITEE_PASSWORD` in CI; the tool uses safe defaults only when these are unset (local use). The MongoDB K8s secret is created from env at deploy time.
- **AM Helm values** (e.g. `env/k8s/am/am-mongodb-*.yaml`) still contain test-only connection strings with a default password for local runs. For strict scan compliance, either exclude these env files from secret scanning or (future) override connection strings via Helm `--set` from env.

---

## Databases (K8s)

### MongoDB
- Bitnami MongoDB chart; credentials via `auth.existingSecret`. The tool creates the secret from `MONGODB_ROOT_PASSWORD` (or a safe default) and `MONGODB_GRAVITEE_PASSWORD` (or a default for local). For CI/Aqua, inject these via env so no secrets are hardcoded.

### PostgreSQL
- **Internal minimal chart** at `env/k8s/db/postgres` using the **official postgres:16.6** image (no Bitnami), so Liquibase `md5()` is available.
- Values: `env/k8s/db/db-postgres.yaml`. One Postgres instance; init script creates DBs: `gravitee-am`, `gravitee-am-gateway`, `gravitee-am-dp1`, `gravitee-am-dp2`.
- Service name: `postgres-postgresql`. Same namespace → host `postgres-postgresql`.
- **Password in values is for local/migration test only;** override in production.
- Verify: `kubectl get statefulset,pods,svc -n gravitee-am | grep postgres`. Test md5:  
  `kubectl -n gravitee-am exec -it postgres-postgresql-0 -- psql -h 127.0.0.1 -U gravitee -d gravitee-am -c "select md5('test');"`

---

## Architecture

- **Entry point:** `index.mjs` – parses CLI (Node `parseArgs`), loads `.env`, selects provider, and runs command (`run`, `setup`, `teardown`, or `trigger`).
- **Orchestrator** (`lib/Orchestrator.mjs`) – Runs stages in sequence; calls provider (`clean`, `setup`, `deploy`, `upgradeMapi`, `upgradeGw`) for infrastructure stages, runs Jest in `gravitee-am-test` for verify stages, and runs `npm run migration:seed` for seed stages. On failure or success, calls `provider.cleanup()` unless `skipCleanup` is set.
- **Providers:** Abstract deployment and teardown.
  - **K8sProvider** – Uses Helm (graviteeio/am + optional internal Postgres chart), Kubectl (namespace, secrets), LicenseManager (license for Helm `--set`), PortForwarder (8093, 8002, 8091, 8092), and a DatabaseStrategy (MongoDB or PostgreSQL). Multi-release: one API + two Gateways; version validated via `VersionValidator` (Docker Hub) before deploy/upgrade.
  - **DockerComposeProvider** – Uses `env/docker-compose/docker-compose.yml`; single API + single Gateway; `AM_VERSION` env drives image tag.
- **Config** (`lib/core/Config.mjs`) – Paths for Helm values and charts; `getK8sReleases(dbType)` returns the three releases (mapi, gateway-dp1, gateway-dp2) for the chosen DB.
- **Strategies** (`lib/strategies/database/`) – `MongoK8sStrategy`, `PostgresK8sStrategy`: deploy/clean/waitForReady for the DB layer.
- **Infra** (`lib/infra/kubernetes/`) – `Helm`, `Kubectl`, `LicenseManager`, `PortForwarder` – no direct dependency on zx in interfaces so they can be mocked in unit tests.
- **CircleCI** (`lib/CircleCI.mjs`) – Used by `trigger` to POST pipeline parameters to CircleCI.

Unit tests live under `test/unit/` and mock shell/spawn and external services.

---

## Testing the tool

Run the migration-tool unit tests (from repo root or from `scripts/migration-tool`):

```bash
cd scripts/migration-tool
npm install
npm test
```

See also `docs/agent-standards/commands.md` for canonical build/test and migration-tool commands.

---

## Next steps

- **Fix Docker Compose** – Make the Docker Compose provider functional so the full migration flow runs with `--provider docker-compose`. Requires fixing compose file, env, and provider logic to align with the K8s flow.
- **Fix Jest/K8s environment** – Resolve remaining environment or wiring issues so Jest tests (gravitee-am-test) run reliably against the K8s-deployed AM (management and gateway specs, URLs, ports, dataplane IDs, timeouts). Aim for the full suite (or a well-defined subset) to pass without manual tweaks.
- **Implement real seed logic** – `migration-bootstrap.mjs` is currently a **stub** (prints confirmation, creates no data). Replace with real seeding that creates domains, applications, users via the Management API so verification tests run against known state. The orchestrator wiring (`seed` / `seed-upgrade` stages) is ready.
- Run a full migration locally (e.g. Kind + `--provider k8s run --db-type postgres --with-downgrade`).
- Use `--stage` and `--test-filter` to iterate on a single stage or a single Jest file.
- Trigger the CircleCI migration workflow via `trigger` (with `CIRCLECI_TOKEN`) or from the GitHub Actions “Trigger Migration Test” workflow.
- Extend: add a new stage in `Orchestrator.executeStage`, a new provider in `lib/providers/`, or new K8s release config in `Config.getK8sReleases` and corresponding values in `env/k8s/am/`.

---

## Future work

- **Infrastructure via configuration** – Manage K8s resources and dependencies more via configuration than code, so adding new resources or dependencies (e.g. OpenFGA, SMTP, LDAP, Kafka) requires minimal code change. For example: declare releases, ports, and env in config or YAML; have the provider and orchestrator drive behaviour from that config instead of hardcoding topology and wiring in code.
- **Seeded dataset** – The `seed` / `seed-upgrade` orchestrator stages are wired; `migration-bootstrap.mjs` is a stub. Implement real seeding logic (domains, apps, users, tokens) so verification tests run against known deterministic state.
- **Migration-specific test scenarios** – Extend existing test assets (e.g. Gatling scenarios, gravitee-am-test specs) to cover migration-specific behaviour (upgrade/downgrade, schema compatibility, regression) rather than introducing a new test project solely for the migration tool.
- **Use both dp1 and dp2** – Extend verification to exercise both gateway dataplanes (dp1 on 8091, dp2 on 8092): run gateway tests against each, or add load/HA checks that involve both.
- **Additional dependencies** – Add optional or configurable dependencies (e.g. SMTP, LDAP, Kafka) in K8s/Docker Compose so migration tests cover a wider range of integrations and configurations.
