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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, startDomain, waitForDomainSync } from '@management-commands/domain-management-commands';
import { getWellKnownOpenIdConfiguration, performPost } from '@gateway-commands/oauth-oidc-commands';
import { createServiceApplication } from './fixtures/rate-limit-fixture';
import { createExtensionGrant } from '@management-commands/extension-grant-commands';
import { updateApplication } from '@management-commands/application-management-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { parseJwt } from '@api-fixtures/jwt';
import { uniqueName } from '@utils-commands/misc';

global.fetch = fetch;

let accessToken;
let domain;
let openIdConfiguration;
let jwtBearerExtGrant;
let basicAuth;
let signingCertificateId;

jest.setTimeout(200000);

export const testCryptData = {
  thirdParty: {
    sshRsa:
      'AAAAB3NzaC1yc2EAAAADAQABAAABAQC7VJTUt9Us8cKjMzEfYyjiWA4R4/M2bS1GB4t7NXp98C3SC6dVMvDuictGeurT8jNbvJZHtCSuYEvuNMoSfm76oqFvAp8Gy0iz5sxjZmSnXyCdPEovGhLa0VzMaQ8s+CLOyS56YyCFGeJZqgtzJ6GR3eqoYSW9b9UMvkBpZODSctWSNGj3P7jRFDO5VoTwCQAWbFnOjDfH5Ulgp2PKSQnSJP3AJLQNFNe7br1XbrhV//eO+t51mIpGSDCUv3E0DDFcWDTH9cXDTTlRZVEiR2BwpZOOkE/Z0/BVnhZYL71oZV34bKfWjQIt6V/isSMahdsAASACp4ZTGtwiVuNd9tyb',
    jwt: 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.Eci61G6w4zh_u9oOCk_v1M_sKcgk0svOmW4ZsL-rt4ojGUH2QY110bQTYNwbEVlowW7phCg7vluX_MCKVwJkxJT6tMk2Ij3Plad96Jf2G2mMsKbxkC-prvjvQkBFYWrYnKWClPBRCyIcG0dVfBvqZ8Mro3t5bX59IKwQ3WZ7AtGBYz5BSiBlrKkp6J1UmP_bFV3eEzIHEFgzRa3pbr4ol4TK6SnAoF88rLr2NhEz9vpdHglUMlOBQiqcZwqrI-Z4XDyDzvnrpujIToiepq9bCimPgVkP54VoZzy-mMSGbthYpLqsL_4MQXaI1Uf_wKFAUuAtzVn4-ebgsKOpvKNzVA',
    jwtPayload: {
      sub: '1234567890',
      name: 'John Doe',
      iat: 1516239022,
    },
  },
};

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  domain = await createDomain(accessToken, uniqueName('ext-grant', true), 'Domain with JWT bearer ext grant');

  const extGrantRequest = {
    type: 'jwtbearer-am-extension-grant',
    grantType: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
    configuration: `{"publicKey":"ssh-rsa ${testCryptData.thirdParty.sshRsa}"}`,
    name: 'bearer',
  };
  jwtBearerExtGrant = await createExtensionGrant(domain.id, accessToken, extGrantRequest);

  const application = await createServiceApplication(domain.id, accessToken, 'app', 'app', 'app').then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            grantTypes: ['client_credentials', `urn:ietf:params:oauth:grant-type:jwt-bearer~${jwtBearerExtGrant.id}`],
          },
        },
      },
      app.id,
    ),
  );
  signingCertificateId = application.certificate;
  basicAuth = getBase64BasicAuth('app', 'app');
  await startDomain(domain.id, accessToken);
  await waitForDomainSync(domain.id, accessToken);

  const result = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);
  openIdConfiguration = result.body;
});

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});

describe('Scenario: Application with extension grant jwt-bearer', () => {
  it('Third party JWT token can be exchanged for new token one if signature match', async () => {
    const response = await performPost(
      openIdConfiguration.token_endpoint,
      `?grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${testCryptData.thirdParty.jwt}`,
      null,
      {
        Authorization: `Basic ${basicAuth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(200);

    const responseJwt = response.body.access_token;
    const newJwt = parseJwt(responseJwt);
    expect(newJwt.header['kid']).toBe(signingCertificateId);
    expect(newJwt.header['typ']).toBe('JWT');

    expect(newJwt.payload['sub']).toBe(testCryptData.thirdParty.jwtPayload.sub);
    expect(newJwt.payload['iat']).toBeGreaterThan(testCryptData.thirdParty.jwtPayload.iat);
  });

  it('Third party JWT token must NOT be exchanged for new token one if signature doesnt match', async () => {
    await performPost(
      openIdConfiguration.token_endpoint,
      `?grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${testCryptData.thirdParty.jwt}aaa`,
      null,
      {
        Authorization: `Basic ${basicAuth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(400);
  });

  it('Exchanged token can be introspected', async () => {
    const response = await performPost(
      openIdConfiguration.token_endpoint,
      `?grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${testCryptData.thirdParty.jwt}`,
      null,
      {
        Authorization: `Basic ${basicAuth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(200);

    const responseJwt = response.body.access_token;

    const introspectResponse = await performPost(openIdConfiguration.introspection_endpoint, `?token=${responseJwt}`, null, {
      Authorization: `Basic ${basicAuth}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    }).expect(200);

    expect(introspectResponse.body['sub']).toBe(testCryptData.thirdParty.jwtPayload.sub);
    expect(introspectResponse.body['domain']).toBe(domain.id);
    expect(introspectResponse.body['active']).toBeTruthy();
  });
});
