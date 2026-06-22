#!/usr/bin/env bash
#
# Copyright (C) 2015 The Gravitee team (http://gravitee.io)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# local-stack.sh — one command to spin up Access Management and its dependencies.
#
#   ./local-stack.sh up                       # build from current code, start (lean, mongo)
#   ./local-stack.sh up --full                # everything the e2e/jest suites need + Console
#   ./local-stack.sh up --version 4.12.x-latest --ui   # pull a released/nightly image instead
#   ./local-stack.sh down                      # tear everything down
#
# Run `./local-stack.sh help` for the full reference.

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
REPO_ROOT="$(cd ../.. && pwd)"
DEV="dev"

# ---------------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------------
if [ -t 1 ]; then C_BLU=$'\033[34m'; C_YEL=$'\033[33m'; C_RED=$'\033[31m'; C_GRN=$'\033[32m'; C_DIM=$'\033[2m'; C_RST=$'\033[0m'
else C_BLU=""; C_YEL=""; C_RED=""; C_GRN=""; C_DIM=""; C_RST=""; fi
log()  { printf '%s==>%s %s\n' "$C_BLU" "$C_RST" "$*"; }
ok()   { printf '%s ok %s %s\n' "$C_GRN" "$C_RST" "$*"; }
warn() { printf '%swarn%s %s\n' "$C_YEL" "$C_RST" "$*" >&2; }
die()  { printf '%serr %s %s\n' "$C_RED" "$C_RST" "$*" >&2; exit 1; }

on_err() {
  local rc=$?
  printf '%serr %s unexpected failure (exit %s) at %s line %s:\n' "$C_RED" "$C_RST" "$rc" "$(basename "$0")" "${1:-?}" >&2
  printf '        %s\n' "${BASH_COMMAND:-?}" >&2
  printf '%s        Re-run with TRACE: bash -x %s ... to see the full execution.%s\n' "$C_DIM" "$(basename "$0")" "$C_RST" >&2
  exit "$rc"
}
trap 'on_err "$LINENO"' ERR

TTY=0; [ -t 1 ] && TTY=1

# Run a long command quietly: spinner + elapsed on a TTY, full output captured
# to a temp log and shown ONLY if it fails. Keeps high-level feedback readable.
mvn_in_repo() { ( cd "$REPO_ROOT" && mvn "$@" ); }

_spin() { # _spin <pid> <message>
  local pid="$1" msg="$2" start frames='|/-\' i=0
  start=$(date +%s)
  while kill -0 "$pid" 2>/dev/null; do
    i=$(( (i + 1) % 4 ))
    printf '\r%s==>%s %s %s(%ss)%s %s\033[K' "$C_BLU" "$C_RST" "$msg" "$C_DIM" "$(( $(date +%s) - start ))" "$C_RST" "${frames:$i:1}"
    sleep 0.2
  done
}

run_step() { # run_step <message> <cmd...>
  local msg="$1"; shift
  local logf rc=0 start; logf="$(mktemp "${TMPDIR:-/tmp}/local-stack.XXXXXX")"; start=$(date +%s)
  if [ "$TTY" -eq 1 ]; then
    ( "$@" ) >"$logf" 2>&1 &
    local pid=$!
    _spin "$pid" "$msg"
    wait "$pid" || rc=$?
  else
    printf '%s==>%s %s …\n' "$C_BLU" "$C_RST" "$msg"
    ( "$@" ) >"$logf" 2>&1 || rc=$?
  fi
  local secs=$(( $(date +%s) - start ))
  if [ "$rc" -eq 0 ]; then
    # On a TTY overwrite the spinner line; otherwise print a clean line (no escapes in logs).
    if [ "$TTY" -eq 1 ]; then printf '\r%s ok %s %s %s(%ss)%s\033[K\n' "$C_GRN" "$C_RST" "$msg" "$C_DIM" "$secs" "$C_RST"
    else printf '%s ok %s %s (%ss)\n' "$C_GRN" "$C_RST" "$msg" "$secs"; fi
    rm -f "$logf"; return 0
  fi
  if [ "$TTY" -eq 1 ]; then printf '\r%serr %s %s failed (exit %s, %ss)%s\033[K\n' "$C_RED" "$C_RST" "$msg" "$rc" "$secs" "$C_RST" >&2
  else printf '%serr %s %s failed (exit %s, %ss)\n' "$C_RED" "$C_RST" "$msg" "$rc" "$secs" >&2; fi
  printf '%s     last 40 lines (full log: %s):%s\n' "$C_DIM" "$logf" "$C_RST" >&2
  tail -n 40 "$logf" >&2 || true
  return "$rc"
}

# ---------------------------------------------------------------------------
# Defaults / configuration
# ---------------------------------------------------------------------------
DB="mongo"                 # mongo | psql
VERSION=""                 # non-empty => pulled mode
REGISTRY="${AM_REGISTRY:-}"
BUILD_MODE="smart"         # smart | force | skip
FULL=0
DETACH=1
LICENSE_FILE="$DEV/license/gravitee-universe-v4.key"
# opt-in extras (plain vars — keep this script bash-3.2 compatible, macOS default)
WANT_UI=0; WANT_WIREMOCK=1; WANT_CIBA=0; WANT_OPENFGA=0; WANT_KAFKA=0; WANT_MTLS=0; WANT_SPIRE=0

want_set() { # want_set <name> <value>
  case "$1" in
    ui) WANT_UI="$2" ;; wiremock) WANT_WIREMOCK="$2" ;; ciba) WANT_CIBA="$2" ;;
    openfga) WANT_OPENFGA="$2" ;; kafka) WANT_KAFKA="$2" ;; mtls) WANT_MTLS="$2" ;;
    spire) WANT_SPIRE="$2" ;; *) return 1 ;;
  esac
}

CIBA_IMAGE="gravitee-am-ciba-delegated-service"

usage() {
  cat <<'EOF'
local-stack.sh — spin up Gravitee Access Management + dependencies

USAGE
  ./local-stack.sh <command> [flags]

COMMANDS
  up        Build (or pull) and start the stack            (default)
  down      Stop and remove the stack and its volumes
  logs      Follow logs (optionally for one service: logs gateway)
  status    Show container status (docker compose ps)
  pull      Pull the AM images for --version without starting
  help      Show this help

MODE
  (default)            Build AM from the CURRENT code state, then start.
  --version <tag>      PULL released/nightly AM images instead of building.
                       e.g. --version 4.12.x-latest   --version 4.10.0
  --registry <host>    Image registry (default: $AM_REGISTRY or "graviteeio").
                       For pre-release tags use your private Gravitee registry.

SERVICES
  (default lean)       gateway, management, <db>, smtp, wiremock
  --ui                 also start the Console UI on :4200 (needed for playwright)
  --full               UI + wiremock + ciba + openfga + kafka + mtls
                       (the union the jest gateway + playwright suites need)
  --with a,b,...       opt-in extras individually:
                       ui,wiremock,ciba,openfga,kafka,mtls,spire

DATABASE
  --db mongo|psql      backend database (default: mongo)

BUILD (source mode only)
  --build              force a full Maven rebuild before packaging
  --quick | --no-build skip Maven; reuse existing zips, just (re)build images
  (default = smart: rebuild only what looks stale; see README)

OTHER
  --no-detach          run in the foreground and stream logs
  -h, --help           this help

EXAMPLES
  ./local-stack.sh up
  ./local-stack.sh up --full
  ./local-stack.sh up --db psql --ui
  ./local-stack.sh up --with ui,openfga
  ./local-stack.sh up --version 4.12.x-latest --ui
  ./local-stack.sh down

URLs / credentials once up:
  Gateway        http://localhost:8092
  Management API http://localhost:8093/management
  Console UI     http://localhost:4200      (with --ui / --full)
  Mailbox (UI)   http://localhost:5080
  Admin login    admin / adminadmin   (org/env: DEFAULT)
EOF
}

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
COMMAND="up"
case "${1:-}" in
  up|down|logs|status|pull|help) COMMAND="$1"; shift ;;
  -h|--help|"") : ;;                      # default to `up`, help handled below
  -*) : ;;                                # leading flag => `up`
  *) COMMAND="$1"; shift ;;
esac

LOGS_SVC=""
while [ $# -gt 0 ]; do
  case "$1" in
    --version)   VERSION="${2:?--version needs a tag}"; shift 2 ;;
    --registry)  REGISTRY="${2:?--registry needs a host}"; shift 2 ;;
    --db)        DB="${2:?--db needs mongo|psql}"; shift 2 ;;
    --ui)        WANT_UI=1; shift ;;
    --full)      FULL=1; shift ;;
    --with)      IFS=',' read -ra _x <<< "${2:?--with needs a list}"
                 for s in "${_x[@]}"; do
                   want_set "$s" 1 || die "unknown --with service: $s"
                 done; shift 2 ;;
    --build)     BUILD_MODE="force"; shift ;;
    --quick|--no-build) BUILD_MODE="skip"; shift ;;
    --license)   LICENSE_FILE="${2:?--license needs a path}"; shift 2 ;;
    --no-detach) DETACH=0; shift ;;
    -h|--help)   usage; exit 0 ;;
    *)           if [ "$COMMAND" = "logs" ] && [ -z "$LOGS_SVC" ]; then LOGS_SVC="$1"; shift
                 else die "unknown argument: $1 (see ./local-stack.sh help)"; fi ;;
  esac
done

[ "$COMMAND" = "help" ] && { usage; exit 0; }
[ "$DB" = "mongo" ] || [ "$DB" = "psql" ] || die "--db must be mongo or psql"

PULLED=0; [ -n "$VERSION" ] && PULLED=1
DBSVC="mongodb"; [ "$DB" = "psql" ] && DBSVC="postgres"

# --full implies UI + the heavy extras
if [ "$FULL" -eq 1 ]; then
  WANT_UI=1; WANT_WIREMOCK=1; WANT_CIBA=1; WANT_OPENFGA=1; WANT_KAFKA=1; WANT_MTLS=1
fi

# ---------------------------------------------------------------------------
# Compose file + env assembly
# ---------------------------------------------------------------------------
# All overlays — used by down/logs/status so we always capture everything.
ALL_FILES=(
  -f "$DEV/docker-compose.yml"
  -f "$DEV/docker-compose-dev.yml"
  -f "$DEV/docker-compose-ci.yml"
  -f "$DEV/docker-compose.mongo.yml"
  -f "$DEV/docker-compose.postgres.yml"
  -f "$DEV/docker-compose-ui.yml"
  -f "$DEV/docker-compose-kerberos.yml"
  -f "$DEV/docker-compose.spire.yml"
  -f "$DEV/docker-compose.images.yml"
)

# Optional, gitignored per-dev override (custom env vars, volume-mounted gravitee.yml,
# etc.). Applied LAST so it wins, for source and pulled images alike. See
# dev/docker-compose.local.yml.example.
LOCAL_OVERRIDE="$DEV/docker-compose.local.yml"
[ -f "$LOCAL_OVERRIDE" ] && ALL_FILES+=(-f "$LOCAL_OVERRIDE")

build_compose_files() {
  COMPOSE_FILES=(-f "$DEV/docker-compose.yml" -f "$DEV/docker-compose-dev.yml")
  if [ "$DB" = "mongo" ]; then COMPOSE_FILES+=(-f "$DEV/docker-compose.mongo.yml")
  else COMPOSE_FILES+=(-f "$DEV/docker-compose.postgres.yml"); fi
  if [ "$WANT_UI" -eq 1 ]; then COMPOSE_FILES+=(-f "$DEV/docker-compose-ui.yml"); fi
  if [ "$WANT_SPIRE" -eq 1 ]; then COMPOSE_FILES+=(-f "$DEV/docker-compose.spire.yml"); fi
  if [ "$PULLED" -eq 1 ]; then COMPOSE_FILES+=(-f "$DEV/docker-compose.images.yml"); fi
  [ -f "$LOCAL_OVERRIDE" ] && COMPOSE_FILES+=(-f "$LOCAL_OVERRIDE")   # per-dev overrides win
}

# Service list for a targeted (non-full) `up`. For --full we start everything
# defined in the included files (empty list).
build_service_list() {
  SERVICES=()
  if [ "$FULL" -eq 1 ]; then return; fi
   SERVICES=(management gateway "$DBSVC" smtp am-wait)
  [ "$WANT_WIREMOCK" -eq 1 ] && SERVICES+=(wiremock wiremock-init)
  [ "$WANT_UI" -eq 1 ]       && SERVICES+=(webui)
  [ "$WANT_CIBA" -eq 1 ]     && SERVICES+=(ciba)
  [ "$WANT_OPENFGA" -eq 1 ]  && SERVICES+=(openfga)
  [ "$WANT_KAFKA" -eq 1 ]    && SERVICES+=(kafka)
  [ "$WANT_MTLS" -eq 1 ]     && SERVICES+=(gateway-mtls)
  if [ "$WANT_SPIRE" -eq 1 ]; then
    SERVICES+=(spire-perms-init spire-server spire-bootstrap spire-agent spire-oidc)
  fi
}

# Export the variables compose substitutes. .env (gitignored) overrides .env.example.
export_env() {
  local envfile=""
  [ -f "$DEV/.env" ] && envfile="$DEV/.env"
  [ -z "$envfile" ] && [ -f "$DEV/.env.example" ] && envfile="$DEV/.env.example"
  if [ -n "$envfile" ]; then
    # shellcheck disable=SC1090
    set -a; . "$envfile"; set +a
  fi
  # CLI overrides win over the env file.
  [ -n "$VERSION" ]  && export GIO_AM_VERSION="$VERSION"
  [ -n "$REGISTRY" ] && export AM_REGISTRY="$REGISTRY"
  export AM_VERSION='*'                       # build-arg glob for source images
  export GRAVITEE_LICENSE_KEY="${GRAVITEE_LICENSE_KEY:-}"  # referenced by the CI overlay; blank in dev
  export JDBC_PLUGINS="${JDBC_PLUGINS:-}"                  # referenced by the postgres overlay
  if [ "$DB" = "psql" ]; then export JDBC_PLUGINS="./plugins/jdbc"; fi
  return 0   # never let a trailing false test become the function's exit status (set -e)
}

compose() { docker compose "${COMPOSE_FILES[@]}" "$@"; }

# ---------------------------------------------------------------------------
# Source build (smart / force / skip)
# ---------------------------------------------------------------------------
GW_TARGET="../../gravitee-am-gateway/gravitee-am-gateway-standalone/gravitee-am-gateway-standalone-distribution/gravitee-am-gateway-standalone-distribution-zip/target"
MGMT_TARGET="../../gravitee-am-management-api/gravitee-am-management-api-standalone/gravitee-am-management-api-standalone-distribution/gravitee-am-management-api-standalone-distribution-zip/target"
WEBUI_TARGET="../../gravitee-am-ui/target"

latest() { ls -t $1 2>/dev/null | head -1 || true; }   # newest file matching a glob

# Speed flags safe for a local dev build (skip checks/docs/coverage/formatting).
MVN_SPEED=(-Dlicense.skip=true -Dmaven.javadoc.skip=true -Djacoco.skip=true -Dprettier.skip=true)

# The distribution plugin dirs live in SOURCE (src/main/resources/plugins), so
# `mvn clean` never touches them. Stale-version zips left here get bundled
# alongside the freshly-built ones → runtime plugin/classpath conflicts (e.g.
# "Lookup method resolution failed" on a repository bean). A force/clean build
# must wipe them first.
DIST_PLUGIN_DIRS=(
  "gravitee-am-gateway/gravitee-am-gateway-standalone/gravitee-am-gateway-standalone-distribution/src/main/resources/plugins"
  "gravitee-am-management-api/gravitee-am-management-api-standalone/gravitee-am-management-api-standalone-distribution/src/main/resources/plugins"
)
clean_plugin_caches() {
  local d
  for d in "${DIST_PLUGIN_DIRS[@]}"; do
    rm -rf "$REPO_ROOT/$d/.work"
    rm -f  "$REPO_ROOT/$d"/*.zip
  done
}
make_in_repo() { ( cd "$REPO_ROOT" && make "$@" ); }

# Any backend source file newer than the reference zip? (repo-wide, cross-module).
backend_sources_newer_than() {
  local ref="$1"
  { find "$REPO_ROOT" \
      -type d \( -name target -o -name node_modules -o -name .git -o -name dist \) -prune -o \
      -type f \( -name '*.java' -o -name 'pom.xml' \) -newer "$ref" -print 2>/dev/null | head -5; } || true
}
ui_sources_newer_than() {
  local ref="$1"
  { find "$REPO_ROOT/gravitee-am-ui/src" "$REPO_ROOT/gravitee-am-ui/package.json" \
      -type f -newer "$ref" -print 2>/dev/null | head -5; } || true
}
config_sources_newer_than() {
  local ref="$1"
  { find \
      "$REPO_ROOT/gravitee-am-gateway/gravitee-am-gateway-standalone/gravitee-am-gateway-standalone-distribution/src/main/resources" \
      "$REPO_ROOT/gravitee-am-management-api/gravitee-am-management-api-standalone/gravitee-am-management-api-standalone-distribution/src/main/resources" \
      -type f -newer "$ref" -print 2>/dev/null | head -5; } || true
}

run_maven() { # run_maven <short-label> <mvn args...>
  local label="$1"; shift
  run_step "Maven: $label" mvn_in_repo "$@" || die "Maven build failed — see output above, or retry: ./local-stack.sh up --build"
}

# Copy one artefact into build-ctx if the target is newer than what's there.
copy_artifact() {
  local target_glob="$1" dest_prefix="$2" label="$3"
  local src; src="$(latest "$target_glob")"
  [ -n "$src" ] || { warn "no built $label zip found under $(dirname "$target_glob")"; return 1; }
  local dest; dest="$(latest "$DEV/build-ctx/${dest_prefix}*.zip")"
  if [ -z "$dest" ] || [ "$src" -nt "$dest" ]; then
    rm -f "$DEV"/build-ctx/${dest_prefix}*.zip
    cp "$src" "$DEV/build-ctx/"
    ok "copied $(basename "$src") -> build-ctx"
  else
    printf '%s    %s up to date in build-ctx%s\n' "$C_DIM" "$label" "$C_RST"
  fi
  return 0
}

source_build() {
  mkdir -p "$DEV/build-ctx"
  local need_backend=0 need_ui=0
  local want_ui="$WANT_UI"

  local gw_zip mgmt_zip webui_zip
  gw_zip="$(latest "$GW_TARGET/gravitee-am-gateway-standalone-*.zip")"
  mgmt_zip="$(latest "$MGMT_TARGET/gravitee-am-management-api-standalone-*.zip")"
  webui_zip="$(latest "$WEBUI_TARGET/gravitee-am-webui-*.zip")"

  # --- decide what to (re)build -----------------------------------------
  if [ "$BUILD_MODE" = "force" ]; then
    need_backend=1; [ "$want_ui" -eq 1 ] && need_ui=1
  elif [ "$BUILD_MODE" = "skip" ]; then
    [ -n "$gw_zip" ] && [ -n "$mgmt_zip" ] || die "no built backend zips and --quick/--no-build set. Run without --quick (or with --build) first."
    if [ "$want_ui" -eq 1 ] && [ -z "$webui_zip" ]; then die "--ui requested but no webui zip built; drop --quick or run --build."; fi
  else
    # smart
    if [ -z "$gw_zip" ] || [ -z "$mgmt_zip" ]; then
      log "No backend distribution zips found — a Maven build is required."
      need_backend=1
    else
      local ref="$gw_zip"; [ "$mgmt_zip" -ot "$ref" ] && ref="$mgmt_zip"
      local hits; hits="$(backend_sources_newer_than "$ref"; config_sources_newer_than "$ref")"
      if [ -n "$hits" ]; then
        warn "backend sources/config changed since the last build, e.g.:"
        printf '%s        %s%s\n' "$C_DIM" "$(echo "$hits" | sed "s#$REPO_ROOT/##" | tr '\n' ' ')" "$C_RST"
        warn "rebuilding (use --quick to skip, --build to force a clean build)"
        need_backend=1
      fi
    fi
    if [ "$want_ui" -eq 1 ]; then
      if [ -z "$webui_zip" ]; then log "No webui zip found — building UI."; need_ui=1
      elif [ -n "$(ui_sources_newer_than "$webui_zip")" ]; then
        warn "UI sources changed since the last build — rebuilding webui."; need_ui=1
      fi
    fi
  fi

  # --- run Maven --------------------------------------------------------
   if [ "$BUILD_MODE" = "force" ]; then
    log "Clean rebuild: wiping stale distribution plugin caches"
    clean_plugin_caches
  fi

  if [ "$need_backend" -eq 1 ] && [ "$need_ui" -eq 1 ]; then
    run_maven "clean install (backend)" clean install -pl '!gravitee-am-ui' -DskipTests "${MVN_SPEED[@]}"
    run_maven "install (UI)" install -pl gravitee-am-ui -DskipTests "${MVN_SPEED[@]}"
  elif [ "$need_backend" -eq 1 ]; then
    run_maven "clean install (backend)" clean install -pl '!gravitee-am-ui' -DskipTests "${MVN_SPEED[@]}"
  elif [ "$need_ui" -eq 1 ]; then
    run_maven "install (UI)" install -pl gravitee-am-ui -DskipTests "${MVN_SPEED[@]}"
  elif [ "$BUILD_MODE" = "skip" ]; then
    ok "skipping Maven (--quick): reusing existing distribution zips"
  else
    ok "build artefacts look current — skipping Maven"
  fi

  if [ "$BUILD_MODE" = "force" ]; then
    run_step "Refreshing plugins (make plugins)" make_in_repo plugins \
      || die "make plugins failed — see output above."
  fi

  # --- copy into build context -----------------------------------------
  log "Syncing distribution zips into build-ctx"
  copy_artifact "$GW_TARGET/gravitee-am-gateway-standalone-*.zip" "gravitee-am-gateway-standalone-" "gateway" || die "gateway zip missing"
  copy_artifact "$MGMT_TARGET/gravitee-am-management-api-standalone-*.zip" "gravitee-am-management-api-standalone-" "management" || die "management zip missing"
  if [ "$want_ui" -eq 1 ]; then
    copy_artifact "$WEBUI_TARGET/gravitee-am-webui-*.zip" "gravitee-am-webui-" "webui" || die "webui zip missing"
    [ -d "../management-ui/config" ] && cp -r ../management-ui/config "$DEV/build-ctx/" || true
  fi
}

# Build the CIBA delegated service image (no published image exists).
ensure_ciba() {
  if [ "$BUILD_MODE" != "force" ] && docker image inspect "$CIBA_IMAGE" >/dev/null 2>&1; then
    printf '%s    ciba image present%s\n' "$C_DIM" "$C_RST"; return 0
  fi
  run_step "Building CIBA image (jib)" mvn_in_repo -f gravitee-am-ciba-delegated-service/pom.xml package jib:dockerBuild \
    || die "CIBA image build failed — see output above."
}

ensure_jdbc_drivers() {
  if [ ! -f "$DEV/plugins/jdbc/postgresql-jdbc.jar" ] || [ ! -f "$DEV/plugins/jdbc/r2dbc-postgresql.jar" ]; then
    log "Downloading PostgreSQL JDBC drivers"
    mkdir -p "$DEV/plugins/jdbc"
    sh "$DEV/scripts/download-jdbc.sh" || die "JDBC driver download failed"
  fi
}

# ---------------------------------------------------------------------------
# Pre-flight checks
# ---------------------------------------------------------------------------
check_docker() { docker info >/dev/null 2>&1 || die "Docker does not appear to be running. Start Docker Desktop and retry."; }

check_license() {
  # The compose overlay mounts this fixed path; --license copies a key into place.
  local mount="$DEV/license/gravitee-universe-v4.key"
  if [ "$LICENSE_FILE" != "$mount" ]; then
    [ -f "$LICENSE_FILE" ] || die "License file not found: $LICENSE_FILE"
    [ -d "$mount" ] && rm -rf "$mount"          # clear a stray bind-mount directory
    mkdir -p "$DEV/license"
    cp "$LICENSE_FILE" "$mount"
    ok "using license $LICENSE_FILE"
  fi
  if [ ! -f "$mount" ]; then
    # A docker bind-mount may have created an empty directory here when the key was absent.
    [ -d "$mount" ] && warn "$mount is a directory (stray bind-mount), not a license file."
    die "License not found at $mount
     Place your Gravitee EE license there (required for AM to start with full features),
     or pass --license <path> and it will be copied into place."
  fi
}

print_ready() {
  printf '\n%s── Access Management is up ──%s\n' "$C_GRN" "$C_RST"
  printf '  Gateway        http://localhost:8092\n'
  printf '  Management API http://localhost:8093/management\n'
  [ "$WANT_UI" -eq 1 ] && printf '  Console UI     http://localhost:4200\n'
  printf '  Mailbox        http://localhost:5080\n'
  printf '  Admin login    admin / adminadmin   (org/env: DEFAULT)\n'
  printf '%s  Stop with: ./local-stack.sh down%s\n\n' "$C_DIM" "$C_RST"
}

start_stack() { # start_stack <compose up args...>
  if compose up "$@"; then return 0; fi
  printf '\n%serr %s the stack did not start cleanly.%s\n' "$C_RED" "$C_RST" "$C_RST" >&2
  printf '     Inspect the failing service:   ./local-stack.sh logs management   (or gateway)\n' >&2
  printf '     To retry a clean rebuild:\n' >&2
  printf '         ./local-stack.sh up --build\n' >&2
  exit 1
}

# ---------------------------------------------------------------------------
# Commands
# ---------------------------------------------------------------------------
cmd_up() {
  check_docker
  check_license
  export_env
  build_compose_files
  build_service_list

  [ "$DB" = "psql" ] && ensure_jdbc_drivers

  if [ "$PULLED" -eq 1 ]; then
    local reg="${REGISTRY:-${AM_REGISTRY:-graviteeio}}"
    log "Pulled mode: ${reg}/am-*:${VERSION}"
    compose pull gateway management $( [ "$WANT_UI" -eq 1 ] && echo webui ) \
      || die "Image pull failed. Check the tag/registry and 'docker login ${reg}'."
    # CIBA has no published image; build it locally if requested.
    [ "$WANT_CIBA" -eq 1 ] && ensure_ciba
    log "Starting stack (no build)…"
    local up_args=(); [ "$DETACH" -eq 1 ] && up_args+=(-d); up_args+=(--no-build)
    start_stack "${up_args[@]}" ${SERVICES[@]+"${SERVICES[@]}"}
  else
    log "Source mode: building Access Management from the current code state"
    source_build
    [ "$WANT_CIBA" -eq 1 ] && ensure_ciba
    log "Building images & starting stack…"
    local up_args=(); [ "$DETACH" -eq 1 ] && up_args+=(-d); up_args+=(--build)
    start_stack "${up_args[@]}" ${SERVICES[@]+"${SERVICES[@]}"}
  fi

  [ "$DETACH" -eq 1 ] && print_ready
  return 0
}

cmd_down()   { export_env; docker compose "${ALL_FILES[@]}" down -v --remove-orphans; ok "stack down"; }
cmd_logs()   { export_env; docker compose "${ALL_FILES[@]}" logs -f ${LOGS_SVC:+$LOGS_SVC}; }
cmd_status() { export_env; docker compose "${ALL_FILES[@]}" ps; }
cmd_pull()   {
  [ -n "$VERSION" ] || die "pull needs --version <tag>"
  check_docker; export_env; build_compose_files
  local reg="${REGISTRY:-${AM_REGISTRY:-graviteeio}}"
  compose pull gateway management webui
  ok "pulled ${reg}/am-*:${VERSION}"
}

case "$COMMAND" in
  up)     cmd_up ;;
  down)   cmd_down ;;
  logs)   cmd_logs ;;
  status) cmd_status ;;
  pull)   cmd_pull ;;
  *)      usage; exit 1 ;;
esac
