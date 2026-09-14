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
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';
import { CrossAppAccessFixture, setupCrossAppAccessFixture } from './fixtures/cross-app-access-fixture';

setup();

let fixture: CrossAppAccessFixture;

beforeAll(async () => {
  fixture = await setupCrossAppAccessFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Cross App Access - the block round-trips through the management API', () => {
  it('should store every part of the block and read it back unchanged', async () => {
    const created = await fixture.createTrustDomain({
      name: uniqueName('xaa-roundtrip'),
      domainIdentifier: 'https://issuer.example.com/roundtrip',
      tokenExchange: { enabled: true },
      keyMaterial: fixture.pemKeyMaterial,
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/roundtrip'),
    });

    const reloaded = await fixture.getTrustDomain(created.id);

    expect(reloaded.crossAppAccess.enabled).toBe(true);
    expect(reloaded.domainIdentifier).toBe('https://issuer.example.com/roundtrip');
    expect(reloaded.crossAppAccess.audSubMapping).toBe("{#context.attributes['user'].email}");
    expect(reloaded.crossAppAccess.scopeMappings).toEqual({ 'domain:read': 'calendar.read' });
    expect(reloaded.crossAppAccess.resourceServers).toHaveLength(1);
    expect(reloaded.crossAppAccess.resourceServers[0].name).toBe('Calendar');
    expect(reloaded.crossAppAccess.resourceServers[0].resource).toBe('https://calendar.example.com/roundtrip');
  });

  it('should generate a resource server id and keep it across a rename and a new resource', async () => {
    const created = await fixture.createTrustDomain({
      name: uniqueName('xaa-stable-id'),
      domainIdentifier: 'https://issuer.example.com/stable-id',
      tokenExchange: { enabled: true },
      keyMaterial: fixture.pemKeyMaterial,
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/stable-id'),
    });
    const generatedId = created.crossAppAccess.resourceServers[0].id;
    expect(generatedId).toBeTruthy();

    const updated = await fixture.updateTrustDomain(created.id, {
      domainIdentifier: 'https://issuer.example.com/stable-id',
      tokenExchange: { enabled: true },
      keyMaterial: fixture.pemKeyMaterial,
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/stable-id-moved', {
        resourceServers: [{ id: generatedId, name: 'Renamed Calendar', resource: 'https://calendar.example.com/stable-id-moved' }],
      }),
    });

    expect(updated.crossAppAccess.resourceServers[0].id).toBe(generatedId);
    expect(updated.crossAppAccess.resourceServers[0].name).toBe('Renamed Calendar');
    expect(updated.crossAppAccess.resourceServers[0].resource).toBe('https://calendar.example.com/stable-id-moved');
  });

  it('should keep the block when an update omits it', async () => {
    const created = await fixture.createTrustDomain({
      name: uniqueName('xaa-kept'),
      domainIdentifier: 'https://issuer.example.com/kept',
      tokenExchange: { enabled: true },
      keyMaterial: fixture.pemKeyMaterial,
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/kept'),
    });

    const updated = await fixture.updateTrustDomain(created.id, {
      domainIdentifier: 'https://issuer.example.com/kept',
      tokenExchange: { enabled: true },
      keyMaterial: fixture.pemKeyMaterial,
    });

    expect(updated.crossAppAccess.enabled).toBe(true);
  });

  it('should disable the block when an update sends it disabled', async () => {
    const created = await fixture.createTrustDomain({
      name: uniqueName('xaa-cleared'),
      domainIdentifier: 'https://issuer.example.com/cleared',
      tokenExchange: { enabled: true },
      keyMaterial: fixture.pemKeyMaterial,
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/cleared'),
    });

    const updated = await fixture.updateTrustDomain(created.id, {
      domainIdentifier: 'https://issuer.example.com/cleared',
      tokenExchange: { enabled: true },
      keyMaterial: fixture.pemKeyMaterial,
      crossAppAccess: { enabled: false },
    });

    expect(updated.crossAppAccess.enabled).toBe(false);
    expect(updated.crossAppAccess.resourceServers).toBeUndefined();
  });

  it('should read back an existing trusted domain with no block at all', async () => {
    const created = await fixture.createTrustDomain({
      name: uniqueName('xaa-absent'),
      domainIdentifier: 'https://issuer.example.com/absent',
      tokenExchange: { enabled: true },
      keyMaterial: fixture.pemKeyMaterial,
    });

    const reloaded = await fixture.getTrustDomain(created.id);

    expect(reloaded.crossAppAccess).toBeUndefined();
  });
});

describe('Cross App Access - a trusted domain AM only issues towards', () => {
  it('should accept a trusted domain with an identifier but no token exchange or key material', async () => {
    const authority = fixture.authorityIssuer();
    const created = await fixture.createTrustDomain({
      name: uniqueName('xaa-only'),
      domainIdentifier: authority,
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/xaa-only'),
    });

    const reloaded = await fixture.getTrustDomain(created.id);

    expect(reloaded.crossAppAccess.enabled).toBe(true);
    expect(reloaded.domainIdentifier).toBe(authority);
    expect(reloaded.tokenExchange).toBeUndefined();
    expect(reloaded.keyMaterial).toBeUndefined();
  });

  it('should not invent a SPIFFE trust domain named after the trusted domain', async () => {
    const created = await fixture.createTrustDomain({
      name: uniqueName('xaa-no-spiffe'),
      domainIdentifier: fixture.authorityIssuer(),
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/xaa-no-spiffe'),
    });

    const reloaded = await fixture.getTrustDomain(created.id);

    expect(reloaded.spiffe).toBeUndefined();
  });

  it('should keep key material that is already stored when the trusted domain is narrowed to Cross App Access only', async () => {
    const created = await fixture.createTrustDomain({
      name: uniqueName('xaa-narrowed'),
      domainIdentifier: 'https://issuer.example.com/narrowed',
      tokenExchange: { enabled: true },
      keyMaterial: fixture.pemKeyMaterial,
    });

    const updated = await fixture.updateTrustDomain(created.id, {
      domainIdentifier: 'https://issuer.example.com/narrowed',
      tokenExchange: { enabled: false },
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/narrowed'),
    });

    expect(updated.domainIdentifier).toBe('https://issuer.example.com/narrowed');
    expect(updated.tokenExchange).toBeUndefined();
    expect(updated.keyMaterial.source).toBe('pem');
  });

  it('should stop accepting this authority JWTs once token exchange is disabled', async () => {
    const authority = fixture.authorityIssuer();
    const created = await fixture.createTrustDomain({
      name: uniqueName('xaa-te-off'),
      domainIdentifier: authority,
      tokenExchange: { enabled: true, scopeMappings: { 'external:read': 'openid' } },
      keyMaterial: fixture.pemKeyMaterial,
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/te-off'),
    });
    expect(created.tokenExchange).toBeDefined();

    const updated = await fixture.updateTrustDomain(created.id, {
      domainIdentifier: authority,
      tokenExchange: { enabled: false },
      keyMaterial: fixture.pemKeyMaterial,
    });

    expect(updated.tokenExchange).toBeUndefined();
    expect(updated.domainIdentifier).toBe(authority);
    expect(updated.crossAppAccess.enabled).toBe(true);
  });

  it('should reject a disabled Cross App Access block when it is the only declared usage', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-disabled'),
        domainIdentifier: fixture.authorityIssuer(),
        crossAppAccess: { ...fixture.crossAppAccess('https://calendar.example.com/xaa-disabled'), enabled: false },
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject a trusted domain that declares no usage at all', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-nothing'),
        domainIdentifier: '  ',
        crossAppAccess: { ...fixture.crossAppAccess('https://calendar.example.com/xaa-nothing'), enabled: false },
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });
});

describe('Cross App Access - identifier and resource constraints', () => {
  it('should reject a resource that is not an absolute URI', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-relative'),
        domainIdentifier: fixture.authorityIssuer(),
        crossAppAccess: fixture.crossAppAccess('/calendar'),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject a blank resource', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-blank-resource'),
        domainIdentifier: fixture.authorityIssuer(),
        crossAppAccess: fixture.crossAppAccess('   '),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject Cross App Access enabled on a trusted domain that names no authority', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-no-identifier'),
        domainIdentifier: '  ',
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/no-identifier'),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject an identifier that is not an absolute URI when Cross App Access is enabled', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-relative-identifier'),
        domainIdentifier: '/auth',
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/relative-identifier'),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject an identifier already held by another trusted domain of the same security domain', async () => {
    const authority = fixture.authorityIssuer();
    await fixture.createTrustDomain({
      name: uniqueName('xaa-holder'),
      domainIdentifier: authority,
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/holder'),
    });

    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-claimant'),
        domainIdentifier: authority,
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/claimant'),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject the same resource twice within one trusted domain', async () => {
    const resource = 'https://calendar.example.com/repeated';
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-repeated'),
        domainIdentifier: fixture.authorityIssuer(),
        crossAppAccess: fixture.crossAppAccess(resource, {
          resourceServers: [
            { name: 'Calendar', resource },
            { name: 'Calendar again', resource },
          ],
        }),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject a resource server without a name', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-unnamed'),
        domainIdentifier: fixture.authorityIssuer(),
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/unnamed', {
          resourceServers: [{ name: '  ', resource: 'https://calendar.example.com/unnamed' }],
        }),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject a null resource server entry', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-null-entry'),
        domainIdentifier: fixture.authorityIssuer(),
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/null-entry', { resourceServers: [null] }),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject Cross App Access enabled without a single resource server', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-empty'),
        domainIdentifier: fixture.authorityIssuer(),
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/empty', { resourceServers: [] }),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });
});

describe('Cross App Access - outbound mapping constraints', () => {
  it('should reject an aud_sub expression whose syntax is invalid', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-bad-el'),
        domainIdentifier: fixture.authorityIssuer(),
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/bad-el', { audSubMapping: '{#user.email' }),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject a blank domain scope in the outbound scope mappings', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-blank-scope'),
        domainIdentifier: fixture.authorityIssuer(),
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/blank-scope', { scopeMappings: { '  ': 'calendar.read' } }),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });
});

describe('Cross App Access - the deprecated inline trusted issuers cannot delete what they cannot see', () => {
  it('should leave a Cross-App-Access-only trusted domain untouched when the deprecated list is written', async () => {
    const created = await fixture.createTrustDomain(
      {
        name: uniqueName('xaa-invisible'),
        domainIdentifier: fixture.authorityIssuer(),
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/invisible'),
      },
      fixture.legacyDomain.id,
    );

    await fixture.patchTrustedIssuers(fixture.legacyDomain.id, []);

    const reloaded = await fixture.getTrustDomain(created.id, fixture.legacyDomain.id);
    expect(reloaded.id).toBe(created.id);
    expect(reloaded.crossAppAccess.enabled).toBe(true);
  });

  it('should clear only token exchange on a dual-purpose trusted domain dropped from the deprecated list', async () => {
    const created = await fixture.createTrustDomain(
      {
        name: uniqueName('xaa-dual'),
        domainIdentifier: 'https://issuer.example.com/dual',
        tokenExchange: { enabled: true },
        keyMaterial: fixture.pemKeyMaterial,
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/dual'),
      },
      fixture.legacyDomain.id,
    );

    await fixture.patchTrustedIssuers(fixture.legacyDomain.id, []);

    const reloaded = await fixture.getTrustDomain(created.id, fixture.legacyDomain.id);
    expect(reloaded.id).toBe(created.id);
    expect(reloaded.domainIdentifier).toBe('https://issuer.example.com/dual');
    expect(reloaded.tokenExchange).toBeUndefined();
    expect(reloaded.crossAppAccess.enabled).toBe(true);
  });
});
