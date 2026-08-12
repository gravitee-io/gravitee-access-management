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
import { RBAC_TEST, RbacFixture, setupRbacFixture } from './fixtures/rbac-fixture';
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { getOrganisationManagementUrl } from '@management-commands/service/utils';

setup();

let fixture: RbacFixture;

beforeAll(async () => {
  fixture = await setupRbacFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

const headers = (token: string) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${token}` });

const domainPath = (suffix = '') => `/environments/${process.env.AM_DEF_ENV_ID}/domains/${fixture.domain.id}${suffix}`;

/** Mirrors the default in domain-management-commands.ts, which does not export it. */
const dataPlaneId = () => process.env.AM_DOMAIN_DATA_PLANE_ID || 'default';

interface EndpointCase {
  name: string;
  path: () => string;
  body?: () => Record<string, unknown>;
}

const NEVER_CREATED = 'deny-by-default-should-never-exist';

/**
 * Read endpoints a bare ORGANIZATION_USER must not reach.
 *
 * The value of this sweep is breadth: an endpoint shipped without a permission check is invisible
 * to any per-feature test and shows up only here.
 *
 * Each case is anchored by first issuing the identical request as admin. Without that anchor a
 * mistyped path would answer 404 for everyone and the denial would look like it passed for the
 * right reason. The anchor proves the path exists and is not permission-blocked for a privileged
 * caller, so the org user's 403 can only be about permissions.
 */
const DENIED_READS: EndpointCase[] = [
  // --- organization tier -------------------------------------------------------------------
  { name: 'list organization users', path: () => '/users' },
  { name: 'list organization members', path: () => '/members' },
  { name: 'list organization entrypoints', path: () => '/entrypoints' },
  { name: 'list organization audits', path: () => '/audits' },
  { name: 'list organization reporters', path: () => '/reporters' },
  { name: 'list organization identity providers', path: () => '/identities' },
  // Gated by ORGANIZATION_SETTINGS[READ], which is distinct from the ORGANIZATION[READ] that
  // ORGANIZATION_USER holds — reading the organization does not extend to reading its settings.
  { name: 'read organization settings', path: () => '/settings' },

  // --- environment / domain tier -----------------------------------------------------------
  { name: 'read a domain', path: () => domainPath() },
  { name: 'list domain applications', path: () => domainPath('/applications') },
  { name: 'list domain users', path: () => domainPath('/users') },
  { name: 'list domain members', path: () => domainPath('/members') },
  { name: 'list domain certificates', path: () => domainPath('/certificates') },
  { name: 'list domain identity providers', path: () => domainPath('/identities') },
  { name: 'list domain scopes', path: () => domainPath('/scopes') },
  { name: 'list domain groups', path: () => domainPath('/groups') },
  { name: 'list domain roles', path: () => domainPath('/roles') },
  { name: 'list domain audits', path: () => domainPath('/audits') },
  { name: 'list domain factors', path: () => domainPath('/factors') },
];

/**
 * Write endpoints a bare ORGANIZATION_USER must not reach.
 *
 * These deliberately carry *valid* payloads. An invalid one is rejected by bean validation during
 * parameter binding — before the resource method and therefore before the permission check — so
 * the request would never exercise the authorization path at all. (Domain creation demonstrates
 * this: omitting `dataPlaneId` returns 400 to admin and org user alike.)
 *
 * They are not admin-anchored like the reads above, because issuing them as admin would genuinely
 * create the resource. Their anchor is the denial message itself: a mistyped path answers 404 and
 * carries no "Permission denied" body, so the message assertion still rules out a bad URL.
 */
const DENIED_WRITES: EndpointCase[] = [
  {
    name: 'create a domain',
    path: () => `/environments/${process.env.AM_DEF_ENV_ID}/domains`,
    body: () => ({ name: NEVER_CREATED, description: 'should never be created', dataPlaneId: dataPlaneId() }),
  },
  {
    name: 'create an organization role',
    path: () => '/roles',
    body: () => ({ name: NEVER_CREATED }),
  },
  {
    name: 'create an organization user',
    path: () => '/users',
    body: () => ({
      username: NEVER_CREATED,
      password: RBAC_TEST.USER_PASSWORD,
      firstName: 'Deny',
      lastName: 'Default',
      email: `${NEVER_CREATED}@test.com`,
      preRegistration: false,
    }),
  },
  {
    name: 'create an application',
    path: () => domainPath('/applications'),
    body: () => ({ name: NEVER_CREATED, type: 'WEB' }),
  },
];

/**
 * ORGANIZATION_USER is not permission-less — it ships with a small read-only allowance. Pinning it
 * matters as much as pinning the denials: if the built-in role were ever widened, the sweep above
 * would start failing without explaining why, and these cases state the intended baseline.
 */
const ALLOWED: EndpointCase[] = [
  { name: 'list environments', path: () => '/environments' },
  { name: 'list organization roles', path: () => '/roles' },
  { name: 'list organization groups', path: () => '/groups' },
  { name: 'list organization tags', path: () => '/tags' },
  { name: 'list data planes', path: () => `/environments/${process.env.AM_DEF_ENV_ID}/data-planes` },
];

describe('Deny by default - read endpoints refused to a bare ORGANIZATION_USER', () => {
  DENIED_READS.forEach((endpoint) => {
    it(`should refuse to ${endpoint.name}`, async () => {
      const asAdmin = await performGet(getOrganisationManagementUrl(), endpoint.path(), headers(fixture.adminToken));
      expect(asAdmin.status).not.toBe(404); // the endpoint exists
      expect(asAdmin.status).not.toBe(403); // and admin is not permission-blocked on it

      const asOrgUser = await performGet(getOrganisationManagementUrl(), endpoint.path(), headers(fixture.orgUser.token));
      expect(asOrgUser.status).toBe(403);
      expect(asOrgUser.body.message).toEqual('Permission denied');
    });
  });
});

describe('Deny by default - write endpoints refused to a bare ORGANIZATION_USER', () => {
  DENIED_WRITES.forEach((endpoint) => {
    it(`should refuse to ${endpoint.name}`, async () => {
      const response = await performPost(getOrganisationManagementUrl(), endpoint.path(), endpoint.body(), headers(fixture.orgUser.token));

      expect(response.status).toBe(403);
      expect(response.body.message).toEqual('Permission denied');
    });
  });
});

describe('Deny by default - the ORGANIZATION_USER read-only allowance', () => {
  ALLOWED.forEach((endpoint) => {
    it(`should permit a bare ORGANIZATION_USER to ${endpoint.name}`, async () => {
      const response = await performGet(getOrganisationManagementUrl(), endpoint.path(), headers(fixture.orgUser.token));
      expect(response.status).toBe(200);
    });
  });
});
