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
import { getDomainFlows, updateDomainFlows } from '@management-commands/domain-management-commands';
import { waitForNextSync } from '@gateway-commands/monitoring-commands';
import { createUser, deleteUser, getAllUsers } from '@management-commands/user-management-commands';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { lookupFlowAndResetPolicies } from '@management-commands/flow-management-commands';
import { AccountLinkFixture, setupFixture } from './fixture/account-link-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let cookies: any;
let fixture: AccountLinkFixture;

// How this tests works:
// 1. Two domains - one acts as OIDC provider (domainOIDC), the second is for testing (domainAccLinking)
// 2. domainAccLinking has two apps - one with a hidden login form and OICD as a default idp to redirect directly to OIDC provider, the second is using the local idp provider (mongo/JDBC).

beforeAll(async () => {
  fixture = await setupFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Account Linking - local IDP and OIDC', () => {
  it('Should login and create double user - account linking not setup', async () => {
    const user1TokenResponse = await loginUserNameAndPassword(
      fixture.upstreamDomain.oidcApp.settings.oauth.clientId,
      fixture.downstreamDomain.user,
      fixture.downstreamDomain.user.password,
      false,
      fixture.upstreamDomain.oidc,
      fixture.upstreamDomain.domain,
      'https://test.com',
      'code',
      true,
    );
    expect(user1TokenResponse.headers['location']).toContain('test.com?code=');
    const allUsers = await getAllUsers(fixture.upstreamDomain.domain.id, fixture.accessToken);
    expect(allUsers.data.length).toBe(2);
    // Remove and recreate users
    await deleteUser(fixture.upstreamDomain.domain.id, fixture.accessToken, allUsers.data[0].id);
    await deleteUser(fixture.upstreamDomain.domain.id, fixture.accessToken, allUsers.data[1].id);
    await createUser(fixture.upstreamDomain.domain.id, fixture.accessToken, fixture.upstreamDomain.user);
    await waitForNextSync(fixture.upstreamDomain.domain.id);
  });

  it('Should login and create double user - account linking setup', async () => {
    const flows = await getDomainFlows(fixture.upstreamDomain.domain.id, fixture.accessToken);
    lookupFlowAndResetPolicies(flows, 'CONNECT', 'pre', [
      {
        name: 'Account Linking',
        policy: 'policy-am-account-linking',
        description: '',
        configuration: JSON.stringify({
          exitIfNoAccount: false,
          exitIfMultipleAccount: false,
          userAttributes: [{ name: 'email', value: 'test@test.fr' }],
        }),
        enabled: true,
        condition: '',
      },
    ]);
    await updateDomainFlows(fixture.upstreamDomain.domain.id, fixture.accessToken, flows);
    await waitForNextSync(fixture.upstreamDomain.domain.id);

    const user1TokenResponse = await loginUserNameAndPassword(
      fixture.upstreamDomain.oidcApp.settings.oauth.clientId,
      fixture.downstreamDomain.user,
      fixture.downstreamDomain.user.password,
      false,
      fixture.upstreamDomain.oidc,
      fixture.upstreamDomain.domain,
      'https://test.com',
      'code',
      true,
    );
    expect(user1TokenResponse.headers['location']).toContain('test.com?code=');
    cookies = user1TokenResponse.headers['set-cookie'];
    const allUsers = await getAllUsers(fixture.upstreamDomain.domain.id, fixture.accessToken);
    expect(allUsers.data.length).toBe(1);
    expect(allUsers.data[0].identities[0].providerId).toBe(fixture.upstreamDomain.oidcIdp.id);
  });

  it('Should login to second app with local idp', async () => {
    const authResponse = await performGet(
      fixture.upstreamDomain.oidc.authorization_endpoint,
      `?response_type=code&client_id=${fixture.upstreamDomain.localIdpApp.settings.oauth.clientId}&redirect_uri=https://test.com`,
      { Cookie: cookies },
    ).expect(302);
    expect(authResponse.headers['location']).toContain('test.com?code=');
  });

  it('Should execute brute force not allow to login with domain user', async () => {
    //Attempt fail login
    for (let i = 0; i < 5; i++) {
      const failResponse = await loginUserNameAndPassword(
        fixture.upstreamDomain.localIdpApp.settings.oauth.clientId,
        fixture.upstreamDomain.user,
        'Wrong123456Password!',
        false,
        fixture.upstreamDomain.oidc,
        fixture.upstreamDomain.domain,
        'https://test.com',
        'code',
        false,
      );
      expect(failResponse.headers['location']).toContain('error=login_failed');
    }

    // Check that the user is locked
    const allUsers = await getAllUsers(fixture.upstreamDomain.domain.id, fixture.accessToken);
    const lockedUser = allUsers.data.find((u) => u.username === fixture.upstreamDomain.user.username);
    expect(lockedUser.accountNonLocked).toBe(false);

    // Try to log in with OIDC
    const user1TokenResponse = await loginUserNameAndPassword(
      fixture.upstreamDomain.oidcApp.settings.oauth.clientId,
      fixture.downstreamDomain.user,
      fixture.downstreamDomain.user.password,
      false,
      fixture.upstreamDomain.oidc,
      fixture.upstreamDomain.domain,
      'https://test.com',
      'code',
      true,
    );
    expect(user1TokenResponse.headers['location']).toContain('test.com?code=');

    //Try to log in with OIDC (user still locked)
    const lockedAttempt = await loginUserNameAndPassword(
      fixture.upstreamDomain.localIdpApp.settings.oauth.clientId,
      fixture.upstreamDomain.user,
      fixture.upstreamDomain.user.password,
      false,
      fixture.upstreamDomain.oidc,
      fixture.upstreamDomain.domain,
      'https://test.com',
      'code',
      false,
    );
    expect(lockedAttempt.headers['location']).toContain('error=login_failed');
  });
});
