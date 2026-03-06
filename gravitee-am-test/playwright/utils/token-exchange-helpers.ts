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
import request from 'supertest';
import { expect } from '@playwright/test';
import * as forge from 'node-forge';
import jwt from 'jsonwebtoken';
import { getDomainManagerUrl } from '../../api/commands/management/service/utils';
import { getWellKnownOpenIdConfiguration, performPost, performGet } from '../../api/commands/gateway/oauth-oidc-commands';
import { waitForSyncAfter, waitForNextSync } from '../../api/commands/gateway/monitoring-commands';
import { createDomain, startDomain, waitForDomainSync, safeDeleteDomain, waitForOidcReady } from '../../api/commands/management/domain-management-commands';
import { getAllIdps } from '../../api/commands/management/idp-management-commands';
import { buildCreateAndTestUser } from '../../api/commands/management/user-management-commands';
import { createTestApp } from '../../api/commands/utils/application-commands';
import { applicationBase64Token } from '../../api/commands/gateway/utils';
import { TOKEN_EXCHANGE_DEFAULTS } from '../fixtures/token-exchange.fixture';
import { quietly, uniqueTestName } from './fixture-helpers';
import { API_USER_PASSWORD } from './test-constants';

/* ------------------------------------------------------------------ */
/*  OIDC types                                                        */
/* ------------------------------------------------------------------ */

export interface OidcConfiguration {
  issuer: string;
  authorization_endpoint: string;
  token_endpoint: string;
  introspection_endpoint: string;
  userinfo_endpoint: string;
  revocation_endpoint: string;
  end_session_endpoint: string;
  jwks_uri: string;
  grant_types_supported: string[];
  scopes_supported: string[];
  [key: string]: unknown;
}

export interface SubjectTokens {
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  expiresIn: number;
}

export interface KeyAndCertificate {
  certificatePem: string;
  privateKeyPem: string;
}

/* ------------------------------------------------------------------ */
/*  Domain patching (raw supertest — SDK strips trustedIssuers)       */
/* ------------------------------------------------------------------ */

export function patchDomainRaw(domainId: string, accessToken: string, body: Record<string, unknown>): request.Test {
  return request(getDomainManagerUrl(domainId))
    .patch('')
    .set('Authorization', `Bearer ${accessToken}`)
    .set('Content-Type', 'application/json')
    .send(body);
}

/* ------------------------------------------------------------------ */
/*  OIDC discovery                                                     */
/* ------------------------------------------------------------------ */

export async function fetchOidcConfig(domainHrid: string): Promise<OidcConfiguration> {
  const res = await getWellKnownOpenIdConfiguration(domainHrid);
  if (res.status !== 200) {
    throw new Error(`OIDC discovery failed for domain "${domainHrid}": status ${res.status}`);
  }
  return res.body as OidcConfiguration;
}

/* ------------------------------------------------------------------ */
/*  Token operations                                                   */
/* ------------------------------------------------------------------ */

export interface RetryOnStatusOptions {
  retryOn: number;
  expect?: number;
  maxRetries?: number;
  retryDelayMs?: number;
}

/**
 * Retries a supertest request when it returns a transient status code.
 * Used for tests where domain sync reports complete but the gateway
 * hasn't fully loaded the configuration under parallel load.
 */
export async function retryOnStatus(
  requestFn: () => request.Test,
  options: RetryOnStatusOptions,
): Promise<request.Response> {
  const { retryOn, expect: expectedStatus = 200, maxRetries = 3, retryDelayMs = 2000 } = options;
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    const response = await requestFn();
    if (response.status !== retryOn || attempt === maxRetries) {
      expect(response.status, `request failed with ${response.status} after ${attempt + 1} attempts`).toBe(expectedStatus);
      return response;
    }
    await new Promise((r) => setTimeout(r, retryDelayMs));
  }
  throw new Error('unreachable');
}

export async function obtainSubjectToken(
  tokenEndpoint: string,
  basicAuth: string,
  username: string,
  password: string,
  scope = 'openid profile offline_access',
): Promise<SubjectTokens> {
  // Accept spaces or %20 — normalize to %20 for form-urlencoded body
  const encodedScope = scope.includes('%20') ? scope : scope.split(' ').join('%20');

  const response = await retryOnStatus(
    () => performPost(
      tokenEndpoint,
      '',
      `grant_type=password&username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&scope=${encodedScope}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ),
    { retryOn: 401 },
  );

  return {
    accessToken: response.body.access_token,
    refreshToken: response.body.refresh_token,
    idToken: response.body.id_token,
    expiresIn: response.body.expires_in,
  };
}

export interface ExchangeTokenParams {
  subjectToken: string;
  subjectTokenType: string;
  actorToken?: string;
  actorTokenType?: string;
  requestedTokenType?: string;
  scope?: string;
}

export function exchangeToken(
  tokenEndpoint: string,
  basicAuth: string,
  params: ExchangeTokenParams,
): request.Test {
  let body =
    `grant_type=${encodeURIComponent('urn:ietf:params:oauth:grant-type:token-exchange')}` +
    `&subject_token=${encodeURIComponent(params.subjectToken)}` +
    `&subject_token_type=${encodeURIComponent(params.subjectTokenType)}`;

  if (params.actorToken) {
    body += `&actor_token=${encodeURIComponent(params.actorToken)}`;
    body += `&actor_token_type=${encodeURIComponent(params.actorTokenType || 'urn:ietf:params:oauth:token-type:access_token')}`;
  }

  if (params.requestedTokenType) {
    body += `&requested_token_type=${encodeURIComponent(params.requestedTokenType)}`;
  }

  if (params.scope) {
    body += `&scope=${encodeURIComponent(params.scope)}`;
  }

  return performPost(tokenEndpoint, '', body, {
    'Content-type': 'application/x-www-form-urlencoded',
    Authorization: `Basic ${basicAuth}`,
  });
}

export async function introspectToken(
  introspectionEndpoint: string,
  basicAuth: string,
  token: string,
): Promise<Record<string, unknown>> {
  const response = await performPost(
    introspectionEndpoint,
    '',
    `token=${encodeURIComponent(token)}`,
    {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${basicAuth}`,
    },
  ).expect(200);
  return response.body;
}

export async function revokeToken(
  revocationEndpoint: string,
  basicAuth: string,
  token: string,
  hint: 'access_token' | 'refresh_token',
): Promise<void> {
  await performPost(
    revocationEndpoint,
    '',
    `token=${encodeURIComponent(token)}&token_type_hint=${hint}`,
    {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${basicAuth}`,
    },
  ).expect(200);
}

/* ------------------------------------------------------------------ */
/*  Crypto: RSA key material + self-signed X.509                       */
/* ------------------------------------------------------------------ */

export function createKeyMaterial(): KeyAndCertificate {
  const keys = forge.pki.rsa.generateKeyPair(2048);
  const cert = forge.pki.createCertificate();
  cert.publicKey = keys.publicKey;
  cert.serialNumber = Date.now().toString();

  const now = new Date();
  const notBefore = new Date(now);
  notBefore.setDate(notBefore.getDate() - 1);
  const notAfter = new Date(now);
  notAfter.setDate(notAfter.getDate() + 365);

  cert.validity.notBefore = notBefore;
  cert.validity.notAfter = notAfter;

  const subjectAttrs = [
    { name: 'countryName', value: 'US' },
    { name: 'organizationName', value: 'Test Org' },
    { name: 'commonName', value: `PW Test ${cert.serialNumber}` },
  ];
  cert.setSubject(subjectAttrs);
  cert.setIssuer(subjectAttrs);
  cert.setExtensions([
    { name: 'basicConstraints', cA: true },
    {
      name: 'keyUsage',
      keyCertSign: true,
      digitalSignature: true,
      nonRepudiation: true,
      keyEncipherment: true,
      dataEncipherment: true,
    },
  ]);

  cert.sign(keys.privateKey, forge.md.sha256.create());

  return {
    certificatePem: forge.pki.certificateToPem(cert),
    privateKeyPem: forge.pki.privateKeyToPem(keys.privateKey),
  };
}

/* ------------------------------------------------------------------ */
/*  JWT signing + parsing                                              */
/* ------------------------------------------------------------------ */

export interface SignJwtOptions {
  issuer: string;
  privateKeyPem: string;
  subject?: string;
  payload?: Record<string, unknown>;
  expiresInSeconds?: number;
}

export function signJwt(options: SignJwtOptions): string {
  const {
    issuer,
    privateKeyPem,
    subject = 'external-subject-id',
    payload = {},
    expiresInSeconds = 3600,
  } = options;

  const now = Math.floor(Date.now() / 1000);
  return jwt.sign(
    { iss: issuer, sub: subject, iat: now, exp: now + expiresInSeconds, ...payload },
    privateKeyPem,
    { algorithm: 'RS256', noTimestamp: false },
  );
}

export function parseJwt(token: string): Record<string, unknown> {
  return jwt.decode(token, { complete: false }) as Record<string, unknown>;
}

/* ------------------------------------------------------------------ */
/*  Domain trusted issuer configuration                                */
/* ------------------------------------------------------------------ */

export interface ConfigureTrustedIssuerOptions {
  issuer?: string;
  certificate?: string;
  scopeMappings?: Record<string, string>;
  userBindingEnabled?: boolean;
  userBindingCriteria?: Array<{ attribute: string; expression: string }>;
  trustedIssuers?: Array<Record<string, unknown>>;
}

export async function configureTrustedIssuer(
  domainId: string,
  adminToken: string,
  domainHrid: string,
  options: ConfigureTrustedIssuerOptions = {},
): Promise<void> {
  const issuerConfig: Record<string, unknown> = {
    issuer: options.issuer || 'https://external-idp.example.com',
    keyResolutionMethod: 'PEM',
    certificate: options.certificate,
  };
  if (options.scopeMappings) {
    issuerConfig.scopeMappings = options.scopeMappings;
  }
  if (options.userBindingEnabled) {
    issuerConfig.userBindingEnabled = true;
    if (options.userBindingCriteria) {
      issuerConfig.userBindingCriteria = options.userBindingCriteria;
    }
  }

  await waitForSyncAfter(domainId, async () => {
    await patchDomainRaw(domainId, adminToken, {
      tokenExchangeSettings: {
        enabled: true,
        allowedSubjectTokenTypes: TOKEN_EXCHANGE_DEFAULTS.ALLOWED_SUBJECT_TOKEN_TYPES,
        allowedRequestedTokenTypes: TOKEN_EXCHANGE_DEFAULTS.ALLOWED_REQUESTED_TOKEN_TYPES,
        allowImpersonation: true,
        allowDelegation: true,
        allowedActorTokenTypes: TOKEN_EXCHANGE_DEFAULTS.ALLOWED_ACTOR_TOKEN_TYPES,
        maxDelegationDepth: 3,
        trustedIssuers: options.trustedIssuers || [issuerConfig],
      },
    }).expect(200);
  });
  await waitForOidcReady(domainHrid);
  // Stabilization: OIDC routes are live but trusted issuer config may not yet be
  // applied internally. Without this, AM-6637 flakes when 12+ patches run in serial.
  await waitMs(500);
}

/* ------------------------------------------------------------------ */
/*  Timing helpers                                                     */
/* ------------------------------------------------------------------ */

// Gateway token cache TTL is 10s; wait slightly longer to ensure cache expiry + sync propagation.
export const REVOCATION_WAIT_MS = 11_000;

export function waitMs(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/* ------------------------------------------------------------------ */
/*  Two-Domain Fixture: External Issuer Domain                         */
/* ------------------------------------------------------------------ */

const ISSUER_USER_PASSWORD = API_USER_PASSWORD;

export interface IssuerDomain {
  domain: import('../../api/management/models').Domain;
  oidcConfig: OidcConfiguration;
  basicAuth: string;
  certificatePem: string;
  /** Obtain a real AM-issued token from this domain */
  obtainToken: (scope?: string) => Promise<SubjectTokens>;
  cleanup: () => Promise<void>;
}

/**
 * Creates a second AM domain to act as an external issuer for cross-domain trust tests.
 * Returns the domain's OIDC config (issuer, JWKS URI), its system certificate PEM,
 * and a helper to obtain real AM-issued tokens.
 */
export async function setupIssuerDomain(adminToken: string): Promise<IssuerDomain> {
  let domain: import('../../api/management/models').Domain | null = null;
  try {
    domain = await quietly(() => createDomain(adminToken, uniqueTestName('pw-issuer-b'), 'Issuer domain for cross-trust tests'));
    await quietly(() => startDomain(domain.id, adminToken));
    await quietly(() => waitForDomainSync(domain.id));

    // Create app with password grant
    const idpSet = await getAllIdps(domain.id, adminToken);
    const defaultIdp = idpSet.values().next().value;
    if (!defaultIdp) throw new Error('No IdP found for issuer domain');

    const app = await quietly(() =>
      createTestApp(uniqueTestName('pw-issuer-app'), domain, adminToken, 'WEB', {
        settings: {
          oauth: {
            redirectUris: ['https://gravitee.io/callback'],
            grantTypes: ['password', 'refresh_token'],
            scopeSettings: [
              { scope: 'openid', defaultScope: true },
              { scope: 'profile', defaultScope: true },
            ],
          },
        },
        identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
      }),
    );

    // Create user
    const user = await quietly(() => buildCreateAndTestUser(domain.id, adminToken, 0));

    // Wait for app+user sync
    await waitForNextSync(domain.id);
    const oidcRes = await waitForOidcReady(domain.hrid, { timeoutMs: 30000, intervalMs: 500 });
    const oidcConfig = oidcRes.body as OidcConfiguration;

    const basicAuth = applicationBase64Token(app);

    const obtainToken = (scope?: string) =>
      obtainSubjectToken(
        oidcConfig.token_endpoint,
        basicAuth,
        user.username,
        ISSUER_USER_PASSWORD,
        scope || 'openid profile',
      );

    // Extract certificate PEM by matching the signing key's kid:
    // 1. Obtain a token to discover the kid from the JWT header
    // 2. Find the matching key in JWKS and extract its x5c certificate
    const sampleTokens = await obtainToken();
    const header = JSON.parse(Buffer.from(sampleTokens.accessToken.split('.')[0], 'base64url').toString());
    const signingKid = header.kid;

    const jwksRes = await performGet(oidcConfig.jwks_uri, '').expect(200);
    const jwks = jwksRes.body as { keys: Array<{ kid?: string; x5c?: string[]; [k: string]: unknown }> };
    const signingKey = signingKid
      ? jwks.keys.find((k) => k.kid === signingKid)
      : jwks.keys[0];
    const x5c = signingKey?.x5c?.[0];
    if (!x5c) throw new Error(`No x5c certificate in issuer domain JWKS for kid=${signingKid}, keys=${JSON.stringify(jwks.keys.map((k) => k.kid))}`);
    // Standard PEM format requires 64-char line wrapping of base64 data
    const wrapped = x5c.match(/.{1,64}/g)!.join('\n');
    const certificatePem = `-----BEGIN CERTIFICATE-----\n${wrapped}\n-----END CERTIFICATE-----`;

    const cleanup = async () => {
      if (domain?.id) await quietly(() => safeDeleteDomain(domain.id, adminToken));
    };

    return { domain, oidcConfig, basicAuth, certificatePem, obtainToken, cleanup };
  } catch (error) {
    if (domain?.id) {
      try { await quietly(() => safeDeleteDomain(domain.id, adminToken)); } catch { /* ignore */ }
    }
    throw error;
  }
}
