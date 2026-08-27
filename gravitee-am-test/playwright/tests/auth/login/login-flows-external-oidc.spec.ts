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
import { AUTH_CODE_FORMAT, BRIEF_TIMEOUT, MULTI_PHASE_TEST_TIMEOUT } from '../../../utils/test-constants';
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

    // Matched on the path alone rather than anywhere in the URL. The provider's authorize URL
    // carries the client's own `/login/callback` inside its redirect_uri parameter, so a
    // whole-URL match counts hops that never went near the client's login page.
    const clientLoginPath = `/${externalOidcBundle.clientDomain.hrid}/login`;
    const providerLoginPath = `/${externalOidcBundle.providerDomain.hrid}/login`;

    // LoginHideFormHandler sits on /login itself, so the browser does reach that URL — it is
    // answered with a 302 rather than a rendered form. Recording the status is therefore how
    // "the form was hidden" is proved; the URL alone cannot show it.
    const clientLoginStatuses: number[] = [];
    page.on('response', (response) => {
      if (new URL(response.url()).pathname === clientLoginPath) {
        clientLoginStatuses.push(response.status());
      }
    });

    await page.goto(buildAuthorizeUrl(externalOidcBundle.clientGatewayUrl, clientId));

    // No fallback to clicking the provider button by hand: if the redirect does not happen, the
    // browser stays on a rendered login form and this fails, which is the point of the feature.
    await page.waitForURL((url) => url.pathname === providerLoginPath, { timeout: BRIEF_TIMEOUT * 6 });

    // The client's login page is reached exactly once, and answered with a redirect rather than
    // a form. Asserting the whole array reports the statuses themselves when it fails.
    expect(clientLoginStatuses).toEqual([302]);
    await expect(page.locator('#username')).toBeVisible();
    expect(new URL(page.url()).pathname).toEqual(providerLoginPath);

    await submitLogin(page, externalOidcBundle.providerUser.username, externalOidcBundle.providerUser.password);

    await reachOAuthAuthorizationCallback(page);

    const callbackUrl = new URL(page.url());
    expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  });
});

test.describe('Login form shown when hiding is off (AM-2169)', () => {
  test.use({ hideLoginForm: false });
  test.setTimeout(MULTI_PHASE_TEST_TIMEOUT * 2);

  test('AM-2169: the same domain serves its own login form when the setting is off', async ({ page, externalOidcBundle }, testInfo) => {
    linkJira(testInfo, 'AM-2169');

    const clientId = externalOidcBundle.clientApp.settings.oauth.clientId;
    const clientLoginPath = `/${externalOidcBundle.clientDomain.hrid}/login`;

    await page.goto(buildAuthorizeUrl(externalOidcBundle.clientGatewayUrl, clientId));
    await page.waitForURL((url) => url.pathname === clientLoginPath);

    // The comparison that gives the test above its meaning: same domain, same single external
    // provider, only the setting differs — and here the form is served rather than redirected past.
    await expect(page.locator('#username')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();
  });
});
