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
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import {
  createDomain,
  safeDeleteDomain,
  patchDomain,
  startDomain,
  waitForDomainStart,
  getDomain,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { uniqueName } from '@utils-commands/misc';

globalThis.fetch = fetch;
jest.setTimeout(200000);

let accessToken: string;
let domain: any;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  const createdDomain = await createDomain(accessToken, uniqueName('cba-settings', true), 'CBA Settings Test Domain');
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();

  const domainStarted = await startDomain(createdDomain.id, accessToken)
    .then((domain) => waitForDomainStart(domain))
    .then((result) => result.domain);
  expect(domainStarted).toBeDefined();
  expect(domainStarted.id).toEqual(createdDomain.id);

  domain = domainStarted;
});

afterAll(async () => {
  if (domain?.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});

describe('Certificate Based Authentication Settings', () => {
  const certificateBasedAuthUrl = 'https://cba-login.example.com/base';

  it('should enable certificate based authentication at domain level', async () => {
    // Given: A domain exists
    expect(domain).toBeDefined();

    // When: Admin enables CBA via login settings
    const patchedDomain = await patchDomain(domain.id, accessToken, {
      loginSettings: {
        certificateBasedAuthEnabled: true,
        certificateBasedAuthUrl,
      },
    });

    // Then: CBA is enabled
    expect(patchedDomain).toBeDefined();
    expect(patchedDomain.loginSettings).toBeDefined();
    expect(patchedDomain.loginSettings?.certificateBasedAuthEnabled).toBe(true);
    expect(patchedDomain.loginSettings?.certificateBasedAuthUrl).toBe(certificateBasedAuthUrl);
  });

  it('should disable certificate based authentication at domain level', async () => {
    // Given: CBA is enabled
    await patchDomain(domain.id, accessToken, {
      loginSettings: {
        certificateBasedAuthEnabled: true,
        certificateBasedAuthUrl,
      },
    });

    // When: Admin disables CBA
    await patchDomain(domain.id, accessToken, {
      loginSettings: {
        certificateBasedAuthEnabled: false,
      },
    });

    // Then: CBA is disabled (verify by getting domain)
    const retrievedDomain = await getDomain(domain.id, accessToken);
    expect(retrievedDomain).toBeDefined();
    expect(retrievedDomain.loginSettings).toBeDefined();
    expect(retrievedDomain.loginSettings?.certificateBasedAuthEnabled).toBe(false);
  });

  it('should persist certificate based authentication setting', async () => {
    // Given: CBA is enabled
    await patchDomain(domain.id, accessToken, {
      loginSettings: {
        certificateBasedAuthEnabled: true,
        certificateBasedAuthUrl,
      },
    });

    // When: Domain is retrieved
    const retrievedDomain = await getDomain(domain.id, accessToken);

    // Then: CBA setting is persisted
    expect(retrievedDomain).toBeDefined();
    expect(retrievedDomain.loginSettings).toBeDefined();
    expect(retrievedDomain.loginSettings?.certificateBasedAuthEnabled).toBe(true);
    expect(retrievedDomain.loginSettings?.certificateBasedAuthUrl).toBe(certificateBasedAuthUrl);
  });

  it('should allow combining certificate based authentication with other login settings', async () => {
    // Given: A domain exists
    expect(domain).toBeDefined();

    // When: Admin enables CBA along with other login settings
    await patchDomain(domain.id, accessToken, {
      loginSettings: {
        certificateBasedAuthEnabled: true,
        certificateBasedAuthUrl,
        passwordlessEnabled: true,
        forgotPasswordEnabled: true,
      },
    });

    // Then: All settings are applied (verify by getting domain)
    const retrievedDomain = await getDomain(domain.id, accessToken);
    expect(retrievedDomain).toBeDefined();
    expect(retrievedDomain.loginSettings).toBeDefined();
    expect(retrievedDomain.loginSettings?.certificateBasedAuthEnabled).toBe(true);
    expect(retrievedDomain.loginSettings?.passwordlessEnabled).toBe(true);
    expect(retrievedDomain.loginSettings?.forgotPasswordEnabled).toBe(true);
    expect(retrievedDomain.loginSettings?.certificateBasedAuthUrl).toBe(certificateBasedAuthUrl);
  });

  it('should reject enabling CBA without base URL', async () => {
    await expect(
      patchDomain(domain.id, accessToken, {
        loginSettings: {
          certificateBasedAuthEnabled: true,
        },
      })
    ).rejects.toMatchObject({
      response: { status: 400 }
    });
  });

  it('should reject enabling CBA with non-HTTPS URL', async () => {
    await expect(
      patchDomain(domain.id, accessToken, {
        loginSettings: {
          certificateBasedAuthEnabled: true,
          certificateBasedAuthUrl: 'http://cba-login.example.com',
        },
      })
    ).rejects.toMatchObject({
      response: { status: 400 }
    });
  });
});
