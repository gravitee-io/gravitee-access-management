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

import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { setup } from '../../test-fixture';
import { createPersona, Persona, RbacFixture, setupRbacFixture } from './fixtures/rbac-fixture';
import { PERMISSION_ENDPOINTS } from './fixtures/permission-endpoints.generated';
import { EXCLUDED_ROUTES, SUFFICIENCY_ONLY_EXCLUDED, requiredPermission } from './fixtures/permission-sweep-tables';
import {
  createCustomOrganizationRole,
  deleteOrganizationRole,
  updateOrganizationRole,
} from '@management-commands/role-management-commands';
import { addOrganizationMembership, userMembership } from '@management-commands/membership-management-commands';
import { deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import type { RoleEntity } from '@management-models/RoleEntity';

setup(200000);

let fixture: RbacFixture;
let holder: Persona;
let probeRole: RoleEntity;

const managementUrl = () => `${process.env.AM_MANAGEMENT_URL}/management`;
const headers = (token: string) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${token}` });

const fill = (route: string) =>
  route
    .replace('{organizationId}', process.env.AM_DEF_ORG_ID)
    .replace('{environmentId}', process.env.AM_DEF_ENV_ID)
    .replace('{domain}', fixture.domain.id)
    .replace('{application}', fixture.application.id);

const candidates = PERMISSION_ENDPOINTS.filter(
  (endpoint) => endpoint.method === 'GET' && !EXCLUDED_ROUTES[endpoint.route] && !SUFFICIENCY_ONLY_EXCLUDED[endpoint.route],
);

beforeAll(async () => {
  fixture = await setupRbacFixture();
  holder = await createPersona(fixture.adminToken, 'sufficiency');

  // One organization-assignable role, rewritten before each case to carry exactly one permission.
  // Organization level is chosen because a grant there is consulted for checks at every tier below,
  // so a single membership can stand in for whichever tier the endpoint belongs to.
  probeRole = await createCustomOrganizationRole(fixture.adminToken, uniqueName('sufficiency-probe', true), 'ORGANIZATION', []);
  await addOrganizationMembership(fixture.adminToken, userMembership(holder.userId, probeRole.id));
});

afterAll(async () => {
  if (holder) {
    await deleteOrganisationUser(fixture.adminToken, holder.userId).catch(() => undefined);
  }
  if (probeRole) {
    await deleteOrganizationRole(fixture.adminToken, probeRole.id).catch(() => undefined);
  }
  if (fixture) {
    await fixture.cleanup();
  }
});

/**
 * The counterpart to endpoint-permission-sweep, which proves only that a caller *without* an
 * endpoint's permission is refused. That is satisfied just as well by the wrong guard: if an
 * endpoint were changed to check some other permission the caller also lacks, the refusal would
 * look identical and every negative test would stay green.
 *
 * Here the holder is granted exactly one permission — the one the endpoint documents — and nothing
 * else. Reaching the endpoint therefore demonstrates that this specific permission is what opens
 * it. Together the two sweeps pin each endpoint to its own permission from both directions.
 *
 * The role is rewritten in place rather than a role created per endpoint: permission resolution
 * reads roles per request and caches only parent references, so an update takes effect immediately.
 */
describe(`Permission sufficiency - ${candidates.length} endpoints open to exactly their own permission`, () => {
  candidates.forEach((endpoint) => {
    it(`should admit ${requiredPermission(endpoint)} alone to ${endpoint.route}`, async () => {
      const permission = requiredPermission(endpoint);
      await updateOrganizationRole(fixture.adminToken, probeRole.id, { name: probeRole.name, permissions: [permission] });

      const response = await performGet(managementUrl(), fill(endpoint.route), headers(holder.token));

      // Success, not a specific code: several of these answer 204 when the collection is empty.
      // What matters is that the single granted permission opened the endpoint rather than being
      // refused, which is precisely the claim a negative sweep cannot make.
      expect(response.status).toBeLessThan(400);
    });
  });
});
