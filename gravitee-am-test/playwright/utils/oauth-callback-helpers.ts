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
import { Page } from '@playwright/test';
import { BRIEF_TIMEOUT } from './test-constants';

const CONSENT_SELECT_ALL_SELECTOR = '#selectAllScopes';
const CONSENT_ALLOW_SELECTOR = '#allowButton';
const CONSENT_SCOPE_CHECKBOX_SELECTOR = '.scope-consent-checkbox';

/**
 * Select all scopes and approve the consent page.
 * If the #selectAllScopes toolbar button is not present, fall back to checking each scope checkbox individually.
 */
async function approveConsent(page: Page): Promise<void> {
  const selectAllScopes = page.locator(CONSENT_SELECT_ALL_SELECTOR);
  if (await selectAllScopes.count()) {
    await selectAllScopes.click();
  } else {
    const checkboxes = page.locator(CONSENT_SCOPE_CHECKBOX_SELECTOR);
    const count = await checkboxes.count();
    for (let i = 0; i < count; i++) {
      const checkbox = checkboxes.nth(i);
      // Required scopes render as checked+disabled; they can't (and needn't) be toggled.
      if (await checkbox.isDisabled()) {
        continue;
      }
      await checkbox.check();
    }
  }
  await page.locator(CONSENT_ALLOW_SELECTOR).click();
}

/**
 * Handle the OAuth consent page if present.
 */
export async function handleConsentIfPresent(page: Page, timeoutMs = BRIEF_TIMEOUT): Promise<void> {
  try {
    await page.waitForURL(/.*oauth\/consent.*/i, { timeout: timeoutMs });
    await approveConsent(page);
  } catch {
    // No consent page — that's fine
  }
}

function assertNoFatalOAuthErrorOnPage(page: Page): void {
  const href = page.url();
  if (!/\/login/i.test(href)) {
    return;
  }
  if (href.includes('error=login_failed') || href.includes('error_code=invalid_user')) {
    throw new Error(`Gateway login error: ${href}`);
  }
}

/** True when the browser has landed on the client redirect URI with an authorisation code. */
function hasOAuthCallbackCode(href: string): boolean {
  try {
    const url = new URL(href);
    return url.pathname.includes('callback') && !!url.searchParams.get('code');
  } catch {
    return false;
  }
}

/**
 * Poll until the browser reaches the OAuth redirect_uri with an authorisation code, handling consent between hops.
 * Prefer this over {@code waitForURL(/callback?code=/)} — the external redirect URI (gravitee.io) may never
 * reach the {@code load} event in CI while the URL already contains a valid {@code code}.
 */
export async function reachOAuthAuthorizationCallback(
  page: Page,
  options?: { iterations?: number; consentTimeoutMs?: number; pollIntervalMs?: number },
): Promise<void> {
  const pollIntervalMs = options?.pollIntervalMs ?? 250;
  const pollingWindowMs = options?.consentTimeoutMs ?? 30_000;
  const iterations = options?.iterations ?? Math.ceil(pollingWindowMs / pollIntervalMs);
  for (let i = 0; i < iterations; i++) {
    assertNoFatalOAuthErrorOnPage(page);
    const href = page.url();
    if (hasOAuthCallbackCode(href)) {
      return;
    }
    if (href.includes('/oauth/consent')) {
      try {
        await approveConsent(page);
      } catch {
        // Transient click failure — retry on next iteration
      }
    } else {
      try {
        await page.waitForURL(/.*oauth\/consent.*/i, { timeout: pollIntervalMs });
        await approveConsent(page);
      } catch {
        // Consent not shown yet — wait before the next poll
        await new Promise((r) => setTimeout(r, pollIntervalMs));
      }
    }
  }
  throw new Error(`OAuth callback with code not reached, last URL: ${page.url()}`);
}
