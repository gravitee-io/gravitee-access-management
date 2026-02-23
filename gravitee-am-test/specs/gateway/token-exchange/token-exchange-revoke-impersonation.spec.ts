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
import { setup } from '../../test-fixture';
import { setupTokenExchangeFixture, TokenExchangeFixture } from './fixtures/token-exchange-fixture';

setup(200000);

let subjectFixture: TokenExchangeFixture;

beforeAll(async () => {
  subjectFixture = await setupTokenExchangeFixture();
});

afterAll(async () => {
  if (subjectFixture) {
    await subjectFixture.cleanup();
  }
});

describe('Token Exchange revoke impersonation', () => {
  describe('Subject token revocation', () => {
    it('should revoke subject access token family recursively', async () => {
      const { obtainSubjectToken, introspectToken, revokeToken, exchangeToken } = subjectFixture;
      const { accessToken: rootToken } = await obtainSubjectToken();

      const childToken = await exchangeToken(rootToken, 'access_token');
      const grandChildToken = await exchangeToken(childToken, 'access_token');

      expect((await introspectToken(childToken)).active).toBe(true);
      expect((await introspectToken(grandChildToken)).active).toBe(true);

      await revokeToken(rootToken, 'access_token');

      expect((await introspectToken(rootToken)).active).toBe(false);
      expect((await introspectToken(childToken)).active).toBe(false);
      expect((await introspectToken(grandChildToken)).active).toBe(false);
    });

    it('should keep token outside revoked subject access token family active', async () => {
      const { obtainSubjectToken, introspectToken, revokeToken, exchangeToken } = subjectFixture;
      const { accessToken: rootToken } = await obtainSubjectToken();
      const childToken = await exchangeToken(rootToken, 'access_token');

      const { accessToken: outsiderToken } = await obtainSubjectToken();
      expect(outsiderToken).toBeDefined();
      expect((await introspectToken(outsiderToken)).active).toBe(true);

      await revokeToken(rootToken, 'access_token');

      expect((await introspectToken(rootToken)).active).toBe(false);
      expect((await introspectToken(childToken)).active).toBe(false);
      expect((await introspectToken(outsiderToken)).active).toBe(true);
    });

    it('should revoke descendants when intermediate subject token is revoked and keep ancestor active', async () => {
      const { obtainSubjectToken, introspectToken, revokeToken, exchangeToken } = subjectFixture;

      const { accessToken: tokenA } = await obtainSubjectToken();
      const tokenB = await exchangeToken(tokenA, 'access_token');
      const tokenC = await exchangeToken(tokenB, 'access_token');
      const { accessToken: outsiderToken } = await obtainSubjectToken();

      expect((await introspectToken(tokenA)).active).toBe(true);
      expect((await introspectToken(tokenB)).active).toBe(true);
      expect((await introspectToken(tokenC)).active).toBe(true);
      expect((await introspectToken(outsiderToken)).active).toBe(true);

      await revokeToken(tokenB, 'access_token');

      expect((await introspectToken(tokenA)).active).toBe(true);
      expect((await introspectToken(tokenB)).active).toBe(false);
      expect((await introspectToken(tokenC)).active).toBe(false);
      expect((await introspectToken(outsiderToken)).active).toBe(true);
    });

    it('should revoke subject refresh token family recursively', async () => {
      const { obtainSubjectToken, introspectToken, revokeToken, exchangeToken } = subjectFixture;
      const { refreshToken: rootRefreshToken } = await obtainSubjectToken();
      expect(rootRefreshToken).toBeDefined();

      const childToken = await exchangeToken(rootRefreshToken!, 'refresh_token');
      const grandChildToken = await exchangeToken(childToken, 'access_token');

      expect((await introspectToken(rootRefreshToken!)).active).toBe(true);
      expect((await introspectToken(childToken)).active).toBe(true);
      expect((await introspectToken(grandChildToken)).active).toBe(true);

      await revokeToken(rootRefreshToken!, 'refresh_token');

      expect((await introspectToken(rootRefreshToken!)).active).toBe(false);
      expect((await introspectToken(childToken)).active).toBe(false);
      expect((await introspectToken(grandChildToken)).active).toBe(false);
    });

    it('should keep token outside revoked subject refresh token family active', async () => {
      const { obtainSubjectToken, introspectToken, revokeToken, exchangeToken } = subjectFixture;
      const { refreshToken: rootRefreshToken } = await obtainSubjectToken();
      expect(rootRefreshToken).toBeDefined();

      const childToken = await exchangeToken(rootRefreshToken!, 'refresh_token');

      const { refreshToken: outsiderRefreshToken } = await obtainSubjectToken();
      expect(outsiderRefreshToken).toBeDefined();
      expect((await introspectToken(outsiderRefreshToken!)).active).toBe(true);

      await revokeToken(rootRefreshToken!, 'refresh_token');

      expect((await introspectToken(rootRefreshToken!)).active).toBe(false);
      expect((await introspectToken(childToken)).active).toBe(false);
      expect((await introspectToken(outsiderRefreshToken!)).active).toBe(true);
    });

    it('should revoke mixed subject family built from refresh root and access descendants', async () => {
      const { obtainSubjectToken, introspectToken, revokeToken, exchangeToken } = subjectFixture;
      const { accessToken: rootAccessToken, refreshToken: rootRefreshToken } = await obtainSubjectToken();
      expect(rootAccessToken).toBeDefined();
      expect(rootRefreshToken).toBeDefined();

      const level1AccessToken = await exchangeToken(rootRefreshToken!, 'refresh_token');
      const child1Token = await exchangeToken(level1AccessToken, 'access_token');
      const child2Token = await exchangeToken(level1AccessToken, 'access_token');
      const child3Token = await exchangeToken(level1AccessToken, 'access_token');

      expect((await introspectToken(rootRefreshToken!)).active).toBe(true);
      expect((await introspectToken(level1AccessToken)).active).toBe(true);
      expect((await introspectToken(child1Token)).active).toBe(true);
      expect((await introspectToken(child2Token)).active).toBe(true);
      expect((await introspectToken(child3Token)).active).toBe(true);

      await revokeToken(rootRefreshToken!, 'refresh_token');

      expect((await introspectToken(rootRefreshToken!)).active).toBe(false);
      expect((await introspectToken(level1AccessToken)).active).toBe(false);
      expect((await introspectToken(child1Token)).active).toBe(false);
      expect((await introspectToken(child2Token)).active).toBe(false);
      expect((await introspectToken(child3Token)).active).toBe(false);
      expect((await introspectToken(rootAccessToken)).active).toBe(true);
    });
  });
});
