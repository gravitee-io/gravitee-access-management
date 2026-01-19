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
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { parseJwt } from '@api-fixtures/jwt';
import { JwtBearerFixture, setupFixture, testCryptData } from './fixture/jwt-bearer-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: JwtBearerFixture;

beforeAll(async () => {
  fixture = await setupFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Scenario: Application with extension grant jwt-bearer', () => {
  it('Third party JWT token can be exchanged for new token one if signature match', async () => {
    const response = await performPost(
      fixture.oidc.token_endpoint,
      `?grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${testCryptData.thirdParty.jwt}`,
      null,
      {
        Authorization: `Basic ${fixture.basicAuth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(200);

    const responseJwt = response.body.access_token;
    const newJwt = parseJwt(responseJwt);
    expect(newJwt.header['kid']).toBe(fixture.application.certificate);
    expect(newJwt.header['typ']).toBe('JWT');

    expect(newJwt.payload['sub']).toBe(testCryptData.thirdParty.jwtPayload.sub);
    expect(newJwt.payload['iat']).toBeGreaterThan(testCryptData.thirdParty.jwtPayload.iat);
  });

  it('Third party JWT token must NOT be exchanged for new token one if signature doesnt match', async () => {
    await performPost(
      fixture.oidc.token_endpoint,
      `?grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${testCryptData.thirdParty.jwt}aaa`,
      null,
      {
        Authorization: `Basic ${fixture.basicAuth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(400);
  });

  it('Exchanged token can be introspected', async () => {
    const response = await performPost(
      fixture.oidc.token_endpoint,
      `?grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${testCryptData.thirdParty.jwt}`,
      null,
      {
        Authorization: `Basic ${fixture.basicAuth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(200);

    const responseJwt = response.body.access_token;

    const introspectResponse = await performPost(fixture.oidc.introspection_endpoint, `?token=${responseJwt}`, null, {
      Authorization: `Basic ${fixture.basicAuth}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    }).expect(200);

    expect(introspectResponse.body['sub']).toBe(testCryptData.thirdParty.jwtPayload.sub);
    expect(introspectResponse.body['domain']).toBe(fixture.domain.id);
    expect(introspectResponse.body['active']).toBeTruthy();
  });
});
