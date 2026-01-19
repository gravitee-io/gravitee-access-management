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
import { afterAll, beforeAll, expect } from '@jest/globals';
import { CbaFixture, setupFixture } from './fixture/cba-fixture';
import { getDomain, patchDomain } from '@management-commands/domain-management-commands';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: CbaFixture;

beforeAll(async () => {
  fixture = await setupFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Certificate Based Authentication - Domain Settings', () => {
  it('should enable CBA', async () => {
    // Given: A domain exists
    expect(fixture.domain).toBeDefined();

    const patchedDomain = await fixture.enableCba();

    // Then: CBA is enabled
    expect(patchedDomain).toBeDefined();
    expect(patchedDomain.loginSettings).toBeDefined();
    expect(patchedDomain.loginSettings?.certificateBasedAuthEnabled).toBe(true);
    expect(patchedDomain.loginSettings?.certificateBasedAuthUrl).toBeDefined();
  });

  it('should disable CBA', async () => {
    // Given: CBA is enabled
    await fixture.enableCba();
    await fixture.disableCba();

    // Then: CBA is disabled (verify by getting domain)
    const retrievedDomain = await getDomain(fixture.domain.id, fixture.accessToken);
    expect(retrievedDomain).toBeDefined();
    expect(retrievedDomain.loginSettings).toBeDefined();
    expect(retrievedDomain.loginSettings?.certificateBasedAuthEnabled).toBe(false);
  });

  it('should persist CBA settings', async () => {
    // Given: CBA is enabled
    await fixture.enableCba();

    // When: Domain is retrieved
    const retrievedDomain = await getDomain(fixture.domain.id, fixture.accessToken);

    // Then: CBA setting is persisted
    expect(retrievedDomain).toBeDefined();
    expect(retrievedDomain.loginSettings).toBeDefined();
    expect(retrievedDomain.loginSettings?.certificateBasedAuthEnabled).toBe(true);
    expect(retrievedDomain.loginSettings?.certificateBasedAuthUrl).toBe(fixture.certificateBasedAuthUrl);
  });

  it('should allow combining certificate based authentication with other login settings', async () => {
    // Given: A domain exists
    expect(fixture.domain).toBeDefined();

    // When: Admin enables CBA along with other login settings
    await patchDomain(fixture.domain.id, fixture.accessToken, {
      loginSettings: {
        certificateBasedAuthEnabled: true,
        certificateBasedAuthUrl: fixture.certificateBasedAuthUrl,
        passwordlessEnabled: true,
        forgotPasswordEnabled: true,
      },
    });

    // Then: All settings are applied (verify by getting domain)
    const retrievedDomain = await getDomain(fixture.domain.id, fixture.accessToken);
    expect(retrievedDomain).toBeDefined();
    expect(retrievedDomain.loginSettings).toBeDefined();
    expect(retrievedDomain.loginSettings?.certificateBasedAuthEnabled).toBe(true);
    expect(retrievedDomain.loginSettings?.passwordlessEnabled).toBe(true);
    expect(retrievedDomain.loginSettings?.forgotPasswordEnabled).toBe(true);
    expect(retrievedDomain.loginSettings?.certificateBasedAuthUrl).toBe(fixture.certificateBasedAuthUrl);
  });

  it('should reject enabling CBA without base URL', async () => {
    await expect(
      patchDomain(fixture.domain.id, fixture.accessToken, {
        loginSettings: {
          certificateBasedAuthEnabled: true,
        },
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
    });
  });

  it('should reject enabling CBA with non-HTTPS URL', async () => {
    await expect(
      patchDomain(fixture.domain.id, fixture.accessToken, {
        loginSettings: {
          certificateBasedAuthEnabled: true,
          certificateBasedAuthUrl: 'http://cba-login.example.com',
        },
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
    });
  });
});
