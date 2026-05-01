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
import { createHash, randomBytes } from 'crypto';
import fs from 'fs';
import path from 'path';

/** Forge attribute shape accepted by cert.setSubject / setIssuer. */
type CertAttr = { name?: string; shortName?: string; value: string };

/**
 * Core self-signed cert builder. Handles all the repetitive forge steps so
 * the public generators only need to prepare their inputs.
 */
function buildSelfSignedPem(
  attrs: CertAttr[],
  serialNumber: string,
  notBefore: Date,
  notAfter: Date,
  extensions: object[] = [],
): string {
  const keypair = forge.pki.rsa.generateKeyPair(2048);
  const cert = forge.pki.createCertificate();
  cert.publicKey = keypair.publicKey;
  cert.serialNumber = serialNumber;
  cert.validity.notBefore = notBefore;
  cert.validity.notAfter = notAfter;
  cert.setSubject(attrs);
  cert.setIssuer(attrs); // self-signed
  if (extensions.length) cert.setExtensions(extensions);
  cert.sign(keypair.privateKey, forge.md.sha256.create());
  return forge.pki.certificateToPem(cert);
}

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

  const serial = serialNumber || Date.now().toString();

  const now = new Date();
  const notBefore = new Date(now);
  notBefore.setDate(notBefore.getDate() - 1); // 1 day ago to avoid clock-skew issues

  const notAfter = new Date(now);
  if (expired) {
    notAfter.setFullYear(notAfter.getFullYear() - 1);
  } else {
    notAfter.setDate(notAfter.getDate() + validDays);
  }

  const attrMap: Record<string, string> = {
    CN: 'commonName',
    C: 'countryName',
    O: 'organizationName',
    OU: 'organizationalUnitName',
    L: 'localityName',
    ST: 'stateOrProvinceName',
    E: 'emailAddress',
  };

  let attrs: CertAttr[];
  if (subjectDN.includes('=')) {
    attrs = subjectDN.split(',').map((part) => {
      const [key, ...rest] = part.trim().split('=');
      return { name: attrMap[key.trim().toUpperCase()] ?? key.trim(), value: rest.join('=').trim() };
    });
  } else {
    attrs = [
      { name: 'countryName', value: 'US' },
      { name: 'organizationName', value: 'Test Org' },
      { name: 'commonName', value: `Test Certificate ${serial}` },
    ];
  }

  const extensions = [
    { name: 'basicConstraints', cA: true },
    {
      name: 'keyUsage',
      keyCertSign: true,
      digitalSignature: true,
      nonRepudiation: true,
      keyEncipherment: true,
      dataEncipherment: true,
    },
  ];

  const pem = buildSelfSignedPem(attrs, serial, notBefore, notAfter, extensions);

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

/** Cert produced by generateSelfSignedCert — PEM, its Subject DN string, and pre-computed SHA-256 thumbprint. */
export type SelfSignedCert = {
  pem: string;
  subjectDn: string;
  thumbprintS256: string;
};

/**
 * Generate an ephemeral RSA 2048 self-signed certificate suitable for mTLS client-auth tests.
 * Uses a random serial and a single CN RDN to avoid DN ordering ambiguity with BouncyCastle.
 */
export function generateSelfSignedCert(cn: string): SelfSignedCert {
  const pem = buildSelfSignedPem(
    [{ shortName: 'CN', value: cn }],
    randomBytes(16).toString('hex'),
    new Date(),
    new Date(Date.now() + 365 * 24 * 60 * 60 * 1000),
  );
  return { pem, subjectDn: `CN=${cn}`, thumbprintS256: certThumbprintS256(pem) };
}

/**
 * Compute the SHA-256 thumbprint (x5t#S256) of a PEM-encoded certificate.
 * Returns a Base64URL-encoded string, matching RFC 7517 §4.9.
 */
export function certThumbprintS256(pem: string): string {
  const cert = forge.pki.certificateFromPem(pem);
  const der = forge.asn1.toDer(forge.pki.certificateToAsn1(cert)).getBytes();
  return createHash('sha256').update(Buffer.from(der, 'binary')).digest('base64url');
}

/** Read a PEM cert or key from a path relative to process.cwd() (e.g. `/certs/client-a/client.crt`). */
export function readCert(relPath: string): string {
  return fs.readFileSync(path.join(process.cwd(), relPath), 'utf8');
}
