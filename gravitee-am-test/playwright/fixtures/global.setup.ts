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
import { test as setup } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { requestAdminAccessToken } from '../../api/commands/management/token-management-commands';
import { listDomains, safeDeleteDomain } from '../../api/commands/management/domain-management-commands';
import { ADMIN_PASSWORD } from '../utils/test-constants';

const ADMIN_STORAGE_STATE = 'playwright/fixtures/.auth/admin.json';

/** Prefixes used by Playwright test fixtures for domain names. */
export const TEST_DOMAIN_PREFIXES = ['pw-', 'cert-'];

/** Delete stale test domains to keep the environment clean. */
export async function cleanupTestDomains(label: string): Promise<void> {
  try {
    const token = await requestAdminAccessToken();
    const page = await listDomains(token, { size: 200 });
    const staleDomains = (page.data || []).filter((d) =>
      TEST_DOMAIN_PREFIXES.some((prefix) => d.name?.startsWith(prefix)),
    );

    if (staleDomains.length === 0) return;

    console.log(`[${label}] Cleaning up ${staleDomains.length} stale test domains...`);
    for (const domain of staleDomains) {
      await safeDeleteDomain(domain.id, token);
    }
    console.log(`[${label}] Stale domain cleanup complete.`);
  } catch (err) {
    console.warn(`[${label}] Stale domain cleanup failed (non-fatal):`, err);
  }
}

/** Authenticate once and save browser state for all tests. */
// eslint-disable-next-line playwright/expect-expect -- setup fixture, no assertions needed
setup('authenticate as admin', async ({ page }) => {
  // Clean up stale test domains before running the suite
  await cleanupTestDomains('setup');

  const loginPage = new LoginPage(page);
  const username = process.env.AM_ADMIN_USERNAME || 'admin';
  const password = process.env.AM_ADMIN_PASSWORD || ADMIN_PASSWORD;

  await loginPage.goto();
  await loginPage.login(username, password);

  await page.context().storageState({ path: ADMIN_STORAGE_STATE });
});
