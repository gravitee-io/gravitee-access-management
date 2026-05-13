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
import { CIMD_REDIRECT_URI, CimdAuthorizeFixture, setupCimdAuthorizeFixture } from './fixtures/cimd-authorize-fixture';
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';

setup(200000);

let fixture: CimdAuthorizeFixture;

beforeAll(async () => {
  fixture = await setupCimdAuthorizeFixture('ENABLED_BASE');
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('CIMD + PAR - require_pushed_authorization_requests', () => {
  const parScenario = 'par-required';

  it('should reject direct authorization when CIMD metadata declares require_pushed_authorization_requests', async () => {
    const clientId = fixture.buildClientId(parScenario);
    const response = await fixture.authorize(clientId);
    fixture.expectInvalidRequest(response, 'pushed authorization');
  });

  it('should accept a pushed authorization request for a CIMD client and return a request_uri', async () => {
    const clientId = fixture.buildClientId(parScenario);
    const parEndpoint = fixture.openIdConfiguration.pushed_authorization_request_endpoint;
    expect(parEndpoint).toBeDefined();

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: clientId,
      redirect_uri: CIMD_REDIRECT_URI,
      scope: 'openid',
    });

    const response = await performPost(parEndpoint, '', params.toString(), {
      'Content-Type': 'application/x-www-form-urlencoded',
    }).expect(201);

    expect(response.body.request_uri).toBeDefined();
    expect(response.body.request_uri).toMatch(/^urn:ietf:params:oauth:request_uri:/);
    expect(response.body.expires_in).toBeGreaterThan(0);
  });

  it('should complete authorization when request_uri from PAR is used for a CIMD client', async () => {
    const clientId = fixture.buildClientId(parScenario);
    const parEndpoint = fixture.openIdConfiguration.pushed_authorization_request_endpoint;
    const authorizeEndpoint = fixture.openIdConfiguration.authorization_endpoint;

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: clientId,
      redirect_uri: CIMD_REDIRECT_URI,
      scope: 'openid',
    });

    const parResponse = await performPost(parEndpoint, '', params.toString(), {
      'Content-Type': 'application/x-www-form-urlencoded',
    }).expect(201);

    const requestUri = parResponse.body.request_uri;
    const authorizeUrl = `${authorizeEndpoint}?${new URLSearchParams({ client_id: clientId, request_uri: requestUri }).toString()}`;
    const authResponse = await performGet(authorizeUrl).expect(302);
    fixture.expectLoginRedirect(authResponse);
  });
});
