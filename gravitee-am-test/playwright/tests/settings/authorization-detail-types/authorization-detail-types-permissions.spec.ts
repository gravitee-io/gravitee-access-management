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
import { test, expect } from '../../../fixtures/authorization-detail-type-permissions.fixture';
import { AuthorizationDetailTypesPage } from '../../../pages/authorization-detail-types.page';
import { linkJira } from '../../../utils/jira';
import { domainListPath, MULTI_PHASE_TEST_TIMEOUT } from '../../../utils/test-constants';

/** The Console root, with or without a trailing slash. */
const CONSOLE_ROOT = /^https?:\/\/[^/]+\/?$/;

// Every test signs in as its own persona, so no shared authenticated state.
test.use({ storageState: { cookies: [], origins: [] } });

// Each test builds its own domain, roles and personas, and that setup counts against the timeout.
test.beforeEach(({}, testInfo) => testInfo.setTimeout(MULTI_PHASE_TEST_TIMEOUT));

test.describe('Authorization Detail Types - Console permissions', () => {
  test('AM-8009: list permission shows the types read-only and keeps the create screen closed', async ({
    page,
    rarWorld,
    signInAs,
  }, testInfo) => {
    linkJira(testInfo, 'AM-8009');
    const rar = new AuthorizationDetailTypesPage(page);
    const type = rarWorld.paymentInitiation.type;
    await signInAs(rarWorld.listUser);

    await test.step('the list shows the type with its discovery toggle disabled', async () => {
      await rar.navigateTo(rarWorld.domain.id);
      await expect(rar.rowByType(type)).toBeVisible();
      await expect(rar.discoveryToggle(type)).toBeDisabled();
    });

    await test.step('no create, edit or delete action is offered', async () => {
      await expect(rar.createButton).toHaveCount(0);
      await expect(rar.editAction(type)).toHaveCount(0);
      await expect(rar.deleteAction(type)).toHaveCount(0);
    });

    await test.step('the create screen does not open from its URL', async () => {
      await page.goto(`${rar.listPath(rarWorld.domain.id)}/new`);
      // The route guard sends a user without CREATE back to the Console root.
      await expect(page).toHaveURL(CONSOLE_ROOT);
      await expect(page.getByRole('heading', { name: 'New Authorization Detail Type' })).toHaveCount(0);
    });
  });

  test('AM-8009: read permission gives a read-only edit screen', async ({ page, rarWorld, signInAs }, testInfo) => {
    linkJira(testInfo, 'AM-8009');
    const rar = new AuthorizationDetailTypesPage(page);
    const type = rarWorld.paymentInitiation;
    await signInAs(rarWorld.readUser);

    await test.step('open the type with its edit action', async () => {
      await rar.navigateTo(rarWorld.domain.id);
      await rar.editAction(type.type).click();
      await expect(rar.heading).toHaveText(type.name);
    });

    await test.step('every field is read-only', async () => {
      await expect(rar.nameInput).toHaveValue(type.name);
      await expect(rar.nameInput).not.toBeEditable();
      await expect(rar.descriptionInput).not.toBeEditable();
      await expect(rar.schemaEditor).toBeVisible();
      await rar.schemaEditor.click();
      await page.keyboard.type('{}');
      await expect(rar.schemaEditor).not.toContainText('{}');
    });

    await test.step('no clear expiry, Format, SAVE or DELETE is offered', async () => {
      await expect(rar.currentPeriod).toContainText('an hour');
      await expect(rar.clearExpiryButton).toHaveCount(0);
      await expect(rar.formatButton).toHaveCount(0);
      await expect(rar.saveButton).toHaveCount(0);
      await expect(rar.deleteButton).toHaveCount(0);
    });
  });

  test('AM-8009: without list permission the menu has no RAR entry', async ({ page, rarWorld, signInAs }, testInfo) => {
    linkJira(testInfo, 'AM-8009');
    await signInAs(rarWorld.noneUser);

    await page.goto(`${domainListPath()}/${rarWorld.domain.id}/settings`);
    // Scopes sits in the same OAuth 2.0 section and stays visible, so the section itself rendered.
    await expect(page.getByRole('link', { name: 'Scopes', exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'RAR', exact: true })).toHaveCount(0);
  });
});
