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
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, patchDomain, startDomain } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../test-fixture';

let accessToken: string;
let domain: any;

setup(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  const createdDomain = await createDomain(accessToken, uniqueName('te-valid', true), faker.company.catchPhraseDescriptor());
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();

  const startedDomain = await startDomain(createdDomain.id, accessToken);
  expect(startedDomain).toBeDefined();
  domain = startedDomain;
});

afterAll(async () => {
  if (domain?.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});

describe('Token Exchange settings validation', () => {
  it('should reject empty allowedSubjectTokenTypes', async () => {
    await expect(
      patchDomain(domain.id, accessToken, {
        tokenExchangeSettings: {
          enabled: true,
          allowImpersonation: true,
          allowDelegation: false,
          allowedSubjectTokenTypes: [],
          allowedRequestedTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
          allowedActorTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
        },
      }),
    ).rejects.toThrow();
  });

  it('should reject empty allowedRequestedTokenTypes', async () => {
    await expect(
      patchDomain(domain.id, accessToken, {
        tokenExchangeSettings: {
          enabled: true,
          allowImpersonation: true,
          allowDelegation: false,
          allowedSubjectTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
          allowedRequestedTokenTypes: [],
          allowedActorTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
        },
      }),
    ).rejects.toThrow();
  });

  it('should reject empty allowedActorTokenTypes when delegation is enabled', async () => {
    await expect(
      patchDomain(domain.id, accessToken, {
        tokenExchangeSettings: {
          enabled: true,
          allowImpersonation: true,
          allowDelegation: true,
          allowedSubjectTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
          allowedRequestedTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
          allowedActorTokenTypes: [],
        },
      }),
    ).rejects.toThrow();
  });

  it('should accept valid config with all required token types', async () => {
    const patchedDomain = await patchDomain(domain.id, accessToken, {
      tokenExchangeSettings: {
        enabled: true,
        allowImpersonation: true,
        allowDelegation: true,
        allowedSubjectTokenTypes: [
          'urn:ietf:params:oauth:token-type:access_token',
          'urn:ietf:params:oauth:token-type:id_token',
        ],
        allowedRequestedTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
        allowedActorTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
      },
    });

    expect(patchedDomain).toBeDefined();
    expect(patchedDomain.tokenExchangeSettings.enabled).toBe(true);
    expect(patchedDomain.tokenExchangeSettings.allowedSubjectTokenTypes.length).toBe(2);
    expect(patchedDomain.tokenExchangeSettings.allowedRequestedTokenTypes.length).toBe(1);
    expect(patchedDomain.tokenExchangeSettings.allowedActorTokenTypes.length).toBe(1);
  });

  it('should skip validation when token exchange is disabled', async () => {
    const patchedDomain = await patchDomain(domain.id, accessToken, {
      tokenExchangeSettings: {
        enabled: false,
        allowImpersonation: false,
        allowDelegation: false,
        allowedSubjectTokenTypes: [],
        allowedRequestedTokenTypes: [],
        allowedActorTokenTypes: [],
      },
    });

    expect(patchedDomain).toBeDefined();
    expect(patchedDomain.tokenExchangeSettings.enabled).toBe(false);
  });
});
