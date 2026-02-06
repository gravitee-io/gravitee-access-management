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
import { DomainOidcConfig } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, startDomain, waitForDomainStart } from '@management-commands/domain-management-commands';
import { createCustomIdp } from '@utils-commands/idps-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

export interface LoginFlowFixture extends Fixture {
  domain: Domain;
  customIdp: any;
  multiUserLoginApp: any;
  openIdConfiguration: DomainOidcConfig;
  uniquePassword: () => string;
}

export const setupFixture = async (): Promise<LoginFlowFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('login-flow-domain', true), 'test user login');

  const customIdp = await createCustomIdp(domain.id, accessToken);
  const multiUserLoginApp = await createTestApp('multi-user-login-app', domain, accessToken, 'WEB', {
    settings: {
      oauth: {
        redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
        scopeSettings: [
          { scope: 'openid', defaultScope: true },
          {
            scope: 'openid',
            defaultScope: true,
          },
        ],
      },
    },
    identityProviders: new Set([{ identity: customIdp.id, priority: 0 }]),
  });

  const startedDomain = await startDomain(domain.id, accessToken);
  const started = await waitForDomainStart(startedDomain);
  const openIdConfiguration = started.oidcConfig;
  let passwordCounter = 1;
  return {
    accessToken,
    domain,
    customIdp,
    multiUserLoginApp,
    openIdConfiguration,
    uniquePassword: () => {
      passwordCounter += 1;
      return `Password12${passwordCounter}!`;
    },
    cleanUp: async () => {
      if (domain?.id) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};
