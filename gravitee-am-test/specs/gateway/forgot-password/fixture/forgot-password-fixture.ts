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
import { Application } from '@management-models/Application';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitFor,
  waitForDomainStart,
  DomainOidcConfig,
} from '@management-commands/domain-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { createUser } from '@management-commands/user-management-commands';
import { clearEmails } from '@utils-commands/email-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { createPasswordPolicy } from '@management-commands/password-policy-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';
import { DomainTestSettings } from './settings-utils';
import { User } from '@management-models/User';

export interface ForgotPasswordFixture extends Fixture {
  domain: Domain;
  application: Application;
  openIdConfiguration: DomainOidcConfig;
  user: User;
  clientId: string;
  userSessionToken: string;
}

export const setupFixture = async (setting: DomainTestSettings, userProps: User): Promise<ForgotPasswordFixture> => {
  await clearEmails(userProps.email);

  const accessToken = await requestAdminAccessToken();
  let domain = await createDomain(accessToken, uniqueName('forgot-password', true), 'test Gateway reset password with password history');
  domain = await patchDomain(domain.id, accessToken, {
    master: true,
    oidc: {
      clientRegistrationSettings: {
        allowLocalhostRedirectUri: true,
        allowHttpSchemeRedirectUri: true,
        allowWildCardRedirectUri: true,
      },
    },
    loginSettings: {
      forgotPasswordEnabled: true,
    },
    accountSettings: setting.inherited ? setting.settings.accountSettings : {},
  });

  if (setting.passwordPolicy) {
    await createPasswordPolicy(domain.id, accessToken, setting.passwordPolicy);
  }

  const application = await createApplication(domain.id, accessToken, {
    name: uniqueName('forgot-password-app', true),
    type: 'WEB',
    clientId: uniqueName('forgot-password-app', true),
    redirectUris: ['http://localhost:4000'],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['http://localhost:4000'],
            grantTypes: ['implicit', 'authorization_code', 'password', 'refresh_token'],
            scopeSettings: [
              {
                scope: 'openid',
                defaultScope: true,
              },
            ],
          },
          account: setting.inherited ? {} : setting.settings.accountSettings,
          passwordSettings: setting.inherited ? {} : setting.settings.passwordSettings,
        },
        identityProviders: [{ identity: `default-idp-${domain.id}`, priority: 0 }],
      },
      app.id,
    ).then((updatedApp) => {
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  const clientId = application.settings.oauth.clientId;

  const started = await startDomain(domain.id, accessToken).then(waitForDomainStart);
  domain = started.domain;
  const openIdConfiguration = started.oidcConfig;

  const user = await createUser(domain.id, accessToken, userProps);
  await waitFor(1000);
  if (setting.settings.accountSettings.resetPasswordConfirmIdentity) {
    await createUser(domain.id, accessToken, {
      firstName: 'second',
      lastName: 'user',
      email: userProps.email,
      username: 'anotherUser',
      password: 'r@Nd0mP@55w0rd!',
    });
    await waitFor(1000);
  }

  const response = await performPost(
    openIdConfiguration.token_endpoint,
    '',
    `grant_type=password&username=${encodeURIComponent(userProps.username)}&password=${encodeURIComponent(userProps.password)}&scope=openid`,
    {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(application),
    },
  ).expect(200);
  const userSessionToken = response.body.access_token;

  return {
    accessToken,
    domain,
    application,
    openIdConfiguration,
    user,
    clientId,
    userSessionToken,
    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};
