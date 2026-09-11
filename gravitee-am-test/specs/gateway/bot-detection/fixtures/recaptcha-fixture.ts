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
import { expect } from '@jest/globals';
import crossFetch from 'cross-fetch';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import {
  DomainOidcConfig,
  patchDomain,
  safeDeleteDomain,
  setupDomainForTest,
  waitForOidcReady,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createBotDetection } from '@management-commands/bot-detection-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { extractXsrfToken, extractXsrfTokenAndActionResponse, performFormPost, performGet } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

/**
 * Google's siteverify is replaced by WireMock: the reCAPTCHA plugin's serviceUrl points at a stub
 * that answers according to the token it is asked to verify, so every verdict is reproducible.
 */
export const RECAPTCHA = {
  DOMAIN_PREFIX: 'bot-detection',
  SECRET_KEY: 'recaptcha-secret-under-test',
  MIN_SCORE: 0.5,
  TOKEN_PARAMETER: 'X-Recaptcha-Token',
  TOKENS: {
    HUMAN: 'token-human',
    BOT: 'token-bot',
    REJECTED: 'token-rejected',
    OUTAGE: 'token-outage',
  },
  ERROR_PAGE_QUERY: 'error=technical_error&error_description=Something+goes+wrong.+Please+try+again.',
  USER_PASSWORD: '#CoMpL3X-P@SsW0Rd',
  REDIRECT_URI: 'https://example.com/callback',
} as const;

const WIREMOCK_ADMIN = (process.env.SFR_URL || 'http://localhost:8181') + '/__admin';
const WIREMOCK_INTERNAL = process.env.INTERNAL_SFR_URL || 'http://wiremock:8080';

export interface RecaptchaFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  application: Application;
  user: { username: string; password: string; email: string };
  // Path of the siteverify stub, unique to this fixture so request lookups only see its own calls
  siteVerifyPath: string;
}

export const setupRecaptchaFixture = async (): Promise<RecaptchaFixture> => {
  const accessToken = await requestAdminAccessToken();
  const siteVerifyPath = `/recaptcha/${uniqueName('siteverify', true)}`;
  const stubIds = await registerSiteVerifyStubs(siteVerifyPath);
  let domain: Domain | null = null;
  try {
    const started = await setupDomainForTest(uniqueName(RECAPTCHA.DOMAIN_PREFIX, true), { accessToken, waitForStart: true });
    domain = started.domain;

    const botDetection = await createBotDetection(domain.id, accessToken, {
      name: 'recaptcha-under-test',
      type: 'google-recaptcha-v3-am-bot-detection',
      detectionType: 'CAPTCHA',
      configuration: JSON.stringify({
        siteKey: 'site-key-under-test',
        secretKey: RECAPTCHA.SECRET_KEY,
        serviceUrl: WIREMOCK_INTERNAL + siteVerifyPath,
        tokenParameterName: RECAPTCHA.TOKEN_PARAMETER,
        minScore: RECAPTCHA.MIN_SCORE,
      }),
    });

    // A real user on the default IdP, so forgot-password can resolve it by email
    const user = {
      username: uniqueName('recaptcha-user', true),
      password: RECAPTCHA.USER_PASSWORD,
      email: `${uniqueName('recaptcha-user', true)}@test.gravitee.io`,
    };
    await createUser(domain.id, accessToken, { firstName: 'Recaptcha', lastName: 'User', ...user });

    const application = await createTestApp(domain, accessToken, `default-idp-${domain.id}`);

    // Domain-level settings are a route redeploy on the gateway: wait for sync, then for routing to be live
    await waitForSyncAfter(domain.id, () =>
      patchDomain(domain.id, accessToken, {
        loginSettings: { forgotPasswordEnabled: true },
        accountSettings: { useBotDetection: true, botDetectionPlugin: botDetection.id },
      }),
    );
    await waitForOidcReady(domain.hrid, { timeoutMs: 5000, intervalMs: 200 });

    return {
      accessToken,
      domain,
      oidc: started.oidcConfig,
      application,
      user,
      siteVerifyPath,
      cleanUp: async () => {
        if (domain?.id && accessToken) {
          await safeDeleteDomain(domain.id, accessToken);
        }
        await removeStubs(stubIds);
      },
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      await safeDeleteDomain(domain.id, accessToken).catch((e) => console.error('Cleanup failed:', e));
    }
    await removeStubs(stubIds).catch((e) => console.error('WireMock cleanup failed:', e));
    throw error;
  }
};

async function createTestApp(domain: Domain, accessToken: string, idpId: string): Promise<Application> {
  const created = await createApplication(domain.id, accessToken, {
    name: uniqueName('recaptcha-app', true),
    type: 'WEB',
    redirectUris: [RECAPTCHA.REDIRECT_URI],
  });
  const updated = await updateApplication(
    domain.id,
    accessToken,
    {
      settings: {
        oauth: {
          redirectUris: [RECAPTCHA.REDIRECT_URI],
          grantTypes: ['authorization_code'],
          scopeSettings: [{ scope: 'openid', defaultScope: true }],
        },
      },
      identityProviders: new Set([{ identity: idpId, priority: 0 }]),
    },
    created.id,
  );
  updated.settings.oauth.clientSecret = created.settings.oauth.clientSecret;
  return updated;
}

/** One stub per token value; the plugin posts `secret=...&response=<token>` as a form. */
async function registerSiteVerifyStubs(siteVerifyPath: string): Promise<string[]> {
  const verdicts: Array<[string, number, string]> = [
    [RECAPTCHA.TOKENS.HUMAN, 200, JSON.stringify({ success: true, score: 0.9 })],
    [RECAPTCHA.TOKENS.BOT, 200, JSON.stringify({ success: true, score: 0.1 })],
    [RECAPTCHA.TOKENS.REJECTED, 200, JSON.stringify({ success: false, 'error-codes': ['invalid-input-response'] })],
    [RECAPTCHA.TOKENS.OUTAGE, 500, 'siteverify unavailable'],
  ];
  const ids: string[] = [];
  for (const [token, status, body] of verdicts) {
    const response = await crossFetch(`${WIREMOCK_ADMIN}/mappings`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        request: { method: 'POST', urlPath: siteVerifyPath, bodyPatterns: [{ contains: `response=${token}` }] },
        response: { status, headers: { 'Content-Type': 'application/json' }, body },
      }),
    });
    expect(response.status).toEqual(201);
    ids.push((await response.json()).id);
  }
  return ids;
}

const removeStubs = async (ids: string[]) => {
  for (const id of ids) {
    await crossFetch(`${WIREMOCK_ADMIN}/mappings/${id}`, { method: 'DELETE' });
  }
};

/** The verify calls the gateway made to this fixture's stub, oldest first. */
export const siteVerifyCalls = async (fixture: RecaptchaFixture): Promise<Array<{ body: string }>> => {
  const response = await crossFetch(`${WIREMOCK_ADMIN}/requests/find`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ method: 'POST', urlPath: fixture.siteVerifyPath }),
  });
  expect(response.status).toEqual(200);
  return (await response.json()).requests;
};

/** Submits the login form with the given reCAPTCHA token and returns the gateway's redirect. */
export const submitLogin = async (fixture: RecaptchaFixture, recaptchaToken?: string) => {
  const clientId = fixture.application.settings.oauth.clientId;
  const authResponse = await performGet(
    fixture.oidc.authorization_endpoint,
    `?response_type=code&client_id=${clientId}&redirect_uri=${RECAPTCHA.REDIRECT_URI}`,
  ).expect(302);
  const loginForm = await extractXsrfTokenAndActionResponse(authResponse);
  return performFormPost(
    loginForm.action,
    '',
    {
      'X-XSRF-TOKEN': loginForm.token,
      username: fixture.user.username,
      password: fixture.user.password,
      client_id: clientId,
      ...(recaptchaToken !== undefined && { [RECAPTCHA.TOKEN_PARAMETER]: recaptchaToken }),
    },
    { Cookie: loginForm.headers['set-cookie'], 'Content-type': 'application/x-www-form-urlencoded' },
  ).expect(302);
};

/** Submits the forgot-password form with the given reCAPTCHA token and returns the gateway's redirect. */
export const submitForgotPassword = async (fixture: RecaptchaFixture, recaptchaToken?: string) => {
  const clientId = fixture.application.settings.oauth.clientId;
  const uri = `/${fixture.domain.hrid}/forgotPassword`;
  const form = await extractXsrfToken(process.env.AM_GATEWAY_URL + uri, `?client_id=${clientId}`);
  return performFormPost(
    process.env.AM_GATEWAY_URL,
    uri,
    {
      'X-XSRF-TOKEN': form.token,
      email: fixture.user.email,
      client_id: clientId,
      ...(recaptchaToken !== undefined && { [RECAPTCHA.TOKEN_PARAMETER]: recaptchaToken }),
    },
    { Cookie: form.headers['set-cookie'], 'Content-type': 'application/x-www-form-urlencoded' },
  ).expect(302);
};

export const expectBotRejection = (fixture: RecaptchaFixture, response) => {
  expect(response.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/error?`);
  expect(response.headers['location']).toContain(RECAPTCHA.ERROR_PAGE_QUERY);
};
