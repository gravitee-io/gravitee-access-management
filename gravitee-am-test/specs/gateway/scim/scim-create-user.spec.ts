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
import { afterAll, beforeAll, describe, expect } from '@jest/globals';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { getUserPage } from '@management-commands/user-management-commands';
import { uniqueName } from '@utils-commands/misc';
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

describe('SCIM ', () => {
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

    // Clear emails for this specific recipient BEFORE creating the user to avoid interference from other tests
    await fixture.clearMailbox(testUserEmail);

    const response = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${fixture.scimAccessToken}`,
    }).expect(201);
    const createdUser = response.body;

    expect(createdUser).toBeDefined();
    // Pre registered user are not enabled.
    // They have to provide a password first.
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
    // Generate unique identifiers
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

    // Clear emails for this specific recipient
    await fixture.clearMailbox(userEmail);

    const response = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${fixture.scimAccessToken}`,
    }).expect(201);
    const createdUser = response.body;

    expect(createdUser).toBeDefined();
    expect(createdUser.enabled).toBeFalsy();

    // Retrieve confirmation email
    const confirmationLink = await fixture.extractConfirmRegistrationLink(userEmail);
    expect(confirmationLink).toBeDefined();

    // Verify email contains client_id parameter
    const url = new URL(confirmationLink);
    const clientIdParam = url.searchParams.get('client_id');
    expect(clientIdParam).toBeDefined();
    expect(clientIdParam).toBe(fixture.application.settings.oauth.clientId);

    // Verify user was created with client
    const users = await getUserPage(fixture.domain.id, fixture.accessToken);
    const user = users.data.find((u) => u.email === userEmail);
    expect(user).toBeDefined();
    expect(user.client).toBe(fixture.application.id);

    await fixture.clearMailbox(userEmail);
  });
});
