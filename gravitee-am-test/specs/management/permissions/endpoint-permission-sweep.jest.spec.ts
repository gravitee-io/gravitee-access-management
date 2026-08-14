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
import { RbacFixture, setupRbacFixture } from './fixtures/rbac-fixture';
import { PERMISSION_ENDPOINTS, PermissionEndpoint } from './fixtures/permission-endpoints.generated';
import {
  DOCUMENTED_PERMISSION_OVERRIDES,
  EXCLUDED_ROUTES,
  SUFFICIENCY_ONLY_EXCLUDED,
  requiredPermission,
} from './fixtures/permission-sweep-tables';
import { performDelete, performGet } from '@gateway-commands/oauth-oidc-commands';

setup(200000);

let fixture: RbacFixture;

const managementUrl = () => `${process.env.AM_MANAGEMENT_URL}/management`;
const headers = (token: string) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${token}` });

/** Substitutes the four path parameters with resources that genuinely exist. */
const fill = (route: string) =>
  route
    .replace('{organizationId}', process.env.AM_DEF_ORG_ID)
    .replace('{environmentId}', process.env.AM_DEF_ENV_ID)
    .replace('{domain}', fixture.domain.id)
    .replace('{application}', fixture.application.id);

/**
 * The read-only allowance the built-in ORGANIZATION_USER role ships with.
 *
 * Held here rather than read back from the API on purpose: deriving the expectation from the live
 * role would make this suite agree with whatever the role happens to grant, and silently widening
 * the default role is exactly the regression worth catching.
 */
const ORGANIZATION_USER_PERMISSIONS = [
  'organization_read',
  'organization_role_list',
  'organization_group_list',
  'organization_tag_list',
  'environment_list',
  'data_plane_list',
  'data_plane_read',
];

const isAllowedForOrganizationUser = (endpoint: PermissionEndpoint) => ORGANIZATION_USER_PERMISSIONS.includes(requiredPermission(endpoint));

const sweepable = PERMISSION_ENDPOINTS.filter((endpoint) => !EXCLUDED_ROUTES[endpoint.route]);

const denied = sweepable.filter((endpoint) => endpoint.method === 'GET' && !isAllowedForOrganizationUser(endpoint));
const allowed = sweepable.filter((endpoint) => endpoint.method === 'GET' && isAllowedForOrganizationUser(endpoint));
const destructive = sweepable.filter((endpoint) => endpoint.method === 'DELETE');

beforeAll(async () => {
  fixture = await setupRbacFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

/**
 * Every management operation states the permission it requires in its OpenAPI description, and
 * fixtures/permission-endpoints.generated.ts is built from those statements. Driving the whole set
 * means a newly added endpoint is covered as soon as the spec is regenerated, instead of whenever
 * someone remembers to extend a hand-written list — which is the only way this keeps pace with an
 * API of roughly 290 permission-guarded operations.
 *
 * Generated cases are used deliberately here (GUIDELINES §8 allows it for large sets of otherwise
 * identical tests): the route is in every test name, so a failure identifies its endpoint exactly.
 *
 * What this proves: every listed endpoint refuses a caller who lacks its permission, and the
 * default role's allowance is exactly the set above. What it cannot prove is that an endpoint
 * declares the *right* permission — if it were guarded by some other permission this caller also
 * lacks, the refusal would look identical. That needs a positive per-permission matrix.
 */
describe(`Endpoint permission sweep - ${denied.length} endpoints refuse a bare ORGANIZATION_USER`, () => {
  denied.forEach((endpoint) => {
    it(`should refuse ${endpoint.method} ${endpoint.route} (needs ${requiredPermission(endpoint)})`, async () => {
      const route = fill(endpoint.route);

      // Anchor: the same request as admin proves the route exists and is not blocked for a
      // privileged caller, so the refusal below can only be about permissions.
      const asAdmin = await performGet(managementUrl(), route, headers(fixture.adminToken));
      expect(asAdmin.status).not.toBe(404);
      expect(asAdmin.status).not.toBe(403);

      const asOrgUser = await performGet(managementUrl(), route, headers(fixture.orgUser.token));
      expect(asOrgUser.status).toBe(403);
      expect(asOrgUser.body.message).toEqual('Permission denied');
    });
  });
});

describe(`Endpoint permission sweep - ${allowed.length} endpoints within the default allowance`, () => {
  allowed.forEach((endpoint) => {
    it(`should permit ${endpoint.method} ${endpoint.route} (${requiredPermission(endpoint)})`, async () => {
      const response = await performGet(managementUrl(), fill(endpoint.route), headers(fixture.orgUser.token));

      // Success rather than 200 exactly: several of these answer 204 when the collection is empty,
      // which is what a freshly provisioned instance looks like. The claim being made is that the
      // default allowance opens the endpoint, and any non-error status carries it.
      expect(response.status).toBeLessThan(400);
    });
  });
});

/**
 * Deletions are swept without an administrator anchor for the obvious reason — issuing them as
 * admin would destroy the fixture. Instead each one asserts the resource is still there
 * afterwards, which is a stronger outcome than the status code alone.
 */
describe(`Endpoint permission sweep - ${destructive.length} deletions are refused and take effect on nothing`, () => {
  destructive.forEach((endpoint) => {
    it(`should refuse ${endpoint.method} ${endpoint.route} (needs ${endpoint.permission})`, async () => {
      const route = fill(endpoint.route);

      const asOrgUser = await performDelete(managementUrl(), route, headers(fixture.orgUser.token));
      expect(asOrgUser.status).toBe(403);
      expect(asOrgUser.body.message).toEqual('Permission denied');

      const stillThere = await performGet(managementUrl(), route, headers(fixture.adminToken));
      expect(stillThere.status).toBe(200);
    });
  });
});

/**
 * Guards the exclusions themselves: if an excluded route disappears or is renamed the list would
 * quietly stop matching anything, and the sweep would look complete while covering less.
 */
describe('Endpoint permission sweep - bookkeeping', () => {
  it('should exclude only routes that exist in the generated table', () => {
    const known = new Set(PERMISSION_ENDPOINTS.map((endpoint) => endpoint.route));
    const excluded = [...Object.keys(EXCLUDED_ROUTES), ...Object.keys(SUFFICIENCY_ONLY_EXCLUDED)];
    expect(excluded.filter((route) => !known.has(route))).toEqual([]);
  });

  it('should override permissions only for routes that exist in the generated table', () => {
    const known = new Set(PERMISSION_ENDPOINTS.map((endpoint) => endpoint.route));
    expect(Object.keys(DOCUMENTED_PERMISSION_OVERRIDES).filter((route) => !known.has(route))).toEqual([]);
  });
});
