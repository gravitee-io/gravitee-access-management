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
import { afterAll, afterEach, beforeAll, describe, expect, it } from '@jest/globals';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';
import { AutomationReporterFixture, setupAutomationReporterFixture } from './fixtures/automation-reporter-fixture';
import {
  DEFAULT_REPORTER_ATTRIBUTE_MAPPINGS,
  buildAutomationReporterDef,
  buildSystemAutomationDef,
} from './fixtures/automation-definitions';

setup(120000);

let fixture: AutomationReporterFixture;

beforeAll(async () => {
  fixture = await setupAutomationReporterFixture({ keyPrefix: 'autorep' });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

afterEach(async () => {
  await fixture.cleanUpReporters();
});

describe('Automation API - Reporters (resource under a domain)', () => {
  it('should list no reporters when none exist', async () => {
    const response = await fixture.client.listReporters(fixture.domainKey);
    expect(response.status).toBe(200);
    expect(response.body).toEqual([]);
  });

  it('should create a reporter via PUT', async () => {
    const { key, response } = await fixture.putNewReporter();

    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(key);
    expect(response.body.type).toEqual('reporter-am-kafka');
    expect(response.body.attributeMappings).toEqual(DEFAULT_REPORTER_ATTRIBUTE_MAPPINGS);
    // internal id / operational flags are intentionally not surfaced
    expect(response.body.id).toBeUndefined();
    expect(response.body.managedBy).toBeUndefined();
    expect(response.body.system ?? false).toBe(false);
  });

  it('should round-trip the reporter on GET', async () => {
    const { key } = await fixture.putNewReporter();

    const response = await fixture.client.getReporter(fixture.domainKey, key);
    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(key);
    expect(response.body.attributeMappings).toEqual(DEFAULT_REPORTER_ATTRIBUTE_MAPPINGS);
  });

  it('should list the reporter under the domain', async () => {
    const { key } = await fixture.putNewReporter();

    const response = await fixture.client.listReporters(fixture.domainKey);
    expect(response.status).toBe(200);
    expect(response.body).toEqual([
      expect.objectContaining({ key, attributeMappings: DEFAULT_REPORTER_ATTRIBUTE_MAPPINGS }),
    ]);
  });

  it('should update the reporter via a second PUT (idempotent)', async () => {
    const { key } = await fixture.putNewReporter();
    const remapped = [{ expression: "{#context.attributes['user'].id}", exportedName: 'user_id' }];

    const response = await fixture.putReporter(key, { name: 'Renamed reporter', attributeMappings: remapped });

    expect(response.status).toBe(200);
    expect(response.body.name).toEqual('Renamed reporter');
    expect(response.body.attributeMappings).toEqual(remapped);
  });

  it('should clear the mappings when a later definition omits them', async () => {
    // the definition is the desired state, so an omitted field declares "none" rather than "leave as-is"
    const { key } = await fixture.putNewReporter();

    const response = await fixture.putReporter(key, { attributeMappings: null });

    expect(response.status).toBe(200);
    expect(response.body.attributeMappings ?? []).toEqual([]);

    const fetched = await fixture.client.getReporter(fixture.domainKey, key);
    expect(fetched.body.attributeMappings ?? []).toEqual([]);
  });

  it('should clear the mappings when a later definition supplies an empty list', async () => {
    const { key } = await fixture.putNewReporter();

    const response = await fixture.putReporter(key, { attributeMappings: [] });

    expect(response.status).toBe(200);
    expect(response.body.attributeMappings ?? []).toEqual([]);
  });

  it('should reject changing the type of an existing reporter (400)', async () => {
    const { key } = await fixture.putNewReporter();

    const response = await fixture.client.putReporter(fixture.domainKey, {
      ...buildAutomationReporterDef({ key }),
      type: 'reporter-am-file',
    });
    expect(response.status).toBe(400);
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
    const { key } = await fixture.putNewReporter();

    const del = await fixture.client.deleteReporter(fixture.domainKey, key);
    expect(del.status).toBe(204);

    const get = await fixture.client.getReporter(fixture.domainKey, key);
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

  it('should reject a mapping whose expression is not wrapped in braces (400)', async () => {
    const { response } = await fixture.putNewReporter({
      attributeMappings: [{ expression: "#context.attributes['user'].id", exportedName: 'user_id' }],
    });
    expect(response.status).toBe(400);
  });

  it('should reject two mappings exporting the same name (400)', async () => {
    const { response } = await fixture.putNewReporter({
      attributeMappings: [
        { expression: "{#context.attributes['user'].id}", exportedName: 'user_sub' },
        { expression: "{#context.attributes['user'].additionalInformation['sub']}", exportedName: 'user_sub' },
      ],
    });
    expect(response.status).toBe(400);
  });

  it('should tolerate an unknown extra property in the configuration (200)', async () => {
    const key = uniqueName('autoextracfg', true).toLowerCase();
    fixture.trackReporter(key);
    const base = buildAutomationReporterDef({ key }) as { configuration: string };
    const configuration = JSON.stringify({ ...JSON.parse(base.configuration), extraUnknownField: 'tolerated' });
    const response = await fixture.client.putReporter(fixture.domainKey, { ...base, configuration });
    expect(response.status).toBe(200);
  });
});

describe('Automation API - System reporter', () => {
  it('should create a system reporter from a minimal {key, system:true} payload', async () => {
    const { key, response } = await fixture.putNewSystemReporter();
    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(key);
    expect(response.body.system).toBe(true);
  });

  it('should be idempotent on re-PUT of a system reporter (200, no update)', async () => {
    const { key } = await fixture.putNewSystemReporter();

    const response = await fixture.client.putReporter(fixture.domainKey, buildSystemAutomationDef(key));
    expect(response.status).toBe(200);
    expect(response.body.system).toBe(true);
    expect(response.body.key).toEqual(key);
  });

  it('should reject a second system reporter (400)', async () => {
    await fixture.putNewSystemReporter();

    const response = await fixture.client.putReporter(
      fixture.domainKey,
      buildSystemAutomationDef(uniqueName('autosysrep2', true).toLowerCase()),
    );
    expect(response.status).toBe(400);
  });

  it('should reject flipping system on an existing reporter (400)', async () => {
    const { key } = await fixture.putNewSystemReporter();

    // the reporter was created with system:true; PUT it again as non-system -> rejected (immutable)
    const response = await fixture.client.putReporter(
      fixture.domainKey,
      buildAutomationReporterDef({ key, system: false }),
    );
    expect(response.status).toBe(400);
  });

  it('should ignore mappings declared on a system reporter', async () => {
    // name, type and configuration are already ignored for a system reporter; mappings join them because
    // the internal audit store it writes to has a fixed record shape
    const { key, response } = await fixture.putNewSystemReporter({
      attributeMappings: [{ expression: "{#context.attributes['user'].id}", exportedName: 'user_id' }],
    });

    expect(response.status).toBe(200);
    expect(response.body.system).toBe(true);
    expect(response.body.attributeMappings ?? []).toEqual([]);

    const fetched = await fixture.client.getReporter(fixture.domainKey, key);
    expect(fetched.body.attributeMappings ?? []).toEqual([]);
  });

  it('should delete the system reporter without a system guard', async () => {
    const { key } = await fixture.putNewSystemReporter();

    const del = await fixture.client.deleteReporter(fixture.domainKey, key);
    expect(del.status).toBe(204);
  });
});
