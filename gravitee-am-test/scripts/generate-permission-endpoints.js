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

/**
 * Regenerates the endpoint/permission table driven by endpoint-permission-sweep.jest.spec.ts.
 *
 *   node scripts/generate-permission-endpoints.js
 *
 * Every management operation documents the permission it requires in its OpenAPI description
 * ("User must have DOMAIN_USER[LIST] permission on ..."). Reading that gives an authoritative,
 * always-current list of what each endpoint is supposed to be guarded by, so a newly added
 * endpoint joins the sweep as soon as the spec is regenerated rather than whenever someone
 * remembers to extend a hand-written list.
 *
 * Only operations whose every path parameter can be filled with a real id are emitted — the
 * sweep must not provoke a refusal by quoting a resource that does not exist, because that is a
 * different denial to the one being tested.
 */

const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const SPEC = path.resolve(__dirname, '../../docs/mapi/openapi.yaml');
const OUT = path.resolve(__dirname, '../specs/management/permissions/fixtures/permission-endpoints.generated.ts');

/** Path params the sweep fixture can substitute with a genuinely existing resource. */
const FILLABLE = new Set(['{organizationId}', '{environmentId}', '{domain}', '{application}']);

/** Methods that carry no request body, so a refusal cannot be confused with payload validation. */
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
      // A handful of operations document several acceptable acls ("CREATE/UPDATE/DELETE"); the
      // first is enough to decide whether a caller holding none of them should be refused.
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
  return rows;
}

function render(rows) {
  const entries = rows
    .map(
      (row) =>
        `  { method: '${row.method}', route: '${row.route}', permission: '${row.permission}', summary: ${JSON.stringify(row.summary)} },`,
    )
    .join('\n');

  return `/*
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

/* eslint-disable */
// GENERATED FILE — do not edit by hand.
// Regenerate with: node scripts/generate-permission-endpoints.js
//
// Source: docs/mapi/openapi.yaml, from each operation's stated permission requirement.
// Only bodyless operations (GET, DELETE) whose path parameters are all fillable with real ids are
// included, so a refusal can only be about permissions.

export interface PermissionEndpoint {
  method: 'GET' | 'DELETE';
  /** OpenAPI route, with {organizationId}/{environmentId}/{domain}/{application} placeholders. */
  route: string;
  /** Flattened permission the operation documents as required, e.g. "domain_user_list". */
  permission: string;
  summary: string;
}

export const PERMISSION_ENDPOINTS: PermissionEndpoint[] = [
${entries}
];
`;
}

const rows = collect();
fs.writeFileSync(OUT, render(rows));
// The repo lints every .ts file, generated or not, so format the output here rather than leaving
// whoever regenerates it to discover a prettier failure afterwards.
require('child_process').execSync(`npx prettier --write ${JSON.stringify(OUT)}`, { stdio: 'ignore' });
const byMethod = rows.reduce((acc, row) => ({ ...acc, [row.method]: (acc[row.method] || 0) + 1 }), {});
console.log(`Wrote ${rows.length} endpoints to ${path.relative(process.cwd(), OUT)}`);
console.log('  by method   :', byMethod);
console.log('  permissions :', new Set(rows.map((row) => row.permission)).size);
