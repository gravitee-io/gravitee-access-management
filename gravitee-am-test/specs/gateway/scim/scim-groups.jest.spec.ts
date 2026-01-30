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
import { performDelete, performGet, performPatch, performPost, performPut } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import { createScimGroupBody, createScimUserBody, ScimFixture, setupFixture } from './fixture/scim-fixture';
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

describe('SCIM Groups', () => {
  describe('CRUD', () => {
    it('should create a group', async () => {
      const displayName = uniqueName('Tour Guides');
      const body = createScimGroupBody(displayName);

      const response = await performPost(fixture.scimEndpoint, '/Groups', JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(201);
      const resBody = response.body;
      expect(resBody).toHaveProperty('id');
      expect(resBody.displayName).toEqual(displayName);
    });

    it('should return 409 if displayName already exists', async () => {
      const displayName = uniqueName('Tour Guides 409');
      const body = createScimGroupBody(displayName);

      await performPost(fixture.scimEndpoint, '/Groups', JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      }).expect(201);

      const response = await performPost(fixture.scimEndpoint, '/Groups', JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });
      expect(response.status).toEqual(409);
    });

    it('should update group', async () => {
      const displayName = uniqueName('Tour Guides Update');
      const createdGroup = await fixture.createGroup(createScimGroupBody(displayName));

      const body = {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:Group'],
        displayName: `${displayName} Updated`,
      };

      const response = await performPut(fixture.scimEndpoint, `/Groups/${createdGroup.id}`, JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.displayName).toEqual(`${displayName} Updated`);
    });

    it('should patch group - add members', async () => {
      const user1 = await fixture.createUser(
        createScimUserBody(`${uniqueName('babs', true)}@example.com`, 'Babs', 'Jensen', uniqueName('babs', true)),
      );
      const user2 = await fixture.createUser(
        createScimUserBody(`${uniqueName('mandy', true)}@example.com`, 'Mandy', 'Pepperidge', uniqueName('mandy', true)),
      );
      const createdGroup = await fixture.createGroup(createScimGroupBody(uniqueName('Tour Guides Add')));

      const body = {
        schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
        Operations: [
          {
            op: 'Add',
            value: {
              members: [
                { value: user1.id, display: 'Babs Jensen' },
                { value: user2.id, display: 'Mandy Pepperidge' },
              ],
            },
          },
        ],
      };

      const response = await performPatch(fixture.scimEndpoint, `/Groups/${createdGroup.id}`, JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.members.length).toEqual(2);
    });

    it('should patch group - replace displayName', async () => {
      const createdGroup = await fixture.createGroup(createScimGroupBody(uniqueName('Tour Guides Replace')));
      const newDisplayName = uniqueName('Tour Guides 3');

      const body = {
        schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
        Operations: [{ op: 'Replace', path: 'displayName', value: newDisplayName }],
      };

      const response = await performPatch(fixture.scimEndpoint, `/Groups/${createdGroup.id}`, JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.displayName).toEqual(newDisplayName);
    });

    it('should patch group - remove operation - multi-valued attribute with filter', async () => {
      const user1 = await fixture.createUser(
        createScimUserBody(`${uniqueName('babs-rem', true)}@example.com`, 'Babs', 'Jensen', uniqueName('babs-rem', true)),
      );
      const user2 = await fixture.createUser(
        createScimUserBody(`${uniqueName('mandy-rem', true)}@example.com`, 'Mandy', 'Pepperidge', uniqueName('mandy-rem', true)),
      );
      const createdGroup = await fixture.createGroup(createScimGroupBody(uniqueName('Tour Guides Remove Filter')));

      await performPatch(
        fixture.scimEndpoint,
        `/Groups/${createdGroup.id}`,
        JSON.stringify({
          schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
          Operations: [
            {
              op: 'Add',
              value: {
                members: [
                  { value: user1.id, display: 'Babs Jensen' },
                  { value: user2.id, display: 'Mandy Pepperidge' },
                ],
              },
            },
          ],
        }),
        {
          Authorization: `Bearer ${fixture.scimAccessToken}`,
          'Content-Type': 'application/json',
        },
      ).expect(200);

      const body = {
        schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
        Operations: [{ op: 'Remove', path: 'members[display eq "Babs Jensen"]' }],
      };

      const response = await performPatch(fixture.scimEndpoint, `/Groups/${createdGroup.id}`, JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.members.length).toEqual(1);
      expect(resBody.members[0].display).toEqual('Mandy Pepperidge');
    });

    it('should patch group - remove operation - multi-valued attribute', async () => {
      const user1 = await fixture.createUser(
        createScimUserBody(`${uniqueName('babs-remall', true)}@example.com`, 'Babs', 'Jensen', uniqueName('babs-remall', true)),
      );
      const createdGroup = await fixture.createGroup(createScimGroupBody(uniqueName('Tour Guides Remove All')));

      await performPatch(
        fixture.scimEndpoint,
        `/Groups/${createdGroup.id}`,
        JSON.stringify({
          schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
          Operations: [
            {
              op: 'Add',
              value: { members: [{ value: user1.id, display: 'Babs Jensen' }] },
            },
          ],
        }),
        {
          Authorization: `Bearer ${fixture.scimAccessToken}`,
          'Content-Type': 'application/json',
        },
      ).expect(200);

      const body = {
        schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
        Operations: [{ op: 'Remove', path: 'members' }],
      };

      const response = await performPatch(fixture.scimEndpoint, `/Groups/${createdGroup.id}`, JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody).not.toHaveProperty('members');
    });

    it('should patch group - multiple operations', async () => {
      const user1 = await fixture.createUser(
        createScimUserBody(`${uniqueName('babs-multi', true)}@example.com`, 'Babs', 'Jensen', uniqueName('babs-multi', true)),
      );
      const createdGroup = await fixture.createGroup(createScimGroupBody(uniqueName('Tour Guides Multi')));
      const newDisplayName = uniqueName('Tour Guides Multi Updated');

      const body = {
        schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
        Operations: [
          { op: 'Add', path: 'displayName', value: newDisplayName },
          {
            op: 'Add',
            path: 'members',
            value: [{ value: user1.id, display: 'Babs Jensen' }],
          },
        ],
      };

      const response = await performPatch(fixture.scimEndpoint, `/Groups/${createdGroup.id}`, JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.displayName).toEqual(newDisplayName);
      expect(resBody.members[0].display).toEqual('Babs Jensen');
    });

    it('should list groups', async () => {
      const response = await performGet(fixture.scimEndpoint, '/Groups', {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
      });
      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.totalResults).toBeGreaterThanOrEqual(0);
    });

    it('should delete group', async () => {
      const createdGroup = await fixture.createGroup(createScimGroupBody(uniqueName('Tour Guides Delete')));

      const response = await performDelete(fixture.scimEndpoint, `/Groups/${createdGroup.id}`, {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
      });
      expect(response.status).toEqual(204);
    });

    it('should return 404 when deleting unknown group', async () => {
      const response = await performDelete(fixture.scimEndpoint, '/Groups/unknown-id', {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
      });
      expect(response.status).toEqual(404);
    });
  });
});
