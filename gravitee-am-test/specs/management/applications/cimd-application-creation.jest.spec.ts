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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, patchDomain, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { createApplication, patchApplication } from '@management-commands/application-management-commands';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';

setup(120000);

const VALID_CIMD_URL = 'http://wiremock:8080/cimd/ENABLED_BASE/valid-none';
const NOT_FOUND_URL = 'http://wiremock:8080/cimd/does-not-exist';

const cimdBaseUrl = (domainId: string): string =>
  `${process.env.AM_MANAGEMENT_ENDPOINT}/organizations/${process.env.AM_DEF_ORG_ID}/environments/${process.env.AM_DEF_ENV_ID}/domains/${domainId}/cimd`;

const post = async (url: string, accessToken: string, body: unknown): Promise<{ status: number; body: any }> => {
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  let parsed: any = undefined;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = text;
    }
  }
  return { status: res.status, body: parsed };
};

describe('CIMD application creation', () => {
  let accessToken: string;
  let domain: Domain;
  let template: Application;

  beforeAll(async () => {
    accessToken = await requestAdminAccessToken();

    domain = await createDomain(accessToken, uniqueName('cimd-create', true), 'CIMD app create');
    expect(domain.id).toBeDefined();

    template = await createApplication(domain.id!, accessToken, {
      name: uniqueName('cimd-template', true),
      type: 'WEB',
      redirectUris: ['https://client.example.com/callback'],
    } as any);
    await patchApplication(domain.id!, accessToken, { template: true } as any, template.id!);

    await patchDomain(domain.id!, accessToken, {
      oidc: {
        cimdSettings: {
          enabled: true,
          allowUnsecuredHttpUri: true,
          allowPrivateIpAddress: true,
          allowedDomains: [],
          fetchTimeoutMs: 5000,
          maxResponseSizeKb: 10,
          cacheTtlSeconds: 3600,
          cacheMaxEntries: 500,
          templateId: template.id,
        },
      },
    } as any);
  });

  afterAll(async () => {
    if (domain?.id) {
      await safeDeleteDomain(domain.id, accessToken);
    }
  });

  it('validates a valid CIMD URL and returns parsed metadata', async () => {
    const { status, body } = await post(`${cimdBaseUrl(domain.id!)}/validate`, accessToken, { url: VALID_CIMD_URL });
    expect(status).toBe(200);
    expect(body).toMatchObject({
      url: VALID_CIMD_URL,
      clientId: VALID_CIMD_URL,
      redirectUris: ['https://client.example.com/callback'],
      tokenEndpointAuthMethod: 'none',
      missing: { clientId: false, clientName: false },
    });
  });

  it('rejects an unreachable CIMD URL with 4xx', async () => {
    const { status } = await post(`${cimdBaseUrl(domain.id!)}/validate`, accessToken, { url: NOT_FOUND_URL });
    expect(status).toBeGreaterThanOrEqual(400);
    expect(status).toBeLessThan(500);
  });

  it('creates an application from a CIMD URL with clientId set to the URL', async () => {
    const appName = uniqueName('cimd-app', true);
    const { status, body } = await post(`${cimdBaseUrl(domain.id!)}/applications`, accessToken, {
      name: appName,
      type: 'WEB',
      cimdUrl: VALID_CIMD_URL,
    });

    expect(status).toBe(201);
    expect(body.id).toBeDefined();
    expect(body.name).toBe(appName);
    expect(body.settings?.oauth?.clientId).toBe(VALID_CIMD_URL);
    expect(body.settings?.oauth?.redirectUris).toEqual(['https://client.example.com/callback']);
  });
});
