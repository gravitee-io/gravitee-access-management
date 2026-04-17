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
import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import {
  CLIENT_ID_WITH_PLUS_BASIC,
  CLIENT_ID_WITH_PLUS_POST,
  ClientAuthenticationFixture,
  setupClientAuthenticationFixture,
} from './fixtures/client-authentication-fixture';

global.fetch = fetch;

jest.setTimeout(200000);

let fixture: ClientAuthenticationFixture;

beforeAll(async () => {
  fixture = await setupClientAuthenticationFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Gateway client authentication - client_secret_basic with + in client_id (AM-4872)', () => {
  it('should authenticate when credentials are sent with literal + (common non-RFC-strict client behaviour)', async () => {
    const response = await performPost(
      fixture.openIdConfiguration.token_endpoint,
      '',
      'grant_type=client_credentials',
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(CLIENT_ID_WITH_PLUS_BASIC, fixture.basicAppSecret),
      },
    ).expect(200);

    expect(response.body.access_token).toEqual(expect.any(String));
    expect(response.body.token_type).toEqual('bearer');
  });

  it('should authenticate when credentials are form-url-encoded per RFC 6749 §2.3.1', async () => {
    const encodedClientId = encodeURIComponent(CLIENT_ID_WITH_PLUS_BASIC);
    const encodedSecret = encodeURIComponent(fixture.basicAppSecret);

    const response = await performPost(
      fixture.openIdConfiguration.token_endpoint,
      '',
      'grant_type=client_credentials',
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(encodedClientId, encodedSecret),
      },
    ).expect(200);

    expect(response.body.access_token).toEqual(expect.any(String));
    expect(response.body.token_type).toEqual('bearer');
  });
});

describe('Gateway client authentication - client_secret_post with + in client_id (AM-4872)', () => {
  it('should authenticate when client_id is form-url-encoded as %2B', async () => {
    const encodedClientId = encodeURIComponent(CLIENT_ID_WITH_PLUS_POST);
    const encodedSecret = encodeURIComponent(fixture.postAppSecret);

    const response = await performPost(
      fixture.openIdConfiguration.token_endpoint,
      '',
      `grant_type=client_credentials&client_id=${encodedClientId}&client_secret=${encodedSecret}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(200);

    expect(response.body.access_token).toEqual(expect.any(String));
    expect(response.body.token_type).toEqual('bearer');
  });
});
