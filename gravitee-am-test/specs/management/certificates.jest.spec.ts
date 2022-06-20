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
import fetch from "cross-fetch";
import * as faker from 'faker';
import {afterAll, beforeAll, expect} from '@jest/globals';
import {requestAdminAccessToken} from "@management-commands/token-management-commands";
import {createDomain, deleteDomain, startDomain} from "@management-commands/domain-management-commands";
import {
    createCertificate, deleteCertificate, getAllCertificates,
    getCertificate, getPublicKey, getPublicKeys,
    updateCertificate
} from "@management-commands/certificate-management-commands";
import {buildCertificate} from "../../api/fixtures/certificates";

global.fetch = fetch;

let accessToken;
let domain;
let certificate;

beforeAll(async () => {
    const adminTokenResponse = await requestAdminAccessToken();
    accessToken = adminTokenResponse.body.access_token;
    expect(accessToken).toBeDefined()

    const createdDomain = await createDomain(accessToken, "domain-certificate", faker.company.catchPhraseDescriptor());
    expect(createdDomain).toBeDefined();
    expect(createdDomain.id).toBeDefined();

    const domainStarted = await startDomain(createdDomain.id, accessToken);
    expect(domainStarted).toBeDefined();
    expect(domainStarted.id).toEqual(createdDomain.id);

    domain = domainStarted;
});


describe("when creating certificates", () => {
    for (let i = 0; i < 10; i++) {
        it('must create new certificate: ' + i, async () => {
            const builtCertificate = buildCertificate(i);

            const createdCertificate = await createCertificate(domain.id, accessToken, builtCertificate);
            expect(createdCertificate).toBeDefined();

            expect(createdCertificate.id).toBeDefined();
            expect(createdCertificate.name).toEqual(builtCertificate.name);
            expect(createdCertificate.configuration).toEqual("{\"jks\":\"server.jks\",\"storepass\":\"********\",\"alias\":\"mytestkey\",\"keypass\":\"********\"}");
            expect(createdCertificate.type).toEqual(builtCertificate.type);
            expect(createdCertificate.domain).toEqual(domain.id);
            certificate = createdCertificate;
        });
    }
});

describe("after creating certificates", () => {
    it('must find certificate', async () => {
        const foundCertificate = await getCertificate(domain.id, accessToken, certificate.id);
        expect(foundCertificate).toBeDefined();
        expect(foundCertificate.id).toEqual(certificate.id);
    });

      it('must update certificate', async () => {
          const updatedCert = await updateCertificate(domain.id, accessToken,
              {...certificate, name: "Another certificate name" }, certificate.id);
          expect(updatedCert.name === certificate.name).toBeFalsy();
          certificate = updatedCert;
      });

    it('must find certificate public key', async () => {
        const publicKey = await getPublicKey(domain.id, accessToken, certificate.id);
        expect(publicKey).toBeDefined();
        expect(publicKey).toEqual("AAAAB3NzaC1yc2EAAAADAQABAAABAQCrviVm3+KD98U899xIF5w9I4C/3JTv/ZVduRhcMoGkfBe6sz2pJ2kf6bMjtWnEb91H63GHWdv554ez3HIYxBZsYddaU93YtiqrsbCCQc3GMmFB120WgUWfjqsvPOMcz3PyFy3yw+XLAiND0Pl2lv8K6ejJvfwmTRhy1DI5PQGvRWz57IdoCxjZE8H+Lr79dc/eFhcu6Fksxa2tugv86tyO38sA9v1L2CCQjsqQL8TnMHbDV8ahGK6Abv43KMjHV6tgFhOhHc1a2YaYCtEI0yKKH2t0K3hrHRwUfgqu4Q5xqWpBkEFX05YW8ygrWivXfDwGjqcMyiHEYlRvDcrRP0Jh");
    });

    it('must find certificate public keys', async () => {
        const foundCertificate = await getPublicKeys(domain.id, accessToken, certificate.id);
        expect(foundCertificate).toBeDefined();
        expect(foundCertificate.length).toEqual(2);
    });

    it('must find all certificates', async () => {
          const idpSet = await getAllCertificates(domain.id, accessToken);

          expect(idpSet.size).toEqual(11);
      });

      it('Must delete certificates', async () => {
          await deleteCertificate(domain.id, accessToken, certificate.id);
          const idpSet = await getAllCertificates(domain.id, accessToken);

          expect(idpSet.size).toEqual(10);
      });
});

afterAll(async () => {
    if (domain && domain.id) {
        await deleteDomain(domain.id, accessToken);
    }
});
