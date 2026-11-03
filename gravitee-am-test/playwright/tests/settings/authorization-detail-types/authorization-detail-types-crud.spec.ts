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
import { test, expect } from '../../../fixtures/base.fixture';
import { createAuthorizationDetailType } from '@management-commands/authorization-detail-type-management-commands';
import { AuthorizationDetailTypesPage } from '../../../pages/authorization-detail-types.page';
import { linkJira } from '../../../utils/jira';

const PAYMENT_INITIATION = 'payment_initiation';
const PAYMENT_INITIATION_NAME = 'Payment initiation';
const ONE_HOUR_IN_SECONDS = 3600;

/** type_01 … type_<count>, zero-padded so that ascending type order is numeric order. */
const numberedTypes = (from: number, to: number) =>
  Array.from({ length: to - from + 1 }, (_, i) => `type_${String(from + i).padStart(2, '0')}`);

async function createTypes(domainId: string, adminToken: string, types: string[]): Promise<void> {
  for (const type of types) {
    await createAuthorizationDetailType(domainId, adminToken, { type, name: type });
  }
}

test.describe('Authorization Detail Types - Console', () => {
  test('AM-8005: a type created from the empty state opens on its edit screen', async ({ page, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-8005');
    const rar = new AuthorizationDetailTypesPage(page);

    await test.step('open the create form from the empty state', async () => {
      await rar.navigateTo(testDomain.id);
      await expect(rar.emptyState).toBeVisible();
      await rar.createButton.click();
      await expect(rar.heading).toHaveText('New Authorization Detail Type');
    });

    await test.step('create the type with a one hour consent expiry', async () => {
      await rar.typeInput.fill(PAYMENT_INITIATION);
      await rar.nameInput.fill(PAYMENT_INITIATION_NAME);
      await rar.descriptionInput.fill('Initiates a payment');
      await rar.durationInput.fill('1');
      await rar.selectUnit('HOURS');
      await rar.createSubmit.click();
      await rar.expectSnackbar(`Authorization detail type ${PAYMENT_INITIATION} created`);
    });

    await test.step('the edit screen shows the values entered', async () => {
      await expect(rar.heading).toHaveText(PAYMENT_INITIATION_NAME);
      await expect(rar.typeInput).toHaveValue(PAYMENT_INITIATION);
      await expect(rar.nameInput).toHaveValue(PAYMENT_INITIATION_NAME);
      await expect(rar.descriptionInput).toHaveValue('Initiates a payment');
      await expect(rar.currentPeriod).toContainText('an hour');
    });
  });

  test('AM-8006: the list is paged ten types at a time', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-8006');
    await createTypes(testDomain.id, adminToken, numberedTypes(1, 25));
    const rar = new AuthorizationDetailTypesPage(page);
    await rar.navigateTo(testDomain.id);

    await test.step('the first page holds the first ten types', async () => {
      await expect(rar.rowTypes).toHaveText(numberedTypes(1, 10));
      await expect(rar.pageCount).toContainText('25 total');
    });

    await test.step('the next page follows on', async () => {
      await rar.nextLink.click();
      await expect(rar.rowTypes).toHaveText(numberedTypes(11, 20));
    });

    await test.step('a numbered page jumps there', async () => {
      await rar.numberedLink(3).click();
      await expect(rar.rowTypes).toHaveText(numberedTypes(21, 25));
    });

    await test.step('the previous page goes back', async () => {
      await rar.previousLink.click();
      await expect(rar.rowTypes).toHaveText(numberedTypes(11, 20));
    });

    await test.step('page 1 returns to the start', async () => {
      await rar.numberedLink(1).click();
      await expect(rar.rowTypes).toHaveText(numberedTypes(1, 10));
    });
  });

  test('AM-8007: a change is saved and stays after reloading', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-8007');
    const type = await createAuthorizationDetailType(testDomain.id, adminToken, {
      type: PAYMENT_INITIATION,
      name: PAYMENT_INITIATION_NAME,
      expiresIn: ONE_HOUR_IN_SECONDS,
    });
    const rar = new AuthorizationDetailTypesPage(page);
    await rar.navigateToType(testDomain.id, type.id);
    await expect(rar.saveButton).toBeDisabled();

    await rar.nameInput.click({ clickCount: 3 });
    await rar.nameInput.pressSequentially('Renamed');
    await rar.saveButton.click();
    await rar.expectSnackbar('Authorization detail type updated');
    await expect(rar.saveButton).toBeDisabled();

    await page.reload();
    await rar.waitForReady();
    await expect(rar.nameInput).toHaveValue('Renamed');
  });
});
