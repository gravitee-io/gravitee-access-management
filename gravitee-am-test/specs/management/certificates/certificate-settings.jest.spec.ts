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
import { setupCertificateSettingsFixture, CertificateSettingsFixture } from './fixtures/certificate-settings-fixture';
import { getDomain, patchDomain, updateCertificateSettings } from '@management-commands/domain-management-commands';
import { createCertificate, deleteCertificate, getAllCertificates } from '@management-commands/certificate-management-commands';
import { buildCertificate } from '@api-fixtures/certificates';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: CertificateSettingsFixture;

beforeAll(async () => {
  fixture = await setupCertificateSettingsFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Certificate Settings - patchDomain API', () => {
  it('should set fallback certificate on domain', async () => {
    const updatedDomain = await patchDomain(fixture.domain.id, fixture.accessToken, {
      certificateSettings: {
        fallbackCertificate: fixture.primaryCertificate.id,
      },
    });

    expect(updatedDomain.certificateSettings).toBeDefined();
    expect(updatedDomain.certificateSettings.fallbackCertificate).toEqual(fixture.primaryCertificate.id);
  });

  it('should retrieve domain with fallback certificate', async () => {
    const fetchedDomain = await getDomain(fixture.domain.id, fixture.accessToken);

    expect(fetchedDomain.certificateSettings).toBeDefined();
    expect(fetchedDomain.certificateSettings.fallbackCertificate).toEqual(fixture.primaryCertificate.id);
  });

  it('should update fallback certificate to another certificate', async () => {
    const updatedDomain = await patchDomain(fixture.domain.id, fixture.accessToken, {
      certificateSettings: {
        fallbackCertificate: fixture.secondaryCertificate.id,
      },
    });

    expect(updatedDomain.certificateSettings.fallbackCertificate).toEqual(fixture.secondaryCertificate.id);
  });

  it('should clear fallback certificate', async () => {
    const updatedDomain = await patchDomain(fixture.domain.id, fixture.accessToken, {
      certificateSettings: {
        fallbackCertificate: null,
      },
    });

    expect(updatedDomain.certificateSettings).toBeDefined();
    expect(updatedDomain.certificateSettings.fallbackCertificate).toBeUndefined();
  });

  it('should not allow deleting a certificate that is set as fallback', async () => {
    await patchDomain(fixture.domain.id, fixture.accessToken, {
      certificateSettings: {
        fallbackCertificate: fixture.primaryCertificate.id,
      },
    });

    await expect(deleteCertificate(fixture.domain.id, fixture.accessToken, fixture.primaryCertificate.id)).rejects.toHaveProperty(
      'response.status',
      400,
    );

    await patchDomain(fixture.domain.id, fixture.accessToken, {
      certificateSettings: {
        fallbackCertificate: null,
      },
    });

    await deleteCertificate(fixture.domain.id, fixture.accessToken, fixture.primaryCertificate.id);

    const remainingCerts = await getAllCertificates(fixture.domain.id, fixture.accessToken);
    const deletedCert = remainingCerts.find((c) => c.id === fixture.primaryCertificate.id);
    expect(deletedCert).toBeUndefined();
  });
});

describe('Certificate Settings - Dedicated Endpoint', () => {
  it('should set fallback certificate using dedicated endpoint', async () => {
    const updatedDomain = await updateCertificateSettings(fixture.domain.id, fixture.accessToken, {
      fallbackCertificate: fixture.secondaryCertificate.id,
    });

    expect(updatedDomain.certificateSettings).toBeDefined();
    expect(updatedDomain.certificateSettings.fallbackCertificate).toEqual(fixture.secondaryCertificate.id);
  });

  it('should retrieve domain with fallback certificate set via dedicated endpoint', async () => {
    const fetchedDomain = await getDomain(fixture.domain.id, fixture.accessToken);

    expect(fetchedDomain.certificateSettings).toBeDefined();
    expect(fetchedDomain.certificateSettings.fallbackCertificate).toEqual(fixture.secondaryCertificate.id);
  });

  it('should update fallback certificate using dedicated endpoint', async () => {
    const newCertificate = await createCertificate(fixture.domain.id, fixture.accessToken, buildCertificate(102));

    const updatedDomain = await updateCertificateSettings(fixture.domain.id, fixture.accessToken, {
      fallbackCertificate: newCertificate.id,
    });

    expect(updatedDomain.certificateSettings.fallbackCertificate).toEqual(newCertificate.id);
  });

  it('should clear fallback certificate using dedicated endpoint', async () => {
    const updatedDomain = await updateCertificateSettings(fixture.domain.id, fixture.accessToken, {
      fallbackCertificate: null,
    });

    expect(updatedDomain.certificateSettings).toBeDefined();
    expect(updatedDomain.certificateSettings.fallbackCertificate).toBeUndefined();
  });

  it('should not allow deleting a certificate that is set as fallback via dedicated endpoint', async () => {
    await updateCertificateSettings(fixture.domain.id, fixture.accessToken, {
      fallbackCertificate: fixture.secondaryCertificate.id,
    });

    await expect(deleteCertificate(fixture.domain.id, fixture.accessToken, fixture.secondaryCertificate.id)).rejects.toHaveProperty(
      'response.status',
      400,
    );

    await updateCertificateSettings(fixture.domain.id, fixture.accessToken, {
      fallbackCertificate: null,
    });

    await deleteCertificate(fixture.domain.id, fixture.accessToken, fixture.secondaryCertificate.id);

    const remainingCerts = await getAllCertificates(fixture.domain.id, fixture.accessToken);
    const deletedCert = remainingCerts.find((c) => c.id === fixture.secondaryCertificate.id);
    expect(deletedCert).toBeUndefined();
  });
});
