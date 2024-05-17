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

global.fetch = fetch;

import { jest, afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, deleteDomain, patchDomain, startDomain } from '@management-commands/domain-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { getWellKnownOpenIdConfiguration, performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';

let domain;
let managementApiAccessToken;
let endUserAccessToken;
let openIdConfiguration;
let application;
let user = {
  username: 'SelfAccountUser',
  password: 'SomeP@ssw0rd',
  firstName: 'SelfAccount',
  lastName: 'User',
  email: 'selfaccountuser@acme.fr',
  preRegistration: false,
};
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

const waitForDomainSync = () => new Promise((r) => setTimeout(r, 10000));

beforeAll(async () => {
  const adminTokenResponse = await requestAdminAccessToken();
  managementApiAccessToken = adminTokenResponse.body.access_token;
  expect(managementApiAccessToken).toBeDefined();
  domain = await createDomain(managementApiAccessToken, 'self-account-change-password', 'test Change Password through SelfAccount API');
  expect(domain).toBeDefined();

  await startDomain(domain.id, managementApiAccessToken);
  await patchDomain(domain.id, managementApiAccessToken, PATCH_DOMAIN_SEFLACCOUNT_SETTINGS);

  // Create the application
  const idpSet = await getAllIdps(domain.id, managementApiAccessToken);

  application = await createApplication(domain.id, managementApiAccessToken, {
    name: 'my-client',
    type: 'WEB',
    clientId: 'selfaccount-changepwd',
    clientSecret: 'selfaccount-changepwd',
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

  // Create a User
  await createUser(domain.id, managementApiAccessToken, user);
  await waitForDomainSync();

  const result = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);
  openIdConfiguration = result.body;
  expect(openIdConfiguration).toBeDefined();
});

describe('SelfAccount - Change Password', () => {
  describe('Prepare - Sign in user', () => {
    it('must deliver an access_token', async () => {
      let response = await performPost(
        openIdConfiguration.token_endpoint,
        '',
        `grant_type=password&username=${user.username}&password=${user.password}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + applicationBase64Token(application),
        },
      ).expect(200);

      expect(response.body.access_token).toBeDefined();
      endUserAccessToken = response.body.access_token;
    });
  });
});

describe('SelfAccount - Change Password', () => {
  describe('With default settings', () => {
    describe('End User', () => {
      it('must be able to change his password', async () => {
        user.password = 'SomeP@ssw0rd!';

        await performPost(
          `${process.env.AM_GATEWAY_URL}/${domain.hrid}/account/api/changePassword`,
          '',
          `{"password": "${user.password}"}`,
          {
            'Content-type': 'application/json',
            Authorization: `Bearer ${endUserAccessToken}`,
          },
        ).expect(204);
      });

      it('must be able to sign in using new password', checkUserSignIn());
    });
  });

  describe('When oldPassword is requried', () => {
    describe('Prepare', () => {
      it('domain settings have to be updated', async () => {
        PATCH_DOMAIN_SEFLACCOUNT_SETTINGS.selfServiceAccountManagementSettings.resetPassword.oldPasswordRequired = true;
        await patchDomain(domain.id, managementApiAccessToken, PATCH_DOMAIN_SEFLACCOUNT_SETTINGS);
        await waitForDomainSync();
      });
    });

    describe('EndUser ', () => {
      it('must NOT be able to change his password if old one is missing', async () => {
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
        await performPost(
          `${process.env.AM_GATEWAY_URL}/${domain.hrid}/account/api/changePassword`,
          '',
          `{"password": "${user.password}", "oldPassword": "invalidpassword"}`,
          {
            'Content-type': 'application/json',
            Authorization: `Bearer ${endUserAccessToken}`,
          },
        ).expect(401);
      });

      it('must be able to change his password if old one is present', async () => {
        let oldPassword = user.password;
        user.password = 'TestW1thOldP@ssw0rd';

        await performPost(
          `${process.env.AM_GATEWAY_URL}/${domain.hrid}/account/api/changePassword`,
          '',
          `{"password": "${user.password}", "oldPassword": "${oldPassword}"}`,
          {
            'Content-type': 'application/json',
            Authorization: `Bearer ${endUserAccessToken}`,
          },
        ).expect(204);
      });

      it('must be able to sign in using new password', checkUserSignIn());
    });
  });

  describe('When access_token is too old', () => {
    describe('Prepare', () => {
      it('domain settings have to be updated', async () => {
        PATCH_DOMAIN_SEFLACCOUNT_SETTINGS.selfServiceAccountManagementSettings.resetPassword.tokenAge = 10;
        await patchDomain(domain.id, managementApiAccessToken, PATCH_DOMAIN_SEFLACCOUNT_SETTINGS);
        await waitForDomainSync();
      });
    });

    describe('EndUser ', () => {
      it('must be able to change his password if old one is present', async () => {
        let oldPassword = user.password;
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

      it('must be able to sign in using current password', checkUserSignIn());
    });
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await deleteDomain(domain.id, managementApiAccessToken);
  }
});

function checkUserSignIn(): Mocha.Func {
  return async () => {
    let response = await performPost(
      openIdConfiguration.token_endpoint,
      '',
      `grant_type=password&username=${user.username}&password=${user.password}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + applicationBase64Token(application),
      },
    );
    expect(200);
    expect(response.body.access_token).toBeDefined();
    endUserAccessToken = response.body.access_token;
  };
}
