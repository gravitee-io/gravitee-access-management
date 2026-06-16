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

/**
 * The `id:<internalUuid>` addressing convention lets the Automation API read, update and delete
 * resources it did not create (managedBy = NONE), which therefore have a random internal id and no
 * automation key. Resolution by id bypasses the managedBy gate; it is update-only (never creates) and
 * leaves the resource's managedBy and (absent) automation key untouched.
 */
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { createDomain, deleteDomain } from '@management-commands/domain-management-commands';
import { getIdp } from '@management-commands/idp-management-commands';
import { setup } from '../../test-fixture';
import { BrownfieldFixture, setupBrownfieldFixture } from './fixtures/brownfield-fixture';

setup(120000);

let fixture: BrownfieldFixture;
const idRef = (uuid: string) => `id:${uuid}`;

beforeAll(async () => {
  fixture = await setupBrownfieldFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Automation API - brownfield id: addressing', () => {
  it('GET reaches a brownfield domain by id, bypassing managedBy', async () => {
    const response = await fixture.client.getDomain(idRef(fixture.domainId));
    expect(response.status).toBe(200);
    // a brownfield domain has no automation key
    expect(response.body.key).toBeUndefined();
  });

  it('GET reaches a brownfield identity provider by id under an id-addressed domain', async () => {
    const response = await fixture.client.getIdentity(idRef(fixture.domainId), idRef(fixture.idpId));
    expect(response.status).toBe(200);
    expect(response.body.name).toEqual('Brownfield LDAP');
    expect(response.body.key).toBeUndefined();
  });

  it('does not list a brownfield identity provider (list is automation-managed only)', async () => {
    const response = await fixture.client.listIdentities(idRef(fixture.domainId));
    expect(response.status).toBe(200);
    expect(response.body).toEqual([]);
  });

  it('PUT by id updates the brownfield identity provider in place, leaving managedBy and key untouched', async () => {
    const response = await fixture.client.putIdentity(idRef(fixture.domainId), {
      key: idRef(fixture.idpId),
      name: 'Brownfield LDAP renamed',
      type: 'inline-am-idp',
      configuration: JSON.stringify({
        users: [{ firstname: 'Test', lastname: 'User', username: 'brownfield-user', password: 'Password1!' }],
      }),
    });
    expect(response.status).toBe(200);
    expect(response.body.name).toEqual('Brownfield LDAP renamed');

    // verified through the legacy API: the rename applied and managedBy was left unchanged (NONE)
    const legacy = await getIdp(fixture.domainId, fixture.accessToken, fixture.idpId);
    expect(legacy.name).toEqual('Brownfield LDAP renamed');
    expect(legacy.managedBy === 'AUTOMATION_API').toBe(false);
  });

  it('PUT by id is update-only: an unknown id is 404, never a create', async () => {
    const response = await fixture.client.putIdentity(idRef(fixture.domainId), {
      key: idRef('00000000-0000-4000-8000-000000000000'),
      name: 'Nope',
      type: 'inline-am-idp',
      configuration: '{"users":[]}',
    });
    expect(response.status).toBe(404);
  });

  it('DELETE by id of an unknown id is an idempotent 204', async () => {
    const response = await fixture.client.deleteIdentity(idRef(fixture.domainId), idRef('00000000-0000-4000-8000-000000000000'));
    expect(response.status).toBe(204);
  });

  it('does not leak a brownfield child across domains (cross-scope is 404)', async () => {
    const otherDomain = await createDomain(fixture.accessToken, 'brownfield-other', 'Other brownfield domain');
    try {
      const response = await fixture.client.getIdentity(idRef(otherDomain.id), idRef(fixture.idpId));
      expect(response.status).toBe(404);
    } finally {
      await deleteDomain(otherDomain.id, fixture.accessToken);
    }
  });
});
