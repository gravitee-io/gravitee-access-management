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
  createOrgReporter,
  deleteOrgReporter,
  getOrgReporter,
  listOrgReporters,
  updateOrgReporter,
} from '@management-commands/reporter-management-commands';
import { OrgReporterFixture, setupOrgReporterFixture } from './fixtures/org-reporter-fixture';
import { setup } from '../../test-fixture';

setup();

let fixture: OrgReporterFixture;

beforeAll(async () => {
  fixture = await setupOrgReporterFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Org Kafka Reporter CRUD', () => {
  describe('Create', () => {
    it('should create a reporter with all fields', async () => {
      const reporter: Reporter = await createOrgReporter(fixture.accessToken, {
        type: 'reporter-am-kafka',
        name: 'test-org-kafka-reporter',
        enabled: true,
        configuration: fixture.kafkaConfig({ acks: '1', auditTypes: [] }),
      });

      expect(reporter).toBeDefined();
      expect(reporter.id).toBeDefined();
      expect(reporter.type).toEqual('reporter-am-kafka');
      expect(reporter.name).toEqual('test-org-kafka-reporter');
      expect(reporter.enabled).toBe(true);
      expect(reporter.configuration).toBeDefined();

      const config = JSON.parse(reporter.configuration);
      expect(config.bootstrapServers).toBeDefined();
      expect(config.acks).toEqual('1');

      fixture.createdReporterIds.push(reporter.id);
    });

    it('should create a reporter with auditTypes filter', async () => {
      const reporter: Reporter = await createOrgReporter(fixture.accessToken, {
        type: 'reporter-am-kafka',
        name: 'test-org-kafka-filtered-reporter',
        enabled: true,
        configuration: fixture.kafkaConfig({ auditTypes: ['USER_LOGIN', 'USER_CREATED'] }),
      });

      expect(reporter).toBeDefined();
      const config = JSON.parse(reporter.configuration);
      expect(config.auditTypes).toEqual(['USER_LOGIN', 'USER_CREATED']);

      fixture.createdReporterIds.push(reporter.id);
    });

    it('should create a disabled reporter', async () => {
      const reporter: Reporter = await createOrgReporter(fixture.accessToken, {
        type: 'reporter-am-kafka',
        name: 'test-org-kafka-disabled-reporter',
        enabled: false,
        configuration: fixture.kafkaConfig(),
      });

      expect(reporter).toBeDefined();
      expect(reporter.enabled).toBe(false);

      fixture.createdReporterIds.push(reporter.id);
    });

    it('should create a reporter with inherited=true', async () => {
      const reporter: Reporter = await createOrgReporter(fixture.accessToken, {
        type: 'reporter-am-kafka',
        name: 'test-org-kafka-inherited-true',
        enabled: true,
        inherited: true,
        configuration: fixture.kafkaConfig(),
      });

      expect(reporter).toBeDefined();
      expect(reporter.inherited).toBe(true);

      fixture.createdReporterIds.push(reporter.id);
    });

    it('should create a reporter with inherited=false', async () => {
      const reporter: Reporter = await createOrgReporter(fixture.accessToken, {
        type: 'reporter-am-kafka',
        name: 'test-org-kafka-inherited-false',
        enabled: true,
        inherited: false,
        configuration: fixture.kafkaConfig(),
      });

      expect(reporter).toBeDefined();
      expect(reporter.inherited).toBe(false);

      fixture.createdReporterIds.push(reporter.id);
    });
  });

  describe('Read', () => {
    let createdId: string;

    beforeAll(async () => {
      const reporter = await createOrgReporter(fixture.accessToken, {
        type: 'reporter-am-kafka',
        name: 'test-org-kafka-read-reporter',
        enabled: true,
        configuration: fixture.kafkaConfig(),
      });
      createdId = reporter.id;
      fixture.createdReporterIds.push(createdId);
    });

    it('should get reporter by ID', async () => {
      const reporter: Reporter = await getOrgReporter(fixture.accessToken, createdId);
      expect(reporter).toBeDefined();
      expect(reporter.id).toEqual(createdId);
      expect(reporter.name).toEqual('test-org-kafka-read-reporter');
    });

    it('should appear in the reporters list', async () => {
      const reporters: Array<Reporter> = await listOrgReporters(fixture.accessToken);
      const found = reporters.find((r) => r.id === createdId);
      expect(found).toBeDefined();
    });

    it('should return 404 for a nonexistent reporter', async () => {
      await expect(getOrgReporter(fixture.accessToken, 'nonexistent-id')).rejects.toMatchObject({
        response: { status: 404 },
      });
    });
  });

  describe('Update', () => {
    let reporter: Reporter;

    beforeAll(async () => {
      reporter = await createOrgReporter(fixture.accessToken, {
        type: 'reporter-am-kafka',
        name: 'test-org-kafka-update-reporter',
        enabled: true,
        inherited: false,
        configuration: fixture.kafkaConfig({ acks: '1' }),
      });
      fixture.createdReporterIds.push(reporter.id);
    });

    it('should update name and acks', async () => {
      const updated: Reporter = await updateOrgReporter(fixture.accessToken, reporter.id, {
        type: reporter.type,
        name: 'updated-org-kafka-reporter',
        enabled: reporter.enabled,
        configuration: fixture.kafkaConfig({ acks: 'all' }),
      });

      expect(updated.name).toEqual('updated-org-kafka-reporter');
      const config = JSON.parse(updated.configuration);
      expect(config.acks).toEqual('all');
    });

    it('should toggle enabled to false', async () => {
      const updated: Reporter = await updateOrgReporter(fixture.accessToken, reporter.id, {
        type: reporter.type,
        name: reporter.name,
        enabled: false,
        configuration: fixture.kafkaConfig(),
      });
      expect(updated.enabled).toBe(false);
    });

    it('should change auditTypes', async () => {
      const updated: Reporter = await updateOrgReporter(fixture.accessToken, reporter.id, {
        type: reporter.type,
        name: reporter.name,
        enabled: true,
        configuration: fixture.kafkaConfig({ auditTypes: ['USER_LOGOUT'] }),
      });

      const config = JSON.parse(updated.configuration);
      expect(config.auditTypes).toEqual(['USER_LOGOUT']);
    });

    it('should toggle inherited from false to true', async () => {
      const updated: Reporter = await updateOrgReporter(fixture.accessToken, reporter.id, {
        type: reporter.type,
        name: reporter.name,
        enabled: true,
        inherited: true,
        configuration: fixture.kafkaConfig(),
      });
      expect(updated.inherited).toBe(true);
    });

    it('should toggle inherited from true to false', async () => {
      const updated: Reporter = await updateOrgReporter(fixture.accessToken, reporter.id, {
        type: reporter.type,
        name: reporter.name,
        enabled: true,
        inherited: false,
        configuration: fixture.kafkaConfig(),
      });
      expect(updated.inherited).toBe(false);
    });
  });

  describe('Delete', () => {
    it('should delete a reporter and return 404 afterward', async () => {
      const reporter: Reporter = await createOrgReporter(fixture.accessToken, {
        type: 'reporter-am-kafka',
        name: 'test-org-kafka-delete-reporter',
        enabled: true,
        configuration: fixture.kafkaConfig(),
      });
      expect(reporter.id).toBeDefined();

      await deleteOrgReporter(fixture.accessToken, reporter.id);

      await expect(getOrgReporter(fixture.accessToken, reporter.id)).rejects.toMatchObject({
        response: { status: 404 },
      });
    });
  });
});
