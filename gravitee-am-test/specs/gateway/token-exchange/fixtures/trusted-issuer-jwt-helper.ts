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
import jwt from 'jsonwebtoken';

const KEY_RESOLUTION_PEM = 'PEM';

export interface TrustedIssuerKeyMaterial {
  /** PEM-encoded X.509 certificate for trusted issuer config */
  certificatePem: string;
  /** PEM-encoded private key for signing subject tokens in tests */
  privateKeyPem: string;
}

/**
 * Generates RSA 2048 keypair and a self-signed X.509 certificate for use in trusted-issuer tests.
 * The certificate PEM is configured as the trusted issuer's key; the private key is used to sign
 * subject JWTs in tests.
 */
export function createTrustedIssuerKeyMaterial(): TrustedIssuerKeyMaterial {
  const keys = forge.pki.rsa.generateKeyPair(2048);
  const cert = forge.pki.createCertificate();
  cert.publicKey = keys.publicKey;
  cert.serialNumber = Date.now().toString();

  const now = new Date();
  const notBefore = new Date(now);
  notBefore.setDate(notBefore.getDate() - 1);
  const notAfter = new Date(now);
  notAfter.setDate(notAfter.getDate() + 365);

  cert.validity.notBefore = notBefore;
  cert.validity.notAfter = notAfter;

  const subjectAttrs = [
    { name: 'countryName', value: 'US' },
    { name: 'organizationName', value: 'Test Org' },
    { name: 'commonName', value: `Trusted Issuer Test ${cert.serialNumber}` },
  ];
  cert.setSubject(subjectAttrs);
  cert.setIssuer(subjectAttrs);
  cert.setExtensions([
    { name: 'basicConstraints', cA: true },
    {
      name: 'keyUsage',
      keyCertSign: true,
      digitalSignature: true,
      nonRepudiation: true,
      keyEncipherment: true,
      dataEncipherment: true,
    },
  ]);

  cert.sign(keys.privateKey, forge.md.sha256.create());

  const certificatePem = forge.pki.certificateToPem(cert);
  const privateKeyPem = forge.pki.privateKeyToPem(keys.privateKey);

  return { certificatePem, privateKeyPem };
}

/**
 * Builds a trusted issuer config object for domain PATCH (PEM method).
 */
export function buildTrustedIssuerPemConfig(
  issuer: string,
  certificatePem: string,
  options?: { scopeMappings?: Record<string, string>; userBindingEnabled?: boolean; userBindingCriteria?: Array<{ attribute: string; expression: string }> },
): Record<string, unknown> {
  const config: Record<string, unknown> = {
    issuer,
    keyResolutionMethod: KEY_RESOLUTION_PEM,
    certificate: certificatePem,
  };
  if (options?.scopeMappings) {
    config.scopeMappings = options.scopeMappings;
  }
  if (options?.userBindingEnabled === true) {
    config.userBindingEnabled = true;
    if (options.userBindingCriteria?.length) {
      config.userBindingCriteria = options.userBindingCriteria;
    }
  }
  return config;
}

export interface SignJwtOptions {
  issuer: string;
  privateKeyPem: string;
  subject?: string;
  /** Additional claims (e.g. email, scope). iss, sub, iat, exp are set automatically if not provided. */
  payload?: Record<string, unknown>;
  /** Expiration in seconds from now (default 3600). */
  expiresInSeconds?: number;
}

/**
 * Signs a JWT with RS256 using the given private key. Used to create subject tokens
 * for trusted-issuer token exchange tests.
 */
export function signJwtForTrustedIssuer(options: SignJwtOptions): string {
  const {
    issuer,
    privateKeyPem,
    subject = 'external-subject-id',
    payload = {},
    expiresInSeconds = 3600,
  } = options;

  const now = Math.floor(Date.now() / 1000);
  const exp = now + expiresInSeconds;

  const fullPayload = {
    iss: issuer,
    sub: subject,
    iat: now,
    exp,
    ...payload,
  };

  return jwt.sign(fullPayload, privateKeyPem, {
    algorithm: 'RS256',
    noTimestamp: false,
  });
}
