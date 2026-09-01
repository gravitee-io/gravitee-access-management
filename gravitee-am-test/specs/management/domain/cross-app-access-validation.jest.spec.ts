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
      issuer: 'https://issuer.example.com/roundtrip',
      keyMaterial: fixture.pemKeyMaterial,
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/roundtrip'),
    });

    const reloaded = await fixture.getTrustDomain(created.id);

    expect(reloaded.crossAppAccess.enabled).toBe(true);
    expect(reloaded.crossAppAccess.audience).toMatch(/^https:\/\/auth\.example\.com\/as-\d+$/);
    expect(reloaded.crossAppAccess.audSubMapping).toBe('{#user.email}');
    expect(reloaded.crossAppAccess.scopeMappings).toEqual({ 'domain:read': 'calendar.read' });
    expect(reloaded.crossAppAccess.resourceServers).toHaveLength(1);
    expect(reloaded.crossAppAccess.resourceServers[0].name).toBe('Calendar');
    expect(reloaded.crossAppAccess.resourceServers[0].resource).toBe('https://calendar.example.com/roundtrip');
  });

  it('should generate a resource server id and keep it across a rename and a new resource', async () => {
    const created = await fixture.createTrustDomain({
      name: uniqueName('xaa-stable-id'),
      issuer: 'https://issuer.example.com/stable-id',
      keyMaterial: fixture.pemKeyMaterial,
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/stable-id'),
    });
    const generatedId = created.crossAppAccess.resourceServers[0].id;
    expect(generatedId).toBeTruthy();

    const updated = await fixture.updateTrustDomain(created.id, {
      issuer: 'https://issuer.example.com/stable-id',
      keyMaterial: fixture.pemKeyMaterial,
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/stable-id-moved', {
        resourceServers: [{ id: generatedId, name: 'Renamed Calendar', resource: 'https://calendar.example.com/stable-id-moved' }],
      }),
    });

    expect(updated.crossAppAccess.resourceServers[0].id).toBe(generatedId);
    expect(updated.crossAppAccess.resourceServers[0].name).toBe('Renamed Calendar');
    expect(updated.crossAppAccess.resourceServers[0].resource).toBe('https://calendar.example.com/stable-id-moved');
  });

  it('should clear the block when an update omits it', async () => {
    const created = await fixture.createTrustDomain({
      name: uniqueName('xaa-cleared'),
      issuer: 'https://issuer.example.com/cleared',
      keyMaterial: fixture.pemKeyMaterial,
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/cleared'),
    });

    const updated = await fixture.updateTrustDomain(created.id, {
      issuer: 'https://issuer.example.com/cleared',
      keyMaterial: fixture.pemKeyMaterial,
    });

    expect(updated.crossAppAccess).toBeUndefined();
  });

  it('should read back an existing trusted domain with no block at all', async () => {
    const created = await fixture.createTrustDomain({
      name: uniqueName('xaa-absent'),
      issuer: 'https://issuer.example.com/absent',
      keyMaterial: fixture.pemKeyMaterial,
    });

    const reloaded = await fixture.getTrustDomain(created.id);

    expect(reloaded.crossAppAccess).toBeUndefined();
  });
});

describe('Cross App Access - a trusted domain AM only issues towards', () => {
  it('should accept a trusted domain with neither an issuer nor key material', async () => {
    const created = await fixture.createTrustDomain({
      name: uniqueName('xaa-only'),
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/xaa-only'),
    });

    const reloaded = await fixture.getTrustDomain(created.id);

    expect(reloaded.crossAppAccess.enabled).toBe(true);
    expect(reloaded.issuer).toBeUndefined();
    expect(reloaded.keyMaterial).toBeUndefined();
  });

  it('should not invent a SPIFFE trust domain named after the trusted domain', async () => {
    const created = await fixture.createTrustDomain({
      name: uniqueName('xaa-no-spiffe'),
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/xaa-no-spiffe'),
    });

    const reloaded = await fixture.getTrustDomain(created.id);

    expect(reloaded.spiffeTrustDomain).toBeUndefined();
  });

  it('should keep key material that is already stored when the trusted domain is narrowed to Cross App Access only', async () => {
    const created = await fixture.createTrustDomain({
      name: uniqueName('xaa-narrowed'),
      issuer: 'https://issuer.example.com/narrowed',
      keyMaterial: fixture.pemKeyMaterial,
    });

    const updated = await fixture.updateTrustDomain(created.id, {
      issuer: '',
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/narrowed'),
    });

    expect(updated.issuer).toBeUndefined();
    expect(updated.keyMaterial.source).toBe('pem');
  });

  it('should reject a disabled Cross App Access block rather than fall back to a SPIFFE trust domain', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-disabled'),
        crossAppAccess: { ...fixture.crossAppAccess('https://calendar.example.com/xaa-disabled'), enabled: false },
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject a trusted domain that declares no usage at all', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-nothing'),
        issuer: '  ',
        crossAppAccess: { ...fixture.crossAppAccess('https://calendar.example.com/xaa-nothing'), enabled: false },
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });
});

describe('Cross App Access - audience and resource constraints', () => {
  it('should reject a resource that is not an absolute URI', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-relative'),
        crossAppAccess: fixture.crossAppAccess('/calendar'),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject a blank resource', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-blank-resource'),
        crossAppAccess: fixture.crossAppAccess('   '),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject a Cross App Access block that declares no audience', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-no-audience'),
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/no-audience', { audience: '  ' }),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject an audience that is not an absolute URI', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-relative-audience'),
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/relative-audience', { audience: '/auth' }),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject an audience already registered on another trusted domain of the same security domain', async () => {
    const audience = 'https://auth.example.com/taken';
    await fixture.createTrustDomain({
      name: uniqueName('xaa-holder'),
      crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/holder', { audience }),
    });

    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-claimant'),
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/claimant', { audience }),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject the same resource twice within one trusted domain', async () => {
    const resource = 'https://calendar.example.com/repeated';
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-repeated'),
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
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/null-entry', { resourceServers: [null] }),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject Cross App Access enabled without a single resource server', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-empty'),
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
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/bad-el', { audSubMapping: '{#user.email' }),
      }),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should reject a blank domain scope in the outbound scope mappings', async () => {
    await expect(
      fixture.createTrustDomain({
        name: uniqueName('xaa-blank-scope'),
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
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/invisible'),
      },
      fixture.legacyDomain.id,
    );

    await fixture.patchTrustedIssuers(fixture.legacyDomain.id, []);

    const reloaded = await fixture.getTrustDomain(created.id, fixture.legacyDomain.id);
    expect(reloaded.id).toBe(created.id);
    expect(reloaded.crossAppAccess.enabled).toBe(true);
  });

  it('should clear only the issuer of a dual-purpose trusted domain dropped from the deprecated list', async () => {
    const created = await fixture.createTrustDomain(
      {
        name: uniqueName('xaa-dual'),
        issuer: 'https://issuer.example.com/dual',
        keyMaterial: fixture.pemKeyMaterial,
        crossAppAccess: fixture.crossAppAccess('https://calendar.example.com/dual'),
      },
      fixture.legacyDomain.id,
    );

    await fixture.patchTrustedIssuers(fixture.legacyDomain.id, []);

    const reloaded = await fixture.getTrustDomain(created.id, fixture.legacyDomain.id);
    expect(reloaded.id).toBe(created.id);
    expect(reloaded.issuer).toBeUndefined();
    expect(reloaded.crossAppAccess.enabled).toBe(true);
  });
});
