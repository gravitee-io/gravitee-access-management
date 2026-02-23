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

import { patchDomain } from '@management-commands/domain-management-commands';
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { TrustedIssuer } from '@management-models/TrustedIssuer';
import { PatchDomain } from '@management-models/PatchDomain';
import request from 'supertest';
import jwt from 'jsonwebtoken';
import forge from 'node-forge';
import {
  setupTokenExchangeFixture,
  TokenExchangeFixture,
  TokenExchangeFixtureConfig,
  TOKEN_EXCHANGE_TEST,
} from './token-exchange-fixture';

// -- Crypto helpers --

export interface KeyAndCertificate {
  privateKey: string;
  publicKeyPem: string;
  certificatePem: string;
}

/**
 * Generate an RSA keypair and a self-signed X.509 certificate.
 */
export function generateKeyAndCertificate(): KeyAndCertificate {
  const keys = forge.pki.rsa.generateKeyPair(2048);
  const cert = forge.pki.createCertificate();
  cert.publicKey = keys.publicKey;
  cert.serialNumber = '01';
  cert.validity.notBefore = new Date();
  cert.validity.notAfter = new Date();
  cert.validity.notAfter.setFullYear(cert.validity.notBefore.getFullYear() + 1);

  const attrs = [{ name: 'commonName', value: 'Trusted Issuer Test' }];
  cert.setSubject(attrs);
  cert.setIssuer(attrs);
  cert.sign(keys.privateKey, forge.md.sha256.create());

  return {
    privateKey: forge.pki.privateKeyToPem(keys.privateKey),
    publicKeyPem: forge.pki.publicKeyToPem(keys.publicKey),
    certificatePem: forge.pki.certificateToPem(cert),
  };
}

/**
 * Sign a JWT with the given private key.
 */
export function signExternalJwt(
  payload: Record<string, unknown>,
  privateKey: string,
  options: jwt.SignOptions = {},
): string {
  return jwt.sign(payload, privateKey, {
    algorithm: 'RS256',
    expiresIn: '1h',
    ...options,
  });
}

// -- Fixture --

/**
 * Extended fixture for trusted issuer tests.
 */
export interface TrustedIssuerFixture extends TokenExchangeFixture {
  trustedKey: KeyAndCertificate;
  untrustedKey: KeyAndCertificate;
  externalIssuer: string;
  /** Convenience: sign a JWT using the trusted key. */
  signExternalJwt: (payload: Record<string, unknown>, options?: jwt.SignOptions) => string;
}

export interface TrustedIssuerFixtureConfig extends Omit<TokenExchangeFixtureConfig, 'allowDelegation'> {
  externalIssuer?: string;
  scopeMappings?: Record<string, string>;
  maxDelegationDepth?: number;
}

const DEFAULT_EXTERNAL_ISSUER = 'https://external-idp.example.com';
const DEFAULT_SCOPE_MAPPINGS: Record<string, string> = {
  'external:read': 'openid',
  'external:profile': 'profile',
};

/**
 * Patch domain via raw supertest. Use this only for error-path assertions
 * (e.g. `.expect(400)`) where the SDK's `patchDomain()` would throw.
 * For success-path patches, prefer the SDK's `patchDomain()`.
 */
export function patchDomainRaw(
  domainId: string,
  accessToken: string,
  body: PatchDomain,
): request.Test {
  return request(getDomainManagerUrl(domainId))
    .patch('')
    .set('Authorization', `Bearer ${accessToken}`)
    .set('Content-Type', 'application/json')
    .send(body);
}

/**
 * Setup a token exchange fixture pre-configured with a trusted issuer (PEM).
 *
 * Pattern: follows `setupTokenExchangeMcpFixture` — builds on top of
 * `setupTokenExchangeFixture`, then patches the domain with trusted issuer config.
 */
export const setupTrustedIssuerFixture = async (
  config: TrustedIssuerFixtureConfig = {},
): Promise<TrustedIssuerFixture> => {
  const {
    externalIssuer = DEFAULT_EXTERNAL_ISSUER,
    scopeMappings = DEFAULT_SCOPE_MAPPINGS,
    maxDelegationDepth = 3,
    ...baseConfig
  } = config;

  // 1. Setup base token exchange fixture with delegation enabled and jwt in allowed types
  const base = await setupTokenExchangeFixture({
    domainNamePrefix: 'trusted-issuers',
    allowDelegation: true,
    allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
    maxDelegationDepth,
    ...baseConfig,
  });

  try {
    // 2. Generate two RSA keypairs: trusted (configured) and untrusted (not configured)
    const trustedKey = generateKeyAndCertificate();
    const untrustedKey = generateKeyAndCertificate();

    // 3. Patch domain with trusted issuer config and wait for gateway sync.
    //    Uses waitForSyncAfter to capture lastSync before the mutation and poll
    //    until it advances — waitForDomainSync alone returns immediately because
    //    the domain is already DEPLOYED from the base fixture.
    const trustedIssuer: TrustedIssuer = {
      issuer: externalIssuer,
      keyResolutionMethod: 'PEM',
      certificate: trustedKey.certificatePem,
      scopeMappings,
    };

    await waitForSyncAfter(base.domain.id, () =>
      patchDomain(base.domain.id, base.accessToken, {
        tokenExchangeSettings: {
          enabled: true,
          allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
          allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
          allowImpersonation: true,
          allowDelegation: true,
          allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
          maxDelegationDepth,
          trustedIssuers: [trustedIssuer],
        },
      }),
    );

    return {
      ...base,
      trustedKey,
      untrustedKey,
      externalIssuer,
      signExternalJwt: (payload, options?) => signExternalJwt(payload, trustedKey.privateKey, options),
    };
  } catch (error) {
    await base.cleanup();
    throw error;
  }
};
