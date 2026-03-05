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
import { test } from '../../fixtures/base.fixture';
import { linkJira } from '../../utils/jira';
import { ApplicationMetadataPage } from '../../pages/application-metadata.page';

test.describe('Application Metadata', () => {
  test('AM-2175: add metadata key-value pair', async ({ page, testDomain, testApplication }, testInfo) => {
    // Jira AM-2175 also describes E2E token introspection with custom claims;
    // that flow belongs in the Jest integration suite. This test covers the UI portion.
    linkJira(testInfo, 'AM-2175');

    const metadataPage = new ApplicationMetadataPage(page);
    await metadataPage.navigateTo(testDomain.id, testApplication.id);

    await metadataPage.addMetadata('test-key', 'test-value');
    await metadataPage.expectMetadataRow('test-key', 'test-value');

    await metadataPage.clickSave();
    await metadataPage.expectSnackbar('updated');

    // Verify metadata persists after reload
    await page.reload();
    await metadataPage.waitForReady();
    await metadataPage.expectMetadataRow('test-key', 'test-value');
  });

  test('AM-2173: delete metadata entry', async ({ page, testDomain, testApplication }, testInfo) => {
    linkJira(testInfo, 'AM-2173');

    const metadataPage = new ApplicationMetadataPage(page);
    await metadataPage.navigateTo(testDomain.id, testApplication.id);

    // Add an entry first
    await metadataPage.addMetadata('to-delete', 'temp-value');
    await metadataPage.clickSave();
    await metadataPage.expectSnackbar('updated');

    // Now delete it
    await metadataPage.deleteMetadata('to-delete');
    await metadataPage.clickSave();
    await metadataPage.expectSnackbar('updated');

    await metadataPage.expectNoMetadataRow('to-delete');
  });

  test('metadata persists on reload', async ({ page, testDomain, testApplication }) => {
    const metadataPage = new ApplicationMetadataPage(page);
    await metadataPage.navigateTo(testDomain.id, testApplication.id);

    await metadataPage.addMetadata('persist-key', 'persist-value');
    await metadataPage.clickSave();
    await metadataPage.expectSnackbar('updated');

    // Reload and verify
    await page.reload();
    await metadataPage.waitForReady();
    await metadataPage.expectMetadataRow('persist-key', 'persist-value');
  });
});
