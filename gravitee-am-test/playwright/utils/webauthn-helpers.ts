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
import { CDPSession, Page, expect } from '@playwright/test';
import { BRIEF_TIMEOUT } from './test-constants';

/* ------------------------------------------------------------------ */
/*  Constants                                                          */
/* ------------------------------------------------------------------ */

export const REDIRECT_URI = 'https://gravitee.io/callback';

/** Locator string for the passwordless / "Sign in with fingerprint" link on the login page. */
export const PASSWORDLESS_LINK_SELECTOR =
  'a:has-text("passwordless"), a:has-text("Sign in with fingerprint"), a[href*="webauthn/login"]';

const SESSION_COOKIE = 'GRAVITEE_IO_AM_SESSION';

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

/**
 * Handle the OAuth consent page if present.
 * After WebAuthn registration/login the flow may land on /oauth/consent.
 * Clicks "Accept" and waits for redirect to the callback.
 */
export async function handleConsentIfPresent(page: Page, timeoutMs = BRIEF_TIMEOUT): Promise<void> {
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

/* ------------------------------------------------------------------ */
/*  URL helpers                                                        */
/* ------------------------------------------------------------------ */

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
/*  Navigation helpers                                                 */
/* ------------------------------------------------------------------ */

/**
 * Navigate to WebAuthn login page, handling non-deterministic gateway routing.
 * Device recognition may route directly to /webauthn/login, or to the standard
 * login page where the passwordless link must be clicked.
 */
export async function navigateToWebAuthnLogin(
  page: Page,
  gatewayUrl: string,
  clientId: string,
): Promise<void> {
  await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
  await page.waitForURL(/.*(?:login|webauthn\/login).*/i);
  if (!page.url().includes('webauthn/login')) {
    await page.locator(PASSWORDLESS_LINK_SELECTOR).click();
    await page.waitForURL(/.*webauthn\/login.*/i);
  }
}

/* ------------------------------------------------------------------ */
/*  Composite flow helpers                                             */
/* ------------------------------------------------------------------ */

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
  await page.waitForURL(/.*login.*/i);

  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('button[type="submit"], #submitBtn').click();

  // forceRegistration=true → redirects to /webauthn/register
  await page.waitForURL(/.*webauthn\/register.*/i);

  const auth = await addVirtualAuthenticator(page);

  await simulateWebAuthnGesture(auth, async () => {
    await page.locator('button.primary, button#register-button').click();
  });

  await handleConsentIfPresent(page);
  await page.waitForURL(/.*callback\?code=.*/i);

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
  await page.waitForURL(/.*login.*/i);

  const passwordlessLink = page.locator(PASSWORDLESS_LINK_SELECTOR);
  await passwordlessLink.click();
  await page.waitForURL(/.*webauthn\/login.*/i);

  await page.locator('#username').fill(username);

  await simulateWebAuthnGesture(auth, async () => {
    await page.locator('button.primary, button#login-button').click();
  });

  await handleConsentIfPresent(page);
  await page.waitForURL(/.*callback\?code=.*/i);
}

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
 * Full first-time login flow with MFA enrollment + WebAuthn registration.
 * Returns the virtual authenticator.
 *
 * Flow: login → WebAuthn register → MFA enroll (select mock, click next, submit)
 *       → MFA challenge (enter code, verify) → consent → callback
 */
export async function fullLoginWithMfaAndWebAuthn(
  page: Page,
  gatewayUrl: string,
  clientId: string,
  username: string,
  password: string,
  mfaCode: string,
  rememberDevice: boolean,
): Promise<VirtualAuthenticator> {
  await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
  await page.waitForURL(/.*login.*/i);

  // Login with password
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('button[type="submit"], #submitBtn').click();

  // WebAuthn registration comes FIRST (forceRegistration=true)
  await page.waitForURL(/.*webauthn\/register.*/i);

  const auth = await addVirtualAuthenticator(page);
  await simulateWebAuthnGesture(auth, async () => {
    await page.locator('button.primary, button#register-button').click();
  });

  // MFA Enrollment: select mock factor, click Next, then Submit
  await page.waitForURL(/.*mfa\/enroll.*/i);

  // Select the mock factor radio button
  const mockRadio = page.locator('input[type="radio"][name="factorId"]');
  await mockRadio.first().click();

  // Click Next to go to step 2
  await page.locator('#next').click();

  // Step 2: "No further action needed, submit" — click submit
  await page.locator('#submitBtn').click();

  // MFA Challenge: enter the mock code
  await page.waitForURL(/.*mfa\/challenge.*/i);
  await page.locator('#code').fill(mfaCode);

  // Check "Remember device" if requested
  if (rememberDevice) {
    const checkbox = page.locator('#rememberDeviceConsent');
    await expect(checkbox).toBeVisible({ timeout: BRIEF_TIMEOUT });
    await checkbox.check();
  }

  await page.locator('#verify').click();

  await handleConsentIfPresent(page);
  await page.waitForURL(/.*callback\?code=.*/i);

  return auth;
}
