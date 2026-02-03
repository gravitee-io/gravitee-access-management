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
import {
  setupApiManagementLoginSocialFixture,
  ApiManagementLoginSocialFixture,
  getLoginForm,
  getSocialForm,
  runLoginFlowWithCookieJar,
  followRedirectsUntil,
} from './fixtures/api-management-login-social-fixture';
import {
  extractCsrfFromManagementLoginHtml,
  extractSocialUrlFromManagementLoginHtml,
  extractXsrfAndActionFromSocialLoginHtml,
} from './management-auth-helper';
import { setup } from '../../test-fixture';

setup(200000);

const MANAGEMENT_URL = process.env.AM_MANAGEMENT_URL!;
const REDIRECT_URI = 'https://nowhere.com';
const TARGET_URL = 'http://nowhere.com';

let fixture: ApiManagementLoginSocialFixture;

beforeAll(async () => {
  fixture = await setupApiManagementLoginSocialFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('API Management - Login Social Provider', () => {
  describe('Login Page', () => {
    it('should redirect to login page when initiating auth flow', async () => {
      const res = await performGet(
        MANAGEMENT_URL,
        `/management/auth/authorize?redirect_uri=${encodeURIComponent(REDIRECT_URI)}`,
      );
      expect(res.status).toBe(302);
      expect(res.headers.location).toContain('/management/auth/login');
    });

    it('should return login form with CSRF token and social provider link', async () => {
      const { loginFormRes } = await getLoginForm(MANAGEMENT_URL, REDIRECT_URI);
      expect(loginFormRes.status).toBe(200);

      const csrf = extractCsrfFromManagementLoginHtml(loginFormRes.text);
      expect(csrf).toBeDefined();
      expect(csrf.length).toBeGreaterThan(0);

      const socialUrl = extractSocialUrlFromManagementLoginHtml(
        loginFormRes.text,
        fixture.internalGatewayUrl,
        fixture.gatewayUrl,
      );
      expect(socialUrl).toBeDefined();
      expect(socialUrl).toContain('oauth');
    });
  });

  describe('Social Provider Redirect', () => {
    it('should redirect to social domain login when choosing social provider', async () => {
      const { loginFormRes, cookie } = await getLoginForm(MANAGEMENT_URL, REDIRECT_URI);
      const socialUrl = extractSocialUrlFromManagementLoginHtml(
        loginFormRes.text,
        fixture.internalGatewayUrl,
        fixture.gatewayUrl,
      );

      const socialRes = await performGet(
        new URL(socialUrl).origin,
        new URL(socialUrl).pathname + new URL(socialUrl).search,
        { Cookie: cookie },
      );

      expect(socialRes.status).toBe(302);
      expect(socialRes.headers.location).toBeDefined();
    });

    it('should return social domain login form with XSRF and action', async () => {
      const { socialFormRes } = await getSocialForm(MANAGEMENT_URL, REDIRECT_URI, fixture);
      expect(socialFormRes.status).toBe(200);

      const { xsrf, action } = extractXsrfAndActionFromSocialLoginHtml(socialFormRes.text);
      expect(xsrf).toBeDefined();
      expect(action).toBeDefined();
    });
  });

  describe('Complete Login Flow', () => {
    it('should complete full login flow and redirect to callback', async () => {
      // Ensure clean session state
      await performGet(fixture.gatewayUrl, `/${fixture.domainHrid}/logout`);

      // Run the complete login flow
      const { jar, postRes } = await runLoginFlowWithCookieJar(
        MANAGEMENT_URL,
        fixture.gatewayUrl,
        REDIRECT_URI,
        fixture,
      );

      expect(postRes.status).toBe(302);
      expect(postRes.headers.location).toBeDefined();

      // Follow redirects until we reach the final callback
      const finalLocation = await followRedirectsUntil(
        jar,
        postRes.headers.location,
        MANAGEMENT_URL,
        fixture.gatewayUrl,
        5,
      );

      expect(finalLocation).toContain('nowhere.com');
      expect(finalLocation).not.toContain('4200');

      // Cleanup: logout from social domain
      await performGet(fixture.gatewayUrl, `/${fixture.domainHrid}/logout`);
    });

    it('should post credentials and receive redirect response', async () => {
      const { socialFormRes, socialCookie } = await getSocialForm(MANAGEMENT_URL, REDIRECT_URI, fixture);
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
        {
          'Content-Type': 'application/x-www-form-urlencoded',
          Cookie: socialCookie,
        },
      );

      expect(postRes.status).toBe(302);
      expect(postRes.headers.location).toBeDefined();

      // Cleanup: logout from social domain
      await performGet(fixture.gatewayUrl, `/${fixture.domainHrid}/logout`);
    });
  });

  describe('Logout Flow', () => {
    it('should logout and redirect to target URL', async () => {
      // First, complete a login flow
      const { jar, postRes } = await runLoginFlowWithCookieJar(
        MANAGEMENT_URL,
        fixture.gatewayUrl,
        REDIRECT_URI,
        fixture,
      );

      expect(postRes.headers.location).toBeDefined();

      const managementOrigin = new URL(MANAGEMENT_URL).origin;
      const finalLocation = await followRedirectsUntil(
        jar,
        postRes.headers.location,
        MANAGEMENT_URL,
        fixture.gatewayUrl,
        4,
      );

      expect(finalLocation).toContain('nowhere.com');
      expect(jar[managementOrigin]).toBeDefined();

      // Now test the logout
      const logoutRes = await performGet(
        MANAGEMENT_URL,
        `/management/auth/logout?target_url=${encodeURIComponent(TARGET_URL)}`,
        { Cookie: jar[managementOrigin]! },
      );

      expect(logoutRes.status).toBe(302);
      expect(logoutRes.headers.location).toBe(TARGET_URL);

      // Cleanup: logout from social domain
      await performGet(fixture.gatewayUrl, `/${fixture.domainHrid}/logout`);
    });

    it('should logout from social domain', async () => {
      const res = await performGet(fixture.gatewayUrl, `/${fixture.domainHrid}/logout`);
      expect(res.status).toBe(302);
      expect(res.headers.location).toBeDefined();
    });
  });
});
