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
import { parseJwt } from '@api-fixtures/jwt';
import { getApplication, patchApplication } from '@management-commands/application-management-commands';
import { waitForDomainSync } from '@management-commands/domain-management-commands';
import { retryUntil } from '@utils-commands/retry';
import { TokenClaim } from '@management-models/TokenClaim';
import { jira } from '@specs-utils/jira';
import { setup } from '../../test-fixture';
import {
  ACCESS_TOKEN_TYPE,
  ClaimMapping,
  TOKEN_EXCHANGE_TEST,
  TokenExchangeFixture,
  setupTokenExchangeFixture,
} from './fixtures/token-exchange-fixture';

setup(300000);

const SUBJECT_CLAIMS = "#context.attributes['token_exchange']['subject']['subject_token_claims']";
const ACTOR_CLAIMS = "#context.attributes['token_exchange']['actor']['actor_token_claims']";

const CONTESTED: ClaimMapping[] = [
  { source: 'SUBJECT_TOKEN', sourceClaim: 'jti', tokenClaim: 'contested_subject' },
  { source: 'ACTOR_TOKEN', sourceClaim: 'jti', tokenClaim: 'contested_actor' },
];

const shadowingClaims = (sourceClaim: string): TokenClaim[] => [
  { claimName: 'contested_subject', claimValue: `{${SUBJECT_CLAIMS}['${sourceClaim}']}`, tokenType: 'ACCESS_TOKEN' },
  { claimName: 'contested_actor', claimValue: `{${ACTOR_CLAIMS}['${sourceClaim}']}`, tokenType: 'ACCESS_TOKEN' },
];

/**
 * The subject token keeps the fixture's default scopes; the actor token is issued narrower, and the
 * exchange asks narrower still. All three differ, so a contested claim cannot pass by matching the
 * wrong one of them.
 */
const SUBJECT_SCOPE = 'openid profile offline_access';
const ACTOR_SCOPE = 'openid profile';
const EXCHANGE_SCOPE = 'openid';

const delegationConfig = {
  allowImpersonation: true,
  allowDelegation: true,
  allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
  maxDelegationDepth: 3,
};

let applicationMapper: TokenExchangeFixture;
let sameSource: TokenExchangeFixture;
let domainMapper: TokenExchangeFixture;
let removal: TokenExchangeFixture;
let impersonation: TokenExchangeFixture;

beforeAll(async () => {
  applicationMapper = await setupTokenExchangeFixture({
    domainNamePrefix: 'tx-el-shadow-app',
    domainDescription: 'Custom claims shadow an application claims mapper',
    clientName: 'tx-el-shadow-app-client',
    ...delegationConfig,
    claimMappings: CONTESTED,
    tokenCustomClaims: shadowingClaims('scope'),
  });

  sameSource = await setupTokenExchangeFixture({
    domainNamePrefix: 'tx-el-shadow-same',
    domainDescription: 'Custom claims and mappers resolve the same source claim',
    clientName: 'tx-el-shadow-same-client',
    ...delegationConfig,
    claimMappings: CONTESTED,
    tokenCustomClaims: shadowingClaims('jti'),
  });

  domainMapper = await setupTokenExchangeFixture({
    domainNamePrefix: 'tx-el-shadow-domain',
    domainDescription: 'Custom claims shadow a mapper inherited from the domain',
    clientName: 'tx-el-shadow-domain-client',
    ...delegationConfig,
    domainClaimMappings: CONTESTED,
    tokenCustomClaims: shadowingClaims('scope'),
  });

  removal = await setupTokenExchangeFixture({
    domainNamePrefix: 'tx-el-shadow-removal',
    domainDescription: 'Mappers apply once the shadowing custom claims are removed',
    clientName: 'tx-el-shadow-removal-client',
    ...delegationConfig,
    claimMappings: CONTESTED,
    tokenCustomClaims: shadowingClaims('scope'),
  });

  // impersonation mode: the domain allows impersonation only, so no actor token ever reaches the
  // exchange and the actor side of both mechanisms has nothing to resolve
  impersonation = await setupTokenExchangeFixture({
    domainNamePrefix: 'tx-el-shadow-impersonation',
    domainDescription: 'Contested actor claim under impersonation',
    clientName: 'tx-el-shadow-impersonation-client',
    allowImpersonation: true,
    allowDelegation: false,
    claimMappings: CONTESTED,
    tokenCustomClaims: shadowingClaims('scope'),
  });
});

afterAll(async () => {
  await applicationMapper?.cleanup();
  await sameSource?.cleanup();
  await domainMapper?.cleanup();
  await removal?.cleanup();
  await impersonation?.cleanup();
});

/** One delegation exchange, returning the issued payload alongside both source tokens. */
const delegate = async (fixture: TokenExchangeFixture, extraParams = '') => {
  const { accessToken: subjectToken } = await fixture.obtainSubjectToken();
  const { accessToken: actorToken } = await fixture.obtainActorToken(encodeURIComponent(ACTOR_SCOPE));

  const body = await fixture.exchange(subjectToken, `&actor_token=${actorToken}&actor_token_type=${ACCESS_TOKEN_TYPE}${extraParams}`);

  return {
    issued: parseJwt(body.access_token).payload,
    subject: parseJwt(subjectToken).payload,
    actor: parseJwt(actorToken).payload,
  };
};

describe('Token Exchange custom claim shadowing a claims mapper', () => {
  it(jira`should let custom claims shadow the mappers writing the same claims ${'AM-7558'}`, async () => {
    const { issued, subject, actor } = await delegate(applicationMapper, `&scope=${EXCHANGE_SCOPE}`);

    expect(issued['contested_subject']).toEqual(SUBJECT_SCOPE);
    expect(issued['contested_subject']).toEqual(subject['scope']);
    expect(issued['contested_subject']).not.toEqual(subject['jti']);

    // the actor claim resolved the actor token, not the subject token
    expect(issued['contested_actor']).toEqual(ACTOR_SCOPE);
    expect(issued['contested_actor']).toEqual(actor['scope']);
    expect(issued['contested_actor']).not.toEqual(actor['jti']);

    // and both came from their source tokens, not from the scope asked for on the exchange
    expect(issued['scope']).toEqual(EXCHANGE_SCOPE);
  });

  it(jira`should write each contested claim once when both sources resolve to the same value ${'AM-7558'}`, async () => {
    const { issued, subject, actor } = await delegate(sameSource);

    expect(issued['contested_subject']).toEqual(subject['jti']);
    expect(issued['contested_actor']).toEqual(actor['jti']);

    // neither writer may turn a claim into a list by appending to the other's value
    expect(typeof issued['contested_subject']).toEqual('string');
    expect(typeof issued['contested_actor']).toEqual('string');
    expect(issued['contested_subject']).not.toEqual(issued['contested_actor']);
  });

  it(jira`should let custom claims shadow mappers inherited from the domain ${'AM-7558'}`, async () => {
    const { issued, subject, actor } = await delegate(domainMapper, `&scope=${EXCHANGE_SCOPE}`);

    expect(issued['contested_subject']).toEqual(SUBJECT_SCOPE);
    expect(issued['contested_subject']).toEqual(subject['scope']);
    expect(issued['contested_subject']).not.toEqual(subject['jti']);

    expect(issued['contested_actor']).toEqual(ACTOR_SCOPE);
    expect(issued['contested_actor']).toEqual(actor['scope']);
    expect(issued['contested_actor']).not.toEqual(actor['jti']);
  });

  it(jira`should apply both mappers once the shadowing custom claims are removed ${'AM-7558'}`, async () => {
    const { domain, application, accessToken: adminToken } = removal;

    await patchApplication(domain.id, adminToken, { settings: { oauth: { tokenCustomClaims: [] } } }, application.id);

    // guard: the patch must leave the mappers in place, or the assertions below would prove nothing
    const reread = await getApplication(domain.id, adminToken, application.id);
    expect(reread.settings.oauth.tokenExchangeOAuthSettings.claimMappings).toHaveLength(2);

    await waitForDomainSync(domain.id);

    // the gateway applies the change on its next sync, so the mapped values take a moment to surface
    const { issued, subject, actor } = await retryUntil(
      () => delegate(removal),
      (result) => result.issued['contested_subject'] === result.subject['jti'],
      { timeoutMillis: 20000, intervalMillis: 1000 },
    );

    expect(issued['contested_subject']).toEqual(subject['jti']);
    expect(issued['contested_actor']).toEqual(actor['jti']);
  });

  it(jira`should not write the contested actor claim in impersonation mode ${'AM-7558'}`, async () => {
    const { accessToken: subjectToken } = await impersonation.obtainSubjectToken();

    const body = await impersonation.exchange(subjectToken);
    const issued = parseJwt(body.access_token).payload;
    const subject = parseJwt(subjectToken).payload;

    // the subject side is unaffected: the custom claim still shadows its mapper
    expect(issued['contested_subject']).toEqual(SUBJECT_SCOPE);
    expect(issued['contested_subject']).toEqual(subject['scope']);

    // with no actor token, neither the custom claim nor the mapper writes the actor claim -
    // an unresolvable EL does not hand the claim back to the mapper
    expect(issued).not.toHaveProperty('contested_actor');
  });
});
