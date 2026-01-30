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
import { getUserPage } from '@management-commands/user-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { createScimUserBody, ScimFixture, setupFixture } from './fixture/scim-fixture';
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

const baseCreateUserRequest = {
  schemas: ['urn:ietf:params:scim:schemas:extension:custom:2.0:User', 'urn:ietf:params:scim:schemas:core:2.0:User'],
  password: null,
  name: {
    formatted: 'Ms. Barbara J Jensen, III',
    familyName: 'Jensen',
    givenName: 'Barbara',
    middleName: 'Jane',
    honorificPrefix: 'Ms.',
    honorificSuffix: 'III',
  },
  displayName: 'John Doe',
};

describe('SCIM Users', () => {
  describe('User creation (preRegistration)', () => {
    it('should create the user with preRegistration and confirm registration', async () => {
      const testUserEmail = `${uniqueName('user', true)}@user.com`;
      const testUserName = uniqueName('barbara', true);

      const request = {
        ...baseCreateUserRequest,
        externalId: uniqueName('externalId', true),
        userName: testUserName,
        nickName: 'Babs',
        emails: [
          {
            value: testUserEmail,
            type: 'work',
            primary: true,
          },
        ],
        userType: 'Employee',
        title: 'Tour Guide',
        preferredLanguage: 'en-US',
        locale: 'en-US',
        timezone: 'America/Los_Angeles',
        active: true,
        'urn:ietf:params:scim:schemas:extension:custom:2.0:User': {
          preRegistration: true,
          forceResetPassword: true,
        },
      };

      await fixture.clearMailbox(testUserEmail);

      const response = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(request), {
        'Content-type': 'application/json',
        Authorization: `Bearer ${fixture.scimAccessToken}`,
      }).expect(201);
      const createdUser = response.body;

      expect(createdUser).toBeDefined();
      expect(createdUser.enabled).toBeFalsy();
      expect(createdUser.registrationUserUri).not.toBeDefined();
      expect(createdUser.registrationAccessToken).not.toBeDefined();

      const confirmationLink = await fixture.extractConfirmRegistrationLink(testUserEmail);
      await fixture.clearMailbox(testUserEmail);
      const postConfirmRegistration = await fixture.confirmRegistrationLink(confirmationLink);

      expect(postConfirmRegistration.headers['location']).toBeDefined();
      expect(postConfirmRegistration.headers['location']).toContain('success=registration_completed');

      const users = await getUserPage(fixture.domain.id, fixture.accessToken);
      const user = users.data.find((u) => u.email === testUserEmail || u.username === testUserName);
      expect(user).toBeDefined();
      expect(user.enabled).toBeTruthy();
      expect(user.forceResetPassword).toBeTruthy();
    });

    it('should send email with client information when client is specified', async () => {
      const userEmail = `${uniqueName('user-client', true)}@user.com`;
      const userName = uniqueName('john-client', true);

      const request = {
        ...baseCreateUserRequest,
        externalId: uniqueName('externalId', true),
        userName: userName,
        emails: [
          {
            value: userEmail,
            type: 'work',
            primary: true,
          },
        ],
        active: true,
        'urn:ietf:params:scim:schemas:extension:custom:2.0:User': {
          preRegistration: true,
          client: fixture.application.id,
        },
      };

      await fixture.clearMailbox(userEmail);

      const response = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(request), {
        'Content-type': 'application/json',
        Authorization: `Bearer ${fixture.scimAccessToken}`,
      }).expect(201);
      const createdUser = response.body;

      expect(createdUser).toBeDefined();
      expect(createdUser.enabled).toBeFalsy();

      const confirmationLink = await fixture.extractConfirmRegistrationLink(userEmail);
      expect(confirmationLink).toBeDefined();

      const url = new URL(confirmationLink);
      const clientIdParam = url.searchParams.get('client_id');
      expect(clientIdParam).toBeDefined();
      expect(clientIdParam).toBe(fixture.application.settings.oauth.clientId);

      const users = await getUserPage(fixture.domain.id, fixture.accessToken);
      const user = users.data.find((u) => u.email === userEmail);
      expect(user).toBeDefined();
      expect(user.client).toBe(fixture.application.id);

      await fixture.clearMailbox(userEmail);
    });
  });

  describe('Create Validation', () => {
    it('should return 400 for malformed json', async () => {
      const response = await performPost(fixture.scimEndpoint, '/Users', '', {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
      });

      expect(response.status).toEqual(400);
      const body = response.body;
      expect(body.status).toEqual('400');
      expect(body.scimType).toEqual('invalidSyntax');
      expect(body.detail).toEqual('Unable to parse body message');
      expect(body.schemas).toEqual(['urn:ietf:params:scim:api:messages:2.0:Error']);
    });

    it('should return 400 when userName is missing', async () => {
      const body = {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        externalId: 'bjensen',
        name: {
          formatted: 'Ms. Barbara J Jensen III',
          familyName: 'Jensen',
          givenName: 'Barbara',
        },
      };
      const response = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(400);
      const resBody = response.body;
      expect(resBody.status).toEqual('400');
      expect(resBody.scimType).toEqual('invalidValue');
      expect(resBody.detail).toEqual('Field [userName] is required');
      expect(resBody.schemas).toEqual(['urn:ietf:params:scim:api:messages:2.0:Error']);
    });

    it('should return 400 when userName is invalid', async () => {
      const body = {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        externalId: 'bjensen',
        userName: '&Invalid',
        name: {
          formatted: 'Ms. Barbara J Jensen III',
          familyName: 'Jensen',
          givenName: 'Barbara',
        },
      };
      const response = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(400);
      const resBody = response.body;
      expect(resBody.status).toEqual('400');
      expect(resBody.scimType).toEqual('invalidValue');
      expect(resBody.detail).toEqual('Username [&Invalid] is not a valid value');
      expect(resBody.schemas).toEqual(['urn:ietf:params:scim:api:messages:2.0:Error']);
    });

    it('should return 400 when familyName is invalid', async () => {
      const body = {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        externalId: 'bjensen',
        userName: `${uniqueName('user', true)}@example.com`,
        name: {
          formatted: 'Ms. Barbara J Jensen III',
          familyName: '#Invalid',
          givenName: 'Barbara',
        },
      };
      const response = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(400);
      const resBody = response.body;
      expect(resBody.status).toEqual('400');
      expect(resBody.scimType).toEqual('invalidValue');
      expect(resBody.detail).toEqual('Last name [#Invalid] is not a valid value');
      expect(resBody.schemas).toEqual(['urn:ietf:params:scim:api:messages:2.0:Error']);
    });

    it('should return 400 when givenName is invalid', async () => {
      const body = {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        externalId: 'bjensen',
        userName: `${uniqueName('user', true)}@example.com`,
        name: {
          formatted: 'Ms. Barbara J Jensen III',
          familyName: 'Jensen',
          givenName: '#Invalid',
        },
      };
      const response = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(400);
      const resBody = response.body;
      expect(resBody.status).toEqual('400');
      expect(resBody.scimType).toEqual('invalidValue');
      expect(resBody.detail).toEqual('First name [#Invalid] is not a valid value');
      expect(resBody.schemas).toEqual(['urn:ietf:params:scim:api:messages:2.0:Error']);
    });
  });

  describe('CRUD', () => {
    it('should create a user', async () => {
      const email = `${uniqueName('user-crud', true)}@example.com`;
      const body = createScimUserBody(email, 'Barbara', 'Jensen', uniqueName('ext', true));

      const response = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(201);
      const resBody = response.body;
      expect(resBody).toHaveProperty('id');
      expect(resBody.schemas).toEqual(['urn:ietf:params:scim:schemas:core:2.0:User']);
    });

    it('should return 409 if userName already exists', async () => {
      const email = `${uniqueName('user-409', true)}@example.com`;
      const body = createScimUserBody(email, 'Barbara', 'Jensen', uniqueName('ext', true));

      await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      }).expect(201);

      const response = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(409);
      const resBody = response.body;
      expect(resBody.status).toEqual('409');
      expect(resBody.scimType).toEqual('uniqueness');
    });

    it('should return 404 when updating unknown user', async () => {
      const body = {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: uniqueName('unknown', true),
        name: {
          familyName: 'Jensen',
          givenName: 'Barbara',
        },
      };
      await performPut(fixture.scimEndpoint, '/Users/wrong-id', JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      }).expect(404);
    });

    it('should update user', async () => {
      const email = `${uniqueName('user-update', true)}@example.com`;
      const userName = uniqueName('user-update', true);
      const createdUser = await fixture.createUser(createScimUserBody(email, 'Barbara', 'Jensen', userName));

      const body = {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: userName,
        externalId: userName,
        name: {
          formatted: 'Ms. Barbara J Jensen III',
          familyName: 'Jensen2',
          givenName: 'Barbara',
        },
      };

      const response = await performPut(fixture.scimEndpoint, `/Users/${createdUser.id}`, JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.name.familyName).toEqual('Jensen2');
    });

    it('should patch user - add single attribute', async () => {
      const email = `${uniqueName('user-patch1', true)}@example.com`;
      const createdUser = await fixture.createUser(createScimUserBody(email, 'Barbara', 'Jensen', uniqueName('patch1', true)));

      const body = {
        schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
        Operations: [{ op: 'Add', path: 'userType', value: 'Employee' }],
      };

      const response = await performPatch(fixture.scimEndpoint, `/Users/${createdUser.id}`, JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.userType).toEqual('Employee');
    });

    it('should patch user - replace operation - multi-valued attribute', async () => {
      const email = `${uniqueName('user-patch2', true)}@example.com`;
      const createdUser = await fixture.createUser(createScimUserBody(email, 'Barbara', 'Jensen', uniqueName('patch2', true)));

      const body = {
        schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
        Operations: [
          {
            op: 'Replace',
            path: 'emails',
            value: [
              { value: `${uniqueName('work', true)}@example.com`, type: 'work', primary: true },
              { value: `${uniqueName('home', true)}@example.com`, type: 'home' },
            ],
          },
        ],
      };

      const response = await performPatch(fixture.scimEndpoint, `/Users/${createdUser.id}`, JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.emails.length).toEqual(2);
    });

    it('should patch user - remove operation - single attribute', async () => {
      const email = `${uniqueName('user-patch3', true)}@example.com`;
      const createdUser = await fixture.createUser(createScimUserBody(email, 'Barbara', 'Jensen', uniqueName('patch3', true)));

      const body = {
        schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
        Operations: [{ op: 'Remove', path: 'userType' }],
      };

      const response = await performPatch(fixture.scimEndpoint, `/Users/${createdUser.id}`, JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody).not.toHaveProperty('userType');
    });

    it('should patch user - remove operation - complex attribute', async () => {
      const email = `${uniqueName('user-patch4', true)}@example.com`;
      const createdUser = await fixture.createUser(createScimUserBody(email, 'Barbara', 'Jensen', uniqueName('patch4', true)));

      const body = {
        schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
        Operations: [{ op: 'Remove', path: 'name.middleName' }],
      };

      const response = await performPatch(fixture.scimEndpoint, `/Users/${createdUser.id}`, JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.name).not.toHaveProperty('middleName');
      expect(resBody.name).toHaveProperty('familyName');
    });

    it('should patch user - multiple operations', async () => {
      const email = `${uniqueName('user-patch5', true)}@example.com`;
      const createdUser = await fixture.createUser(createScimUserBody(email, 'Barbara', 'Jensen', uniqueName('patch5', true)));

      const body = {
        schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
        Operations: [
          {
            op: 'Add',
            path: 'emails',
            value: [{ value: `${uniqueName('extra', true)}@mail.com`, type: 'home' }],
          },
          { op: 'Replace', path: 'userType', value: 'Director' },
          { op: 'Replace', path: 'name.givenName', value: 'Barbara' },
          { op: 'Replace', path: 'active', value: false },
          { op: 'Remove', path: 'name.middleName' },
        ],
      };

      const response = await performPatch(fixture.scimEndpoint, `/Users/${createdUser.id}`, JSON.stringify(body), {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
        'Content-Type': 'application/json',
      });

      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.emails.some((e: { value: string }) => e.value.endsWith('@mail.com'))).toBeTruthy();
      expect(resBody.userType).toEqual('Director');
      expect(resBody.active).toEqual(false);
      expect(resBody.name).not.toHaveProperty('middleName');
    });

    it('should list users', async () => {
      const response = await performGet(fixture.scimEndpoint, '/Users', {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
      });
      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.totalResults).toBeGreaterThanOrEqual(0);
      expect(Array.isArray(resBody.Resources)).toBeTruthy();
    });

    it('should filter users - simple filter - eq', async () => {
      const userName = `${uniqueName('filter-eq', true)}@example.com`;
      await fixture.createUser(createScimUserBody(userName, 'Barbara', 'Jensen', uniqueName('fe', true)));

      const response = await performGet(fixture.scimEndpoint, `/Users?filter=userName eq "${userName}"`, {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
      });
      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.totalResults).toEqual(1);
      expect(resBody.Resources[0].userName).toEqual(userName);
    });

    it('should filter users - simple filter - regex', async () => {
      const substring = uniqueName('filter-co', true);
      const userName = `${substring}@example.com`;
      await fixture.createUser(createScimUserBody(userName, 'Barbara', 'Jensen', uniqueName('fc', true)));

      const response = await performGet(fixture.scimEndpoint, `/Users?filter=userName co "${substring}"`, {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
      });
      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.totalResults).toBeGreaterThanOrEqual(1);
      const matched = resBody.Resources.some((u: { userName: string }) => u.userName.includes(substring));
      expect(matched).toBeTruthy();
    });

    it('should filter users - complex filter - or', async () => {
      const userName = `${uniqueName('filter-or', true)}@example.com`;
      await fixture.createUser(createScimUserBody(userName, 'Barbara', 'Jensen', uniqueName('for', true)));

      const response = await performGet(fixture.scimEndpoint, `/Users?filter=userName eq "invalid" or name.givenName eq "Barbara"`, {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
      });
      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.totalResults).toBeGreaterThanOrEqual(1);
      const matched = resBody.Resources.some((u: { name: { givenName: string } }) => u.name.givenName === 'Barbara');
      expect(matched).toBeTruthy();
    });

    it('should filter users - complex filter - and', async () => {
      const userName = `${uniqueName('filter-and', true)}@example.com`;
      const u = createScimUserBody(userName, 'Barbara', 'Jensen', uniqueName('fand', true));
      u.active = false;
      await fixture.createUser(u);

      const response = await performGet(fixture.scimEndpoint, `/Users?filter=name.givenName eq "Barbara" and active eq false`, {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
      });
      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.totalResults).toBeGreaterThanOrEqual(1);
      const matched = resBody.Resources.some(
        (u: { name: { givenName: string }; active: boolean }) => u.name.givenName === 'Barbara' && u.active === false,
      );
      expect(matched).toBeTruthy();
    });

    it('should return 400 for invalid filter syntax', async () => {
      const response = await performGet(fixture.scimEndpoint, `/Users?filter=invalid`, {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
      });
      expect(response.status).toEqual(400);
      const resBody = response.body;
      expect(resBody.status).toEqual('400');
      expect(resBody.scimType).toEqual('invalidSyntax');
    });

    it('should return 200 with 0 results for unknown user filter', async () => {
      const response = await performGet(
        fixture.scimEndpoint,
        `/Users?filter=userName eq "${uniqueName('nonexistent', true)}@example.com"`,
        {
          Authorization: `Bearer ${fixture.scimAccessToken}`,
        },
      );
      expect(response.status).toEqual(200);
      const resBody = response.body;
      expect(resBody.totalResults).toEqual(0);
      expect(resBody.Resources.length).toEqual(0);
    });

    it('should delete user', async () => {
      const email = `${uniqueName('user-delete', true)}@example.com`;
      const createdUser = await fixture.createUser(createScimUserBody(email, 'Barbara', 'Jensen', uniqueName('del', true)));

      const response = await performDelete(fixture.scimEndpoint, `/Users/${createdUser.id}`, {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
      });
      expect(response.status).toEqual(204);
    });

    it('should return 404 when deleting unknown user', async () => {
      const response = await performDelete(fixture.scimEndpoint, '/Users/unknown-id', {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
      });
      expect(response.status).toEqual(404);
    });
  });
});
