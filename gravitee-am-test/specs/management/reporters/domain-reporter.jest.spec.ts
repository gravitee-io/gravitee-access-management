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

import { afterAll, beforeAll, expect } from '@jest/globals';
import { Reporter } from '@management-models/Reporter';
import {
  deleteDomainReporter,
  getDomainReporter,
  listDomainReporters,
  updateDomainReporter,
} from '@management-commands/reporter-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { DomainReporterFixture, setupDomainReporterFixture } from './fixtures/domain-reporter-fixture';
import { DEFAULT_ATTRIBUTE_MAPPINGS } from './fixtures/kafka-reporter-config-helper';
import { setup } from '../../test-fixture';

setup();

let fixture: DomainReporterFixture;

beforeAll(async () => {
  fixture = await setupDomainReporterFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Domain Kafka Reporter CRUD', () => {
  describe('Create', () => {
    it('should create a reporter with all fields', async () => {
      const topic = uniqueName('audit-topic', true);
      const reporter: Reporter = await fixture.createReporter({
        name: 'test-kafka-reporter',
        configuration: fixture.kafkaConfig({ topic, acks: '1', auditTypes: [] }),
      });

      expect(reporter.id).toEqual(expect.any(String));
      expect(reporter.type).toEqual('reporter-am-kafka');
      expect(reporter.name).toEqual('test-kafka-reporter');
      expect(reporter.enabled).toBe(true);
      expect(reporter.attributeMappings).toEqual(DEFAULT_ATTRIBUTE_MAPPINGS);

      const config = JSON.parse(reporter.configuration);
      expect(config.bootstrapServers).toEqual(expect.any(String));
      expect(config.topic).toEqual(topic);
      expect(config.acks).toEqual('1');
      expect(config.auditTypes).toEqual([]);
    });

    it('should create a reporter that exports no additional attributes', async () => {
      const reporter: Reporter = await fixture.createReporter({ attributeMappings: [] });

      expect(reporter.attributeMappings ?? []).toEqual([]);
    });

    it('should create a reporter with auditTypes filter', async () => {
      const reporter: Reporter = await fixture.createReporter({
        name: 'test-kafka-filtered-reporter',
        configuration: fixture.kafkaConfig({ auditTypes: ['USER_LOGIN', 'USER_CREATED'] }),
      });

      const config = JSON.parse(reporter.configuration);
      expect(config.auditTypes).toEqual(['USER_LOGIN', 'USER_CREATED']);
    });

    it('should create a disabled reporter', async () => {
      const reporter: Reporter = await fixture.createReporter({ name: 'test-kafka-disabled-reporter', enabled: false });

      expect(reporter.enabled).toBe(false);
    });
  });

  describe('Read', () => {
    let createdId: string;

    beforeAll(async () => {
      const reporter = await fixture.createReporter({ name: 'test-kafka-read-reporter' });
      createdId = reporter.id;
    });

    it('should get reporter by ID', async () => {
      const reporter: Reporter = await getDomainReporter(fixture.domain.id, fixture.accessToken, createdId);
      expect(reporter.id).toEqual(createdId);
      expect(reporter.name).toEqual('test-kafka-read-reporter');
      expect(reporter.attributeMappings).toEqual(DEFAULT_ATTRIBUTE_MAPPINGS);
    });

    it('should appear in the reporters list', async () => {
      const reporters: Array<Reporter> = await listDomainReporters(fixture.domain.id, fixture.accessToken);
      const found = reporters.find((r) => r.id === createdId);
      expect(found).toEqual(expect.objectContaining({ id: createdId, attributeMappings: DEFAULT_ATTRIBUTE_MAPPINGS }));
    });

    it('should return 404 for a nonexistent reporter', async () => {
      await expect(getDomainReporter(fixture.domain.id, fixture.accessToken, 'nonexistent-id')).rejects.toMatchObject({
        response: { status: 404 },
      });
    });
  });

  describe('Update', () => {
    let reporter: Reporter;

    beforeAll(async () => {
      reporter = await fixture.createReporter({
        name: 'test-kafka-update-reporter',
        configuration: fixture.kafkaConfig({ acks: '1' }),
      });
    });

    it('should update name and acks', async () => {
      const updated: Reporter = await fixture.updateReporter(reporter, {
        name: 'updated-kafka-reporter',
        configuration: fixture.kafkaConfig({ acks: 'all' }),
      });

      expect(updated.name).toEqual('updated-kafka-reporter');
      const config = JSON.parse(updated.configuration);
      expect(config.acks).toEqual('all');
    });

    it('should replace the attribute mappings', async () => {
      const remapped = [{ expression: "{#context.attributes['user'].id}", exportedName: 'user_id' }];

      const updated: Reporter = await fixture.updateReporter(reporter, { attributeMappings: remapped });

      expect(updated.attributeMappings).toEqual(remapped);
    });

    it('should clear the attribute mappings when none are supplied', async () => {
      await fixture.updateReporter(reporter, { attributeMappings: DEFAULT_ATTRIBUTE_MAPPINGS });

      const updated: Reporter = await fixture.updateReporter(reporter, { attributeMappings: null });

      expect(updated.attributeMappings ?? []).toEqual([]);
      const fetched: Reporter = await getDomainReporter(fixture.domain.id, fixture.accessToken, reporter.id);
      expect(fetched.attributeMappings ?? []).toEqual([]);
    });

    it('should toggle enabled to false', async () => {
      const updated: Reporter = await fixture.updateReporter(reporter, { enabled: false });
      expect(updated.enabled).toBe(false);
    });

    it('should change auditTypes', async () => {
      const updated: Reporter = await fixture.updateReporter(reporter, {
        enabled: true,
        configuration: fixture.kafkaConfig({ auditTypes: ['USER_LOGOUT'] }),
      });

      const config = JSON.parse(updated.configuration);
      expect(config.auditTypes).toEqual(['USER_LOGOUT']);
    });
  });

  describe('Delete', () => {
    it('should delete a reporter and return 404 afterward', async () => {
      const reporter: Reporter = await fixture.createReporter({ name: 'test-kafka-delete-reporter' });
      expect(reporter.id).toEqual(expect.any(String));

      await deleteDomainReporter(fixture.domain.id, fixture.accessToken, reporter.id);

      await expect(getDomainReporter(fixture.domain.id, fixture.accessToken, reporter.id)).rejects.toMatchObject({
        response: { status: 404 },
      });
    });
  });
});

describe('Domain Reporter Attribute Mapping Validation', () => {
  const invalid = (attributeMappings: any[]) => fixture.createReporter({ attributeMappings });

  it('should reject two mappings exporting the same name', async () => {
    await expect(
      invalid([
        { expression: "{#context.attributes['user'].additionalInformation['sub']}", exportedName: 'user_sub' },
        { expression: "{#context.attributes['user'].id}", exportedName: 'user_sub' },
      ]),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject an invalid expression (missing braces)', async () => {
    await expect(
      invalid([{ expression: "#context.attributes['user'].id", exportedName: 'user_id' }]),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject an invalid exported name (too long)', async () => {
    await expect(
      invalid([{ expression: "{#context.attributes['user'].id}", exportedName: 'a'.repeat(65) }]),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });
});

describe('Domain System Reporter', () => {
  it('should reject attribute mappings on the system reporter', async () => {
    const system = await fixture.systemReporter();

    await expect(
      updateDomainReporter(fixture.domain.id, fixture.accessToken, system.id, {
        type: system.type,
        name: system.name,
        enabled: system.enabled,
        configuration: system.configuration ?? '{}',
        attributeMappings: [
          { expression: "{#context.attributes['user'].additionalInformation['sub']}", exportedName: 'user_sub' },
        ],
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should allow an unrelated update that leaves the mappings alone', async () => {
    const system = await fixture.systemReporter();

    const updated: Reporter = await updateDomainReporter(fixture.domain.id, fixture.accessToken, system.id, {
      type: system.type,
      name: 'renamed-system-reporter',
      enabled: system.enabled,
      configuration: system.configuration ?? '{}',
      attributeMappings: system.attributeMappings,
    });

    expect(updated.name).toEqual('renamed-system-reporter');
    expect(updated.attributeMappings ?? []).toEqual([]);
  });
});
