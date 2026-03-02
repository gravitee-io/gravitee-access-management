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

/** Domain CRUD tests — testDomain fixture handles API setup/teardown. */
test.describe('Domain Management', () => {
  test('should display the domain list', async ({ homePage }) => {
    await homePage.gotoDomainsList();

    await expect(homePage.domainsContent).toBeVisible({ timeout: 10_000 });
  });

  test('should navigate into a domain created via API', async ({ homePage, testDomain }) => {
    await homePage.navigateToDomain(testDomain.name);

    await homePage.expectUrlMatches(/.*domains.*/);
  });

  test('should show domain detail page with sidenav', async ({ homePage, testDomain }) => {
    await homePage.navigateToDomain(testDomain.name);

    await expect(homePage.sidenav.locator('a').first()).toBeVisible({ timeout: 10_000 });
  });
});
