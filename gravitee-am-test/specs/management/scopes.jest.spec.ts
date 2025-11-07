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

import fetch from 'cross-fetch';
import * as faker from 'faker';
import { afterAll, beforeAll, expect } from '@jest/globals';
import { createDomain, safeDeleteDomain, setupDomainForTest, startDomain } from '@management-commands/domain-management-commands';

import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createScope,
  deleteScope,
  getAllScopes,
  getScope,
  getScopesPage,
  patchScope,
  updateScope,
} from '@management-commands/scope-management-commands';
import { uniqueName } from '@utils-commands/misc';

global.fetch = fetch;

jest.setTimeout(200000);

let accessToken;
let domain;
let scope;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  domain = await setupDomainForTest(uniqueName('scopes', true), { accessToken, waitForStart: false }).then((it) => it.domain);
});

describe('when using the scope commands', () => {
  it('must find all default scopes', async () => {
    const allScopes = await getAllScopes(domain.id, accessToken);

    expect(allScopes.currentPage).toEqual(0);
    expect(allScopes.totalCount).toEqual(14);
    expect(allScopes.data.length).toEqual(14);
  });

  for (let i = 0; i < 10; i++) {
    it('must create new scopes: ' + i, async () => {
      scope = await createScope(domain.id, accessToken, {
        key: 'scope' + i,
        name: 'scope' + i,
        description: 'scope' + i,
      });
    });
  }
});

describe('after creating scopes', () => {
  it('must find scope by id', async () => {
    const foundScope = await getScope(domain.id, accessToken, scope.id);

    expect(foundScope).toBeDefined();
    expect(foundScope.id).toEqual(scope.id);
    expect(foundScope.key).toEqual(scope.key);
    expect(foundScope.name).toEqual(scope.name);
    expect(foundScope.description).toEqual(scope.description);
  });

  it('must update scope', async () => {
    const updatedScope = await updateScope(domain.id, accessToken, scope.id, {
      key: 'new-scope-key',
      name: 'update-scope',
      description: 'update-scope',
    });
    expect(updatedScope).toBeDefined();
    expect(updatedScope.id).toEqual(scope.id);
    expect(updatedScope.key).toEqual(scope.key);
    expect(updatedScope.name).toEqual('update-scope');
    expect(updatedScope.description).toEqual('update-scope');
    scope = updatedScope;
  });

  it('must patch scope', async () => {
    const patchedScope = await patchScope(domain.id, accessToken, scope.id, {
      name: 'patch-scope',
    });
    expect(patchedScope).toBeDefined();
    expect(patchedScope.id).toEqual(scope.id);
    expect(patchedScope.name).toEqual('patch-scope');
    expect(patchedScope.description).toEqual(scope.description);
    scope = patchedScope;
  });

  it('must find all scopes', async () => {
    const allScopes = await getAllScopes(domain.id, accessToken);

    expect(allScopes.currentPage).toEqual(0);
    expect(allScopes.totalCount).toEqual(24);
    expect(allScopes.data.length).toEqual(24);
  });

  it('must find scope page', async () => {
    const scopePage = await getScopesPage(domain.id, accessToken, 1, 3);

    expect(scopePage.currentPage).toEqual(1);
    expect(scopePage.totalCount).toEqual(24);
    expect(scopePage.data.length).toEqual(3);
  });

  it('must find last scope page', async () => {
    const scopePage = await getScopesPage(domain.id, accessToken, 11, 2);

    expect(scopePage.currentPage).toEqual(11);
    expect(scopePage.totalCount).toEqual(24);
    expect(scopePage.data.length).toEqual(2);
  });

  it('Must delete scope', async () => {
    await deleteScope(domain.id, accessToken, scope.id);
    const scopePage = await getScopesPage(domain.id, accessToken);

    expect(scopePage.currentPage).toEqual(0);
    expect(scopePage.totalCount).toEqual(23);
    expect(scopePage.data.length).toEqual(23);
    expect(scopePage.data.find((s) => s.id === scope.id)).toBeFalsy();
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});
