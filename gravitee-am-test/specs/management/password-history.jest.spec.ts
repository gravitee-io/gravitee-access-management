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
import {
  safeDeleteDomain,
  setupDomainForTest,
  waitForDomainSync,
  waitFor,
} from '@management-commands/domain-management-commands';
import { buildCreateAndTestUser, resetUserPassword } from '@management-commands/user-management-commands';

import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { ResponseError } from '../../api/management/runtime';
import { createPasswordPolicy } from '@management-commands/password-policy-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../test-fixture';

setup(200000);

let accessToken;
let domain;
let user;

describe('Testing password history...', () => {
  beforeAll(async () => {
    accessToken = await requestAdminAccessToken();
    domain = await setupDomainForTest(uniqueName('ph-users', true), { accessToken, waitForStart: false }).then((it) => it.domain);

    await createPasswordPolicy(domain.id, accessToken, {
      name: 'default',
      passwordHistoryEnabled: true,
      oldPasswords: 3,
    });

    user = await buildCreateAndTestUser(domain.id, accessToken, -1, false);
    await waitForDomainSync(domain.id);
  });

  const desc = (password) => `when an admin resets a user's password with ${password}`;
  const testPass = 'succeeds as it is not in history';
  const testFail = 'fails as it is already in history';
  const tests = [
    {
      password: 'SomeP@ssw0rd',
      description: desc,
      test: testFail,
      expectFunc: (password) => expectResetToThrow(password),
    },
    {
      password: 'SomeP@ssw0rd01',
      description: desc,
      test: testPass,
      expectFunc: (password) => expectResetNotToThrow(password),
    },
    {
      password: 'SomeP@ssw0rd02',
      description: desc,
      test: testPass,
      expectFunc: (password) => expectResetNotToThrow(password),
    },
    {
      password: 'SomeP@ssw0rd03',
      description: desc,
      test: testPass,
      expectFunc: (password) => expectResetNotToThrow(password),
    },
    {
      password: 'SomeP@ssw0rd',
      description: desc,
      test: 'succeeds as password is no longer in history',
      expectFunc: (password) => expectResetNotToThrow(password),
    },
  ];

  tests.forEach(({ password, description, test, expectFunc }) => {
    describe(description(password), () => {
      it(test, () => {
        expectFunc(password);
      });
    });
  });

  afterEach(async () => {
    // Brief delay to let password history persist in the database before the next test
    await waitFor(500);
  });

  afterAll(async () => {
    await waitFor(500);
    if (domain && domain.id) {
      await safeDeleteDomain(domain.id, accessToken);
    }
  });
});

const expectResetToThrow = async (password) => {
  await expect(async () => {
    await resetUserPassword(domain.id, accessToken, user.id, password);
  }).rejects.toThrow(ResponseError);
};

const expectResetNotToThrow = async (password) => {
  await expect(async () => {
    await resetUserPassword(domain.id, accessToken, user.id, password);
  }).not.toThrow(ResponseError);
};
