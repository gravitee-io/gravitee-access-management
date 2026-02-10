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
import { createDomain, patchDomain, safeDeleteDomain, startDomain } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';

setup();

let accessToken: string;
let domain: any;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();

  const createdDomain = await createDomain(accessToken, uniqueName('txn-valid', true), 'Token exchange validation test domain');
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

describe('Token exchange validation - reject empty options when enabled', () => {
  it('should reject token exchange enabled with empty allowedSubjectTokenTypes', async () => {
    await expect(
      patchDomain(domain.id, accessToken, {
        tokenExchangeSettings: {
          enabled: true,
          allowImpersonation: true,
          allowDelegation: false,
          allowedSubjectTokenTypes: [],
          allowedRequestedTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
        },
      }),
    ).rejects.toThrow();
  });

  it('should reject token exchange enabled with empty allowedRequestedTokenTypes', async () => {
    await expect(
      patchDomain(domain.id, accessToken, {
        tokenExchangeSettings: {
          enabled: true,
          allowImpersonation: true,
          allowDelegation: false,
          allowedSubjectTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
          allowedRequestedTokenTypes: [],
        },
      }),
    ).rejects.toThrow();
  });

  it('should reject delegation enabled with empty allowedActorTokenTypes', async () => {
    await expect(
      patchDomain(domain.id, accessToken, {
        tokenExchangeSettings: {
          enabled: true,
          allowImpersonation: false,
          allowDelegation: true,
          allowedSubjectTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
          allowedRequestedTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
          allowedActorTokenTypes: [],
        },
      }),
    ).rejects.toThrow();
  });
});

describe('Token exchange validation - accept valid configurations', () => {
  it('should accept valid token exchange config with all required fields populated', async () => {
    const patched: any = await patchDomain(domain.id, accessToken, {
      tokenExchangeSettings: {
        enabled: true,
        allowImpersonation: true,
        allowDelegation: true,
        allowedSubjectTokenTypes: [
          'urn:ietf:params:oauth:token-type:access_token',
          'urn:ietf:params:oauth:token-type:id_token',
        ],
        allowedRequestedTokenTypes: [
          'urn:ietf:params:oauth:token-type:access_token',
          'urn:ietf:params:oauth:token-type:id_token',
        ],
        allowedActorTokenTypes: [
          'urn:ietf:params:oauth:token-type:access_token',
        ],
        maxDelegationDepth: 3,
      },
    });

    expect(patched).toBeDefined();
    expect(patched.tokenExchangeSettings).toBeDefined();
    expect(patched.tokenExchangeSettings.enabled).toBe(true);
  });

  it('should accept token exchange disabled with empty lists', async () => {
    const patched: any = await patchDomain(domain.id, accessToken, {
      tokenExchangeSettings: {
        enabled: false,
        allowImpersonation: false,
        allowDelegation: false,
        allowedSubjectTokenTypes: [],
        allowedRequestedTokenTypes: [],
        allowedActorTokenTypes: [],
      },
    });

    expect(patched).toBeDefined();
    expect(patched.tokenExchangeSettings).toBeDefined();
    expect(patched.tokenExchangeSettings.enabled).toBe(false);
  });
});
