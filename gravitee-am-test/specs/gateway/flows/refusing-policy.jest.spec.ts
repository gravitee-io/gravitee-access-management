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
  refusingPolicy,
  setupRefusingPolicyFixture,
  throwingPolicy,
} from './fixture/refusing-policy-fixture';

setup(200000);
// Each test sets the flow's policies before signing in, so they must not interleave.
retryImmediatelyForThisFile();

/**
 * AM-2199 / UC-AM35 — a policy in the authentication flow that refuses a sign-in.
 *
 * The policies already covered all add or set something, and every one of those tests ends with
 * the sign-in succeeding. Nothing showed that a policy can turn a user away, so a flow engine
 * that stopped honouring a refusal would go unnoticed.
 *
 * The policy is attached to the Login post step: the user's credentials are accepted and the
 * policy then refuses, which is the case worth guarding. On the pre step the refusal lands on the
 * login page request itself, before any credentials are entered.
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
const signInAndReadRedirect = async (): Promise<string> => {
  const clientId = fixture.application.settings.oauth.clientId;

  const authResponse = await performGet(
    fixture.openIdConfiguration.authorization_endpoint,
    `?response_type=code&client_id=${clientId}&redirect_uri=${REDIRECT_URI}&scope=openid`,
  ).expect(302);

  const postLogin = await login(authResponse, fixture.user.username, clientId, fixture.user.password);
  expect(postLogin.headers['location']).toBeDefined();
  return postLogin.headers['location'];
};

describe('A policy in the login flow that refuses', () => {
  it(jira`a refusing policy stops the sign-in ${'AM-2199'}`, async () => {
    await fixture.setLoginPolicies('post', [refusingPolicy()]);

    const location = await signInAndReadRedirect();

    // Sent back to the login page carrying the policy's refusal, rather than on to the
    // application: no authorization code is issued.
    expect(location).toContain('error=login_failed');
    expect(location).toContain('error_code=REQUEST_VALIDATION_INVALID');
    // Anchored so it matches only an authorization code parameter, not response_type=code
    // or error_code= which are both present on the refusal redirect.
    expect(location).not.toMatch(/[?&]code=/);
    expect(location).not.toContain(REDIRECT_URI);
  });

  it(jira`the same user signs in once the policy is removed ${'AM-2199'}`, async () => {
    await fixture.setLoginPolicies('post', []);

    const location = await signInAndReadRedirect();

    // The discriminator for the test above: same user, same credentials, only the policy differs.
    // Without this, a refusal could equally have been bad credentials or a broken domain.
    expect(location).not.toContain('error');
    expect(location).toContain('/oauth/authorize');
  });

  it(jira`a policy that fails at runtime also stops the sign-in ${'AM-2199'}`, async () => {
    await fixture.setLoginPolicies('post', [throwingPolicy()]);

    const location = await signInAndReadRedirect();
    const url = new URL(location);

    // Pinning the behaviour down rather than assuming it: a policy erroring mid-flow turns the
    // user away, it does not let them through.
    expect(url.searchParams.get('error')).toEqual('login_failed');
    expect(url.pathname).toEqual(`/${fixture.domain.hrid}/login`);
    expect(location).not.toMatch(/[?&]code=/);
    expect(location).not.toContain(REDIRECT_URI);

    // The gateway currently puts the script's own runtime message into error_description. That is
    // deliberately not asserted here: the wording belongs to the JVM rather than to AM, and an
    // internal message reaching the browser at all is tracked as AM-7593. Should that be fixed to
    // a generic description, this test keeps passing.
  });
});
