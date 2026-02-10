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

const userPassword = 'ZxcPrm7123!!';

const defaultOAuthSettings = {
  redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
  forcePKCE: false,
  forceS256CodeChallengeMethod: false,
  grantTypes: ['authorization_code'],
  tokenEndpointAuthMethod: 'none',
};

export interface SessionManagementGatewayFixture {
  accessToken: string;
  domain: Domain;
  customIdp: any;
  user: any;
  userPassword: string;
  openIdConfiguration: DomainOidcConfig;
  appPersistentTrue: any;
  appPersistentFalse: any;
  appInheritedTrue: any;
  appNoSettings: any;
  appForLifecyclePersistent: any;
  appForLifecycleSession: any;
  cleanUp: () => Promise<void>;
}

export const initFixture = async (): Promise<SessionManagementGatewayFixture> => {
  const accessToken = await requestAdminAccessToken();

  let domain = await createDomain(accessToken, uniqueName('session-gw', true), 'test session management').then((createdDomain) =>
    startDomain(createdDomain.id, accessToken),
  );

  const customIdp = jdbc === 'jdbc' ? await createJdbcIdp(domain.id, accessToken) : await createMongoIdp(domain.id, accessToken);

  const createApp = async (name: string, cookieSettings: any) => {
    return createTestApp(name, domain, accessToken, 'browser', {
      settings: {
        oauth: defaultOAuthSettings,
        cookieSettings,
      },
      identityProviders: new Set([{ identity: customIdp.id, priority: 0 }]),
    });
  };

  // Create all apps upfront before syncing
  const appPersistentTrue = await createApp(uniqueName('persistent-on', true), {
    inherited: false,
    session: { persistent: true },
  });

  const appPersistentFalse = await createApp(uniqueName('persistent-off', true), {
    inherited: false,
    session: { persistent: false },
  });

  const appInheritedTrue = await createApp(uniqueName('inherited-on', true), {
    inherited: true,
  });

  // App created without explicit cookieSettings to test default behaviour
  const appNoSettings = await createTestApp(uniqueName('no-settings', true), domain, accessToken, 'browser', {
    settings: {
      oauth: defaultOAuthSettings,
    },
    identityProviders: new Set([{ identity: customIdp.id, priority: 0 }]),
  });

  const appForLifecyclePersistent = await createApp(uniqueName('lifecycle-on', true), {
    inherited: false,
    session: { persistent: true },
  });

  const appForLifecycleSession = await createApp(uniqueName('lifecycle-off', true), {
    inherited: false,
    session: { persistent: false },
  });

  // Single sync + start after all apps are created
  await waitForDomainSync(domain.id, accessToken);

  const started = await waitForDomainStart(domain);
  domain = started.domain;
  const openIdConfiguration = started.oidcConfig;

  const firstName = faker.name.firstName();
  const lastName = faker.name.lastName();
  const user = await createUser(domain.id, accessToken, {
    firstName,
    lastName,
    email: faker.internet.email(firstName, lastName),
    username: faker.internet.userName(firstName, lastName),
    password: userPassword,
    source: customIdp.id,
    preRegistration: false,
  });
  expect(user).toBeDefined();

  const cleanUp = async () => {
    if (user?.id) {
      try {
        await deleteUser(domain.id, accessToken, user.id);
      } catch (err) {
        // ignore cleanup errors
      }
    }
    await safeDeleteDomain(domain?.id, accessToken);
  };

  return {
    accessToken,
    domain,
    customIdp,
    user,
    userPassword,
    openIdConfiguration,
    appPersistentTrue,
    appPersistentFalse,
    appInheritedTrue,
    appNoSettings,
    appForLifecyclePersistent,
    appForLifecycleSession,
    cleanUp,
  };
};
