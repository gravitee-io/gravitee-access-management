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

import fetch from 'cross-fetch';
import { clearEmails, getLastEmail, hasEmail } from '@utils-commands/email-commands';

import { jest, afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { createUser, getUserPage } from '@management-commands/user-management-commands';
import { extractXsrfToken, performFormPost, performGet } from '@gateway-commands/oauth-oidc-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createApplication, patchApplication, updateApplication } from '@management-commands/application-management-commands';
import { uniqueName } from '@utils-commands/misc';

import cheerio from 'cheerio';

globalThis.fetch = fetch;

let domain;
let application;
let accessToken;
let clientId;
let defaultIdp;

jest.setTimeout(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();
  const createdDomain = await createDomain(accessToken, uniqueName('pre-registration', true), 'test user pre-registration');
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();
  domain = createdDomain;

  await startDomain(domain.id, accessToken);

  // Create the application
  const idpSet = await getAllIdps(domain.id, accessToken);
  defaultIdp = idpSet.values().next().value.id;
  const appClientId = uniqueName('flow-app', true);
  const appClientSecret = uniqueName('flow-app', true);
  const appName = uniqueName('my-client', true);
  application = await createApplication(domain.id, accessToken, {
    name: appName,
    type: 'WEB',
    clientId: appClientId,
    clientSecret: appClientSecret,
    redirectUris: ['https://callback'],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['https://callback'],
            grantTypes: ['authorization_code'],
          },
          login: {
            inherited: false,
            forgotPasswordEnabled: true,
          },
        },
        identityProviders: [{ identity: defaultIdp, priority: -1 }],
      },
      app.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );
  expect(application).toBeDefined();
  clientId = application.settings.oauth.clientId;

  // Wait for application to sync to gateway
  await waitForDomainSync(domain.id, accessToken);

  // Wait for domain to be ready to serve requests
  await waitForDomainStart(domain).then((started) => {
    domain = started.domain;
  });
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
    const createdUser = await createUser(domain.id, accessToken, preRegisteredUser);
    expect(createdUser).toBeDefined();
    // Pre registered user are not enabled.
    // They have to provide a password first.
    expect(createdUser.enabled).toBeFalsy();
    expect(createdUser.registrationUserUri).not.toBeDefined();
    expect(createdUser.registrationAccessToken).not.toBeDefined();

    // Retrieve and use confirmation email in the same test
    const confirmationLink = (await getLastEmail(1000, preRegisteredUser.email)).extractLink();
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
    const users = await getUserPage(domain.id, accessToken);
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
    await createUser(domain.id, accessToken, preRegisteredUser);
  });

  describe('User', () => {
    it('must received an email', async () => {
      const link = (await getLastEmail(1000, preRegisteredUser.email)).extractLink();
      expect(link).toBeDefined();
      await clearEmails(preRegisteredUser.email);
    });

    it("Can't request a new password", async () => {
      await forgotPassword(preRegisteredUser);
      expect(await hasEmail(1000, preRegisteredUser.email)).toBeFalsy();
    });

    it('Update Application to allow account validation using forgot password', async () => {
      patchApplication(
        domain.id,
        accessToken,
        {
          settings: {
            account: {
              inherited: false,
              completeRegistrationWhenResetPassword: true,
            },
          },
        },
        application.id,
      );
      await waitForDomainSync(domain.id, accessToken);
    });

    it('Can reset the password', async () => {
      await forgotPassword(preRegisteredUser);
      const confirmationLink = (await getLastEmail(1000, preRegisteredUser.email)).extractLink();
      expect(confirmationLink).toBeDefined();
      await clearEmails(preRegisteredUser.email);

      await resetPassword(confirmationLink, 'SomeP@ssw0rd');
    });

    it('must be enabled', async () => {
      // Fetch user by email
      const users = await getUserPage(domain.id, accessToken);
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
    patchApplication(
      domain.id,
      accessToken,
      {
        settings: {
          account: {
            inherited: false,
            completeRegistrationWhenResetPassword: false,
            dynamicUserRegistration: true,
          },
        },
      },
      application.id,
    );
    await waitForDomainSync(domain.id, accessToken);
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

    const createdUser = await createUser(domain.id, accessToken, preRegisteredUserWithoutApp);
    expect(createdUser).toBeDefined();
    expect(createdUser.enabled).toBeFalsy();
    expect(createdUser.registrationUserUri).not.toBeDefined();
    expect(createdUser.registrationAccessToken).not.toBeDefined();

    expect(await hasEmail(1000, preRegisteredUserWithoutApp.email)).toBeTruthy();
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
      client: application.id,
    };

    // Clear emails before creating user
    await clearEmails(preRegisteredUserWithApp.email);

    const createdUser = await createUser(domain.id, accessToken, preRegisteredUserWithApp);
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

afterAll(async () => {
  await safeDeleteDomain(domain?.id, accessToken);
});

const params = () => {
  return `?response_type=token&client_id=${clientId}&redirect_uri=http://localhost:4000`;
};

const forgotPassword = async (user) => {
  const uri = `/${domain.hrid}/forgotPassword` + params();
  const { headers, token } = await extractXsrfToken(process.env.AM_GATEWAY_URL, uri);

  //Submit forgot password form
  const postResponse = await performFormPost(
    process.env.AM_GATEWAY_URL,
    uri,
    {
      'X-XSRF-TOKEN': token,
      email: user.email,
      client_id: clientId,
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
