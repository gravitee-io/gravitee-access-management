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
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { Application } from '@management-models/Application';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { extractXsrfToken, performFormPost } from '@gateway-commands/oauth-oidc-commands';
import { User } from '@management-models/User';
import { Fixture } from '../../../test-fixture';

export interface VerifyEmailFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  defaultIdp: IdentityProvider;
  /** App with sendVerifyRegistrationAccountEmail=true, autoLogin=false */
  appVerifyOnly: Application;
  /** App with sendVerifyRegistrationAccountEmail=true, autoLogin=true, redirectUri set */
  appVerifyAutoLogin: Application;
}

export const setupFixture = async (): Promise<VerifyEmailFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    // Create domain but don't start yet — apps must exist before first sync
    domain = await createDomain(accessToken, uniqueName('reg-verify-email', true), 'Email verification registration tests');

    const idpSet = await getAllIdps(domain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;

    const appVerifyOnly = await createApp(domain.id, accessToken, {
      settings: {
        oauth: {
          redirectUris: ['https://callback'],
          grantTypes: ['authorization_code'],
        },
        login: {
          inherited: false,
          registerEnabled: true,
        },
        account: {
          inherited: false,
          sendVerifyRegistrationAccountEmail: true,
        },
      },
      identityProviders: [{ identity: defaultIdp.id, priority: -1 }],
    });

    const appVerifyAutoLogin = await createApp(domain.id, accessToken, {
      settings: {
        oauth: {
          redirectUris: ['https://callback'],
          grantTypes: ['authorization_code'],
        },
        login: {
          inherited: false,
          registerEnabled: true,
        },
        account: {
          inherited: false,
          sendVerifyRegistrationAccountEmail: true,
          autoLoginAfterRegistration: true,
          redirectUriAfterRegistration: 'https://acustom/verify/redirect',
        },
      },
      identityProviders: [{ identity: defaultIdp.id, priority: -1 }],
    });

    // Start domain after all resources are created — initial sync picks up everything
    await startDomain(domain.id, accessToken);
    const domainWithOidc = await waitForDomainStart(domain);

    return {
      domain,
      accessToken,
      oidc: domainWithOidc.oidcConfig,
      defaultIdp,
      appVerifyOnly,
      appVerifyAutoLogin,
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
        console.error('Failed to cleanup after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};

const createApp = async (domainId: string, accessToken: string, settings: any): Promise<Application> => {
  const appName = uniqueName('verify-app', true);
  const app = await createApplication(domainId, accessToken, {
    name: appName,
    type: 'WEB',
    clientId: appName,
    clientSecret: appName,
    redirectUris: ['https://callback'],
  });
  const updatedApp = await updateApplication(domainId, accessToken, settings, app.id);
  updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
  return updatedApp;
};

export const register = async (domain: Domain, user: User, clientId: string): Promise<{ locationHeader: string; cookies: string[] }> => {
  const uri = `/${domain.hrid}/register?client_id=${clientId}`;
  const { headers, token: xsrfToken } = await extractXsrfToken(process.env.AM_GATEWAY_URL, uri);
  const postResponse = await performFormPost(
    process.env.AM_GATEWAY_URL,
    uri,
    {
      'X-XSRF-TOKEN': xsrfToken,
      firstName: user.firstName,
      lastName: user.lastName,
      username: user.username,
      email: user.email,
      password: user.password,
      client_id: clientId,
    },
    {
      Cookie: headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);
  return {
    locationHeader: postResponse.headers['location'],
    cookies: postResponse.headers['set-cookie'],
  };
};
