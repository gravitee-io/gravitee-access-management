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
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';
import { AutomationDomainFixture, setupAutomationDomainFixture } from './fixtures/automation-domain-fixture';
import { buildAutomationDomainDef } from './fixtures/automation-definitions';

setup(120000);

let fixture: AutomationDomainFixture;

beforeAll(async () => {
  fixture = await setupAutomationDomainFixture({ keyPrefix: 'xframe' });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Automation API - X-Frame-Options action', () => {
  it('should accept SAMEORIGIN on update', async () => {
    const response = await fixture.client.putDomain(
      buildAutomationDomainDef({
        key: fixture.domainKey,
        webProtectionSettings: {
          xframe: { inherited: false, enabled: true, action: 'SAMEORIGIN' },
        },
      }),
    );

    expect(response.status).toBe(200);
    expect(response.body.webProtectionSettings.xframe.action).toBe('SAMEORIGIN');
  });

  it('should reject an unsupported action on create and leave no domain behind', async () => {
    const key = uniqueName('xframebad', true).toLowerCase();

    const response = await fixture.client.putDomain(
      buildAutomationDomainDef({
        key,
        webProtectionSettings: {
          xframe: { inherited: false, enabled: true, action: 'ALLOW-FROM' },
        },
      }),
    );

    expect(response.status).toBe(400);
    expect(JSON.stringify(response.body)).toContain('ALLOW-FROM');
    expect((await fixture.client.getDomain(key)).status).toBe(404);
  });
});
