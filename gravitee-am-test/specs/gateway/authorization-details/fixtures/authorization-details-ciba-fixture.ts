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
import * as jose from 'jose';
import crypto from 'crypto';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { patchApplication } from '@management-commands/application-management-commands';
import { createAuthorizationDetailType } from '@management-commands/authorization-detail-type-management-commands';
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { performPatch, performPost } from '@gateway-commands/oauth-oidc-commands';
import { createCibaUpstreamOpMock } from '@utils-commands/ciba-upstream-op-mock';
import { CIMD_PRIVATE_KEY_JWT_KID, cimdPrivateKeyJwtPrivateJwk } from '@api-fixtures/cimd-private-key-jwt';
import { AuthorizationDetailType } from '@management-models/AuthorizationDetailType';
import { NewAuthorizationDetailType } from '@management-models/NewAuthorizationDetailType';
import { Fixture } from '../../../test-fixture';
import { HintDomainFixture, setupHintDomain } from '../../ciba-federation/fixtures/hint-domain-fixture';
import { AUTHORIZATION_DETAILS_TEST, paymentSchema } from './authorization-details-fixture';

export const UPSTREAM_LOGIN_HINT = 'upstream-user';

export interface AuthorizationDetailsCibaFixture extends Fixture {
  hintDomain: HintDomainFixture;
  /** Sends a backchannel authentication request to the hint domain, whose notifier supports authorization details. */
  authenticate: (params?: Record<string, string>) => Promise<any>;
  /** Sends the request of {@link authenticate} with `openid` scope, the login hint and `claims` in a signed request object. */
  authenticateWithRequestObject: (claims: Record<string, unknown>) => Promise<any>;
  pollTokenUntilGranted: (authReqId: string) => Promise<any>;
  createType: (type: NewAuthorizationDetailType) => Promise<AuthorizationDetailType>;
  /** Replaces the types enabled on the hint application and waits for the gateway to apply them. */
  enableTypes: (types: string[]) => Promise<void>;
}

/**
 * Federated CIBA towards {@link createCibaUpstreamOpMock}. The hint domain has the reference types of
 * {@link AUTHORIZATION_DETAILS_TEST}, with the payment and account types enabled on its application.
 */
export const setupAuthorizationDetailsCibaFixture = async (): Promise<AuthorizationDetailsCibaFixture> => {
  const accessToken = await requestAdminAccessToken();
  const upstream = await createCibaUpstreamOpMock();
  let hintDomain: HintDomainFixture | null = null;
  const cleanUp = async () => {
    await hintDomain?.cleanUp();
    await upstream.remove();
  };
  try {
    hintDomain = await setupHintDomain(accessToken, upstream);
    const hint = hintDomain;
    const domainId = hint.domain.id;

    const createType = (type: NewAuthorizationDetailType) => createAuthorizationDetailType(domainId, accessToken, type);
    await createType({
      type: AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE,
      name: 'Payment initiation',
      schema: paymentSchema(AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE),
      discovery: true,
    });
    await createType({ type: AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE, name: 'Account information', discovery: true });
    await createType({ type: AUTHORIZATION_DETAILS_TEST.NOT_ENABLED_TYPE, name: 'Internal transfer', discovery: false });

    const typeSettings = (types: string[]) => types.map((type) => ({ type }));
    const { kty, n, e } = cimdPrivateKeyJwtPrivateJwk;
    // patchApplication drops the RSA members of a JWK
    await waitForSyncAfter(domainId, async () => {
      const response = await performPatch(
        getDomainManagerUrl(domainId),
        `/applications/${hint.applicationId}`,
        {
          settings: {
            oauth: {
              jwks: { keys: [{ kty, n, e, use: 'sig', kid: CIMD_PRIVATE_KEY_JWT_KID, alg: 'RS256' }] },
              requestObjectSigningAlg: 'RS256',
              authorizationDetailTypes: typeSettings([AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE, AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE]),
            },
          },
        },
        { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
      );
      expect(response.status).toBe(200);
    });

    const cibaEndpoint = `${process.env.AM_GATEWAY_URL}/${hint.domain.hrid}/oidc/ciba/authenticate`;
    const audience = `${process.env.AM_GATEWAY_URL}/${hint.domain.hrid}/oidc`;
    const basicAuth = `Basic ${Buffer.from(`${hint.clientId}:${hint.clientSecret}`).toString('base64')}`;

    const authenticateWithRequestObject = async (claims: Record<string, unknown>): Promise<any> => {
      const privateKey = await jose.importJWK(cimdPrivateKeyJwtPrivateJwk as jose.JWK, 'RS256');
      const now = Math.floor(Date.now() / 1000);
      const request = await new jose.SignJWT({ scope: 'openid', login_hint: UPSTREAM_LOGIN_HINT, ...claims })
        .setProtectedHeader({ alg: 'RS256', kid: CIMD_PRIVATE_KEY_JWT_KID })
        .setIssuer(hint.clientId)
        .setAudience(audience)
        .setJti(crypto.randomUUID())
        .setIssuedAt(now)
        .setNotBefore(now)
        .setExpirationTime(now + 300)
        .sign(privateKey);
      return performPost(cibaEndpoint, '', new URLSearchParams({ client_id: hint.clientId, request }).toString(), {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: basicAuth,
      });
    };

    return {
      accessToken,
      hintDomain: hint,
      authenticate: (params = {}) => hint.initiateCiba(UPSTREAM_LOGIN_HINT, params),
      authenticateWithRequestObject,
      pollTokenUntilGranted: hint.pollTokenUntilGranted,
      createType: (type) => waitForSyncAfter(domainId, () => createType(type)),
      enableTypes: async (types) => {
        await waitForSyncAfter(domainId, () =>
          patchApplication(
            domainId,
            accessToken,
            { settings: { oauth: { authorizationDetailTypes: typeSettings(types) } } },
            hint.applicationId,
          ),
        );
      },
      cleanUp,
    };
  } catch (error) {
    await cleanUp();
    throw error;
  }
};

export const expectBackchannelError = (response: any, error: string, description: string): void => {
  expect(response.status).toBe(400);
  expect(response.body).toMatchObject({ error, error_description: description });
  expect(response.body.auth_req_id).toBeUndefined();
};

export const expectAcknowledged = (response: any): string => {
  expect(response.status).toBe(200);
  expect(response.body.auth_req_id).toEqual(expect.any(String));
  return response.body.auth_req_id;
};
