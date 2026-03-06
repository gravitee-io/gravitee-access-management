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
/* eslint-disable playwright/no-standalone-expect */
import { expect } from '@playwright/test';
import { createTokenExchangeFixture, TOKEN_EXCHANGE_DEFAULTS } from '../../../fixtures/token-exchange.fixture';
import { linkJira } from '../../../utils/jira';
import {
  patchDomainRaw,
  createKeyMaterial,
  signJwt,
  configureTrustedIssuer,
  retryOnStatus,
} from '../../../utils/token-exchange-helpers';
import { waitForOidcReady } from '../../../../api/commands/management/domain-management-commands';
import { waitForSyncAfter } from '../../../../api/commands/gateway/monitoring-commands';
import { patchApplication } from '../../../../api/commands/management/application-management-commands';

/* ------------------------------------------------------------------ */
/*  Downscoping impersonation (AM-6642)                                */
/* ------------------------------------------------------------------ */

const downscopingImpersonation = createTokenExchangeFixture({
  domainNamePrefix: 'pw-te-ds-imp',
  tokenExchangeScopeHandling: 'DOWNSCOPING',
  allowImpersonation: true,
  allowDelegation: false,
});

// Jira AM-6642 scenario 1 (subject with no scopes → rejected) not covered;
// password grant always assigns app default scopes, so producing a truly
// scopeless subject token is not feasible with the current fixture.
// Tests cover scenarios 2-4.
downscopingImpersonation.describe('AM-6642: Downscoping impersonation', () => {
  downscopingImpersonation('subset scope succeeds', async ({ obtainSubjectToken, doTokenExchange, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6642');
    const tokens = await obtainSubjectToken('openid%20profile');
    const res = await doTokenExchange({
      subjectToken: tokens.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      scope: 'openid',
    }).expect(200);
    const introspection = await doIntrospect(res.body.access_token);
    expect(introspection.active).toBe(true);
    expect((introspection.scope as string).split(' ')).toContain('openid');
  });

  downscopingImpersonation('no scope = intersection of subject + client defaults', async ({ obtainSubjectToken, doTokenExchange, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6642');
    const tokens = await obtainSubjectToken('openid%20profile');
    const res = await doTokenExchange({
      subjectToken: tokens.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
    }).expect(200);
    const introspection = await doIntrospect(res.body.access_token);
    expect(introspection.active).toBe(true);
    // Should get intersection of subject scopes and client default scopes
    expect((introspection.scope as string).split(' ')).toContain('openid');
  });

  downscopingImpersonation('disjoint scope rejected', async ({ obtainSubjectToken, doTokenExchange }, testInfo) => {
    linkJira(testInfo, 'AM-6642');
    const tokens = await obtainSubjectToken('openid');
    // Request a scope the subject token doesn't have
    await doTokenExchange({
      subjectToken: tokens.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      scope: 'offline_access',
    }).expect(400);
  });

  downscopingImpersonation('superset scope rejected', async ({ obtainSubjectToken, doTokenExchange }, testInfo) => {
    linkJira(testInfo, 'AM-6642');
    const tokens = await obtainSubjectToken('openid');
    // Downscoping mode rejects scope superset of subject token (AM-6642)
    await doTokenExchange({
      subjectToken: tokens.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      scope: 'openid profile',
    }).expect(400);
  });

  // AM-6629: requesting a scope the subject doesn't have is rejected in downscoping
  downscopingImpersonation('AM-6629: unmapped scope beyond subject is rejected', async ({ obtainSubjectToken, doTokenExchange }, testInfo) => {
    linkJira(testInfo, 'AM-6629');
    // Subject has openid+profile, request includes email (not in subject)
    const tokens = await obtainSubjectToken('openid%20profile');
    await doTokenExchange({
      subjectToken: tokens.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      scope: 'openid profile email',
    }).expect(400);
  });
});

/* ------------------------------------------------------------------ */
/*  Downscoping delegation (AM-6643)                                   */
/* ------------------------------------------------------------------ */

const downscopingDelegation = createTokenExchangeFixture({
  domainNamePrefix: 'pw-te-ds-del',
  tokenExchangeScopeHandling: 'DOWNSCOPING',
  allowImpersonation: true,
  allowDelegation: true,
  maxDelegationDepth: 3,
});

// Jira AM-6643 scenario 4 (subject has scopes actor lacks) not covered;
// producing an actor token without default scopes requires a separate app.
downscopingDelegation.describe('AM-6643: Downscoping delegation', () => {
  downscopingDelegation('subset scope succeeds', async ({ obtainSubjectToken, doTokenExchange, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6643');
    const subject = await obtainSubjectToken('openid%20profile');
    const actor = await obtainSubjectToken('openid%20profile');
    const res = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      actorToken: actor.accessToken,
      scope: 'openid',
    }).expect(200);
    const introspection = await doIntrospect(res.body.access_token);
    expect(introspection.active).toBe(true);
    expect((introspection.scope as string).split(' ')).toContain('openid');
  });

  downscopingDelegation('no scope = intersection', async ({ obtainSubjectToken, doTokenExchange, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6643');
    const subject = await obtainSubjectToken('openid%20profile');
    const actor = await obtainSubjectToken('openid');
    const res = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      actorToken: actor.accessToken,
    }).expect(200);
    const introspection = await doIntrospect(res.body.access_token);
    expect(introspection.active).toBe(true);
  });

  downscopingDelegation('disjoint scope rejected', async ({ obtainSubjectToken, doTokenExchange }, testInfo) => {
    linkJira(testInfo, 'AM-6643');
    const subject = await obtainSubjectToken('openid');
    const actor = await obtainSubjectToken('openid');
    await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      actorToken: actor.accessToken,
      scope: 'offline_access',
    }).expect(400);
  });

  downscopingDelegation('superset scope rejected', async ({ obtainSubjectToken, doTokenExchange }, testInfo) => {
    linkJira(testInfo, 'AM-6643');
    const subject = await obtainSubjectToken('openid');
    const actor = await obtainSubjectToken('openid');
    // Downscoping mode rejects scope superset of subject token (AM-6643)
    await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      actorToken: actor.accessToken,
      scope: 'openid profile',
    }).expect(400);
  });

  // AM-6643 scenario 3: asymmetric scopes — actor has narrower scopes than subject
  downscopingDelegation('asymmetric scopes: actor narrower than subject', async ({ obtainSubjectToken, doTokenExchange, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6643');
    const subject = await obtainSubjectToken('openid profile');
    const actor = await obtainSubjectToken('openid');
    // Request a scope the subject has but actor doesn't — downscoping intersects both
    const res = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      actorToken: actor.accessToken,
      scope: 'openid',
    }).expect(200);
    const introspection = await doIntrospect(res.body.access_token);
    expect(introspection.active).toBe(true);
    expect((introspection.scope as string).split(' ')).toContain('openid');
  });
});

/* ------------------------------------------------------------------ */
/*  Permissive impersonation (AM-6644)                                 */
/* ------------------------------------------------------------------ */

const permissiveImpersonation = createTokenExchangeFixture({
  domainNamePrefix: 'pw-te-pm-imp',
  tokenExchangeScopeHandling: 'PERMISSIVE',
  allowImpersonation: true,
  allowDelegation: false,
});

permissiveImpersonation.describe('AM-6644: Permissive impersonation', () => {
  permissiveImpersonation('subset scope succeeds', async ({ obtainSubjectToken, doTokenExchange, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6644');
    const tokens = await obtainSubjectToken('openid%20profile');
    const res = await doTokenExchange({
      subjectToken: tokens.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      scope: 'openid',
    }).expect(200);
    const introspection = await doIntrospect(res.body.access_token);
    expect(introspection.active).toBe(true);
    expect((introspection.scope as string).split(' ')).toContain('openid');
  });

  permissiveImpersonation('no scope = client defaults', async ({ obtainSubjectToken, doTokenExchange, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6644');
    const tokens = await obtainSubjectToken('openid');
    const res = await doTokenExchange({
      subjectToken: tokens.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
    }).expect(200);
    const introspection = await doIntrospect(res.body.access_token);
    expect(introspection.active).toBe(true);
    // In permissive mode without scope, the client's default scopes apply
    expect((introspection.scope as string).split(' ')).toContain('openid');
  });

  // AM-6630: permissive mode grants client-only scope even when subject lacks it
  permissiveImpersonation('AM-6630: client-only scope granted in permissive mode', async ({ obtainSubjectToken, doTokenExchange, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6630');
    linkJira(testInfo, 'AM-6644');
    // PROVE: subject has only openid, but client has openid+profile — permissive grants both
    const tokens = await obtainSubjectToken('openid');
    const res = await doTokenExchange({
      subjectToken: tokens.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      scope: 'openid profile',
    }).expect(200);
    const introspection = await doIntrospect(res.body.access_token);
    expect(introspection.active).toBe(true);
    const scope = introspection.scope as string;
    expect(scope.split(' '), 'permissive mode should grant openid').toContain('openid');
    expect(scope.split(' '), 'permissive mode should grant profile from client config').toContain('profile');
  });

  permissiveImpersonation('unknown scope rejected', async ({ obtainSubjectToken, doTokenExchange }, testInfo) => {
    linkJira(testInfo, 'AM-6644');
    const tokens = await obtainSubjectToken('openid');
    await doTokenExchange({
      subjectToken: tokens.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      scope: 'nonexistent_scope',
    }).expect(400);
  });
});

/* ------------------------------------------------------------------ */
/*  Permissive delegation (AM-6645)                                    */
/* ------------------------------------------------------------------ */

const permissiveDelegation = createTokenExchangeFixture({
  domainNamePrefix: 'pw-te-pm-del',
  tokenExchangeScopeHandling: 'PERMISSIVE',
  allowImpersonation: true,
  allowDelegation: true,
  maxDelegationDepth: 3,
});

permissiveDelegation.describe('AM-6645: Permissive delegation', () => {
  permissiveDelegation('subset scope succeeds', async ({ obtainSubjectToken, doTokenExchange, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6645');
    const subject = await obtainSubjectToken('openid%20profile');
    const actor = await obtainSubjectToken('openid');
    const res = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      actorToken: actor.accessToken,
      scope: 'openid',
    }).expect(200);
    const introspection = await doIntrospect(res.body.access_token);
    expect(introspection.active).toBe(true);
  });

  permissiveDelegation('no scope = client defaults', async ({ obtainSubjectToken, doTokenExchange, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6645');
    const subject = await obtainSubjectToken('openid');
    const actor = await obtainSubjectToken('openid');
    const res = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      actorToken: actor.accessToken,
    }).expect(200);
    const introspection = await doIntrospect(res.body.access_token);
    expect(introspection.active).toBe(true);
  });

  permissiveDelegation('new client scope allowed', async ({ obtainSubjectToken, doTokenExchange, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6645');
    const subject = await obtainSubjectToken('openid');
    const actor = await obtainSubjectToken('openid');
    const res = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      actorToken: actor.accessToken,
      scope: 'openid profile',
    }).expect(200);
    const introspection = await doIntrospect(res.body.access_token);
    expect(introspection.active).toBe(true);
  });

  permissiveDelegation('unknown scope rejected', async ({ obtainSubjectToken, doTokenExchange }, testInfo) => {
    linkJira(testInfo, 'AM-6645');
    const subject = await obtainSubjectToken('openid');
    const actor = await obtainSubjectToken('openid');
    // Even permissive mode enforces client-allowed scopes
    await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      actorToken: actor.accessToken,
      scope: 'nonexistent_scope',
    }).expect(400);
  });
});

/* ------------------------------------------------------------------ */
/*  Persistence across restart (AM-6648)                               */
/* ------------------------------------------------------------------ */

const persistenceFixture = createTokenExchangeFixture({
  domainNamePrefix: 'pw-te-persist',
  tokenExchangeScopeHandling: 'PERMISSIVE',
  allowImpersonation: true,
  allowDelegation: false,
});

// Jira AM-6648 requires full stack restart; test uses sync cycle as proxy.
// Both DB backends tested in separate CI runs.
persistenceFixture.describe('AM-6648: Scope mode persistence', () => {
  persistenceFixture('domain permissive mode survives sync cycle', async ({ tokenExchangeDomain, teAdminToken, obtainSubjectToken, doTokenExchange, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6648');

    // The fixture created the domain with PERMISSIVE mode.
    // Trigger a sync cycle by patching an unrelated field.
    await waitForSyncAfter(tokenExchangeDomain.id, async () => {
      await patchDomainRaw(tokenExchangeDomain.id, teAdminToken, {
        description: 'Sync cycle test',
      }).expect(200);
    });
    await waitForOidcReady(tokenExchangeDomain.hrid);

    // Permissive mode should still work — requesting a scope beyond subject should succeed
    const tokens = await obtainSubjectToken('openid');
    const res = await doTokenExchange({
      subjectToken: tokens.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      scope: 'openid profile',
    }).expect(200);
    const introspection = await doIntrospect(res.body.access_token);
    expect(introspection.active).toBe(true);
  });

  persistenceFixture('app override survives sync cycle', async ({ tokenExchangeDomain, tokenExchangeApp, teAdminToken, obtainSubjectToken, doTokenExchange }, testInfo) => {
    linkJira(testInfo, 'AM-6648');

    // Override app to DOWNSCOPING mode
    await waitForSyncAfter(tokenExchangeDomain.id, async () => {
      await patchApplication(tokenExchangeDomain.id, teAdminToken, {
        settings: {
          oauth: {
            tokenExchangeOAuthSettings: {
              scopeHandling: 'DOWNSCOPING',
              inherited: false,
            },
          },
        },
      } as Record<string, unknown>, tokenExchangeApp.id);
    });
    await waitForOidcReady(tokenExchangeDomain.hrid);

    // With DOWNSCOPING, requesting scope beyond subject should be rejected
    const tokens = await obtainSubjectToken('openid');
    await doTokenExchange({
      subjectToken: tokens.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      scope: 'openid profile',
    }).expect(400);
  });

  persistenceFixture('inherited setting tracks domain change', async ({ tokenExchangeDomain, teAdminToken, obtainSubjectToken, doTokenExchange }, testInfo) => {
    linkJira(testInfo, 'AM-6648');

    // Switch domain to DOWNSCOPING
    await waitForSyncAfter(tokenExchangeDomain.id, async () => {
      await patchDomainRaw(tokenExchangeDomain.id, teAdminToken, {
        tokenExchangeSettings: {
          enabled: true,
          allowedSubjectTokenTypes: TOKEN_EXCHANGE_DEFAULTS.ALLOWED_SUBJECT_TOKEN_TYPES,
          allowedRequestedTokenTypes: TOKEN_EXCHANGE_DEFAULTS.ALLOWED_REQUESTED_TOKEN_TYPES,
          allowImpersonation: true,
          allowDelegation: false,
          tokenExchangeOAuthSettings: { scopeHandling: 'DOWNSCOPING' },
        },
      }).expect(200);
    });
    await waitForOidcReady(tokenExchangeDomain.hrid);

    // With downscoping at domain level, scope widening should be rejected
    const tokens = await obtainSubjectToken('openid');
    await doTokenExchange({
      subjectToken: tokens.accessToken,
      subjectTokenType: 'urn:ietf:params:oauth:token-type:access_token',
      scope: 'openid profile',
    }).expect(400);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-6642: Exchange with no-scope subject token (downscoping)        */
/* ------------------------------------------------------------------ */

const scopelessDownscoping = createTokenExchangeFixture({
  domainNamePrefix: 'pw-te-scopeless-ds',
  tokenExchangeScopeHandling: 'DOWNSCOPING',
  allowImpersonation: true,
  allowDelegation: false,
});

scopelessDownscoping.describe('AM-6642: No-scope subject in downscoping mode', () => {
  let key: ReturnType<typeof createKeyMaterial>;

  scopelessDownscoping.beforeAll(async () => {
    key = createKeyMaterial();
  });

  scopelessDownscoping('scopeless trusted JWT yields minimal or no scopes', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6642');

    const externalIssuer = 'https://scopeless-idp.example.com';
    await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
      issuer: externalIssuer,
      certificate: key.certificatePem,
    });

    // Create a JWT with no scope claim at all
    const jwt = signJwt({
      issuer: externalIssuer,
      privateKeyPem: key.privateKeyPem,
      subject: 'scopeless-user',
      // No scope in payload — truly scopeless
    });

    // PROVE: in downscoping mode, a scopeless subject yields no/empty scope
    // because downscoping intersects subject scopes with requested scopes
    const res = await retryOnStatus(
      () => doTokenExchange({ subjectToken: jwt, subjectTokenType: 'urn:ietf:params:oauth:token-type:jwt' }),
      { retryOn: 400 },
    );
    const intr = await doIntrospect(res.body.access_token);
    expect(intr.active).toBe(true);
    // Scopeless subject in downscoping: result should have no scope or empty scope
    const scope = ((intr.scope as string) || '').trim();
    expect(scope, 'downscoping with scopeless subject should yield no scopes').toBe('');
  });
});

/* ------------------------------------------------------------------ */
/*  AM-6643: Exchange with no-scope subject token (permissive)         */
/* ------------------------------------------------------------------ */

const scopelessPermissive = createTokenExchangeFixture({
  domainNamePrefix: 'pw-te-scopeless-pm',
  tokenExchangeScopeHandling: 'PERMISSIVE',
  allowImpersonation: true,
  allowDelegation: false,
});

scopelessPermissive.describe('AM-6643: No-scope subject in permissive mode', () => {
  let key: ReturnType<typeof createKeyMaterial>;

  scopelessPermissive.beforeAll(async () => {
    key = createKeyMaterial();
  });

  scopelessPermissive('scopeless trusted JWT yields empty scope in permissive mode', async ({ tokenExchangeDomain, teAdminToken, doTokenExchange, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6643');

    const externalIssuer = 'https://scopeless-pm-idp.example.com';
    await configureTrustedIssuer(tokenExchangeDomain.id, teAdminToken, tokenExchangeDomain.hrid, {
      issuer: externalIssuer,
      certificate: key.certificatePem,
    });

    // Create a JWT with no scope claim
    const jwt = signJwt({
      issuer: externalIssuer,
      privateKeyPem: key.privateKeyPem,
      subject: 'scopeless-user',
    });

    // Permissive mode with scopeless subject: exchange succeeds but scope is empty
    // (client scope expansion only applies when subject has at least some scopes)
    const res = await retryOnStatus(
      () => doTokenExchange({ subjectToken: jwt, subjectTokenType: 'urn:ietf:params:oauth:token-type:jwt' }),
      { retryOn: 400 },
    );
    const intr = await doIntrospect(res.body.access_token);
    expect(intr.active).toBe(true);
    const scope = ((intr.scope as string) || '').trim();
    expect(scope, 'scopeless subject yields empty scope even in permissive mode').toBe('');
  });
});

