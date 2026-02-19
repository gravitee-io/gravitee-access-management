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
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { setup } from '../../test-fixture';
import { TOKEN_EXCHANGE_TEST } from './fixtures/token-exchange-fixture';
import { patchDomainRaw, setupTrustedIssuerFixture, signExternalJwt, TrustedIssuerFixture } from './fixtures/trusted-issuer-fixture';

setup(200000);

let fixture: TrustedIssuerFixture;

beforeAll(async () => {
  fixture = await setupTrustedIssuerFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Trusted Issuers - Token Exchange', () => {
  describe('Impersonation with external JWT', () => {
    it('should exchange an external JWT signed by a trusted issuer (PEM)', async () => {
      const { oidc, basicAuth, externalIssuer } = fixture;

      const externalJwt = fixture.signExternalJwt({
        sub: 'external-user-123',
        scope: 'external:read external:profile',
        iss: externalIssuer,
      });

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
      const { oidc, basicAuth, trustedKey } = fixture;

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
      const { oidc, basicAuth, untrustedKey, externalIssuer } = fixture;

      // Sign with untrusted key (different from the PEM configured for this issuer)
      const externalJwt = signExternalJwt(
        { sub: 'external-user-123', scope: 'openid', iss: externalIssuer },
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
      const { oidc, basicAuth, externalIssuer } = fixture;

      const externalJwt = fixture.signExternalJwt({
        sub: 'external-user-scope',
        scope: 'external:read external:profile',
        iss: externalIssuer,
      });

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
      const { oidc, basicAuth, externalIssuer } = fixture;

      // external:read maps to openid, external:profile maps to profile, external:unknown has no mapping
      const externalJwt = fixture.signExternalJwt({
        sub: 'external-user-unmapped',
        scope: 'external:read external:profile external:unknown',
        iss: externalIssuer,
      });

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
      const { oidc, basicAuth, trustedKey, externalIssuer } = fixture;

      const externalSubjectJwt = signExternalJwt(
        { sub: 'external-user-delegation', scope: 'external:read', iss: externalIssuer },
        trustedKey.privateKey,
      );

      // Sign an actor JWT (same external issuer -- should be rejected regardless)
      const actorJwt = signExternalJwt(
        { sub: 'actor-service', scope: 'openid', iss: externalIssuer },
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
      const { oidc, basicAuth, externalIssuer } = fixture;

      const externalSubjectJwt = fixture.signExternalJwt({
        sub: 'external-user-delegation-ok',
        scope: 'external:read external:profile',
        iss: externalIssuer,
      });

      // Get a domain-issued access token as actor
      const domainTokens = await fixture.obtainSubjectToken('openid%20profile');

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
      const { oidc, basicAuth } = fixture;
      const domainTokens = await fixture.obtainSubjectToken('openid%20profile');

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
      const { oidc, basicAuth } = fixture;
      const domainTokens = await fixture.obtainSubjectToken('openid%20profile');

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
  // NOTE: These tests use patchDomainRaw (raw supertest) because the generated SDK
  // model for TokenExchangeSettings does not include `trustedIssuers` yet.
  // patchDomain() silently strips the field during serialization.

  it('should reject invalid PEM certificate', async () => {
    const { domain, accessToken } = fixture;

    await patchDomainRaw(domain.id, accessToken, {
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
    }).expect(400);
  });

  it('should reject duplicate issuer URLs', async () => {
    const { domain, accessToken, trustedKey } = fixture;

    await patchDomainRaw(domain.id, accessToken, {
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
    }).expect(400);
  });

  it('should reject exceeding max trusted issuer count', async () => {
    const { domain, accessToken, trustedKey } = fixture;
    const issuers = Array.from({ length: 10 }, (_, i) => ({
      issuer: `https://issuer-${i}.example.com`,
      keyResolutionMethod: 'PEM',
      certificate: trustedKey.certificatePem,
    }));

    await patchDomainRaw(domain.id, accessToken, {
      tokenExchangeSettings: {
        enabled: true,
        allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
        allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
        allowImpersonation: true,
        allowDelegation: false,
        trustedIssuers: issuers,
      },
    }).expect(400);
  });

  it('should persist trusted issuer configuration after save and reload', async () => {
    const { domain, accessToken, trustedKey, externalIssuer } = fixture;

    // Re-apply the original trusted issuer config and wait for sync
    const patchResponse = await waitForSyncAfter(domain.id, () =>
      patchDomainRaw(domain.id, accessToken, {
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
              issuer: externalIssuer,
              keyResolutionMethod: 'PEM',
              certificate: trustedKey.certificatePem,
              scopeMappings: {
                'external:read': 'openid',
                'external:profile': 'profile',
              },
            },
          ],
        },
      }).expect(200),
    );

    // Verify persisted values
    const body = patchResponse.body;
    expect(body.tokenExchangeSettings).toBeDefined();
    expect(body.tokenExchangeSettings.trustedIssuers).toHaveLength(1);
    expect(body.tokenExchangeSettings.trustedIssuers[0].issuer).toBe(externalIssuer);
    expect(body.tokenExchangeSettings.trustedIssuers[0].keyResolutionMethod).toBe('PEM');
    expect(body.tokenExchangeSettings.trustedIssuers[0].scopeMappings).toEqual({
      'external:read': 'openid',
      'external:profile': 'profile',
    });
  });
});
