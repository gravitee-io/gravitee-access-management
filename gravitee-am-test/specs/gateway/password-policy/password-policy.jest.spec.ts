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
import { afterAll, afterEach, beforeAll, expect } from '@jest/globals';
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
import { User } from '@management-models/index';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { getWellKnownOpenIdConfiguration, logoutUser } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import { PasswordPolicyFixture, removeAllPasswordPolicies, setupFixture } from './fixture/password-policy-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let user;
let fixture: PasswordPolicyFixture;

beforeAll(async () => {
  fixture = await setupFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

afterEach(async () => {
  await removeAllPasswordPolicies(fixture.domain.id, fixture.accessToken);
  if (user) {
    await deleteUser(fixture.domain.id, fixture.accessToken, user.id);
    user = undefined;
  }
});

describe('password policy management', () => {
  it('must set default password policy to Idp', async () => {
    const policy = await createPasswordPolicy(fixture.domain.id, fixture.accessToken, {
      name: 'default-to-assign',
    });
    await setPasswordPolicyDefault(fixture.domain.id, fixture.accessToken, policy.id);
    await assignPasswordPolicyToIdp(fixture.domain.id, fixture.accessToken, fixture.customIdp.id, policy.id);

    const updatedPolicy = await getPasswordPolicy(fixture.domain.id, fixture.accessToken, policy.id);
    expect(updatedPolicy).toBeDefined();
    expect(updatedPolicy.defaultPolicy).toBeTruthy();

    const foundIdp = await getIdp(fixture.domain.id, fixture.accessToken, fixture.customIdp.id);
    expect(foundIdp).toBeDefined();
    expect(foundIdp.id).toEqual(fixture.customIdp.id);
    expect(foundIdp.passwordPolicy).toEqual(policy.id);
  });

  it('must update default password policy from removed to oldest', async () => {
    const { oldPolicy, newPolicy1, newPolicy2 } = await createThreePasswordPolicies();

    await setPasswordPolicyDefault(fixture.domain.id, fixture.accessToken, newPolicy1.id);

    const updatedDefaultPolicy = await getPasswordPolicy(fixture.domain.id, fixture.accessToken, newPolicy1.id);
    expect(updatedDefaultPolicy).toBeDefined();
    expect(updatedDefaultPolicy.defaultPolicy).toBeTruthy();

    await deletePasswordPolicy(fixture.domain.id, fixture.accessToken, newPolicy1.id);

    const updatedPolicy = await getPasswordPolicy(fixture.domain.id, fixture.accessToken, oldPolicy.id);
    expect(updatedPolicy).toBeDefined();
    expect(updatedPolicy.defaultPolicy).toBeTruthy();
    const new2Policy = await getPasswordPolicy(fixture.domain.id, fixture.accessToken, newPolicy2.id);
    expect(new2Policy).toBeDefined();
    expect(new2Policy.defaultPolicy).toBeFalsy();
  });

  it('must update default password policy from removed to oldest one in identity provider', async () => {
    const { oldPolicy, newPolicy1, newPolicy2 } = await createThreePasswordPolicies();

    await setPasswordPolicyDefault(fixture.domain.id, fixture.accessToken, newPolicy1.id);
    await assignPasswordPolicyToIdp(fixture.domain.id, fixture.accessToken, fixture.customIdp.id, oldPolicy.id);

    const updatedDefaultPolicy = await getPasswordPolicy(fixture.domain.id, fixture.accessToken, newPolicy1.id);
    expect(updatedDefaultPolicy).toBeDefined();
    expect(updatedDefaultPolicy.defaultPolicy).toBeTruthy();

    await deletePasswordPolicy(fixture.domain.id, fixture.accessToken, newPolicy1.id);

    const updatedPolicy = await getPasswordPolicy(fixture.domain.id, fixture.accessToken, oldPolicy.id);
    expect(updatedPolicy).toBeDefined();
    expect(updatedPolicy.defaultPolicy).toBeTruthy();
    const new2Policy = await getPasswordPolicy(fixture.domain.id, fixture.accessToken, newPolicy2.id);
    expect(new2Policy).toBeDefined();
    expect(new2Policy.defaultPolicy).toBeFalsy();

    const foundIdp = await getIdp(fixture.domain.id, fixture.accessToken, fixture.customIdp.id);
    expect(foundIdp).toBeDefined();
    expect(foundIdp.id).toEqual(fixture.customIdp.id);
    expect(foundIdp.passwordPolicy).toEqual(oldPolicy.id);
  });

  it('must add user with password set in idp', async () => {
    const policy = await createPasswordPolicy(fixture.domain.id, fixture.accessToken, {
      name: 'default-to-assign',
      minLength: 5,
    });
    await assignPasswordPolicyToIdp(fixture.domain.id, fixture.accessToken, fixture.customIdp.id, policy.id);

    user = await createOneUser('correct-user', fixture.customIdp.id);
    await new Promise((r) => setTimeout(r, 1000));

    const fetchUser = await getUser(fixture.domain.id, fixture.accessToken, user.id);
    expect(fetchUser).toBeDefined();
    expect(fetchUser.id).toEqual(user.id);
  });

  it('must add user with password set in idp when user does not use default idp', async () => {
    const policy = await createPasswordPolicy(fixture.domain.id, fixture.accessToken, {
      name: 'default-to-assign',
      minLength: 5,
    });
    await assignPasswordPolicyToIdp(fixture.domain.id, fixture.accessToken, fixture.customIdp.id, policy.id);

    user = await createOneUser('correct-user-2', fixture.customIdp2.id);
    await new Promise((r) => setTimeout(r, 1000));

    const fetchUser = await getUser(fixture.domain.id, fixture.accessToken, user.id);
    expect(fetchUser).toBeDefined();
    expect(fetchUser.id).toEqual(user.id);
  });

  it('must not add user when password invalid', async () => {
    const policy = await createPasswordPolicy(fixture.domain.id, fixture.accessToken, {
      name: 'default-to-assign',
      minLength: 30,
    });

    await assignPasswordPolicyToIdp(fixture.domain.id, fixture.accessToken, fixture.customIdp.id, policy.id);

    await expect(async () => {
      await createOneUser('user-invalid-password', fixture.customIdp.id);
    }).rejects.toThrow();
  });

  it('must reset user password', async () => {
    const policy = await createPasswordPolicy(fixture.domain.id, fixture.accessToken, {
      name: 'default-to-assign',
      minLength: 5,
    });
    await assignPasswordPolicyToIdp(fixture.domain.id, fixture.accessToken, fixture.customIdp.id, policy.id);
    const userNewPassword = 'newpassTest^&*';
    user = await createOneUser('correct-user-pass-reset', fixture.customIdp.id);

    await waitFor(1000);
    await resetUserPassword(fixture.domain.id, fixture.accessToken, user.id, userNewPassword);
    await waitFor(1000);

    const clientId = fixture.application.settings.oauth.clientId;
    const user1TokenResponse = await loginUserNameAndPassword(clientId, user, userNewPassword, false, fixture.oidc, fixture.domain);
    expect(user1TokenResponse.headers['location']).toContain('callback?code=');
    await logoutUser(fixture.oidc.end_session_endpoint, user1TokenResponse);
  });

  it('must not reset user password when its incorrect', async () => {
    const policy = await createPasswordPolicy(fixture.domain.id, fixture.accessToken, {
      name: 'default-to-assign',
      minLength: 30,
    });
    await assignPasswordPolicyToIdp(fixture.domain.id, fixture.accessToken, fixture.customIdp.id, policy.id);
    const userNewPassword = 'newpassTest^&*';
    user = await createOneUser('incorrect-pass-reset', fixture.customIdp.id, '1234352342@#$@Tetssiokiokdsfsjji323j2i3j2i3j2');
    await waitFor(1000);

    await expect(async () => {
      const response = await resetUserPassword(fixture.domain.id, fixture.accessToken, user.id, userNewPassword);
      expect(response.raw.status).toEqual(400);
    }).rejects.toThrow();
  });

  async function createThreePasswordPolicies() {
    const oldPolicy = await createPasswordPolicy(fixture.domain.id, fixture.accessToken, {
      name: 'old-policy',
    });
    const newPolicy1 = await createPasswordPolicy(fixture.domain.id, fixture.accessToken, {
      name: 'new-1',
    });
    const newPolicy2 = await createPasswordPolicy(fixture.domain.id, fixture.accessToken, {
      name: 'new-2',
    });

    const passwordPolicies = await getAllPasswordPolicies(fixture.domain.id, fixture.accessToken);
    expect(passwordPolicies.length).toEqual(3);

    return { oldPolicy, newPolicy1, newPolicy2 };
  }

  async function createOneUser(username: string, idpId: string, password?: string): Promise<User> {
    return await createUser(fixture.domain.id, fixture.accessToken, {
      firstName: 'john',
      lastName: 'doe',
      email: `${username}@test.com`,
      username: username,
      password: password ? password : 'ZxcPrm7123!!',
      client: fixture.application.id,
      source: idpId,
      additionalInformation: {
        contract: '1234',
      },
      preRegistration: false,
    });
  }
});
