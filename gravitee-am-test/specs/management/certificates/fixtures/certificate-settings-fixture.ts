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
import { expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { setupDomainForTest, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { createCertificate } from '@management-commands/certificate-management-commands';
import { Domain } from '@management-models/Domain';
import { Certificate } from '@management-models/Certificate';
import { uniqueName } from '@utils-commands/misc';
import { buildCertificate } from '@api-fixtures/certificates';

/**
 * Fixture interface for certificate settings tests
 */
export interface CertificateSettingsFixture {
  domain: Domain;
  accessToken: string;
  primaryCertificate: Certificate;
  secondaryCertificate: Certificate;
  cleanup: () => Promise<void>;
}

/**
 * Test constants for certificate settings
 */
export const CERT_SETTINGS_TEST = {
  DOMAIN_NAME_PREFIX: 'cert-settings',
  PRIMARY_CERT_INDEX: 100,
  SECONDARY_CERT_INDEX: 101,
} as const;

/**
 * Helper function to setup test environment (domain)
 */
async function setupTestEnvironment() {
  const accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  const { domain } = await setupDomainForTest(
    uniqueName(CERT_SETTINGS_TEST.DOMAIN_NAME_PREFIX, true),
    { accessToken, waitForStart: false }, // Don't need to wait for start for management tests
  );
  expect(domain).toBeDefined();
  expect(domain.id).toBeDefined();

  return { domain, accessToken };
}

/**
 * Helper function to create test certificates
 */
async function createTestCertificates(
  domain: Domain,
  accessToken: string,
): Promise<{ primaryCertificate: Certificate; secondaryCertificate: Certificate }> {
  const primaryCertificate = await createCertificate(
    domain.id,
    accessToken,
    buildCertificate(CERT_SETTINGS_TEST.PRIMARY_CERT_INDEX),
  );
  expect(primaryCertificate).toBeDefined();
  expect(primaryCertificate.id).toBeDefined();

  const secondaryCertificate = await createCertificate(
    domain.id,
    accessToken,
    buildCertificate(CERT_SETTINGS_TEST.SECONDARY_CERT_INDEX),
  );
  expect(secondaryCertificate).toBeDefined();
  expect(secondaryCertificate.id).toBeDefined();

  return { primaryCertificate, secondaryCertificate };
}

/**
 * Main fixture setup function for certificate settings tests
 */
export const setupCertificateSettingsFixture = async (): Promise<CertificateSettingsFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    // Setup test environment
    const envResult = await setupTestEnvironment();
    domain = envResult.domain;
    accessToken = envResult.accessToken;

    // Create test certificates
    const { primaryCertificate, secondaryCertificate } = await createTestCertificates(domain, accessToken);

    // Cleanup function
    const cleanup = async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    };

    return {
      domain,
      accessToken,
      primaryCertificate,
      secondaryCertificate,
      cleanup,
    };
  } catch (error) {
    // Cleanup on failure
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup domain after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};
