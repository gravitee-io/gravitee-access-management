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

import * as forge from 'node-forge';

/**
 * Generate a self-signed X.509 certificate in PEM format.
 *
 * @param options Certificate generation options
 * @param options.subjectDN Subject Distinguished Name (e.g., "CN=Test Certificate, O=Test Org, C=US")
 * @param options.validDays Number of days the certificate is valid (default: 365)
 * @param options.expired If true, generate an expired certificate (default: false)
 * @param options.serialNumber Optional serial number (default: timestamp-based)
 * @returns PEM-encoded certificate string
 */
export function generateCertificatePEM(
  options: {
    subjectDN?: string;
    validDays?: number;
    expired?: boolean;
    serialNumber?: string;
  } = {},
): string {
  const { subjectDN = 'CN=Test Certificate, O=Test Org, C=US', validDays = 365, expired = false, serialNumber } = options;

  // Generate a key pair
  const keys = forge.pki.rsa.generateKeyPair(2048);

  // Create certificate
  const cert = forge.pki.createCertificate();
  cert.publicKey = keys.publicKey;
  // Serial number must be a hex string (without 0x prefix) or decimal string
  // Use timestamp as decimal string for better compatibility
  cert.serialNumber = serialNumber || Date.now().toString();

  // Set certificate validity
  const now = new Date();
  const notBefore = new Date(now);
  notBefore.setDate(notBefore.getDate() - 1); // Start 1 day ago to avoid timing issues

  const notAfter = new Date(now);
  if (expired) {
    // Set expiration to 1 year ago
    notAfter.setFullYear(notAfter.getFullYear() - 1);
  } else {
    // Set expiration to validDays from now
    notAfter.setDate(notAfter.getDate() + validDays);
  }

  cert.validity.notBefore = notBefore;
  cert.validity.notAfter = notAfter;

  // Parse subjectDN or use default attributes
  let subjectAttrs;

  if (subjectDN && subjectDN.includes('=')) {
    // Parse DN string format: "CN=Name, O=Org, C=US"
    const parts = subjectDN.split(',').map((p) => p.trim());
    const attrMap: { [key: string]: string } = {
      CN: 'commonName',
      C: 'countryName',
      O: 'organizationName',
      OU: 'organizationalUnitName',
      L: 'localityName',
      ST: 'stateOrProvinceName',
      E: 'emailAddress',
    };

    subjectAttrs = parts.map((part) => {
      const [key, ...valueParts] = part.split('=');
      const keyTrimmed = key.trim().toUpperCase();
      const value = valueParts.join('=').trim();
      const forgeKey = attrMap[keyTrimmed] || key.trim();
      return { name: forgeKey, value };
    });
  } else {
    // Use default attributes
    subjectAttrs = [
      { name: 'countryName', value: 'US' },
      { name: 'organizationName', value: 'Test Org' },
      { name: 'commonName', value: `Test Certificate ${cert.serialNumber}` },
    ];
  }

  cert.setSubject(subjectAttrs);
  cert.setIssuer(subjectAttrs); // Self-signed, so issuer = subject

  // Set extensions
  cert.setExtensions([
    {
      name: 'basicConstraints',
      cA: true,
    },
    {
      name: 'keyUsage',
      keyCertSign: true,
      digitalSignature: true,
      nonRepudiation: true,
      keyEncipherment: true,
      dataEncipherment: true,
    },
  ]);

  // Sign certificate with private key
  cert.sign(keys.privateKey, forge.md.sha256.create());

  // Convert to PEM format
  const pem = forge.pki.certificateToPem(cert);

  // Ensure proper PEM format (should already be correct, but verify)
  // PEM should start with -----BEGIN CERTIFICATE----- and end with -----END CERTIFICATE-----
  if (!pem.includes('-----BEGIN CERTIFICATE-----') || !pem.includes('-----END CERTIFICATE-----')) {
    throw new Error('Generated certificate is not in valid PEM format');
  }

  return pem;
}

/**
 * Generate a unique certificate PEM for testing.
 * Each call generates a new certificate with a unique serial number.
 *
 * @param index Optional index to make certificate more unique
 * @returns PEM-encoded certificate string
 */
export function generateUniqueCertificatePEM(index?: number): string {
  // Use a numeric serial number for better compatibility
  const serialNumber = index !== undefined ? `${Date.now()}${index}` : Date.now().toString();
  return generateCertificatePEM({
    subjectDN: `CN=Test Certificate ${serialNumber}, O=Test Org, C=US`,
    serialNumber,
  });
}

/**
 * Generate an expired certificate PEM for testing.
 *
 * @returns PEM-encoded expired certificate string
 */
export function generateExpiredCertificatePEM(): string {
  return generateCertificatePEM({
    expired: true,
    subjectDN: 'CN=Expired Test Certificate, O=Test Org, C=US',
  });
}
