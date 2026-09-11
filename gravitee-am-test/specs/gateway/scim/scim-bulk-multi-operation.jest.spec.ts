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
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
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
  fixture = await setupFixture('scim-bulk-multi');
});

afterAll(async () => {
  if (fixture) await fixture.cleanup();
});

const bulk = (operations: any[]) =>
  performPost(fixture.scimEndpoint, '/Bulk', JSON.stringify({ schemas: [SCHEMA_BULK_REQUEST], Operations: operations }), {
    'Content-type': 'application/json',
    Authorization: `Bearer ${fixture.scimAccessToken}`,
  });

const idOf = (location: string) => location.substring(location.lastIndexOf('/') + 1);

/** Creates `count` users through a single bulk request and returns their ids. */
async function seedUsers(count: number): Promise<string[]> {
  const operations = Array.from({ length: count }, () => ({
    method: 'POST',
    path: '/Users',
    bulkId: uniqueName('bulk', true),
    data: { schemas: [SCHEMA_USER], userName: uniqueName('multi', true) },
  }));
  const response = await bulk(operations).expect(200);
  response.body.Operations.forEach((op) => expect(op.status).toEqual('201'));
  return response.body.Operations.map((op) => idOf(op.location));
}

const putOp = (id: string, displayName: string, withBulkId: boolean) => ({
  method: 'PUT',
  path: `/Users/${id}`,
  ...(withBulkId ? { bulkId: uniqueName('bulk', true) } : {}),
  data: { schemas: [SCHEMA_USER], userName: uniqueName('multi', true), displayName },
});

const patchOp = (id: string, displayName: string, withBulkId: boolean) => ({
  method: 'PATCH',
  path: `/Users/${id}`,
  ...(withBulkId ? { bulkId: uniqueName('bulk', true) } : {}),
  data: { schemas: [SCHEMA_PATCH_OP], Operations: [{ op: 'replace', path: 'displayName', value: displayName }] },
});

async function readUser(id: string) {
  const response = await performGet(`${fixture.scimEndpoint}/Users/${id}`, '', {
    Authorization: `Bearer ${fixture.scimAccessToken}`,
  }).expect(200);
  return response.body;
}

describe('SCIM Bulk - updating several users at once', () => {
  it.each([
    ['PUT', true],
    ['PUT', false],
    ['PATCH', true],
    ['PATCH', false],
  ])('updates three users with %s, bulkIds=%s', async (method, withBulkId) => {
    const ids = await seedUsers(3);
    const build = method === 'PUT' ? putOp : patchOp;
    const names = ids.map(() => `renamed-${random.alphaNumeric(8)}`);

    const response = await bulk(ids.map((id, i) => build(id, names[i], withBulkId as boolean))).expect(200);

    expect(response.body.Operations).toHaveLength(3);
    response.body.Operations.forEach((op) => expect(op.status).toEqual('200'));
    // Each user takes its own new name, so the operations are not applied to the wrong record.
    for (let i = 0; i < ids.length; i++) {
      expect(await readUser(ids[i])).toMatchObject({ displayName: names[i] });
    }
  });

  it('applies the valid updates and reports 404 for the unknown user', async () => {
    const ids = await seedUsers(2);
    const names = ids.map(() => `renamed-${random.alphaNumeric(8)}`);

    const response = await bulk([
      putOp(ids[0], names[0], true),
      putOp(random.uuid(), 'never-applied', true),
      putOp(ids[1], names[1], true),
    ]).expect(200);

    expect(response.body.Operations.map((op) => op.status)).toEqual(['200', '404', '200']);
    expect(await readUser(ids[0])).toMatchObject({ displayName: names[0] });
    expect(await readUser(ids[1])).toMatchObject({ displayName: names[1] });
  });

  it('reports a missing userName on update against only that operation', async () => {
    // The create path rejects a missing userName; this is the update path, which validates
    // separately.
    const ids = await seedUsers(3);
    const names = ids.map(() => `renamed-${random.alphaNumeric(8)}`);
    const invalid = { ...putOp(ids[1], names[1], true), data: { schemas: [SCHEMA_USER] } };

    const response = await bulk([putOp(ids[0], names[0], true), invalid, putOp(ids[2], names[2], true)]).expect(200);

    expect(response.body.Operations.map((op) => op.status)).toEqual(['200', '400', '200']);
    expect(response.body.Operations[1].response.detail).toContain('userName');
    expect(await readUser(ids[0])).toMatchObject({ displayName: names[0] });
    expect(await readUser(ids[2])).toMatchObject({ displayName: names[2] });
  });

  it('is idempotent when the same update is sent twice', async () => {
    const ids = await seedUsers(2);
    const names = ids.map(() => `renamed-${random.alphaNumeric(8)}`);
    const operations = ids.map((id, i) => putOp(id, names[i], true));

    await bulk(operations).expect(200);
    const second = await bulk(operations).expect(200);

    second.body.Operations.forEach((op) => expect(op.status).toEqual('200'));
    for (let i = 0; i < ids.length; i++) {
      expect(await readUser(ids[i])).toMatchObject({ displayName: names[i] });
    }
  });
});

describe('SCIM Bulk - deleting several users at once', () => {
  it.each([[true], [false]])('deletes three users, bulkIds=%s', async (withBulkId) => {
    const ids = await seedUsers(3);

    const response = await bulk(
      ids.map((id) => ({
        method: 'DELETE',
        path: `/Users/${id}`,
        ...(withBulkId ? { bulkId: uniqueName('bulk', true) } : {}),
      })),
    ).expect(200);

    expect(response.body.Operations).toHaveLength(3);
    response.body.Operations.forEach((op) => expect(op.status).toEqual('204'));
    for (const id of ids) {
      const lookup = await performGet(`${fixture.scimEndpoint}/Users/${id}`, '', {
        Authorization: `Bearer ${fixture.scimAccessToken}`,
      });
      expect(lookup.status).toBe(404);
    }
  });

  it('reports 404 for every user when none of them exists', async () => {
    const response = await bulk(
      Array.from({ length: 3 }, () => ({ method: 'DELETE', path: `/Users/${random.uuid()}`, bulkId: uniqueName('bulk', true) })),
    ).expect(200);

    expect(response.body.Operations.map((op) => op.status)).toEqual(['404', '404', '404']);
  });

  it('deletes the users that exist and reports 404 for those that do not', async () => {
    const [existing] = await seedUsers(1);

    const response = await bulk([
      { method: 'DELETE', path: `/Users/${random.uuid()}`, bulkId: uniqueName('bulk', true) },
      { method: 'DELETE', path: `/Users/${existing}`, bulkId: uniqueName('bulk', true) },
      { method: 'DELETE', path: `/Users/${random.uuid()}`, bulkId: uniqueName('bulk', true) },
    ]).expect(200);

    expect(response.body.Operations.map((op) => op.status)).toEqual(['404', '204', '404']);
    const lookup = await performGet(`${fixture.scimEndpoint}/Users/${existing}`, '', {
      Authorization: `Bearer ${fixture.scimAccessToken}`,
    });
    expect(lookup.status).toBe(404);
  });
});
