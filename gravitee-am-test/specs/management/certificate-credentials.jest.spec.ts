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
import fetch from 'cross-fetch';
import { afterAll, afterEach, beforeAll, beforeEach, expect, jest } from '@jest/globals';
import { createDomain, safeDeleteDomain, startDomain, waitForDomainStart } from '@management-commands/domain-management-commands';
import { buildCreateAndTestUser, deleteUser } from '@management-commands/user-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  enrollCertificate,
  listCertificateCredentials,
  getCertificateCredential,
  deleteCertificateCredential,
  NewCertificateCredential,
} from '@management-commands/certificate-credential-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { generateUniqueCertificatePEM, generateExpiredCertificatePEM } from '@utils/certificate-utils';

globalThis.fetch = fetch;
jest.setTimeout(200000);

let accessToken: string;
let domain: any;
let user: any; // User created per test in beforeEach
let userCounter = 0; // Counter for unique user indices

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  const createdDomain = await createDomain(accessToken, uniqueName('cert-credentials', true), 'Certificate Credentials Test Domain');
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();

  const domainStarted = await startDomain(createdDomain.id, accessToken)
  expect(domainStarted).toBeDefined();
  expect(domainStarted.id).toEqual(createdDomain.id);

  domain = domainStarted;
});

afterAll(async () => {
  if (domain?.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});

describe('Certificate Credential Enrollment', () => {
  beforeEach(async () => {
    // Create a fresh user for each test with unique index to avoid username conflicts
    user = await buildCreateAndTestUser(domain.id, accessToken, userCounter++);
    expect(user).toBeDefined();
    expect(user.id).toBeDefined();
  });

  afterEach(async () => {
    // Delete the user (which automatically cleans up all certificate credentials)
    if (user?.id) {
      await deleteUser(domain.id, accessToken, user.id);
    }
  });

  it('should enroll a certificate for a user', async () => {
    // Given: A user exists
    expect(user).toBeDefined();

    // When: Admin enrolls a certificate
    const certPem = generateUniqueCertificatePEM(1);
    const newCredential: NewCertificateCredential = {
      certificatePem: certPem,
      deviceName: 'My Laptop',
    };
    const credential = await enrollCertificate(domain.id, user.id, accessToken, newCredential);

    // Then: Certificate is stored
    expect(credential).toBeDefined();
    expect(credential.id).toBeDefined();
    expect(credential.certificateThumbprint).toBeDefined();
    expect(credential.certificateSubjectDN).toBeDefined();
    expect(credential.certificateSerialNumber).toBeDefined();
    expect(credential.certificateExpiresAt).toBeDefined();
    expect(credential.deviceName).toBe('My Laptop');
  });

  it('should reject expired certificate', async () => {
    // Given: A user exists
    expect(user).toBeDefined();

    // When: Admin enrolls an expired certificate
    const expiredCertPem = generateExpiredCertificatePEM();
    const newCredential: NewCertificateCredential = {
      certificatePem: expiredCertPem,
      deviceName: 'Expired Device',
    };

    // Then: Enrollment fails with error
    await expect(enrollCertificate(domain.id, user.id, accessToken, newCredential)).rejects.toMatchObject({
      message: expect.stringContaining('400'),
    });
  });

  it('should reject duplicate certificate', async () => {
    // Given: A user has an enrolled certificate
    expect(user).toBeDefined();
    const certPem = generateUniqueCertificatePEM(2);
    const newCredential: NewCertificateCredential = {
      certificatePem: certPem,
      deviceName: 'First Device',
    };
    const firstCredential = await enrollCertificate(domain.id, user.id, accessToken, newCredential);
    expect(firstCredential.id).toBeDefined();

    // When: Admin enrolls the same certificate again
    const duplicateCredential: NewCertificateCredential = {
      certificatePem: certPem, // Same certificate PEM
      deviceName: 'Duplicate Device',
    };

    // Then: Enrollment fails with error (409 Conflict)
    await expect(enrollCertificate(domain.id, user.id, accessToken, duplicateCredential)).rejects.toMatchObject({
      message: expect.stringContaining('409'),
    });
  });

  it('should enforce certificate limit (20 per user)', async () => {
    // Given: A user has enrolled 20 certificates (the limit)
    expect(user).toBeDefined();

    const CERTIFICATE_LIMIT = 20;

    // Enroll certificates up to the limit
    for (let i = 0; i < CERTIFICATE_LIMIT; i++) {
      const certPem = generateUniqueCertificatePEM(i);
      const newCredential: NewCertificateCredential = {
        certificatePem: certPem,
        deviceName: `Device ${i + 1}`,
      };
      const credential = await enrollCertificate(domain.id, user.id, accessToken, newCredential);
      expect(credential.id).toBeDefined();
    }

    // Verify we have exactly 20 certificates
    const allCredentials = await listCertificateCredentials(domain.id, user.id, accessToken);
    expect(allCredentials.length).toBe(CERTIFICATE_LIMIT);

    // When: Admin attempts to enroll a 21st certificate
    const limitExceededCertPem = generateUniqueCertificatePEM(CERTIFICATE_LIMIT);
    const limitExceededCredential: NewCertificateCredential = {
      certificatePem: limitExceededCertPem,
      deviceName: 'Limit Exceeded Device',
    };

    // Then: Enrollment fails with error (400 Bad Request)
    await expect(enrollCertificate(domain.id, user.id, accessToken, limitExceededCredential)).rejects.toMatchObject({
      message: expect.stringContaining('400'),
    });

    // Verify the 21st certificate was NOT added
    const credentialsAfterLimit = await listCertificateCredentials(domain.id, user.id, accessToken);
    expect(credentialsAfterLimit.length).toBe(CERTIFICATE_LIMIT);
  });
});

describe('Certificate Credential Management', () => {
  beforeEach(async () => {
    // Create a fresh user for each test with unique index to avoid username conflicts
    user = await buildCreateAndTestUser(domain.id, accessToken, userCounter++);
    expect(user).toBeDefined();
    expect(user.id).toBeDefined();
  });

  afterEach(async () => {
    // Delete the user (which automatically cleans up all certificate credentials)
    if (user?.id) {
      await deleteUser(domain.id, accessToken, user.id);
    }
  });

  it('should list certificate credentials for a user', async () => {
    // Given: A user has enrolled certificates
    expect(user).toBeDefined();
    const cert1 = generateUniqueCertificatePEM(100);
    const cert2 = generateUniqueCertificatePEM(101);

    const credential1 = await enrollCertificate(domain.id, user.id, accessToken, {
      certificatePem: cert1,
      deviceName: 'Device 1',
    });
    const credential2 = await enrollCertificate(domain.id, user.id, accessToken, {
      certificatePem: cert2,
      deviceName: 'Device 2',
    });

    // When: Admin lists certificates
    const credentials = await listCertificateCredentials(domain.id, user.id, accessToken);

    // Then: All enrolled certificates are returned
    expect(credentials).toBeDefined();
    expect(Array.isArray(credentials)).toBe(true);
    expect(credentials.length).toBe(2);
    expect(credentials.some((c) => c.id === credential1.id)).toBe(true);
    expect(credentials.some((c) => c.id === credential2.id)).toBe(true);
    expect(credentials.find((c) => c.id === credential1.id)?.deviceName).toBe('Device 1');
    expect(credentials.find((c) => c.id === credential2.id)?.deviceName).toBe('Device 2');
  });

  it('should list empty certificate credentials for a user with no certificates', async () => {
    // Given: A user exists with no enrolled certificates
    expect(user).toBeDefined();

    // When: Admin lists certificates
    const credentials = await listCertificateCredentials(domain.id, user.id, accessToken);

    // Then: Empty array is returned
    expect(credentials).toBeDefined();
    expect(Array.isArray(credentials)).toBe(true);
    expect(credentials.length).toBe(0);
  });

  it('should get certificate credential by ID', async () => {
    // Given: A user has an enrolled certificate
    expect(user).toBeDefined();
    const certPem = generateUniqueCertificatePEM(200);
    const credential = await enrollCertificate(domain.id, user.id, accessToken, {
      certificatePem: certPem,
      deviceName: 'Test Device',
    });

    // When: Admin gets certificate by ID
    const retrieved = await getCertificateCredential(domain.id, user.id, credential.id, accessToken);

    // Then: Certificate is returned with all expected fields
    expect(retrieved).toBeDefined();
    expect(retrieved.id).toBe(credential.id);
    expect(retrieved.certificatePem).toBeDefined();
    expect(retrieved.certificateThumbprint).toBe(credential.certificateThumbprint);
    expect(retrieved.certificateSubjectDN).toBe(credential.certificateSubjectDN);
    expect(retrieved.certificateSerialNumber).toBe(credential.certificateSerialNumber);
    expect(retrieved.certificateExpiresAt).toBeDefined();
    expect(retrieved.deviceName).toBe('Test Device');
    expect(retrieved.userId).toBe(user.id);
  });

  it('should return 404 when getting non-existent certificate credential', async () => {
    // Given: A user exists
    expect(user).toBeDefined();
    const nonExistentCredentialId = 'non-existent-credential-id';

    // When: Admin tries to get a non-existent certificate
    // Then: 404 error is returned
    await expect(getCertificateCredential(domain.id, user.id, nonExistentCredentialId, accessToken)).rejects.toMatchObject({
      message: expect.stringContaining('404'),
    });
  });

  it('should delete certificate credential', async () => {
    // Given: A user has an enrolled certificate
    expect(user).toBeDefined();
    const certPem = generateUniqueCertificatePEM(300);
    const credential = await enrollCertificate(domain.id, user.id, accessToken, {
      certificatePem: certPem,
      deviceName: 'Device to Delete',
    });

    // Verify certificate exists before deletion
    const beforeDelete = await getCertificateCredential(domain.id, user.id, credential.id, accessToken);
    expect(beforeDelete.id).toBe(credential.id);

    // When: Admin deletes certificate
    await deleteCertificateCredential(domain.id, user.id, credential.id, accessToken);

    // Then: Certificate is deleted and cannot be retrieved
    await expect(getCertificateCredential(domain.id, user.id, credential.id, accessToken)).rejects.toMatchObject({
      message: expect.stringContaining('404'),
    });

    // Verify it's also removed from the list
    const credentials = await listCertificateCredentials(domain.id, user.id, accessToken);
    expect(credentials.some((c) => c.id === credential.id)).toBe(false);
  });

  it('should return 404 when deleting non-existent certificate credential', async () => {
    // Given: A user exists
    expect(user).toBeDefined();
    const nonExistentCredentialId = 'non-existent-credential-id';

    // When: Admin tries to delete a non-existent certificate
    // Then: 404 error is returned
    await expect(deleteCertificateCredential(domain.id, user.id, nonExistentCredentialId, accessToken)).rejects.toMatchObject({
      message: expect.stringContaining('404'),
    });
  });
});


