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
import { setup } from '../../test-fixture';
import {
  ID_JAG_JOSE_TYPE,
  IdJagRedemptionFixture,
  JWT_BEARER_GRANT,
  setupIdJagRedemptionFixture,
} from './fixtures/id-jag-redemption-fixture';

setup(600000);

let fixture: IdJagRedemptionFixture;

const errorOf = (response: any) => response.body.error;

const formHeaders = { 'Content-type': 'application/x-www-form-urlencoded' };

describe('ID-JAG redemption', () => {
  beforeAll(async () => {
    fixture = await setupIdJagRedemptionFixture({ selfIssued: true });
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  describe('the issuer must be an identity provider of this domain', () => {
    it('should refuse an assertion whose issuer matches no identity provider', async () => {
      const assertion = fixture.craftAssertion(fixture.craftedClaims({ iss: 'https://unknown.example.com/oidc' }));

      const response = await fixture.agent.redeem(assertion).expect(400);

      expect(errorOf(response)).toBe('invalid_grant');
    });

    it('should refuse an assertion signed with a key the issuer does not publish', async () => {
      const assertion = fixture.craftAssertion(fixture.craftedClaims());

      const response = await fixture.agent.redeem(assertion).expect(400);

      expect(errorOf(response)).toBe('invalid_grant');
    });

    it('should refuse an assertion signed with a symmetric algorithm', async () => {
      const assertion = fixture.craftAssertion(fixture.craftedClaims(), { algorithm: 'HS256', key: 'a-shared-secret' });

      const response = await fixture.agent.redeem(assertion).expect(400);

      expect(errorOf(response)).toBe('invalid_grant');
    });

    it('should refuse an assertion this domain issued itself', async () => {
      const assertion = await fixture.selfIssuedAssertion();

      const response = await fixture.agent.redeem(assertion).expect(400);

      expect(errorOf(response)).toBe('invalid_grant');
    });
  });

  describe('every claim check fails closed', () => {
    it('should refuse an assertion that cannot be parsed', async () => {
      const header = Buffer.from(JSON.stringify({ alg: 'RS256', typ: ID_JAG_JOSE_TYPE })).toString('base64url');

      const response = await fixture.agent.redeem(`${header}.not-a-payload.signature`).expect(400);

      expect(errorOf(response)).toBe('invalid_grant');
    });

    it('should refuse an assertion without an issuer', async () => {
      const claims = fixture.craftedClaims();
      delete claims.iss;

      const response = await fixture.agent.redeem(fixture.craftAssertion(claims)).expect(400);

      expect(errorOf(response)).toBe('invalid_grant');
    });

    it('should refuse an assertion whose audience omits this domain', async () => {
      const assertion = await fixture.agent.assertion({ audience: fixture.strangerAudience });

      const response = await fixture.agent.redeem(assertion).expect(400);

      expect(errorOf(response)).toBe('invalid_grant');
    });

    it('should refuse an assertion minted for another client', async () => {
      const assertion = await fixture.impostor.assertion();

      const response = await fixture.impostor.redeem(assertion).expect(400);

      expect(errorOf(response)).toBe('invalid_grant');
    });
  });

  describe('client authentication is the application own', () => {
    it('should redeem for a public client authenticating with none', async () => {
      const assertion = await fixture.publicAgent.assertion();

      const response = await fixture.publicAgent.redeem(assertion).expect(200);

      expect(response.body.access_token).toBeDefined();
    });

    it('should refuse a request that carries no client authentication', async () => {
      const assertion = await fixture.agent.assertion();

      const response = await performPost(
        fixture.resourceOidc.token_endpoint,
        '',
        `grant_type=${JWT_BEARER_GRANT}&assertion=${assertion}`,
        formHeaders,
      ).expect(401);

      expect(errorOf(response)).toBe('invalid_client');
    });
  });
});
