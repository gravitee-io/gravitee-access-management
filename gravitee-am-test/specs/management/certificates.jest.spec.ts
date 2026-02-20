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
import fetch from 'cross-fetch';
import * as faker from 'faker';
import { afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, deleteDomain, setupDomainForTest, startDomain } from '@management-commands/domain-management-commands';
import {
  createCertificate,
  deleteCertificate,
  getAllCertificates,
  getCertificate,
  getPublicKey,
  getPublicKeys,
  rotateCertificate,
  updateCertificate,
} from '@management-commands/certificate-management-commands';
import { buildCertificate } from '../../api/fixtures/certificates';

global.fetch = fetch;

let accessToken;
let domain;
let certificate;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  domain = await setupDomainForTest('domain-certificate', { accessToken }).then((it) => it.domain);
});

describe('when creating certificates', () => {
  for (let i = 0; i < 2; i++) {
    it('must create new certificate: ' + i, async () => {
      const builtCertificate = buildCertificate(i);

      const createdCertificate = await createCertificate(domain.id, accessToken, builtCertificate);
      expect(createdCertificate).toBeDefined();

      expect(createdCertificate.id).toBeDefined();
      expect(createdCertificate.name).toEqual(builtCertificate.name);
      const expectedAlias = i % 2 === 0 ? 'mytestkey' : 'my4096key';
      expect(createdCertificate.configuration).toEqual(
        `{"jks":"********","storepass":"********","alias":"${expectedAlias}","keypass":"********"}`,
      );
      expect(createdCertificate.type).toEqual(builtCertificate.type);
      expect(createdCertificate.domain).toEqual(domain.id);
      certificate = createdCertificate;
    });
  }
});

describe('after creating certificates', () => {
  it('must find certificate', async () => {
    const foundCertificate = await getCertificate(domain.id, accessToken, certificate.id);
    expect(foundCertificate).toBeDefined();
    expect(foundCertificate.id).toEqual(certificate.id);
  });

  it('must update certificate', async () => {
    const updatedCert = await updateCertificate(
      domain.id,
      accessToken,
      { ...certificate, name: 'Another certificate name' },
      certificate.id,
    );
    expect(updatedCert.name === certificate.name).toBeFalsy();
    certificate = updatedCert;
  });

  it('must find certificate public key', async () => {
    const publicKey = await getPublicKey(domain.id, accessToken, certificate.id);
    expect(publicKey).toBeDefined();
    expect(publicKey.length).toBeGreaterThan(0);
  });

  it('must find certificate public keys', async () => {
    const foundCertificate = await getPublicKeys(domain.id, accessToken, certificate.id);
    expect(foundCertificate).toBeDefined();
    expect(foundCertificate.length).toBeGreaterThanOrEqual(1);
  });

  it('must find all certificates', async () => {
    const idpSet = await getAllCertificates(domain.id, accessToken);

    expect(idpSet).toHaveLength(3);
  });

  it('Must delete certificates', async () => {
    await deleteCertificate(domain.id, accessToken, certificate.id);
    const idpSet = await getAllCertificates(domain.id, accessToken);

    expect(idpSet).toHaveLength(2);
  });
});

describe('When we want to renew a certificate', () => {
  let certificateCount = 0;
  it('before the renewal, only one System certificate exists', async () => {
    const foundCertificates = await getAllCertificates(domain.id, accessToken);
    certificateCount = foundCertificates.length;
    let numberOfDefault = 0;
    foundCertificates.forEach((cert) => {
      if (cert.system) {
        numberOfDefault++;
      }
    });
    expect(foundCertificates).toBeDefined();
    expect(numberOfDefault).toEqual(1);
  });

  it('when rotate endpoint is called, new system certificate is generated', async () => {
    const refreshCert = await rotateCertificate(domain.id, accessToken);
    expect(refreshCert.name.startsWith('Default ')).toBeTruthy();
    expect(refreshCert.system).toBeTruthy();
    certificate = refreshCert;
  });

  it('must find certificate public key', async () => {
    const publicKey = await getPublicKey(domain.id, accessToken, certificate.id);
    expect(publicKey).toBeDefined();
  });

  it('must find certificate public keys', async () => {
    const foundCertificate = await getPublicKeys(domain.id, accessToken, certificate.id);
    expect(foundCertificate).toBeDefined();
    expect(foundCertificate.length).toEqual(2);
  });

  it('After the renewal, two System certificates exist', async () => {
    const foundCertificates = await getAllCertificates(domain.id, accessToken);
    certificateCount = foundCertificates.length;
    let numberOfDefault = 0;
    let numberOfRenewed = 0;
    foundCertificates.forEach((cert) => {
      if (cert.system) {
        numberOfDefault++;
      }
      if (cert.status.toLowerCase() === 'renewed') {
        numberOfRenewed++;
      }
    });
    expect(foundCertificates).toBeDefined();
    expect(numberOfDefault).toEqual(2);
    expect(numberOfRenewed).toEqual(1);
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await deleteDomain(domain.id, accessToken);
  }
});
