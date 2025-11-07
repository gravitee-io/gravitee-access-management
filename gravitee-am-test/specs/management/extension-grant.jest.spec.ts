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
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import {
  createExtensionGrant,
  deleteExtensionGrant,
  getExtensionGrant,
  listExtensionGrant,
  updateExtensionGrant,
} from '@management-commands/extension-grant-commands';
import { createApplication, patchApplication } from '@management-commands/application-management-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { buildCreateAndTestUser, deleteUser, getAllUsers } from '@management-commands/user-management-commands';
import { delay, uniqueName } from '@utils-commands/misc';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { generateSignedJwt, getPublicKey } from '@utils-commands/jwt';

global.fetch = fetch;

let accessToken: any;
let domain: any;
let extensionGrant: any;
let app: any;
let tokenEndpoint: any;
let defaultIdp: any;
let pub: any;
let user: any;

jest.setTimeout(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  const startedDomain = await setupDomainForTest(uniqueName('extension-grant', true), { accessToken, waitForStart: true });
  domain = startedDomain.domain;
  tokenEndpoint = startedDomain.oidcConfig.token_endpoint;

  const appBody = {
    name: 'app',
    type: 'WEB',
    clientId: 'app',
    clientSecret: 'app',
    redirectUris: ['https://callback'],
  };

  app = await createApplication(domain.id, accessToken, appBody);

  const identityProviders = await getAllIdps(domain.id, accessToken);
  defaultIdp = identityProviders.values().next().value.id;
  pub = getPublicKey();
  user = await buildCreateAndTestUser(domain.id, accessToken, 0);
});

describe('when creating extension grant', () => {
  it('must create new extension grant: ', async () => {
    const body = {
      configuration: `{\"publicKey\":\"ssh-rsa ${pub}\"}`,
      grantType: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      name: 'extenstion grant',
      type: 'jwtbearer-am-extension-grant',
    };
    const extensionGrantResponse = await createExtensionGrant(domain.id, accessToken, body);
    expect(extensionGrantResponse.id).toBeDefined();
    extensionGrant = extensionGrantResponse;
  });
});

describe('when manipulating extension grant', () => {
  it('must get extension grant:', async () => {
    const grant = await getExtensionGrant(domain.id, accessToken, extensionGrant.id);
    expect(grant).toBeDefined();
    expect(grant.id).toEqual(extensionGrant.id);
  });
  it('must get list of extension grants:', async () => {
    const grants = await listExtensionGrant(domain.id, accessToken);
    expect(grants).toHaveLength(1);
  });
  it('must update extension grant:', async () => {
    const updated = await updateExtensionGrant(domain.id, accessToken, extensionGrant.id, {
      ...extensionGrant,
      name: 'new extension grant',
    });
    expect(updated.name === extensionGrant.name).toBeFalsy();
    extensionGrant = updated;
  });
  it('must remove extension grant:', async () => {
    await deleteExtensionGrant(domain.id, accessToken, extensionGrant.id);
    const grants = await listExtensionGrant(domain.id, accessToken);
    expect(grants).toHaveLength(0);
  });
});

describe('when user creation', () => {
  it('must create extension grant and assign to application', async () => {
    const body = {
      configuration: `{\"publicKey\":\"${pub}\", \"claimsMapper\":[{\"assertion_claim\":\"a\",\"token_claim\":\"a1\"},{\"assertion_claim\":\"b\",\"token_claim\":\"b1\"}] }`,
      grantType: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      name: 'extenstion grant',
      type: 'jwtbearer-am-extension-grant',
      createUser: true,
      userExists: false,
      identityProvider: defaultIdp,
    };
    extensionGrant = await createExtensionGrant(domain.id, accessToken, body);
    expect(extensionGrant).toBeDefined();
    const grantType = extensionGrant.grantType + '~' + extensionGrant.id;
    const patch = {
      settings: {
        oauth: {
          grantTypes: [grantType],
        },
      },
    };
    const patchedApp = await patchApplication(domain.id, accessToken, patch, app.id);
    expect(patchedApp).toBeDefined();
    //wait for domain sync
    await delay(6000);
  });

  it('must create new user and map claims:', async () => {
    const assertion = generateSignedJwt({
      sub: '1234567890',
      a: 'testA',
      b: 'testB',
    });
    const response = await performPost(tokenEndpoint, '', 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=' + assertion, {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(app),
    }).expect(200);
    expect(response.body.access_token).toBeDefined();
    //check user was created
    const allUsers = await getAllUsers(domain.id, accessToken);
    expect(allUsers.totalCount).toEqual(2);
    const createdUser = allUsers.data.filter((user) => user.username === '1234567890')[0];
    expect(createdUser).toBeDefined();
    expect(createdUser.username).toEqual('1234567890');
    expect(createdUser.additionalInformation['a1']).toEqual('testA');
    expect(createdUser.additionalInformation['b1']).toEqual('testB');
  });

  it('must check if user exists:', async () => {
    const body = {
      configuration: `{\"publicKey\":\"${pub}\", \"claimsMapper\":[{\"assertion_claim\":\"a\",\"token_claim\":\"a1\"},{\"assertion_claim\":\"b\",\"token_claim\":\"b1\"}] }`,
      grantType: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      name: 'extenstion grant',
      type: 'jwtbearer-am-extension-grant',
      createUser: false,
      userExists: true,
      identityProvider: defaultIdp,
    };
    const updatedExtensionGrant = await updateExtensionGrant(domain.id, accessToken, extensionGrant.id, body);
    expect(updatedExtensionGrant.id).toBeDefined();
    //wait for domain sync
    await delay(6000);
    const assertion = generateSignedJwt({
      sub: user.id,
    });
    const response = await performPost(tokenEndpoint, '', 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=' + assertion, {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(app),
    });
    expect(response.body.access_token).toBeDefined();
  });

  it('must not return if user doesnt exists:', async () => {
    const assertion = generateSignedJwt({
      sub: '1',
    });
    await performPost(tokenEndpoint, '', 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=' + assertion, {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(app),
    }).expect(400, {
      error: 'invalid_grant',
      error_description: 'Unknown user: 1',
    });
  });

  it('must not check if user exist and not create user:', async () => {
    const body = {
      configuration: `{\"publicKey\":\"${pub}\", \"claimsMapper\":[{\"assertion_claim\":\"a\",\"token_claim\":\"a1\"},{\"assertion_claim\":\"b\",\"token_claim\":\"b1\"}] }`,
      grantType: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      name: 'extenstion grant',
      type: 'jwtbearer-am-extension-grant',
      createUser: false,
      userExists: false,
      identityProvider: defaultIdp,
    };
    const updatedExtensionGrant = await updateExtensionGrant(domain.id, accessToken, extensionGrant.id, body);
    expect(updatedExtensionGrant.id).toBeDefined();

    //wait for domain sync
    await delay(6000);

    const assertion = generateSignedJwt({
      sub: '123',
    });

    await performPost(tokenEndpoint, '', 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=' + assertion, {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(app),
    }).expect(200);
    const allUsers = await getAllUsers(domain.id, accessToken);
    const usernames = allUsers.data.map((user) => user.username);
    expect(usernames.indexOf('123')).toEqual(-1);
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await deleteUser(domain.id, accessToken, user.id);
    await safeDeleteDomain(domain.id, accessToken);
  }
});
