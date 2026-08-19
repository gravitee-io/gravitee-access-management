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
import { setupTrustedIssuerFixture, TrustedIssuerFixture } from './fixtures/trusted-issuer-fixture';
import { ClaimMapping } from './fixtures/token-exchange-fixture';

setup();

const JWT_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:jwt';

/**
 * The reserved-claim rules exist because a mapping can read from a subject token an external issuer
 * signed. These tests run that exact topology: a JWT signed by a domain trusted issuer, exchanged
 * with a mapper configured on the application.
 */
const MAPPINGS: ClaimMapping[] = [
  { source: 'SUBJECT_TOKEN', sourceClaim: 'claim_id', tokenClaim: 'business_claim_id' },
  { source: 'SUBJECT_TOKEN', sourceClaim: 'no_such_claim', tokenClaim: 'never_appears' },
  { source: 'ACTOR_TOKEN', sourceClaim: 'email', tokenClaim: 'agent_email' },
];

let fixture: TrustedIssuerFixture;

beforeAll(async () => {
  fixture = await setupTrustedIssuerFixture({ claimsMapper: MAPPINGS });
});

afterAll(async () => {
  await fixture?.cleanup();
});

const exchangeExternalJwt = async (externalJwt: string) => {
  const { oidc, basicAuth } = fixture;
  const response = await performPost(
    oidc.token_endpoint,
    '',
    `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
      `&subject_token=${encodeURIComponent(externalJwt)}` +
      `&subject_token_type=${JWT_TOKEN_TYPE}`,
    {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${basicAuth}`,
    },
  ).expect(200);
  return response.body;
};

describe('Token Exchange claims mapper over a trusted issuer subject token', () => {
  it('should copy a claim from the externally signed subject token onto the issued token', async () => {
    const externalJwt = fixture.signExternalJwt({
      sub: 'external-user-123',
      scope: 'external:read',
      iss: fixture.externalIssuer,
      claim_id: 'EXTERNAL-42',
    });

    const body = await exchangeExternalJwt(externalJwt);
    const exchanged = parseJwt(body.access_token);

    expect(exchanged.payload['business_claim_id']).toEqual('EXTERNAL-42');
  });

  it('should keep the issuer of the exchanged token as the domain, not the external issuer', async () => {
    const externalJwt = fixture.signExternalJwt({
      sub: 'external-user-123',
      scope: 'external:read',
      iss: fixture.externalIssuer,
      claim_id: 'EXTERNAL-42',
      // the mapper must not give the external issuer a route onto these
      client_id: 'spoofed-client',
    });

    const body = await exchangeExternalJwt(externalJwt);
    const exchanged = parseJwt(body.access_token);

    expect(exchanged.payload['iss']).not.toEqual(fixture.externalIssuer);
    expect(exchanged.payload['client_id']).not.toEqual('spoofed-client');
  });

  it('should skip a mapping whose source claim is absent from the external token', async () => {
    const externalJwt = fixture.signExternalJwt({
      sub: 'external-user-123',
      scope: 'external:read',
      iss: fixture.externalIssuer,
      claim_id: 'EXTERNAL-42',
    });

    const body = await exchangeExternalJwt(externalJwt);
    const exchanged = parseJwt(body.access_token);

    expect(exchanged.payload).not.toHaveProperty('never_appears');
  });

  it('should resolve an actor token mapping to nothing when impersonating with an external token', async () => {
    const externalJwt = fixture.signExternalJwt({
      sub: 'external-user-123',
      scope: 'external:read',
      iss: fixture.externalIssuer,
      claim_id: 'EXTERNAL-42',
      email: 'external@example.com',
    });

    const body = await exchangeExternalJwt(externalJwt);
    const exchanged = parseJwt(body.access_token);

    expect(exchanged.payload).not.toHaveProperty('agent_email');
  });
});
