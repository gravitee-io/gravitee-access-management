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
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { Application } from '@management-models/Application';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { uniqueName } from '@utils-commands/misc';
import faker from 'faker';
import { createCustomIdp } from '@utils-commands/idps-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { deletePasswordPolicy, getAllPasswordPolicies } from '@management-commands/password-policy-management-commands';
import { Fixture } from '../../../test-fixture';

export interface PasswordPolicyFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  application: Application;
  defaultIdp: IdentityProvider;
  customIdp: IdentityProvider;
  customIdp2: IdentityProvider;
}

export const setupFixture = async (): Promise<PasswordPolicyFixture> => {
  const accessToken = await requestAdminAccessToken();

  const domain = await createDomain(accessToken, uniqueName('password-policy-idp'), faker.company.catchPhraseDescriptor());

  const customIdp1 = await createCustomIdp(domain.id, accessToken);
  const customIdp2 = await createCustomIdp(domain.id, accessToken);
  const application = await createTestApp('password-policy-test-login-app', domain, accessToken, 'WEB', {
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
    identityProviders: new Set([{ identity: customIdp1.id, priority: 0 }]),
  });

  const domainWithOidc = await startDomain(domain.id, accessToken).then((domain) => waitForDomainStart(domain));

  return {
    accessToken: accessToken,
    domain: domain,
    oidc: domainWithOidc.oidcConfig,
    application: application,
    defaultIdp: null,
    customIdp: customIdp1,
    customIdp2: customIdp2,
    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};

export async function removeAllPasswordPolicies(domainId: string, accessToken: string): Promise<void> {
  const passwordPolicies = await getAllPasswordPolicies(domainId, accessToken).catch(() => []);
  for (const p of passwordPolicies) {
    await deletePasswordPolicy(domainId, accessToken, p.id);
  }
}
