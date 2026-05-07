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
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import * as jose from 'jose';
import crypto from 'crypto';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { JWT_FORMAT } from '@specs-utils/jwt-format';
import { setup } from '../../test-fixture';
import { PrivateKeyJwtFixture, setupPrivateKeyJwtFixture } from './fixtures/private-key-jwt-fixture';
import { privateJwk } from '@api-fixtures/oidc';

setup(200000);

const CLIENT_ASSERTION_TYPE = 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer';

async function createPrivateKeyJwtAssertion(clientId: string, audience: string, privateKeyJwk: jose.JWK): Promise<string> {
  const privateKey = await jose.importJWK(privateKeyJwk, 'RS256');
  const now = Math.floor(Date.now() / 1000);
  return new jose.SignJWT({})
    .setProtectedHeader({ alg: 'RS256', kid: '123' })
    .setIssuer(clientId)
    .setSubject(clientId)
    .setAudience(audience)
    .setJti(crypto.randomUUID())
    .setIssuedAt(now)
    .setExpirationTime(now + 300)
    .sign(privateKey);
}

describe('private_key_jwt', () => {
  let fixture: PrivateKeyJwtFixture;

  beforeAll(async () => {
    fixture = await setupPrivateKeyJwtFixture();
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  it('should obtain token using private_key_jwt', async () => {
    const assertion = await createPrivateKeyJwtAssertion(fixture.clientId, fixture.oidc.token_endpoint, privateJwk as jose.JWK);

    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(CLIENT_ASSERTION_TYPE)}&client_assertion=${encodeURIComponent(assertion)}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    ).expect(200);

    expect(response.body.access_token).toMatch(JWT_FORMAT);
    expect(response.body.token_type).toEqual('bearer');
  });

  it('should reject token request when JWT is signed with an unregistered key', async () => {
    const { privateKey: unregisteredKey } = await jose.generateKeyPair('RS256');
    const now = Math.floor(Date.now() / 1000);
    const assertion = await new jose.SignJWT({})
      .setProtectedHeader({ alg: 'RS256' })
      .setIssuer(fixture.clientId)
      .setSubject(fixture.clientId)
      .setAudience(fixture.oidc.token_endpoint)
      .setJti(crypto.randomUUID())
      .setIssuedAt(now)
      .setExpirationTime(now + 300)
      .sign(unregisteredKey);

    await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(CLIENT_ASSERTION_TYPE)}&client_assertion=${encodeURIComponent(assertion)}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    ).expect(401);
  });

  it('should reject token request when JWT assertion has expired', async () => {
    const privateKey = await jose.importJWK(privateJwk as jose.JWK, 'RS256');
    const past = Math.floor(Date.now() / 1000) - 600;
    const assertion = await new jose.SignJWT({})
      .setProtectedHeader({ alg: 'RS256', kid: '123' })
      .setIssuer(fixture.clientId)
      .setSubject(fixture.clientId)
      .setAudience(fixture.oidc.token_endpoint)
      .setJti(crypto.randomUUID())
      .setIssuedAt(past)
      .setExpirationTime(past + 300)
      .sign(privateKey);

    await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(CLIENT_ASSERTION_TYPE)}&client_assertion=${encodeURIComponent(assertion)}`,
      { 'Content-type': 'application/x-www-form-urlencoded' },
    ).expect(401);
  });
});
