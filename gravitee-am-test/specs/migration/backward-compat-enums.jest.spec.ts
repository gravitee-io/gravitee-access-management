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
import { getRoleApi, getDomainApi } from '@management-commands/service/utils';

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
const orgId = process.env.AM_DEF_ORG_ID || 'DEFAULT';
const envId = process.env.AM_DEF_ENV_ID || 'DEFAULT';

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
});

describe('Backward compatibility — enum filtering (AM-6174)', () => {
  it('Organization roles endpoint returns 200', async () => {
    const roles = await getRoleApi(accessToken).listRoles({ organizationId: orgId });
    expect(Array.isArray(roles)).toBe(true);
    expect(roles.length).toBeGreaterThan(0);
  });

  it('Organization roles contain expected system roles', async () => {
    const roles = await getRoleApi(accessToken).listRoles({ organizationId: orgId });
    const roleNames = roles.map((r) => r.name);

    // These system roles exist in every AM version
    expect(roleNames).toContain('ORGANIZATION_USER');
    expect(roleNames).toContain('ORGANIZATION_OWNER');
  });

  it('Domain-level roles endpoint returns 200 when domains exist', async () => {
    const domainsPage = await getDomainApi(accessToken).listDomains({
      organizationId: orgId,
      environmentId: envId,
      page: 0,
      size: 10,
    });

    const domains = domainsPage?.data || [];
    // In the full migration pipeline, seed runs before verify — domains should exist.
    // When running a single stage locally (--stage verify-baseline without seed), domains may be empty.
    if (domains.length === 0) {
      console.warn('No domains present — domain-level roles check skipped (run seed first for full coverage)');
      return;
    }

    const domain = domains[0];
    const roles = await getRoleApi(accessToken).findRoles({
      organizationId: orgId,
      environmentId: envId,
      domain: domain.id,
    });

    expect(Array.isArray(roles.data)).toBe(true);
  });
});
