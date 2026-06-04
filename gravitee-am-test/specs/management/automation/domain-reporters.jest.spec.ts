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

/**
 * Reporters are managed individually under a domain via the
 * /domains/{domainKey}/reporters endpoints — key-keyed, each PUT manages one reporter.
 */
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';
import { AutomationDomainFixture, setupAutomationDomainFixture } from './fixtures/automation-domain-fixture';
import { buildAutomationReporterDef, buildSystemAutomationDef } from './fixtures/automation-definitions';

setup(120000);

let fixture: AutomationDomainFixture;

beforeAll(async () => {
  fixture = await setupAutomationDomainFixture({ keyPrefix: 'autorep' });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Automation API - Reporters (resource under a domain)', () => {
  const reporterKey = uniqueName('autoaudit', true).toLowerCase();

  it('should expose no automation-managed reporters on a freshly-created domain', async () => {
    const response = await fixture.client.listReporters(fixture.domainKey);
    expect(response.status).toBe(200);
    expect(response.body).toEqual([]);
  });

  it('should create a reporter via PUT', async () => {
    const response = await fixture.client.putReporter(
      fixture.domainKey,
      buildAutomationReporterDef({ key: reporterKey }),
    );

    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(reporterKey);
    expect(response.body.type).toEqual('reporter-am-kafka');
    // internal id / operational flags are intentionally not surfaced
    expect(response.body.id).toBeUndefined();
    expect(response.body.managedBy).toBeUndefined();
    expect(response.body.system ?? false).toBe(false);
  });

  it('should round-trip the reporter on GET', async () => {
    const response = await fixture.client.getReporter(fixture.domainKey, reporterKey);
    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(reporterKey);
  });

  it('should list the reporter under the domain', async () => {
    const response = await fixture.client.listReporters(fixture.domainKey);
    expect(response.status).toBe(200);
    expect(response.body).toEqual([expect.objectContaining({ key: reporterKey })]);
  });

  it('should update the reporter via a second PUT (idempotent)', async () => {
    const response = await fixture.client.putReporter(
      fixture.domainKey,
      buildAutomationReporterDef({ key: reporterKey, name: 'Renamed reporter' }),
    );
    expect(response.status).toBe(200);
    expect(response.body.name).toEqual('Renamed reporter');
  });

  it('should reject an invalid key pattern (400)', async () => {
    const response = await fixture.client.putReporter(
      fixture.domainKey,
      buildAutomationReporterDef({ key: 'Invalid Key!' }),
    );
    expect(response.status).toBe(400);
  });

  it('should return 404 for an unknown reporter key', async () => {
    const response = await fixture.client.getReporter(fixture.domainKey, 'does-not-exist-xyz');
    expect(response.status).toBe(404);
  });

  it('should return 404 for reporters of an unknown domain', async () => {
    const response = await fixture.client.listReporters('no-such-domain-xyz');
    expect(response.status).toBe(404);
  });

  it('should delete the reporter', async () => {
    const del = await fixture.client.deleteReporter(fixture.domainKey, reporterKey);
    expect(del.status).toBe(204);

    const get = await fixture.client.getReporter(fixture.domainKey, reporterKey);
    expect(get.status).toBe(404);
  });
});

describe('Automation API - Reporters - payload validation', () => {
  it('should reject an unknown reporter type (400)', async () => {
    const response = await fixture.client.putReporter(fixture.domainKey, {
      ...buildAutomationReporterDef({ key: uniqueName('autobadtype', true).toLowerCase() }),
      type: 'reporter-am-does-not-exist',
    });
    expect(response.status).toBe(400);
  });

  it('should reject a configuration that is not valid JSON (400)', async () => {
    const response = await fixture.client.putReporter(fixture.domainKey, {
      ...buildAutomationReporterDef({ key: uniqueName('autobadcfg', true).toLowerCase() }),
      configuration: 'not-json',
    });
    expect(response.status).toBe(400);
  });

  it('should tolerate an unknown extra property in the configuration (200)', async () => {
    const base = buildAutomationReporterDef({ key: uniqueName('autoextracfg', true).toLowerCase() }) as {
      configuration: string;
    };
    const configuration = JSON.stringify({ ...JSON.parse(base.configuration), extraUnknownField: 'tolerated' });
    const response = await fixture.client.putReporter(fixture.domainKey, { ...base, configuration });
    expect(response.status).toBe(200);
  });
});

describe('Automation API - System reporter', () => {
  const systemKey = uniqueName('autosysrep', true).toLowerCase();
  const secondSystemKey = uniqueName('autosysrep2', true).toLowerCase();

  it('should create a system reporter from a minimal {key, system:true} payload', async () => {
    const response = await fixture.client.putReporter(
      fixture.domainKey,
      buildSystemAutomationDef(systemKey),
    );
    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(systemKey);
    expect(response.body.system).toBe(true);
  });

  it('should be idempotent on re-PUT of a system reporter (200, no update)', async () => {
    const response = await fixture.client.putReporter(
      fixture.domainKey,
      buildSystemAutomationDef(systemKey),
    );
    expect(response.status).toBe(200);
    expect(response.body.system).toBe(true);
    expect(response.body.key).toEqual(systemKey);
  });

  it('should reject a second system reporter (400)', async () => {
    const response = await fixture.client.putReporter(
      fixture.domainKey,
      buildSystemAutomationDef(secondSystemKey),
    );
    expect(response.status).toBe(400);
  });

  it('should reject flipping system on an existing reporter (400)', async () => {
    // the system reporter was created with system:true; PUT it again as non-system -> rejected (immutable)
    const response = await fixture.client.putReporter(
      fixture.domainKey,
      buildAutomationReporterDef({ key: systemKey, system: false }),
    );
    expect(response.status).toBe(400);
  });

  it('should delete the system reporter without a system guard', async () => {
    const del = await fixture.client.deleteReporter(fixture.domainKey, systemKey);
    expect(del.status).toBe(204);
  });
});
