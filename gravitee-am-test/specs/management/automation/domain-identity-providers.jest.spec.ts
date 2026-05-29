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
 * Identity providers are managed individually under a domain via the
 * /domains/{domainKey}/identity-providers endpoints — key-keyed, each PUT manages one IdP.
 */
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';
import {
  AutomationDomainFixture,
  setupAutomationDomainFixture,
} from './fixtures/automation-domain-fixture';
import { buildAutomationDomainDef, buildInlineIdpDef, buildSystemAutomationDef } from './fixtures/automation-definitions';

setup(120000);

let fixture: AutomationDomainFixture;

beforeAll(async () => {
  fixture = await setupAutomationDomainFixture({ keyPrefix: 'autodomidp' });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Automation API - Identity providers (resource under a domain)', () => {
  const idpKey = uniqueName('autoinline', true).toLowerCase();

  it('should expose no automation-managed identity providers on a freshly-created domain', async () => {
    const response = await fixture.client.listIdentityProviders(fixture.domainKey);
    expect(response.status).toBe(200);
    expect(response.body).toEqual([]);
  });

  it('should create an inline identity provider via PUT', async () => {
    const response = await fixture.client.putIdentityProvider(
      fixture.domainKey,
      buildInlineIdpDef({ key: idpKey, users: [{ username: 'testuser', password: 'Password1!' }] }),
    );

    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(idpKey);
    expect(response.body.name).toEqual(`Automation IDP ${idpKey}`);
    expect(response.body.type).toEqual('inline-am-idp');
    // internal id / operational flags are intentionally not surfaced
    expect(response.body.id).toBeUndefined();
    expect(response.body.managedBy).toBeUndefined();
    expect(response.body.system).toBe(false);
  });

  it('should round-trip the identity provider on GET', async () => {
    const response = await fixture.client.getIdentityProvider(fixture.domainKey, idpKey);
    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(idpKey);
    expect(response.body.name).toEqual(`Automation IDP ${idpKey}`);
  });

  it('should list the identity provider under the domain', async () => {
    const response = await fixture.client.listIdentityProviders(fixture.domainKey);
    expect(response.status).toBe(200);
    expect(response.body).toEqual([expect.objectContaining({ key: idpKey })]);
  });

  it('should update the identity provider via a second PUT (idempotent)', async () => {
    const response = await fixture.client.putIdentityProvider(
      fixture.domainKey,
      buildInlineIdpDef({
        key: idpKey,
        name: `Automation IDP ${idpKey} Updated`,
        users: [{ username: 'testuser2', password: 'Password2!' }],
      }),
    );

    expect(response.status).toBe(200);
    expect(response.body.name).toEqual(`Automation IDP ${idpKey} Updated`);
  });

  it('should reject an invalid key pattern (400)', async () => {
    const response = await fixture.client.putIdentityProvider(
      fixture.domainKey,
      buildInlineIdpDef({ key: 'Invalid Key!', users: [{ username: 'u', password: 'P@ssword1' }] }),
    );
    expect(response.status).toBe(400);
  });

  it('should return 404 for an unknown identity provider key', async () => {
    const response = await fixture.client.getIdentityProvider(fixture.domainKey, 'does-not-exist-xyz');
    expect(response.status).toBe(404);
  });

  it('should return 404 for identity providers of an unknown domain', async () => {
    const response = await fixture.client.listIdentityProviders('no-such-domain-xyz');
    expect(response.status).toBe(404);
  });

  it('should delete the identity provider', async () => {
    const del = await fixture.client.deleteIdentityProvider(fixture.domainKey, idpKey);
    expect(del.status).toBe(204);

    const get = await fixture.client.getIdentityProvider(fixture.domainKey, idpKey);
    expect(get.status).toBe(404);
  });
});

describe('Automation API - Domain - defaultIdentityProviderForRegistration reference', () => {
  const idpKey = uniqueName('autodefidp', true).toLowerCase();

  it('should accept a null reference on the initial domain PUT', async () => {
    const response = await fixture.client.putDomain(
      buildAutomationDomainDef({ key: fixture.domainKey, accountSettings: { inherited: false } }),
    );
    expect(response.status).toBe(200);
    expect(response.body.accountSettings?.defaultIdentityProviderForRegistration ?? null).toBeNull();
  });

  it('should accept a reference to a not-yet-created identity provider (eventual consistency)', async () => {
    const response = await fixture.client.putDomain(
      buildAutomationDomainDef({
        key: fixture.domainKey,
        accountSettings: { defaultIdentityProviderForRegistration: 'not-created-yet' },
      }),
    );
    // the reference is accepted and the key round-trips even though the IdP does not exist yet
    expect(response.status).toBe(200);
    expect(response.body.accountSettings.defaultIdentityProviderForRegistration).toEqual('not-created-yet');
  });

  it('should resolve a reference to a pre-created identity provider', async () => {
    const created = await fixture.client.putIdentityProvider(
      fixture.domainKey,
      buildInlineIdpDef({ key: idpKey, users: [{ username: 'reg', password: 'P@ssword1' }] }),
    );
    expect(created.status).toBe(200);

    const response = await fixture.client.putDomain(
      buildAutomationDomainDef({
        key: fixture.domainKey,
        accountSettings: { defaultIdentityProviderForRegistration: idpKey },
      }),
    );
    expect(response.status).toBe(200);
    expect(response.body.accountSettings.defaultIdentityProviderForRegistration).toEqual(idpKey);
  });
});

describe('Automation API - System identity provider', () => {
  const systemIdpKey = uniqueName('autosysidp', true).toLowerCase();
  const secondSystemKey = uniqueName('autosysidp2', true).toLowerCase();

  it('should create a system identity provider from a minimal {key, system:true} payload', async () => {
    const response = await fixture.client.putIdentityProvider(
      fixture.domainKey,
      buildSystemAutomationDef(systemIdpKey),
    );
    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(systemIdpKey);
    expect(response.body.system).toBe(true);
  });

  it('should be idempotent on re-PUT of a system identity provider (200, no update)', async () => {
    const response = await fixture.client.putIdentityProvider(
      fixture.domainKey,
      buildSystemAutomationDef(systemIdpKey),
    );
    expect(response.status).toBe(200);
    expect(response.body.system).toBe(true);
    expect(response.body.key).toEqual(systemIdpKey);
  });

  it('should reject a second system identity provider (400)', async () => {
    const response = await fixture.client.putIdentityProvider(
      fixture.domainKey,
      buildSystemAutomationDef(secondSystemKey),
    );
    expect(response.status).toBe(400);
  });

  it('should reject flipping system on an existing identity provider (400)', async () => {
    // systemIdpKey was created with system:true; PUT it again as non-system -> rejected (immutable)
    const response = await fixture.client.putIdentityProvider(
      fixture.domainKey,
      buildInlineIdpDef({ key: systemIdpKey, system: false, users: [{ username: 'def', password: 'P@ssword1' }] }),
    );
    expect(response.status).toBe(400);
  });

  it('should delete the system identity provider without a system guard', async () => {
    const del = await fixture.client.deleteIdentityProvider(fixture.domainKey, systemIdpKey);
    expect(del.status).toBe(204);
  });
});
