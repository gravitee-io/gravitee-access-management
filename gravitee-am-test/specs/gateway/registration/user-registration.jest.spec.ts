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

import * as faker from 'faker';
import { afterAll, beforeAll, expect } from '@jest/globals';
import { patchDomain, waitForOidcReady } from '@management-commands/domain-management-commands';
import { getAllUsers, listUsers } from '@management-commands/user-management-commands';
import { extractXsrfToken, performFormPost, performGet } from '@gateway-commands/oauth-oidc-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import { decodeJwt } from '@utils-commands/jwt';
import { register, setupFixture, UserRegistrationFixture } from './fixture/user-registration';
import { setup } from '../../test-fixture';

let fixture: UserRegistrationFixture;

setup(200000);

beforeAll(async () => {
  fixture = await setupFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Register User on domain', () => {
  it("Domain shouldn't have users", async () => {
    const usersPage = await getAllUsers(fixture.domain.id, fixture.accessToken);
    expect(usersPage).toBeDefined();
    expect(usersPage.totalCount).toEqual(0);
  });

  describe('User', () => {
    it('Should not be able to register with invalid username', async () => {
      const user = {
        firstName: faker.name.firstName(),
        lastName: faker.name.lastName(),
        username: '$£êê',
        email: faker.internet.email(),
        password: 'P@ssw0rd!123',
      };
      await register(fixture.domain, user, 'warning=invalid_user_information', fixture.application.settings.oauth.clientId);

      const usersPage = await getAllUsers(fixture.domain.id, fixture.accessToken);
      expect(usersPage).toBeDefined();
      expect(usersPage.totalCount).toEqual(0);
    });

    it('Should not be able to register with invalid email address', async () => {
      const user = {
        firstName: faker.name.firstName(),
        lastName: faker.name.lastName(),
        username: faker.name.firstName(),
        email: faker.random.word(),
        password: 'P@ssw0rd!123',
      };
      await register(fixture.domain, user, 'warning=invalid_email', fixture.application.settings.oauth.clientId);

      const usersPage = await getAllUsers(fixture.domain.id, fixture.accessToken);
      expect(usersPage).toBeDefined();
      expect(usersPage.totalCount).toEqual(0);
    });

    it('Should not be able to register with invalid password', async () => {
      const user = {
        firstName: faker.name.firstName(),
        lastName: faker.name.lastName(),
        username: faker.name.firstName(),
        email: faker.internet.email(),
        password: '1234',
      };
      await register(fixture.domain, user, 'warning=invalid_password_value', fixture.application.settings.oauth.clientId);

      const usersPage = await getAllUsers(fixture.domain.id, fixture.accessToken);
      expect(usersPage).toBeDefined();
      expect(usersPage.totalCount).toEqual(0);
    });

    it('Should be able to register with valid information', async () => {
      const user = {
        firstName: faker.name.firstName(),
        lastName: faker.name.lastName(),
        username: faker.name.firstName(),
        email: faker.internet.email(),
        password: 'P@ssw0rd!123',
      };
      await register(fixture.domain, user, 'success=registration_succeed', fixture.application.settings.oauth.clientId);

      const foundUser = await listUsers(fixture.domain.id, fixture.accessToken, user.username);
      expect(foundUser).toBeDefined();
      expect(foundUser.totalCount).toEqual(1);
      expect(foundUser.data[0]).toBeDefined();
      expect(foundUser.data[0].source).toEqual(fixture.defaultIdp.name);
    });
  });

  describe('Application update to use Custom IDP as default', () => {
    it('When a user register, he should be linked to the custom idp', async () => {
      const user = {
        firstName: faker.name.firstName(),
        lastName: faker.name.lastName(),
        username: faker.name.firstName(),
        email: faker.internet.email(),
        password: 'P@ssw0rd!123',
      };
      await register(fixture.domain, user, 'success=registration_succeed', fixture.applicationWithCustomIdp.settings.oauth.clientId);

      const foundUser = await listUsers(fixture.domain.id, fixture.accessToken, user.username);
      expect(foundUser).toBeDefined();
      expect(foundUser.totalCount).toEqual(1);
      expect(foundUser.data[0]).toBeDefined();
      expect(foundUser.data[0].source).toEqual(fixture.customIdp.name);
    });
  });

  describe('Application preserves user session and redirect to specific URL', () => {
    it('When a user register, he should have userId into the session and be redirected to specific URL', async () => {
      const user = {
        firstName: faker.name.firstName(),
        lastName: faker.name.lastName(),
        username: faker.name.firstName(),
        email: faker.internet.email(),
        password: 'P@ssw0rd!123',
      };

      await register(
        fixture.domain,
        user,
        `https://acustom/web/site?client_id=${fixture.applicationWithCustomIdpAndRedirect.settings.oauth.clientId}`,
        fixture.applicationWithCustomIdpAndRedirect.settings.oauth.clientId,
        true,
      );

      const foundUser = await listUsers(fixture.domain.id, fixture.accessToken, user.username);
      expect(foundUser).toBeDefined();
      expect(foundUser.totalCount).toEqual(1);
      expect(foundUser.data[0]).toBeDefined();
      expect(foundUser.data[0].source).toEqual(fixture.customIdp.name);
    });
  });

  describe('Registration disabled', () => {
    it('should reject registration when registerEnabled is false', async () => {
      const clientId = fixture.applicationRegistrationDisabled.settings.oauth.clientId;
      const uri = `/${fixture.domain.hrid}/register?client_id=${clientId}`;
      const response = await performGet(process.env.AM_GATEWAY_URL, uri);
      // Gateway returns 302 redirect with error when registration is disabled
      expect(response.status).toEqual(302);
      expect(response.headers['location']).toContain('error=registration_failed');
    });
  });

  describe('Duplicate username rejection', () => {
    it('should reject registration with a username that already exists', async () => {
      const username = uniqueName('dup-user', true);
      const user = {
        firstName: faker.name.firstName(),
        lastName: faker.name.lastName(),
        username,
        email: faker.internet.email(),
        password: 'P@ssw0rd!123',
      };

      // First registration should succeed
      await register(fixture.domain, user, 'success=registration_succeed', fixture.application.settings.oauth.clientId);

      // Second registration with same username should fail
      const duplicateUser = {
        ...user,
        email: faker.internet.email(), // different email, same username
      };
      await register(fixture.domain, duplicateUser, 'warning=', fixture.application.settings.oauth.clientId);

      // Verify only one user exists with this username
      const foundUsers = await listUsers(fixture.domain.id, fixture.accessToken, username);
      expect(foundUsers.totalCount).toEqual(1);
    });
  });

  // This test mutates domain-level settings so it MUST run last
  describe('Domain-level registration settings (application inherits)', () => {
    it('should use domain-level registration settings when application inherits', async () => {
      // Patch domain-level settings: enable registration + set autoLoginAfterRegistration
      await waitForSyncAfter(fixture.domain.id, () =>
        patchDomain(fixture.domain.id, fixture.accessToken, {
          loginSettings: {
            inherited: false,
            registerEnabled: true,
          },
          accountSettings: {
            inherited: false,
            autoLoginAfterRegistration: true,
            redirectUriAfterRegistration: 'https://domain-level/redirect',
          },
        }),
      );
      // patchDomain causes full route redeploy — wait for OIDC routes to be live
      await waitForOidcReady(fixture.domain.hrid, { timeoutMs: 5000, intervalMs: 200 });

      const user = {
        firstName: faker.name.firstName(),
        lastName: faker.name.lastName(),
        username: uniqueName('domain-lvl', true),
        email: faker.internet.email(),
        password: 'P@ssw0rd!123',
      };

      // The inherited app should pick up domain-level settings:
      // registerEnabled=true (allows registration) and autoLoginAfterRegistration=true
      // with redirectUriAfterRegistration
      const uri = `/${fixture.domain.hrid}/register?client_id=${fixture.applicationInherited.settings.oauth.clientId}`;
      const { headers, token: xsrfToken } = await extractXsrfToken(process.env.AM_GATEWAY_URL, uri);
      const postResponse = await performFormPost(
        process.env.AM_GATEWAY_URL,
        uri,
        {
          'X-XSRF-TOKEN': xsrfToken,
          firstName: user.firstName,
          lastName: user.lastName,
          username: user.username,
          email: user.email,
          password: user.password,
          client_id: fixture.applicationInherited.settings.oauth.clientId,
        },
        {
          Cookie: headers['set-cookie'],
          'Content-type': 'application/x-www-form-urlencoded',
        },
      ).expect(302);

      // Should redirect to domain-level redirectUriAfterRegistration
      expect(postResponse.headers['location']).toContain('https://domain-level/redirect');

      // Verify the user was created
      const foundUser = await listUsers(fixture.domain.id, fixture.accessToken, user.username);
      expect(foundUser.totalCount).toEqual(1);

      // Verify session has userId (autoLoginAfterRegistration=true at domain level)
      const cookies = postResponse.headers['set-cookie'];
      expect(cookies).toBeDefined();
      cookies
        .filter((entry) => entry.startsWith('GRAVITEE_IO_AM_SESSION'))
        .map((gioSessionCookie) => gioSessionCookie.substring(gioSessionCookie.indexOf('=') + 1, gioSessionCookie.indexOf(';')))
        .map((jwtSession) => {
          const jwt = decodeJwt(jwtSession);
          expect(jwt.userId).toBeDefined();
        });
    });
  });
});
