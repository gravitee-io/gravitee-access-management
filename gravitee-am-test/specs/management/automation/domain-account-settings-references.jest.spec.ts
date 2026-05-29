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

/**
 * Pins the out-of-scope contract for the domain payload: bot detections and device
 * notifiers are not managed by the Automation API and never surfaced, and
 * {@code oidc.cimdSettings} is not surfaced. The in-scope cross-reference
 * ({@code accountSettings.defaultIdentityProviderForRegistration}) is covered by
 * domain-identity-providers.jest.spec.ts.
 */
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { setup } from '../../test-fixture';
import {
  AutomationDomainFixture,
  setupAutomationDomainFixture,
} from './fixtures/automation-domain-fixture';

setup(120000);

let fixture: AutomationDomainFixture;

beforeAll(async () => {
  fixture = await setupAutomationDomainFixture({ keyPrefix: 'autoacct' });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Automation API - out-of-scope resources are not surfaced', () => {
  it('should not surface bot detections or device notifiers on the domain', async () => {
    const get = await fixture.client.getDomain(fixture.domainKey);
    expect(get.status).toBe(200);
    expect(get.body.botDetections).toBeUndefined();
    expect(get.body.deviceNotifiers).toBeUndefined();
    expect(get.body.accountSettings?.botDetectionPlugin).toBeUndefined();
    expect(get.body.accountSettings?.useBotDetection).toBeUndefined();
  });

  it('should not surface oidc.cimdSettings on GET responses', async () => {
    const get = await fixture.client.getDomain(fixture.domainKey);
    expect(get.status).toBe(200);
    expect(get.body.oidc?.cimdSettings).toBeUndefined();
  });
});
