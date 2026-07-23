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

SPIRE is only needed by the env-guarded gateway tests (`RUN_SPIRE_TESTS=true`); start it with
`--with spire`.

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

## Advanced

The underlying `yarn stack:*` scripts in [`package.json`](package.json) remain valid (CI uses
them) and wrap the same compose overlays in `dev/`. `local-stack.sh` is the recommended entry
point; reach for the raw scripts only for bespoke overlay combinations.
