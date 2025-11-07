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
  waitFor,
  waitForDomainStart,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { createUser, getUser } from '@management-commands/user-management-commands';
import { extractXsrfToken, performFormPost, performGet } from '@gateway-commands/oauth-oidc-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createApplication, patchApplication, updateApplication } from '@management-commands/application-management-commands';
import { uniqueName } from '@utils-commands/misc';

import cheerio from 'cheerio';

global.fetch = fetch;

let domain;
let application;
let accessToken;
let confirmationLink;
let createdUser;
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

  const domainStarted = await startDomain(domain.id, accessToken);
  expect(domainStarted).toBeDefined();
  expect(domainStarted.id).toEqual(domain.id);
  domain = domainStarted;

  // Create the application
  const idpSet = await getAllIdps(domain.id, accessToken);
  defaultIdp = idpSet.values().next().value.id;
  application = await createApplication(domain.id, accessToken, {
    name: 'my-client',
    type: 'WEB',
    clientId: 'flow-app',
    clientSecret: 'flow-app',
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

  await waitForDomainStart(domain);
});

describe('AM - User Pre-Registration', () => {
  const preRegisteredUser = {
    username: 'preregister',
    firstName: 'preregister',
    lastName: 'preregister',
    email: 'preregister@acme.fr',
    preRegistration: true,
  };

  it('must pre-register a user', async () => {
    createdUser = await createUser(domain.id, accessToken, preRegisteredUser);
    expect(createdUser).toBeDefined();
    // Pre registered user are not enabled.
    // They have to provide a password first.
    expect(createdUser.enabled).toBeFalsy();
    expect(createdUser.registrationUserUri).not.toBeDefined();
    expect(createdUser.registrationAccessToken).not.toBeDefined();
  });

  describe('User', () => {
    it('must received an email', async () => {
      confirmationLink = (await getLastEmail()).extractLink();
      expect(confirmationLink).toBeDefined();
      await clearEmails();
    });

    it('must confirm the registration by providing a password', async () => {
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
      let user = await getUser(domain.id, accessToken, createdUser.id);
      expect(user).toBeDefined();
      // Pre registered user are not enabled.
      // They have to provide a password first.
      expect(user.enabled).toBeTruthy();
    });
  });
});

describe('AM - User Pre-Registration - Reset Password to confirm', () => {
  const preRegisteredUser = {
    username: 'preregister2',
    firstName: 'preregister',
    lastName: 'preregister2',
    email: 'preregister2@acme.fr',
    preRegistration: true,
  };

  it('must pre-register a user', async () => {
    createdUser = await createUser(domain.id, accessToken, preRegisteredUser);
    expect(createdUser).toBeDefined();
    expect(createdUser.enabled).toBeFalsy();
    expect(createdUser.registrationUserUri).not.toBeDefined();
    expect(createdUser.registrationAccessToken).not.toBeDefined();
  });

  describe('User', () => {
    it('must received an email', async () => {
      const link = (await getLastEmail()).extractLink();
      expect(link).toBeDefined();
      await clearEmails();
    });

    it("Can't request a new password", async () => {
      await forgotPassword(preRegisteredUser);
      expect(await hasEmail()).toBeFalsy();
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
      confirmationLink = (await getLastEmail()).extractLink();
      expect(confirmationLink).toBeDefined();
      await clearEmails();

      await resetPassword(confirmationLink, 'SomeP@ssw0rd');
    });

    it('must be enabled', async () => {
      let user = await getUser(domain.id, accessToken, createdUser.id);
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
    const preRegisteredUserWithoutApp = {
      username: 'preregister3',
      firstName: 'preregister',
      lastName: 'preregister3',
      email: 'preregister3@acme.fr',
      preRegistration: true,
    };

    createdUser = await createUser(domain.id, accessToken, preRegisteredUserWithoutApp);
    expect(createdUser).toBeDefined();
    expect(createdUser.enabled).toBeFalsy();
    expect(createdUser.registrationUserUri).not.toBeDefined();
    expect(createdUser.registrationAccessToken).not.toBeDefined();

    expect(await hasEmail()).toBeTruthy();
    await clearEmails();
  });

  it('Pre-Registered user with application id MUST have registration contact point information', async () => {
    const preRegisteredUserWithApp = {
      username: 'preregister4',
      firstName: 'preregister',
      lastName: 'preregister4',
      email: 'preregister4@acme.fr',
      preRegistration: true,
      client: application.id,
    };

    createdUser = await createUser(domain.id, accessToken, preRegisteredUserWithApp);
    expect(createdUser).toBeDefined();
    expect(createdUser.enabled).toBeFalsy();
    expect(createdUser.registrationUserUri).toBeDefined();
    expect(createdUser.registrationAccessToken).toBeDefined();
  });

  it("Pre-Registered user doesn't receive email", async () => {
    expect(await hasEmail()).toBeFalsy();
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
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
