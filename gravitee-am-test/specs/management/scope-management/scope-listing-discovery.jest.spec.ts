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
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { setup } from '../../test-fixture';
import { setupScopeManagementFixture, ScopeManagementFixture } from './fixtures/scope-management-fixture';
import {
  createScope,
  getAllScopes,
  getScope,
  getScopesPage,
  patchScope,
  searchScopes,
} from '@management-commands/scope-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { getWellKnownOpenIdConfiguration } from '@gateway-commands/oauth-oidc-commands';

setup(200000);

let fixture: ScopeManagementFixture;

beforeAll(async () => {
  fixture = await setupScopeManagementFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Pagination', () => {
  const PAGINATION_SCOPE_COUNT = 10;

  beforeAll(async () => {
    for (let i = 0; i < PAGINATION_SCOPE_COUNT; i++) {
      await createScope(fixture.domain.id, fixture.accessToken, {
        key: `pagtest${i}`,
        name: `pagtest${i}`,
        description: `pagination test scope ${i}`,
      });
    }
  });

  it('should return all scopes with default pagination', async () => {
    const allScopes = await getAllScopes(fixture.domain.id, fixture.accessToken);

    expect(allScopes.currentPage).toEqual(0);
    // 14 system/default scopes + 10 created above
    expect(allScopes.totalCount).toBeGreaterThanOrEqual(24);
    expect(allScopes.data.length).toEqual(Number(allScopes.totalCount));
  });

  it('should return a specific page with given size', async () => {
    const scopePage = await getScopesPage(fixture.domain.id, fixture.accessToken, 1, 3);

    expect(scopePage.currentPage).toEqual(1);
    expect(scopePage.data.length).toEqual(3);
  });

  it('should return last page with remaining items', async () => {
    const allScopes = await getAllScopes(fixture.domain.id, fixture.accessToken);
    const totalCount = Number(allScopes.totalCount);
    const pageSize = 2;
    const lastPage = Math.floor((totalCount - 1) / pageSize);

    const scopePage = await getScopesPage(fixture.domain.id, fixture.accessToken, lastPage, pageSize);

    expect(scopePage.currentPage).toEqual(lastPage);
    expect(scopePage.data.length).toBeGreaterThan(0);
    expect(scopePage.data.length).toBeLessThanOrEqual(pageSize);
  });

  it('should return scopes sorted by key ascending', async () => {
    const allScopes = await getAllScopes(fixture.domain.id, fixture.accessToken);
    const keys = allScopes.data.map((s) => s.key);
    const sorted = [...keys].sort();

    expect(keys).toEqual(sorted);
  });

  it('should return empty data for out-of-range page', async () => {
    const scopePage = await getScopesPage(fixture.domain.id, fixture.accessToken, 9999, 10);

    expect(scopePage.currentPage).toEqual(9999);
    expect(scopePage.data.length).toEqual(0);
  });
});

describe('Search (q parameter)', () => {
  beforeAll(async () => {
    await createScope(fixture.domain.id, fixture.accessToken, {
      key: 'uniquesearchable',
      name: 'Unique Searchable Scope',
      description: 'scope for search tests',
    });
  });

  it('should find scopes matching search query', async () => {
    const result = await searchScopes(fixture.domain.id, fixture.accessToken, 'uniquesearchable');

    expect(result).toBeDefined();
    expect(result.totalCount).toBeGreaterThanOrEqual(1);
    expect(result.data.some((s) => s.key === 'uniquesearchable')).toBe(true);
  });

  it('should return empty results for non-matching query', async () => {
    const result = await searchScopes(fixture.domain.id, fixture.accessToken, 'zzz_nonexistent_scope_zzz');

    expect(result).toBeDefined();
    expect(result.totalCount).toEqual(0);
    expect(result.data.length).toEqual(0);
  });
});

describe('Scope Discovery', () => {
  let walletScopeId: string;

  beforeAll(async () => {
    await createScope(fixture.domain.id, fixture.accessToken, {
      key: 'discadmin',
      name: 'discadmin',
      description: 'admin non public scope',
    });

    await createScope(fixture.domain.id, fixture.accessToken, {
      key: 'body',
      name: 'body',
      description: 'body information',
      discovery: true,
    });

    const wallet = await createScope(fixture.domain.id, fixture.accessToken, {
      key: 'wallet',
      name: 'wallet',
      description: 'wallet information',
    });
    walletScopeId = wallet.id;
  });

  it('should create admin scope with discovery=false by default', async () => {
    const allScopes = await getAllScopes(fixture.domain.id, fixture.accessToken);
    const discadmin = allScopes.data.find((s) => s.key === 'discadmin');

    expect(discadmin).toBeDefined();
    expect(discadmin.discovery).toEqual(false);
  });

  it('should create body scope with discovery=true', async () => {
    const allScopes = await getAllScopes(fixture.domain.id, fixture.accessToken);
    const body = allScopes.data.find((s) => s.key === 'body');

    expect(body).toBeDefined();
    expect(body.key).toEqual('body');
    expect(body.discovery).toEqual(true);
  });

  it('should create wallet scope with discovery=false by default', async () => {
    const scope = await getScope(fixture.domain.id, fixture.accessToken, walletScopeId);

    expect(scope).toBeDefined();
    expect(scope.key).toEqual('wallet');
    expect(scope.discovery).toEqual(false);
  });

  it('should patch wallet scope to set discovery=true', async () => {
    const patched = await patchScope(fixture.domain.id, fixture.accessToken, walletScopeId, {
      discovery: true,
      description: 'wallet information updated',
    });

    expect(patched).toBeDefined();
    expect(patched.discovery).toEqual(true);
    expect(patched.name).toEqual('wallet');
    expect(patched.description).toEqual('wallet information updated');
  });

  it('should expose only discovery=true scopes in well-known openid-configuration', async () => {
    await waitForSyncAfter(fixture.domain.id, () => patchScope(fixture.domain.id, fixture.accessToken, walletScopeId, { discovery: true }));

    const response = await getWellKnownOpenIdConfiguration(fixture.domain.hrid);

    expect(response.status).toBe(200);
    // System scopes (discovery=true by default) + custom scopes with discovery=true (body, wallet)
    // discadmin has discovery=false and must not appear
    expect(response.body.scopes_supported).toEqual([
      'address',
      'body',
      'email',
      'full_profile',
      'groups',
      'offline_access',
      'openid',
      'phone',
      'profile',
      'roles',
      'wallet',
    ]);
  });
});
