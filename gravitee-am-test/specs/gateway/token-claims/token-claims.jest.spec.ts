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
import { TokenClaimsFixture, setupTokenClaimsFixture } from './fixtures/token-claims-fixture';

setup(200000);

// Follows pattern: mcp-oauth2-resource.jest.spec.ts (token payload inspection)

const JWT_FORMAT = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;

function decodeJwtPayload(token: string): Record<string, unknown> {
  const parts = token.split('.');
  return JSON.parse(Buffer.from(parts[1], 'base64').toString());
}

describe('Token Claims and Duration', () => {
  describe('Access Token Duration (UC-AM63)', () => {
    let fixture: TokenClaimsFixture;
    const ACCESS_TOKEN_TTL = 60;

    beforeAll(async () => {
      fixture = await setupTokenClaimsFixture({
        accessTokenValiditySeconds: ACCESS_TOKEN_TTL,
      });
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should enforce custom access token duration ${'AM-2176'}`, async () => {
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
      // expires_in may be 1s less than configured TTL due to processing time
      expect(response.body.expires_in).toBeGreaterThanOrEqual(ACCESS_TOKEN_TTL - 1);
      expect(response.body.expires_in).toBeLessThanOrEqual(ACCESS_TOKEN_TTL);

      const payload = decodeJwtPayload(response.body.access_token);
      const tokenDuration = (payload.exp as number) - (payload.iat as number);
      expect(tokenDuration).toBeGreaterThanOrEqual(ACCESS_TOKEN_TTL - 1);
      expect(tokenDuration).toBeLessThanOrEqual(ACCESS_TOKEN_TTL);
    });
  });

  describe('ID Token Duration (UC-AM64)', () => {
    let fixture: TokenClaimsFixture;
    const ID_TOKEN_TTL = 120;

    beforeAll(async () => {
      fixture = await setupTokenClaimsFixture({
        idTokenValiditySeconds: ID_TOKEN_TTL,
      });
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should enforce custom ID token duration ${'AM-2174'}`, async () => {
      const response = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=password&username=${encodeURIComponent(fixture.user.username)}&password=${encodeURIComponent(fixture.user.password)}&scope=openid`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + getBase64BasicAuth(fixture.clientId, fixture.clientSecret),
        },
      ).expect(200);

      expect(response.body.id_token).toMatch(JWT_FORMAT);

      const payload = decodeJwtPayload(response.body.id_token);
      expect((payload.exp as number) - (payload.iat as number)).toEqual(ID_TOKEN_TTL);
    });
  });

  describe('Static Custom Claim on ID Token (UC-AM62)', () => {
    let fixture: TokenClaimsFixture;

    beforeAll(async () => {
      fixture = await setupTokenClaimsFixture({
        tokenCustomClaims: [
          {
            tokenType: 'ID_TOKEN',
            claimName: 'custom_static',
            claimValue: 'hello-world',
          },
        ],
      });
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should include static custom claim in ID token ${'AM-2178'}`, async () => {
      const response = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=password&username=${encodeURIComponent(fixture.user.username)}&password=${encodeURIComponent(fixture.user.password)}&scope=openid`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + getBase64BasicAuth(fixture.clientId, fixture.clientSecret),
        },
      ).expect(200);

      expect(response.body.id_token).toMatch(JWT_FORMAT);
      const payload = decodeJwtPayload(response.body.id_token);
      expect(payload.custom_static).toEqual('hello-world');
    });
  });

  describe('Dynamic (EL) Custom Claim on ID Token (UC-AM61)', () => {
    let fixture: TokenClaimsFixture;

    beforeAll(async () => {
      fixture = await setupTokenClaimsFixture({
        tokenCustomClaims: [
          {
            tokenType: 'ID_TOKEN',
            claimName: 'user_email',
            claimValue: "{#context.attributes['user']['email']}",
          },
        ],
      });
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should resolve dynamic EL claim in ID token ${'AM-2187'}`, async () => {
      const response = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=password&username=${encodeURIComponent(fixture.user.username)}&password=${encodeURIComponent(fixture.user.password)}&scope=openid`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + getBase64BasicAuth(fixture.clientId, fixture.clientSecret),
        },
      ).expect(200);

      expect(response.body.id_token).toMatch(JWT_FORMAT);
      const payload = decodeJwtPayload(response.body.id_token);
      expect(payload.user_email).toEqual(fixture.user.email);
    });
  });

  describe('Static Custom Claim on Access Token (UC-AM60)', () => {
    let fixture: TokenClaimsFixture;

    beforeAll(async () => {
      fixture = await setupTokenClaimsFixture({
        tokenCustomClaims: [
          {
            tokenType: 'ACCESS_TOKEN',
            claimName: 'custom_access',
            claimValue: 'static-value',
          },
        ],
      });
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should include static custom claim in access token ${'AM-2188'}`, async () => {
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
      const payload = decodeJwtPayload(response.body.access_token);
      expect(payload.custom_access).toEqual('static-value');
    });
  });

  describe('Dynamic (EL) Custom Claim on Access Token (UC-AM59)', () => {
    let fixture: TokenClaimsFixture;

    beforeAll(async () => {
      fixture = await setupTokenClaimsFixture({
        tokenCustomClaims: [
          {
            tokenType: 'ACCESS_TOKEN',
            claimName: 'user_email',
            claimValue: "{#context.attributes['user']['email']}",
          },
        ],
      });
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should resolve dynamic EL claim in access token ${'AM-2189'}`, async () => {
      const response = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=password&username=${encodeURIComponent(fixture.user.username)}&password=${encodeURIComponent(fixture.user.password)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + getBase64BasicAuth(fixture.clientId, fixture.clientSecret),
        },
      ).expect(200);

      expect(response.body.access_token).toMatch(JWT_FORMAT);
      const payload = decodeJwtPayload(response.body.access_token);
      expect(payload.user_email).toEqual(fixture.user.email);
    });
  });
});
