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
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { createApplication, patchApplication } from '@management-commands/application-management-commands';
import { findOrganizationRoleByName } from '@management-commands/role-management-commands';
import { deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { addApplicationMembership, addDomainMembership, userMembership } from '@management-commands/membership-management-commands';
import { ListRolesTypeEnum } from '@management-apis/RoleApi';
import { uniqueName } from '@utils-commands/misc';
import type { Domain } from '@management-models/Domain';
import type { NewApplicationTypeEnum } from '@management-models/NewApplication';
import { Fixture } from '../../../test-fixture';
import { createPersona, Persona } from '../../permissions/fixtures/rbac-fixture';

export interface ListedApplication {
  id: string;
  name: string;
  type: string;
  enabled: boolean;
}

export interface ApplicationPage {
  data: ListedApplication[];
  page: number;
  totalCount: number;
  nextCursor?: string;
}

export type ApplicationsQuery = Record<string, string | number | string[]>;

export interface ApplicationListFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  ownedNames: string[];
  readableNames: string[];
  /** DOMAIN_OWNER on the domain and primary owner of `ownedNames`. */
  owner: Persona;
  /** APPLICATION_OWNER on `readableNames` only, without APPLICATION[READ] on the domain. */
  limited: Persona;
  list: (query?: ApplicationsQuery, token?: string) => Promise<ApplicationPage>;
  /** Lists the applications of another domain as the admin. */
  listIn: (domainId: string, query?: ApplicationsQuery) => Promise<ApplicationPage>;
  listStatus: (query: ApplicationsQuery) => Promise<number>;
  follow: (nextCursor: string, token?: string) => Promise<ApplicationPage>;
  /** A new domain holding `count` service applications. */
  domainWithApplications: (count: number) => Promise<Domain>;
}

export const APPLICATION_LIST_TEST = {
  DOMAIN_NAME_PREFIX: 'app-list',
} as const;

/** Created by the admin, in this order. */
export const ADMIN_APPLICATIONS = [
  { name: 'app_a', type: 'WEB', enabled: true },
  { name: 'App_B', type: 'SERVICE', enabled: true },
  { name: 'app_c', type: 'WEB', enabled: false },
  { name: 'app_d', type: 'SERVICE', enabled: false },
  { name: 'beta_e', type: 'WEB', enabled: true },
] as const;

const OWNED_NAMES = ['owned_f', 'owned_g'];
const READABLE_NAMES = ['App_B', 'app_d'];

const managementUrl = (path: string) => `${process.env.AM_MANAGEMENT_URL}/management${path}`;

const applicationsPath = (domainId: string) =>
  `/organizations/${process.env.AM_DEF_ORG_ID}/environments/${process.env.AM_DEF_ENV_ID}/domains/${domainId}/applications/search`;

const queryString = (query: ApplicationsQuery) => {
  const params = new URLSearchParams();
  for (const [name, value] of Object.entries(query)) {
    for (const item of Array.isArray(value) ? value : [value]) {
      params.append(name, String(item));
    }
  }
  return params.toString();
};

const get = (path: string, accessToken: string) => fetch(managementUrl(path), { headers: { Authorization: `Bearer ${accessToken}` } });

const readPage = async (response: Response): Promise<ApplicationPage> => {
  if (!response.ok) {
    throw new Error(`GET applications failed: ${response.status} ${await response.text()}`);
  }
  return response.json();
};

const create = (domainId: string, accessToken: string, name: string, type: NewApplicationTypeEnum) =>
  createApplication(domainId, accessToken, { name, type, clientId: `${name}-id`, redirectUris: ['https://example.com/callback'] });

export const setupApplicationListFixture = async (): Promise<ApplicationListFixture> => {
  const domains: Domain[] = [];
  const personas: Persona[] = [];
  let accessToken: string | null = null;

  const cleanUp = async () => {
    for (const domain of domains) {
      await safeDeleteDomain(domain.id, accessToken);
    }
    for (const persona of personas) {
      try {
        await deleteOrganisationUser(accessToken, persona.userId);
      } catch {
        // best effort
      }
    }
  };

  const newDomain = async () => {
    const { domain } = await setupDomainForTest(uniqueName(APPLICATION_LIST_TEST.DOMAIN_NAME_PREFIX, true), {
      accessToken,
      waitForStart: false,
    });
    domains.push(domain);
    return domain;
  };

  try {
    accessToken = await requestAdminAccessToken();
    const domain = await newDomain();

    // one at a time, so updatedAt follows creation order
    const idsByName: Record<string, string> = {};
    for (const { name, type, enabled } of ADMIN_APPLICATIONS) {
      const app = await create(domain.id, accessToken, name, type);
      idsByName[name] = app.id;
      if (!enabled) {
        await patchApplication(domain.id, accessToken, { enabled: false }, app.id);
      }
    }

    const [domainOwnerRole, applicationOwnerRole] = await Promise.all([
      findOrganizationRoleByName(accessToken, 'DOMAIN_OWNER', ListRolesTypeEnum.Domain),
      findOrganizationRoleByName(accessToken, 'APPLICATION_OWNER', ListRolesTypeEnum.Application),
    ]);

    const owner = await createPersona(accessToken, 'applistowner');
    personas.push(owner);
    const limited = await createPersona(accessToken, 'applistlimited');
    personas.push(limited);

    await addDomainMembership(domain.id, accessToken, userMembership(owner.userId, domainOwnerRole.id));
    for (const name of OWNED_NAMES) {
      await create(domain.id, owner.token, name, 'SERVICE');
    }
    for (const name of READABLE_NAMES) {
      await addApplicationMembership(domain.id, idsByName[name], accessToken, userMembership(limited.userId, applicationOwnerRole.id));
    }

    const token = accessToken;
    const domainId = domain.id;

    return {
      accessToken,
      domain,
      ownedNames: OWNED_NAMES,
      readableNames: READABLE_NAMES,
      owner,
      limited,
      list: async (query = {}, as = token) => readPage(await get(`${applicationsPath(domainId)}?${queryString(query)}`, as)),
      listIn: async (otherDomainId, query = {}) => readPage(await get(`${applicationsPath(otherDomainId)}?${queryString(query)}`, token)),
      listStatus: async (query) => (await get(`${applicationsPath(domainId)}?${queryString(query)}`, token)).status,
      follow: async (nextCursor, as = token) => readPage(await get(nextCursor, as)),
      domainWithApplications: async (count) => {
        const created = await newDomain();
        await Promise.all(
          Array.from({ length: count }, (_, index) => create(created.id, token, `bulk_${String(index).padStart(3, '0')}`, 'SERVICE')),
        );
        const page = await readPage(await get(`${applicationsPath(created.id)}?limit=1`, token));
        expect(page.totalCount).toBe(count);
        return created;
      },
      cleanUp,
    };
  } catch (error) {
    try {
      await cleanUp();
    } catch (cleanupError) {
      console.error('Failed to clean up after setup failure:', cleanupError);
    }
    throw error;
  }
};
