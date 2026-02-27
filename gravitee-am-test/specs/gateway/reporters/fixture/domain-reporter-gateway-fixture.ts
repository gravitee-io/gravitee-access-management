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
import { Application } from '@management-models/Application';
import { Reporter } from '@management-models/Reporter';
import { DomainOidcConfig, safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { createDomainReporter, deleteDomainReporter } from '@management-commands/reporter-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

export interface DomainReporterGatewayFixture extends Fixture {
  domain: Domain;
  application: Application;
  user: any & { password: string };
  openIdConfiguration: DomainOidcConfig;
  addReporter(topicName: string, auditTypes?: string[]): Promise<Reporter>;
  cleanUp(): Promise<void>;
}

export const setupDomainReporterGatewayFixture = async (): Promise<DomainReporterGatewayFixture> => {
  const accessToken = await requestAdminAccessToken();
  const { domain, oidcConfig } = await setupDomainForTest(uniqueName('reporter-gw-domain', true), { accessToken, waitForStart: true });

  const idpSet = await getAllIdps(domain.id, accessToken);
  const defaultIdpId = idpSet.values().next().value.id;

  const appName = uniqueName('reporter-gw-app', true);
  const app = await createApplication(domain.id, accessToken, {
    name: appName,
    type: 'WEB',
    redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
  }).then((created) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
            grantTypes: ['authorization_code'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
        },
        identityProviders: [{ identity: defaultIdpId, priority: -1 }],
      },
      created.id,
    ).then((updated) => {
      updated.settings.oauth.clientSecret = created.settings.oauth.clientSecret;
      return updated;
    }),
  );

  const password = 'SomeP@ssw0rd';
  const username = uniqueName('reporter-user', true);
  const user = {
    username,
    password,
    firstName: 'Reporter',
    lastName: 'User',
    email: `${username}@test.com`,
    preRegistration: false,
  };
  await waitForSyncAfter(domain.id, () => createUser(domain.id, accessToken, user));

  const reporterIds: string[] = [];

  const addReporter = async (topicName: string, auditTypes: string[] = []): Promise<Reporter> => {
    const reporter = await waitForSyncAfter(domain.id, () =>
      createDomainReporter(domain.id, accessToken, {
        type: 'reporter-am-kafka',
        name: uniqueName('gw-kafka-reporter', true),
        enabled: true,
        configuration: JSON.stringify({
          bootstrapServers: 'kafka:29092',
          topic: topicName,
          acks: '1',
          auditTypes,
        }),
      }),
    );
    reporterIds.push(reporter.id);
    return reporter;
  };

  const cleanUp = async (): Promise<void> => {
    for (const id of reporterIds) {
      try {
        await deleteDomainReporter(domain.id, accessToken, id);
      } catch {
        // ignore
      }
    }
    await safeDeleteDomain(domain.id, accessToken);
  };

  return {
    accessToken,
    domain,
    application: app,
    user,
    openIdConfiguration: oidcConfig,
    addReporter,
    cleanUp,
  };
};
