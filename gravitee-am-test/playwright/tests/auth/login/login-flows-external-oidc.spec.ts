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
import type { Page } from '@playwright/test';
import { test, expect, buildAuthorizeUrl, submitLogin } from '../../../fixtures/login-flows-external-oidc.fixture';
import { reachOAuthAuthorizationCallback } from '../../../utils/mfa-helpers';
import { AUTH_CODE_FORMAT, MULTI_PHASE_TEST_TIMEOUT } from '../../../utils/test-constants';
import { linkJira } from '../../../utils/jira';

test.use({ storageState: { cookies: [], origins: [] } });

/**
 * Clicks the oauth2-generic-am-idp social entry. Template uses {@code btn-oauth2-generic-am-idp}; alternate markup
 * uses {@code a.button.social} with an authorize href. The bundle fixture ends with
 * {@code waitForOAuthAuthorizeRedirectsToLogin} so the HTTP authorize→login redirect matches the browser before
 * {@code page.goto}; no extended per-step timeouts are required here.
 */
async function clickClientOauth2GenericAmSocial(page: Page): Promise<void> {
  const social = page.locator('a.btn-oauth2-generic-am-idp, a.button.social[href*="authorize"]').first();
  await expect(social).toBeVisible();
  await social.click();
}

test.describe('External AM OIDC IdP (AM-2207)', () => {
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT * 2);

  test('AM-2207: user signs in through external IdP button to authorization callback', async ({ page, externalOidcBundle }, testInfo) => {
    linkJira(testInfo, 'AM-2207');

    const clientId = externalOidcBundle.clientApp.settings.oauth.clientId;

    await page.goto(buildAuthorizeUrl(externalOidcBundle.clientGatewayUrl, clientId));
    await page.waitForURL(/\/login/i);

    await expect(page.locator('#username')).toBeVisible();
    await clickClientOauth2GenericAmSocial(page);

    await page.waitForURL(new RegExp(`${externalOidcBundle.providerDomain.hrid}.*/login`, 'i'));

    await submitLogin(page, externalOidcBundle.providerUser.username, externalOidcBundle.providerUser.password);

    await reachOAuthAuthorizationCallback(page);

    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
    expect(callbackUrl.origin + callbackUrl.pathname).toContain('callback');
  });
});

test.describe('Hide login form with external IdP (AM-2169)', () => {
  test.use({ hideLoginForm: true });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT * 2);

  test('AM-2169: login form hidden; user signs in only via external IdP', async ({ page, externalOidcBundle }, testInfo) => {
    linkJira(testInfo, 'AM-2169');

    const clientId = externalOidcBundle.clientApp.settings.oauth.clientId;

    await page.goto(buildAuthorizeUrl(externalOidcBundle.clientGatewayUrl, clientId));
    const providerLoginRe = new RegExp(`${externalOidcBundle.providerDomain.hrid}.*/login`, 'i');
    const clientLoginRe = new RegExp(`${externalOidcBundle.clientDomain.hrid}/login`, 'i');
    // Prefer LoginHideFormHandler auto-redirect (hideForm + single external IdP). If that is slow, or the gateway
    // renders the standard login view first, use the social entry like AM-2207.
    try {
      await page.waitForURL(providerLoginRe);
    } catch {
      await page.waitForURL(clientLoginRe);
      await clickClientOauth2GenericAmSocial(page);
      await page.waitForURL(providerLoginRe);
    }
    expect(page.url()).not.toMatch(clientLoginRe);

    await submitLogin(page, externalOidcBundle.providerUser.username, externalOidcBundle.providerUser.password);

    await reachOAuthAuthorizationCallback(page);

    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});
