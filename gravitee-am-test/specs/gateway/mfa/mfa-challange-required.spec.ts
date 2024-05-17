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
import { afterAll, beforeAll, beforeEach, expect, jest } from '@jest/globals';
import { Domain, enableDomain, initClient, initDomain, removeDomain, TestSuiteContext } from './fixture/mfa-setup-fixture';
import { withRetry } from '@utils-commands/retry';
import { followUpGet, get, postForm, processMfaEndToEnd, processMfaEnrollment } from './fixture/mfa-flow-fixture';
import { extractDomAttr, extractDomValue } from './fixture/mfa-extract-fixture';
import { getWellKnownOpenIdConfiguration } from '@gateway-commands/oauth-oidc-commands';
import { waitFor } from '@management-commands/domain-management-commands';
import { loginUser } from '@gateway-commands/login-commands';

global.fetch = fetch;
jest.setTimeout(200000);

function defaultApplicationSettings(domain: Domain) {
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
        enroll: { active: false, enrollmentSkipActive: false, forceEnrollment: false, type: 'required' },
        challenge: { active: true, challengeRule: '', type: 'required' },
      },
    },
  };
}

const domain = {
  admin: { username: 'admin', password: 'adminadmin' },
  domain: { domainHrid: 'mfa-test-domain-challenge-req' },
} as Domain;

beforeAll(async () => {
  await initDomain(domain, 5);

  const defaultClient1 = await initClient(domain, 'default-1', defaultApplicationSettings(domain));
  const defaultClient2 = await initClient(domain, 'default-2', defaultApplicationSettings(domain));
  const defaultClient3 = await initClient(domain, 'default-3', defaultApplicationSettings(domain));

  const rememberDeviceClientSettings = defaultApplicationSettings(domain);
  rememberDeviceClientSettings.settings.mfa.rememberDevice = {
    active: true,
    skipRememberDevice: false,
    expirationTimeSeconds: 360000,
    deviceIdentifierId: domain.domain.devices[0].id,
  };

  const rememberDeviceClient1 = await initClient(domain, 'remember-1', rememberDeviceClientSettings);
  const rememberDeviceClient2 = await initClient(domain, 'remember-2', rememberDeviceClientSettings);

  const oidc = await enableDomain(domain)
    .then(() => waitFor(3000))
    .then(() => withRetry(() => getWellKnownOpenIdConfiguration(domain.domain.domainHrid).expect(200)));

  defaultClientCtx1 = new TestSuiteContext(domain, defaultClient1, domain.domain.users[0], oidc.body.authorization_endpoint);
  defaultClientCtx2 = new TestSuiteContext(domain, defaultClient2, domain.domain.users[1], oidc.body.authorization_endpoint);
  defaultClientCtx3 = new TestSuiteContext(domain, defaultClient3, domain.domain.users[2], oidc.body.authorization_endpoint);
  rememberMeCtx1 = new TestSuiteContext(domain, rememberDeviceClient1, domain.domain.users[3], oidc.body.authorization_endpoint);
  rememberMeCtx2 = new TestSuiteContext(domain, rememberDeviceClient2, domain.domain.users[4], oidc.body.authorization_endpoint);
});

afterAll(async () => {
  await removeDomain(domain);
});

let defaultClientCtx1: TestSuiteContext;
let defaultClientCtx2: TestSuiteContext;
let defaultClientCtx3: TestSuiteContext;
let rememberMeCtx1: TestSuiteContext;
let rememberMeCtx2: TestSuiteContext;

describe('When Challenge REQUIRED and factor not enrolled', () =>
  it('should Enroll', async () => {
    const ctx = defaultClientCtx1;

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
        rememberMe: 'off',
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
  }));

describe('When Challenge REQUIRED and factor enrolled and valid session issued using MFA', () =>
  it('should end MFA flow', async () => {
    const ctx = defaultClientCtx2;

    let session = await processMfaEndToEnd(ctx);
    const authResponse = await get(ctx.clientAuthUrl, 302, { Cookie: session.cookie });

    expect(authResponse.headers['location']).toBeDefined();
    expect(authResponse.headers['location']).toContain('code=');
  }));

describe('When Challenge REQUIRED and factor enrolled and no session issued using MFA and RememberDevice disabled', () =>
  it('should Challenge', async () => {
    const ctx = defaultClientCtx3;

    let session = await processMfaEnrollment(ctx);
    const authResponse = await get(ctx.clientAuthUrl, 302, { Cookie: session.cookie });

    expect(authResponse.headers['location']).toBeDefined();
    expect(authResponse.headers['location']).toContain('challenge');
  }));

describe('When Challenge REQUIRED and factor enrolled and no session issued using MFA and RememberDevice enabled and user has NOT associated device', () =>
  it('should Challenge', async () => {
    const ctx = rememberMeCtx1;

    let session = await processMfaEnrollment(ctx);
    const authResponse = await withRetry(() => get(ctx.clientAuthUrl, 302, { Cookie: session.cookie }, 'challenge'));

    expect(authResponse.headers['location']).toBeDefined();
    expect(authResponse.headers['location']).toContain('challenge');
  }));

describe('When Challenge REQUIRED and factor enrolled and no session issued using MFA and RememberDevice enabled and user associated device', () =>
  it('should End MFA flow', async () => {
    const ctx = rememberMeCtx2;
    let session = await processMfaEndToEnd(ctx, true);
    const authResponse = await get(ctx.clientAuthUrl, 302, { Cookie: session.cookie });

    expect(authResponse.headers['location']).toBeDefined();
    expect(authResponse.headers['location']).toContain('code=');
  }));
