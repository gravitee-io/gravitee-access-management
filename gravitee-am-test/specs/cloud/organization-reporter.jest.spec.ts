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
import { waitForCockpitConnection } from '@cloud-commands/cockpit-commands';
import { listOrgReporters } from '@management-commands/reporter-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getAuditApi } from '@management-commands/service/utils';
import { retryUntil } from '@utils-commands/retry';
import { CloudOrganizationFixture, setupCloudOrganizationFixture } from './fixtures/cloud-organization-fixture';
import { setup } from '../test-fixture';

setup(120000);

const POLL = { timeoutMillis: 30000, intervalMillis: 1000 };

let fixture: CloudOrganizationFixture;

beforeAll(async () => {
  await waitForCockpitConnection();
  fixture = await setupCloudOrganizationFixture('cloud-org-reporter');
});

afterAll(async () => {
  await fixture?.cleanup();
});

describe('Audit reporting for a Cockpit-created organization', () => {
  it('creates a system reporter for the organization', async () => {
    const reporters = await retryUntil(
      () => listOrgReporters(fixture.accessToken, fixture.organizationId),
      (found) => found.length > 0,
      POLL,
    );

    expect(reporters).toHaveLength(1);
    expect(reporters[0].system).toBe(true);
    expect(reporters[0].enabled).toBe(true);
  });

  it('records and serves audits for the organization', async () => {
    const since = Date.now();
    const page = await retryUntil(
      async () => {
        await requestAdminAccessToken(fixture.organizationId);
        return getAuditApi(fixture.accessToken).listOrganizationAudits({
          organizationId: fixture.organizationId,
          type: 'USER_LOGIN',
          from: since,
          size: 1,
        });
      },
      (found) => found.data?.length > 0,
      POLL,
    );

    expect(page.data[0]).toMatchObject({
      type: 'USER_LOGIN',
      referenceType: 'organization',
      referenceId: fixture.organizationId,
      outcome: { status: 'success' },
    });
  });
});
