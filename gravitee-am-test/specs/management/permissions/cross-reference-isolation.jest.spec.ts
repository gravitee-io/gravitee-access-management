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
import { performGet, performPatch } from '@gateway-commands/oauth-oidc-commands';
import { getOrganisationManagementUrl } from '@management-commands/service/utils';

setup();

let fixture: RbacFixture;

const headers = (token: string) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${token}` });
const domainPath = (domainId: string, suffix = '') => `/environments/${process.env.AM_DEF_ENV_ID}/domains/${domainId}${suffix}`;

beforeAll(async () => {
  fixture = await setupRbacFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

/**
 * Management API paths name a whole chain of resources — organization, environment, domain,
 * application. Authorising only the leaf would let a caller reach a resource by quoting a parent
 * they are entitled to instead of the one that actually owns it. `haveConsistentReferenceIds`
 * exists to reject that, and these cases are issued **as the administrator**: a caller with every
 * permission on both domains. Permissions therefore cannot account for the refusal — only the
 * inconsistent path can.
 */
describe('Cross-reference isolation - a resource cannot be reached through a parent that does not own it', () => {
  it('should serve an application addressed through its own domain', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      domainPath(fixture.domain.id, `/applications/${fixture.application.id}`),
      headers(fixture.adminToken),
    );

    expect(response.status).toBe(200);
    expect(response.body.id).toEqual(fixture.application.id);
  });

  it('should refuse an application addressed through an unrelated domain', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      domainPath(fixture.otherDomain.id, `/applications/${fixture.application.id}`),
      headers(fixture.adminToken),
    );

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });

  it('should refuse a sub-resource addressed through an unrelated domain', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      domainPath(fixture.otherDomain.id, `/applications/${fixture.application.id}/members`),
      headers(fixture.adminToken),
    );

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });
});

describe('Cross-reference isolation - a domain owner is confined to their own domain', () => {
  it('should let a DOMAIN_OWNER read the domain they are assigned to', async () => {
    const response = await performGet(getOrganisationManagementUrl(), domainPath(fixture.domain.id), headers(fixture.domainOwner.token));

    expect(response.status).toBe(200);
    expect(response.body.id).toEqual(fixture.domain.id);
  });

  it('should refuse a DOMAIN_OWNER reading a domain they have no membership on', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      domainPath(fixture.otherDomain.id),
      headers(fixture.domainOwner.token),
    );

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });

  it('should refuse a DOMAIN_OWNER modifying a domain they have no membership on', async () => {
    const response = await performPatch(
      getOrganisationManagementUrl(),
      domainPath(fixture.otherDomain.id),
      { description: 'modified by a domain owner from elsewhere' },
      headers(fixture.domainOwner.token),
    );

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });
});

describe('Cross-reference isolation - an application owner is confined to their own application', () => {
  it('should let an APPLICATION_OWNER read the application they are assigned to', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      domainPath(fixture.domain.id, `/applications/${fixture.application.id}`),
      headers(fixture.appOwner.token),
    );

    expect(response.status).toBe(200);
    expect(response.body.id).toEqual(fixture.application.id);
  });

  it('should refuse an APPLICATION_OWNER reading a sibling application in the same domain', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      domainPath(fixture.domain.id, `/applications/${fixture.otherApplication.id}`),
      headers(fixture.appOwner.token),
    );

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });

  it('should refuse an APPLICATION_OWNER modifying a sibling application in the same domain', async () => {
    const response = await performPatch(
      getOrganisationManagementUrl(),
      domainPath(fixture.domain.id, `/applications/${fixture.otherApplication.id}`),
      { description: 'modified by the owner of a different application' },
      headers(fixture.appOwner.token),
    );

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });
});
