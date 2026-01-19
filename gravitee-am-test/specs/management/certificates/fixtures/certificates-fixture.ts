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
import { Domain } from '@management-models/Domain';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, startDomain, waitForDomainStart } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { readFileSync } from 'fs';
import { join } from 'path';
import { Fixture } from '../../../test-fixture';

export interface CertificatesFixture extends Fixture {
  domain: Domain;
  jks: CertificatePayload;
  p12: CertificatePayload;
}

export interface CertificatePayload {
  password: string;
  alias: string;
  content: string;
  type: string;
  contentType: string;
}

export const setupCertificateFixture = async (): Promise<CertificatesFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('certificates', true), 'Description');

  return {
    accessToken: accessToken,
    domain: domain,
    jks: {
      password: 'changeit',
      alias: 'test',
      content: read('test.jks'),
      type: 'javakeystore-am-certificate',
      contentType: 'application/x-java-keystore',
    },
    p12: {
      password: 'changeit',
      alias: 'test',
      content: read('test.p12'),
      type: 'pkcs12-am-certificate',
      contentType: 'application/x-pkcs12',
    },
    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};

const read = (file: string): string => {
  const path = join(__dirname, file);
  return readFileSync(path).toString('base64');
};

export const createJksCertificateRequest = (payload: CertificatePayload): any => {
  const size = new TextEncoder().encode(payload.content).length;
  const content = {
    jks: JSON.stringify({
      name: uniqueName('certificates', true),
      type: payload.contentType,
      size: size,
      content: payload.content,
    }),
  };
  return createBaseCertificateRequest(payload, content);
};

export const createPKCS12CertificateRequest = (payload: CertificatePayload): any => {
  const size = new TextEncoder().encode(payload.content).length;
  const content = {
    content: JSON.stringify({
      name: uniqueName('certificates', true),
      type: payload.contentType,
      size: size,
      content: payload.content,
    }),
  };
  return createBaseCertificateRequest(payload, content);
};

const createBaseCertificateRequest = (payload: CertificatePayload, content: any): any => {
  return {
    ...{
      type: payload.type,
      name: uniqueName('certificates', true),
      configuration: JSON.stringify({
        storepass: payload.password,
        alias: payload.alias,
        keypass: payload.password,
        algorithm: 'RS256',
        use: ['sig', 'enc'],
        ...content,
      }),
    },
  };
};
