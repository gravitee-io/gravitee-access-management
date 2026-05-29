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
 * Certificates are managed individually under a domain via the
 * /domains/{domainKey}/certificates endpoints — key-keyed, each PUT manages one certificate.
 * Domain SAML settings reference one of them by `key`, resolved with eventual consistency.
 */
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';
import { AutomationDomainFixture, setupAutomationDomainFixture } from './fixtures/automation-domain-fixture';
import { buildAutomationCertificateDef, buildAutomationDomainDef, buildSystemAutomationDef } from './fixtures/automation-definitions';

setup(120000);

let fixture: AutomationDomainFixture;

beforeAll(async () => {
  fixture = await setupAutomationDomainFixture({ keyPrefix: 'autocert' });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Automation API - Certificates (resource under a domain)', () => {
  const certKey = uniqueName('autosign', true).toLowerCase();

  it('should expose no automation-managed certificates on a freshly-created domain', async () => {
    const response = await fixture.client.listCertificates(fixture.domainKey);
    expect(response.status).toBe(200);
    expect(response.body).toEqual([]);
  });

  it('should create a certificate via PUT', async () => {
    const response = await fixture.client.putCertificate(
      fixture.domainKey,
      buildAutomationCertificateDef({ key: certKey }),
    );

    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(certKey);
    // internal id / operational flags are intentionally not surfaced
    expect(response.body.id).toBeUndefined();
    expect(response.body.managedBy).toBeUndefined();
    // a non-system certificate
    expect(response.body.system ?? false).toBe(false);
  });

  it('should round-trip the certificate on GET', async () => {
    const response = await fixture.client.getCertificate(fixture.domainKey, certKey);
    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(certKey);
  });

  it('should update the certificate via a second PUT (idempotent)', async () => {
    const response = await fixture.client.putCertificate(
      fixture.domainKey,
      buildAutomationCertificateDef({ key: certKey, name: 'Renamed cert' }),
    );
    expect(response.status).toBe(200);
    expect(response.body.name).toEqual('Renamed cert');
  });

  it('should reject an invalid key pattern (400)', async () => {
    const response = await fixture.client.putCertificate(
      fixture.domainKey,
      buildAutomationCertificateDef({ key: 'Invalid Key!' }),
    );
    expect(response.status).toBe(400);
  });

  it('should return 404 for an unknown certificate key', async () => {
    const response = await fixture.client.getCertificate(fixture.domainKey, 'does-not-exist-xyz');
    expect(response.status).toBe(404);
  });

  it('should return 404 for certificates of an unknown domain', async () => {
    const response = await fixture.client.listCertificates('no-such-domain-xyz');
    expect(response.status).toBe(404);
  });

  it('should delete the certificate', async () => {
    // also frees its keystore alias, which is unique per domain among non-default certificates
    const del = await fixture.client.deleteCertificate(fixture.domainKey, certKey);
    expect(del.status).toBe(204);

    const get = await fixture.client.getCertificate(fixture.domainKey, certKey);
    expect(get.status).toBe(404);
  });
});

describe('Automation API - System certificate', () => {
  const systemKey = uniqueName('autosyscert', true).toLowerCase();
  const secondSystemKey = uniqueName('autosyscert2', true).toLowerCase();

  it('should create a system certificate from a minimal {key, system:true} payload', async () => {
    const response = await fixture.client.putCertificate(
      fixture.domainKey,
      buildSystemAutomationDef(systemKey),
    );
    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(systemKey);
    expect(response.body.system).toBe(true);
  });

  it('should be idempotent on re-PUT of a system certificate (200, no update)', async () => {
    const response = await fixture.client.putCertificate(
      fixture.domainKey,
      buildSystemAutomationDef(systemKey),
    );
    expect(response.status).toBe(200);
    expect(response.body.system).toBe(true);
    expect(response.body.key).toEqual(systemKey);
  });

  it('should reject a second system certificate (400)', async () => {
    const response = await fixture.client.putCertificate(
      fixture.domainKey,
      buildSystemAutomationDef(secondSystemKey),
    );
    expect(response.status).toBe(400);
  });

  it('should reject flipping system on an existing certificate (400)', async () => {
    // the system cert was created with system:true; PUT it again as non-system -> rejected (immutable)
    const response = await fixture.client.putCertificate(
      fixture.domainKey,
      buildAutomationCertificateDef({ key: systemKey, system: false }),
    );
    expect(response.status).toBe(400);
  });

  it('should delete the system certificate without a system guard', async () => {
    const del = await fixture.client.deleteCertificate(fixture.domainKey, systemKey);
    expect(del.status).toBe(204);

    const get = await fixture.client.getCertificate(fixture.domainKey, systemKey);
    expect(get.status).toBe(404);
  });
});

describe('Automation API - Domain SAML certificate reference (by key)', () => {
  const samlCertKey = uniqueName('autosaml', true).toLowerCase();

  const domainDefinition = (certificateKey: string | null) =>
    buildAutomationDomainDef({
      key: fixture.domainKey,
      saml: {
        enabled: true,
        entityId: `https://idp.example.com/${fixture.domainKey}`,
        certificate: certificateKey,
      },
    });

  it('should accept a SAML reference to a not-yet-created certificate (eventual consistency)', async () => {
    const response = await fixture.client.putDomain(domainDefinition(samlCertKey));
    expect(response.status).toBe(200);
    // the key round-trips even though the certificate does not exist yet
    expect(response.body.saml.certificate).toEqual(samlCertKey);
  });

  it('should keep the SAML reference once the certificate is created', async () => {
    // self-contained (no dependency on the previous test): establish the reference, create the
    // certificate, then confirm a fresh GET round-trips the key — i.e. it is re-read from the
    // datastore, not just echoed from the in-memory PUT response.
    const ref = await fixture.client.putDomain(domainDefinition(samlCertKey));
    expect(ref.status).toBe(200);

    const created = await fixture.client.putCertificate(
      fixture.domainKey,
      buildAutomationCertificateDef({ key: samlCertKey }),
    );
    expect(created.status).toBe(200);

    const get = await fixture.client.getDomain(fixture.domainKey);
    expect(get.body.saml.certificate).toEqual(samlCertKey);
  });

  it('should strictly reconcile settings: omitting SAML on PUT resets it', async () => {
    const put = await fixture.client.putDomain(buildAutomationDomainDef({ key: fixture.domainKey }));
    expect(put.status).toBe(200);
    expect(put.body.saml ?? null).toBeNull();
  });
});
