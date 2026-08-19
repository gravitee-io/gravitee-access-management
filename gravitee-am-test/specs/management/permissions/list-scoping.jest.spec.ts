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
import { listDomains } from '@management-commands/domain-management-commands';
import { listApplications } from '@management-commands/application-management-commands';

setup(200000);

let fixture: RbacFixture;

beforeAll(async () => {
  fixture = await setupRbacFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

const domainIds = async (token: string) => (await listDomains(token, { size: 100 })).data.map((domain) => domain.id);
const applicationIds = async (token: string) =>
  (await listApplications(fixture.domain.id, token, { size: 100 })).data.map((application) => application.id);

/**
 * Listing is a second, separate authorisation path: rather than a single yes/no on one resource,
 * the resource set is narrowed to the ids the caller holds a membership on. A regression here does
 * not produce an error — it silently widens what a user can see, which is why each case asserts
 * both the presence of the permitted resource and the absence of the unpermitted one.
 */
describe('List scoping - domains are narrowed to those the caller is a member of', () => {
  it('should return every domain to an unrestricted administrator', async () => {
    const ids = await domainIds(fixture.adminToken);

    expect(ids).toContain(fixture.domain.id);
    expect(ids).toContain(fixture.otherDomain.id);
  });

  it('should return only the assigned domain to a DOMAIN_OWNER', async () => {
    const ids = await domainIds(fixture.domainOwner.token);

    expect(ids).toContain(fixture.domain.id);
    expect(ids).not.toContain(fixture.otherDomain.id);
  });

  it('should return only the parent domain to an APPLICATION_OWNER', async () => {
    // The membership cascade provisions DOMAIN_USER on the parent domain, so an application owner
    // can see that one domain — and must not gain sight of any other.
    const ids = await domainIds(fixture.appOwner.token);

    expect(ids).toContain(fixture.domain.id);
    expect(ids).not.toContain(fixture.otherDomain.id);
  });

  it('should refuse the domain list outright to a bare ORGANIZATION_USER', async () => {
    // ORGANIZATION_USER holds no DOMAIN[LIST], so the request is rejected before any narrowing.
    await expect(listDomains(fixture.orgUser.token, { size: 100 })).rejects.toMatchObject({
      response: { status: 403 },
      message: expect.stringContaining('Permission denied'),
    });
  });
});

describe('List scoping - applications are narrowed to those the caller is a member of', () => {
  it('should return every application in the domain to an unrestricted administrator', async () => {
    const ids = await applicationIds(fixture.adminToken);

    expect(ids).toContain(fixture.application.id);
    expect(ids).toContain(fixture.otherApplication.id);
  });

  it('should return only the assigned application to an APPLICATION_OWNER', async () => {
    const ids = await applicationIds(fixture.appOwner.token);

    expect(ids).toContain(fixture.application.id);
    expect(ids).not.toContain(fixture.otherApplication.id);
  });

  it('should return every application in the domain to a DOMAIN_OWNER', async () => {
    // A domain owner holds APPLICATION[READ] across the whole domain, so no narrowing applies and
    // both applications are visible. This is the counterpart to the case above.
    const ids = await applicationIds(fixture.domainOwner.token);

    expect(ids).toContain(fixture.application.id);
    expect(ids).toContain(fixture.otherApplication.id);
  });
});
