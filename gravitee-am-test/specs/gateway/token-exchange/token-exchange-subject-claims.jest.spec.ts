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
import { jira } from '@specs-utils/jira';
import { setup } from '../../test-fixture';
import {
  ACCESS_TOKEN_TYPE,
  ID_TOKEN_TYPE,
  TOKEN_EXCHANGE_TEST,
  TokenExchangeFixture,
  setupTokenExchangeFixture,
} from './fixtures/token-exchange-fixture';

setup(120000);

const SUBJECT_CLAIMS = "#context.attributes['token_exchange']['subject']['subject_token_claims']";
const ACTOR_CLAIMS = "#context.attributes['token_exchange']['actor']['actor_token_claims']";

let impersonationFixture: TokenExchangeFixture;
let delegationFixture: TokenExchangeFixture;

beforeAll(async () => {
  impersonationFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'tx-subject-claims-impersonation',
    domainDescription: 'Token exchange exposing subject token claims to EL (impersonation)',
    clientName: 'tx-subject-claims-impersonation-client',
    allowImpersonation: true,
    allowDelegation: false,
    tokenCustomClaims: [
      { claimName: 'tx_subject_jti', claimValue: `{${SUBJECT_CLAIMS}['jti']}`, tokenType: 'ACCESS_TOKEN' },
      { claimName: 'tx_subject_scope', claimValue: `{${SUBJECT_CLAIMS}['scope']}`, tokenType: 'ACCESS_TOKEN' },
      { claimName: 'tx_subject_absent', claimValue: `{${SUBJECT_CLAIMS}['no_such_claim']}`, tokenType: 'ACCESS_TOKEN' },
      { claimName: 'tx_subject_jti', claimValue: `{${SUBJECT_CLAIMS}['jti']}`, tokenType: 'ID_TOKEN' },
    ],
  });

  delegationFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'tx-subject-claims-delegation',
    domainDescription: 'Token exchange exposing subject and actor token claims to EL (delegation)',
    clientName: 'tx-subject-claims-delegation-client',
    allowImpersonation: true,
    allowDelegation: true,
    allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
    maxDelegationDepth: 3,
    tokenCustomClaims: [
      { claimName: 'tx_subject_jti', claimValue: `{${SUBJECT_CLAIMS}['jti']}`, tokenType: 'ACCESS_TOKEN' },
      { claimName: 'tx_actor_jti', claimValue: `{${ACTOR_CLAIMS}['jti']}`, tokenType: 'ACCESS_TOKEN' },
    ],
  });
});

afterAll(async () => {
  if (impersonationFixture) {
    await impersonationFixture.cleanup();
  }
  if (delegationFixture) {
    await delegationFixture.cleanup();
  }
});

describe('Token Exchange subject token claims in EL (RFC 8693)', () => {
  it(jira`should copy a subject token claim onto the exchanged access token ${'AM-7539'}`, async () => {
    const { obtainSubjectToken } = impersonationFixture;
    const { accessToken: subjectToken } = await obtainSubjectToken();

    const body = await impersonationFixture.exchange(subjectToken);
    const exchanged = parseJwt(body.access_token);
    const subject = parseJwt(subjectToken);

    // the value came from the subject token, not from the request or the user profile
    expect(exchanged.payload['tx_subject_jti']).toEqual(subject.payload['jti']);
    expect(exchanged.payload['tx_subject_jti']).not.toEqual(exchanged.payload['jti']);
  });

  it(jira`should expose the subject token scope, not the scope requested on the exchange ${'AM-7539'}`, async () => {
    const { obtainSubjectToken } = impersonationFixture;
    const { accessToken: subjectToken } = await obtainSubjectToken('openid%20profile%20offline_access');

    const body = await impersonationFixture.exchange(subjectToken, '&scope=openid');
    const exchanged = parseJwt(body.access_token);

    const subjectScope = String(exchanged.payload['tx_subject_scope']).split(' ').sort();
    expect(subjectScope).toEqual(['offline_access', 'openid', 'profile']);
    expect(exchanged.payload['scope']).toEqual('openid');
  });

  it('should omit the claim and still issue a token when the subject claim is absent', async () => {
    const { obtainSubjectToken } = impersonationFixture;
    const { accessToken: subjectToken } = await obtainSubjectToken();

    const body = await impersonationFixture.exchange(subjectToken);
    const exchanged = parseJwt(body.access_token);

    expect(body.access_token).toBeDefined();
    expect(exchanged.payload).not.toHaveProperty('tx_subject_absent');
  });

  it('should copy a subject token claim onto an exchanged id_token', async () => {
    const { obtainSubjectToken } = impersonationFixture;
    const { accessToken: subjectToken } = await obtainSubjectToken();

    const body = await impersonationFixture.exchange(subjectToken, `&requested_token_type=${ID_TOKEN_TYPE}`);
    const issued = parseJwt(body.access_token);
    const subject = parseJwt(subjectToken);

    expect(body.issued_token_type).toEqual(ID_TOKEN_TYPE);
    expect(issued.payload['tx_subject_jti']).toEqual(subject.payload['jti']);
  });

  it('should resolve both subject and actor claims for delegation', async () => {
    const { obtainSubjectToken, obtainActorToken } = delegationFixture;
    const { accessToken: subjectToken } = await obtainSubjectToken();
    const { accessToken: actorToken } = await obtainActorToken();

    const body = await delegationFixture.exchange(subjectToken, `&actor_token=${actorToken}&actor_token_type=${ACCESS_TOKEN_TYPE}`);
    const exchanged = parseJwt(body.access_token);

    expect(exchanged.payload['tx_subject_jti']).toEqual(parseJwt(subjectToken).payload['jti']);
    expect(exchanged.payload['tx_actor_jti']).toEqual(parseJwt(actorToken).payload['jti']);
    expect(exchanged.payload['tx_subject_jti']).not.toEqual(exchanged.payload['tx_actor_jti']);
  });
});
