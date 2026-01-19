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
import { afterAll, beforeAll, expect } from '@jest/globals';
import { safeDeleteDomain } from '@management-commands/domain-management-commands';
import { initiateLoginFlow, login } from '@gateway-commands/login-commands';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { RedirectUriFixture, setupFixture } from './fixture/redirect-uri-fixture';
import { failingELParam, multiParam, multiParamAndRegular, singleParam } from './fixture/redirect-uri-applications-fixture';
import { setup } from '../../test-fixture';

let fixture: RedirectUriFixture;

setup(200000);

beforeAll(async () => {
  fixture = await setupFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Redirect URI', () => {
  describe('Dynamic parameters', () => {
    it('No EL redirect_uri registered', async () => {
      const authResponse = await initiateLoginFlow(
        fixture.apps.normal.settings.oauth.clientId,
        fixture.oidc,
        fixture.domain,
        'code',
        singleParam.callback1,
      );
      const loginResponse = await login(
        authResponse,
        fixture.user.username,
        fixture.apps.normal.settings.oauth.clientId,
        fixture.user.password,
      );
      const tokenResponse = await performGet(loginResponse.headers['location'], '', {
        Cookie: loginResponse.headers['set-cookie'],
      }).expect(302);
      expect(tokenResponse.headers['location']).toContain('https://callback/?param=test');
      expect(tokenResponse.headers['location']).toContain('code=');
    });

    it('Single dynamic parameter without key', async () => {
      const authResponse = await initiateLoginFlow(
        fixture.apps.singleParam.settings.oauth.clientId,
        fixture.oidc,
        fixture.domain,
        'code',
        singleParam.callback2,
      );
      const loginResponse = await login(
        authResponse,
        fixture.user.username,
        fixture.apps.singleParam.settings.oauth.clientId,
        fixture.user.password,
      );
      const tokenResponse = await performGet(loginResponse.headers['location'], '', {
        Cookie: loginResponse.headers['set-cookie'],
      }).expect(302);
      expect(tokenResponse.headers['location']).toContain('https://callback2/?web');
      expect(tokenResponse.headers['location']).toContain('code=');
    });

    it('Single dynamic parameter with key', async () => {
      const authResponse = await initiateLoginFlow(
        fixture.apps.singleParam.settings.oauth.clientId,
        fixture.oidc,
        fixture.domain,
        'code',
        singleParam.callback1,
      );
      const loginResponse = await login(
        authResponse,
        fixture.user.username,
        fixture.apps.singleParam.settings.oauth.clientId,
        fixture.user.password,
      );
      const tokenResponse = await performGet(loginResponse.headers['location'], '', {
        Cookie: loginResponse.headers['set-cookie'],
      }).expect(302);
      expect(tokenResponse.headers['location']).toContain('https://callback/?param=web');
      expect(tokenResponse.headers['location']).toContain('code=');
    });

    it('Multi EL parameters', async () => {
      const authResponse = await initiateLoginFlow(
        fixture.apps.multiParam.settings.oauth.clientId,
        fixture.oidc,
        fixture.domain,
        'code',
        multiParam.callback1,
      );
      const loginResponse = await login(
        authResponse,
        fixture.user.username,
        fixture.apps.multiParam.settings.oauth.clientId,
        fixture.user.password,
      );
      const tokenResponse = await performGet(loginResponse.headers['location'], '', {
        Cookie: loginResponse.headers['set-cookie'],
      }).expect(302);
      expect(tokenResponse.headers['location']).toContain('https://callback/?param=web&param2=web');
      expect(tokenResponse.headers['location']).toContain('code=');
    });

    it('Multi EL parameters with regular one', async () => {
      const authResponse = await initiateLoginFlow(
        fixture.apps.multiParamAndRegular.settings.oauth.clientId,
        fixture.oidc,
        fixture.domain,
        'code',
        multiParamAndRegular.callback1,
      );
      const loginResponse = await login(
        authResponse,
        fixture.user.username,
        fixture.apps.multiParamAndRegular.settings.oauth.clientId,
        fixture.user.password,
      );
      const tokenResponse = await performGet(loginResponse.headers['location'], '', {
        Cookie: loginResponse.headers['set-cookie'],
      }).expect(302);
      expect(tokenResponse.headers['location']).toContain('https://callback/?param=web&param3=test&param2=web');
      expect(tokenResponse.headers['location']).toContain('code=');
    });

    it('Failing EL evaluation', async () => {
      const authResponse = await initiateLoginFlow(
        fixture.apps.failingELParam.settings.oauth.clientId,
        fixture.oidc,
        fixture.domain,
        'code',
        failingELParam.callback1,
      );
      const loginResponse = await login(
        authResponse,
        fixture.user.username,
        fixture.apps.failingELParam.settings.oauth.clientId,
        fixture.user.password,
      );
      const tokenResponse = await performGet(loginResponse.headers['location'], '', {
        Cookie: loginResponse.headers['set-cookie'],
      }).expect(302);
      expect(tokenResponse.headers['location']).toContain('/oauth/error');
      expect(tokenResponse.headers['location']).toContain('error=query_param_parsing_error');
    });

    it('EL uses nonexisting param - should evaluate to empty string', async () => {
      const authResponse = await initiateLoginFlow(
        fixture.apps.failingELParam.settings.oauth.clientId,
        fixture.oidc,
        fixture.domain,
        'code',
        failingELParam.callback2,
      );
      const loginResponse = await login(
        authResponse,
        fixture.user.username,
        fixture.apps.failingELParam.settings.oauth.clientId,
        fixture.user.password,
      );
      const tokenResponse = await performGet(loginResponse.headers['location'], '', {
        Cookie: loginResponse.headers['set-cookie'],
      }).expect(302);
      expect(tokenResponse.headers['location']).toContain('https://callback2/?param=&param3=test&param2=web');
      expect(tokenResponse.headers['location']).toContain('code=');
    });
  });
});
