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
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { jira } from '@specs-utils/jira';
import { setup } from '../../test-fixture';
import { TokenAuthFixture, setupTokenAuthFixture } from './fixtures/token-auth-fixture';
import jwt from 'jsonwebtoken';
import crypto from 'crypto'; // Used for client_secret_jwt assertion JTI

setup(200000);

const JWT_FORMAT = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;

describe('Token Authentication Methods', () => {
  describe('client_secret_basic', () => {
    let fixture: TokenAuthFixture;

    beforeAll(async () => {
      fixture = await setupTokenAuthFixture({
        tokenEndpointAuthMethod: 'client_secret_basic',
      });
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should obtain token using client_secret_basic ${'AM-2225'}`, async () => {
      const response = await performPost(
        fixture.oidc.token_endpoint,
        '',
        'grant_type=client_credentials',
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + getBase64BasicAuth(fixture.clientId, fixture.clientSecret),
        },
      ).expect(200);

      expect(response.body.access_token).toMatch(JWT_FORMAT);
      expect(response.body.token_type).toEqual('bearer');
    });

    it(jira`should reject client_secret_post when basic is configured ${'AM-2225'}`, async () => {
      await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=client_credentials&client_id=${encodeURIComponent(fixture.clientId)}&client_secret=${encodeURIComponent(fixture.clientSecret)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
        },
      ).expect(401);
    });
  });

  describe('client_secret_post', () => {
    let fixture: TokenAuthFixture;

    beforeAll(async () => {
      fixture = await setupTokenAuthFixture({
        tokenEndpointAuthMethod: 'client_secret_post',
      });
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should obtain token using client_secret_post ${'AM-2236'}`, async () => {
      const response = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=client_credentials&client_id=${encodeURIComponent(fixture.clientId)}&client_secret=${encodeURIComponent(fixture.clientSecret)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
        },
      ).expect(200);

      expect(response.body.access_token).toMatch(JWT_FORMAT);
      expect(response.body.token_type).toEqual('bearer');
    });
  });

  describe('client_secret_jwt', () => {
    let fixture: TokenAuthFixture;

    function createClientAssertionJwt(clientId: string, clientSecret: string, audience: string): string {
      const now = Math.floor(Date.now() / 1000);
      return jwt.sign(
        {
          iss: clientId,
          sub: clientId,
          aud: audience,
          jti: crypto.randomUUID(),
          iat: now,
          exp: now + 300,
        },
        clientSecret,
        { algorithm: 'HS256' },
      );
    }

    beforeAll(async () => {
      fixture = await setupTokenAuthFixture({
        tokenEndpointAuthMethod: 'client_secret_jwt',
      });
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should obtain token using client_secret_jwt ${'AM-2234'}`, async () => {
      const assertion = createClientAssertionJwt(fixture.clientId, fixture.clientSecret, fixture.oidc.token_endpoint);

      const response = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=client_credentials&client_assertion_type=${encodeURIComponent('urn:ietf:params:oauth:client-assertion-type:jwt-bearer')}&client_assertion=${encodeURIComponent(assertion)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
        },
      ).expect(200);

      expect(response.body.access_token).toMatch(JWT_FORMAT);
      expect(response.body.token_type).toEqual('bearer');
    });
  });

  describe('Refresh Token', () => {
    let fixture: TokenAuthFixture;

    beforeAll(async () => {
      fixture = await setupTokenAuthFixture({
        tokenEndpointAuthMethod: 'client_secret_basic',
        grantTypes: ['password', 'refresh_token'],
      });
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should obtain and use refresh token ${'AM-2228'}`, async () => {
      // Step 1: Get access_token + refresh_token via password grant
      const tokenResponse = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=password&username=${encodeURIComponent(fixture.user.username)}&password=${encodeURIComponent(fixture.user.password)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + getBase64BasicAuth(fixture.clientId, fixture.clientSecret),
        },
      ).expect(200);

      expect(tokenResponse.body.access_token).toMatch(JWT_FORMAT);
      expect(tokenResponse.body.refresh_token).toMatch(/^[A-Za-z0-9_-]+/);

      const firstAccessToken = tokenResponse.body.access_token;
      const refreshToken = tokenResponse.body.refresh_token;

      // Step 2: Use refresh_token to get new access_token
      const refreshResponse = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=refresh_token&refresh_token=${encodeURIComponent(refreshToken)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + getBase64BasicAuth(fixture.clientId, fixture.clientSecret),
        },
      ).expect(200);

      expect(refreshResponse.body.access_token).toMatch(JWT_FORMAT);
      // New access token should be different from the first one
      expect(refreshResponse.body.access_token).not.toEqual(firstAccessToken);
    });
  });
});
