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
import { test, expect } from '../../fixtures/base.fixture';

/** Application CRUD tests — fixtures handle API setup/teardown. */
test.describe('Application Management', () => {
  test('should display application list in a domain', async ({ homePage, testDomain }) => {
    await homePage.navigateToDomain(testDomain.name);

    await homePage.clickSidenavItem('Applications');
    await homePage.expectUrlMatches(/.*applications.*/);
  });

  test('should show application created via API in the UI list', async ({ page, homePage, testDomain, testApplication }) => {
    await homePage.navigateToDomain(testDomain.name);
    await homePage.clickSidenavItem('Applications');

    const appEntry = page.locator('text=' + testApplication.name);
    await expect(appEntry.first()).toBeVisible({ timeout: 10_000 });
  });
});
