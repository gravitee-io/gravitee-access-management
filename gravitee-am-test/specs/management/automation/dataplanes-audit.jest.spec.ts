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
import {
  AutomationDataPlaneFixture,
  SECRET_PASSWORD,
  SECRET_USERNAME,
  dataPlanePayload,
  setupAutomationDataPlaneFixture,
} from './fixtures/automation-dataplane-fixture';
import { getAuditApi } from '@management-commands/service/utils';
import { retryUntil } from '@utils-commands/retry';

setup(200000);

/**
 * The audit builder is covered by DataPlaneDefinitionServiceTest; what only a running stack can show
 * is that the reporter persists the organization audit and the audit API returns it.
 */

let fixture: AutomationDataPlaneFixture;

beforeAll(async () => {
  fixture = await setupAutomationDataPlaneFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Automation API data planes - audit', () => {
  it('should land a DATA_PLANE_CREATED audit on the organization that the audit API returns', async () => {
    const id = fixture.reserveId('audit-created');
    const from = Date.now();

    expect((await fixture.client.putDataPlane(dataPlanePayload(id))).status).toBe(200);

    // the reporter writes asynchronously (bulk flush on JDBC); `from` keeps earlier runs out of the page
    const page = await retryUntil(
      () =>
        getAuditApi(fixture.adminToken).listOrganizationAudits({
          organizationId: process.env.AM_DEF_ORG_ID,
          type: 'DATA_PLANE_CREATED',
          from,
          size: 50,
        }),
      (result) => (result.data ?? []).some((audit) => audit.target?.id === id),
      { timeoutMillis: 15_000, intervalMillis: 500 },
    );
    const audit = page.data.find((entry) => entry.target?.id === id);

    expect(audit).toMatchObject({
      referenceType: 'organization',
      referenceId: process.env.AM_DEF_ORG_ID,
      actor: { alternativeId: process.env.AM_ADMIN_USERNAME },
      target: { id, type: 'DATA_PLANE' },
      outcome: { status: 'success' },
    });
    // the audit carries the connection summary, never the settings
    const raw = JSON.stringify(audit);
    expect(raw).not.toContain(SECRET_PASSWORD);
    expect(raw).not.toContain(SECRET_USERNAME);
    expect(raw).not.toContain('configuration');
  });
});
