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
import { getDomainApi, getIdpApi } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';
import { CloudDomainScope, safeDeleteCloudDomain } from '@cloud-commands/domain-commands';
import { setupCloudSharedFixture } from './cloud-shared-fixture';

/**
 * Identity provider calls scoped to a Cockpit-provisioned org and environment. The
 * `@management-commands/idp-management-commands` helpers read AM_DEF_ORG_ID / AM_DEF_ENV_ID, so they
 * cannot address a cloud organization.
 */
export interface CloudIdpScope extends CloudDomainScope {
  domainId: string;
}

export interface SystemClusterIdpFixture extends CloudIdpScope {
  /** Deletes the domain created for this spec and releases the shared cloud fixture. */
  cleanup: () => Promise<void>;
}

/** Creates a domain on the shared cloud org/env to hold the identity providers under test. */
export const setupSystemClusterIdpFixture = async (): Promise<SystemClusterIdpFixture> => {
  const shared = await setupCloudSharedFixture();
  const domain = await getDomainApi(shared.accessToken).createDomain({
    organizationId: shared.organizationId,
    environmentId: shared.environmentId,
    newDomain: { name: uniqueName('idp-system-cluster', true) },
  });
  const scope: CloudIdpScope = {
    accessToken: shared.accessToken,
    organizationId: shared.organizationId,
    environmentId: shared.environmentId,
    domainId: domain.id,
  };

  const cleanup = async (): Promise<void> => {
    await safeDeleteCloudDomain(scope, scope.domainId);
    await shared.cleanup();
  };

  return { ...scope, cleanup };
};

export const createCloudIdp = (scope: CloudIdpScope, idp: object) =>
  getIdpApi(scope.accessToken).createIdentityProvider({
    organizationId: scope.organizationId,
    environmentId: scope.environmentId,
    domain: scope.domainId,
    newIdentityProvider: idp as any,
  });

export const getCloudIdp = (scope: CloudIdpScope, idpId: string) =>
  getIdpApi(scope.accessToken).findIdentityProvider({
    organizationId: scope.organizationId,
    environmentId: scope.environmentId,
    domain: scope.domainId,
    identity: idpId,
  });

export const updateCloudIdp = (scope: CloudIdpScope, idpId: string, body: object) =>
  getIdpApi(scope.accessToken).updateIdentityProvider({
    organizationId: scope.organizationId,
    environmentId: scope.environmentId,
    domain: scope.domainId,
    identity: idpId,
    updateIdentityProvider: body as any,
  });

export const buildMongoIdpBody = (overrides: { useSystemCluster: boolean; database?: string; usersCollection?: string }) => ({
  external: false,
  type: 'mongo-am-idp',
  domainWhitelist: [],
  name: uniqueName('mongo-idp', true),
  configuration: JSON.stringify({
    uri: 'mongodb://mongodb:27017',
    host: 'mongodb',
    port: 27017,
    enableCredentials: false,
    useSystemCluster: overrides.useSystemCluster,
    database: overrides.database ?? 'my-own-database',
    usersCollection: overrides.usersCollection ?? 'my-own-users',
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
