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
import { clearEmails, getLastEmail, hasEmail } from '@utils-commands/email-commands';
import { waitForDomainSync } from '@management-commands/domain-management-commands';
import { getDomainApi } from '@management-commands/service/utils';
import { updateUserStatus } from '@management-commands/user-management-commands';
import { expect, test } from '../../../fixtures/forgot-password-gateway.fixture';
import {
  loginOnGatewayWithPassword,
  openLoginThenForgotPasswordPage,
  submitForgotPasswordEmail,
  submitNewPasswordOnResetPage,
} from '../../../utils/forgot-password-ui';
import { linkJira } from '../../../utils/jira';
import { fetchOrCreateDomainResetPasswordEmail } from '../../../utils/management-domain-fetch';
import {
  AM2200_RESET_EMAIL_SUBJECT_MARKER,
  FORGOT_PASSWORD_NEW_PASSWORD_AFTER_RESET,
  MULTI_PHASE_TEST_TIMEOUT,
  RESET_PASSWORD_GATEWAY_SETTLE_MS,
} from '../../../utils/test-constants';

test.describe('Gateway forgot password (Phase 8)', () => {
  test.use({ storageState: { cookies: [], origins: [] } });
  test.describe.configure({ timeout: MULTI_PHASE_TEST_TIMEOUT });

  test('AM-2196: forgot password email, reset link, then login with new password', async ({ page, forgotPasswordBundle }, testInfo) => {
    linkJira(testInfo, 'AM-2196');

    const email = forgotPasswordBundle.user.email!;
    const clientId = forgotPasswordBundle.app.settings.oauth.clientId;
    await clearEmails(email);

    await openLoginThenForgotPasswordPage(page, forgotPasswordBundle.gatewayUrl, clientId);
    await submitForgotPasswordEmail(page, email);

    const resetLink = (await getLastEmail(2000, email)).extractLink();
    expect(resetLink).toMatch(/^https?:\/\//);
    await clearEmails(email);

    await submitNewPasswordOnResetPage(page, resetLink, FORGOT_PASSWORD_NEW_PASSWORD_AFTER_RESET);
    // Gateway may cache old credentials briefly after password reset — no domain event to poll
    await page.waitForTimeout(RESET_PASSWORD_GATEWAY_SETTLE_MS);

    await loginOnGatewayWithPassword(
      page,
      forgotPasswordBundle.gatewayUrl,
      clientId,
      forgotPasswordBundle.user.username!,
      FORGOT_PASSWORD_NEW_PASSWORD_AFTER_RESET,
    );
  });

  test('AM-2181: disabled user gets no reset mail; after re-enable forgot password sends email', async ({ page, forgotPasswordBundle }, testInfo) => {
    linkJira(testInfo, 'AM-2181');

    const email = forgotPasswordBundle.user.email!;
    const userId = forgotPasswordBundle.user.id!;
    const clientId = forgotPasswordBundle.app.settings.oauth.clientId;

    await updateUserStatus(forgotPasswordBundle.domain.id, forgotPasswordBundle.adminToken, userId, false);
    await waitForDomainSync(forgotPasswordBundle.domain.id);
    await clearEmails(email);

    await openLoginThenForgotPasswordPage(page, forgotPasswordBundle.gatewayUrl, clientId);
    await submitForgotPasswordEmail(page, email);
    await expect.poll(async () => await hasEmail(1200, email), { timeout: 12_000 }).toBe(false);

    await updateUserStatus(forgotPasswordBundle.domain.id, forgotPasswordBundle.adminToken, userId, true);
    await waitForDomainSync(forgotPasswordBundle.domain.id);
    await clearEmails(email);

    await openLoginThenForgotPasswordPage(page, forgotPasswordBundle.gatewayUrl, clientId);
    await submitForgotPasswordEmail(page, email);
    const afterEnable = await getLastEmail(3000, email);
    expect(afterEnable.extractLink()).toMatch(/^https?:\/\//);
    await clearEmails(email);
  });

  test('AM-2182: disabled user triggers forgot password but no email is sent', async ({ page, forgotPasswordBundle }, testInfo) => {
    linkJira(testInfo, 'AM-2182');

    const email = forgotPasswordBundle.user.email!;
    const userId = forgotPasswordBundle.user.id!;
    const clientId = forgotPasswordBundle.app.settings.oauth.clientId;

    await updateUserStatus(forgotPasswordBundle.domain.id, forgotPasswordBundle.adminToken, userId, false);
    await waitForDomainSync(forgotPasswordBundle.domain.id);
    await clearEmails(email);

    await openLoginThenForgotPasswordPage(page, forgotPasswordBundle.gatewayUrl, clientId);
    await submitForgotPasswordEmail(page, email);
    await expect.poll(async () => await hasEmail(1200, email), { timeout: 12_000 }).toBe(false);
  });

  test('AM-2200: domain reset-password email subject override is used in sent mail', async ({ page, forgotPasswordBundle }, testInfo) => {
    linkJira(testInfo, 'AM-2200');

    const email = forgotPasswordBundle.user.email!;
    const clientId = forgotPasswordBundle.app.settings.oauth.clientId;

    const resetEmail = await fetchOrCreateDomainResetPasswordEmail(forgotPasswordBundle.domain.id, forgotPasswordBundle.adminToken);
    expect(resetEmail.id).toBeTruthy();
    await waitForDomainSync(forgotPasswordBundle.domain.id);
    expect(resetEmail.content).toBeTruthy();
    expect(resetEmail.subject).toBeTruthy();
    expect(resetEmail.from).toBeTruthy();
    expect(resetEmail.expiresAfter).not.toBeUndefined();

    await getDomainApi(forgotPasswordBundle.adminToken).updateDomainEmail({
      organizationId: process.env.AM_DEF_ORG_ID!,
      environmentId: process.env.AM_DEF_ENV_ID!,
      domain: forgotPasswordBundle.domain.id,
      email: resetEmail.id!,
      updateEmail: {
        content: resetEmail.content!,
        expiresAfter: resetEmail.expiresAfter!,
        from: resetEmail.from!,
        fromName: resetEmail.fromName,
        subject: `${AM2200_RESET_EMAIL_SUBJECT_MARKER} ${resetEmail.subject}`,
        enabled: resetEmail.enabled ?? true,
      },
    });
    await waitForDomainSync(forgotPasswordBundle.domain.id);
    // Gateway reloads email templates after domain sync — no endpoint to probe
    await page.waitForTimeout(RESET_PASSWORD_GATEWAY_SETTLE_MS);

    await clearEmails(email);
    await openLoginThenForgotPasswordPage(page, forgotPasswordBundle.gatewayUrl, clientId);
    await submitForgotPasswordEmail(page, email);

    await expect
      .poll(async () => (await getLastEmail(4000, email)).subject, { timeout: 25_000 })
      .toContain(AM2200_RESET_EMAIL_SUBJECT_MARKER);
    await clearEmails(email);
  });
});
