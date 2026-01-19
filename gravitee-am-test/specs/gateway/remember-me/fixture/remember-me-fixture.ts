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

import { expect } from '@jest/globals';
import { Domain } from '@management-models/Domain';
import { DomainOidcConfig } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { createUser, deleteUser } from '@management-commands/user-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { createJdbcIdp, createMongoIdp } from '@utils-commands/idps-commands';
import * as faker from 'faker';
import { uniqueName } from '@utils-commands/misc';

const jdbc = process.env.REPOSITORY_TYPE;

const contractValue = '1234';
const userPassword = 'ZxcPrm7123!!';

const defaultApplicationConfiguration = {
  settings: {
    oauth: {
      redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
      forcePKCE: false,
      forceS256CodeChallengeMethod: false,
      grantTypes: ['authorization_code'],
      tokenEndpointAuthMethod: 'none',
    },
  },
};

export interface RememberMeFixture {
  accessToken: string;
  domain: Domain;
  customIdp: any;
  app: any;
  openIdConfiguration: DomainOidcConfig;
  user: any;
  userPassword: string;
  cleanUp: () => Promise<void>;
}

export const initEnv = async (applicationConfiguration, domainConfiguration = null): Promise<RememberMeFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain = await createDomain(accessToken, uniqueName('remember-me', true), 'test remember me option')
    .then((createdDomain) => startDomain(createdDomain.id, accessToken))
    .then((startedDomain) => {
      if (domainConfiguration != null) {
        return patchDomain(startedDomain.id, accessToken, domainConfiguration);
      }

      return startedDomain;
    });

  const customIdp = jdbc === 'jdbc' ? await createJdbcIdp(domain.id, accessToken) : await createMongoIdp(domain.id, accessToken);
  const app = await createTestApp('remember-me-app', domain, accessToken, 'browser', {
    settings: {
      ...defaultApplicationConfiguration.settings,
      ...applicationConfiguration.settings,
    },
    identityProviders: new Set([{ identity: customIdp.id, priority: 0 }]),
  });

  await waitForDomainSync(domain.id, accessToken);

  // Wait for domain to be ready to serve requests
  const started = await waitForDomainStart(domain);
  domain = started.domain;
  const openIdConfiguration = started.oidcConfig;

  const firstName = faker.name.firstName();
  const lastName = faker.name.lastName();
  const user = await createUser(domain.id, accessToken, {
    firstName: firstName,
    lastName: lastName,
    email: faker.internet.email(firstName, lastName),
    username: faker.internet.userName(firstName, lastName),
    password: userPassword,
    client: app.id,
    source: customIdp.id,
    additionalInformation: {
      contract: contractValue,
    },
    preRegistration: false,
  });

  expect(user).toBeDefined();

  return {
    accessToken,
    domain,
    customIdp,
    app,
    openIdConfiguration,
    user,
    userPassword,
    cleanUp: async () => {
      if (user?.id) {
        await deleteUser(domain.id, accessToken, user.id);
      }
      await safeDeleteDomain(domain?.id, accessToken);
    },
  };
};
