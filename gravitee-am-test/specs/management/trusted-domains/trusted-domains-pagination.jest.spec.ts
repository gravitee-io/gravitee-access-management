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
import {
  setupTrustedDomainsPaginationFixture,
  TrustedDomainPage,
  TrustedDomainsPaginationFixture,
} from './fixtures/trusted-domains-pagination-fixture';

setup();

let fixture: TrustedDomainsPaginationFixture;

beforeAll(async () => {
  fixture = await setupTrustedDomainsPaginationFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const names = (page: TrustedDomainPage) => page.data.map((trustedDomain) => trustedDomain.name);

describe('Trusted domains list - pagination', () => {
  it('should return every trusted domain sorted by name, ignoring case, on the default page', async () => {
    const result = await fixture.listTrustedDomains();

    expect(result.page).toBe(0);
    expect(result.totalCount).toBe(5);
    expect(result.nextCursor).toBeFalsy();
    expect(names(result)).toEqual(['alpha', 'bravo', 'charlie', 'Delta', 'echo']);
  });

  it.each([
    [0, ['alpha', 'bravo']],
    [1, ['charlie', 'Delta']],
    [2, ['echo']],
    [3, []],
  ])('should jump to page %i of size 2', async (pageNumber, expectedNames) => {
    const result = await fixture.listTrustedDomains({ page: pageNumber, limit: 2 });

    expect(result.page).toBe(pageNumber);
    expect(result.totalCount).toBe(5);
    expect(names(result)).toEqual(expectedNames);
  });

  it('should walk every page of size 2 by following the next cursor', async () => {
    const first = await fixture.listTrustedDomains({ limit: 2 });
    expect(names(first)).toEqual(['alpha', 'bravo']);
    expect(first.nextCursor).toContain('/trusted-domains?');

    const second = await fixture.followNextCursor(first.nextCursor);
    expect(second.page).toBe(1);
    expect(second.totalCount).toBe(5);
    expect(names(second)).toEqual(['charlie', 'Delta']);

    const third = await fixture.followNextCursor(second.nextCursor);
    expect(third.page).toBe(2);
    expect(names(third)).toEqual(['echo']);
    expect(third.nextCursor).toBeFalsy();
  });
});

describe('Trusted domains list - deprecated cursor endpoint', () => {
  it('should return the same next page from the deprecated cursor endpoint', async () => {
    const first = await fixture.listTrustedDomains({ limit: 2 });
    const deprecatedPath = first.nextCursor.replace('/trusted-domains?', '/trusted-domains/_cursor?');

    const second = await fixture.followNextCursor(deprecatedPath);

    expect(second.page).toBe(1);
    expect(names(second)).toEqual(['charlie', 'Delta']);
    expect(second.nextCursor).toContain('/trusted-domains/_cursor?');
  });
});

describe('Trusted domains list - limit', () => {
  it('should reduce a limit above 100 instead of rejecting it', async () => {
    const result = await fixture.listTrustedDomains({ limit: 200 });

    expect(names(result)).toEqual(['alpha', 'bravo', 'charlie', 'Delta', 'echo']);
  });
});

describe('Trusted domains list - sort', () => {
  it('should sort by name ascending when asked explicitly', async () => {
    const result = await fixture.listTrustedDomains({ sort: 'name', dir: 'asc' });

    expect(names(result)).toEqual(['alpha', 'bravo', 'charlie', 'Delta', 'echo']);
  });

  it('should sort by name descending', async () => {
    const result = await fixture.listTrustedDomains({ dir: 'desc' });

    expect(names(result)).toEqual(['echo', 'Delta', 'charlie', 'bravo', 'alpha']);
  });

  it('should follow the next cursor through the trusted domains sorted by name descending', async () => {
    const first = await fixture.listTrustedDomains({ dir: 'desc', limit: 2 });
    const second = await fixture.followNextCursor(first.nextCursor);

    expect(second.page).toBe(1);
    expect(second.totalCount).toBe(5);
    expect(names(second)).toEqual(['charlie', 'bravo']);
  });
});

describe('Trusted domains list - usage filter', () => {
  it.each([
    ['SPIFFE', ['bravo', 'charlie', 'Delta']],
    ['TOKEN_EXCHANGE', ['alpha', 'bravo']],
    ['CROSS_APP_ACCESS', ['echo']],
  ])('should keep only the trusted domains trusted for %s', async (usage, expectedNames) => {
    const result = await fixture.listTrustedDomains({ usage });

    expect(result.totalCount).toBe(expectedNames.length);
    expect(names(result)).toEqual(expectedNames);
  });

  it('should keep the usage filter when following the next cursor', async () => {
    const first = await fixture.listTrustedDomains({ usage: 'SPIFFE', limit: 2 });
    const second = await fixture.followNextCursor(first.nextCursor);

    expect(second.page).toBe(1);
    expect(second.totalCount).toBe(3);
    expect(names(second)).toEqual(['Delta']);
    expect(second.nextCursor).toBeFalsy();
  });
});

describe('Trusted domains list - invalid query parameters', () => {
  it.each([[{ page: -1 }], [{ page: 1 }], [{ limit: 0 }], [{ usage: 'UNKNOWN' }], [{ sort: 'createdAt' }], [{ dir: 'sideways' }]])(
    'should reject %j with 400',
    async (query) => {
      expect(await fixture.listTrustedDomainsStatus(query)).toBe(400);
    },
  );

  it('should reject an invalid cursor with 400', async () => {
    expect(await fixture.listTrustedDomainsStatus({ cursor: 'not base64!' })).toBe(400);
  });

  it('should reject a cursor issued for another direction with 400', async () => {
    const { nextCursor } = await fixture.listTrustedDomains({ limit: 2 });
    const cursor = new URLSearchParams(nextCursor.split('?')[1]).get('cursor');

    expect(await fixture.listTrustedDomainsStatus({ cursor, limit: 2, dir: 'desc' })).toBe(400);
  });
});
