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
import { createOrganisationUser, searchOrganisationUsers } from '@management-commands/organisation-user-commands';
import { findOrganizationRoleByName } from '@management-commands/role-management-commands';
import { addDomainMembership, userMembership } from '@management-commands/membership-management-commands';
import { ListRolesTypeEnum } from '@management-apis/RoleApi';
import { getDomainApi } from '../api/commands/management/service/utils';
import { Domain } from '../api/management/models/Domain';
import { User } from '../api/management/models/User';
import { getDataPlaneIds, getInstanceLabel, normalizeForName } from './seed';
import { migrationSeed } from './version-range';

export const RAR_USER_PASSWORD = 'SomeP@ssw0rd!';

/**
 * A domain with a domain owner and a domain user, given their roles before the upgrade. The
 * upgrade has to grant the authorization detail type permission to the default roles they hold
 * (AM-5635), so they can use the RAR endpoints without anyone touching their membership.
 */
export const RAR_PERMISSIONS_SEED = migrationSeed({
  name: 'RAR authorization detail type permissions',
  variants: [{ range: { from: '4.10' }, options: undefined }],
  seed: (channelLabel) => seedRarPermissionsData(channelLabel),
});

export function getRarDomainName(label: string): string {
  return `migration-seeded-rar-${normalizeForName(label)}`;
}

export function getRarOwnerUsername(label: string): string {
  return `migration-seeded-rar-owner-${normalizeForName(label)}`.toLowerCase();
}

export function getRarUserUsername(label: string): string {
  return `migration-seeded-rar-user-${normalizeForName(label)}`.toLowerCase();
}

/** Seed one domain per data plane, with its owner and user. */
export async function seedRarPermissionsData(channelLabel: string): Promise<void> {
  const accessToken = await requestAdminAccessToken();
  const domainOwner = await findOrganizationRoleByName(accessToken, 'DOMAIN_OWNER', ListRolesTypeEnum.Domain);
  const domainUser = await findOrganizationRoleByName(accessToken, 'DOMAIN_USER', ListRolesTypeEnum.Domain);

  for (const dataPlaneId of getDataPlaneIds()) {
    const label = getInstanceLabel(channelLabel, dataPlaneId);
    const domain = await getOrCreateDomain(getRarDomainName(label), dataPlaneId, accessToken);
    const owner = await getOrCreateOrganisationUser(getRarOwnerUsername(label), accessToken);
    const user = await getOrCreateOrganisationUser(getRarUserUsername(label), accessToken);
    // Adding a member again keeps a single membership, so the seed converges on these roles.
    await addDomainMembership(domain.id, accessToken, userMembership(owner.id, domainOwner.id));
    await addDomainMembership(domain.id, accessToken, userMembership(user.id, domainUser.id));
  }
}

async function getOrCreateDomain(name: string, dataPlaneId: string, accessToken: string): Promise<Domain> {
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = process.env.AM_DEF_ENV_ID;
  const domainApi = getDomainApi(accessToken);
  const existingDomains = await domainApi.listDomains({ organizationId, environmentId, q: name });
  const existingDomain = existingDomains.data?.find((candidate) => candidate.name === name);
  if (existingDomain) {
    return existingDomain;
  }
  return domainApi.createDomain({
    organizationId,
    environmentId,
    newDomain: { name, description: 'Migration RAR authorization detail type permissions', dataPlaneId },
  });
}

async function getOrCreateOrganisationUser(username: string, accessToken: string): Promise<User> {
  const existing = await searchOrganisationUsers(accessToken, `userName eq "${username}"`);
  const existingUser = existing.data?.find((candidate) => candidate.username === username);
  if (existingUser) {
    return existingUser;
  }
  return createOrganisationUser(accessToken, {
    firstName: 'Migration',
    lastName: 'Rar',
    email: `${username}@example.com`,
    username,
    password: RAR_USER_PASSWORD,
    preRegistration: false,
  });
}
