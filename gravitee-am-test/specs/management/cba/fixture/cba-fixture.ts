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
import {
  createDomain,
  getDomain,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { readFileSync } from 'fs';
import { join } from 'path';
import { patchApplication } from '@management-commands/application-management-commands';
import { generateCertificatePEM } from '@utils/certificate-utils';
import { Fixture } from '../../../test-fixture';

export interface CbaFixture extends Fixture {
  domain: Domain;
  certificateBasedAuthUrl: string;
  enableCba: () => Promise<Domain>;
  disableCba: () => Promise<Domain>;
}

export const setupFixture = async (): Promise<CbaFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('cba', true), 'Description');
  await startDomain(domain.id, accessToken);
  await waitForDomainStart(domain);
  const certificateBasedAuthUrl = 'https://cba-login.example.com';
  return {
    accessToken: accessToken,
    domain: domain,
    certificateBasedAuthUrl: certificateBasedAuthUrl,
    enableCba: async () => {
      return await patchDomain(domain.id, accessToken, {
        loginSettings: {
          certificateBasedAuthEnabled: true,
          certificateBasedAuthUrl: certificateBasedAuthUrl,
        },
      });
    },
    disableCba: async () => {
      return await patchDomain(domain.id, accessToken, {
        loginSettings: {
          certificateBasedAuthEnabled: false,
        },
      });
    },
    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};

export function generateUniqueCertificatePEM(index?: number): string {
  // Use a numeric serial number for better compatibility
  const serialNumber = index !== undefined ? `${Date.now()}${index}` : Date.now().toString();
  return generateCertificatePEM({
    subjectDN: `CN=Test Certificate ${serialNumber}, O=Test Org, C=US`,
    serialNumber,
  });
}

export function generateExpiredCertificatePEM(): string {
  return generateCertificatePEM({
    expired: true,
    subjectDN: 'CN=Expired Test Certificate, O=Test Org, C=US',
  });
}
