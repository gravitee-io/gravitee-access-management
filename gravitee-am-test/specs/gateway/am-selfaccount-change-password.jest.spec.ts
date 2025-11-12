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

globalThis.fetch = fetch;

import { jest, afterAll, beforeAll, beforeEach, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, patchDomain, startDomain, waitForDomainStart, waitForDomainSync } from '@management-commands/domain-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';

let domain;
let managementApiAccessToken;
let openIdConfiguration;
let application;
let user;
let originalPassword: string;
const PATCH_DOMAIN_SEFLACCOUNT_SETTINGS = {
  selfServiceAccountManagementSettings: {
    enabled: true,
    resetPassword: {
      oldPasswordRequired: false,
      tokenAge: 0,
    },
  },
};

jest.setTimeout(200000);

beforeAll(async () => {
  managementApiAccessToken = await requestAdminAccessToken();
  expect(managementApiAccessToken).toBeDefined();
  domain = await createDomain(managementApiAccessToken, uniqueName('self-account-change-password', true), 'test Change Password through SelfAccount API');
  expect(domain).toBeDefined();

  await startDomain(domain.id, managementApiAccessToken);
  await patchDomain(domain.id, managementApiAccessToken, PATCH_DOMAIN_SEFLACCOUNT_SETTINGS);

  // Create the application
  const idpSet = await getAllIdps(domain.id, managementApiAccessToken);
  const appClientId = uniqueName('selfaccount-changepwd', true);
  const appClientSecret = uniqueName('selfaccount-changepwd', true);
  const appName = uniqueName('my-client', true);
  application = await createApplication(domain.id, managementApiAccessToken, {
    name: appName,
    type: 'WEB',
    clientId: appClientId,
    clientSecret: appClientSecret,
    redirectUris: ['https://callback'],
  }).then((app) =>
    updateApplication(
      domain.id,
      managementApiAccessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['https://callback'],
            grantTypes: ['authorization_code', 'password'],
            scopeSettings: [
              {
                scope: 'openid',
                defaultScope: true,
              },
              {
                scope: 'profile',
                defaultScope: true,
              },
              {
                scope: 'email',
                defaultScope: true,
              },
            ],
          },
        },
        identityProviders: [{ identity: idpSet.values().next().value.id, priority: -1 }],
      },
      app.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );
  expect(application).toBeDefined();

  // Wait for application to sync to gateway
  await waitForDomainSync(domain.id, managementApiAccessToken);

  // Create a User
  const username = uniqueName('SelfAccountUser', true);
  originalPassword = 'SomeP@ssw0rd';
  user = {
    username: username,
    password: originalPassword,
    firstName: 'SelfAccount',
    lastName: 'User',
    email: 'selfaccountuser@acme.fr',
    preRegistration: false,
  };
  await createUser(domain.id, managementApiAccessToken, user);
  // Ensure user is synced to gateway before authentication
  await waitForDomainSync(domain.id, managementApiAccessToken);

  await waitForDomainStart(domain).then((started) => {
    domain = started.domain;
    openIdConfiguration = started.oidcConfig;
    expect(openIdConfiguration).toBeDefined();
  });
});

describe('SelfAccount - Change Password', () => {
  let currentPassword: string;

  beforeAll(() => {
    // Initialize currentPassword after originalPassword is set in outer beforeAll
    // This ensures currentPassword is set before any tests run
    currentPassword = originalPassword;
    expect(currentPassword).toBeDefined();
    expect(currentPassword).toBe(originalPassword);
  });

  describe('With default settings', () => {
    describe('End User', () => {
      it('must be able to change his password', async () => {
        // Generate token with original password (user was created with this password)
        const tokenResponse = await performPost(
          openIdConfiguration.token_endpoint,
          '',
          `grant_type=password&username=${user.username}&password=${originalPassword}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(application),
          },
        ).expect(200);
        const endUserAccessToken = tokenResponse.body.access_token;

        currentPassword = 'SomeP@ssw0rd!';

        await performPost(
          `${process.env.AM_GATEWAY_URL}/${domain.hrid}/account/api/changePassword`,
          '',
          `{"password": "${currentPassword}"}`,
          {
            'Content-type': 'application/json',
            Authorization: `Bearer ${endUserAccessToken}`,
          },
        ).expect(204);
        
        // Wait for password change to sync to gateway
        await waitForDomainSync(domain.id, managementApiAccessToken);
      });

      it('must be able to sign in using new password', async () => {
        const response = await performPost(
          openIdConfiguration.token_endpoint,
          '',
          `grant_type=password&username=${user.username}&password=${currentPassword}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(application),
          },
        ).expect(200);
        expect(response.body.access_token).toBeDefined();
      });
    });
  });

  describe('When oldPassword is requried', () => {
    beforeAll(async () => {
      // Update domain settings once for this test group
      PATCH_DOMAIN_SEFLACCOUNT_SETTINGS.selfServiceAccountManagementSettings.resetPassword.oldPasswordRequired = true;
      await patchDomain(domain.id, managementApiAccessToken, PATCH_DOMAIN_SEFLACCOUNT_SETTINGS);
      await waitForDomainSync(domain.id, managementApiAccessToken);
    });

    describe('EndUser ', () => {
      it('must NOT be able to change his password if old one is missing', async () => {
        // Generate token with current password
        const tokenResponse = await performPost(
          openIdConfiguration.token_endpoint,
          '',
          `grant_type=password&username=${user.username}&password=${currentPassword}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(application),
          },
        ).expect(200);
        const endUserAccessToken = tokenResponse.body.access_token;

        await performPost(
          `${process.env.AM_GATEWAY_URL}/${domain.hrid}/account/api/changePassword`,
          '',
          `{"password": "TestWithoutOldPassword"}`,
          {
            'Content-type': 'application/json',
            Authorization: `Bearer ${endUserAccessToken}`,
          },
        ).expect(400);
      });

      it('must NOT be able to change his password if old one is invalid', async () => {
        // Generate token with current password
        const tokenResponse = await performPost(
          openIdConfiguration.token_endpoint,
          '',
          `grant_type=password&username=${user.username}&password=${currentPassword}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(application),
          },
        ).expect(200);
        const endUserAccessToken = tokenResponse.body.access_token;

        await performPost(
          `${process.env.AM_GATEWAY_URL}/${domain.hrid}/account/api/changePassword`,
          '',
          `{"password": "TestPassword123!", "oldPassword": "invalidpassword"}`,
          {
            'Content-type': 'application/json',
            Authorization: `Bearer ${endUserAccessToken}`,
          },
        ).expect(401);
      });

      it('must be able to change his password if old one is present', async () => {
        // Generate token with current password
        const tokenResponse = await performPost(
          openIdConfiguration.token_endpoint,
          '',
          `grant_type=password&username=${user.username}&password=${currentPassword}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(application),
          },
        ).expect(200);
        const endUserAccessToken = tokenResponse.body.access_token;

        const oldPassword = currentPassword;
        currentPassword = 'TestW1thOldP@ssw0rd';

        await performPost(
          `${process.env.AM_GATEWAY_URL}/${domain.hrid}/account/api/changePassword`,
          '',
          `{"password": "${currentPassword}", "oldPassword": "${oldPassword}"}`,
          {
            'Content-type': 'application/json',
            Authorization: `Bearer ${endUserAccessToken}`,
          },
        ).expect(204);
        
        // Wait for password change to sync to gateway
        await waitForDomainSync(domain.id, managementApiAccessToken);
      });

      it('must be able to sign in using new password', async () => {
        const response = await performPost(
          openIdConfiguration.token_endpoint,
          '',
          `grant_type=password&username=${user.username}&password=${currentPassword}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(application),
          },
        ).expect(200);
        expect(response.body.access_token).toBeDefined();
      });
    });
  });

  describe('When access_token is too old', () => {
    beforeAll(async () => {
      // Update domain settings once for this test group
      PATCH_DOMAIN_SEFLACCOUNT_SETTINGS.selfServiceAccountManagementSettings.resetPassword.tokenAge = 10;
      await patchDomain(domain.id, managementApiAccessToken, PATCH_DOMAIN_SEFLACCOUNT_SETTINGS);
      await waitForDomainSync(domain.id, managementApiAccessToken);
    });

    describe('EndUser ', () => {
      it('must be able to change his password if old one is present', async () => {
        // Generate token and wait for it to age (tokenAge = 10 seconds)
        const tokenResponse = await performPost(
          openIdConfiguration.token_endpoint,
          '',
          `grant_type=password&username=${user.username}&password=${currentPassword}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(application),
          },
        ).expect(200);
        const endUserAccessToken = tokenResponse.body.access_token;

        // Wait for token to become older than tokenAge (10 seconds)
        await new Promise((resolve) => setTimeout(resolve, 11000)); // 10 seconds + 1 second buffer

        const oldPassword = currentPassword;
        await performPost(
          `${process.env.AM_GATEWAY_URL}/${domain.hrid}/account/api/changePassword`,
          '',
          `{"password": "fake", "oldPassword": "${oldPassword}"}`,
          {
            'Content-type': 'application/json',
            Authorization: `Bearer ${endUserAccessToken}`,
          },
        ).expect(401);
      });

      it('must be able to sign in using current password', async () => {
        const response = await performPost(
          openIdConfiguration.token_endpoint,
          '',
          `grant_type=password&username=${user.username}&password=${currentPassword}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(application),
          },
        ).expect(200);
        expect(response.body.access_token).toBeDefined();
      });
    });
  });
});

afterAll(async () => {
  await safeDeleteDomain(domain?.id, managementApiAccessToken);
});

