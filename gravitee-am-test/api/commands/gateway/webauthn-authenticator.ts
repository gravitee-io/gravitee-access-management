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
import { createECDH, createHash, createPrivateKey, createPublicKey, generateKeyPairSync, KeyObject, randomBytes, sign } from 'crypto';
import { TestCertificateAuthority } from './webauthn-certificates';

/** COSE algorithm identifiers (https://www.iana.org/assignments/cose/cose.xhtml#algorithms). */
export const COSE_ALGORITHM = {
  ES256: -7,
  ES384: -35,
} as const;
export type CoseAlgorithm = typeof COSE_ALGORITHM[keyof typeof COSE_ALGORITHM];

const CURVES: Record<CoseAlgorithm, { namedCurve: string; ecdhCurve: string; coseCurve: number; coordinateLength: number; hash: string }> =
  {
    [COSE_ALGORITHM.ES256]: { namedCurve: 'P-256', ecdhCurve: 'prime256v1', coseCurve: 1, coordinateLength: 32, hash: 'sha256' },
    [COSE_ALGORITHM.ES384]: { namedCurve: 'P-384', ecdhCurve: 'secp384r1', coseCurve: 2, coordinateLength: 48, hash: 'sha384' },
  };

const FLAG = { userPresent: 0x01, userVerified: 0x04, attestedCredentialData: 0x40 };

/**
 * The authenticator data flags a ceremony sets. Both default to true, which is what a platform
 * authenticator returns when the relying party requires user verification.
 */
export interface AuthenticatorFlags {
  userPresent?: boolean;
  userVerified?: boolean;
}

/** The attestation statement a registration carries. */
export type Attestation = { format: 'none' } | { format: 'packed' } | { format: 'apple'; issuer: TestCertificateAuthority };

/** What the browser adds around the authenticator: where the ceremony runs and what it asserts. */
export interface CeremonyContext {
  /** The origin the browser reports in the client data, i.e. the WebAuthn origin of the security domain. */
  origin: string;
  /** Overrides the relying party id the options carry (or the origin host when they carry none). */
  rpId?: string;
  /** The signature counter. Defaults to {@link timeBasedCounter}. */
  counter?: number;
  flags?: AuthenticatorFlags;
}

/** A credential as the gateway WebAuthn scripts post it: binary members in base64url. */
export interface PublicKeyCredentialJson {
  id: string;
  rawId: string;
  type: 'public-key';
  response: Record<string, string>;
}

let lastCounter = 0;

/**
 * A signature counter that grows across processes, so a credential registered by one run (or by a
 * migration seed) can still sign in from a later one: tenths of a second since 2026, which fits the
 * 32 bits of the counter until 2039. Strictly increasing within a process, since two ceremonies can
 * run in the same tenth of a second and the gateway refuses a counter that does not increase.
 */
export function timeBasedCounter(): number {
  lastCounter = Math.max(lastCounter + 1, Math.floor((Date.now() - Date.UTC(2026, 0, 1)) / 100));
  return lastCounter;
}

/**
 * A WebAuthn authenticator in software, holding one EC credential, for the gateway ceremonies the
 * Jest suites drive over HTTP where Playwright would use the browser's virtual authenticator.
 */
export class SoftwareAuthenticator {
  private constructor(
    readonly algorithm: CoseAlgorithm,
    readonly credentialId: Buffer,
    private readonly privateKey: KeyObject,
    readonly publicKey: KeyObject,
  ) {}

  /** A new credential with a random key and id. */
  static create(algorithm: CoseAlgorithm = COSE_ALGORITHM.ES256): SoftwareAuthenticator {
    const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: CURVES[algorithm].namedCurve });
    return new SoftwareAuthenticator(algorithm, randomBytes(32), privateKey, publicKey);
  }

  /**
   * The same ES256 credential every time for the same seed, so a process that did not register it
   * can still use it (a migration verification signing in with the credential its seed registered).
   */
  static fromSeed(seed: string): SoftwareAuthenticator {
    const digest = (purpose: string) => createHash('sha256').update(`${purpose}:${seed}`).digest();
    const ecdh = createECDH(CURVES[COSE_ALGORITHM.ES256].ecdhCurve);
    const d = digest('webauthn-private-key');
    ecdh.setPrivateKey(d);
    const point = ecdh.getPublicKey();
    const jwk = {
      kty: 'EC',
      crv: 'P-256',
      d: base64Url(d),
      x: base64Url(point.subarray(1, 33)),
      y: base64Url(point.subarray(33)),
    };
    // JWK key import is typed from @types/node 15.12 on; the runtime has supported it since 15.12 too.
    const privateKey = createPrivateKey({ key: jwk, format: 'jwk' } as any);
    return new SoftwareAuthenticator(COSE_ALGORITHM.ES256, digest('webauthn-credential-id'), privateKey, createPublicKey(privateKey));
  }

  /** Another key behind the same credential id: what it signs must not pass for this credential. */
  impostor(): SoftwareAuthenticator {
    const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: CURVES[this.algorithm].namedCurve });
    return new SoftwareAuthenticator(this.algorithm, this.credentialId, privateKey, publicKey);
  }

  /** The credential id as the gateway and the Management API spell it. */
  get id(): string {
    return base64Url(this.credentialId);
  }

  /** `navigator.credentials.create()` on the creation options the gateway returned. */
  createCredential(options: any, context: CeremonyContext, attestation: Attestation = { format: 'none' }): PublicKeyCredentialJson {
    const clientDataJSON = clientData('webauthn.create', options.challenge, context.origin);
    const clientDataHash = sha256(clientDataJSON);
    const authenticatorData = Buffer.concat([
      sha256(Buffer.from(context.rpId ?? options.rp?.id ?? new URL(context.origin).hostname)),
      Buffer.from([flagsByte(context.flags) | FLAG.attestedCredentialData]),
      uint32(context.counter ?? timeBasedCounter()),
      Buffer.alloc(16), // AAGUID
      uint16(this.credentialId.length),
      this.credentialId,
      encodeCbor(this.coseKey()),
    ]);

    return {
      id: this.id,
      rawId: this.id,
      type: 'public-key',
      response: {
        attestationObject: base64Url(
          encodeCbor(
            new Map<CborValue, CborValue>([
              ['fmt', attestation.format],
              ['attStmt', this.attestationStatement(attestation, authenticatorData, clientDataHash)],
              ['authData', authenticatorData],
            ]),
          ),
        ),
        clientDataJSON: base64Url(clientDataJSON),
      },
    };
  }

  /** `navigator.credentials.get()` on the request options the gateway returned. */
  getAssertion(options: any, context: CeremonyContext, userHandle = ''): PublicKeyCredentialJson {
    const clientDataJSON = clientData('webauthn.get', options.challenge, context.origin);
    const authenticatorData = Buffer.concat([
      sha256(Buffer.from(context.rpId ?? options.rpId ?? new URL(context.origin).hostname)),
      Buffer.from([flagsByte(context.flags)]),
      uint32(context.counter ?? timeBasedCounter()),
    ]);

    return {
      id: this.id,
      rawId: this.id,
      type: 'public-key',
      response: {
        authenticatorData: base64Url(authenticatorData),
        clientDataJSON: base64Url(clientDataJSON),
        signature: base64Url(this.sign(Buffer.concat([authenticatorData, sha256(clientDataJSON)]))),
        userHandle,
      },
    };
  }

  private sign(data: Buffer): Buffer {
    return sign(CURVES[this.algorithm].hash, data, this.privateKey);
  }

  private attestationStatement(attestation: Attestation, authenticatorData: Buffer, clientDataHash: Buffer): Map<CborValue, CborValue> {
    switch (attestation.format) {
      case 'none':
        return new Map();
      case 'packed':
        // Self attestation: signed with the credential key itself, no certificate.
        return new Map<CborValue, CborValue>([
          ['alg', this.algorithm],
          ['sig', this.sign(Buffer.concat([authenticatorData, clientDataHash]))],
        ]);
      case 'apple': {
        const nonce = sha256(Buffer.concat([authenticatorData, clientDataHash]));
        return new Map<CborValue, CborValue>([['x5c', [attestation.issuer.issueAppleAttestationCertificate(this.publicKey, nonce)]]]);
      }
    }
  }

  private coseKey(): Map<CborValue, CborValue> {
    const curve = CURVES[this.algorithm];
    // An uncompressed EC point closes the SubjectPublicKeyInfo: 0x04, then x and y.
    const spki = this.publicKey.export({ type: 'spki', format: 'der' }) as Buffer;
    const point = spki.subarray(spki.length - 2 * curve.coordinateLength);
    return new Map<CborValue, CborValue>([
      [1, 2], // kty: EC2
      [3, this.algorithm],
      [-1, curve.coseCurve],
      [-2, point.subarray(0, curve.coordinateLength)],
      [-3, point.subarray(curve.coordinateLength)],
    ]);
  }
}

function clientData(type: string, challenge: string, origin: string): Buffer {
  return Buffer.from(JSON.stringify({ type, challenge, origin, crossOrigin: false }));
}

function flagsByte(flags: AuthenticatorFlags = {}): number {
  return (flags.userPresent === false ? 0 : FLAG.userPresent) | (flags.userVerified === false ? 0 : FLAG.userVerified);
}

function sha256(data: Buffer): Buffer {
  return createHash('sha256').update(data).digest();
}

function uint16(value: number): Buffer {
  const buffer = Buffer.alloc(2);
  buffer.writeUInt16BE(value);
  return buffer;
}

function uint32(value: number): Buffer {
  const buffer = Buffer.alloc(4);
  buffer.writeUInt32BE(value);
  return buffer;
}

export function base64Url(data: Buffer): string {
  return data.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

type CborValue = number | string | Buffer | CborValue[] | Map<CborValue, CborValue>;

/** The subset of CBOR (RFC 8949) WebAuthn needs: integers, text and byte strings, arrays and maps. */
function encodeCbor(value: CborValue): Buffer {
  if (typeof value === 'number') {
    return value >= 0 ? cborHead(0, value) : cborHead(1, -1 - value);
  }
  if (typeof value === 'string') {
    const bytes = Buffer.from(value, 'utf8');
    return Buffer.concat([cborHead(3, bytes.length), bytes]);
  }
  if (Buffer.isBuffer(value)) {
    return Buffer.concat([cborHead(2, value.length), value]);
  }
  if (Array.isArray(value)) {
    return Buffer.concat([cborHead(4, value.length), ...value.map(encodeCbor)]);
  }
  const entries: Buffer[] = [];
  value.forEach((entry, key) => entries.push(encodeCbor(key), encodeCbor(entry)));
  return Buffer.concat([cborHead(5, value.size), ...entries]);
}

function cborHead(majorType: number, length: number): Buffer {
  const major = majorType << 5;
  if (length < 24) {
    return Buffer.from([major | length]);
  }
  if (length < 0x100) {
    return Buffer.from([major | 24, length]);
  }
  if (length < 0x10000) {
    return Buffer.concat([Buffer.from([major | 25]), uint16(length)]);
  }
  return Buffer.concat([Buffer.from([major | 26]), uint32(length)]);
}
