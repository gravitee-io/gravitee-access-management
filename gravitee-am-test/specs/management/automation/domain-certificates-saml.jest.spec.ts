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
import { afterAll, afterEach, beforeAll, describe, expect, it } from '@jest/globals';
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

const createdCertKeys: string[] = [];

afterEach(async () => {
  while (createdCertKeys.length) {
    await fixture.client.deleteCertificate(fixture.domainKey, createdCertKeys.pop());
  }
});

/** Create a certificate via PUT and track its key for cleanup. */
async function createCertificate(overrides: { key?: string; name?: string } = {}) {
  const key = overrides.key ?? uniqueName('autosign', true).toLowerCase();
  createdCertKeys.push(key);
  const response = await fixture.client.putCertificate(fixture.domainKey, buildAutomationCertificateDef({ ...overrides, key }));
  return { key, response };
}

/** Create a system certificate via PUT and track its key for cleanup. */
async function createSystemCertificate(key = uniqueName('autosyscert', true).toLowerCase()) {
  createdCertKeys.push(key);
  const response = await fixture.client.putCertificate(fixture.domainKey, buildSystemAutomationDef(key));
  return { key, response };
}

describe('Automation API - Certificates (resource under a domain)', () => {
  it('should list no certificates when none exist', async () => {
    const response = await fixture.client.listCertificates(fixture.domainKey);
    expect(response.status).toBe(200);
    expect(response.body).toEqual([]);
  });

  it('should create a certificate via PUT', async () => {
    const { key, response } = await createCertificate();

    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(key);
    // internal id / operational flags are intentionally not surfaced
    expect(response.body.id).toBeUndefined();
    expect(response.body.managedBy).toBeUndefined();
    // a non-system certificate
    expect(response.body.system ?? false).toBe(false);
  });

  it('should round-trip the certificate on GET', async () => {
    const { key } = await createCertificate();

    const response = await fixture.client.getCertificate(fixture.domainKey, key);
    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(key);
  });

  it('should update the certificate via a second PUT (idempotent)', async () => {
    const { key } = await createCertificate();

    const response = await fixture.client.putCertificate(
      fixture.domainKey,
      buildAutomationCertificateDef({ key, name: 'Renamed cert' }),
    );
    expect(response.status).toBe(200);
    expect(response.body.name).toEqual('Renamed cert');
  });

  it('should reject changing the type of an existing certificate (400)', async () => {
    const { key } = await createCertificate();

    const response = await fixture.client.putCertificate(fixture.domainKey, {
      ...buildAutomationCertificateDef({ key }),
      type: 'pkcs12-am-certificate',
    });
    expect(response.status).toBe(400);
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
    const { key } = await createCertificate();

    const del = await fixture.client.deleteCertificate(fixture.domainKey, key);
    expect(del.status).toBe(204);

    const get = await fixture.client.getCertificate(fixture.domainKey, key);
    expect(get.status).toBe(404);
  });
});

describe('Automation API - System certificate', () => {
  it('should create a system certificate from a minimal {key, system:true} payload', async () => {
    const { key, response } = await createSystemCertificate();
    expect(response.status).toBe(200);
    expect(response.body.key).toEqual(key);
    expect(response.body.system).toBe(true);
  });

  it('should be idempotent on re-PUT of a system certificate (200, no update)', async () => {
    const { key } = await createSystemCertificate();

    const response = await fixture.client.putCertificate(fixture.domainKey, buildSystemAutomationDef(key));
    expect(response.status).toBe(200);
    expect(response.body.system).toBe(true);
    expect(response.body.key).toEqual(key);
  });

  it('should reject a second system certificate (400)', async () => {
    await createSystemCertificate();

    const response = await fixture.client.putCertificate(
      fixture.domainKey,
      buildSystemAutomationDef(uniqueName('autosyscert2', true).toLowerCase()),
    );
    expect(response.status).toBe(400);
  });

  it('should reject flipping system on an existing certificate (400)', async () => {
    const { key } = await createSystemCertificate();

    // the cert was created with system:true; PUT it again as non-system -> rejected (immutable)
    const response = await fixture.client.putCertificate(
      fixture.domainKey,
      buildAutomationCertificateDef({ key, system: false }),
    );
    expect(response.status).toBe(400);
  });

  it('should delete the system certificate without a system guard', async () => {
    const { key } = await createSystemCertificate();

    const del = await fixture.client.deleteCertificate(fixture.domainKey, key);
    expect(del.status).toBe(204);

    const get = await fixture.client.getCertificate(fixture.domainKey, key);
    expect(get.status).toBe(404);
  });
});

describe('Automation API - Domain SAML certificate reference (by key)', () => {
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
    const samlCertKey = uniqueName('autosaml', true).toLowerCase();
    const response = await fixture.client.putDomain(domainDefinition(samlCertKey));
    expect(response.status).toBe(200);
    // the key round-trips even though the certificate does not exist yet
    expect(response.body.saml.certificate).toEqual(samlCertKey);
  });

  it('should keep the SAML reference once the certificate is created', async () => {
    // establish the reference, create the certificate, then confirm a fresh GET
    // round-trips the key — i.e. it is re-read from the datastore, not just echoed from the PUT response.
    const samlCertKey = uniqueName('autosaml', true).toLowerCase();
    const ref = await fixture.client.putDomain(domainDefinition(samlCertKey));
    expect(ref.status).toBe(200);

    const { response: created } = await createCertificate({ key: samlCertKey });
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
