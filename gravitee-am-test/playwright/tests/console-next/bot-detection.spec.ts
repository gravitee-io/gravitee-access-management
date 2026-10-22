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
import { test, expect } from '../../fixtures/base.fixture';
import { createBotDetection, getBotDetection } from '@management-commands/bot-detection-management-commands';

const RECAPTCHA_V3 = 'google-recaptcha-v3-am-bot-detection';

const recaptchaBody = (name: string) => ({
  type: RECAPTCHA_V3,
  name,
  detectionType: 'CAPTCHA',
  configuration: JSON.stringify({
    siteKey: 'site-key',
    secretKey: 'secret-key',
    serviceUrl: 'https://www.google.com/recaptcha/api/siteverify',
    tokenParameterName: 'X-Recaptcha-Token',
    minScore: 0.5,
  }),
});

/** The bot detections of a domain in the React console (gravitee-am-ui-next). Runs in the console-next project only. */
test.describe('React console: bot detection', () => {
  const botDetectionUrl = (hrid: string) => `/environments/${process.env.AM_DEF_ENV_HRID}/domains/${hrid}/settings/bot-detection`;

  test('creates a reCAPTCHA v3 bot detection with the wizard', async ({ page, adminToken, testDomain }) => {
    await page.goto(botDetectionUrl(testDomain.hrid));

    await page.getByRole('link', { name: 'Add bot detection' }).click();
    await page.getByRole('radio', { name: 'Google reCAPTCHA v3' }).click();
    await page.getByRole('button', { name: 'Next' }).click();
    await page.getByRole('textbox', { name: 'Name', exact: true }).fill('pw-next-recaptcha');
    await expect(page.getByRole('textbox', { name: 'Detection type' })).toHaveValue('CAPTCHA');
    await page.getByRole('textbox', { name: /Site Key/ }).fill('site-key');
    await page.getByRole('textbox', { name: /Secret Key/ }).fill('secret-key');
    const createRequest = page.waitForRequest((request) => request.method() === 'POST' && request.url().endsWith('/bot-detections'));
    await page.getByRole('button', { name: 'Create' }).click();

    const body = (await createRequest).postDataJSON();
    expect(body).toMatchObject({ name: 'pw-next-recaptcha', type: RECAPTCHA_V3, detectionType: 'CAPTCHA' });
    // The Angular form does not render the untyped detectionType property, so the configuration has no such key.
    expect(JSON.parse(body.configuration)).toEqual({
      siteKey: 'site-key',
      secretKey: 'secret-key',
      serviceUrl: 'https://www.google.com/recaptcha/api/siteverify',
      tokenParameterName: 'X-Recaptcha-Token',
      minScore: 0.5,
    });
    await expect(page.getByRole('heading', { level: 1, name: 'pw-next-recaptcha' })).toBeVisible();
    const created = await getBotDetection(testDomain.id, adminToken, page.url().split('/').pop());
    expect(created).toMatchObject({ name: 'pw-next-recaptcha', type: RECAPTCHA_V3, detectionType: 'CAPTCHA' });
  });

  test('renames a bot detection on its page', async ({ page, adminToken, testDomain }) => {
    const created = await createBotDetection(testDomain.id, adminToken, recaptchaBody('pw-next-rename'));
    await page.goto(botDetectionUrl(testDomain.hrid));

    await page.getByRole('table').getByRole('link', { name: 'pw-next-rename' }).click();
    await page.getByRole('textbox', { name: 'Name', exact: true }).fill('pw-next-renamed');
    const saveResponse = page.waitForResponse((response) => response.request().method() === 'PUT' && response.url().endsWith(created.id));
    await page.getByRole('button', { name: 'Save' }).click();
    expect((await saveResponse).ok()).toBe(true);

    const saved: any = await getBotDetection(testDomain.id, adminToken, created.id);
    expect(saved.name).toEqual('pw-next-renamed');
    expect(JSON.parse(saved.configuration)).toMatchObject({ siteKey: 'site-key', minScore: 0.5 });
  });

  test('deletes a bot detection after the confirmation', async ({ page, testDomain, adminToken }) => {
    const created = await createBotDetection(testDomain.id, adminToken, recaptchaBody('pw-next-delete'));
    await page.goto(`${botDetectionUrl(testDomain.hrid)}/${created.id}`);

    await page.getByRole('button', { name: 'Delete' }).click();
    const dialog = page.getByRole('dialog', { name: 'Delete bot detection' });
    await dialog.getByRole('button', { name: 'Delete' }).click();

    await expect(page).toHaveURL(new RegExp(`/settings/bot-detection$`));
    await expect(page.getByText('No bot detection yet')).toBeVisible();
  });
});
