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
  mergeCookieStrings,
} from './management-auth-helper';
import { setup } from '../../test-fixture';

// Global Jest timeout for slow CI/containers.
setup(200000);

// Management API base URL; redirect/target URLs used to assert end-to-end flow (no real callback server).
const MANAGEMENT_URL = process.env.AM_MANAGEMENT_URL!;
const ORG_ID = process.env.AM_DEF_ORG_ID!;
const REDIRECT_URI = 'https://nowhere.com';
const TARGET_URL = 'http://nowhere.com';

let fixture: ApiManagementLoginSocialFixture;

/**
 * Parses a Location header (absolute URL or path) into origin and path+query for follow-up requests.
 * Relative locations are resolved against the given base (default: MANAGEMENT_URL).
 */
function parseLocation(location: string, baseOrigin?: string): { origin: string; pathAndSearch: string } {
  if (!location) {
    console.error('Empty location');
    throw new Error('Empty location');
  }
  if (location.startsWith('http://') || location.startsWith('https://')) {
    const u = new URL(location);
    return { origin: u.origin, pathAndSearch: u.pathname + u.search };
  }
  const base = baseOrigin ?? MANAGEMENT_URL;
  const u = new URL(location, base.endsWith('/') ? base : base + '/');
  return { origin: u.origin, pathAndSearch: u.pathname + u.search };
}

beforeAll(async () => {
  // Creates org, environment, gateway domain with social IDP, test user, and OAuth client for the flow.
  fixture = await setupApiManagementLoginSocialFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('API Management - Login Social Provider', () => {
  describe('Login flow', () => {
    beforeAll(async () => {
      // Clear any existing session on the social (gateway) domain before the flow tests.
      const res = await performGet(fixture.gatewayUrl, `/${fixture.domainHrid}/logout`);
      expect(res.status).toBe(302);
    });

    // Step 1: /management/auth/authorize should redirect to the login page (no session yet).
    it('should initiate login flow and redirect to login page', async () => {
      const res = await performGet(
        MANAGEMENT_URL,
        `/management/auth/authorize?redirect_uri=${encodeURIComponent(REDIRECT_URI)}`,
      );
      expect(res.status).toBe(302);
      expect(res.headers.location).toContain('/management/auth/login');
    });

    // Step 2: Follow redirect to login page; confirm we get HTML with CSRF token and a link to the social (gateway) login.
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

    // Step 3: From management login form, "click" the social provider link; gateway (social domain) should redirect (e.g. to its login form).
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

    // Step 4: Follow redirect to social domain; we should get the gateway's login form with XSRF and form action for POST.
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

    // Step 5: POST credentials to the social domain login form; server should redirect (e.g. to callback or profile/authorize).
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

    // Step 6: Full flow again; after POST we follow redirects (callback → management authorize → …) until we land on nowhere.com.
    // Use a per-origin cookie jar so the management redirect cookie is sent when following the callback (cross-origin).
    it('should redirect to login callback then to authorize', async () => {
      const managementOrigin = new URL(MANAGEMENT_URL).origin;
      const gatewayOrigin = new URL(fixture.gatewayUrl).origin;
      const jar: Record<string, string> = {};

      const initiateRes = await performGet(
        MANAGEMENT_URL,
        `/management/auth/authorize?redirect_uri=${encodeURIComponent(REDIRECT_URI)}`,
      );
      const initCookie = cookieHeaderFromSetCookie(initiateRes.headers['set-cookie']);
      if (initCookie) jar[managementOrigin] = mergeCookieStrings(jar[managementOrigin], initCookie);

      const { origin, pathAndSearch } = parseLocation(initiateRes.headers.location ?? '', MANAGEMENT_URL);
      const loginFormRes = await performGet(origin, pathAndSearch, jar[origin] ? { Cookie: jar[origin] } : undefined);
      const loginCookie = cookieHeaderFromSetCookie(loginFormRes.headers['set-cookie']);
      if (loginCookie) jar[origin] = mergeCookieStrings(jar[origin], loginCookie);

      const socialUrl = extractSocialUrlFromManagementLoginHtml(
        loginFormRes.text,
        fixture.internalGatewayUrl,
        fixture.gatewayUrl,
      );
      const socialRes = await performGet(
        new URL(socialUrl).origin,
        new URL(socialUrl).pathname + new URL(socialUrl).search,
        jar[new URL(socialUrl).origin] ? { Cookie: jar[new URL(socialUrl).origin] } : undefined,
      );
      const socialResCookie = cookieHeaderFromSetCookie(socialRes.headers['set-cookie']);
      if (socialResCookie) jar[gatewayOrigin] = mergeCookieStrings(jar[gatewayOrigin], socialResCookie);

      const { origin: o2, pathAndSearch: p2 } = parseLocation(socialRes.headers.location ?? '', fixture.gatewayUrl);
      const socialFormRes = await performGet(o2, p2, jar[o2] ? { Cookie: jar[o2] } : undefined);
      const formCookie = cookieHeaderFromSetCookie(socialFormRes.headers['set-cookie']);
      if (formCookie) jar[o2] = mergeCookieStrings(jar[o2], formCookie);

      const { xsrf, action } = extractXsrfAndActionFromSocialLoginHtml(socialFormRes.text);
      const actionUrl = new URL(action);
      const postHeaders: Record<string, string> = { 'Content-Type': 'application/x-www-form-urlencoded' };
      if (jar[actionUrl.origin]) postHeaders.Cookie = jar[actionUrl.origin];
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
      const postResCookie = cookieHeaderFromSetCookie(postRes.headers['set-cookie']);
      if (postResCookie) jar[actionUrl.origin] = mergeCookieStrings(jar[actionUrl.origin], postResCookie);

      let loc = postRes.headers.location;
      // Follow up to 5 redirects (callback, authorize, etc.) until we reach the configured redirect_uri (nowhere.com).
      for (let i = 0; i < 5 && loc; i++) {
        const base =
          loc.includes(MANAGEMENT_URL) || loc.startsWith('/management')
            ? MANAGEMENT_URL
            : fixture.gatewayUrl;
        const { origin: o, pathAndSearch: p } = parseLocation(loc, base);
        const next = await performGet(o, p, jar[o] ? { Cookie: jar[o] } : undefined);
        const nextCookie = cookieHeaderFromSetCookie(next.headers['set-cookie']);
        if (nextCookie) jar[o] = mergeCookieStrings(jar[o], nextCookie);
        expect([200, 302]).toContain(next.status);
        loc = next.headers.location ?? loc;
        if (loc && loc.includes('nowhere.com')) break;
      }
      expect(loc).toBeDefined();
      expect(loc).toContain('nowhere.com');
      // Ensure we got the requested redirect_uri, not the server default (localhost:4200); confirms redirect cookie was sent.
      expect(loc).not.toContain('4200');
    });

    // Step 7: Full login flow, then call management /logout with target_url; should redirect to TARGET_URL (nowhere.com).
    // Use a per-origin cookie jar so the management redirect cookie is sent when following the callback (cross-origin).
    it('should logout and redirect to target url', async () => {
      const managementOrigin = new URL(MANAGEMENT_URL).origin;
      const gatewayOrigin = new URL(fixture.gatewayUrl).origin;
      const jar: Record<string, string> = {};

      const initiateRes = await performGet(
        MANAGEMENT_URL,
        `/management/auth/authorize?redirect_uri=${encodeURIComponent(REDIRECT_URI)}`,
      );
      const initCookie = cookieHeaderFromSetCookie(initiateRes.headers['set-cookie']);
      if (initCookie) jar[managementOrigin] = mergeCookieStrings(jar[managementOrigin], initCookie);

      const { origin, pathAndSearch } = parseLocation(initiateRes.headers.location ?? '', MANAGEMENT_URL);
      const loginFormRes = await performGet(origin, pathAndSearch, jar[origin] ? { Cookie: jar[origin] } : undefined);
      const loginCookie = cookieHeaderFromSetCookie(loginFormRes.headers['set-cookie']);
      if (loginCookie) jar[origin] = mergeCookieStrings(jar[origin], loginCookie);

      const socialUrl = extractSocialUrlFromManagementLoginHtml(
        loginFormRes.text,
        fixture.internalGatewayUrl,
        fixture.gatewayUrl,
      );
      const socialRes = await performGet(
        new URL(socialUrl).origin,
        new URL(socialUrl).pathname + new URL(socialUrl).search,
        jar[new URL(socialUrl).origin] ? { Cookie: jar[new URL(socialUrl).origin] } : undefined,
      );
      const socialResCookie = cookieHeaderFromSetCookie(socialRes.headers['set-cookie']);
      if (socialResCookie) jar[gatewayOrigin] = mergeCookieStrings(jar[gatewayOrigin], socialResCookie);

      const { origin: o2, pathAndSearch: p2 } = parseLocation(socialRes.headers.location ?? '', fixture.gatewayUrl);
      const socialFormRes = await performGet(o2, p2, jar[o2] ? { Cookie: jar[o2] } : undefined);
      const formCookie = cookieHeaderFromSetCookie(socialFormRes.headers['set-cookie']);
      if (formCookie) jar[o2] = mergeCookieStrings(jar[o2], formCookie);

      const { xsrf, action } = extractXsrfAndActionFromSocialLoginHtml(socialFormRes.text);
      const actionUrl = new URL(action);
      const postHeaders: Record<string, string> = { 'Content-Type': 'application/x-www-form-urlencoded' };
      if (jar[actionUrl.origin]) postHeaders.Cookie = jar[actionUrl.origin];
      const postRes = await performFormPost(
        new URL(action).origin,
        new URL(action).pathname + new URL(action).search,
        { 'X-XSRF-TOKEN': xsrf, username: fixture.username, password: 'test', client_id: fixture.clientId },
        postHeaders,
      );
      const postResCookie = cookieHeaderFromSetCookie(postRes.headers['set-cookie']);
      if (postResCookie) jar[actionUrl.origin] = mergeCookieStrings(jar[actionUrl.origin], postResCookie);

      let loc = postRes.headers.location;
      // Follow redirects until we hit redirect_uri; management cookies (including session) are in jar[managementOrigin].
      for (let i = 0; i < 4 && loc; i++) {
        const base =
          loc.includes(MANAGEMENT_URL) || loc.startsWith('/management')
            ? MANAGEMENT_URL
            : fixture.gatewayUrl;
        const { origin: o, pathAndSearch: p } = parseLocation(loc, base);
        const next = await performGet(o, p, jar[o] ? { Cookie: jar[o] } : undefined);
        const nextCookie = cookieHeaderFromSetCookie(next.headers['set-cookie']);
        if (nextCookie) jar[o] = mergeCookieStrings(jar[o], nextCookie);
        loc = next.headers.location ?? loc;
        if (loc?.includes('nowhere.com')) break;
      }

      // We must have reached redirect_uri (nowhere.com) before testing logout; confirms full login flow and cookie jar.
      expect(loc).toBeDefined();
      expect(loc).toContain('nowhere.com');
      expect(loc).not.toContain('4200');

      const res = await performGet(
        MANAGEMENT_URL,
        `/management/auth/logout?target_url=${encodeURIComponent(TARGET_URL)}`,
        jar[managementOrigin] ? { Cookie: jar[managementOrigin] } : undefined,
      );
      expect(res.status).toBe(302);
      expect(res.headers.location).toBe(TARGET_URL);
    });

    // Step 8: Logout on the gateway (social) domain; should get a redirect (e.g. back to login or configured URL).
    it('should logout social domain', async () => {
      const res = await performGet(fixture.gatewayUrl, `/${fixture.domainHrid}/logout`);
      expect(res.status).toBe(302);
      expect(res.headers.location).toBeDefined();
    });
  });
});
