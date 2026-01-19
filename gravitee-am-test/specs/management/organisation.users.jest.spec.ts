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

import { afterAll, beforeAll, expect } from '@jest/globals';
import {
  createOrganisationUser,
  deleteOrganisationUser,
  getCurrentUser,
  getOrganisationUserPage,
  updateOrganisationUsername,
} from '@management-commands/organisation-user-commands';

import { requestAccessToken, requestAdminAccessToken } from '@management-commands/token-management-commands';
import { buildTestUser, bulkCreateOrgUsers } from '@management-commands/user-management-commands';
import { BulkResponse } from '@management-models/BulkResponse';
import { checkBulkResponse, uniqueName } from '@utils-commands/misc';
import { User } from '@management-models/User';
import { waitFor } from '@management-commands/domain-management-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { createRandomString, getOrganisationManagementUrl } from '@management-commands/service/utils';
import { setup } from '../test-fixture';

setup(200000);

let accessToken;
let organisationUser: User;
const password = 'SomeP@ssw0rd';
let organisationUserToken;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  const firstName = 'orgUserFirstName';
  const lastName = 'orgUserLastName';
  const payload = {
    firstName: firstName,
    lastName: lastName,
    email: `${firstName}.${lastName}@mail.com`,
    username: uniqueName('org').toLowerCase(),
    password: password,
    preRegistration: false,
  };
  organisationUser = await createOrganisationUser(accessToken, payload);
  expect(organisationUser.id).toBeDefined();
  expect(organisationUser.firstName).toEqual(payload.firstName);
  expect(organisationUser.lastName).toEqual(payload.lastName);
  expect(organisationUser.username).toEqual(payload.username);
  expect(organisationUser.email).toEqual(payload.email);
  await waitFor(3000);
});

describe('when creating organization users in bulk', () => {
  it('should create all users ', async () => {
    let usersToCreate = [];
    for (let i = 0; i < 10; i++) {
      usersToCreate.push(buildTestUser(Math.floor(Math.random() * 100000)));
    }
    const response = await bulkCreateOrgUsers(accessToken, usersToCreate);
    expectAllUsersCreatedOk(response, usersToCreate.length);
  });

  it('should create org users & service accounts and report errors', async () => {
    let usersToCreate = [];
    for (let i = 0; i < 10; i++) {
      const serviceAccount = i < 4; // make some of the users service accounts
      usersToCreate.push(buildTestUser(Math.floor(Math.random() * 100000), { serviceAccount }));
    }
    usersToCreate.push(usersToCreate[4]); // duplicate one user
    const response = await bulkCreateOrgUsers(accessToken, usersToCreate);
    expectAllUsersCreatedExceptOneError(response, usersToCreate);
  });

  it('should not create users, more than 1MB', async () => {
    let usersToCreate = [];
    for (let i = 0; i < 250; i++) {
      let user = {
        firstName: createRandomString(1000),
        lastName: createRandomString(1000),
        email: `${createRandomString(1000)}.${createRandomString(1000)}@example.com`,
        username: `${createRandomString(1000)}.${createRandomString(1000)}`,
        password: 'Password1234567!',
        preRegistration: false,
        serviceAccount: false,
      };
      usersToCreate.push(user);
    }

    const request = {
      action: 'CREATE',
      items: usersToCreate,
    };

    const response = await performPost(getOrganisationManagementUrl(), '/users/bulk', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    });
    expect(response.status).toEqual(413);
    expect(response.body.message).toEqual('The size of the bulk operation exceeds the maxPayloadSize (1048576).');
  });

  it('should not create users, more than 1000', async () => {
    let usersToCreate = [];
    for (let i = 0; i < 1001; i++) {
      usersToCreate.push(buildTestUser(1001 + i));
    }

    const request = {
      action: 'CREATE',
      items: usersToCreate,
    };
    const response = await performPost(getOrganisationManagementUrl(), '/users/bulk', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    });
    expect(response.status).toEqual(413);
    expect(response.body.message).toEqual('The bulk operation exceeds the maximum number of operations (1000).');
  });
});

describe('when managing users at organisation level', () => {
  it('it should get current user', async () => {
    organisationUserToken = await requestAccessToken(organisationUser.username, password);
    const currentOrgUser = await getCurrentUser(organisationUserToken);
    expect(currentOrgUser.email).toEqual(organisationUser.email);
  });

  it('should change organisation username', async () => {
    const username = uniqueName('my-new-username').toLowerCase();
    organisationUser = await updateOrganisationUsername(accessToken, organisationUser.id, username);
    expect(organisationUser.username).toEqual(username);
  });

  it('should not be authorized due to username change', async () => {
    let isUserDisconnected = false;
    try {
      await getCurrentUser(organisationUserToken);
    } catch (e) {
      isUserDisconnected = true;
    }
    expect(isUserDisconnected).toBeTruthy();
  });

  it('it should get current user with new username', async () => {
    const newOrganisationUserToken = await requestAccessToken(organisationUser.username, password);

    expect(newOrganisationUserToken).toBeDefined();
    expect(newOrganisationUserToken).not.toEqual(organisationUserToken);
  });
});

afterAll(async () => {
  if (organisationUser && organisationUser.id) {
    await deleteOrganisationUser(accessToken, organisationUser.id);
    const userPage = await getOrganisationUserPage(accessToken);
    expect(userPage.data.find((user) => user.id === organisationUser.id)).toBeUndefined();
  }
});

function expectAllUsersCreatedOk(response: BulkResponse, numberOfUsers: number) {
  let ids = [];
  checkBulkResponse(response, numberOfUsers, true, {
    201: {
      count: numberOfUsers,
      assertions: (result) => {
        expect(result.httpStatus).toBe(201);
        expect(result.body).toBeDefined();
        const newUserId = result.body.id;
        expect(newUserId).toBeDefined();
        expect(ids).not.toContain(newUserId);
        ids.push(newUserId);
      },
    },
  });
}
function expectAllUsersCreatedExceptOneError(response: BulkResponse, usersToCreate: any[]) {
  let ids = [];
  checkBulkResponse(response, usersToCreate.length, false, {
    201: {
      count: usersToCreate.length - 1,
      assertions: (result) => {
        expect(result.body).toBeDefined();
        const newUserId = result.body.id;
        expect(newUserId).toBeDefined();
        expect(ids).not.toContain(newUserId);
        ids.push(newUserId);
      },
    },
    400: {
      count: 1,
      assertions: (result) => {
        expect(result.body).toBeUndefined();
        expect(result.errorDetails).toHaveProperty('error');
        expect(result.errorDetails).toHaveProperty('message');
      },
    },
  });
}
