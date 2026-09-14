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
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import { ScimFixture, setupFixture } from './fixture/scim-fixture';
import { setup } from '../../test-fixture';

setup(200000);

const SCHEMA_BULK_REQUEST = 'urn:ietf:params:scim:api:messages:2.0:BulkRequest';
const SCHEMA_USER = 'urn:ietf:params:scim:schemas:core:2.0:User';

let fixture: ScimFixture;

beforeAll(async () => {
  fixture = await setupFixture('scim-bulk-validation');
});

afterAll(async () => {
  if (fixture) await fixture.cleanup();
});

const bulk = (body: any, token = fixture.scimAccessToken) =>
  performPost(fixture.scimEndpoint, '/Bulk', typeof body === 'string' ? body : JSON.stringify(body), {
    'Content-type': 'application/json',
    Authorization: `Bearer ${token}`,
  });

const createOp = (overrides: any = {}) => {
  const userName = uniqueName('user', true);
  return {
    method: 'POST',
    path: '/Users',
    bulkId: uniqueName('bulk', true),
    data: { schemas: [SCHEMA_USER], userName },
    ...overrides,
  };
};

const request = (operations: any[]) => ({ schemas: [SCHEMA_BULK_REQUEST], Operations: operations });

/** Two valid operations either side of the one under test, to prove the failure is scoped to it. */
const withNeighbours = (subject: any) => request([createOp(), subject, createOp()]);

describe('SCIM Bulk - request-level validation', () => {
  it('rejects a request with no body', async () => {
    const response = await bulk('');

    expect(response.status).toBe(400);
    expect(response.body.detail).toContain('BulkRequest is required');
  });

  it('rejects a request with no operations', async () => {
    const response = await bulk(request([]));

    expect(response.status).toBe(400);
    expect(response.body.detail).toContain('at least one operation');
  });

  it('rejects a bulkId reused across operations, without creating anything', async () => {
    const sharedBulkId = uniqueName('bulk', true);
    const first = createOp({ bulkId: sharedBulkId });
    const untouched = createOp();
    const second = createOp({ bulkId: sharedBulkId });

    const response = await bulk(request([first, untouched, second]));

    expect(response.status).toBe(400);
    expect(response.body.detail).toContain('bulkId must be unique');

    // The whole request is refused, so even the operation carrying a distinct bulkId is not
    // applied. Re-creating its username has to succeed — a 409 would mean it had been created.
    const retry = await bulk(request([createOp({ data: untouched.data })]));
    expect(retry.status).toBe(200);
    expect(retry.body.Operations[0].status).toEqual('201');
  });

  it.each([
    ['no token', undefined],
    ['a malformed token', 'not-a-real-token'],
  ])('rejects a request with %s', async (_label, token) => {
    const response = await performPost(fixture.scimEndpoint, '/Bulk', JSON.stringify(request([createOp()])), {
      'Content-type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    });

    expect(response.status).toBe(401);
  });
});

describe('SCIM Bulk - per-operation validation', () => {
  it('reports a missing userName against only that operation', async () => {
    const response = await bulk(withNeighbours(createOp({ data: { schemas: [SCHEMA_USER] } })));

    expect(response.status).toBe(200);
    const [first, subject, third] = response.body.Operations;
    expect(first.status).toEqual('201');
    expect(third.status).toEqual('201');
    expect(subject.status).toEqual('400');
    expect(subject.response.detail).toContain('userName');
  });

  it('reports an invalid email format against only that operation', async () => {
    const subject = createOp();
    subject.data = { ...subject.data, emails: [{ value: 'qatest.co@m', type: 'work', primary: true }] };

    const response = await bulk(withNeighbours(subject));

    expect(response.status).toBe(200);
    const operations = response.body.Operations;
    expect(operations[0].status).toEqual('201');
    expect(operations[2].status).toEqual('201');
    expect(operations[1].status).toEqual('400');
    expect(operations[1].response.detail).toContain('email');
  });

  it('reports a missing bulkId against only that operation', async () => {
    const subject = createOp();
    delete subject.bulkId;

    const response = await bulk(withNeighbours(subject));

    expect(response.status).toBe(200);
    const operations = response.body.Operations;
    expect(operations[0].status).toEqual('201');
    expect(operations[2].status).toEqual('201');
    expect(operations[1].status).toEqual('400');
    expect(operations[1].response.detail).toContain('bulkId');
  });

  it('reports missing schemas against only that operation', async () => {
    const response = await bulk(withNeighbours(createOp({ data: { userName: uniqueName('user', true) } })));

    expect(response.status).toBe(200);
    const operations = response.body.Operations;
    expect(operations[0].status).toEqual('201');
    expect(operations[2].status).toEqual('201');
    expect(operations[1].status).toEqual('400');
    expect(operations[1].response.detail).toContain('schemas');
  });

  it('reports a path outside /Users against only that operation', async () => {
    const response = await bulk(withNeighbours(createOp({ path: '/NotUsers' })));

    expect(response.status).toBe(200);
    const operations = response.body.Operations;
    expect(operations[0].status).toEqual('201');
    expect(operations[2].status).toEqual('201');
    expect(operations[1].status).toEqual('400');
    expect(operations[1].response.detail).toContain('/Users');
  });
});
