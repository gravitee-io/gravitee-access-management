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
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { setup } from '../../test-fixture';
import { APPROVED_SCOPES, setupUserConsentsFixture, UserConsentsFixture } from './fixtures/user-consents-fixture';

setup(200000);

let fixture: UserConsentsFixture;

beforeAll(async () => {
  fixture = await setupUserConsentsFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

const expectedUserId = () => ({
  id: fixture.consentingUser.id,
  externalId: fixture.consentingUser.externalId,
  source: fixture.consentingUser.source,
});

const expectedConsent = () =>
  expect.objectContaining({
    id: expect.any(String),
    userId: expectedUserId(),
    clientId: fixture.application.settings.oauth.clientId,
    scope: expect.any(String),
    domain: fixture.domain.id,
  });

const userHeaders = () => ({ Authorization: `Bearer ${fixture.userToken}` });

const listGatewayConsents = async () => {
  const response = await performGet(fixture.gatewayUsersConsentsUrl(), '', userHeaders()).expect(200);
  return response.body;
};

describe('User consents response', () => {
  describe('Gateway Users API', () => {
    it('should return userId as UserId object when listing consents', async () => {
      const consents = await listGatewayConsents();

      expect(consents.map((c) => c.scope)).toEqual(expect.arrayContaining(APPROVED_SCOPES));
      consents.forEach((consent) => expect(consent).toEqual(expectedConsent()));
    });

    it('should return userId as UserId object when getting consent by id', async () => {
      const [first] = await listGatewayConsents();

      const response = await performGet(fixture.gatewayUsersConsentsUrl(`/${first.id}`), '', userHeaders()).expect(200);

      expect(response.body).toEqual(expectedConsent());
      expect(response.body.id).toBe(first.id);
    });
  });

  describe('Self-service Account API', () => {
    it('should return userId as UserId object when listing consents', async () => {
      const response = await performGet(fixture.accountConsentsUrl(), '', userHeaders()).expect(200);

      expect(response.body.map((c) => c.scope)).toEqual(expect.arrayContaining(APPROVED_SCOPES));
      response.body.forEach((consent) => expect(consent).toEqual(expectedConsent()));
    });

    it('should return userId as UserId object when getting consent by id', async () => {
      const [first] = await listGatewayConsents();

      const response = await performGet(fixture.accountConsentsUrl(`/${first.id}`), '', userHeaders()).expect(200);

      expect(response.body).toEqual(expectedConsent());
      expect(response.body.id).toBe(first.id);
    });
  });
});
