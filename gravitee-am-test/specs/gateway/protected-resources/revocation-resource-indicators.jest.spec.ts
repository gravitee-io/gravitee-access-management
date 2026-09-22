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
import { setup } from '../../test-fixture';
import { requestClientCredentialsToken } from '@gateway-commands/oauth-oidc-commands';
import { JWT_FORMAT } from '@specs-utils/jwt-format';
import { waitPastOfflineVerification } from '../revocation/fixtures/revocation-fixture';
import { ProtectedResourcesFixture, setupProtectedResourcesFixture } from './fixtures/protected-resources-fixture';

// RFC 8707 + RFC 7009: revocation of resource-bound tokens

setup(200000);

const PHOTOS_RESOURCE = 'https://api.example.com/photos';

let fixture: ProtectedResourcesFixture;

beforeAll(async () => {
  fixture = await setupProtectedResourcesFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const serviceApplicationCredentials = () => ({
  clientId: fixture.serviceApplication.settings.oauth.clientId,
  clientSecret: fixture.serviceApplication.settings.oauth.clientSecret,
});

const photosResource = () => fixture.protectedResources[0];

describe('Revocation of resource-bound tokens (RFC 8707 + RFC 7009)', () => {
  it('owning application can revoke its own resource-bound access token', async () => {
    const credentials = serviceApplicationCredentials();
    const accessToken = await requestClientCredentialsToken(
      credentials.clientId,
      credentials.clientSecret,
      fixture.openIdConfiguration,
      undefined,
      [PHOTOS_RESOURCE],
    );
    expect(accessToken).toMatch(JWT_FORMAT);

    await fixture.revokeToken(accessToken, credentials).expect(200);
    await fixture.waitUntilTokenInactive(accessToken, photosResource());
  });

  it('owning application can revoke an access token minted without a resource', async () => {
    const credentials = serviceApplicationCredentials();
    const accessToken = await requestClientCredentialsToken(credentials.clientId, credentials.clientSecret, fixture.openIdConfiguration);
    expect(accessToken).toMatch(JWT_FORMAT);

    await fixture.revokeToken(accessToken, credentials).expect(200);
    await fixture.waitUntilTokenInactive(accessToken, credentials);
  });

  it('protected resource named in aud cannot revoke a token it only receives', async () => {
    const credentials = serviceApplicationCredentials();
    const accessToken = await requestClientCredentialsToken(
      credentials.clientId,
      credentials.clientSecret,
      fixture.openIdConfiguration,
      undefined,
      [PHOTOS_RESOURCE],
    );
    expect(accessToken).toMatch(JWT_FORMAT);

    const revocation = await fixture.revokeToken(accessToken, photosResource()).expect(400);
    expect(revocation.body.error).toBe('invalid_grant');

    await waitPastOfflineVerification(accessToken);
    const introspection = await fixture.introspectToken(accessToken, photosResource()).expect(200);
    expect(introspection.body.active).toBe(true);
  });
});
