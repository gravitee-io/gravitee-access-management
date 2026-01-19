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

import * as faker from 'faker';
import { afterAll, beforeAll, expect } from '@jest/globals';
import { createDomain, safeDeleteDomain, startDomain, waitForDomainStart } from '@management-commands/domain-management-commands';
import {
  buildCreateAndTestUser,
  buildTestUser,
  bulkCreateUsers,
  deleteUser,
  getAllUsers,
  getUser,
  getUserPage,
  lockUser,
  resetUserPassword,
  sendRegistrationConfirmation,
  unlockUser,
  updateUser,
  updateUsername,
  updateUserStatus,
} from '@management-commands/user-management-commands';

import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { ResponseError } from '../../api/management/runtime';
import { checkBulkResponse, uniqueName } from '@utils-commands/misc';
import { BulkResponse } from '@management-models/BulkResponse';
import { setup } from '../test-fixture';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { createRandomString, getDomainManagerUrl } from '@management-commands/service/utils';

setup(200000);

let accessToken;
let domain;
let user;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  const createdDomain = await createDomain(accessToken, uniqueName('users', true), faker.company.catchPhraseDescriptor());
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();

  const domainStarted = await startDomain(createdDomain.id, accessToken)
    .then((domain) => waitForDomainStart(domain))
    .then((result) => result.domain);
  expect(domainStarted).toBeDefined();
  expect(domainStarted.id).toEqual(createdDomain.id);

  domain = domainStarted;
});

describe('when using the users commands', () => {
  it('must create new user: ', async () => {
    user = await buildCreateAndTestUser(domain.id, accessToken, 0);
  });
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
function expectAllUsersCreatedExceptOneError(response: BulkResponse, responseCount: number) {
  let ids = [];
  checkBulkResponse(response, responseCount, false, {
    201: {
      count: responseCount - 1,
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

describe('when creating users in bulk', () => {
  it('should create all users ', async () => {
    let usersToCreate = [];
    for (let i = 0; i < 10; i++) {
      usersToCreate.push(buildTestUser(100 + i));
    }
    const response = await bulkCreateUsers(domain.id, accessToken, usersToCreate);
    expectAllUsersCreatedOk(response, usersToCreate.length);
  });

  it('should create users even if there are errors and report errors', async () => {
    let usersToCreate = [];
    for (let i = 0; i < 10; i++) {
      usersToCreate.push(buildTestUser(200 + i));
    }
    usersToCreate.push(buildTestUser(204)); // duplicate
    const response = await bulkCreateUsers(domain.id, accessToken, usersToCreate);
    expectAllUsersCreatedExceptOneError(response, usersToCreate.length);
  });

  it('should stop creating users when failOnErrors is set', async () => {
    let usersToCreate = [];
    usersToCreate.push(buildTestUser(304)); // will be duplicated
    for (let i = 0; i < 10; i++) {
      usersToCreate.push(buildTestUser(300 + i));
    }
    const response = await bulkCreateUsers(domain.id, accessToken, usersToCreate, 1);
    expectAllUsersCreatedExceptOneError(response, 6);
  });

  it('should not create users, more than 1MB', async () => {
    let usersToCreate = [];
    for (let i = 0; i < 500; i++) {
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

    const response = await performPost(getDomainManagerUrl(domain.id), '/users/bulk', JSON.stringify(request), {
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

    const response = await performPost(getDomainManagerUrl(domain.id), '/users/bulk', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    });
    expect(response.status).toEqual(413);
    expect(response.body.message).toEqual('The bulk operation exceeds the maximum number of operations (1000).');
  });
});

describe('after creating users', () => {
  it('must find User by id', async () => {
    const foundUser = await getUser(domain.id, accessToken, user.id);
    expect(foundUser).toBeDefined();
    expect(foundUser.id).toEqual(user.id);
  });

  it('must update User', async () => {
    const updatedUser = await updateUser(domain.id, accessToken, user.id, {
      ...user,
      firstName: 'another-name',
    });
    expect(user.firstName === updatedUser.firstName).toBeFalsy();
  });

  it('must find all Users', async () => {
    const applicationPage = await getAllUsers(domain.id, accessToken);

    expect(applicationPage.currentPage).toEqual(0);
    expect(applicationPage.totalCount).toEqual(26);
    expect(applicationPage.data.length).toEqual(26);
  });

  it('must find User page', async () => {
    const UserPage = await getUserPage(domain.id, accessToken, 1, 3);

    expect(UserPage.currentPage).toEqual(1);
    expect(UserPage.totalCount).toEqual(26);
    expect(UserPage.data.length).toEqual(3);
  });

  it('must find last User page', async () => {
    const UserPage = await getUserPage(domain.id, accessToken, 3, 3);

    expect(UserPage.currentPage).toEqual(3);
    expect(UserPage.totalCount).toEqual(26);
    expect(UserPage.data.length).toEqual(3);
  });

  it('must change user status', async () => {
    const updatedUser = await updateUserStatus(domain.id, accessToken, user.id, false);

    expect(updatedUser.enabled).not.toEqual(user.enabled);
    user = updatedUser;
  });

  it('should change username', async () => {
    const username = faker.internet.userName();
    const updatedUser = await updateUsername(domain.id, accessToken, user.id, username);

    expect(updatedUser.username).toEqual(username);
    user = updatedUser;
  });

  it('username update should fail with invalid user ID', async () => {
    const username = faker.internet.userName();
    await expect(async () => {
      await updateUsername(domain.id, accessToken, 123, username);
    }).rejects.toThrow(ResponseError);
  });

  it('must reset user password', async () => {
    await resetUserPassword(domain.id, accessToken, user.id, 'SomeOtherP@ssw0rd');
    // We cannot test here, we just believe if nothing breaks it's all good.
    expect(true).toBeTruthy();
  });

  it('must send registration confirmation', async () => {
    const preRegisteredUser = await buildCreateAndTestUser(domain.id, accessToken, 1000, true);
    await sendRegistrationConfirmation(domain.id, accessToken, preRegisteredUser.id);
    // same as above
    expect(true).toBeTruthy();
    await deleteUser(domain.id, accessToken, preRegisteredUser.id);
  });

  it('must lock user', async () => {
    await lockUser(domain.id, accessToken, user.id);
    const foundUser = await getUser(domain.id, accessToken, user.id);
    // same as above
    expect(foundUser.accountNonLocked).toBeFalsy();
    user = foundUser;
  });

  it('must unlock user', async () => {
    await unlockUser(domain.id, accessToken, user.id);
    const foundUser = await getUser(domain.id, accessToken, user.id);
    // same as above
    expect(foundUser.accountNonLocked).toBeTruthy();
    user = foundUser;
  });

  it('Must delete User', async () => {
    await deleteUser(domain.id, accessToken, user.id);
    const UserPage = await getUserPage(domain.id, accessToken);

    expect(UserPage.currentPage).toEqual(0);
    expect(UserPage.totalCount).toEqual(25);
    expect(UserPage.data.length).toEqual(25);
    expect(UserPage.data.find((u) => u.id === user.id)).toBeFalsy();
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});
