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
import { ApplicationGeneralSettingsPage } from '../../pages/application-general-settings.page';
import { Oauth2SettingsPage } from '../../pages/oauth2-settings.page';
import { linkJira } from '../../utils/jira';
import { submenuItem } from '../../utils/permissions-helpers';
import { APPLICATION_OVERVIEW_URL, MULTI_PHASE_TEST_TIMEOUT } from '../../utils/test-constants';

// Each test signs in as its own persona, so no shared authenticated state.
test.use({ storageState: { cookies: [], origins: [] } });

// Every test here builds its own permissions world (domains, applications, personas, memberships)
// and that setup counts against the test timeout, so the extended budget applies to all of them
// rather than to the few that happened to ask for it.
test.beforeEach(({}, testInfo) => testInfo.setTimeout(MULTI_PHASE_TEST_TIMEOUT));

type Page = import('@playwright/test').Page;

/** An application row in the list, rendered as <a [routerLink]="[row.id]">{{ row.name }}</a>. */
const applicationLink = (page: Page, name: string) => page.getByRole('link', { name, exact: true });

/**
 * The OAuth2 settings form saves through a plain mat-raised-button, not a submit button, so
 * BasePage.clickSave() cannot reach it. It stays disabled until the form is dirty.
 */
const oauthSaveButton = (page: Page) => page.getByRole('button', { name: /^SAVE$/ });

test.describe('Application-level roles - Console visibility', () => {
  test('AM-6036: an application owner can rename the application they own', async ({ page, permissionsWorld, signInAs }, testInfo) => {
    linkJira(testInfo, 'AM-6036');

    await signInAs(permissionsWorld.appOwner);

    const settings = new ApplicationGeneralSettingsPage(page);
    await settings.navigateTo(permissionsWorld.domain.id, permissionsWorld.ownedApplication.id);

    const renamed = `${permissionsWorld.ownedApplication.name}-renamed`;
    await settings.fillName(renamed);
    await settings.clickSave();

    await settings.expectSnackbar(/updated|saved/i);
    await expect(settings.nameInput).toHaveValue(renamed);
  });

  test('AM-6036: an application owner can change the client authentication method', async ({
    page,
    permissionsWorld,
    signInAs,
  }, testInfo) => {
    linkJira(testInfo, 'AM-6036');

    await signInAs(permissionsWorld.appOwner);

    const oauth = new Oauth2SettingsPage(page);
    await oauth.navigateTo(permissionsWorld.domain.id, permissionsWorld.ownedApplication.id);

    await expect(oauth.tokenAuthMethodSelect).toBeVisible();
    await oauth.tokenAuthMethodSelect.click();
    await oauth.tokenAuthMethodOption(/client_secret_post/).click();

    await expect(oauthSaveButton(page)).toBeEnabled();
    await oauthSaveButton(page).click();

    await oauth.expectSnackbar(/updated|saved/i);
    expect(await oauth.getTokenAuthMethodText()).toContain('client_secret_post');
  });

  test('AM-6036: an application owner sees only the application they are a member of', async ({
    page,
    permissionsWorld,
    signInAs,
  }, testInfo) => {
    linkJira(testInfo, 'AM-6036');

    await signInAs(permissionsWorld.appOwner);

    // Positive anchor before the absence assertion — on an unrendered page every row is missing,
    // so the owned application must be shown first for the exclusion below to mean anything.
    await expect(applicationLink(page, permissionsWorld.ownedApplication.name)).toBeVisible();
    await expect(applicationLink(page, permissionsWorld.otherApplication.name)).toHaveCount(0);
  });

  test('AM-6036: a read-only application role hides the editing menus', async ({ page, permissionsWorld, signInAs }, testInfo) => {
    linkJira(testInfo, 'AM-6036');

    await signInAs(permissionsWorld.appViewer);

    await applicationLink(page, permissionsWorld.ownedApplication.name).click();
    await page.waitForURL(APPLICATION_OVERVIEW_URL);

    // Anchor on the menus a reader is entitled to before asserting the rest are absent.
    await expect(submenuItem(page, 'Overview')).toBeVisible();
    await expect(submenuItem(page, 'Endpoints')).toBeVisible();

    await expect(submenuItem(page, 'Settings')).toHaveCount(0);
    await expect(submenuItem(page, 'Identity Providers')).toHaveCount(0);
    await expect(submenuItem(page, 'Design')).toHaveCount(0);
    await expect(submenuItem(page, 'Analytics')).toHaveCount(0);
  });

  test('AM-6036: an application owner keeps the editing menus the reader is denied', async ({
    page,
    permissionsWorld,
    signInAs,
  }, testInfo) => {
    linkJira(testInfo, 'AM-6036');

    await signInAs(permissionsWorld.appOwner);

    await applicationLink(page, permissionsWorld.ownedApplication.name).click();
    await page.waitForURL(APPLICATION_OVERVIEW_URL);

    await expect(submenuItem(page, 'Overview')).toBeVisible();
    await expect(submenuItem(page, 'Settings')).toBeVisible();
  });
});
