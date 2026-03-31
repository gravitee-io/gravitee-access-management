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
import { Page, expect } from '@playwright/test';
import { AUTH_CODE_FORMAT, BRIEF_TIMEOUT, MOCK_MFA_CODE, MULTI_PHASE_TEST_TIMEOUT } from './test-constants';
import { buildAuthorizeUrl } from './webauthn-helpers';
import { patchApplication } from '@management-commands/application-management-commands';
import { waitForDomainSync, waitForOidcReady } from '@management-commands/domain-management-commands';
import { getUser } from '@management-commands/user-management-commands';

/* ------------------------------------------------------------------ */
/*  Re-exports from shared modules (avoid duplication)                  */
/* ------------------------------------------------------------------ */

export { REDIRECT_URI, buildAuthorizeUrl } from './webauthn-helpers';
export { MOCK_MFA_CODE } from './test-constants';

/* ------------------------------------------------------------------ */
/*  Gateway page helpers                                               */
/* ------------------------------------------------------------------ */

/**
 * Submit the login form on the gateway login page.
 * Assumes the page is already on /login.
 */
export async function submitLogin(page: Page, username: string, password: string): Promise<void> {
  await expect(page.locator('#username')).toBeVisible();
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#submitBtn').click();
}

/**
 * Complete MFA enrollment on the gateway enrollment page.
 * Selects the Nth factor radio (0-indexed), clicks Next, then Submit.
 * For MOCK factors, step 2 requires no additional input.
 */
export async function enrollMockFactor(page: Page, factorIndex = 0): Promise<void> {
  await expect(page.locator('#mfa-enroll-step1')).toBeVisible();

  const radios = page.locator('input[type="radio"][name="factorId"]');
  await expect(radios.nth(factorIndex)).toBeVisible();
  await radios.nth(factorIndex).click();

  // Next button becomes enabled after radio selection
  const nextButton = page.locator('#next');
  await expect(nextButton).toBeEnabled();
  await nextButton.click();

  // Step 2: mock factor — just submit
  await expect(page.locator('#mfa-enroll-step2')).toBeVisible();
  await page.locator('#submitBtn').click();
}

/**
 * Complete MFA challenge by entering the code and clicking Verify.
 * When {@code rememberDevice} is true, checks the gateway "Remember device" consent if present.
 */
export async function completeMfaChallenge(
  page: Page,
  code: string = MOCK_MFA_CODE,
  options?: { rememberDevice?: boolean },
): Promise<void> {
  await expect(page.locator('#code')).toBeVisible();
  await page.locator('#code').fill(code);
  if (options?.rememberDevice) {
    const checkbox = page.locator('#rememberDeviceConsent');
    await expect(checkbox).toBeVisible({ timeout: BRIEF_TIMEOUT });
    await checkbox.check();
  }
  await page.locator('#verify').click();
}

/**
 * Handle the OAuth consent page if present.
 */
export async function handleConsentIfPresent(page: Page, timeoutMs = BRIEF_TIMEOUT): Promise<void> {
  try {
    await page.waitForURL(/.*oauth\/consent.*/i, { timeout: timeoutMs });
    await page.locator('button:has-text("Accept"), input[value="Accept"], #submitBtn').click();
  } catch {
    // No consent page — that's fine
  }
}

/**
 * Poll until the browser reaches the OAuth redirect_uri with an authorisation code, handling consent between hops.
 * Prefer this over a single {@code waitForURL} when redirects can be slow or interleaved with consent.
 */
function assertNoFatalOAuthErrorOnPage(page: Page): void {
  const href = page.url();
  if (!/\/login/i.test(href)) {
    return;
  }
  if (href.includes('error=login_failed') || href.includes('error_code=invalid_user')) {
    throw new Error(`Gateway login error: ${href}`);
  }
}

export async function reachOAuthAuthorizationCallback(
  page: Page,
  options?: { iterations?: number; consentTimeoutMs?: number },
): Promise<void> {
  const iterations = options?.iterations ?? 24;
  const consentTimeoutMs = options?.consentTimeoutMs ?? 8000;
  for (let i = 0; i < iterations; i++) {
    assertNoFatalOAuthErrorOnPage(page);
    const href = page.url();
    if (href.includes('callback') && href.includes('code=')) {
      const redirect = new URL(href);
      if (redirect.searchParams.get('code')) {
        return;
      }
    }
    await handleConsentIfPresent(page, consentTimeoutMs);
  }
  throw new Error(`OAuth callback with code not reached, last URL: ${page.url()}`);
}

/**
 * Click the "Skip" button on the MFA enrollment page.
 * Only visible when enrollment is optional (forceEnrollment=false).
 *
 * Waits until navigation leaves `/mfa/enroll` so the next assertion does not race the enroll POST
 * (the gateway may update session asynchronously after skip).
 */
export async function skipMfaEnrollment(page: Page): Promise<void> {
  const skipButton = page.locator('button[name="user_mfa_enrollment"][value="false"]');
  await expect(skipButton).toBeVisible();
  await Promise.all([
    page.waitForURL((u) => !/\/mfa\/enroll/i.test(new URL(u).pathname), {
      timeout: MULTI_PHASE_TEST_TIMEOUT,
    }),
    skipButton.click(),
  ]);
}

/**
 * Second OAuth authorisation round-trip for a user who already completed MFA once in this browser context.
 * Expects no MFA enrollment or challenge pages (challenge skipped by RISK_BASED / CONDITIONAL rule).
 * After /authorize, AM may redirect to login, consent, or straight to the client callback — wait for one of
 * those states, submit credentials only when the login form is shown, then finish consent (if any) and callback.
 */
export async function secondAuthorizeExpectCallbackWithoutMfa(
  page: Page,
  gatewayUrl: string,
  clientId: string,
  username: string,
  password: string,
): Promise<void> {
  await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
  await waitAfterAuthorizeThenLoginIfNeeded(page, username, password);

  await handleConsentIfPresent(page);
  await page.waitForURL(/.*callback\?code=.*/i);
  expect(page.url()).not.toMatch(/mfa\/enroll/i);
  expect(page.url()).not.toMatch(/mfa\/challenge/i);
  const callbackUrl = new URL(page.url());
  expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
}

/**
 * After `page.goto(authorizeUrl)`, wait for login, consent, or callback; submit credentials only on /login.
 * When `options.allowMfa` is true, also accepts /mfa/challenge as a valid landing page.
 */
export async function waitAfterAuthorizeThenLoginIfNeeded(
  page: Page,
  username: string,
  password: string,
  options?: { allowMfa?: boolean },
): Promise<void> {
  await page.waitForURL((u) => {
    if (/\/login/i.test(u.pathname)) return true;
    if (/oauth\/consent/i.test(u.pathname)) return true;
    if (options?.allowMfa && /mfa\/challenge/i.test(u.pathname)) return true;
    if (/callback/i.test(u.href) && u.searchParams.has('code')) return true;
    return false;
  });
  if (/\/login/i.test(new URL(page.url()).pathname)) {
    await submitLogin(page, username, password);
  }
}

/**
 * Full first-time MFA login flow (no WebAuthn).
 * Flow: authorize → login → MFA enroll → MFA challenge → consent → callback
 */
/** First visible recovery code on `/mfa/recovery_code` (`.code-item` spans). */
export async function readFirstRecoveryCodeFromPage(page: Page): Promise<string> {
  const item = page.locator('.recovery-codes .code-item').first();
  await expect(item).toBeVisible();
  const text = (await item.textContent())?.trim() ?? '';
  expect(text.length).toBeGreaterThan(0);
  return text;
}

/** Submit the recovery codes acknowledgement form (`#continue`). */
export async function submitRecoveryCodesContinue(page: Page): Promise<void> {
  await expect(page.locator('#continue')).toBeVisible();
  await page.locator('#continue').click();
}

/**
 * From MFA challenge page, open the alternatives view (second factor / recovery).
 */
export async function openMfaChallengeAlternatives(page: Page): Promise<void> {
  const altLink = page.locator('form#form ~ .section a').first();
  await expect(altLink).toBeVisible();
  await altLink.click();
}

/**
 * On MFA alternatives page, select the recovery-code factor and go to its challenge.
 */
export async function selectRecoveryFactorOnAlternativesPage(page: Page, recoveryFactorId: string): Promise<void> {
  await expect(page.locator('#enrollment-title')).toBeVisible();
  await page.locator(`#list-factor-${recoveryFactorId}`).click();
  await page.locator('#next').click();
}

/**
 * Waits until the MFA enrollment skip window has elapsed.
 * Uses the Management API's user.mfaEnrollmentSkippedAt if available,
 * otherwise falls back to the wall-clock timestamp captured at skip time.
 */
export async function waitUntilMfaEnrollmentSkipWindowExpired(
  domainId: string,
  adminToken: string,
  userId: string,
  skipTimeSeconds: number,
  skipHandledAtMs: number,
): Promise<void> {
  const pollMs = 400;
  const clockSkewMarginMs = 500;
  const maxWaitMs = (skipTimeSeconds + 30) * 1000;
  const deadline = Date.now() + maxWaitMs;

  while (Date.now() < deadline) {
    const user = await getUser(domainId, adminToken, userId);
    const skippedAt = user.mfaEnrollmentSkippedAt;
    const anchorMs = skippedAt != null ? skippedAt.getTime() : skipHandledAtMs;
    const windowEndMs = anchorMs + skipTimeSeconds * 1000;

    if (Date.now() >= windowEndMs + clockSkewMarginMs) {
      return;
    }
    await new Promise((r) => setTimeout(r, pollMs));
  }

  throw new Error(
    `Timed out waiting for MFA enrollment skip window (${skipTimeSeconds}s) to elapse for user ${userId}`,
  );
}

/**
 * Patches an app to standalone challenge mode (enrollment disabled).
 * Used after the user has already enrolled via the gateway.
 */
export async function applyStandaloneChallengeMfaPatch(
  domainId: string,
  adminToken: string,
  appId: string,
  factorId: string,
  challenge: { active: boolean; type: 'REQUIRED' | 'CONDITIONAL' | 'RISK_BASED'; challengeRule: string },
  domainHrid?: string,
): Promise<void> {
  await patchApplication(
    domainId,
    adminToken,
    {
      settings: {
        mfa: {
          factor: {
            defaultFactorId: factorId,
            applicationFactors: [{ id: factorId, selectionRule: '' }],
          },
          enrollment: { forceEnrollment: false },
          enroll: {
            active: false,
            type: 'REQUIRED',
            forceEnrollment: false,
            enrollmentRule: '',
            enrollmentSkipActive: false,
            enrollmentSkipRule: '',
          },
          challenge,
        },
      },
    },
    appId,
  );
  await waitForDomainSync(domainId);
  if (domainHrid) {
    await waitForOidcReady(domainHrid, { timeoutMs: 45_000, intervalMs: 500 });
  }
}

/** Login -> enroll -> challenge -> callback. Asserts auth code in callback URL. */
export async function expectEnrollThenChallengeThenCallback(
  page: Page, gatewayUrl: string, clientId: string, username: string, password: string,
): Promise<void> {
  await fullMfaLogin(page, gatewayUrl, clientId, username, password);
  const callbackUrl = new URL(page.url());
  expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
}

/** Login -> enroll page shown -> skip -> callback. Asserts auth code in callback URL. */
export async function expectEnrollSkipThenCallback(
  page: Page, gatewayUrl: string, clientId: string, username: string, password: string,
): Promise<void> {
  await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
  await page.waitForURL(/.*login.*/i);
  await submitLogin(page, username, password);
  await page.waitForURL(/.*mfa\/enroll.*/i);
  await skipMfaEnrollment(page);
  await handleConsentIfPresent(page);
  await page.waitForURL(/.*callback\?code=.*/i);
  const callbackUrl = new URL(page.url());
  expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
}

/** Login -> enrollment bypassed (conditional=true) -> callback. Asserts no enroll page appeared. */
export async function expectEnrollBypassedThenCallback(
  page: Page, gatewayUrl: string, clientId: string, username: string, password: string,
): Promise<void> {
  await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
  await page.waitForURL(/.*login.*/i);
  await submitLogin(page, username, password);
  await handleConsentIfPresent(page);
  await page.waitForURL(/.*callback\?code=.*/i);
  const callbackUrl = new URL(page.url());
  expect(callbackUrl.searchParams.get('code')).toMatch(AUTH_CODE_FORMAT);
  expect(page.url()).not.toMatch(/mfa\/enroll/);
}

export async function fullMfaLogin(
  page: Page,
  gatewayUrl: string,
  clientId: string,
  username: string,
  password: string,
  options: { factorIndex?: number; code?: string } = {},
): Promise<void> {
  const { factorIndex = 0, code = MOCK_MFA_CODE } = options;

  await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
  await page.waitForURL(/.*login.*/i);

  await submitLogin(page, username, password);

  await page.waitForURL(/.*mfa\/enroll.*/i);
  await enrollMockFactor(page, factorIndex);

  await page.waitForURL(/.*mfa\/challenge.*/i);
  await completeMfaChallenge(page, code);

  await handleConsentIfPresent(page);
  await page.waitForURL(/.*callback\?code=.*/i);
}
