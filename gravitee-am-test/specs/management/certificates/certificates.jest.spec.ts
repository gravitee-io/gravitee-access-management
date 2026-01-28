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
import {
  setupCertificateFixture,
  CertificatesFixture,
  createPKCS12CertificateRequest,
  createJksCertificateRequest,
} from './fixtures/certificates-fixture';
import {
  createCertificate,
  deleteCertificate,
  getAllCertificates,
  getPublicKey,
  getPublicKeys,
  rotateCertificate,
  updateCertificate,
} from '@management-commands/certificate-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: CertificatesFixture;

beforeAll(async () => {
  fixture = await setupCertificateFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Certificates', () => {
  it('should list certificates', async () => {
    const certificateList = await getAllCertificates(fixture.domain.id, fixture.accessToken);
    expect(certificateList).toHaveLength(1);
  });

  it('should create certificate - JKS', async () => {
    const request = createJksCertificateRequest(fixture.jks);
    const createdCertificate = await createCertificate(fixture.domain.id, fixture.accessToken, request);
    const configuration = JSON.parse(createdCertificate.configuration);
    expect(createdCertificate.name).toBe(request.name);
    expect(createdCertificate.type).toBe(request.type);
    expect(createdCertificate.domain).toBe(fixture.domain.id);
    expect(configuration).toEqual(
      expect.objectContaining({
        alias: fixture.jks.alias,
        storepass: '********',
        keypass: '********',
        jks: '********',
      }),
    );
    const certificateList = await getAllCertificates(fixture.domain.id, fixture.accessToken);
    expect(certificateList).toContainEqual(
      expect.objectContaining({
        id: createdCertificate.id,
      }),
    );
  });

  it('should NOT create certificate - JKS - invalid password', async () => {
    const request = createJksCertificateRequest({ ...fixture.jks, password: 'invalid' });
    await expect(createCertificate(fixture.domain.id, fixture.accessToken, request)).rejects.toHaveProperty('response.status', 400);
  });

  it('should NOT create certificate - JKS - invalid alias', async () => {
    const request = createJksCertificateRequest({ ...fixture.jks, alias: 'invalid' });
    await expect(createCertificate(fixture.domain.id, fixture.accessToken, request)).rejects.toHaveProperty('response.status', 400);
  });

  it('should create certificate - PKCS12', async () => {
    const request = createPKCS12CertificateRequest(fixture.p12);
    const createdCertificate = await createCertificate(fixture.domain.id, fixture.accessToken, request);
    const certificateList = await getAllCertificates(fixture.domain.id, fixture.accessToken);
    expect(certificateList).toContainEqual(
      expect.objectContaining({
        id: createdCertificate.id,
      }),
    );
  });

  it('should create and delete multiple certificates - JKS', async () => {
    const before = await getAllCertificates(fixture.domain.id, fixture.accessToken);
    const created: Array<{ id?: string }> = [];
    try {
      for (let i = 0; i < 10; i++) {
        const request = createJksCertificateRequest(fixture.jks);
        const createdCertificate = await createCertificate(fixture.domain.id, fixture.accessToken, request);
        created.push(createdCertificate);
      }
      const after = await getAllCertificates(fixture.domain.id, fixture.accessToken);
      expect(after.length).toBe(before.length + 10);
    } finally {
      for (const certificate of created) {
        await deleteCertificate(fixture.domain.id, fixture.accessToken, certificate.id);
      }
    }
  });

  it('should update certificate', async () => {
    const request = createPKCS12CertificateRequest(fixture.p12);
    const createdCertificate = await createCertificate(fixture.domain.id, fixture.accessToken, request);
    const certificateList = await getAllCertificates(fixture.domain.id, fixture.accessToken);
    expect(certificateList).toContainEqual(
      expect.objectContaining({
        id: createdCertificate.id,
      }),
    );
    const newName = uniqueName('new', true);
    const updateRequest = { ...request, name: newName };
    const updatedCertificate = await updateCertificate(fixture.domain.id, fixture.accessToken, updateRequest, createdCertificate.id);
    expect(updatedCertificate.name).toBe(newName);
  });

  it('should NOT update certificate - invalid password', async () => {
    const request = createPKCS12CertificateRequest(fixture.p12);
    const createdCertificate = await createCertificate(fixture.domain.id, fixture.accessToken, request);
    const certificateList = await getAllCertificates(fixture.domain.id, fixture.accessToken);
    expect(certificateList).toContainEqual(
      expect.objectContaining({
        id: createdCertificate.id,
      }),
    );
    const updateRequest = createPKCS12CertificateRequest({ ...fixture.p12, password: 'invalid' });
    await expect(updateCertificate(fixture.domain.id, fixture.accessToken, updateRequest, createdCertificate.id)).rejects.toHaveProperty(
      'response.status',
      400,
    );
  });

  it('should delete certificate', async () => {
    const request = createJksCertificateRequest(fixture.jks);
    const createdCertificate = await createCertificate(fixture.domain.id, fixture.accessToken, request);
    await deleteCertificate(fixture.domain.id, fixture.accessToken, createdCertificate.id);
    const certificateList = await getAllCertificates(fixture.domain.id, fixture.accessToken);
    expect(certificateList).not.toContainEqual(
      expect.objectContaining({
        id: createdCertificate.id,
      }),
    );
  });

  it('should NOT delete certificate - used by application', async () => {
    const request = createJksCertificateRequest(fixture.jks);
    const createdCertificate = await createCertificate(fixture.domain.id, fixture.accessToken, request);
    const createdApplication = await createApplication(fixture.domain.id, fixture.accessToken, {
      clientId: 'test',
      clientSecret: 'test',
      description: 'test',
      name: 'test',
      redirectUris: ['https://local.local'],
      type: 'WEB',
    });
    await updateApplication(fixture.domain.id, fixture.accessToken, { certificate: createdCertificate.id }, createdApplication.id);
    await expect(deleteCertificate(fixture.domain.id, fixture.accessToken, createdCertificate.id)).rejects.toHaveProperty(
      'response.status',
      400,
    );

    const certificateList = await getAllCertificates(fixture.domain.id, fixture.accessToken);
    expect(certificateList).toContainEqual(
      expect.objectContaining({
        id: createdCertificate.id,
      }),
    );
  });
  it('should publish keys - JKS', async () => {
    const request = createJksCertificateRequest(fixture.jks);
    const createdCertificate = await createCertificate(fixture.domain.id, fixture.accessToken, request);
    const keys = await getPublicKeys(fixture.domain.id, fixture.accessToken, createdCertificate.id);
    expect(keys).toContainEqual(
      expect.objectContaining({
        fmt: 'PEM',
      }),
    );
    expect(keys).toContainEqual(
      expect.objectContaining({
        fmt: 'SSH-RSA',
      }),
    );
  });

  it('should publish keys - PKCS12', async () => {
    const request = createPKCS12CertificateRequest(fixture.p12);
    const createdCertificate = await createCertificate(fixture.domain.id, fixture.accessToken, request);
    const keys = await getPublicKeys(fixture.domain.id, fixture.accessToken, createdCertificate.id);
    expect(keys).toContainEqual(
      expect.objectContaining({
        fmt: 'PEM',
      }),
    );
    expect(keys).toContainEqual(
      expect.objectContaining({
        fmt: 'SSH-RSA',
        payload:
          'AAAAB3NzaC1yc2EAAAADAQABAAABAQDCMwP5u2NyoE3i/OY9tbdenOUiUQNBKsBTFFWRrlq6FAkHox1zBowaazrIPuq6ayhfe2NTUX57oowKbvb0c+Lkbk2MwimpO6Fx4HfZ8mpy2YFelohDqoLb2ws37p9gOw9T8ESeVw63U2J89qw97lFr11cG8RsPsZUIIQMrJni1J8Oql90R5oxIn5HTBfxHGtOfwiXOYe1vtNcNieNGjR4nWUda6FBicSShM87wrk1oNxfe3F5hxx/J80Vl7DNUtGI93ckv0X05JCmdI3pUHSoyi+M4PGp9/RJO5SJDYJ+GRptOMzllA/sZbG7oROB9dbD6NkV03GT7HMnLNZGTcLg1',
      }),
    );
  });
});

describe('Certificates - Rotating', () => {
  it('before the renewal, only one System certificate exists', async () => {
    const certificateList = await getAllCertificates(fixture.domain.id, fixture.accessToken);
    expect(certificateList.filter((cert) => cert.system).length).toBe(1);
  });

  it('when rotate endpoint is called, new system certificate is generated', async () => {
    const refreshCert = await rotateCertificate(fixture.domain.id, fixture.accessToken);
    expect(refreshCert.name.startsWith('Default ')).toBeTruthy();
    expect(refreshCert.system).toBeTruthy();

    const certificateList = await getAllCertificates(fixture.domain.id, fixture.accessToken);
    expect(certificateList.filter((cert) => cert.system).length).toBe(2);

    const publicKey = await getPublicKey(fixture.domain.id, fixture.accessToken, refreshCert.id);
    expect(publicKey).toBeDefined();

    const foundCertificate = await getPublicKeys(fixture.domain.id, fixture.accessToken, refreshCert.id);
    expect(foundCertificate).toBeDefined();
    expect(foundCertificate.length).toEqual(2);
  });

  it('after the renewal, new System certificates is added', async () => {
    const certificateList = await getAllCertificates(fixture.domain.id, fixture.accessToken);
    const renewedBefore = certificateList.filter((cert) => cert.status.toUpperCase() === 'RENEWED');
    const newDefault = await rotateCertificate(fixture.domain.id, fixture.accessToken);
    const newCertificateList = await getAllCertificates(fixture.domain.id, fixture.accessToken);
    expect(newDefault.name).toContain('Default');
    expect(certificateList.length).toBe(newCertificateList.length - 1);

    const renewedAfter = newCertificateList.filter((cert) => cert.status.toUpperCase() === 'RENEWED');
    expect(renewedBefore.length).toBe(renewedAfter.length - 1);
  });
});
