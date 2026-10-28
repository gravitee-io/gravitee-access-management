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
  ADMIN_APPLICATIONS,
  ApplicationListFixture,
  ApplicationPage,
  ApplicationsQuery,
  setupApplicationListFixture,
} from './fixtures/application-list-fixture';

setup(180000);

let fixture: ApplicationListFixture;

beforeAll(async () => {
  fixture = await setupApplicationListFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const names = (page: ApplicationPage) => page.data.map((application) => application.name);

const walk = async (query: ApplicationsQuery, token?: string): Promise<ApplicationPage[]> => {
  const pages = [await fixture.list(query, token)];
  while (pages[pages.length - 1].nextCursor) {
    pages.push(await fixture.follow(pages[pages.length - 1].nextCursor, token));
  }
  return pages;
};

const walkNames = async (query: ApplicationsQuery, token?: string): Promise<string[]> => (await walk(query, token)).flatMap(names);

const cursorOf = (nextCursor: string) => new URLSearchParams(nextCursor.split('?')[1]).get('cursor');

const allNames = () => [...ADMIN_APPLICATIONS.map(({ name }) => name), ...fixture.ownedNames];

describe('Applications list', () => {
  it('should return every application on the first page without a cursor', async () => {
    const page = await fixture.list();

    expect(page.page).toBe(0);
    expect(page.totalCount).toBe(allNames().length);
    expect(page.nextCursor).toBeUndefined();
    expect([...names(page)].sort()).toEqual([...allNames()].sort());
  });

  it('should link the next page on the same endpoint', async () => {
    const { nextCursor } = await fixture.list({ limit: 2 });

    expect(nextCursor).toContain('/applications/search?');
    expect(nextCursor).not.toContain('/_cursor');
  });
});

describe('Applications list - following the next cursor', () => {
  it.each([
    ['updatedAt', 'DESC'],
    ['updatedAt', 'ASC'],
    ['name', 'DESC'],
    ['name', 'ASC'],
  ])('should return every application once, in order, sorted by %s %s', async (sort, dir) => {
    const singlePage = names(await fixture.list({ sort, dir, limit: 100 }));

    const walked = await walkNames({ sort, dir, limit: 2 });

    expect(walked).toEqual(singlePage);
    expect([...walked].sort()).toEqual([...allNames()].sort());
  });

  it.each([
    ['q', { q: 'app_*' }, ['app_a', 'App_B', 'app_c', 'app_d']],
    ['type', { type: ['WEB'] }, ['app_a', 'app_c', 'beta_e']],
    ['several types', { type: ['WEB', 'SERVICE'] }, null],
  ])('should keep the %s filter on every page', async (_, filter, expected) => {
    const walked = await walkNames({ ...filter, sort: 'name', dir: 'ASC', limit: 1 });

    expect([...walked].sort()).toEqual([...(expected ?? allNames())].sort());
  });

  it('should keep the owner.email filter on every page', async () => {
    const walked = await walkNames({ 'owner.email': fixture.owner.email, limit: 1 });

    expect([...walked].sort()).toEqual([...fixture.ownedNames].sort());
  });

  it('should return only the readable applications to a user limited to some applications', async () => {
    const pages = await walk({ sort: 'name', dir: 'ASC', limit: 1 }, fixture.limited.token);

    expect(pages[0].totalCount).toBe(fixture.readableNames.length);
    expect([...pages.flatMap(names)].sort()).toEqual([...fixture.readableNames].sort());
  });

  it('should return the same next page from the deprecated cursor endpoint', async () => {
    const first = await fixture.list({ sort: 'name', dir: 'ASC', limit: 2 });
    const second = await fixture.follow(first.nextCursor);

    const deprecated = await fixture.follow(first.nextCursor.replace('/applications/search?', '/applications/search/_cursor?'));

    expect(names(deprecated)).toEqual(names(second));
    expect(deprecated.page).toBe(1);
    expect(deprecated.nextCursor).toContain('/applications/search/_cursor?');
  });
});

describe('Applications list - limit', () => {
  it('should return 50 applications by default and at most 100', async () => {
    const domain = await fixture.domainWithApplications(101);
    expect((await fixture.listIn(domain.id)).data).toHaveLength(50);
    const capped = await fixture.listIn(domain.id, { limit: 200 });
    expect(capped.data).toHaveLength(100);
    expect(capped.totalCount).toBe(101);
    expect((await fixture.follow(capped.nextCursor)).data).toHaveLength(1);
  });
});

describe('Applications list - invalid query parameters', () => {
  it('should reject a limit below 1 with 400', async () => {
    expect(await fixture.listStatus({ limit: 0 })).toBe(400);
  });

  it('should reject a malformed cursor with 400', async () => {
    expect(await fixture.listStatus({ cursor: 'not base64!' })).toBe(400);
  });

  it.each([
    ['sort', { sort: 'updatedAt' }],
    ['dir', { dir: 'ASC' }],
  ])('should reject a cursor issued for another %s with 400', async (_, other) => {
    const { nextCursor } = await fixture.list({ sort: 'name', dir: 'DESC', limit: 1 });

    expect(await fixture.listStatus({ sort: 'name', dir: 'DESC', limit: 1, ...other, cursor: cursorOf(nextCursor) })).toBe(400);
  });
});
