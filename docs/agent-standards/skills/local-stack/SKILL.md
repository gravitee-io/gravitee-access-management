---
name: local-stack
description: Spin up Gravitee AM and its dependencies with one command (docker/local-stack/local-stack.sh) for jest/playwright tests or manual API/Console work — build from current code, or pull a specific released/nightly version.
---

# Local Stack

`docker/local-stack/local-stack.sh` is the single entry point for running Access Management
locally. It wraps the docker-compose overlays in `docker/local-stack/dev/` so you never have
to remember overlay combinations. Two modes, identical ports/service names so the test suites
behave the same either way.

Always run it from `docker/local-stack/`.

## Two modes

| Goal | Command |
|------|---------|
| Build from the **current code state** and start | `./local-stack.sh up` |
| Start a **specific released/nightly version** (pulled images, no Maven) | `./local-stack.sh up --version <tag>` |

```bash
cd docker/local-stack

./local-stack.sh up                              # lean, MongoDB, built from source
./local-stack.sh up --full                       # full service set + Console UI
./local-stack.sh up --cloud                      # managed-cloud (cockpit mock)
./local-stack.sh up --db psql --ui               # PostgreSQL + Console UI
./local-stack.sh up --version 4.12.x-latest --ui # pull a version instead of building
./local-stack.sh down                            # stop + remove (incl. volumes)
```

## Flags

| Flag | Meaning |
|------|---------|
| `--version <tag>` | Pulled mode: use released/nightly images instead of building. |
| `--registry <host>` | Image registry (default `graviteeio` / Docker Hub). Private registry for nightlies. |
| `--db mongo\|psql` | Backend database (default `mongo`). `psql` auto-downloads JDBC drivers. |
| `--ui` | Also start the Console UI on `:4200` (required for playwright). |
| `--full` | UI + wiremock + ciba + openfga + kafka + mtls (jest-gateway + playwright union). Does **not** include cloud. |
| `--cloud` | Overlay: cockpit mock + management API in managed-cloud mode. |
| `--with a,b,…` | Opt-in extras: `ui,wiremock,ciba,openfga,kafka,mtls,spire,cloud`. |
| `--chaos` | Route AM's outbound connections through toxiproxy so they can be broken on demand. Off by default. |
| `--build` | Full clean rebuild: wipes stale source-tree plugin caches, `mvn clean install`, `make plugins`. Use when a container crashes on boot. |
| `--quick` / `--no-build` | Skip Maven; reuse existing zips, just rebuild images. |
| `--license <path>` | EE license file (default `dev/license/gravitee-universe-v4.key`). |

Other commands: `down`, `logs [svc]`, `status`, `pull --version <tag>`, `chaos <verb>`, `help`.

## Service set (what to start for which tests)

- **Lean (default)** — `gateway, management, <db>, smtp, wiremock`. Enough for management
  jest specs and most manual work.
- **`--ui`** — adds the Console (`:4200`); needed for **playwright** and manual Console work.
- **`--full`** — the union the **jest gateway** and **playwright** suites need (not cloud).
- **`--cloud`** — managed-cloud overlay (Cockpit mock) for the **cloud** jest suite.
- **`--with spire`** — only for the env-guarded gateway specs (`RUN_SPIRE_TESTS=true`).

## Fault injection (`--chaos`)

Starts toxiproxy between AM and the infrastructure it dials out to, so connections can be
broken and restored while the stack keeps running. Omitted unless asked for, and inert until
something is injected.

```bash
./local-stack.sh up --db psql --chaos

./local-stack.sh chaos status            # what is proxied, and what is disrupting it
./local-stack.sh chaos cut postgres      # black hole — callers hang until they time out
./local-stack.sh chaos reject postgres   # refused — immediate connection reset
./local-stack.sh chaos heal postgres     # or: chaos heal --all
```

Targets: `postgres` / `mongo` (per `--db`), `smtp` (always), `cockpit` (with `--cloud`).

- `cut` vs `reject` are different failures: `cut` stops data without closing anything, so a
  pool connection looks healthy and blocks until some timeout fires (silent failover);
  `reject` disables the listener, so connections are refused immediately (service restarted).
- `cut` is **one-way** — it blocks responses, not requests, so a query sent during a cut still
  reaches the database and commits. Add a matching `upstream` toxic for a two-way stall.
- `docker stop <db>` is not an equivalent test: a stopped container loses its DNS alias, so AM
  sees name resolution fail rather than a refused, reset, or hung socket.
- **Only the admin API (`:8474`) is published.** Proxy listeners stay on the compose network,
  so a jest/playwright run on the host keeps its direct route to the database and can assert
  against the data while AM's connection is cut.
- **LDAP and external HTTP IdPs are not covered** — their addresses are per-domain config in
  the database (`ldap://openldap:1389`, `http://wiremock:8080`), not compose env, so there is
  nothing central to redirect.
- Anything beyond `cut`/`reject` (latency, bandwidth, slicer, …) is deliberately not wrapped:
  use the toxiproxy API on `localhost:8474`. `chaos heal` clears those toxics too.
- A toxic survives until removed — `chaos heal --all` before concluding AM is broken.

## URLs & credentials (once up)

| What | URL |
|------|-----|
| Gateway | http://localhost:8092 |
| Management API | http://localhost:8093/management |
| Console UI (`--ui`/`--full`) | http://localhost:4200 |
| Cockpit mock (`--cloud`) | http://localhost:8085 |
| Mailbox (fake SMTP) | http://localhost:5080 |

Admin login **`admin` / `adminadmin`**; organization & environment **`DEFAULT`**.

## Running the suites against it

```bash
npm --prefix gravitee-am-test run ci:management:parallel
npm --prefix gravitee-am-test run ci:gateway
REPOSITORY_TYPE=jdbc npm --prefix gravitee-am-test run ci:management:parallel   # when started with --db psql
npm --prefix gravitee-am-test run ci:cloud      # needs --cloud
npm --prefix gravitee-am-test run pw            # playwright (needs --ui/--full)
```

## Custom config (gravitee.yml settings)

- **Override file (any mode, no rebuild):** `cp dev/docker-compose.local.yml.example
  dev/docker-compose.local.yml`, add `GRAVITEE_*` env (or volume-mount a `gravitee.yml`) per
  service — `local-stack.sh` merges it last on every `up`. Key mapping: `email.host` →
  `GRAVITEE_EMAIL_HOST`, `dataPlanes[0].type` → `GRAVITEE_DATAPLANES_0_TYPE`.
- **Source edit:** changing `…/*-standalone-distribution/src/main/resources/config/gravitee.yml`
  is picked up by smart rebuild on the next `up` (source mode only).

## Prerequisites & gotchas

- **Docker Desktop** must be running; the script checks and fails fast otherwise.
- **EE license** must exist at `dev/license/gravitee-universe-v4.key` (or `--license`).
- **Source build is smart but best-effort.** It compares repo sources against the built
  distribution zips and rebuilds only what looks stale, printing what it found. The
  multi-stage zip/plugin assembly can fool timestamps — if a run looks wrong, use `--build`
  (full clean) or `--quick` (skip). `--build` also wipes the stale `src/main/resources/plugins`
  caches that `mvn clean` leaves behind (the usual cause of boot-time plugin/classpath errors).
- **Pulled nightlies** (`*.x-latest`, `master-latest`) are only in the **private** Gravitee
  registry: copy `dev/.env.example` to `dev/.env`, set `AM_REGISTRY`, and `docker login`
  first. The private registry hostname is never committed in an active config line.
- **CIBA has no published image** — `--full`/`--with ciba` builds it from source via jib even
  in pulled mode.

See `docker/local-stack/README.md` for the full reference.
