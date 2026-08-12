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

import { test, expect } from '../../fixtures/domain-permissions.fixture';
import { ApplicationCreationPage } from '../../pages/application-creation.page';
import { linkJira } from '../../utils/jira';
import { uniqueTestName } from '../../utils/fixture-helpers';
import { MULTI_PHASE_TEST_TIMEOUT } from '../../utils/test-constants';

// Every test signs in as its own persona, so no shared authenticated state.
test.use({ storageState: { cookies: [], origins: [] } });

const DOMAIN_LIST = '/environments/default/domains';

/** Left-hand navigation entries for the domain a user is currently inside. */
const domainMenuLink = (page: import('@playwright/test').Page, label: string) => page.getByRole('link', { name: label, exact: true });

test.describe('Domain-level roles - Console visibility', () => {
  test('AM-6032: a domain owner can create an application inside their own domain', async ({ page, domainWorld, signInAs }, testInfo) => {
    linkJira(testInfo, 'AM-6032');
    test.setTimeout(MULTI_PHASE_TEST_TIMEOUT);

    await signInAs(domainWorld.devManager);

    await page.goto(`/environments/default/domains/${domainWorld.devDomain.id}/applications`);
    const wizard = new ApplicationCreationPage(page);
    await wizard.openWizard();

    const created = uniqueTestName('made-by-domain-owner');
    await wizard.selectAppType('Web');
    await wizard.clickNext();
    await wizard.fillAppName(created);
    await wizard.fillRedirectUri('https://example.com/callback');
    await wizard.clickCreate();

    // The wizard's own route is .../applications/new, so waiting for an /applications/ URL would
    // match before anything happened. Go back to the list instead and look for the application:
    // it appearing there is the outcome the scenario actually asks for.
    await page.goto(`/environments/default/domains/${domainWorld.devDomain.id}/applications`);
    await expect(page.getByRole('link', { name: created, exact: true })).toBeVisible();
  });

  test('AM-6032: a domain owner sees only the domain they are assigned to', async ({ page, domainWorld, signInAs }, testInfo) => {
    linkJira(testInfo, 'AM-6032');

    await signInAs(domainWorld.qaManager);
    await page.goto(DOMAIN_LIST);

    // Positive anchor before the exclusion — an unrendered list would otherwise satisfy it.
    await expect(page.getByRole('link', { name: domainWorld.qaDomain.name, exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: domainWorld.devDomain.name, exact: true })).toHaveCount(0);
  });

  test('AM-6032: a domain owner is offered no way to create another domain', async ({ page, domainWorld, signInAs }, testInfo) => {
    linkJira(testInfo, 'AM-6032');

    await signInAs(domainWorld.qaManager);
    await page.goto(DOMAIN_LIST);

    await expect(page.getByRole('link', { name: domainWorld.qaDomain.name, exact: true })).toBeVisible();
    // DOMAIN[CREATE] is authorised at environment and organization level, never at domain level,
    // so a domain owner never gets the affordance regardless of what their role carries.
    await expect(page.locator('a[mat-fab], button[mat-fab]')).toHaveCount(0);
  });

  test('AM-6032: a user holding only the default organization role reaches no domain at all', async ({
    page,
    domainWorld,
    signInAs,
  }, testInfo) => {
    linkJira(testInfo, 'AM-6032');

    await signInAs(domainWorld.basicUser);

    // They are not merely shown an empty list — the Console tells them they have no environment.
    await expect(page.getByText(/you don't have any environment yet/i)).toBeVisible();
    await expect(page.getByRole('link', { name: domainWorld.devDomain.name, exact: true })).toHaveCount(0);
    await expect(page.getByRole('link', { name: domainWorld.qaDomain.name, exact: true })).toHaveCount(0);
  });

  test('AM-6032: an application owner lands on the applications list with the domain menus withheld', async ({
    page,
    domainWorld,
    signInAs,
  }, testInfo) => {
    linkJira(testInfo, 'AM-6032');

    await signInAs(domainWorld.devAppOwner);

    // Sign-in takes them straight to the applications list of the domain holding their application.
    await expect(page).toHaveURL(new RegExp(`/domains/${domainWorld.devDomain.id}/applications`));

    // Anchor on the one menu they are entitled to before asserting the rest are withheld.
    await expect(domainMenuLink(page, 'Applications')).toBeVisible();
    for (const withheld of ['Dashboard', 'Settings', 'Alerts', 'Authorization']) {
      await expect(domainMenuLink(page, withheld)).toHaveCount(0);
    }
  });

  test('AM-6032: an application owner sees only the application they are a member of', async ({
    page,
    domainWorld,
    signInAs,
  }, testInfo) => {
    linkJira(testInfo, 'AM-6032');

    await signInAs(domainWorld.devAppOwner);

    await expect(page.getByRole('link', { name: domainWorld.devApplication.name, exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: domainWorld.devOtherApplication.name, exact: true })).toHaveCount(0);
  });

  test('AM-6032: a domain owner keeps the domain menus the application owner is denied', async ({
    page,
    domainWorld,
    signInAs,
  }, testInfo) => {
    linkJira(testInfo, 'AM-6032');

    await signInAs(domainWorld.devManager);

    // The counterpart to the withheld-menus case: proves those entries are role-dependent rather
    // than simply absent from this build of the Console.
    for (const granted of ['Dashboard', 'Applications', 'Settings', 'Alerts']) {
      await expect(domainMenuLink(page, granted)).toBeVisible();
    }
  });
});
