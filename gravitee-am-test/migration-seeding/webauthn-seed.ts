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
import request from 'supertest';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getDefaultIdp } from '@management-commands/idp-management-commands';
import { SoftwareAuthenticator } from '@gateway-commands/webauthn-authenticator';
import { registerWebAuthnCredential, WebAuthnClient } from '@gateway-commands/webauthn-commands';
import { getApplicationApi, getDomainApi, getUserApi } from '../api/commands/management/service/utils';
import { Application } from '../api/management/models/Application';
import { Domain } from '../api/management/models/Domain';
import { DataPlaneTarget, getDataPlaneTargets, getInstanceLabel, normalizeForName } from './seed';
import { migrationSeed } from './version-range';

export const WEBAUTHN_REDIRECT_URI = 'https://callback.example.com';
export const WEBAUTHN_USER_PASSWORD = 'SomeP@ssw0rd';

/**
 * A passwordless user and the WebAuthn credential it registered through the gateway. Seeded from
 * every version the migration pipeline starts from: up to 4.13 the credential is stored by
 * vertx-auth-webauthn, and webauthn4j (4.14) has to keep accepting it.
 */
export const WEBAUTHN_SEED = migrationSeed({
  name: 'WebAuthn passwordless credential',
  variants: [{ range: { from: '4.10' }, options: undefined }],
  seed: (channelLabel) => seedWebAuthnData(channelLabel),
});

export function getWebAuthnDomainName(label: string): string {
  return `migration-seeded-webauthn-${normalizeForName(label)}`;
}

export function getWebAuthnApplicationName(label: string): string {
  return `migration-seeded-webauthn-app-${normalizeForName(label)}`;
}

export function getWebAuthnUsername(label: string): string {
  return `migration-seeded-webauthn-user-${normalizeForName(label)}`.toLowerCase();
}

/**
 * The authenticator holding the seeded credential. Derived from the label, so the verification
 * signs with the very key the seed registered without the key having to be stored anywhere.
 */
export function getWebAuthnAuthenticator(label: string): SoftwareAuthenticator {
  return SoftwareAuthenticator.fromSeed(`migration-webauthn-${normalizeForName(label)}`);
}

/** The application of the seeded security domain, as the WebAuthn ceremonies address it. */
export function getWebAuthnClient(domain: Domain, application: Application, target: DataPlaneTarget): WebAuthnClient {
  return {
    gatewayUrl: target.gatewayUrl,
    domainHrid: domain.hrid,
    clientId: application.settings.oauth.clientId,
    redirectUri: WEBAUTHN_REDIRECT_URI,
    origin: target.gatewayUrl,
  };
}

/**
 * Seed one passwordless security domain per data plane, with a user who registered a WebAuthn
 * credential through the gateway of that data plane: the credential is stored the way the seeding
 * version's WebAuthn library stores it.
 */
export async function seedWebAuthnData(channelLabel: string): Promise<void> {
  const accessToken = await requestAdminAccessToken();
  for (const target of getDataPlaneTargets()) {
    await seedDataPlane(getInstanceLabel(channelLabel, target.id), target, accessToken);
  }
}

async function seedDataPlane(label: string, target: DataPlaneTarget, accessToken: string): Promise<void> {
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = process.env.AM_DEF_ENV_ID;
  const domainApi = getDomainApi(accessToken);

  const domain = await getOrCreateDomain(getWebAuthnDomainName(label), target.id, domainApi);
  await domainApi.patchDomain({
    organizationId,
    environmentId,
    domain: domain.id,
    patchDomain: {
      loginSettings: { inherited: false, passwordlessEnabled: true, passwordlessDeviceNamingEnabled: false },
      webAuthnSettings: {
        origin: target.gatewayUrl,
        relyingPartyName: getWebAuthnDomainName(label),
        userVerification: 'REQUIRED',
        attestationConveyancePreference: 'NONE',
        requireResidentKey: false,
      },
    },
  });

  const defaultIdp = await getDefaultIdp(domain.id, accessToken);
  const application = await getOrCreateWebApplication(domain.id, getWebAuthnApplicationName(label), defaultIdp.id, accessToken);
  await domainApi.patchDomain({ organizationId, environmentId, domain: domain.id, patchDomain: { enabled: true } });

  const user = await getOrCreateUser(domain.id, getWebAuthnUsername(label), defaultIdp.id, accessToken);
  if ((await listWebAuthnCredentials(domain.id, user.id, accessToken)).length > 0) {
    return;
  }

  const client = getWebAuthnClient(domain, application, target);
  await waitForApplication(client);
  const registration = await registerWebAuthnCredential(
    client,
    { username: user.username, password: WEBAUTHN_USER_PASSWORD },
    getWebAuthnAuthenticator(label),
  );
  if (!registration.code) {
    throw new Error(`The WebAuthn registration of ${user.username} failed, the gateway redirected to ${registration.location}`);
  }
}

async function getOrCreateDomain(name: string, dataPlaneId: string, domainApi: any): Promise<Domain> {
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = process.env.AM_DEF_ENV_ID;
  const existingDomains = await domainApi.listDomains({ organizationId, environmentId, q: name });
  const existingDomain = existingDomains.data?.find((candidate) => candidate.name === name);
  if (existingDomain) {
    return existingDomain;
  }
  return domainApi.createDomain({
    organizationId,
    environmentId,
    newDomain: { name, description: 'Migration WebAuthn passwordless credential', dataPlaneId },
  });
}

async function getOrCreateWebApplication(
  domainId: string,
  name: string,
  identityProviderId: string,
  accessToken: string,
): Promise<Application> {
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = process.env.AM_DEF_ENV_ID;
  const applicationApi = getApplicationApi(accessToken);
  const existingApplications = await applicationApi.listApplications({ organizationId, environmentId, domain: domainId, q: name });
  const application =
    existingApplications.data?.find((candidate) => candidate.name === name) ??
    (await applicationApi.createApplication({
      organizationId,
      environmentId,
      domain: domainId,
      newApplication: { name, type: 'WEB', clientId: name, redirectUris: [WEBAUTHN_REDIRECT_URI] },
    }));

  return applicationApi.updateApplication({
    organizationId,
    environmentId,
    domain: domainId,
    application: application.id,
    patchApplication: {
      settings: { oauth: { redirectUris: [WEBAUTHN_REDIRECT_URI], grantTypes: ['authorization_code'] } },
      identityProviders: new Set([{ identity: identityProviderId, priority: 0 }]),
    },
  });
}

async function getOrCreateUser(domainId: string, username: string, identityProviderId: string, accessToken: string): Promise<any> {
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = process.env.AM_DEF_ENV_ID;
  const existingUsers = await getDomainApi(accessToken).listUsers({ organizationId, environmentId, domain: domainId, q: username });
  const existingUser = existingUsers.data?.find((candidate) => candidate.username === username);
  if (existingUser) {
    return existingUser;
  }
  return getUserApi(accessToken).createUser({
    organizationId,
    environmentId,
    domain: domainId,
    newUser: {
      firstName: 'Migration',
      lastName: 'WebAuthn',
      email: `${username}@example.com`,
      username,
      password: WEBAUTHN_USER_PASSWORD,
      source: identityProviderId,
      preRegistration: false,
    },
  });
}

/**
 * The WebAuthn credentials of a user. Not through the user commands, which use Jest's `expect` and
 * cannot load outside a test; read raw, since the generated SDK types the array as a single credential.
 */
export async function listWebAuthnCredentials(domainId: string, userId: string, accessToken: string): Promise<any[]> {
  const response = await getUserApi(accessToken).listUserCredentialsRaw({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    user: userId,
  });
  return response.raw.json();
}

const APPLICATION_TIMEOUT_MS = Number(process.env.AM_DOMAIN_SYNC_TIMEOUT_MS) || 120000;
const APPLICATION_INTERVAL_MS = 500;

/**
 * Poll the authorization endpoint until the gateway sends the application to its login page, i.e.
 * until the security domain is deployed and knows the application: the registration that follows
 * goes through the whole login flow and must not race the deployment.
 */
async function waitForApplication(client: WebAuthnClient): Promise<void> {
  const query = `response_type=code&client_id=${encodeURIComponent(client.clientId)}&redirect_uri=${encodeURIComponent(
    client.redirectUri,
  )}`;
  const start = Date.now();
  let lastOutcome = 'no response';
  while (Date.now() - start < APPLICATION_TIMEOUT_MS) {
    try {
      const response = await request(`${client.gatewayUrl}/${client.domainHrid}/oauth/authorize?${query}`).get('').send();
      const location = response.headers['location'] ?? '';
      if (response.status === 302 && new URL(location).pathname.endsWith('/login')) {
        return;
      }
      lastOutcome = `${response.status} ${location}`;
    } catch (error) {
      lastOutcome = error instanceof Error ? error.message : String(error);
    }
    await new Promise((resolve) => setTimeout(resolve, APPLICATION_INTERVAL_MS));
  }
  throw new Error(`Timed out waiting for application ${client.clientId} on ${client.gatewayUrl}/${client.domainHrid}: ${lastOutcome}`);
}
