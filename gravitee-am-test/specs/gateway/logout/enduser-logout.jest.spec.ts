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
import { setup } from '../../test-fixture';

import { afterAll, beforeAll, expect } from '@jest/globals';
import { patchDomain } from '@management-commands/domain-management-commands';
import { patchApplication } from '@management-commands/application-management-commands';
import { logoutUser } from '@gateway-commands/oauth-oidc-commands';
import { syncApplication } from '@gateway-commands/application-sync-commands';
import { waitForNextSync } from '@gateway-commands/monitoring-commands';
import { EnduserLogoutFixture, setupFixture } from './fixture/enduser-logout-fixture';

let fixture: EnduserLogoutFixture;

setup(200000);

beforeAll(async () => {
  fixture = await setupFixture();
  expect(fixture.openIdConfiguration).toBeDefined();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('OAuth2 - Logout tests', () => {
  describe('Default settings - target_uri is not restricted', () => {
    it('After sign-in a user can logout without target_uri', async () => {
      const postLoginRedirect = await fixture.signInUser();
      const response = await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect);
      expect(response.headers['location']).toEqual('/');
    });

    it('After sign-in a user can logout with any target_uri', async () => {
      const postLoginRedirect = await fixture.signInUser();
      const response = await logoutUser(
        fixture.openIdConfiguration.end_session_endpoint,
        postLoginRedirect,
        'https://somewhere/after/logout',
      );
      expect(response.headers['location']).toEqual('https://somewhere/after/logout');
    });
  });

  describe('Domain Settings - target_uri is restricted', () => {
    it('Update domain settings', async () => {
      await patchDomain(fixture.domain.id, fixture.accessToken, {
        oidc: {
          postLogoutRedirectUris: ['https://somewhere/after/logout'],
        },
      });
      await waitForNextSync(fixture.domain.id);
    });

    it('After sign-in a user can logout without target_uri', async () => {
      const postLoginRedirect = await fixture.signInUser();
      const response = await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect);
      expect(response.headers['location']).toEqual('/');
    });

    it('After sign-in a user can NOT logout with an unknown target_uri', async () => {
      const postLoginRedirect = await fixture.signInUser();
      const response = await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect, 'https://someunkown/location');
      expect(response.headers['location']).toContain('/error');
      expect(response.headers['location']).toContain('error=invalid_request');
    });

    it('After sign-in a user can logout with a declared target_uri', async () => {
      const postLoginRedirect = await fixture.signInUser();
      const response = await logoutUser(
        fixture.openIdConfiguration.end_session_endpoint,
        postLoginRedirect,
        'https://somewhere/after/logout',
      );
      expect(response.headers['location']).toEqual('https://somewhere/after/logout');
    });
  });

  describe('Application Settings - target_uri is restricted', () => {
    it('Update application settings', async () => {
      const patch = patchApplication(
        fixture.domain.id,
        fixture.accessToken,
        {
          settings: {
            oauth: {
              postLogoutRedirectUris: ['https://somewhere/after/app/logout'],
            },
          },
        },
        fixture.application.id,
      );
      await syncApplication(fixture.domain.id, fixture.application.id, patch);
    });

    it('After sign-in a user can logout without target_uri', async () => {
      const postLoginRedirect = await fixture.signInUser();
      const response = await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect);
      expect(response.headers['location']).toEqual('/');
    });

    it('After sign-in a user can NOT logout with an unknown target_uri', async () => {
      const postLoginRedirect = await fixture.signInUser();
      const response = await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect, 'https://someunkown/location');
      expect(response.headers['location']).toContain('/error');
      expect(response.headers['location']).toContain('error=invalid_request');
    });

    it('After sign-in a user can NOT logout with a target_uri only declared in at domain level', async () => {
      const postLoginRedirect = await fixture.signInUser();
      const response = await logoutUser(
        fixture.openIdConfiguration.end_session_endpoint,
        postLoginRedirect,
        'https://somewhere/after/logout',
      );
      expect(response.headers['location']).toContain('/error');
      expect(response.headers['location']).toContain('error=invalid_request');
    });

    it('After sign-in a user can logout with a target_uri declared at app level', async () => {
      const postLoginRedirect = await fixture.signInUser();
      const response = await logoutUser(
        fixture.openIdConfiguration.end_session_endpoint,
        postLoginRedirect,
        'https://somewhere/after/app/logout',
      );
      expect(response.headers['location']).toEqual('https://somewhere/after/app/logout');
    });
  });
});
