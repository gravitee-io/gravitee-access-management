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
import { safeDeleteDomain } from '@management-commands/domain-management-commands';

import { test, expect } from '../../fixtures/domain-picker.fixture';
import { uniqueTestName as uniqueName } from '../../utils/fixture-helpers';

// tests share the admin user's console preferences, so run them one at a time
test.describe.configure({ mode: 'serial' });

test.describe('Navbar domain picker', () => {
  test('opens from the domain chip and shows the current domain', async ({ homePage, navbar, currentDomain }) => {
    await homePage.navigateToDomain(currentDomain.name);

    await navbar.open();

    await expect(navbar.sectionLabel('Current')).toBeVisible();
    await expect(navbar.row(currentDomain.name)).toBeVisible();
  });

  test('stays reachable from the domains list when no domain is selected', async ({ homePage, navbar }) => {
    await homePage.gotoDomainsList();

    await expect(navbar.trigger).toBeVisible();
    await navbar.open();
    await expect(navbar.search).toBeVisible();
  });

  test('renders Current, Default and Pinned sections from saved preferences', async ({
    homePage,
    navbar,
    currentDomain,
    extraDomains,
    setPreferences,
  }) => {
    await setPreferences({
      defaultDomainId: extraDomains.toDefault.id,
      defaultEnvironmentId: process.env.AM_DEF_ENV_ID,
      pinnedDomainIds: [extraDomains.toPin.id],
    });

    await homePage.navigateToDomain(currentDomain.name);
    await navbar.open();

    await expect(navbar.sectionLabel('Current')).toBeVisible();
    await expect(navbar.row(currentDomain.name)).toBeVisible();

    await expect(navbar.sectionLabel('Default')).toBeVisible();
    await expect(navbar.row(extraDomains.toDefault.name)).toBeVisible();

    await expect(navbar.sectionLabel('Pinned')).toBeVisible();
    await expect(navbar.row(extraDomains.toPin.name)).toBeVisible();

    // current domain is not the default, so the chip carries no star
    await expect(navbar.chipDefaultStar).toHaveCount(0);
  });

  test('pinning a domain from search persists it under Pinned', async ({ homePage, navbar, currentDomain, extraDomains }) => {
    await homePage.navigateToDomain(currentDomain.name);
    await navbar.open();

    await navbar.searchFor(extraDomains.toPin.name);
    await expect(navbar.row(extraDomains.toPin.name)).toBeVisible();
    await navbar.pin(extraDomains.toPin.name);

    // clearing the search returns to the Current/Pinned view where the pin should now stick
    await navbar.searchFor('');
    await expect(navbar.sectionLabel('Pinned')).toBeVisible();
    await expect(navbar.row(extraDomains.toPin.name)).toBeVisible();
  });

  test('marks the chip with a star when the current domain is the default', async ({ homePage, navbar, currentDomain, setPreferences }) => {
    await setPreferences({
      defaultDomainId: currentDomain.id,
      defaultEnvironmentId: process.env.AM_DEF_ENV_ID,
    });

    await homePage.navigateToDomain(currentDomain.name);

    await expect(navbar.chipDefaultStar).toBeVisible();

    // when current is the default it only appears under Current, never a separate Default section
    await navbar.open();
    await expect(navbar.sectionLabel('Current')).toBeVisible();
    await expect(navbar.sectionLabel('Default')).toHaveCount(0);
  });

  test('creates a domain from the picker New link and switches to it', async ({
    page,
    homePage,
    navbar,
    currentDomain,
    trackedDomainIds,
  }) => {
    await homePage.navigateToDomain(currentDomain.name);

    await navbar.open();
    await navbar.newDomainLink.click();

    // the create form only needs a name — the data plane defaults to the first available
    const name = uniqueName('pw-ui');
    await page.locator('input[name="name"]').fill(name);
    await page.getByRole('button', { name: /create/i }).click();

    // creation navigates to the new domain, which becomes the current domain
    const uuid = /\/domains\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/i;
    await page.waitForURL(uuid);
    const createdId = page.url().match(uuid)?.[1];
    expect(createdId).toBeTruthy();
    trackedDomainIds.push(createdId!);

    await expect(navbar.trigger).toContainText(name.toLowerCase());

    await navbar.open();
    await expect(navbar.row(name)).toBeVisible();
  });

  test('reload lands the user directly on their default domain', async ({ homePage, extraDomains, setPreferences }) => {
    await setPreferences({
      defaultDomainId: extraDomains.toDefault.id,
      defaultEnvironmentId: process.env.AM_DEF_ENV_ID,
    });

    await homePage.navigate('/');

    await homePage.expectUrlMatches(new RegExp(`/domains/${extraDomains.toDefault.id}`));
  });

  test('falls back gracefully when the default domain has been deleted', async ({
    page,
    homePage,
    adminToken,
    extraDomains,
    setPreferences,
  }) => {
    await setPreferences({
      defaultDomainId: extraDomains.toDefault.id,
      defaultEnvironmentId: process.env.AM_DEF_ENV_ID,
    });

    await safeDeleteDomain(extraDomains.toDefault.id, adminToken);

    await homePage.navigate('/');

    // never tries to load the now-deleted domain, and no error is shown
    expect(page.url()).not.toContain(extraDomains.toDefault.id);
    await expect(page.locator('text=/not found|error/i')).toHaveCount(0);
  });

  test('pin/default controls on the "All domains" list stay in sync with the navbar picker', async ({ homePage, navbar, extraDomains }) => {
    await homePage.gotoDomainsList();

    await homePage.togglePinInList(extraDomains.toPin.name);
    await homePage.toggleDefaultInList(extraDomains.toDefault.name);

    // toHaveText polls until the async toggle request resolves and the icon re-renders
    await expect(homePage.pinIconInList(extraDomains.toPin.name)).toHaveText('bookmark');
    await expect(homePage.defaultIconInList(extraDomains.toDefault.name)).toHaveText('star');

    // reflected in the navbar picker
    await navbar.open();
    await expect(navbar.sectionLabel('Default')).toBeVisible();
    await expect(navbar.row(extraDomains.toDefault.name)).toBeVisible();
    await expect(navbar.sectionLabel('Pinned')).toBeVisible();
    await expect(navbar.row(extraDomains.toPin.name)).toBeVisible();

    // and unpinning from the navbar picker is reflected back on the "All domains" list
    await navbar.pin(extraDomains.toPin.name);
    await homePage.gotoDomainsList();
    await expect(homePage.pinIconInList(extraDomains.toPin.name)).toHaveText('bookmark_border');
  });

  test('avatar opens the account menu, not the domain picker', async ({ homePage, navbar, currentDomain }) => {
    await homePage.navigateToDomain(currentDomain.name);

    await navbar.accountMenuTrigger.click();

    await expect(navbar.accountMenu).toBeVisible();
    await expect(navbar.search).toHaveCount(0);
  });

  test('a pinned domain survives a full page reload', async ({ page, homePage, navbar, currentDomain, extraDomains }) => {
    await homePage.navigateToDomain(currentDomain.name);
    await navbar.open();
    await navbar.searchFor(extraDomains.toPin.name);
    await navbar.pin(extraDomains.toPin.name);
    await navbar.searchFor('');

    await page.reload();
    await homePage.waitForReady();

    await navbar.open();
    await expect(navbar.sectionLabel('Pinned')).toBeVisible();
    await expect(navbar.row(extraDomains.toPin.name)).toBeVisible();
  });

  test('unpinning a domain via the picker removes it from Pinned', async ({
    homePage,
    navbar,
    currentDomain,
    extraDomains,
    setPreferences,
  }) => {
    await setPreferences({ pinnedDomainIds: [extraDomains.toPin.id] });

    await homePage.navigateToDomain(currentDomain.name);
    await navbar.open();
    await expect(navbar.row(extraDomains.toPin.name)).toBeVisible();

    await navbar.pin(extraDomains.toPin.name);

    await expect(navbar.row(extraDomains.toPin.name)).toHaveCount(0);
    await expect(navbar.sectionLabel('Pinned')).toHaveCount(0);
  });

  test('clicking the picker star sets a domain as default', async ({ homePage, navbar, currentDomain, extraDomains }) => {
    await homePage.navigateToDomain(currentDomain.name);
    await navbar.open();
    await navbar.searchFor(extraDomains.toDefault.name);
    await navbar.setDefault(extraDomains.toDefault.name);
    await navbar.searchFor('');

    await expect(navbar.sectionLabel('Default')).toBeVisible();
    await expect(navbar.row(extraDomains.toDefault.name)).toBeVisible();
  });

  test('clicking the picker star again unsets the default domain', async ({
    homePage,
    navbar,
    currentDomain,
    extraDomains,
    setPreferences,
  }) => {
    await setPreferences({
      defaultDomainId: extraDomains.toDefault.id,
      defaultEnvironmentId: process.env.AM_DEF_ENV_ID,
    });

    await homePage.navigateToDomain(currentDomain.name);
    await navbar.open();
    await expect(navbar.sectionLabel('Default')).toBeVisible();

    await navbar.setDefault(extraDomains.toDefault.name);

    await expect(navbar.sectionLabel('Default')).toHaveCount(0);
  });

  test('search filters the picker to matching domains only', async ({ homePage, navbar, currentDomain, extraDomains }) => {
    await homePage.navigateToDomain(currentDomain.name);
    await navbar.open();

    await navbar.searchFor(extraDomains.toPin.name);

    await expect(navbar.sectionLabel('Results')).toBeVisible();
    await expect(navbar.row(extraDomains.toPin.name)).toBeVisible();
    await expect(navbar.row(extraDomains.toDefault.name)).toHaveCount(0);
    await expect(navbar.sectionLabel('Current')).toHaveCount(0);
  });

  test('search with no matches shows the empty message', async ({ homePage, navbar, currentDomain }) => {
    await homePage.navigateToDomain(currentDomain.name);
    await navbar.open();

    await navbar.searchFor('zz-no-such-domain-exists-xyz');

    await expect(navbar.noSearchResultsMessage).toHaveText(/no domains match/i);
  });

  test('a deleted pinned domain quietly drops out of the picker', async ({
    homePage,
    navbar,
    currentDomain,
    extraDomains,
    adminToken,
    setPreferences,
  }) => {
    await setPreferences({ pinnedDomainIds: [extraDomains.toPin.id] });
    await safeDeleteDomain(extraDomains.toPin.id, adminToken);

    await homePage.navigateToDomain(currentDomain.name);
    await navbar.open();

    await expect(navbar.row(extraDomains.toPin.name)).toHaveCount(0);
    await expect(navbar.search).toBeVisible();
  });
});
