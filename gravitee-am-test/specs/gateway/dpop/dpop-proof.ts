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
import { calculateJwkThumbprint, exportJWK, generateKeyPair, JWK, SignJWT } from 'jose';
import { createHash, randomUUID } from 'crypto';

/**
 * DPoP (RFC 9449) proof factory for the JEST integration suite. Wraps `jose` to mint real,
 * client-signed proofs and compute JWK thumbprints exactly as the gateway does. See
 * `.scratch/dpop-jest-tests/issues/00-proof-minter-and-fixture.md`.
 *
 * Key facts (proven against a live gateway):
 *  - `htu` must be the endpoint URL verbatim, including the `/{domain.hrid}` path segment — pass
 *    `fixture.oidc.token_endpoint` / `userinfo_endpoint` straight through. The validator strips only
 *    the query/fragment.
 *  - `ath = base64url(sha256(access_token))`.
 *  - A fresh `jti` is minted per proof (replay is enforced at the live endpoint); reuse a whole
 *    proof string to test replay.
 */
export type DpopAlg = 'ES256' | 'RS256';

export interface DpopProofOptions {
  /** The endpoint URL, used verbatim as `htu`. */
  htu: string;
  /** The HTTP method, e.g. 'POST' | 'GET'. */
  htm: string;
  /** Resource proofs: the access token to hash into `ath`. */
  accessToken?: string;
  /** Override `jti` (defaults to a fresh UUID). */
  jti?: string;
  /** Override the issued-at, in epoch seconds (for staleness tests). */
  iat?: number;
}

export interface DpopKey {
  /** The RFC 7638 JWK thumbprint — what lands in the token's `cnf.jkt`. */
  readonly jkt: string;
  readonly publicJwk: JWK;
  /** Mint a signed DPoP proof bound to this key. */
  proof(options: DpopProofOptions): Promise<string>;
}

/** `ath` claim value for a given access token. */
export function dpopAth(accessToken: string): string {
  return createHash('sha256').update(accessToken).digest('base64url');
}

/** Create a fresh DPoP key that can mint proofs. Default `ES256`; `RS256` for allowlist tests. */
export async function createDpopKey(alg: DpopAlg = 'ES256'): Promise<DpopKey> {
  const { privateKey, publicKey } = await generateKeyPair(alg, { extractable: true });
  const publicJwk = await exportJWK(publicKey);
  const jkt = await calculateJwkThumbprint(publicJwk, 'sha256');

  // The proof header carries the public key with the minimal members the thumbprint is computed over.
  const jwkHeader: JWK =
    alg === 'ES256'
      ? { kty: publicJwk.kty, crv: publicJwk.crv, x: publicJwk.x, y: publicJwk.y }
      : { kty: publicJwk.kty, n: publicJwk.n, e: publicJwk.e };

  const proof = async (options: DpopProofOptions): Promise<string> => {
    const claims: Record<string, unknown> = {
      htm: options.htm,
      htu: options.htu,
      jti: options.jti ?? randomUUID(),
    };
    if (options.accessToken) {
      claims.ath = dpopAth(options.accessToken);
    }
    const signer = new SignJWT(claims).setProtectedHeader({ alg, typ: 'dpop+jwt', jwk: jwkHeader });
    signer.setIssuedAt(options.iat);
    return signer.sign(privateKey);
  };

  return { jkt, publicJwk, proof };
}
