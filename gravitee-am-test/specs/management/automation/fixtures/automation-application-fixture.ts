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
import { listDomains } from '@management-commands/domain-management-commands';
import { getApplication, listApplications } from '@management-commands/application-management-commands';
import { getAllCertificates } from '@management-commands/certificate-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Application } from '@management-models/Application';
import { AutomationDomainFixture, setupAutomationDomainFixture } from './automation-domain-fixture';

export const APPLICATION_TEST = {
  REDIRECT_URI: 'https://app.example.com/callback',
} as const;

export interface AutomationApplicationDef {
  key: string;
  name?: string;
  type?: string;
  certificate?: string;
  clientId?: string;
  enabled?: boolean;
  identityProviders?: { identity: string; priority?: number }[];
  settings?: Record<string, any>;
  [extra: string]: unknown;
}

/** A web application with the minimal settings AM requires (a redirect URI for the authorization code grant). */
export const buildAutomationApplicationDef = (overrides: AutomationApplicationDef): AutomationApplicationDef => ({
  name: applicationName(overrides.key),
  type: 'web',
  certificate: 'signing-cert',
  settings: { oauth: { redirectUris: [APPLICATION_TEST.REDIRECT_URI] } },
  ...overrides,
});

const applicationName = (key: string): string => `Automation app ${key}`;

export interface AutomationApplicationFixture extends AutomationDomainFixture {
  /** Internal id of the domain, for the Management API calls. */
  domainId: string;
  /** A unique application key, removed by the fixture cleanup. */
  newApplicationKey: () => string;
  /** Creates an application through the Automation API and returns the response body. */
  createApplication: (overrides: AutomationApplicationDef) => Promise<any>;
  /** The application as the Management API returns it. */
  managementApplication: (key: string) => Promise<Application>;
  /** Internal id of a certificate created through the Automation API. */
  certificateId: (key: string) => Promise<string>;
  /** Internal id of the domain's system certificate. */
  systemCertificateId: () => Promise<string>;
  /** Internal id of an identity provider created through the Automation API. */
  identityProviderId: (key: string) => Promise<string>;
}

export const setupAutomationApplicationFixture = async (): Promise<AutomationApplicationFixture> => {
  const domainFixture = await setupAutomationDomainFixture({ keyPrefix: 'autoapp' });
  const { client, domainKey, accessToken } = domainFixture;
  const applicationKeys: string[] = [];

  try {
    const [domain] = (await listDomains(accessToken, { q: `Automation Domain ${domainKey}` })).data;
    expect(domain.id).toEqual(expect.any(String));

    const newApplicationKey = (): string => {
      const key = uniqueName('app', true).toLowerCase();
      applicationKeys.push(key);
      return key;
    };

    return {
      ...domainFixture,
      domainId: domain.id,
      newApplicationKey,
      createApplication: async (overrides) => {
        const response = await client.putApplication(domainKey, buildAutomationApplicationDef(overrides));
        expect(response.status).toBe(200);
        return response.body;
      },
      managementApplication: async (key) => {
        const page = await listApplications(domain.id, accessToken, { q: applicationName(key) });
        expect(page.data).toHaveLength(1);
        return getApplication(domain.id, accessToken, page.data[0].id);
      },
      certificateId: async (key) => (await getAllCertificates(domain.id, accessToken)).find((c) => c.name.endsWith(key)).id,
      systemCertificateId: async () => (await getAllCertificates(domain.id, accessToken)).find((c) => c.system).id,
      identityProviderId: async (key) => (await getAllIdps(domain.id, accessToken)).find((idp) => idp.name.endsWith(key)).id,
      cleanUp: async () => {
        for (const key of applicationKeys) {
          await client.deleteApplication(domainKey, key);
        }
        await domainFixture.cleanUp();
      },
    };
  } catch (error) {
    await domainFixture.cleanUp();
    throw error;
  }
};
