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
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { waitForDomainSync } from '@management-commands/domain-management-commands';
import { clearEmails, getLastEmail } from '@utils-commands/email-commands';
import { UserManagementAppFixture, setupUserManagementAppFixture, CONSTANTS } from './fixtures/user-management-app-fixture';

setup();

let fixture: UserManagementAppFixture;

beforeAll(async () => {
  fixture = await setupUserManagementAppFixture();
});

afterAll(async () => {
  expect(fixture).toBeDefined();
  await fixture.cleanUp();
});

describe('Users - Create', () => {
  let userId: string;

  it('should reject malformed input', async () => {
    // SDK validates required params client-side; Postman sent malformed JSON → 400
    await expect(fixture.createUser(undefined)).rejects.toThrow();
  });

  it('should reject missing username with 400', async () => {
    await expect(
      fixture.createUser({
        firstName: 'Jensen',
        lastName: 'Barbara',
        email: 'jensen.barbara@mail.com',
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringMatching(/username.*must not be blank|username.*ne doit pas/),
    });
  });

  it('should reject missing email with 400', async () => {
    await expect(
      fixture.createUser({
        firstName: 'Jensen',
        lastName: 'Barbara',
        username: 'jensen.barbara',
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringMatching(/email.*must not be blank|email.*ne doit pas/),
    });
  });

  it('should reject missing password when no pre-registration with 400', async () => {
    await expect(
      fixture.createUser({
        firstName: 'Jensen',
        lastName: 'Barbara',
        username: 'jensen.barbara',
        email: 'jensen.barbara@mail.com',
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('Field [password] is required'),
    });
  });

  it('should reject invalid username with 400', async () => {
    await expect(
      fixture.createUser({
        firstName: 'Jensen',
        lastName: 'Barbara',
        username: '£Invalid',
        email: 'jensen.barbara@mail.com',
        password: CONSTANTS.USER_PASSWORD,
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('Username [£Invalid] is not a valid value'),
    });
  });

  it('should reject invalid email with 400', async () => {
    await expect(
      fixture.createUser({
        firstName: 'Jensen',
        lastName: 'Barbara',
        username: 'jensen.barbara',
        email: 'jensen.barba',
        password: CONSTANTS.USER_PASSWORD,
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('Value [jensen.barba] is not a valid email'),
    });
  });

  it('should reject invalid first name with 400', async () => {
    await expect(
      fixture.createUser({
        firstName: '#Invalid',
        lastName: 'Barbara',
        username: 'jensen.barbara',
        email: 'jensen.barbara@mail.com',
        password: CONSTANTS.USER_PASSWORD,
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('First name [#Invalid] is not a valid value'),
    });
  });

  it('should reject invalid last name with 400', async () => {
    await expect(
      fixture.createUser({
        firstName: 'Jensen',
        lastName: '#Invalid',
        username: 'jensen.barbara',
        email: 'jensen.barbara@mail.com',
        password: CONSTANTS.USER_PASSWORD,
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('Last name [#Invalid] is not a valid value'),
    });
  });

  it('should reject password that is too long with 400', async () => {
    await expect(
      fixture.createUser({
        firstName: 'Jensen',
        lastName: 'Barbara',
        username: 'jensen.barbara',
        email: 'jensen.barbara@mail.com',
        password: 'a'.repeat(73),
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringMatching(/The provided password does not meet the password policy requirements/),
    });
  });

  it('should reject email that is too long with 400', async () => {
    const longEmail = 'jensen.barbara@' + 'a'.repeat(310) + 'mail.com';
    await expect(
      fixture.createUser({
        firstName: 'Jensen',
        lastName: 'Barbara',
        username: 'jensen.barbara',
        email: longEmail,
        password: CONSTANTS.USER_PASSWORD,
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('must not be greater than 320'),
    });
  });

  it('should reject externalId that is too long with 400', async () => {
    const longExternalId = 'T' + 'o'.repeat(95) + ' long';
    await expect(
      fixture.createUser({
        firstName: 'Jensen',
        lastName: 'Barbara',
        username: 'jensen.barbara',
        email: 'jensen.barbara@mail.com',
        password: CONSTANTS.USER_PASSWORD,
        externalId: longExternalId,
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining(`External id [${longExternalId}] is not a valid value`),
    });
  });

  it('should create a user', async () => {
    const user = await fixture.createUser({
      firstName: 'Jensen',
      lastName: 'Barbara',
      username: 'jensen.barbara',
      email: 'jensen.barbara@mail.com',
      password: CONSTANTS.USER_PASSWORD,
    });
    expect(user).toBeDefined();
    expect(user.id).toBeDefined();
    expect(user.internal).toBe(true);
    expect(user.enabled).toBe(true);
    expect(user.preRegistration).toBe(false);
    expect(user.registrationCompleted).toBe(true);
    expect(user.source).toEqual(`default-idp-${fixture.domain.id}`);
    userId = user.id;
  });

  it('should reject duplicate username with 400', async () => {
    await expect(
      fixture.createUser({
        firstName: 'Jensen',
        lastName: 'Barbara',
        username: 'jensen.barbara',
        email: 'jensen.barbara@mail.com',
        password: CONSTANTS.USER_PASSWORD,
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('A user [jensen.barbara] already exists'),
    });
  });

  it('should reject duplicate username case-insensitively with 400', async () => {
    await expect(
      fixture.createUser({
        firstName: 'Jensen',
        lastName: 'Barbara',
        username: 'jensen.BarBara',
        email: 'jensen.barbara@mail.com',
        password: CONSTANTS.USER_PASSWORD,
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('A user [jensen.BarBara] already exists'),
    });
  });

  describe('Users - Pre-Register', () => {
    let preRegUserId: string;
    let registrationUserUri: string;
    let registrationAccessToken: string;

    it('should enable dynamic user registration', async () => {
      const result = await fixture.patchDomain({
        accountSettings: { dynamicUserRegistration: true },
      });
      expect(result).toBeDefined();
      await waitForDomainSync(fixture.domain.id);
    });

    it('should create a pre-registered user', async () => {
      const user = await fixture.createUser({
        firstName: 'John',
        lastName: 'Doe',
        username: 'john.doe',
        email: 'john.doe@mail.com',
        preRegistration: true,
      });
      expect(user).toBeDefined();
      expect(user.id).toBeDefined();
      expect(user.registrationUserUri).toBeDefined();
      expect(user.registrationAccessToken).toBeDefined();
      expect(user.internal).toBe(true);
      expect(user.enabled).toBe(false);
      expect(user.preRegistration).toBe(true);
      expect(user.registrationCompleted).toBe(false);
      expect(user.source).toEqual(`default-idp-${fixture.domain.id}`);
      preRegUserId = user.id;
      registrationUserUri = user.registrationUserUri;
      registrationAccessToken = user.registrationAccessToken;
    });

    it('should initiate the post-registration flow', async () => {
      const registrationUrl = new URL(`${registrationUserUri}?token=${registrationAccessToken}`);
      const response = await performGet(registrationUrl.origin, `${registrationUrl.pathname}${registrationUrl.search}`).expect(200);
      expect(response.text).toContain('Thanks for signing up, please complete the form to activate your account');
    });

    it('should delete the pre-registered user', async () => {
      await fixture.deleteUser(preRegUserId);
    });
  });

  describe('Users - Update', () => {
    it('should reject unknown user with 404', async () => {
      await expect(
        fixture.updateUser('wrong-id', {
          firstName: 'Jensen',
          lastName: 'Barbara',
          email: 'jensen.barbara@mail.com',
        }),
      ).rejects.toMatchObject({
        response: { status: 404 },
        message: expect.stringContaining('User [wrong-id] can not be found'),
      });
    });

    it('should reject empty body', async () => {
      // SDK validates required params client-side; Postman sent empty body → 400
      await expect(fixture.updateUser(userId, undefined)).rejects.toThrow();
    });

    it('should reject invalid email on update with 400', async () => {
      await expect(
        fixture.updateUser(userId, {
          firstName: 'Jensen',
          lastName: 'Barbara',
          email: 'jensen.barba',
        }),
      ).rejects.toMatchObject({
        response: { status: 400 },
        message: expect.stringContaining('Value [jensen.barba] is not a valid email'),
      });
    });

    it('should reject invalid first name on update with 400', async () => {
      await expect(
        fixture.updateUser(userId, {
          firstName: '#Invalid',
          lastName: 'Barbara',
          email: 'jensen.barbara@mail.com',
        }),
      ).rejects.toMatchObject({
        response: { status: 400 },
        message: expect.stringContaining('First name [#Invalid] is not a valid value'),
      });
    });

    it('should reject invalid last name on update with 400', async () => {
      await expect(
        fixture.updateUser(userId, {
          firstName: 'Jensen',
          lastName: '#Invalid',
          email: 'jensen.barbara@mail.com',
        }),
      ).rejects.toMatchObject({
        response: { status: 400 },
        message: expect.stringContaining('Last name [#Invalid] is not a valid value'),
      });
    });

    it('should reject email that is too long on update with 400', async () => {
      const longEmail = 'jensen.barbara@' + 'a'.repeat(310) + 'mail.com';
      await expect(
        fixture.updateUser(userId, {
          firstName: 'Jensen',
          lastName: 'Barbara',
          email: longEmail,
        }),
      ).rejects.toMatchObject({
        response: { status: 400 },
        message: expect.stringContaining('must not be greater than 320'),
      });
    });

    it('should reject externalId that is too long on update with 400', async () => {
      const longExternalId = 'T' + 'o'.repeat(95) + ' long';
      await expect(
        fixture.updateUser(userId, {
          firstName: 'Jensen',
          lastName: 'Barbara',
          email: 'jensen.barbara@mail.com',
          externalId: longExternalId,
        }),
      ).rejects.toMatchObject({
        response: { status: 400 },
        message: expect.stringContaining(`External id [${longExternalId}] is not a valid value`),
      });
    });

    it('should update a user with additionalInformation', async () => {
      const updated = await fixture.updateUser(userId, {
        firstName: 'Jensen',
        lastName: 'Barbara',
        email: 'jensen.barbara@mail.com',
        additionalInformation: { profile: 'https://my.profile.com' },
      });
      expect(updated.additionalInformation.profile).toEqual('https://my.profile.com');
    });
  });

  describe('Users - List', () => {
    it('should list users', async () => {
      const result = await fixture.listUsers();
      expect(result).toBeDefined();
      expect(result.currentPage).toEqual(0);
      expect(result.totalCount).toEqual(1);
      expect(result.data.length).toEqual(1);
    });
  });

  describe('Users - Get Single', () => {
    it('should reject unknown user with 404', async () => {
      await expect(fixture.getUser('wrong-id')).rejects.toMatchObject({
        response: { status: 404 },
        message: expect.stringContaining('User [wrong-id] can not be found'),
      });
    });

    it('should get a single user', async () => {
      const user = await fixture.getUser(userId);
      expect(user).toBeDefined();
      expect(user.id).toEqual(userId);
      expect(user.username).toEqual('jensen.barbara');
      expect(user.firstName).toEqual('Jensen');
      expect(user.lastName).toEqual('Barbara');
      expect(user.email).toEqual('jensen.barbara@mail.com');
    });
  });

  describe('Users - Update Status', () => {
    it('should disable user via status endpoint', async () => {
      const updated = await fixture.updateUserStatus(userId, false);
      expect(updated.enabled).toBe(false);
    });

    it('should re-enable user via status endpoint', async () => {
      const updated = await fixture.updateUserStatus(userId, true);
      expect(updated.enabled).toBe(true);
    });
  });

  describe('Users - Update Username', () => {
    it('should reject updating username for unknown user with 404', async () => {
      await expect(fixture.updateUsername('wrong-id', 'new.username')).rejects.toMatchObject({
        response: { status: 404 },
      });
    });

    it('should update username', async () => {
      const updated = await fixture.updateUsername(userId, 'jensen.barbara.renamed');
      expect(updated.username).toEqual('jensen.barbara.renamed');
    });

    it('should reject invalid username with 400', async () => {
      await expect(fixture.updateUsername(userId, '£Invalid')).rejects.toMatchObject({
        response: { status: 400 },
        message: expect.stringContaining('Username [£Invalid] is not a valid value'),
      });
    });

    it('should restore original username', async () => {
      const updated = await fixture.updateUsername(userId, 'jensen.barbara');
      expect(updated.username).toEqual('jensen.barbara');
    });
  });

  describe('Users - Lock / Unlock', () => {
    it('should lock user', async () => {
      await fixture.lockUser(userId);
      const user = await fixture.getUser(userId);
      expect(user.accountNonLocked).toBe(false);
    });

    it('should unlock user', async () => {
      await fixture.unlockUser(userId);
      const user = await fixture.getUser(userId);
      expect(user.accountNonLocked).toBe(true);
    });
  });

  describe('Users - Assign Roles', () => {
    let roleId: string;

    beforeAll(async () => {
      const role = await fixture.createRole('auth-role', 'Role for user assignment');
      roleId = role.id;
    });

    it('should reject assigning unknown role with 404', async () => {
      await expect(fixture.addRolesToUser(userId, ['wrong-role-id'])).rejects.toMatchObject({
        response: { status: 404 },
      });
    });

    it('should assign a role to user', async () => {
      const result = await fixture.addRolesToUser(userId, [roleId]);
      expect(result).toBeDefined();
      expect(result.roles).toEqual([roleId]);
    });
  });

  describe('Users - List Roles and Revoke', () => {
    let revokeRoleId: string;

    beforeAll(async () => {
      const role = await fixture.createRole('revoke-role', 'Role for revoke test');
      revokeRoleId = role.id;
      await fixture.addRolesToUser(userId, [revokeRoleId]);
    });

    it('should list user roles', async () => {
      const roles = await fixture.listUserRoles(userId);
      expect(roles).toBeDefined();
      expect(Array.isArray(roles)).toBe(true);
      expect(roles.some((r: any) => r.id === revokeRoleId)).toBe(true);
    });

    it('should revoke a role from user', async () => {
      await fixture.revokeUserRole(userId, revokeRoleId);
      const roles = await fixture.listUserRoles(userId);
      expect(roles.some((r: any) => r.id === revokeRoleId)).toBe(false);
    });

    it('should reject revoking unknown role with 404', async () => {
      await expect(fixture.revokeUserRole(userId, 'wrong-role-id')).rejects.toMatchObject({
        response: { status: 404 },
      });
    });
  });

  describe('Users - Send Registration Confirmation', () => {
    const preRegEmail = 'regconfirm.user@mail.com';
    let preRegUserId: string;

    beforeAll(async () => {
      // Dynamic user registration was already enabled in the Pre-Register section
      const user = await fixture.createUser({
        firstName: 'RegConfirm',
        lastName: 'User',
        username: 'regconfirm.user',
        email: preRegEmail,
        preRegistration: true,
      });
      preRegUserId = user.id;
      // Clear any emails sent during user creation
      await clearEmails(preRegEmail);
    });

    it('should send registration confirmation for pre-registered user', async () => {
      await fixture.sendRegistrationConfirmation(preRegUserId);
      const email = await getLastEmail(5000, preRegEmail);
      expect(email).toBeDefined();
      expect(email.toAddress).toEqual(preRegEmail);
      expect(email.subject).toBeDefined();
    });

    afterAll(async () => {
      await clearEmails(preRegEmail);
      await fixture.deleteUser(preRegUserId);
    });
  });
});
