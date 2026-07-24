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

# --- build stage: compile TypeScript to plain JS ---
FROM node:22.23.1-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npx tsc -p tsconfig.json --noEmit false --outDir dist

# --- runtime stage: only production deps (ws) + compiled JS ---
FROM node:22.23.1-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --chown=node:node package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=build --chown=node:node /app/dist ./dist

# /app itself must be writable by 'node' too, so --state-file can create a new
# file under a relative path (e.g. ./cockpit-state.json) as the non-root user.
RUN chown node:node /app

EXPOSE 8085
USER node

# Args passed to `docker run`/compose `command:` are appended (e.g. --state-file /data/state.json)
ENTRYPOINT ["node", "dist/server.js"]
