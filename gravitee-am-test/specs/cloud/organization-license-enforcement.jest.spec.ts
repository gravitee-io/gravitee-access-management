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
import {
  CloudLicenseEnforcementFixture,
  GATED_PLUGIN,
  UNLICENSED_MESSAGE,
  setupCloudLicenseEnforcementFixture,
} from './fixtures/cloud-license-enforcement-fixture';

setup(180000);

let fixture: CloudLicenseEnforcementFixture;

beforeAll(async () => {
  const accessToken = await requestAdminAccessToken();
  await waitForCockpitConnection();
  fixture = await setupCloudLicenseEnforcementFixture(accessToken);
});

afterAll(async () => {
  await fixture?.cleanup();
});

describe('Cloud organization license enforcement (gateway)', () => {
  it('skips an EE plugin, without harming the domain, when the organization has no license', async () => {
    await fixture.clearLicense();

    const state = await fixture.deployDomain();

    expect(state.creationState[GATED_PLUGIN].message).toMatch(UNLICENSED_MESSAGE);
    expect(state.creationState[GATED_PLUGIN].success).toBe(true);
    expect(state.status).toBe('DEPLOYED');
    expect(state.stable).toBe(true);
    expect(state.synchronized).toBe(true);
  });

  it('loads the same EE plugin once the organization is licensed for it', async () => {
    await fixture.pushLicense();

    const state = await fixture.deployDomain();

    expect(state.creationState[GATED_PLUGIN].message).toBeFalsy();
  });
});
