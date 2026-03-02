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
import { ForgotPasswordContext } from './forgot-password-flow-utils';
import { ResetPasswordContext } from './reset-password-flow-utils';
import { User } from '@management-models/User';

export interface ForgotPasswordFixture extends Fixture {
  domain: Domain;
  application: Application;
  openIdConfiguration: DomainOidcConfig;
  user: User;
  clientId: string;
  userSessionToken: string;
  forgotPasswordContext: () => ForgotPasswordContext;
  resetPasswordContext: () => ResetPasswordContext;
}

export const setupFixture = async (setting: DomainTestSettings, userProps: User): Promise<ForgotPasswordFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    await clearEmails(userProps.email);

    accessToken = await requestAdminAccessToken();
    domain = await createDomain(accessToken, uniqueName('forgot-password', true), 'test Gateway reset password');
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

    // Create users BEFORE starting domain — initial sync picks them up, no waitFor needed
    const user = await createUser(domain.id, accessToken, userProps);
    if (setting.settings.accountSettings.resetPasswordConfirmIdentity) {
      await createUser(domain.id, accessToken, {
        firstName: 'second',
        lastName: 'user',
        email: userProps.email,
        username: uniqueName('another-user', true),
        password: 'r@Nd0mP@55w0rd!',
      });
    }

    const started = await startDomain(domain.id, accessToken).then(waitForDomainStart);
    domain = started.domain;
    const openIdConfiguration = started.oidcConfig;

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

    const forgotPasswordContext = (): ForgotPasswordContext => ({
      domainHrid: domain.hrid,
      clientId,
      openIdConfiguration,
      user,
    });

    const resetPasswordContext = (): ResetPasswordContext => ({
      openIdConfiguration,
      application,
      userSessionToken,
      user,
      resetPasswordFailed: 'error=reset_password_failed',
      invalidPasswordValue: 'invalid_password_value',
    });

    return {
      accessToken,
      domain,
      application,
      openIdConfiguration,
      user,
      clientId,
      userSessionToken,
      forgotPasswordContext,
      resetPasswordContext,
      cleanUp: async () => {
        if (domain?.id && accessToken) {
          await safeDeleteDomain(domain.id, accessToken);
        }
      },
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup domain after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};
