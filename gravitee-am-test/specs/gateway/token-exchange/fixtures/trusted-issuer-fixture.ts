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

import { getDomainManagerUrl } from '@management-commands/service/utils';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { waitForOidcReady } from '@management-commands/domain-management-commands';
import request from 'supertest';
import {
  setupTokenExchangeFixture,
  TokenExchangeFixture,
  TokenExchangeFixtureConfig,
  TOKEN_EXCHANGE_TEST,
} from './token-exchange-fixture';
import {
  createTrustedIssuerKeyMaterial,
  signJwtForTrustedIssuer,
  TrustedIssuerKeyMaterial,
} from './trusted-issuer-jwt-helper';

export type KeyAndCertificate = TrustedIssuerKeyMaterial;

const DEFAULT_EXTERNAL_ISSUER = 'https://external-idp.example.com';
const DEFAULT_SCOPE_MAPPINGS: Record<string, string> = {
  'external:read': 'openid',
  'external:profile': 'profile',
};

/**
 * Patch domain via raw supertest. Use this when the request body includes fields
 * not present on the generated SDK model (e.g. tokenExchangeSettings.trustedIssuers),
 * which would otherwise be stripped during serialisation.
 */
export function patchDomainRaw(
  domainId: string,
  accessToken: string,
  body: Record<string, unknown>,
): request.Test {
  return request(getDomainManagerUrl(domainId))
    .patch('')
    .set('Authorization', `Bearer ${accessToken}`)
    .set('Content-Type', 'application/json')
    .send(body);
}

/**
 * Extended fixture for trusted-issuer tests: base token exchange + delegation enabled,
 * then patch with a trusted issuer (PEM + scope mappings). Uses waitForSyncAfter
 * so the gateway sees the new config before tests run.
 */
export interface TrustedIssuerFixture extends TokenExchangeFixture {
  trustedKey: KeyAndCertificate;
  untrustedKey: KeyAndCertificate;
  externalIssuer: string;
  /** Sign a JWT with the trusted key; payload may include iss, sub, scope, email, etc. */
  signExternalJwt: (payload: Record<string, unknown>, options?: { expiresInSeconds?: number }) => string;
}

export interface TrustedIssuerFixtureConfig extends Omit<TokenExchangeFixtureConfig, 'allowDelegation'> {
  externalIssuer?: string;
  scopeMappings?: Record<string, string>;
  maxDelegationDepth?: number;
}

export const setupTrustedIssuerFixture = async (
  config: TrustedIssuerFixtureConfig = {},
): Promise<TrustedIssuerFixture> => {
  const {
    externalIssuer = DEFAULT_EXTERNAL_ISSUER,
    scopeMappings = DEFAULT_SCOPE_MAPPINGS,
    maxDelegationDepth = 3,
    ...baseConfig
  } = config;

  const base = await setupTokenExchangeFixture({
    domainNamePrefix: 'trusted-issuers',
    allowDelegation: true,
    allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
    maxDelegationDepth,
    ...baseConfig,
  });

  try {
    const trustedKey = createTrustedIssuerKeyMaterial();
    const untrustedKey = createTrustedIssuerKeyMaterial();

    await waitForSyncAfter(base.domain.id, async () => {
      await patchDomainRaw(base.domain.id, base.accessToken, {
        tokenExchangeSettings: {
          enabled: true,
          allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
          allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
          allowImpersonation: true,
          allowDelegation: true,
          allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
          maxDelegationDepth,
          trustedIssuers: [
            {
              issuer: externalIssuer,
              keyResolutionMethod: 'PEM',
              certificate: trustedKey.certificatePem,
              scopeMappings,
            },
          ],
        },
      }).expect(200);
    });

    // After sync, the gateway may briefly undeploy/redeploy domain routes.
    // Verify the OIDC endpoints are actually serving before returning.
    await waitForOidcReady(base.domain.hrid);

    return {
      ...base,
      trustedKey,
      untrustedKey,
      externalIssuer,
      signExternalJwt: (payload: Record<string, unknown>, options?: { expiresInSeconds?: number }) =>
        signJwtForTrustedIssuer({
          issuer: (payload.iss as string) ?? externalIssuer,
          privateKeyPem: trustedKey.privateKeyPem,
          subject: (payload.sub as string) ?? 'external-subject',
          payload,
          expiresInSeconds: options?.expiresInSeconds,
        }),
    };
  } catch (error) {
    await base.cleanup();
    throw error;
  }
};
