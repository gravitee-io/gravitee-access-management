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
import { getApplicationApi, getDomainApi, getExtensionApi, getFactorApi, getIdpApi, getUserApi } from '../api/commands/management/service/utils';
import { Application } from '../api/management/models/Application';
import { Domain } from '../api/management/models/Domain';
import { NewApplicationTypeEnum } from '../api/management/models/NewApplication';

export const JWT_BEARER_GRANT_TYPE = 'urn:ietf:params:oauth:grant-type:jwt-bearer';

// Static test vectors for the jwt-bearer extension grant (GIVEN_KEY / ssh-rsa resolver). The
// assertion JWT below is pre-signed by the private key matching EXT_GRANT_PUBLIC_KEY_SSH_RSA, so it
// validates against the seeded grant without any live signing. Copied from the canonical e2e
// fixture: specs/gateway/extension-grant/fixture/jwt-bearer-fixture.ts.
export const EXT_GRANT_PUBLIC_KEY_SSH_RSA =
  'AAAAB3NzaC1yc2EAAAADAQABAAABAQC7VJTUt9Us8cKjMzEfYyjiWA4R4/M2bS1GB4t7NXp98C3SC6dVMvDuictGeurT8jNbvJZHtCSuYEvuNMoSfm76oqFvAp8Gy0iz5sxjZmSnXyCdPEovGhLa0VzMaQ8s+CLOyS56YyCFGeJZqgtzJ6GR3eqoYSW9b9UMvkBpZODSctWSNGj3P7jRFDO5VoTwCQAWbFnOjDfH5Ulgp2PKSQnSJP3AJLQNFNe7br1XbrhV//eO+t51mIpGSDCUv3E0DDFcWDTH9cXDTTlRZVEiR2BwpZOOkE/Z0/BVnhZYL71oZV34bKfWjQIt6V/isSMahdsAASACp4ZTGtwiVuNd9tyb';
export const EXT_GRANT_ASSERTION_JWT =
  'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.Eci61G6w4zh_u9oOCk_v1M_sKcgk0svOmW4ZsL-rt4ojGUH2QY110bQTYNwbEVlowW7phCg7vluX_MCKVwJkxJT6tMk2Ij3Plad96Jf2G2mMsKbxkC-prvjvQkBFYWrYnKWClPBRCyIcG0dVfBvqZ8Mro3t5bX59IKwQ3WZ7AtGBYz5BSiBlrKkp6J1UmP_bFV3eEzIHEFgzRa3pbr4ol4TK6SnAoF88rLr2NhEz9vpdHglUMlOBQiqcZwqrI-Z4XDyDzvnrpujIToiepq9bCimPgVkP54VoZzy-mMSGbthYpLqsL_4MQXaI1Uf_wKFAUuAtzVn4-ebgsKOpvKNzVA';
export const EXT_GRANT_ASSERTION_SUB = '1234567890';

export type DataPlaneTarget = { id: string; gatewayUrl: string };

// Seeded data is keyed by an instance label: the channel label (alpha/beta) combined with the
// data plane id, so each data plane gets its own isolated domain and entities (including the
// custom IdP user store, which is otherwise shared across domains in the same database).
export function getInstanceLabel(channelLabel: string, dataPlaneId: string): string {
  return `${channelLabel}-${dataPlaneId}`;
}

// The data plane ids to seed. The primary comes from AM_DOMAIN_DATA_PLANE_ID (default "default");
// a second data plane is seeded only when AM_DOMAIN_DATA_PLANE_ID_DP2 is set (the k8s migration
// setup), so local/single-data-plane runs keep seeding a single domain.
export function getDataPlaneIds(): string[] {
  const primary = process.env.AM_DOMAIN_DATA_PLANE_ID || 'default';
  const secondary = process.env.AM_DOMAIN_DATA_PLANE_ID_DP2;
  return secondary ? [primary, secondary] : [primary];
}

// Data plane targets for the gateway tests: each id paired with the gateway base URL that serves
// it. The second target is included only when both its env vars are present.
export function getDataPlaneTargets(): DataPlaneTarget[] {
  const targets: DataPlaneTarget[] = [
    { id: process.env.AM_DOMAIN_DATA_PLANE_ID || 'default', gatewayUrl: process.env.AM_GATEWAY_URL },
  ];
  if (process.env.AM_DOMAIN_DATA_PLANE_ID_DP2 && process.env.AM_GATEWAY_URL_DP2) {
    targets.push({ id: process.env.AM_DOMAIN_DATA_PLANE_ID_DP2, gatewayUrl: process.env.AM_GATEWAY_URL_DP2 });
  }
  return targets;
}

export function getDomainName(label: string): string {
  return `migration-seeded-domain-${normalizeForName(label)}`;
}

export function getApplicationName(label: string): string {
  return `migration-seeded-application-${normalizeForName(label)}`;
}

export function getApplicationWithCustomIdpName(label: string): string {
  return `migration-seeded-application-custom-idp-${normalizeForName(label)}`;
}

export function getConsentApplicationName(label: string): string {
  return `migration-seeded-application-consent-${normalizeForName(label)}`;
}

export function getServiceApplicationName(label: string): string {
  return `migration-seeded-application-service-${normalizeForName(label)}`;
}

export function getExtensionGrantName(label: string): string {
  return `migration-seeded-ext-grant-${normalizeForName(label)}`;
}

export function getFactorId(label: string): string {
  return `migration-seeded-factor-${normalizeForName(label)}`;
}

export function getCustomIdpId(label: string): string {
  return `migration-seeded-custom-idp-${normalizeForName(label)}`;
}

export function getCustomIdpName(label: string): string {
  return isJdbcRepository() ? `Migration JDBC IDP ${label}` : `Migration Mongo IDP ${label}`;
}

export function getDefaultStoreUserName(label: string): string {
  return `migration-seeded-user-default-${normalizeForName(label)}`;
}

export function getCustomIdpUserName(label: string): string {
  return `migration-seeded-user-custom-${normalizeForName(label)}`;
}

// The seeded "custom" identity provider mirrors the AM repository backend: a JDBC (PostgreSQL)
// user store when REPOSITORY_TYPE=jdbc, otherwise a MongoDB user store. Same discriminator the
// e2e helpers use (api/commands/utils/idps-commands.ts).
function isJdbcRepository(): boolean {
  return process.env.REPOSITORY_TYPE === 'jdbc';
}

export async function seedMapiData(channelLabel: string): Promise<void> {
  const accessToken = await requestAdminAccessToken();
  const apis = {
    domainApi: getDomainApi(accessToken),
    applicationApi: getApplicationApi(accessToken),
    factorApi: getFactorApi(accessToken),
    idpApi: getIdpApi(accessToken),
    userApi: getUserApi(accessToken),
    extensionGrantApi: getExtensionApi(accessToken),
  };
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = process.env.AM_DEF_ENV_ID;

  // Duplicate the full data set onto every configured data plane (one domain each).
  for (const dataPlaneId of getDataPlaneIds()) {
    await seedDomain(getInstanceLabel(channelLabel, dataPlaneId), dataPlaneId, apis, organizationId, environmentId);
  }
}

type SeedApis = {
  domainApi: any;
  applicationApi: any;
  factorApi: any;
  idpApi: any;
  userApi: any;
  extensionGrantApi: any;
};

async function seedDomain(
  label: string,
  dataPlaneId: string,
  apis: SeedApis,
  organizationId: string,
  environmentId: string,
): Promise<void> {
  const { domainApi, applicationApi, factorApi, idpApi, userApi, extensionGrantApi } = apis;
  const domain = await getOrCreateDomain(label, dataPlaneId, domainApi, organizationId, environmentId);
  const factorId = getFactorId(label);
  const customIdpId = getCustomIdpId(label);
  const defaultIdpId = `default-idp-${domain.id}`;

  await domainApi.patchDomain({
    organizationId,
    environmentId,
    domain: domain.id,
    patchDomain: { enabled: true },
  });

  await getOrCreateTotpFactor(label, factorApi, organizationId, environmentId, domain.id, factorId);
  await getOrCreateCustomIdp(label, idpApi, organizationId, environmentId, domain.id, customIdpId);

  const application = await getOrCreateApplication(applicationApi, organizationId, environmentId, domain.id, getApplicationName(label));
  const applicationWithCustomIdp = await getOrCreateApplication(applicationApi, organizationId, environmentId, domain.id, getApplicationWithCustomIdpName(label));
  const applicationWithConsent = await getOrCreateApplication(applicationApi, organizationId, environmentId, domain.id, getConsentApplicationName(label));

  await configureApplication(applicationApi, organizationId, environmentId, domain.id, factorId, {
    applicationId: application.id,
    idpId: defaultIdpId,
    scopeSettings: [{ scope: 'openid', defaultScope: true }],
    skipConsent: true,
  });

  await configureApplication(applicationApi, organizationId, environmentId, domain.id, factorId, {
    applicationId: applicationWithCustomIdp.id,
    idpId: customIdpId,
    scopeSettings: [{ scope: 'openid', defaultScope: true }],
    skipConsent: true,
  });

  await configureApplication(applicationApi, organizationId, environmentId, domain.id, factorId, {
    applicationId: applicationWithConsent.id,
    idpId: defaultIdpId,
    mfa: false,
    scopeSettings: [
      { scope: 'openid', defaultScope: true },
      { scope: 'email', defaultScope: false },
    ],
  });

  await getOrCreateUser(userApi, organizationId, environmentId, domain.id, defaultIdpId, getDefaultStoreUserName(label));
  await getOrCreateUser(userApi, organizationId, environmentId, domain.id, customIdpId, getCustomIdpUserName(label));

  // Service application + jwt-bearer extension grant: a third-party JWT (signed by the key matching
  // EXT_GRANT_PUBLIC_KEY_SSH_RSA) can be exchanged for an AM token via this app.
  const extensionGrant = await getOrCreateExtensionGrant(label, extensionGrantApi, organizationId, environmentId, domain.id);
  const serviceApplication = await getOrCreateApplication(
    applicationApi,
    organizationId,
    environmentId,
    domain.id,
    getServiceApplicationName(label),
    NewApplicationTypeEnum.Service,
  );
  await configureServiceApplication(applicationApi, organizationId, environmentId, domain.id, serviceApplication.id, extensionGrant.id);
}

async function getOrCreateDomain(label: string, dataPlaneId: string, domainApi: any, organizationId: string, environmentId: string): Promise<Domain> {
  const name = getDomainName(label);
  const existingDomains = await domainApi.listDomains({ organizationId, environmentId, q: name });
  const existingDomain = existingDomains.data?.find((candidate) => candidate.name === name);
  if (existingDomain) {
    return existingDomain;
  }

  return domainApi.createDomain({
    organizationId,
    environmentId,
    newDomain: {
      name,
      description: `Migration seed domain ${label}`,
      dataPlaneId,
    },
  });
}

async function getOrCreateApplication(
  applicationApi: any,
  organizationId: string,
  environmentId: string,
  domainId: string,
  name: string,
  type: NewApplicationTypeEnum = NewApplicationTypeEnum.Web,
): Promise<Application> {
  const existingApplications = await applicationApi.listApplications({ organizationId, environmentId, domain: domainId, q: name });
  const existingApplication = existingApplications.data?.find((candidate) => candidate.name === name);
  if (existingApplication) {
    return existingApplication;
  }

  return applicationApi.createApplication({
    organizationId,
    environmentId,
    domain: domainId,
    newApplication: {
      name,
      type,
      clientId: name,
      clientSecret: name,
      ...(type === NewApplicationTypeEnum.Service ? {} : { redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'] }),
    },
  });
}

async function getOrCreateExtensionGrant(
  label: string,
  extensionGrantApi: any,
  organizationId: string,
  environmentId: string,
  domainId: string,
): Promise<{ id: string }> {
  const name = getExtensionGrantName(label);
  const existing = await extensionGrantApi.listExtensionGrants({ organizationId, environmentId, domain: domainId });
  const found = existing?.find((candidate) => candidate.name === name);
  if (found) {
    return found;
  }

  return extensionGrantApi.createExtensionGrant({
    organizationId,
    environmentId,
    domain: domainId,
    newExtensionGrant: {
      type: 'jwtbearer-am-extension-grant',
      grantType: JWT_BEARER_GRANT_TYPE,
      configuration: JSON.stringify({ publicKeyResolver: 'GIVEN_KEY', publicKey: `ssh-rsa ${EXT_GRANT_PUBLIC_KEY_SSH_RSA}` }),
      name,
    },
  });
}

async function configureServiceApplication(
  applicationApi: any,
  organizationId: string,
  environmentId: string,
  domainId: string,
  applicationId: string,
  extensionGrantId: string,
): Promise<void> {
  await applicationApi.updateApplication({
    organizationId,
    environmentId,
    domain: domainId,
    application: applicationId,
    patchApplication: {
      settings: {
        oauth: {
          grantTypes: ['client_credentials', `${JWT_BEARER_GRANT_TYPE}~${extensionGrantId}`],
        },
      },
    },
  });
}

async function configureApplication(
  applicationApi: any,
  organizationId: string,
  environmentId: string,
  domainId: string,
  factorId: string,
  options: { applicationId: string; idpId: string; scopeSettings: { scope: string; defaultScope: boolean }[]; skipConsent?: boolean; mfa?: boolean },
): Promise<void> {
  await applicationApi.updateApplication({
    organizationId,
    environmentId,
    domain: domainId,
    application: options.applicationId,
    patchApplication: {
      settings: {
        oauth: {
          redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
          grantTypes: ['authorization_code', 'password', 'refresh_token', 'client_credentials'],
          scopeSettings: options.scopeSettings,
        },
        ...(options.skipConsent === undefined ? {} : { advanced: { skipConsent: options.skipConsent } }),
        ...(options.mfa === false ? {} : { mfa: {
          factor: {
            defaultFactorId: factorId,
            applicationFactors: [{ id: factorId, selectionRule: '' }],
          },
          enroll: { active: true, type: 'REQUIRED', forceEnrollment: true },
          challenge: { active: true, type: 'REQUIRED' },
        } }),
      },
      identityProviders: new Set([{ identity: options.idpId, priority: 0 }]),
    },
  });
}

async function getOrCreateTotpFactor(
  label: string,
  factorApi: any,
  organizationId: string,
  environmentId: string,
  domainId: string,
  factorId: string,
): Promise<void> {
  const factors = await factorApi.listFactors({ organizationId, environmentId, domain: domainId });
  if (factors.some((candidate) => candidate.id === factorId)) {
    return;
  }

  await factorApi.createFactor({
    organizationId,
    environmentId,
    domain: domainId,
    newFactor: {
      id: factorId,
      type: 'otp-am-factor',
      factorType: 'TOTP',
      configuration: '{"issuer":"Gravitee.io","algorithm":"HmacSHA1","timeStep":"30","returnDigits":"6"}',
      name: `Migration TOTP factor ${label}`,
    },
  });
}

async function getOrCreateCustomIdp(
  label: string,
  idpApi: any,
  organizationId: string,
  environmentId: string,
  domainId: string,
  customIdpId: string,
): Promise<void> {
  const idps = await idpApi.listIdentityProviders({ organizationId, environmentId, domain: domainId });
  if (idps.some((candidate) => candidate.id === customIdpId)) {
    return;
  }

  await idpApi.createIdentityProvider({
    organizationId,
    environmentId,
    domain: domainId,
    newIdentityProvider: {
      id: customIdpId,
      external: false,
      domainWhitelist: [],
      name: getCustomIdpName(label),
      ...buildCustomIdpStore(label),
    },
  });
}

function buildCustomIdpStore(label: string): { type: string; configuration: string } {
  const usersStore = `migration_seeded_users_${normalizeForName(label).replace(/-/g, '_')}`;

  if (isJdbcRepository()) {
    return {
      type: 'jdbc-am-idp',
      configuration: JSON.stringify({
        host: process.env.AM_INTERNAL_POSTGRES_HOST || process.env.AM_POSTGRES_HOST,
        port: Number(process.env.AM_INTERNAL_POSTGRES_PORT || 5432),
        protocol: 'postgresql',
        database: process.env.AM_INTERNAL_POSTGRES_DATABASE || 'gravitee-am',
        usersTable: usersStore,
        user: process.env.AM_INTERNAL_POSTGRES_USER || 'postgres',
        password: process.env.AM_INTERNAL_POSTGRES_PASSWORD || 'postgres',
        autoProvisioning: true,
        selectUserByUsernameQuery: `SELECT * FROM ${usersStore} WHERE username = %s`,
        selectUserByMultipleFieldsQuery: `SELECT * FROM ${usersStore} WHERE username = %s or email = %s`,
        selectUserByEmailQuery: `SELECT * FROM ${usersStore} WHERE email = %s`,
        identifierAttribute: 'id',
        usernameAttribute: 'username',
        emailAttribute: 'email',
        passwordAttribute: 'password',
        passwordEncoder: 'None',
        useDedicatedSalt: false,
        passwordSaltLength: 32,
      }),
    };
  }

  return {
    type: 'mongo-am-idp',
    configuration: JSON.stringify({
      uri: process.env.AM_INTERNAL_MONGODB_URI || process.env.AM_MONGODB_URI,
      enableCredentials: false,
      databaseCredentials: process.env.AM_INTERNAL_MONGODB_DATABASE || 'gravitee-am',
      database: process.env.AM_INTERNAL_MONGODB_DATABASE || 'gravitee-am',
      usersCollection: usersStore,
      findUserByUsernameQuery: '{username: ?}',
      findUserByEmailQuery: '{email: ?}',
      usernameField: 'username',
      passwordField: 'password',
      passwordEncoder: 'None',
      useDedicatedSalt: false,
      passwordSaltLength: 32,
    }),
  };
}

async function getOrCreateUser(
  userApi: any,
  organizationId: string,
  environmentId: string,
  domainId: string,
  source: string,
  username: string,
): Promise<void> {
  const users = await userApi.listUsers({ organizationId, environmentId, domain: domainId, q: username });
  if (users.data?.some((candidate) => candidate.username === username && candidate.source === source)) {
    return;
  }

  await userApi.createUser({
    organizationId,
    environmentId,
    domain: domainId,
    newUser: {
      username,
      email: `${username}@example.test`,
      firstName: 'Migration',
      lastName: `Seed`,
      password: 'SomeP@ssw0rd',
      preRegistration: false,
      registrationCompleted: true,
      source,
    },
  });
}

function normalizeForName(label: string): string {
  return label.replace(/[^0-9A-Za-z]+/g, '-');
}
