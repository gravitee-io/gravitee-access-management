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
import { requestAccessToken, requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getDomainApi } from '../../../api/commands/management/service/utils';
import {
  getRarDomainName,
  getRarOwnerUsername,
  getRarUserUsername,
  RAR_PERMISSIONS_SEED,
  RAR_USER_PASSWORD,
} from '../../../migration-seeding/rar-permissions-seed';
import { migrationRequirements } from '../../../migration-seeding/version-range';

/**
 * What each assertion needs. Authorization detail types arrive in 4.14, so the Management API has to
 * serve them; after a downgrade to an older Management API the assertions are skipped.
 */
export const RAR_PERMISSIONS_REQUIREMENTS = migrationRequirements({
  /** The channel holds the domain owner and domain user. */
  seeded: { seed: RAR_PERMISSIONS_SEED },
  /** The authorization detail type endpoints and their permission. */
  managementApiServesTypes: { managementApi: { from: '4.14' } },
});

export interface RarPermissionsMigrationFixture {
  domainId: string;
  /** Token of the user given DOMAIN_OWNER on the domain when the channel was seeded. */
  ownerToken: string;
  /** Token of the user given DOMAIN_USER on the domain when the channel was seeded. */
  userToken: string;
}

export async function createRarPermissionsMigrationFixture(label: string): Promise<RarPermissionsMigrationFixture> {
  const accessToken = await requestAdminAccessToken();
  const domainName = getRarDomainName(label);
  const domains = await getDomainApi(accessToken).listDomains({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    q: domainName,
  });
  const domain = domains.data?.find((candidate) => candidate.name === domainName);
  expect(domain?.name).toEqual(domainName);

  return {
    domainId: domain.id,
    ownerToken: await requestAccessToken(getRarOwnerUsername(label), RAR_USER_PASSWORD),
    userToken: await requestAccessToken(getRarUserUsername(label), RAR_USER_PASSWORD),
  };
}
