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
import {
  test,
  expect,
  PAYMENT_DETAIL,
  PAYMENT_TYPE,
  PAYMENT_TYPE_NAME,
  signInAndConsent,
} from '../../fixtures/user-authorization-detail-consents.fixture';
import { UserDetailPage } from '../../pages/user-detail.page';
import { linkJira } from '../../utils/jira';

test.describe('User authorization detail consents - Console', () => {
  test('AM-8126: authorization details a user consented to are shown on their authorized application', async ({
    page,
    consentsBundle,
  }, testInfo) => {
    linkJira(testInfo, 'AM-8126');
    const userPage = new UserDetailPage(page);

    await test.step('consent to a payment detail on the gateway', async () => {
      await signInAndConsent(page, consentsBundle.gatewayUrl, consentsBundle.detailApp, {
        authorization_details: JSON.stringify([PAYMENT_DETAIL]),
      });
    });

    await test.step("find the application in the user's authorized applications", async () => {
      await userPage.navigateToApplications(consentsBundle.domain.id, await consentsBundle.userId());
      await expect(userPage.applicationRow(consentsBundle.detailApp.name)).toBeVisible();
    });

    await test.step('open it to see the consented detail with its type name', async () => {
      await userPage.openApplication(consentsBundle.detailApp.name);
      await expect(userPage.authorizationDetailConsentRow(PAYMENT_TYPE)).toContainText(PAYMENT_TYPE_NAME);
    });

    await test.step('expand the row to see the detail as consented', async () => {
      await userPage.expandAuthorizationDetailConsent(PAYMENT_TYPE);
      await expect(userPage.authorizationDetailConsentDetail).toContainText('"amount": "10.00"');
    });
  });

  test('AM-8126: an application without authorization detail consents shows no authorization details section', async ({
    page,
    consentsBundle,
  }, testInfo) => {
    linkJira(testInfo, 'AM-8126');
    const userPage = new UserDetailPage(page);

    await test.step('consent to scopes only on the gateway', async () => {
      await signInAndConsent(page, consentsBundle.gatewayUrl, consentsBundle.scopeApp, { scope: 'openid profile' });
    });

    await test.step('open the application from the authorized applications', async () => {
      await userPage.navigateToApplications(consentsBundle.domain.id, await consentsBundle.userId());
      await userPage.openApplication(consentsBundle.scopeApp.name);
    });

    await test.step('see its scopes and no authorization details', async () => {
      await expect(page.locator('datatable-body-row').filter({ hasText: 'openid' })).toBeVisible();
      await expect(userPage.authorizationDetailConsents).toHaveCount(0);
    });
  });
});
