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
const AUTONOMOUS_AGENT_CIMD_URL = 'http://wiremock:8080/cimd/ENABLED_BASE/autonomous-agent';
const AUTONOMOUS_AGENT_NO_GRANTS_CIMD_URL = 'http://wiremock:8080/cimd/ENABLED_BASE/autonomous-agent-no-grants';
const USER_EMBEDDED_AGENT_NO_GRANTS_CIMD_URL = 'http://wiremock:8080/cimd/ENABLED_BASE/user-embedded-agent-no-grants';
const MISSING_TOKEN_AUTH_METHOD_CIMD_URL = 'http://wiremock:8080/cimd/ENABLED_BASE/missing-token-auth-method';
const SECRET_BASIC_CIMD_URL = 'http://wiremock:8080/cimd/ENABLED_BASE/secret-basic';

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
      missing: { clientId: false, clientName: false },
      metadata: {
        client_id: VALID_CIMD_URL,
        redirect_uris: ['https://client.example.com/callback'],
        token_endpoint_auth_method: 'none',
      },
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

  it('rejects an AGENT CIMD application without subType', async () => {
    const { status, body } = await post(`${cimdBaseUrl(domain.id!)}/applications`, accessToken, {
      name: uniqueName('cimd-agent-bad', true),
      type: 'AGENT',
      cimdUrl: AUTONOMOUS_AGENT_CIMD_URL,
    });

    expect(status).toBe(400);
    expect(JSON.stringify(body)).toContain('subType is required when application type is AGENT');
  });

  it('creates an AUTONOMOUS AGENT application from a CIMD URL and persists subType', async () => {
    const appName = uniqueName('cimd-agent', true);
    const { status, body } = await post(`${cimdBaseUrl(domain.id!)}/applications`, accessToken, {
      name: appName,
      type: 'AGENT',
      subType: 'AUTONOMOUS',
      cimdUrl: AUTONOMOUS_AGENT_CIMD_URL,
    });

    expect(status).toBe(201);
    expect(body.type).toBe('agent');
    expect(body.subType).toBe('AUTONOMOUS');
    expect(body.settings?.oauth?.clientId).toBe(AUTONOMOUS_AGENT_CIMD_URL);
  });

  it('applies AUTONOMOUS subType grant_types default when CIMD doc omits grant_types', async () => {
    const appName = uniqueName('cimd-agent-auto-defaults', true);
    const { status, body } = await post(`${cimdBaseUrl(domain.id!)}/applications`, accessToken, {
      name: appName,
      type: 'AGENT',
      subType: 'AUTONOMOUS',
      cimdUrl: AUTONOMOUS_AGENT_NO_GRANTS_CIMD_URL,
    });

    expect(status).toBe(201);
    expect(body.type).toBe('agent');
    expect(body.subType).toBe('AUTONOMOUS');
    expect(body.settings?.oauth?.grantTypes).toEqual(
      expect.arrayContaining(['client_credentials', 'urn:ietf:params:oauth:grant-type:token-exchange']),
    );
  });

  it('applies USER_EMBEDDED subType defaults when CIMD doc omits grant_types', async () => {
    const appName = uniqueName('cimd-agent-ue-defaults', true);
    const { status, body } = await post(`${cimdBaseUrl(domain.id!)}/applications`, accessToken, {
      name: appName,
      type: 'AGENT',
      subType: 'USER_EMBEDDED',
      cimdUrl: USER_EMBEDDED_AGENT_NO_GRANTS_CIMD_URL,
    });

    expect(status).toBe(201);
    expect(body.type).toBe('agent');
    expect(body.subType).toBe('USER_EMBEDDED');
    expect(body.settings?.oauth?.grantTypes).toEqual(expect.arrayContaining(['authorization_code']));
    expect(body.settings?.oauth?.responseTypes).toEqual(expect.arrayContaining(['code']));
    expect(body.settings?.oauth?.tokenEndpointAuthMethod).toBe('none');
  });

  it('defaults token_endpoint_auth_method to none for a public WEB CIMD app when the doc omits it', async () => {
    const { status, body } = await post(`${cimdBaseUrl(domain.id!)}/applications`, accessToken, {
      name: uniqueName('cimd-app-no-auth', true),
      type: 'WEB',
      cimdUrl: MISSING_TOKEN_AUTH_METHOD_CIMD_URL,
    });

    expect(status).toBe(201);
    expect(body.settings?.oauth?.tokenEndpointAuthMethod).toBe('none');
  });

  it('still rejects a CIMD doc that declares a secret-based token_endpoint_auth_method', async () => {
    const { status, body } = await post(`${cimdBaseUrl(domain.id!)}/applications`, accessToken, {
      name: uniqueName('cimd-app-secret-basic', true),
      type: 'WEB',
      cimdUrl: SECRET_BASIC_CIMD_URL,
    });

    expect(status).toBe(400);
    expect(JSON.stringify(body)).toMatch(/token_endpoint_auth_method/);
  });

});
