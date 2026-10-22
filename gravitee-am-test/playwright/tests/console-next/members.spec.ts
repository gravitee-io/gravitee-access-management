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
import { test, expect } from '../../fixtures/base.fixture';
import { createOrganizationGroup, deleteOrganizationGroup } from '@management-commands/group-management-commands';
import { listDomainMemberships } from '@management-commands/membership-management-commands';

/** The administrative roles of a domain in the React console (gravitee-am-ui-next). Runs in the console-next project only. */
test.describe('React console: domain administrative roles', () => {
  const membersUrl = (hrid: string) => `/environments/${process.env.AM_DEF_ENV_HRID}/domains/${hrid}/settings/members`;

  test('lists the primary owner without a role select or a delete action', async ({ page, testDomain }) => {
    await page.goto(membersUrl(testDomain.hrid));

    const table = page.getByRole('table', { name: 'Members' });
    await expect(table.getByText('DOMAIN_PRIMARY_OWNER')).toBeVisible();
    await expect(table.getByRole('combobox')).toHaveCount(0);
    await expect(table.getByRole('button', { name: /^Delete / })).toHaveCount(0);
  });

  test('adds an organization group with a role, then deletes it after the confirmation', async ({ page, adminToken, testDomain }) => {
    const group = await createOrganizationGroup(adminToken, `pw-next-members-${Date.now()}`);
    try {
      await page.goto(membersUrl(testDomain.hrid));

      await page.getByRole('button', { name: 'Add member' }).click();
      const dialog = page.getByRole('dialog', { name: 'Add member' });
      await dialog.getByRole('combobox', { name: 'Type' }).click();
      await page.getByRole('option', { name: 'Group' }).click();
      await dialog.getByRole('combobox', { name: 'Group' }).click();
      await page.getByRole('option', { name: group.name }).click();
      await dialog.getByRole('combobox', { name: 'Role' }).click();
      await page.getByRole('option', { name: 'DOMAIN_USER' }).click();
      const addRequest = page.waitForRequest(
        (request) => request.method() === 'POST' && request.url().endsWith(`/domains/${testDomain.id}/members`),
      );
      const addResponse = page.waitForResponse(
        (response) => response.request().method() === 'POST' && response.url().endsWith(`/domains/${testDomain.id}/members`),
      );
      await dialog.getByRole('button', { name: 'Add member' }).click();

      expect((await addRequest).postDataJSON()).toMatchObject({ memberId: group.id, memberType: 'GROUP' });
      expect((await addResponse).ok()).toBe(true);
      const table = page.getByRole('table', { name: 'Members' });
      await expect(table.getByText(group.name)).toBeVisible();
      const added: any = await listDomainMemberships(testDomain.id, adminToken);
      expect(added.memberships.find((membership) => membership.memberId === group.id)).toBeDefined();

      const deleteResponse = page.waitForResponse(
        (response) => response.request().method() === 'DELETE' && response.url().includes(`/domains/${testDomain.id}/members/`),
      );
      await table.getByRole('button', { name: `Delete ${group.name}` }).click();
      await page.getByRole('dialog', { name: 'Delete member' }).getByRole('button', { name: 'Delete' }).click();
      expect((await deleteResponse).ok()).toBe(true);

      await expect(table.getByText(group.name)).toHaveCount(0);
      const remaining: any = await listDomainMemberships(testDomain.id, adminToken);
      expect(remaining.memberships.find((membership) => membership.memberId === group.id)).toBeUndefined();
    } finally {
      await deleteOrganizationGroup(adminToken, group.id);
    }
  });
});
