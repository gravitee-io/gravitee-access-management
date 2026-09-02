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
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { jira } from '@specs-utils/jira';
import { waitForOidcReady } from '@management-commands/domain-management-commands';
import { Domain, initClient, initDomain, enableDomain, removeDomain, TestSuiteContext } from './fixture/mfa-setup-fixture';
import { get, processLoginFromContext, processMfaEndToEnd } from './fixture/mfa-flow-fixture';
import { setup } from '../../test-fixture';

setup(300000);

/**
 * AM-2209 / UC-AM43 — step-up authentication that does not apply, for a user who already has a
 * session.
 *
 * "Not challenged" is only worth asserting if something here is challenged, otherwise these tests
 * would pass just as well with multi-factor authentication switched off altogether. The test
 * covering one session across two applications carries that weight, by getting opposite outcomes
 * from the same session.
 */
const domain = {
  admin: { username: 'admin', password: 'adminadmin' },
  domain: { domainHrid: 'mfa-step-up-not-applied-am2209' },
} as Domain;

/** An application whose step-up rule is `rule`, or with step-up switched off when `active` is false. */
const stepUpSettings = (dom: Domain, rule: string, active: boolean) => ({
  factors: [],
  settings: {
    mfa: {
      factor: {
        defaultFactorId: dom.domain.factors[0].id,
        applicationFactors: [dom.domain.factors[0]],
      },
      stepUpAuthenticationRule: rule,
      stepUpAuthentication: { active, stepUpAuthenticationRule: rule },
    },
  },
});

let ruleDoesNotApplyCtx: TestSuiteContext;
let stepUpOffCtx: TestSuiteContext;
let ruleAppliesCtx: TestSuiteContext;

/** Signs in from scratch and returns the session that sign-in established. */
const signInAndKeepSession = async (ctx: TestSuiteContext) => {
  const response = await processLoginFromContext(ctx);
  expect(response.headers['location']).toContain('code=');
  const cookie = response.headers['set-cookie'];
  expect(cookie).toBeDefined();
  return cookie;
};

/** Returns to an application carrying an existing session. */
const returnToApplication = async (ctx: TestSuiteContext, cookie: any) => {
  const response = await get(ctx.clientAuthUrl, 302, { Cookie: cookie });
  const location = response.headers['location'];
  expect(location).toBeDefined();
  return { location, letThrough: location.includes('code=') };
};

beforeAll(async () => {
  await initDomain(domain, 3);
  const ruleDoesNotApply = await initClient(domain, 'step-up-rule-false', stepUpSettings(domain, '{{ false }}', true));
  const stepUpOff = await initClient(domain, 'step-up-off', stepUpSettings(domain, '', false));
  const ruleApplies = await initClient(domain, 'step-up-rule-true', stepUpSettings(domain, '{{ true }}', true));
  await enableDomain(domain);

  const oidc = await waitForOidcReady(domain.domain.domainHrid);
  const endpoint = oidc.body.authorization_endpoint;
  ruleDoesNotApplyCtx = new TestSuiteContext(domain, ruleDoesNotApply, domain.domain.users[0], endpoint);
  stepUpOffCtx = new TestSuiteContext(domain, stepUpOff, domain.domain.users[1], endpoint);
  ruleAppliesCtx = new TestSuiteContext(domain, ruleApplies, domain.domain.users[2], endpoint);
});

afterAll(async () => {
  await removeDomain(domain);
});

describe('Step-up authentication that does not apply', () => {
  it(jira`an already signed-in user is not challenged when the rule does not apply ${'AM-2209'}`, async () => {
    const session = await signInAndKeepSession(ruleDoesNotApplyCtx);

    const again = await returnToApplication(ruleDoesNotApplyCtx, session);

    expect(again.letThrough).toBe(true);
    expect(again.location).not.toContain('/mfa/challenge');
  });

  it(jira`an already signed-in user is not challenged when step-up is switched off ${'AM-2209'}`, async () => {
    const session = await signInAndKeepSession(stepUpOffCtx);

    const again = await returnToApplication(stepUpOffCtx, session);

    expect(again.letThrough).toBe(true);
    expect(again.location).not.toContain('/mfa/challenge');
  });

  it(jira`a session one application challenges is let through where the rule does not apply ${'AM-2209'}`, async () => {
    // This session has already answered a challenge on the application that asks for one.
    const session = await processMfaEndToEnd(ruleAppliesCtx);

    // Returning there asks again — so the session is genuinely subject to step-up.
    const backWhereItApplies = await returnToApplication(ruleAppliesCtx, session.cookie);
    expect(backWhereItApplies.letThrough).toBe(false);
    expect(backWhereItApplies.location).toContain('/mfa/challenge');

    // The same session, on an application whose rule does not apply, is let straight through.
    const whereItDoesNot = await returnToApplication(ruleDoesNotApplyCtx, session.cookie);
    expect(whereItDoesNot.letThrough).toBe(true);
    expect(whereItDoesNot.location).not.toContain('/mfa/challenge');
  });
});
