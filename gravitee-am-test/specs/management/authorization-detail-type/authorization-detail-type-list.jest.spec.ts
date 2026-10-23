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
  listAuthorizationDetailTypes,
  ListAuthorizationDetailTypesParams,
} from '@management-commands/authorization-detail-type-management-commands';
import { AuthorizationDetailTypePage } from '@management-models/AuthorizationDetailTypePage';

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

const domainWithTypes = async (...types: string[]): Promise<string> => {
  const domain = await fixture.newDomain();
  await Promise.all(types.map((type) => createAuthorizationDetailType(domain.id, fixture.accessToken, newType(type))));
  return domain.id;
};

const list = (domainId: string, params?: ListAuthorizationDetailTypesParams): Promise<AuthorizationDetailTypePage> =>
  listAuthorizationDetailTypes(domainId, fixture.accessToken, params);

const typesOf = (page: AuthorizationDetailTypePage): string[] => page.data.map((item) => item.type);

describe('List', () => {
  it(jira`should return an empty page for a domain without types ${'AM-7931'}`, async () => {
    const domain = await fixture.newDomain();

    const page = await list(domain.id);

    expect(page).toEqual({ data: [], currentPage: 0, totalCount: 0 });
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
  it(jira`should return one page at a time, starting at page 0 ${'AM-7931'}`, async () => {
    const domainId = await domainWithTypes('type_a', 'type_b', 'type_c');

    const first = await list(domainId, { size: 2 });
    const second = await list(domainId, { page: 1, size: 2 });

    expect(first).toMatchObject({ currentPage: 0, totalCount: 3 });
    expect(typesOf(first)).toEqual(['type_a', 'type_b']);
    expect(second).toMatchObject({ currentPage: 1, totalCount: 3 });
    expect(typesOf(second)).toEqual(['type_c']);
  });

  it(jira`should return no types for a page beyond the last ${'AM-7931'}`, async () => {
    const domainId = await domainWithTypes('type_a', 'type_b', 'type_c');

    const page = await list(domainId, { page: 5, size: 2 });

    expect(page).toEqual({ data: [], currentPage: 5, totalCount: 3 });
  });

  it(jira`should return at most 50 types per page ${'AM-7931'}`, async () => {
    const types = Array.from({ length: 51 }, (_, index) => `type_${String(index).padStart(2, '0')}`);
    const domainId = await domainWithTypes(...types);

    const byDefault = await list(domainId);
    const capped = await list(domainId, { size: 100 });

    expect(byDefault.data).toHaveLength(50);
    expect(byDefault.totalCount).toEqual(51);
    expect(typesOf(capped)).toEqual(types.slice(0, 50));
  });

  it.each([
    ['a negative page', { page: -1 }],
    ['a size below 1', { size: 0 }],
  ])(jira`should reject %s ${'AM-7931'}`, async (_, params) => {
    const domain = await fixture.newDomain();

    await expect(list(domain.id, params)).rejects.toMatchObject({ response: { status: 400 } });
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

  it(jira`should page the matching types ${'AM-7931'}`, async () => {
    const domainId = await domainWithTypes('payment_a', 'payment_b', 'payment_c', 'files_access');

    const page = await list(domainId, { q: 'payment*', page: 1, size: 2 });

    expect(typesOf(page)).toEqual(['payment_c']);
    expect(page).toMatchObject({ currentPage: 1, totalCount: 3 });
  });
});
