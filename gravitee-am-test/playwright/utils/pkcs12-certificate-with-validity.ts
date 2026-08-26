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
import forge from 'node-forge';

import type { NewCertificate } from '@management-models/NewCertificate';

const PKCS12_PASSWORD = 'changeme';
/**
 * The alias has to differ per certificate: AM refuses a second certificate carrying an alias
 * already used in the domain ("A certificate with alias [x] already exists in this domain"), and
 * these tests deliberately put several in one domain.
 */
const aliasFor = (displayName: string) => `alias-${displayName}`.slice(0, 60);

/**
 * Build a pkcs12 certificate whose validity window is given explicitly, so a certificate that has
 * already expired can be created as easily as one that has not.
 *
 * Built with node-forge rather than by shelling out to OpenSSL. `openssl req` refuses a
 * non-positive `-days`, and its `-not_before`/`-not_after` options only arrived in OpenSSL 3.5 —
 * newer than the image CI runs on. Doing it in JavaScript keeps every validity window available
 * on any machine.
 */
export function buildPkcs12CertificateWithValidity(displayName: string, notBefore: Date, notAfter: Date): NewCertificate {
  const keys = forge.pki.rsa.generateKeyPair(2048);
  const cert = forge.pki.createCertificate();

  cert.publicKey = keys.publicKey;
  cert.serialNumber = '01' + forge.util.bytesToHex(forge.random.getBytesSync(8));
  cert.validity.notBefore = notBefore;
  cert.validity.notAfter = notAfter;

  const attrs = [{ name: 'commonName', value: 'am-playwright-validity.test' }];
  cert.setSubject(attrs);
  cert.setIssuer(attrs);
  cert.sign(keys.privateKey, forge.md.sha256.create());

  const p12Asn1 = forge.pkcs12.toPkcs12Asn1(keys.privateKey, [cert], PKCS12_PASSWORD, {
    friendlyName: aliasFor(displayName),
    algorithm: '3des',
  });
  const contentB64 = forge.util.encode64(forge.asn1.toDer(p12Asn1).getBytes());

  const fileMeta = JSON.stringify({
    name: `${displayName}.p12`,
    type: 'application/x-pkcs12',
    size: new TextEncoder().encode(contentB64).length,
    content: contentB64,
  });

  return {
    name: displayName,
    type: 'pkcs12-am-certificate',
    configuration: JSON.stringify({
      storepass: PKCS12_PASSWORD,
      alias: aliasFor(displayName),
      keypass: PKCS12_PASSWORD,
      algorithm: 'RS256',
      use: ['sig', 'enc'],
      content: fileMeta,
    }),
  };
}

/** Convenience: a certificate valid from now until `days` from now. */
export const validForDays = (displayName: string, days: number): NewCertificate =>
  buildPkcs12CertificateWithValidity(displayName, new Date(), new Date(Date.now() + days * 24 * 3600 * 1000));

/** Convenience: a certificate whose validity window has already passed. */
export const alreadyExpired = (displayName: string): NewCertificate =>
  buildPkcs12CertificateWithValidity(displayName, new Date(Date.now() - 30 * 24 * 3600 * 1000), new Date(Date.now() - 24 * 3600 * 1000));
