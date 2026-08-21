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
import {
  ACCESS_TOKEN_TYPE,
  ClaimMapping,
  setupTokenExchangeFixture,
  TOKEN_EXCHANGE_GRANT,
  TOKEN_EXCHANGE_TEST,
  TokenExchangeFixture,
} from './fixtures/token-exchange-fixture';
import { setupTrustedIssuerFixture, TrustedIssuerFixture } from './fixtures/trusted-issuer-fixture';

setup(240000);

const JWT_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:jwt';

const SUBJECT_MAPPING: ClaimMapping[] = [{ source: 'SUBJECT_TOKEN', sourceClaim: 'jti', tokenClaim: 'mapped_subject_jti' }];
const EXTERNAL_MAPPING: ClaimMapping[] = [{ source: 'SUBJECT_TOKEN', sourceClaim: 'claim_id', tokenClaim: 'business_claim_id' }];

const FORGED_JTI = 'forged-jti-value';

const encode = (value: object): string => Buffer.from(JSON.stringify(value), 'utf8').toString('base64url');
const decodeSegment = (segment: string): Record<string, any> => JSON.parse(Buffer.from(segment, 'base64url').toString('utf8'));

/** Rewrite one payload claim, keeping the original header and the original signature. */
const forgeClaim = (token: string, claim: string, value: unknown): string => {
  const [header, payload, signature] = token.split('.');
  return `${header}.${encode({ ...decodeSegment(payload), [claim]: value })}.${signature}`;
};

/** Strip the signature entirely and declare the token unsigned. */
const unsign = (token: string): string => {
  const [header, payload] = token.split('.');
  return `${encode({ ...decodeSegment(header), alg: 'none' })}.${payload}.`;
};

let defaultFixture: TokenExchangeFixture;
let delegationFixture: TokenExchangeFixture;
let mapperFixture: TokenExchangeFixture;
let externalFixture: TrustedIssuerFixture;

beforeAll(async () => {
  // Setup default fixture with all token types allowed (impersonation only by default)
  defaultFixture = await setupTokenExchangeFixture();

  // Setup delegation fixture with delegation enabled
  delegationFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'token-exchange-delegation',
    domainDescription: 'Token exchange with delegation',
    clientName: 'token-exchange-delegation-client',
    allowImpersonation: true,
    allowDelegation: true,
    allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
    maxDelegationDepth: 3,
  });

  // a domain that sources a claim from the subject token, so a refused token can be shown to
  // contribute nothing
  mapperFixture = await setupTokenExchangeFixture({
    domainNamePrefix: 'token-exchange-mapper',
    domainDescription: 'Token exchange sourcing a claim from the subject token',
    clientName: 'token-exchange-mapper-client',
    allowImpersonation: true,
    allowDelegation: false,
    claimMappings: SUBJECT_MAPPING,
  });

  // the externally signed case, where the expiry of someone else's JWT is the only thing between
  // their claims and the issued token
  externalFixture = await setupTrustedIssuerFixture({ claimMappings: EXTERNAL_MAPPING });
});

afterAll(async () => {
  if (defaultFixture) {
    await defaultFixture.cleanup();
  }
  if (delegationFixture) {
    await delegationFixture.cleanup();
  }
  await mapperFixture?.cleanup();
  await externalFixture?.cleanup();
});

/** Raw exchange that asserts nothing, so each test can inspect the status itself. */
const rawExchange = (fixture: TokenExchangeFixture, subjectToken: string, extraParams = '') =>
  performPost(
    fixture.oidc.token_endpoint,
    '',
    `grant_type=${TOKEN_EXCHANGE_GRANT}&subject_token=${subjectToken}&subject_token_type=${ACCESS_TOKEN_TYPE}${extraParams}`,
    { 'Content-type': 'application/x-www-form-urlencoded', Authorization: `Basic ${fixture.basicAuth}` },
  );

const exchangeExternalJwt = (externalJwt: string) =>
  performPost(
    externalFixture.oidc.token_endpoint,
    '',
    `grant_type=${TOKEN_EXCHANGE_GRANT}&subject_token=${encodeURIComponent(externalJwt)}&subject_token_type=${JWT_TOKEN_TYPE}`,
    { 'Content-type': 'application/x-www-form-urlencoded', Authorization: `Basic ${externalFixture.basicAuth}` },
  );

describe('Token Exchange with invalid tokens', () => {
  it('should reject a subject token with a tampered signature', async () => {
    const { oidc, basicAuth, obtainSubjectToken } = defaultFixture;

    // Given: obtain a valid subject token, then corrupt its signature.
    const { accessToken: validToken } = await obtainSubjectToken('openid%20profile');
    const parts = validToken.split('.');
    const tamperedToken = `${parts[0]}.${parts[1]}.invalidsignatureXXXXXXXXXXX`;

    // When: present the tampered token for exchange.
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=${tamperedToken}&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    // Then: invalid_request with a generic description.
    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toBe('The presented token is invalid');
  });

  it('should reject a tampered actor token in delegation', async () => {
    const { oidc, basicAuth, obtainSubjectToken, obtainActorToken } = delegationFixture;

    // Given: valid subject and actor tokens; tamper the actor token's signature.
    const { accessToken: subjectToken } = await obtainSubjectToken('openid%20profile');
    const { accessToken: validActorToken } = await obtainActorToken('openid%20profile');
    const parts = validActorToken.split('.');
    const tamperedActorToken = `${parts[0]}.${parts[1]}.invalidsignatureXXXXXXXXXXX`;

    // When: present the tampered actor token for delegation exchange.
    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` +
        `&actor_token=${tamperedActorToken}` +
        `&actor_token_type=urn:ietf:params:oauth:token-type:access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(400);

    // Then: invalid_request with a generic description.
    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toBe('The presented token is invalid');
  });

  it('should refuse a subject token whose mapped claim was edited after signing', async () => {
    const { accessToken: subjectToken } = await mapperFixture.obtainSubjectToken();

    const genuine = await mapperFixture.exchange(subjectToken);
    expect(parseJwt(genuine.access_token).payload['mapped_subject_jti']).toEqual(parseJwt(subjectToken).payload['jti']);

    // unlike the tampered-signature cases above, this carries the ORIGINAL signature - correct
    // length and encoding - so it reaches the cryptographic comparison rather than failing on shape
    const forged = forgeClaim(subjectToken, 'jti', FORGED_JTI);
    expect(parseJwt(forged).payload['jti']).toEqual(FORGED_JTI);
    expect(forged.split('.')[2]).toEqual(subjectToken.split('.')[2]);

    const response = await rawExchange(mapperFixture, forged).expect(400);

    expect(response.body.error).toEqual('invalid_request');
    expect(response.body.error_description).toEqual('The presented token is invalid');
    expect(response.body).not.toHaveProperty('access_token');
  });

  it('should refuse an unsigned subject token declaring alg none', async () => {
    const { accessToken: subjectToken } = await mapperFixture.obtainSubjectToken();

    const genuine = await mapperFixture.exchange(subjectToken);
    expect(parseJwt(genuine.access_token).payload['mapped_subject_jti']).toEqual(parseJwt(subjectToken).payload['jti']);

    const unsigned = unsign(forgeClaim(subjectToken, 'jti', FORGED_JTI));
    expect(parseJwt(unsigned).header['alg']).toEqual('none');
    expect(parseJwt(unsigned).payload['jti']).toEqual(FORGED_JTI);

    const response = await rawExchange(mapperFixture, unsigned).expect(400);

    expect(response.body.error).toEqual('invalid_request');
    expect(response.body).not.toHaveProperty('access_token');
  });

  it('should refuse a subject token minted by another domain', async () => {
    const { accessToken: ownToken } = await mapperFixture.obtainSubjectToken();
    const { accessToken: foreignToken } = await defaultFixture.obtainSubjectToken();

    const genuine = await mapperFixture.exchange(ownToken);
    expect(parseJwt(genuine.access_token).payload['mapped_subject_jti']).toEqual(parseJwt(ownToken).payload['jti']);

    // correctly signed, but by another domain's certificate: a valid signature is not enough,
    // it has to be valid under the trust anchor of the domain being asked
    const response = await rawExchange(mapperFixture, foreignToken).expect(400);

    expect(response.body.error).toEqual('invalid_request');
    expect(response.body).not.toHaveProperty('access_token');
  });

  it('should refuse an expired subject token that is correctly signed', async () => {
    const claims = { sub: 'external-user-123', scope: 'external:read', iss: externalFixture.externalIssuer, claim_id: 'EXTERNAL-42' };

    const genuine = await exchangeExternalJwt(externalFixture.signExternalJwt(claims)).expect(200);
    expect(parseJwt(genuine.body.access_token).payload['business_claim_id']).toEqual('EXTERNAL-42');

    // the same issuer, the same key, the same claim - only the lifetime has passed
    const expired = externalFixture.signExternalJwt(claims, { expiresInSeconds: -60 });
    expect(parseJwt(expired).payload['exp']).toBeLessThan(Math.floor(Date.now() / 1000));

    const response = await exchangeExternalJwt(expired).expect(400);

    expect(response.body.error).toEqual('invalid_request');
    // the refusal is indistinguishable from a signature failure by message alone - the trusted
    // issuer resolver's nimbus claims verifier rejects the expiry before AM's temporal check
    // can raise its more specific "has expired".
    expect(response.body.error_description).toEqual('The presented token is invalid');
    expect(response.body).not.toHaveProperty('access_token');
  });
});
