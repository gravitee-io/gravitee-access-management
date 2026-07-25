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
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { listDomainAudits } from '@management-commands/audit-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { expectNoAuditInElasticsearch, indexDocument, waitForAuditInElasticsearch } from '@utils-commands/elasticsearch-client';
import { ElasticsearchReporterFixture, setupElasticsearchReporterFixture } from './fixture/domain-reporter-elasticsearch-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: ElasticsearchReporterFixture;
let selectionFixture: ElasticsearchReporterFixture;

const login = (target: ElasticsearchReporterFixture) =>
  loginUserNameAndPassword(
    target.application.settings.oauth.clientId,
    target.user,
    target.user.password,
    false,
    target.openIdConfiguration,
    target.domain,
  ).then(() => {});

const sentinelAudit = (domainId: string, id: string) => ({
  id,
  transactionId: id,
  referenceType: 'DOMAIN',
  referenceId: domainId,
  type: 'ELASTICSEARCH_ONLY_SENTINEL',
  timestamp: Date.now(),
  outcome: { status: 'SUCCESS' },
});

beforeAll(async () => {
  [fixture, selectionFixture] = await Promise.all([setupElasticsearchReporterFixture(), setupElasticsearchReporterFixture()]);
});

afterAll(async () => {
  await Promise.all([fixture?.cleanUp(), selectionFixture?.cleanUp()].filter(Boolean));
});

describe('Elasticsearch Reporter - Domain Level Gateway', () => {
  describe('Enabled', () => {
    it('should write a gateway audit into Elasticsearch', async () => {
      const index = uniqueName('es-audit-enabled', true);
      await fixture.addElasticsearchReporter(index);

      await login(fixture);

      const audit = await waitForAuditInElasticsearch(`${index}-*`, fixture.domain.id, (a) => a.type === 'USER_LOGIN');

      expect(audit.type).toEqual('USER_LOGIN');
      expect(audit.referenceType).toEqual('DOMAIN');
      expect(audit.referenceId).toEqual(fixture.domain.id);
      expect(audit.outcome.status).toEqual('SUCCESS');
      expect(audit.actor.alternativeId).toEqual(fixture.user.username);
    });
  });

  describe('Disabled', () => {
    it('should not write anything into Elasticsearch when the reporter is disabled', async () => {
      const index = uniqueName('es-audit-disabled', true);
      await fixture.addElasticsearchReporter(index, false);

      await login(fixture);

      await expectNoAuditInElasticsearch(`${index}-*`, fixture.domain.id);
    });
  });

  describe('Reporter selection with both stores configured', () => {
    it('should read from the database reporter until it is disabled, then from Elasticsearch', async () => {
      const index = uniqueName('es-audit-selection', true);
      await selectionFixture.addElasticsearchReporter(index);
      await login(selectionFixture);
      await waitForAuditInElasticsearch(`${index}-*`, selectionFixture.domain.id, (a) => a.type === 'USER_LOGIN');

      // a record only Elasticsearch can possibly know about, so which store answered is unambiguous
      const sentinelId = uniqueName('sentinel', true);
      const today = new Date().toISOString().slice(0, 10).replace(/-/g, '.');
      await indexDocument(`${index}-${today}`, sentinelId, sentinelAudit(selectionFixture.domain.id, sentinelId));

      const beforeDisabling = await listDomainAudits(selectionFixture.domain.id, selectionFixture.accessToken, { size: 100 });
      expect(beforeDisabling.data.map((audit) => audit.type)).toContain('USER_LOGIN');
      expect(beforeDisabling.data.map((audit) => audit.id)).not.toContain(sentinelId);

      await selectionFixture.disableDatabaseReporter();

      const afterDisabling = await listDomainAudits(selectionFixture.domain.id, selectionFixture.accessToken, { size: 100 });
      expect(afterDisabling.data.map((audit) => audit.id)).toContain(sentinelId);
      expect(afterDisabling.data.map((audit) => audit.type)).toContain('USER_LOGIN');
    });
  });
});
