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
import { createCustomIdp } from '@utils-commands/idps-commands';
import { expect } from '@jest/globals';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { extractXsrfToken, performFormPost } from '@gateway-commands/oauth-oidc-commands';
import { decodeJwt } from '@utils-commands/jwt';
import { User } from '@management-models/User';
import { Fixture } from '../../../test-fixture';

export interface UserRegistrationFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  application: Application;
  applicationWithCustomIdp: Application;
  applicationWithCustomIdpAndRedirect: Application;
  /** App with registerEnabled=false */
  applicationRegistrationDisabled: Application;
  /** App with inherited login+account settings (falls back to domain-level) */
  applicationInherited: Application;
  defaultIdp: IdentityProvider;
  customIdp: IdentityProvider;
}

export const setupFixture = async (): Promise<UserRegistrationFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    domain = await createDomain(accessToken, uniqueName('user-registration', true), 'Description');
    const idpSet = await getAllIdps(domain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;
    if (!defaultIdp) throw new Error('No default identity provider found for domain');
    const customIdp = await createCustomIdp(domain.id, accessToken);
    const application = await createApp(domain.id, accessToken, {
      settings: {
        oauth: {
          redirectUris: ['https://callback'],
          grantTypes: ['authorization_code'],
        },
        login: {
          inherited: false,
          registerEnabled: true,
        },
      },
      identityProviders: [
        { identity: defaultIdp.id, priority: -1 },
        { identity: customIdp.id, priority: -1 },
      ],
    });

    const applicationWithCustomIdp = await createApp(domain.id, accessToken, {
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
          defaultIdentityProviderForRegistration: customIdp.id,
        },
      },
      identityProviders: [
        { identity: defaultIdp.id, priority: -1 },
        { identity: customIdp.id, priority: -1 },
      ],
    });

    const applicationWithCustomIdpAndRedirect = await createApp(domain.id, accessToken, {
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
          defaultIdentityProviderForRegistration: customIdp.id,
          autoLoginAfterRegistration: true,
          redirectUriAfterRegistration: 'https://acustom/web/site',
        },
      },
      identityProviders: [
        { identity: defaultIdp.id, priority: -1 },
        { identity: customIdp.id, priority: -1 },
      ],
    });

    const applicationRegistrationDisabled = await createApp(domain.id, accessToken, {
      settings: {
        oauth: {
          redirectUris: ['https://callback'],
          grantTypes: ['authorization_code'],
        },
        login: {
          inherited: false,
          registerEnabled: false,
        },
      },
      identityProviders: [{ identity: defaultIdp.id, priority: -1 }],
    });

    const applicationInherited = await createApp(domain.id, accessToken, {
      settings: {
        oauth: {
          redirectUris: ['https://callback'],
          grantTypes: ['authorization_code'],
        },
        login: {
          inherited: true,
        },
        account: {
          inherited: true,
        },
      },
      identityProviders: [
        { identity: defaultIdp.id, priority: -1 },
        { identity: customIdp.id, priority: -1 },
      ],
    });

    await startDomain(domain.id, accessToken);
    const domainWithOidc = await waitForDomainStart(domain);

    return {
      domain: domain,
      accessToken: accessToken,
      oidc: domainWithOidc.oidcConfig,
      application: application,
      applicationWithCustomIdp: applicationWithCustomIdp,
      applicationWithCustomIdpAndRedirect: applicationWithCustomIdpAndRedirect,
      applicationRegistrationDisabled: applicationRegistrationDisabled,
      applicationInherited: applicationInherited,
      customIdp: customIdp,
      defaultIdp: defaultIdp,
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

export const createApp = async (domainId: string, accessToken: string, settings: Record<string, any>): Promise<Application> => {
  const appName = uniqueName('reg-app', true);
  return await createApplication(domainId, accessToken, {
    name: appName,
    type: 'WEB',
    clientId: appName,
    clientSecret: appName,
    redirectUris: ['https://callback'],
  }).then((app) =>
    updateApplication(domainId, accessToken, settings, app.id).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );
};

export const register = async (domain: Domain, user: User, expected: string, clientId: string, sessionActive = false) => {
  const uri = `/${domain.hrid}/register?client_id=${clientId}`;
  const { headers, token: xsrfToken } = await extractXsrfToken(process.env.AM_GATEWAY_URL, uri);
  //Submit forgot password formf
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
  expect(postResponse.headers['location']).toContain(expected);

  const cookies = postResponse.headers['set-cookie'];
  expect(cookies).toBeDefined();
  const sessionCookies = cookies.filter((entry) => entry.startsWith('GRAVITEE_IO_AM_SESSION'));
  if (sessionActive) {
    expect(sessionCookies.length).toBeGreaterThan(0);
  }
  sessionCookies.forEach((gioSessionCookie) => {
    const jwtSession = gioSessionCookie.substring(gioSessionCookie.indexOf('=') + 1, gioSessionCookie.indexOf(';'));
    const JWT = decodeJwt(jwtSession);
    sessionActive ? expect(JWT.userId).toBeDefined() : expect(JWT.userId).not.toBeDefined();
  });
};
