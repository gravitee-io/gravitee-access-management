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
import { expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, patchDomain, startDomain, waitForDomainStart, waitForDomainSync } from '@management-commands/domain-management-commands';
import { createUser, deleteUser } from '@management-commands/user-management-commands';
import { getWellKnownOpenIdConfiguration, logoutUser } from '@gateway-commands/oauth-oidc-commands';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { createJdbcIdp, createMongoIdp } from '@utils-commands/idps-commands';
import * as faker from 'faker';
import { uniqueName } from '@utils-commands/misc';

global.fetch = fetch;

const jdbc = process.env.REPOSITORY_TYPE;
let accessToken;
let domain;
let customIdp;
let app;
let openIdConfiguration;
let user;

const contractValue = '1234';
const password = 'ZxcPrm7123!!';

jest.setTimeout(200000);

const defaultApplicationConfiguration = {
  settings: {
    oauth: {
      redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
      forcePKCE: false,
      forceS256CodeChallengeMethod: false,
      grantTypes: ['authorization_code'],
      tokenEndpointAuthMethod: 'none',
    },
  },
};

describe('remember me', () => {
  describe('max-Age cookie value', () => {
    it('no max-age present if the cookie is not persisted', async () => {
      await initEnv({
        settings: {
          cookieSettings: {
            inherited: false,
            session: {
              persistent: false,
            },
          },
        },
      });

      const tokenResponse = await loginUserNameAndPassword(app.settings.oauth.clientId, user, password, false, openIdConfiguration, domain);
      expect(tokenResponse.headers['set-cookie'][0]).not.toContain('Max-Age');
      await logoutUser(openIdConfiguration.end_session_endpoint, tokenResponse);
    });

    it('max-age present with default value if the cookie is persisted', async () => {
      await initEnv({
        settings: {
          cookieSettings: {
            inherited: false,
            session: {
              persistent: true,
            },
          },
        },
      });

      const tokenResponse = await loginUserNameAndPassword(app.settings.oauth.clientId, user, password, false, openIdConfiguration, domain);
      await validateMaxAgeCookie('GRAVITEE_IO_AM_SESSION', '1800', tokenResponse);
      await logoutUser(openIdConfiguration.end_session_endpoint, tokenResponse);
    });

    it('max-age use the "remember me" duration when defined at domain level and user wants to be remembered', async () => {
      await initEnv(
        {
          settings: {
            account: {
              inherited: true,
            },
            cookieSettings: {
              inherited: false,
              session: {
                persistent: true,
              },
            },
          },
        },
        {
          accountSettings: {
            rememberMe: true,
            rememberMeDuration: 432000,
          },
        },
      );

      const tokenResponse = await loginUserNameAndPassword(app.settings.oauth.clientId, user, password, false, openIdConfiguration, domain);
      await validateMaxAgeCookie('GRAVITEE_IO_AM_SESSION', '1800', tokenResponse);
      await logoutUser(openIdConfiguration.end_session_endpoint, tokenResponse);
    });

    it('max-age use the "remember me" duration when defined at application level and user wants to be remembered', async () => {
      await initEnv({
        settings: {
          account: {
            inherited: false,
            rememberMe: true,
            rememberMeDuration: 3600,
          },
          cookieSettings: {
            inherited: false,
            session: {
              persistent: true,
            },
          },
        },
      });

      const tokenResponse = await loginUserNameAndPassword(app.settings.oauth.clientId, user, password, false, openIdConfiguration, domain);
      await validateMaxAgeCookie('GRAVITEE_IO_AM_SESSION', '1800', tokenResponse);
      await logoutUser(openIdConfiguration.end_session_endpoint, tokenResponse);
    });

    afterEach(async () => {
      if (user?.id) {
        await deleteUser(domain.id, accessToken, user.id);
      }
      await safeDeleteDomain(domain?.id, accessToken);
    });
  });

  describe('remember me cookie max age', () => {
    it('user wants to be remembered and "remember me" is activated at domain level', async () => {
      await initEnv(
        {
          settings: {
            cookieSettings: {
              inherited: false,
              session: {
                persistent: false,
              },
            },
          },
        },
        {
          accountSettings: {
            rememberMe: true,
            rememberMeDuration: 604800,
          },
        },
      );

      const tokenResponse = await loginUserNameAndPassword(app.settings.oauth.clientId, user, password, true, openIdConfiguration, domain);
      await validateMaxAgeCookie('GRAVITEE_IO_REMEMBER_ME', '604800', tokenResponse);
      const logoutResponse = await logoutUser(openIdConfiguration.end_session_endpoint, tokenResponse);
      await validateCookieNotExists('GRAVITEE_IO_REMEMBER_ME', logoutResponse);
    });

    it('user wants to be remembered and "remember me" is activated at application level', async () => {
      await initEnv(
        {
          settings: {
            account: {
              inherited: false,
              rememberMe: true,
              rememberMeDuration: 45567,
            },
            cookieSettings: {
              inherited: false,
              session: {
                persistent: false,
              },
            },
          },
        },
        {
          accountSettings: {
            rememberMe: true,
            rememberMeDuration: 3600,
          },
        },
      );

      const loginResponse = await loginUserNameAndPassword(app.settings.oauth.clientId, user, password, true, openIdConfiguration, domain);
      await validateMaxAgeCookie('GRAVITEE_IO_REMEMBER_ME', '45567', loginResponse);
      const logoutResponse = await logoutUser(openIdConfiguration.end_session_endpoint, loginResponse);
      await validateCookieNotExists('GRAVITEE_IO_REMEMBER_ME', logoutResponse);
    });

    it('user not wants to be remembered and "remember me" is activated at domain level', async () => {
      await initEnv(
        {
          settings: {
            cookieSettings: {
              inherited: false,
              session: {
                persistent: false,
              },
            },
          },
        },
        {
          accountSettings: {
            rememberMe: true,
            rememberMeDuration: 5,
          },
        },
      );

      const loginResponse = await loginUserNameAndPassword(app.settings.oauth.clientId, user, password, false, openIdConfiguration, domain);
      await validateCookieNotExists('GRAVITEE_IO_REMEMBER_ME', loginResponse);
      await logoutUser(openIdConfiguration.end_session_endpoint, loginResponse);
    });

    it('user not wants to be remembered and "remember me" is activated at application level', async () => {
      await initEnv(
        {
          settings: {
            account: {
              inherited: false,
              rememberMe: true,
              rememberMeDuration: 5,
            },
            cookieSettings: {
              inherited: false,
              session: {
                persistent: false,
              },
            },
          },
        },
        {
          accountSettings: {
            rememberMe: true,
            rememberMeDuration: 3600,
          },
        },
      );

      const loginResponse = await loginUserNameAndPassword(app.settings.oauth.clientId, user, password, false, openIdConfiguration, domain);
      await validateCookieNotExists('GRAVITEE_IO_REMEMBER_ME', loginResponse);
      await logoutUser(openIdConfiguration.end_session_endpoint, loginResponse);
    });

    afterEach(async () => {
      if (user?.id) {
        await deleteUser(domain.id, accessToken, user.id);
      }
      await safeDeleteDomain(domain?.id, accessToken);
    });
  });
});

async function validateCookieNotExists(cookieName: string, tokenResponse: any) {
  expect(tokenResponse.request.header.Cookie.filter((cookie) => cookie.includes(cookieName))).toHaveLength(0);
}

async function validateMaxAgeCookie(cookieName: string, maxAgeExpected: string, tokenResponse: any) {
  expect(tokenResponse.request.header.Cookie.filter((cookie) => cookie.includes(cookieName))[0]).toContain(`Max-Age=${maxAgeExpected}`);
}

async function initEnv(applicationConfiguration, domainConfiguration = null) {
  accessToken = await requestAdminAccessToken();
  domain = await createDomain(accessToken, uniqueName('remember-me', true), 'test remember me option')
    .then((domain) => startDomain(domain.id, accessToken))
    .then((domain) => {
      if (domainConfiguration != null) {
        return patchDomain(domain.id, accessToken, domainConfiguration);
      }

      return domain;
    });

  customIdp = jdbc === 'jdbc' ? await createJdbcIdp(domain.id, accessToken) : await createMongoIdp(domain.id, accessToken);
  app = await createTestApp('remember-me-app', domain, accessToken, 'browser', {
    settings: {
      ...defaultApplicationConfiguration.settings,
      ...applicationConfiguration.settings,
    },
    identityProviders: new Set([{ identity: customIdp.id, priority: 0 }]),
  });

  await waitForDomainSync(domain.id, accessToken);

  // Wait for domain to be ready to serve requests
  await waitForDomainStart(domain).then((started) => {
    domain = started.domain;
    openIdConfiguration = started.oidcConfig;
  });

  const firstname = faker.name.firstName();
  const lastname = faker.name.lastName();
  user = await createUser(domain.id, accessToken, {
    firstName: firstname,
    lastName: lastname,
    email: faker.internet.email(firstname, lastname),
    username: faker.internet.userName(firstname, lastname),
    password: password,
    client: app.id,
    source: customIdp.id,
    additionalInformation: {
      contract: contractValue,
    },
    preRegistration: false,
  });

  expect(user).toBeDefined();
}
