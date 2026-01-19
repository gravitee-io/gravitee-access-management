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
import { Domain } from '@management-models/Domain';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  DomainOidcConfig,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { createExtensionGrant } from '@management-commands/extension-grant-commands';
import { createServiceApplication } from '../../fixtures/rate-limit-fixture';
import { updateApplication } from '@management-commands/application-management-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { Application } from '@management-models/Application';
import { Fixture } from '../../../test-fixture';

export interface JwtBearerFixture extends Fixture {
  domain: Domain;
  application: Application;
  oidc: DomainOidcConfig;
  basicAuth: string;
}

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

export const setupFixture = async (): Promise<JwtBearerFixture> => {
  const accessToken = await requestAdminAccessToken();

  const domain = await createDomain(accessToken, uniqueName('ext-grant', true), 'Description');

  const extGrantRequest = {
    type: 'jwtbearer-am-extension-grant',
    grantType: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
    configuration: `{"publicKey":"ssh-rsa ${testCryptData.thirdParty.sshRsa}"}`,
    name: 'bearer',
  };
  const jwtBearerExtGrant = await createExtensionGrant(domain.id, accessToken, extGrantRequest);

  const clientId = 'app';
  const clientSecret = 'app';
  const application = await createServiceApplication(domain.id, accessToken, 'app', clientId, clientSecret).then((app) =>
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
  const basicAuth = getBase64BasicAuth(clientId, clientSecret);
  const startedDomain = await startDomain(domain.id, accessToken).then((domain) => waitForDomainStart(domain));

  return {
    domain: domain,
    application: application,
    oidc: startedDomain.oidcConfig,
    accessToken: accessToken,
    basicAuth: basicAuth,
    cleanUp: async () => {
      if (domain && domain.id) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};
