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
import { random } from 'faker';
import { ScimFixture, setupFixture } from './fixture/scim-fixture';
import { setup } from '../../test-fixture';

setup(200000);

const SCHEMA_BULK_REQUEST = 'urn:ietf:params:scim:api:messages:2.0:BulkRequest';
const SCHEMA_USER = 'urn:ietf:params:scim:schemas:core:2.0:User';
const SCHEMA_PATCH_OP = 'urn:ietf:params:scim:api:messages:2.0:PatchOp';

let fixture: ScimFixture;

beforeAll(async () => {
  fixture = await setupFixture('scim-bulk-foe');
});

afterAll(async () => {
  if (fixture) await fixture.cleanup();
});

const bulk = (operations: any[], failOnErrors?: number) =>
  performPost(
    fixture.scimEndpoint,
    '/Bulk',
    JSON.stringify({ schemas: [SCHEMA_BULK_REQUEST], ...(failOnErrors ? { failOnErrors } : {}), Operations: operations }),
    { 'Content-type': 'application/json', Authorization: `Bearer ${fixture.scimAccessToken}` },
  );

const idOf = (location: string) => location.substring(location.lastIndexOf('/') + 1);

async function seedUsers(count: number): Promise<string[]> {
  const response = await bulk(
    Array.from({ length: count }, () => ({
      method: 'POST',
      path: '/Users',
      bulkId: uniqueName('bulk', true),
      data: { schemas: [SCHEMA_USER], userName: uniqueName('foe', true) },
    })),
  ).expect(200);
  return response.body.Operations.map((op) => idOf(op.location));
}

/**
 * Builds one operation of the given method against a user id. An unknown id is what makes an
 * operation fail, so the same shape covers the success and failure cases for all three methods.
 */
const operationFor = (method: string, id: string) => {
  const base = { method, path: `/Users/${id}`, bulkId: uniqueName('bulk', true) };
  if (method === 'DELETE') return base;
  if (method === 'PUT') {
    return { ...base, data: { schemas: [SCHEMA_USER], userName: uniqueName('foe', true) } };
  }
  return {
    ...base,
    data: { schemas: [SCHEMA_PATCH_OP], Operations: [{ op: 'replace', path: 'displayName', value: random.alphaNumeric(8) }] },
  };
};

const OK: Record<string, string> = { DELETE: '204', PUT: '200', PATCH: '200' };
const METHODS = ['DELETE', 'PUT', 'PATCH'];

describe.each(METHODS)('SCIM Bulk - failOnErrors with %s', (method) => {
  it('processes every operation when none fails', async () => {
    const ids = await seedUsers(4);

    const response = await bulk(
      ids.map((id) => operationFor(method, id)),
      2,
    ).expect(200);

    expect(response.body.Operations).toHaveLength(4);
    response.body.Operations.forEach((op) => expect(op.status).toEqual(OK[method]));
  });

  it('keeps going after a single failure', async () => {
    const ids = await seedUsers(3);

    // One error, below the threshold of two, so the request runs to the end.
    const response = await bulk(
      [operationFor(method, ids[0]), operationFor(method, random.uuid()), operationFor(method, ids[1]), operationFor(method, ids[2])],
      2,
    ).expect(200);

    expect(response.body.Operations).toHaveLength(4);
    expect(response.body.Operations.map((op) => op.status)).toEqual([OK[method], '404', OK[method], OK[method]]);
  });

  it('stops once the second failure is reached', async () => {
    const ids = await seedUsers(2);

    const response = await bulk(
      [
        operationFor(method, ids[0]),
        operationFor(method, random.uuid()),
        operationFor(method, random.uuid()),
        operationFor(method, ids[1]),
      ],
      2,
    ).expect(200);

    // The trailing operation is never attempted, which is what distinguishes this from the case
    // above — the count of returned operations, not just their statuses.
    expect(response.body.Operations).toHaveLength(3);
    expect(response.body.Operations.map((op) => op.status)).toEqual([OK[method], '404', '404']);
  });
});
