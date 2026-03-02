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
  createDomainReporter,
  deleteDomainReporter,
  getDomainReporter,
  listDomainReporters,
  updateDomainReporter,
} from '@management-commands/reporter-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { DomainReporterFixture, setupDomainReporterFixture } from './fixtures/domain-reporter-fixture';
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
      const reporter: Reporter = await createDomainReporter(fixture.domain.id, fixture.accessToken, {
        type: 'reporter-am-kafka',
        name: 'test-kafka-reporter',
        enabled: true,
        configuration: fixture.kafkaConfig({ topic, acks: '1', auditTypes: [] }),
      });

      expect(reporter).toBeDefined();
      expect(reporter.id).toBeDefined();
      expect(reporter.type).toEqual('reporter-am-kafka');
      expect(reporter.name).toEqual('test-kafka-reporter');
      expect(reporter.enabled).toBe(true);
      expect(reporter.configuration).toBeDefined();

      const config = JSON.parse(reporter.configuration);
      expect(config.bootstrapServers).toBeDefined();
      expect(config.topic).toEqual(topic);
      expect(config.acks).toEqual('1');
      expect(config.auditTypes).toEqual([]);

      fixture.createdReporterIds.push(reporter.id);
    });

    it('should create a reporter with auditTypes filter', async () => {
      const reporter: Reporter = await createDomainReporter(fixture.domain.id, fixture.accessToken, {
        type: 'reporter-am-kafka',
        name: 'test-kafka-filtered-reporter',
        enabled: true,
        configuration: fixture.kafkaConfig({ auditTypes: ['USER_LOGIN', 'USER_CREATED'] }),
      });

      expect(reporter).toBeDefined();
      expect(reporter.id).toBeDefined();

      const config = JSON.parse(reporter.configuration);
      expect(config.auditTypes).toEqual(['USER_LOGIN', 'USER_CREATED']);

      fixture.createdReporterIds.push(reporter.id);
    });

    it('should create a disabled reporter', async () => {
      const reporter: Reporter = await createDomainReporter(fixture.domain.id, fixture.accessToken, {
        type: 'reporter-am-kafka',
        name: 'test-kafka-disabled-reporter',
        enabled: false,
        configuration: fixture.kafkaConfig(),
      });

      expect(reporter).toBeDefined();
      expect(reporter.id).toBeDefined();
      expect(reporter.enabled).toBe(false);

      fixture.createdReporterIds.push(reporter.id);
    });
  });

  describe('Read', () => {
    let createdId: string;

    beforeAll(async () => {
      const reporter = await createDomainReporter(fixture.domain.id, fixture.accessToken, {
        type: 'reporter-am-kafka',
        name: 'test-kafka-read-reporter',
        enabled: true,
        configuration: fixture.kafkaConfig(),
      });
      createdId = reporter.id;
      fixture.createdReporterIds.push(createdId);
    });

    it('should get reporter by ID', async () => {
      const reporter: Reporter = await getDomainReporter(fixture.domain.id, fixture.accessToken, createdId);
      expect(reporter).toBeDefined();
      expect(reporter.id).toEqual(createdId);
      expect(reporter.name).toEqual('test-kafka-read-reporter');
    });

    it('should appear in the reporters list', async () => {
      const reporters: Array<Reporter> = await listDomainReporters(fixture.domain.id, fixture.accessToken);
      const found = reporters.find((r) => r.id === createdId);
      expect(found).toBeDefined();
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
      reporter = await createDomainReporter(fixture.domain.id, fixture.accessToken, {
        type: 'reporter-am-kafka',
        name: 'test-kafka-update-reporter',
        enabled: true,
        configuration: fixture.kafkaConfig({ acks: '1' }),
      });
      fixture.createdReporterIds.push(reporter.id);
    });

    it('should update name and acks', async () => {
      const updatedConfig = fixture.kafkaConfig({ acks: 'all' });
      const updated: Reporter = await updateDomainReporter(fixture.domain.id, fixture.accessToken, reporter.id, {
        type: reporter.type,
        name: 'updated-kafka-reporter',
        enabled: reporter.enabled,
        configuration: updatedConfig,
      });

      expect(updated.name).toEqual('updated-kafka-reporter');
      const config = JSON.parse(updated.configuration);
      expect(config.acks).toEqual('all');
    });

    it('should toggle enabled to false', async () => {
      const updated: Reporter = await updateDomainReporter(fixture.domain.id, fixture.accessToken, reporter.id, {
        type: reporter.type,
        name: reporter.name,
        enabled: false,
        configuration: fixture.kafkaConfig(),
      });
      expect(updated.enabled).toBe(false);
    });

    it('should change auditTypes', async () => {
      const updatedConfig = fixture.kafkaConfig({ auditTypes: ['USER_LOGOUT'] });
      const updated: Reporter = await updateDomainReporter(fixture.domain.id, fixture.accessToken, reporter.id, {
        type: reporter.type,
        name: reporter.name,
        enabled: true,
        configuration: updatedConfig,
      });

      const config = JSON.parse(updated.configuration);
      expect(config.auditTypes).toEqual(['USER_LOGOUT']);
    });
  });

  describe('Delete', () => {
    it('should delete a reporter and return 404 afterward', async () => {
      const reporter: Reporter = await createDomainReporter(fixture.domain.id, fixture.accessToken, {
        type: 'reporter-am-kafka',
        name: 'test-kafka-delete-reporter',
        enabled: true,
        configuration: fixture.kafkaConfig(),
      });
      expect(reporter.id).toBeDefined();

      await deleteDomainReporter(fixture.domain.id, fixture.accessToken, reporter.id);

      await expect(getDomainReporter(fixture.domain.id, fixture.accessToken, reporter.id)).rejects.toMatchObject({
        response: { status: 404 },
      });
    });
  });
});
