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
import { setup } from '../../test-fixture';
import { ApplicationGeneralSettingsFixture, initFixture } from './fixtures/application-general-settings-fixture';

setup();

let fixture: ApplicationGeneralSettingsFixture;

beforeAll(async () => {
  fixture = await initFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Application General Settings', () => {
  describe('Redirect URIs', () => {
    it('should add a redirect URI', async () => {
      const patched = await fixture.patchApp({
        settings: { oauth: { redirectUris: ['https://callback.example.com'] } },
      });
      expect(patched.settings.oauth.redirectUris).toContain('https://callback.example.com');
    });

    it('should add multiple redirect URIs', async () => {
      const patched = await fixture.patchApp({
        settings: { oauth: { redirectUris: ['https://callback.example.com', 'https://callback2.example.com'] } },
      });
      expect(patched.settings.oauth.redirectUris).toEqual(
        expect.arrayContaining(['https://callback.example.com', 'https://callback2.example.com']),
      );
    });

    it('should remove a redirect URI by updating the list', async () => {
      const patched = await fixture.patchApp({
        settings: { oauth: { redirectUris: ['https://callback.example.com'] } },
      });
      expect(patched.settings.oauth.redirectUris).toEqual(['https://callback.example.com']);
      expect(patched.settings.oauth.redirectUris).not.toContain('https://callback2.example.com');
    });

    it('should persist redirect URIs across GET', async () => {
      await fixture.patchApp({ settings: { oauth: { redirectUris: ['https://persist.example.com'] } } });
      const fetched = await fixture.fetchApp();
      expect(fetched.settings.oauth.redirectUris).toContain('https://persist.example.com');
    });
  });

  describe('Post logout redirect URIs', () => {
    it('should add a post logout redirect URI', async () => {
      const patched = await fixture.patchApp({
        settings: { oauth: { postLogoutRedirectUris: ['https://logout.example.com'] } },
      });
      expect(patched.settings.oauth.postLogoutRedirectUris).toContain('https://logout.example.com');
    });

    it('should remove a post logout redirect URI by updating the list', async () => {
      const patched = await fixture.patchApp({
        settings: { oauth: { postLogoutRedirectUris: [] } },
      });
      expect(patched.settings.oauth.postLogoutRedirectUris).toEqual([]);
    });

    it('should persist post logout redirect URIs across GET', async () => {
      await fixture.patchApp({ settings: { oauth: { postLogoutRedirectUris: ['https://logout-persist.example.com'] } } });
      const fetched = await fixture.fetchApp();
      expect(fetched.settings.oauth.postLogoutRedirectUris).toContain('https://logout-persist.example.com');
    });
  });

  describe('Request URIs', () => {
    it('should add a request URI', async () => {
      const patched = await fixture.patchApp({
        settings: { oauth: { requestUris: ['https://request.example.com'] } },
      });
      expect(patched.settings.oauth.requestUris).toContain('https://request.example.com');
    });

    it('should add multiple request URIs', async () => {
      const patched = await fixture.patchApp({
        settings: { oauth: { requestUris: ['https://request.example.com', 'https://request2.example.com'] } },
      });
      expect(patched.settings.oauth.requestUris).toEqual(
        expect.arrayContaining(['https://request.example.com', 'https://request2.example.com']),
      );
    });

    it('should remove a request URI by updating the list', async () => {
      const patched = await fixture.patchApp({
        settings: { oauth: { requestUris: ['https://request.example.com'] } },
      });
      expect(patched.settings.oauth.requestUris).toEqual(['https://request.example.com']);
      expect(patched.settings.oauth.requestUris).not.toContain('https://request2.example.com');
    });

    it('should persist request URIs across GET', async () => {
      await fixture.patchApp({ settings: { oauth: { requestUris: ['https://persist-request.example.com'] } } });
      const fetched = await fixture.fetchApp();
      expect(fetched.settings.oauth.requestUris).toContain('https://persist-request.example.com');
    });
  });

  describe('Single Sign Out toggle', () => {
    it('should enable single sign out', async () => {
      const patched = await fixture.patchApp({ settings: { oauth: { singleSignOut: true } } });
      expect(patched.settings.oauth.singleSignOut).toBe(true);
    });

    it('should disable single sign out', async () => {
      const patched = await fixture.patchApp({ settings: { oauth: { singleSignOut: false } } });
      expect(patched.settings.oauth.singleSignOut).toBe(false);
    });

    it('should persist single sign out setting across GET', async () => {
      await fixture.patchApp({ settings: { oauth: { singleSignOut: true } } });
      const fetched = await fixture.fetchApp();
      expect(fetched.settings.oauth.singleSignOut).toBe(true);
    });
  });

  describe('Silent re-authentication toggle', () => {
    it('should enable silent re-authentication', async () => {
      const patched = await fixture.patchApp({ settings: { oauth: { silentReAuthentication: true } } });
      expect(patched.settings.oauth.silentReAuthentication).toBe(true);
    });

    it('should disable silent re-authentication', async () => {
      const patched = await fixture.patchApp({ settings: { oauth: { silentReAuthentication: false } } });
      expect(patched.settings.oauth.silentReAuthentication).toBe(false);
    });
  });

  describe('Skip user consent toggle', () => {
    it('should enable skip consent', async () => {
      const patched = await fixture.patchApp({ settings: { advanced: { skipConsent: true } } });
      expect(patched.settings.advanced.skipConsent).toBe(true);
    });

    it('should disable skip consent', async () => {
      const patched = await fixture.patchApp({ settings: { advanced: { skipConsent: false } } });
      expect(patched.settings.advanced.skipConsent).toBe(false);
    });
  });

  describe('Template toggle', () => {
    it('should set application as DCR template', async () => {
      const patched = await fixture.patchApp({ template: true });
      expect(patched.template).toBe(true);
    });

    it('should unset DCR template flag', async () => {
      const patched = await fixture.patchApp({ template: false });
      expect(patched.template).toBe(false);
    });
  });

  describe('Application type and OAuth2 parameter differences', () => {
    it('should switch from WEB to SERVICE (Backend to Backend) and set client_credentials grant type', async () => {
      const updated = await fixture.setAppType('SERVICE');
      expect(updated.settings.oauth.grantTypes).toEqual(['client_credentials']);
      expect(updated.settings.oauth.responseTypes).toEqual([]);
    });

    it('should accept empty redirect URIs when type is SERVICE', async () => {
      const patched = await fixture.patchApp({
        settings: { oauth: { redirectUris: [], grantTypes: ['client_credentials'], responseTypes: [] } },
      });
      expect(patched.settings.oauth.redirectUris).toEqual([]);
      expect(patched.settings.oauth.grantTypes).toEqual(['client_credentials']);
    });

    it('should switch from SERVICE back to WEB and set authorization_code grant type', async () => {
      const updated = await fixture.setAppType('WEB');
      expect(updated.settings.oauth.grantTypes).toEqual(expect.arrayContaining(['authorization_code']));
      expect(updated.settings.oauth.applicationType).toEqual('web');
    });

    it('should require redirect URIs when configuring WEB type with authorization_code', async () => {
      const patched = await fixture.patchApp({
        settings: { oauth: { redirectUris: ['https://callback.example.com'], grantTypes: ['authorization_code'] } },
      });
      expect(patched.settings.oauth.redirectUris).toContain('https://callback.example.com');
      expect(patched.settings.oauth.grantTypes).toEqual(['authorization_code']);
    });

    it('should reject authorization_code grant with empty redirect URIs for WEB type', async () => {
      await expect(
        fixture.patchApp({
          settings: { oauth: { redirectUris: [], grantTypes: ['authorization_code'] } },
        }),
      ).rejects.toMatchObject({ response: { status: 400 } });
    });
  });
});
