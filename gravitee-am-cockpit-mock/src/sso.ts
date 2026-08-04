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

/**
 * Mints the short-lived SSO token cockpit hands to a browser when it redirects a user into AM.
 *
 * Mirrors cockpit's own `JWTService`: RS512, `kid=cockpit`, `iss=https://gravitee.cockpit`, a 10 second
 * TTL, and `sub`/`org`/`env`/`redirect_uri` claims. AM's CockpitAuthenticationFilter verifies it with
 * the public key of the certificate held under alias `cockpit-client` in the keystore configured by
 * `cloud.connector.ws.ssl.keystore.*`, so the private key given here must match that certificate.
 *
 * Note cockpit does NOT set `preferred_username`; the filter therefore builds a principal with a null
 * username and the lookup succeeds only on `sub` (the external id) plus source `cockpit`. The USER
 * command must have run first. Reproducing that faithfully is the point of this mock.
 */

import { createSign, createPrivateKey, KeyObject } from 'node:crypto';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';

/** Cockpit's JWTService constants, kept identical so AM validates the token unchanged. */
const ISSUER = 'https://gravitee.cockpit';
const KID = 'cockpit';
const ALGORITHM = 'RS512';
const TTL_SECONDS = 10;

export interface SsoTokenRequest {
  /** Cockpit user id. Lands in `sub`, and must match the USER command's `id`. */
  sub: string;
  /** Organization the user is signing in to. */
  org: string;
  /** Environment to land on. Optional: AM only uses it to build the redirect path. */
  env?: string;
  redirectUri?: string;
  /** Override the default 10s TTL, e.g. to exercise AM's expiry handling. */
  ttlSeconds?: number;
}

const base64url = (input: Buffer | string): string =>
  Buffer.from(input).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

export class SsoTokenSigner {
  private readonly privateKey: KeyObject;

  constructor(privateKeyPath: string) {
    this.privateKey = createPrivateKey(readFileSync(privateKeyPath));
  }

  sign(request: SsoTokenRequest): string {
    const issuedAt = Math.floor(Date.now() / 1000);
    const header = { alg: ALGORITHM, kid: KID, typ: 'JWT' };
    const claims: Record<string, unknown> = {
      jti: randomUUID(),
      iss: ISSUER,
      sub: request.sub,
      iat: issuedAt,
      exp: issuedAt + (request.ttlSeconds ?? TTL_SECONDS),
      org: request.org,
    };
    if (request.env !== undefined) claims.env = request.env;
    if (request.redirectUri !== undefined) claims.redirect_uri = request.redirectUri;

    const signingInput = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claims))}`;
    const signature = createSign('RSA-SHA512').update(signingInput).sign(this.privateKey);

    return `${signingInput}.${base64url(signature)}`;
  }
}
