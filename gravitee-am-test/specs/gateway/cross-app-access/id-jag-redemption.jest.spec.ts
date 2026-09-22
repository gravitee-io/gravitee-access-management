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
import { setup } from '../../test-fixture';
import {
  audiencesOf,
  decodeToken,
  IdJagRedemptionFixture,
  ISSUER_SCOPE,
  JWT_BEARER_GRANT,
  RESOURCE_SCOPE,
  scopesOf,
  setupIdJagRedemptionFixture,
} from './fixtures/id-jag-redemption-fixture';

setup(600000);

let fixture: IdJagRedemptionFixture;

const resourceParam = (resource: string) => `&resource=${encodeURIComponent(resource)}`;

const scopeParam = (scope: string) => `&scope=${encodeURIComponent(scope)}`;

const errorOf = (response: any) => response.body.error;

describe('ID-JAG redemption', () => {
  beforeAll(async () => {
    fixture = await setupIdJagRedemptionFixture();
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  describe('the round trip an agent makes', () => {
    it('should return an access token addressed to the resolved resource', async () => {
      const assertion = await fixture.agent.assertion();

      const response = await fixture.agent.redeem(assertion).expect(200);

      expect(response.body.token_type).toBe('bearer');
      expect(response.body.access_token).toBeDefined();
      expect(audiencesOf(decodeToken(response.body.access_token))).toContain(fixture.mcpResource);
    });

    it('should issue the token for the local user the assertion subject binds to', async () => {
      const assertion = await fixture.agent.assertion();

      const response = await fixture.agent.redeem(assertion).expect(200);

      const payload = decodeToken(response.body.access_token);
      expect(payload.sub).toBe(fixture.boundUserSub);
      expect(payload.sub).not.toBe(decodeToken(assertion).sub);
    });

    it('should issue a token this domain signed as its own issuer', async () => {
      const assertion = await fixture.agent.assertion();

      const response = await fixture.agent.redeem(assertion).expect(200);

      expect(decodeToken(response.body.access_token).iss).toBe(fixture.resourceOidc.issuer);
    });

    it('should grant the assertion scopes when the request names none', async () => {
      const assertion = await fixture.agent.assertion();

      const response = await fixture.agent.redeem(assertion).expect(200);

      expect(scopesOf(response.body.scope)).toEqual([RESOURCE_SCOPE.read, RESOURCE_SCOPE.write].sort());
    });

    it('should narrow the granted scopes to the requested subset', async () => {
      const assertion = await fixture.agent.assertion();

      const response = await fixture.agent.redeem(assertion, scopeParam(RESOURCE_SCOPE.read)).expect(200);

      expect(scopesOf(response.body.scope)).toEqual([RESOURCE_SCOPE.read]);
    });

    it('should issue no refresh token although the application allows that grant', async () => {
      const assertion = await fixture.agent.assertion();

      const response = await fixture.agent.redeem(assertion).expect(200);

      expect(response.body.refresh_token).toBeUndefined();
    });
  });

  describe('resolving the resource', () => {
    it('should accept a resource parameter naming the resource the assertion names', async () => {
      const assertion = await fixture.agent.assertion();

      const response = await fixture.agent.redeem(assertion, resourceParam(fixture.mcpResource)).expect(200);

      expect(audiencesOf(decodeToken(response.body.access_token))).toContain(fixture.mcpResource);
    });

    it('should refuse a resource parameter that contradicts the assertion', async () => {
      const assertion = await fixture.agent.assertion();

      const response = await fixture.agent.redeem(assertion, resourceParam(fixture.otherResource)).expect(400);

      expect(errorOf(response)).toBe('invalid_target');
    });

    it('should refuse a resource that is not a protected resource of this domain', async () => {
      const assertion = await fixture.agent.assertion({ resource: fixture.unregisteredResource });

      const response = await fixture.agent.redeem(assertion).expect(400);

      expect(errorOf(response)).toBe('invalid_target');
    });
  });

  describe('scopes are capped by the resource and by the application', () => {
    it('should refuse a scope no MCP tool of the resource declares', async () => {
      const assertion = await fixture.agent.assertion({ scope: ISSUER_SCOPE.ghost });

      const response = await fixture.agent.redeem(assertion).expect(400);

      expect(errorOf(response)).toBe('invalid_scope');
    });

    it('should refuse a scope the application is not allowed to obtain', async () => {
      const assertion = await fixture.agent.assertion({ scope: ISSUER_SCOPE.admin });

      const response = await fixture.agent.redeem(assertion).expect(400);

      expect(errorOf(response)).toBe('invalid_scope');
    });

    it('should refuse a requested scope wider than the assertion scopes', async () => {
      const assertion = await fixture.agent.assertion({ scope: ISSUER_SCOPE.read });

      const response = await fixture.agent.redeem(assertion, scopeParam(RESOURCE_SCOPE.write)).expect(400);

      expect(errorOf(response)).toBe('invalid_scope');
    });
  });

  describe('the audit log answers who redeemed what', () => {
    it('should audit a redemption in the token stream with the resource and the granted scope', async () => {
      const assertion = await fixture.agent.assertion();

      const response = await fixture.agent.redeem(assertion, scopeParam(RESOURCE_SCOPE.write)).expect(200);

      const tokenId = decodeToken(response.body.access_token).jti;
      const audit = await fixture.awaitTokenAudit('SUCCESS', (detail) => JSON.stringify(detail).includes(tokenId));
      expect(JSON.stringify(audit)).toContain(fixture.mcpResource);
      expect(JSON.stringify(audit)).toContain(RESOURCE_SCOPE.write);
      expect(JSON.stringify(audit)).toContain(JWT_BEARER_GRANT);
    });

    it('should never write the assertion into the audit record', async () => {
      const assertion = await fixture.agent.assertion();

      const response = await fixture.agent.redeem(assertion).expect(200);

      const tokenId = decodeToken(response.body.access_token).jti;
      const audit = await fixture.awaitTokenAudit('SUCCESS', (detail) => JSON.stringify(detail).includes(tokenId));
      expect(JSON.stringify(audit)).not.toContain(assertion);
    });

    it('should audit a refused redemption as a failure', async () => {
      const assertion = await fixture.agent.assertion({ resource: fixture.unregisteredResource });

      const since = Date.now();
      await fixture.agent.redeem(assertion).expect(400);

      const audit = await fixture.awaitTokenAudit('FAILURE', (detail) => JSON.stringify(detail).includes(JWT_BEARER_GRANT), since);
      expect(JSON.stringify(audit)).not.toContain(assertion);
    });
  });
});
