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
import { waitForDomainSync } from '@management-commands/domain-management-commands';
import { getAllUsers, listUsers } from '@management-commands/user-management-commands';
import { extractXsrfToken, performFormPost } from '@gateway-commands/oauth-oidc-commands';
import { updateApplication } from '@management-commands/application-management-commands';
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
});
