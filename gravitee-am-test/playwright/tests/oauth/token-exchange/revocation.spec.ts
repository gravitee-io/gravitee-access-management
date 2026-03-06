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
import { obtainAuthorizationCodeTokens, waitForTokenInactive } from '../../../utils/auth-code-helpers';
import { exchangeToken } from '../../../utils/token-exchange-helpers';
import { revokeUserConsents } from '../../../../api/commands/management/user-management-commands';
import { API_USER_PASSWORD } from '../../../utils/test-constants';

const ACCESS_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:access_token';

/**
 * Shared fixture for revocation tests: delegation enabled with depth 5
 * to support chain/tree tests without creating multiple domains.
 */
const test = createTokenExchangeFixture({
  domainNamePrefix: 'pw-te-revoke',
  allowImpersonation: true,
  allowDelegation: true,
  maxDelegationDepth: 5,
});

/* ------------------------------------------------------------------ */
/*  AM-6615: Impersonation root revocation cascades                    */
/*  Jira includes refresh token cascade and fan-out scenarios not      */
/*  covered here; test validates access token cascade only.            */
/* ------------------------------------------------------------------ */

test.describe('AM-6615: Impersonation root revocation cascades', () => {
  test('revoking root invalidates impersonated token', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6615');

    const subject = await obtainSubjectToken();
    const exchanged = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);

    // Revoke the root subject token
    await revokeAndWait(subject.accessToken, 'access_token');

    // Exchanged token should now be inactive
    const introspection = await doIntrospect(exchanged.body.access_token);
    expect(introspection.active).toBe(false);
  });

  test('revoking root invalidates chain of impersonated tokens', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6615');

    const subject = await obtainSubjectToken();
    const first = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);
    const second = await doTokenExchange({
      subjectToken: first.body.access_token,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);

    await revokeAndWait(subject.accessToken, 'access_token');

    // All downstream tokens in the chain must be revoked (fan-out)
    const i1 = await doIntrospect(first.body.access_token);
    const i2 = await doIntrospect(second.body.access_token);
    expect(i1.active, 'first downstream token should be revoked after root revocation').toBe(false);
    expect(i2.active, 'second downstream token should be revoked after root revocation').toBe(false);
  });

  test('revoking root refresh token does not cascade to exchanged access tokens', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6615');

    const subject = await obtainSubjectToken('openid profile offline_access');
    expect(subject.refreshToken).toBeTruthy();

    const exchanged = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);

    // Revoke the refresh token
    await revokeAndWait(subject.refreshToken!, 'refresh_token');

    // Refresh token revocation does NOT cascade to exchanged access tokens —
    // only access_token revocation triggers cascade propagation
    const introspection = await doIntrospect(exchanged.body.access_token);
    expect(introspection.active, 'exchanged access token remains active after refresh token revocation').toBe(true);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-6616: Intermediate revocation + family isolation                */
/*  Jira includes refresh token isolation scenarios not covered here.  */
/* ------------------------------------------------------------------ */

test.describe('AM-6616: Intermediate revocation + family isolation', () => {
  test('revoking intermediate invalidates downstream only', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6616');

    const subject = await obtainSubjectToken();
    const first = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);
    const second = await doTokenExchange({
      subjectToken: first.body.access_token,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);

    // Revoke intermediate (first exchanged)
    await revokeAndWait(first.body.access_token, 'access_token');

    // Root should still be active
    const rootIntrospection = await doIntrospect(subject.accessToken);
    expect(rootIntrospection.active).toBe(true);

    // Downstream should be inactive
    const downstreamIntrospection = await doIntrospect(second.body.access_token);
    expect(downstreamIntrospection.active).toBe(false);
  });

  test('token exchange does not produce refresh_token', async ({ obtainSubjectToken, doTokenExchange }, testInfo) => {
    linkJira(testInfo, 'AM-6616');

    // Token exchange grant produces only access_tokens, not refresh_tokens
    const subject = await obtainSubjectToken('openid profile offline_access');
    const exchanged = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);
    expect(exchanged.body.refresh_token, 'token exchange should not produce a refresh_token').toBeFalsy();
  });

  test('sibling branches isolated', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6616');

    const subject = await obtainSubjectToken();
    // Branch A
    const branchA = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);
    // Branch B
    const branchB = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);

    // Revoke branch A
    await revokeAndWait(branchA.body.access_token, 'access_token');

    // Branch A inactive, branch B still active
    const intrA = await doIntrospect(branchA.body.access_token);
    expect(intrA.active).toBe(false);
    const intrB = await doIntrospect(branchB.body.access_token);
    expect(intrB.active).toBe(true);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-6617: Actor token revocation cascades through delegation chain  */
/*  Jira includes actor refresh token cascade not covered here.        */
/* ------------------------------------------------------------------ */

test.describe('AM-6617: Actor token revocation cascades', () => {
  test('revoking actor cascades to all delegated descendants', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6617');

    const subject = await obtainSubjectToken();
    const actor = await obtainSubjectToken();
    const d1 = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: actor.accessToken,
    }).expect(200);
    const d2 = await doTokenExchange({
      subjectToken: d1.body.access_token,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: actor.accessToken,
    }).expect(200);

    await revokeAndWait(actor.accessToken, 'access_token');

    // Subject stays active (not revoked)
    expect((await doIntrospect(subject.accessToken)).active).toBe(true);
    // Actor and all delegation descendants are inactive
    expect((await doIntrospect(actor.accessToken)).active).toBe(false);
    expect((await doIntrospect(d1.body.access_token)).active).toBe(false);
    expect((await doIntrospect(d2.body.access_token)).active).toBe(false);
  });

  test('outsider delegation chain unaffected by another actor revocation', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6617');

    // Chain 1
    const s1 = await obtainSubjectToken();
    const a1 = await obtainSubjectToken();
    const chain1 = await doTokenExchange({
      subjectToken: s1.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: a1.accessToken,
    }).expect(200);
    // Chain 2
    const s2 = await obtainSubjectToken();
    const a2 = await obtainSubjectToken();
    const chain2 = await doTokenExchange({
      subjectToken: s2.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: a2.accessToken,
    }).expect(200);

    // Revoke actor of chain 1
    await revokeAndWait(a1.accessToken, 'access_token');

    // Chain 1 delegation inactive
    expect((await doIntrospect(chain1.body.access_token)).active).toBe(false);
    // S1 still active (subject, not revoked)
    expect((await doIntrospect(s1.accessToken)).active).toBe(true);
    // Chain 2 fully unaffected
    expect((await doIntrospect(a2.accessToken)).active).toBe(true);
    expect((await doIntrospect(chain2.body.access_token)).active).toBe(true);
  });

  test('revoking actor refresh token does not cascade to delegated tokens', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6617');

    const subject = await obtainSubjectToken('openid profile offline_access');
    const actor = await obtainSubjectToken('openid profile offline_access');
    expect(actor.refreshToken).toBeTruthy();

    const delegated = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: actor.accessToken,
    }).expect(200);

    // Revoke the actor's refresh token — only the refresh_token itself is invalidated,
    // cascade propagation is triggered by access_token revocation only
    await revokeAndWait(actor.refreshToken!, 'refresh_token');

    // Delegation token remains active (refresh_token revocation doesn't cascade)
    expect((await doIntrospect(delegated.body.access_token)).active, 'delegation token stays active after actor refresh_token revocation').toBe(true);
    // Subject stays active (not revoked)
    expect((await doIntrospect(subject.accessToken)).active).toBe(true);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-6618: Intermediate delegation token revocation cascades         */
/* ------------------------------------------------------------------ */

test.describe('AM-6618: Intermediate delegation token revocation', () => {
  test('revoking intermediate keeps ancestor and actor active, cascades to descendants', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6618');

    const subject = await obtainSubjectToken();
    const actor = await obtainSubjectToken();
    const d1 = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: actor.accessToken,
    }).expect(200);
    const d2 = await doTokenExchange({
      subjectToken: d1.body.access_token,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: actor.accessToken,
    }).expect(200);
    const d3 = await doTokenExchange({
      subjectToken: d2.body.access_token,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: actor.accessToken,
    }).expect(200);

    // Revoke intermediate D2
    await revokeAndWait(d2.body.access_token, 'access_token');

    // Subject and actor stay active
    expect((await doIntrospect(subject.accessToken)).active).toBe(true);
    expect((await doIntrospect(actor.accessToken)).active).toBe(true);
    // D1 (ancestor of D2) stays active
    expect((await doIntrospect(d1.body.access_token)).active).toBe(true);
    // D2 and D3 (descendant) are inactive
    expect((await doIntrospect(d2.body.access_token)).active).toBe(false);
    expect((await doIntrospect(d3.body.access_token)).active).toBe(false);
  });

  test('outsider delegation chain stays active when another intermediate is revoked', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6618');

    // Chain 1: S1+A1 → D1a → D1b
    const s1 = await obtainSubjectToken();
    const a1 = await obtainSubjectToken();
    const d1a = await doTokenExchange({
      subjectToken: s1.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: a1.accessToken,
    }).expect(200);
    const d1b = await doTokenExchange({
      subjectToken: d1a.body.access_token,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: a1.accessToken,
    }).expect(200);
    // Chain 2: S2+A2 → D2a → D2b
    const s2 = await obtainSubjectToken();
    const a2 = await obtainSubjectToken();
    const d2a = await doTokenExchange({
      subjectToken: s2.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: a2.accessToken,
    }).expect(200);
    const d2b = await doTokenExchange({
      subjectToken: d2a.body.access_token,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: a2.accessToken,
    }).expect(200);

    // Revoke D1a (intermediate in chain 1)
    await revokeAndWait(d1a.body.access_token, 'access_token');

    // Chain 1: S1/A1 active, D1a/D1b inactive
    expect((await doIntrospect(s1.accessToken)).active).toBe(true);
    expect((await doIntrospect(a1.accessToken)).active).toBe(true);
    expect((await doIntrospect(d1a.body.access_token)).active).toBe(false);
    expect((await doIntrospect(d1b.body.access_token)).active).toBe(false);
    // Chain 2: fully unaffected
    expect((await doIntrospect(s2.accessToken)).active).toBe(true);
    expect((await doIntrospect(a2.accessToken)).active).toBe(true);
    expect((await doIntrospect(d2a.body.access_token)).active).toBe(true);
    expect((await doIntrospect(d2b.body.access_token)).active).toBe(true);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-6619: Delegation subject revocation cascades                    */
/*  Jira includes refresh token cascade, outsider isolation, and       */
/*  actor survival checks not covered here.                            */
/* ------------------------------------------------------------------ */

test.describe('AM-6619: Delegation subject revocation cascades', () => {
  test('revoking subject invalidates delegation token', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6619');

    const subject = await obtainSubjectToken();
    const actor = await obtainSubjectToken();
    const delegated = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: actor.accessToken,
    }).expect(200);

    await revokeAndWait(subject.accessToken, 'access_token');

    const introspection = await doIntrospect(delegated.body.access_token);
    expect(introspection.active).toBe(false);
  });

  test('revoking subject does not affect independent chain (outsider isolation)', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6619');

    // Chain 1
    const subject1 = await obtainSubjectToken();
    const actor1 = await obtainSubjectToken();
    const chain1 = await doTokenExchange({
      subjectToken: subject1.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: actor1.accessToken,
    }).expect(200);
    // Chain 2 (independent)
    const subject2 = await obtainSubjectToken();
    const actor2 = await obtainSubjectToken();
    const chain2 = await doTokenExchange({
      subjectToken: subject2.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: actor2.accessToken,
    }).expect(200);

    // Revoke subject of chain 1
    await revokeAndWait(subject1.accessToken, 'access_token');

    // Chain 1 delegation should be inactive
    expect((await doIntrospect(chain1.body.access_token)).active, 'revoked chain should be inactive').toBe(false);

    // Chain 2 should be completely unaffected
    expect((await doIntrospect(subject2.accessToken)).active, 'outsider subject should remain active').toBe(true);
    expect((await doIntrospect(actor2.accessToken)).active, 'outsider actor should remain active').toBe(true);
    expect((await doIntrospect(chain2.body.access_token)).active, 'outsider delegation should remain active').toBe(true);
  });

  test('revoking subject refresh token does not cascade to delegation token', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6619');

    const subject = await obtainSubjectToken('openid profile offline_access');
    expect(subject.refreshToken).toBeTruthy();
    const actor = await obtainSubjectToken();
    const delegated = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: actor.accessToken,
    }).expect(200);

    // Revoke subject's refresh token — only the refresh_token itself is invalidated
    await revokeAndWait(subject.refreshToken!, 'refresh_token');

    // Delegation token remains active (refresh_token revocation doesn't cascade)
    const introspection = await doIntrospect(delegated.body.access_token);
    expect(introspection.active, 'delegation token stays active after subject refresh_token revocation').toBe(true);
    // Actor stays active
    expect((await doIntrospect(actor.accessToken)).active).toBe(true);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-6620: Edge cases                                                */
/* ------------------------------------------------------------------ */

test.describe('AM-6620: Revocation edge cases', () => {
  test('shared actor: revoking actor invalidates all delegations using it', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6620');

    const subject1 = await obtainSubjectToken();
    const subject2 = await obtainSubjectToken();
    const sharedActor = await obtainSubjectToken();

    const del1 = await doTokenExchange({
      subjectToken: subject1.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: sharedActor.accessToken,
    }).expect(200);
    const del2 = await doTokenExchange({
      subjectToken: subject2.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: sharedActor.accessToken,
    }).expect(200);

    await revokeAndWait(sharedActor.accessToken, 'access_token');

    const intr1 = await doIntrospect(del1.body.access_token);
    const intr2 = await doIntrospect(del2.body.access_token);
    expect(intr1.active).toBe(false);
    expect(intr2.active).toBe(false);
  });

  test('idempotent revocation', async ({ obtainSubjectToken, revokeAndWait, doRevoke, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6620');

    const subject = await obtainSubjectToken();

    // First revocation succeeds (with wait for propagation)
    await revokeAndWait(subject.accessToken, 'access_token');
    // Second revocation should also succeed (RFC 7009: idempotent)
    await doRevoke(subject.accessToken, 'access_token');

    // Verify the token is indeed revoked
    const introspection = await doIntrospect(subject.accessToken);
    expect(introspection.active).toBe(false);
  });

  test('self-revocation: token can revoke itself', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6620');

    const subject = await obtainSubjectToken();
    const exchanged = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);

    // Revoke the exchanged token itself (self-revocation)
    await revokeAndWait(exchanged.body.access_token, 'access_token');

    const introspection = await doIntrospect(exchanged.body.access_token);
    expect(introspection.active).toBe(false);

    // Original subject should still be active
    expect((await doIntrospect(subject.accessToken)).active).toBe(true);
  });

  test('fresh exchange after revocation succeeds', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6620');

    const subject = await obtainSubjectToken();
    const first = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);

    // Revoke the first exchanged token
    await revokeAndWait(first.body.access_token, 'access_token');
    expect((await doIntrospect(first.body.access_token)).active).toBe(false);

    // A new exchange with the same (still-active) subject should succeed
    const second = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);
    const intr = await doIntrospect(second.body.access_token);
    expect(intr.active).toBe(true);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-6621: Mixed chain cascading                                     */
/*  Jira includes actor revocation through mixed chain and outsider    */
/*  isolation not covered here.                                        */
/* ------------------------------------------------------------------ */

test.describe('AM-6621: Mixed chain cascading', () => {
  test('revoke root of impersonation-then-delegation chain', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6621');

    const root = await obtainSubjectToken();
    // Step 1: impersonation
    const impersonated = await doTokenExchange({
      subjectToken: root.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);
    // Step 2: delegation using impersonated token as subject
    const actor = await obtainSubjectToken();
    const delegated = await doTokenExchange({
      subjectToken: impersonated.body.access_token,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: actor.accessToken,
    }).expect(200);

    await revokeAndWait(root.accessToken, 'access_token');

    const impIntr = await doIntrospect(impersonated.body.access_token);
    const delIntr = await doIntrospect(delegated.body.access_token);
    expect(impIntr.active).toBe(false);
    expect(delIntr.active).toBe(false);
  });

  test('outsider chain tokens remain active after unrelated revocation', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6621');

    // Chain 1: impersonation
    const root1 = await obtainSubjectToken();
    const imp1 = await doTokenExchange({
      subjectToken: root1.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);

    // Chain 2: independent impersonation (outsider)
    const root2 = await obtainSubjectToken();
    const imp2 = await doTokenExchange({
      subjectToken: root2.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);

    // Revoke chain 1 root
    await revokeAndWait(root1.accessToken, 'access_token');

    // Chain 1 should be revoked
    expect((await doIntrospect(imp1.body.access_token)).active, 'revoked chain token should be inactive').toBe(false);

    // Chain 2 should be completely unaffected
    expect((await doIntrospect(imp2.body.access_token)).active, 'outsider access_token should remain active').toBe(true);
  });

  test('revoke actor in mixed chain cascades to delegation but not impersonation', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6621');

    const root = await obtainSubjectToken();
    // Step 1: impersonation (no actor)
    const impersonated = await doTokenExchange({
      subjectToken: root.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);
    // Step 2: delegation using impersonated as subject + actor
    const actor = await obtainSubjectToken();
    const delegated = await doTokenExchange({
      subjectToken: impersonated.body.access_token,
      subjectTokenType: ACCESS_TOKEN_TYPE,
      actorToken: actor.accessToken,
    }).expect(200);

    // Revoke actor — should cascade to delegation token, but not impersonation token
    await revokeAndWait(actor.accessToken, 'access_token');

    // Actor and delegation inactive
    expect((await doIntrospect(actor.accessToken)).active).toBe(false);
    expect((await doIntrospect(delegated.body.access_token)).active).toBe(false);
    // Impersonation token stays active (not linked to actor)
    expect((await doIntrospect(impersonated.body.access_token)).active).toBe(true);
    // Root stays active
    expect((await doIntrospect(root.accessToken)).active).toBe(true);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-6623: Branching tree isolation                                  */
/* ------------------------------------------------------------------ */

test.describe('AM-6623: Branching tree isolation', () => {
  test('revoking one branch does not affect sibling branches', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6623');

    const root = await obtainSubjectToken();
    const branchA = await doTokenExchange({
      subjectToken: root.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);
    const branchB = await doTokenExchange({
      subjectToken: root.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);

    const leafA = await doTokenExchange({
      subjectToken: branchA.body.access_token,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);

    // Revoke branch A
    await revokeAndWait(branchA.body.access_token, 'access_token');

    // Branch A and its leaf should be inactive
    expect((await doIntrospect(branchA.body.access_token)).active).toBe(false);
    expect((await doIntrospect(leafA.body.access_token)).active).toBe(false);

    // Branch B should be unaffected
    expect((await doIntrospect(branchB.body.access_token)).active).toBe(true);
    // Root should be unaffected
    expect((await doIntrospect(root.accessToken)).active).toBe(true);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-6624: Token revocation cascade (proxy for consent scenario)     */
/* ------------------------------------------------------------------ */

test.describe('AM-6624: Token revocation cascade', () => {
  test('revoking access token cascades to exchanged tokens', async ({ obtainSubjectToken, doTokenExchange, revokeAndWait, doIntrospect }, testInfo) => {
    linkJira(testInfo, 'AM-6624');

    const subject = await obtainSubjectToken('openid%20profile%20offline_access');
    expect(subject.refreshToken).toMatch(/^[A-Za-z0-9_-]+/);

    const exchanged = await doTokenExchange({
      subjectToken: subject.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);

    // Revoke the subject access token — should cascade to exchanged tokens
    await revokeAndWait(subject.accessToken, 'access_token');

    // Subject access token and exchanged token should be invalidated
    const subjectIntr = await doIntrospect(subject.accessToken);
    const exchangedIntr = await doIntrospect(exchanged.body.access_token);
    expect(subjectIntr.active).toBe(false);
    expect(exchangedIntr.active).toBe(false);
  });
});

/* ------------------------------------------------------------------ */
/*  AM-6623/AM-6624: Consent revocation via authorization_code flow    */
/*  These tests use the auth code flow to create consent, then revoke  */
/*  consent via Management API and verify token tree invalidation.     */
/* ------------------------------------------------------------------ */

const consentFixture = createTokenExchangeFixture({
  domainNamePrefix: 'pw-te-consent',
  grantTypes: ['authorization_code', 'refresh_token', 'urn:ietf:params:oauth:grant-type:token-exchange'],
  allowImpersonation: true,
  allowDelegation: false,
  maxDelegationDepth: 1,
});

consentFixture.describe('AM-6623/AM-6624: Consent revocation propagates to token tree', () => {
  // Consent tests involve auth code flow (multi-step) + exchange + async consent revocation polling.
  // Under parallel load, the total wall time can exceed the default 60s test timeout.
  consentFixture.setTimeout(180000);
  consentFixture('AM-6623: consent revocation invalidates exchanged token tree', async (
    { tokenExchangeDomain, tokenExchangeApp, tokenExchangeUser, oidcConfig, basicAuth, teAdminToken, doIntrospect },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-6623');

    const clientId = tokenExchangeApp.settings.oauth.clientId;
    const redirectUri = TOKEN_EXCHANGE_DEFAULTS.REDIRECT_URI;

    // Obtain tokens via authorization code flow (creates consent)
    const tokens = await obtainAuthorizationCodeTokens(
      oidcConfig,
      basicAuth,
      clientId,
      tokenExchangeUser.username,
      API_USER_PASSWORD,
      redirectUri,
    );
    expect(tokens.accessToken).toBeTruthy();

    // Exchange the access token (creates token tree)
    const exchangeRes = await exchangeToken(oidcConfig.token_endpoint, basicAuth, {
      subjectToken: tokens.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);
    const exchangedToken = exchangeRes.body.access_token;

    // Verify both are active before revocation
    expect((await doIntrospect(tokens.accessToken)).active).toBe(true);
    expect((await doIntrospect(exchangedToken)).active).toBe(true);

    // Revoke user consent via Management API
    await revokeUserConsents(tokenExchangeDomain.id, teAdminToken, tokenExchangeUser.id, clientId);

    // Poll until tokens become inactive (consent revocation is async — needs longer timeout under parallel load)
    await waitForTokenInactive(doIntrospect, exchangedToken, 180000);
    expect((await doIntrospect(exchangedToken)).active, 'exchanged token should be revoked after consent revocation').toBe(false);
  });

  consentFixture('AM-6624: consent revocation invalidates branching token tree', async (
    { tokenExchangeDomain, tokenExchangeApp, tokenExchangeUser, oidcConfig, basicAuth, teAdminToken, doIntrospect },
    testInfo,
  ) => {
    linkJira(testInfo, 'AM-6624');

    const clientId = tokenExchangeApp.settings.oauth.clientId;
    const redirectUri = TOKEN_EXCHANGE_DEFAULTS.REDIRECT_URI;

    // Obtain tokens via authorization code flow
    const tokens = await obtainAuthorizationCodeTokens(
      oidcConfig,
      basicAuth,
      clientId,
      tokenExchangeUser.username,
      API_USER_PASSWORD,
      redirectUri,
    );

    // Create two exchange branches from the same root
    const branch1 = await exchangeToken(oidcConfig.token_endpoint, basicAuth, {
      subjectToken: tokens.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);
    const branch2 = await exchangeToken(oidcConfig.token_endpoint, basicAuth, {
      subjectToken: tokens.accessToken,
      subjectTokenType: ACCESS_TOKEN_TYPE,
    }).expect(200);

    // Verify all active
    expect((await doIntrospect(branch1.body.access_token)).active).toBe(true);
    expect((await doIntrospect(branch2.body.access_token)).active).toBe(true);

    // Revoke consent
    await revokeUserConsents(tokenExchangeDomain.id, teAdminToken, tokenExchangeUser.id, clientId);

    // Both branches should become inactive (consent revocation is async — needs longer timeout under parallel load)
    await waitForTokenInactive(doIntrospect, branch1.body.access_token, 180000);
    await waitForTokenInactive(doIntrospect, branch2.body.access_token, 180000);
    expect((await doIntrospect(branch1.body.access_token)).active, 'branch 1 should be revoked').toBe(false);
    expect((await doIntrospect(branch2.body.access_token)).active, 'branch 2 should be revoked').toBe(false);
  });
});
