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
import { ALERT_NAME, AlertEngineFixture, setupAlertEngineFixture } from './fixtures/alert-engine-fixture';
import { setup } from '../test-fixture';

/**
 * End-to-end alerting through a real Gravitee Alert Engine: the management API pushes the domain's
 * alert trigger and its notifier, the gateway pushes the authentication events, and Alert Engine
 * calls the webhook (WireMock) once the trigger fires.
 *
 * Needs the local stack started with Alert Engine:
 *
 *   ./local-stack.sh up --alert-engine
 *   npm --prefix gravitee-am-test run ci:alert-engine
 */
setup(200000);

let fixture: AlertEngineFixture;

beforeAll(async () => {
  fixture = await setupAlertEngineFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Alert Engine - too many login failures', () => {
  it('should enable the alert on the domain with the webhook notifier', async () => {
    expect(fixture.domain.alertEnabled).toBe(true);
    expect(fixture.alertNotifier.type).toEqual('webhook-notifier');
    expect(fixture.alertNotifier.enabled).toBe(true);
    expect(fixture.alertTrigger.type).toEqual('too_many_login_failures');
    expect(fixture.alertTrigger.enabled).toBe(true);
    expect(fixture.alertTrigger.alertNotifiers).toEqual([fixture.alertNotifier.id]);
  });

  it('should push the alert notification to the webhook when user logins keep failing', async () => {
    const notifications = await fixture.failLoginsUntilNotified();

    expect(notifications[0]).toEqual({
      alert: ALERT_NAME,
      severity: 'WARNING',
      domainId: fixture.domain.id,
      domainName: fixture.domain.name,
    });
  });
});
