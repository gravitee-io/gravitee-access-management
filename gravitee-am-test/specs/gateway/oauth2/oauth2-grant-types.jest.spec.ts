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
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { Domain } from '@management-models/index';
import { uniqueName } from '@utils-commands/misc';
import { OAuth2Fixture, setupFixture } from './fixture/oauth2-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: OAuth2Fixture;

beforeAll(async () => {
  fixture = await setupFixture({
    withOpenidScope: false,
    type: 'WEB',
    grantTypes: ['authorization_code'],
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('OAuth2 - RFC 6746', () => {
  describe('Invalid Request', () => {
    it('must perform an invalid grant type token request', async () => {
      await performPost(fixture.oidc.token_endpoint, '', 'grant_type=unknown', {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + applicationBase64Token(fixture.application),
      }).expect(400, {
        error: 'unsupported_grant_type',
        error_description: 'Unsupported grant type: unknown',
      });
    });
  });
});

describe('ResponseType token', () => {
  it('must NOT handle token flow with query response_mode', async () => {
    // Prepare Login Flow Parameters
    const clientId = fixture.application.settings.oauth.clientId;
    const params = `?response_type=token&response_mode=query&client_id=${clientId}&redirect_uri=http://localhost:4000/`;

    // Initiate the Login Flow
    const authResponse = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);
    const loginLocation = authResponse.headers['location'];

    expect(loginLocation).toBeDefined();
    // error params should be provided based on the response_mode (here as query param)
    expect(loginLocation).toContain(`http://localhost:4000/?`);
    expect(loginLocation).toContain(`error=unsupported_response_mode`);
  });

  it('must reject token flow with prompt none as user not authenticated', async () => {
    // Prepare Login Flow Parameters
    const clientId = fixture.application.settings.oauth.clientId;
    const params = `?response_type=token&prompt=none&client_id=${clientId}&redirect_uri=http://localhost:4000/`;

    // Initiate the Login Flow
    const authResponse = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);
    const loginLocation = authResponse.headers['location'];

    expect(loginLocation).toBeDefined();
    // error params should be provided based on the default response_mode (here as fragment param)
    expect(loginLocation).toContain(`http://localhost:4000/#`);
    expect(loginLocation).toContain(`error=login_required`);
  });
});

async function createApp1(domain: Domain, accessToken: string, scopeKey: string, idpId: string) {
  const appName = uniqueName('my-client-1', true);
  const appClientId = uniqueName('clientId-test-1', true);
  const application = await createApplication(domain.id, accessToken, {
    name: appName,
    type: 'WEB',
    clientId: appClientId,
    redirectUris: ['http://localhost:4000/'],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['http://localhost:4000/'],
            grantTypes: ['authorization_code', 'password', 'refresh_token'],
            scopeSettings: [
              { scope: scopeKey, defaultScope: true },
              {
                scope: 'openid',
                defaultScope: true,
              },
            ],
          },
        },
        identityProviders: [{ identity: idpId, priority: -1 }],
      },
      app.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  expect(application).toBeDefined();
  return application;
}
