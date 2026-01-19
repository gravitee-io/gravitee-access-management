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
import { afterAll, beforeAll, expect } from '@jest/globals';
import { Domain, enableDomain, initClient, initDomain, removeDomain, TestSuiteContext } from './fixture/mfa-setup-fixture';
import { withRetry } from '@utils-commands/retry';
import { followUpGet, get, postForm, processMfaEndToEnd } from './fixture/mfa-flow-fixture';
import { extractCookieSessionValues, extractDomAttr, extractDomValue } from './fixture/mfa-extract-fixture';
import { getWellKnownOpenIdConfiguration } from '@gateway-commands/oauth-oidc-commands';
import { waitFor } from '@management-commands/domain-management-commands';
import { setup } from '../../test-fixture';

setup(200000);

function defaultApplicationSettings() {
  return {
    factors: [],
    settings: {
      mfa: {
        factor: {
          defaultFactorId: domain.domain.factors[0].id,
          applicationFactors: [domain.domain.factors[0], domain.domain.factors[1]],
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
        challenge: { active: false, challengeRule: '' },
      },
    },
  };
}

const domain = {
  admin: { username: 'admin', password: 'adminadmin' },
  domain: { domainHrid: 'mfa-test-domain-enroll-required' },
} as Domain;

beforeAll(async () => {
  await initDomain(domain, 3);

  let settings;

  settings = defaultApplicationSettings();
  const defaultClient1 = await initClient(domain, 'enroll-default-1', settings);

  settings = defaultApplicationSettings();
  const defaultClient2 = await initClient(domain, 'enroll-default-2', settings);

  settings = defaultApplicationSettings();
  settings.settings.mfa.challenge = { active: true, challengeRule: '', type: 'required' };
  const defaultClient3 = await initClient(domain, 'enroll-with-challenge-1', settings);

  const oidc = await enableDomain(domain)
    .then(() => waitFor(3000))
    .then(() => withRetry(() => getWellKnownOpenIdConfiguration(domain.domain.domainHrid).expect(200)));

  enrollDefaultCtx1 = new TestSuiteContext(domain, defaultClient1, domain.domain.users[0], oidc.body.authorization_endpoint);
  enrollDefaultCtx2 = new TestSuiteContext(domain, defaultClient2, domain.domain.users[1], oidc.body.authorization_endpoint);
  enrollDefaultCtx3 = new TestSuiteContext(domain, defaultClient3, domain.domain.users[2], oidc.body.authorization_endpoint);
});

afterAll(async () => {
  await removeDomain(domain);
});

let enrollDefaultCtx1: TestSuiteContext;
let enrollDefaultCtx2: TestSuiteContext;
let enrollDefaultCtx3: TestSuiteContext;

describe('When Enrollment REQUIRED', () => {
  it('and factor is not enrolled should enroll', async () => {
    const ctx = enrollDefaultCtx1;
    const authResponse = await get(ctx.clientAuthUrl, 302);
    const loginPage = await followUpGet(authResponse, 200);

    let xsrf = extractDomValue(loginPage, '[name=X-XSRF-TOKEN]');
    let action = extractDomAttr(loginPage, 'form', 'action');

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

    const enrollLocationResponse = await followUpGet(loginPostResponse, 302);
    expect(enrollLocationResponse.headers['location']).toBeDefined();
    expect(enrollLocationResponse.headers['location']).toContain('/enroll');
  });
  it('and factor is enrolled but challenge is disabled should stop MFA flow', async () => {
    const ctx = enrollDefaultCtx2;
    await processMfaEndToEnd(ctx);

    const authResponse = await get(ctx.clientAuthUrl, 302);
    const loginPage = await followUpGet(authResponse, 200);

    let xsrf = extractDomValue(loginPage, '[name=X-XSRF-TOKEN]');
    let action = extractDomAttr(loginPage, 'form', 'action');

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

    const authorizeResponse = await followUpGet(loginPostResponse, 302);

    expect(authorizeResponse.headers['location']).toBeDefined();
    expect(authorizeResponse.headers['location']).toContain('code=');

    const session = extractCookieSessionValues(authorizeResponse.headers['set-cookie']);
    expect(session.strongAuthCompleted).not.toBeDefined();
  });
  it('and factor is enrolled and challenge is enabled goto Challenge', async () => {
    const ctx = enrollDefaultCtx3;
    await processMfaEndToEnd(ctx);

    const authResponse = await get(ctx.clientAuthUrl, 302);
    const loginPage = await followUpGet(authResponse, 200);

    let xsrf = extractDomValue(loginPage, '[name=X-XSRF-TOKEN]');
    let action = extractDomAttr(loginPage, 'form', 'action');

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

    const authorizeResponse = await followUpGet(loginPostResponse, 302);

    expect(authorizeResponse.headers['location']).toBeDefined();
    expect(authorizeResponse.headers['location']).toContain('/challenge');
  });
});
