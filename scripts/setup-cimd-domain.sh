#!/usr/bin/env bash
#
# setup-cimd-domain.sh — Provision an AM domain with CIMD enabled and a
# template application set as the CIMD template, idempotently.
#
# Steps:
#   1. Ensure target domain exists (creates if missing) and select it.
#   2. Ensure the template application exists (creates if missing).
#   3. Mark the application as a template (PATCH application; CLI gap).
#   4. Patch the domain's CIMD settings: enable CIMD, allow private/HTTP,
#      and bind it to the template application (PATCH domain; CLI gap).
#
# Usage:
#   scripts/setup-cimd-domain.sh                                # uses defaults
#   scripts/setup-cimd-domain.sh --domain dev --app cimd-template
#   scripts/setup-cimd-domain.sh --no-private --no-http --allowed-domains acme.example,*.partner.example
#
# Requirements: am CLI, jq, curl, an authenticated session (run `am login` first).

set -euo pipefail

DOMAIN_NAME="dev"
APP_NAME="cimd-template"
ALLOW_PRIVATE_IP="true"
ALLOW_UNSECURED_HTTP="true"
ALLOWED_DOMAINS=""
REDIRECT_URIS="https://example.com/cb"
CONFIG_FILE="${HOME}/.gravitee-am/config.json"

log()  { printf '\033[1;34m▸\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m⚠\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m✓\033[0m %s\n' "$*"; }
err()  { printf '\033[1;31m✘\033[0m %s\n' "$*" >&2; }

usage() {
  sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --domain)             DOMAIN_NAME="$2"; shift 2 ;;
    --app)                APP_NAME="$2"; shift 2 ;;
    --redirect-uris)      REDIRECT_URIS="$2"; shift 2 ;;
    --no-private)         ALLOW_PRIVATE_IP="false"; shift ;;
    --no-http)            ALLOW_UNSECURED_HTTP="false"; shift ;;
    --allowed-domains)    ALLOWED_DOMAINS="$2"; shift 2 ;;
    -h|--help)            usage 0 ;;
    *)                    err "Unknown argument: $1"; usage 1 ;;
  esac
done

# --- Pre-flight ---
command -v am   >/dev/null || { err "am CLI not on PATH"; exit 1; }
command -v jq   >/dev/null || { err "jq not on PATH"; exit 1; }
command -v curl >/dev/null || { err "curl not on PATH"; exit 1; }
[[ -f "$CONFIG_FILE" ]]    || { err "AM CLI config not found at $CONFIG_FILE — run 'am login' first"; exit 1; }

am health >/dev/null 2>&1 || { err "AM is unreachable. Start the local stack."; exit 1; }
am whoami >/dev/null 2>&1 || { err "AM session expired. Run 'am login' first."; exit 1; }

WORKSPACE=$(jq -r '.currentWorkspace // "default"' "$CONFIG_FILE")
BASE_URL=$(jq -r --arg w "$WORKSPACE" '.workspaces[$w].url' "$CONFIG_FILE")
ORG=$(jq -r --arg w "$WORKSPACE" '.workspaces[$w].organizationId // "DEFAULT"' "$CONFIG_FILE")
ENV=$(jq -r --arg w "$WORKSPACE" '.workspaces[$w].environmentId // "DEFAULT"' "$CONFIG_FILE")
TOKEN=$(jq -r --arg w "$WORKSPACE" '.auth[$w].accessToken // empty' "$CONFIG_FILE")
[[ -n "$TOKEN" ]] || { err "No access token in $CONFIG_FILE for workspace '$WORKSPACE'"; exit 1; }

API="${BASE_URL%/}/management/organizations/${ORG}/environments/${ENV}"

api_patch() {
  local path="$1" body="$2"
  curl -fsS -X PATCH \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${body}" \
    "${API}${path}"
}

# --- 1. Domain ---
log "Looking up domain '${DOMAIN_NAME}'…"
DOMAIN_ID=$(am --output json domain list 2>/dev/null \
  | jq -r --arg n "$DOMAIN_NAME" '(.domains // .) | (if type=="array" then . else .[]? end) | select(.name == $n) | .id' \
  | head -n1)

if [[ -z "$DOMAIN_ID" ]]; then
  log "Creating domain '${DOMAIN_NAME}'…"
  DOMAIN_ID=$(am --output json domain create --name "$DOMAIN_NAME" --description "CIMD test domain" 2>/dev/null \
    | jq -r '.id')
  am domain enable "$DOMAIN_ID" >/dev/null 2>&1 || true
  ok "Created domain ${DOMAIN_ID}"
else
  ok "Reusing existing domain ${DOMAIN_ID}"
fi

am set domain "$DOMAIN_ID" >/dev/null

# --- 2. Template application ---
log "Looking up application '${APP_NAME}'…"
APP_ID=$(am --output json --domain "$DOMAIN_ID" app list 2>/dev/null \
  | jq -r --arg n "$APP_NAME" '(.applications // .) | (if type=="array" then . else .[]? end) | select(.name == $n) | .id' \
  | head -n1)

if [[ -z "$APP_ID" ]]; then
  log "Creating template application '${APP_NAME}'…"
  APP_ID=$(am --output json --domain "$DOMAIN_ID" app create \
      --name "$APP_NAME" \
      --type web \
      --redirect-uris "$REDIRECT_URIS" \
      --description "CIMD template application (do not modify directly)" 2>/dev/null \
    | jq -r '.id')
  ok "Created application ${APP_ID}"
else
  ok "Reusing existing application ${APP_ID}"
fi

# --- 3. Mark application as template (CLI gap → curl) ---
warn "AM CLI GAP: marking application as template requires PATCH /applications/{id}"
warn "  Falling back to: curl PATCH ${API}/domains/${DOMAIN_ID}/applications/${APP_ID}"
api_patch "/domains/${DOMAIN_ID}/applications/${APP_ID}" '{"template": true}' >/dev/null
ok "Application marked as template"

# --- 4. CIMD settings (CLI gap → curl) ---
warn "AM CLI GAP: enabling CIMD requires PATCH /domains/{id} with oidc.cimdSettings"
warn "  Falling back to: curl PATCH ${API}/domains/${DOMAIN_ID}"

ALLOWED_DOMAINS_JSON='[]'
if [[ -n "$ALLOWED_DOMAINS" ]]; then
  ALLOWED_DOMAINS_JSON=$(printf '%s' "$ALLOWED_DOMAINS" \
    | jq -R 'split(",") | map(select(length>0) | gsub("^\\s+|\\s+$"; ""))')
fi

CIMD_BODY=$(jq -n \
  --argjson enabled  true \
  --argjson allowPriv "$ALLOW_PRIVATE_IP" \
  --argjson allowHttp "$ALLOW_UNSECURED_HTTP" \
  --arg     templateId "$APP_ID" \
  --argjson allowedDomains "$ALLOWED_DOMAINS_JSON" \
  '{ oidc: { cimdSettings: {
       enabled: $enabled,
       allowPrivateIpAddress: $allowPriv,
       allowUnsecuredHttpUri: $allowHttp,
       templateId: $templateId,
       allowedDomains: $allowedDomains
     } } }')

api_patch "/domains/${DOMAIN_ID}" "$CIMD_BODY" >/dev/null
ok "CIMD enabled on domain"

cat <<EOF

────────────────────────────────────────────────────────────────
CIMD setup complete.

  domain          ${DOMAIN_NAME} (${DOMAIN_ID})
  template app    ${APP_NAME} (${APP_ID})
  allow private   ${ALLOW_PRIVATE_IP}
  allow http      ${ALLOW_UNSECURED_HTTP}
  allowed hosts   ${ALLOWED_DOMAINS:-<any>}

Try it: in the management UI, create a new application on '${DOMAIN_NAME}',
choose CIMD on step 2, and paste a fixture URL e.g.
  http://host.docker.internal:1111/cimd/valid-with-client-name.json
────────────────────────────────────────────────────────────────
EOF
