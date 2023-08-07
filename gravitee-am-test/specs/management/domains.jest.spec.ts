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
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, deleteDomain, getDomain, patchDomain, startDomain } from '@management-commands/domain-management-commands';
import { getWellKnownOpenIdConfiguration, performOptions } from '@gateway-commands/oauth-oidc-commands';
import { delay } from '@utils-commands/misc';

global.fetch = fetch;

let accessToken;
let domain;
let userInfo;

jest.setTimeout(200000);

beforeAll(async () => {
  const adminTokenResponse = await requestAdminAccessToken();
  accessToken = adminTokenResponse.body.access_token;
  expect(accessToken).toBeDefined();

  const createdDomain = await createDomain(accessToken, 'domain-domain', faker.company.catchPhraseDescriptor());
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();
  domain = createdDomain;

  const domainStarted = await startDomain(domain.id, accessToken);
  expect(domainStarted).toBeDefined();
  expect(domainStarted.id).toEqual(domain.id);
  domain = domainStarted;
  await delay(5000);
  const wellKnown = await getWellKnownOpenIdConfiguration(domain.hrid);
  userInfo = wellKnown.body['userinfo_endpoint'];
});

describe('when using the domains commands', () => {
  it('Must Find a domain by id', async () => {
    const foundDomain = await getDomain(domain.id, accessToken);
    expect(foundDomain).toBeDefined();
    expect(foundDomain.id).toEqual(foundDomain.id);
  });

  it('Must Start a domain', async () => {
    const domainStarted = await startDomain(domain.id, accessToken);
    expect(domainStarted).toBeDefined();
    expect(domainStarted.id).toEqual(domain.id);
  });
});

describe('Entrypoints: CORS settings', () => {
  it('should create default CORS settings', async () => {
    const patchedDomain = await patchDomain(domain.id, accessToken, {
      path: `${domain.path}`,
      vhostMode: false,
      vhosts: [],
      corsSettings: {
        allowedOrigins: ['*'],
        allowedMethods: [],
        allowedHeaders: [],
        allowCredentials: false,
        enabled: true,
      },
    });

    const corsSettings = patchedDomain.corsSettings;
    expect(corsSettings.enabled).toBe(true);
    expect(corsSettings.allowedOrigins).toHaveLength(1);
    expect(corsSettings.allowedOrigins).toContain('*');
    expect(corsSettings.allowedHeaders).toHaveLength(0);
    expect(corsSettings.allowedMethods).toHaveLength(0);
  });

  it('should disable CORS settings', async () => {
    const patchedDomain = await patchDomain(domain.id, accessToken, {
      path: `${domain.path}`,
      vhostMode: false,
      vhosts: [],
      corsSettings: {
        allowedOrigins: ['*'],
        allowedMethods: [],
        allowedHeaders: [],
        allowCredentials: false,
        enabled: false,
      },
    });

    const corsSettings = patchedDomain.corsSettings;
    expect(corsSettings.enabled).toBe(false);
    expect(corsSettings.allowedOrigins).toHaveLength(1);
    expect(corsSettings.allowedOrigins).toContain('*');
    expect(corsSettings.allowedHeaders).toHaveLength(0);
    expect(corsSettings.allowedMethods).toHaveLength(0);
  });

  it('should create custom CORS settings', async () => {
    const patchedDomain = await patchDomain(domain.id, accessToken, {
      path: `${domain.path}`,
      vhostMode: false,
      vhosts: [],
      corsSettings: {
        allowedOrigins: ['https://foo.com, https://bar.com/*'],
        allowedMethods: ['OPTIONS', 'PUT', 'DELETE'],
        allowedHeaders: ['Authorization', 'Application-Json'],
        allowCredentials: true,
        enabled: true,
        maxAge: 50,
      },
    });

    const corsSettings = patchedDomain.corsSettings;
    expect(corsSettings.enabled).toBe(true);
    expect(corsSettings.allowedOrigins).toHaveLength(1);
    expect(corsSettings.allowedOrigins).toContain('https://foo.com, https://bar.com/*');
    expect(corsSettings.allowedHeaders).toHaveLength(2);
    expect(corsSettings.allowedHeaders).toEqual(expect.arrayContaining(['Authorization', 'Application-Json']));
    expect(corsSettings.allowedMethods).toHaveLength(3);
    expect(corsSettings.allowedMethods).toEqual(expect.arrayContaining(['OPTIONS', 'PUT', 'DELETE']));
    expect(corsSettings.maxAge).toBe(50);
  });
});

describe('Entrypoints: CORS settings - test open configuration', () => {
  it('should test CORS settings, open configuration', async () => {
    await patchDomain(domain.id, accessToken, {
      path: `${domain.path}`,
      vhostMode: false,
      vhosts: [],
      corsSettings: {
        allowedOrigins: ['https://foo.com'],
        allowedMethods: [],
        allowedHeaders: [],
        allowCredentials: true,
        enabled: true,
        maxAge: 50,
      },
    });
    await delay(5000);
    const response = await performOptions(process.env.AM_GATEWAY_URL, userInfo.replace(process.env.AM_GATEWAY_URL, ''), {
      'Access-Control-Request-Headers': 'Authorization',
      'Access-Control-Request-Method': 'GET',
      Origin: 'https://foo.com',
      Referer: 'https://foo.com',
      'Sec-Fetch-Dest': 'empty',
      'Sec-Fetch-Mode': 'cors',
      'Sec-Fetch-Site': 'same-site',
    });

    expect(response.status).toEqual(204);
  });
});

describe('Entrypoints: CORS settings - test strict configuration', () => {
  it('should test CORS settings strict configuration', async () => {
    await patchDomain(domain.id, accessToken, {
      path: `${domain.path}`,
      vhostMode: false,
      vhosts: [],
      corsSettings: {
        allowedOrigins: ['https://bar.com'],
        allowedMethods: ['GET', 'OPTIONS'],
        allowedHeaders: ['Authorization'],
        allowCredentials: true,
        enabled: true,
        maxAge: 50,
      },
    });
    await delay(5000);

    const response = await performOptions(process.env.AM_GATEWAY_URL, userInfo.replace(process.env.AM_GATEWAY_URL, ''), {
      'Access-Control-Request-Headers': 'Authorization',
      'Access-Control-Request-Method': 'GET',
      Origin: 'https://foo.com',
      Referer: 'https://foo.com',
      'Sec-Fetch-Dest': 'empty',
      'Sec-Fetch-Mode': 'cors',
      'Sec-Fetch-Site': 'same-site',
    });

    expect(response.status).toEqual(403);
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await deleteDomain(domain.id, accessToken);
  }
});
