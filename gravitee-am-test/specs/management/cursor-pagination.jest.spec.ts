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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, startDomain } from '@management-commands/domain-management-commands';
import { createApplication } from '@management-commands/application-management-commands';
import { createUser, deleteUser } from '@management-commands/user-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../test-fixture';
import request from 'supertest';

setup(120000);

/**
 * Helper to build the base management URL path with org/env.
 */
const managementBase = () =>
  `${process.env.AM_MANAGEMENT_URL}/management/organizations/${process.env.AM_DEF_ORG_ID}/environments/${process.env.AM_DEF_ENV_ID}`;

/**
 * Generic cursor page response shape.
 */
interface CursorPage<T> {
  data: T[];
  nextCursor: string | null;
  hasNext: boolean;
}

/**
 * Fetch a cursor page from a given URL path with optional query params.
 */
async function fetchCursorPage<T>(path: string, accessToken: string, params?: Record<string, string | number>): Promise<CursorPage<T>> {
  const url = new URL(path, process.env.AM_MANAGEMENT_URL);
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null) {
        url.searchParams.set(key, String(value));
      }
    }
  }
  const res = await request(process.env.AM_MANAGEMENT_URL)
    .get(url.pathname + url.search)
    .set('Authorization', `Bearer ${accessToken}`)
    .expect(200);
  return res.body;
}

describe('Cursor-based pagination', () => {
  let accessToken: string;

  beforeAll(async () => {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();
  });

  describe('Domain cursor pagination', () => {
    const domainIds: string[] = [];
    const domainNames: string[] = [];
    const DOMAIN_COUNT = 5;

    beforeAll(async () => {
      for (let i = 0; i < DOMAIN_COUNT; i++) {
        const name = uniqueName(`cursor-dom-${String(i).padStart(2, '0')}`, true);
        const domain = await createDomain(accessToken, name, `Cursor test domain ${i}`);
        domainIds.push(domain.id);
        domainNames.push(domain.name);
      }
    });

    afterAll(async () => {
      for (const id of domainIds) {
        await safeDeleteDomain(id, accessToken);
      }
    });

    it('should return first page without cursor', async () => {
      const page = await fetchCursorPage<any>(
        `${managementBase()}/domains/_cursor`,
        accessToken,
        { limit: 3 },
      );

      expect(page.data).toBeDefined();
      expect(Array.isArray(page.data)).toBe(true);
      expect(page.data.length).toBeLessThanOrEqual(3);
      expect(typeof page.hasNext).toBe('boolean');
    });

    it('should paginate through all domains with cursor', async () => {
      const allDomains: any[] = [];
      let cursor: string | null = null;

      // Paginate with small limit to force multiple pages
      for (let i = 0; i < 20; i++) {
        const params: Record<string, any> = { limit: 2 };
        if (cursor) params.after = cursor;

        const page = await fetchCursorPage<any>(
          `${managementBase()}/domains/_cursor`,
          accessToken,
          params,
        );

        allDomains.push(...page.data);
        cursor = page.nextCursor;

        if (!page.hasNext) break;
        expect(page.nextCursor).toBeTruthy();
      }

      // Should have found at least our test domains
      const ourDomainIds = new Set(domainIds);
      const foundOurs = allDomains.filter((d) => ourDomainIds.has(d.id));
      expect(foundOurs.length).toEqual(DOMAIN_COUNT);
    });

    it('should return hasNext=false on last page', async () => {
      // Fetch with very large limit to get all in one page
      const page = await fetchCursorPage<any>(
        `${managementBase()}/domains/_cursor`,
        accessToken,
        { limit: 100 },
      );

      // If everything fits in one page, hasNext should be false
      if (page.data.length < 100) {
        expect(page.hasNext).toBe(false);
        expect(page.nextCursor).toBeFalsy();
      }
    });

    it('should return domains sorted by name', async () => {
      const page = await fetchCursorPage<any>(
        `${managementBase()}/domains/_cursor`,
        accessToken,
        { limit: 100 },
      );

      const names = page.data.map((d) => d.name.toLowerCase());
      const sorted = [...names].sort();
      expect(names).toEqual(sorted);
    });

    it('should support search with q parameter', async () => {
      // Use the hrid of the first domain for an exact search
      const firstDomain = await request(process.env.AM_MANAGEMENT_URL)
        .get(`${managementBase()}/domains/${domainIds[0]}`.replace(process.env.AM_MANAGEMENT_URL, ''))
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      const hrid = firstDomain.body.hrid;
      if (hrid) {
        const page = await fetchCursorPage<any>(
          `${managementBase()}/domains/_cursor`,
          accessToken,
          { limit: 10, q: hrid },
        );

        expect(page.data.length).toBeGreaterThanOrEqual(1);
        expect(page.data.some((d) => d.id === domainIds[0])).toBe(true);
      }
    });

    it('should not include totalCount in response', async () => {
      const page = await fetchCursorPage<any>(
        `${managementBase()}/domains/_cursor`,
        accessToken,
        { limit: 10 },
      );

      // CursorPage intentionally omits totalCount for performance
      expect((page as any).totalCount).toBeUndefined();
      expect((page as any).currentPage).toBeUndefined();
    });
  });

  describe('Application cursor pagination', () => {
    let domainId: string;
    const appIds: string[] = [];
    const APP_COUNT = 5;

    beforeAll(async () => {
      const domain = await createDomain(accessToken, uniqueName('cursor-app-test', true), 'Cursor app test');
      domainId = domain.id;
      await startDomain(domainId, accessToken);

      for (let i = 0; i < APP_COUNT; i++) {
        const app = await createApplication(domainId, accessToken, {
          name: uniqueName(`cursor-app-${String(i).padStart(2, '0')}`, true),
          type: 'SERVICE',
        });
        appIds.push(app.id);
      }
    });

    afterAll(async () => {
      await safeDeleteDomain(domainId, accessToken);
    });

    it('should return first page without cursor', async () => {
      const page = await fetchCursorPage<any>(
        `${managementBase()}/domains/${domainId}/applications/_cursor`,
        accessToken,
        { limit: 3 },
      );

      expect(page.data).toBeDefined();
      expect(page.data.length).toBeLessThanOrEqual(3);
      expect(typeof page.hasNext).toBe('boolean');
    });

    it('should paginate through all applications', async () => {
      const allApps: any[] = [];
      let cursor: string | null = null;

      for (let i = 0; i < 20; i++) {
        const params: Record<string, any> = { limit: 2 };
        if (cursor) params.after = cursor;

        const page = await fetchCursorPage<any>(
          `${managementBase()}/domains/${domainId}/applications/_cursor`,
          accessToken,
          params,
        );

        allApps.push(...page.data);
        cursor = page.nextCursor;

        if (!page.hasNext) break;
        expect(page.nextCursor).toBeTruthy();
      }

      // Should find all our test apps (+ the default app created with the domain)
      expect(allApps.length).toBeGreaterThanOrEqual(APP_COUNT);
    });

    it('should return applications sorted by updatedAt descending', async () => {
      const page = await fetchCursorPage<any>(
        `${managementBase()}/domains/${domainId}/applications/_cursor`,
        accessToken,
        { limit: 100 },
      );

      // Applications with updatedAt should be in descending order
      const dates = page.data.map((a) => a.updatedAt).filter(Boolean);
      for (let i = 1; i < dates.length; i++) {
        expect(new Date(dates[i - 1]).getTime()).toBeGreaterThanOrEqual(new Date(dates[i]).getTime());
      }
    });

    it('should not include totalCount in response', async () => {
      const page = await fetchCursorPage<any>(
        `${managementBase()}/domains/${domainId}/applications/_cursor`,
        accessToken,
        { limit: 10 },
      );

      expect((page as any).totalCount).toBeUndefined();
      expect((page as any).currentPage).toBeUndefined();
    });
  });

  describe('User cursor pagination', () => {
    let domainId: string;
    const userIds: string[] = [];
    const USER_COUNT = 5;

    beforeAll(async () => {
      const domain = await createDomain(accessToken, uniqueName('cursor-user-test', true), 'Cursor user test');
      domainId = domain.id;
      await startDomain(domainId, accessToken);

      for (let i = 0; i < USER_COUNT; i++) {
        const user = await createUser(domainId, accessToken, {
          username: uniqueName(`cursoruser${String(i).padStart(2, '0')}`, true),
          password: 'SomeP@ssw0rd!',
          firstName: `First${i}`,
          lastName: `Last${i}`,
          email: `cursoruser${i}@test.com`,
        });
        userIds.push(user.id);
      }
    });

    afterAll(async () => {
      for (const userId of userIds) {
        try {
          await deleteUser(domainId, accessToken, userId);
        } catch {
          // ignore cleanup errors
        }
      }
      await safeDeleteDomain(domainId, accessToken);
    });

    it('should return first page without cursor', async () => {
      const page = await fetchCursorPage<any>(
        `${managementBase()}/domains/${domainId}/users/_cursor`,
        accessToken,
        { limit: 3 },
      );

      expect(page.data).toBeDefined();
      expect(page.data.length).toBeLessThanOrEqual(3);
      expect(typeof page.hasNext).toBe('boolean');
    });

    it('should paginate through all users', async () => {
      const allUsers: any[] = [];
      let cursor: string | null = null;

      for (let i = 0; i < 20; i++) {
        const params: Record<string, any> = { limit: 2 };
        if (cursor) params.after = cursor;

        const page = await fetchCursorPage<any>(
          `${managementBase()}/domains/${domainId}/users/_cursor`,
          accessToken,
          params,
        );

        allUsers.push(...page.data);
        cursor = page.nextCursor;

        if (!page.hasNext) break;
        expect(page.nextCursor).toBeTruthy();
      }

      expect(allUsers.length).toBeGreaterThanOrEqual(USER_COUNT);
    });

    it('should return users sorted by username ascending', async () => {
      const page = await fetchCursorPage<any>(
        `${managementBase()}/domains/${domainId}/users/_cursor`,
        accessToken,
        { limit: 100 },
      );

      const usernames = page.data.map((u) => u.username).filter(Boolean);
      const sorted = [...usernames].sort((a, b) => a.localeCompare(b));
      expect(usernames).toEqual(sorted);
    });

    it('should support search with q parameter', async () => {
      // Search for a specific username pattern
      const targetUser = await request(process.env.AM_MANAGEMENT_URL)
        .get(`${managementBase()}/domains/${domainId}/users/${userIds[0]}`.replace(process.env.AM_MANAGEMENT_URL, ''))
        .set('Authorization', `Bearer ${accessToken}`)
        .expect(200);

      const username = targetUser.body.username;
      const page = await fetchCursorPage<any>(
        `${managementBase()}/domains/${domainId}/users/_cursor`,
        accessToken,
        { limit: 10, q: username },
      );

      expect(page.data.length).toBeGreaterThanOrEqual(1);
      expect(page.data.some((u) => u.id === userIds[0])).toBe(true);
    });

    it('should not include totalCount in response', async () => {
      const page = await fetchCursorPage<any>(
        `${managementBase()}/domains/${domainId}/users/_cursor`,
        accessToken,
        { limit: 10 },
      );

      expect((page as any).totalCount).toBeUndefined();
      expect((page as any).currentPage).toBeUndefined();
    });
  });
});
