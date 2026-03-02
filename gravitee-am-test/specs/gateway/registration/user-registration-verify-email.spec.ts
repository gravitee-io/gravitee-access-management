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

import { afterAll, beforeAll, expect } from '@jest/globals';
import { clearEmails, getLastEmail } from '@utils-commands/email-commands';
import { listUsers } from '@management-commands/user-management-commands';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import { decodeJwt } from '@utils-commands/jwt';
import { register, setupFixture, VerifyEmailFixture } from './fixture/user-registration-verify-email';
import { setup } from '../../test-fixture';

let fixture: VerifyEmailFixture;

setup(200000);

beforeAll(async () => {
  fixture = await setupFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('User Registration - Email Verification (sendVerifyRegistrationAccountEmail)', () => {
  describe('Self-register with email verification enabled', () => {
    const username = uniqueName('verify-reg', true);
    const email = `${username}@acme.fr`;
    const user = {
      firstName: 'VerifyFirst',
      lastName: 'VerifyLast',
      username,
      email,
      password: 'P@ssw0rd!123',
    };

    beforeAll(async () => {
      await clearEmails(email);
    });

    it('should register user and redirect with registration_succeed', async () => {
      const { locationHeader } = await register(fixture.domain, user, fixture.appVerifyOnly.settings.oauth.clientId);
      expect(locationHeader).toContain('success=registration_succeed');
    });

    it('should create user in disabled state', async () => {
      const foundUser = await listUsers(fixture.domain.id, fixture.accessToken, user.username);
      expect(foundUser.totalCount).toEqual(1);
      expect(foundUser.data[0].enabled).toEqual(false);
      expect(foundUser.data[0].preRegistration).toEqual(true);
    });

    it('should send a verification email', async () => {
      const lastEmail = await getLastEmail(5000, email);
      expect(lastEmail).toBeDefined();
      const link = lastEmail.extractLink();
      expect(link).toBeDefined();
      expect(link).toContain('/verifyRegistration');
    });

    it('should verify registration via email link and enable user', async () => {
      const lastEmail = await getLastEmail(1000, email);
      const verifyLink = lastEmail.extractLink();
      await clearEmails(email);

      const response = await performGet(verifyLink);
      // On success with no auto-login, the verify endpoint renders the registration_completed page
      expect(response.status).toEqual(200);

      // Check user is now enabled
      const foundUser = await listUsers(fixture.domain.id, fixture.accessToken, user.username);
      expect(foundUser.totalCount).toEqual(1);
      expect(foundUser.data[0].enabled).toEqual(true);
    });
  });

  describe('Email verification with auto-login and redirect URI', () => {
    const username = uniqueName('verify-auto', true);
    const email = `${username}@acme.fr`;
    const user = {
      firstName: 'VerifyAutoFirst',
      lastName: 'VerifyAutoLast',
      username,
      email,
      password: 'P@ssw0rd!123',
    };

    beforeAll(async () => {
      await clearEmails(email);
    });

    it('should register user successfully', async () => {
      const { locationHeader } = await register(fixture.domain, user, fixture.appVerifyAutoLogin.settings.oauth.clientId);
      expect(locationHeader).toContain('success=registration_succeed');
    });

    it('should create user in disabled state', async () => {
      const foundUser = await listUsers(fixture.domain.id, fixture.accessToken, user.username);
      expect(foundUser.totalCount).toEqual(1);
      expect(foundUser.data[0].enabled).toEqual(false);
    });

    it('should verify registration and redirect to custom URI with auto-login', async () => {
      const lastEmail = await getLastEmail(5000, email);
      const verifyLink = lastEmail.extractLink();
      await clearEmails(email);

      const response = await performGet(verifyLink);
      // With autoLoginAfterRegistration + redirectUriAfterRegistration, the handler
      // returns a 302 redirect to the custom URI
      expect(response.status).toEqual(302);
      expect(response.headers['location']).toContain('https://acustom/verify/redirect');

      // Check session cookie has userId (auto-login active)
      const cookies = response.headers['set-cookie'];
      expect(cookies).toBeDefined();
      const sessionCookies = cookies.filter((c: string) => c.startsWith('GRAVITEE_IO_AM_SESSION'));
      expect(sessionCookies.length).toBeGreaterThan(0);
      const sessionCookie = sessionCookies[0];
      const jwtValue = sessionCookie.substring(sessionCookie.indexOf('=') + 1, sessionCookie.indexOf(';'));
      const jwt = decodeJwt(jwtValue);
      expect(jwt.userId).toBeTruthy();

      // Check user is now enabled
      const foundUser = await listUsers(fixture.domain.id, fixture.accessToken, user.username);
      expect(foundUser.totalCount).toEqual(1);
      expect(foundUser.data[0].enabled).toEqual(true);
    });
  });
});
