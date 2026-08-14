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
import { setup } from '../../test-fixture';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { privateJwk } from '@api-fixtures/oidc';
import { setupTokenAuthFixture, TokenAuthFixture } from '../token-auth-methods/fixtures/token-auth-fixture';
import { PrivateKeyJwtFixture, setupPrivateKeyJwtFixture } from '../token-auth-methods/fixtures/private-key-jwt-fixture';

// The introspection endpoint shares the gateway's clientAuthHandler with the token endpoint
// (OAuth2Provider.java), and discovery advertises the same
// introspection_endpoint_auth_methods_supported. But every existing auth-method test posts to
// token_endpoint only, so the non-Basic methods were never proven to work on this route.

setup(200000);

const FORM = 'application/x-www-form-urlencoded';
const CLIENT_ASSERTION_TYPE = 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer';

describe('Introspection - client_secret_post authentication', () => {
  let fixture: TokenAuthFixture;

  beforeAll(async () => {
    fixture = await setupTokenAuthFixture({
      tokenEndpointAuthMethod: 'client_secret_post',
      grantTypes: ['client_credentials'],
    });
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  const mintToken = async (): Promise<string> => {
    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=client_credentials&client_id=${encodeURIComponent(fixture.clientId)}&client_secret=${encodeURIComponent(
        fixture.clientSecret,
      )}`,
      { 'Content-type': FORM },
    ).expect(200);
    return response.body.access_token;
  };

  it('accepts credentials supplied in the request body', async () => {
    const accessToken = await mintToken();

    const response = await performPost(
      fixture.oidc.introspection_endpoint,
      '',
      `token=${encodeURIComponent(accessToken)}&client_id=${encodeURIComponent(fixture.clientId)}&client_secret=${encodeURIComponent(
        fixture.clientSecret,
      )}`,
      { 'Content-type': FORM },
    ).expect(200);

    expect(response.body.active).toBe(true);
    expect(response.body.client_id).toBe(fixture.clientId);
  });

  it('rejects a wrong client secret supplied in the request body', async () => {
    const accessToken = await mintToken();

    const response = await performPost(
      fixture.oidc.introspection_endpoint,
      '',
      `token=${encodeURIComponent(accessToken)}&client_id=${encodeURIComponent(fixture.clientId)}&client_secret=not-the-secret`,
      { 'Content-type': FORM },
    ).expect(401);

    expect(response.body.error).toBe('invalid_client');
  });
});

describe('Introspection - private_key_jwt authentication', () => {
  let fixture: PrivateKeyJwtFixture;

  beforeAll(async () => {
    fixture = await setupPrivateKeyJwtFixture();
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  const clientAssertion = async (audience: string, key: jose.JWK = privateJwk as jose.JWK): Promise<string> => {
    const privateKey = await jose.importJWK(key, 'RS256');
    const now = Math.floor(Date.now() / 1000);
    return new jose.SignJWT({})
      .setProtectedHeader({ alg: 'RS256', kid: '123' })
      .setIssuer(fixture.clientId)
      .setSubject(fixture.clientId)
      .setAudience(audience)
      .setJti(crypto.randomUUID())
      .setIssuedAt(now)
      .setExpirationTime(now + 300)
      .sign(privateKey);
  };

  const mintToken = async (): Promise<string> => {
    const assertion = await clientAssertion(fixture.oidc.token_endpoint);
    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=client_credentials&client_assertion_type=${encodeURIComponent(
        CLIENT_ASSERTION_TYPE,
      )}&client_assertion=${encodeURIComponent(assertion)}`,
      { 'Content-type': FORM },
    ).expect(200);
    return response.body.access_token;
  };

  it('accepts a client assertion signed with the registered key', async () => {
    const accessToken = await mintToken();
    const assertion = await clientAssertion(fixture.oidc.token_endpoint);

    const response = await performPost(
      fixture.oidc.introspection_endpoint,
      '',
      `token=${encodeURIComponent(accessToken)}&client_assertion_type=${encodeURIComponent(
        CLIENT_ASSERTION_TYPE,
      )}&client_assertion=${encodeURIComponent(assertion)}`,
      { 'Content-type': FORM },
    ).expect(200);

    expect(response.body.active).toBe(true);
    expect(response.body.client_id).toBe(fixture.clientId);
  });

  it('rejects a client assertion signed with an unregistered key', async () => {
    const accessToken = await mintToken();
    const { privateKey: unregisteredKey } = await jose.generateKeyPair('RS256', { extractable: true });
    const unregisteredJwk = await jose.exportJWK(unregisteredKey);
    const assertion = await clientAssertion(fixture.oidc.token_endpoint, unregisteredJwk);

    const response = await performPost(
      fixture.oidc.introspection_endpoint,
      '',
      `token=${encodeURIComponent(accessToken)}&client_assertion_type=${encodeURIComponent(
        CLIENT_ASSERTION_TYPE,
      )}&client_assertion=${encodeURIComponent(assertion)}`,
      { 'Content-type': FORM },
    ).expect(401);

    expect(response.body.error).toBe('invalid_client');
  });
});
