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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { listDomains, safeDeleteDomain } from '@management-commands/domain-management-commands';

/** Prefixes used by Playwright test fixtures for domain names. */
export const TEST_DOMAIN_PREFIXES = ['pw-', 'cert-'];

/**
 * Delete stale test domains to keep the environment clean.
 * Shared by global setup (pre-suite) and global teardown (post-suite). Kept in its own module so
 * teardown does not import `global.setup`, which registers a Playwright `setup()` test at load time.
 */
export async function cleanupTestDomains(label: string): Promise<void> {
  try {
    const token = await requestAdminAccessToken();
    const page = await listDomains(token, { size: 200 });
    const staleDomains = (page.data || []).filter((d) => TEST_DOMAIN_PREFIXES.some((prefix) => d.name?.startsWith(prefix)));

    if (staleDomains.length === 0) return;

    console.log(`[${label}] Cleaning up ${staleDomains.length} stale test domains...`);
    for (const domain of staleDomains) {
      await safeDeleteDomain(domain.id, token);
    }
    console.log(`[${label}] Stale domain cleanup complete.`);
  } catch (err) {
    // Non-fatal: managed-cloud has no DEFAULT admin; chromium setup already cleaned what it could.
    const message = err instanceof Error ? err.message : String(err);
    if (/401/.test(message)) {
      console.warn(`[${label}] Stale domain cleanup skipped (no DEFAULT admin — expected on managed cloud).`);
    } else {
      console.warn(`[${label}] Stale domain cleanup failed (non-fatal):`, err);
    }
  }
}
