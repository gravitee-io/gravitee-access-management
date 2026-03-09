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
import { test as base, CDPSession, Page } from '@playwright/test';

import crossFetch from 'cross-fetch';
globalThis.fetch = crossFetch;

import { requestAdminAccessToken } from '../../api/commands/management/token-management-commands';
import {
  createDomain,
  startDomain,
  waitForDomainSync,
  safeDeleteDomain,
  waitForOidcReady,
  patchDomain,
} from '../../api/commands/management/domain-management-commands';
import { getAllIdps } from '../../api/commands/management/idp-management-commands';
import { createUser, deleteUser } from '../../api/commands/management/user-management-commands';
import { createTestApp } from '../../api/commands/utils/application-commands';
import { Domain, Application, User } from '../../api/management/models';

import { quietly, uniqueTestName } from '../utils/fixture-helpers';
import { API_USER_PASSWORD } from '../utils/test-constants';

/* ------------------------------------------------------------------ */
/*  CDP virtual authenticator helpers                                   */
/* ------------------------------------------------------------------ */

export interface VirtualAuthenticator {
  cdpSession: CDPSession;
  authenticatorId: string;
}

/**
 * Attach a CTAP2 virtual authenticator to the page via CDP.
 * Chromium only — this is the same mechanism Puppeteer and Selenium use.
 */
export async function addVirtualAuthenticator(page: Page): Promise<VirtualAuthenticator> {
  const cdpSession = await page.context().newCDPSession(page);
  await cdpSession.send('WebAuthn.enable');

  const result = await cdpSession.send('WebAuthn.addVirtualAuthenticator', {
    options: {
      protocol: 'ctap2',
      transport: 'internal',
      hasResidentKey: true,
      hasUserVerification: true,
      isUserVerified: true,
      automaticPresenceSimulation: false,
    },
  });

  return { cdpSession, authenticatorId: result.authenticatorId };
}

/**
 * Simulate a successful WebAuthn user gesture (register or login).
 *
 * Turns on automatic presence simulation, triggers the given action,
 * then waits for the credentialAdded or credentialAsserted CDP event.
 */
export async function simulateWebAuthnGesture(
  auth: VirtualAuthenticator,
  triggerAction: () => Promise<void>,
  timeoutMs = 15000,
): Promise<void> {
  const operationCompleted = new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => {
      auth.cdpSession.off('WebAuthn.credentialAdded', onAdded);
      auth.cdpSession.off('WebAuthn.credentialAsserted', onAsserted);
      reject(new Error(`WebAuthn CDP event (credentialAdded/credentialAsserted) not received within ${timeoutMs}ms`));
    }, timeoutMs);
    const onAdded = () => {
      clearTimeout(timer);
      auth.cdpSession.off('WebAuthn.credentialAdded', onAdded);
      auth.cdpSession.off('WebAuthn.credentialAsserted', onAsserted);
      resolve();
    };
    const onAsserted = () => {
      clearTimeout(timer);
      auth.cdpSession.off('WebAuthn.credentialAdded', onAdded);
      auth.cdpSession.off('WebAuthn.credentialAsserted', onAsserted);
      resolve();
    };
    auth.cdpSession.on('WebAuthn.credentialAdded', onAdded);
    auth.cdpSession.on('WebAuthn.credentialAsserted', onAsserted);
  });

  await auth.cdpSession.send('WebAuthn.setUserVerified', {
    authenticatorId: auth.authenticatorId,
    isUserVerified: true,
  });
  await auth.cdpSession.send('WebAuthn.setAutomaticPresenceSimulation', {
    authenticatorId: auth.authenticatorId,
    enabled: true,
  });

  await triggerAction();
  await operationCompleted;

  await auth.cdpSession.send('WebAuthn.setAutomaticPresenceSimulation', {
    authenticatorId: auth.authenticatorId,
    enabled: false,
  });
}

/**
 * Get the list of credentials on the virtual authenticator.
 */
export async function getCredentials(auth: VirtualAuthenticator) {
  const result = await auth.cdpSession.send('WebAuthn.getCredentials', {
    authenticatorId: auth.authenticatorId,
  });
  return result.credentials;
}

/**
 * Handle the OAuth consent page if present.
 * After WebAuthn registration/login the flow may land on /oauth/consent.
 * Clicks "Accept" and waits for redirect to the callback.
 */
export async function handleConsentIfPresent(page: Page, timeoutMs = 5000): Promise<void> {
  const currentUrl = page.url();
  if (currentUrl.includes('/oauth/consent')) {
    await page.locator('button:has-text("Accept"), input[value="Accept"], #submitBtn').click();
    return;
  }
  // Maybe we haven't navigated there yet — wait briefly
  try {
    await page.waitForURL(/.*oauth\/consent.*/i, { timeout: timeoutMs });
    await page.locator('button:has-text("Accept"), input[value="Accept"], #submitBtn').click();
  } catch {
    // No consent page appeared — that's fine
  }
}

/**
 * Perform the full login-then-register-WebAuthn flow.
 * Returns the virtual authenticator (caller must clean up).
 *
 * Steps: goto authorize → login with password → WebAuthn register page →
 * create virtual authenticator → simulate gesture → handle consent → arrive at callback.
 */
export async function loginAndRegisterWebAuthn(
  page: Page,
  gatewayUrl: string,
  clientId: string,
  username: string,
  password: string,
): Promise<VirtualAuthenticator> {
  await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
  await page.waitForURL(/.*login.*/i, { timeout: 30000 });

  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('button[type="submit"], #submitBtn').click();

  // forceRegistration=true → redirects to /webauthn/register
  await page.waitForURL(/.*webauthn\/register.*/i, { timeout: 15000 });

  const auth = await addVirtualAuthenticator(page);

  await simulateWebAuthnGesture(auth, async () => {
    await page.locator('button.primary, button#register-button').click();
  });

  await handleConsentIfPresent(page);
  await page.waitForURL(/.*callback\?code=.*/i, { timeout: 15000 });

  return auth;
}

/**
 * Perform a passwordless WebAuthn login (assumes credential already registered on auth).
 * Navigates through: authorize → login page → passwordless link → WebAuthn login → consent → callback.
 */
export async function passwordlessLogin(
  page: Page,
  auth: VirtualAuthenticator,
  gatewayUrl: string,
  clientId: string,
  username: string,
): Promise<void> {
  await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
  await page.waitForURL(/.*login.*/i, { timeout: 30000 });

  const passwordlessLink = page.locator(PASSWORDLESS_LINK_SELECTOR);
  await passwordlessLink.click();
  await page.waitForURL(/.*webauthn\/login.*/i, { timeout: 15000 });

  await page.locator('#username').fill(username);

  await simulateWebAuthnGesture(auth, async () => {
    await page.locator('button.primary, button#login-button').click();
  });

  await handleConsentIfPresent(page);
  await page.waitForURL(/.*callback\?code=.*/i, { timeout: 15000 });
}

const SESSION_COOKIE = 'GRAVITEE_IO_AM_SESSION';

/**
 * Clear only the gateway session cookie, preserving device recognition cookies.
 * Simulates a user returning after their session expired but their device
 * is still recognized.
 */
export async function clearSessionOnly(page: Page): Promise<void> {
  const allCookies = await page.context().cookies();
  const sessionCookies = allCookies.filter((c) => c.name === SESSION_COOKIE);
  for (const cookie of sessionCookies) {
    await page.context().clearCookies({ name: cookie.name, domain: cookie.domain });
  }
}

/**
 * Clean up the virtual authenticator.
 */
export async function removeVirtualAuthenticator(auth: VirtualAuthenticator): Promise<void> {
  try {
    await auth.cdpSession.send('WebAuthn.removeVirtualAuthenticator', {
      authenticatorId: auth.authenticatorId,
    });
    await auth.cdpSession.send('WebAuthn.disable');
    await auth.cdpSession.detach();
  } catch {
    // Page/context may already be closed during teardown — safe to ignore
  }
}

/* ------------------------------------------------------------------ */
/*  Fixture types                                                      */
/* ------------------------------------------------------------------ */

export type WebAuthnFixtures = {
  waAdminToken: string;
  waDomain: Domain;
  waApp: Application;
  waUser: User;
  /** Gateway URL for this domain (e.g. http://localhost:8092/domain-hrid) */
  gatewayUrl: string;
  /** Extra loginSettings merged into the domain config before start.
   *  Use test.use({ waExtraLoginSettings: { ... } }) to configure per-test. */
  waExtraLoginSettings: Record<string, unknown>;
};

const REDIRECT_URI = 'https://gravitee.io/callback';

/* ------------------------------------------------------------------ */
/*  Shared locators & helpers                                          */
/* ------------------------------------------------------------------ */

/** Locator string for the passwordless / "Sign in with fingerprint" link on the login page. */
export const PASSWORDLESS_LINK_SELECTOR =
  'a:has-text("passwordless"), a:has-text("Sign in with fingerprint"), a[href*="webauthn/login"]';

/** Build the OAuth authorize URL for a given domain + app. */
export function buildAuthorizeUrl(gatewayUrl: string, clientId: string): string {
  return (
    `${gatewayUrl}/oauth/authorize?response_type=code` +
    `&client_id=${clientId}` +
    `&redirect_uri=${encodeURIComponent(REDIRECT_URI)}` +
    `&scope=openid`
  );
}

/* ------------------------------------------------------------------ */
/*  Fixture                                                            */
/* ------------------------------------------------------------------ */

export const test = base.extend<WebAuthnFixtures>({
  waExtraLoginSettings: [{}, { option: true }],

  waAdminToken: async ({}, use) => {
    const token = await requestAdminAccessToken();
    await use(token);
  },

  waDomain: async ({ waAdminToken, waExtraLoginSettings }, use) => {
    const name = uniqueTestName('pw-webauthn');
    const domain = await quietly(() => createDomain(waAdminToken, name, 'WebAuthn Playwright test domain'));

    // Enable passwordless (WebAuthn) on the domain.
    // Domain is NOT started here — gatewayUrl starts it after app+user are
    // created so the initial sync picks up all resources in one pass.
    // waExtraLoginSettings is merged in so tests can configure features like
    // device recognition or enforce password before the domain starts,
    // avoiding a patchDomain on a running domain (which causes a redeploy gap).
    await quietly(() =>
      patchDomain(domain.id, waAdminToken, {
        loginSettings: {
          inherited: false,
          passwordlessEnabled: true,
          passwordlessDeviceNamingEnabled: false,
          ...waExtraLoginSettings,
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

    await quietly(() => safeDeleteDomain(domain.id, waAdminToken));
  },

  waApp: async ({ waAdminToken, waDomain }, use) => {
    const idpSet = await getAllIdps(waDomain.id, waAdminToken);
    const defaultIdp = idpSet.values().next().value;
    if (!defaultIdp) throw new Error('No IdP found for WebAuthn domain');

    const app = await quietly(() =>
      createTestApp(uniqueTestName('pw-wa-app'), waDomain, waAdminToken, 'WEB', {
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

    await use(app);
  },

  waUser: async ({ waAdminToken, waDomain }, use) => {
    const user = await quietly(() =>
      createUser(waDomain.id, waAdminToken, {
        firstName: 'WebAuthn',
        lastName: 'Test',
        email: `${uniqueTestName('wa-user')}@example.com`,
        username: uniqueTestName('wa-user'),
        password: API_USER_PASSWORD,
        preRegistration: false,
      }),
    );

    await use(user);

    await quietly(async () => {
      try {
        await deleteUser(waDomain.id, waAdminToken, user.id);
      } catch {
        // domain teardown may cascade
      }
    });
  },

  gatewayUrl: async ({ waAdminToken, waDomain, waApp, waUser }, use) => {
    // All resources (domain config, app, user) are created before starting
    // the domain, so the initial sync picks up everything in one pass.
    // This avoids the waitForNextSync race condition.
    void waApp;
    void waUser;
    await quietly(() => startDomain(waDomain.id, waAdminToken));
    await quietly(() => waitForDomainSync(waDomain.id));
    await waitForOidcReady(waDomain.hrid, { timeoutMs: 30000, intervalMs: 500 });
    const baseUrl = process.env.AM_GATEWAY_URL || 'http://localhost:8092';
    await use(`${baseUrl}/${waDomain.hrid}`);
  },
});

export { expect } from '@playwright/test';
