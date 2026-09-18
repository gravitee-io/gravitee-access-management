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
  connectablePayload,
  dataPlanePayload,
  jdbcPayload,
  setupAutomationDataPlaneFixture,
} from './fixtures/automation-dataplane-fixture';
import { createDomain, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { getAuditApi } from '@management-commands/service/utils';
import { retryUntil } from '@utils-commands/retry';
import { uniqueName } from '@utils-commands/misc';
import { Audit } from '@management-models/Audit';

setup(200000);

/**
 * Data plane audits are recorded against the owning organization, never the environment, and the
 * summary they carry must never include the connection settings.
 */

let fixture: AutomationDataPlaneFixture;

// the JDBC reporter flushes in bulk; under a full-suite load an audit can take well over 10 s to land
const AUDIT_TIMEOUT = { timeoutMillis: 30_000, intervalMillis: 500 };

/**
 * The reporter writes asynchronously. `from` keeps earlier audits of the same type out of the page,
 * and `targetId` picks the entry for this test's plane out of anything a parallel worker wrote.
 */
const waitForAudit = async (type: string, targetId: string, from: number, expectedCount = 1): Promise<Audit[]> => {
  const page = await retryUntil(
    () => getAuditApi(fixture.adminToken).listOrganizationAudits({ organizationId: process.env.AM_DEF_ORG_ID, type, from, size: 50 }),
    (result) => (result.data ?? []).filter((audit) => audit.target?.id === targetId).length >= expectedCount,
    AUDIT_TIMEOUT,
  );
  return page.data.filter((audit) => audit.target?.id === targetId);
};

/**
 * Absence cannot be polled for. A control action of the same audit type is run after the one under
 * test; once the control's audit has landed, the reporter has flushed past `from`, and anything
 * written for `targetId` would be visible too.
 */
const expectNoAuditOnceFlushed = async (type: string, targetId: string, from: number, controlTargetId: string): Promise<void> => {
  await waitForAudit(type, controlTargetId, from);
  const page = await getAuditApi(fixture.adminToken).listOrganizationAudits({
    organizationId: process.env.AM_DEF_ORG_ID,
    type,
    from,
    size: 50,
  });
  expect((page.data ?? []).filter((audit) => audit.target?.id === targetId)).toEqual([]);
};

const expectNoSettings = (audit: Audit) => {
  const raw = JSON.stringify(audit);
  expect(raw).not.toContain(SECRET_PASSWORD);
  expect(raw).not.toContain(SECRET_USERNAME);
  expect(raw).not.toContain('configuration');
};

beforeAll(async () => {
  fixture = await setupAutomationDataPlaneFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Automation API data planes - create and update audits', () => {
  it('should record a successful creation against the organization, without the settings', async () => {
    const id = fixture.reserveId('audit-created');
    const from = Date.now();

    expect((await fixture.client.putDataPlane(dataPlanePayload(id))).status).toBe(200);

    const [audit] = await waitForAudit('DATA_PLANE_CREATED', id, from);
    expect(audit).toMatchObject({
      referenceType: 'organization',
      referenceId: process.env.AM_DEF_ORG_ID,
      actor: { alternativeId: process.env.AM_ADMIN_USERNAME },
      target: { id, type: 'DATA_PLANE', displayName: dataPlanePayload(id).name },
      outcome: { status: 'success' },
    });
    // the message is a JSON-patch of the connection summary
    expect(audit.outcome.message).toContain('"path":"/database"');
    expect(audit.outcome.message).toContain('"path":"/hosts"');
    expectNoSettings(audit);
  });

  it('should record an update with the fields that changed', async () => {
    const id = fixture.reserveId('audit-updated');
    expect((await fixture.client.putDataPlane(dataPlanePayload(id))).status).toBe(200);
    const from = Date.now();

    const renamed = dataPlanePayload(id, { name: 'Audited rename', gatewayUrl: 'https://gateway-audited.example.com' });
    expect((await fixture.client.putDataPlane(renamed)).status).toBe(200);

    const [audit] = await waitForAudit('DATA_PLANE_UPDATED', id, from);
    expect(audit).toMatchObject({ target: { id, displayName: 'Audited rename' }, outcome: { status: 'success' } });
    const patch = JSON.parse(audit.outcome.message);
    expect(patch).toEqual(
      expect.arrayContaining([
        { op: 'replace', path: '/name', value: 'Audited rename' },
        { op: 'replace', path: '/gatewayUrl', value: 'https://gateway-audited.example.com' },
      ]),
    );
    expectNoSettings(audit);
  });

  it('should record a refused type change as a failed update', async () => {
    const id = fixture.reserveId('audit-retype');
    expect((await fixture.client.putDataPlane(dataPlanePayload(id))).status).toBe(200);
    const from = Date.now();

    expect((await fixture.client.putDataPlane(jdbcPayload(id))).status).toBe(400);

    const [audit] = await waitForAudit('DATA_PLANE_UPDATED', id, from);
    expect(audit).toMatchObject({
      target: { id },
      outcome: { status: 'failure', message: `Once a data plane is created, 'type' cannot be changed [${id}]` },
    });
  });

  it('should not audit a request refused by validation', async () => {
    const from = Date.now();

    const refused = await fixture.client.putDataPlane(dataPlanePayload('default'));
    expect(refused.status).toBe(400);
    expect(refused.body.message).toContain('reserved');

    const control = fixture.reserveId('audit-control');
    expect((await fixture.client.putDataPlane(dataPlanePayload(control))).status).toBe(200);
    await expectNoAuditOnceFlushed('DATA_PLANE_CREATED', 'default', from, control);
  });
});

describe('Automation API data planes - delete audits', () => {
  it('should record a refused delete as a failure and the later delete as a success', async () => {
    const id = fixture.reserveId('audit-deleted');
    expect((await fixture.client.putDataPlane(connectablePayload(id))).status).toBe(200);
    const domain = await createDomain(fixture.adminToken, uniqueName('audit-bound-domain', true), 'Holds the plane', id);
    const from = Date.now();

    expect((await fixture.client.deleteDataPlane(id)).status).toBe(409);

    const [refused] = await waitForAudit('DATA_PLANE_DELETED', id, from);
    expect(refused).toMatchObject({
      target: { id, type: 'DATA_PLANE' },
      outcome: { status: 'failure', message: `Data plane [${id}] is used by at least one domain` },
    });

    await safeDeleteDomain(domain.id, fixture.adminToken);
    expect((await fixture.client.deleteDataPlane(id)).status).toBe(204);

    const audits = await waitForAudit('DATA_PLANE_DELETED', id, from, 2);
    expect(audits.map((audit) => audit.outcome.status).sort()).toEqual(['failure', 'success']);
  });

  it('should not audit the delete of a data plane that does not exist', async () => {
    const from = Date.now();

    expect((await fixture.client.deleteDataPlane('audit-never-existed')).status).toBe(204);

    const control = fixture.reserveId('audit-control-delete');
    expect((await fixture.client.putDataPlane(dataPlanePayload(control))).status).toBe(200);
    expect((await fixture.client.deleteDataPlane(control)).status).toBe(204);
    await expectNoAuditOnceFlushed('DATA_PLANE_DELETED', 'audit-never-existed', from, control);
  });
});
