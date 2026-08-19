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
import { ClaimMapping, setupTokenExchangeFixture, TokenExchangeFixture, TOKEN_EXCHANGE_TEST } from './fixtures/token-exchange-fixture';

setup(180000);

const TOKEN_EXCHANGE_GRANT = 'urn:ietf:params:oauth:grant-type:token-exchange';
const ACCESS_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:access_token';
const ID_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:id_token';

// jti is used as the source claim because it is always present and its value differs between the
// subject token and the token the exchange issues. That difference is what proves the mapper read
// the subject token rather than the issued one.
const MAPPINGS: ClaimMapping[] = [
  { source: 'SUBJECT_TOKEN', sourceClaim: 'jti', tokenClaim: 'mapped_subject_jti' },
  { source: 'ACTOR_TOKEN', sourceClaim: 'jti', tokenClaim: 'mapped_actor_jti' },
  { source: 'SUBJECT_TOKEN', sourceClaim: 'no_such_claim', tokenClaim: 'never_appears' },
];

const CONTESTED: ClaimMapping[] = [{ source: 'SUBJECT_TOKEN', sourceClaim: 'jti', tokenClaim: 'contested_claim' }];

/**
 * The same scenarios run twice: once with the mapper on the application, once with it on the domain
 * defaults and the application inheriting. Both must behave identically.
 */
type Placement = 'application' | 'domain';

const PLACEMENTS: Placement[] = ['application', 'domain'];

const mapperConfig = (placement: Placement, mappings: ClaimMapping[]) =>
  placement === 'application' ? { claimMappings: mappings } : { domainClaimMappings: mappings };

const fixtures: Record<Placement, { main: TokenExchangeFixture; precedence: TokenExchangeFixture }> = {} as any;

beforeAll(async () => {
  for (const placement of PLACEMENTS) {
    const main = await setupTokenExchangeFixture({
      domainNamePrefix: `tx-mapper-${placement}`,
      domainDescription: `Token exchange claims mapper on the ${placement}`,
      clientName: `tx-mapper-${placement}-client`,
      allowImpersonation: true,
      allowDelegation: true,
      allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
      maxDelegationDepth: 3,
      ...mapperConfig(placement, MAPPINGS),
    });

    const precedence = await setupTokenExchangeFixture({
      domainNamePrefix: `tx-mapper-${placement}-precedence`,
      domainDescription: `Custom token claim overrides the ${placement} mapper`,
      clientName: `tx-mapper-${placement}-precedence-client`,
      allowImpersonation: true,
      allowDelegation: false,
      tokenCustomClaims: [{ claimName: 'contested_claim', claimValue: 'from-custom-claim', tokenType: 'ACCESS_TOKEN' }],
      ...mapperConfig(placement, CONTESTED),
    });

    fixtures[placement] = { main, precedence };
  }
});

afterAll(async () => {
  for (const placement of PLACEMENTS) {
    await fixtures[placement]?.main?.cleanup();
    await fixtures[placement]?.precedence?.cleanup();
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

describe.each(PLACEMENTS)('Token Exchange claims mapper on the %s (RFC 8693)', (placement) => {
  it('should copy a subject token claim onto the exchanged access token', async () => {
    const fixture = fixtures[placement].main;
    const { accessToken: subjectToken } = await fixture.obtainSubjectToken();

    const body = await exchange(fixture, subjectToken);
    const exchanged = parseJwt(body.access_token);

    expect(exchanged.payload['mapped_subject_jti']).toEqual(parseJwt(subjectToken).payload['jti']);
    expect(exchanged.payload['mapped_subject_jti']).not.toEqual(exchanged.payload['jti']);
  });

  it('should skip a mapping whose source claim is absent and still issue the token', async () => {
    const fixture = fixtures[placement].main;
    const { accessToken: subjectToken } = await fixture.obtainSubjectToken();

    const body = await exchange(fixture, subjectToken);

    expect(body.access_token).toBeDefined();
    expect(parseJwt(body.access_token).payload).not.toHaveProperty('never_appears');
  });

  it('should resolve an actor token mapping to nothing during impersonation', async () => {
    const fixture = fixtures[placement].main;
    const { accessToken: subjectToken } = await fixture.obtainSubjectToken();

    const body = await exchange(fixture, subjectToken);

    expect(parseJwt(body.access_token).payload).not.toHaveProperty('mapped_actor_jti');
  });

  it('should copy both subject and actor claims during delegation', async () => {
    const fixture = fixtures[placement].main;
    const { accessToken: subjectToken } = await fixture.obtainSubjectToken();
    const { accessToken: actorToken } = await fixture.obtainActorToken();

    const body = await exchange(fixture, subjectToken, `&actor_token=${actorToken}&actor_token_type=${ACCESS_TOKEN_TYPE}`);
    const exchanged = parseJwt(body.access_token);

    expect(exchanged.payload['mapped_subject_jti']).toEqual(parseJwt(subjectToken).payload['jti']);
    expect(exchanged.payload['mapped_actor_jti']).toEqual(parseJwt(actorToken).payload['jti']);
    expect(exchanged.payload['mapped_subject_jti']).not.toEqual(exchanged.payload['mapped_actor_jti']);
  });

  it('should copy a mapped claim onto an exchanged id_token', async () => {
    const fixture = fixtures[placement].main;
    const { accessToken: subjectToken } = await fixture.obtainSubjectToken();

    const body = await exchange(fixture, subjectToken, `&requested_token_type=${ID_TOKEN_TYPE}`);
    const issued = parseJwt(body.access_token);

    expect(body.issued_token_type).toEqual(ID_TOKEN_TYPE);
    expect(issued.payload['mapped_subject_jti']).toEqual(parseJwt(subjectToken).payload['jti']);
  });

  it('should let a custom token claim override a mapping of the same name', async () => {
    const fixture = fixtures[placement].precedence;
    const { accessToken: subjectToken } = await fixture.obtainSubjectToken();

    const body = await exchange(fixture, subjectToken);
    const exchanged = parseJwt(body.access_token);

    expect(exchanged.payload['contested_claim']).toEqual('from-custom-claim');
    expect(exchanged.payload['contested_claim']).not.toEqual(parseJwt(subjectToken).payload['jti']);
  });
});

describe('Token Exchange claims mapper inheritance', () => {
  let overrideFixture: TokenExchangeFixture;

  beforeAll(async () => {
    // The domain defines a mapper. The application overrides with its own settings but defines no
    // mapper of its own. Inheritance is wholesale, so the domain's mapper must not apply.
    overrideFixture = await setupTokenExchangeFixture({
      domainNamePrefix: 'tx-mapper-override-empty',
      domainDescription: 'Application override drops the domain mapper',
      clientName: 'tx-mapper-override-empty-client',
      allowImpersonation: true,
      allowDelegation: false,
      domainClaimMappings: MAPPINGS,
      claimMappings: [],
    });
  });

  afterAll(async () => {
    await overrideFixture?.cleanup();
  });

  it('should not apply the domain mapper to an application that overrides without one', async () => {
    const { accessToken: subjectToken } = await overrideFixture.obtainSubjectToken();

    const body = await exchange(overrideFixture, subjectToken);

    expect(body.access_token).toBeDefined();
    expect(parseJwt(body.access_token).payload).not.toHaveProperty('mapped_subject_jti');
  });
});
