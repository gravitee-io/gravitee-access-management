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

/**
 * Needs the Elasticsearch overlay running alongside the stack, which the default gateway stack does
 * not include:
 *
 *   npm --prefix docker/local-stack run stack:elasticsearch
 *   RUN_ELASTICSEARCH_TESTS=true npm --prefix gravitee-am-test run test -- \
 *     specs/gateway/reporters/domain-reporter-elasticsearch.jest.spec.ts
 *
 * Opt-in rather than probing for a reachable cluster, so that a CI job where Elasticsearch failed to
 * start reports a hard failure instead of quietly skipping. CI sets the flag in both gateway jobs.
 */
const describeIfElasticsearch = process.env.RUN_ELASTICSEARCH_TESTS === 'true' ? describe : describe.skip;

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

/** Polls the console's audit read path until the expected record shows up. */
const waitForConsoleAudit = async (
  target: ElasticsearchReporterFixture,
  predicate: (audit: any) => boolean,
  timeoutMs = 30000,
): Promise<any> => {
  const deadline = Date.now() + timeoutMs;
  let page = await listDomainAudits(target.domain.id, target.accessToken, { size: 100 });
  while (Date.now() < deadline && !page.data.some(predicate)) {
    await new Promise((resolve) => setTimeout(resolve, 500));
    page = await listDomainAudits(target.domain.id, target.accessToken, { size: 100 });
  }
  return page;
};

const sentinelAudit = (domainId: string, id: string) => ({
  id,
  transactionId: id,
  referenceType: 'DOMAIN',
  referenceId: domainId,
  type: 'ELASTICSEARCH_ONLY_SENTINEL',
  timestamp: Date.now(),
  outcome: { status: 'SUCCESS' },
});

describeIfElasticsearch('Elasticsearch Reporter - Domain Level Gateway', () => {
  // inside the gated describe, not at file scope: a file-scope beforeAll still runs when the tests
  // below are skipped, and would reach for a cluster that is not there
  beforeAll(async () => {
    [fixture, selectionFixture] = await Promise.all([setupElasticsearchReporterFixture(), setupElasticsearchReporterFixture()]);
  });

  afterAll(async () => {
    await Promise.all([fixture?.cleanUp(), selectionFixture?.cleanUp()].filter(Boolean));
  });

  describe('Enabled', () => {
    it('should write a gateway audit into Elasticsearch', async () => {
      const index = uniqueName('es-audit-enabled', true).toLowerCase();
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
      const index = uniqueName('es-audit-disabled', true).toLowerCase();
      await fixture.addElasticsearchReporter(index, false);

      await login(fixture);

      await expectNoAuditInElasticsearch(`${index}-*`, fixture.domain.id);
    });
  });

  describe('Reporter selection with both stores configured', () => {
    it('should read from the database reporter until it is disabled, then from Elasticsearch', async () => {
      const index = uniqueName('es-audit-selection', true).toLowerCase();
      await selectionFixture.addElasticsearchReporter(index);
      await login(selectionFixture);
      await waitForAuditInElasticsearch(`${index}-*`, selectionFixture.domain.id, (a) => a.type === 'USER_LOGIN');

      // a record only Elasticsearch can possibly know about, so which store answered is unambiguous
      const sentinelId = uniqueName('sentinel', true);
      const today = new Date().toISOString().slice(0, 10).replace(/-/g, '.');
      await indexDocument(`${index}-${today}`, sentinelId, sentinelAudit(selectionFixture.domain.id, sentinelId));

      // the database reporter writes on its own schedule, so give the login audit time to land there
      const beforeDisabling = await waitForConsoleAudit(selectionFixture, (audit) => audit.type === 'USER_LOGIN');
      expect(beforeDisabling.data.map((audit) => audit.type)).toContain('USER_LOGIN');
      expect(beforeDisabling.data.map((audit) => audit.id))
        .not.toContain(sentinelId);

      await selectionFixture.disableDatabaseReporter();

      const afterDisabling = await waitForConsoleAudit(selectionFixture, (audit) => audit.id === sentinelId);
      expect(afterDisabling.data.map((audit) => audit.id)).toContain(sentinelId);
      expect(afterDisabling.data.map((audit) => audit.type)).toContain('USER_LOGIN');
    });
  });
});
