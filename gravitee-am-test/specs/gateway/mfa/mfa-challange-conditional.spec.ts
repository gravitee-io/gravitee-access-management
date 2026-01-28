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
        enroll: { active: false, enrollmentSkipActive: false, forceEnrollment: false, type: 'required' },
        challenge: { active: true, challengeRule: '', type: 'CONDITIONAL' },
      },
    },
  };
}

const domain = {
  admin: { username: 'admin', password: 'adminadmin' },
  domain: { domainHrid: 'mfa-test-domain-challenge-conditional' },
} as Domain;

beforeAll(async () => {
  await initDomain(domain, 3);

  let settings;

  settings = defaultApplicationSettings();
  settings.settings.mfa.challenge.challengeRule = '{{ true }}';
  const challengeRulePositiveClient = await initClient(domain, 'challenge-rule-positive', settings);

  settings = defaultApplicationSettings();
  settings.settings.mfa.challenge.challengeRule = '{{ false }}';
  const challengeRuleNegativeClient1 = await initClient(domain, 'challenge-rule-negative', settings);

  settings = defaultApplicationSettings();
  settings.settings.mfa.challenge.challengeRule = '{{ false }}';
  const challengeRuleNegativeClient2 = await initClient(domain, 'challenge-rule-negative2', settings);

  const oidc = await enableDomain(domain)
    .then(() => waitFor(3000))
    .then(() => withRetry(() => getWellKnownOpenIdConfiguration(domain.domain.domainHrid).expect(200)));

  challengeRulePositiveCtx = new TestSuiteContext(
    domain,
    challengeRulePositiveClient,
    domain.domain.users[0],
    oidc.body.authorization_endpoint,
  );
  challengeRuleNegativeCtx1 = new TestSuiteContext(
    domain,
    challengeRuleNegativeClient1,
    domain.domain.users[1],
    oidc.body.authorization_endpoint,
  );
  challengeRuleNegativeCtx2 = new TestSuiteContext(
    domain,
    challengeRuleNegativeClient2,
    domain.domain.users[2],
    oidc.body.authorization_endpoint,
  );
});

afterAll(async () => {
  await removeDomain(domain);
});

let challengeRulePositiveCtx: TestSuiteContext;
let challengeRuleNegativeCtx1: TestSuiteContext;
let challengeRuleNegativeCtx2: TestSuiteContext;

describe('When Challenge CONDITIONAL', () => {
  it('and rule evaluates to true should end MFA flow', async () => {
    const ctx = challengeRulePositiveCtx;
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

    const finalPage = await followUpGet(loginPostResponse, 302);
    expect(finalPage.headers['location']).toBeDefined();
    expect(finalPage.headers['location']).toContain('code=');
  });
  it('and rule evaluates to false and no factor enrolled should enroll', async () => {
    const ctx = challengeRuleNegativeCtx1;
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

    const finalPage = await followUpGet(loginPostResponse, 302);
    expect(finalPage.headers['location']).toBeDefined();
    expect(finalPage.headers['location']).toContain('enroll');
  });
  it('and rule evaluates to false and factor is enrolled and has valid MFA session should end MFA flow', async () => {
    const ctx = challengeRuleNegativeCtx2;
    const cookie = await processMfaEndToEnd(ctx);
    const authResponse = await get(ctx.clientAuthUrl, 302, { Cookie: cookie.cookie });
    expect(authResponse.headers['location']).toBeDefined();
    expect(authResponse.headers['location']).toContain('code=');
  });
});
