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
import { Reporter } from '@management-models/Reporter';
import {
  createDomainReporter,
  createOrgReporter,
  deleteOrgReporter,
  getDomainReporter,
  getOrgReporter,
  listDomainReporters,
} from '@management-commands/reporter-management-commands';

import { test, expect } from '../../../fixtures/base.fixture';
import { linkJira } from '../../../utils/jira';
import { uniqueTestName } from '../../../utils/fixture-helpers';
import { DomainReporterPage } from '../../../pages/domain-reporter.page';

const USER_ID_EXPRESSION = "{#context.attributes['user'].id}";
const REQUEST_IP_EXPRESSION = "{#context.attributes['request']['ip']}";

async function createFileReporter(domainId: string, token: string): Promise<Reporter> {
  const name = uniqueTestName('file-reporter');
  return createDomainReporter(domainId, token, {
    type: 'reporter-am-file',
    name,
    enabled: true,
    // Filenames are unique per domain.
    configuration: JSON.stringify({ filename: name }),
  });
}

test.describe('Reporter attribute mappings', () => {
  test('AM-7554: mappings entered in the console are stored on the reporter', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-7554');

    const reporter = await createFileReporter(testDomain.id, adminToken);
    const reporterPage = new DomainReporterPage(page);
    await reporterPage.navigateToDetail(testDomain.id, reporter.id);

    await expect(reporterPage.attributeMappingsSection).toBeVisible();
    await expect(reporterPage.eventTypesEmptyMessage).toContainText(/all event types/i);

    await reporterPage.addMapping('user_id', USER_ID_EXPRESSION);
    await reporterPage.addMapping('client_ip', REQUEST_IP_EXPRESSION);
    await reporterPage.selectEventTypes(['USER_LOGIN']);

    await reporterPage.save();
    await reporterPage.expectSnackbar(/updated/i);

    const stored = await getDomainReporter(testDomain.id, adminToken, reporter.id);
    expect(stored.attributeMappings).toEqual([
      { exportedName: 'user_id', expression: USER_ID_EXPRESSION },
      { exportedName: 'client_ip', expression: REQUEST_IP_EXPRESSION },
    ]);
    expect(stored.attributeMappingEventTypes).toEqual(['USER_LOGIN']);
  });

  test('AM-7554: stored mappings are rendered when the reporter is reopened', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-7554');

    const name = uniqueTestName('file-reporter');
    const reporter = await createDomainReporter(testDomain.id, adminToken, {
      type: 'reporter-am-file',
      name,
      enabled: true,
      configuration: JSON.stringify({ filename: name }),
      attributeMappings: [{ exportedName: 'user_id', expression: USER_ID_EXPRESSION }],
      attributeMappingEventTypes: ['USER_LOGIN'],
    });

    const reporterPage = new DomainReporterPage(page);
    await reporterPage.navigateToDetail(testDomain.id, reporter.id);

    await expect(reporterPage.mappingRows).toHaveCount(1);
    await expect(reporterPage.mappingNameInput(0)).toHaveValue('user_id');
    await expect(reporterPage.mappingExpressionInput(0)).toHaveValue(USER_ID_EXPRESSION);
    await expect(reporterPage.selectedEventTypes).toHaveText(['USER_LOGIN']);
  });

  test('AM-7554: a system reporter offers no attribute mapping section', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-7554');

    const reporters = await listDomainReporters(testDomain.id, adminToken);
    const systemReporter = reporters.find((reporter) => reporter.system);
    expect(systemReporter, 'the domain has no system reporter to open').toBeTruthy();

    const reporterPage = new DomainReporterPage(page);
    await reporterPage.navigateToDetail(testDomain.id, systemReporter.id);

    // The name field proves the page rendered, which is what makes the absence below meaningful.
    await expect(reporterPage.nameInput).toBeVisible();
    await expect(reporterPage.attributeMappingsSection).toHaveCount(0);
  });

  test('AM-7554: event types cannot be kept without an attribute mapping', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-7554');

    const reporter = await createFileReporter(testDomain.id, adminToken);
    const reporterPage = new DomainReporterPage(page);
    await reporterPage.navigateToDetail(testDomain.id, reporter.id);

    await reporterPage.addMapping('user_id', USER_ID_EXPRESSION);
    await reporterPage.selectEventTypes(['USER_LOGIN']);
    await expect(reporterPage.saveButton).toBeEnabled();

    await reporterPage.removeMapping(0);

    await expect(reporterPage.validationErrors).toContainText([/event types apply to attribute mappings/i]);
    await expect(reporterPage.saveButton).toBeDisabled();
  });

  test('AM-7554: a repeated exported name blocks the save until it is renamed', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-7554');

    const reporter = await createFileReporter(testDomain.id, adminToken);
    const reporterPage = new DomainReporterPage(page);
    await reporterPage.navigateToDetail(testDomain.id, reporter.id);

    await reporterPage.addMapping('user_id', USER_ID_EXPRESSION);
    await reporterPage.addMapping('user_id', REQUEST_IP_EXPRESSION);

    await expect(reporterPage.validationErrors).toContainText([/used more than once/i]);
    await expect(reporterPage.saveButton).toBeDisabled();

    await reporterPage.mappingNameInput(1).fill('client_ip');

    await expect(reporterPage.validationErrors).toHaveCount(0);
    await expect(reporterPage.saveButton).toBeEnabled();
  });

  test('AM-7554: an exported name the reporter cannot use is rejected in place', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-7554');

    const reporter = await createFileReporter(testDomain.id, adminToken);
    const reporterPage = new DomainReporterPage(page);
    await reporterPage.navigateToDetail(testDomain.id, reporter.id);

    await reporterPage.addMapping('bad-name', USER_ID_EXPRESSION);
    await reporterPage.mappingNameInput(0).blur();

    await expect(reporterPage.mappingFieldError(0)).toContainText(/letters, digits and underscore/i);
    await expect(reporterPage.saveButton).toBeDisabled();
  });

  // The organization page builds its own request payload.
  test('AM-7554: mappings entered on an organization reporter are stored too', async ({ page, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-7554');

    const name = uniqueTestName('org-file-reporter');
    const reporter = await createOrgReporter(adminToken, {
      type: 'reporter-am-file',
      name,
      enabled: true,
      configuration: JSON.stringify({ filename: name }),
    });

    try {
      const reporterPage = new DomainReporterPage(page);
      await reporterPage.navigateToOrgDetail(reporter.id);

      await expect(reporterPage.attributeMappingsSection).toBeVisible();
      await reporterPage.addMapping('user_id', USER_ID_EXPRESSION);
      await reporterPage.save();
      await reporterPage.expectSnackbar(/updated/i);

      const stored = await getOrgReporter(adminToken, reporter.id);
      expect(stored.attributeMappings).toEqual([{ exportedName: 'user_id', expression: USER_ID_EXPRESSION }]);
    } finally {
      await deleteOrgReporter(adminToken, reporter.id);
    }
  });

  test('AM-7554: the mapping event types are separate from a Kafka reporter’s reported events', async ({
    page,
    testDomain,
    adminToken,
  }, testInfo) => {
    linkJira(testInfo, 'AM-7554');

    const reporter = await createDomainReporter(testDomain.id, adminToken, {
      type: 'reporter-am-kafka',
      name: uniqueTestName('kafka-reporter'),
      enabled: true,
      configuration: JSON.stringify({
        bootstrapServers: 'localhost:9092',
        topic: 'gravitee-audit',
        acks: '1',
        auditTypes: ['USER_LOGOUT'],
      }),
    });

    const reporterPage = new DomainReporterPage(page);
    await reporterPage.navigateToDetail(testDomain.id, reporter.id);

    await expect(reporterPage.reportedEventTypesSection).toBeVisible();
    await expect(reporterPage.attributeMappingsSection).toBeVisible();
    await expect(reporterPage.selectedEventTypes).toHaveCount(0);

    await reporterPage.addMapping('user_id', USER_ID_EXPRESSION);
    await reporterPage.selectEventTypes(['USER_LOGIN']);
    await reporterPage.save();
    await reporterPage.expectSnackbar(/updated/i);

    const stored = await getDomainReporter(testDomain.id, adminToken, reporter.id);
    expect(stored.attributeMappingEventTypes).toEqual(['USER_LOGIN']);
    expect(JSON.parse(stored.configuration).auditTypes).toEqual(['USER_LOGOUT']);
  });
});
