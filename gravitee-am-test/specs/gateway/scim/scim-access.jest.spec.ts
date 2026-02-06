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
import { performDelete, performGet, performPost, performPut } from '@gateway-commands/oauth-oidc-commands';
import { ScimFixture, setupFixture } from './fixture/scim-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: ScimFixture;

beforeAll(async () => {
  fixture = await setupFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('SCIM Access', () => {
  describe('Service Provider Configuration', () => {
    it('should retrieve ServiceProviderConfig', async () => {
      const response = await performGet(fixture.scimEndpoint, '/ServiceProviderConfig');
      expect(response.status).toEqual(200);

      const body = response.body;
      expect(body.schemas).toEqual(['urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig']);
      expect(body.patch.supported).toEqual(true);
      expect(body.bulk.supported).toEqual(false);
      expect(body.filter.supported).toEqual(true);
      expect(body.changePassword.supported).toEqual(false);
      expect(body.sort.supported).toEqual(false);
      expect(body.etag.supported).toEqual(false);
      expect(body.authenticationSchemes[0].type).toEqual('oauthbearertoken');
    });
  });

  describe('No auth token', () => {
    const endpoints = ['Users', 'Groups'];
    const actions = [
      { method: 'GET', path: '' },
      { method: 'POST', path: '' },
      { method: 'PUT', path: '/test-id' },
      { method: 'DELETE', path: '/test-id' },
    ];

    endpoints.forEach((endpoint) => {
      describe(`${endpoint} Endpoint`, () => {
        actions.forEach((action) => {
          it(`should return 401 when accessing ${endpoint} (${action.method}) with no token`, async () => {
            let req;
            const path = `/${endpoint}${action.path}`;

            if (action.method === 'GET') req = performGet(fixture.scimEndpoint, path);
            if (action.method === 'POST')
              req = performPost(fixture.scimEndpoint, path, '', {
                'Content-Type': 'application/json',
              });
            if (action.method === 'PUT')
              req = performPut(fixture.scimEndpoint, `/${endpoint}${action.path}`, '', {
                'Content-Type': 'application/json',
              });
            if (action.method === 'DELETE')
              req = performDelete(fixture.scimEndpoint, `/${endpoint}${action.path}`, {
                'Content-Type': 'application/json',
              });

            const response = await req;
            expect(response.status).toEqual(401);

            const body = response.body;
            expect(body.status).toEqual('401');
            expect(body.detail).toEqual('Missing access token. The access token must be sent using the Authorization header field (Bearer scheme) or the \'access_token\' body parameter');
            expect(body.schemas).toEqual(['urn:ietf:params:scim:api:messages:2.0:Error']);
          });

          it(`should return 401 when accessing ${endpoint} (${action.method}) with invalid token`, async () => {
            let req;
            const path = `/${endpoint}${action.path}`;
            const headers = {
              Authorization: 'Bearer wrong-token',
              'Content-Type': 'application/json',
            };

            if (action.method === 'GET') req = performGet(fixture.scimEndpoint, path, headers);
            if (action.method === 'POST') req = performPost(fixture.scimEndpoint, path, '', headers);
            if (action.method === 'PUT') req = performPut(fixture.scimEndpoint, path, '', headers);
            if (action.method === 'DELETE') req = performDelete(fixture.scimEndpoint, path, headers);

            const response = await req;
            expect(response.status).toEqual(401);

            const body = response.body;
            expect(body.status).toEqual('401');
            expect(body.detail).toEqual('The access token is invalid');
            expect(body.schemas).toEqual(['urn:ietf:params:scim:api:messages:2.0:Error']);
          });
        });
      });
    });
  });
});
