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
import { parseJwt } from '@api-fixtures/jwt';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { uniqueName } from '@utils-commands/misc';
import { TokenClaim } from '@management-models/TokenClaim';
import { setup } from '../../test-fixture';
import { LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS, LIGHTWEIGHT_ID_TOKEN_CLAIMS } from '../token-claims/fixtures/token-identity-fixture';
import {
  ACCESS_TOKEN_TYPE,
  ID_TOKEN_TYPE,
  REFRESH_TOKEN_TYPE,
  setupTokenExchangeFixture,
  TokenExchangeFixture,
  TOKEN_EXCHANGE_TEST,
} from './fixtures/token-exchange-fixture';

setup(200000);

const SUBJECT_CLAIMS = "#context.attributes['token_exchange']['subject']['subject_token_claims']";
const ACTOR_CLAIMS = "#context.attributes['token_exchange']['actor']['actor_token_claims']";
const SUBJECT_SCOPE = 'openid%20profile%20offline_access';

const TOKEN_CUSTOM_CLAIMS: TokenClaim[] = [
  { tokenType: 'ACCESS_TOKEN', claimName: 'tenant_hidden', claimValue: 'acme-hidden' },
  { tokenType: 'ACCESS_TOKEN', claimName: 'tenant_allowed', claimValue: 'acme-allowed' },
  { tokenType: 'ACCESS_TOKEN', claimName: 'tx_subject_hidden', claimValue: `{${SUBJECT_CLAIMS}['tenant_hidden']}` },
  { tokenType: 'ACCESS_TOKEN', claimName: 'tx_subject_allowed', claimValue: `{${SUBJECT_CLAIMS}['tenant_allowed']}` },
  { tokenType: 'ACCESS_TOKEN', claimName: 'tx_subject_gis', claimValue: `{${SUBJECT_CLAIMS}['gis']}` },
  { tokenType: 'ACCESS_TOKEN', claimName: 'tx_actor_allowed', claimValue: `{${ACTOR_CLAIMS}['tenant_allowed']}` },
  { tokenType: 'ACCESS_TOKEN', claimName: 'tx_actor_jti', claimValue: `{${ACTOR_CLAIMS}['jti']}` },
  { tokenType: 'ID_TOKEN', claimName: 'id_tenant_hidden', claimValue: 'acme-id-hidden' },
  { tokenType: 'ID_TOKEN', claimName: 'id_tenant_allowed', claimValue: 'acme-id-allowed' },
];

// tx_subject_hidden is allowlisted, so its absence from an exchanged token means EL resolved nothing
const LIGHTWEIGHT_JWT_SETTINGS = {
  enabled: true,
  allowedAccessTokenClaims: ['tenant_allowed', 'tx_subject_hidden', 'tx_subject_allowed', 'tx_subject_gis', 'tx_actor_allowed'],
  allowedIdTokenClaims: ['id_tenant_allowed'],
};

const FIXTURE_CONFIG = {
  allowImpersonation: true,
  allowDelegation: true,
  allowedActorTokenTypes: [...TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES, REFRESH_TOKEN_TYPE],
  maxDelegationDepth: 3,
  tokenCustomClaims: TOKEN_CUSTOM_CLAIMS,
};

let lightweight: TokenExchangeFixture;
let full: TokenExchangeFixture;
let fullAppBasicAuth: string;

beforeAll(async () => {
  lightweight = await setupTokenExchangeFixture({
    ...FIXTURE_CONFIG,
    domainNamePrefix: 'token-exchange-lightweight',
    domainDescription: 'Token exchange with Lightweight JWT',
    clientName: 'token-exchange-lightweight-client',
    lightweightJwtSettings: LIGHTWEIGHT_JWT_SETTINGS,
  });

  full = await setupTokenExchangeFixture({
    ...FIXTURE_CONFIG,
    domainNamePrefix: 'token-exchange-lightweight-off',
    domainDescription: 'Token exchange with Lightweight JWT disabled',
    clientName: 'token-exchange-lightweight-off-client',
  });

  const fullApp = await waitForSyncAfter(lightweight.domain.id, () =>
    createTestApp(uniqueName('token-exchange-full-client', true), lightweight.domain, lightweight.accessToken, 'WEB', {
      settings: {
        oauth: {
          redirectUris: [TOKEN_EXCHANGE_TEST.REDIRECT_URI],
          grantTypes: TOKEN_EXCHANGE_TEST.DEFAULT_GRANT_TYPES,
          scopeSettings: TOKEN_EXCHANGE_TEST.DEFAULT_SCOPES,
          tokenCustomClaims: TOKEN_CUSTOM_CLAIMS,
        },
      },
      identityProviders: new Set([{ identity: lightweight.defaultIdp.id, priority: 0 }]),
    }),
  );
  fullAppBasicAuth = applicationBase64Token(fullApp);
});

afterAll(async () => {
  await lightweight?.cleanup();
  await full?.cleanup();
});

const claimNames = (token: string): string[] => Object.keys(parseJwt(token).payload).sort();

const payload = (token: string): Record<string, any> => parseJwt(token).payload;

const obtainFullAppTokens = async () => {
  const response = await performPost(
    lightweight.oidc.token_endpoint,
    '',
    `grant_type=password&username=${lightweight.user.username}&password=${TOKEN_EXCHANGE_TEST.USER_PASSWORD}&scope=${SUBJECT_SCOPE}`,
    {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${fullAppBasicAuth}`,
    },
  ).expect(200);
  return { accessToken: response.body.access_token as string };
};

const expectRejected = async (response: { status: number; body: any }) => {
  expect(response.status).toBe(400);
  expect(response.body.error).toBe('invalid_request');
  expect(response.body.error_description).toBeDefined();
};

describe('Token Exchange - Lightweight JWT', () => {
  describe('consuming lightweight tokens', () => {
    it('should accept a lightweight access token as subject token', async () => {
      const { accessToken } = await lightweight.obtainSubjectToken();

      const exchanged = await lightweight.exchangeToken(accessToken, 'access_token');

      expect(payload(exchanged).sub).toEqual(payload(accessToken).sub);
    });

    it('should accept a lightweight refresh token as subject token', async () => {
      const { accessToken, refreshToken } = await lightweight.obtainSubjectToken();

      const exchanged = await lightweight.exchangeToken(refreshToken!, 'refresh_token');

      expect(payload(exchanged).sub).toEqual(payload(accessToken).sub);
    });

    it('should accept a lightweight access token as actor token', async () => {
      const { accessToken: subjectToken } = await lightweight.obtainSubjectToken();
      const { accessToken: actorToken } = await lightweight.obtainActorToken();

      const exchanged = await lightweight.exchangeToken(subjectToken, 'access_token', actorToken);

      expect(payload(exchanged).act).toMatchObject({ sub: payload(actorToken).sub });
    });

    it('should accept a lightweight refresh token as actor token', async () => {
      const { accessToken: subjectToken } = await lightweight.obtainSubjectToken();
      const { accessToken: actorAccessToken, refreshToken: actorRefreshToken } = await lightweight.obtainActorToken();

      const response = await lightweight.requestExchange({
        subjectToken,
        subjectTokenType: ACCESS_TOKEN_TYPE,
        actorToken: actorRefreshToken,
        actorTokenType: REFRESH_TOKEN_TYPE,
      });

      expect(response.status).toBe(200);
      expect(payload(response.body.access_token).act).toMatchObject({ sub: payload(actorAccessToken).sub });
    });

    it('should accept a lightweight ID token as subject token', async () => {
      const { idToken } = await lightweight.obtainSubjectToken();

      const response = await lightweight.requestExchange({ subjectToken: idToken!, subjectTokenType: ID_TOKEN_TYPE });

      expect(response.status).toBe(200);
      expect(payload(response.body.access_token).sub).toEqual(payload(idToken!).sub);
    });
  });

  describe('revoked lightweight tokens', () => {
    it('should reject a revoked lightweight access token used as subject token', async () => {
      const { accessToken } = await lightweight.obtainSubjectToken();
      await lightweight.revokeToken(accessToken, 'access_token');

      await expectRejected(await lightweight.requestExchange({ subjectToken: accessToken, subjectTokenType: ACCESS_TOKEN_TYPE }));
    });

    it('should reject a revoked lightweight refresh token used as subject token', async () => {
      const { refreshToken } = await lightweight.obtainSubjectToken();
      await lightweight.revokeToken(refreshToken!, 'refresh_token');

      await expectRejected(await lightweight.requestExchange({ subjectToken: refreshToken!, subjectTokenType: REFRESH_TOKEN_TYPE }));
    });

    it('should reject a revoked lightweight access token used as actor token', async () => {
      const { accessToken: subjectToken } = await lightweight.obtainSubjectToken();
      const { accessToken: actorToken } = await lightweight.obtainActorToken();
      await lightweight.revokeToken(actorToken, 'access_token');

      await expectRejected(
        await lightweight.requestExchange({
          subjectToken,
          subjectTokenType: ACCESS_TOKEN_TYPE,
          actorToken,
          actorTokenType: ACCESS_TOKEN_TYPE,
        }),
      );
    });

    it('should reject a revoked lightweight refresh token used as actor token', async () => {
      const { accessToken: subjectToken } = await lightweight.obtainSubjectToken();
      const { refreshToken: actorToken } = await lightweight.obtainActorToken();
      await lightweight.revokeToken(actorToken!, 'refresh_token');

      await expectRejected(
        await lightweight.requestExchange({
          subjectToken,
          subjectTokenType: ACCESS_TOKEN_TYPE,
          actorToken,
          actorTokenType: REFRESH_TOKEN_TYPE,
        }),
      );
    });

    it('should revoke a token exchanged from a lightweight subject token when the subject token is revoked', async () => {
      const { accessToken: subjectToken } = await lightweight.obtainSubjectToken();
      const exchanged = await lightweight.exchangeToken(subjectToken, 'access_token');
      expect((await lightweight.introspectToken(exchanged)).active).toBe(true);

      await lightweight.revokeToken(subjectToken, 'access_token');

      expect((await lightweight.introspectToken(exchanged)).active).toBe(false);
    });

    it('should revoke a token delegated to a lightweight actor token when the actor token is revoked', async () => {
      const { accessToken: subjectToken } = await lightweight.obtainSubjectToken();
      const { accessToken: actorToken } = await lightweight.obtainActorToken();
      const exchanged = await lightweight.exchangeToken(subjectToken, 'access_token', actorToken);
      expect((await lightweight.introspectToken(exchanged)).active).toBe(true);

      await lightweight.revokeToken(actorToken, 'access_token');

      expect((await lightweight.introspectToken(exchanged)).active).toBe(false);
    });
  });

  describe('issued tokens', () => {
    it('should issue a lightweight access token on impersonation', async () => {
      const { accessToken } = await lightweight.obtainSubjectToken();

      const exchanged = await lightweight.exchangeToken(accessToken, 'access_token');

      expect(claimNames(exchanged)).toEqual(
        [...LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS, 'tenant_allowed', 'tx_subject_allowed', 'tx_subject_gis'].sort(),
      );
    });

    it('should issue a lightweight access token on delegation, keeping act', async () => {
      const { accessToken: subjectToken } = await lightweight.obtainSubjectToken();
      const { accessToken: actorToken } = await lightweight.obtainActorToken();

      const exchanged = await lightweight.exchangeToken(subjectToken, 'access_token', actorToken);

      expect(claimNames(exchanged)).toEqual(
        [...LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS, 'act', 'tenant_allowed', 'tx_subject_allowed', 'tx_subject_gis', 'tx_actor_allowed'].sort(),
      );
    });

    it('should issue a lightweight ID token when an ID token is requested', async () => {
      const { accessToken } = await lightweight.obtainSubjectToken();

      const response = await lightweight.requestExchange({
        subjectToken: accessToken,
        subjectTokenType: ACCESS_TOKEN_TYPE,
        requestedTokenType: ID_TOKEN_TYPE,
      });

      expect(response.status).toBe(200);
      expect(response.body.issued_token_type).toBe(ID_TOKEN_TYPE);
      expect(claimNames(response.body.access_token)).toEqual([...LIGHTWEIGHT_ID_TOKEN_CLAIMS, 'id_tenant_allowed'].sort());
    });
  });

  describe('expression language on subject and actor token claims', () => {
    it('should expose only reserved and allowlisted subject token claims to EL', async () => {
      const { accessToken } = await lightweight.obtainSubjectToken();

      const exchanged = payload(await lightweight.exchangeToken(accessToken, 'access_token'));

      expect(exchanged).not.toHaveProperty('tx_subject_hidden');
      expect(exchanged.tx_subject_allowed).toEqual('acme-allowed');
      expect(exchanged.tx_subject_gis).toEqual(payload(accessToken).gis);
    });

    it('should expose only reserved and allowlisted actor token claims to EL', async () => {
      const { accessToken: subjectToken } = await lightweight.obtainSubjectToken();
      const { accessToken: actorToken } = await lightweight.obtainActorToken();

      const exchanged = payload(await lightweight.exchangeToken(subjectToken, 'access_token', actorToken));

      expect(exchanged.tx_actor_allowed).toEqual('acme-allowed');
      expect(exchanged).not.toHaveProperty('tx_actor_jti');
    });
  });

  describe('introspection and UserInfo of an exchanged token', () => {
    it('should return the withheld claims when introspecting an exchanged lightweight token', async () => {
      const { accessToken } = await lightweight.obtainSubjectToken();
      const exchanged = await lightweight.exchangeToken(accessToken, 'access_token');

      const introspection = await lightweight.introspectToken(exchanged);

      expect(introspection.active).toBe(true);
      expect(introspection.tenant_hidden).toEqual('acme-hidden');
    });

    it('should return the scope-bound profile from UserInfo for an exchanged lightweight token', async () => {
      const { accessToken } = await lightweight.obtainSubjectToken();
      const exchanged = await lightweight.exchangeToken(accessToken, 'access_token');

      const userinfo = await lightweight.userinfo(exchanged);

      expect(userinfo.sub).toEqual(payload(exchanged).sub);
      expect(userinfo.given_name).toEqual(lightweight.user.firstName);
      expect(userinfo.family_name).toEqual(lightweight.user.lastName);
    });
  });

  describe('mixed Lightweight JWT settings in one domain', () => {
    it('should issue a lightweight token when a lightweight application exchanges a full token', async () => {
      const { accessToken } = await obtainFullAppTokens();
      expect(payload(accessToken).tenant_hidden).toEqual('acme-hidden');

      const exchanged = payload(await lightweight.exchangeToken(accessToken, 'access_token'));

      expect(exchanged).not.toHaveProperty('tenant_hidden');
      expect(exchanged.tx_subject_hidden).toEqual('acme-hidden');
    });

    it('should issue a full token when an application without Lightweight JWT exchanges a lightweight token', async () => {
      const { accessToken } = await lightweight.obtainSubjectToken();

      const response = await lightweight.requestExchange({
        subjectToken: accessToken,
        subjectTokenType: ACCESS_TOKEN_TYPE,
        basicAuth: fullAppBasicAuth,
      });

      expect(response.status).toBe(200);
      const exchanged = payload(response.body.access_token);
      expect(exchanged.tenant_hidden).toEqual('acme-hidden');
      expect(exchanged).not.toHaveProperty('tx_subject_hidden');
    });
  });

  describe('Lightweight JWT disabled', () => {
    it('should keep custom claims and full subject token claims in EL', async () => {
      const { accessToken: subjectToken } = await full.obtainSubjectToken();
      const { accessToken: actorToken } = await full.obtainActorToken();

      const exchanged = payload(await full.exchangeToken(subjectToken, 'access_token', actorToken));

      expect(exchanged.tenant_hidden).toEqual('acme-hidden');
      expect(exchanged.tx_subject_hidden).toEqual('acme-hidden');
      expect(exchanged.tx_actor_jti).toEqual(payload(actorToken).jti);
    });
  });
});
