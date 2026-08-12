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
import { PERMISSION_ENDPOINTS, PermissionEndpoint } from './fixtures/permission-endpoints.generated';
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

setup();

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

/** See endpoint-permission-sweep for why each of these cannot be driven generically. */
const EXCLUDED: Record<string, string> = {
  '/organizations/{organizationId}/forms': 'requires a template query parameter',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/forms': 'requires a template query parameter',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/emails': 'requires a template query parameter',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/forms':
    'requires a template query parameter',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/emails':
    'requires a template query parameter',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/analytics': 'AM-7476 class: 500 before authorisation',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/{application}/analytics':
    'AM-7476 class: 500 before authorisation',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/applications/search/_cursor':
    'AM-7476 class: 500 before authorisation',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/protected-resources':
    'AM-7476: type query parameter parsed before the permission check',
  // INSTALLATION is only relevant to the PLATFORM tier, so it cannot be granted by an
  // organization-assignable role and this technique cannot reach it.
  '/platform/installation': 'PLATFORM-tier permission, not grantable at organization level',
  // Guarded by DOMAIN_FACTOR instead of DOMAIN_RESOURCE, so the documented permission does not
  // open it. Re-include once AM-7478 is fixed — asserting today's behaviour would enshrine it.
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/resources': 'AM-7478: guarded by the wrong permission',
};

/**
 * The OpenAPI description misnames the permission for these routes; each was found by this sweep
 * and confirmed against the guard in the resource class.
 *  - groups/tags are documented as ORGANIZATION[LIST] but check the resource-specific permission
 *  - entrypoints is documented as ORGANIZATION[LIST] but checks ORGANIZATION_ENTRYPOINT[LIST]
 *  - device-identifiers documents DOMAIN_DEVICE_IDENTIFIERS, which is not a permission at all —
 *    the enum constant is singular, and the plural form makes role updates fail outright (AM-7477)
 */
const DOCUMENTED_PERMISSION_OVERRIDES: Record<string, string> = {
  '/organizations/{organizationId}/groups': 'organization_group_list',
  '/organizations/{organizationId}/tags': 'organization_tag_list',
  '/organizations/{organizationId}/entrypoints': 'organization_entrypoint_list',
  '/organizations/{organizationId}/environments/{environmentId}/domains/{domain}/device-identifiers': 'domain_device_identifier_list',
};

const requiredPermission = (endpoint: PermissionEndpoint) => DOCUMENTED_PERMISSION_OVERRIDES[endpoint.route] ?? endpoint.permission;

const candidates = PERMISSION_ENDPOINTS.filter((endpoint) => endpoint.method === 'GET' && !EXCLUDED[endpoint.route]);

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
