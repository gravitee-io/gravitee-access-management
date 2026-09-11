# Local Development Stack

Docker Compose-based local environment for Gravitee Access Management (AM), driven by a
single command: **`./local-stack.sh`**. Use it to spin up AM and its dependencies for
jest/playwright tests or for manual work against the APIs and Console UI.

## TL;DR

```bash
cd docker/local-stack

# Build from the CURRENT code state and start (lean: gateway, management, mongo, smtp, wiremock)
./local-stack.sh up

# Everything the e2e/jest suites need, plus the Console UI
./local-stack.sh up --full

# Start a specific RELEASED/nightly version instead of building (no Maven)
./local-stack.sh up --version 4.11 --ui

# Tear everything down
./local-stack.sh down
```

Run `./local-stack.sh help` for the full reference.

## Prerequisites

- **Docker Desktop** running.
- **Enterprise license** at `dev/license/gravitee-universe-v4.key` (required for AM to start
  with full features). Point elsewhere with `--license <path>`.
- **Source mode** (building from code) also needs Java 21+, Maven 3.6+, and — only when you
  pass `--ui`/`--full` — Node 20+/Yarn (the UI build).
- **Pulled mode** (`--version`) needs registry access. Public releases pull anonymously from
  Docker Hub; **pre-release/nightly tags** (`*.x-latest`, `master-latest`) live in the private
  Gravitee registry — `docker login <registry>` first and configure `dev/.env` (see below).

## The two modes

### 1. Build & start from current code state

```bash
./local-stack.sh up                 # lean, MongoDB
./local-stack.sh up --full          # full service set + Console UI
./local-stack.sh up --cloud         # managed-cloud (cockpit mock)
./local-stack.sh up --db psql --ui  # PostgreSQL + Console UI
```

The build is **smart by default**: it runs Maven only for what looks stale, re-syncs the
distribution zips into `dev/build-ctx/`, and rebuilds the Docker images (cheap when layers
are cached). Override the heuristic with:

| Flag | Effect |
|------|--------|
| *(default)* | Rebuild only what changed (compares repo sources against the built zips). |
| `--build` | **Full clean rebuild.** Wipes the stale source-tree plugin caches, runs `mvn clean install`, then `make plugins`. Use this when a container crashes on boot. |
| `--quick` / `--no-build` | Skip Maven; reuse the existing zips, just (re)build images. |

> Smart detection is best-effort — the multi-stage distribution/plugin assembly can fool
> timestamps. It prints what it considers stale before acting; if a run looks wrong, fall
> back to `--build` (clean) or `--quick` (skip).
>
> **Why `--build` does more than `mvn clean`:** the bundled plugins live in each
> distribution's `src/main/resources/plugins/` (source tree, untouched by `mvn clean`), so
> stale-version plugin zips linger there and get packaged alongside the new ones — causing
> runtime plugin/classpath errors (e.g. `Lookup method resolution failed`). `--build` clears
> those caches first, which a plain Maven build cannot.

Long build steps (Maven, `make plugins`, CIBA) run quietly with a progress spinner; their
full output is captured and shown only if the step fails.

### 2. Start a specific version (pulled images)

```bash
./local-stack.sh up --version 4.10.0            # public release (Docker Hub)
./local-stack.sh up --version 4.12.x-latest --ui   # nightly (private registry — see below)
```

Pulled mode skips Maven entirely: it `docker compose pull`s the AM images and starts them
with `--no-build`. Service names and ports are identical to source mode, so the test suites
behave the same way.

**Registry config** — `dev/.env` (gitignored) overrides `dev/.env.example`:

```bash
cp dev/.env.example dev/.env
# edit dev/.env:
#   GIO_AM_VERSION=4.12.x-latest
#   AM_REGISTRY=graviteeio.azurecr.io
docker login graviteeio.azurecr.io
./local-stack.sh up --version 4.12.x-latest --ui
```

`--version` / `--registry` on the CLI always override the env file.

> CIBA has no published image. If you request it in pulled mode (`--full` or `--with ciba`),
> the CIBA delegated service is still built from source via jib.

## Service sets

| Selector | Services started |
|----------|------------------|
| *(default lean)* | gateway, management, db, smtp, wiremock |
| `--ui` | + Console UI (`:4200`) — needed for playwright & manual Console work |
| `--full` | UI + wiremock + ciba + openfga + kafka + mtls (the jest-gateway + playwright union) |
| `--cloud` | cockpit mock; management API in managed-cloud mode (Cockpit command path) |
| `--with a,b,…` | opt-in individually: `ui,wiremock,ciba,openfga,kafka,mtls,spire,cloud` |
| `--db mongo\|psql` | choose the backend database (default `mongo`) |
| `--chaos` | + toxiproxy, interposed on AM's outbound connections — see [Fault injection](#fault-injection) |

SPIRE is only needed by the env-guarded gateway tests (`RUN_SPIRE_TESTS=true`); start it with
`--with spire`.

## Fault injection

`--chaos` starts [toxiproxy](https://github.com/Shopify/toxiproxy) and routes AM's outbound
connections through it, so they can be broken and restored while the stack keeps running.
It is off unless you ask for it, and changes nothing about how the stack behaves until you
inject something.

```bash
./local-stack.sh up --db psql --chaos

./local-stack.sh chaos status            # what is proxied, and what is disrupting it
./local-stack.sh chaos cut postgres      # black hole: callers hang until they time out
./local-stack.sh chaos reject postgres   # refused: immediate connection reset
./local-stack.sh chaos heal postgres     # or: chaos heal --all
```

`cut` and `reject` are different failures and tend to expose different bugs. `cut` stops data
flowing without closing anything, so an open pool connection looks healthy and blocks until
some timeout fires — a silent failover, or a partition. `reject` disables the listener, so
connections are refused straight away — the service restarted, or its port closed.

`cut` is one-way: it blocks responses, not requests. A query sent during a cut still reaches
the database and commits — the caller just never learns the outcome, which is what a real
partition looks like and where retry bugs live. For a two-way stall, add the upstream half:

```bash
curl -s -X POST http://localhost:8474/proxies/mongo/toxics \
  -H 'Content-Type: application/json' \
  -d '{"name":"cut_up","type":"timeout","stream":"upstream","attributes":{"timeout":0}}'
```

Note also that `docker stop <db>` is **not** an equivalent test: a stopped container loses its
DNS alias, so AM sees a name-resolution failure rather than a refused, reset, or hung socket —
a different code path, reached before any pool or retry logic.

### What gets proxied

| Target | Fronts | Present when |
|--------|--------|--------------|
| `postgres` | PostgreSQL, for every AM JDBC scope | `--db psql` |
| `mongo` | MongoDB, for every AM repository scope | `--db mongo` (default) |
| `smtp` | the fake SMTP server | always |
| `cockpit` | the Cockpit mock the management API dials out to | `--cloud` |

Each proxy listens on the **same port as the service it fronts**, so redirecting AM is a pure
host substitution (`postgres` → `toxiproxy`) and the connection strings stay readable.

Two exclusions:

- **The host.** Only the admin API is published; the proxy listeners stay on the compose
  network. A jest or playwright run on your machine keeps its *direct* route to the database,
  so it can still assert against the data while AM's own connection is cut.
- **`gateway-migrator`.** It is a one-shot liquibase run that has to finish before the gateway
  starts, so putting it behind the proxy would only add a way for the stack to fail at boot.

Not covered: **LDAP and external HTTP identity providers**. Those addresses live in per-domain
configuration in the database (`ldap://openldap:1389`, `http://wiremock:8080`), written by the
test or the Console rather than by compose, so there is nothing central to redirect. Breaking
one means pointing that identity provider at a proxy you add yourself.

### Everything else toxiproxy can do

The script wraps disrupt-and-resume and stops there. Its other toxics are available on the
API directly:

```bash
# 800ms ± 100ms on every response
curl -s -X POST http://localhost:8474/proxies/postgres/toxics \
  -H 'Content-Type: application/json' \
  -d '{"name":"slow","type":"latency","attributes":{"latency":800,"jitter":100}}'

# throttle to 10 KB/s
curl -s -X POST http://localhost:8474/proxies/postgres/toxics \
  -H 'Content-Type: application/json' \
  -d '{"name":"thin","type":"bandwidth","attributes":{"rate":10}}'
```

`chaos heal` clears toxics it did not create, so anything you add by hand still has a one-word
undo. The full toxic reference — `latency`, `bandwidth`, `slow_close`, `timeout`, `reset_peer`,
`slicer`, `limit_data`, plus `toxicity` and `stream` on all of them — is in the
[toxiproxy README](https://github.com/Shopify/toxiproxy#toxics).

### Making recovery observable

Connection-pool defaults are tuned for production, not for a manual test window: with
`maxLifeTime` at default you may wait a long time to watch a pool recover. Shorten the
timings in your gitignored `dev/docker-compose.local.yml` (merged last, so it wins) for as
long as you need them:

```yaml
services:
  gateway:
    environment:
      - GRAVITEE_REPOSITORIES_GATEWAY_JDBC_VALIDATIONQUERY=SELECT 1
      - GRAVITEE_REPOSITORIES_GATEWAY_JDBC_MAXVALIDATIONTIME=3000
      - GRAVITEE_REPOSITORIES_GATEWAY_JDBC_MAXLIFETIME=20000
      - GRAVITEE_REPOSITORIES_GATEWAY_JDBC_TCPKEEPALIVE=true
```

Repeat per scope (`OAUTH2`, `MANAGEMENT`, `DATAPLANES_0`) as needed. With these applied you
are no longer observing production timings.

## Customising configuration (gravitee.yml settings)

Copy the example to a gitignored override that `local-stack.sh` merges last on every `up`:

```bash
cp dev/docker-compose.local.yml.example dev/docker-compose.local.yml
# edit it, then:
./local-stack.sh up
```

Any `gravitee.yml` key maps to an env var: `GRAVITEE_<UPPER_SNAKE>`, nested keys joined by `_`,
list indices as numbers — e.g. `email.host` → `GRAVITEE_EMAIL_HOST`, `dataPlanes[0].type` →
`GRAVITEE_DATAPLANES_0_TYPE`. The override can also volume-mount a whole `gravitee.yml` over
the baked one. Env vars override the image's `gravitee.yml` at runtime, so this is identical
for source-built and `--version` images.

## URLs & credentials

| What | URL | Notes |
|------|-----|-------|
| Gateway | http://localhost:8092 | OAuth2/OIDC endpoints |
| Gateway node | http://localhost:18092/_node | health/metrics |
| Management API | http://localhost:8093/management | REST API |
| Management node | http://localhost:18093/_node | health/metrics |
| Console UI | http://localhost:4200 | with `--ui` / `--full` |
| Cockpit mock | http://localhost:8085 | with `--cloud` |
| Mailbox (fake SMTP) | http://localhost:5080 | SMTP on `:5025` |
| WireMock | http://localhost:8181 | SFR/CIMD mocks |
| MongoDB / PostgreSQL | `:27017` / `:5432` | per `--db` |
| Toxiproxy admin API | http://localhost:8474 | with `--chaos` |

Admin login: **`admin` / `adminadmin`** — organization & environment: **`DEFAULT`**.

## Running the tests against it

The suites live in `gravitee-am-test/` and default to the ports above.

```bash
# Jest (needs lean stack, or --full for gateway protocol specs)
npm --prefix gravitee-am-test run ci:management:parallel
npm --prefix gravitee-am-test run ci:gateway
REPOSITORY_TYPE=jdbc npm --prefix gravitee-am-test run ci:management:parallel   # with --db psql

# Cloud / Cockpit command specs (needs --cloud, distinct from --full)
npm --prefix gravitee-am-test run ci:cloud

# Playwright (needs --ui / --full)
npm --prefix gravitee-am-test run pw            # interactive
npm --prefix gravitee-am-test run pw:ci         # CI mode
```

## Other commands

```bash
./local-stack.sh status          # docker compose ps
./local-stack.sh logs            # follow all logs
./local-stack.sh logs gateway    # follow one service
./local-stack.sh pull --version 4.12.x-latest   # pull images without starting
./local-stack.sh down            # stop + remove containers and volumes
```

## Optional stacks

- **[Kerberos SPNEGO Lab](dev/kerberos/KERBEROS-LAB-HOWTO.md)** — KDC container for testing
  SPNEGO authentication (brought up via the dedicated overlay).

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `License not found` | Put the EE key at `dev/license/gravitee-universe-v4.key` or pass `--license`. |
| `Docker does not appear to be running` | Start Docker Desktop. |
| Pull fails / `unauthorized` | `docker login <registry>`; for nightlies set `AM_REGISTRY` in `dev/.env`. |
| Stale code, or a service crashes on boot | `./local-stack.sh up --build` (full clean rebuild — see Build above). |
| Port already in use | Stop the conflicting process or run `./local-stack.sh down` first. |
| A container won't become healthy | `./local-stack.sh logs management` (or `gateway`) to inspect the boot error. |
| `toxiproxy is not reachable` | The stack was started without `--chaos`. Restart it with the flag. |
| `toxiproxy is up but has no proxy named …` | Proxies are seeded from `dev/toxiproxy.json` at container start; restart the stack after editing it. |
| AM still failing after you finished testing | `./local-stack.sh chaos heal --all` — a toxic survives until you remove it. |

## Advanced

The underlying `yarn stack:*` scripts in [`package.json`](package.json) remain valid (CI uses
them) and wrap the same compose overlays in `dev/`. `local-stack.sh` is the recommended entry
point; reach for the raw scripts only for bespoke overlay combinations.
