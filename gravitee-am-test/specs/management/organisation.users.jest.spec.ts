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

import fetch from 'cross-fetch';
import { afterAll, beforeAll, expect } from '@jest/globals';
import {
  createOrganisationUser,
  deleteOrganisationUser,
  getCurrentUser,
  getOrganisationUserPage,
  updateOrganisationUsername,
} from '@management-commands/organisation-user-commands';

import { requestAccessToken, requestAdminAccessToken } from '@management-commands/token-management-commands';
import {buildTestUser,bulkCreateOrgUsers} from '@management-commands/user-management-commands';
import {BulkResponse} from '@management-models/BulkResponse';
import {checkBulkResponse,uniqueName} from '@utils-commands/misc';
import {User} from '@management-models/User';
import {waitFor} from '@management-commands/domain-management-commands';

global.fetch = fetch;

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
  await waitFor(3000)
  console.log(`using user: ${organisationUser.username}:${password}`)
});

describe('when creating organization users in bulk', () => {
  it('should create all users ', async () => {
    let usersToCreate = [];
    for (let i = 0; i < 10; i++) {
      usersToCreate.push(buildTestUser(Math.floor(Math.random() * 100000)));
    }
    console.log('Creating org users: ', usersToCreate);
    const response = await bulkCreateOrgUsers(accessToken, usersToCreate);
    console.log('Response', JSON.stringify(response, null, 2));
    expectAllUsersCreatedOk(response, usersToCreate.length);
  });

  it('should create org users & service accounts and report errors', async () => {
    const numUniqueUsersToCreate = 10;
    let usersToCreate = [];
    for (let i = 0; i < 10; i++) {
      const serviceAccount = Math.random() < 0.4; // make some of the users service accounts
      return buildTestUser(Math.floor(Math.random() * 100000), { serviceAccount });
    }
    usersToCreate.push(usersToCreate[4]); // duplicate one user
    console.log('Creating org users: ', usersToCreate);
    const response = await bulkCreateOrgUsers(accessToken, usersToCreate);
    console.log('Response', JSON.stringify(response, null, 2));
    expectAllUsersCreatedExceptOneError(response, usersToCreate);
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
  console.log('Response', JSON.stringify(response, null, 2));
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
