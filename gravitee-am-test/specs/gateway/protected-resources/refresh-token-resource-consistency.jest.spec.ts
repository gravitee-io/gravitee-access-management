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
import { decodeJwt } from '@utils-commands/jwt';
import { validateAudienceClaim } from './fixtures/test-utils';
import { ProtectedResourcesFixture, setupProtectedResourcesFixture } from './fixtures/protected-resources-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: ProtectedResourcesFixture;

beforeAll(async () => {
  fixture = await setupProtectedResourcesFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Refresh Token Flow - Resource Parameter Consistency (RFC 8707)', () => {
  it('should issue access token with subset resources on refresh and preserve orig_resources', async () => {
    const authResources = ['https://api.example.com/photos', 'https://api.example.com/albums'];
    const code = await fixture.completeAuthorizationFlow(authResources);
    const initialToken = await fixture.exchangeAuthCodeForTokenWithoutResources(code).expect(200);
    expect(initialToken.body.refresh_token).toBeDefined();

    // Refresh requesting subset
    const subset = ['https://api.example.com/photos'];
    const refreshResp = await fixture.exchangeRefreshToken(initialToken.body.refresh_token, subset).expect(200);

    // Access token aud should be subset
    validateAudienceClaim(refreshResp.body.access_token, subset);
    const at = decodeJwt(refreshResp.body.access_token);
    const atAud: string[] = Array.isArray(at.aud) ? at.aud : [at.aud];
    expect(atAud).not.toContain('https://api.example.com/albums');

    // Refresh token orig_resources should remain original authorization resources
    const rt = decodeJwt(refreshResp.body.refresh_token);
    expect(Array.isArray(rt.orig_resources)).toBe(true);
    authResources.forEach((r) => expect(rt.orig_resources).toContain(r));
  });

  it('should deduplicate duplicate resources on refresh', async () => {
    const authResources = ['https://api.example.com/photos'];
    const code = await fixture.completeAuthorizationFlow(authResources);
    const initialToken = await fixture.exchangeAuthCodeForTokenWithoutResources(code).expect(200);
    const dup = ['https://api.example.com/photos', 'https://api.example.com/photos'];
    const resp = await fixture.exchangeRefreshToken(initialToken.body.refresh_token, dup).expect(200);
    const at = decodeJwt(resp.body.access_token);
    const aud: string[] = Array.isArray(at.aud) ? at.aud : [at.aud];
    expect(aud.filter((v) => v === 'https://api.example.com/photos').length).toBe(1);
  });

  it('should reject refresh with resources not in original authorization (invalid_target)', async () => {
    const authResources = ['https://api.example.com/photos'];
    const code = await fixture.completeAuthorizationFlow(authResources);
    const initialToken = await fixture.exchangeAuthCodeForTokenWithoutResources(code).expect(200);

    const invalid = ['https://api.example.com/photos', 'https://api.example.com/albums'];
    const resp = await fixture.exchangeRefreshToken(initialToken.body.refresh_token, invalid).expect(400);
    expect(resp.body.error).toBe('invalid_target');
  });

  it('should use original resources when no resource provided on refresh', async () => {
    const authResources = ['https://api.example.com/photos', 'https://api.example.com/albums'];
    const code = await fixture.completeAuthorizationFlow(authResources);
    const initialToken = await fixture.exchangeAuthCodeForTokenWithoutResources(code).expect(200);

    const resp = await fixture.exchangeRefreshToken(initialToken.body.refresh_token).expect(200);
    validateAudienceClaim(resp.body.access_token, authResources);

    const rt = decodeJwt(resp.body.refresh_token);
    expect(Array.isArray(rt.orig_resources)).toBe(true);
    authResources.forEach((r) => expect(rt.orig_resources).toContain(r));
  });
});
