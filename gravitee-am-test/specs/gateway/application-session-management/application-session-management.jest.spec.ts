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
import { logoutUser } from '@gateway-commands/oauth-oidc-commands';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { initFixture, SessionManagementGatewayFixture } from './fixtures/session-management-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: SessionManagementGatewayFixture;

const SESSION_COOKIE_NAME = 'GRAVITEE_IO_AM_SESSION';

const getSessionCookie = (response: any): string | undefined => {
  const cookies: string[] = response.request?.header?.Cookie ?? response.headers?.['set-cookie'] ?? [];
  return cookies.find((cookie: string) => cookie.includes(SESSION_COOKIE_NAME));
};

const loginAndGetResponse = async (app: any) => {
  return loginUserNameAndPassword(
    app.settings.oauth.clientId,
    fixture.user,
    fixture.userPassword,
    false,
    fixture.openIdConfiguration,
    fixture.domain,
  );
};

beforeAll(async () => {
  fixture = await initFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Application Session Management - Cookie Behaviour', () => {
  describe('Persistent session cookie', () => {
    it('should set Max-Age on session cookie when persistent session is enabled', async () => {
      const tokenResponse = await loginAndGetResponse(fixture.appPersistentTrue);
      const sessionCookie = getSessionCookie(tokenResponse);

      expect(sessionCookie).toBeDefined();
      expect(sessionCookie).toContain('Max-Age=1800');

      await logoutUser(fixture.openIdConfiguration.end_session_endpoint, tokenResponse);
    });

    it('should not set Max-Age on session cookie when persistent session is disabled', async () => {
      const tokenResponse = await loginAndGetResponse(fixture.appPersistentFalse);
      const sessionCookie = getSessionCookie(tokenResponse);

      expect(sessionCookie).toBeDefined();
      expect(sessionCookie).not.toContain('Max-Age');

      await logoutUser(fixture.openIdConfiguration.end_session_endpoint, tokenResponse);
    });
  });

  describe('Inherited session settings', () => {
    it('should use gateway defaults when inherited is true', async () => {
      const tokenResponse = await loginAndGetResponse(fixture.appInheritedTrue);
      const sessionCookie = getSessionCookie(tokenResponse);

      expect(sessionCookie).toBeDefined();
      expect(sessionCookie).toContain('Max-Age=1800');

      await logoutUser(fixture.openIdConfiguration.end_session_endpoint, tokenResponse);
    });

    it('should use gateway defaults when no cookie settings are configured', async () => {
      const tokenResponse = await loginAndGetResponse(fixture.appNoSettings);
      const sessionCookie = getSessionCookie(tokenResponse);

      expect(sessionCookie).toBeDefined();
      expect(sessionCookie).toContain('Max-Age=1800');

      await logoutUser(fixture.openIdConfiguration.end_session_endpoint, tokenResponse);
    });
  });

  describe('Session cookie lifecycle', () => {
    it('should clear persistent session cookie on logout', async () => {
      const tokenResponse = await loginAndGetResponse(fixture.appForLifecyclePersistent);
      const sessionCookieAfterLogin = getSessionCookie(tokenResponse);
      expect(sessionCookieAfterLogin).toBeDefined();
      expect(sessionCookieAfterLogin).toContain('Max-Age=1800');

      const logoutResponse = await logoutUser(fixture.openIdConfiguration.end_session_endpoint, tokenResponse);
      const logoutCookies: string[] = logoutResponse.headers?.['set-cookie'] ?? [];
      const sessionCookieAfterLogout = logoutCookies.find((cookie: string) => cookie.includes(SESSION_COOKIE_NAME));

      expect(sessionCookieAfterLogout).toBeDefined();
      expect(sessionCookieAfterLogout).toContain('Max-Age=0');
    });

    it('should clear session cookie on logout when persistent is disabled', async () => {
      const tokenResponse = await loginAndGetResponse(fixture.appForLifecycleSession);
      const sessionCookieAfterLogin = getSessionCookie(tokenResponse);
      expect(sessionCookieAfterLogin).toBeDefined();
      expect(sessionCookieAfterLogin).not.toContain('Max-Age');

      const logoutResponse = await logoutUser(fixture.openIdConfiguration.end_session_endpoint, tokenResponse);
      const logoutCookies: string[] = logoutResponse.headers?.['set-cookie'] ?? [];
      const sessionCookieAfterLogout = logoutCookies.find((cookie: string) => cookie.includes(SESSION_COOKIE_NAME));

      expect(sessionCookieAfterLogout).toBeDefined();
      expect(sessionCookieAfterLogout).toContain('Max-Age=0');
    });
  });
});
