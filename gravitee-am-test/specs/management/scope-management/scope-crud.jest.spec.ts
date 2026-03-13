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
import { setupScopeManagementFixture, ScopeManagementFixture, SCOPE_TEST } from './fixtures/scope-management-fixture';
import { createScope, deleteScope, getAllScopes, getScope, patchScope, updateScope } from '@management-commands/scope-management-commands';

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

describe('Invalid Case', () => {
  describe('Malformed IconUri', () => {
    let graviteeScopeId: string;

    beforeAll(async () => {
      const scope = await createScope(fixture.domain.id, fixture.accessToken, {
        key: 'gravitee',
        name: 'gravitee',
        description: 'gravitee scope',
        iconUri: SCOPE_TEST.ICON_URI,
      });
      graviteeScopeId = scope.id;
    });

    it('should reject create with malformed iconUri', async () => {
      await expect(
        createScope(fixture.domain.id, fixture.accessToken, {
          key: 'shouldNotExists',
          name: 'should not be created',
          description: 'should not be created',
          iconUri: 'badUriFormat',
        }),
      ).rejects.toMatchObject({
        response: { status: 400 },
        message: expect.stringContaining('Icon uri claim is malformed :'),
      });
    });

    it('should create scope with valid iconUri', async () => {
      const scope = await getScope(fixture.domain.id, fixture.accessToken, graviteeScopeId);

      expect(scope).toBeDefined();
      expect(scope.key).toEqual('gravitee');
      expect(scope.discovery).toEqual(false);
      expect(scope.id).toBeDefined();
    });

    it('should reject update with malformed iconUri', async () => {
      await expect(
        updateScope(fixture.domain.id, fixture.accessToken, graviteeScopeId, {
          name: 'shouldNotBeUpdated',
          description: 'shouldNotBeUpdated',
          iconUri: 'malformedIconUri',
        }),
      ).rejects.toMatchObject({
        response: { status: 400 },
        message: expect.stringContaining('Icon uri claim is malformed :'),
      });
    });

    it('should reject patch with malformed iconUri', async () => {
      await expect(
        patchScope(fixture.domain.id, fixture.accessToken, graviteeScopeId, {
          name: 'shouldNotBeUpdated',
          description: 'shouldNotBeUpdated',
          iconUri: 'malformedIconUri',
        }),
      ).rejects.toMatchObject({
        response: { status: 400 },
        message: expect.stringContaining('Icon uri claim is malformed :'),
      });
    });

    it('should delete scope successfully', async () => {
      await deleteScope(fixture.domain.id, fixture.accessToken, graviteeScopeId);

      const allScopes = await getAllScopes(fixture.domain.id, fixture.accessToken);
      expect(allScopes.data.find((s) => s.id === graviteeScopeId)).toBeFalsy();
    });
  });

  it('should reject create with missing description', async () => {
    await expect(
      createScope(fixture.domain.id, fixture.accessToken, {
        key: 'admin',
        name: 'admin',
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringMatching(/\[description: (must not be null|ne doit pas être nul)\]/),
    });
  });

  it('should reject create with missing key', async () => {
    await expect(
      createScope(fixture.domain.id, fixture.accessToken, {
        name: 'no key scope',
        description: 'missing key',
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
    });
  });

  it('should reject create of already existing scope', async () => {
    await expect(
      createScope(fixture.domain.id, fixture.accessToken, {
        key: 'email',
        name: 'should not be created',
        description: 'should not be created',
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('A scope [email] already exists for domain'),
    });
  });

  it('should return 404 when getting non-existing scope', async () => {
    await expect(getScope(fixture.domain.id, fixture.accessToken, 'nonExistingScopeId')).rejects.toMatchObject({
      response: { status: 404 },
    });
  });

  it('should reject update of non-existing scope', async () => {
    await expect(
      updateScope(fixture.domain.id, fixture.accessToken, 'nonExistingScope', {
        name: 'shouldNotBeUpdated',
        description: 'shouldNotBeUpdated',
      }),
    ).rejects.toMatchObject({
      response: { status: 404 },
      message: expect.stringContaining('can not be found'),
    });
  });

  it('should reject update with missing name property', async () => {
    await expect(
      updateScope(fixture.domain.id, fixture.accessToken, 'nonExistingScope', {
        discovery: true,
        description: 'wallet information updated',
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringMatching(/\[name: (must not be null|ne doit pas être nul)\]/),
    });
  });

  it('should reject update with missing description property', async () => {
    const scope = await createScope(fixture.domain.id, fixture.accessToken, {
      key: 'updvaldesc',
      name: 'Update Validation',
      description: 'will try to update without description',
    });

    await expect(
      updateScope(fixture.domain.id, fixture.accessToken, scope.id, {
        name: 'Updated Name',
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
    });
  });

  it('should reject delete of non-existing scope', async () => {
    await expect(deleteScope(fixture.domain.id, fixture.accessToken, 'nonExistingScope')).rejects.toMatchObject({
      response: { status: 404 },
      message: expect.stringContaining('can not be found'),
    });
  });
});

describe('Nominal Case', () => {
  let adminScopeId: string;

  beforeAll(async () => {
    const scope = await createScope(fixture.domain.id, fixture.accessToken, {
      key: 'admin',
      name: 'admin',
      description: 'admin non public scope',
      iconUri: SCOPE_TEST.ICON_URI,
    });
    adminScopeId = scope.id;
  });

  it('should list default scopes and identify address scope', async () => {
    const allScopes = await getAllScopes(fixture.domain.id, fixture.accessToken);

    expect(allScopes).toBeDefined();
    expect(allScopes.data.length).toBeGreaterThan(0);

    const addressScope = allScopes.data.find((s) => s.key === 'address');
    expect(addressScope).toBeDefined();
    expect(addressScope.system).toEqual(true);
  });

  it('should verify the created scope properties', async () => {
    const scope = await getScope(fixture.domain.id, fixture.accessToken, adminScopeId);

    expect(scope).toBeDefined();
    expect(scope.key).toEqual('admin');
    expect(scope.discovery).toEqual(false);
    expect(scope.id).toBeDefined();
  });

  it('should get scope by id', async () => {
    const scope = await getScope(fixture.domain.id, fixture.accessToken, adminScopeId);

    expect(scope).toBeDefined();
    expect(scope.discovery).toEqual(false);
    expect(scope.name).toEqual('admin');
    expect(scope.description).toEqual('admin non public scope');
  });

  it('should patch scope', async () => {
    const patched = await patchScope(fixture.domain.id, fixture.accessToken, adminScopeId, {
      discovery: true,
      name: 'admin name patched',
      description: 'admin information patched',
    });

    expect(patched).toBeDefined();
    expect(patched.discovery).toEqual(true);
    expect(patched.name).toEqual('admin name patched');
    expect(patched.description).toEqual('admin information patched');
  });

  it('should update scope preserving discovery flag', async () => {
    // Set known state: discovery=true before testing that PUT without discovery preserves it
    await patchScope(fixture.domain.id, fixture.accessToken, adminScopeId, { discovery: true });

    const updated = await updateScope(fixture.domain.id, fixture.accessToken, adminScopeId, {
      name: 'admin name updated',
      description: 'admin information updated',
    });

    expect(updated).toBeDefined();
    // discovery should not be overridden when missing in the body
    expect(updated.discovery).toEqual(true);
    expect(updated.name).toEqual('admin name updated');
    expect(updated.description).toEqual('admin information updated');
  });

  it('should not change scope key on PUT update', async () => {
    const updated = await updateScope(fixture.domain.id, fixture.accessToken, adminScopeId, {
      name: 'key preservation test',
      description: 'key preservation test',
    });

    expect(updated.key).toEqual('admin');
  });

  it('should preserve all fields when patching with empty body', async () => {
    // Set known state so assertion values are deterministic
    await patchScope(fixture.domain.id, fixture.accessToken, adminScopeId, {
      name: 'known name',
      description: 'known description',
    });

    const patched = await patchScope(fixture.domain.id, fixture.accessToken, adminScopeId, {});

    expect(patched.name).toEqual('known name');
    expect(patched.description).toEqual('known description');
    expect(patched.key).toEqual('admin');
  });

  it('should delete scope', async () => {
    // Create a dedicated scope for deletion so this test doesn't affect others
    const scope = await createScope(fixture.domain.id, fixture.accessToken, {
      key: 'todelete',
      name: 'To Delete',
      description: 'scope created for deletion test',
    });

    await deleteScope(fixture.domain.id, fixture.accessToken, scope.id);

    const allScopes = await getAllScopes(fixture.domain.id, fixture.accessToken);
    expect(allScopes.data.find((s) => s.id === scope.id)).toBeFalsy();
  });

  describe('System scope protection', () => {
    let systemAddressScopeId: string;

    beforeAll(async () => {
      const allScopes = await getAllScopes(fixture.domain.id, fixture.accessToken);
      const addressScope = allScopes.data.find((s) => s.key === 'address');
      expect(addressScope).toBeDefined();
      systemAddressScopeId = addressScope.id;
    });

    it('should keep discovery=true when patching system scope with discovery=false', async () => {
      const patched = await patchScope(fixture.domain.id, fixture.accessToken, systemAddressScopeId, {
        discovery: false,
        description: 'Discovery should stay to true',
      });

      expect(patched).toBeDefined();
      expect(patched.discovery).toEqual(true);
    });

    it('should keep discovery=true when updating system scope with discovery=false', async () => {
      const updated = await updateScope(fixture.domain.id, fixture.accessToken, systemAddressScopeId, {
        discovery: false,
        name: 'Address',
        description: 'Discovery should stay to true',
      });

      expect(updated).toBeDefined();
      expect(updated.discovery).toEqual(true);
    });

    it('should not allow patching parameterized on system scope', async () => {
      const allScopes = await getAllScopes(fixture.domain.id, fixture.accessToken);
      const openidScope = allScopes.data.find((s) => s.key === 'openid');
      expect(openidScope).toBeDefined();

      const patched = await patchScope(fixture.domain.id, fixture.accessToken, openidScope.id, {
        parameterized: true,
      });

      expect(patched.parameterized).toEqual(false);
    });

    it('should not allow updating parameterized on system scope via PUT', async () => {
      const allScopes = await getAllScopes(fixture.domain.id, fixture.accessToken);
      const openidScope = allScopes.data.find((s) => s.key === 'openid');
      expect(openidScope).toBeDefined();

      const updated = await updateScope(fixture.domain.id, fixture.accessToken, openidScope.id, {
        name: 'OpenID Connect',
        description: 'OpenID Connect scope',
        parameterized: true,
      });

      expect(updated.parameterized).toEqual(false);
    });

    it('should reject deletion of a system scope', async () => {
      const allScopes = await getAllScopes(fixture.domain.id, fixture.accessToken);
      const openidScope = allScopes.data.find((s) => s.key === 'openid');
      expect(openidScope).toBeDefined();

      await expect(deleteScope(fixture.domain.id, fixture.accessToken, openidScope.id)).rejects.toMatchObject({
        response: { status: 400 },
      });
    });
  });
});
