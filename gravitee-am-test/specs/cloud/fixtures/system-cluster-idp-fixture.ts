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
import { performDelete, performGet, performPost, performPut } from '@gateway-commands/oauth-oidc-commands';
import { getApplicationApi, getUserApi } from '@management-commands/service/utils';
import { waitForDomainStart } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { CloudDomainScope, safeDeleteCloudDomain } from '@cloud-commands/domain-commands';
import { MONGO_DATABASE, MONGO_URI, setupCloudSharedFixture } from './cloud-shared-fixture';

/**
 * Identity provider calls scoped to a Cockpit-provisioned org and environment. The
 * `@management-commands/idp-management-commands` helpers read AM_DEF_ORG_ID / AM_DEF_ENV_ID, so they
 * cannot address a cloud organization.
 */
export interface CloudIdpScope extends CloudDomainScope {
  domainId: string;
}

export interface SystemClusterIdpFixture extends CloudIdpScope {
  /** Key of a second domain, created through the automation API to hold the manifest under test. */
  automationDomainKey: string;
  /** Deletes the domains created for this spec and releases the shared cloud fixture. */
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

  const automationDomainKey = uniqueName('idp-system-cluster-auto', true).toLowerCase();
  const domainCreated = await putAutomationDomain(scope, {
    key: automationDomainKey,
    name: automationDomainKey,
    path: `/${automationDomainKey}`,
    dataPlaneId: shared.dataPlaneId,
  });
  if (domainCreated.status !== 200) {
    throw new Error(`Failed to create the automation domain: status=${domainCreated.status} body=${domainCreated.text}`);
  }

  const cleanup = async (): Promise<void> => {
    await performDelete(automationUrl(), `${automationPath(scope)}/domains/${automationDomainKey}`, authHeaders(scope.accessToken));
    await safeDeleteCloudDomain(scope, scope.domainId);
    await shared.cleanup();
  };

  return { ...scope, automationDomainKey, cleanup };
};

/**
 * The automation API scoped to the cloud organization. The `@management-commands` automation client
 * reads AM_DEF_ORG_ID / AM_DEF_ENV_ID, so it cannot address a Cockpit-provisioned organization.
 */
const automationUrl = () => `${process.env.AM_MANAGEMENT_URL}/automation`;

const automationPath = (scope: CloudIdpScope) => `/organizations/${scope.organizationId}/environments/${scope.environmentId}`;

const authHeaders = (accessToken: string) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` });

export const putAutomationDomain = (scope: CloudIdpScope, definition: object) =>
  performPut(automationUrl(), `${automationPath(scope)}/domains`, definition, authHeaders(scope.accessToken));

export const putAutomationIdp = (scope: SystemClusterIdpFixture, definition: object) =>
  performPut(
    automationUrl(),
    `${automationPath(scope)}/domains/${scope.automationDomainKey}/identities`,
    definition,
    authHeaders(scope.accessToken),
  );

/** The storage rules the Console reads to shape the mongo identity provider form. */
export const readStorageRules = async (scope: CloudIdpScope) => {
  const response = await performGet(process.env.AM_MANAGEMENT_URL, '/management/platform/configuration/installation', {
    Authorization: `Bearer ${scope.accessToken}`,
  });
  return response.body.identityProviderStorage;
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

/** Storage the identity provider asks for, and keeps unless the platform pins it. */
export const OWN_DATABASE = 'my-own-database';
export const OWN_USERS_COLLECTION = 'my-own-users';

/**
 * The database the platform pins a restricted provider to. Only a mongo management repository names
 * one, so on the postgres stack the provider keeps the database it was created with.
 */
export const platformDatabase = () => (process.env.REPOSITORY_TYPE === 'jdbc' ? OWN_DATABASE : MONGO_DATABASE);

/** The stack runs a mongo container only under the mongo overlay, so a mongo idp cannot connect on jdbc. */
export const isMongoStack = process.env.REPOSITORY_TYPE !== 'jdbc';

/** A domain's default identity provider follows the platform backend, not the type under test. */
export const defaultIdpType = () => (isMongoStack ? 'mongo-am-idp' : 'jdbc-am-idp');

const mongoStore = () => {
  const url = new URL(MONGO_URI);
  return { uri: MONGO_URI, host: url.hostname, port: Number(url.port || 27017) };
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

/** The manifest an operator keeps and re-applies, which never names the storage the platform pins. */
export const buildMongoAutomationIdpDef = (key: string, configurationOverrides: object = {}) => {
  const body = buildMongoIdpBody({ useSystemCluster: true });
  return {
    key,
    name: key,
    type: body.type,
    configuration: JSON.stringify({ ...JSON.parse(body.configuration), ...configurationOverrides }),
  };
};

export const buildMongoIdpUpdateBody = (idp, configurationOverrides: object) => ({
  name: idp.name,
  type: idp.type,
  domainWhitelist: [],
  configuration: JSON.stringify({ ...JSON.parse(idp.configuration), ...configurationOverrides }),
});

/**
 * Identity provider calls at the organization scope. `IdentityProviderServiceImpl.create` runs the
 * same policy for `ReferenceType.ORGANIZATION`, and the Console reuses the domain components for the
 * organization routes, so the scope needs its own coverage.
 */
export const createOrganizationIdp = async (scope: CloudIdpScope, idp: object) => {
  // The generated client types this create as returning nothing, so the body has to come off the
  // raw response. The endpoint does return the created provider.
  const response = await getIdpApi(scope.accessToken).createIdentityProvider1Raw({
    organizationId: scope.organizationId,
    newIdentityProvider: idp as any,
  });
  return response.raw.json();
};

export const updateOrganizationIdp = (scope: CloudIdpScope, idpId: string, body: object) =>
  getIdpApi(scope.accessToken).updateIdentityProvider1({
    organizationId: scope.organizationId,
    identity: idpId,
    updateIdentityProvider: body as any,
  });

export const deleteOrganizationIdp = (scope: CloudIdpScope, idpId: string) =>
  getIdpApi(scope.accessToken).deleteIdentityProvider1({
    organizationId: scope.organizationId,
    identity: idpId,
  });

export const deleteCloudIdp = (scope: CloudIdpScope, idpId: string) =>
  getIdpApi(scope.accessToken).deleteIdentityProvider({
    organizationId: scope.organizationId,
    environmentId: scope.environmentId,
    domain: scope.domainId,
    identity: idpId,
  });

/** A second domain on the same org and environment, so two domains can be compared. */
export const createSecondCloudDomain = async (scope: CloudIdpScope) => {
  const domain = await getDomainApi(scope.accessToken).createDomain({
    organizationId: scope.organizationId,
    environmentId: scope.environmentId,
    newDomain: { name: uniqueName('idp-system-cluster-second', true) },
  });
  return { ...scope, domainId: domain.id } as CloudIdpScope;
};

export const listCloudIdps = (scope: CloudIdpScope) =>
  getIdpApi(scope.accessToken).listIdentityProviders({
    organizationId: scope.organizationId,
    environmentId: scope.environmentId,
    domain: scope.domainId,
  });

/**
 * A domain of its own, enabled, holding a pinned identity provider, a user created through it and an
 * application that accepts the password grant. Only a successful token proves the gateway reads back
 * what the management API wrote: `MongoAbstractProvider` overrides the database again at runtime from
 * the node's own client wrapper, so the two sides can disagree with every other assertion still green.
 */
export const setupGatewayLoginFixture = async (scope: CloudIdpScope) => {
  const domainApi = getDomainApi(scope.accessToken);
  const domain = await domainApi.createDomain({
    organizationId: scope.organizationId,
    environmentId: scope.environmentId,
    newDomain: { name: uniqueName('idp-system-cluster-gw', true) },
  });
  const domainScope: CloudIdpScope = { ...scope, domainId: domain.id };

  const idp = await createCloudIdp(domainScope, buildMongoIdpBody({ useSystemCluster: true }));

  const username = uniqueName('gw-user', true);
  const password = 'Password123!';
  const user = await getUserApi(scope.accessToken).createUser({
    organizationId: scope.organizationId,
    environmentId: scope.environmentId,
    domain: domain.id,
    newUser: {
      username,
      password,
      email: `${username}@example.com`,
      firstName: 'Gateway',
      lastName: 'User',
      preRegistration: false,
      source: idp.id,
    },
  });

  // Through the API directly rather than createTestApp: that helper hardcodes the default
  // organization and environment, and this domain lives in the environment Cockpit provisioned.
  // `localhost` is refused as a redirect uri, so the application needs a routable one.
  const clientId = uniqueName('gw-app', true);
  const application = await getApplicationApi(scope.accessToken).createApplication({
    organizationId: scope.organizationId,
    environmentId: scope.environmentId,
    domain: domain.id,
    newApplication: { name: clientId, type: 'WEB', clientId, redirectUris: ['https://example.com/callback'] },
  });
  await getApplicationApi(scope.accessToken).patchApplication({
    organizationId: scope.organizationId,
    environmentId: scope.environmentId,
    domain: domain.id,
    application: application.id,
    patchApplication: {
      settings: {
        oauth: {
          redirectUris: ['https://example.com/callback'],
          grantTypes: ['authorization_code', 'password', 'refresh_token'],
          scopeSettings: [{ scope: 'openid', defaultScope: true }],
        },
      },
      identityProviders: [{ identity: idp.id, priority: -1 }],
    } as any,
  });

  await domainApi.patchDomain({
    organizationId: scope.organizationId,
    environmentId: scope.environmentId,
    domain: domain.id,
    patchDomain: { enabled: true },
  });
  // waitForDomainStart, not waitForDomainReady: the latter only reports sync, and the gateway rebuilds
  // its routes afterwards, so the first request can land in a 404 window.
  await waitForDomainStart(domain);

  const login = () =>
    performPost(
      process.env.AM_GATEWAY_URL,
      `/${domain.hrid}/oauth/token`,
      `grant_type=password&username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&scope=openid`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization:
          'Basic ' + Buffer.from(`${application.settings.oauth.clientId}:${application.settings.oauth.clientSecret}`).toString('base64'),
      },
    );

  return {
    idp,
    user,
    login,
    cleanup: () => safeDeleteCloudDomain(domainScope, domain.id),
  };
};
