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
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, patchDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';

global.fetch = fetch;

let accessToken;
let domain;

jest.setTimeout(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  let startedDomain = await setupDomainForTest(uniqueName('user-accounts-test', true), { accessToken, waitForStart: true });
  domain = startedDomain.domain;
});

describe('User Accounts', () => {
  it('should define the "remember me" amount of time', async () => {
    const patchedDomain = await patchDomain(domain.id, accessToken, {
      path: `${domain.path}`,
      vhostMode: false,
      vhosts: [],
      accountSettings: {
        rememberMe: true,
        rememberMeDuration: 10,
      },
    });

    const accountSettings = patchedDomain.accountSettings;
    expect(accountSettings.rememberMe).toBe(true);
    expect(accountSettings.rememberMeDuration).toBe(10);
  });
});

afterAll(async () => {
  await safeDeleteDomain(domain?.id, accessToken);
});
