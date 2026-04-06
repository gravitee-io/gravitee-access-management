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
import fetch from 'cross-fetch';
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import { Domain, enableDomain, initClient, initDomain, removeDomain, TestSuiteContext } from './fixture/mfa-setup-fixture';
import { withRetry } from '@utils-commands/retry';
import { followUpGet, get, postForm, processMfaEnrollment } from './fixture/mfa-flow-fixture';
import { extractDomAttr, extractDomValue } from './fixture/mfa-extract-fixture';
import { getWellKnownOpenIdConfiguration } from '@gateway-commands/oauth-oidc-commands';
import { waitFor } from '@management-commands/domain-management-commands';

global.fetch = fetch;
jest.setTimeout(200000);

const domain = {
  admin: { username: 'admin', password: 'adminadmin' },
  domain: { domainHrid: 'mfa-pending-activation-reset' },
} as Domain;

let ctx: TestSuiteContext;

beforeAll(async () => {
  await initDomain(domain, 1);

  const settings = {
    factors: [],
    settings: {
      mfa: {
        factor: {
          defaultFactorId: domain.domain.factors[0].id,
          applicationFactors: [domain.domain.factors[0]],
        },
        stepUpAuthenticationRule: '',
        stepUpAuthentication: { active: false, stepUpAuthenticationRule: '' },
        adaptiveAuthenticationRule: '',
        rememberDevice: {
          active: false,
          skipRememberDevice: false,
          expirationTimeSeconds: undefined,
          deviceIdentifierId: undefined,
        },
        enrollment: { forceEnrollment: false },
        enroll: { active: true, enrollmentSkipActive: false, forceEnrollment: true, type: 'required' },
        challenge: { active: true, challengeRule: '', type: 'required' },
      },
    },
  };
  const client = await initClient(domain, 'pending-activation-app', settings);

  const oidc = await enableDomain(domain)
    .then(() => waitFor(3000))
    .then(() => withRetry(() => getWellKnownOpenIdConfiguration(domain.domain.domainHrid).expect(200)));

  ctx = new TestSuiteContext(domain, client, domain.domain.users[0], oidc.body.authorization_endpoint);
});

afterAll(async () => {
  await removeDomain(domain);
});

describe('MFA Pending Activation Reset (AM-6745)', () => {
  it('should allow re-enrollment after abandoned enrollment attempt', async () => {
    // === First attempt: enroll but do NOT complete challenge (abandon) ===
    const firstEnrollment = await processMfaEnrollment(ctx);

    // Follow redirect to challenge page but do NOT submit the code.
    // This leaves a PENDING_ACTIVATION factor in the database.
    const challengeLocationResponse = await get(firstEnrollment.location, 302, { Cookie: firstEnrollment.cookie });
    await followUpGet(challengeLocationResponse, 200);
    // Session abandoned here — no challenge code submitted.

    // === Second attempt: fresh session, full enrollment + challenge ===
    // Start a completely new auth flow (no cookies from previous session)
    const authResponse = await get(ctx.clientAuthUrl, 302);
    const loginPage = await followUpGet(authResponse, 200);

    let xsrf = extractDomValue(loginPage, '[name=X-XSRF-TOKEN]');
    let action = extractDomAttr(loginPage, 'form', 'action');

    // Login
    const loginPostResponse = await postForm(
      action,
      {
        'X-XSRF-TOKEN': xsrf,
        username: ctx.user.username,
        password: ctx.user.password,
        client_id: ctx.client.clientId,
      },
      {
        Cookie: loginPage.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
      302,
    );

    // Follow redirects to enrollment page
    const enrollLocationResponse = await followUpGet(loginPostResponse, 302);
    const enrollmentPage = await followUpGet(enrollLocationResponse, 200);

    xsrf = extractDomValue(enrollmentPage, '[name=X-XSRF-TOKEN]');
    action = extractDomAttr(enrollmentPage, 'form', 'action');
    const factorId = extractDomValue(enrollmentPage, '[name=factorId]');

    // Submit enrollment
    const enrollmentPostResponse = await postForm(
      action,
      {
        'X-XSRF-TOKEN': xsrf,
        factorId: factorId,
        user_mfa_enrollment: true,
      },
      {
        Cookie: enrollmentPage.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
      302,
    );

    // Follow redirects to challenge page
    const challengeLocationResponse2 = await get(enrollmentPostResponse.headers['location'], 302, {
      Cookie: enrollmentPostResponse.headers['set-cookie'],
    });
    const challengePage = await followUpGet(challengeLocationResponse2, 200);

    xsrf = extractDomValue(challengePage, '[name=X-XSRF-TOKEN]');
    action = extractDomAttr(challengePage, 'form', 'action');

    // Submit challenge code (mock factor uses "1234")
    const challengePostResponse = await postForm(
      action,
      {
        'X-XSRF-TOKEN': xsrf,
        factorId: factorId,
        code: '1234',
        rememberDeviceConsent: 'off',
      },
      {
        Cookie: challengePage.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
      302,
    );

    // Final redirect should contain authorization code — MFA flow complete
    const finalResponse = await followUpGet(challengePostResponse, 302);
    expect(finalResponse.headers['location']).toBeDefined();
    expect(finalResponse.headers['location']).toContain('code=');
  });
});
