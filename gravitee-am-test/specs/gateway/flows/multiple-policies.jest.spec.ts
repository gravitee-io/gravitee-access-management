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
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { login } from '@gateway-commands/login-commands';
import { retryImmediatelyForThisFile, setup } from '../../test-fixture';
import {
  REDIRECT_URI,
  RefusingPolicyFixture,
  enrichProfilePolicy,
  refusingPolicy,
  setupRefusingPolicyFixture,
  throwingPolicy,
} from './fixture/refusing-policy-fixture';

setup(200000);
// Each test rewrites the flow's policies before signing in, so they must not interleave.
retryImmediatelyForThisFile();

/**
 * AM-2198 / UC-AM36 — several policies in one flow, where one of them refuses or fails.
 *
 * Policies running together, in order, and domain and application flows combining are all
 * covered already. Every one of those tests ends with the sign-in succeeding, so nothing showed
 * what a chain does when a member of it turns the user away or breaks.
 *
 * Enrich User Profile is used as the observable step: it writes to the user's stored profile
 * rather than only to the token, so whether it ran can still be established after a sign-in that
 * was refused and issued nothing. Each test writes its own key, since the profile accumulates.
 */
let fixture: RefusingPolicyFixture;

beforeAll(async () => {
  fixture = await setupRefusingPolicyFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

/** Signs in with valid credentials and returns where the gateway sent the user. */
const signIn = async (): Promise<string> => {
  const clientId = fixture.application.settings.oauth.clientId;

  const authResponse = await performGet(
    fixture.openIdConfiguration.authorization_endpoint,
    `?response_type=code&client_id=${clientId}&redirect_uri=${REDIRECT_URI}&scope=openid`,
  ).expect(302);

  const postLogin = await login(authResponse, fixture.user.username, clientId, fixture.user.password);
  expect(postLogin.headers['location']).toBeDefined();
  return postLogin.headers['location'];
};

describe('Several policies in one flow', () => {
  it(jira`all of them run when none refuses ${'AM-2198'}`, async () => {
    await fixture.setLoginPolicies('post', [
      enrichProfilePolicy('chain_first', 'first-ran'),
      enrichProfilePolicy('chain_second', 'second-ran'),
    ]);

    const location = await signIn();
    expect(location).toContain('/oauth/authorize');

    // Anchors the two tests below: both policies in a chain do take effect, so a later absence
    // means the chain stopped rather than the policy never having worked.
    const profile = await fixture.readUserProfile();
    expect(profile.chain_first).toEqual('first-ran');
    expect(profile.chain_second).toEqual('second-ran');
  });

  it(jira`a refusing policy among others stops the sign-in ${'AM-2198'}`, async () => {
    await fixture.setLoginPolicies('post', [enrichProfilePolicy('before_refusal', 'written-first'), refusingPolicy()]);

    const location = await signIn();

    expect(location).toContain('error=login_failed');
    expect(location).toContain('error_code=REQUEST_VALIDATION_INVALID');
    // Anchored so it matches only an authorization code, not response_type=code or error_code=.
    expect(location).not.toMatch(/[?&]code=/);

    // The policy that ran before the refusal keeps its effect: a refused sign-in still leaves
    // what earlier policies wrote against the user.
    expect((await fixture.readUserProfile()).before_refusal).toEqual('written-first');
  });

  it(jira`a policy failing partway stops the ones after it ${'AM-2198'}`, async () => {
    await fixture.setLoginPolicies('post', [
      enrichProfilePolicy('before_failure', 'ran-before'),
      throwingPolicy(),
      enrichProfilePolicy('after_failure', 'ran-after'),
    ]);

    const location = await signIn();
    expect(location).toContain('error=login_failed');
    expect(location).not.toMatch(/[?&]code=/);

    const profile = await fixture.readUserProfile();
    // The chain stops where it broke: what came before is kept, what came after never ran.
    // The first test shows a second policy in a chain does otherwise take effect, so this is
    // the failure halting it rather than the policy being misconfigured.
    expect(profile.before_failure).toEqual('ran-before');
    expect(profile).not.toHaveProperty('after_failure');
  });
});
