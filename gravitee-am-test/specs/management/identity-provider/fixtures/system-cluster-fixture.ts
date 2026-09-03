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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { setupDomainForTest, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Fixture } from '../../../test-fixture';

export interface SystemClusterFixture extends Fixture {
  domain: Domain;
  accessToken: string;
}

export const setupSystemClusterFixture = async (): Promise<SystemClusterFixture> => {
  const accessToken = await requestAdminAccessToken();
  const { domain } = await setupDomainForTest(uniqueName('idp-system-cluster', true), { accessToken, waitForStart: false });

  return {
    accessToken,
    domain,
    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};

export const OWN_DATABASE = 'my-own-database';

export const OWN_USERS_COLLECTION = 'my-own-users';

export const isMongoStack = process.env.REPOSITORY_TYPE !== 'jdbc';

const mongoStore = () => {
  const url = new URL(process.env.AM_INTERNAL_MONGODB_URI ?? process.env.AM_MONGODB_URI);
  return { uri: url.toString(), host: url.hostname, port: Number(url.port || 27017) };
};

export const buildMongoIdpBody = (overrides: { useSystemCluster: boolean; database?: string; usersCollection?: string }) => ({
  external: false,
  type: 'mongo-am-idp',
  domainWhitelist: [],
  name: uniqueName('mongo-idp', true),
  configuration: JSON.stringify({
    ...mongoStore(),
    enableCredentials: false,
    useSystemCluster: overrides.useSystemCluster,
    database: overrides.database ?? OWN_DATABASE,
    usersCollection: overrides.usersCollection ?? OWN_USERS_COLLECTION,
    findUserByUsernameQuery: '{username: ?}',
    findUserByEmailQuery: '{email: ?}',
    usernameField: 'username',
    passwordField: 'password',
    passwordEncoder: 'BCrypt',
  }),
});

export const buildMongoIdpUpdateBody = (idp, configurationOverrides: object) => ({
  name: idp.name,
  type: idp.type,
  domainWhitelist: [],
  configuration: JSON.stringify({ ...JSON.parse(idp.configuration), ...configurationOverrides }),
});
