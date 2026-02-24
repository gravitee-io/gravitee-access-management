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
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { setup } from '../../test-fixture';
import { setupTokenExchangeFixture, TokenExchangeFixture, TOKEN_EXCHANGE_TEST } from './fixtures/token-exchange-fixture';

setup(200000);

let delegationFixture: TokenExchangeFixture;

beforeAll(async () => {
  delegationFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'token-exchange-revoke-delegation',
    domainDescription: 'Token exchange revoke with delegation',
    clientName: 'token-exchange-revoke-delegation-client',
    allowImpersonation: true,
    allowDelegation: true,
    allowedActorTokenTypes: [...TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES, 'urn:ietf:params:oauth:token-type:refresh_token'],
  });
});

afterAll(async () => {
  if (delegationFixture) {
    await delegationFixture.cleanup();
  }
});

describe('Token Exchange revoke delegation', () => {
  describe('Delegation token revocation', () => {
    const assertTokenExchangeRejected = async (
      subjectToken: string,
      subjectTokenType: 'access_token' | 'refresh_token',
      actorToken?: string,
      actorTokenType?: 'access_token' | 'refresh_token',
    ) => {
      const { oidc, basicAuth } = delegationFixture;
      const actorParams = actorToken && actorTokenType
        ? `&actor_token=${actorToken}&actor_token_type=urn:ietf:params:oauth:token-type:${actorTokenType}`
        : '';

      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:${subjectTokenType}` +
        actorParams,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
      ).expect(400);

      expect(response.body.error).toBe('invalid_grant');
      expect(response.body.error_description).toBeDefined();
    };

    it('should reject token exchange when subject access token is revoked', async () => {
      const { obtainSubjectToken, introspectToken, revokeToken } = delegationFixture;
      const { accessToken: subjectAccessToken } = await obtainSubjectToken();

      await revokeToken(subjectAccessToken, 'access_token');
      expect((await introspectToken(subjectAccessToken)).active).toBe(false);

      await assertTokenExchangeRejected(subjectAccessToken, 'access_token');
    });

    it('should reject token exchange when subject refresh token is revoked', async () => {
      const { obtainSubjectToken, introspectToken, revokeToken } = delegationFixture;
      const { refreshToken: subjectRefreshToken } = await obtainSubjectToken();
      expect(subjectRefreshToken).toBeDefined();

      await revokeToken(subjectRefreshToken!, 'refresh_token');
      expect((await introspectToken(subjectRefreshToken!)).active).toBe(false);

      await assertTokenExchangeRejected(subjectRefreshToken!, 'refresh_token');
    });

    it('should reject token exchange when actor access token is revoked', async () => {
      const { obtainSubjectToken, obtainActorToken, introspectToken, revokeToken } = delegationFixture;
      const { accessToken: subjectAccessToken } = await obtainSubjectToken();
      const { accessToken: actorAccessToken } = await obtainActorToken();

      await revokeToken(actorAccessToken, 'access_token');
      expect((await introspectToken(actorAccessToken)).active).toBe(false);

      await assertTokenExchangeRejected(subjectAccessToken, 'access_token', actorAccessToken, 'access_token');
    });

    it('should reject token exchange when actor refresh token is revoked', async () => {
      const { obtainSubjectToken, obtainActorToken, introspectToken, revokeToken } = delegationFixture;
      const { accessToken: subjectAccessToken } = await obtainSubjectToken();
      const { refreshToken: actorRefreshToken } = await obtainActorToken();
      expect(actorRefreshToken).toBeDefined();

      await revokeToken(actorRefreshToken!, 'refresh_token');
      expect((await introspectToken(actorRefreshToken!)).active).toBe(false);

      await assertTokenExchangeRejected(subjectAccessToken, 'access_token', actorRefreshToken!, 'refresh_token');
    });

    it('should revoke delegated descendants when actor token is revoked', async () => {
      const { obtainSubjectToken, obtainActorToken, introspectToken, revokeToken, exchangeToken } = delegationFixture;

      const { accessToken: subjectToken } = await obtainSubjectToken();
      const { accessToken: actorToken } = await obtainActorToken();
      expect(subjectToken).toBeDefined();
      expect(actorToken).toBeDefined();

      const delegatedToken = await exchangeToken(subjectToken, 'access_token', actorToken);
      const delegatedChildToken = await exchangeToken(delegatedToken, 'access_token');
      const { accessToken: outsiderToken } = await obtainSubjectToken();

      expect((await introspectToken(actorToken)).active).toBe(true);
      expect((await introspectToken(delegatedToken)).active).toBe(true);
      expect((await introspectToken(delegatedChildToken)).active).toBe(true);
      expect((await introspectToken(outsiderToken)).active).toBe(true);

      await revokeToken(actorToken, 'access_token');

      expect((await introspectToken(subjectToken)).active).toBe(true);
      expect((await introspectToken(actorToken)).active).toBe(false);
      expect((await introspectToken(delegatedToken)).active).toBe(false);
      expect((await introspectToken(delegatedChildToken)).active).toBe(false);
      expect((await introspectToken(outsiderToken)).active).toBe(true);
    });

    it('should keep token outside revoked delegated actor family active', async () => {
      const { obtainSubjectToken, obtainActorToken, introspectToken, revokeToken, exchangeToken } = delegationFixture;

      const { accessToken: subjectToken } = await obtainSubjectToken();
      const { accessToken: actorToken } = await obtainActorToken();
      const delegatedToken = await exchangeToken(subjectToken, 'access_token', actorToken);

      const { accessToken: outsiderSubjectToken } = await obtainSubjectToken();
      const { accessToken: outsiderActorToken } = await obtainActorToken();
      const outsiderDelegatedToken = await exchangeToken(outsiderSubjectToken, 'access_token', outsiderActorToken);

      expect((await introspectToken(delegatedToken)).active).toBe(true);
      expect((await introspectToken(outsiderDelegatedToken)).active).toBe(true);

      await revokeToken(actorToken, 'access_token');

      expect((await introspectToken(delegatedToken)).active).toBe(false);
      expect((await introspectToken(outsiderDelegatedToken)).active).toBe(true);
      expect((await introspectToken(outsiderActorToken)).active).toBe(true);
      expect((await introspectToken(outsiderSubjectToken)).active).toBe(true);
    });

    it('should revoke delegated subtree across mixed subject and actor edges', async () => {
      const { obtainSubjectToken, obtainActorToken, introspectToken, revokeToken, exchangeToken } = delegationFixture;

      const { accessToken: rootSubjectToken } = await obtainSubjectToken();
      const { accessToken: rootActorToken } = await obtainActorToken();
      const level1DelegatedToken = await exchangeToken(rootSubjectToken, 'access_token', rootActorToken);

      const { accessToken: level2ActorToken } = await obtainActorToken();
      const level2DelegatedToken = await exchangeToken(level1DelegatedToken, 'access_token', level2ActorToken);
      const level3SubjectOnlyToken = await exchangeToken(level2DelegatedToken, 'access_token');

      const { accessToken: outsiderToken } = await obtainSubjectToken();

      expect((await introspectToken(level1DelegatedToken)).active).toBe(true);
      expect((await introspectToken(level2DelegatedToken)).active).toBe(true);
      expect((await introspectToken(level3SubjectOnlyToken)).active).toBe(true);
      expect((await introspectToken(level2ActorToken)).active).toBe(true);
      expect((await introspectToken(outsiderToken)).active).toBe(true);

      await revokeToken(rootActorToken, 'access_token');

      expect((await introspectToken(rootSubjectToken)).active).toBe(true);
      expect((await introspectToken(rootActorToken)).active).toBe(false);
      expect((await introspectToken(level1DelegatedToken)).active).toBe(false);
      expect((await introspectToken(level2DelegatedToken)).active).toBe(false);
      expect((await introspectToken(level3SubjectOnlyToken)).active).toBe(false);
      expect((await introspectToken(level2ActorToken)).active).toBe(true);
      expect((await introspectToken(outsiderToken)).active).toBe(true);
    });

    it('should revoke delegated chain from refresh subject when actor is revoked', async () => {
      const { obtainSubjectToken, obtainActorToken, introspectToken, revokeToken, exchangeToken } = delegationFixture;

      const { refreshToken: rootRefreshToken } = await obtainSubjectToken();
      const { accessToken: actorToken } = await obtainActorToken();
      expect(rootRefreshToken).toBeDefined();
      expect(actorToken).toBeDefined();

      const delegatedToken = await exchangeToken(rootRefreshToken!, 'refresh_token', actorToken);
      const delegatedChildToken = await exchangeToken(delegatedToken, 'access_token');
      const { accessToken: outsiderToken } = await obtainSubjectToken();

      expect((await introspectToken(rootRefreshToken!)).active).toBe(true);
      expect((await introspectToken(actorToken)).active).toBe(true);
      expect((await introspectToken(delegatedToken)).active).toBe(true);
      expect((await introspectToken(delegatedChildToken)).active).toBe(true);
      expect((await introspectToken(outsiderToken)).active).toBe(true);

      await revokeToken(actorToken, 'access_token');

      expect((await introspectToken(rootRefreshToken!)).active).toBe(true);
      expect((await introspectToken(actorToken)).active).toBe(false);
      expect((await introspectToken(delegatedToken)).active).toBe(false);
      expect((await introspectToken(delegatedChildToken)).active).toBe(false);
      expect((await introspectToken(outsiderToken)).active).toBe(true);
    });
  });
});
