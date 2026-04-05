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
import { jira } from '@specs-utils/jira';
import { setup } from '../test-fixture';

setup(200000);

describe('API Verification', () => {
  let accessToken: string;

  beforeAll(async () => {
    accessToken = await requestAdminAccessToken();
  });

  it(jira`should verify key Management API endpoints respond ${'AM-2186'}`, async () => {
    const baseUrl = `${process.env.AM_MANAGEMENT_URL || 'http://localhost:8093'}/management`;
    const orgId = process.env.AM_DEF_ORG_ID || 'DEFAULT';
    const envId = process.env.AM_DEF_ENV_ID || 'DEFAULT';

    // Verify domains list endpoint
    const domainsResponse = await performGet(
      `${baseUrl}/organizations/${orgId}/environments/${envId}/domains`,
      '',
      { Authorization: `Bearer ${accessToken}` },
    ).expect(200);
    expect(Array.isArray(domainsResponse.body.data)).toBe(true);

    // Verify environments endpoint
    const envResponse = await performGet(
      `${baseUrl}/organizations/${orgId}/environments`,
      '',
      { Authorization: `Bearer ${accessToken}` },
    ).expect(200);
    expect(Array.isArray(envResponse.body)).toBe(true);
    expect(envResponse.body.length).toBeGreaterThanOrEqual(1);
  });
});
