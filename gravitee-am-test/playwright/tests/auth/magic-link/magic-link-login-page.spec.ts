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
import { expect, test } from '../../../fixtures/magic-link-gateway.fixture';
import { linkJira } from '../../../utils/jira';
import { MULTI_PHASE_TEST_TIMEOUT } from '../../../utils/test-constants';

/** Rendered on the login page only when the domain allows magic link. */
const MAGIC_LINK_OPTION = '#magicLinkLinkButton';
/** The email field on the magic link login page. */
const MAGIC_LINK_EMAIL_FIELD = '#email';
/** The password field, which identifies the standard login page. */
const PASSWORD_FIELD = '#password';

const authorizeUrl = (gatewayUrl: string, clientId: string, redirectUri: string) =>
  `${gatewayUrl}/oauth/authorize?response_type=code&client_id=${encodeURIComponent(clientId)}&redirect_uri=${encodeURIComponent(redirectUri)}`;

test.describe('Gateway magic link login page', () => {
  test.use({ storageState: { cookies: [], origins: [] } });
  test.describe.configure({ timeout: MULTI_PHASE_TEST_TIMEOUT });

  test('AM-6705: the sign-in page offers magic link, and the page it opens returns to sign in', async ({ page, magicLinkBundle }, testInfo) => {
    linkJira(testInfo, 'AM-6705');

    const clientId = magicLinkBundle.app.settings.oauth.clientId;
    await page.goto(authorizeUrl(magicLinkBundle.gatewayUrl, clientId, 'https://gravitee.io/callback'));
    await page.waitForURL(/.*login.*/i);

    await expect(page.locator(MAGIC_LINK_OPTION)).toBeVisible();
    await page.locator(MAGIC_LINK_OPTION).click();

    await page.waitForURL(/.*magic-link\/login.*/i);
    await expect(page.locator(MAGIC_LINK_EMAIL_FIELD)).toBeVisible();

    await page.getByText('Back to sign in').click();

    await page.waitForURL(/.*\/login.*/i);
    // The password field is what tells the standard login page apart from the magic link one.
    await expect(page.locator(PASSWORD_FIELD)).toBeVisible();
  });

  test('AM-6702: the sign-in page does not offer magic link when the toggle is off', async ({ page, magicLinkBundle }, testInfo) => {
    linkJira(testInfo, 'AM-6702');

    const clientId = magicLinkBundle.app.settings.oauth.clientId;

    // Positive anchor first: the option is there while the toggle is on, so its absence below is
    // caused by the toggle rather than by a selector that never matches.
    await page.goto(authorizeUrl(magicLinkBundle.gatewayUrl, clientId, 'https://gravitee.io/callback'));
    await page.waitForURL(/.*login.*/i);
    await expect(page.locator(MAGIC_LINK_OPTION)).toBeVisible();

    await magicLinkBundle.setMagicLinkEnabled(false);

    // The login page is server-rendered, so the option only disappears on a fresh request.
    // Re-asserting against an already-loaded page would never see the change.
    await expect
      .poll(
        async () => {
          await page.goto(authorizeUrl(magicLinkBundle.gatewayUrl, clientId, 'https://gravitee.io/callback'));
          await page.waitForURL(/.*login.*/i);
          return page.locator(MAGIC_LINK_OPTION).count();
        },
        { timeout: 60000, intervals: [2000] },
      )
      .toBe(0);

    // Still the login page, so the option is gone rather than the page having changed.
    await expect(page.locator(PASSWORD_FIELD)).toBeVisible();
  });
});
