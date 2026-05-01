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
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { setup } from '../../test-fixture';
import {
  MtlsAuthFixture,
  encodeCertForHeader,
  postWithClientCert,
  readCert,
  setupMtlsAuthFixture,
} from './fixtures/mtls-auth-fixture';

setup(300000);

const JWT_FORMAT = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;

describe('mTLS OAuth Client Authentication (RFC 8705)', () => {
  let fixture: MtlsAuthFixture;

  beforeAll(async () => {
    fixture = await setupMtlsAuthFixture();
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  describe('tls_client_auth', () => {
    it('should obtain access_token when client presents certificate with configured Subject DN', async () => {
      const response = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=client_credentials&client_id=${encodeURIComponent(fixture.tlsAuthClientId)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          'X-Client-Cert': encodeCertForHeader(fixture.validCertPem),
        },
      ).expect(200);

      expect(response.body.access_token).toMatch(JWT_FORMAT);
      expect(response.body.token_type).toEqual('bearer');
    });

    it('should reject request when client presents certificate with wrong Subject DN', async () => {
      await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=client_credentials&client_id=${encodeURIComponent(fixture.tlsAuthClientId)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          'X-Client-Cert': encodeCertForHeader(fixture.wrongCertPem),
        },
      ).expect(401);
    });

    it('should reject request when no client certificate is presented', async () => {
      await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=client_credentials&client_id=${encodeURIComponent(fixture.tlsAuthClientId)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
        },
      ).expect(401);
    });
  });

  describe('self_signed_tls_client_auth', () => {
    it('should obtain access_token when certificate SHA-256 thumbprint matches JWKS entry', async () => {
      const response = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=client_credentials&client_id=${encodeURIComponent(fixture.selfSignedClientId)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          'X-Client-Cert': encodeCertForHeader(fixture.validCertPem),
        },
      ).expect(200);

      expect(response.body.access_token).toMatch(JWT_FORMAT);
      expect(response.body.token_type).toEqual('bearer');
    });

    it('should reject request when certificate thumbprint is not registered in JWKS', async () => {
      await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=client_credentials&client_id=${encodeURIComponent(fixture.selfSignedClientId)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          'X-Client-Cert': encodeCertForHeader(fixture.wrongCertPem),
        },
      ).expect(401);
    });

    it('should reject request when no client certificate is presented', async () => {
      await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=client_credentials&client_id=${encodeURIComponent(fixture.selfSignedClientId)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
        },
      ).expect(401);
    });
  });

  describe('tls_client_auth via nginx mTLS edge stack', () => {
    let edgeTokenEndpoint: string;
    let clientACert: string;
    let clientAKey: string;
    let clientBCert: string;
    let clientBKey: string;

    beforeAll(() => {
      const tokenPath = new URL(fixture.oidc.token_endpoint).pathname;
      edgeTokenEndpoint = `${process.env.MTLS_URL}${tokenPath}`;
      clientACert = readCert('/certs/client-a/client.crt');
      clientAKey = readCert('/certs/client-a/client.key');
      clientBCert = readCert('/certs/client-b/client.crt');
      clientBKey = readCert('/certs/client-b/client.key');
    });

    it('should issue token when CA-signed cert with matching Subject DN passes through nginx', async () => {
      const response = await postWithClientCert(
        edgeTokenEndpoint,
        `grant_type=client_credentials&client_id=${encodeURIComponent(fixture.edgeTlsClientId)}`,
        clientACert,
        clientAKey,
      );
      expect(response.status).toBe(200);
      const body = await response.json();
      expect(body.access_token).toMatch(JWT_FORMAT);
      expect(body.token_type).toEqual('bearer');
    });

    it('should reject when CA-signed cert Subject DN does not match configured value', async () => {
      const response = await postWithClientCert(
        edgeTokenEndpoint,
        `grant_type=client_credentials&client_id=${encodeURIComponent(fixture.edgeTlsClientId)}`,
        clientBCert,
        clientBKey,
      );
      expect(response.status).toBe(401);
    });
  });
});
