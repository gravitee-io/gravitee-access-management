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
import { setup } from '../../test-fixture';
import {
  AppCertificateFixture,
  assignCertificate,
  fetchJwks,
  kidOf,
  requestClientCredentialsToken,
  requestPasswordTokens,
  setupAppCertificateFixture,
  SigningCertificate,
  verifySignature,
} from './fixtures/app-certificate-fixture';

setup(200000);

/**
 * Application > Settings > Certificates: a domain certificate assigned to an application signs
 * that application's tokens. Each check ties the token to the certificate's own public key as
 * published by the management API, not merely to "a key that differs from before".
 */
let fixture: AppCertificateFixture;

beforeAll(async () => {
  fixture = await setupAppCertificateFixture();
});

afterAll(async () => {
  await fixture?.cleanUp();
});

const expectSignedWith = async (token: string, certificate: SigningCertificate) => {
  expect(kidOf(token)).toEqual(certificate.alias);
  await expect(verifySignature(token, certificate.publicKey)).resolves.toBeDefined();
};

describe('Application certificate - access tokens are signed with the assigned certificate', () => {
  it('should sign with the JKS certificate assigned to the application', async () => {
    const token = await requestClientCredentialsToken(fixture, fixture.jksApp);
    await expectSignedWith(token, fixture.jks);
  });

  it('should sign with the PKCS12 certificate assigned to the application', async () => {
    const token = await requestClientCredentialsToken(fixture, fixture.pkcs12App);
    await expectSignedWith(token, fixture.pkcs12);
  });

  it('should publish the assigned certificate keys in the domain JWKS under the token kid', async () => {
    const token = await requestClientCredentialsToken(fixture, fixture.jksApp);
    const jwks = await fetchJwks(fixture);
    expect(jwks).toContainEqual(expect.objectContaining({ kid: kidOf(token), n: fixture.jks.jwk.n }));
  });
});

describe('Application certificate - the assignment is what selects the signer', () => {
  it('should not sign with an assigned certificate when the application has none', async () => {
    const token = await requestClientCredentialsToken(fixture, fixture.defaultApp);
    expect([fixture.jks.alias, fixture.pkcs12.alias]).not.toContain(kidOf(token));
    await expect(verifySignature(token, fixture.jks.publicKey)).rejects.toThrow('signature verification failed');
    await expect(verifySignature(token, fixture.pkcs12.publicKey)).rejects.toThrow('signature verification failed');
  });

  it('should not verify a JKS-signed token with the PKCS12 certificate key', async () => {
    const token = await requestClientCredentialsToken(fixture, fixture.jksApp);
    await expect(verifySignature(token, fixture.pkcs12.publicKey)).rejects.toThrow('signature verification failed');
  });

  it('should sign with the new certificate once the application is reassigned', async () => {
    const before = await requestClientCredentialsToken(fixture, fixture.reassignApp);
    await expectSignedWith(before, fixture.jks);

    await assignCertificate(fixture, fixture.reassignApp, fixture.pkcs12.id);

    const after = await requestClientCredentialsToken(fixture, fixture.reassignApp);
    await expectSignedWith(after, fixture.pkcs12);
  });
});

describe('Application certificate - ID tokens', () => {
  it('should sign the ID token with the assigned certificate too', async () => {
    const { accessToken, idToken } = await requestPasswordTokens(fixture, fixture.pkcs12App);
    await expectSignedWith(accessToken, fixture.pkcs12);
    await expectSignedWith(idToken, fixture.pkcs12);
  });
});
