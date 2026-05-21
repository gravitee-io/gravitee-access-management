#!/bin/sh
# Bootstrap SPIRE for the AM local-stack:
#   1. Export the trust bundle so the agent can verify the server.
#   2. Generate a join token tied to the agent's SPIFFE ID.
#   3. Register workload entries so the agent can issue JWT-SVIDs to
#      processes inside the spire-agent container.
#
# Workload selectors use unix:uid:1000 because the spire-agent container
# runs as the unprivileged `spire` user (uid 1000); AM tests fetch SVIDs via
# `docker compose exec spire-agent spire-agent api fetch jwt ...`, which
# inherits that uid.
set -eu

SHARED=/run/spire/shared
SOCK="${SHARED}/server.sock"
SPIRE=/usr/local/bin/spire-server

# 1. Trust bundle so the agent can authenticate the server's SPIFFE certs.
"${SPIRE}" bundle show -socketPath "${SOCK}" > "${SHARED}/bootstrap.crt"

# 2. Join token. SPIRE prints "Token: <uuid>"; the token file holds just the value.
TOKEN="$("${SPIRE}" token generate \
    -socketPath "${SOCK}" \
    -spiffeID spiffe://am.local/agent | awk '/Token:/ { print $2 }')"
printf '%s' "${TOKEN}" > "${SHARED}/agent-token"

# 3. Workload entries.
#    - exact: spiffe://am.local/agent/billing
#    - pattern: spiffe://am.local/agent/test/<id> (any single segment)
#    - oidc-discovery-provider: needs *any* SVID to fetch the trust bundle.
#      It runs as uid 1000 (forced via docker-compose) and shares the PID
#      namespace with spire-agent so the unix attestor can readlink
#      /proc/<pid>/exe without ptrace privileges.
"${SPIRE}" entry create \
    -socketPath "${SOCK}" \
    -parentID spiffe://am.local/agent \
    -spiffeID spiffe://am.local/agent/billing \
    -selector unix:uid:1000 || true

"${SPIRE}" entry create \
    -socketPath "${SOCK}" \
    -parentID spiffe://am.local/agent \
    -spiffeID spiffe://am.local/agent/test/sample \
    -selector unix:uid:1000 || true

"${SPIRE}" entry create \
    -socketPath "${SOCK}" \
    -parentID spiffe://am.local/agent \
    -spiffeID spiffe://am.local/oidc-provider \
    -selector unix:uid:1000 || true

echo "spire bootstrap complete: token=${TOKEN}"
