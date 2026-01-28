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
import * as faker from 'faker';
import { afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, patchDomain, startDomain } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../test-fixture';

let accessToken;
let domain;

setup(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  const createdDomain = await createDomain(accessToken, uniqueName('cors-test', true), faker.company.catchPhraseDescriptor());
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();

  const startedDomain = await startDomain(createdDomain.id, accessToken);
  expect(startedDomain).toBeDefined();
  expect(startedDomain.id).toEqual(createdDomain.id);
  domain = startedDomain;
});

describe('CORS settings validation', () => {
  it('should reject invalid maxAge values - negative numbers', async () => {
    await expect(
      patchDomain(domain.id, accessToken, {
        path: `${domain.path}`,
        vhostMode: false,
        vhosts: [],
        corsSettings: {
          enabled: true,
          maxAge: -1,
        },
      }),
    ).rejects.toThrow();
  });

  it('should reject invalid maxAge values - non-numeric values', async () => {
    await expect(
      patchDomain(domain.id, accessToken, {
        path: `${domain.path}`,
        vhostMode: false,
        vhosts: [],
        corsSettings: {
          enabled: true,
          maxAge: '100',
        },
      }),
    ).rejects.toThrow();
  });

  it('should create custom CORS settings with valid values', async () => {
    const patchedDomain = await patchDomain(domain.id, accessToken, {
      path: `${domain.path}`,
      vhostMode: false,
      vhosts: [],
      corsSettings: {
        allowedOrigins: ['https://example.com', 'https://app.example.com'],
        allowedMethods: ['GET', 'POST', 'OPTIONS'],
        allowedHeaders: ['Authorization', 'Content-Type', 'X-Custom-Header'],
        allowCredentials: true,
        enabled: true,
        maxAge: 3600,
      },
    });

    const corsSettings = patchedDomain.corsSettings;
    expect(corsSettings.enabled).toBe(true);
    expect(corsSettings.allowedOrigins.size).toBe(2);
    expect(corsSettings.allowedOrigins.has('https://example.com')).toBe(true);
    expect(corsSettings.allowedOrigins.has('https://app.example.com')).toBe(true);
    expect(corsSettings.allowedMethods.size).toBe(3);
    expect(corsSettings.allowedMethods.has('GET')).toBe(true);
    expect(corsSettings.allowedMethods.has('POST')).toBe(true);
    expect(corsSettings.allowedMethods.has('OPTIONS')).toBe(true);
    expect(corsSettings.allowedHeaders.size).toBe(3);
    expect(corsSettings.allowedHeaders.has('Authorization')).toBe(true);
    expect(corsSettings.allowedHeaders.has('Content-Type')).toBe(true);
    expect(corsSettings.allowedHeaders.has('X-Custom-Header')).toBe(true);
    expect(corsSettings.allowCredentials).toBe(true);
    expect(corsSettings.maxAge).toBe(3600);
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});
