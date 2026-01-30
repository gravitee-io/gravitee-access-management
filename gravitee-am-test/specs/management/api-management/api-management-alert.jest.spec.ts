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
import { setupApiManagementAlertFixture, ApiManagementAlertFixture } from './fixtures/api-management-alert-fixture';
import { setup } from '../../test-fixture';
import { patchDomain } from '@management-commands/domain-management-commands';
import { getAlertsApi, getDomainApi, getNotifierApi } from '@management-commands/service/utils';
import { PatchAlertTriggerTypeEnum } from '../../../api/management/models/PatchAlertTrigger';

setup(200000);

let fixture: ApiManagementAlertFixture;

beforeAll(async () => {
  fixture = await setupApiManagementAlertFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('API Management - Alert', () => {
  const orgId = process.env.AM_DEF_ORG_ID!;
  const envId = process.env.AM_DEF_ENV_ID!;

  describe('Prepare', () => {
    it('should create alert domain', async () => {
      expect(fixture.domain).toBeDefined();
      expect(fixture.domain.id).toBeDefined();
    });
  });

  describe('Alert', () => {
    it('should enable domain alerts', async () => {
      const patched = await patchDomain(fixture.domain.id, fixture.accessToken!, {
        alertEnabled: true,
      });
      expect(patched).toBeDefined();
      expect(patched.alertEnabled).toBe(true);
    });

    it('should list domain alert notifiers (empty)', async () => {
      const api = getAlertsApi(fixture.accessToken!);
      const response = await api.listAlertNotifiersRaw({
        organizationId: orgId,
        environmentId: envId,
        domain: fixture.domain.id,
      });
      expect(response.raw.status).toBe(200);
      const notifiers = await response.value();
      expect(notifiers).toEqual([]);
    });

    it('should list plugin notifiers', async () => {
      const api = getNotifierApi(fixture.accessToken!);
      const notifiers = await api.listNotifiers({ expand: ['icon'] });
      expect(notifiers).toBeDefined();
      expect(Array.isArray(notifiers)).toBe(true);
      expect(notifiers.length).toBe(3);
    });

    it('should create domain webhook alert notifier', async () => {
      const notifierId = await fixture.ensureWebhookNotifierExists();
      expect(notifierId).toBeDefined();
    });

    it('should list domain alert notifiers (not empty)', async () => {
      await fixture.ensureWebhookNotifierExists();
      const api = getAlertsApi(fixture.accessToken!);
      const response = await api.listAlertNotifiersRaw({
        organizationId: orgId,
        environmentId: envId,
        domain: fixture.domain.id,
      });
      expect(response.raw.status).toBe(200);
      const notifiers = await response.value();
      expect(notifiers.length).toBeGreaterThan(0);
    });

    it('should enable too many login failures alert', async () => {
      const alertNotifierId = await fixture.ensureWebhookNotifierExists();

      const api = getAlertsApi(fixture.accessToken!);
      const response = await api.updateAlertTriggersRaw({
        organizationId: orgId,
        environmentId: envId,
        domain: fixture.domain.id,
        patchAlertTrigger: [
          {
            type: PatchAlertTriggerTypeEnum.TooManyLoginFailures,
            enabled: true,
            alertNotifiers: [alertNotifierId],
          },
        ],
      });
      expect(response.raw.status).toBe(200);
    });
  });

  describe('Cleanup', () => {
    it('should delete alert domain', async () => {
      const response = await getDomainApi(fixture.accessToken!).deleteDomainRaw({
        organizationId: orgId,
        environmentId: envId,
        domain: fixture.domain.id,
      });
      expect(response.raw.status).toBe(204);
    });
  });
});
