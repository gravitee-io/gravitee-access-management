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
import { createDomain, patchDomain, safeDeleteDomain, startDomain, waitForDomainStart } from '@management-commands/domain-management-commands';
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
  let domain = await createDomain(accessToken, uniqueName('remember-me', true), 'test remember me option');

  if (domainConfiguration != null) {
    domain = await patchDomain(domain.id, accessToken, domainConfiguration);
  }

  const customIdp = jdbc === 'jdbc' ? await createJdbcIdp(domain.id, accessToken) : await createMongoIdp(domain.id, accessToken);
  const app = await createTestApp('remember-me-app', domain, accessToken, 'browser', {
    settings: {
      ...defaultApplicationConfiguration.settings,
      ...applicationConfiguration.settings,
    },
    identityProviders: new Set([{ identity: customIdp.id, priority: 0 }]),
  });

  const startedDomain = await startDomain(domain.id, accessToken);
  const started = await waitForDomainStart(startedDomain);
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

export async function validateCookieNotExists(cookieName: string, tokenResponse: any) {
  expect(tokenResponse.request.header.Cookie.filter((cookie) => cookie.includes(cookieName))).toHaveLength(0);
}

export async function validateMaxAgeCookie(cookieName: string, maxAgeExpected: string, tokenResponse: any) {
  expect(tokenResponse.request.header.Cookie.filter((cookie) => cookie.includes(cookieName))[0]).toContain(`Max-Age=${maxAgeExpected}`);
}

export function cookieValueIfNotExpired(setCookieHeader: string, issuedAtMillis: number): string | undefined {
  const maxAgePart = setCookieHeader
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.toLowerCase().startsWith('max-age='));

  if (!maxAgePart) {
    return setCookieHeader.split(';')[0];
  }

  const maxAgeSeconds = Number(maxAgePart.split('=')[1]);
  if (Number.isNaN(maxAgeSeconds)) {
    return setCookieHeader.split(';')[0];
  }

  return Date.now() - issuedAtMillis <= maxAgeSeconds * 1000 ? setCookieHeader.split(';')[0] : undefined;
}

