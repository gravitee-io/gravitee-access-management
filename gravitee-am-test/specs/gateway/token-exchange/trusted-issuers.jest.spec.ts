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
import { getDomain, waitForOidcReady } from '@management-commands/domain-management-commands';
import { listTrustDomains } from '@management-commands/trust-domain-management-commands';
import { TrustDomain } from '@management-models/TrustDomain';
import { setup } from '../../test-fixture';
import { setupTrustedIssuerFixture, TrustedIssuerFixture, patchDomainRaw } from './fixtures/trusted-issuer-fixture';
import { signJwtForTrustedIssuer } from './fixtures/trusted-issuer-jwt-helper';
import { TOKEN_EXCHANGE_TEST } from './fixtures/token-exchange-fixture';

setup(120000);

let fixture: TrustedIssuerFixture;

const baselineTrustedIssuer = () => ({
  issuer: fixture.externalIssuer,
  keyResolutionMethod: 'PEM',
  certificate: fixture.trustedKey.certificatePem,
  scopeMappings: { 'external:read': 'openid', 'external:profile': 'profile' },
});

const writeTrustedIssuers = async (trustedIssuers: Array<Record<string, unknown>>) => {
  const { domain, accessToken } = fixture;
  await waitForSyncAfter(domain.id, () =>
    patchDomainRaw(domain.id, accessToken, {
      tokenExchangeSettings: {
        enabled: true,
        allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
        allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
        allowImpersonation: true,
        allowDelegation: true,
        allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
        maxDelegationDepth: 3,
        trustedIssuers,
      },
    }).expect(200),
  );
  await waitForOidcReady(domain.hrid);
};

const restoreBaseline = () => writeTrustedIssuers([baselineTrustedIssuer()]);

beforeAll(async () => {
  fixture = await setupTrustedIssuerFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Trusted Issuers - Impersonation with external JWT', () => {
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
        `&subject_token=${encodeURIComponent(externalJwt)}` +
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

    const externalJwt = signJwtForTrustedIssuer({
      issuer: 'https://unknown-issuer.example.com',
      privateKeyPem: trustedKey.privateKeyPem,
      subject: 'external-user-123',
      payload: { scope: 'openid' },
    });

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${encodeURIComponent(externalJwt)}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:jwt`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toContain('Untrusted issuer: https://unknown-issuer.example.com');
  });

  it('should exchange external JWT with kid in header when PEM trusted issuer configured', async () => {
    const { oidc, basicAuth, trustedKey, externalIssuer } = fixture;

    const externalJwt = signJwtForTrustedIssuer({
      issuer: externalIssuer,
      privateKeyPem: trustedKey.privateKeyPem,
      subject: 'external-user-with-kid',
      payload: { scope: 'external:read external:profile' },
      kid: 'cert-uuid-simulating-am',
    });

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${encodeURIComponent(externalJwt)}` +
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

  it('should reject an external JWT with an invalid signature', async () => {
    const { oidc, basicAuth, untrustedKey, externalIssuer } = fixture;

    // Sign with untrusted key (different from the PEM configured for this issuer)
    const externalJwt = signJwtForTrustedIssuer({
      issuer: externalIssuer,
      privateKeyPem: untrustedKey.privateKeyPem,
      subject: 'external-user-123',
      payload: { scope: 'openid' },
    });

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${encodeURIComponent(externalJwt)}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:jwt`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toBe('The presented token is invalid');
  });
});

describe('Trusted Issuers - Scope mapping', () => {
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
        `&subject_token=${encodeURIComponent(externalJwt)}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:jwt` +
        `&scope=openid%20profile`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    const decoded = parseJwt(response.body.access_token);
    const scopes = (decoded.payload['scope'] as string).split(' ');
    expect(scopes.sort()).toEqual(['openid', 'profile']);
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
        `&subject_token=${encodeURIComponent(externalJwt)}` +
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
    expect(scopes.sort()).toEqual(['openid', 'profile']);
  });
});

describe('Trusted Issuers - Delegation with external subject token', () => {
  it('should reject JWT actor token when subject is from an external trusted issuer', async () => {
    const { oidc, basicAuth, trustedKey, externalIssuer } = fixture;

    const externalSubjectJwt = signJwtForTrustedIssuer({
      issuer: externalIssuer,
      privateKeyPem: trustedKey.privateKeyPem,
      subject: 'external-user-delegation',
      payload: { scope: 'external:read' },
    });

    // Sign an actor JWT (same external issuer -- should be rejected regardless)
    const actorJwt = signJwtForTrustedIssuer({
      issuer: externalIssuer,
      privateKeyPem: trustedKey.privateKeyPem,
      subject: 'actor-service',
      payload: { scope: 'openid' },
    });

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${encodeURIComponent(externalSubjectJwt)}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:jwt` +
        `&actor_token=${encodeURIComponent(actorJwt)}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:jwt`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toBeDefined();
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
        `&subject_token=${encodeURIComponent(externalSubjectJwt)}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:jwt` +
        `&actor_token=${encodeURIComponent(domainTokens.accessToken)}` +
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

describe('Trusted Issuers - Domain-issued tokens (no regression)', () => {
  it('should still exchange domain-issued access tokens', async () => {
    const { oidc, basicAuth } = fixture;
    const domainTokens = await fixture.obtainSubjectToken('openid%20profile');

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${encodeURIComponent(domainTokens.accessToken)}` +
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
        `&subject_token=${encodeURIComponent(domainTokens.idToken)}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:id_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    expect(response.body.access_token).toBeDefined();
    const subjectDecoded = parseJwt(domainTokens.idToken);
    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['sub']).toBe(subjectDecoded.payload['sub']);
  });
});

describe('Trusted Issuers - User Binding', () => {
  const EMAIL_BINDING_CRITERIA = [{ attribute: 'emails.value', expression: "{#token['email']}" }];

  const writeUserBinding = (userBindingEnabled: boolean, userBindingCriteria?: Array<{ attribute: string; expression: string }>) =>
    writeTrustedIssuers([
      {
        ...baselineTrustedIssuer(),
        userBindingEnabled,
        ...(userBindingCriteria?.length && { userBindingCriteria }),
      },
    ]);

  afterAll(async () => {
    await restoreBaseline();
  });

  it('should use domain user when user binding is enabled and email matches', async () => {
    const { oidc, basicAuth, externalIssuer, user } = fixture;

    // Discover the domain user's actual sub by parsing a domain-issued token
    const domainTokens = await fixture.obtainSubjectToken('openid');
    const domainUserSub = parseJwt(domainTokens.accessToken).payload['sub'] as string;

    await writeUserBinding(true, EMAIL_BINDING_CRITERIA);

    // Sign external JWT with matching email (from fixture user created by buildCreateAndTestUser)
    const externalJwt = fixture.signExternalJwt({
      sub: 'external-user-bound',
      email: user.email,
      scope: 'external:read external:profile',
      iss: externalIssuer,
    });

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${encodeURIComponent(externalJwt)}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:jwt`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    expect(response.body.access_token).toBeDefined();

    // The minted token should use the domain user's subject, not the external JWT's sub
    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['sub']).toBe(domainUserSub);
  });

  it('should reject when user binding is enabled but no matching domain user found', async () => {
    const { oidc, basicAuth, externalIssuer } = fixture;

    await writeUserBinding(true, EMAIL_BINDING_CRITERIA);

    const externalJwt = fixture.signExternalJwt({
      sub: 'external-user-no-match',
      email: 'nonexistent@example.com',
      scope: 'external:read external:profile',
      iss: externalIssuer,
    });

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${encodeURIComponent(externalJwt)}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:jwt`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toContain('No domain user found for token binding');
  });

  it('should use synthetic user when user binding is disabled', async () => {
    const { oidc, basicAuth, externalIssuer } = fixture;

    await writeUserBinding(false);

    const externalJwt = fixture.signExternalJwt({
      sub: 'external-user-synthetic',
      email: 'nonexistent@example.com',
      scope: 'external:read external:profile',
      iss: externalIssuer,
    });

    // With binding disabled, any external JWT should succeed (synthetic user, no domain lookup)
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${encodeURIComponent(externalJwt)}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:jwt`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    expect(response.body.access_token).toBeDefined();
    const decoded = parseJwt(response.body.access_token);
    expect(decoded.payload['sub']).toBe('external-user-synthetic');
  });
});

describe('Trusted Issuers - Deprecated list is a projection over trusted domains', () => {
  const REPLACEMENT_ISSUER = 'https://replacement-idp.example.com';

  const replacementIssuer = () => ({
    issuer: REPLACEMENT_ISSUER,
    keyResolutionMethod: 'PEM',
    certificate: fixture.untrustedKey.certificatePem,
  });

  const signReplacementJwt = () =>
    signJwtForTrustedIssuer({
      issuer: REPLACEMENT_ISSUER,
      privateKeyPem: fixture.untrustedKey.privateKeyPem,
      subject: 'external-user-456',
      payload: { scope: 'openid' },
    });

  const trustedIssuerDomains = (trustDomains: TrustDomain[]) => trustDomains.filter((trustDomain) => trustDomain.issuer != null);

  const exchangeExternalJwt = (externalJwt: string) => {
    const { oidc, basicAuth } = fixture;
    return performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${encodeURIComponent(externalJwt)}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:jwt`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    );
  };

  afterAll(async () => {
    await restoreBaseline();
  });

  it('should back a written issuer with a trusted domain carrying an issuer matcher', async () => {
    const { domain, accessToken, externalIssuer } = fixture;

    await writeTrustedIssuers([baselineTrustedIssuer()]);

    const issuerDomains = trustedIssuerDomains(await listTrustDomains(domain.id, accessToken));
    expect(issuerDomains).toHaveLength(1);
    expect(issuerDomains[0].issuer).toBe(externalIssuer);
    expect(issuerDomains[0].spiffeTrustDomain).toBeUndefined();
    expect(issuerDomains[0].keyMaterial.source.toUpperCase()).toBe('PEM');

    const projected = await getDomain(domain.id, accessToken);
    expect(projected.tokenExchangeSettings.trustedIssuers).toHaveLength(1);
    expect(projected.tokenExchangeSettings.trustedIssuers[0].issuer).toBe(externalIssuer);
  });

  it('should stop trusting an issuer omitted from a written list', async () => {
    const { domain, accessToken, externalIssuer } = fixture;

    await writeTrustedIssuers([baselineTrustedIssuer()]);
    await writeTrustedIssuers([replacementIssuer()]);

    expect(trustedIssuerDomains(await listTrustDomains(domain.id, accessToken))).toHaveLength(1);

    const omittedIssuerJwt = fixture.signExternalJwt({
      sub: 'external-user-123',
      scope: 'external:read',
      iss: externalIssuer,
    });

    const rejected = await exchangeExternalJwt(omittedIssuerJwt).expect(400);
    expect(rejected.body.error).toBe('invalid_request');
    expect(rejected.body.error_description).toContain(`Untrusted issuer: ${externalIssuer}`);

    await exchangeExternalJwt(signReplacementJwt()).expect(200);
  });

  it('should stop trusting every issuer when an empty list is written', async () => {
    const { domain, accessToken } = fixture;

    await writeTrustedIssuers([replacementIssuer()]);
    await writeTrustedIssuers([]);

    expect(trustedIssuerDomains(await listTrustDomains(domain.id, accessToken))).toHaveLength(0);

    const rejected = await exchangeExternalJwt(signReplacementJwt()).expect(400);
    expect(rejected.body.error_description).toBe('The presented token is invalid');
  });
});
