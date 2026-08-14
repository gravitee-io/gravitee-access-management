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

import { test, expect } from '../../fixtures/org-permissions.fixture';
import { deleteOrganizationRole, findOrganizationRoleByName, getOrganizationRole } from '@management-commands/role-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { linkJira } from '../../utils/jira';
import { uniqueTestName } from '../../utils/fixture-helpers';
import { workerScope } from '../../utils/permissions-helpers';
import { CREATE_FAB_SELECTOR, domainListPath, MULTI_PHASE_TEST_TIMEOUT } from '../../utils/test-constants';

type Page = import('@playwright/test').Page;

const ROLES_PAGE = '/settings/roles';
const DOMAIN_LIST = domainListPath();

/** The five permission columns in the role editor, in the order the grid renders them. */
const ACL_COLUMN = { create: 0, read: 1, list: 2, update: 3, delete: 4 };

/** A row of the role editor's permission grid, addressed by the permission it carries. */
const permissionRow = (page: Page, permission: string) =>
  page.locator('tr').filter({ has: page.locator('td').filter({ hasText: new RegExp(`^${permission}$`) }) });

// Every test here builds its own permissions world (domains, roles, personas) and that setup
// counts against the test timeout, so the extended budget applies to all of them rather than to
// the one that happened to ask for it.
test.beforeEach(({}, testInfo) => testInfo.setTimeout(MULTI_PHASE_TEST_TIMEOUT));

/**
 * These run as the administrator, using the signed-in state the setup project produces. The
 * organization tier is where roles themselves are administered, so most of AM-6031 is about what
 * that editor lets you do rather than about a restricted persona's view.
 */
test.describe('Organization-level roles - administering roles in the Console', () => {
  test('AM-6031: an organization administrator can create a custom role carrying a chosen permission', async ({ page }, testInfo) => {
    linkJira(testInfo, 'AM-6031');

    const roleName = uniqueTestName(workerScope('Org-QA-Auditor'));

    await page.goto('/settings/roles/new');
    await page.locator('input[name="name"]').fill(roleName);
    await page.locator('mat-select[name="type"]').click();
    await page
      .locator('mat-option')
      .filter({ hasText: /^\s*ORGANIZATION\s*$/ })
      .first()
      .click();
    await page.getByRole('button', { name: /^CREATE$/ }).click();

    // Creation lands on the new role's editor, where permissions are granted separately — the API
    // rejects a permissions property at creation, so this second step is required, not cosmetic.
    await page.waitForURL(/\/settings\/roles\/(?!new)[\w-]+/);
    await permissionRow(page, 'DOMAIN').locator('mat-checkbox').nth(ACL_COLUMN.read).click();
    await page.getByRole('button', { name: /^SAVE$/ }).click();

    // Reopened from scratch, the grid must still show the permission as granted.
    await page.reload();
    const readBox = permissionRow(page, 'DOMAIN').locator('mat-checkbox').nth(ACL_COLUMN.read).locator('input');
    await expect(readBox).toBeChecked();

    const adminToken = await requestAdminAccessToken();
    const listed = await findOrganizationRoleByName(adminToken, roleName);
    const created = await getOrganizationRole(adminToken, listed.id);
    expect(created.permissions).toEqual(['domain_read']);

    await deleteOrganizationRole(adminToken, created.id).catch(() => undefined);
  });

  test('AM-6031: the permissions of a system role cannot be edited', async ({ page }, testInfo) => {
    linkJira(testInfo, 'AM-6031');

    await page.goto(ROLES_PAGE);
    await page.getByText('ORGANIZATION_PRIMARY_OWNER', { exact: true }).first().click();
    await page.waitForURL(/\/settings\/roles\/[\w-]+/);

    // Positive anchor: the grid has to have rendered before "everything is disabled" means anything.
    const checkboxes = page.locator('mat-checkbox input');
    await expect(checkboxes.first()).toBeVisible();
    const total = await checkboxes.count();
    expect(total).toBeGreaterThan(0);

    const enabled = await checkboxes.evaluateAll((inputs) => inputs.filter((input) => !(input as HTMLInputElement).disabled).length);
    expect(enabled).toBe(0);
  });

  test('AM-6031: the permissions of a non-system role remain editable', async ({ page }, testInfo) => {
    linkJira(testInfo, 'AM-6031');

    // The counterpart to the case above: without it, a Console that disabled every checkbox
    // everywhere would still look correct.
    await page.goto(ROLES_PAGE);
    await page.getByText('DOMAIN_OWNER', { exact: true }).first().click();
    await page.waitForURL(/\/settings\/roles\/[\w-]+/);

    const checkboxes = page.locator('mat-checkbox input');
    await expect(checkboxes.first()).toBeVisible();

    const disabled = await checkboxes.evaluateAll((inputs) => inputs.filter((input) => (input as HTMLInputElement).disabled).length);
    expect(disabled).toBe(0);
  });
});

/**
 * The restricted views. Each signs in as its own persona, so the administrator session above must
 * not leak into them.
 */
test.describe('Organization-level roles - what a restricted organization user reaches', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  test('AM-6031: a user holding only the default organization role is offered no way to create a domain', async ({
    page,
    orgWorld,
    signInAs,
  }, testInfo) => {
    linkJira(testInfo, 'AM-6031');

    await signInAs(orgWorld.orgUser);

    // They cannot reach the domain list at all — the Console reports no environment rather than
    // showing an empty one, so there is nowhere to offer a create control.
    await expect(page.getByText(/you don't have any environment yet/i)).toBeVisible();
    await expect(page.locator(CREATE_FAB_SELECTOR)).toHaveCount(0);
  });

  test('AM-6031: a custom organization role granting only DOMAIN[READ] exposes no domains and no way to create one', async ({
    page,
    orgWorld,
    signInAs,
  }, testInfo) => {
    linkJira(testInfo, 'AM-6031');

    await signInAs(orgWorld.auditor);
    await page.goto(DOMAIN_LIST);

    // Anchor: the Console shell rendered and the session is real, so the absences below are about
    // this role rather than an unloaded page.
    await expect(page.getByText('Access Management').first()).toBeVisible();

    // DOMAIN[READ] is neither DOMAIN[LIST] nor ENVIRONMENT[LIST]. The domain exists and this role
    // could read it by id, but the Console offers no route to it: asking for the domain list does
    // not even stay on that URL, nothing lists the domain, and no control creates one.
    await expect(page).not.toHaveURL(/\/domains\/?$/);
    await expect(page.getByRole('link', { name: orgWorld.domain.name, exact: true })).toHaveCount(0);
    await expect(page.locator(CREATE_FAB_SELECTOR)).toHaveCount(0);
  });
});
