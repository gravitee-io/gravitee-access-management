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
import { parseJwt } from '@api-fixtures/jwt';
import { createDomain, safeDeleteDomain, startDomain, waitForDomainSync, waitForOidcReady } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { User } from '@management-models/User';
import request from 'supertest';
import crypto from 'crypto';
import jwt from 'jsonwebtoken';
import forge from 'node-forge';
import { setup } from '../../test-fixture';
import { OidcConfiguration, TOKEN_EXCHANGE_TEST } from './fixtures/token-exchange-fixture';

setup(200000);

// ── Crypto helpers ──────────────────────────────────────────────────────

/**
 * Generate an RSA keypair and a self-signed X.509 certificate.
 * Returns { privateKey, publicKeyPem, certificatePem }.
 */
function generateKeyAndCertificate(): {
  privateKey: string;
  publicKeyPem: string;
  certificatePem: string;
} {
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
function signExternalJwt(
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

// ── Test state ──────────────────────────────────────────────────────────

const EXTERNAL_ISSUER = 'https://external-idp.example.com';
const USER_PASSWORD = TOKEN_EXCHANGE_TEST.USER_PASSWORD;

let adminAccessToken: string;
let domain: Domain;
let application: Application;
let user: User;
let oidc: OidcConfiguration;
let basicAuth: string;

// Two keypairs: one trusted, one untrusted
let trustedKey: ReturnType<typeof generateKeyAndCertificate>;
let untrustedKey: ReturnType<typeof generateKeyAndCertificate>;

// ── Setup/Teardown ──────────────────────────────────────────────────────

beforeAll(async () => {
  // Generate crypto material
  trustedKey = generateKeyAndCertificate();
  untrustedKey = generateKeyAndCertificate();

  // Create domain and enable token exchange with trusted issuer
  adminAccessToken = await requestAdminAccessToken();
  expect(adminAccessToken).toBeDefined();

  domain = await createDomain(adminAccessToken, uniqueName('trusted-issuers', true), 'Trusted issuers test domain');
  expect(domain.id).toBeDefined();

  const idpSet = await getAllIdps(domain.id, adminAccessToken);
  const defaultIdp = idpSet.values().next().value;
  expect(defaultIdp).toBeDefined();

  // Enable token exchange with a trusted issuer (PEM)
  await request(getDomainManagerUrl(domain.id))
    .patch('')
    .set('Authorization', `Bearer ${adminAccessToken}`)
    .set('Content-Type', 'application/json')
    .send({
      tokenExchangeSettings: {
        enabled: true,
        allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
        allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
        allowImpersonation: true,
        allowDelegation: true,
        allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
        maxDelegationDepth: 3,
        trustedIssuers: [
          {
            issuer: EXTERNAL_ISSUER,
            keyResolutionMethod: 'PEM',
            certificate: trustedKey.certificatePem,
            scopeMappings: {
              'external:read': 'openid',
              'external:profile': 'profile',
            },
          },
        ],
      },
    })
    .expect(200);

  // Create application with token exchange grant
  application = await createTestApp(uniqueName('trusted-issuers-client', true), domain, adminAccessToken, 'WEB', {
    settings: {
      oauth: {
        redirectUris: [TOKEN_EXCHANGE_TEST.REDIRECT_URI],
        grantTypes: TOKEN_EXCHANGE_TEST.DEFAULT_GRANT_TYPES,
        scopeSettings: [
          { scope: 'openid', defaultScope: true },
          { scope: 'profile', defaultScope: true },
          { scope: 'offline_access', defaultScope: false },
        ],
      },
    },
    identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
  });
  expect(application.id).toBeDefined();

  // Start domain and wait for it to be ready
  const startedDomain = await startDomain(domain.id, adminAccessToken);
  domain = startedDomain;

  const oidcResponse = await waitForOidcReady(startedDomain.hrid, { timeoutMs: 30000, intervalMs: 500 });
  expect(oidcResponse.status).toBe(200);
  oidc = oidcResponse.body as OidcConfiguration;

  // Create test user
  user = await buildCreateAndTestUser(startedDomain.id, adminAccessToken, 0);
  expect(user).toBeDefined();

  basicAuth = applicationBase64Token(application);
});

afterAll(async () => {
  if (domain?.id && adminAccessToken) {
    await safeDeleteDomain(domain.id, adminAccessToken);
  }
});

// ── Helper to obtain domain-issued tokens (password grant) ──────────────

async function obtainDomainTokens(scope: string = 'openid%20profile%20offline_access') {
  const response = await performPost(
    oidc.token_endpoint,
    '',
    `grant_type=password&username=${user.username}&password=${USER_PASSWORD}&scope=${scope}`,
    {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${basicAuth}`,
    },
  ).expect(200);

  return {
    accessToken: response.body.access_token as string,
    refreshToken: response.body.refresh_token as string,
    idToken: response.body.id_token as string,
  };
}

// ── Tests ───────────────────────────────────────────────────────────────

describe('Trusted Issuers - Token Exchange', () => {
  describe('Impersonation with external JWT', () => {
    it('should exchange an external JWT signed by a trusted issuer (PEM)', async () => {
      const externalJwt = signExternalJwt(
        { sub: 'external-user-123', scope: 'external:read external:profile', iss: EXTERNAL_ISSUER },
        trustedKey.privateKey,
      );

      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
          `&subject_token=${externalJwt}` +
          `&subject_token_type=urn:ietf:params:oauth:token-type:jwt`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
      ).expect(200);

      expect(response.body.access_token).toBeDefined();
      expect(response.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
      expect(response.body.token_type.toLowerCase()).toBe('bearer');
    });

    it('should reject an external JWT from an unknown issuer', async () => {
      const externalJwt = signExternalJwt(
        { sub: 'external-user-123', scope: 'openid', iss: 'https://unknown-issuer.example.com' },
        trustedKey.privateKey,
      );

      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
          `&subject_token=${externalJwt}` +
          `&subject_token_type=urn:ietf:params:oauth:token-type:jwt`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
      ).expect(400);

      expect(response.body.error).toBe('invalid_grant');
    });

    it('should reject an external JWT with an invalid signature', async () => {
      // Sign with untrusted key (different from the PEM configured for this issuer)
      const externalJwt = signExternalJwt(
        { sub: 'external-user-123', scope: 'openid', iss: EXTERNAL_ISSUER },
        untrustedKey.privateKey,
      );

      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
          `&subject_token=${externalJwt}` +
          `&subject_token_type=urn:ietf:params:oauth:token-type:jwt`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
      ).expect(400);

      expect(response.body.error).toBe('invalid_grant');
    });
  });

  describe('Scope mapping', () => {
    it('should map external scopes to domain scopes', async () => {
      const externalJwt = signExternalJwt(
        { sub: 'external-user-scope', scope: 'external:read external:profile', iss: EXTERNAL_ISSUER },
        trustedKey.privateKey,
      );

      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
          `&subject_token=${externalJwt}` +
          `&subject_token_type=urn:ietf:params:oauth:token-type:jwt` +
          `&scope=openid%20profile`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
      ).expect(200);

      const decoded = parseJwt(response.body.access_token);
      const scopes = (decoded.payload['scope'] as string).split(' ');
      expect(scopes).toContain('openid');
      expect(scopes).toContain('profile');
    });

    it('should drop unmapped external scopes', async () => {
      // external:read maps to openid, external:profile maps to profile, external:unknown has no mapping
      const externalJwt = signExternalJwt(
        { sub: 'external-user-unmapped', scope: 'external:read external:profile external:unknown', iss: EXTERNAL_ISSUER },
        trustedKey.privateKey,
      );

      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
          `&subject_token=${externalJwt}` +
          `&subject_token_type=urn:ietf:params:oauth:token-type:jwt` +
          `&scope=openid%20profile`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
      ).expect(200);

      const decoded = parseJwt(response.body.access_token);
      const scopes = (decoded.payload['scope'] as string).split(' ');
      // external:read -> openid, external:profile -> profile, external:unknown -> dropped
      expect(scopes).toContain('openid');
      expect(scopes).toContain('profile');
      expect(scopes).not.toContain('external:unknown');
      expect(scopes).not.toContain('external:read');
      expect(scopes).not.toContain('external:profile');
    });
  });

  describe('Delegation with external subject token', () => {
    it('should reject JWT actor token when subject is from an external trusted issuer', async () => {
      const externalSubjectJwt = signExternalJwt(
        { sub: 'external-user-delegation', scope: 'external:read', iss: EXTERNAL_ISSUER },
        trustedKey.privateKey,
      );

      // Sign an actor JWT (same external issuer — should be rejected regardless)
      const actorJwt = signExternalJwt(
        { sub: 'actor-service', scope: 'openid', iss: EXTERNAL_ISSUER },
        trustedKey.privateKey,
      );

      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
          `&subject_token=${externalSubjectJwt}` +
          `&subject_token_type=urn:ietf:params:oauth:token-type:jwt` +
          `&actor_token=${actorJwt}` +
          `&actor_token_type=urn:ietf:params:oauth:token-type:jwt`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
      ).expect(400);

      expect(response.body.error).toBe('invalid_grant');
    });

    it('should allow delegation with external subject + domain access_token actor', async () => {
      const externalSubjectJwt = signExternalJwt(
        { sub: 'external-user-delegation-ok', scope: 'external:read external:profile', iss: EXTERNAL_ISSUER },
        trustedKey.privateKey,
      );

      // Get a domain-issued access token as actor
      const domainTokens = await obtainDomainTokens('openid%20profile');

      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
          `&subject_token=${externalSubjectJwt}` +
          `&subject_token_type=urn:ietf:params:oauth:token-type:jwt` +
          `&actor_token=${domainTokens.accessToken}` +
          `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
      ).expect(200);

      expect(response.body.access_token).toBeDefined();
      // Delegation token should have 'act' claim
      const decoded = parseJwt(response.body.access_token);
      expect(decoded.payload['act']).toBeDefined();
    });
  });

  describe('Domain-issued tokens (no regression)', () => {
    it('should still exchange domain-issued access tokens', async () => {
      const domainTokens = await obtainDomainTokens('openid%20profile');

      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
          `&subject_token=${domainTokens.accessToken}` +
          `&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
      ).expect(200);

      expect(response.body.access_token).toBeDefined();
      expect(response.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    });

    it('should still exchange domain-issued id_tokens', async () => {
      const domainTokens = await obtainDomainTokens('openid%20profile');

      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
          `&subject_token=${domainTokens.idToken}` +
          `&subject_token_type=urn:ietf:params:oauth:token-type:id_token`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
      ).expect(200);

      expect(response.body.access_token).toBeDefined();
    });
  });
});

describe('Trusted Issuers - Management API Validation', () => {
  it('should reject invalid PEM certificate', async () => {
    await request(getDomainManagerUrl(domain.id))
      .patch('')
      .set('Authorization', `Bearer ${adminAccessToken}`)
      .set('Content-Type', 'application/json')
      .send({
        tokenExchangeSettings: {
          enabled: true,
          allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
          allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
          allowImpersonation: true,
          allowDelegation: false,
          trustedIssuers: [
            {
              issuer: 'https://bad-pem-issuer.example.com',
              keyResolutionMethod: 'PEM',
              certificate: 'not-a-valid-pem-certificate',
            },
          ],
        },
      })
      .expect(400);
  });

  it('should reject duplicate issuer URLs', async () => {
    await request(getDomainManagerUrl(domain.id))
      .patch('')
      .set('Authorization', `Bearer ${adminAccessToken}`)
      .set('Content-Type', 'application/json')
      .send({
        tokenExchangeSettings: {
          enabled: true,
          allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
          allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
          allowImpersonation: true,
          allowDelegation: false,
          trustedIssuers: [
            {
              issuer: 'https://duplicate-issuer.example.com',
              keyResolutionMethod: 'PEM',
              certificate: trustedKey.certificatePem,
            },
            {
              issuer: 'https://duplicate-issuer.example.com',
              keyResolutionMethod: 'PEM',
              certificate: trustedKey.certificatePem,
            },
          ],
        },
      })
      .expect(400);
  });

  it('should reject exceeding max trusted issuer count', async () => {
    const issuers = Array.from({ length: 10 }, (_, i) => ({
      issuer: `https://issuer-${i}.example.com`,
      keyResolutionMethod: 'PEM',
      certificate: trustedKey.certificatePem,
    }));

    await request(getDomainManagerUrl(domain.id))
      .patch('')
      .set('Authorization', `Bearer ${adminAccessToken}`)
      .set('Content-Type', 'application/json')
      .send({
        tokenExchangeSettings: {
          enabled: true,
          allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
          allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
          allowImpersonation: true,
          allowDelegation: false,
          trustedIssuers: issuers,
        },
      })
      .expect(400);
  });

  it('should persist trusted issuer configuration after save and reload', async () => {
    // Re-apply the original trusted issuer config
    const patchResponse = await request(getDomainManagerUrl(domain.id))
      .patch('')
      .set('Authorization', `Bearer ${adminAccessToken}`)
      .set('Content-Type', 'application/json')
      .send({
        tokenExchangeSettings: {
          enabled: true,
          allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
          allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
          allowImpersonation: true,
          allowDelegation: true,
          allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
          maxDelegationDepth: 3,
          trustedIssuers: [
            {
              issuer: EXTERNAL_ISSUER,
              keyResolutionMethod: 'PEM',
              certificate: trustedKey.certificatePem,
              scopeMappings: {
                'external:read': 'openid',
                'external:profile': 'profile',
              },
            },
          ],
        },
      })
      .expect(200);

    // Verify persisted values
    const body = patchResponse.body;
    expect(body.tokenExchangeSettings).toBeDefined();
    expect(body.tokenExchangeSettings.trustedIssuers).toHaveLength(1);
    expect(body.tokenExchangeSettings.trustedIssuers[0].issuer).toBe(EXTERNAL_ISSUER);
    expect(body.tokenExchangeSettings.trustedIssuers[0].keyResolutionMethod).toBe('PEM');
    expect(body.tokenExchangeSettings.trustedIssuers[0].scopeMappings).toEqual({
      'external:read': 'openid',
      'external:profile': 'profile',
    });

    // Wait for domain sync after the config change
    await waitForDomainSync(domain.id);
  });
});
