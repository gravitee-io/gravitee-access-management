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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { listUserCredentials, listUsers } from '@management-commands/user-management-commands';
import { SoftwareAuthenticator } from '@gateway-commands/webauthn-authenticator';
import { CeremonyOverrides, CeremonyResult, signInWithWebAuthn, WebAuthnClient } from '@gateway-commands/webauthn-commands';
import { getApplicationApi, getDomainApi } from '../../../api/commands/management/service/utils';
import { DataPlaneTarget } from '../../../migration-seeding/seed';
import {
  getWebAuthnApplicationName,
  getWebAuthnAuthenticator,
  getWebAuthnClient,
  getWebAuthnDomainName,
  getWebAuthnUsername,
  WEBAUTHN_SEED,
} from '../../../migration-seeding/webauthn-seed';
import { migrationRequirements } from '../../../migration-seeding/version-range';

/**
 * What each WebAuthn assertion needs. A channel seeded by 4.14 (webauthn4j) is still asserted after
 * the downgrade (vertx-auth-webauthn up to 4.13): both directions have to read each other's credentials.
 */
export const WEBAUTHN_REQUIREMENTS = migrationRequirements({
  /** The channel holds the WebAuthn data set. */
  seeded: { seed: WEBAUTHN_SEED },
  /** The passwordless ceremony pages the software authenticator drives, unchanged since 4.10. */
  gatewaySignsIn: { gateway: { from: '4.10' } },
});

/** A WebAuthn credential as the Management API lists it for a user. */
export interface SeededCredential {
  credentialId: string;
  counter: number;
}

export interface WebAuthnMigrationFixture {
  username: string;
  /** The authenticator holding the credential the seed registered. */
  authenticator: SoftwareAuthenticator;
  /** The WebAuthn credentials the Management API lists for the seeded user. */
  credentials: () => Promise<SeededCredential[]>;
  /** Signs the seeded user in without a password, with `authenticator` unless another one is given. */
  signIn: (authenticator?: SoftwareAuthenticator, overrides?: CeremonyOverrides) => Promise<CeremonyResult>;
}

export async function createWebAuthnMigrationFixture(label: string, target: DataPlaneTarget): Promise<WebAuthnMigrationFixture> {
  const accessToken = await requestAdminAccessToken();
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = process.env.AM_DEF_ENV_ID;

  const domainName = getWebAuthnDomainName(label);
  const domains = await getDomainApi(accessToken).listDomains({ organizationId, environmentId, q: domainName });
  const domain = domains.data?.find((candidate) => candidate.name === domainName);
  expect(domain).toBeDefined();

  const applicationName = getWebAuthnApplicationName(label);
  const applications = await getApplicationApi(accessToken).listApplications({
    organizationId,
    environmentId,
    domain: domain.id,
    q: applicationName,
  });
  const application = applications.data?.find((candidate) => candidate.name === applicationName);
  expect(application).toBeDefined();

  const username = getWebAuthnUsername(label);
  const users = await listUsers(domain.id, accessToken, username);
  const user = users.data?.find((candidate) => candidate.username === username);
  expect(user).toBeDefined();

  // The page lists only the summary of the application; the client id comes with the full one.
  const fullApplication = await getApplicationApi(accessToken).findApplication({
    organizationId,
    environmentId,
    domain: domain.id,
    application: application.id,
  });
  const client: WebAuthnClient = getWebAuthnClient(domain, fullApplication, target);
  const authenticator = getWebAuthnAuthenticator(label);

  return {
    username,
    authenticator,
    credentials: async () => (await listUserCredentials(domain.id, accessToken, user.id)) as unknown as SeededCredential[],
    signIn: (signer = authenticator, overrides = {}) => signInWithWebAuthn(client, username, signer, overrides),
  };
}
