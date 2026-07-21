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
import { createDomain, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { performPut } from '@gateway-commands/oauth-oidc-commands';
import type { Domain } from '@management-models/Domain';

import { test as base, expect } from './base.fixture';
import { NavbarPage } from '../pages/navbar.page';
import { quietly, uniqueTestName as uniqueName } from '../utils/fixture-helpers';

export interface ConsoleUserPreferences {
  defaultDomainId?: string;
  defaultEnvironmentId?: string;
  pinnedDomainIds?: string[];
}

export type PickerFixtures = {
  navbar: NavbarPage;
  /** The domain the picker treats as "current". Bare (not started) — the picker is a management-console feature. */
  currentDomain: Domain;
  /** Two bare domains (created, not started) available to pin or set default in the picker. */
  extraDomains: { toPin: Domain; toDefault: Domain };
  /** Persist the shared admin user's console preferences before loading the page. */
  setPreferences: (preferences: ConsoleUserPreferences) => Promise<unknown>;
  /** Clears the shared admin user's preferences before and after each test. */
  cleanPreferences: void;
  /** Domain ids created through the UI during a test; deleted via API on teardown. */
  trackedDomainIds: string[];
};

const managementUrl = () => `${process.env.AM_MANAGEMENT_URL}/management`;
const authHeaders = (token: string) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${token}` });

export const test = base.extend<PickerFixtures>({
  navbar: async ({ page }, use) => {
    await use(new NavbarPage(page));
  },

  currentDomain: async ({ adminToken }, use) => {
    const domain = await quietly(() => createDomain(adminToken, uniqueName('pw-cur'), 'Playwright picker current domain'));

    await use(domain);

    await quietly(() => safeDeleteDomain(domain.id, adminToken));
  },

  extraDomains: async ({ adminToken }, use) => {
    const toPin = await quietly(() => createDomain(adminToken, uniqueName('pw-pin'), 'Playwright picker pin domain'));
    const toDefault = await quietly(() => createDomain(adminToken, uniqueName('pw-def'), 'Playwright picker default domain'));

    await use({ toPin, toDefault });

    await quietly(() => safeDeleteDomain(toPin.id, adminToken));
    await quietly(() => safeDeleteDomain(toDefault.id, adminToken));
  },

  setPreferences: async ({ adminToken }, use) => {
    await use((preferences: ConsoleUserPreferences) =>
      quietly(() => performPut(managementUrl(), '/user/preferences', preferences, authHeaders(adminToken))),
    );
  },

  trackedDomainIds: async ({ adminToken }, use) => {
    const ids: string[] = [];

    await use(ids);

    for (const id of ids) {
      await quietly(() => safeDeleteDomain(id, adminToken));
    }
  },

  cleanPreferences: [
    async ({ adminToken }, use) => {
      const reset = () => quietly(() => performPut(managementUrl(), '/user/preferences', {}, authHeaders(adminToken)));
      await reset();
      await use();
      await reset();
    },
    { auto: true },
  ],
});

export { expect };
