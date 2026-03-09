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
import { test as base, expect, Page } from '@playwright/test';

import crossFetch from 'cross-fetch';
globalThis.fetch = crossFetch;

import { requestAdminAccessToken } from '../../../../api/commands/management/token-management-commands';
import {
  createDomain,
  startDomain,
  waitForDomainSync,
  safeDeleteDomain,
  waitForOidcReady,
  patchDomain,
} from '../../../../api/commands/management/domain-management-commands';
import { getAllIdps } from '../../../../api/commands/management/idp-management-commands';
import { createUser, deleteUser } from '../../../../api/commands/management/user-management-commands';
import { createTestApp } from '../../../../api/commands/utils/application-commands';
import { createFactor } from '../../../../api/commands/management/factor-management-commands';
import { createDevice } from '../../../../api/commands/management/device-management-commands';
import { patchApplication } from '../../../../api/commands/management/application-management-commands';
import { Domain, Application, User } from '../../../../api/management/models';
import {
  addVirtualAuthenticator,
  simulateWebAuthnGesture,
  handleConsentIfPresent,
  removeVirtualAuthenticator,
  clearSessionOnly,
  PASSWORDLESS_LINK_SELECTOR,
  VirtualAuthenticator,
} from '../../../fixtures/webauthn.fixture';
import { quietly, uniqueTestName } from '../../../utils/fixture-helpers';
import { API_USER_PASSWORD } from '../../../utils/test-constants';

const REDIRECT_URI = 'https://gravitee.io/callback';
const MOCK_MFA_CODE = '1234';

/**
 * Fixture that sets up a domain with:
 * - Passwordless (WebAuthn) enabled
 * - Mock MFA factor created and enabled on the app
 * - Cookie device identifier for "Remember Device"
 * - MFA enrollment=required, challenge=required, rememberDevice=enabled
 */
const test = base.extend<{
  mfaAdminToken: string;
  mfaDomain: Domain;
  mfaApp: Application;
  mfaUser: User;
  mfaFactorId: string;
  mfaDeviceId: string;
  gatewayUrl: string;
}>({
  mfaAdminToken: async ({}, use) => {
    await use(await requestAdminAccessToken());
  },

  mfaDomain: async ({ mfaAdminToken }, use) => {
    const name = uniqueTestName('pw-mfa-wa');
    const domain = await quietly(() => createDomain(mfaAdminToken, name, 'MFA + WebAuthn + Remember Device test'));

    // Domain is NOT started here — gatewayUrl starts it after app+user+factor
    // are created so the initial sync picks up all resources in one pass.
    await quietly(() =>
      patchDomain(domain.id, mfaAdminToken, {
        loginSettings: {
          inherited: false,
          passwordlessEnabled: true,
          passwordlessDeviceNamingEnabled: false,
        },
        webAuthnSettings: {
          origin: process.env.AM_GATEWAY_URL || 'http://localhost:8092',
          relyingPartyName: name,
          attestationConveyancePreference: 'NONE',
          authenticatorAttachment: 'PLATFORM',
          userVerification: 'REQUIRED',
          requireResidentKey: false,
          forceRegistration: true,
        },
      }),
    );

    await use(domain);
    await quietly(() => safeDeleteDomain(domain.id, mfaAdminToken));
  },

  mfaFactorId: async ({ mfaAdminToken, mfaDomain }, use) => {
    const factor = await quietly(() =>
      createFactor(mfaDomain.id, mfaAdminToken, {
        type: 'mock-am-factor',
        factorType: 'MOCK',
        configuration: `{"code":"${MOCK_MFA_CODE}"}`,
        name: uniqueTestName('mock-factor'),
      }),
    );
    await use(factor.id);
  },

  mfaDeviceId: async ({ mfaAdminToken, mfaDomain }, use) => {
    const device = await quietly(() =>
      createDevice(mfaDomain.id, mfaAdminToken, {
        type: 'cookie-device-identifier',
        configuration: '{}',
        name: uniqueTestName('cookie-device'),
      }),
    );
    await use(device.id);
  },

  mfaApp: async ({ mfaAdminToken, mfaDomain, mfaFactorId, mfaDeviceId }, use) => {
    const idpSet = await getAllIdps(mfaDomain.id, mfaAdminToken);
    const defaultIdp = idpSet.values().next().value;
    if (!defaultIdp) throw new Error('No IdP found');

    const app = await quietly(() =>
      createTestApp(uniqueTestName('pw-mfa-app'), mfaDomain, mfaAdminToken, 'WEB', {
        settings: {
          oauth: {
            redirectUris: [REDIRECT_URI],
            grantTypes: ['authorization_code'],
            scopeSettings: [
              { scope: 'openid', defaultScope: true },
              { scope: 'profile', defaultScope: true },
            ],
          },
        },
        identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
      }),
    );

    // Enable MFA with mock factor + remember device
    await quietly(() =>
      patchApplication(mfaDomain.id, mfaAdminToken, {
        settings: {
          mfa: {
            factor: {
              defaultFactorId: mfaFactorId,
              applicationFactors: [{ id: mfaFactorId, selectionRule: '' }],
            },
            enroll: {
              active: true,
              forceEnrollment: true,
              type: 'REQUIRED',
            },
            challenge: {
              active: true,
              type: 'REQUIRED',
            },
            rememberDevice: {
              active: true,
              deviceIdentifierId: mfaDeviceId,
              expirationTimeSeconds: 180, // 3 minutes
            },
          },
        },
      }, app.id),
    );

    await use(app);
  },

  mfaUser: async ({ mfaAdminToken, mfaDomain }, use) => {
    const user = await quietly(() =>
      createUser(mfaDomain.id, mfaAdminToken, {
        firstName: 'MFA',
        lastName: 'WebAuthn',
        email: `${uniqueTestName('mfa-wa-user')}@example.com`,
        username: uniqueTestName('mfa-wa-user'),
        password: API_USER_PASSWORD,
        preRegistration: false,
      }),
    );
    await use(user);
    await quietly(async () => {
      try {
        await deleteUser(mfaDomain.id, mfaAdminToken, user.id);
      } catch {
        // domain teardown may cascade
      }
    });
  },

  gatewayUrl: async ({ mfaAdminToken, mfaDomain, mfaApp, mfaUser }, use) => {
    // All resources (domain config, app, user, factor, device) are created
    // before starting the domain, so the initial sync picks up everything.
    void mfaApp;
    void mfaUser;
    await quietly(() => startDomain(mfaDomain.id, mfaAdminToken));
    await quietly(() => waitForDomainSync(mfaDomain.id));
    await waitForOidcReady(mfaDomain.hrid, { timeoutMs: 30000, intervalMs: 500 });
    const baseUrl = process.env.AM_GATEWAY_URL || 'http://localhost:8092';
    await use(`${baseUrl}/${mfaDomain.hrid}`);
  },
});

/**
 * Helper: Full first-time login flow with MFA enrollment + WebAuthn registration.
 * Returns the virtual authenticator.
 *
 * Flow: login → MFA enroll (select mock, click next, submit) → MFA challenge (enter code, verify)
 *       → WebAuthn register → consent → callback
 */
async function fullLoginWithMfaAndWebAuthn(
  page: Page,
  gatewayUrl: string,
  clientId: string,
  username: string,
  password: string,
  rememberDevice: boolean,
): Promise<VirtualAuthenticator> {
  const authorizeUrl =
    `${gatewayUrl}/oauth/authorize?response_type=code` +
    `&client_id=${clientId}` +
    `&redirect_uri=${encodeURIComponent(REDIRECT_URI)}` +
    `&scope=openid`;

  await page.goto(authorizeUrl);
  await page.waitForURL(/.*login.*/i, { timeout: 30000 });

  // Login with password
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('button[type="submit"], #submitBtn').click();

  // WebAuthn registration comes FIRST (forceRegistration=true)
  await page.waitForURL(/.*webauthn\/register.*/i, { timeout: 15000 });

  const auth = await addVirtualAuthenticator(page);
  await simulateWebAuthnGesture(auth, async () => {
    await page.locator('button.primary, button#register-button').click();
  });

  // MFA Enrollment: select mock factor, click Next, then Submit
  await page.waitForURL(/.*mfa\/enroll.*/i, { timeout: 15000 });

  // Select the mock factor radio button
  const mockRadio = page.locator('input[type="radio"][name="factorId"]');
  await mockRadio.first().click();

  // Click Next to go to step 2
  await page.locator('#next').click();

  // Step 2: "No further action needed, submit" — click submit
  await page.locator('#submitBtn').click();

  // MFA Challenge: enter the mock code
  await page.waitForURL(/.*mfa\/challenge.*/i, { timeout: 15000 });
  await page.locator('#code').fill(MOCK_MFA_CODE);

  // Check "Remember device" if requested
  if (rememberDevice) {
    const checkbox = page.locator('#rememberDeviceConsent');
    if (await checkbox.isVisible({ timeout: 3000 })) {
      await checkbox.check();
    }
  }

  await page.locator('#verify').click();

  await handleConsentIfPresent(page);
  await page.waitForURL(/.*callback\?code=.*/i, { timeout: 15000 });

  return auth;
}

/**
 * AM-5333: MFA when Remember Device enabled - Passwordless with Device NOT remembered
 * AM-5334: MFA when Remember Device enabled - Passwordless with Device remembered
 */
test.describe('WebAuthn - MFA + Remember Device (AM-5333, AM-5334)', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  let auth: VirtualAuthenticator;

  test.afterEach(async () => {
    if (auth) {
      await removeVirtualAuthenticator(auth);
      auth = undefined;
    }
  });

  test('AM-5334: passwordless login skips MFA when device is remembered', async ({
    page,
    mfaApp,
    mfaUser,
    gatewayUrl,
  }) => {
    test.setTimeout(120_000);
    const clientId = mfaApp.settings.oauth.clientId;

    // First login: full flow with MFA enrollment + WebAuthn + remember device checked
    auth = await fullLoginWithMfaAndWebAuthn(page, gatewayUrl, clientId, mfaUser.username, API_USER_PASSWORD, true);

    // Clear session only — keep device recognition + remember device cookies
    await clearSessionOnly(page);

    // Second login: passwordless — should skip MFA because device is remembered
    const authorizeUrl =
      `${gatewayUrl}/oauth/authorize?response_type=code` +
      `&client_id=${clientId}` +
      `&redirect_uri=${encodeURIComponent(REDIRECT_URI)}` +
      `&scope=openid`;

    await page.goto(authorizeUrl);

    // Should land on login page or webauthn login
    await page.waitForURL(/.*(?:login|webauthn\/login).*/i, { timeout: 30000 });

    if (!page.url().includes('webauthn/login')) {
      const passwordlessLink = page.locator(PASSWORDLESS_LINK_SELECTOR);
      await passwordlessLink.click();
      await page.waitForURL(/.*webauthn\/login.*/i, { timeout: 15000 });
    }

    await page.locator('#username').fill(mfaUser.username);

    await simulateWebAuthnGesture(auth, async () => {
      await page.locator('button.primary, button#login-button').click();
    });

    // Should go straight to consent/callback — no MFA challenge
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i, { timeout: 15000 });

    const url = new URL(page.url());
    expect(url.searchParams.get('code')).toMatch(/^.+$/);
  });

  test('AM-5333: passwordless login requires MFA when device is NOT remembered', async ({
    page,
    mfaApp,
    mfaUser,
    gatewayUrl,
  }) => {
    test.setTimeout(120_000);
    const clientId = mfaApp.settings.oauth.clientId;

    // First login: full flow with MFA enrollment + WebAuthn but WITHOUT checking remember device
    auth = await fullLoginWithMfaAndWebAuthn(page, gatewayUrl, clientId, mfaUser.username, API_USER_PASSWORD, false);

    // Clear ALL cookies — no device remembered
    await page.context().clearCookies();

    // Second login: passwordless — should still need MFA challenge
    const authorizeUrl =
      `${gatewayUrl}/oauth/authorize?response_type=code` +
      `&client_id=${clientId}` +
      `&redirect_uri=${encodeURIComponent(REDIRECT_URI)}` +
      `&scope=openid`;

    await page.goto(authorizeUrl);
    await page.waitForURL(/.*login.*/i, { timeout: 30000 });

    // Navigate to passwordless login
    const passwordlessLink = page.locator(PASSWORDLESS_LINK_SELECTOR);
    await passwordlessLink.click();
    await page.waitForURL(/.*webauthn\/login.*/i, { timeout: 15000 });

    await page.locator('#username').fill(mfaUser.username);

    await simulateWebAuthnGesture(auth, async () => {
      await page.locator('button.primary, button#login-button').click();
    });

    // Should be challenged with MFA (device not remembered)
    await page.waitForURL(/.*mfa\/challenge.*/i, { timeout: 15000 });

    // Enter the mock MFA code
    await page.locator('#code').fill(MOCK_MFA_CODE);
    await page.locator('#verify').click();

    // Now should proceed to consent/callback
    await handleConsentIfPresent(page);
    await page.waitForURL(/.*callback\?code=.*/i, { timeout: 15000 });

    const url = new URL(page.url());
    expect(url.searchParams.get('code')).toMatch(/^.+$/);
  });
});
