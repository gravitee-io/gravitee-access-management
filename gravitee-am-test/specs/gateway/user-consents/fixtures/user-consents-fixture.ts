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
import { expect } from '@jest/globals';
import { extractXsrfTokenAndActionResponse, performFormPost, performGet } from '@gateway-commands/oauth-oidc-commands';
import { getUser } from '@management-commands/user-management-commands';
import { createUserWithToken, SelfAccountFixture, setupFixture, tokenFor } from '../../self-account/fixture/self-account-fixture';

export const APPROVED_SCOPES = ['openid', 'email'];

export interface ConsentingUser {
  id: string;
  externalId: string;
  source: string;
  username: string;
  password: string;
}

export interface UserConsentsFixture extends SelfAccountFixture {
  consentingUser: ConsentingUser;
  userToken: string;
  gatewayUsersConsentsUrl: (path?: string) => string;
  accountConsentsUrl: (path?: string) => string;
}

const SETTINGS = {
  selfServiceAccountManagementSettings: {
    enabled: true,
    resetPassword: { oldPasswordRequired: false, tokenAge: 0 },
  },
};

const approveConsent = async (fixture: SelfAccountFixture, user: { username: string; password: string }) => {
  const clientId = fixture.application.settings.oauth.clientId;
  const params = `?response_type=code&client_id=${clientId}&redirect_uri=https://callback&scope=${APPROVED_SCOPES.join('%20')}`;

  const start = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);
  const login = await extractXsrfTokenAndActionResponse(start);
  const postLogin = await performFormPost(
    login.action,
    '',
    { 'X-XSRF-TOKEN': login.token, username: user.username, password: user.password, client_id: clientId },
    { Cookie: login.headers['set-cookie'], 'Content-type': 'application/x-www-form-urlencoded' },
  ).expect(302);

  const consentLocation = await performGet(postLogin.headers['location'], '', { Cookie: postLogin.headers['set-cookie'] }).expect(302);
  expect(consentLocation.headers['location']).toContain('/oauth/consent');

  const consent = await extractXsrfTokenAndActionResponse(consentLocation);
  const scopeApprovals = Object.fromEntries(APPROVED_SCOPES.map((scope) => [`scope.${scope}`, true]));
  await performFormPost(
    consent.action,
    '',
    { ...scopeApprovals, user_oauth_approval: true, 'X-XSRF-TOKEN': consent.token },
    { Cookie: consent.headers['set-cookie'], 'Content-type': 'application/x-www-form-urlencoded' },
  ).expect(302);
};

export const setupUserConsentsFixture = async (): Promise<UserConsentsFixture> => {
  const base = await setupFixture(SETTINGS, 'user-consents');
  try {
    const { user } = await createUserWithToken(base, 'consentUser');
    await approveConsent(base, user);
    const stored = await getUser(base.domain.id, base.accessToken, user.id);
    const userToken = await tokenFor(base, user);

    return {
      ...base,
      consentingUser: {
        id: stored.id,
        externalId: stored.externalId,
        source: stored.sourceId,
        username: user.username,
        password: user.password,
      },
      userToken,
      gatewayUsersConsentsUrl: (path = '') => `${process.env.AM_GATEWAY_URL}/${base.domain.hrid}/users/${stored.id}/consents${path}`,
      accountConsentsUrl: (path = '') => `${process.env.AM_GATEWAY_URL}/${base.domain.hrid}/account/api/consent${path}`,
    };
  } catch (error) {
    await base.cleanUp();
    throw error;
  }
};
