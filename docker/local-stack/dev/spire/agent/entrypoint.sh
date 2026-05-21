#!/bin/sh
# After first successful attestation SPIRE agent persists its identity
# under data_dir, so the join token is only needed once. We still pass it
# every start; SPIRE silently ignores reuse if the agent is already
# attested.
set -eu
TOKEN="$(cat /run/spire/shared/agent-token)"
exec /usr/local/bin/spire-agent run \
    -config /run/spire/config/agent.conf \
    -joinToken "${TOKEN}"
