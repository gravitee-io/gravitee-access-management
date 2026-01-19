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

import { expect } from '@jest/globals';
import { logoutUser } from '@gateway-commands/oauth-oidc-commands';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { initEnv, RememberMeFixture } from './fixture/remember-me-fixture';
import { setup } from '../../test-fixture';

let fixture: RememberMeFixture;

setup(200000);

describe('remember me', () => {
  describe('max-Age cookie value', () => {
    it('no max-age present if the cookie is not persisted', async () => {
      fixture = await initEnv({
        settings: {
          cookieSettings: {
            inherited: false,
            session: {
              persistent: false,
            },
          },
        },
      });

      const tokenResponse = await loginUserNameAndPassword(
        fixture.app.settings.oauth.clientId,
        fixture.user,
        fixture.userPassword,
        false,
        fixture.openIdConfiguration,
        fixture.domain,
      );
      expect(tokenResponse.headers['set-cookie'][0]).not.toContain('Max-Age');
      await logoutUser(fixture.openIdConfiguration.end_session_endpoint, tokenResponse);
    });

    it('max-age present with default value if the cookie is persisted', async () => {
      fixture = await initEnv({
        settings: {
          cookieSettings: {
            inherited: false,
            session: {
              persistent: true,
            },
          },
        },
      });

      const tokenResponse = await loginUserNameAndPassword(
        fixture.app.settings.oauth.clientId,
        fixture.user,
        fixture.userPassword,
        false,
        fixture.openIdConfiguration,
        fixture.domain,
      );
      await validateMaxAgeCookie('GRAVITEE_IO_AM_SESSION', '1800', tokenResponse);
      await logoutUser(fixture.openIdConfiguration.end_session_endpoint, tokenResponse);
    });

    it('max-age use the "remember me" duration when defined at domain level and user wants to be remembered', async () => {
      fixture = await initEnv(
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

      const tokenResponse = await loginUserNameAndPassword(
        fixture.app.settings.oauth.clientId,
        fixture.user,
        fixture.userPassword,
        false,
        fixture.openIdConfiguration,
        fixture.domain,
      );
      await validateMaxAgeCookie('GRAVITEE_IO_AM_SESSION', '1800', tokenResponse);
      await logoutUser(fixture.openIdConfiguration.end_session_endpoint, tokenResponse);
    });

    it('max-age use the "remember me" duration when defined at application level and user wants to be remembered', async () => {
      fixture = await initEnv({
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

      const tokenResponse = await loginUserNameAndPassword(
        fixture.app.settings.oauth.clientId,
        fixture.user,
        fixture.userPassword,
        false,
        fixture.openIdConfiguration,
        fixture.domain,
      );
      await validateMaxAgeCookie('GRAVITEE_IO_AM_SESSION', '1800', tokenResponse);
      await logoutUser(fixture.openIdConfiguration.end_session_endpoint, tokenResponse);
    });

    afterEach(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });
  });

  describe('remember me cookie max age', () => {
    it('user wants to be remembered and "remember me" is activated at domain level', async () => {
      fixture = await initEnv(
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

      const tokenResponse = await loginUserNameAndPassword(
        fixture.app.settings.oauth.clientId,
        fixture.user,
        fixture.userPassword,
        true,
        fixture.openIdConfiguration,
        fixture.domain,
      );
      await validateMaxAgeCookie('GRAVITEE_IO_REMEMBER_ME', '604800', tokenResponse);
      const logoutResponse = await logoutUser(fixture.openIdConfiguration.end_session_endpoint, tokenResponse);
      await validateCookieNotExists('GRAVITEE_IO_REMEMBER_ME', logoutResponse);
    });

    it('user wants to be remembered and "remember me" is activated at application level', async () => {
      fixture = await initEnv(
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

      const loginResponse = await loginUserNameAndPassword(
        fixture.app.settings.oauth.clientId,
        fixture.user,
        fixture.userPassword,
        true,
        fixture.openIdConfiguration,
        fixture.domain,
      );
      await validateMaxAgeCookie('GRAVITEE_IO_REMEMBER_ME', '45567', loginResponse);
      const logoutResponse = await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
      await validateCookieNotExists('GRAVITEE_IO_REMEMBER_ME', logoutResponse);
    });

    it('user not wants to be remembered and "remember me" is activated at domain level', async () => {
      fixture = await initEnv(
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

      const loginResponse = await loginUserNameAndPassword(
        fixture.app.settings.oauth.clientId,
        fixture.user,
        fixture.userPassword,
        false,
        fixture.openIdConfiguration,
        fixture.domain,
      );
      await validateCookieNotExists('GRAVITEE_IO_REMEMBER_ME', loginResponse);
      await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
    });

    it('user not wants to be remembered and "remember me" is activated at application level', async () => {
      fixture = await initEnv(
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

      const loginResponse = await loginUserNameAndPassword(
        fixture.app.settings.oauth.clientId,
        fixture.user,
        fixture.userPassword,
        false,
        fixture.openIdConfiguration,
        fixture.domain,
      );
      await validateCookieNotExists('GRAVITEE_IO_REMEMBER_ME', loginResponse);
      await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
    });

    afterEach(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });
  });
});

async function validateCookieNotExists(cookieName: string, tokenResponse: any) {
  expect(tokenResponse.request.header.Cookie.filter((cookie) => cookie.includes(cookieName))).toHaveLength(0);
}

async function validateMaxAgeCookie(cookieName: string, maxAgeExpected: string, tokenResponse: any) {
  expect(tokenResponse.request.header.Cookie.filter((cookie) => cookie.includes(cookieName))[0]).toContain(`Max-Age=${maxAgeExpected}`);
}
