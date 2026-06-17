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
 * /domains/{domainKey}/identities endpoints — key-keyed, each PUT manages one IdP.
 */
import { afterAll, afterEach, beforeAll, describe, expect, it } from '@jest/globals';
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

const createdIdpKeys: string[] = [];

afterEach(async () => {
  while (createdIdpKeys.length) {
    await fixture.client.deleteIdentity(fixture.domainKey, createdIdpKeys.pop());
  }
});

type InlineUser = { username: string; password: string };
const defaultUsers: InlineUser[] = [{ username: 'testuser', password: 'Password1!' }];

/** Create an inline IdP via PUT and track its key for cleanup. */
async function createIdp(overrides: { key?: string; name?: string; users?: InlineUser[] } = {}) {
  const key = overrides.key ?? uniqueName('autoinline', true).toLowerCase();
  createdIdpKeys.push(key);
  const response = await fixture.client.putIdentity(
    fixture.domainKey,
    buildInlineIdpDef({ key, name: overrides.name, users: overrides.users ?? defaultUsers }),
  );
  return { key, response };
}

/** Create a system IdP via PUT and track its key for cleanup. */
async function createSystemIdp(key = uniqueName('autosysidp', true).toLowerCase()) {
  createdIdpKeys.push(key);
  const response = await fixture.client.putIdentity(fixture.domainKey, buildSystemAutomationDef(key));
  return { key, response };
}

describe('Automation API - Identity providers (resource under a domain)', () => {
  it('should list no identity providers when none exist', async () => {
    const response = await fixture.client.listIdentities(fixture.domainKey);
    expect(response.status).toBe(200);
    expect(response.body).toEqual([]);
  });

  it('should create an inline identity provider via PUT', async () => {
    const { key, response } = await createIdp();

    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(key);
    expect(response.body.name).toEqual(`Automation IDP ${key}`);
    expect(response.body.type).toEqual('inline-am-idp');
    // internal id / operational flags are intentionally not surfaced
    expect(response.body.id).toBeUndefined();
    expect(response.body.managedBy).toBeUndefined();
    expect(response.body.external).toBeUndefined();
    expect(response.body.system).toBe(false);
  });

  it('should round-trip the identity provider on GET', async () => {
    const { key } = await createIdp();

    const response = await fixture.client.getIdentity(fixture.domainKey, key);
    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(key);
    expect(response.body.name).toEqual(`Automation IDP ${key}`);
  });

  it('should list the identity provider under the domain', async () => {
    const { key } = await createIdp();

    const response = await fixture.client.listIdentities(fixture.domainKey);
    expect(response.status).toBe(200);
    expect(response.body).toEqual([expect.objectContaining({ key })]);
  });

  it('should update the identity provider via a second PUT (idempotent)', async () => {
    const { key } = await createIdp();

    const response = await fixture.client.putIdentity(
      fixture.domainKey,
      buildInlineIdpDef({
        key,
        name: `Automation IDP ${key} Updated`,
        users: [{ username: 'testuser2', password: 'Password2!' }],
      }),
    );

    expect(response.status).toBe(200);
    expect(response.body.name).toEqual(`Automation IDP ${key} Updated`);
  });

  it('should reject changing the type of an existing identity provider (400)', async () => {
    const { key } = await createIdp();

    const response = await fixture.client.putIdentity(fixture.domainKey, {
      ...buildInlineIdpDef({ key, users: defaultUsers }),
      type: 'ldap-am-idp',
    });
    expect(response.status).toBe(400);
  });

  it('should reject an update missing the required name (400)', async () => {
    const { key } = await createIdp();

    const response = await fixture.client.putIdentity(fixture.domainKey, {
      key,
      type: 'inline-am-idp',
      configuration: JSON.stringify({ users: defaultUsers }),
    });
    expect(response.status).toBe(400);
  });

  it('should reject an invalid key pattern (400)', async () => {
    const response = await fixture.client.putIdentity(
      fixture.domainKey,
      buildInlineIdpDef({ key: 'Invalid Key!', users: [{ username: 'u', password: 'P@ssword1' }] }),
    );
    expect(response.status).toBe(400);
  });

  it('should return 404 for an unknown identity provider key', async () => {
    const response = await fixture.client.getIdentity(fixture.domainKey, 'does-not-exist-xyz');
    expect(response.status).toBe(404);
  });

  it('should return 404 for identity providers of an unknown domain', async () => {
    const response = await fixture.client.listIdentities('no-such-domain-xyz');
    expect(response.status).toBe(404);
  });

  it('should delete the identity provider', async () => {
    const { key } = await createIdp();

    const del = await fixture.client.deleteIdentity(fixture.domainKey, key);
    expect(del.status).toBe(204);

    const get = await fixture.client.getIdentity(fixture.domainKey, key);
    expect(get.status).toBe(404);
  });
});

describe('Automation API - Domain - defaultIdentityProviderForRegistration reference', () => {
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
    const { key } = await createIdp({ users: [{ username: 'reg', password: 'P@ssword1' }] });

    const response = await fixture.client.putDomain(
      buildAutomationDomainDef({
        key: fixture.domainKey,
        accountSettings: { defaultIdentityProviderForRegistration: key },
      }),
    );
    expect(response.status).toBe(200);
    expect(response.body.accountSettings.defaultIdentityProviderForRegistration).toEqual(key);
  });
});

describe('Automation API - Identity providers - payload validation', () => {
  it('should reject an unknown identity provider type (400)', async () => {
    const response = await fixture.client.putIdentity(fixture.domainKey, {
      ...buildInlineIdpDef({ key: uniqueName('autobadtype', true).toLowerCase(), users: defaultUsers }),
      type: 'unknown-am-idp',
    });
    expect(response.status).toBe(400);
  });

  it('should reject a configuration that is not valid JSON (400)', async () => {
    const response = await fixture.client.putIdentity(fixture.domainKey, {
      ...buildInlineIdpDef({ key: uniqueName('autobadcfg', true).toLowerCase(), users: defaultUsers }),
      configuration: 'not-json',
    });
    expect(response.status).toBe(400);
  });

  it('should reject an omitted configuration (400)', async () => {
    const { configuration, ...withoutConfiguration } = buildInlineIdpDef({
      key: uniqueName('autonocfg', true).toLowerCase(),
      users: defaultUsers,
    });
    const response = await fixture.client.putIdentity(fixture.domainKey, withoutConfiguration);
    expect(response.status).toBe(400);
  });

  it('should reject an empty configuration (400)', async () => {
    const response = await fixture.client.putIdentity(fixture.domainKey, {
      ...buildInlineIdpDef({ key: uniqueName('autoemptycfg', true).toLowerCase(), users: defaultUsers }),
      configuration: '',
    });
    expect(response.status).toBe(400);
  });

  it('should tolerate an unknown extra property in the configuration (200)', async () => {
    const key = uniqueName('autoextracfg', true).toLowerCase();
    createdIdpKeys.push(key);
    const base = buildInlineIdpDef({ key, users: defaultUsers });
    const configuration = JSON.stringify({ ...JSON.parse(base.configuration), extraUnknownField: 'tolerated' });
    const response = await fixture.client.putIdentity(fixture.domainKey, { ...base, configuration });
    expect(response.status).toBe(200);
  });
});

describe('Automation API - System identity provider', () => {
  it('should create a system identity provider from a minimal {key, system:true} payload', async () => {
    const { key, response } = await createSystemIdp();
    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(key);
    expect(response.body.system).toBe(true);
  });

  it('should be idempotent on re-PUT of a system identity provider (200, no update)', async () => {
    const { key } = await createSystemIdp();

    const response = await fixture.client.putIdentity(fixture.domainKey, buildSystemAutomationDef(key));
    expect(response.status).toBe(200);
    expect(response.body.system).toBe(true);
    expect(response.body.key).toEqual(key);
  });

  it('should reject a second system identity provider (400)', async () => {
    await createSystemIdp();

    const response = await fixture.client.putIdentity(
      fixture.domainKey,
      buildSystemAutomationDef(uniqueName('autosysidp2', true).toLowerCase()),
    );
    expect(response.status).toBe(400);
  });

  it('should reject flipping system on an existing identity provider (400)', async () => {
    const { key } = await createSystemIdp();

    // the IdP was created with system:true; PUT it again as non-system -> rejected (immutable)
    const response = await fixture.client.putIdentity(
      fixture.domainKey,
      buildInlineIdpDef({ key, system: false, users: [{ username: 'def', password: 'P@ssword1' }] }),
    );
    expect(response.status).toBe(400);
  });

  it('should delete the system identity provider without a system guard', async () => {
    const { key } = await createSystemIdp();

    const del = await fixture.client.deleteIdentity(fixture.domainKey, key);
    expect(del.status).toBe(204);
  });
});
