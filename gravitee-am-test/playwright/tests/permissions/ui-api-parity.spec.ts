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

import { test, expect } from '../../fixtures/permissions.fixture';
import { requestAccessToken } from '@management-commands/token-management-commands';
import { performGet, performPatch, performPost } from '@gateway-commands/oauth-oidc-commands';
import { createConsolePersona, submenuItem } from '../../utils/permissions-helpers';
import { deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { linkJira } from '../../utils/jira';
import { uniqueTestName } from '../../utils/fixture-helpers';
import { APPLICATION_OVERVIEW_URL, CREATE_FAB_SELECTOR, MULTI_PHASE_TEST_TIMEOUT } from '../../utils/test-constants';

test.use({ storageState: { cookies: [], origins: [] } });

// Every test here builds its own permissions world, and that setup counts against the test
// timeout, so the extended budget applies uniformly across the file.
test.beforeEach(({}, testInfo) => testInfo.setTimeout(MULTI_PHASE_TEST_TIMEOUT));

const managementUrl = () => `${process.env.AM_MANAGEMENT_URL}/management`;
const authHeaders = (token: string) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${token}` });
const applicationPath = (domainId: string, applicationId: string) =>
  `/organizations/${process.env.AM_DEF_ORG_ID}/environments/${process.env.AM_DEF_ENV_ID}/domains/${domainId}/applications/${applicationId}`;

/**
 * The Console does not display the management API's verdict — it reaches its own. `AuthService`
 * keeps a flattened list of permission strings per tier and decides visibility by looking a value
 * up in it, entirely client-side, while the API resolves memberships and acls server-side. They are
 * two implementations of one rule, and nothing else in this suite compares them.
 *
 * So each case drives both channels as the *same* user: what the browser shows, and what the API
 * answers for the corresponding request. A divergence in either direction is a defect — a control
 * offered that the API refuses wastes the user's time and misrepresents their access; a control
 * withheld that the API would have allowed silently removes function they are entitled to.
 */
test.describe('UI and API parity - the Console agrees with the management API', () => {
  test('AM-7472: a control the Console withholds is also refused by the API', async ({ page, permissionsWorld, signInAs }, testInfo) => {
    linkJira(testInfo, 'AM-7472');

    const { appViewer, domain, ownedApplication } = permissionsWorld;

    await signInAs(appViewer);
    await page.getByRole('link', { name: ownedApplication.name, exact: true }).click();
    await page.waitForURL(APPLICATION_OVERVIEW_URL);

    // Anchor on what this role is entitled to before asserting what it is not offered.
    await expect(submenuItem(page, 'Overview')).toBeVisible();
    await expect(submenuItem(page, 'Settings')).toHaveCount(0);

    // The same user, asking the API directly for the change that menu would have led to.
    const token = await requestAccessToken(appViewer.username, appViewer.password);
    const response = await performPatch(
      managementUrl(),
      applicationPath(domain.id, ownedApplication.id),
      { description: 'attempted by a read-only application role' },
      authHeaders(token),
    );

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });

  test('AM-7472: a control the Console offers is accepted by the API', async ({ page, permissionsWorld, signInAs }, testInfo) => {
    linkJira(testInfo, 'AM-7472');

    const { appOwner, domain, ownedApplication } = permissionsWorld;

    await signInAs(appOwner);
    await page.getByRole('link', { name: ownedApplication.name, exact: true }).click();
    await page.waitForURL(APPLICATION_OVERVIEW_URL);

    await expect(submenuItem(page, 'Settings')).toBeVisible();

    // The counterpart: an offered control must correspond to a request the API actually allows,
    // otherwise the Console is advertising access its holder does not have.
    const token = await requestAccessToken(appOwner.username, appOwner.password);
    const response = await performPatch(
      managementUrl(),
      applicationPath(domain.id, ownedApplication.id),
      { description: 'applied by the application owner' },
      authHeaders(token),
    );

    expect(response.status).toBeLessThan(400);

    // And the change is real, not merely accepted.
    const reread = await performGet(managementUrl(), applicationPath(domain.id, ownedApplication.id), authHeaders(token));
    expect(reread.body.description).toEqual('applied by the application owner');
  });

  test('AM-7472: a user offered no way to create a domain is also refused by the API', async ({
    page,
    permissionsWorld,
    signInAs,
  }, testInfo) => {
    linkJira(testInfo, 'AM-7472');

    // A user holding nothing but the default organization role, created here because this is the
    // only case that needs one.
    const bareUser = await createConsolePersona(permissionsWorld.adminToken, 'parity-bare');

    try {
      await signInAs(bareUser);

      // Nothing anywhere offers domain creation to them.
      await expect(page.locator(CREATE_FAB_SELECTOR)).toHaveCount(0);

      const token = await requestAccessToken(bareUser.username, bareUser.password);
      const response = await performPost(
        managementUrl(),
        `/organizations/${process.env.AM_DEF_ORG_ID}/environments/${process.env.AM_DEF_ENV_ID}/domains`,
        {
          name: uniqueTestName('parity-should-never-exist'),
          description: 'must never be created',
          dataPlaneId: process.env.AM_DOMAIN_DATA_PLANE_ID || 'default',
        },
        authHeaders(token),
      );

      expect(response.status).toBe(403);
      expect(response.body.message).toEqual('Permission denied');
    } finally {
      await deleteOrganisationUser(permissionsWorld.adminToken, bareUser.userId).catch(() => undefined);
    }
  });
});
