/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const SPEC = path.resolve(__dirname, '../../docs/mapi/openapi.yaml');

const MIN_ENDPOINTS = 51;

/** Path parameters the sweep fixture fills with ids of resources that exist. */
const FILLABLE = new Set(['{organizationId}', '{environmentId}', '{domain}', '{application}']);

const BODYLESS = new Set(['GET', 'DELETE']);

const REQUIREMENT = /User must have (?:the )?([A-Z_]+)\[([A-Z/]+)\]/;

function collect() {
  const spec = yaml.load(fs.readFileSync(SPEC, 'utf8'));
  const rows = [];

  for (const [route, operations] of Object.entries(spec.paths || {})) {
    for (const [method, operation] of Object.entries(operations || {})) {
      const verb = method.toUpperCase();
      if (!BODYLESS.has(verb)) continue;

      const requirement = REQUIREMENT.exec((operation && operation.description) || '');
      if (!requirement) continue;

      const params = route.match(/\{[^}]+\}/g) || [];
      if (!params.every((param) => FILLABLE.has(param))) continue;

      const [, permission, acls] = requirement;
      // Some descriptions list several ACLs separated by "/"; only the first is kept.
      const acl = acls.split('/')[0];

      rows.push({
        method: verb,
        route,
        permission: `${permission.toLowerCase()}_${acl.toLowerCase()}`,
        summary: (operation.summary || '').trim(),
      });
    }
  }

  rows.sort((a, b) => a.route.localeCompare(b.route) || a.method.localeCompare(b.method));

  const usable = rows.length >= MIN_ENDPOINTS && rows.every((row) => row.route && row.permission);
  if (!usable) {
    throw new Error(
      `OpenAPI permission table is unusable (${rows.length} rows from ${SPEC}). ` +
        `Expected at least ${MIN_ENDPOINTS} GET/DELETE operations with a documented permission and fillable path parameters.`,
    );
  }

  return rows;
}

module.exports = { collect };
