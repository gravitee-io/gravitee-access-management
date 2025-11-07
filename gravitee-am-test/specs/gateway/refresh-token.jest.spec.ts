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
import * as faker from 'faker';
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import { createDomain, safeDeleteDomain, startDomain, waitForDomainSync } from '@management-commands/domain-management-commands';
import { buildCreateAndTestUser, updateUserStatus } from '@management-commands/user-management-commands';

import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getWellKnownOpenIdConfiguration, performPost } from '@gateway-commands/oauth-oidc-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';

global.fetch = fetch;

let accessToken;
let domain;
let defaultIdp;
let client;
let user;
let oidc;

jest.setTimeout(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  const createdDomain = await createDomain(accessToken, uniqueName('refresh-tokens', true), faker.company.catchPhraseDescriptor());
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();

  const idpSet = await getAllIdps(createdDomain.id, accessToken);
  defaultIdp = idpSet.values().next().value;

  client = await createTestApp('webapp', createdDomain, accessToken, 'WEB', {
    settings: {
      oauth: {
        redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
        grantTypes: ['authorization_code', 'password', 'refresh_token'],
        scopeSettings: [
          { scope: 'openid', defaultScope: true },
          { scope: 'email', defaultScope: true },
          { scope: 'profile', defaultScope: true },
        ],
      },
    },
    identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
  });

  const domainStarted = await startDomain(createdDomain.id, accessToken);
  expect(domainStarted).toBeDefined();
  expect(domainStarted.id).toEqual(createdDomain.id);

  domain = domainStarted;
  await new Promise((r) => setTimeout(r, 10000));

  const result = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);
  oidc = result.body;

  user = await buildCreateAndTestUser(domain.id, accessToken, 0);
});

afterAll(async () => {
  if (domain?.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});

describe('when user is enabled', () => {
  let tokens;

  it('they can obtain access_token and refresh_token', async () => {
    let response = await performPost(oidc.token_endpoint, '', `grant_type=password&username=${user.username}&password=SomeP@ssw0rd`, {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(client),
    }).expect(200);
    tokens = response.body;
  });

  it('and the new token can be obtained by refresh_token', async () => {
    let response = await performPost(oidc.token_endpoint, '', `grant_type=refresh_token&refresh_token=${tokens.refresh_token}`, {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(client),
    }).expect(200);
    tokens = response.body;
  });

  describe('tokens will be revoked', () => {
    it('when user is disabled by MAPI', async () => {
      await updateUserStatus(domain.id, accessToken, user.id, false);
      await waitForDomainSync(domain.id, accessToken); // wait sync as since 4.7, token revocations are managed asynchornously
      let response = await performPost(oidc.token_endpoint, '', `grant_type=refresh_token&refresh_token=${tokens.refresh_token}`, {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + applicationBase64Token(client),
      }).expect(400);
      expect(response.body.error).toEqual('invalid_grant');
      expect(response.body.error_description).toEqual('Refresh token is invalid');
    });

    it('and will remain revoked, when user is enabled back', async () => {
      await updateUserStatus(domain.id, accessToken, user.id, true);
      await waitForDomainSync(domain.id, accessToken); // wait sync as since 4.7, token revocations are managed asynchornously
      let response = await performPost(oidc.token_endpoint, '', `grant_type=refresh_token&refresh_token=${tokens.refresh_token}`, {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + applicationBase64Token(client),
      }).expect(400);
      expect(response.body.error).toEqual('invalid_grant');
      expect(response.body.error_description).toEqual('Refresh token is invalid');
    });
  });
});
