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
import * as faker from 'faker';
import { afterAll, afterEach, beforeAll, expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, startDomain, waitFor, waitForDomainStart } from '@management-commands/domain-management-commands';
import { getIdp } from '@management-commands/idp-management-commands';
import {
  assignPasswordPolicyToIdp,
  createPasswordPolicy,
  deletePasswordPolicy,
  getAllPasswordPolicies,
  getPasswordPolicy,
  resetUserPassword,
  setPasswordPolicyDefault,
} from '@management-commands/password-policy-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { createJdbcIdp, createMongoIdp } from '@utils-commands/idps-commands';
import { createUser, deleteUser, getUser } from '@management-commands/user-management-commands';
import { User } from '../../api/management/models';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { getWellKnownOpenIdConfiguration, logoutUser } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';

global.fetch = fetch;

const jdbc = process.env.GRAVITEE_REPOSITORIES_MANAGEMENT_TYPE;

jest.setTimeout(200000);
let accessToken;
let domain;
let app;
let customIdp;
let customIdp2;
let openIdConfiguration;
let user;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  const createdDomain = await createDomain(accessToken, uniqueName('domain-idp'), faker.company.catchPhraseDescriptor());
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();

  const domainStarted = await startDomain(createdDomain.id, accessToken);
  expect(domainStarted).toBeDefined();
  expect(domainStarted.id).toEqual(createdDomain.id);

  domain = domainStarted;

  customIdp = jdbc === 'jdbc' ? await createJdbcIdp(domain.id, accessToken) : await createMongoIdp(domain.id, accessToken);
  customIdp2 = jdbc === 'jdbc' ? await createJdbcIdp(domain.id, accessToken) : await createMongoIdp(domain.id, accessToken);
  app = await createTestApp('password-policy-test-login-app', domain, accessToken, 'WEB', {
    settings: {
      oauth: {
        redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
        scopeSettings: [
          { scope: 'openid', defaultScope: true },
          {
            scope: 'openid',
            defaultScope: true,
          },
        ],
      },
    },
    identityProviders: new Set([{ identity: customIdp.id, priority: 0 }]),
  });

  openIdConfiguration = (await waitForDomainStart(domain)).oidcConfig;
});

describe('password policy management', () => {
  it('must set default password policy to Idp', async () => {
    const policy = await createPasswordPolicy(domain.id, accessToken, {
      name: 'default-to-assign',
    });
    await setPasswordPolicyDefault(domain.id, accessToken, policy.id);
    await assignPasswordPolicyToIdp(domain.id, accessToken, customIdp.id, policy.id);

    const updatedPolicy = await getPasswordPolicy(domain.id, accessToken, policy.id);
    expect(updatedPolicy).toBeDefined();
    expect(updatedPolicy.defaultPolicy).toBeTruthy();

    const foundIdp = await getIdp(domain.id, accessToken, customIdp.id);
    expect(foundIdp).toBeDefined();
    expect(foundIdp.id).toEqual(customIdp.id);
    expect(foundIdp.passwordPolicy).toEqual(policy.id);
  });

  it('must update default password policy from removed to oldest', async () => {
    const { oldPolicy, newPolicy1, newPolicy2 } = await createThreePasswordPolicies();

    await setPasswordPolicyDefault(domain.id, accessToken, newPolicy1.id);

    const updatedDefaultPolicy = await getPasswordPolicy(domain.id, accessToken, newPolicy1.id);
    expect(updatedDefaultPolicy).toBeDefined();
    expect(updatedDefaultPolicy.defaultPolicy).toBeTruthy();

    await deletePasswordPolicy(domain.id, accessToken, newPolicy1.id);

    const updatedPolicy = await getPasswordPolicy(domain.id, accessToken, oldPolicy.id);
    expect(updatedPolicy).toBeDefined();
    expect(updatedPolicy.defaultPolicy).toBeTruthy();
    const new2Policy = await getPasswordPolicy(domain.id, accessToken, newPolicy2.id);
    expect(new2Policy).toBeDefined();
    expect(new2Policy.defaultPolicy).toBeFalsy();
  });

  it('must update default password policy from removed to oldest one in identity provider', async () => {
    const { oldPolicy, newPolicy1, newPolicy2 } = await createThreePasswordPolicies();

    await setPasswordPolicyDefault(domain.id, accessToken, newPolicy1.id);
    await assignPasswordPolicyToIdp(domain.id, accessToken, customIdp.id, oldPolicy.id);

    const updatedDefaultPolicy = await getPasswordPolicy(domain.id, accessToken, newPolicy1.id);
    expect(updatedDefaultPolicy).toBeDefined();
    expect(updatedDefaultPolicy.defaultPolicy).toBeTruthy();

    await deletePasswordPolicy(domain.id, accessToken, newPolicy1.id);

    const updatedPolicy = await getPasswordPolicy(domain.id, accessToken, oldPolicy.id);
    expect(updatedPolicy).toBeDefined();
    expect(updatedPolicy.defaultPolicy).toBeTruthy();
    const new2Policy = await getPasswordPolicy(domain.id, accessToken, newPolicy2.id);
    expect(new2Policy).toBeDefined();
    expect(new2Policy.defaultPolicy).toBeFalsy();

    const foundIdp = await getIdp(domain.id, accessToken, customIdp.id);
    expect(foundIdp).toBeDefined();
    expect(foundIdp.id).toEqual(customIdp.id);
    expect(foundIdp.passwordPolicy).toEqual(oldPolicy.id);
  });

  it('must add user with password set in idp', async () => {
    const policy = await createPasswordPolicy(domain.id, accessToken, {
      name: 'default-to-assign',
      minLength: 5,
    });
    await assignPasswordPolicyToIdp(domain.id, accessToken, customIdp.id, policy.id);

    user = await createOneUser('correct-user', customIdp.id);
    await new Promise((r) => setTimeout(r, 1000));

    const fetchUser = await getUser(domain.id, accessToken, user.id);
    expect(fetchUser).toBeDefined();
    expect(fetchUser.id).toEqual(user.id);
  });

  it('must add user with password set in idp when user does not use default idp', async () => {
    const policy = await createPasswordPolicy(domain.id, accessToken, {
      name: 'default-to-assign',
      minLength: 5,
    });
    await assignPasswordPolicyToIdp(domain.id, accessToken, customIdp.id, policy.id);

    user = await createOneUser('correct-user-2', customIdp2.id);
    await new Promise((r) => setTimeout(r, 1000));

    const fetchUser = await getUser(domain.id, accessToken, user.id);
    expect(fetchUser).toBeDefined();
    expect(fetchUser.id).toEqual(user.id);
  });

  it('must not add user when password invalid', async () => {
    const policy = await createPasswordPolicy(domain.id, accessToken, {
      name: 'default-to-assign',
      minLength: 30,
    });

    await assignPasswordPolicyToIdp(domain.id, accessToken, customIdp.id, policy.id);

    await expect(async () => {
      await createOneUser('user-invalid-password', customIdp.id);
    }).rejects.toThrow();
  });

  it('must reset user password', async () => {
    const policy = await createPasswordPolicy(domain.id, accessToken, {
      name: 'default-to-assign',
      minLength: 5,
    });
    await assignPasswordPolicyToIdp(domain.id, accessToken, customIdp.id, policy.id);
    const userNewPassword = 'newpassTest^&*';
    user = await createOneUser('correct-user-pass-reset', customIdp.id);

    await waitFor(1000);
    await resetUserPassword(domain.id, accessToken, user.id, userNewPassword);
    await waitFor(1000);

    const clientId = app.settings.oauth.clientId;
    const user1TokenResponse = await loginUserNameAndPassword(clientId, user, userNewPassword, false, openIdConfiguration, domain);
    expect(user1TokenResponse.headers['location']).toContain('callback?code=');
    await logoutUser(openIdConfiguration.end_session_endpoint, user1TokenResponse);
  });

  it('must not reset user password when its incorrect', async () => {
    const policy = await createPasswordPolicy(domain.id, accessToken, {
      name: 'default-to-assign',
      minLength: 30,
    });
    await assignPasswordPolicyToIdp(domain.id, accessToken, customIdp.id, policy.id);
    const userNewPassword = 'newpassTest^&*';
    user = await createOneUser('incorrect-pass-reset', customIdp.id, '1234352342@#$@Tetssiokiokdsfsjji323j2i3j2i3j2');
    await waitFor(1000);

    await expect(async () => {
      const response = await resetUserPassword(domain.id, accessToken, user.id, userNewPassword);
      expect(response.raw.status).toEqual(400);
    }).rejects.toThrow();
  });

  async function createThreePasswordPolicies() {
    const oldPolicy = await createPasswordPolicy(domain.id, accessToken, {
      name: 'old-policy',
    });
    const newPolicy1 = await createPasswordPolicy(domain.id, accessToken, {
      name: 'new-1',
    });
    const newPolicy2 = await createPasswordPolicy(domain.id, accessToken, {
      name: 'new-2',
    });

    const passwordPolicies = await getAllPasswordPolicies(domain.id, accessToken);
    expect(passwordPolicies.length).toEqual(3);

    return { oldPolicy, newPolicy1, newPolicy2 };
  }

  async function createOneUser(username: string, idpId: string, password?: string): Promise<User> {
    return await createUser(domain.id, accessToken, {
      firstName: 'john',
      lastName: 'doe',
      email: `${username}@test.com`,
      username: username,
      password: password ? password : 'ZxcPrm7123!!',
      client: app.id,
      source: idpId,
      additionalInformation: {
        contract: '1234',
      },
      preRegistration: false,
    });
  }
});

afterEach(async () => {
  await removeAllPasswordPolicies();
  if (user) {
    await deleteUser(domain.id, accessToken, user.id);
    user = undefined;
  }
});

afterAll(async () => {
  if (domain?.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});

async function removeAllPasswordPolicies(): Promise<void> {
  const passwordPolicies = await getAllPasswordPolicies(domain.id, accessToken).catch(() => []);
  for (const p of passwordPolicies) {
    await deletePasswordPolicy(domain.id, accessToken, p.id);
  }
}
