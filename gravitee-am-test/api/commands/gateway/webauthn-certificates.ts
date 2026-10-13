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
import { generateKeyPairSync, KeyObject, randomBytes, sign } from 'crypto';

/**
 * Just enough DER to issue the X.509 certificates of a WebAuthn attestation chain: Node can parse
 * certificates but not create them, and node-forge only signs with RSA keys.
 */
const der = {
  tlv: (tag: number, content: Buffer): Buffer => Buffer.concat([Buffer.from([tag]), derLength(content.length), content]),
  sequence: (...items: Buffer[]): Buffer => der.tlv(0x30, Buffer.concat(items)),
  set: (...items: Buffer[]): Buffer => der.tlv(0x31, Buffer.concat(items)),
  explicit: (tagNumber: number, content: Buffer): Buffer => der.tlv(0xa0 | tagNumber, content),
  boolean: (value: boolean): Buffer => der.tlv(0x01, Buffer.from([value ? 0xff : 0x00])),
  integer: (value: Buffer): Buffer => der.tlv(0x02, value[0] & 0x80 ? Buffer.concat([Buffer.from([0]), value]) : value),
  octetString: (content: Buffer): Buffer => der.tlv(0x04, content),
  bitString: (content: Buffer, unusedBits = 0): Buffer => der.tlv(0x03, Buffer.concat([Buffer.from([unusedBits]), content])),
  utf8String: (value: string): Buffer => der.tlv(0x0c, Buffer.from(value, 'utf8')),
  time: (date: Date): Buffer => {
    const iso = date.toISOString().replace(/[-:T]/g, '').slice(0, 14) + 'Z';
    return date.getUTCFullYear() < 2050 ? der.tlv(0x17, Buffer.from(iso.slice(2), 'ascii')) : der.tlv(0x18, Buffer.from(iso, 'ascii'));
  },
  oid: (dotted: string): Buffer => {
    const [first, second, ...rest] = dotted.split('.').map(Number);
    const bytes = [40 * first + second];
    for (const arc of rest) {
      const chunk = [arc & 0x7f];
      for (let value = Math.floor(arc / 128); value > 0; value = Math.floor(value / 128)) {
        chunk.unshift((value & 0x7f) | 0x80);
      }
      bytes.push(...chunk);
    }
    return der.tlv(0x06, Buffer.from(bytes));
  },
};

function derLength(length: number): Buffer {
  if (length < 0x80) {
    return Buffer.from([length]);
  }
  const bytes: number[] = [];
  for (let value = length; value > 0; value = Math.floor(value / 256)) {
    bytes.unshift(value & 0xff);
  }
  return Buffer.from([0x80 | bytes.length, ...bytes]);
}

const OID = {
  commonName: '2.5.4.3',
  basicConstraints: '2.5.29.19',
  keyUsage: '2.5.29.15',
  ecdsaWithSha256: '1.2.840.10045.4.3.2',
  appleAnonymousAttestationNonce: '1.2.840.113635.100.8.2',
};

const DAY_MILLIS = 24 * 60 * 60 * 1000;

function name(commonName: string): Buffer {
  return der.sequence(der.set(der.sequence(der.oid(OID.commonName), der.utf8String(commonName))));
}

function extension(oid: string, critical: boolean, value: Buffer): Buffer {
  return der.sequence(der.oid(oid), ...(critical ? [der.boolean(true)] : []), der.octetString(value));
}

/**
 * A certificate authority made up for a test, with an EC P-256 key. Its root certificate can be
 * given to a security domain as a WebAuthn root certificate, and it issues the attestation
 * certificates a software authenticator presents.
 */
export class TestCertificateAuthority {
  /** DER of the self-signed root certificate. */
  readonly certificate: Buffer;

  private constructor(private readonly commonName: string, private readonly privateKey: KeyObject, publicKey: KeyObject) {
    this.certificate = this.issue({
      subject: commonName,
      publicKey,
      extensions: [
        extension(OID.basicConstraints, true, der.sequence(der.boolean(true))),
        // keyCertSign and cRLSign: bits 5 and 6, the last bit unused.
        extension(OID.keyUsage, true, der.bitString(Buffer.from([0x06]), 1)),
      ],
    });
  }

  static create(commonName: string): TestCertificateAuthority {
    const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'P-256' });
    return new TestCertificateAuthority(commonName, privateKey, publicKey);
  }

  /** The root certificate as the WebAuthn settings of a security domain take it: base64 of the DER. */
  get certificateBase64(): string {
    return this.certificate.toString('base64');
  }

  /**
   * The leaf certificate of an `apple` attestation: it certifies the credential public key and
   * carries the nonce, i.e. the SHA-256 of the authenticator data followed by the client data hash.
   */
  issueAppleAttestationCertificate(credentialPublicKey: KeyObject, nonce: Buffer): Buffer {
    return this.issue({
      subject: 'Apple anonymous attestation test credential',
      publicKey: credentialPublicKey,
      extensions: [extension(OID.appleAnonymousAttestationNonce, false, der.sequence(der.explicit(1, der.octetString(nonce))))],
    });
  }

  private issue(certificate: { subject: string; publicKey: KeyObject; extensions: Buffer[] }): Buffer {
    const signatureAlgorithm = der.sequence(der.oid(OID.ecdsaWithSha256));
    const now = Date.now();
    const tbsCertificate = der.sequence(
      der.explicit(0, der.integer(Buffer.from([2]))),
      der.integer(randomBytes(8)),
      signatureAlgorithm,
      name(this.commonName),
      der.sequence(der.time(new Date(now - DAY_MILLIS)), der.time(new Date(now + 365 * DAY_MILLIS))),
      name(certificate.subject),
      certificate.publicKey.export({ type: 'spki', format: 'der' }) as Buffer,
      der.explicit(3, der.sequence(...certificate.extensions)),
    );
    return der.sequence(tbsCertificate, signatureAlgorithm, der.bitString(sign('sha256', tbsCertificate, this.privateKey)));
  }
}
