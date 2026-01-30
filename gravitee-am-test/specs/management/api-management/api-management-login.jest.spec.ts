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
import request from 'supertest';
import { performGet, performFormPost } from '@gateway-commands/oauth-oidc-commands';
import { setupApiManagementLoginFixture, ApiManagementLoginFixture } from './fixtures/api-management-login-fixture';
import { extractCsrfFromManagementLoginHtml, cookieHeaderFromSetCookie } from './management-auth-helper';
import { getDefaultApi, getIdpApi } from '@management-commands/service/utils';
import { setup } from '../../test-fixture';

setup(200000);

const MANAGEMENT_URL = process.env.AM_MANAGEMENT_URL!;
const ORG_ID = process.env.AM_DEF_ORG_ID!;
const REDIRECT_URI = 'http://nowhere.com';
const TARGET_URL = 'http://nowhere.com';

let fixture: ApiManagementLoginFixture;

beforeAll(async () => {
  fixture = await setupApiManagementLoginFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

function parseLocation(location: string, baseOrigin?: string): { origin: string; pathAndSearch: string } {
  if (!location) throw new Error('Empty location');
  if (location.startsWith('http://') || location.startsWith('https://')) {
    const u = new URL(location);
    return { origin: u.origin, pathAndSearch: u.pathname + u.search };
  }
  const base = baseOrigin ?? MANAGEMENT_URL;
  const u = new URL(location, base.endsWith('/') ? base : base + '/');
  return { origin: u.origin, pathAndSearch: u.pathname + u.search };
}

describe('API Management - Login', () => {
  describe('Prepare', () => {
    it('should get admin configuration', async () => {
      const defaultApi = getDefaultApi(fixture.accessToken!);
      const settings = await defaultApi.getOrganizationSettings({ organizationId: ORG_ID });
      expect(settings).toBeDefined();
      expect(settings.identities).toBeDefined();
      expect(Array.from(settings.identities!).length).toBeGreaterThan(0);
    });

    it('should create new identity provider', async () => {
      const idpApi = getIdpApi(fixture.accessToken!);
      const idp = await idpApi.getIdentityProvider({
        organizationId: ORG_ID,
        identity: fixture.newIdp,
      });
      expect(idp).toBeDefined();
      expect(idp.id).toBe(fixture.newIdp);
    });

    it('should update admin configuration', async () => {
      const defaultApi = getDefaultApi(fixture.accessToken!);
      const settings = await defaultApi.getOrganizationSettings({ organizationId: ORG_ID });
      expect(settings.identities).toBeDefined();
      expect(Array.from(settings.identities!).includes(fixture.newIdp)).toBe(true);
    });
  });

  describe('Login flow', () => {
    it('should initiate login flow and redirect to login page', async () => {
      const res = await performGet(
        MANAGEMENT_URL,
        `/management/auth/authorize?redirect_uri=${encodeURIComponent(REDIRECT_URI)}`,
      );
      expect(res.status).toBe(302);
      expect(res.headers.location).toBeDefined();
      expect(res.headers.location).toContain('/management/auth/login');
      if (res.headers['set-cookie']) {
        const setCookie = res.headers['set-cookie'];
        const arr = Array.isArray(setCookie) ? setCookie : [setCookie];
        const redirectCookie = arr.find((c: string) => c.startsWith('Redirect-Graviteeio-AM='));
        expect(redirectCookie).toBeDefined();
      }
    });

    it('should return login form with XSRF token', async () => {
      const initiateRes = await performGet(
        MANAGEMENT_URL,
        `/management/auth/authorize?redirect_uri=${encodeURIComponent(REDIRECT_URI)}`,
      );
      expect(initiateRes.status).toBe(302);
      const location = initiateRes.headers.location ?? '';
      const { origin, pathAndSearch } = parseLocation(location, MANAGEMENT_URL);
      const cookie = cookieHeaderFromSetCookie(initiateRes.headers['set-cookie']);
      const headers = cookie ? { Cookie: cookie } : undefined;
      const loginFormRes = await performGet(origin, pathAndSearch, headers);
      expect(loginFormRes.status).toBe(200);
      const csrf = extractCsrfFromManagementLoginHtml(loginFormRes.text);
      expect(csrf).toBeDefined();
      expect(csrf.length).toBeGreaterThan(0);
    });

    it('should post login form and redirect to authorize', async () => {
      const initiateRes = await performGet(
        MANAGEMENT_URL,
        `/management/auth/authorize?redirect_uri=${encodeURIComponent(REDIRECT_URI)}`,
      );
      const { origin, pathAndSearch } = parseLocation(initiateRes.headers.location ?? '', MANAGEMENT_URL);
      const cookie = cookieHeaderFromSetCookie(initiateRes.headers['set-cookie']);
      const headers = cookie ? { Cookie: cookie } : undefined;
      const loginFormRes = await performGet(origin, pathAndSearch, headers);
      const csrf = extractCsrfFromManagementLoginHtml(loginFormRes.text);
      const loginCookie = cookieHeaderFromSetCookie(loginFormRes.headers['set-cookie']);
      const postHeaders: Record<string, string> = { 'Content-Type': 'application/x-www-form-urlencoded' };
      if (loginCookie) postHeaders.Cookie = loginCookie;

      const postRes = await performFormPost(
        MANAGEMENT_URL,
        '/management/auth/login',
        { _csrf: csrf, username: fixture.username, password: 'test' },
        postHeaders,
      );
      expect(postRes.status).toBe(302);
      expect(postRes.headers.location).toBeDefined();
      const loc = postRes.headers.location ?? '';
      expect(loc).toMatch(/\/management\/auth\/(authorize|login)/);
      if (loc.includes('/management/auth/authorize')) {
        expect(decodeURIComponent(loc)).toContain(REDIRECT_URI);
      }
    });

    it('should redirect to authorize and land on nowhere.com', async () => {
      const initiateRes = await performGet(
        MANAGEMENT_URL,
        `/management/auth/authorize?redirect_uri=${encodeURIComponent(REDIRECT_URI)}`,
      );
      const { origin, pathAndSearch } = parseLocation(initiateRes.headers.location ?? '', MANAGEMENT_URL);
      const cookie = cookieHeaderFromSetCookie(initiateRes.headers['set-cookie']);
      const headers = cookie ? { Cookie: cookie } : undefined;
      const loginFormRes = await performGet(origin, pathAndSearch, headers);
      const csrf = extractCsrfFromManagementLoginHtml(loginFormRes.text);
      const loginCookie = cookieHeaderFromSetCookie(loginFormRes.headers['set-cookie']);
      const postHeaders: Record<string, string> = { 'Content-Type': 'application/x-www-form-urlencoded' };
      if (loginCookie) postHeaders.Cookie = loginCookie;
      const postRes = await performFormPost(
        MANAGEMENT_URL,
        '/management/auth/login',
        { _csrf: csrf, username: fixture.username, password: 'test' },
        postHeaders,
      );
      expect(postRes.status).toBe(302);
      const redirectToAuthorize = postRes.headers.location ?? '';
      const { origin: o2, pathAndSearch: p2 } = parseLocation(redirectToAuthorize, MANAGEMENT_URL);
      const postCookie = cookieHeaderFromSetCookie(postRes.headers['set-cookie']);
      const followHeaders = postCookie ? { Cookie: postCookie } : undefined;
      const followRes = await performGet(o2, p2, followHeaders);
      expect([200, 302]).toContain(followRes.status);
      if (followRes.status === 302) {
        expect(followRes.headers.location).toContain('nowhere.com');
      }
    });

    it('should logout and redirect to target url', async () => {
      const initiateRes = await performGet(
        MANAGEMENT_URL,
        `/management/auth/authorize?redirect_uri=${encodeURIComponent(REDIRECT_URI)}`,
      );
      const { origin, pathAndSearch } = parseLocation(initiateRes.headers.location ?? '', MANAGEMENT_URL);
      const cookie = cookieHeaderFromSetCookie(initiateRes.headers['set-cookie']);
      const loginFormRes = await performGet(origin, pathAndSearch, cookie ? { Cookie: cookie } : undefined);
      const csrf = extractCsrfFromManagementLoginHtml(loginFormRes.text);
      const loginCookie = cookieHeaderFromSetCookie(loginFormRes.headers['set-cookie']);
      const postHeaders: Record<string, string> = { 'Content-Type': 'application/x-www-form-urlencoded' };
      if (loginCookie) postHeaders.Cookie = loginCookie;
      const postRes = await performFormPost(
        MANAGEMENT_URL,
        '/management/auth/login',
        { _csrf: csrf, username: fixture.username, password: 'test' },
        postHeaders,
      );
      const postCookie = cookieHeaderFromSetCookie(postRes.headers['set-cookie']);
      const followHeaders = postCookie ? { Cookie: postCookie } : undefined;
      const { origin: o2, pathAndSearch: p2 } = parseLocation(postRes.headers.location ?? '', MANAGEMENT_URL);
      await performGet(o2, p2, followHeaders);
      const res = await performGet(
        MANAGEMENT_URL,
        `/management/auth/logout?target_url=${encodeURIComponent(TARGET_URL)}`,
        followHeaders,
      );
      expect(res.status).toBe(302);
      expect(res.headers.location).toBeDefined();
      expect(res.headers.location).toBe(TARGET_URL);
    });
  });

  describe('Token', () => {
    it('should generate user token', async () => {
      const btoa = require('btoa');
      const res = await request(MANAGEMENT_URL)
        .post('/management/auth/token')
        .set('Authorization', 'Basic ' + btoa('admin:adminadmin'))
        .send({
          grant_type: 'password',
          username: fixture.username,
          password: 'test',
        });
      expect(res.status).toBe(200);
      expect(res.body.access_token).toBeDefined();
    });
  });

  describe('Cleanup', () => {
    it('should reset admin configuration', async () => {
      const defaultApi = getDefaultApi(fixture.accessToken!);
      await defaultApi.patchOrganizationSettings({
        organizationId: ORG_ID,
        patchOrganization: { identities: [fixture.currentIdp] },
      });
      const settings = await defaultApi.getOrganizationSettings({ organizationId: ORG_ID });
      expect(Array.from(settings.identities!).includes(fixture.newIdp)).toBe(false);
    });

    it('should delete new identity provider', async () => {
      const idpApi = getIdpApi(fixture.accessToken!);
      const res = await idpApi.deleteIdentityProvider1Raw({
        organizationId: ORG_ID,
        identity: fixture.newIdp,
      });
      expect(res.raw.status).toBe(204);
    });
  });
});
