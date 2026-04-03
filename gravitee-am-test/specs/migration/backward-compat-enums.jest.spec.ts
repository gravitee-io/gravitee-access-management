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
import { RoleApi } from '@management-apis/RoleApi';
import { DomainApi } from '@management-apis/DomainApi';
import { Configuration } from '@management-apis/runtime';

/**
 * AM-6174: Backward compatibility — enum filtering after upgrade/downgrade.
 *
 * When MAPI 4.10+ starts, upgraders create system roles with PROTECTED_RESOURCE
 * reference type. If the environment is then downgraded to a version that doesn't
 * know this enum, the roles endpoint must not crash — unknown enum values should
 * be filtered silently (EnumParsingUtils.safeValueOf).
 *
 * This test runs at every verify stage of the migration pipeline. No manual seeding
 * is needed — the PROTECTED_RESOURCE data is created automatically by upgraders.
 *
 * Placed under specs/migration/ — run via ci:migration by the migration tool.
 * Not included in ci:management:parallel or ci:gateway (normal CI jobs).
 */

let accessToken: string;
let roleApi: RoleApi;
let domainApi: DomainApi;
const orgId = process.env.AM_DEF_ORG_ID || 'DEFAULT';
const envId = process.env.AM_DEF_ENV_ID || 'DEFAULT';

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  const cfg = new Configuration({
    basePath: `${process.env.AM_MANAGEMENT_URL}/management`,
    accessToken: () => accessToken,
  });
  roleApi = new RoleApi(cfg);
  domainApi = new DomainApi(cfg);
});

describe('Backward compatibility — enum filtering (AM-6174)', () => {
  it('Organization roles endpoint returns 200', async () => {
    const roles = await roleApi.listRoles({ organizationId: orgId });
    expect(Array.isArray(roles)).toBe(true);
    expect(roles.length).toBeGreaterThan(0);
  });

  it('Organization roles contain expected system roles', async () => {
    const roles = await roleApi.listRoles({ organizationId: orgId });
    const roleNames = roles.map((r) => r.name);

    // These system roles exist in every AM version
    expect(roleNames).toContain('ORGANIZATION_USER');
    expect(roleNames).toContain('ORGANIZATION_OWNER');
  });

  it('Domain-level roles endpoint returns 200 for existing domain', async () => {
    const domainsPage = await domainApi.findDomains({
      organizationId: orgId,
      environmentId: envId,
      page: 0,
      size: 10,
    });

    const domains = domainsPage?.data || [];
    expect(domains.length).toBeGreaterThan(0);

    const domain = domains[0];
    const roles = await roleApi.findRoles({
      organizationId: orgId,
      environmentId: envId,
      domain: domain.id,
    });

    expect(roles).toBeDefined();
  });
});
