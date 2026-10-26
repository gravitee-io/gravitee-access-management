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
import { setup } from '../../test-fixture';
import { jira } from '@specs-utils/jira';
import {
  AuthorizationDetailTypeFixture,
  newType,
  setupAuthorizationDetailTypeFixture,
  validSchema,
} from './fixtures/authorization-detail-type-fixture';
import {
  createAuthorizationDetailType,
  followNextCursor,
  listAuthorizationDetailTypes,
  ListAuthorizationDetailTypesParams,
} from '@management-commands/authorization-detail-type-management-commands';
import { AuthorizationDetailTypeCursorPage } from '@management-models/AuthorizationDetailTypeCursorPage';
import { NewAuthorizationDetailType } from '@management-models/NewAuthorizationDetailType';

setup();

let fixture: AuthorizationDetailTypeFixture;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailTypeFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const domainWithTypes = async (...types: (string | NewAuthorizationDetailType)[]): Promise<string> => {
  const domain = await fixture.newDomain();
  await Promise.all(
    types.map((type) => createAuthorizationDetailType(domain.id, fixture.accessToken, typeof type === 'string' ? newType(type) : type)),
  );
  return domain.id;
};

const list = (domainId: string, params?: ListAuthorizationDetailTypesParams): Promise<AuthorizationDetailTypeCursorPage> =>
  listAuthorizationDetailTypes(domainId, fixture.accessToken, params);

const typesOf = (page: AuthorizationDetailTypeCursorPage): string[] => page.data.map((item) => item.type);

const walk = async (domainId: string, params: ListAuthorizationDetailTypesParams): Promise<AuthorizationDetailTypeCursorPage[]> => {
  const pages = [await list(domainId, params)];
  while (pages[pages.length - 1].nextCursor) {
    pages.push(await followNextCursor(domainId, fixture.accessToken, pages[pages.length - 1].nextCursor));
  }
  return pages;
};

const walkTypes = async (domainId: string, params: ListAuthorizationDetailTypesParams): Promise<string[]> =>
  (await walk(domainId, params)).flatMap(typesOf);

describe('List', () => {
  it(jira`should return an empty page for a domain without types ${'AM-7931'}`, async () => {
    const domain = await fixture.newDomain();

    const page = await list(domain.id);

    expect(page.data).toEqual([]);
    expect(page.totalCount).toEqual(0);
    expect(page.page).toEqual(0);
    expect(page.nextCursor).toBeUndefined();
  });

  it(jira`should list the types without their schema, ordered by type ignoring case ${'AM-7931'}`, async () => {
    const domain = await fixture.newDomain();
    const created = await createAuthorizationDetailType(
      domain.id,
      fixture.accessToken,
      newType('payment_initiation', {
        description: 'Initiates a payment',
        schema: validSchema('payment_initiation'),
        discovery: true,
        expiresIn: 3600,
      }),
    );
    await createAuthorizationDetailType(domain.id, fixture.accessToken, newType('Account_Information'));
    await createAuthorizationDetailType(domain.id, fixture.accessToken, newType('files_access'));

    const page = await list(domain.id);

    expect(typesOf(page)).toEqual(['Account_Information', 'files_access', 'payment_initiation']);
    expect(page.totalCount).toEqual(3);
    expect(page.nextCursor).toBeUndefined();
    const listed = page.data.find((item) => item.id === created.id);
    const { schema, ...withoutSchema } = created;
    expect(schema).toEqual(expect.any(String));
    expect(listed).toEqual(withoutSchema);
  });

  it(jira`should list only the types of the domain ${'AM-7921'}`, async () => {
    const domainId = await domainWithTypes('payment_initiation');
    await domainWithTypes('files_access');

    const page = await list(domainId);

    expect(typesOf(page)).toEqual(['payment_initiation']);
    expect(page.totalCount).toEqual(1);
  });

  it(jira`should refuse a domain that does not exist ${'AM-7921'}`, async () => {
    await expect(list('unknown-domain')).rejects.toMatchObject({ response: { status: 403 } });
  });
});

describe('Paging', () => {
  it(jira`should link the next page until the last one ${'AM-7931'}`, async () => {
    const domainId = await domainWithTypes('type_a', 'type_b', 'type_c');

    const [first, second] = await walk(domainId, { limit: 2 });

    expect(typesOf(first)).toEqual(['type_a', 'type_b']);
    expect(first).toMatchObject({ page: 0, totalCount: 3, nextCursor: expect.any(String) });
    expect(typesOf(second)).toEqual(['type_c']);
    expect(second.page).toEqual(1);
    expect(second.nextCursor).toBeUndefined();
  });

  it(jira`should return every type once when following the cursor ${'AM-7931'}`, async () => {
    const types = ['type_a', 'type_b', 'type_c', 'type_d', 'type_e'];
    const domainId = await domainWithTypes(...types);

    const pages = await walk(domainId, { limit: 2 });

    expect(pages.map((page) => page.page)).toEqual([0, 1, 2]);
    expect(pages.flatMap(typesOf)).toEqual(types);
  });

  it(jira`should skip whole pages when a page number is given without a cursor ${'AM-7931'}`, async () => {
    const domainId = await domainWithTypes('type_a', 'type_b', 'type_c');

    const page = await list(domainId, { page: 1, limit: 2 });

    expect(typesOf(page)).toEqual(['type_c']);
    expect(page.page).toEqual(1);
  });

  it(jira`should return at most 50 types per page ${'AM-7931'}`, async () => {
    const types = Array.from({ length: 51 }, (_, index) => `type_${String(index).padStart(2, '0')}`);
    const domainId = await domainWithTypes(...types);

    const byDefault = await list(domainId);
    const [capped, rest] = await walk(domainId, { limit: 100 });

    expect(byDefault.data).toHaveLength(50);
    expect(byDefault.totalCount).toEqual(51);
    expect(typesOf(capped)).toEqual(types.slice(0, 50));
    expect(typesOf(rest)).toEqual(types.slice(50));
  });

  it(jira`should continue past a type containing the cursor separator ${'AM-7931'}`, async () => {
    const domainId = await domainWithTypes('a##1', 'a##2', 'a##3');

    expect(await walkTypes(domainId, { limit: 1 })).toEqual(['a##1', 'a##2', 'a##3']);
  });

  it(jira`should reject a cursor reused with another sort or direction ${'AM-7931'}`, async () => {
    const domainId = await domainWithTypes('type_a', 'type_b');
    const { nextCursor } = await list(domainId, { limit: 1 });
    const cursor = new URLSearchParams(nextCursor.substring(nextCursor.indexOf('?') + 1)).get('cursor');

    await expect(list(domainId, { limit: 1, cursor, sort: 'name' })).rejects.toMatchObject({ response: { status: 400 } });
    await expect(list(domainId, { limit: 1, cursor, dir: 'DESC' })).rejects.toMatchObject({ response: { status: 400 } });
  });

  it.each([
    ['a negative page', { page: -1 }],
    ['a limit below 1', { limit: 0 }],
    ['an unknown sort field', { sort: 'updatedAt' }],
    ['an unknown direction', { dir: 'sideways' }],
    ['a cursor that is not valid', { cursor: 'not base64!' }],
  ])(jira`should reject %s ${'AM-7931'}`, async (_, params) => {
    const domain = await fixture.newDomain();

    await expect(list(domain.id, params as ListAuthorizationDetailTypesParams)).rejects.toMatchObject({ response: { status: 400 } });
  });
});

describe('Sort', () => {
  it(jira`should sort by type in descending order ${'AM-7931'}`, async () => {
    const domainId = await domainWithTypes('type_a', 'Type_B', 'type_c');

    expect(await walkTypes(domainId, { dir: 'DESC', limit: 2 })).toEqual(['type_c', 'Type_B', 'type_a']);
  });

  it(jira`should sort by name ignoring case in both directions ${'AM-7931'}`, async () => {
    const domainId = await domainWithTypes(
      newType('type_1', { name: 'bravo' }),
      newType('type_2', { name: 'Alpha' }),
      newType('type_3', { name: 'charlie' }),
    );

    expect(await walkTypes(domainId, { sort: 'name', limit: 1 })).toEqual(['type_2', 'type_1', 'type_3']);
    expect(await walkTypes(domainId, { sort: 'name', dir: 'DESC', limit: 1 })).toEqual(['type_3', 'type_1', 'type_2']);
  });
});

describe('Search', () => {
  it(jira`should match the type exactly, ignoring case ${'AM-7931'}`, async () => {
    const domainId = await domainWithTypes('payment_initiation', 'payment_initiation_v2', 'files_access');

    const page = await list(domainId, { q: 'PAYMENT_Initiation' });

    expect(typesOf(page)).toEqual(['payment_initiation']);
    expect(page.totalCount).toEqual(1);
  });

  it(jira`should treat * as a wildcard ${'AM-7931'}`, async () => {
    const domainId = await domainWithTypes('payment_initiation', 'Payment_Status', 'files_access', 'my_payment');

    const page = await list(domainId, { q: 'payment*' });

    expect(typesOf(page)).toEqual(['payment_initiation', 'Payment_Status']);
    expect(page.totalCount).toEqual(2);
  });

  it(jira`should take every character other than * literally ${'AM-7931'}`, async () => {
    const domainId = await domainWithTypes('payment_initiation', 'paymentXinitiation', 'payment.status', 'paymentXstatus');

    expect(typesOf(await list(domainId, { q: 'payment_initiation' }))).toEqual(['payment_initiation']);
    expect(typesOf(await list(domainId, { q: 'payment_*' }))).toEqual(['payment_initiation']);
    expect(typesOf(await list(domainId, { q: 'payment.*' }))).toEqual(['payment.status']);
  });

  it(jira`should keep the search when following the cursor ${'AM-7931'}`, async () => {
    const domainId = await domainWithTypes('payment_c', 'payment_a', 'payment_b', 'files_access');

    expect(await walkTypes(domainId, { q: 'payment*', limit: 2 })).toEqual(['payment_a', 'payment_b', 'payment_c']);
  });
});
