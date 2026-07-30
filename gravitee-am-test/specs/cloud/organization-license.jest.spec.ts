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
import { waitForCockpitConnection } from '@cloud-commands/cockpit-commands';
import { setup } from '../test-fixture';
import { CloudLicenseFixture, OSS_TIER, EXAMPLE_EE_FEATURES, setupCloudLicenseFixture } from './fixtures/cloud-license-fixture';

setup(120000);

let fixture: CloudLicenseFixture;

beforeAll(async () => {
  const accessToken = await requestAdminAccessToken();
  await waitForCockpitConnection();
  fixture = await setupCloudLicenseFixture(accessToken);
});

afterAll(async () => {
  await fixture?.cleanup();
});

describe('Cloud organization license', () => {
  it('serves only OSS entitlements while the organization has no license', async () => {
    await fixture.clearLicense();

    const license = await fixture.organizationLicense();

    expect(license.scope).toBe('PLATFORM');
    expect(license.tier).toBe(OSS_TIER);
    expect([...(license.features ?? [])]).toEqual([]);
  });

  it('acknowledges a Cockpit license update for the organization', async () => {
    const reply = await fixture.pushLicense();

    expect(reply.commandStatus).toBe('SUCCEEDED');
    expect(reply.errorDetails).toBeUndefined();
  });

  it("serves the pushed license as the organization's own, granting its EE features", async () => {
    await fixture.pushLicense();

    const license = await fixture.organizationLicense();

    expect(license.scope).toBe('ORGANIZATION');
    expect(license.tier).not.toBe(OSS_TIER);
    expect([...(license.features ?? [])]).toEqual(expect.arrayContaining(EXAMPLE_EE_FEATURES));
    expect(license.expiresAt).toBeGreaterThan(Date.now());
  });

  it('withdraws the EE features when Cockpit stops sending a license', async () => {
    await fixture.pushLicense();
    expect([...((await fixture.organizationLicense()).features ?? [])]).toEqual(expect.arrayContaining(EXAMPLE_EE_FEATURES));

    const reply = await fixture.clearLicense();

    expect(reply.commandStatus).toBe('SUCCEEDED');
    const license = await fixture.organizationLicense();
    expect(license.scope).toBe('PLATFORM');
    expect(license.tier).toBe(OSS_TIER);
    EXAMPLE_EE_FEATURES.forEach((feature) => expect([...(license.features ?? [])]).not.toContain(feature));
  });
});
