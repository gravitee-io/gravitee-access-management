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
import {
  createDomain,
  DomainOidcConfig,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { createCustomIdp } from '@utils-commands/idps-commands';
import { createApps, RedirectUriApplications } from './redirect-uri-applications-fixture';
import { createUser } from '@management-commands/user-management-commands';
import { User } from '@management-models/User';

export interface RedirectUriFixture {
  domain: Domain;
  accessToken: string;
  oidc: DomainOidcConfig;
  apps: RedirectUriApplications;
  user: User;
  cleanup: () => Promise<void>;
}

export const setupFixture = async (): Promise<RedirectUriFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('redirect-uri', true), 'Description').then((domain) =>
    patchDomain(domain.id, accessToken, {
      oidc: {
        clientRegistrationSettings: {
          allowRedirectUriParamsExpressionLanguage: true,
        },
      },
    }),
  );
  const customIdp = await createCustomIdp(domain.id, accessToken);
  const apps = await createApps(domain.id, accessToken, customIdp.id);

  const user = await createUser(domain.id, accessToken, {
    firstName: 'john',
    lastName: 'doe',
    email: 'john.doe@test.com',
    username: 'john.doe',
    password: 'Password123!',
    source: customIdp.id,
    preRegistration: false,
  });
  user.password = 'Password123!';

  await startDomain(domain.id, accessToken);
  const domainWithOidc = await waitForDomainStart(domain);

  return {
    domain: domain,
    accessToken: accessToken,
    oidc: domainWithOidc.oidcConfig,
    apps: apps,
    user: user,
    cleanup: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};
