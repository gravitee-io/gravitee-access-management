#!/bin/sh
# Convenience: print a JWT-SVID for the given SPIFFE ID and audience.
# Used by jest fixtures and manual smoke tests.
#
# Usage:
#   ./issue-svid.sh <spiffeID> <audience>
# Example:
#   ./issue-svid.sh spiffe://am.local/agent/billing http://gateway:8092/oauth/token
set -eu

SPIFFE_ID="${1:-spiffe://am.local/agent/billing}"
AUDIENCE="${2:-http://gateway:8092/oauth/token}"

docker compose \
    -f "$(dirname "$0")/../../docker-compose.yml" \
    -f "$(dirname "$0")/../../docker-compose.spire.yml" \
    exec -T spire-agent \
    /opt/spire/bin/spire-agent api fetch jwt \
    -socketPath /run/spire-agent/public/api.sock \
    -audience "${AUDIENCE}" \
    -spiffeID "${SPIFFE_ID}" \
    | awk '/^token\(spiffe/ { getline; gsub(/^[ \t]+|[ \t]+$/, "", $0); print $0; exit }'
