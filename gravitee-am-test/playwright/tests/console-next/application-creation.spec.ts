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
import { getApplication } from '@management-commands/application-management-commands';

/** The application creation wizard in the React console (gravitee-am-ui-next). Runs in the console-next project only. */
test.describe('React console: application creation', () => {
  const applicationsUrl = (hrid: string) => `/environments/${process.env.AM_DEF_ENV_HRID}/domains/${hrid}/applications`;

  test('creates a web application and shows the client secret until it is copied', async ({ page, adminToken, testDomain }) => {
    await page.context().grantPermissions(['clipboard-read', 'clipboard-write'], { origin: process.env.AM_UI_NEXT_URL });
    await page.goto(applicationsUrl(testDomain.hrid));

    await page.getByRole('link', { name: 'Create application' }).click();
    await page.getByRole('radio', { name: /^Web/ }).click();
    await page.getByRole('button', { name: 'Next' }).click();
    await page.getByRole('textbox', { name: 'Name' }).fill('React web app');
    await page.getByRole('textbox', { name: 'Redirect URI' }).fill('https://web.example.com/callback');
    await page.getByRole('button', { name: 'Create application' }).click();

    const dialog = page.getByRole('dialog');
    const done = dialog.getByRole('button', { name: 'I have copied the secret' });
    await expect(dialog.getByRole('textbox')).not.toHaveValue('');
    await expect(done).toBeDisabled();
    await dialog.getByRole('button', { name: 'Copy to clipboard' }).click();
    await done.click();

    await expect(page).toHaveURL(/\/applications\/[^/]+\/overview$/);
    const applicationId = page.url().split('/applications/')[1].split('/')[0];
    const created = await getApplication(testDomain.id, adminToken, applicationId);
    expect(created.name).toBe('React web app');
    expect(created.type?.toLowerCase()).toBe('web');
    expect(created.settings?.oauth?.redirectUris).toEqual(['https://web.example.com/callback']);
  });
});
