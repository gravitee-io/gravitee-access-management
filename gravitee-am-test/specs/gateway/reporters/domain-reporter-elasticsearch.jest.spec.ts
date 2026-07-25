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
import { createDomainReporter, listDomainReporters } from '@management-commands/reporter-management-commands';
import { uniqueName } from '@utils-commands/misc';
import {
  expectNoAuditInElasticsearch,
  indexDocument,
  internalElasticsearchUrl,
  putIndexTemplate,
  waitForAuditInElasticsearch,
} from '@utils-commands/elasticsearch-client';
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
let misconfiguredFixture: ElasticsearchReporterFixture;

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

/**
 * A reporter reaches its terminal state a moment after it deploys, so the management API is polled
 * rather than assumed to have caught up.
 */
const waitForReporterToFail = async (target: ElasticsearchReporterFixture, reporterId: string, timeoutMs = 30000): Promise<any[]> => {
  const deadline = Date.now() + timeoutMs;
  let reporters = await listDomainReporters(target.domain.id, target.accessToken);
  while (Date.now() < deadline && reporters.find((reporter) => reporter.id === reporterId)?.status !== 'FAILED') {
    await new Promise((resolve) => setTimeout(resolve, 500));
    reporters = await listDomainReporters(target.domain.id, target.accessToken);
  }
  const failed = reporters.find((reporter) => reporter.id === reporterId);
  if (failed?.status !== 'FAILED') {
    throw new Error(
      `Reporter ${reporterId} did not fail within ${timeoutMs}ms. Saw: ${JSON.stringify(
        reporters.map((reporter) => ({ id: reporter.id, status: reporter.status })),
      )}`,
    );
  }
  return reporters;
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
    [fixture, selectionFixture, misconfiguredFixture] = await Promise.all([
      setupElasticsearchReporterFixture(),
      setupElasticsearchReporterFixture(),
      setupElasticsearchReporterFixture(),
    ]);
  });

  afterAll(async () => {
    await Promise.all([fixture?.cleanUp(), selectionFixture?.cleanUp(), misconfiguredFixture?.cleanUp()].filter(Boolean));
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
    it('should serve reads from Elasticsearch without disabling the database reporter', async () => {
      const index = uniqueName('es-audit-selection', true).toLowerCase();
      const elasticsearch = await selectionFixture.addElasticsearchReporter(index);
      await login(selectionFixture);
      await waitForAuditInElasticsearch(`${index}-*`, selectionFixture.domain.id, (a) => a.type === 'USER_LOGIN');

      // a record only Elasticsearch can possibly know about, so which store answered is unambiguous
      const sentinelId = uniqueName('sentinel', true);
      const today = new Date().toISOString().slice(0, 10).replace(/-/g, '.');
      await indexDocument(`${index}-${today}`, sentinelId, sentinelAudit(selectionFixture.domain.id, sentinelId));

      const audits = await waitForConsoleAudit(selectionFixture, (audit) => audit.id === sentinelId);
      expect(audits.data.map((audit) => audit.id)).toContain(sentinelId);
      expect(audits.data.map((audit) => audit.type)).toContain('USER_LOGIN');

      // the database reporter is still enabled and still being written to; it just no longer wins reads
      const reporters = await listDomainReporters(selectionFixture.domain.id, selectionFixture.accessToken);
      expect(reporters.find((reporter) => reporter.id === elasticsearch.id).readSource).toBe(true);
      expect(reporters.filter((reporter) => reporter.readSource).map((reporter) => reporter.id)).toEqual([elasticsearch.id]);
      expect(reporters.every((reporter) => reporter.enabled)).toBe(true);
    });
  });

  describe('Index name validation', () => {
    it('should reject an index name Elasticsearch would refuse rather than accept it and break later', async () => {
      // the rule lives in the plugin schema as well as in the reporter, so it is enforced here rather
      // than at deployment, where the operator has already been told the reporter was created
      await expect(
        createDomainReporter(fixture.domain.id, fixture.accessToken, {
          type: 'reporter-am-elasticsearch',
          name: uniqueName('es-reporter-illegal-index', true),
          enabled: true,
          configuration: JSON.stringify({
            endpoints: [internalElasticsearchUrl()],
            index: 'QA-Audit-UPPER',
            bulkActions: 1,
            flushInterval: 1,
          }),
        }),
      ).rejects.toMatchObject({ response: { status: 400 } });
    });

    it('should accept a legal index name', async () => {
      const index = uniqueName('es-audit-legal', true).toLowerCase();
      const reporter = await fixture.addElasticsearchReporter(index);
      expect(JSON.parse(reporter.configuration).index).toEqual(index);
    });
  });

  describe('Reporter that cannot start', () => {
    it('should keep serving database history when the Elasticsearch reporter is misconfigured', async () => {
      const index = uniqueName('es-audit-broken', true).toLowerCase();

      // history the database reporter holds and Elasticsearch never will
      await login(misconfiguredFixture);
      const beforeBreaking = await waitForConsoleAudit(misconfiguredFixture, (audit) => audit.type === 'USER_LOGIN');
      expect(beforeBreaking.data.map((audit) => audit.type)).toContain('USER_LOGIN');

      // occupy the template slot the reporter needs, at the priority it will ask for: Elasticsearch
      // refuses overlapping composable templates at the same priority, and retrying cannot fix it
      await putIndexTemplate(`${index}-conflict`, {
        index_patterns: [`${index}-*`],
        priority: index.length,
        template: { mappings: {} },
      });

      const broken = await misconfiguredFixture.addElasticsearchReporter(index);
      const reporters = await waitForReporterToFail(misconfiguredFixture, broken.id);

      // the broken reporter outranks the database one, so without a liveness check it would win reads
      // and the audit screen would render empty while the history sat in the database, reachable
      const database = reporters.find((reporter) => reporter.system);
      expect(reporters.find((reporter) => reporter.id === broken.id).readSource).toBe(false);
      expect(reporters.filter((reporter) => reporter.readSource).map((reporter) => reporter.id)).toEqual([database.id]);

      const afterBreaking = await listDomainAudits(misconfiguredFixture.domain.id, misconfiguredFixture.accessToken, { size: 100 });
      expect(afterBreaking.data.map((audit) => audit.type)).toContain('USER_LOGIN');
    });
  });
});
