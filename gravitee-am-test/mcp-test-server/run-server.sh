#!/bin/bash
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

# Script to run MCP Test Server locally

set -e

PORT=${PORT:-3001}
AM_GATEWAY_URL=${AM_GATEWAY_URL:-http://localhost:8092}
DOMAIN_HRID=${DOMAIN_HRID:-test}
AUTHZEN_URL=${AUTHZEN_URL:-http://localhost:8092}
LOG_LEVEL=${LOG_LEVEL:-debug}

echo "🚀 Starting MCP Test Server"
echo "   PORT: $PORT"
echo "   AM_GATEWAY_URL: $AM_GATEWAY_URL"
echo "   DOMAIN_HRID: $DOMAIN_HRID"
echo "   LOG_LEVEL: $LOG_LEVEL"
echo ""

# Build if needed
if [ ! -d "dist" ]; then
  echo "Building TypeScript..."
  npm run build
fi

# Start server
PORT=$PORT \
AM_GATEWAY_URL=$AM_GATEWAY_URL \
DOMAIN_HRID=$DOMAIN_HRID \
AUTHZEN_URL=$AUTHZEN_URL \
LOG_LEVEL=$LOG_LEVEL \
node dist/index.js
