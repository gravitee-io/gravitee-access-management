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

import { expect, Page } from '@playwright/test';

import { buildAuthorizeUrl, handleConsentIfPresent, submitLogin } from './mfa-helpers';

/** Gateway login → forgot-password link (href or visible text). */
export async function openLoginThenForgotPasswordPage(page: Page, gatewayUrl: string, clientId: string): Promise<void> {
  await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
  await page.waitForURL(/.*login.*/i);
  const forgotLink = page.locator('a[href*="forgotPassword"], a[href*="forgot_password"]').first();
  await expect(forgotLink).toBeVisible();
  await forgotLink.click();
  await page.waitForURL(/.*forgotPassword.*/i);
}

export async function submitForgotPasswordEmail(page: Page, email: string): Promise<void> {
  const field = page.locator('#email').or(page.locator('#username')).first();
  await expect(field).toBeVisible();
  await field.fill(email);
  // forgot_password.html enables submit only on keyup; fill() does not fire it.
  await field.dispatchEvent('keyup');
  await page.locator('#submitBtn').click();
}

/**
 * Complete reset form at the link from fakeSMTP (expects reset password page).
 */
export async function submitNewPasswordOnResetPage(page: Page, resetLink: string, newPassword: string): Promise<void> {
  await page.goto(resetLink);
  await page.waitForURL(/.*resetPassword.*/i);
  await expect(page.locator('#password')).toBeVisible();
  await page.locator('#password').fill(newPassword);
  await page.locator('#confirm-password').fill(newPassword);
  await page.locator('#submitBtn').click();
  await page.waitForURL(/gravitee\.io\/callback|\/callback/i);
}

export async function loginOnGatewayWithPassword(
  page: Page,
  gatewayUrl: string,
  clientId: string,
  username: string,
  password: string,
): Promise<void> {
  // Reset-password auto-login leaves a gateway session; force a fresh login to assert the new password.
  await page.context().clearCookies();

  await page.goto(buildAuthorizeUrl(gatewayUrl, clientId));
  await page.waitForURL(/.*login.*/i);
  await submitLogin(page, username, password);
  await handleConsentIfPresent(page);
  await page.waitForURL(/.*callback\?code=.*/i);
}
