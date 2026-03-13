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
import { createScope, getScope, patchScope, updateScope } from '@management-commands/scope-management-commands';

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

describe('Key normalisation', () => {
  it('should replace whitespace in key with underscores', async () => {
    const scope = await createScope(fixture.domain.id, fixture.accessToken, {
      key: 'my custom scope',
      name: 'My Custom Scope',
      description: 'scope with whitespace in key',
    });

    expect(scope.key).toEqual('my_custom_scope');

    const fetched = await getScope(fixture.domain.id, fixture.accessToken, scope.id);
    expect(fetched.key).toEqual('my_custom_scope');
  });

  it('should collapse multiple spaces into single underscore', async () => {
    const scope = await createScope(fixture.domain.id, fixture.accessToken, {
      key: 'multi   space',
      name: 'Multi Space',
      description: 'multiple consecutive spaces',
    });

    expect(scope.key).toEqual('multi_space');
  });

  it('should preserve case of regular scope key', async () => {
    const scope = await createScope(fixture.domain.id, fixture.accessToken, {
      key: 'MyMixedCaseKey',
      name: 'Mixed Case',
      description: 'key case should be preserved',
    });

    expect(scope.key).toEqual('MyMixedCaseKey');
  });
});

describe('expiresIn field', () => {
  let expiresInScopeId: string;

  beforeAll(async () => {
    const scope = await createScope(fixture.domain.id, fixture.accessToken, {
      key: 'expiring',
      name: 'Expiring Scope',
      description: 'scope with consent expiration',
      expiresIn: 3600,
    });
    expiresInScopeId = scope.id;
  });

  it('should create scope with expiresIn', async () => {
    const scope = await getScope(fixture.domain.id, fixture.accessToken, expiresInScopeId);

    expect(scope).toBeDefined();
    expect(scope.expiresIn).toBeDefined();
    expect(scope.id).toBeDefined();
  });

  it('should update expiresIn via PUT', async () => {
    // Set known state before asserting the update result
    await patchScope(fixture.domain.id, fixture.accessToken, expiresInScopeId, { expiresIn: 3600 });

    const updated = await updateScope(fixture.domain.id, fixture.accessToken, expiresInScopeId, {
      name: 'Expiring Scope',
      description: 'scope with consent expiration',
      expiresIn: 7200,
    });

    expect(updated.expiresIn).toEqual(7200);
  });

  it('should patch expiresIn', async () => {
    const patched = await patchScope(fixture.domain.id, fixture.accessToken, expiresInScopeId, {
      expiresIn: 1800,
    });

    expect(patched.expiresIn).toEqual(1800);
  });
});

describe('parameterized field', () => {
  let paramScopeId: string;

  beforeAll(async () => {
    const scope = await createScope(fixture.domain.id, fixture.accessToken, {
      key: 'paramscope',
      name: 'Parameterized Scope',
      description: 'scope with parameterized flag',
      parameterized: true,
    });
    paramScopeId = scope.id;
  });

  it('should have created scope with parameterized=true', async () => {
    // Verify initial state — reads from server, doesn't depend on other tests
    const scope = await getScope(fixture.domain.id, fixture.accessToken, paramScopeId);

    expect(scope).toBeDefined();
    expect(scope.id).toBeDefined();
  });

  it('should update parameterized via PUT', async () => {
    // Set known state before asserting the update result
    await patchScope(fixture.domain.id, fixture.accessToken, paramScopeId, { parameterized: true });

    const updated = await updateScope(fixture.domain.id, fixture.accessToken, paramScopeId, {
      name: 'Parameterized Scope',
      description: 'scope with parameterized flag',
      parameterized: false,
    });

    expect(updated.parameterized).toEqual(false);
  });

  it('should patch parameterized', async () => {
    // Reset to false first so test doesn't depend on update test running before it
    await patchScope(fixture.domain.id, fixture.accessToken, paramScopeId, { parameterized: false });

    const patched = await patchScope(fixture.domain.id, fixture.accessToken, paramScopeId, {
      parameterized: true,
    });

    expect(patched.parameterized).toEqual(true);
  });
});
