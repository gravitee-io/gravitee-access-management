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

import { test as base } from '@playwright/test';
import crossFetch from 'cross-fetch';
globalThis.fetch = crossFetch;

import { getDomainApi } from '@management-commands/service/utils';
import { waitForDomainSync } from '@management-commands/domain-management-commands';
import { OrgLicenseFixture, setupOrgLicenseFixture } from '../../specs/cloud/fixtures/org-license-fixture';
import { setupCloudSharedFixture } from '../../specs/cloud/fixtures/cloud-shared-fixture';
import { quietly, uniqueTestName as uniqueName } from '../utils/fixture-helpers';
import { cockpitBrowserSignIn } from '../utils/cockpit-browser-signin';
import type { Domain } from '@management-models/Domain';
import type { Page } from '@playwright/test';

export type CloudPluginLicenseFixtures = {
  /** Cockpit-provisioned org + license helpers. */
  licenseFixture: OrgLicenseFixture;
  /** Domain in the Cockpit org's home environment. */
  testDomain: Domain;
  /** Environment hrid used in Console routes (`/environments/{hrid}/...`). */
  environmentHrid: string;
  /** Browser page already signed in via Cockpit SSO. */
  signedInPage: Page;
};

/**
 * Managed-cloud Console fixtures for the plugin-license UI suite.
 * Does not touch the chromium admin setup / storageState path.
 */
export const test = base.extend<CloudPluginLicenseFixtures>({
  // Cloud has no inline admin — do not reuse the chromium project's admin storageState.
  storageState: { cookies: [], origins: [] },

  // The shared cloud org/env, whose data plane is the one the gateway is pinned to: cloud mode
  // resolves the data plane from the environment link, so a domain elsewhere is never served.
  licenseFixture: async ({}, use) => {
    const fixture = await setupOrgLicenseFixture(await setupCloudSharedFixture());
    await use(fixture);
    await fixture.cleanup();
  },

  testDomain: async ({ licenseFixture }, use) => {
    const { accessToken, organizationId, environmentId } = licenseFixture;
    const { dataPlaneId } = await setupCloudSharedFixture();
    const domainApi = getDomainApi(accessToken);
    const domain = await quietly(() =>
      domainApi.createDomain({
        organizationId,
        environmentId,
        newDomain: {
          name: uniqueName('pw-license-ui'),
          description: 'Playwright EE plugin license UI',
          dataPlaneId,
        },
      }),
    );
    await quietly(() => domainApi.patchDomain({ organizationId, environmentId, domain: domain.id!, patchDomain: { enabled: true } }));
    await quietly(() => waitForDomainSync(domain.id));

    await use(domain);

    await quietly(() => licenseFixture.deleteDomain(domain.id));
  },

  environmentHrid: async ({ licenseFixture }, use) => {
    // The shared cloud fixture sets hrids to [environmentId].
    await use(licenseFixture.environmentId);
  },

  signedInPage: async ({ page, licenseFixture }, use) => {
    const redirectUri = process.env.AM_UI_URL || 'http://localhost:4200';
    await cockpitBrowserSignIn(page, {
      sub: licenseFixture.userId,
      organizationId: licenseFixture.organizationId,
      environmentId: licenseFixture.environmentId,
      redirectUri,
    });
    await use(page);
  },
});

export { expect } from '@playwright/test';
