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
import { setup } from '../../test-fixture';
import { setupTokenExchangeFixture, TokenExchangeFixture, TOKEN_EXCHANGE_TEST } from './fixtures/token-exchange-fixture';

setup(120000);

const TOKEN_EXCHANGE_GRANT = 'urn:ietf:params:oauth:grant-type:token-exchange';
const ACCESS_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:access_token';
const ID_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:id_token';

let mapperFixture: TokenExchangeFixture;
let delegationMapperFixture: TokenExchangeFixture;
let precedenceFixture: TokenExchangeFixture;

beforeAll(async () => {
  // No EL written at all — the mapper alone must copy the claim.
  mapperFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'tx-mapper-impersonation',
    domainDescription: 'Token exchange declarative claims mapper (impersonation)',
    clientName: 'tx-mapper-impersonation-client',
    allowImpersonation: true,
    allowDelegation: false,
    claimsMapper: [
      { source: 'SUBJECT_TOKEN', sourceClaim: 'jti', tokenClaim: 'mapped_subject_jti' },
      { source: 'SUBJECT_TOKEN', sourceClaim: 'no_such_claim', tokenClaim: 'mapped_absent' },
      // resolves to nothing during impersonation: there is no actor token
      { source: 'ACTOR_TOKEN', sourceClaim: 'jti', tokenClaim: 'mapped_actor_jti' },
    ],
  });

  delegationMapperFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'tx-mapper-delegation',
    domainDescription: 'Token exchange declarative claims mapper (delegation)',
    clientName: 'tx-mapper-delegation-client',
    allowImpersonation: true,
    allowDelegation: true,
    allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
    maxDelegationDepth: 3,
    claimsMapper: [
      { source: 'SUBJECT_TOKEN', sourceClaim: 'jti', tokenClaim: 'mapped_subject_jti' },
      { source: 'ACTOR_TOKEN', sourceClaim: 'jti', tokenClaim: 'mapped_actor_jti' },
    ],
  });

  // A custom claim of the same name must beat the mapper.
  precedenceFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'tx-mapper-precedence',
    domainDescription: 'Token exchange claims mapper overridden by a custom token claim',
    clientName: 'tx-mapper-precedence-client',
    allowImpersonation: true,
    allowDelegation: false,
    claimsMapper: [{ source: 'SUBJECT_TOKEN', sourceClaim: 'jti', tokenClaim: 'contested_claim' }],
    tokenCustomClaims: [{ claimName: 'contested_claim', claimValue: 'from-custom-claim', tokenType: 'ACCESS_TOKEN' }],
  });
});

afterAll(async () => {
  if (mapperFixture) {
    await mapperFixture.cleanup();
  }
  if (delegationMapperFixture) {
    await delegationMapperFixture.cleanup();
  }
  if (precedenceFixture) {
    await precedenceFixture.cleanup();
  }
});

const exchange = async (fixture: TokenExchangeFixture, subjectToken: string, extraParams = ''): Promise<Record<string, any>> => {
  const response = await performPost(
    fixture.oidc.token_endpoint,
    '',
    `grant_type=${TOKEN_EXCHANGE_GRANT}&subject_token=${subjectToken}&subject_token_type=${ACCESS_TOKEN_TYPE}${extraParams}`,
    {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${fixture.basicAuth}`,
    },
  ).expect(200);
  return response.body;
};

describe('Token Exchange declarative claims mapper (RFC 8693)', () => {
  it('should copy a subject token claim with no expression language configured', async () => {
    const { obtainSubjectToken } = mapperFixture;
    const { accessToken: subjectToken } = await obtainSubjectToken();

    const body = await exchange(mapperFixture, subjectToken);
    const exchanged = parseJwt(body.access_token);

    expect(exchanged.payload['mapped_subject_jti']).toEqual(parseJwt(subjectToken).payload['jti']);
    expect(exchanged.payload['mapped_subject_jti']).not.toEqual(exchanged.payload['jti']);
  });

  it('should skip a mapping whose source claim is absent and still issue the token', async () => {
    const { obtainSubjectToken } = mapperFixture;
    const { accessToken: subjectToken } = await obtainSubjectToken();

    const body = await exchange(mapperFixture, subjectToken);
    const exchanged = parseJwt(body.access_token);

    expect(body.access_token).toBeDefined();
    expect(exchanged.payload).not.toHaveProperty('mapped_absent');
  });

  it('should resolve an actor token mapping to nothing during impersonation', async () => {
    const { obtainSubjectToken } = mapperFixture;
    const { accessToken: subjectToken } = await obtainSubjectToken();

    const body = await exchange(mapperFixture, subjectToken);
    const exchanged = parseJwt(body.access_token);

    expect(exchanged.payload).not.toHaveProperty('mapped_actor_jti');
  });

  it('should copy both subject and actor claims during delegation', async () => {
    const { obtainSubjectToken, obtainActorToken } = delegationMapperFixture;
    const { accessToken: subjectToken } = await obtainSubjectToken();
    const { accessToken: actorToken } = await obtainActorToken();

    const body = await exchange(
      delegationMapperFixture,
      subjectToken,
      `&actor_token=${actorToken}&actor_token_type=${ACCESS_TOKEN_TYPE}`,
    );
    const exchanged = parseJwt(body.access_token);

    expect(exchanged.payload['mapped_subject_jti']).toEqual(parseJwt(subjectToken).payload['jti']);
    expect(exchanged.payload['mapped_actor_jti']).toEqual(parseJwt(actorToken).payload['jti']);
  });

  it('should copy a mapped claim onto an exchanged id_token', async () => {
    const { obtainSubjectToken } = mapperFixture;
    const { accessToken: subjectToken } = await obtainSubjectToken();

    const body = await exchange(mapperFixture, subjectToken, `&requested_token_type=${ID_TOKEN_TYPE}`);
    const issued = parseJwt(body.access_token);

    expect(body.issued_token_type).toEqual(ID_TOKEN_TYPE);
    expect(issued.payload['mapped_subject_jti']).toEqual(parseJwt(subjectToken).payload['jti']);
  });

  it('should let a custom token claim override a mapping of the same name', async () => {
    const { obtainSubjectToken } = precedenceFixture;
    const { accessToken: subjectToken } = await obtainSubjectToken();

    const body = await exchange(precedenceFixture, subjectToken);
    const exchanged = parseJwt(body.access_token);

    expect(exchanged.payload['contested_claim']).toEqual('from-custom-claim');
    expect(exchanged.payload['contested_claim']).not.toEqual(parseJwt(subjectToken).payload['jti']);
  });
});
