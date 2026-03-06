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
import { Domain } from '@management-models/Domain';
import {
  createDomain,
  DomainOidcConfig,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { Application } from '@management-models/Application';
import { Factor } from '@management-models/Factor';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { expect } from '@jest/globals';
import { uniqueName } from '@utils-commands/misc';
import { createApplication, patchApplication, updateApplication } from '@management-commands/application-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { createFactor } from '@management-commands/factor-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { Fixture } from '../../../test-fixture';

export interface SelfAccountFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  application: Application;
  user: {
    username: string;
    password: string;
  };
}

export interface SelfAccountFactorFixture extends SelfAccountFixture {
  mockFactor: Factor;
}

export interface SelfAccountRecoveryCodeFixture extends SelfAccountFixture {
  recoveryCodeFactor: Factor;
}

export async function getEndUserAccessToken(fixture: SelfAccountFixture, password?: string): Promise<string> {
  const pw = password ?? fixture.user.password;
  const response = await performPost(
    fixture.oidc.token_endpoint,
    '',
    `grant_type=password&username=${fixture.user.username}&password=${pw}`,
    {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(fixture.application),
    },
  );
  expect(response.status).toBe(200);
  expect(response.body.access_token).toBeDefined();
  return response.body.access_token;
}

export const setupFixture = async (domainSettings: any): Promise<SelfAccountFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('self-account-change-password', true), 'Description');
  await patchDomain(domain.id, accessToken, domainSettings);
  const idpSet = await getAllIdps(domain.id, accessToken);

  const application = await createApplication(domain.id, accessToken, {
    name: 'name',
    type: 'WEB',
    clientId: uniqueName('selfaccount-changepwd', true),
    clientSecret: uniqueName('selfaccount-changepwd', true),
    redirectUris: ['https://callback'],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
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
        identityProviders: new Set([{ identity: idpSet.values().next().value.id, priority: -1 }]),
      },
      app.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  const username = uniqueName('SelfAccountUser', true);
  const user = {
    username: username,
    password: 'Password123!',
    firstName: 'SelfAccount',
    lastName: 'User',
    email: 'selfaccountuser@acme.fr',
    preRegistration: false,
  };
  await createUser(domain.id, accessToken, user);

  await startDomain(domain.id, accessToken);
  const domainWithOidc = await waitForDomainStart(domain);
  return {
    domain: domain,
    accessToken: accessToken,
    oidc: domainWithOidc.oidcConfig,
    application: application,
    user: user,
    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};

const SELF_ACCOUNT_ENABLED_SETTINGS = {
  selfServiceAccountManagementSettings: {
    enabled: true,
    resetPassword: {
      oldPasswordRequired: false,
      tokenAge: 0,
    },
  },
};

export const setupFactorFixture = async (): Promise<SelfAccountFactorFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('self-account-factors', true), 'Description');
  await patchDomain(domain.id, accessToken, SELF_ACCOUNT_ENABLED_SETTINGS);
  const idpSet = await getAllIdps(domain.id, accessToken);

  const application = await createApplication(domain.id, accessToken, {
    name: 'name',
    type: 'WEB',
    clientId: uniqueName('selfaccount-factors', true),
    clientSecret: uniqueName('selfaccount-factors', true),
    redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
            grantTypes: ['authorization_code', 'password'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
        },
        identityProviders: new Set([{ identity: idpSet.values().next().value.id, priority: -1 }]),
      },
      app.id,
    ).then((updatedApp) => {
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  const username = uniqueName('SelfAccountUser', true);
  const user = {
    username: username,
    password: 'Password123!',
    firstName: 'SelfAccount',
    lastName: 'User',
    email: 'selfaccountuser@acme.fr',
    preRegistration: false,
  };
  await createUser(domain.id, accessToken, user);
  await startDomain(domain.id, accessToken);
  const domainWithOidc = await waitForDomainStart(domain);

  const mockFactor = await waitForSyncAfter(domain.id, () =>
    createFactor(domain.id, accessToken, {
      type: 'otp-am-factor',
      factorType: 'TOTP',
      configuration: '{"issuer":"Gravitee.io","algorithm":"HmacSHA1","timeStep":"30","returnDigits":"6"}',
      name: 'TOTP Factor',
    }),
  );

  return {
    domain: domain,
    accessToken: accessToken,
    oidc: domainWithOidc.oidcConfig,
    application: application,
    user: user,
    mockFactor: mockFactor,
    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};

export const setupRecoveryCodeFixture = async (): Promise<SelfAccountRecoveryCodeFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('self-account-recovery', true), 'Description');
  await patchDomain(domain.id, accessToken, SELF_ACCOUNT_ENABLED_SETTINGS);
  const idpSet = await getAllIdps(domain.id, accessToken);

  const application = await createApplication(domain.id, accessToken, {
    name: 'name',
    type: 'WEB',
    clientId: uniqueName('selfaccount-recovery', true),
    clientSecret: uniqueName('selfaccount-recovery', true),
    redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
            grantTypes: ['authorization_code', 'password'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
        },
        identityProviders: new Set([{ identity: idpSet.values().next().value.id, priority: -1 }]),
      },
      app.id,
    ).then((updatedApp) => {
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  const username = uniqueName('SelfAccountUser', true);
  const user = {
    username: username,
    password: 'Password123!',
    firstName: 'SelfAccount',
    lastName: 'User',
    email: 'selfaccountuser@acme.fr',
    preRegistration: false,
  };
  await createUser(domain.id, accessToken, user);
  await startDomain(domain.id, accessToken);
  const domainWithOidc = await waitForDomainStart(domain);

  const recoveryCodeFactor = await waitForSyncAfter(domain.id, () =>
    createFactor(domain.id, accessToken, {
      type: 'recovery-code-am-factor',
      factorType: 'Recovery Code',
      configuration: '{"digit":8,"count":10}',
      name: 'Recovery Code Factor',
    }),
  );

  await waitForSyncAfter(domain.id, () =>
    patchApplication(domain.id, accessToken, {
      factors: new Set([recoveryCodeFactor.id]),
      settings: {
        mfa: {
          factor: {
            applicationFactors: [{ id: recoveryCodeFactor.id }],
            defaultFactorId: recoveryCodeFactor.id,
          },
        },
      },
    }, application.id),
  );

  return {
    domain: domain,
    accessToken: accessToken,
    oidc: domainWithOidc.oidcConfig,
    application: application,
    user: user,
    recoveryCodeFactor: recoveryCodeFactor,
    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};
