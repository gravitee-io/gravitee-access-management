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
import { AutomationDataPlaneFixture, dataPlanePayload, setupAutomationDataPlaneFixture } from './fixtures/automation-dataplane-fixture';
import { authHeaders, automationUrl } from './fixtures/automation-client';
import { performPut } from '@gateway-commands/oauth-oidc-commands';

setup(200000);

/**
 * Field-level validation of the data plane definition: what is required, how long an id or name
 * may be, which ids are valid keys, and which parts of the read model are ignored on write.
 */

let fixture: AutomationDataPlaneFixture;

const itMongo = process.env.REPOSITORY_TYPE !== 'jdbc' ? it : it.skip;

const KEY_FORMAT_MESSAGE = /must be lowercase alphanumeric and hyphens, starting and ending with an alphanumeric character/;

const withoutField = (id: string, field: string) => {
  const payload: Record<string, unknown> = { ...dataPlanePayload(id) };
  delete payload[field];
  return payload;
};

beforeAll(async () => {
  fixture = await setupAutomationDataPlaneFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Automation API data planes - required fields and limits', () => {
  it.each([
    ['id', /must not be null/],
    ['name', /'name' is required/],
    ['type', /'type' is required/],
    ['configuration', /'configuration' is required and must be a JSON object/],
  ])('should refuse a definition without %s', async (field, message) => {
    const response = await fixture.client.putDataPlane(withoutField(fixture.reserveId('val-missing'), field));

    expect(response.status).toBe(400);
    expect(response.body.message).toMatch(message);
  });

  it('should refuse a blank name', async () => {
    const response = await fixture.client.putDataPlane(dataPlanePayload(fixture.reserveId('val-blank'), { name: '   ' }));

    expect(response.status).toBe(400);
    expect(response.body.message).toMatch(/'name' is required/);
  });

  // AM-7755: the JDBC columns are narrower (id 64, name 128) than the documented 255, so the
  // accepted-maximum case only holds on MongoDB until the limits agree
  itMongo('should accept an id of 255 characters', async () => {
    // the padded id is not the one the fixture reserved, so it is deleted here
    const longest = fixture.reserveId('val-len').padEnd(255, 'x');

    try {
      expect((await fixture.client.putDataPlane(dataPlanePayload(longest))).status).toBe(200);
    } finally {
      await fixture.client.deleteDataPlane(longest);
    }
  });

  it('should refuse an id of 256 characters', async () => {
    const response = await fixture.client.putDataPlane(dataPlanePayload(fixture.reserveId('val-len').padEnd(256, 'x')));

    expect(response.status).toBe(400);
    expect(response.body.message).toMatch(/size must be between 1 and 255/);
  });

  it('should refuse a name longer than 255 characters', async () => {
    const response = await fixture.client.putDataPlane(dataPlanePayload(fixture.reserveId('val-name'), { name: 'n'.repeat(256) }));

    expect(response.status).toBe(400);
    expect(response.body.message).toMatch(/size must be between 1 and 255/);
  });

  it.each(['B-two', 'b.one', 'b one', 'b_one', '-bone', 'bone-', 'bone '])('should refuse the id %j', async (id) => {
    const response = await fixture.client.putDataPlane(dataPlanePayload(id));

    expect(response.status).toBe(400);
    // the wording is tracked by AM-7739; only the rule is asserted
    expect(response.body.message).toMatch(KEY_FORMAT_MESSAGE);
  });
});

describe('Automation API data planes - write model', () => {
  it('should ignore read-only fields sent in the body', async () => {
    const id = fixture.reserveId('val-readonly');
    expect((await fixture.client.putDataPlane(dataPlanePayload(id))).status).toBe(200);
    const created = (await fixture.client.getDataPlane(id)).body;

    const response = await fixture.client.putDataPlane(
      dataPlanePayload(id, {
        organizationId: 'OTHER',
        environmentId: 'OTHER',
        database: 'hacked',
        hosts: ['x:1'],
        createdAt: '2000-01-01T00:00:00.000Z',
      }),
    );

    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({
      organizationId: process.env.AM_DEF_ORG_ID,
      environmentId: process.env.AM_DEF_ENV_ID,
      database: created.database,
      hosts: created.hosts,
      createdAt: created.createdAt,
    });
  });

  it('should refuse an unknown top-level field', async () => {
    const response = await fixture.client.putDataPlane({ ...dataPlanePayload(fixture.reserveId('val-unknown')), bogus: 'x' });

    expect(response.status).toBe(400);
    // the deserializer answers in plain text, not the JSON error envelope
    expect(response.text).toContain('Unrecognized field "bogus"');
  });

  it.each([
    ['organization', `/organizations/does-not-exist/environments/${process.env.AM_DEF_ENV_ID}/dataplanes`],
    ['environment', `/organizations/${process.env.AM_DEF_ORG_ID}/environments/does-not-exist/dataplanes`],
  ])('should refuse a PUT under an unknown %s before revealing anything', async (_scope, path) => {
    const response = await performPut(automationUrl(), path, dataPlanePayload('val-scope'), authHeaders(fixture.adminToken));

    // the permission gate runs first: no membership can exist on a reference that does not exist
    expect(response.status).toBe(403);
  });
});
