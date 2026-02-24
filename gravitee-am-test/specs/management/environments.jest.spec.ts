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

import { beforeAll, describe, expect, it } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { getOrganisationManagementUrl } from '@management-commands/service/utils';
import { setup } from '../test-fixture';

setup();

let accessToken: string;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();
});

const authHeaders = () => ({
  'Content-Type': 'application/json',
  Authorization: `Bearer ${accessToken}`,
});

describe('Environment Management - List Environments', () => {
  it('should list exactly one default environment with expected shape', async () => {
    const response = await performGet(getOrganisationManagementUrl(), '/environments', authHeaders());

    expect(response.status).toBe(200);
    const body = response.body;
    expect(body).toHaveLength(1);

    const env = body[0];
    expect(env.id).toEqual(process.env.AM_DEF_ENV_ID);
    expect(env.name).toEqual('Default environment');
    expect(env.description).toEqual('Default environment');
    expect(env.domainRestrictions).toEqual([]);
    expect(env.hrids).toEqual(['default']);
  });
});

describe('Environment Management - Error Handling', () => {
  it('should reject unauthenticated request with 401', async () => {
    const response = await performGet(getOrganisationManagementUrl(), '/environments', {
      'Content-Type': 'application/json',
    });

    expect(response.status).toBe(401);
  });

  it('should deny access with wrong organization ID', async () => {
    const response = await performGet(`${process.env.AM_MANAGEMENT_URL}/management/organizations/OTHER`, '/environments', authHeaders());

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });
});
