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
import { followUpGet, get, postForm } from './fixture/mfa-flow-fixture';
import { extractDomAttr, extractDomValue } from './fixture/mfa-extract-fixture';
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
        enroll: {
          active: true,
          enrollmentRule: '{{ true }}',
          enrollmentSkipActive: false,
          enrollmentSkipRule: '',
          skipTimeSeconds: undefined,
          forceEnrollment: true,
          type: 'conditional',
        },
        challenge: { active: false, challengeRule: '', type: 'required' },
      },
    },
  };
}

const domain = {
  admin: { username: 'admin', password: 'adminadmin' },
  domain: { domainHrid: 'mfa-test-domain-enroll-conditional' },
} as Domain;

beforeAll(async () => {
  await initDomain(domain, 3);

  let settings;

  settings = defaultApplicationSettings();
  settings.settings.mfa.enroll.enrollmentRule = '{{ true }}';
  const rulePositiveClient = await initClient(domain, 'enroll-rule-positive', settings);

  settings = defaultApplicationSettings();
  settings.settings.mfa.enroll.enrollmentRule = '{{ false }}';
  settings.settings.mfa.enroll.enrollmentSkipActive = false;
  const ruleNegativeClient = await initClient(domain, 'enroll-rule-negative-1', settings);

  settings = defaultApplicationSettings();
  settings.settings.mfa.enroll.enrollmentRule = '{{ false }}';
  settings.settings.mfa.enroll.enrollmentSkipActive = true;
  settings.settings.mfa.enroll.enrollmentSkipRule = '{{ false }}';
  const ruleNegativeClient2 = await initClient(domain, 'enroll-rule-negative-2', settings);

  const oidc = await enableDomain(domain)
    .then(() => waitFor(3000))
    .then(() => withRetry(() => getWellKnownOpenIdConfiguration(domain.domain.domainHrid).expect(200)));

  rulePositiveCtx = new TestSuiteContext(domain, rulePositiveClient, domain.domain.users[0], oidc.body.authorization_endpoint);
  ruleNegativeCtx = new TestSuiteContext(domain, ruleNegativeClient, domain.domain.users[1], oidc.body.authorization_endpoint);
  ruleNegativeCtx2 = new TestSuiteContext(domain, ruleNegativeClient2, domain.domain.users[2], oidc.body.authorization_endpoint);
});

afterAll(async () => {
  await removeDomain(domain);
});

let rulePositiveCtx: TestSuiteContext;
let ruleNegativeCtx: TestSuiteContext;
let ruleNegativeCtx2: TestSuiteContext;
describe('When Enrollment CONDITIONAL', () => {
  it('and rule evaluates to true should stop MFA', async () => {
    const ctx = rulePositiveCtx;

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
    expect(enrollLocationResponse.headers['location']).toContain('code=');
  });

  it('and rule evaluates to false and factor is not enrolled and user can not skip should enroll', async () => {
    const ctx = ruleNegativeCtx;

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

  it('and rule evaluates to false and factor is not enrolled and user did not choose to skip should enroll', async () => {
    const ctx = ruleNegativeCtx2;

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
      '/authorize',
    );

    const enrollLocationResponse = await followUpGet(loginPostResponse, 302);
    expect(enrollLocationResponse.headers['location']).toBeDefined();
    expect(enrollLocationResponse.headers['location']).toContain('/enroll');
  });
});
