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
import { expect } from '@jest/globals';
import { randomBytes } from 'crypto';
import { uniqueName } from '@utils-commands/misc';
import { buildAutomationCertificateDef } from './automation-definitions';
import {
  AutomationApplicationFixture,
  buildAutomationApplicationDef,
  setupAutomationApplicationFixture,
} from './automation-application-fixture';

/** A value as the Automation API recommends it: 32 random bytes, base64url-encoded (43 characters). */
export const newSecretValue = (): string => randomBytes(32).toString('base64url');

/** A service application definition, able to request client_credentials tokens. */
export const buildServiceApplicationDef = (key: string, certificate: string, overrides: Record<string, unknown> = {}) =>
  buildAutomationApplicationDef({ key, type: 'service', certificate, settings: {}, ...overrides });

export interface AutomationClientSecretFixture extends AutomationApplicationFixture {
  /** Key of a certificate the service applications sign their tokens with. */
  signingCertificateKey: string;
  /** Creates a service application through the Automation API and returns its key and clientId. */
  createServiceApplication: () => Promise<{ key: string; clientId: string }>;
  /** A unique client secret key. */
  newSecretKey: () => string;
  /** Applies a client secret through the Automation API and returns the raw response. */
  putSecret: (applicationKey: string, key: string, value: string, query?: string) => Promise<any>;
}

export const setupAutomationClientSecretFixture = async (): Promise<AutomationClientSecretFixture> => {
  const applicationFixture = await setupAutomationApplicationFixture();
  const { client, domainKey } = applicationFixture;

  try {
    const signingCertificateKey = uniqueName('signcert', true).toLowerCase();
    const certificate = await client.putCertificate(domainKey, buildAutomationCertificateDef({ key: signingCertificateKey, keystore: 0 }));
    expect(certificate.status).toBe(200);

    return {
      ...applicationFixture,
      signingCertificateKey,
      createServiceApplication: async () => {
        const key = applicationFixture.newApplicationKey();
        const created = await applicationFixture.createApplication(buildServiceApplicationDef(key, signingCertificateKey));
        return { key, clientId: created.clientId };
      },
      newSecretKey: () => uniqueName('secret', true).toLowerCase(),
      putSecret: (applicationKey, key, value, query = '') => client.putClientSecret(domainKey, applicationKey, { key, value }, query),
    };
  } catch (error) {
    await applicationFixture.cleanUp();
    throw error;
  }
};
