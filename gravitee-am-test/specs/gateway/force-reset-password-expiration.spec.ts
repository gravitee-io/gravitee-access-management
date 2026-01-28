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
import * as faker from 'faker';
import { afterAll, beforeAll, expect } from '@jest/globals';
import {
  createDomain,
  safeDeleteDomain,
  patchDomain,
  startDomain,
  waitFor,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { buildCreateAndTestUser, updateUserStatus } from '@management-commands/user-management-commands';

import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getWellKnownOpenIdConfiguration, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import {
  assignPasswordPolicyToIdp,
  createPasswordPolicy,
  updatePasswordPolicy,
} from '@management-commands/password-policy-management-commands';
import { initiateLoginFlow, login } from '@gateway-commands/login-commands';
import { TEST_USER } from './oidc-idp/common';
import { enableDomain } from './mfa/fixture/mfa-setup-fixture';
import { setup } from '../test-fixture';
import { withRetry } from '@utils-commands/retry';
import { uniqueName } from '@utils-commands/misc';

setup(200000);

let accessToken;
let withReset;
let withoutReset;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  withReset = await initDomain(true);
  withoutReset = await initDomain(false);
});

async function initDomain(resetPasswordOnExpiration: boolean) {
  const createdDomain = await createDomain(accessToken, uniqueName('force-reset-password', true), faker.company.catchPhraseDescriptor());
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();

  const idpSet = await getAllIdps(createdDomain.id, accessToken);
  const defaultIdp = idpSet.values().next().value;

  const client = await createTestApp('webapp', createdDomain, accessToken, 'WEB', {
    settings: {
      login: {
        inherited: false,
        resetPasswordOnExpiration: resetPasswordOnExpiration,
      },
      oauth: {
        redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
        grantTypes: ['authorization_code'],
        scopeSettings: [],
      },
    },
    identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
  });

  await createPasswordPolicy(createdDomain.id, accessToken, {
    name: 'default-to-assign',
    expiryDuration: 1,
  }).then((policy) => assignPasswordPolicyToIdp(createdDomain.id, accessToken, defaultIdp.id, policy.id));

  const oidc = await startDomain(createdDomain.id, accessToken)
    .then(() => waitFor(3000))
    .then(() => withRetry(() => getWellKnownOpenIdConfiguration(createdDomain.hrid).expect(200)));

  const today = new Date();
  const twoDaysAgo = new Date(today);
  twoDaysAgo.setDate(today.getDate() - 2);

  const user = await buildCreateAndTestUser(createdDomain.id, accessToken, 1, false, 'SomeP@sswo0rd', twoDaysAgo);
  return {
    domain: createdDomain,
    client: client,
    user: user,
    oidc: oidc.body,
  };
}

afterAll(async () => {
  if (withoutReset?.domain?.id) {
    await safeDeleteDomain(withoutReset?.domain?.id, accessToken);
  }
  if (withReset?.domain?.id) {
    await safeDeleteDomain(withReset?.domain?.id, accessToken);
  }
});

describe('when resetPasswordOnExpiration is enabled', () => {
  it('and user with expired password is trying to log in should be forced to reset password', async () => {
    const clientId = withReset.client.settings.oauth.clientId;
    const authResponse = await initiateLoginFlow(clientId, withReset.oidc, withReset.domain);
    const postLogin = await login(authResponse, withReset.user.username, clientId, 'SomeP@sswo0rd');
    const authResponse2 = await performGet(postLogin.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'],
    }).expect(302);

    const resetPasswordLocation = authResponse2.headers['location'];

    expect(resetPasswordLocation).toBeDefined();
    expect(resetPasswordLocation).toContain('/resetPassword');
  });
});

describe('when resetPasswordOnExpiration is disabled', () => {
  it('and user with expired password is trying to log in should see an error', async () => {
    const clientId = withoutReset.client.settings.oauth.clientId;
    const authResponse = await initiateLoginFlow(clientId, withoutReset.oidc, withoutReset.domain);
    const postLogin = await login(authResponse, withoutReset.user.username, clientId, 'SomeP@sswo0rd');

    const postLoginLocation = postLogin.headers['location'];

    expect(postLoginLocation).toBeDefined();
    expect(postLoginLocation).toContain('error_code=account_password_expired');
  });
});
