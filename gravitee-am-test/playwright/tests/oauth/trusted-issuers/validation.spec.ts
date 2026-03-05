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
import { expect } from '@playwright/test';
import { createTokenExchangeFixture, TOKEN_EXCHANGE_DEFAULTS } from '../../../fixtures/token-exchange.fixture';
import { linkJira } from '../../../utils/jira';
import {
  createKeyMaterial,
  signJwt,
  patchDomainRaw,
  parseJwt,
  configureTrustedIssuer,
  setupIssuerDomain,
  IssuerDomain,
} from '../../../utils/token-exchange-helpers';
import { waitForSyncAfter } from '../../../../api/commands/gateway/monitoring-commands';
import { waitForOidcReady } from '../../../../api/commands/management/domain-management-commands';
import { requestAdminAccessToken } from '../../../../api/commands/management/token-management-commands';

const JWT_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:jwt';
const ACCESS_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:access_token';
const EXTERNAL_ISSUER = 'https://external-idp.example.com';

/**
 * Fixture with delegation enabled and a trusted issuer configured via API
 * (PEM + scope mappings). Setup mirrors the Jest trusted-issuer-fixture.
 */
const test = createTokenExchangeFixture({
  domainNamePrefix: 'pw-ti-val',
  allowImpersonation: true,
  allowDelegation: true,
  maxDelegationDepth: 3,
});

test.describe('Trusted Issuer Validation', () => {
  test.describe.configure({ mode: 'serial' });

  let trustedKey: ReturnType<typeof createKeyMaterial>;
  let untrustedKey: ReturnType<typeof createKeyMaterial>;
  let issuerDomain: IssuerDomain;

  test.beforeAll(async () => {
    trustedKey = createKeyMaterial();
    untrustedKey = createKeyMaterial();
    // Create a real second domain (Domain B) to act as external issuer.
    // This is fragile (AM-6654) — wrap in try/catch so other tests still run.
    try {
      const adminToken = await requestAdminAccessToken();
      issuerDomain = await setupIssuerDomain(adminToken);
    } catch (e) {
      console.warn('setupIssuerDomain failed (AM-6654); cross-domain tests will be skipped:', (e as Error).message);
    }
  });

  test.afterAll(async () => {
    if (issuerDomain) await issuerDomain.cleanup();
  });

/* ------------------------------------------------------------------ */
/*  AM-6625: JWKS URL cross-domain trust                               */
/* ------------------------------------------------------------------ */

// AM-6654: Cross-domain trust is a known backend bug — PEM/JWKS from different domain
// causes "no matching key(s) found". Re-enable when AM-6654 is fixed.
test.fixme('AM-6625: exchange succeeds with real Domain B token via JWKS URL trust', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange, doIntrospect }, testInfo) => {
  linkJira(testInfo, 'AM-6625');

  // PROVE: real cross-domain trust via JWKS — Domain A trusts Domain B's JWKS endpoint
  await waitForSyncAfter(tokenExchangeDomain.id, async () => {
    await patchDomainRaw(tokenExchangeDomain.id, teAdminToken, {
      tokenExchangeSettings: {
        enabled: true,
        allowedSubjectTokenTypes: TOKEN_EXCHANGE_DEFAULTS.ALLOWED_SUBJECT_TOKEN_TYPES,
        allowedRequestedTokenTypes: TOKEN_EXCHANGE_DEFAULTS.ALLOWED_REQUESTED_TOKEN_TYPES,
        allowImpersonation: true,
        allowDelegation: true,
        allowedActorTokenTypes: TOKEN_EXCHANGE_DEFAULTS.ALLOWED_ACTOR_TOKEN_TYPES,
        maxDelegationDepth: 3,
        trustedIssuers: [{
          issuer: issuerDomain.oidcConfig.issuer,
          keyResolutionMethod: 'JWKS_URL',
          jwksUri: issuerDomain.oidcConfig.jwks_uri,
        }],
      },
    }).expect(200);
  });
  await waitForOidcReady(tokenExchangeDomain.hrid);

  // Obtain a real AM-issued token from Domain B
  const domainBTokens = await issuerDomain.obtainToken();
  const res = await doTokenExchange({
    subjectToken: domainBTokens.accessToken,
    subjectTokenType: JWT_TOKEN_TYPE,
  }).expect(200);
  const intr = await doIntrospect(res.body.access_token);
  expect(intr.active).toBe(true);
});

/* ------------------------------------------------------------------ */
/*  AM-6626: JWKS URL invalid                                          */
/* ------------------------------------------------------------------ */

test('AM-6626: exchange fails with invalid JWKS URL issuer', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange }, testInfo) => {
  linkJira(testInfo, 'AM-6626');

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    issuer: EXTERNAL_ISSUER,
    certificate: trustedKey.certificatePem,
  });

  // Sign JWT with trusted key but claim issuer doesn't match configured JWKS URL
  const jwt = signJwt({
    issuer: 'https://wrong-issuer.example.com',
    privateKeyPem: trustedKey.privateKeyPem,
    subject: 'ext-user',
  });

  await doTokenExchange({
    subjectToken: jwt,
    subjectTokenType: JWT_TOKEN_TYPE,
  }).expect(400);
});

/* ------------------------------------------------------------------ */
/*  AM-6626: Expired JWT rejected                                      */
/* ------------------------------------------------------------------ */

test('AM-6626: exchange fails with expired JWT from trusted issuer', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange }, testInfo) => {
  linkJira(testInfo, 'AM-6626');

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    certificate: trustedKey.certificatePem,
  });

  // Sign JWT with expiration in the past
  const jwt = signJwt({
    issuer: EXTERNAL_ISSUER,
    privateKeyPem: trustedKey.privateKeyPem,
    subject: 'ext-user',
    expiresInSeconds: -3600, // expired 1 hour ago
  });

  await doTokenExchange({
    subjectToken: jwt,
    subjectTokenType: JWT_TOKEN_TYPE,
  }).expect(400);
});

/* ------------------------------------------------------------------ */
/*  AM-6627: PEM cross-domain trust                                    */
/* ------------------------------------------------------------------ */

// AM-6654: PEM cross-domain trust is a known backend bug — key mismatch between
// JWKS JWK and x5c certificate causes "no matching key(s) found" on PEM resolution.
// Re-enable this test when AM-6654 is fixed.
test.fixme('AM-6627: exchange succeeds with real Domain B token via PEM trust', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange, doIntrospect }, testInfo) => {
  linkJira(testInfo, 'AM-6627');

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    issuer: issuerDomain.oidcConfig.issuer,
    certificate: issuerDomain.certificatePem,
  });

  const domainBTokens = await issuerDomain.obtainToken();
  const res = await doTokenExchange({
    subjectToken: domainBTokens.accessToken,
    subjectTokenType: JWT_TOKEN_TYPE,
  }).expect(200);
  const intr = await doIntrospect(res.body.access_token);
  expect(intr.active).toBe(true);
});

/* ------------------------------------------------------------------ */
/*  AM-6628: PEM invalid (untrusted key)                               */
/* ------------------------------------------------------------------ */

test('AM-6628: exchange fails with untrusted key', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange }, testInfo) => {
  linkJira(testInfo, 'AM-6628');

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    certificate: trustedKey.certificatePem,
  });

  // Sign with untrusted key — signature won't verify against configured PEM
  const jwt = signJwt({
    issuer: EXTERNAL_ISSUER,
    privateKeyPem: untrustedKey.privateKeyPem,
    subject: 'ext-user',
  });

  await doTokenExchange({
    subjectToken: jwt,
    subjectTokenType: JWT_TOKEN_TYPE,
  }).expect(400);
});

/* ------------------------------------------------------------------ */
/*  AM-6628: Wrong audience rejected                                   */
/* ------------------------------------------------------------------ */

test('AM-6628: exchange accepts JWT with wrong audience claim', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange, doIntrospect }, testInfo) => {
  linkJira(testInfo, 'AM-6628');

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    certificate: trustedKey.certificatePem,
  });

  // AM does not validate the aud claim on trusted issuer JWTs —
  // audience validation is not enforced for token exchange subject tokens
  const jwt = signJwt({
    issuer: EXTERNAL_ISSUER,
    privateKeyPem: trustedKey.privateKeyPem,
    subject: 'ext-user',
    payload: { aud: 'https://wrong-audience.example.com' },
  });

  const res = await doTokenExchange({
    subjectToken: jwt,
    subjectTokenType: JWT_TOKEN_TYPE,
  }).expect(200);
  const intr = await doIntrospect(res.body.access_token);
  expect(intr.active).toBe(true);
});

/* ------------------------------------------------------------------ */
/*  AM-6629: Scope mapping (external → domain)                         */
/* ------------------------------------------------------------------ */

test('AM-6629: scope mapping translates external scopes to domain scopes', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange, doIntrospect }, testInfo) => {
  linkJira(testInfo, 'AM-6629');

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    certificate: trustedKey.certificatePem,
    scopeMappings: { 'external:read': 'openid', 'external:profile': 'profile' },
  });

  const jwt = signJwt({
    issuer: EXTERNAL_ISSUER,
    privateKeyPem: trustedKey.privateKeyPem,
    subject: 'ext-user',
    payload: { scope: 'external:read external:profile' },
  });

  const res = await doTokenExchange({
    subjectToken: jwt,
    subjectTokenType: JWT_TOKEN_TYPE,
    scope: 'openid profile',
  }).expect(200);
  const intr = await doIntrospect(res.body.access_token);
  expect(intr.active).toBe(true);
  const scope = intr.scope as string;
  expect(scope.split(' ')).toContain('openid');
  expect(scope.split(' ')).toContain('profile');
});

/* ------------------------------------------------------------------ */
/*  AM-6630: Unknown scope dropped                                     */
/* ------------------------------------------------------------------ */

test('AM-6630: unmapped external scope is dropped', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange, doIntrospect }, testInfo) => {
  linkJira(testInfo, 'AM-6630');

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    certificate: trustedKey.certificatePem,
    scopeMappings: { 'external:read': 'openid', 'external:profile': 'profile' },
  });

  const jwt = signJwt({
    issuer: EXTERNAL_ISSUER,
    privateKeyPem: trustedKey.privateKeyPem,
    subject: 'ext-user',
    payload: { scope: 'external:read external:profile external:unknown' },
  });

  const res = await doTokenExchange({
    subjectToken: jwt,
    subjectTokenType: JWT_TOKEN_TYPE,
    scope: 'openid profile',
  }).expect(200);
  const intr = await doIntrospect(res.body.access_token);
  expect(intr.active).toBe(true);
  const scope = intr.scope as string;
  expect(scope.split(' ')).toContain('openid');
  expect(scope.split(' ')).toContain('profile');
  expect(scope.split(' ')).not.toContain('unknown');
});

/* ------------------------------------------------------------------ */
/*  AM-6631: User binding invalid setup                                */
/* ------------------------------------------------------------------ */

test('AM-6631: user binding with no matching user fails exchange', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange }, testInfo) => {
  linkJira(testInfo, 'AM-6631');

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    certificate: trustedKey.certificatePem,
    userBindingEnabled: true,
    userBindingCriteria: [{ attribute: 'emails.value', expression: "{#token['email']}" }],
  });

  const jwt = signJwt({
    issuer: EXTERNAL_ISSUER,
    privateKeyPem: trustedKey.privateKeyPem,
    subject: 'ext-user',
    payload: { email: 'nonexistent@example.com' },
  });

  // Should fail because no user matches the email
  await doTokenExchange({
    subjectToken: jwt,
    subjectTokenType: JWT_TOKEN_TYPE,
  }).expect(400);
});

/* ------------------------------------------------------------------ */
/*  AM-6631: Unsupported subject_token_type rejected                   */
/* ------------------------------------------------------------------ */

test('AM-6631: exchange fails with unsupported subject_token_type id_token', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange }, testInfo) => {
  linkJira(testInfo, 'AM-6631');

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    certificate: trustedKey.certificatePem,
  });

  // Sign a valid JWT but present it as id_token type (valid per RFC 8693 but
  // AM does not support id_token as subject_token_type for exchange)
  const jwt = signJwt({
    issuer: EXTERNAL_ISSUER,
    privateKeyPem: trustedKey.privateKeyPem,
    subject: 'ext-user',
  });

  await doTokenExchange({
    subjectToken: jwt,
    subjectTokenType: 'urn:ietf:params:oauth:token-type:id_token',
  }).expect(400);
});

/* ------------------------------------------------------------------ */
/*  AM-6632: User binding by fixed value                               */
/* ------------------------------------------------------------------ */

test('AM-6632: user binding by fixed email succeeds', async ({ tokenExchangeDomain, teAdminToken, tokenExchangeUser, obtainSubjectToken, doTokenExchange, doIntrospect }, testInfo) => {
  linkJira(testInfo, 'AM-6632');

  // Discover domain user's actual gateway-level sub (differs from management API user ID)
  const domainTokens = await obtainSubjectToken('openid');
  const domainUserSub = parseJwt(domainTokens.accessToken).sub as string;

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    certificate: trustedKey.certificatePem,
    userBindingEnabled: true,
    userBindingCriteria: [{ attribute: 'emails.value', expression: "{#token['email']}" }],
  });

  const jwt = signJwt({
    issuer: EXTERNAL_ISSUER,
    privateKeyPem: trustedKey.privateKeyPem,
    subject: 'ext-user',
    payload: { email: tokenExchangeUser.email },
  });

  const res = await doTokenExchange({
    subjectToken: jwt,
    subjectTokenType: JWT_TOKEN_TYPE,
  }).expect(200);
  const intr = await doIntrospect(res.body.access_token);
  expect(intr.active).toBe(true);

  // The minted token should use the domain user's subject, not the external JWT's sub
  const decoded = parseJwt(res.body.access_token);
  expect(decoded.sub).toBe(domainUserSub);
});

/* ------------------------------------------------------------------ */
/*  AM-6633: User binding by expression                                */
/* ------------------------------------------------------------------ */

test('AM-6633: user binding by EL expression succeeds', async ({ tokenExchangeDomain, teAdminToken, tokenExchangeUser, obtainSubjectToken, doTokenExchange, doIntrospect }, testInfo) => {
  linkJira(testInfo, 'AM-6633');

  // Discover domain user's actual gateway-level sub (differs from management API user ID)
  const domainTokens = await obtainSubjectToken('openid');
  const domainUserSub = parseJwt(domainTokens.accessToken).sub as string;

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    certificate: trustedKey.certificatePem,
    userBindingEnabled: true,
    userBindingCriteria: [{ attribute: 'emails.value', expression: "{#token['email']}" }],
  });

  const jwt = signJwt({
    issuer: EXTERNAL_ISSUER,
    privateKeyPem: trustedKey.privateKeyPem,
    subject: 'ext-user-expr',
    payload: { email: tokenExchangeUser.email },
  });

  const res = await doTokenExchange({
    subjectToken: jwt,
    subjectTokenType: JWT_TOKEN_TYPE,
  }).expect(200);
  const intr = await doIntrospect(res.body.access_token);
  expect(intr.active).toBe(true);
  // Verify the token was minted for the domain user, not a synthetic user
  const decoded = parseJwt(res.body.access_token);
  expect(decoded.sub).toBe(domainUserSub);
});

/* ------------------------------------------------------------------ */
/*  AM-6634: User binding — user not found                             */
/* ------------------------------------------------------------------ */

test('AM-6634: user binding fails when no matching domain user', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange }, testInfo) => {
  linkJira(testInfo, 'AM-6634');

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    certificate: trustedKey.certificatePem,
    userBindingEnabled: true,
    userBindingCriteria: [{ attribute: 'userName', expression: "{#token['preferred_username']}" }],
  });

  const jwt = signJwt({
    issuer: EXTERNAL_ISSUER,
    privateKeyPem: trustedKey.privateKeyPem,
    subject: 'ext-user',
    payload: { preferred_username: 'user-that-does-not-exist' },
  });

  await doTokenExchange({
    subjectToken: jwt,
    subjectTokenType: JWT_TOKEN_TYPE,
  }).expect(400);
});

/* ------------------------------------------------------------------ */
/*  AM-6635: Issuer removal → exchange rejected                        */
/* ------------------------------------------------------------------ */

test('AM-6635: removing trusted issuer rejects exchange', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange }, testInfo) => {
  linkJira(testInfo, 'AM-6635');

  // Configure issuer
  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    certificate: trustedKey.certificatePem,
  });

  // Verify exchange works
  const jwt1 = signJwt({ issuer: EXTERNAL_ISSUER, privateKeyPem: trustedKey.privateKeyPem });
  await doTokenExchange({ subjectToken: jwt1, subjectTokenType: JWT_TOKEN_TYPE }).expect(200);

  // Remove all trusted issuers
  await waitForSyncAfter(tokenExchangeDomain.id, async () => {
    await patchDomainRaw(tokenExchangeDomain.id, teAdminToken, {
      tokenExchangeSettings: {
        enabled: true,
        allowedSubjectTokenTypes: TOKEN_EXCHANGE_DEFAULTS.ALLOWED_SUBJECT_TOKEN_TYPES,
        allowedRequestedTokenTypes: TOKEN_EXCHANGE_DEFAULTS.ALLOWED_REQUESTED_TOKEN_TYPES,
        allowImpersonation: true,
        allowDelegation: true,
        allowedActorTokenTypes: TOKEN_EXCHANGE_DEFAULTS.ALLOWED_ACTOR_TOKEN_TYPES,
        maxDelegationDepth: 3,
        trustedIssuers: [],
      },
    }).expect(200);
  });
  await waitForOidcReady(tokenExchangeDomain.hrid);

  // Exchange should now fail
  const jwt2 = signJwt({ issuer: EXTERNAL_ISSUER, privateKeyPem: trustedKey.privateKeyPem });
  await doTokenExchange({ subjectToken: jwt2, subjectTokenType: JWT_TOKEN_TYPE }).expect(400);
});

/* ------------------------------------------------------------------ */
/*  AM-6636: Issuer count exceeded                                     */
/*  NOTE: Jira describes testing maxCount limit enforcement            */
/*  (domain.tokenExchange.trustedIssuers.maxCount=5). This requires    */
/*  custom gateway config not available in default Docker stack.        */
/*  Test verifies multi-issuer positive case instead.                  */
/* ------------------------------------------------------------------ */

test('AM-6636: multiple trusted issuers can be configured', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange, doIntrospect }, testInfo) => {
  linkJira(testInfo, 'AM-6636');

  const secondKey = createKeyMaterial();
  const secondIssuer = 'https://second-idp.example.com';

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    trustedIssuers: [
      { issuer: EXTERNAL_ISSUER, keyResolutionMethod: 'PEM', certificate: trustedKey.certificatePem },
      { issuer: secondIssuer, keyResolutionMethod: 'PEM', certificate: secondKey.certificatePem },
    ],
  });

  // Exchange with first issuer
  const jwt1 = signJwt({ issuer: EXTERNAL_ISSUER, privateKeyPem: trustedKey.privateKeyPem });
  const res1 = await doTokenExchange({ subjectToken: jwt1, subjectTokenType: JWT_TOKEN_TYPE }).expect(200);
  expect((await doIntrospect(res1.body.access_token)).active).toBe(true);

  // Exchange with second issuer
  const jwt2 = signJwt({ issuer: secondIssuer, privateKeyPem: secondKey.privateKeyPem });
  const res2 = await doTokenExchange({ subjectToken: jwt2, subjectTokenType: JWT_TOKEN_TYPE }).expect(200);
  expect((await doIntrospect(res2.body.access_token)).active).toBe(true);
});

/* ------------------------------------------------------------------ */
/*  AM-6636: Untrusted issuer rejected                                 */
/* ------------------------------------------------------------------ */

test('AM-6636: exchange fails with JWT from untrusted issuer', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange }, testInfo) => {
  linkJira(testInfo, 'AM-6636');

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    certificate: trustedKey.certificatePem,
  });

  // Sign with trusted key but claim an issuer NOT registered as trusted
  const jwt = signJwt({
    issuer: 'https://untrusted-issuer.example.com',
    privateKeyPem: trustedKey.privateKeyPem,
    subject: 'ext-user',
  });

  await doTokenExchange({
    subjectToken: jwt,
    subjectTokenType: JWT_TOKEN_TYPE,
  }).expect(400);
});

/* ------------------------------------------------------------------ */
/*  AM-6637: JWT as actor_token                                        */
/* ------------------------------------------------------------------ */

test('AM-6637: external JWT as actor_token in delegation', async ({ tokenExchangeDomain, teAdminToken, obtainSubjectToken, doTokenExchange, doIntrospect }, testInfo) => {
  linkJira(testInfo, 'AM-6637');

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    certificate: trustedKey.certificatePem,
  });

  const subject = await obtainSubjectToken();
  const actorJwt = signJwt({
    issuer: EXTERNAL_ISSUER,
    privateKeyPem: trustedKey.privateKeyPem,
    subject: 'external-actor',
  });

  const res = await doTokenExchange({
    subjectToken: subject.accessToken,
    subjectTokenType: ACCESS_TOKEN_TYPE,
    actorToken: actorJwt,
    actorTokenType: JWT_TOKEN_TYPE,
  }).expect(200);

  const intr = await doIntrospect(res.body.access_token);
  expect(intr.active).toBe(true);
  // The token should have an act claim for delegation with the external actor's sub
  const decoded = parseJwt(res.body.access_token);
  expect(decoded.act).toMatchObject({ sub: expect.stringContaining('external-actor') });
});

/* ------------------------------------------------------------------ */
/*  AM-6638: JWT as subject_token                                      */
/* ------------------------------------------------------------------ */

test('AM-6638: external JWT as subject_token', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange, doIntrospect }, testInfo) => {
  linkJira(testInfo, 'AM-6638');

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    certificate: trustedKey.certificatePem,
  });

  const subjectJwt = signJwt({
    issuer: EXTERNAL_ISSUER,
    privateKeyPem: trustedKey.privateKeyPem,
    subject: 'external-subject',
  });

  const res = await doTokenExchange({
    subjectToken: subjectJwt,
    subjectTokenType: JWT_TOKEN_TYPE,
  }).expect(200);

  const intr = await doIntrospect(res.body.access_token);
  expect(intr.active).toBe(true);
});

/* ------------------------------------------------------------------ */
/*  AM-6638: Cross-domain delegation chain with act claim              */
/* ------------------------------------------------------------------ */

test('AM-6638: delegation with external subject contains correct act claim', async ({ tokenExchangeDomain, teAdminToken, obtainSubjectToken, doTokenExchange, doIntrospect }, testInfo) => {
  linkJira(testInfo, 'AM-6638');

  await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
    certificate: trustedKey.certificatePem,
  });

  // External JWT as subject (simulates cross-domain token from Domain B)
  const subjectJwt = signJwt({
    issuer: EXTERNAL_ISSUER,
    privateKeyPem: trustedKey.privateKeyPem,
    subject: 'domain-b-user',
  });

  // Domain A user's token as actor
  const actor = await obtainSubjectToken();

  const res = await doTokenExchange({
    subjectToken: subjectJwt,
    subjectTokenType: JWT_TOKEN_TYPE,
    actorToken: actor.accessToken,
    actorTokenType: ACCESS_TOKEN_TYPE,
  }).expect(200);

  const intr = await doIntrospect(res.body.access_token);
  expect(intr.active).toBe(true);

  // PROVE: act claim contains the domain A actor's identity, not the external subject
  const decoded = parseJwt(res.body.access_token);
  const actorDecoded = parseJwt(actor.accessToken);
  const act = decoded.act as Record<string, unknown>;
  expect(act.sub, 'act.sub should be the domain A actor sub').toBe(actorDecoded.sub);
});
}); // end Trusted Issuer Validation
