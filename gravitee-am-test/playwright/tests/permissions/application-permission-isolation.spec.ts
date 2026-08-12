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
import { addApplicationMembership, userMembership } from '@management-commands/membership-management-commands';
import { deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { ConsolePersona, createConsolePersona, submenuItem } from '../../utils/permissions-helpers';
import { linkJira } from '../../utils/jira';
import { MULTI_PHASE_TEST_TIMEOUT } from '../../utils/test-constants';

test.use({ storageState: { cookies: [], origins: [] } });

/**
 * One user, two applications, a different role on each. This is the case the Console is most
 * likely to get wrong, because `AuthService` holds a single `applicationPermissions` list for
 * "the application" rather than one per application, and `hasPermissions` consults every cached
 * tier at once. Moving between applications must therefore replace that list, not add to it —
 * otherwise the richer role visited first keeps granting affordances on the second.
 *
 * Both directions matter. Carrying permissions forward offers editing on an application the user
 * may only read; failing to restore them withholds editing on one they own.
 */
test.describe('Application permission isolation - moving between applications', () => {
  let mixedUser: ConsolePersona;

  test.beforeEach(async ({ permissionsWorld }) => {
    const { adminToken, domain, ownedApplication, otherApplication, applicationOwnerRole, applicationViewerRole } = permissionsWorld;

    // Owner of one application, read-only on the other — the two roles differ only in what the
    // Console should offer once each application is open.
    mixedUser = await createConsolePersona(adminToken, 'mixedroles');
    await addApplicationMembership(domain.id, ownedApplication.id, adminToken, userMembership(mixedUser.userId, applicationOwnerRole.id));
    await addApplicationMembership(domain.id, otherApplication.id, adminToken, userMembership(mixedUser.userId, applicationViewerRole.id));
  });

  test.afterEach(async ({ permissionsWorld }) => {
    if (mixedUser) {
      await deleteOrganisationUser(permissionsWorld.adminToken, mixedUser.userId).catch(() => undefined);
    }
  });

  const openApplication = async (page: import('@playwright/test').Page, name: string) => {
    await page.getByRole('link', { name, exact: true }).click();
    await page.waitForURL(/.*\/applications\/.*\/overview.*/i);
  };

  const backToApplicationList = async (page: import('@playwright/test').Page, domainId: string) => {
    await page.getByRole('link', { name: 'Applications', exact: true }).click();
    await page.waitForURL(new RegExp(`/domains/${domainId}/applications`));
  };

  test('AM-7473: editing menus are not carried into an application the user may only read', async ({
    page,
    permissionsWorld,
    signInAs,
  }, testInfo) => {
    linkJira(testInfo, 'AM-7473');
    test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

    const { domain, ownedApplication, otherApplication } = permissionsWorld;

    await signInAs(mixedUser);

    // Visit the owned application first, so the richer permission set is the one already cached.
    await openApplication(page, ownedApplication.name);
    await expect(submenuItem(page, 'Settings')).toBeVisible();

    // Then move to the read-only one without reloading the application.
    await backToApplicationList(page, domain.id);
    await openApplication(page, otherApplication.name);

    await expect(submenuItem(page, 'Overview')).toBeVisible();
    await expect(submenuItem(page, 'Settings')).toHaveCount(0);
  });

  test('AM-7473: editing menus are restored on returning to an application the user owns', async ({
    page,
    permissionsWorld,
    signInAs,
  }, testInfo) => {
    linkJira(testInfo, 'AM-7473');
    test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

    const { domain, ownedApplication, otherApplication } = permissionsWorld;

    await signInAs(mixedUser);

    // The other direction: start from the narrower role, then return to the owned application.
    await openApplication(page, otherApplication.name);
    await expect(submenuItem(page, 'Settings')).toHaveCount(0);

    await backToApplicationList(page, domain.id);
    await openApplication(page, ownedApplication.name);

    await expect(submenuItem(page, 'Settings')).toBeVisible();
  });

  test('AM-7473: a reloaded application shows the same menus as one reached by navigation', async ({
    page,
    permissionsWorld,
    signInAs,
  }, testInfo) => {
    linkJira(testInfo, 'AM-7473');
    test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

    const { domain, ownedApplication, otherApplication } = permissionsWorld;

    await signInAs(mixedUser);
    await openApplication(page, ownedApplication.name);
    await backToApplicationList(page, domain.id);
    await openApplication(page, otherApplication.name);

    // Reloading rebuilds the cached permissions from scratch. If in-session navigation had left
    // anything behind, the two views would disagree here.
    const navigated = await page.locator('.gv-submenu a').evaluateAll((links) => links.map((link) => link.getAttribute('title')));
    await page.reload();
    await expect(submenuItem(page, 'Overview')).toBeVisible();
    const reloaded = await page.locator('.gv-submenu a').evaluateAll((links) => links.map((link) => link.getAttribute('title')));

    expect(navigated).toEqual(reloaded);
  });
});
