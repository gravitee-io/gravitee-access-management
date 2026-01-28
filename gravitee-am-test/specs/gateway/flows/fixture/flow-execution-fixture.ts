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
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { clearEmails } from '@utils-commands/email-commands';
import { Fixture } from '../../../test-fixture';

export interface FlowExecutionFixture extends Fixture {
  domain: Domain;
  openIdConfiguration: DomainOidcConfig;
  application: any;
  user: any;
  cleanUp: () => Promise<void>;
}

export const setupFixture = async (): Promise<FlowExecutionFixture> => {
  const accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  const createdDomain = await createDomain(accessToken, uniqueName('flow-execution', true), 'test end-user logout');
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();

  const domainStarted = await startDomain(createdDomain.id, accessToken);

  const idpSet = await getAllIdps(createdDomain.id, accessToken);
  const appClientId = uniqueName('flow-app', true);
  const appClientSecret = uniqueName('flow-app', true);
  const appName = uniqueName('my-client', true);
  const application = await createApplication(createdDomain.id, accessToken, {
    name: appName,
    type: 'WEB',
    clientId: appClientId,
    clientSecret: appClientSecret,
    redirectUris: ['https://callback'],
  }).then((app) =>
    updateApplication(
      createdDomain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['https://callback'],
            grantTypes: ['authorization_code'],
          },
        },
        identityProviders: [{ identity: idpSet.values().next().value.id, priority: -1 }],
      },
      app.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );
  expect(application).toBeDefined();

  await waitForDomainSync(createdDomain.id, accessToken);

  const user = {
    username: 'FlowUser',
    password: 'SomeP@ssw0rd',
    firstName: 'Flow',
    lastName: 'User',
    email: 'flowuser@acme.fr',
    preRegistration: false,
  };
  await createUser(createdDomain.id, accessToken, user);

  const started = await waitForDomainStart(domainStarted);
  const domain = started.domain;
  const openIdConfiguration = started.oidcConfig;

  await clearEmails(user.email);

  return {
    accessToken,
    domain,
    openIdConfiguration,
    application,
    user,
    cleanUp: async () => {
      await safeDeleteDomain(domain?.id, accessToken);
    },
  };
};
