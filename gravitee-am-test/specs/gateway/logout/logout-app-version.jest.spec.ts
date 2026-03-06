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
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { setup } from '../../test-fixture';
import {
  LogoutAppVersionFixture,
  setupLogoutAppVersionFixture,
} from './fixture/logout-app-version-fixture';

setup(240000);

let fixture: LogoutAppVersionFixture;

beforeAll(async () => {
  fixture = await setupLogoutAppVersionFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const waitForOfflineVerificationExpiry = () => new Promise((resolve) => setTimeout(resolve, 1_000));

describe('Logout - app version collection parity', () => {
  describe('Get Access Token', () => {
    it('should keep implicit access token active after GET logout', async () => {
      const flow = await fixture.runImplicitFlow({ responseType: 'token' });

      expect(flow.authorizeResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/login`);
      expect(flow.authorizeResponse.headers['location']).toContain(`client_id=${fixture.application.settings.oauth.clientId}`);

      expect(flow.finalLocation).toContain(`${fixture.redirectUri}#`);
      expect(flow.fragmentParams.get('access_token')).toBeDefined();
      expect(flow.fragmentParams.get('token_type')).toBe('bearer');
      expect(flow.fragmentParams.get('expires_in')).toBeDefined();

      const firstIntrospection = await fixture.introspectAccessToken(flow.accessToken!);
      expect(firstIntrospection.active).toBe(true);
      expect(firstIntrospection.client_id).toEqual(fixture.application.settings.oauth.clientId);
      expect(firstIntrospection.username).toEqual(fixture.inlineUser.username);
      expect(firstIntrospection.token_type).toEqual('bearer');

      const logoutResponse = await fixture.logoutWithGet(flow.sessionCookie);
      expect(logoutResponse.status).toBe(302);
      expect(logoutResponse.headers['location']).toEqual('/');

      const secondIntrospection = await fixture.introspectAccessToken(flow.accessToken!);
      expect(secondIntrospection.active).toBe(true);
      expect(secondIntrospection.client_id).toEqual(fixture.application.settings.oauth.clientId);
    });
  });

  describe('Get Access Token - invalidate tokens', () => {
    it('should invalidate access token when logout is called with invalidate_tokens', async () => {
      const flow = await fixture.runImplicitFlow({ responseType: 'token' });

      const introspection = await fixture.introspectAccessToken(flow.accessToken!);
      expect(introspection.active).toBe(true);

      await waitForOfflineVerificationExpiry();

      const logoutResponse = await fixture.logoutWithGet(flow.sessionCookie, true);
      expect(logoutResponse.status).toBe(302);
      expect(logoutResponse.headers['location']).toBeDefined();

      const introspectionAfter = await fixture.introspectAccessToken(flow.accessToken!);
      expect(introspectionAfter.active).toBe(false);

      const reAuthResponse = await performGet(
        fixture.openIdConfiguration.authorization_endpoint,
        `?response_type=token&client_id=${fixture.application.settings.oauth.clientId}&redirect_uri=${encodeURIComponent(
          fixture.redirectUri,
        )}`,
      ).expect(302);

      expect(reAuthResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/login`);
      expect(reAuthResponse.headers['location']).toContain(`client_id=${fixture.application.settings.oauth.clientId}`);
    });
  });

  describe('Get Access Token - POST logout', () => {
    it('should end user session when logout is posted with id_token_hint', async () => {
      const flow = await fixture.runImplicitFlow({ responseType: 'id_token' });

      expect(flow.authorizeResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/login`);
      expect(flow.finalLocation).toContain(`${fixture.redirectUri}#`);
      expect(flow.fragmentParams.get('id_token')).toBeDefined();
      expect(flow.idToken).toBeDefined();

      const logoutResponse = await fixture.logoutWithPost(flow.sessionCookie, flow.idToken!);
      expect(logoutResponse.status).toBe(302);
      expect(logoutResponse.headers['location']).toBeDefined();

      const reAuthResponse = await performGet(
        fixture.openIdConfiguration.authorization_endpoint,
        `?response_type=id_token&client_id=${fixture.application.settings.oauth.clientId}&redirect_uri=${encodeURIComponent(
          fixture.redirectUri,
        )}&nonce=logout-check`,
      ).expect(302);

      expect(reAuthResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/login`);
      expect(reAuthResponse.headers['location']).toContain(`client_id=${fixture.application.settings.oauth.clientId}`);
    });
  });
});
