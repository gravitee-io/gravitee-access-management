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
import { createCertificate } from '@management-commands/certificate-management-commands';
import { expect } from '@jest/globals';

export async function createDomainCertificate(domain: Domain, accessToken: string) {
  const newCertificate = await createCertificate(domain.id, accessToken, buildCertificate('rs256'));
  expect(newCertificate).toBeDefined();
  return newCertificate;
}

function buildCertificate(suffix: string) {
  return {
    type: 'javakeystore-am-certificate',
    configuration:
      '{"jks":"{\\"name\\":\\"server.jks\\",\\"type\\":\\"\\",\\"size\\":2237,\\"content\\":\\"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\\"}","storepass":"letmein","alias":"mytestkey","keypass":"changeme"}',
    name: 'Certficate ' + suffix,
  };
}
