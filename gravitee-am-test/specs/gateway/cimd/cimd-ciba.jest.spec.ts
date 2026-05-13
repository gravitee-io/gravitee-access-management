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
import {
  CimdCibaFixture,
  CIMD_CIBA_CLIENT_ID_JAR,
  CIMD_CIBA_CLIENT_ID_POLL,
  setupCimdCibaFixture,
} from './fixtures/cimd-ciba-fixture';
import { performGet } from '@gateway-commands/oauth-oidc-commands';

setup(300000);

let fixture: CimdCibaFixture;

beforeAll(async () => {
  fixture = await setupCimdCibaFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('CIMD + CIBA — poll flow with device notifier auto-accept (token_endpoint_auth_method: none)', () => {
  it('should issue auth_req_id for a CIMD poll client', async () => {
    const response = await fixture.initiateCiba(CIMD_CIBA_CLIENT_ID_POLL, fixture.user.username);

    expect(response.status).toBe(200);
    expect(response.body.auth_req_id).toBeDefined();
    expect(response.body.expires_in).toBeGreaterThan(0);
    expect(response.body.interval).toEqual(5);
  });

  it('should return access_token and id_token after the device notifier auto-accepts', async () => {
    const initiateResponse = await fixture.initiateCiba(CIMD_CIBA_CLIENT_ID_POLL, fixture.user.username);
    expect(initiateResponse.status).toBe(200);
    const authReqId = initiateResponse.body.auth_req_id;

    const tokenResponse = await fixture.pollTokenUntilGranted(authReqId, CIMD_CIBA_CLIENT_ID_POLL);
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.body.access_token).toBeDefined();
    expect(tokenResponse.body.id_token).toBeDefined();
  });

  it('should return userinfo claims using a CIBA-issued access_token', async () => {
    const initiateResponse = await fixture.initiateCiba(CIMD_CIBA_CLIENT_ID_POLL, fixture.user.username);
    expect(initiateResponse.status).toBe(200);

    const tokenResponse = await fixture.pollTokenUntilGranted(
      initiateResponse.body.auth_req_id,
      CIMD_CIBA_CLIENT_ID_POLL,
    );
    expect(tokenResponse.status).toBe(200);

    const userInfoResponse = await performGet(fixture.oidcConfig.userinfo_endpoint, '', {
      Authorization: `Bearer ${tokenResponse.body.access_token}`,
    });
    expect(userInfoResponse.status).toBe(200);
  });
});

describe('CIMD + CIBA — poll flow with signed request object (JAR) and private_key_jwt auth', () => {
  it('should accept bc-authorize with a request JWT whose iss matches the CIMD client_id', async () => {
    const requestJwt = await fixture.createRequestJwt(CIMD_CIBA_CLIENT_ID_JAR, fixture.user.username);

    const response = await fixture.initiateCibaWithPrivateKeyJwt(CIMD_CIBA_CLIENT_ID_JAR, fixture.user.username, requestJwt);

    expect(response.status).toBe(200);
    expect(response.body.auth_req_id).toBeDefined();
    expect(response.body.expires_in).toBeGreaterThan(0);
    expect(response.body.interval).toEqual(5);
  });

  it('should complete the full poll cycle for a JAR-initiated request', async () => {
    const requestJwt = await fixture.createRequestJwt(CIMD_CIBA_CLIENT_ID_JAR, fixture.user.username);
    const initiateResponse = await fixture.initiateCibaWithPrivateKeyJwt(CIMD_CIBA_CLIENT_ID_JAR, fixture.user.username, requestJwt);
    expect(initiateResponse.status).toBe(200);

    const tokenResponse = await fixture.pollTokenWithPrivateKeyJwtUntilGranted(
      initiateResponse.body.auth_req_id,
      CIMD_CIBA_CLIENT_ID_JAR,
    );
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.body.access_token).toBeDefined();
    expect(tokenResponse.body.id_token).toBeDefined();
  });

  it('should reject a request JWT with an iss that does not match the CIMD client_id', async () => {
    const wrongClientId = 'http://wrong-client.example.com';
    const requestJwt = await fixture.createRequestJwt(wrongClientId, fixture.user.username);

    const response = await fixture.initiateCibaWithPrivateKeyJwt(CIMD_CIBA_CLIENT_ID_JAR, fixture.user.username, requestJwt);

    expect(response.status).toBe(400);
    expect(response.body.error).toBe('invalid_request');
    expect(response.body.error_description).toContain('iss');
  });
});
