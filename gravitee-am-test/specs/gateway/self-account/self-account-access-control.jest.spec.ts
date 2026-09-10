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
import { performGet, performPut } from '@gateway-commands/oauth-oidc-commands';
import { SelfAccountFixture, setupFixture, getEndUserAccessToken } from './fixture/self-account-fixture';
import { setup } from '../../test-fixture';

setup(300000);

const ENABLED = {
  selfServiceAccountManagementSettings: { enabled: true, resetPassword: { oldPasswordRequired: false, tokenAge: 0 } },
};
const DISABLED = { selfServiceAccountManagementSettings: { enabled: false } };

// The guard is on the router, not per route, so a spread of three is enough.
const REPRESENTATIVE_PATHS = ['/profile', '/activity', '/consent'];

let enabled: SelfAccountFixture;
let disabled: SelfAccountFixture;
let userToken: string;

const urlFor = (fixture: SelfAccountFixture, path: string) => `${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/account/api${path}`;

beforeAll(async () => {
  enabled = await setupFixture(ENABLED, 'self-account-access-control-on');
  disabled = await setupFixture(DISABLED, 'self-account-access-control-off');
  userToken = await getEndUserAccessToken(enabled);
});

afterAll(async () => {
  if (enabled) await enabled.cleanUp();
  if (disabled) await disabled.cleanUp();
});

describe('SelfAccount - the feature switch', () => {
  it.each(REPRESENTATIVE_PATHS)(jira`%s is not served when self-account management is disabled ${'AM-7652'}`, async (path) => {
    // The routes are only registered when the setting is on, so nothing behind it should answer.
    // Asserted against a domain that is otherwise identical to the one used below.
    const token = await getEndUserAccessToken(disabled);

    const response = await performGet(urlFor(disabled, path), '', { Authorization: `Bearer ${token}` });

    expect(response.status).toBe(404);
  });

  it(jira`the same paths are served when it is enabled ${'AM-7652'}`, async () => {
    // The control for the block above: these 404s are the switch, not a wrong URL.
    for (const path of REPRESENTATIVE_PATHS) {
      const response = await performGet(urlFor(enabled, path), '', { Authorization: `Bearer ${userToken}` });
      expect(response.status).toBe(200);
    }
  });
});

describe('SelfAccount - authentication', () => {
  it.each(REPRESENTATIVE_PATHS)(jira`%s refuses a request with no token ${'AM-7652'}`, async (path) => {
    const response = await performGet(urlFor(enabled, path), '');

    expect(response.status).toBe(401);
  });

  it.each(REPRESENTATIVE_PATHS)(jira`%s refuses a malformed token ${'AM-7652'}`, async (path) => {
    const response = await performGet(urlFor(enabled, path), '', { Authorization: 'Bearer not-a-real-token' });

    expect(response.status).toBe(401);
  });

  it(jira`a token from another domain is refused ${'AM-7652'}`, async () => {
    // Structurally valid and signed, but issued by a different domain — the check has to be more
    // than "is this a well-formed token".
    const foreign = await getEndUserAccessToken(disabled);

    const response = await performGet(urlFor(enabled, '/profile'), '', { Authorization: `Bearer ${foreign}` });

    expect(response.status).toBe(401);
  });

  it(jira`writes are refused without a token as well as reads ${'AM-7652'}`, async () => {
    const response = await performPut(urlFor(enabled, '/profile'), '', JSON.stringify({ firstName: 'Nope' }), {
      'Content-Type': 'application/json',
    });

    expect(response.status).toBe(401);
  });

  // Not covered: an expired token. Shortening accessTokenValiditySeconds stores fine but the
  // gateway keeps issuing the previous lifetime, so a token cannot be made to lapse in a test run.
});
