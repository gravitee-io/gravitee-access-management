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

import { afterAll, beforeAll, describe, expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { createDomain, safeDeleteDomain, patchDomain, startDomain, waitForDomainSync } from '@management-commands/domain-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { BulkRequest } from '../../../api/gateway/models/scim/BulkRequest/BulkRequest';
import { getWellKnownOpenIdConfiguration, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { BulkOperation } from '../../../api/gateway/models/scim/BulkRequest/BulkOperation';
import { BulkResponse } from '../../../api/gateway/models/scim/BulkRequest/BulkResponse';
import { Error } from '../../../api/gateway/models/scim/BulkRequest/Error';
import { random } from 'faker';
import { uniqueName } from '@utils-commands/misc';
import { retryUntil } from '@utils-commands/retry';

let mngAccessToken: string;
let scimAccessToken: string;
let domain: Domain;
let scimClient: Application;
let scimEndpoint: string;
let testUser01: string;
let testUser02: string;
let testRubble: string;
let testSkye: string;
let testMarshal: string;
let testSky: string;
let testChase: string;

jest.setTimeout(200000);

beforeAll(async function () {
  mngAccessToken = await requestAdminAccessToken();
  expect(mngAccessToken).toBeDefined();

  // Generate unique usernames to avoid conflicts in parallel execution
  testUser01 = uniqueName('user01', true);
  testUser02 = uniqueName('user02', true);
  testRubble = uniqueName('rubble', true);
  testSkye = uniqueName('skye', true);
  testMarshal = uniqueName('marshal', true);
  testSky = uniqueName('sky', true);
  testChase = uniqueName('chase', true);

  domain = await createDomain(mngAccessToken, uniqueName('bulk-scim', true), 'Domain used to test Bulk SCIM requests');
  expect(domain).toBeDefined();

  // enable SCIM
  await patchDomain(domain.id, mngAccessToken, {
    scim: {
      enabled: true,
      idpSelectionEnabled: false,
    },
  });

  const appName = uniqueName('SCIM App', true);
  const applicationDefinition: Application = {
    name: appName,
    type: 'SERVICE',
    settings: {
      oauth: {
        grantTypes: ['client_credentials'],
        scopeSettings: [
          {
            scope: 'scim',
            defaultScope: true,
          },
        ],
      },
    },
  };

  scimClient = await createApplication(domain.id, mngAccessToken, applicationDefinition);
  expect(scimClient).toBeDefined();
  // we need to call update app as the CREATE order is not taking the scope settings into account
  // NOTE: we do not override the scimClient to keep reference of the clientSecret
  await updateApplication(domain.id, mngAccessToken, applicationDefinition, scimClient.id);
  // Ensure application update is synced before starting domain
  await waitForDomainSync(domain.id, mngAccessToken);

  await startDomain(domain.id, mngAccessToken);

  await waitForDomainSync(domain.id, mngAccessToken);
  
  // Verify domain is ready to serve requests by polling OpenID config endpoint
  const result = await retryUntil(
    () => getWellKnownOpenIdConfiguration(domain.hrid) as Promise<any>,
    (res: any) => res.status === 200,
    {
      timeoutMillis: 10000,
      intervalMillis: 200,
      onRetry: () => console.debug(`Domain ${domain.hrid} not ready yet, retrying...`),
    }
  );
  const openIdConfiguration = result.body;

  // generate SCIM access token
  const response = await performPost(openIdConfiguration.token_endpoint, '', 'grant_type=client_credentials', {
    'Content-type': 'application/x-www-form-urlencoded',
    Authorization: 'Basic ' + applicationBase64Token(scimClient),
  });
  scimAccessToken = response.body.access_token;
  scimEndpoint = process.env.AM_GATEWAY_URL + `/${domain.hrid}/scim`;
});

afterAll(async function () {
  if (domain?.id) {
    await safeDeleteDomain(domain.id, mngAccessToken);
  }
});

describe('SCIM Bulk endpoint', () => {
  it('should reject request with invalid schema', async () => {
    const operation: BulkOperation = {
      method: 'POST',
      path: '/Users',
      data: {},
    };

    const request: BulkRequest = {
      schemas: ['unknown'],
      Operations: [operation],
    };

    const scimResponse = await performPost(scimEndpoint, '/Bulk', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(400);

    const scimError: Error = scimResponse.body;

    assertExpectedError(
      scimError,
      '400',
      'invalidSyntax',
      "The 'schemas' attribute MUST only contain values defined as 'schema' and 'schemaExtensions' for the resource's defined BulkRequest type",
    );
  });

  it('should reject request with too many operation', async () => {
    const operation: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {},
    };

    const request: BulkRequest = {
      schemas: ['urn:ietf:params:scim:api:messages:2.0:BulkRequest'],
      Operations: Array.from({ length: 1001 }, (v, i) => operation),
    };

    const scimResponse = await performPost(scimEndpoint, '/Bulk', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(413);
    const scimError: Error = scimResponse.body;
    assertExpectedError(scimError, '413', null, 'The bulk operation exceeds the maximum number of operations (1000).');
  });

  it('should accept request with 3 user creations (two success, one failure)', async () => {
    const operation1: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: testUser01,
      },
    };
    const operation2: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: testUser01,
      },
    };
    const operation3: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: testUser02,
      },
    };

    const request: BulkRequest = {
      schemas: ['urn:ietf:params:scim:api:messages:2.0:BulkRequest'],
      Operations: [operation1, operation2, operation3],
    };

    const scimResponse = await performPost(scimEndpoint, '/Bulk', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(200);

    const bulkResponse: BulkResponse = scimResponse.body;
    expect(bulkResponse.Operations).toHaveLength(3);
    const firstOp = bulkResponse.Operations[0];
    expect(firstOp.bulkId).toBeDefined();
    expect(firstOp.bulkId).toEqual(operation1.bulkId);
    expect(firstOp.status).toEqual('201');
    expect(firstOp.location).toBeDefined();
    expect(firstOp.location).toContain(scimEndpoint + '/Users/');

    const secondOp = bulkResponse.Operations[1];
    expect(secondOp.bulkId).toBeDefined();
    expect(secondOp.bulkId).toEqual(operation2.bulkId);
    expect(secondOp.status).toEqual('409');
    expect(secondOp.location).toBeUndefined();
    expect(secondOp.response).toBeDefined();
    expect(secondOp.response.status).toEqual('409');

    const thirdOp = bulkResponse.Operations[2];
    expect(thirdOp.bulkId).toBeDefined();
    expect(thirdOp.bulkId).toEqual(operation3.bulkId);
    expect(thirdOp.status).toEqual('201');
    expect(thirdOp.location).toBeDefined();
    expect(thirdOp.location).toMatch(scimEndpoint + '/Users/');
  });

  it('should accept request with update user', async () => {
    const userLocation = await createRandomUser();
    const user = await readScimUserProfile(userLocation);

    const changedUser = { ...user };
    changedUser['profileUrl'] = 'https://me';
    changedUser['emails'] = [
      {
        value: user['username'] + '@noware.com',
        type: 'home',
        primary: true,
      },
    ];

    const updateOp: BulkOperation = {
      method: 'PUT',
      path: '/Users/' + userLocation.substring(userLocation.lastIndexOf('/') + 1),
      bulkId: random.word(),
      data: changedUser,
    };

    const updateRequest: BulkRequest = {
      schemas: ['urn:ietf:params:scim:api:messages:2.0:BulkRequest'],
      Operations: [updateOp],
    };

    const scimResponse = await performPost(scimEndpoint, '/Bulk', JSON.stringify(updateRequest), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(200);

    const bulkResponse: BulkResponse = scimResponse.body;
    expect(bulkResponse.Operations).toHaveLength(1);
    const op = bulkResponse.Operations[0];
    expect(op.bulkId).toBeDefined();
    expect(op.bulkId).toEqual(updateOp.bulkId);
    expect(op.status).toEqual('200');
    expect(op.location).toBeDefined();
    expect(op.location).toEqual(userLocation);

    const updatedUser = await readScimUserProfile(userLocation);
    expect(updatedUser.profileUrl).toBeDefined();
    expect(updatedUser.emails).toBeDefined();
    expect(updatedUser.emails).toHaveLength(1);
    expect(updatedUser.emails[0].value).toEqual(user['username'] + '@noware.com');
  });

  it('should reject update if user is unknown', async () => {
    const updateOp: BulkOperation = {
      method: 'PUT',
      path: '/Users/' + random.word(),
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: random.word(),
      },
    };

    const updateRequest: BulkRequest = {
      schemas: ['urn:ietf:params:scim:api:messages:2.0:BulkRequest'],
      Operations: [updateOp],
    };

    const scimResponse = await performPost(scimEndpoint, '/Bulk', JSON.stringify(updateRequest), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(200);

    const bulkResponse: BulkResponse = scimResponse.body;
    expect(bulkResponse.Operations).toHaveLength(1);
    const op = bulkResponse.Operations[0];
    expect(op.bulkId).toBeDefined();
    expect(op.bulkId).toEqual(updateOp.bulkId);
    expect(op.status).toEqual('404');
    expect(op.location).toBeUndefined();
  });

  it('should stop processing at second failure when failOnErrors=2', async () => {
    const operation1: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: testRubble,
      },
    };
    const operation2: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: testRubble,
      },
    };
    const operation3: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: testRubble,
      },
    };
    const operation4: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: testSkye,
      },
    };

    const request: BulkRequest = {
      schemas: ['urn:ietf:params:scim:api:messages:2.0:BulkRequest'],
      failOnErrors: 2,
      Operations: [operation1, operation2, operation3, operation4],
    };

    const scimResponse = await performPost(scimEndpoint, '/Bulk', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(200);

    const bulkResponse: BulkResponse = scimResponse.body;
    expect(bulkResponse.Operations).toHaveLength(3);
    const firstOp = bulkResponse.Operations[0];
    expect(firstOp.bulkId).toBeDefined();
    expect(firstOp.bulkId).toEqual(operation1.bulkId);
    expect(firstOp.status).toEqual('201');
    expect(firstOp.location).toBeDefined();
    expect(firstOp.location).toContain(scimEndpoint + '/Users/');

    const secondOp = bulkResponse.Operations[1];
    expect(secondOp.bulkId).toBeDefined();
    expect(secondOp.bulkId).toEqual(operation2.bulkId);
    expect(secondOp.status).toEqual('409');
    expect(secondOp.location).toBeUndefined();
    expect(secondOp.response).toBeDefined();
    expect(secondOp.response.status).toEqual('409');

    const thirdOp = bulkResponse.Operations[2];
    expect(thirdOp.bulkId).toBeDefined();
    expect(thirdOp.bulkId).toEqual(operation3.bulkId);
    expect(thirdOp.status).toEqual('409');
    expect(thirdOp.location).toBeUndefined();
    expect(thirdOp.response).toBeDefined();
    expect(thirdOp.response.status).toEqual('409');
  });

  it('should process all operations despite single error when failOnErrors=2', async () => {
    const operation1: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: testMarshal,
      },
    };
    const operation2: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: testMarshal,
      },
    };
    const operation3: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: testSky,
      },
    };
    const operation4: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: testChase,
      },
    };

    const request: BulkRequest = {
      schemas: ['urn:ietf:params:scim:api:messages:2.0:BulkRequest'],
      failOnErrors: 2,
      Operations: [operation1, operation2, operation3, operation4],
    };

    const scimResponse = await performPost(scimEndpoint, '/Bulk', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(200);

    const bulkResponse: BulkResponse = scimResponse.body;
    expect(bulkResponse.Operations).toHaveLength(4);

    const firstOp = bulkResponse.Operations[0];
    expect(firstOp.bulkId).toBeDefined();
    expect(firstOp.bulkId).toEqual(operation1.bulkId);
    expect(firstOp.status).toEqual('201');
    expect(firstOp.location).toBeDefined();
    expect(firstOp.location).toContain(scimEndpoint + '/Users/');

    const secondOp = bulkResponse.Operations[1];
    expect(secondOp.bulkId).toBeDefined();
    expect(secondOp.bulkId).toEqual(operation2.bulkId);
    expect(secondOp.status).toEqual('409');
    expect(secondOp.location).toBeUndefined();
    expect(secondOp.response).toBeDefined();
    expect(secondOp.response.status).toEqual('409');

    const thirdOp = bulkResponse.Operations[2];
    expect(thirdOp.bulkId).toBeDefined();
    expect(thirdOp.bulkId).toEqual(operation3.bulkId);
    expect(thirdOp.status).toEqual('201');
    expect(thirdOp.location).toBeDefined();
    expect(thirdOp.location).toContain(scimEndpoint + '/Users/');

    const forth = bulkResponse.Operations[3];
    expect(forth.bulkId).toBeDefined();
    expect(forth.bulkId).toEqual(operation4.bulkId);
    expect(forth.status).toEqual('201');
    expect(forth.location).toBeDefined();
    expect(forth.location).toContain(scimEndpoint + '/Users/');
  });
  it('should not process more than 1MB', async () => {
    let operations = [];
    for (let i = 0; i < 500; i++) {
      const operation: BulkOperation = {
        method: 'POST',
        path: '/Users',
        bulkId: random.word(),
        data: {
          schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
          userName: createRandomString(1000),
          lastName: createRandomString(1000),
          firstName: createRandomString(1000),
        },
      };
      operations.push(operation);
    }

    const request: BulkRequest = {
      schemas: ['urn:ietf:params:scim:api:messages:2.0:BulkRequest'],
      failOnErrors: 1,
      Operations: operations,
    };
    const res = await performPost(scimEndpoint, '/Bulk', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(413);
    const scimError: Error = res.body;
    assertExpectedError(scimError, '413', null, 'The size of the bulk operation exceeds the maxPayloadSize (1048576).');
  });

  it('should accept request with patch user', async () => {
    const userLocation = await createRandomUser();
    const user = await readScimUserProfile(userLocation);

    const patchOp: BulkOperation = {
      method: 'PATCH',
      path: '/Users/' + user.id,
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
        Operations: [
          {
            op: 'add',
            path: 'emails',
            value: [
              {
                value: user['username'] + '@noware.com',
                type: 'home',
                primary: true,
              },
            ],
          },
          {
            op: 'add',
            path: 'profileUrl',
            value: 'https://my.picture.xyz/me',
          },
        ],
      },
    };

    const patchRequest: BulkRequest = {
      schemas: ['urn:ietf:params:scim:api:messages:2.0:BulkRequest'],
      Operations: [patchOp],
    };

    const scimResponse = await performPost(scimEndpoint, '/Bulk', JSON.stringify(patchRequest), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(200);

    const bulkResponse: BulkResponse = scimResponse.body;
    expect(bulkResponse.Operations).toHaveLength(1);
    const op = bulkResponse.Operations[0];
    expect(op.bulkId).toBeDefined();
    expect(op.bulkId).toEqual(patchOp.bulkId);
    expect(op.status).toEqual('200');
    expect(op.location).toBeDefined();
    expect(op.location).toEqual(userLocation);

    const updatedUser = await readScimUserProfile(userLocation);
    expect(updatedUser.profileUrl).toBeDefined();
    expect(updatedUser.emails).toBeDefined();
    expect(updatedUser.emails).toHaveLength(1);
    expect(updatedUser.emails[0].value).toEqual(user['username'] + '@noware.com');
  });

  it('should reject patch if user is unknown', async () => {
    const patchOp: BulkOperation = {
      method: 'PATCH',
      path: '/Users/' + random.word(),
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
        Operations: [
          {
            op: 'replace',
            path: 'displayName',
            value: 'replacedDisplayname',
          },
          {
            op: 'add',
            path: 'profileUrl',
            value: 'https://my.picture.xyz/me',
          },
        ],
      },
    };

    const patchRequest: BulkRequest = {
      schemas: ['urn:ietf:params:scim:api:messages:2.0:BulkRequest'],
      Operations: [patchOp],
    };

    const scimResponse = await performPost(scimEndpoint, '/Bulk', JSON.stringify(patchRequest), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(200);

    const bulkResponse: BulkResponse = scimResponse.body;
    expect(bulkResponse.Operations).toHaveLength(1);
    const op = bulkResponse.Operations[0];
    expect(op.bulkId).toBeDefined();
    expect(op.bulkId).toEqual(patchOp.bulkId);
    expect(op.status).toEqual('404');
    expect(op.location).toBeUndefined();
  });

  it('should reject delete if user is unknown', async () => {
    const deleteOp: BulkOperation = {
      method: 'DELETE',
      path: '/Users/' + random.word(),
      bulkId: random.word(),
    };

    const deleteRequest: BulkRequest = {
      schemas: ['urn:ietf:params:scim:api:messages:2.0:BulkRequest'],
      Operations: [deleteOp],
    };

    const scimResponse = await performPost(scimEndpoint, '/Bulk', JSON.stringify(deleteRequest), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(200);

    const bulkResponse: BulkResponse = scimResponse.body;
    expect(bulkResponse.Operations).toHaveLength(1);
    const op = bulkResponse.Operations[0];
    expect(op.bulkId).toBeDefined();
    expect(op.bulkId).toEqual(deleteOp.bulkId);
    expect(op.status).toEqual('404');
    expect(op.location).toBeUndefined();
  });

  it('should accept request with delete user', async () => {
    const userLocation = await createRandomUser();
    const user = await readScimUserProfile(userLocation);

    const deleteOp: BulkOperation = {
      method: 'DELETE',
      path: '/Users/' + user.id,
    };

    const deleteRequest: BulkRequest = {
      schemas: ['urn:ietf:params:scim:api:messages:2.0:BulkRequest'],
      Operations: [deleteOp],
    };

    const scimResponse = await performPost(scimEndpoint, '/Bulk', JSON.stringify(deleteRequest), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(200);

    const bulkResponse: BulkResponse = scimResponse.body;
    expect(bulkResponse.Operations).toHaveLength(1);
    const op = bulkResponse.Operations[0];
    expect(op.bulkId).toBeUndefined();
    expect(op.status).toEqual('204');
    expect(op.location).toBeDefined();
    expect(op.location).toEqual(userLocation);

    await performGet(userLocation, '', {
      Authorization: `Bearer ${scimAccessToken}`,
    }).expect(404);
  });
});

function assertExpectedError(scimError: Error, status: string, scimType: string, errorMessage: string) {
  expect(scimError).toBeDefined();
  expect(scimError.schemas).toBeDefined();
  expect(scimError.detail).toBeDefined();
  expect(scimError.status).toEqual(status);
  if (scimType) {
    expect(scimError.scimType).toEqual(scimType);
  }
  if (errorMessage) {
    expect(scimError.detail).toEqual(errorMessage);
  }
}

async function readScimUserProfile(userLocation: string) {
  const getUser = await performGet(userLocation, '', {
    Authorization: `Bearer ${scimAccessToken}`,
  }).expect(200);
  return getUser.body;
}

function createRandomString(length: number) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  let result = '';
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

async function createRandomUser() {
  const createOp: BulkOperation = {
    method: 'POST',
    path: '/Users',
    bulkId: random.word(),
    data: {
      schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
      userName: random.word(),
    },
  };

  const request: BulkRequest = {
    schemas: ['urn:ietf:params:scim:api:messages:2.0:BulkRequest'],
    Operations: [createOp],
  };

  const scimResponse = await performPost(scimEndpoint, '/Bulk', JSON.stringify(request), {
    'Content-type': 'application/json',
    Authorization: `Bearer ${scimAccessToken}`,
  }).expect(200);

  const bulkResponse: BulkResponse = scimResponse.body;
  expect(bulkResponse.Operations).toHaveLength(1);

  return bulkResponse.Operations[0].location;
}
