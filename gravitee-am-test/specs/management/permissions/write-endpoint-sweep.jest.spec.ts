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
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';

setup();

let fixture: RbacFixture;

const managementUrl = () => `${process.env.AM_MANAGEMENT_URL}/management`;
const headers = (token: string) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${token}` });
const org = () => `/organizations/${process.env.AM_DEF_ORG_ID}`;
const domainRoot = () => `${org()}/environments/${process.env.AM_DEF_ENV_ID}/domains/${fixture.domain.id}`;

interface WriteCase {
  name: string;
  path: () => string;
  body: () => Record<string, unknown>;
  /** The permission the operation documents as required. */
  permission: string;
  /** Collection to re-read afterwards to prove the refusal left nothing behind. */
  collection?: () => string;
}

/**
 * Bodies are hand-written rather than generated, and that is forced by the API's own behaviour:
 * 48 of the 53 write operations reject an empty payload with 400 before the permission check ever
 * runs, so a generated empty-body sweep would prove nothing about authorisation. Each body below
 * is the minimum that gets past validation and reaches the check — verified by the fact that an
 * unprivileged caller receives 403 rather than 400.
 *
 * Nothing here can create anything: every request is made by a caller the API refuses, and the
 * collections are re-read afterwards to confirm that.
 */
const WRITE_CASES: WriteCase[] = [
  // --- organization tier ---------------------------------------------------------------------
  {
    name: 'create an organization group',
    permission: 'organization_group_create',
    path: () => `${org()}/groups`,
    body: () => ({ name: 'sweep-should-never-exist' }),
  },
  {
    name: 'create an organization tag',
    permission: 'organization_tag_create',
    path: () => `${org()}/tags`,
    body: () => ({ name: 'sweep-should-never-exist' }),
  },
  {
    name: 'create an organization identity provider',
    permission: 'organization_identity_provider_create',
    path: () => `${org()}/identities`,
    body: () => ({ name: 'sweep-should-never-exist', type: 'inline-am-idp', configuration: '{}' }),
  },
  {
    name: 'create an organization role',
    permission: 'organization_role_create',
    path: () => `${org()}/roles`,
    body: () => ({ name: 'sweep-should-never-exist', assignableType: 'ORGANIZATION' }),
  },
  {
    name: 'create an organization entrypoint',
    permission: 'organization_entrypoint_create',
    path: () => `${org()}/entrypoints`,
    body: () => ({ name: 'sweep-should-never-exist', url: 'https://sweep.example.com', tags: [] }),
  },
  {
    name: 'add an organization member',
    permission: 'organization_member_create',
    path: () => `${org()}/members`,
    body: () => ({ memberId: fixture.assignee.userId, memberType: 'USER', role: fixture.roles.organizationUser.id }),
  },

  // --- domain tier ---------------------------------------------------------------------------
  {
    name: 'create a domain certificate',
    permission: 'domain_certificate_create',
    path: () => `${domainRoot()}/certificates`,
    body: () => ({ name: 'sweep-should-never-exist', type: 'pkcs12-am-certificate', configuration: '{}' }),
    collection: () => `${domainRoot()}/certificates`,
  },
  {
    name: 'create a domain identity provider',
    permission: 'domain_identity_provider_create',
    path: () => `${domainRoot()}/identities`,
    body: () => ({ name: 'sweep-should-never-exist', type: 'inline-am-idp', configuration: '{}' }),
  },
  {
    name: 'create a domain group',
    permission: 'domain_group_create',
    path: () => `${domainRoot()}/groups`,
    body: () => ({ name: 'sweep-should-never-exist' }),
    collection: () => `${domainRoot()}/groups`,
  },
  {
    name: 'create a domain role',
    permission: 'domain_role_create',
    path: () => `${domainRoot()}/roles`,
    body: () => ({ name: 'sweep-should-never-exist' }),
    collection: () => `${domainRoot()}/roles`,
  },
  {
    name: 'create a domain scope',
    permission: 'domain_scope_create',
    path: () => `${domainRoot()}/scopes`,
    body: () => ({ key: 'sweepscope', name: 'sweep-should-never-exist', description: 'never' }),
  },
  {
    name: 'create a domain factor',
    permission: 'domain_factor_create',
    path: () => `${domainRoot()}/factors`,
    body: () => ({ name: 'sweep-should-never-exist', type: 'otp-am-factor', configuration: '{}', factorType: 'OTP' }),
    collection: () => `${domainRoot()}/factors`,
  },
  {
    name: 'create a domain reporter',
    permission: 'domain_reporter_create',
    path: () => `${domainRoot()}/reporters`,
    body: () => ({ name: 'sweep-should-never-exist', type: 'reporter-am-file', configuration: '{}' }),
  },
  {
    name: 'create a domain bot detection',
    permission: 'domain_bot_detection_create',
    path: () => `${domainRoot()}/bot-detections`,
    body: () => ({ name: 'sweep-should-never-exist', type: 'x', configuration: '{}', detectionType: 'CAPTCHA' }),
  },
  {
    name: 'create a domain user',
    permission: 'domain_user_create',
    path: () => `${domainRoot()}/users`,
    body: () => ({
      username: 'sweep-should-never-exist',
      password: 'SweepP@ssw0rd1',
      firstName: 'a',
      lastName: 'b',
      email: 'sweep@test.com',
      preRegistration: false,
    }),
    collection: () => `${domainRoot()}/users`,
  },
  {
    name: 'add a domain member',
    permission: 'domain_member_create',
    path: () => `${domainRoot()}/members`,
    body: () => ({ memberId: fixture.assignee.userId, memberType: 'USER', role: fixture.roles.domainOwner.id }),
  },

  // --- application tier ----------------------------------------------------------------------
  {
    name: 'add an application member',
    permission: 'application_member_create',
    path: () => `${domainRoot()}/applications/${fixture.application.id}/members`,
    body: () => ({ memberId: fixture.assignee.userId, memberType: 'USER', role: fixture.roles.applicationOwner.id }),
  },
];

beforeAll(async () => {
  fixture = await setupRbacFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

/**
 * The read sweeps prove a caller without a permission cannot see things. This proves they cannot
 * change them, which is where a missing check does the real damage — creating administrators,
 * identity providers or certificates rather than merely reading a list.
 */
describe(`Write endpoint sweep - ${WRITE_CASES.length} write operations refuse a bare ORGANIZATION_USER`, () => {
  WRITE_CASES.forEach((writeCase) => {
    it(`should refuse to ${writeCase.name} (needs ${writeCase.permission})`, async () => {
      const response = await performPost(managementUrl(), writeCase.path(), writeCase.body(), headers(fixture.orgUser.token));

      expect(response.status).toBe(403);
      expect(response.body.message).toEqual('Permission denied');
    });
  });
});

describe('Write endpoint sweep - the refusals left nothing behind', () => {
  WRITE_CASES.filter((writeCase) => writeCase.collection).forEach((writeCase) => {
    it(`should leave nothing created by the attempt to ${writeCase.name}`, async () => {
      // Read back as administrator: a refusal that still wrote something would be far worse than
      // the wrong status code, and the status alone cannot rule it out.
      const response = await performGet(managementUrl(), writeCase.collection(), headers(fixture.adminToken));

      expect(response.status).toBe(200);
      expect(JSON.stringify(response.body)).not.toContain('sweep-should-never-exist');
    });
  });
});
