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
import { Application } from '@management-models/Application';
import { ResponseError } from '../../../api/management/runtime';
import { setup } from '../../test-fixture';
import { ApplicationTypeFixture, initFixture } from './fixtures/application-type-fixture';
import { uniqueName } from '@utils-commands/misc';

setup(200000);

let fixture: ApplicationTypeFixture;

beforeAll(async () => {
  fixture = await initFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Application Type Management', () => {
  describe('OAuth settings validation', () => {
    let app: Application;

    beforeAll(async () => {
      app = await fixture.createApp(uniqueName('invalid-svc', true), 'SERVICE');
      await fixture.setAppType(app.id, 'WEB');
    });

    it('should reject implicit grant with empty redirect_uris', async () => {
      const error = await fixture
        .updateApp(app.id, {
          settings: {
            oauth: {
              redirectUris: [],
              grantTypes: ['implicit'],
              responseTypes: ['token'],
            },
          },
        })
        .then(
          () => null,
          (e) => e,
        );

      expect(error).toBeInstanceOf(ResponseError);
      expect(error.response.status).toBe(400);
      expect(error.message).toContain('redirect_uris.');
    });

    it('should reject refresh_token without authorization_code or password', async () => {
      const error = await fixture
        .updateApp(app.id, {
          settings: {
            oauth: {
              redirectUris: ['https://callback'],
              grantTypes: ['refresh_token'],
              responseTypes: [''],
            },
          },
        })
        .then(
          () => null,
          (e) => e,
        );

      expect(error).toBeInstanceOf(ResponseError);
      expect(error.response.status).toBe(400);
      expect(error.message).toContain('refresh_token grant type must be associated with one of');
    });

    it('should reject refresh_token with client_credentials only', async () => {
      const error = await fixture
        .updateApp(app.id, {
          settings: {
            oauth: {
              redirectUris: ['https://callback'],
              grantTypes: ['refresh_token', 'client_credentials'],
              responseTypes: [''],
            },
          },
        })
        .then(
          () => null,
          (e) => e,
        );

      expect(error).toBeInstanceOf(ResponseError);
      expect(error.response.status).toBe(400);
      expect(error.message).toContain('refresh_token grant type must be associated with one of');
    });
  });

  describe('Type defaults and configuration', () => {
    let app: Application;

    it('should create a SERVICE app with correct default values', async () => {
      app = await fixture.createApp(uniqueName('nominal-svc', true), 'SERVICE', { key: 'value' });

      expect(app).toBeDefined();
      expect(app.metadata).toBeDefined();
      expect(app.settings).toBeDefined();
      expect(app.settings.oauth).toBeDefined();
      expect(app.settings.oauth.grantTypes).toEqual(['client_credentials']);
      expect(app.settings.oauth.responseTypes).toEqual([]);
      expect(app.settings.oauth.applicationType).toEqual('web');
      expect(app.settings.oauth.clientName).toBeDefined();
      expect(app.settings.oauth.tokenEndpointAuthMethod).toEqual('client_secret_basic');
      expect(app.settings.oauth.requireAuthTime).toEqual(false);
      expect(app.settings.oauth.accessTokenValiditySeconds).toEqual(7200);
      expect(app.settings.oauth.refreshTokenValiditySeconds).toEqual(14400);
      expect(app.settings.oauth.idTokenValiditySeconds).toEqual(14400);
      expect(app.domain).toEqual(fixture.domain.id);
      expect(app.enabled).toEqual(true);
      expect(app.settings.oauth.enhanceScopesWithUserPermissions).toEqual(false);
    });

    it('should set type to WEB and validate defaults', async () => {
      const updated = await fixture.setAppType(app.id, 'WEB');

      expect(updated.settings.oauth.grantTypes).toEqual(['authorization_code', 'password']);
      expect(updated.settings.oauth.responseTypes).toEqual(['code', 'code id_token token', 'code id_token', 'code token']);
      expect(updated.settings.oauth.applicationType).toEqual('web');
    });

    it('should configure WEB app with authorization_code', async () => {
      const updated = await fixture.updateApp(app.id, {
        settings: {
          oauth: {
            redirectUris: ['https://callback'],
            grantTypes: ['authorization_code'],
            scopeSettings: [{ scope: 'openid' }],
          },
        },
      });

      expect(updated.settings.oauth.redirectUris).toEqual(['https://callback']);
      expect(updated.settings.oauth.grantTypes).toEqual(['authorization_code']);
      expect(updated.settings.oauth.responseTypes).toEqual(['code', 'code id_token token', 'code id_token', 'code token']);
      expect(updated.settings.oauth.applicationType).toEqual('web');
    });

    it('should set type to BROWSER (SPA) and validate defaults', async () => {
      const updated = await fixture.setAppType(app.id, 'BROWSER');

      expect(updated.settings.oauth.grantTypes).toEqual(['authorization_code']);
      expect(updated.settings.oauth.responseTypes).toEqual(['code', 'code id_token token', 'code id_token', 'code token']);
      expect(updated.settings.oauth.applicationType).toEqual('web');
    });

    it('should configure SPA with implicit grant', async () => {
      const updated = await fixture.updateApp(app.id, {
        settings: {
          oauth: {
            redirectUris: ['https://callback'],
            grantTypes: ['implicit'],
            responseTypes: ['token'],
            scopeSettings: [{ scope: 'openid' }],
          },
        },
      });

      expect(updated.settings.oauth.grantTypes).toEqual(['implicit']);
      expect(updated.settings.oauth.responseTypes).toEqual(['token']);
    });

    it('should set type to NATIVE (mobile) and validate defaults', async () => {
      const updated = await fixture.setAppType(app.id, 'NATIVE');

      expect(updated.settings.oauth.grantTypes).toEqual(['authorization_code']);
      expect(updated.settings.oauth.responseTypes).toEqual(['code', 'code id_token token', 'code id_token', 'code token']);
      expect(updated.settings.oauth.applicationType).toEqual('native');
    });

    it('should configure mobile app with authorization_code + refresh_token', async () => {
      const updated = await fixture.updateApp(app.id, {
        settings: {
          oauth: {
            redirectUris: ['com.gravitee.app://callback'],
            grantTypes: ['authorization_code', 'refresh_token'],
            applicationType: 'native',
            scopeSettings: [{ scope: 'openid' }],
          },
        },
      });

      expect(updated.settings.oauth.redirectUris).toEqual(['com.gravitee.app://callback']);
      expect(updated.settings.oauth.grantTypes).toEqual(['authorization_code', 'refresh_token']);
      expect(updated.settings.oauth.responseTypes).toEqual(['code', 'code id_token token', 'code id_token', 'code token']);
      expect(updated.settings.oauth.applicationType).toEqual('native');
    });

    it('should set type to SERVICE (server) and validate defaults', async () => {
      const updated = await fixture.setAppType(app.id, 'SERVICE');

      expect(updated.settings.oauth.grantTypes).toEqual(['client_credentials']);
      expect(updated.settings.oauth.responseTypes).toEqual([]);
      expect(updated.settings.oauth.applicationType).toEqual('web');
    });

    it('should configure server app with client_credentials', async () => {
      const updated = await fixture.updateApp(app.id, {
        settings: {
          oauth: {
            redirectUris: [],
            grantTypes: ['client_credentials'],
            responseTypes: [],
            applicationType: 'web',
            scopeSettings: [{ scope: 'openid' }],
          },
        },
      });

      expect(updated.settings.oauth.redirectUris).toEqual([]);
      expect(updated.settings.oauth.grantTypes).toEqual(['client_credentials']);
      expect(updated.settings.oauth.responseTypes).toEqual([]);
      expect(updated.settings.oauth.applicationType).toEqual('web');
    });

    it('should set template flag to true', async () => {
      const updated = await fixture.updateApp(app.id, { template: true });

      expect(updated.template).toEqual(true);
    });
  });
});
