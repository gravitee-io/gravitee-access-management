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

import { afterAll, beforeAll, describe, expect } from '@jest/globals';
import { BulkRequest } from '../../../api/gateway/models/scim/BulkRequest/BulkRequest';
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { BulkOperation } from '../../../api/gateway/models/scim/BulkRequest/BulkOperation';
import { BulkResponse } from '../../../api/gateway/models/scim/BulkRequest/BulkResponse';
import { Error } from '../../../api/gateway/models/scim/BulkRequest/Error';
import { random } from 'faker';
import { uniqueName } from '@utils-commands/misc';
import { retryUntil } from '@utils-commands/retry';
import { ScimFixture, setupFixture } from './fixture/scim-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: ScimFixture;

beforeAll(async () => {
  fixture = await setupFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
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

    const scimResponse = await performPost(fixture.scimEndpoint, '/Bulk', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${fixture.scimAccessToken}`,
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

    const scimResponse = await performPost(fixture.scimEndpoint, '/Bulk', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${fixture.scimAccessToken}`,
    }).expect(413);
    const scimError: Error = scimResponse.body;
    assertExpectedError(scimError, '413', null, 'The bulk operation exceeds the maximum number of operations (1000).');
  });

  it('should accept request with 3 user creations (two success, one failure)', async () => {
    const user = uniqueName('user01', true);
    const operation1: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: user,
      },
    };
    const operation2: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: user,
      },
    };
    const operation3: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: uniqueName('user', true),
      },
    };

    const request: BulkRequest = {
      schemas: ['urn:ietf:params:scim:api:messages:2.0:BulkRequest'],
      Operations: [operation1, operation2, operation3],
    };

    const scimResponse = await performPost(fixture.scimEndpoint, '/Bulk', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${fixture.scimAccessToken}`,
    }).expect(200);

    const bulkResponse: BulkResponse = scimResponse.body;
    expect(bulkResponse.Operations).toHaveLength(3);
    const firstOp = bulkResponse.Operations[0];
    expect(firstOp.bulkId).toBeDefined();
    expect(firstOp.bulkId).toEqual(operation1.bulkId);
    expect(firstOp.status).toEqual('201');
    expect(firstOp.location).toBeDefined();
    expect(firstOp.location).toContain(fixture.scimEndpoint + '/Users/');

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
    expect(thirdOp.location).toMatch(fixture.scimEndpoint + '/Users/');
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

    const scimResponse = await performPost(fixture.scimEndpoint, '/Bulk', JSON.stringify(updateRequest), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${fixture.scimAccessToken}`,
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

    const scimResponse = await performPost(fixture.scimEndpoint, '/Bulk', JSON.stringify(updateRequest), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${fixture.scimAccessToken}`,
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
    const name = uniqueName('rubble', true);
    const operation1: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: name,
      },
    };
    const operation2: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: name,
      },
    };
    const operation3: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: name,
      },
    };
    const operation4: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: uniqueName('skye', true),
      },
    };

    const request: BulkRequest = {
      schemas: ['urn:ietf:params:scim:api:messages:2.0:BulkRequest'],
      failOnErrors: 2,
      Operations: [operation1, operation2, operation3, operation4],
    };

    const scimResponse = await performPost(fixture.scimEndpoint, '/Bulk', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${fixture.scimAccessToken}`,
    }).expect(200);

    const bulkResponse: BulkResponse = scimResponse.body;
    expect(bulkResponse.Operations).toHaveLength(3);
    const firstOp = bulkResponse.Operations[0];
    expect(firstOp.bulkId).toBeDefined();
    expect(firstOp.bulkId).toEqual(operation1.bulkId);
    expect(firstOp.status).toEqual('201');
    expect(firstOp.location).toBeDefined();
    expect(firstOp.location).toContain(fixture.scimEndpoint + '/Users/');

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
    const name = uniqueName('marshal', true);
    const operation1: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: name,
      },
    };
    const operation2: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: name,
      },
    };
    const operation3: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: uniqueName('sky', true),
      },
    };
    const operation4: BulkOperation = {
      method: 'POST',
      path: '/Users',
      bulkId: random.word(),
      data: {
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: uniqueName('chase', true),
      },
    };

    const request: BulkRequest = {
      schemas: ['urn:ietf:params:scim:api:messages:2.0:BulkRequest'],
      failOnErrors: 2,
      Operations: [operation1, operation2, operation3, operation4],
    };

    const scimResponse = await performPost(fixture.scimEndpoint, '/Bulk', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${fixture.scimAccessToken}`,
    }).expect(200);

    const bulkResponse: BulkResponse = scimResponse.body;
    expect(bulkResponse.Operations).toHaveLength(4);

    const firstOp = bulkResponse.Operations[0];
    expect(firstOp.bulkId).toBeDefined();
    expect(firstOp.bulkId).toEqual(operation1.bulkId);
    expect(firstOp.status).toEqual('201');
    expect(firstOp.location).toBeDefined();
    expect(firstOp.location).toContain(fixture.scimEndpoint + '/Users/');

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
    expect(thirdOp.location).toContain(fixture.scimEndpoint + '/Users/');

    const forth = bulkResponse.Operations[3];
    expect(forth.bulkId).toBeDefined();
    expect(forth.bulkId).toEqual(operation4.bulkId);
    expect(forth.status).toEqual('201');
    expect(forth.location).toBeDefined();
    expect(forth.location).toContain(fixture.scimEndpoint + '/Users/');
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
    const res = await performPost(fixture.scimEndpoint, '/Bulk', JSON.stringify(request), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${fixture.scimAccessToken}`,
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

    const scimResponse = await performPost(fixture.scimEndpoint, '/Bulk', JSON.stringify(patchRequest), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${fixture.scimAccessToken}`,
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

    const scimResponse = await performPost(fixture.scimEndpoint, '/Bulk', JSON.stringify(patchRequest), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${fixture.scimAccessToken}`,
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

    const scimResponse = await performPost(fixture.scimEndpoint, '/Bulk', JSON.stringify(deleteRequest), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${fixture.scimAccessToken}`,
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

    const scimResponse = await performPost(fixture.scimEndpoint, '/Bulk', JSON.stringify(deleteRequest), {
      'Content-type': 'application/json',
      Authorization: `Bearer ${fixture.scimAccessToken}`,
    }).expect(200);

    const bulkResponse: BulkResponse = scimResponse.body;
    expect(bulkResponse.Operations).toHaveLength(1);
    const op = bulkResponse.Operations[0];
    expect(op.bulkId).toBeUndefined();
    expect(op.status).toEqual('204');
    expect(op.location).toBeDefined();
    expect(op.location).toEqual(userLocation);

    // Verify user is deleted - poll until deletion is synced (should return 404)
    await retryUntil(
      async () => {
        const response = await performGet(userLocation, '', {
          Authorization: `Bearer ${fixture.scimAccessToken}`,
        });
        return { status: response.status };
      },
      (resp: any) => resp.status === 404,
      {
        timeoutMillis: 10000,
        intervalMillis: 250,
        onDone: () => console.log('user deletion synced, GET correctly returns 404'),
      },
    );
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
    Authorization: `Bearer ${fixture.scimAccessToken}`,
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

  const scimResponse = await performPost(fixture.scimEndpoint, '/Bulk', JSON.stringify(request), {
    'Content-type': 'application/json',
    Authorization: `Bearer ${fixture.scimAccessToken}`,
  }).expect(200);

  const bulkResponse: BulkResponse = scimResponse.body;
  expect(bulkResponse.Operations).toHaveLength(1);

  const location = bulkResponse.Operations[0].location;
  expect(location).toBeDefined();
  expect(location).toContain(fixture.scimEndpoint + '/Users/');

  return location;
}
