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

FROM node:18-alpine AS builder

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies (including dev dependencies for building)
RUN npm ci

# Build TypeScript
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

# Remove dev dependencies after build
RUN npm prune --production

FROM node:18-alpine

# Create non-root user
RUN addgroup -g 1001 mcpuser \
    && adduser -D -u 1001 mcpuser --ingroup mcpuser

WORKDIR /app

# Copy built artifacts and dependencies from builder stage
COPY --from=builder --chown=mcpuser:mcpuser /app/node_modules ./node_modules
COPY --from=builder --chown=mcpuser:mcpuser /app/dist ./dist
COPY --chown=mcpuser:mcpuser package.json ./

# Expose port
EXPOSE 3001

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD node -e "const http = require('http'); const req = http.get('http://localhost:3001/health', r => process.exit(r.statusCode === 200 ? 0 : 1)); req.on('error', () => process.exit(1));"

# Switch to non-root user
USER mcpuser

# Start server
CMD ["node", "dist/index.js"]
