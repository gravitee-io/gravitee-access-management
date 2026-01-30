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
import { performGet, performFormPost } from '@gateway-commands/oauth-oidc-commands';
import { setupApiManagementLoginSocialFixture, ApiManagementLoginSocialFixture } from './fixtures/api-management-login-social-fixture';
import {
  extractCsrfFromManagementLoginHtml,
  extractSocialUrlFromManagementLoginHtml,
  extractXsrfAndActionFromSocialLoginHtml,
  cookieHeaderFromSetCookie,
} from './management-auth-helper';
import { getDefaultApi, getIdpApi } from '@management-commands/service/utils';
import { setup } from '../../test-fixture';

setup(200000);

const MANAGEMENT_URL = process.env.AM_MANAGEMENT_URL!;
const ORG_ID = process.env.AM_DEF_ORG_ID!;
const REDIRECT_URI = 'https://nowhere.com';
const TARGET_URL = 'http://nowhere.com';

let fixture: ApiManagementLoginSocialFixture;

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

beforeAll(async () => {
  fixture = await setupApiManagementLoginSocialFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('API Management - Login Social Provider', () => {
  describe('Prepare', () => {
    it('should have created social domain', async () => {
      expect(fixture.domainId).toBeDefined();
      expect(fixture.domainHrid).toBeDefined();
    });
    it('should accept unsecured redirect uris', async () => {
      expect(fixture.domain).toBeDefined();
    });
    it('should have created social in-memory IDP', async () => {
      expect(fixture.username).toBeDefined();
    });
    it('should have created social application', async () => {
      expect(fixture.appId).toBeDefined();
      expect(fixture.clientId).toBe('my-client');
      expect(fixture.clientSecret).toBeDefined();
    });
    it('should have configured and started social domain', async () => {
      expect(fixture.domainHrid).toBeDefined();
    });
    it('should get admin configuration', async () => {
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
      expect(res.headers.location).toContain('/management/auth/login');
    });

    it('should return login form with XSRF and social URL', async () => {
      const initiateRes = await performGet(
        MANAGEMENT_URL,
        `/management/auth/authorize?redirect_uri=${encodeURIComponent(REDIRECT_URI)}`,
      );
      const { origin, pathAndSearch } = parseLocation(initiateRes.headers.location ?? '', MANAGEMENT_URL);
      const cookie = cookieHeaderFromSetCookie(initiateRes.headers['set-cookie']);
      const headers = cookie ? { Cookie: cookie } : undefined;
      const loginFormRes = await performGet(origin, pathAndSearch, headers);
      expect(loginFormRes.status).toBe(200);
      extractCsrfFromManagementLoginHtml(loginFormRes.text);
      extractSocialUrlFromManagementLoginHtml(
        loginFormRes.text,
        fixture.internalGatewayUrl,
        fixture.gatewayUrl,
      );
    });

    it('should choose social provider and redirect to social domain login', async () => {
      const initiateRes = await performGet(
        MANAGEMENT_URL,
        `/management/auth/authorize?redirect_uri=${encodeURIComponent(REDIRECT_URI)}`,
      );
      const { origin, pathAndSearch } = parseLocation(initiateRes.headers.location ?? '', MANAGEMENT_URL);
      const cookie = cookieHeaderFromSetCookie(initiateRes.headers['set-cookie']);
      const headers = cookie ? { Cookie: cookie } : undefined;
      const loginFormRes = await performGet(origin, pathAndSearch, headers);
      const socialUrl = extractSocialUrlFromManagementLoginHtml(
        loginFormRes.text,
        fixture.internalGatewayUrl,
        fixture.gatewayUrl,
      );
      const socialRes = await performGet(
        new URL(socialUrl).origin,
        new URL(socialUrl).pathname + new URL(socialUrl).search,
        headers as Record<string, string> | undefined,
      );
      expect(socialRes.status).toBe(302);
      expect(socialRes.headers.location).toBeDefined();
    });

    it('should return social domain login form with XSRF and action', async () => {
      const initiateRes = await performGet(
        MANAGEMENT_URL,
        `/management/auth/authorize?redirect_uri=${encodeURIComponent(REDIRECT_URI)}`,
      );
      const { origin, pathAndSearch } = parseLocation(initiateRes.headers.location ?? '', MANAGEMENT_URL);
      const cookie = cookieHeaderFromSetCookie(initiateRes.headers['set-cookie']);
      const headers = cookie ? { Cookie: cookie } : undefined;
      const loginFormRes = await performGet(origin, pathAndSearch, headers);
      const socialUrl = extractSocialUrlFromManagementLoginHtml(
        loginFormRes.text,
        fixture.internalGatewayUrl,
        fixture.gatewayUrl,
      );
      const socialRes = await performGet(
        new URL(socialUrl).origin,
        new URL(socialUrl).pathname + new URL(socialUrl).search,
        headers as Record<string, string> | undefined,
      );
      const { origin: o2, pathAndSearch: p2 } = parseLocation(socialRes.headers.location ?? '', fixture.gatewayUrl);
      const socialCookie = cookieHeaderFromSetCookie(socialRes.headers['set-cookie']);
      const socialFormRes = await performGet(o2, p2, socialCookie ? { Cookie: socialCookie } : undefined);
      expect(socialFormRes.status).toBe(200);
      const { xsrf, action } = extractXsrfAndActionFromSocialLoginHtml(socialFormRes.text);
      expect(xsrf).toBeDefined();
      expect(action).toBeDefined();
    });

    it('should post login form and redirect to complete profile / authorize', async () => {
      const initiateRes = await performGet(
        MANAGEMENT_URL,
        `/management/auth/authorize?redirect_uri=${encodeURIComponent(REDIRECT_URI)}`,
      );
      const { origin, pathAndSearch } = parseLocation(initiateRes.headers.location ?? '', MANAGEMENT_URL);
      const cookie = cookieHeaderFromSetCookie(initiateRes.headers['set-cookie']);
      const headers = cookie ? { Cookie: cookie } : undefined;
      const loginFormRes = await performGet(origin, pathAndSearch, headers);
      const socialUrl = extractSocialUrlFromManagementLoginHtml(
        loginFormRes.text,
        fixture.internalGatewayUrl,
        fixture.gatewayUrl,
      );
      const socialRes = await performGet(
        new URL(socialUrl).origin,
        new URL(socialUrl).pathname + new URL(socialUrl).search,
        headers as Record<string, string> | undefined,
      );
      const { origin: o2, pathAndSearch: p2 } = parseLocation(socialRes.headers.location ?? '', fixture.gatewayUrl);
      const socialCookie = cookieHeaderFromSetCookie(socialRes.headers['set-cookie']);
      const socialFormRes = await performGet(o2, p2, socialCookie ? { Cookie: socialCookie } : undefined);
      const { xsrf, action } = extractXsrfAndActionFromSocialLoginHtml(socialFormRes.text);
      const actionUrl = new URL(action);
      const formCookie = cookieHeaderFromSetCookie(socialFormRes.headers['set-cookie']);
      const postHeaders: Record<string, string> = { 'Content-Type': 'application/x-www-form-urlencoded' };
      if (formCookie) postHeaders.Cookie = formCookie;
      const postRes = await performFormPost(
        actionUrl.origin,
        actionUrl.pathname + actionUrl.search,
        {
          'X-XSRF-TOKEN': xsrf,
          username: fixture.username,
          password: 'test',
          client_id: fixture.clientId,
        },
        postHeaders,
      );
      expect(postRes.status).toBe(302);
      expect(postRes.headers.location).toBeDefined();
    });

    it('should redirect to login callback then to authorize', async () => {
      const initiateRes = await performGet(
        MANAGEMENT_URL,
        `/management/auth/authorize?redirect_uri=${encodeURIComponent(REDIRECT_URI)}`,
      );
      const { origin, pathAndSearch } = parseLocation(initiateRes.headers.location ?? '', MANAGEMENT_URL);
      let cookie = cookieHeaderFromSetCookie(initiateRes.headers['set-cookie']);
      let headers: Record<string, string> | undefined = cookie ? { Cookie: cookie } : undefined;
      const loginFormRes = await performGet(origin, pathAndSearch, headers);
      const socialUrl = extractSocialUrlFromManagementLoginHtml(
        loginFormRes.text,
        fixture.internalGatewayUrl,
        fixture.gatewayUrl,
      );
      const socialRes = await performGet(
        new URL(socialUrl).origin,
        new URL(socialUrl).pathname + new URL(socialUrl).search,
        headers,
      );
      const { origin: o2, pathAndSearch: p2 } = parseLocation(socialRes.headers.location ?? '', fixture.gatewayUrl);
      cookie = cookieHeaderFromSetCookie(socialRes.headers['set-cookie']);
      headers = cookie ? { Cookie: cookie } : undefined;
      const socialFormRes = await performGet(o2, p2, headers);
      cookie = cookieHeaderFromSetCookie(socialFormRes.headers['set-cookie']);
      const postHeaders: Record<string, string> = { 'Content-Type': 'application/x-www-form-urlencoded' };
      if (cookie) postHeaders.Cookie = cookie;
      const { xsrf, action } = extractXsrfAndActionFromSocialLoginHtml(socialFormRes.text);
      const actionUrl = new URL(action);
      const postRes = await performFormPost(
        actionUrl.origin,
        actionUrl.pathname + actionUrl.search,
        {
          'X-XSRF-TOKEN': xsrf,
          username: fixture.username,
          password: 'test',
          client_id: fixture.clientId,
        },
        postHeaders,
      );
      let loc = postRes.headers.location;
      let cookieStr = cookieHeaderFromSetCookie(postRes.headers['set-cookie']);
      for (let i = 0; i < 5 && loc; i++) {
        const { origin: o, pathAndSearch: p } = parseLocation(loc, loc.includes(MANAGEMENT_URL) ? MANAGEMENT_URL : fixture.gatewayUrl);
        const next = await performGet(o, p, cookieStr ? { Cookie: cookieStr } : undefined);
        expect(next.status).toBe(302);
        loc = next.headers.location;
        cookieStr = cookieHeaderFromSetCookie(next.headers['set-cookie']);
        if (loc && loc.includes('nowhere.com')) break;
      }
      expect(loc).toContain('nowhere.com');
    });

    it('should logout and redirect to target url', async () => {
      const initiateRes = await performGet(
        MANAGEMENT_URL,
        `/management/auth/authorize?redirect_uri=${encodeURIComponent(REDIRECT_URI)}`,
      );
      const { origin, pathAndSearch } = parseLocation(initiateRes.headers.location ?? '', MANAGEMENT_URL);
      const cookie = cookieHeaderFromSetCookie(initiateRes.headers['set-cookie']);
      const loginFormRes = await performGet(origin, pathAndSearch, cookie ? { Cookie: cookie } : undefined);
      const socialUrl = extractSocialUrlFromManagementLoginHtml(
        loginFormRes.text,
        fixture.internalGatewayUrl,
        fixture.gatewayUrl,
      );
      const socialRes = await performGet(
        new URL(socialUrl).origin,
        new URL(socialUrl).pathname + new URL(socialUrl).search,
        cookie ? { Cookie: cookie } : undefined,
      );
      const { origin: o2, pathAndSearch: p2 } = parseLocation(socialRes.headers.location ?? '', fixture.gatewayUrl);
      let cookieStr = cookieHeaderFromSetCookie(socialRes.headers['set-cookie']);
      const socialFormRes = await performGet(o2, p2, cookieStr ? { Cookie: cookieStr } : undefined);
      const { xsrf, action } = extractXsrfAndActionFromSocialLoginHtml(socialFormRes.text);
      cookieStr = cookieHeaderFromSetCookie(socialFormRes.headers['set-cookie']);
      const postHeaders: Record<string, string> = { 'Content-Type': 'application/x-www-form-urlencoded' };
      if (cookieStr) postHeaders.Cookie = cookieStr;
      const postRes = await performFormPost(
        new URL(action).origin,
        new URL(action).pathname + new URL(action).search,
        { 'X-XSRF-TOKEN': xsrf, username: fixture.username, password: 'test', client_id: fixture.clientId },
        postHeaders,
      );
      cookieStr = cookieHeaderFromSetCookie(postRes.headers['set-cookie']);
      let loc = postRes.headers.location;
      for (let i = 0; i < 4 && loc; i++) {
        const { origin: o, pathAndSearch: p } = parseLocation(loc, loc.includes(MANAGEMENT_URL) ? MANAGEMENT_URL : fixture.gatewayUrl);
        const next = await performGet(o, p, cookieStr ? { Cookie: cookieStr } : undefined);
        loc = next.headers.location;
        cookieStr = cookieHeaderFromSetCookie(next.headers['set-cookie']);
        if (loc?.includes('nowhere.com')) break;
      }
      const res = await performGet(
        MANAGEMENT_URL,
        `/management/auth/logout?target_url=${encodeURIComponent(TARGET_URL)}`,
        cookieStr ? { Cookie: cookieStr } : undefined,
      );
      expect(res.status).toBe(302);
      expect(res.headers.location).toBe(TARGET_URL);
    });

    it('should logout social domain', async () => {
      const res = await performGet(fixture.gatewayUrl, `/${fixture.domainHrid}/logout`);
      expect(res.status).toBe(302);
      expect(res.headers.location).toBeDefined();
    });
  });

  describe('Cleanup', () => {
    it('should delete social domain', async () => {
      const { getDomainApi } = await import('@management-commands/service/utils');
      const api = getDomainApi(fixture.accessToken!);
      const res = await api.deleteDomainRaw({
        organizationId: ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID!,
        domain: fixture.domainId,
      });
      expect(res.raw.status).toBe(204);
    });
    it('should reset admin configuration', async () => {
      const defaultApi = getDefaultApi(fixture.accessToken!);
      await defaultApi.patchOrganizationSettings({
        organizationId: ORG_ID,
        patchOrganization: { identities: [fixture.currentIdp] },
      });
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
