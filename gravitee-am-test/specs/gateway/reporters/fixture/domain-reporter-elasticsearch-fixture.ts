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
import { DomainOidcConfig, safeDeleteDomain, setupDomainForTest, waitFor } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import {
  createDomainReporter,
  deleteDomainReporter,
  listDomainReporters,
  updateDomainReporter,
} from '@management-commands/reporter-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import { deleteIndices, internalElasticsearchUrl } from '@utils-commands/elasticsearch-client';
import { Fixture } from '../../../test-fixture';

export interface ElasticsearchReporterFixture extends Fixture {
  domain: Domain;
  application: Application;
  user: any & { password: string };
  openIdConfiguration: DomainOidcConfig;
  addElasticsearchReporter(index: string, enabled?: boolean): Promise<Reporter>;
  disableDatabaseReporter(): Promise<void>;
  cleanUp(): Promise<void>;
}

export const setupElasticsearchReporterFixture = async (): Promise<ElasticsearchReporterFixture> => {
  const accessToken = await requestAdminAccessToken();
  const { domain, oidcConfig } = await setupDomainForTest(uniqueName('es-reporter-domain', true), { accessToken, waitForStart: true });

  const idpSet = await getAllIdps(domain.id, accessToken);
  const defaultIdpId = idpSet.values().next().value.id;

  const app = await createApplication(domain.id, accessToken, {
    name: uniqueName('es-reporter-app', true),
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
  const username = uniqueName('es-reporter-user', true);
  const user = {
    username,
    password,
    firstName: 'Elasticsearch',
    lastName: 'Reporter',
    email: `${username}@test.com`,
    preRegistration: false,
  };
  await waitForSyncAfter(domain.id, () => createUser(domain.id, accessToken, user));

  const reporterIds: string[] = [];
  const indexPatterns: string[] = [];

  const addElasticsearchReporter = async (index: string, enabled = true): Promise<Reporter> => {
    const reporter = await waitForSyncAfter(domain.id, () =>
      createDomainReporter(domain.id, accessToken, {
        type: 'reporter-am-elasticsearch',
        name: uniqueName('es-reporter', true),
        enabled,
        configuration: JSON.stringify({
          endpoints: [internalElasticsearchUrl()],
          index,
          bulkActions: 1,
          flushInterval: 1,
        }),
      }),
    );
    reporterIds.push(reporter.id);
    indexPatterns.push(`${index}-*`);
    return reporter;
  };

  const disableDatabaseReporter = async (): Promise<void> => {
    const reporters = await listDomainReporters(domain.id, accessToken);
    const database = reporters.find((reporter) => reporter.type !== 'reporter-am-elasticsearch');
    if (!database) {
      throw new Error('No database reporter found on the domain');
    }
    await updateDomainReporter(domain.id, accessToken, database.id, {
      type: database.type,
      name: database.name,
      enabled: false,
      configuration: database.configuration,
    });
    // a disabled reporter produces no sync event to wait on, so give the reload a moment to land
    await waitFor(5000);
  };

  const cleanUp = async (): Promise<void> => {
    for (const id of reporterIds) {
      try {
        await deleteDomainReporter(domain.id, accessToken, id);
      } catch {
        // already gone
      }
    }
    for (const pattern of indexPatterns) {
      await deleteIndices(pattern);
    }
    await safeDeleteDomain(domain.id, accessToken);
  };

  return {
    accessToken,
    domain,
    application: app,
    user,
    openIdConfiguration: oidcConfig,
    addElasticsearchReporter,
    disableDatabaseReporter,
    cleanUp,
  };
};
