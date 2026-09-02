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
import { TokenClaim } from '@management-models/TokenClaim';
import { setup } from '../../test-fixture';
import { setupTrustedIssuerFixture, TrustedIssuerFixture } from './fixtures/trusted-issuer-fixture';

setup();

const JWT_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:jwt';

const SUBJECT_CLAIMS = "#context.attributes['token_exchange']['subject']['subject_token_claims']";
const ACTOR_CLAIMS = "#context.attributes['token_exchange']['actor']['actor_token_claims']";

/**
 * The subject token here is a JWT signed by a domain trusted issuer, not one the domain minted.
 * Its claims reach the issued token only through custom claims reading the token exchange context.
 */
const CUSTOM_CLAIMS: TokenClaim[] = [
  { claimName: 'business_claim_id', claimValue: `{${SUBJECT_CLAIMS}['claim_id']}`, tokenType: 'ACCESS_TOKEN' },
  { claimName: 'never_appears', claimValue: `{${SUBJECT_CLAIMS}['no_such_claim']}`, tokenType: 'ACCESS_TOKEN' },
  { claimName: 'agent_email', claimValue: `{${ACTOR_CLAIMS}['email']}`, tokenType: 'ACCESS_TOKEN' },
];

let fixture: TrustedIssuerFixture;

beforeAll(async () => {
  fixture = await setupTrustedIssuerFixture({ tokenCustomClaims: CUSTOM_CLAIMS });
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

describe('Token Exchange subject token claims in EL over a trusted issuer subject token', () => {
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
      // only the claims named by a custom claim reach the issued token
      client_id: 'spoofed-client',
    });

    const body = await exchangeExternalJwt(externalJwt);
    const exchanged = parseJwt(body.access_token);

    expect(exchanged.payload['iss']).not.toEqual(fixture.externalIssuer);
    expect(exchanged.payload['client_id']).not.toEqual('spoofed-client');
  });

  it('should omit a custom claim whose source claim is absent from the external token', async () => {
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

  it('should resolve an actor token claim to nothing when impersonating with an external token', async () => {
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
