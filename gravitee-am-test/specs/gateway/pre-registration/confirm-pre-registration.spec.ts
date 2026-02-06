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

import { clearEmails, getLastEmail, hasEmail } from '@utils-commands/email-commands';

import { afterAll, beforeAll, expect } from '@jest/globals';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { createUser, getUserPage } from '@management-commands/user-management-commands';
import { extractXsrfToken, performFormPost, performGet } from '@gateway-commands/oauth-oidc-commands';
import { patchApplication } from '@management-commands/application-management-commands';
import { uniqueName } from '@utils-commands/misc';

import cheerio from 'cheerio';
import { ConfirmPreRegistrationFixture, setupFixture } from './fixture/confirm-pre-registration-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: ConfirmPreRegistrationFixture;

beforeAll(async () => {
  fixture = await setupFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanup();
});

describe('AM - User Pre-Registration', () => {
  // Generate unique username and email to avoid conflicts in parallel execution
  const username = uniqueName('preregister', true);
  const preRegisteredUser = {
    username: username,
    firstName: 'preregister',
    lastName: 'preregister',
    email: `${username}@acme.fr`,
    preRegistration: true,
  };

  beforeAll(async () => {
    // Clear emails for this specific recipient at the start to avoid interference from other tests
    await clearEmails(preRegisteredUser.email);
  });

  it('must pre-register a user, receive email, and confirm registration', async () => {
    // Create user
    const createdUser = await createUser(fixture.domain.id, fixture.accessToken, preRegisteredUser);
    expect(createdUser).toBeDefined();
    // Pre registered user are not enabled.
    // They have to provide a password first.
    expect(createdUser.enabled).toBeFalsy();
    expect(createdUser.registrationUserUri).not.toBeDefined();
    expect(createdUser.registrationAccessToken).not.toBeDefined();

    // Retrieve and use confirmation email in the same test
    const confirmationLink = (await getLastEmail(5000, preRegisteredUser.email)).extractLink();
    expect(confirmationLink).toBeDefined();
    await clearEmails(preRegisteredUser.email);

    // Confirm registration
    const url = new URL(confirmationLink);
    const resetPwdToken = url.searchParams.get('token');
    const baseUrlConfirmRegister = confirmationLink.substring(0, confirmationLink.indexOf('?'));

    const { headers, token: xsrfToken } = await extractXsrfToken(baseUrlConfirmRegister, '?token=' + resetPwdToken);

    const postConfirmRegistration = await performFormPost(
      baseUrlConfirmRegister,
      '',
      {
        'X-XSRF-TOKEN': xsrfToken,
        token: resetPwdToken,
        password: '#CoMpL3X-P@SsW0Rd',
      },
      {
        Cookie: headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    expect(postConfirmRegistration.headers['location']).toBeDefined();
    expect(postConfirmRegistration.headers['location']).toContain('success=registration_completed');
  });

  it('must be enabled', async () => {
    // Fetch user by email
    const users = await getUserPage(fixture.domain.id, fixture.accessToken);
    const user = users.data.find((u) => u.email === preRegisteredUser.email || u.username === preRegisteredUser.username);
    expect(user).toBeDefined();
    // Pre registered user are not enabled.
    // They have to provide a password first.
    expect(user.enabled).toBeTruthy();
  });
});

describe('AM - User Pre-Registration - Reset Password to confirm', () => {
  // Generate unique username and email to avoid conflicts in parallel execution
  const username = uniqueName('preregister2', true);
  const preRegisteredUser = {
    username: username,
    firstName: 'preregister',
    lastName: 'preregister2',
    email: `${username}@acme.fr`,
    preRegistration: true,
  };

  beforeAll(async () => {
    // Clear emails for this specific recipient at the start to avoid interference from other tests
    await clearEmails(preRegisteredUser.email);
  });

  it('must pre-register a user', async () => {
    await createUser(fixture.domain.id, fixture.accessToken, preRegisteredUser);
  });

  describe('User', () => {
    it('must received an email', async () => {
      const link = (await getLastEmail(5000, preRegisteredUser.email)).extractLink();
      expect(link).toBeDefined();
      await clearEmails(preRegisteredUser.email);
    });

    it("Can't request a new password", async () => {
      await forgotPassword(preRegisteredUser);
      expect(await hasEmail(2000, preRegisteredUser.email)).toBeFalsy();
    });

    it('Update Application to allow account validation using forgot password', async () => {
      await waitForSyncAfter(
        fixture.domain.id,
        patchApplication(
          fixture.domain.id,
          fixture.accessToken,
          {
            settings: {
              account: {
                inherited: false,
                completeRegistrationWhenResetPassword: true,
              },
            },
          },
          fixture.application.id,
        ),
      );
    });

    it('Can reset the password', async () => {
      await forgotPassword(preRegisteredUser);
      const confirmationLink = (await getLastEmail(5000, preRegisteredUser.email)).extractLink();
      expect(confirmationLink).toBeDefined();
      await clearEmails(preRegisteredUser.email);

      await resetPassword(confirmationLink, 'SomeP@ssw0rd');
    });

    it('must be enabled', async () => {
      // Fetch user by email
      const users = await getUserPage(fixture.domain.id, fixture.accessToken);
      const user = users.data.find((u) => u.email === preRegisteredUser.email || u.username === preRegisteredUser.username);
      expect(user).toBeDefined();
      // Pre registered user are not enabled.
      // They have to provide a password first.
      expect(user.enabled).toBeTruthy();
    });
  });
});

describe('AM - User Pre-Registration - Dynamic User Registration', () => {
  it('Update Application to allow Dynamic User Registration', async () => {
    await waitForSyncAfter(
      fixture.domain.id,
      patchApplication(
        fixture.domain.id,
        fixture.accessToken,
        {
          settings: {
            account: {
              inherited: false,
              completeRegistrationWhenResetPassword: false,
              dynamicUserRegistration: true,
            },
          },
        },
        fixture.application.id,
      ),
    );
  });

  it('Pre-Registered user without application id MUST NOT have registration contact point information', async () => {
    // Generate unique username and email to avoid conflicts in parallel execution
    const username = uniqueName('preregister3', true);
    const preRegisteredUserWithoutApp = {
      username: username,
      firstName: 'preregister',
      lastName: 'preregister3',
      email: `${username}@acme.fr`,
      preRegistration: true,
    };

    const createdUser = await createUser(fixture.domain.id, fixture.accessToken, preRegisteredUserWithoutApp);
    expect(createdUser).toBeDefined();
    expect(createdUser.enabled).toBeFalsy();
    expect(createdUser.registrationUserUri).not.toBeDefined();
    expect(createdUser.registrationAccessToken).not.toBeDefined();

    expect(await hasEmail(5000, preRegisteredUserWithoutApp.email)).toBeTruthy();
    await clearEmails(preRegisteredUserWithoutApp.email);
  });

  it('Pre-Registered user with application id MUST have registration contact point information', async () => {
    // Generate unique username and email to avoid conflicts in parallel execution
    const username = uniqueName('preregister4', true);
    const preRegisteredUserWithApp = {
      username: username,
      firstName: 'preregister',
      lastName: 'preregister4',
      email: `${username}@acme.fr`,
      preRegistration: true,
      client: fixture.application.id,
    };

    // Clear emails before creating user
    await clearEmails(preRegisteredUserWithApp.email);

    const createdUser = await createUser(fixture.domain.id, fixture.accessToken, preRegisteredUserWithApp);
    expect(createdUser).toBeDefined();
    expect(createdUser.enabled).toBeFalsy();
    expect(createdUser.registrationUserUri).toBeDefined();
    expect(createdUser.registrationAccessToken).toBeDefined();

    // Check that no email was sent (dynamic registration enabled, so no email should be sent)
    // Wait a bit to ensure no email is sent
    await new Promise((resolve) => setTimeout(resolve, 1000));
    // Check that no email exists for this user
    expect(await hasEmail(1000, preRegisteredUserWithApp.email)).toBeFalsy();
  });
});

const params = () => {
  return `?response_type=token&client_id=${fixture.clientId}&redirect_uri=http://localhost:4000`;
};

const forgotPassword = async (user) => {
  const uri = `/${fixture.domain.hrid}/forgotPassword` + params();
  const { headers, token } = await extractXsrfToken(process.env.AM_GATEWAY_URL, uri);

  //Submit forgot password form
  const postResponse = await performFormPost(
    process.env.AM_GATEWAY_URL,
    uri,
    {
      'X-XSRF-TOKEN': token,
      email: user.email,
      client_id: fixture.clientId,
    },
    {
      Cookie: headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);

  expect(postResponse.headers['location']).toContain('forgot_password_completed');
};

const resetPassword = async (resetPasswordLink, password) => {
  const resetPwdLinkResponse = await performGet(resetPasswordLink);
  const headers = resetPwdLinkResponse.headers['set-cookie'];
  const dom = cheerio.load(resetPwdLinkResponse.text);
  const action = dom('form').attr('action');
  const xsrfToken = dom('[name=X-XSRF-TOKEN]').val();
  const resetPwdToken = dom('[name=token]').val();
  const resetResponse = await performFormPost(
    action,
    '',
    {
      'X-XSRF-TOKEN': xsrfToken,
      token: resetPwdToken,
      password: password,
      'confirm-password': password,
    },
    {
      Cookie: headers,
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);
  expect(resetResponse.headers['location']).toContain('success=reset_password_completed');
};
