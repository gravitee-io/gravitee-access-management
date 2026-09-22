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
import jwt from 'jsonwebtoken';
import { setup } from '../test-fixture';
import { KeycloakIdJagFixture, setupKeycloakIdJagFixture } from './fixtures/keycloak-id-jag-fixture';

/**
 * AM issues the ID-JAG, Keycloak redeems it.
 *
 * Requires `./local-stack.sh up --keycloak`, which runs Keycloak with the experimental
 * identity-assertion-jwt feature.
 */
setup(600000);

let fixture: KeycloakIdJagFixture;

const refusal = (response: any) => ({ error: response.body.error, reason: response.body.error_description });

describe('Keycloak redeeming an ID-JAG issued by AM', () => {
  beforeAll(async () => {
    fixture = await setupKeycloakIdJagFixture();
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  describe('the round trip an agent makes', () => {
    it('should issue a token for the Keycloak user linked to the assertion subject', async () => {
      const response = await fixture.redeem(await fixture.assertion()).expect(200);

      const token: any = jwt.decode(response.body.access_token);
      expect(token.iss).toBe(fixture.realmIssuer);
      expect(token.sub).toBe(fixture.keycloakUserId);
      expect(token.azp).toBe(fixture.clientId);
    });

    it('should issue no refresh token', async () => {
      const response = await fixture.redeem(await fixture.assertion()).expect(200);

      expect(response.body.refresh_token).toBeUndefined();
    });

    it('should refuse an assertion redeemed twice', async () => {
      const assertion = await fixture.assertion();
      await fixture.redeem(assertion).expect(200);

      const response = await fixture.redeem(assertion).expect(400);

      expect(refusal(response)).toEqual({ error: 'invalid_grant', reason: 'Token reuse detected' });
    });
  });

  describe('the issuer must be an identity provider of the realm', () => {
    it('should refuse an assertion whose issuer matches no identity provider', async () => {
      const assertion = fixture.craftAssertion({ iss: 'https://unknown.example.com/oidc' });

      const response = await fixture.redeem(assertion).expect(400);

      expect(refusal(response)).toEqual({ error: 'invalid_grant', reason: 'No Identity Provider for provided issuer' });
    });

    it('should refuse an assertion signed with a key the AM domain does not publish', async () => {
      const response = await fixture.redeem(fixture.craftAssertion({})).expect(400);

      expect(refusal(response)).toEqual({ error: 'invalid_grant', reason: 'Invalid signature' });
    });
  });

  describe('every claim check fails closed', () => {
    it('should refuse an assertion whose audience is another authorization server', async () => {
      const assertion = await fixture.assertion({ audience: fixture.strangerAudience });

      const response = await fixture.redeem(assertion).expect(400);

      expect(refusal(response)).toEqual({ error: 'invalid_grant', reason: 'Invalid token audience' });
    });

    it('should refuse an assertion minted for another client', async () => {
      const assertion = await fixture.impostorAssertion();

      const response = await fixture.redeem(assertion).expect(400);

      expect(refusal(response)).toEqual({
        error: 'invalid_grant',
        reason: `client id in assertion : some-other-agent and client id in request header/body : ${fixture.clientId}`,
      });
    });

    it('should refuse a subject no Keycloak user is linked to', async () => {
      const assertion = await fixture.assertion({ user: 'stranger' });

      const response = await fixture.redeem(assertion).expect(400);

      expect(refusal(response)).toEqual({ error: 'invalid_grant', reason: 'User not found' });
    });
  });
});

describe('AM redeeming an ID-JAG issued by Keycloak', () => {
  it.todo('should redeem an ID-JAG Keycloak issues through token exchange, once keycloak/keycloak#49998 ships');
});
